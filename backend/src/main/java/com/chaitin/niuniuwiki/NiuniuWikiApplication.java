package com.chaitin.niuniuwiki;

import com.chaitin.niuniuwiki.config.NiuniuWikiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 提供 NiuniuWiki Java 后端的应用启动入口。
 *
 * @author 程序员牛肉
 * @since 2026-07-09
 */
@SpringBootApplication
@EnableConfigurationProperties(NiuniuWikiProperties.class)
@EnableScheduling
public class NiuniuWikiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NiuniuWikiApplication.class, args);
    }
}
