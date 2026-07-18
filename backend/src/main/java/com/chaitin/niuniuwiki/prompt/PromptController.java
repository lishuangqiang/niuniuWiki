package com.chaitin.niuniuwiki.prompt;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理知识库自定义 AI 提示词接口。
 *
 * @author 程序员牛肉
 * @since 2026-05-11
 */
@RestController
@RequestMapping("/api/pro/v1/prompt")
public class PromptController {

    private final PromptService service;

    public PromptController(PromptService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> get(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.get(kbId));
    }

    @PutMapping
    public ApiResponse<?> update(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.update(request));
    }
}
