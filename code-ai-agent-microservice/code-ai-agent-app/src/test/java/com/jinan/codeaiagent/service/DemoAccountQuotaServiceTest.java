package com.jinan.codeaiagent.service;

import com.jinan.codeaiagent.exception.BusinessException;
import com.jinan.codeaiagent.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoAccountQuotaServiceTest {

    private RedissonClient redissonClient;
    private RRateLimiter rateLimiter;
    private DemoAccountQuotaService quotaService;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        rateLimiter = mock(RRateLimiter.class);
        quotaService = new DemoAccountQuotaService(redissonClient);
        ReflectionTestUtils.setField(quotaService, "demoAccountUsername", "demo");
        ReflectionTestUtils.setField(quotaService, "dailyGenerationLimit", 10);
        when(redissonClient.getRateLimiter(startsWith("quota:demo:generation:"))).thenReturn(rateLimiter);
    }

    @Test
    void regularUserDoesNotConsumeDemoQuota() {
        User user = User.builder().id(1L).userAccount("regular-user").build();

        assertDoesNotThrow(() -> quotaService.checkAndConsume(user));

        verify(redissonClient, never()).getRateLimiter(startsWith("quota:demo:generation:"));
    }

    @Test
    void demoUserConsumesSharedDailyQuota() {
        User user = User.builder().id(2L).userAccount("demo").build();
        when(rateLimiter.tryAcquire()).thenReturn(true);

        assertDoesNotThrow(() -> quotaService.checkAndConsume(user));

        verify(rateLimiter).tryAcquire();
    }

    @Test
    void demoUserIsRejectedAfterDailyQuotaIsExhausted() {
        User user = User.builder().id(2L).userAccount("demo").build();
        when(rateLimiter.tryAcquire()).thenReturn(false);

        assertThrows(BusinessException.class, () -> quotaService.checkAndConsume(user));
    }
}
