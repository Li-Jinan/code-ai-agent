package com.jinan.codeaiagent.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationTaskExecutorConfigTest {

    @Test
    void enforcesWorkerAndQueueCapacity() throws InterruptedException {
        GenerationTaskExecutorProperties properties = new GenerationTaskExecutorProperties();
        properties.setWorkerCount(1);
        properties.setQueueCapacity(1);
        ThreadPoolExecutor executor = new GenerationTaskExecutorConfig().generationTaskExecutor(properties);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        try {
            executor.execute(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            workerStarted.await();
            executor.execute(() -> { });

            assertEquals(1, executor.getActiveCount());
            assertEquals(1, executor.getQueue().size());
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
        }
    }
}
