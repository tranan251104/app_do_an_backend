package vn.anpay.backend.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends Firebase Cloud Messaging messages through the official FCM HTTP v1 API.
 *
 * This implementation deliberately uses only JDK HttpClient + Jackson so the src-only
 * backend can support FCM without requiring a new Maven dependency. Authentication uses
 * the OAuth 2.0 service-account JWT flow.
 */
@Service
public class FcmPushService {
    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String projectId;
    private final String credentialsPath;

    private volatile ServiceAccount serviceAccount;
    private volatile AccessToken cachedToken;

    public FcmPushService(
            ObjectMapper mapper,
            @Value("${app.push.fcm.project-id:}") String projectId,
            @Value("${app.push.fcm.credentials-path:}") String credentialsPath
    ) {
        this.mapper = mapper;
        this.projectId = projectId == null ? "" : projectId.trim();
        this.credentialsPath = credentialsPath == null ? "" : credentialsPath.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void send(String token, String title, String body, Map<String, String> data) {
        if (token == null || token.isBlank()) {
            throw new InvalidFcmTokenException("FCM token is blank");
        }

        ServiceAccount account = account();
        String resolvedProjectId = !projectId.isBlank() ? projectId : account.projectId();
        if (resolvedProjectId == null || resolvedProjectId.isBlank()) {
            throw new IllegalStateException("FCM project id is not configured");
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("token", token);
        message.put("notification", Map.of(
                "title", title == null ? "ANPAY" : title,
                "body", body == null ? "" : body
        ));
        if (data != null && !data.isEmpty()) {
            message.put("data", data);
        }
        message.put("android", Map.of(
                "priority", "HIGH",
                "notification", Map.of("sound", "default")
        ));

        String json;
        try {
            json = mapper.writeValueAsString(Map.of("message", message));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize FCM request", ex);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://fcm.googleapis.com/v1/projects/" + resolvedProjectId + "/messages:send"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken(account))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.debug("[FCM_SEND_OK] status={}", response.statusCode());
            return;
        }

        String responseBody = response.body() == null ? "" : response.body();
        if (isInvalidRegistrationToken(response.statusCode(), responseBody)) {
            throw new InvalidFcmTokenException("FCM registration token is no longer valid");
        }

        throw new IllegalStateException(
                "FCM HTTP " + response.statusCode() + ": " + abbreviate(responseBody, 1000)
        );
    }

    private synchronized ServiceAccount account() {
        if (serviceAccount != null) {
            return serviceAccount;
        }
        if (credentialsPath.isBlank()) {
            throw new IllegalStateException(
                    "FCM credentials path is empty. Set FIREBASE_CREDENTIALS_PATH to a Firebase service-account JSON file"
            );
        }

        try {
            String raw = Files.readString(Path.of(credentialsPath), StandardCharsets.UTF_8);
            JsonNode node = mapper.readTree(raw);
            String clientEmail = required(node, "client_email");
            String privateKeyPem = required(node, "private_key");
            String accountProjectId = node.path("project_id").asText("").trim();
            String tokenUri = node.path("token_uri").asText(DEFAULT_TOKEN_URI).trim();
            if (tokenUri.isBlank()) {
                tokenUri = DEFAULT_TOKEN_URI;
            }
            serviceAccount = new ServiceAccount(
                    accountProjectId,
                    clientEmail,
                    parsePrivateKey(privateKeyPem),
                    tokenUri
            );
            log.info("[FCM_CONFIG] Firebase service account loaded projectId={} clientEmail={}",
                    accountProjectId, maskEmail(clientEmail));
            return serviceAccount;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load Firebase service-account credentials", ex);
        }
    }

    private String accessToken(ServiceAccount account) {
        AccessToken token = cachedToken;
        Instant now = Instant.now();
        if (token != null && token.expiresAt().isAfter(now.plusSeconds(60))) {
            return token.value();
        }
        synchronized (this) {
            token = cachedToken;
            now = Instant.now();
            if (token != null && token.expiresAt().isAfter(now.plusSeconds(60))) {
                return token.value();
            }
            cachedToken = exchangeAccessToken(account, now);
            return cachedToken.value();
        }
    }

    private AccessToken exchangeAccessToken(ServiceAccount account, Instant now) {
        long issuedAt = now.getEpochSecond();
        long expiresAt = issuedAt + 3600;

        try {
            String header = base64Url(mapper.writeValueAsBytes(Map.of(
                    "alg", "RS256",
                    "typ", "JWT"
            )));
            String claims = base64Url(mapper.writeValueAsBytes(Map.of(
                    "iss", account.clientEmail(),
                    "scope", FCM_SCOPE,
                    "aud", account.tokenUri(),
                    "iat", issuedAt,
                    "exp", expiresAt
            )));
            String unsignedJwt = header + "." + claims;

            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(account.privateKey());
            signer.update(unsignedJwt.getBytes(StandardCharsets.UTF_8));
            String assertion = unsignedJwt + "." + base64Url(signer.sign());

            String form = "grant_type=" + urlEncode("urn:ietf:params:oauth:grant-type:jwt-bearer")
                    + "&assertion=" + urlEncode(assertion);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(account.tokenUri()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "OAuth token endpoint HTTP " + response.statusCode() + ": "
                                + abbreviate(response.body(), 1000)
                );
            }

            JsonNode node = mapper.readTree(response.body());
            String value = required(node, "access_token");
            long expiresIn = node.path("expires_in").asLong(3600);
            return new AccessToken(value, Instant.now().plusSeconds(Math.max(60, expiresIn)));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not obtain Google OAuth access token for FCM", ex);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FCM HTTP request interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("FCM HTTP request failed", ex);
        }
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private boolean isInvalidRegistrationToken(int status, String body) {
        if (status != 400 && status != 404) {
            return false;
        }
        String normalized = body == null ? "" : body.toUpperCase();
        return normalized.contains("UNREGISTERED")
                || normalized.contains("REGISTRATION-TOKEN-NOT-REGISTERED");
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing Firebase service-account field: " + field);
        }
        return value;
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.substring(0, Math.min(3, at)) + "***" + email.substring(at);
    }

    private String abbreviate(String value, int max) {
        if (value == null) return "";
        String singleLine = value.replaceAll("[\\r\\n]+", " ");
        return singleLine.length() <= max ? singleLine : singleLine.substring(0, max);
    }

    private record ServiceAccount(String projectId, String clientEmail, PrivateKey privateKey, String tokenUri) {
    }

    private record AccessToken(String value, Instant expiresAt) {
    }
}
