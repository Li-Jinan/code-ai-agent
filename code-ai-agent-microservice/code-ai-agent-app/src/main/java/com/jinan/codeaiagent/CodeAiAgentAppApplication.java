package com.jinan.codeaiagent;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.jinan.codeaiagent.mapper")
@EnableDubbo
@EnableCaching
@EnableScheduling
public class CodeAiAgentAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeAiAgentAppApplication.class, args);
    }
}
