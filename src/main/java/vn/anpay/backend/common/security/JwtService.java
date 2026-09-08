package vn.anpay.backend.common.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class JwtService {
    private final byte[] secret;
    private final long accessMinutes;
    public JwtService(@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.access-minutes}") long accessMinutes) {
        byte[] raw=secret.getBytes(StandardCharsets.UTF_8);
        this.secret=Arrays.copyOf(MessageDigestHolder.sha256(raw),32);
        this.accessMinutes=accessMinutes;

    }
    public String issueAccess(UUID userId) {
        return issue(userId,"access",Duration.ofMinutes(accessMinutes),List.of("USER"));

    }
    public String issue(UUID userId,String type,Duration ttl,List<String> roles) {
        try {
            Instant now=Instant.now();
            JWTClaimsSet claims=new JWTClaimsSet.Builder().subject(userId.toString()).claim("roles",roles).claim("token_type",type) .issueTime(Date.from(now)).expirationTime(Date.from(now.plus(ttl))).jwtID(UUID.randomUUID().toString()).build();
            SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();

        } catch(JOSEException e) {
            throw new IllegalStateException("JWT signing failed");

        }

    }
    public JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt=SignedJWT.parse(token);
            if(!jwt.verify(new MACVerifier(secret))) throw new IllegalArgumentException();
            var c=jwt.getJWTClaimsSet();
            if(c.getExpirationTime().before(new Date())) throw new IllegalArgumentException();
            return c;

        } catch(Exception e) {
            throw new IllegalArgumentException("Invalid token");

        }

    }
    static final class MessageDigestHolder {
        static byte[] sha256(byte[] in) {
            try {
                return java.security.MessageDigest.getInstance("SHA-256").digest(in);

            } catch(Exception e) {
                throw new IllegalStateException(e);

            }

        }

    }

}
