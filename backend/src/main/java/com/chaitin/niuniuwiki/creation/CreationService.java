package com.chaitin.niuniuwiki.creation;

import com.chaitin.niuniuwiki.common.ApiResponse;
import com.chaitin.niuniuwiki.model.ModelGateway;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 执行文本润色与上下文补全，隔离控制器和模型供应商协议。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@Service
public class CreationService {

    private final ModelGateway modelGateway;

    public CreationService(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    public String text(Map<String, String> request) {
        String action = request.getOrDefault("action", "improve");
        String prompt = switch (action) {
            case "summary" -> "概括输入文本，只输出概括结果，保持原文语言。";
            case "extend" -> "扩写输入文本，只输出扩写结果，保持原文语言和风格。";
            case "shorten" -> "精简输入文本，只输出精简结果，保持核心信息。";
            default -> "润色并优化输入文本，只输出优化后的文本，保持原文语言、风格和段落结构。";
        };
        return modelGateway.completeText(prompt, request.getOrDefault("text", ""));
    }

    public ApiResponse<String> tabComplete(Map<String, String> request) {
        String prefix = request.getOrDefault("prefix", "");
        String suffix = request.getOrDefault("suffix", "");
        if (prefix.isBlank() && suffix.isBlank()) {
            return ApiResponse.ok("");
        }
        String prompt = "补全用户文档中 <fim_prefix> 与 <fim_suffix> 之间缺失的内容。"
                + "只输出缺失文本，不重复前后文。";
        return ApiResponse.ok(modelGateway.completeText(prompt,
                "<fim_prefix>" + prefix + "<fim_suffix>" + suffix + "<fim_middle>"));
    }
}
