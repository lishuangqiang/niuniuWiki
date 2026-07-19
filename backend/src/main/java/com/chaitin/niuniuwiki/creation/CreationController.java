package com.chaitin.niuniuwiki.creation;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理AI 创作相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-04-13
 */
@RestController
@RequestMapping("/api/v1/creation")
public class CreationController {

    private final CreationService service;

    public CreationController(CreationService service) {
        this.service = service;
    }

    @PostMapping(value = "/text", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public String text(@RequestBody Map<String, String> request) {
        return service.text(request);
    }

    @PostMapping("/tab-complete")
    public ApiResponse<String> tabComplete(@RequestBody Map<String, String> request) {
        return service.tabComplete(request);
    }
}
