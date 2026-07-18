package com.chaitin.niuniuwiki.stat;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthService;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;

/**
 * 封装访问统计相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-06-21
 */
@Service
public class StatService {

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public StatService(MyBatisStore store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public void record(
            String kbId,
            int scene,
            String nodeId,
            String sessionId,
            String ip,
            String userAgent,
            String referer
    ) {
        String refererHost = "";
        try {
            refererHost = referer == null || referer.isBlank() ? "" : URI.create(referer).getHost();
        } catch (IllegalArgumentException ignored) {
        }
        store.update(
                "INSERT INTO stat_pages(kb_id, node_id, user_id, session_id, scene, ip, ua, browser_name, "
                        + "browser_os, referer, referer_host, created_at) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                kbId, value(nodeId), sessionId, scene, ip, value(userAgent), browser(userAgent), os(userAgent),
                value(referer), value(refererHost));
    }

    public Map<String, Object> count(String kbId, int day) {
        require(kbId);
        validateDay(day);
        Map<String, Object> raw = store.queryForMap(
                "SELECT count(DISTINCT ip) AS ip_count, count(DISTINCT session_id) AS session_count, "
                        + "count(*) AS page_visit_count FROM stat_pages WHERE kb_id = ?",
                kbId);
        long conversations = store.queryForObject(
                "SELECT count(*) FROM conversations WHERE kb_id = ? AND created_at > now() - interval '24 hours'",
                Long.class, kbId);
        long ipCount = number(raw.get("ip_count"));
        long sessionCount = number(raw.get("session_count"));
        long pageCount = number(raw.get("page_visit_count"));
        if (day > 1) {
            Map<String, Object> hourly = store.queryForMap(
                    "SELECT COALESCE(sum(ip_count), 0) AS ip_count, COALESCE(sum(session_count), 0) AS session_count, "
                            + "COALESCE(sum(page_visit_count), 0) AS page_visit_count, "
                            + "COALESCE(sum(conversation_count), 0) AS conversation_count "
                            + "FROM stat_page_hours WHERE kb_id = ? AND hour >= now() - (? * interval '1 day')",
                    kbId, day);
            ipCount += number(hourly.get("ip_count"));
            sessionCount += number(hourly.get("session_count"));
            pageCount += number(hourly.get("page_visit_count"));
            conversations += number(hourly.get("conversation_count"));
        }
        return Map.of(
                "ip_count", ipCount,
                "session_count", sessionCount,
                "page_visit_count", pageCount,
                "conversation_count", conversations);
    }

    public List<Map<String, Object>> instantCount(String kbId) {
        require(kbId);
        return store.query(
                "SELECT date_trunc('minute', created_at) AS time, count(*) AS count FROM stat_pages "
                        + "WHERE kb_id = ? AND created_at >= now() - interval '1 hour' GROUP BY time ORDER BY time",
                store.rowMapper(), kbId);
    }

    public List<Map<String, Object>> instantPages(String kbId) {
        require(kbId);
        List<Map<String, Object>> pages = store.query("""
                SELECT s.node_id, CASE s.scene WHEN 1 THEN '欢迎页' WHEN 3 THEN '问答页'
                       WHEN 4 THEN '登录页' ELSE COALESCE(n.name, '未知') END AS node_name,
                       s.ip, s.scene, s.created_at, s.user_id
                  FROM stat_pages s LEFT JOIN nodes n ON s.node_id = n.id
                 WHERE s.kb_id = ? ORDER BY s.created_at DESC LIMIT 10
                """, store.rowMapper(), kbId);
        pages.forEach(page -> page.put("ip_address", Map.of(
                "ip", page.getOrDefault("ip", ""), "country", "未知", "province", "未知", "city", "未知")));
        return pages;
    }

    public List<Map<String, Object>> hotPages(String kbId, int day) {
        require(kbId);
        validateDay(day);
        Map<String, Long> counts = new HashMap<>();
        store.query(
                "SELECT node_id, count(*) AS count FROM stat_pages WHERE kb_id = ? AND node_id <> '' AND scene = 2 "
                        + "GROUP BY node_id",
                store.rowMapper(), kbId).forEach(row -> counts.merge(value(row.get("node_id")), number(row.get("count")), Long::sum));
        if (day > 1) {
            mergeHourly(kbId, day, "hot_page", counts);
        }
        List<Map<String, Object>> pages = new ArrayList<>();
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(10)
                .forEach(entry -> {
                    String name = store.query(
                            "SELECT name FROM nodes WHERE id = ?",
                            (rs, rowNum) -> rs.getString(1), entry.getKey()).stream().findFirst().orElse("");
                    pages.add(Map.of("node_id", entry.getKey(), "node_name", name, "count", entry.getValue()));
                });
        return pages;
    }

    public List<Map<String, Object>> refererHosts(String kbId, int day) {
        require(kbId);
        validateDay(day);
        Map<String, Long> counts = new HashMap<>();
        store.query(
                "SELECT referer_host, count(*) AS count FROM stat_pages WHERE kb_id = ? AND referer_host <> '' "
                        + "GROUP BY referer_host",
                store.rowMapper(), kbId).forEach(row -> counts.merge(value(row.get("referer_host")), number(row.get("count")), Long::sum));
        if (day > 1) {
            mergeHourly(kbId, day, "hot_referer_host", counts);
        }
        return topCounts(counts, "referer_host");
    }

    public Map<String, Object> browsers(String kbId, int day) {
        require(kbId);
        validateDay(day);
        Map<String, Long> browsers = grouped(kbId, "browser_name");
        Map<String, Long> systems = grouped(kbId, "browser_os");
        if (day > 1) {
            mergeHourly(kbId, day, "hot_browser", browsers);
            mergeHourly(kbId, day, "hot_os", systems);
        }
        return Map.of("browser", topCounts(browsers, "name"), "os", topCounts(systems, "name"));
    }

    public Map<String, Long> geo(String kbId, int day) {
        require(kbId);
        validateDay(day);
        Map<String, Long> counts = new HashMap<>();
        mergeHourly(kbId, day, "geo_count", counts);
        return counts;
    }

    public List<Map<String, Object>> conversationDistribution(String kbId, int day) {
        require(kbId);
        validateDay(day);
        Map<String, Long> counts = new HashMap<>();
        store.query("""
                SELECT a.type AS app_type, count(*) AS count FROM conversations c
                  JOIN apps a ON c.app_id = a.id
                 WHERE c.kb_id = ? AND c.created_at > now() - interval '24 hours'
                 GROUP BY a.type
                """, store.rowMapper(), kbId)
                .forEach(row -> counts.merge(value(row.get("app_type")), number(row.get("count")), Long::sum));
        if (day > 1) {
            mergeHourly(kbId, day, "conversation_distribution", counts);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        counts.forEach((type, count) -> result.add(Map.of("app_type", Integer.parseInt(type), "count", count)));
        return result;
    }

    private Map<String, Long> grouped(String kbId, String column) {
        Map<String, Long> counts = new HashMap<>();
        store.query(
                "SELECT " + column + " AS name, count(*) AS count FROM stat_pages WHERE kb_id = ? AND "
                        + column + " <> '' GROUP BY " + column,
                store.rowMapper(), kbId)
                .forEach(row -> counts.put(value(row.get("name")), number(row.get("count"))));
        return counts;
    }

    private void mergeHourly(String kbId, int day, String column, Map<String, Long> target) {
        List<Map<String, Object>> rows = store.query(
                "SELECT " + column + " AS value FROM stat_page_hours "
                        + "WHERE kb_id = ? AND hour >= now() - (? * interval '1 day')",
                store.rowMapper(), kbId, day);
        for (Map<String, Object> row : rows) {
            jsonMaps.jsonMap(row.get("value")).forEach((key, count) -> target.merge(key, number(count), Long::sum));
        }
    }

    private List<Map<String, Object>> topCounts(Map<String, Long> counts, String key) {
        List<Map<String, Object>> result = new ArrayList<>();
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(10)
                .forEach(entry -> result.add(Map.of(key, entry.getKey(), "count", entry.getValue())));
        return result;
    }

    private void require(String kbId) {
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
    }

    private void validateDay(int day) {
        if (day != 1 && day != 7 && day != 30 && day != 90) {
            throw new com.chaitin.niuniuwiki.common.ApiException("invalid stat day");
        }
    }

    private static long number(Object value) {
        return value == null ? 0 : value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String browser(String agent) {
        String value = value(agent);
        if (value.contains("Edg/")) return "Edge";
        if (value.contains("Chrome/")) return "Chrome";
        if (value.contains("Firefox/")) return "Firefox";
        if (value.contains("Safari/")) return "Safari";
        return "Unknown";
    }

    private static String os(String agent) {
        String value = value(agent);
        if (value.contains("Windows")) return "Windows";
        if (value.contains("Mac OS")) return "macOS";
        if (value.contains("Android")) return "Android";
        if (value.contains("iPhone") || value.contains("iPad")) return "iOS";
        if (value.contains("Linux")) return "Linux";
        return "Unknown";
    }
}
