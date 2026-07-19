package com.chaitin.niuniuwiki.share;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 绑定公开集成接口，并把 OAuth 与机器人业务流程交给服务层。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@RestController
@RequestMapping("/share/v1/openapi")
public class PublicOpenApiController {

    private final OpenApiService service;

    public PublicOpenApiController(OpenApiService service) {
        this.service = service;
    }

    @GetMapping("/github/callback")
    public ResponseEntity<?> githubCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpSession session
    ) {
        return service.githubCallback(code, state, session);
    }

    @PostMapping("/lark/bot/{kb_id}")
    public Object larkBot(
            @PathVariable("kb_id") String kbId,
            @RequestBody Map<String, Object> request
    ) {
        return service.larkBot(kbId, request);
    }
}
