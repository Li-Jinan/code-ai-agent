-- Run once with every app-service instance stopped and after taking a DB backup.
-- Existing in-flight work is interrupted by the schema rollout and can be retried.
USE code_ai_agent;

UPDATE generation_task
SET status = 'failed',
    errorMessage = '生成服务升级中断了任务，可重新提交。',
    finishTime = CURRENT_TIMESTAMP
WHERE status IN ('pending', 'running');

ALTER TABLE generation_task
    ADD COLUMN heartbeat_time DATETIME NULL COMMENT 'last generation task heartbeat' AFTER finishTime,
    ADD COLUMN running_app_id BIGINT NULL COMMENT 'unique active app generation slot' AFTER heartbeat_time,
    ADD UNIQUE KEY uk_generation_task_running_app (running_app_id);
