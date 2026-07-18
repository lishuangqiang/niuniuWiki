package com.chaitin.niuniuwiki.release;

import com.chaitin.niuniuwiki.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供文档历史版本列表和快照详情接口。
 *
 * @author 程序员牛肉
 * @since 2026-05-20
 */
@RestController
@RequestMapping("/api/pro/v1/node/release")
public class NodeReleaseController {

    private final NodeReleaseService service;

    public NodeReleaseController(NodeReleaseService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<?> list(
            @RequestParam("kb_id") String kbId,
            @RequestParam("node_id") String nodeId
    ) {
        return ApiResponse.ok(service.list(kbId, nodeId));
    }

    @GetMapping("/detail")
    public ApiResponse<?> detail(@RequestParam("kb_id") String kbId, @RequestParam String id) {
        return ApiResponse.ok(service.detail(kbId, id));
    }
}
