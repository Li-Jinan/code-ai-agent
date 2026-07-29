package com.jinan.codeaiagent.model.enums;

import lombok.Getter;

@Getter
public enum GenerationTaskStatusEnum {

    PENDING("等待中", "pending"),
    RUNNING("生成中", "running"),
    SUCCEEDED("已完成", "succeeded"),
    FAILED("失败", "failed");

    private final String text;

    private final String value;

    GenerationTaskStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
}
