CREATE DATABASE IF NOT EXISTS code_ai_agent
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE code_ai_agent;

CREATE TABLE IF NOT EXISTS user
(
    id           BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    userAccount  VARCHAR(256)                            NOT NULL COMMENT 'account',
    userEmail    VARCHAR(256)                            NULL COMMENT 'email',
    userPassword VARCHAR(512)                            NOT NULL COMMENT 'password',
    userName     VARCHAR(256)                            NULL COMMENT 'nickname',
    userAvatar   VARCHAR(1024) DEFAULT '/userAvatar.svg' NULL COMMENT 'avatar',
    userProfile  VARCHAR(512)                            NULL COMMENT 'profile',
    userRole     VARCHAR(256)  DEFAULT 'user'            NOT NULL COMMENT 'role',
    editTime     DATETIME      DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT 'edit time',
    createTime   DATETIME      DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT 'create time',
    updateTime   DATETIME      DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    isDelete     TINYINT       DEFAULT 0                  NOT NULL COMMENT 'deleted',
    UNIQUE KEY uk_userAccount (userAccount),
    UNIQUE KEY uk_userEmail (userEmail),
    INDEX idx_userName (userName)
) COMMENT 'user' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app
(
    id           BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    appName      VARCHAR(256)                       NULL COMMENT 'app name',
    cover        VARCHAR(512)                       NULL COMMENT 'cover',
    initPrompt   TEXT                               NULL COMMENT 'initial prompt',
    codeGenType  VARCHAR(64)                        NULL COMMENT 'code generation type',
    deployKey    VARCHAR(64)                        NULL COMMENT 'deploy key',
    deployedTime DATETIME                           NULL COMMENT 'deployed time',
    priority     INT      DEFAULT 0                 NOT NULL COMMENT 'priority',
    userId       BIGINT                             NOT NULL COMMENT 'user id',
    editTime     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT 'edit time',
    createTime   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT 'create time',
    updateTime   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    isDelete     TINYINT  DEFAULT 0                 NOT NULL COMMENT 'deleted',
    UNIQUE KEY uk_deployKey (deployKey),
    INDEX idx_appName (appName),
    INDEX idx_userId (userId)
) COMMENT 'app' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_history
(
    id          BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    message     TEXT                               NOT NULL COMMENT 'message',
    messageType VARCHAR(32)                        NOT NULL COMMENT 'user/ai',
    appId       BIGINT                             NOT NULL COMMENT 'app id',
    userId      BIGINT                             NOT NULL COMMENT 'user id',
    createTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT 'create time',
    updateTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    isDelete    TINYINT  DEFAULT 0                 NOT NULL COMMENT 'deleted',
    INDEX idx_appId (appId),
    INDEX idx_createTime (createTime),
    INDEX idx_appId_createTime (appId, createTime)
) COMMENT 'chat history' COLLATE = utf8mb4_unicode_ci;

