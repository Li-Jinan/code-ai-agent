package com.jinan.codeaiagent.core.parser;

import com.jinan.codeaiagent.ai.model.HtmlCodeResult;
import com.jinan.codeaiagent.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HtmlCodeParserTest {

    private final HtmlCodeParser parser = new HtmlCodeParser();

    @Test
    void extractsCompleteFencedHtmlAndDropsExplanation() {
        String response = "说明文字\n```html\n<!DOCTYPE html><html><body>ok</body></html>\n```";

        HtmlCodeResult result = parser.parseCode(response);

        assertEquals("<!DOCTYPE html><html><body>ok</body></html>", result.getHtmlCode());
    }

    @Test
    void acceptsCompleteRawHtml() {
        String response = "<!DOCTYPE html><html><body>ok</body></html>";

        HtmlCodeResult result = parser.parseCode(response);

        assertEquals(response, result.getHtmlCode());
    }

    @Test
    void rejectsTruncatedHtmlInsteadOfPublishingIt() {
        String response = "说明文字\n```html\n<!DOCTYPE html><html><body><main>未完成";

        assertThrows(BusinessException.class, () -> parser.parseCode(response));
    }
}
