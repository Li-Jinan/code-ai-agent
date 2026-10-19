package com.jinan.codeaiagent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Low-cardinality metrics for completed generation tasks. */
@Component
public class GenerationTaskMetrics {

    private final MeterRegistry meterRegistry;

    public GenerationTaskMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String codeGenType, String status, Duration duration) {
        String safeType = codeGenType == null || codeGenType.isBlank() ? "unknown" : codeGenType;
        Timer.builder("generation.task.duration")
                .description("Generation task execution duration after leaving the queue")
                .tags("code_gen_type", safeType, "status", status)
                .maximumExpectedValue(Duration.ofMinutes(10))
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(duration);
        Counter.builder("generation.task.completed")
                .description("Completed generation tasks")
                .tags("code_gen_type", safeType, "status", status)
                .register(meterRegistry)
                .increment();
    }
}
