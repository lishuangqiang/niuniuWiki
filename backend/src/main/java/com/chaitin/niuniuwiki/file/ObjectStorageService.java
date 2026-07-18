package com.chaitin.niuniuwiki.file;

import com.chaitin.niuniuwiki.common.ApiException;
import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 封装文件存储相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-04-18
 */
@Service
public class ObjectStorageService {

    private final NiuniuWikiProperties properties;
    private volatile MinioClient client;

    public ObjectStorageService(NiuniuWikiProperties properties) {
        this.properties = properties;
    }

    public String put(String key, InputStream stream, long size, String contentType, String originalName) {
        try {
            MinioClient minio = client();
            minio.putObject(PutObjectArgs.builder()
                    .bucket(properties.getStorage().getBucket())
                    .object(key)
                    .stream(stream, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .userMetadata(Map.of("originalname", originalName == null ? "" : originalName))
                    .build());
            return key;
        } catch (Exception exception) {
            throw new ApiException("upload failed: " + exception.getMessage());
        }
    }

    public String put(String key, byte[] content, String contentType, String originalName) {
        return put(key, new ByteArrayInputStream(content), content.length, contentType, originalName);
    }

    private MinioClient client() throws Exception {
        if (client != null) {
            return client;
        }
        synchronized (this) {
            if (client == null) {
                if (properties.getStorage().getSecretKey().isBlank()) {
                    throw new IllegalStateException("S3_SECRET_KEY is not configured");
                }
                String endpoint = properties.getStorage().getEndpoint();
                if (!endpoint.contains("://")) {
                    endpoint = "http://" + endpoint;
                }
                MinioClient created = MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials(properties.getStorage().getAccessKey(), properties.getStorage().getSecretKey())
                        .build();
                String bucket = properties.getStorage().getBucket();
                if (!created.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    created.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                client = created;
            }
            return client;
        }
    }
}
