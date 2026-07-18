package com.chaitin.niuniuwiki.captcha;

import com.chaitin.niuniuwiki.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 封装人机验证相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-06-12
 */
@Service
public class CaptchaService {

    private static final long CHALLENGE_TTL_SECONDS = 120;
    private static final long TOKEN_TTL_SECONDS = 300;
    private static final int CHALLENGE_TOKEN_SIZE = 25;
    private static final int CHALLENGE_COUNT = 50;
    private static final int CHALLENGE_SIZE = 32;
    private static final int CHALLENGE_DIFFICULTY = 3;
    private static final int TOKEN_SIZE = 15;
    private static final int TOKEN_ID_SIZE = 8;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Long> challenges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> verified = new ConcurrentHashMap<>();

    public Map<String, Object> challenge() {
        cleanup();
        String token = token();
        long expires = Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS).toEpochMilli();
        challenges.put(token, expires);
        return Map.of(
                "token", token,
                "expires", expires,
                "challenge", Map.of("c", CHALLENGE_COUNT, "s", CHALLENGE_SIZE, "d", CHALLENGE_DIFFICULTY));
    }

    public Map<String, Object> redeem(String challengeToken, Object solutions) {
        cleanup();
        if (challengeToken == null || challengeToken.length() != CHALLENGE_TOKEN_SIZE) {
            return Map.of("success", false, "message", "invalid challenge body");
        }
        Long expires = challenges.remove(challengeToken);
        if (expires == null || expires < Instant.now().toEpochMilli()) {
            return Map.of("success", false, "message", "challenge expired");
        }
        if (!(solutions instanceof List<?> list) || list.size() < CHALLENGE_COUNT) {
            return Map.of("success", false, "message", "invalid solutions");
        }
        for (int index = 0; index < CHALLENGE_COUNT; index++) {
            long solution;
            try {
                Object item = list.get(index);
                solution = item instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(item));
            } catch (RuntimeException exception) {
                return Map.of("success", false, "message", "invalid solutions");
            }
            String seed = challengeToken + (index + 1);
            String target = prng(seed + "d", CHALLENGE_DIFFICULTY);
            String salt = prng(seed, CHALLENGE_SIZE);
            if (!sha256(salt + solution).startsWith(target)) {
                return Map.of("success", false, "message", "invalid solutions");
            }
        }
        String verificationToken = randomHex(TOKEN_SIZE);
        String id = randomHex(TOKEN_ID_SIZE);
        String publicToken = id + ":" + verificationToken;
        String storageKey = id + ":" + sha256(verificationToken);
        long tokenExpires = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).toEpochMilli();
        verified.put(storageKey, tokenExpires);
        return Map.of("success", true, "token", publicToken, "expires", tokenExpires);
    }

    public void verify(String token) {
        cleanup();
        if (token == null) {
            throw new ApiException("failed to validate captcha token");
        }
        String[] parts = token.split(":", -1);
        if (parts.length != 2) {
            throw new ApiException("failed to validate captcha token");
        }
        Long expires = verified.remove(parts[0] + ":" + sha256(parts[1]));
        if (expires == null || expires < Instant.now().toEpochMilli()) {
            throw new ApiException("failed to validate captcha token");
        }
    }

    private String token() {
        return randomHex(CHALLENGE_TOKEN_SIZE);
    }

    private String randomHex(int length) {
        byte[] bytes = new byte[(length + 1) / 2];
        random.nextBytes(bytes);
        return hex(bytes).substring(0, length);
    }

    private static String prng(String seed, int length) {
        int state = fnv1a(seed);
        StringBuilder result = new StringBuilder(length + 8);
        while (result.length() < length) {
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            result.append(String.format("%08x", state));
        }
        return result.substring(0, length);
    }

    private static int fnv1a(String value) {
        int hash = 0x811c9dc5;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= item & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static String sha256(String value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }

    private void cleanup() {
        long now = Instant.now().toEpochMilli();
        challenges.entrySet().removeIf(entry -> entry.getValue() < now);
        verified.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
