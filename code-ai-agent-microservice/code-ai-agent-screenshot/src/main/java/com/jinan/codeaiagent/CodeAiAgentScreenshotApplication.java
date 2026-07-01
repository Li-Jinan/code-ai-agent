package com.jinan.codeaiagent;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class CodeAiAgentScreenshotApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeAiAgentScreenshotApplication.class, args);
    }
}