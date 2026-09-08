package vn.anpay.backend.auth.firebase;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.common.exception.BusinessException;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies Firebase Authentication ID tokens according to Firebase's documented
 * RS256/public-certificate flow. The phone number is read only from the signed token.
 */
@Service
public class FirebasePhoneIdTokenVerifier implements FirebasePhoneTokenVerifier {
    private static final URI FIREBASE_CERTIFICATES = URI.create(
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
    );
    private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final boolean enabled;
    private final String projectId;
    private final long maxAuthenticationAgeSeconds;

    private volatile CachedKeys cachedKeys;

    public FirebasePhoneIdTokenVerifier(
            ObjectMapper mapper,
            @Value("${app.auth.firebase.phone-enabled:true}") boolean enabled,
            @Value("${app.auth.firebase.project-id:}") String projectId,
            @Value("${app.auth.firebase.max-auth-age-seconds:600}") long maxAuthenticationAgeSeconds
    ) {
        this.mapper = mapper;
        this.enabled = enabled;
        this.projectId = projectId == null ? "" : projectId.trim();
        this.maxAuthenticationAgeSeconds = maxAuthenticationAgeSeconds;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public FirebasePhoneIdentity verify(String idToken) {
        ensureConfigured();
        try {
            SignedJWT jwt = SignedJWT.parse(idToken);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw invalidToken();
            }

            String keyId = jwt.getHeader().getKeyID();
            if (keyId == null || keyId.isBlank()) {
                throw invalidToken();
            }
            PublicKey publicKey = publicKey(keyId);
            if (!(publicKey instanceof RSAPublicKey rsaKey)
                    || !jwt.verify(new RSASSAVerifier(rsaKey))) {
                throw invalidToken();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = Instant.now();
            validateStandardClaims(claims, now);

            String uid = claims.getSubject();
            String phoneNumber = stringClaim(claims, "phone_number");
            Map<String, Object> firebase = claims.getJSONObjectClaim("firebase");
            String provider = firebase == null ? null : value(firebase.get("sign_in_provider"));
            Instant authenticatedAt = epochSeconds(claims.getClaim("auth_time"));

            if (uid == null || uid.isBlank() || uid.length() > 128
                    || phoneNumber == null || phoneNumber.isBlank()
                    || !"phone".equals(provider)
                    || authenticatedAt == null
                    || authenticatedAt.isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))
                    || authenticatedAt.isBefore(now.minusSeconds(maxAuthenticationAgeSeconds))) {
                throw invalidToken();
            }

            return new FirebasePhoneIdentity(uid, phoneNumber, authenticatedAt);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidToken();
        }
    }

    private void validateStandardClaims(JWTClaimsSet claims, Instant now) {
        Date expiresAt = claims.getExpirationTime();
        Date issuedAt = claims.getIssueTime();
        List<String> audience = claims.getAudience();
        String expectedIssuer = "https://securetoken.google.com/" + projectId;

        if (expiresAt == null || !expiresAt.toInstant().isAfter(now)
                || issuedAt == null || issuedAt.toInstant().isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))
                || audience == null || audience.size() != 1 || !projectId.equals(audience.getFirst())
                || !expectedIssuer.equals(claims.getIssuer())) {
            throw invalidToken();
        }
    }

    private PublicKey publicKey(String keyId) {
        CachedKeys snapshot = cachedKeys;
        Instant now = Instant.now();
        if (snapshot != null && snapshot.expiresAt().isAfter(now)) {
            PublicKey key = snapshot.keys().get(keyId);
            if (key != null) {
                return key;
            }
        }
        return refreshAndFind(keyId);
    }

    private synchronized PublicKey refreshAndFind(String keyId) {
        CachedKeys snapshot = cachedKeys;
        Instant now = Instant.now();
        if (snapshot != null && snapshot.expiresAt().isAfter(now)) {
            PublicKey existing = snapshot.keys().get(keyId);
            if (existing != null) {
                return existing;
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(FIREBASE_CERTIFICATES)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable();
            }

            JsonNode root = mapper.readTree(response.body());
            Map<String, PublicKey> keys = new HashMap<>();
            CertificateFactory certificates = CertificateFactory.getInstance("X.509");
            for (Map.Entry<String, JsonNode> entry : root.properties()) {
                byte[] pem = entry.getValue().asText().getBytes(StandardCharsets.US_ASCII);
                X509Certificate certificate = (X509Certificate) certificates.generateCertificate(
                        new ByteArrayInputStream(pem)
                );
                certificate.checkValidity();
                if (certificate.getPublicKey() instanceof RSAPublicKey) {
                    keys.put(entry.getKey(), certificate.getPublicKey());
                }
            }
            if (keys.isEmpty()) {
                throw unavailable();
            }

            long maxAge = cacheMaxAge(response.headers().firstValue("Cache-Control").orElse(""));
            cachedKeys = new CachedKeys(Map.copyOf(keys), now.plusSeconds(maxAge));
            PublicKey key = keys.get(keyId);
            if (key == null) {
                throw invalidToken();
            }
            return key;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private long cacheMaxAge(String cacheControl) {
        Matcher matcher = MAX_AGE.matcher(cacheControl);
        if (!matcher.find()) {
            return 3600;
        }
        try {
            return Math.clamp(Long.parseLong(matcher.group(1)), 60, 86_400);
        } catch (NumberFormatException ignored) {
            return 3600;
        }
    }

    private String stringClaim(JWTClaimsSet claims, String name) {
        return value(claims.getClaim(name));
    }

    private String value(Object value) {
        return value instanceof String text ? text : null;
    }

    private Instant epochSeconds(Object value) {
        return value instanceof Number number ? Instant.ofEpochSecond(number.longValue()) : null;
    }

    private void ensureConfigured() {
        if (!enabled || projectId.isBlank() || maxAuthenticationAgeSeconds <= 0) {
            throw new BusinessException(
                    "PHONE_AUTH_NOT_CONFIGURED",
                    "Đăng nhập bằng số điện thoại chưa được cấu hình",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(
                "FIREBASE_PHONE_TOKEN_INVALID",
                "Phiên xác minh số điện thoại không hợp lệ hoặc đã hết hạn",
                HttpStatus.UNAUTHORIZED
        );
    }

    private BusinessException unavailable() {
        return new BusinessException(
                "PHONE_AUTH_UNAVAILABLE",
                "Không thể xác minh số điện thoại lúc này. Vui lòng thử lại.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private record CachedKeys(Map<String, PublicKey> keys, Instant expiresAt) {
    }
}
