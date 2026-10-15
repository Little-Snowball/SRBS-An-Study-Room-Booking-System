package edu.campus.studyroom.security;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Issues HS256 access tokens. Claims: sub=userId, sid=auth_session id, ver=auth_version,
 * iss=studyroom, aud=studyroom-web. Roles are never trusted from the token; the
 * session filter re-reads them from the database on every request.
 */
@Service
public class JwtTokenService {

    public static final String ISSUER = "studyroom";
    public static final String AUDIENCE = "studyroom-web";
    public static final String CLAIM_SID = "sid";
    public static final String CLAIM_VER = "ver";

    private final JwtEncoder encoder;
    private final AppProperties properties;

    public JwtTokenService(AppProperties properties) {
        this.properties = properties;
        byte[] secret = decodeSecret(properties.getJwtSecretBase64());
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key).keyID("studyroom-mac").build();
        JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
        this.encoder = new NimbusJwtEncoder(jwks);
    }

    private static byte[] decodeSecret(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_BASE64 is not configured");
        }
        byte[] secret = Base64.getDecoder().decode(base64.getBytes(StandardCharsets.UTF_8));
        if (secret.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        return secret;
    }

    public String issue(Long userId, int authVersion, String sid, Instant now, int ttlHours) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(String.valueOf(userId))
                .claim(CLAIM_SID, sid)
                .claim(CLAIM_VER, authVersion)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlHours * 3600L))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String issue(Long userId, int authVersion, String sid, Instant now) {
        return issue(userId, authVersion, sid, now, properties.getSession().getAccessTokenHours());
    }

    public static Long parseUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
