package com.chaitin.niuniuwiki.share;

import com.chaitin.niuniuwiki.captcha.CaptchaService;
import com.chaitin.niuniuwiki.common.ApiResponse;
import com.chaitin.niuniuwiki.contribute.ContributeService;
import com.chaitin.niuniuwiki.feedback.DocumentFeedbackService;
import com.chaitin.niuniuwiki.file.FileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 提供公开端完整的认证、贡献、纠错和文件上传能力。
 *
 * @author 程序员牛肉
 * @since 2026-07-12
 */
@RestController
@RequestMapping("/share/pro/v1")
public class ProShareCompatibilityController {

    private final ShareAuthService authService;
    private final ExternalAuthService externalAuthService;
    private final ShareAccessService accessService;
    private final CaptchaService captchaService;
    private final ContributeService contributeService;
    private final DocumentFeedbackService feedbackService;
    private final FileService fileService;

    public ProShareCompatibilityController(
            ShareAuthService authService,
            ExternalAuthService externalAuthService,
            ShareAccessService accessService,
            CaptchaService captchaService,
            ContributeService contributeService,
            DocumentFeedbackService feedbackService,
            FileService fileService
    ) {
        this.authService = authService;
        this.externalAuthService = externalAuthService;
        this.accessService = accessService;
        this.captchaService = captchaService;
        this.contributeService = contributeService;
        this.feedbackService = feedbackService;
        this.fileService = fileService;
    }

    @GetMapping("/auth/info")
    public ApiResponse<?> authInfo(
            @RequestHeader("X-KB-ID") String kbId,
            HttpSession session
    ) {
        return ApiResponse.ok(authService.sessionInfo(kbId, session));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        authService.logout(session);
        return ApiResponse.ok(null);
    }

    @PostMapping("/auth/{sourceType:github|oauth|dingtalk|feishu|wecom|cas}")
    public ApiResponse<?> login(
            @PathVariable String sourceType,
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request
    ) {
        String redirectUrl = String.valueOf(request.getOrDefault("redirect_url", ""));
        boolean app = Boolean.TRUE.equals(request.get("is_app"));
        return ApiResponse.ok(Map.of(
                "url", externalAuthService.authorizationUrl(kbId, sourceType, redirectUrl, app)));
    }

    @PostMapping("/auth/ldap")
    public ApiResponse<Void> ldap(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpSession session
    ) {
        externalAuthService.ldapLogin(
                kbId,
                String.valueOf(request.getOrDefault("username", "")),
                String.valueOf(request.getOrDefault("password", "")),
                session);
        return ApiResponse.ok(null);
    }

    @GetMapping("/openapi/{sourceType:github|oauth|dingtalk|feishu|wecom|cas}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String sourceType,
            @RequestParam(required = false, defaultValue = "") String code,
            @RequestParam(required = false, defaultValue = "") String ticket,
            @RequestParam String state,
            HttpSession session
    ) {
        String redirect = externalAuthService.callback(sourceType, code, ticket, state, session);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, redirect).build();
    }

    @PostMapping("/contribute/submit")
    public ApiResponse<?> contribute(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        captchaService.verify(String.valueOf(request.getOrDefault("captcha_token", "")));
        return ApiResponse.ok(contributeService.submit(
                kbId, request, servletRequest.getRemoteAddr(), session));
    }

    @PostMapping("/file/upload")
    public ApiResponse<?> upload(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestPart MultipartFile file,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        return ApiResponse.ok(Map.of("key", fileService.upload(kbId, file)));
    }

    @PostMapping("/document/feedback")
    public ApiResponse<Void> feedback(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestParam("node_id") String nodeId,
            @RequestParam String content,
            @RequestParam(name = "correction_suggestion", required = false, defaultValue = "") String suggestion,
            @RequestPart(name = "image", required = false) MultipartFile image,
            HttpServletRequest servletRequest,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        feedbackService.create(
                kbId, nodeId, content, suggestion, image, servletRequest.getRemoteAddr(), session);
        return ApiResponse.ok(null);
    }
}
