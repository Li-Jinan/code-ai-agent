package com.jinan.codeaiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户修改密码请求
 */
@Data
public class UserPasswordUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String oldPassword;

    private String newPassword;

    private String checkPassword;
}
