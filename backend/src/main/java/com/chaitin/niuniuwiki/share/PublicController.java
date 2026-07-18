package com.chaitin.niuniuwiki.share;

import com.chaitin.niuniuwiki.app.AppService;
import com.chaitin.niuniuwiki.captcha.CaptchaService;
import com.chaitin.niuniuwiki.comment.CommentService;
import com.chaitin.niuniuwiki.common.ApiResponse;
import com.chaitin.niuniuwiki.conversation.ConversationService;
import com.chaitin.niuniuwiki.file.FileService;
import com.chaitin.niuniuwiki.stat.StatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 处理公开访问相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-04-27
 */
@RestController
@RequestMapping("/share/v1")
public class PublicController {

    private final AppService appService;
    private final PublicContentService contentService;
    private final ShareAccessService accessService;
    private final ShareAuthService authService;
    private final CaptchaService captchaService;
    private final CommentService commentService;
    private final ConversationService conversationService;
    private final StatService statService;
    private final FileService fileService;

    public PublicController(
            AppService appService,
            PublicContentService contentService,
            ShareAccessService accessService,
            ShareAuthService authService,
            CaptchaService captchaService,
            CommentService commentService,
            ConversationService conversationService,
            StatService statService,
            FileService fileService
    ) {
        this.appService = appService;
        this.contentService = contentService;
        this.accessService = accessService;
        this.authService = authService;
        this.captchaService = captchaService;
        this.commentService = commentService;
        this.conversationService = conversationService;
        this.statService = statService;
        this.fileService = fileService;
    }

    @GetMapping("/app/web/info")
    public ApiResponse<?> webInfo(@RequestHeader("X-KB-ID") String kbId, HttpSession session) {
        accessService.authorize(kbId, session);
        return ApiResponse.ok(appService.publicInfo(kbId, 1));
    }

    @GetMapping("/app/widget/info")
    public ApiResponse<?> widgetInfo(@RequestHeader("X-KB-ID") String kbId, HttpSession session) {
        accessService.authorize(kbId, session);
        return ApiResponse.ok(appService.publicInfo(kbId, 2));
    }

    @GetMapping("/app/wechat/info")
    public ApiResponse<?> wechatInfo(@RequestHeader("X-KB-ID") String kbId, HttpSession session) {
        accessService.authorize(kbId, session);
        return ApiResponse.ok(appService.publicInfo(kbId, 5));
    }

    @GetMapping("/nav/list")
    public ApiResponse<?> navs(
            @RequestParam("kb_id") String kbId,
            HttpSession session
    ) {
        return ApiResponse.ok(contentService.navs(kbId, session));
    }

    @GetMapping("/node/list")
    public ApiResponse<?> nodes(@RequestHeader("X-KB-ID") String kbId, HttpSession session) {
        return ApiResponse.ok(contentService.nodeGroups(kbId, session));
    }

    @GetMapping("/node/detail")
    public ApiResponse<?> nodeDetail(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestParam String id,
            @RequestParam(name = "release_id", required = false) String releaseId,
            HttpSession session
    ) {
        if (releaseId != null && !releaseId.isBlank()) {
            return ApiResponse.ok(contentService.historicalNodeDetail(kbId, id, releaseId, session));
        }
        return ApiResponse.ok(contentService.nodeDetail(kbId, id, session));
    }

    @GetMapping("/conversation/detail")
    public ApiResponse<?> conversation(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestParam String id,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        return ApiResponse.ok(conversationService.detail(kbId, id, true));
    }

    @PostMapping("/comment")
    public ApiResponse<?> comment(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        captchaService.verify(String.valueOf(request.getOrDefault("captcha_token", "")));
        return ApiResponse.ok(commentService.create(kbId, servletRequest.getRemoteAddr(), request));
    }

    @GetMapping("/comment/list")
    public ApiResponse<?> comments(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestParam String id,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        return ApiResponse.ok(commentService.publicList(kbId, id));
    }

    @PostMapping("/stat/page")
    public ApiResponse<Void> stat(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        String sessionId = servletRequest.getHeader("x-pw-session-id");
        if ((sessionId == null || sessionId.isBlank()) && servletRequest.getCookies() != null) {
            sessionId = session.getId();
        }
        statService.record(
                kbId,
                ((Number) request.getOrDefault("scene", 0)).intValue(),
                String.valueOf(request.getOrDefault("node_id", "")),
                sessionId,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"),
                servletRequest.getHeader("Referer"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/auth/get")
    public ApiResponse<?> authInfo(@RequestHeader("X-KB-ID") String kbId) {
        return ApiResponse.ok(authService.info(kbId));
    }

    @PostMapping("/auth/login/simple")
    public ApiResponse<Void> simpleLogin(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, String> request,
            HttpSession session
    ) {
        authService.loginSimple(kbId, request.getOrDefault("password", ""), session);
        return ApiResponse.ok(null);
    }

    @PostMapping("/auth/github")
    public ApiResponse<?> github(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, String> request
    ) {
        return ApiResponse.ok(Map.of("url", authService.githubUrl(kbId, request.getOrDefault("redirect_url", ""))));
    }

    @PostMapping("/captcha/challenge")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> captchaChallenge(@RequestHeader("X-KB-ID") String kbId) {
        accessService.settings(kbId);
        return captchaService.challenge();
    }

    @PostMapping("/captcha/redeem")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> captchaRedeem(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, Object> request
    ) {
        accessService.settings(kbId);
        return captchaService.redeem(String.valueOf(request.getOrDefault("token", "")), request.get("solutions"));
    }

    @PostMapping("/common/file/upload")
    public ApiResponse<?> upload(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestPart MultipartFile file,
            @RequestParam("captcha_token") String captchaToken,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        captchaService.verify(captchaToken);
        if (!fileService.isImage(file.getOriginalFilename())) {
            throw new com.chaitin.niuniuwiki.common.ApiException("只支持图片文件上传");
        }
        return ApiResponse.ok(Map.of("key", fileService.upload(kbId, file)));
    }

    @PostMapping("/common/file/upload/url")
    public ApiResponse<?> uploadUrl(
            @RequestHeader("X-KB-ID") String kbId,
            @RequestBody Map<String, String> request,
            HttpSession session
    ) {
        accessService.authorize(kbId, session);
        captchaService.verify(request.getOrDefault("captcha_token", ""));
        if (!fileService.isImage(request.get("url"))) {
            throw new com.chaitin.niuniuwiki.common.ApiException("只支持图片文件上传");
        }
        return ApiResponse.ok(Map.of("key", fileService.uploadUrl(kbId, request.get("url"))));
    }
}
