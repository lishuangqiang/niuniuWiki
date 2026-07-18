package com.chaitin.niuniuwiki.nav;

import com.chaitin.niuniuwiki.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理导航相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-05-10
 */
@RestController
@RequestMapping("/api/v1/nav")
public class NavController {

    private final NavService service;

    public NavController(NavService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<?> list(@RequestParam("kb_id") String kbId) {
        return ApiResponse.ok(service.list(kbId));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@Valid @RequestBody NavDtos.AddRequest request) {
        service.add(request);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody NavDtos.UpdateRequest request) {
        service.update(request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/delete")
    public ApiResponse<Void> delete(@RequestParam("kb_id") String kbId, @RequestParam String id) {
        service.delete(kbId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/move")
    public ApiResponse<Void> move(@Valid @RequestBody NavDtos.MoveRequest request) {
        service.move(request);
        return ApiResponse.ok(null);
    }
}
