package com.jinan.codeaiagent.core.parser;

import com.jinan.codeaiagent.ai.model.HtmlCodeResult;
import com.jinan.codeaiagent.exception.BusinessException;
import com.jinan.codeaiagent.exception.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 单文件代码解析器
 *
 * @author jinan
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        if (codeContent == null || codeContent.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 未返回 HTML 内容");
        }
        HtmlCodeResult result = new HtmlCodeResult();
        // 提取 HTML 代码
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            // 兼容模型直接返回 HTML，或代码围栏因输出被截断而没有闭合的情况。
            int docTypeIndex = indexOfIgnoreCase(codeContent, "<!doctype html");
            int htmlIndex = indexOfIgnoreCase(codeContent, "<html");
            int startIndex = docTypeIndex >= 0 ? docTypeIndex : htmlIndex;
            htmlCode = startIndex >= 0 ? codeContent.substring(startIndex) : codeContent;
        }
        htmlCode = htmlCode.trim();
        if (!containsIgnoreCase(htmlCode, "<html")
                || !containsIgnoreCase(htmlCode, "</body>")
                || !containsIgnoreCase(htmlCode, "</html>")) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回的 HTML 不完整，请重试");
        }
        result.setHtmlCode(htmlCode);
        return result;
    }

    /**
     * 提取 HTML 代码内容
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private String extractHtmlCode(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private int indexOfIgnoreCase(String content, String target) {
        return content.toLowerCase().indexOf(target.toLowerCase());
    }

    private boolean containsIgnoreCase(String content, String target) {
        return indexOfIgnoreCase(content, target) >= 0;
    }
}
