package com.chaitin.niuniuwiki.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Translates environment variables accepted by the former Go binaries.
 *
 * @author 程序员牛肉
 * @since 2026-07-16
*/
public class LegacyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Pattern DSN_ITEM = Pattern.compile("([A-Za-z_]+)=('([^']*)'|\\S+)");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, String> env = System.getenv();

        if (blank(env.get("SPRING_DATASOURCE_URL")) && !blank(env.get("PG_DSN"))) {
            Map<String, String> dsn = parseDsn(env.get("PG_DSN"));
            String host = dsn.getOrDefault("host", "niuniu-wiki-postgres");
            String port = dsn.getOrDefault("port", "5432");
            String database = dsn.getOrDefault("dbname", "niuniu-wiki");
            String sslMode = dsn.getOrDefault("sslmode", "disable");
            properties.put("spring.datasource.url",
                    "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode=" + sslMode);
            put(properties, "spring.datasource.username", dsn.get("user"));
            put(properties, "spring.datasource.password", dsn.get("password"));
        }

        if (!blank(env.get("REDIS_ADDR")) && blank(env.get("REDIS_HOST"))) {
            String[] address = env.get("REDIS_ADDR").split(":", 2);
            properties.put("spring.data.redis.host", address[0]);
            if (address.length == 2) {
                properties.put("spring.data.redis.port", address[1]);
            }
        }

        String subnet = env.get("SUBNET_PREFIX");
        if (!blank(subnet)) {
            if (blank(env.get("RAG_CT_RAG_BASE_URL"))) {
                properties.put("niuniu-wiki.rag.base-url", "http://" + subnet + ".18:5050");
            }
            if (blank(env.get("MQ_NATS_SERVER"))) {
                properties.put("niuniu-wiki.messaging.nats-url", "nats://" + subnet + ".13:4222");
            }
        }

        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("niuniuWikiLegacyEnvironment", properties));
        }
    }

    private static Map<String, String> parseDsn(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = DSN_ITEM.matcher(value);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(3) == null ? matcher.group(2) : matcher.group(3));
        }
        return result;
    }

    private static void put(Map<String, Object> target, String key, String value) {
        if (!blank(value)) {
            target.put(key, value);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
