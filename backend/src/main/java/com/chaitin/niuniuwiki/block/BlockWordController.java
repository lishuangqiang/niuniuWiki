package com.chaitin.niuniuwiki.block;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供知识库 AI 问答屏蔽词配置接口。
 *
 * @author 程序员牛肉
 * @since 2026-04-29
 */
@RestController
@RequestMapping("/api/pro/v1/block")
public class BlockWordController {

    private final BlockWordService service;

    public BlockWordController(BlockWordService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> get(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.get(kbId));
    }

    @PostMapping
    public ApiResponse<Void> update(@RequestBody Map<String, Object> request) {
        service.update(request);
        return ApiResponse.ok(null);
    }
}
