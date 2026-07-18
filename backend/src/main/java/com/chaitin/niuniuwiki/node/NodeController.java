package com.chaitin.niuniuwiki.node;

import com.chaitin.niuniuwiki.common.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 处理文档节点相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-04-13
 */
@RestController
@RequestMapping("/api/v1/node")
public class NodeController {

    private final NodeService service;
    private final ObjectMapper objectMapper;

    public NodeController(NodeService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/list")
    public ApiResponse<?> list(
            @RequestParam("kb_id") String kbId,
            @RequestParam(name = "nav_id", required = false) String navId,
            @RequestParam(required = false) String search
    ) {
        return ApiResponse.ok(service.list(kbId, navId, search));
    }

    @GetMapping("/list/group/nav")
    public ApiResponse<?> listGrouped(
            @RequestParam("kb_id") String kbId,
            @RequestParam(name = "nav_ids[]", required = false) List<String> navIds,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(service.listGrouped(kbId, navIds, search, status));
    }

    @GetMapping("/stats")
    public ApiResponse<?> stats(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.stats(kbId));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody NodeDtos.CreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @GetMapping("/detail")
    public ApiResponse<?> detail(
            @RequestParam("kb_id") String kbId,
            @RequestParam String id,
            @RequestParam(defaultValue = "") String format
    ) {
        return ApiResponse.ok(service.detail(kbId, id, format));
    }

    @PutMapping("/detail")
    public ApiResponse<Void> update(@Valid @RequestBody NodeDtos.UpdateRequest request) {
        service.update(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/action")
    public ApiResponse<Void> action(@Valid @RequestBody NodeDtos.ActionRequest request) {
        service.action(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/move")
    public ApiResponse<Void> move(@Valid @RequestBody NodeDtos.MoveRequest request) {
        service.move(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/batch_move")
    public ApiResponse<Void> batchMove(@Valid @RequestBody NodeDtos.BatchMoveRequest request) {
        service.batchMove(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/move/nav")
    public ApiResponse<Void> moveNav(@Valid @RequestBody NodeDtos.MoveNavRequest request) {
        service.moveNav(request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/recommend_nodes")
    public ApiResponse<?> recommend(
            @RequestParam("kb_id") String kbId,
            @RequestParam(name = "node_ids[]", required = false) List<String> nodeIds,
            @RequestParam(name = "nav_ids[]", required = false) List<String> navIds
    ) {
        return ApiResponse.ok(service.recommend(kbId, nodeIds, navIds));
    }

    @PostMapping("/restudy")
    public ApiResponse<Void> restudy(@Valid @RequestBody NodeDtos.RestudyRequest request) {
        service.restudy(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/summary")
    public ApiResponse<Void> summary(@Valid @RequestBody NodeDtos.SummaryRequest request) {
        service.summarize(request);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/summary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter summaryStream(@Valid @RequestBody NodeDtos.SummaryRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        try {
            String content = service.streamSummary(request);
            emitter.send(SseEmitter.event().data(json(Map.of("type", "text", "content", content))));
            emitter.send(SseEmitter.event().data(json(Map.of("type", "done", "content", ""))));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @GetMapping("/permission")
    public ApiResponse<?> permission(@RequestParam("kb_id") String kbId, @RequestParam String id) {
        return ApiResponse.ok(service.permission(kbId, id));
    }

    @PatchMapping("/permission/edit")
    public ApiResponse<Void> editPermission(@Valid @RequestBody NodeDtos.PermissionEditRequest request) {
        service.editPermission(request);
        return ApiResponse.ok(null);
    }

    private String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }
}
