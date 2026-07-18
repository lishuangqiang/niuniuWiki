package com.chaitin.niuniuwiki.creation;

import com.chaitin.niuniuwiki.chat.ChatService;
import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理AI 创作相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-04-13
 */
@RestController
@RequestMapping("/api/v1/creation")
public class CreationController {

    private final ChatService chatService;

    public CreationController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/text", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public String text(@RequestBody Map<String, String> request) {
        String action = request.getOrDefault("action", "improve");
        String prompt = switch (action) {
            case "summary" -> "概括输入文本，只输出概括结果，保持原文语言。";
            case "extend" -> "扩写输入文本，只输出扩写结果，保持原文语言和风格。";
            case "shorten" -> "精简输入文本，只输出精简结果，保持核心信息。";
            default -> "润色并优化输入文本，只输出优化后的文本，保持原文语言、风格和段落结构。";
        };
        return chatService.rawComplete(prompt, request.getOrDefault("text", ""));
    }

    @PostMapping("/tab-complete")
    public ApiResponse<String> tabComplete(@RequestBody Map<String, String> request) {
        String prefix = request.getOrDefault("prefix", "");
        String suffix = request.getOrDefault("suffix", "");
        if (prefix.isBlank() && suffix.isBlank()) {
            return ApiResponse.ok("");
        }
        String prompt = "补全用户文档中 <fim_prefix> 与 <fim_suffix> 之间缺失的内容。"
                + "只输出缺失文本，不重复前后文。";
        return ApiResponse.ok(chatService.rawComplete(prompt,
                "<fim_prefix>" + prefix + "<fim_suffix>" + suffix + "<fim_middle>"));
    }
}
