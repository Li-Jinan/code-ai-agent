package com.jinan.codeaiagent.core.saver;

import cn.hutool.core.util.StrUtil;
import com.jinan.codeaiagent.ai.model.HtmlCodeResult;
import com.jinan.codeaiagent.exception.BusinessException;
import com.jinan.codeaiagent.exception.ErrorCode;
import com.jinan.codeaiagent.model.enums.CodeGenTypeEnum;

/**
 * HTML代码文件保存器
 *
 * @author jinan
 */
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    @Override
    protected void validateInput(HtmlCodeResult result) {
        super.validateInput(result);
        // HTML 代码不能为空
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML 代码不能为空");
        }
    }
}
