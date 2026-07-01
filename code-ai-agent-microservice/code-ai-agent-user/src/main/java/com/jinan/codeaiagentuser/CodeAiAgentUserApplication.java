package com.jinan.codeaiagent.user;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDubbo
@MapperScan("com.jinan.codeaiagent.user.mapper")
@ComponentScan("com.jinan")
public class CodeAiAgentUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeAiAgentUserApplication.class, args);
    }
}