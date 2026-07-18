package com.chaitin.niuniuwiki.security;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 封装安全认证相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-05-31
 */
@Component
public class JwtService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {};

    private final NiuniuWikiProperties properties;
    private final ObjectMapper objectMapper;

    public JwtService(NiuniuWikiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String create(String userId) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("id", userId);
            claims.put("exp", Instant.now().getEpochSecond() + properties.getAuth().getTokenTtlSeconds());
            String payload = encode(objectMapper.writeValueAsBytes(claims));
            String unsigned = header + "." + payload;
            return unsigned + "." + encode(sign(unsigned));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to create JWT", exception);
        }
    }

    public String verifyAndGetUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw unauthorized();
            }
            byte[] actual = DECODER.decode(parts[2]);
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw unauthorized();
            }
            Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), CLAIMS_TYPE);
            long expiresAt = ((Number) claims.getOrDefault("exp", 0)).longValue();
            if (expiresAt <= Instant.now().getEpochSecond()) {
                throw unauthorized();
            }
            Object id = claims.get("id");
            if (!(id instanceof String userId) || userId.isBlank()) {
                throw unauthorized();
            }
            return userId;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    private byte[] sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                properties.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
}
