package com.chaitin.niuniuwiki;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApplicationContextTest {

    private static final EmbeddedPostgres POSTGRES = postgres();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @AfterAll
    static void closePostgres() throws IOException {
        POSTGRES.close();
    }

    @Test
    void startsAndMapsEveryLegacySwaggerOperation() throws Exception {
        Map<String, Object> swagger = new ObjectMapper().readValue(Path.of("openapi/swagger.json").toFile(), MAP);
        Set<Route> expected = swaggerRoutes(swagger);
        Set<Route> actual = springRoutes();

        assertThat(expected).hasSize(100);
        assertThat(actual).containsAll(expected);
    }

    @SuppressWarnings("unchecked")
    private Set<Route> swaggerRoutes(Map<String, Object> swagger) {
        Set<Route> result = new HashSet<>();
        Map<String, Object> paths = (Map<String, Object>) swagger.get("paths");
        paths.forEach((path, operationsValue) -> {
            Map<String, Object> operations = (Map<String, Object>) operationsValue;
            operations.keySet().stream()
                    .map(String::toUpperCase)
                    .filter(method -> Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method))
                    .forEach(method -> result.add(new Route(method, path)));
        });
        return result;
    }

    private Set<Route> springRoutes() {
        Set<Route> result = new HashSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (var pattern : info.getPathPatternsCondition().getPatterns()) {
                for (var method : info.getMethodsCondition().getMethods()) {
                    result.add(new Route(method.name(), pattern.getPatternString()));
                }
            }
        }
        return result;
    }

    private static EmbeddedPostgres postgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Route(String method, String path) {
    }
}
