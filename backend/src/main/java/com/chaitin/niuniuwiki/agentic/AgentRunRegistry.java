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
    private final AgenticRagStore store;

    public AgentRunRegistry(AgenticRagStore store) {
        this.store = store;
    }

    public CancellationSignal register(String runId) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean existing = runs.putIfAbsent(runId, cancelled);
        AtomicBoolean signal = existing == null ? cancelled : existing;
        return new DistributedSignal(runId, signal, store);
    }

    public boolean cancel(String runId) {
        AtomicBoolean signal = runs.get(runId);
        boolean local = signal != null && !signal.getAndSet(true);
        return store.requestCancellation(runId) || local;
    }

    public void complete(String runId) {
        runs.remove(runId);
    }

    private static final class DistributedSignal implements CancellationSignal {
        private final String runId;
        private final AtomicBoolean local;
        private final AgenticRagStore store;
        private volatile long checkedAt;
        private volatile boolean cancelled;

        private DistributedSignal(String runId, AtomicBoolean local, AgenticRagStore store) {
            this.runId = runId;
            this.local = local;
            this.store = store;
        }

        @Override
        public boolean isCancelled() {
            if (local.get() || cancelled) {
                return true;
            }
            long now = System.nanoTime();
            if (now - checkedAt > 200_000_000L) {
                checkedAt = now;
                cancelled = store.cancellationRequested(runId);
            }
            return cancelled;
        }
    }
}
