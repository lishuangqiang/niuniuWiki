package com.chaitin.niuniuwiki.share;

import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 基于当前已发布知识版本生成公开站点地图。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@Service
public class SitemapService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;

    public SitemapService(JdbcMaps store, JsonMaps jsonMaps) {
        this.store = store;
        this.jsonMaps = jsonMaps;
    }

    public String sitemap(String kbId) {
        Map<String, Object> knowledgeBase = store.queryForObject(
                "SELECT access_settings FROM knowledge_bases WHERE id = ?", store.rowMapper(), kbId);
        Map<String, Object> access = jsonMaps.jsonMap(knowledgeBase.get("access_settings"));
        String baseUrl = value(access.get("base_url")).replaceAll("/+$", "");
        List<Map<String, Object>> nodes = store.query("""
                SELECT nr.node_id, nr.updated_at
                  FROM kb_releases kr
                  JOIN kb_release_node_releases link ON link.release_id = kr.id
                  JOIN node_releases nr ON nr.id = link.node_release_id
                 WHERE kr.id = (SELECT id FROM kb_releases WHERE kb_id = ? ORDER BY created_at DESC LIMIT 1)
                   AND nr.type = 2
                 ORDER BY nr.position
                """, store.rowMapper(), kbId);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")
                .append(entry(baseUrl + "/welcome", LocalDate.now().toString()));
        for (Map<String, Object> node : nodes) {
            String date = node.get("updated_at") instanceof java.time.OffsetDateTime updated
                    ? updated.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().toString()
                    : LocalDate.now().toString();
            xml.append(entry(baseUrl + "/node/" + node.get("node_id"), date));
        }
        return xml.append("</urlset>").toString();
    }

    private static String entry(String location, String lastModified) {
        return "<url><loc>" + xml(location) + "</loc><lastmod>" + xml(lastModified) + "</lastmod></url>";
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
