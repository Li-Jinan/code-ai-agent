package com.jinan.codeaiagent.service.impl;

import com.jinan.codeaiagent.config.GenerationTaskExecutorProperties;
import com.jinan.codeaiagent.exception.BusinessException;
import com.jinan.codeaiagent.exception.ErrorCode;
import com.jinan.codeaiagent.mapper.GenerationTaskMapper;
import com.jinan.codeaiagent.model.entity.App;
import com.jinan.codeaiagent.model.entity.GenerationTask;
import com.jinan.codeaiagent.model.entity.User;
import com.jinan.codeaiagent.service.AppService;
import com.jinan.codeaiagent.service.DemoAccountQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationTaskServiceImplTest {

    @Test
    void concurrentSubmissionsForSameAppReturnTheSinglePersistedTask() throws Exception {
        int contenderCount = 10;
        Long appId = 901L;
        Long userId = 42L;
        AppService appService = mock(AppService.class);
        GenerationTaskMapper taskMapper = mock(GenerationTaskMapper.class);
        ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);
        when(executor.getQueue()).thenReturn(new LinkedBlockingQueue<>());
        GenerationTaskServiceImpl service = spy(new GenerationTaskServiceImpl());
        ReflectionTestUtils.setField(service, "appService", appService);
        ReflectionTestUtils.setField(service, "generationTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "generationTaskExecutor", executor);
        DemoAccountQuotaService quotaService = mock(DemoAccountQuotaService.class);
        AtomicInteger quotaConsumptions = new AtomicInteger();
        doAnswer(invocation -> quotaConsumptions.incrementAndGet()).when(quotaService).checkAndConsume(any(User.class));
        ReflectionTestUtils.setField(service, "demoAccountQuotaService", quotaService);

        App app = App.builder().id(appId).userId(userId).build();
        User user = User.builder().id(userId).userRole("user").build();
        when(appService.getById(appId)).thenReturn(app);

        var activeTasksByApp = new ConcurrentHashMap<Long, GenerationTask>();
        AtomicLong taskIds = new AtomicLong(1000L);
        AtomicInteger insertAttempts = new AtomicInteger();
        doAnswer(invocation -> {
            GenerationTask task = invocation.getArgument(0);
            insertAttempts.incrementAndGet();
            task.setId(taskIds.incrementAndGet());
            GenerationTask previous = activeTasksByApp.putIfAbsent(task.getRunningAppId(), task);
            if (previous != null) {
                throw new DuplicateKeyException("uk_generation_task_running_app");
            }
            return true;
        }).when(service).save(any(GenerationTask.class));
        when(taskMapper.selectActiveByAppId(appId)).thenAnswer(invocation -> activeTasksByApp.get(appId));
        doNothing().when(executor).execute(any(Runnable.class));

        ExecutorService callers = Executors.newFixedThreadPool(contenderCount);
        CountDownLatch ready = new CountDownLatch(contenderCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<GenerationTask>> results = java.util.stream.IntStream
                    .range(0, contenderCount)
                    .mapToObj(index -> callers.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return service.startGenerationTask(appId, "generate a site", user);
                    }))
                    .toList();

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            Set<Long> returnedTaskIds = results.stream().map(result -> {
                try {
                    return result.get(5, TimeUnit.SECONDS).getId();
                } catch (Exception exception) {
                    throw new AssertionError("Concurrent submission failed", exception);
                }
            }).collect(java.util.stream.Collectors.toSet());

            assertEquals(1, returnedTaskIds.size());
            assertEquals(1, activeTasksByApp.size());
            // 晚到的请求会命中已有活动任务直接复用，因此插入次数在 1..contenderCount 之间
            assertTrue(insertAttempts.get() >= 1 && insertAttempts.get() <= contenderCount);
            assertEquals(insertAttempts.get(), quotaConsumptions.get(),
                    "quota is consumed only by submissions that actually try to create a task");
            verify(executor, times(1)).execute(any(Runnable.class));
        } finally {
            start.countDown();
            callers.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    void staleRunningTaskIsFailedAndReleasesItsAppSlot() {
        GenerationTaskMapper taskMapper = mock(GenerationTaskMapper.class);
        GenerationTaskServiceImpl service = new GenerationTaskServiceImpl();
        GenerationTaskExecutorProperties properties = new GenerationTaskExecutorProperties();
        properties.setStaleAfter(java.time.Duration.ofMinutes(5));
        ReflectionTestUtils.setField(service, "generationTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "executorProperties", properties);

        GenerationTask staleTask = GenerationTask.builder()
                .id(500L)
                .appId(901L)
                .runningAppId(901L)
                .status("running")
                .heartbeatTime(LocalDateTime.now().minusMinutes(6))
                .build();
        when(taskMapper.selectStaleActiveTasks(300L)).thenReturn(List.of(staleTask));
        when(taskMapper.failIfStale(any(Long.class), anyLong())).thenAnswer(invocation -> {
            long staleAfterSeconds = invocation.getArgument(1);
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(staleAfterSeconds);
            if ("running".equals(staleTask.getStatus()) && staleTask.getHeartbeatTime().isBefore(cutoff)) {
                staleTask.setStatus("failed");
                staleTask.setRunningAppId(null);
                staleTask.setErrorMessage("生成中断，可重试。");
                return 1;
            }
            return 0;
        });

        int reclaimed = service.reclaimStaleTasksNow();

        assertEquals(1, reclaimed);
        assertEquals("failed", staleTask.getStatus());
        assertEquals("生成中断，可重试。", staleTask.getErrorMessage());
        assertNotNull(staleTask.getHeartbeatTime());
        assertNull(staleTask.getRunningAppId());
    }

    @Test
    void resubmittingWhileTaskIsActiveReturnsItWithoutConsumingDemoQuota() {
        Fixture fixture = new Fixture();
        GenerationTask activeTask = GenerationTask.builder()
                .id(700L).appId(fixture.appId).userId(fixture.user.getId())
                .runningAppId(fixture.appId).status("running").build();
        when(fixture.taskMapper.selectActiveByAppId(fixture.appId)).thenReturn(activeTask);

        GenerationTask returned = fixture.service.startGenerationTask(fixture.appId, "generate a site", fixture.user);

        assertSame(activeTask, returned);
        verify(fixture.quotaService, never()).checkAndConsume(any(User.class));
        verify(fixture.service, never()).save(any(GenerationTask.class));
        verify(fixture.executor, never()).execute(any(Runnable.class));
    }

    @Test
    void newTaskConsumesDemoQuotaExactlyOnce() {
        Fixture fixture = new Fixture();
        when(fixture.taskMapper.selectActiveByAppId(fixture.appId)).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<GenerationTask>getArgument(0).setId(701L);
            return true;
        }).when(fixture.service).save(any(GenerationTask.class));

        GenerationTask created = fixture.service.startGenerationTask(fixture.appId, "generate a site", fixture.user);

        assertEquals(701L, created.getId());
        verify(fixture.quotaService, times(1)).checkAndConsume(fixture.user);
        verify(fixture.executor, times(1)).execute(any(Runnable.class));
    }

    @Test
    void exhaustedDemoQuotaRejectsBeforeCreatingTask() {
        Fixture fixture = new Fixture();
        when(fixture.taskMapper.selectActiveByAppId(fixture.appId)).thenReturn(null);
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUEST, "演示账号今日生成额度已用完，请明日再试"))
                .when(fixture.quotaService).checkAndConsume(fixture.user);

        assertThrows(BusinessException.class,
                () -> fixture.service.startGenerationTask(fixture.appId, "generate a site", fixture.user));

        verify(fixture.service, never()).save(any(GenerationTask.class));
        verify(fixture.executor, never()).execute(any(Runnable.class));
    }

    private static class Fixture {
        final Long appId = 902L;
        final User user = User.builder().id(43L).userAccount("demo").userRole("user").build();
        final AppService appService = mock(AppService.class);
        final GenerationTaskMapper taskMapper = mock(GenerationTaskMapper.class);
        final ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);
        final DemoAccountQuotaService quotaService = mock(DemoAccountQuotaService.class);
        final GenerationTaskServiceImpl service = spy(new GenerationTaskServiceImpl());

        Fixture() {
            when(executor.getQueue()).thenReturn(new LinkedBlockingQueue<>());
            ReflectionTestUtils.setField(service, "appService", appService);
            ReflectionTestUtils.setField(service, "generationTaskMapper", taskMapper);
            ReflectionTestUtils.setField(service, "generationTaskExecutor", executor);
            ReflectionTestUtils.setField(service, "demoAccountQuotaService", quotaService);
            when(appService.getById(appId)).thenReturn(App.builder().id(appId).userId(user.getId()).build());
            doNothing().when(executor).execute(any(Runnable.class));
        }
    }
}
