package com.chaitin.niuniuwiki.common;

/**
 * 提供 NiuniuWiki 后端的公共基础基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-04-28
 */
public record PageResult<T>(long total, T data) {
}
