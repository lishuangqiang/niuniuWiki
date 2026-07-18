package com.chaitin.niuniuwiki.file;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.common.JsonMaps;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.chaitin.niuniuwiki.persistence.MyBatisStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 封装文件存储相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-06-16
 */
@Service
public class FileService {

    private static final long MAX_REMOTE_FILE_SIZE = 50L * 1024 * 1024;

    private final ObjectStorageService storage;
    private final MyBatisStore store;
    private final JsonMaps jsonMaps;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public FileService(ObjectStorageService storage, MyBatisStore store, JsonMaps jsonMaps) {
        this.storage = storage;
        this.store = store;
        this.jsonMaps = jsonMaps;
    }

    public String upload(String kbId, MultipartFile file) {
        try {
            String extension = extension(file.getOriginalFilename());
            ensureAllowed(extension);
            String key = kbId + "/" + UUID.randomUUID() + extension;
            return storage.put(key, file.getInputStream(), file.getSize(), file.getContentType(), file.getOriginalFilename());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("upload failed: " + exception.getMessage());
        }
    }

    public String uploadAtPath(String path, MultipartFile file) {
        try {
            ensureAllowed(extension(file.getOriginalFilename()));
            return storage.put(path, file.getInputStream(), file.getSize(), file.getContentType(), file.getOriginalFilename());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("upload failed: " + exception.getMessage());
        }
    }

    public String uploadUrl(String kbId, String url) {
        try {
            URI uri = URI.create(url);
            validateRemote(uri);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new ApiException("failed to download file, status: " + response.statusCode());
            }
            if (response.body().length > MAX_REMOTE_FILE_SIZE) {
                throw new ApiException("file size exceeds 50MB");
            }
            String extension = extension(uri.getPath());
            ensureAllowed(extension);
            String key = kbId + "/" + UUID.randomUUID() + extension;
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return storage.put(key, response.body(), contentType, Path.of(uri.getPath()).getFileName().toString());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("upload failed: " + exception.getMessage());
        }
    }

    public boolean isImage(String filename) {
        return Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".bmp")
                .contains(extension(filename));
    }

    private void ensureAllowed(String extension) {
        if (extension == null || extension.length() < 2) {
            return;
        }
        List<Map<String, Object>> rows = store.query(
                "SELECT value FROM system_settings WHERE key = 'upload'",
                store.rowMapper());
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> value = jsonMaps.jsonMap(rows.getFirst().get("value"));
        if (value.get("denied_extensions") instanceof List<?> denied
                && denied.stream().map(String::valueOf).anyMatch(item -> item.equalsIgnoreCase(extension.substring(1)))) {
            throw new ApiException("file extension '" + extension + "' is not allowed for upload");
        }
    }

    private void validateRemote(URI uri) throws Exception {
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
            throw new ApiException("invalid URL format");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new ApiException("private network URLs are not allowed");
            }
        }
    }

    private String extension(String filename) {
        String value = filename == null ? "" : filename;
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        int dot = value.lastIndexOf('.');
        return dot > slash ? value.substring(dot).toLowerCase() : "";
    }
}
