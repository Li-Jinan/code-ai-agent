package com.jinan.codeaiagent.ai;

import com.jinan.codeaiagent.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeGenTypeRoutingManagerTest {

    @Test
    void returnsRemoteRoutingResultWhenAvailable() {
        AiCodeGenTypeRoutingServiceFactory routingServiceFactory = factoryReturning(
                prompt -> CodeGenTypeEnum.VUE_PROJECT
        );

        CodeGenTypeRoutingManager manager = new CodeGenTypeRoutingManager(routingServiceFactory);

        assertEquals(CodeGenTypeEnum.VUE_PROJECT, manager.route("创建商城"));
    }

    @Test
    void fallsBackToLocalRoutingWhenRemoteModelFails() {
        AiCodeGenTypeRoutingServiceFactory routingServiceFactory = new AiCodeGenTypeRoutingServiceFactory() {
            @Override
            public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
                throw new RuntimeException("model unavailable");
            }
        };

        CodeGenTypeRoutingManager manager = new CodeGenTypeRoutingManager(routingServiceFactory);

        assertEquals(CodeGenTypeEnum.MULTI_FILE, manager.route("创建一个作品展示网站"));
    }

    @Test
    void defaultsToHtmlForUnknownPrompt() {
        CodeGenTypeRoutingManager manager = new CodeGenTypeRoutingManager(factoryReturning(prompt -> null));

        assertEquals(CodeGenTypeEnum.HTML, manager.routeLocally("制作一个简洁页面"));
    }

    private AiCodeGenTypeRoutingServiceFactory factoryReturning(AiCodeGenTypeRoutingService routingService) {
        return new AiCodeGenTypeRoutingServiceFactory() {
            @Override
            public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
                return routingService;
            }
        };
    }
}
