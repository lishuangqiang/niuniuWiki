package com.chaitin.niuniuwiki.crawler;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理内容抓取相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-07-14
 */
@RestController
@RequestMapping("/api/v1/crawler")
public class CrawlerController {

    private final CrawlerService service;

    public CrawlerController(CrawlerService service) {
        this.service = service;
    }

    @PostMapping("/parse")
    public ApiResponse<?> parse(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.parse(request));
    }

    @PostMapping("/export")
    public ApiResponse<?> export(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.export(request));
    }

    @GetMapping("/result")
    public ApiResponse<?> result(@RequestParam("task_id") String taskId) {
        return ApiResponse.ok(service.result(taskId));
    }

    @PostMapping("/results")
    public ApiResponse<?> results(@RequestBody Map<String, List<String>> request) {
        return ApiResponse.ok(service.results(request.getOrDefault("task_ids", List.of())));
    }
}
