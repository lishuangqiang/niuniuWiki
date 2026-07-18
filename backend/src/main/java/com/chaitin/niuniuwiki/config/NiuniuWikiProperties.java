package com.chaitin.niuniuwiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 承载应用配置相关的配置属性。
 *
 * @author 程序员牛肉
 * @since 2026-06-17
 */
@ConfigurationProperties(prefix = "niuniu-wiki")
public class NiuniuWikiProperties {

    private String adminPassword = "";
    private boolean readOnly;
    private final Auth auth = new Auth();
    private final Rag rag = new Rag();
    private final Storage storage = new Storage();
    private final Messaging messaging = new Messaging();
    private final Crawler crawler = new Crawler();

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public Auth getAuth() {
        return auth;
    }

    public Rag getRag() {
        return rag;
    }

    public Storage getStorage() {
        return storage;
    }

    public Messaging getMessaging() {
        return messaging;
    }

    public Crawler getCrawler() {
        return crawler;
    }

    public static class Auth {
        private String jwtSecret = "change-me-before-production";
        private long tokenTtlSeconds = 86_400;

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

        public void setTokenTtlSeconds(long tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }
    }

    public static class Rag {
        private String baseUrl = "http://169.254.15.18:5050";
        private String apiKey = "sk-1234567890";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Storage {
        private String endpoint = "http://niuniu-wiki-minio:9000";
        private String accessKey = "s3niuniu-wiki";
        private String secretKey = "";
        private String bucket = "static-file";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class Messaging {
        private String natsUrl = "nats://169.254.15.13:4222";
        private String username = "niuniu-wiki";
        private String password = "";

        public String getNatsUrl() {
            return natsUrl;
        }

        public void setNatsUrl(String natsUrl) {
            this.natsUrl = natsUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Crawler {
        private String baseUrl = "http://niuniu-wiki-crawler:8080";
        private String uploaderUrl = "http://niuniu-wiki-api:8000/api/v1/file/upload/anydoc";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUploaderUrl() {
            return uploaderUrl;
        }

        public void setUploaderUrl(String uploaderUrl) {
            this.uploaderUrl = uploaderUrl;
        }
    }
}
