package com.jinan.codeaiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 邮箱验证码重置密码请求
 */
@Data
public class UserPasswordResetRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userEmail;

    private String emailCode;

    private String newPassword;

    private String checkPassword;
}
