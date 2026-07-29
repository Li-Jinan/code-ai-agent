package com.jinan.codeaiagent.service;

import com.jinan.codeaiagent.model.entity.GenerationTask;
import com.jinan.codeaiagent.model.entity.User;
import com.mybatisflex.core.service.IService;

public interface GenerationTaskService extends IService<GenerationTask> {

    GenerationTask startGenerationTask(Long appId, String message, User loginUser);

    GenerationTask getLatestTask(Long appId, User loginUser);

    GenerationTask getTask(Long taskId, User loginUser);
}
