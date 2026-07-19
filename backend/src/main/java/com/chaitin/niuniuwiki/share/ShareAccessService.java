package com.chaitin.niuniuwiki.share;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import com.chaitin.niuniuwiki.security.KnowledgeAccessScope;
import org.springframework.http.HttpStatus;
import com.chaitin.niuniuwiki.persistence.JdbcMaps;
import org.springframework.stereotype.Service;

/**
 * 封装公开访问相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-05-25
 */
@Service
public class ShareAccessService {

    private final JdbcMaps store;
    private final JsonMaps jsonMaps;

    public ShareAccessService(JdbcMaps store, JsonMaps jsonMaps) {
        this.store = store;
        this.jsonMaps = jsonMaps;
    }

    public Map<String, Object> settings(String kbId) {
        Map<String, Object> kb = store.queryForObject(
                "SELECT access_settings FROM knowledge_bases WHERE id = ?",
                store.rowMapper(), kbId);
        Map<String, Object> settings = jsonMaps.jsonMap(kb.get("access_settings"));
        if (Boolean.TRUE.equals(settings.get("is_forbidden"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "access is forbidden");
        }
        return settings;
    }

    public void authorize(String kbId, HttpSession session) {
        Map<String, Object> settings = settings(kbId);
        Map<String, Object> simple = jsonMaps.jsonMap(settings.get("simple_auth"));
        Map<String, Object> enterprise = jsonMaps.jsonMap(settings.get("enterprise_auth"));
        boolean enabled = Boolean.TRUE.equals(simple.get("enabled")) || Boolean.TRUE.equals(enterprise.get("enabled"));
        if (!enabled) {
            return;
        }
        if (!kbId.equals(session.getAttribute("kb_id"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (Boolean.TRUE.equals(enterprise.get("enabled")) && session.getAttribute("user_id") == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
    }

    public KnowledgeAccessScope scope(String kbId, HttpSession session) {
        authorize(kbId, session);
        Object sessionUserId = session.getAttribute("user_id");
        if (!(sessionUserId instanceof Number number)) {
            return KnowledgeAccessScope.publicAccess();
        }
        long authId = number.longValue();
        List<Integer> groupIds = store.query("""
                SELECT id FROM auth_groups
                 WHERE kb_id = ? AND ? = ANY(auth_ids)
                 ORDER BY id
                """, (row, rowNumber) -> row.getInt(1), kbId, authId);
        return new KnowledgeAccessScope(String.valueOf(authId), groupIds);
    }
}
