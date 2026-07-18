package com.chaitin.niuniuwiki.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaptchaServiceTest {

    @Test
    void acceptsCapCompatibleProofAndMakesTokenOneTime() throws Exception {
        CaptchaService service = new CaptchaService();
        Map<String, Object> challenge = service.challenge();
        String token = String.valueOf(challenge.get("token"));
        List<Long> solutions = new ArrayList<>();
        for (int index = 1; index <= 50; index++) {
            String seed = token + index;
            String target = prng(seed + "d", 3);
            String salt = prng(seed, 32);
            long solution = 0;
            while (!sha256(salt + solution).startsWith(target)) {
                solution++;
            }
            solutions.add(solution);
        }

        Map<String, Object> result = service.redeem(token, solutions);

        assertThat(result.get("success")).isEqualTo(true);
        String verificationToken = String.valueOf(result.get("token"));
        service.verify(verificationToken);
        assertThatThrownBy(() -> service.verify(verificationToken)).hasMessageContaining("captcha token");
    }

    private static String prng(String seed, int length) {
        int state = 0x811c9dc5;
        for (byte item : seed.getBytes(StandardCharsets.UTF_8)) {
            state ^= item & 0xff;
            state *= 0x01000193;
        }
        StringBuilder result = new StringBuilder();
        while (result.length() < length) {
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            result.append(String.format("%08x", state));
        }
        return result.substring(0, length);
    }

    private static String sha256(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : bytes) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }
}
