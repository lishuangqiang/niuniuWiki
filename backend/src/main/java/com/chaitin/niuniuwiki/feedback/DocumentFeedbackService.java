package com.chaitin.niuniuwiki.feedback;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import com.chaitin.niuniuwiki.file.FileService;
import com.chaitin.niuniuwiki.security.AuthService;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 记录访客提交的文档纠错反馈，并提供管理端分页和批量删除能力。
 *
 * @author 程序员牛肉
 * @since 2026-04-12
 */
@Service
public class DocumentFeedbackService {

    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final AuthService authService;
    private final FileService fileService;

    public DocumentFeedbackService(
            MyBatisStore store,
            JsonMaps jsonMaps,
            AuthService authService,
            FileService fileService
    ) {
        this.store = store;
        this.jsonMaps = jsonMaps;
        this.authService = authService;
        this.fileService = fileService;
    }

    public void create(
            String kbId,
            String nodeId,
            String content,
            String correctionSuggestion,
            MultipartFile image,
            String remoteIp,
            HttpSession session
    ) {
        Integer exists = store.queryForObject(
                "SELECT count(*) FROM nodes WHERE kb_id = ? AND id = ?", Integer.class, kbId, nodeId);
        if (exists == null || exists == 0) {
            throw new ApiException("反馈文档不存在");
        }
        if ((content == null || content.isBlank())
                && (correctionSuggestion == null || correctionSuggestion.isBlank())) {
            throw new ApiException("反馈内容不能为空");
        }
        String screenshot = "";
        if (image != null && !image.isEmpty()) {
            if (!fileService.isImage(image.getOriginalFilename())) {
                throw new ApiException("反馈截图必须是图片文件");
            }
            screenshot = fileService.upload(kbId, image);
        }
        Map<String, Object> info = new LinkedHashMap<>();
        Object authId = session.getAttribute("user_id");
        info.put("auth_user_id", authId == null ? 0 : authId);
        info.put("user_name", "");
        info.put("email", "");
        info.put("avatar", "");
        info.put("remote_ip", value(remoteIp));
        info.put("screen_shot", screenshot);
        store.update(
                "INSERT INTO document_feedbacks(user_id, kb_id, node_id, content, correction_suggestion, info, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, now())",
                authId == null ? null : String.valueOf(authId), kbId, nodeId,
                value(content), value(correctionSuggestion), jsonMaps.json(info));
    }

    public Map<String, Object> list(String kbId, int page, int perPage) {
        authService.requireKbPermission(kbId, AuthService.DATA_OPERATE);
        long total = store.queryForObject(
                "SELECT count(*) FROM document_feedbacks WHERE kb_id = ?", Long.class, kbId);
        List<Map<String, Object>> rows = store.query(
                "SELECT f.id::text AS id, f.user_id, f.kb_id, f.node_id, f.content, "
                        + "f.correction_suggestion, f.info, f.created_at, n.name AS node_name "
                        + "FROM document_feedbacks f LEFT JOIN nodes n ON n.id = f.node_id "
                        + "WHERE f.kb_id = ? ORDER BY f.created_at DESC OFFSET ? LIMIT ?",
                store.rowMapper(), kbId, Math.max(0, page - 1) * perPage, Math.min(100, perPage));
        rows.forEach(row -> {
            Map<String, Object> info = jsonMaps.jsonMap(row.get("info"));
            row.put("info", info);
            row.put("ip_address", Map.of(
                    "ip", info.getOrDefault("remote_ip", ""),
                    "country", "", "province", "", "city", ""));
        });
        return Map.of("data", rows, "total", total);
    }

    public void delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException("反馈 ID 不能为空");
        }
        List<Long> numericIds = new ArrayList<>();
        try {
            ids.forEach(id -> numericIds.add(Long.parseLong(id)));
        } catch (NumberFormatException exception) {
            throw new ApiException("反馈 ID 格式无效");
        }
        List<String> kbIds = store.query(
                "SELECT DISTINCT kb_id FROM document_feedbacks WHERE id = ANY (?::bigint[])",
                (rs, rowNum) -> rs.getString(1), (Object) numericIds.toArray(Long[]::new));
        if (kbIds.isEmpty()) {
            throw new ApiException("反馈不存在");
        }
        kbIds.forEach(kbId -> authService.requireKbPermission(kbId, AuthService.DATA_OPERATE));
        store.update("DELETE FROM document_feedbacks WHERE id = ANY (?::bigint[])",
                (Object) numericIds.toArray(Long[]::new));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
