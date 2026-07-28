package com.jinan.codeaiagent.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.jinan.codeaiagent.exception.BusinessException;
import com.jinan.codeaiagent.exception.ErrorCode;
import com.jinan.codeaiagent.model.dto.user.UserQueryRequest;
import com.jinan.codeaiagent.model.entity.User;
import com.jinan.codeaiagent.user.mapper.UserMapper;
import com.jinan.codeaiagent.model.enums.UserRoleEnum;
import com.jinan.codeaiagent.model.vo.LoginUserVO;
import com.jinan.codeaiagent.model.vo.UserVO;
import com.jinan.codeaiagent.user.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.jinan.codeaiagent.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String DEFAULT_USER_AVATAR = "/userAvatar.svg";
    private static final String DEFAULT_USER_NAME = "新用户";
    private static final String EMAIL_CODE_KEY_PREFIX = "code-ai-agent:user:email-login:";
    private static final String EMAIL_CODE_LIMIT_KEY_PREFIX = "code-ai-agent:user:email-login-limit:";
    private static final Duration EMAIL_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration EMAIL_CODE_SEND_INTERVAL = Duration.ofSeconds(60);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Override
    public long userRegister(String userAccount, String userEmail, String userName, String userPassword, String checkPassword) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userEmail, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (!isEmail(userEmail)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 查询用户是否已存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        QueryWrapper emailQueryWrapper = new QueryWrapper();
        emailQueryWrapper.eq("userEmail", userEmail);
        long emailCount = this.mapper.selectCountByQuery(emailQueryWrapper);
        if (emailCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱已被注册");
        }
        // 3. 加密密码
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 创建用户，插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserEmail(userEmail);
        user.setUserPassword(encryptPassword);
        user.setUserName(StrUtil.blankToDefault(userName, DEFAULT_USER_NAME).trim());
        user.setUserAvatar(DEFAULT_USER_AVATAR);
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        // 2. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        if (isEmail(userAccount)) {
            queryWrapper.eq("userEmail", userAccount);
        } else {
            queryWrapper.eq("userAccount", userAccount);
        }
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 4. 如果用户存在，记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 5. 返回脱敏的用户信息
        return this.getLoginUserVO(user);
    }

    @Override
    public boolean sendEmailLoginCode(String userEmail) {
        if (!isEmail(userEmail)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        }
        if (StrUtil.isBlank(mailFrom)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "邮件发送账号未配置");
        }
        QueryWrapper queryWrapper = QueryWrapper.create().eq("userEmail", userEmail);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱未注册");
        }
        String limitKey = EMAIL_CODE_LIMIT_KEY_PREFIX + userEmail;
        Boolean canSend = stringRedisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", EMAIL_CODE_SEND_INTERVAL);
        if (Boolean.FALSE.equals(canSend)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码发送太频繁，请稍后再试");
        }
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        String codeKey = EMAIL_CODE_KEY_PREFIX + userEmail;
        stringRedisTemplate.opsForValue().set(codeKey, code, EMAIL_CODE_TTL);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(userEmail);
            message.setSubject("今安 AI 应用登录验证码");
            message.setText("你的登录验证码是：" + code + "，5 分钟内有效。若非本人操作，请忽略本邮件。");
            javaMailSender.send(message);
            return true;
        } catch (Exception e) {
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(limitKey);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码发送失败，请检查邮箱配置");
        }
    }

    @Override
    public LoginUserVO userEmailCodeLogin(String userEmail, String emailCode, HttpServletRequest request) {
        if (!isEmail(userEmail) || StrUtil.isBlank(emailCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        String codeKey = EMAIL_CODE_KEY_PREFIX + userEmail;
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (StrUtil.isBlank(cachedCode) || !cachedCode.equals(emailCode.trim())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
        }
        QueryWrapper queryWrapper = QueryWrapper.create().eq("userEmail", userEmail);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }
        stringRedisTemplate.delete(codeKey);
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询当前用户信息
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userEmail = userQueryRequest.getUserEmail();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id) // where id = ${id}
                .eq("userRole", userRole) // and userRole = ${userRole}
                .like("userAccount", userAccount)
                .like("userEmail", userEmail)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "jinan";
        return DigestUtils.md5DigestAsHex((userPassword + SALT).getBytes(StandardCharsets.UTF_8));
    }

    private boolean isEmail(String value) {
        return StrUtil.isNotBlank(value) && value.matches(EMAIL_PATTERN);
    }
}
