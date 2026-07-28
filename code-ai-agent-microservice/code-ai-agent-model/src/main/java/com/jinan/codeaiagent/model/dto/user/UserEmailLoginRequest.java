package com.jinan.codeaiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 邮箱验证码登录请求
 */
@Data
public class UserEmailLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邮箱
     */
    private String userEmail;

    /**
     * 验证码
     */
    private String emailCode;
}
