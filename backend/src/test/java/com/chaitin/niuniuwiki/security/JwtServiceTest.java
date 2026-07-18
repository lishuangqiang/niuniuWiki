package com.chaitin.niuniuwiki.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void createsAndVerifiesGoCompatibleHs256Token() {
        NiuniuWikiProperties properties = new NiuniuWikiProperties();
        properties.getAuth().setJwtSecret("test-secret-with-enough-entropy");
        JwtService service = new JwtService(properties, new ObjectMapper());

        String token = service.create("user-123");

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(service.verifyAndGetUserId(token)).isEqualTo("user-123");
    }

    @Test
    void rejectsTamperedToken() {
        NiuniuWikiProperties properties = new NiuniuWikiProperties();
        properties.getAuth().setJwtSecret("test-secret-with-enough-entropy");
        JwtService service = new JwtService(properties, new ObjectMapper());
        String token = service.create("user-123");

        assertThatThrownBy(() -> service.verifyAndGetUserId(token + "x"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Unauthorized");
    }
}
