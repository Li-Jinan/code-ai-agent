package com.jinan.codeaiagent.service.impl;

import com.jinan.codeaiagent.innerservice.InnerScreenshotService;
import com.jinan.codeaiagent.service.ScreenshotService;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class InnerScreenshotServiceImpl implements InnerScreenshotService {

    @Resource
    private ScreenshotService screenshotService;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        return screenshotService.generateAndUploadScreenshot(webUrl);
    }
}