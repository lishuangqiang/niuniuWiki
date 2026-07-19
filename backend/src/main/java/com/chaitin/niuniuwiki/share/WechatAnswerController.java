package com.chaitin.niuniuwiki.share;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 处理公开访问相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-06-19
 */
@RestController
@RequestMapping("/share/v1/app")
public class WechatAnswerController {

    private final WechatAnswerService service;

    public WechatAnswerController(WechatAnswerService service) {
        this.service = service;
    }

    @GetMapping(value = "/wechat/service/answer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answer(@RequestParam String id) {
        return service.answer(id);
    }
}
