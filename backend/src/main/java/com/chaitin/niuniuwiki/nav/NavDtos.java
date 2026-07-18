package com.chaitin.niuniuwiki.nav;

import jakarta.validation.constraints.NotBlank;

/**
 * 定义导航模块的请求与响应数据结构。
 *
 * @author 程序员牛肉
 * @since 2026-05-10
 */
public final class NavDtos {

    private NavDtos() {
    }

    public record AddRequest(@NotBlank String kbId, @NotBlank String name, Double position) {
    }

    public record UpdateRequest(@NotBlank String kbId, @NotBlank String id, @NotBlank String name) {
    }

    public record MoveRequest(
            @NotBlank String kbId,
            @NotBlank String id,
            String prevId,
            String nextId
    ) {
    }
}
