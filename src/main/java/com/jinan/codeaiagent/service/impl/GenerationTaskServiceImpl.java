package com.jinan.codeaiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jinan.codeaiagent.constant.AppConstant;
import com.jinan.codeaiagent.constant.UserConstant;
import com.jinan.codeaiagent.exception.BusinessException;
import com.jinan.codeaiagent.exception.ErrorCode;
import com.jinan.codeaiagent.exception.ThrowUtils;
import com.jinan.codeaiagent.mapper.GenerationTaskMapper;
import com.jinan.codeaiagent.model.entity.App;
import com.jinan.codeaiagent.model.entity.GenerationTask;
import com.jinan.codeaiagent.model.entity.User;
import com.jinan.codeaiagent.model.enums.GenerationTaskStatusEnum;
import com.jinan.codeaiagent.model.enums.CodeGenTypeEnum;
import com.jinan.codeaiagent.service.AppService;
import com.jinan.codeaiagent.service.GenerationTaskService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.io.File;
import java.util.List;

@Service
@Slf4j
public class GenerationTaskServiceImpl extends ServiceImpl<GenerationTaskMapper, GenerationTask>
        implements GenerationTaskService {

    @Resource
    @Lazy
    private AppService appService;

    @Override
    public GenerationTask startGenerationTask(Long appId, String message, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");

        GenerationTask runningTask = getRunningTask(appId, loginUser.getId());
        if (runningTask != null) {
            return runningTask;
        }

        GenerationTask task = GenerationTask.builder()
                .appId(appId)
                .userId(loginUser.getId())
                .message(message)
                .status(GenerationTaskStatusEnum.PENDING.getValue())
                .build();
        boolean saved = this.save(task);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建生成任务失败");
        Thread.startVirtualThread(() -> runTask(task.getId(), appId, message, loginUser));
        return task;
    }

    @Override
    public GenerationTask getLatestTask(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .orderBy("createTime", false)
                .limit(1);
        GenerationTask task = this.getOne(queryWrapper);
        if (task != null) {
            checkTaskAuth(task, loginUser);
            normalizeSucceededTask(task);
        }
        return task;
    }

    @Override
    public GenerationTask getTask(Long taskId, User loginUser) {
        ThrowUtils.throwIf(taskId == null || taskId <= 0, ErrorCode.PARAMS_ERROR, "任务 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        GenerationTask task = this.getById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "生成任务不存在");
        checkTaskAuth(task, loginUser);
        normalizeSucceededTask(task);
        return task;
    }

    private GenerationTask getRunningTask(Long appId, Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .in("status", List.of(
                        GenerationTaskStatusEnum.PENDING.getValue(),
                        GenerationTaskStatusEnum.RUNNING.getValue()
                ))
                .orderBy("createTime", false)
                .limit(1);
        return this.getOne(queryWrapper);
    }

    private void runTask(Long taskId, Long appId, String message, User loginUser) {
        updateTaskStatus(taskId, GenerationTaskStatusEnum.RUNNING, null, LocalDateTime.now(), null);
        try {
            List<String> chunks = appService.chatToGenCode(appId, message, loginUser).collectList().block();
            if (containsGenerationFailure(chunks)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回格式异常，请点击重试。");
            }
            App app = appService.getById(appId);
            if (!hasGeneratedArtifact(app)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成未产出可预览文件，请点击重试。");
            }
            updateTaskStatus(taskId, GenerationTaskStatusEnum.SUCCEEDED, null, null, LocalDateTime.now());
        } catch (Exception e) {
            log.error("后台生成任务失败，taskId: {}, appId: {}", taskId, appId, e);
            updateTaskStatus(taskId, GenerationTaskStatusEnum.FAILED,
                    getFriendlyErrorMessage(e), null, LocalDateTime.now());
        }
    }

    private boolean containsGenerationFailure(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return true;
        }
        return chunks.stream()
                .filter(StrUtil::isNotBlank)
                .anyMatch(this::isFailureMessage);
    }

    private boolean isFailureMessage(String message) {
        return StrUtil.containsAny(message, "AI回复失败", "生成失败", "JsonParseException", "Unexpected character",
                "系统错误", "tool_calls", "Messages with role");
    }

    private boolean hasGeneratedArtifact(App app) {
        if (app == null || app.getId() == null || StrUtil.isBlank(app.getCodeGenType())) {
            return false;
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == null) {
            return false;
        }
        File appDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, app.getCodeGenType() + "_" + app.getId());
        return switch (codeGenTypeEnum) {
            case HTML, MULTI_FILE -> new File(appDir, "index.html").isFile();
            case VUE_PROJECT -> new File(appDir, "dist/index.html").isFile() || new File(appDir, "index.html").isFile();
        };
    }

    private void updateTaskStatus(Long taskId, GenerationTaskStatusEnum status, String errorMessage,
                                  LocalDateTime startTime, LocalDateTime finishTime) {
        GenerationTask updateTask = new GenerationTask();
        updateTask.setId(taskId);
        updateTask.setStatus(status.getValue());
        updateTask.setErrorMessage(errorMessage);
        if (startTime != null) {
            updateTask.setStartTime(startTime);
        }
        if (finishTime != null) {
            updateTask.setFinishTime(finishTime);
        }
        this.updateById(updateTask);
    }

    private void checkTaskAuth(GenerationTask task, User loginUser) {
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = task.getUserId().equals(loginUser.getId());
        if (!isAdmin && !isCreator) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看该生成任务");
        }
    }

    private void normalizeSucceededTask(GenerationTask task) {
        if (task == null || !GenerationTaskStatusEnum.SUCCEEDED.getValue().equals(task.getStatus())) {
            return;
        }
        App app = appService.getById(task.getAppId());
        if (hasGeneratedArtifact(app)) {
            return;
        }
        task.setStatus(GenerationTaskStatusEnum.FAILED.getValue());
        task.setErrorMessage("生成未产出可预览文件，请点击重试。");
        task.setFinishTime(LocalDateTime.now());
        this.updateById(task);
    }

    private String getFriendlyErrorMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        if (message != null && StrUtil.containsAny(message, "Messages with role", "tool_calls", "invalid_request_error")) {
            return "生成上下文异常，请点击重试。";
        }
        if (message != null && StrUtil.containsAny(message, "JsonParseException", "Unexpected character")) {
            return "AI 返回格式异常，请点击重试。";
        }
        if (message != null && StrUtil.isNotBlank(message)
                && !message.contains("{\"error\"")
                && !message.contains("Exception")
                && !message.contains("java.")) {
            return message;
        }
        return "生成过程中出现异常，请点击重试。";
    }
}
