package com.jinan.codeaiagent.service;

import com.jinan.codeaiagent.exception.BusinessException;
import com.jinan.codeaiagent.exception.ErrorCode;
import com.jinan.codeaiagent.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Shared daily generation quota for the public demo account.
 */
@Service
@RequiredArgsConstructor
public class DemoAccountQuotaService {

    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");

    private final RedissonClient redissonClient;

    @Value("${demo.account.username:demo}")
    private String demoAccountUsername;

    @Value("${demo.account.daily-generation-limit:10}")
    private int dailyGenerationLimit;

    public void checkAndConsume(User user) {
        if (user == null || !demoAccountUsername.equals(user.getUserAccount())) {
            return;
        }
        if (dailyGenerationLimit <= 0) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "演示账号今日生成额度已用完，请明日再试");
        }

        String quotaDate = LocalDate.now(QUOTA_ZONE).toString();
        RRateLimiter limiter = redissonClient.getRateLimiter("quota:demo:generation:" + quotaDate);
        limiter.trySetRate(RateType.OVERALL, dailyGenerationLimit, 1, RateIntervalUnit.DAYS);
        limiter.expire(Duration.ofDays(2));
        if (!limiter.tryAcquire()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "演示账号今日生成额度已用完，请明日再试");
        }
    }
}
