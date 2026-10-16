package com.jinan.codeaiagent.mapper;

import com.jinan.codeaiagent.model.entity.GenerationTask;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface GenerationTaskMapper extends BaseMapper<GenerationTask> {

    @Update("UPDATE generation_task SET status = #{newStatus}, errorMessage = #{errorMessage}, " +
            "startTime = COALESCE(#{startTime}, startTime), " +
            "finishTime = COALESCE(#{finishTime}, finishTime), " +
            "heartbeat_time = CASE WHEN #{newStatus} = 'running' THEN CURRENT_TIMESTAMP ELSE heartbeat_time END, " +
            "running_app_id = CASE WHEN #{newStatus} IN ('pending', 'running') THEN running_app_id ELSE NULL END " +
            "WHERE id = #{taskId} AND status = #{expectedStatus} AND isDelete = 0")
    int transitionStatus(@Param("taskId") Long taskId,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("newStatus") String newStatus,
                         @Param("errorMessage") String errorMessage,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("finishTime") LocalDateTime finishTime);

    @Update("UPDATE generation_task SET heartbeat_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{taskId} AND status IN ('pending', 'running') AND isDelete = 0")
    int touchHeartbeat(@Param("taskId") Long taskId);

    @Select("SELECT * FROM generation_task WHERE running_app_id = #{appId} " +
            "AND status IN ('pending', 'running') AND isDelete = 0 ORDER BY createTime DESC LIMIT 1")
    GenerationTask selectActiveByAppId(@Param("appId") Long appId);

    @Select("SELECT * FROM generation_task WHERE status IN ('pending', 'running') AND isDelete = 0 " +
            "AND (heartbeat_time IS NULL OR heartbeat_time < #{cutoff})")
    List<GenerationTask> selectStaleActiveTasks(@Param("cutoff") LocalDateTime cutoff);

    @Update("UPDATE generation_task SET status = 'failed', running_app_id = NULL, " +
            "errorMessage = '生成中断，可重试。', finishTime = CURRENT_TIMESTAMP " +
            "WHERE id = #{taskId} AND status IN ('pending', 'running') AND isDelete = 0 " +
            "AND (heartbeat_time IS NULL OR heartbeat_time < #{cutoff})")
    int failIfStale(@Param("taskId") Long taskId, @Param("cutoff") LocalDateTime cutoff);
}
