package com.chaitin.niuniuwiki.knowledgebase;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * 定义知识库模块的请求与响应数据结构。
 *
 * @author 程序员牛肉
 * @since 2026-06-25
 */
public final class KnowledgeBaseDtos {

    private KnowledgeBaseDtos() {
    }

    public record CreateRequest(
            @NotBlank String name,
            List<Integer> ports,
            List<Integer> sslPorts,
            String publicKey,
            String privateKey,
            List<String> hosts
    ) {
    }

    public record UpdateRequest(
            @NotBlank String id,
            String name,
            Map<String, Object> accessSettings
    ) {
    }

    public record ReleaseRequest(
            @NotBlank String kbId,
            @NotBlank String message,
            @NotBlank String tag,
            List<String> nodeIds
    ) {
    }

    public record UserPermissionRequest(
            @NotBlank String kbId,
            @NotBlank String userId,
            @NotBlank String perm
    ) {
    }
}
