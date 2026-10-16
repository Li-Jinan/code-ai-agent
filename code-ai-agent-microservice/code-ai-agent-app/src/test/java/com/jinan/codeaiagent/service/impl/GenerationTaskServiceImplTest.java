package com.jinan.codeaiagent.service.impl;

import com.jinan.codeaiagent.config.GenerationTaskExecutorProperties;
import com.jinan.codeaiagent.mapper.GenerationTaskMapper;
import com.jinan.codeaiagent.model.entity.App;
import com.jinan.codeaiagent.model.entity.GenerationTask;
import com.jinan.codeaiagent.model.entity.User;
import com.jinan.codeaiagent.service.AppService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
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
            assertEquals(contenderCount, insertAttempts.get());
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
        when(taskMapper.selectStaleActiveTasks(any(LocalDateTime.class))).thenReturn(List.of(staleTask));
        when(taskMapper.failIfStale(any(Long.class), any(LocalDateTime.class))).thenAnswer(invocation -> {
            LocalDateTime cutoff = invocation.getArgument(1);
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
}
