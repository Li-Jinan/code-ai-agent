package com.jinan.codeaiagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicUrlConfigurationValidatorTest {

    @Test
    void acceptsSeparateHttpsOrigins() {
        assertDoesNotThrow(() -> PublicUrlConfigurationValidator.validateOrigins(
                "https://ai.lijinan.cn",
                "https://preview.lijinan.cn"
        ));
    }

    @Test
    void rejectsMixedContentPreviewOrigin() {
        assertThrows(IllegalStateException.class, () -> PublicUrlConfigurationValidator.validateOrigins(
                "https://ai.lijinan.cn",
                "http://preview.lijinan.cn:8080"
        ));
    }

    @Test
    void rejectsSameOrigin() {
        assertThrows(IllegalStateException.class, () -> PublicUrlConfigurationValidator.validateOrigins(
                "https://ai.lijinan.cn",
                "https://ai.lijinan.cn"
        ));
    }

    @Test
    void rejectsPathAndQuery() {
        assertThrows(IllegalStateException.class, () -> PublicUrlConfigurationValidator.validateOrigins(
                "https://ai.lijinan.cn/app",
                "https://preview.lijinan.cn?legacy=true"
        ));
    }
}
