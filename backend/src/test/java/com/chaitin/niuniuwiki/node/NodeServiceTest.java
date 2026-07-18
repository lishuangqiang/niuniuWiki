package com.chaitin.niuniuwiki.node;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证文档学习状态的兼容判定规则。
 *
 * @author 程序员牛肉
 * @since 2026-06-18
 */
class NodeServiceTest {

    @Test
    void recognizesCurrentAndLegacySuccessStatuses() {
        assertTrue(NodeService.isLearnedStatus("SUCCEEDED"));
        assertTrue(NodeService.isLearnedStatus("success"));
        assertTrue(NodeService.isLearnedStatus(" completed "));
    }

    @Test
    void keepsPendingAndFailedDocumentsUnstudied() {
        assertFalse(NodeService.isLearnedStatus("PENDING"));
        assertFalse(NodeService.isLearnedStatus("failed"));
        assertFalse(NodeService.isLearnedStatus(""));
        assertFalse(NodeService.isLearnedStatus(null));
    }
}
