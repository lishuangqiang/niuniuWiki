package com.chaitin.niuniuwiki.agentic;

/**
 * 表示 Agent 已触达可控预算边界，执行器应停止继续检索并使用已有证据作答。
 *
 * @author 程序员牛肉
 * @since 2026-06-21
 */
final class AgenticBudgetExceededException extends RuntimeException {

    AgenticBudgetExceededException(String message) {
        super(message);
    }
}
