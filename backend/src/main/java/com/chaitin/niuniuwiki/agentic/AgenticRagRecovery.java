package com.chaitin.niuniuwiki.agentic;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 在服务重启后把未结束运行转换为可恢复状态，避免历史任务永久停留在运行中。
 *
 * @author 程序员牛肉
 * @since 2026-05-08
 */
@Component
public class AgenticRagRecovery {

    private final AgenticRagStore store;

    public AgenticRagRecovery(AgenticRagStore store) {
        this.store = store;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void pauseInterruptedRuns() {
        store.pauseInterruptedRuns();
    }
}
