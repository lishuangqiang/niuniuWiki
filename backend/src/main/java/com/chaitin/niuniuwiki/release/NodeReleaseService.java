package com.chaitin.niuniuwiki.release;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.security.AuthService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;

/**
 * 查询文档每次发布时保存的不可变快照，用于历史对比和回滚。
 *
 * @author 程序员牛肉
 * @since 2026-05-15
 */
@Service
public class NodeReleaseService {

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;

    public NodeReleaseService(MyBatisStore store, JsonMaps jsonMaps, AuthService authService) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
    }

    public List<Map<String, Object>> list(String kbId, String nodeId) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        return store.query("""
                SELECT nr.id, nr.node_id, nr.name, nr.meta, nr.publisher_id, nr.editor_id,
                       kr.id AS release_id, kr.tag AS release_name, kr.message AS release_message,
                       COALESCE(n.creator_id, '') AS creator_id,
                       creator.account AS creator_account, editor.account AS editor_account,
                       publisher.account AS publisher_account,
                       COALESCE(nr.updated_at, nr.created_at, kr.created_at) AS updated_at
                  FROM kb_release_node_releases link
                  JOIN node_releases nr ON nr.id = link.node_release_id
                  JOIN kb_releases kr ON kr.id = link.release_id
                  LEFT JOIN nodes n ON n.id = nr.node_id
                  LEFT JOIN users creator ON creator.id = n.creator_id
                  LEFT JOIN users editor ON editor.id = nr.editor_id
                  LEFT JOIN users publisher ON publisher.id = nr.publisher_id
                 WHERE link.kb_id = ? AND link.node_id = ?
                 ORDER BY kr.created_at DESC, nr.updated_at DESC
                """, store.rowMapper(), kbId, nodeId);
    }

    public Map<String, Object> detail(String kbId, String id) {
        authService.requireKbPermission(kbId, AuthService.DOC_MANAGE);
        List<Map<String, Object>> rows = store.query("""
                SELECT nr.node_id, nr.name, nr.content, nr.meta, nr.publisher_id, nr.editor_id,
                       COALESCE(n.creator_id, '') AS creator_id,
                       creator.account AS creator_account, editor.account AS editor_account,
                       publisher.account AS publisher_account
                  FROM node_releases nr
                  LEFT JOIN nodes n ON n.id = nr.node_id
                  LEFT JOIN users creator ON creator.id = n.creator_id
                  LEFT JOIN users editor ON editor.id = nr.editor_id
                  LEFT JOIN users publisher ON publisher.id = nr.publisher_id
                 WHERE nr.kb_id = ? AND nr.id = ?
                """, store.rowMapper(), kbId, id);
        if (rows.isEmpty()) {
            throw new ApiException("文档历史版本不存在");
        }
        return new LinkedHashMap<>(rows.getFirst());
    }
}
