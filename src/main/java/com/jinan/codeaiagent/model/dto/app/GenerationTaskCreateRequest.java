package com.jinan.codeaiagent.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class GenerationTaskCreateRequest implements Serializable {

    private Long appId;

    private String message;
}
