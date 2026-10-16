package com.jinan.codeaiagent.user.config;

import com.mybatisflex.core.query.QueryWrapper;
import com.jinan.codeaiagent.model.entity.User;
import com.jinan.codeaiagent.model.enums.UserRoleEnum;
import com.jinan.codeaiagent.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Creates or repairs the public demo account when explicitly enabled.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "demo.account", name = "enabled", havingValue = "true")
public class DemoAccountInitializer implements ApplicationRunner {

    private final UserService userService;

    @Value("${demo.account.username:demo}")
    private String username;

    @Value("${demo.account.password:demo123456}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank() || password == null || password.length() < 8) {
            throw new IllegalStateException("Demo account username or password is invalid");
        }
        User user = userService.getOne(QueryWrapper.create().eq("userAccount", username));
        boolean isNew = user == null;
        if (isNew) {
            user = new User();
            user.setUserAccount(username);
        }
        user.setUserName("演示账号");
        user.setUserAvatar("/userAvatar.svg");
        user.setUserRole(UserRoleEnum.USER.getValue());
        user.setUserPassword(userService.getEncryptPassword(password));
        boolean saved = isNew ? userService.save(user) : userService.updateById(user);
        if (!saved) {
            throw new IllegalStateException("Failed to initialize demo account");
        }
        log.info("Demo account '{}' is ready", username);
    }
}
