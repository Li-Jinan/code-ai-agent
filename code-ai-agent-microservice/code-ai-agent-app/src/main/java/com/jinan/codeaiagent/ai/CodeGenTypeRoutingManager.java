package com.jinan.codeaiagent.ai;

import cn.hutool.core.util.StrUtil;
import com.jinan.codeaiagent.model.enums.CodeGenTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Routes a prompt to a code generation type and falls back to local rules
 * when the remote routing model is unavailable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CodeGenTypeRoutingManager {

    private final AiCodeGenTypeRoutingServiceFactory routingServiceFactory;

    public CodeGenTypeEnum route(String prompt) {
        try {
            AiCodeGenTypeRoutingService routingService =
                    routingServiceFactory.createAiCodeGenTypeRoutingService();
            CodeGenTypeEnum selectedType = routingService.routeCodeGenType(prompt);
            if (selectedType != null) {
                return selectedType;
            }
            log.warn("AI code type routing returned no result; using local fallback");
        } catch (Exception exception) {
            log.warn("AI code type routing failed; using local fallback: {}", exception.getMessage());
        }
        return routeLocally(prompt);
    }

    CodeGenTypeEnum routeLocally(String prompt) {
        String normalizedPrompt = StrUtil.blankToDefault(prompt, "").toLowerCase();
        if (StrUtil.containsAny(normalizedPrompt,
                "vue", "后台", "管理系统", "dashboard", "复杂交互", "登录注册", "购物车", "订单", "商城")) {
            return CodeGenTypeEnum.VUE_PROJECT;
        }
        if (StrUtil.containsAny(normalizedPrompt,
                "博客", "官网", "作品", "展示", "落地页", "个人主页", "portfolio", "blog", "landing")) {
            return CodeGenTypeEnum.MULTI_FILE;
        }
        return CodeGenTypeEnum.HTML;
    }
}
