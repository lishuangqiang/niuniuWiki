package com.chaitin.niuniuwiki.config;

import com.chaitin.niuniuwiki.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 提供 NiuniuWiki 后端的应用配置基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-07-15
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**", "/api/pro/v1/**")
                .excludePathPatterns(
                        "/api/v1/user/login",
                        "/api/v1/file/upload/anydoc");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/share/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
