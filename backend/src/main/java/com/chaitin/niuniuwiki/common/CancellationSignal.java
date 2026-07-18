package com.chaitin.niuniuwiki.common;

import java.util.concurrent.CancellationException;

/**
 * 为模型调用、检索子任务和 Agent 循环提供统一的协作式取消信号。
 *
 * @author 程序员牛肉
 * @since 2026-06-03
 */
@FunctionalInterface
public interface CancellationSignal {

    boolean isCancelled();

    default void check() {
        if (isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Agent 运行已取消");
        }
    }

    static CancellationSignal none() {
        return () -> false;
    }
}
