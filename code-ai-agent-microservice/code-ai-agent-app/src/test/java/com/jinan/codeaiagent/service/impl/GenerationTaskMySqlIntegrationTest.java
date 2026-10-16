package com.jinan.codeaiagent.service.impl;

import com.jinan.codeaiagent.mapper.GenerationTaskMapper;
import com.jinan.codeaiagent.model.entity.GenerationTask;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database-level integration coverage for the MySQL invariants used by
 * GenerationTaskServiceImpl and GenerationTaskMapper. It requires an explicitly
 * configured, isolated database named generation_task_it; it never selects the
 * production schema by default.
 */
@EnabledIfSystemProperty(
        named = "mysql.it.url",
        matches = "jdbc:mysql://[^/]+/generation_task_it(?:\\?.*)?"
)
class GenerationTaskMySqlIntegrationTest {

    private static final int CONTENDER_COUNT = 10;
    private static final long APP_ID = 99001L;
    private static final long USER_ID = 99002L;
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void createSchema() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS generation_task (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        appId BIGINT NOT NULL,
                        userId BIGINT NOT NULL,
                        message TEXT NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        errorMessage VARCHAR(512) NULL,
                        startTime DATETIME NULL,
                        finishTime DATETIME NULL,
                        heartbeat_time DATETIME NULL,
                        running_app_id BIGINT NULL,
                        createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        isDelete TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_generation_task_running_app (running_app_id)
                    ) ENGINE=InnoDB
                    """);
        }
        Configuration configuration = new Configuration(new Environment("mysql-it", new JdbcTransactionFactory(),
                new UnpooledDataSource("com.mysql.cj.jdbc.Driver", jdbcUrl(), username(), password())));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(GenerationTaskMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void clearRows() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM generation_task");
        }
    }

    @Test
    void mysqlUniqueIndexAllowsOnlyOneConcurrentClaimAndReleasesSlotOnCompletion() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(CONTENDER_COUNT);
        CountDownLatch ready = new CountDownLatch(CONTENDER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger duplicateKeys = new AtomicInteger();
        List<Future<?>> results = new ArrayList<>();

        try {
            for (int i = 0; i < CONTENDER_COUNT; i++) {
                int contender = i;
                results.add(callers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try (Connection connection = connection()) {
                        connection.setAutoCommit(false);
                        try (PreparedStatement insert = connection.prepareStatement("""
                                INSERT INTO generation_task (appId, userId, message, status, heartbeat_time, running_app_id)
                                VALUES (?, ?, ?, 'pending', CURRENT_TIMESTAMP, ?)
                                """)) {
                            insert.setLong(1, APP_ID);
                            insert.setLong(2, USER_ID);
                            insert.setString(3, "concurrent contender " + contender);
                            insert.setLong(4, APP_ID);
                            insert.executeUpdate();
                            connection.commit();
                            inserted.incrementAndGet();
                        } catch (SQLException exception) {
                            connection.rollback();
                            if (exception.getErrorCode() == 1062) {
                                duplicateKeys.incrementAndGet();
                            } else {
                                throw exception;
                            }
                        }
                    }
                    return null;
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "all contenders should be ready");
            start.countDown();
            for (Future<?> result : results) {
                result.get(20, TimeUnit.SECONDS);
            }

            assertEquals(1, inserted.get(), "MySQL unique key must admit one active task per app");
            assertEquals(CONTENDER_COUNT - 1, duplicateKeys.get());
            assertEquals(1, countRowsForActiveSlot(APP_ID));

            long activeTaskId = findActiveTaskId(APP_ID);
            GenerationTask visibleActiveTask = withMapper(mapper -> mapper.selectActiveByAppId(APP_ID));
            assertNotNull(visibleActiveTask);
            assertEquals(activeTaskId, visibleActiveTask.getId(),
                    "duplicate-submit lookup must return the row that won the database race");
            assertTrue(transitionToTerminal(activeTaskId, "succeeded"));
            assertEquals(0, countRowsForActiveSlot(APP_ID), "terminal transition must release app slot");

            insertTask(APP_ID, "next task");
            assertEquals(1, countRowsForActiveSlot(APP_ID), "released slot must be claimable again");

            transitionToTerminal(findActiveTaskId(APP_ID), "succeeded");
            int nullSlotsBefore = countRowsWithNullSlot();
            insertUnslottedTask(APP_ID + 1, "row without an active slot");
            insertUnslottedTask(APP_ID + 2, "another row without an active slot");
            assertEquals(nullSlotsBefore + 2, countRowsWithNullSlot(),
                    "MySQL unique indexes must allow multiple NULL slots");
        } finally {
            start.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void staleHeartbeatIsConditionallyFailedAndReleasesSlotExactlyOnce() throws SQLException {
        long taskId = insertTask(APP_ID, "stale task");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE generation_task SET status = 'running', " +
                    "heartbeat_time = CURRENT_TIMESTAMP - INTERVAL 6 MINUTE, " +
                    "startTime = CURRENT_TIMESTAMP - INTERVAL 7 MINUTE WHERE id = " + taskId);
        }

        assertTrue(selectStaleTaskIds().contains(taskId), "mapper stale scan must find the expired task");
        int reclaimed = failIfStale(taskId);
        int duplicateReclaim = failIfStale(taskId);

        assertEquals(1, reclaimed, "stale task should be reclaimed");
        assertEquals(0, duplicateReclaim, "conditional update should not reclaim a terminal task twice");
        assertEquals("failed", taskStatus(taskId));
        assertEquals("生成中断，可重试。", taskError(taskId));
        assertNull(taskRunningAppId(taskId), "reclamation must release app slot");

        insertTask(APP_ID, "retry after reclamation");
        assertEquals(1, countRowsForActiveSlot(APP_ID), "a retry should be able to claim the released slot");
    }

    @Test
    void freshHeartbeatIsExcludedAndCompletionRacesRecoveryWithCas() throws Exception {
        long taskId = insertTask(APP_ID, "fresh task");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE generation_task SET status = 'running', " +
                    "heartbeat_time = CURRENT_TIMESTAMP, startTime = CURRENT_TIMESTAMP WHERE id = " + taskId);
        }

        withMapper(mapper -> mapper.touchHeartbeat(taskId));
        assertFalse(selectStaleTaskIds().contains(taskId), "mapper stale scan must exclude a fresh heartbeat");
        assertEquals(0, failIfStale(taskId), "fresh task must not be reclaimed");

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE generation_task SET " +
                    "heartbeat_time = CURRENT_TIMESTAMP - INTERVAL 6 MINUTE WHERE id = " + taskId);
        }
        assertTrue(selectStaleTaskIds().contains(taskId));

        ExecutorService racers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> reclaim = racers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return failIfStale(taskId);
            });
            Future<Integer> complete = racers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return withMapper(mapper -> mapper.transitionStatus(taskId, "running", "succeeded",
                        null, null, java.time.LocalDateTime.now()));
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int reclaimed = reclaim.get(20, TimeUnit.SECONDS);
            int completed = complete.get(20, TimeUnit.SECONDS);

            assertEquals(1, reclaimed + completed,
                    "conditional terminal updates must allow exactly one race winner");
        } finally {
            start.countDown();
            racers.shutdownNow();
        }

        assertTrue(Set.of("failed", "succeeded").contains(taskStatus(taskId)));
        assertNull(taskRunningAppId(taskId));
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), username(), password());
    }

    private static String jdbcUrl() {
        return System.getProperty("mysql.it.url");
    }

    private static String username() {
        return System.getProperty("mysql.it.username", "root");
    }

    private static String password() {
        return System.getProperty("mysql.it.password", "");
    }

    private static <T> T withMapper(Function<GenerationTaskMapper, T> action) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return action.apply(session.getMapper(GenerationTaskMapper.class));
        }
    }

    private static long insertTask(long appId, String message) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO generation_task (appId, userId, message, status, heartbeat_time, running_app_id)
                VALUES (?, ?, ?, 'pending', CURRENT_TIMESTAMP, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, appId);
            statement.setLong(2, USER_ID);
            statement.setString(3, message);
            statement.setLong(4, appId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private static void insertUnslottedTask(long appId, String message) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO generation_task (appId, userId, message, status, heartbeat_time, running_app_id)
                VALUES (?, ?, ?, 'succeeded', CURRENT_TIMESTAMP, NULL)
                """)) {
            statement.setLong(1, appId);
            statement.setLong(2, USER_ID);
            statement.setString(3, message);
            statement.executeUpdate();
        }
    }

    private static int failIfStale(long taskId) throws SQLException {
        return withMapper(mapper -> mapper.failIfStale(taskId,
                java.time.LocalDateTime.now().minusMinutes(5)));
    }

    private static Set<Long> selectStaleTaskIds() throws SQLException {
        return withMapper(mapper -> mapper.selectStaleActiveTasks(
                        java.time.LocalDateTime.now().minusMinutes(5)))
                .stream()
                .map(GenerationTask::getId)
                .collect(Collectors.toSet());
    }

    private static boolean transitionToTerminal(long taskId, String terminalStatus) throws SQLException {
        String expectedStatus = taskStatus(taskId);
        return withMapper(mapper -> mapper.transitionStatus(taskId, expectedStatus, terminalStatus,
                null, null, java.time.LocalDateTime.now()) == 1);
    }

    private static int countRowsForActiveSlot(long appId) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM generation_task WHERE running_app_id = ?")) {
            statement.setLong(1, appId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static int countRowsWithNullSlot() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM generation_task WHERE running_app_id IS NULL")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static long findActiveTaskId(long appId) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM generation_task WHERE running_app_id = ?")) {
            statement.setLong(1, appId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static String taskStatus(long taskId) throws SQLException {
        return stringColumn(taskId, "status");
    }

    private static String taskError(long taskId) throws SQLException {
        return stringColumn(taskId, "errorMessage");
    }

    private static String stringColumn(long taskId, String column) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM generation_task WHERE id = ?")) {
            statement.setLong(1, taskId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static Long taskRunningAppId(long taskId) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT running_app_id FROM generation_task WHERE id = ?")) {
            statement.setLong(1, taskId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                long value = result.getLong(1);
                return result.wasNull() ? null : value;
            }
        }
    }
}
