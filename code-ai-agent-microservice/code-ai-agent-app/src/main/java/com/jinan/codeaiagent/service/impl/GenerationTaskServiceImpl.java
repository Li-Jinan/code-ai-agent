package com.jinan.codeaiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jinan.codeaiagent.config.GenerationTaskExecutorProperties;
import com.jinan.codeaiagent.constant.AppConstant;
import com.jinan.codeaiagent.constant.UserConstant;
import com.jinan.codeaiagent.exception.BusinessException;
import com.jinan.codeaiagent.exception.ErrorCode;
import com.jinan.codeaiagent.exception.ThrowUtils;
import com.jinan.codeaiagent.mapper.GenerationTaskMapper;
import com.jinan.codeaiagent.model.entity.App;
import com.jinan.codeaiagent.model.entity.GenerationTask;
import com.jinan.codeaiagent.model.entity.User;
import com.jinan.codeaiagent.model.enums.CodeGenTypeEnum;
import com.jinan.codeaiagent.model.enums.GenerationTaskStatusEnum;
import com.jinan.codeaiagent.service.AppService;
import com.jinan.codeaiagent.service.GenerationTaskService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

@Service
@Slf4j
public class GenerationTaskServiceImpl extends ServiceImpl<GenerationTaskMapper, GenerationTask>
        implements GenerationTaskService {

    @Resource
    @Lazy
    private AppService appService;

    @Resource
    private GenerationTaskMapper generationTaskMapper;

    @Resource(name = "generationTaskExecutor")
    private ThreadPoolExecutor generationTaskExecutor;

    @Resource
    private GenerationTaskExecutorProperties executorProperties;

    /** 只为当前进程实际接收的任务续租，其他实例的任务由其所属实例续租。 */
    private final Set<Long> locallyOwnedTaskIds = ConcurrentHashMap.newKeySet();

    @Override
    public GenerationTask startGenerationTask(Long appId, String message, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");

        GenerationTask task = GenerationTask.builder()
                .appId(appId)
                .userId(loginUser.getId())
                .message(message)
                .status(GenerationTaskStatusEnum.PENDING.getValue())
                .runningAppId(appId)
                .heartbeatTime(LocalDateTime.now())
                .build();
        boolean saved;
        try {
            saved = this.save(task);
        } catch (DuplicateKeyException duplicateKeyException) {
            GenerationTask existingTask = generationTaskMapper.selectActiveByAppId(appId);
            if (existingTask != null) {
                checkTaskAuth(existingTask, loginUser);
                return existingTask;
            }
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST,
                    "该应用已有生成任务正在排队或执行，请稍后重试");
        }
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建生成任务失败");
        locallyOwnedTaskIds.add(task.getId());
        try {
            generationTaskExecutor.execute(() -> {
                try {
                    runTask(task.getId(), appId, message, loginUser);
                } finally {
                    locallyOwnedTaskIds.remove(task.getId());
                }
            });
        } catch (RejectedExecutionException rejectedExecutionException) {
            locallyOwnedTaskIds.remove(task.getId());
            generationTaskMapper.transitionStatus(task.getId(),
                    GenerationTaskStatusEnum.PENDING.getValue(),
                    GenerationTaskStatusEnum.FAILED.getValue(),
                    "当前生成队列已满，请稍后重试。", null, LocalDateTime.now());
            log.warn("生成队列已满，拒绝任务，taskId: {}, active: {}, queued: {}",
                    task.getId(), generationTaskExecutor.getActiveCount(), generationTaskExecutor.getQueue().size());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "当前生成队列已满，请稍后重试");
        }
        log.info("生成任务已入队，taskId: {}, userId: {}, appId: {}, active: {}, queued: {}",
                task.getId(), loginUser.getId(), appId,
                generationTaskExecutor.getActiveCount(), generationTaskExecutor.getQueue().size());
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

    private void runTask(Long taskId, Long appId, String message, User loginUser) {
        int started = generationTaskMapper.transitionStatus(taskId,
                GenerationTaskStatusEnum.PENDING.getValue(),
                GenerationTaskStatusEnum.RUNNING.getValue(), null, LocalDateTime.now(), null);
        if (started == 0) {
            log.warn("生成任务已被其他流程回收或状态已变化，跳过执行，taskId: {}", taskId);
            return;
        }
        try {
            List<String> chunks = appService.chatToGenCode(appId, message, loginUser)
                    .collectList()
                    .block(executorProperties.getTimeout());
            if (containsGenerationFailure(chunks)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回格式异常，请点击重试。");
            }
            App app = appService.getById(appId);
            if (!hasGeneratedArtifact(app)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成未产出可预览文件，请点击重试。");
            }
            generationTaskMapper.transitionStatus(taskId,
                    GenerationTaskStatusEnum.RUNNING.getValue(),
                    GenerationTaskStatusEnum.SUCCEEDED.getValue(), null, null, LocalDateTime.now());
        } catch (Exception exception) {
            log.error("后台生成任务失败，taskId: {}, appId: {}", taskId, appId, exception);
            generationTaskMapper.transitionStatus(taskId,
                    GenerationTaskStatusEnum.RUNNING.getValue(),
                    GenerationTaskStatusEnum.FAILED.getValue(),
                    getFriendlyErrorMessage(exception), null, LocalDateTime.now());
        } finally {
            locallyOwnedTaskIds.remove(taskId);
        }
    }

    /**
     * A background task cannot survive an app-service restart. Mark such records
     * as failed so users can retry instead of seeing a permanent running state.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        reclaimStaleTasks();
    }

    /** 定期续租本实例的排队/运行任务，避免长时间生成被判定为失联。 */
    @Scheduled(fixedDelayString = "${generation.task.heartbeat-interval-millis:30000}")
    public void maintainTaskHeartbeats() {
        locallyOwnedTaskIds.forEach(generationTaskMapper::touchHeartbeat);
    }

    /** 每分钟扫描心跳过期的活动任务；条件更新避免覆盖并发完成的任务。 */
    @Scheduled(fixedDelayString = "${generation.task.recovery-interval-millis:60000}")
    void reclaimStaleTasks() {
        reclaimStaleTasksNow();
    }

    int reclaimStaleTasksNow() {
        LocalDateTime cutoff = LocalDateTime.now().minus(executorProperties.getStaleAfter());
        List<GenerationTask> staleTasks = generationTaskMapper.selectStaleActiveTasks(cutoff);
        int reclaimed = 0;
        for (GenerationTask task : staleTasks) {
            if (generationTaskMapper.failIfStale(task.getId(), cutoff) > 0) {
                locallyOwnedTaskIds.remove(task.getId());
                reclaimed++;
            }
        }
        if (reclaimed > 0) {
            log.warn("已回收 {} 个心跳超时的生成任务", reclaimed);
        }
        return reclaimed;
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
            case VUE_PROJECT -> new File(appDir, "dist/index.html").isFile();
        };
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
        String message = collectErrorMessages(error).toLowerCase();
        if (StrUtil.containsAny(message, "429", "rate limit", "too many requests", "server busy")) {
            return "DeepSeek 当前请求较多，请稍后重试。";
        }
        if (StrUtil.containsAny(message, "401", "unauthorized", "invalid api key", "authentication")) {
            return "AI 服务配置异常，请联系管理员检查 DeepSeek API Key。";
        }
        if (StrUtil.containsAny(message, "402", "insufficient balance", "insufficient quota")) {
            return "DeepSeek 账户额度不足，请联系管理员充值后重试。";
        }
        if (StrUtil.containsAny(message, "timeout", "timed out")) {
            return "DeepSeek 响应超时，请精简需求后重试。";
        }
        if (StrUtil.containsAny(message, "connection refused", "connection reset", "unknown host", "network")) {
            return "暂时无法连接 DeepSeek，请稍后重试。";
        }
        if (StrUtil.containsAny(message, "messages with role", "tool_calls", "invalid_request_error")) {
            return "生成上下文异常，请点击重试。";
        }
        if (StrUtil.containsAny(message, "jsonparseexception", "unexpected character")) {
            return "AI 返回格式异常，请点击重试。";
        }
        if (StrUtil.containsAny(message, "返回格式异常", "无法生成可预览文件", "未产出可预览文件")) {
            return "AI 返回内容无法生成可预览文件，请点击重试。";
        }
        if (StrUtil.containsAny(message, "vue 项目构建失败", "npm", "build failed")) {
            return "Vue 项目构建失败，请稍后重试。";
        }
        return "生成过程中出现异常，请点击重试。";
    }

    private String collectErrorMessages(Throwable error) {
        StringBuilder messages = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (StrUtil.isNotBlank(current.getMessage())) {
                messages.append(' ').append(current.getMessage());
            }
            current = current.getCause();
            depth++;
        }
        return messages.toString();
    }
}
