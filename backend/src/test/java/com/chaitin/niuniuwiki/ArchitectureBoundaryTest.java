package com.chaitin.niuniuwiki;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 固化轻量模块边界，避免控制器重新承载存储和模型业务，或知识编译反向依赖 Agent 编排。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
class ArchitectureBoundaryTest {

    private static final Path MAIN_SOURCE = Path.of("src/main/java/com/chaitin/niuniuwiki");

    @Test
    void controllersDoNotAccessPersistenceOrModelGatewaysDirectly() throws IOException {
        List<Path> violations;
        try (var files = Files.walk(MAIN_SOURCE)) {
            violations = files.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .filter(path -> contains(path, "persistence.JdbcMaps") || contains(path, "model.ModelGateway"))
                    .toList();
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void compilerDoesNotDependOnAgentOrchestration() throws IOException {
        Path compiler = MAIN_SOURCE.resolve("compiler");
        List<Path> violations;
        try (var files = Files.walk(compiler)) {
            violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "com.chaitin.niuniuwiki.agentic"))
                    .toList();
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void jdbcTemplateIsWrappedByThePersistenceAdapter() throws IOException {
        List<Path> violations;
        try (var files = Files.walk(MAIN_SOURCE)) {
            violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "org.springframework.jdbc.core.JdbcTemplate"))
                    .filter(path -> !path.endsWith(Path.of("persistence/JdbcMaps.java")))
                    .toList();
        }

        assertThat(violations).isEmpty();
    }

    private static boolean contains(Path path, String value) {
        try {
            return Files.readString(path).contains(value);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
