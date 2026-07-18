package com.chaitin.niuniuwiki.user;

import com.chaitin.niuniuwiki.common.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 封装用户管理相关的业务逻辑。
 *
 * @author 程序员牛肉
 * @since 2026-06-13
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void ensureNotLocked(String ip) {
        Attempt attempt = attempts.get(ip);
        if (attempt == null || attempt.lockedUntil() == null) {
            return;
        }
        if (attempt.lockedUntil().isBefore(Instant.now())) {
            attempts.remove(ip);
            return;
        }
        Duration remaining = Duration.between(Instant.now(), attempt.lockedUntil());
        throw new ApiException("账号已被锁定，请 " + Math.max(1, remaining.toMinutes()) + " 分钟后重试");
    }

    public void failed(String ip) {
        attempts.compute(ip, (key, current) -> {
            int count = current == null ? 1 : current.count() + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            return new Attempt(count, lockedUntil);
        });
    }

    public void succeeded(String ip) {
        attempts.remove(ip);
    }

    private record Attempt(int count, Instant lockedUntil) {
    }
}
