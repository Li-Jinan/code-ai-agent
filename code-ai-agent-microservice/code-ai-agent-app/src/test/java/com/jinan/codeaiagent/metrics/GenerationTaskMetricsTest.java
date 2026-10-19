package com.jinan.codeaiagent.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GenerationTaskMetricsTest {

    @Test
    void recordsDurationAndCompletionWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GenerationTaskMetrics metrics = new GenerationTaskMetrics(registry);

        metrics.record("vue_project", "succeeded", Duration.ofSeconds(140));

        var timer = registry.find("generation.task.duration")
                .tags("code_gen_type", "vue_project", "status", "succeeded")
                .timer();
        var counter = registry.find("generation.task.completed")
                .tags("code_gen_type", "vue_project", "status", "succeeded")
                .counter();
        assertNotNull(timer);
        assertNotNull(counter);
        assertEquals(1, timer.count());
        assertEquals(140.0, timer.totalTime(java.util.concurrent.TimeUnit.SECONDS), 0.001);
        assertEquals(140.0, timer.max(java.util.concurrent.TimeUnit.SECONDS), 0.001);
        assertEquals(1.0, counter.count(), 0.001);
    }
}
