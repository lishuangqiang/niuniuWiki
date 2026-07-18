package com.chaitin.niuniuwiki.file;

import com.chaitin.niuniuwiki.common.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 处理文件存储相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-05-08
 */
@RestController
@RequestMapping("/api/v1/file")
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public ApiResponse<?> upload(
            @RequestPart MultipartFile file,
            @RequestParam(name = "kb_id", required = false) String kbId
    ) {
        String key = service.upload(kbId == null || kbId.isBlank() ? UUID.randomUUID().toString() : kbId, file);
        return ApiResponse.ok(Map.of("key", key, "filename", file.getOriginalFilename()));
    }

    @PostMapping("/upload/url")
    public ApiResponse<?> uploadUrl(@RequestBody Map<String, String> request) {
        String kbId = request.getOrDefault("kb_id", UUID.randomUUID().toString());
        return ApiResponse.ok(Map.of("key", service.uploadUrl(kbId, request.get("url"))));
    }

    @PostMapping("/upload/anydoc")
    public Map<String, Object> anydoc(@RequestPart MultipartFile file, @RequestParam String path) {
        service.uploadAtPath(path, file);
        return Map.of("code", 0, "data", "/static-file/" + path.replaceFirst("^/", ""), "err", "");
    }
}
