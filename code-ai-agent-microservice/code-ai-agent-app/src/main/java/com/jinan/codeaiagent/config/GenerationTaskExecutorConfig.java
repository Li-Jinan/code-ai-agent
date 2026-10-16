package com.jinan.codeaiagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class GenerationTaskExecutorConfig {

    @Bean(name = "generationTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor generationTaskExecutor(GenerationTaskExecutorProperties properties) {
        int workerCount = Math.max(1, properties.getWorkerCount());
        int queueCapacity = Math.max(1, properties.getQueueCapacity());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name("generation-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        log.info("生成任务执行器已启动，workerCount: {}, queueCapacity: {}", workerCount, queueCapacity);
        return executor;
    }
}
