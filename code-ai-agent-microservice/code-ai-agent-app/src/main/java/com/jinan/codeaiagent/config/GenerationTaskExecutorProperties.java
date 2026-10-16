package com.jinan.codeaiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 后台代码生成资源配额。
 *
 * <p>workerCount 对应可同时占用的生成槽位，queueCapacity 对应等待槽位，
 * 用于像有限 GPU Pod 一样对昂贵的 AI 生成任务实施硬上限。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "generation.task")
public class GenerationTaskExecutorProperties {

    /** 同时执行的生成任务数。 */
    private int workerCount = 2;

    /** 等待执行的任务数上限。 */
    private int queueCapacity = 8;

    /** 单个生成任务的最长执行时间。 */
    private Duration timeout = Duration.ofMinutes(10);

    /** 超过该时长未收到任务心跳时，任务被视为执行节点失联。 */
    private Duration staleAfter = Duration.ofMinutes(5);
}
