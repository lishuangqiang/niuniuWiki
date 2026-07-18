package com.chaitin.niuniuwiki.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

/**
 * 定义大模型模块的请求与响应数据结构。
 *
 * @author 程序员牛肉
 * @since 2026-05-28
 */
public final class ModelDtos {

    private ModelDtos() {
    }

    public record CreateRequest(
            @NotBlank String provider,
            @NotBlank String model,
            @NotBlank String baseUrl,
            String apiKey,
            String apiHeader,
            String apiVersion,
            @NotBlank @Pattern(regexp = "chat|embedding|rerank|analysis|analysis-vl") String type,
            Map<String, Object> parameters
    ) {
    }

    public record UpdateRequest(
            @NotBlank String id,
            @NotBlank String provider,
            @NotBlank String model,
            @NotBlank String baseUrl,
            String apiKey,
            String apiHeader,
            String apiVersion,
            @NotBlank @Pattern(regexp = "chat|embedding|rerank|analysis|analysis-vl") String type,
            Map<String, Object> parameters,
            Boolean isActive
    ) {
    }

    public record ProviderRequest(
            @NotBlank String provider,
            @NotBlank String baseUrl,
            String apiKey,
            String apiHeader,
            @NotBlank String type
    ) {
    }

    public record SwitchModeRequest(
            @NotBlank @Pattern(regexp = "manual|auto") String mode,
            String autoModeApiKey,
            String autoModeProvider,
            String chatModel
    ) {
    }
}
