package com.chaitin.niuniuwiki.stat;

import com.chaitin.niuniuwiki.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理访问统计相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-04-19
 */
@RestController
@RequestMapping("/api/v1/stat")
public class StatController {

    private final StatService service;

    public StatController(StatService service) {
        this.service = service;
    }

    @GetMapping("/instant_count")
    public ApiResponse<?> instantCount(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.instantCount(kbId));
    }

    @GetMapping("/instant_pages")
    public ApiResponse<?> instantPages(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.instantPages(kbId));
    }

    @GetMapping("/count")
    public ApiResponse<?> count(@RequestParam("kb_id") String kbId, @RequestParam(defaultValue = "1") int day) {
        return ApiResponse.ok(service.count(kbId, day));
    }

    @GetMapping("/geo_count")
    public ApiResponse<?> geo(@RequestParam("kb_id") String kbId, @RequestParam(defaultValue = "1") int day) {
        return ApiResponse.ok(service.geo(kbId, day));
    }

    @GetMapping("/conversation_distribution")
    public ApiResponse<?> conversations(@RequestParam("kb_id") String kbId, @RequestParam(defaultValue = "1") int day) {
        return ApiResponse.ok(service.conversationDistribution(kbId, day));
    }

    @GetMapping("/hot_pages")
    public ApiResponse<?> hotPages(@RequestParam("kb_id") String kbId, @RequestParam(defaultValue = "1") int day) {
        return ApiResponse.ok(service.hotPages(kbId, day));
    }

    @GetMapping("/referer_hosts")
    public ApiResponse<?> referers(@RequestParam("kb_id") String kbId, @RequestParam(defaultValue = "1") int day) {
        return ApiResponse.ok(service.refererHosts(kbId, day));
    }

    @GetMapping("/browsers")
    public ApiResponse<?> browsers(@RequestParam("kb_id") String kbId, @RequestParam(defaultValue = "1") int day) {
        return ApiResponse.ok(service.browsers(kbId, day));
    }
}
