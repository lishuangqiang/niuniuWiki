package com.chaitin.niuniuwiki.model;

import com.chaitin.niuniuwiki.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理大模型相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-07-09
 */
@RestController
@RequestMapping("/api/v1/model")
public class ModelController {

    private final ModelService service;

    public ModelController(ModelService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<?> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody ModelDtos.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping
    public ApiResponse<Void> update(@Valid @RequestBody ModelDtos.UpdateRequest request) {
        service.update(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/check")
    public ApiResponse<?> check(@Valid @RequestBody ModelDtos.CreateRequest request) {
        return ApiResponse.ok(service.check(request));
    }

    @PostMapping("/provider/supported")
    public ApiResponse<?> supported(@Valid @RequestBody ModelDtos.ProviderRequest request) {
        return ApiResponse.ok(service.supported(request));
    }

    @PostMapping("/switch-mode")
    public ApiResponse<?> switchMode(@Valid @RequestBody ModelDtos.SwitchModeRequest request) {
        service.switchMode(request);
        return ApiResponse.ok(Map.of("message", "模式切换成功"));
    }

    @GetMapping("/mode-setting")
    public ApiResponse<?> modeSetting() {
        return ApiResponse.ok(service.modeSetting());
    }
}
