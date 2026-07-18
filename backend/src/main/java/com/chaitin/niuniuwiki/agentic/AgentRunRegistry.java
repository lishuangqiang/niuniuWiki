package com.chaitin.niuniuwiki.agentic;

import com.chaitin.niuniuwiki.common.CancellationSignal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * 维护当前进程内正在执行的 Agent 运行，并把客户端断开转换为可传播的取消信号。
 *
 * @author 程序员牛肉
 * @since 2026-05-19
 */
@Component
public class AgentRunRegistry {

    private final Map<String, AtomicBoolean> runs = new ConcurrentHashMap<>();

    public CancellationSignal register(String runId) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean existing = runs.putIfAbsent(runId, cancelled);
        AtomicBoolean signal = existing == null ? cancelled : existing;
        return signal::get;
    }

    public boolean cancel(String runId) {
        AtomicBoolean signal = runs.get(runId);
        return signal != null && !signal.getAndSet(true);
    }

    public void complete(String runId) {
        runs.remove(runId);
    }
}
