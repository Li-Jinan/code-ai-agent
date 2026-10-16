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

    /** 截止时间由数据库时钟计算，与写入心跳的 CURRENT_TIMESTAMP 同源，避免 JVM 与 MySQL 时区不一致时误判。 */
    @Select("SELECT * FROM generation_task WHERE status IN ('pending', 'running') AND isDelete = 0 " +
            "AND (heartbeat_time IS NULL OR heartbeat_time < CURRENT_TIMESTAMP - INTERVAL #{staleAfterSeconds} SECOND)")
    List<GenerationTask> selectStaleActiveTasks(@Param("staleAfterSeconds") long staleAfterSeconds);

    @Update("UPDATE generation_task SET status = 'failed', running_app_id = NULL, " +
            "errorMessage = '生成中断，可重试。', finishTime = CURRENT_TIMESTAMP " +
            "WHERE id = #{taskId} AND status IN ('pending', 'running') AND isDelete = 0 " +
            "AND (heartbeat_time IS NULL OR heartbeat_time < CURRENT_TIMESTAMP - INTERVAL #{staleAfterSeconds} SECOND)")
    int failIfStale(@Param("taskId") Long taskId, @Param("staleAfterSeconds") long staleAfterSeconds);
}
