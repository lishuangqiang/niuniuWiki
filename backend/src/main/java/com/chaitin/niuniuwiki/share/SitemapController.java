package com.chaitin.niuniuwiki.share;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理公开访问相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-04-05
 */
@RestController
public class SitemapController {

    private final SitemapService service;

    public SitemapController(SitemapService service) {
        this.service = service;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(@RequestHeader("X-KB-ID") String kbId) {
        return service.sitemap(kbId);
    }
}
