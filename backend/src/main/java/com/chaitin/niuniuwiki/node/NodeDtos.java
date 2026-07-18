package com.chaitin.niuniuwiki.node;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 定义文档节点模块的请求与响应数据结构。
 *
 * @author 程序员牛肉
 * @since 2026-04-14
 */
public final class NodeDtos {

    private NodeDtos() {
    }

    public record CreateRequest(
            @NotBlank String kbId,
            @NotBlank String navId,
            String parentId,
            @NotNull Integer type,
            @NotBlank String name,
            String content,
            String emoji,
            String summary,
            String contentType,
            Double position
    ) {
    }

    public record UpdateRequest(
            @NotBlank String id,
            @NotBlank String kbId,
            String name,
            String content,
            String emoji,
            String summary,
            Double position,
            String contentType,
            String navId
    ) {
    }

    public record ActionRequest(
            @NotEmpty List<String> ids,
            @NotBlank String kbId,
            @NotBlank String action
    ) {
    }

    public record MoveRequest(
            @NotBlank String id,
            @NotBlank String kbId,
            String parentId,
            String prevId,
            String nextId
    ) {
    }

    public record BatchMoveRequest(
            @NotEmpty List<String> ids,
            @NotBlank String kbId,
            String parentId
    ) {
    }

    public record MoveNavRequest(
            @NotEmpty List<String> ids,
            @NotBlank String kbId,
            @NotBlank String navId
    ) {
    }

    public record SummaryRequest(@NotEmpty List<String> ids, @NotBlank String kbId) {
    }

    public record RestudyRequest(@NotEmpty List<String> nodeIds, @NotBlank String kbId) {
    }

    public record PermissionEditRequest(
            @NotBlank String kbId,
            @NotEmpty List<String> ids,
            Map<String, Object> permissions,
            List<Integer> answerableGroups,
            List<Integer> visitableGroups,
            List<Integer> visibleGroups
    ) {
    }
}
