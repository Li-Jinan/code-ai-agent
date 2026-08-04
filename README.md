# AI 代码生成与智能体应用平台

> 基于 LLM + LangChain4j + LangGraph4j + Tool Calling + SSE 构建的 AI 代码生成 Agent 平台。系统支持用户输入需求后生成应用代码，并提供生成过程流式展示、文件工具调用、在线预览、代码下载和部署发布能力。

## 项目定位

本项目面向 AI 应用工程与 Agent 工程场景，目标不是做一个单纯的聊天机器人，而是将 LLM 能力接入到真实的代码生成工作流中：

```text
用户需求
 -> 前端发起 SSE 对话
 -> 后端创建应用级 AI 服务
 -> Agent / Workflow 规划生成流程
 -> Tool Calling 执行文件读写与项目构建
 -> 生成应用代码
 -> 在线预览 / 下载 / 部署
```

在线访问：

| 类型 | 地址 |
| --- | --- |
| 前端应用 | [http://124.221.85.117](http://124.221.85.117) |
| 接口文档 | [http://124.221.85.117/api/doc.html#/home](http://124.221.85.117/api/doc.html#/home) |

## 核心能力

- **AI 代码生成**：支持 HTML、多文件和 Vue 项目等生成类型，将用户需求转换为可预览的应用代码。
- **Agent 工具调用**：封装文件读取、写入、修改、删除、目录读取和退出工具，支持 Agent 在受控范围内操作项目文件。
- **工作流编排**：基于 LangGraph4j 编排提示词增强、代码生成、质量检查、图片资源收集和项目构建等节点。
- **流式响应**：通过 SSE 实时返回生成过程，前端同步展示模型输出、工具执行和生成状态。
- **上下文记忆**：基于 Redis ChatMemory 保存应用级对话上下文，并加载历史消息恢复会话。
- **服务实例缓存**：基于 Caffeine 缓存 AI 服务实例，降低频繁创建服务对象的开销。
- **结果闭环**：支持生成结果在线预览、应用代码下载、部署发布和后台管理。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Ant Design Vue、Pinia、Axios |
| 后端 | Spring Boot、MyBatis-Flex、Knife4j、Redisson、Caffeine |
| AI 能力 | LangChain4j、LangGraph4j、Tool Calling、SSE、ChatMemory |
| 存储 | MySQL、Redis、COS / 本地文件存储 |
| 部署 | Nginx、Jar 进程部署、前后端分离部署 |

## 核心链路

### 1. 应用创建与对话生成

用户创建应用后，在应用对话页输入需求。前端通过 SSE 请求后端接口：

```text
GET /app/chat/gen/code
```

后端根据 `appId` 和生成类型创建或复用 AI 服务实例，并将生成过程以流式事件返回前端。

相关代码：

- `AppController#chatToGenCode`
- `AppServiceImpl#chatToGenCode`
- `AiCodeGeneratorServiceFactory`

### 2. AI 服务与上下文管理

系统按应用维度创建 AI 服务，并使用 Redis ChatMemory 保存对话上下文。为了减少重复构建服务实例，使用 Caffeine 按 `appId + codeGenType` 缓存 AI 服务。

关键设计：

- 每个应用维护独立对话记忆，避免不同应用上下文串扰。
- 启动 AI 服务时加载历史会话，支持用户继续迭代同一个应用。
- 对 Vue 项目生成模式启用工具调用，让 Agent 可以操作文件系统。

相关代码：

- `AiCodeGeneratorServiceFactory`
- `RedisChatMemoryStoreConfig`
- `ChatHistoryServiceImpl`

### 3. 工具调用与文件操作

项目将文件系统操作封装为工具，并通过 `ToolManager` 统一注册。Agent 在生成 Vue 项目时可以按需调用工具完成代码写入、修改、读取目录等动作。

已封装工具：

- `FileReadTool`：读取文件内容
- `FileWriteTool`：写入文件
- `FileModifyTool`：修改文件内容
- `FileDeleteTool`：删除文件
- `FileDirReadTool`：读取目录结构
- `ExitTool`：任务完成后退出工具调用循环

相关代码：

- `ToolManager`
- `BaseTool`
- `FileWriteTool`
- `FileModifyTool`
- `ExitTool`

### 4. 工作流编排

项目引入 LangGraph4j，将复杂代码生成过程拆成多个节点，便于表达 Agent 执行状态和后续扩展。

典型节点：

- `PromptEnhancerNode`：优化用户需求
- `RouterNode`：判断生成路径
- `CodeGeneratorNode`：生成核心代码
- `CodeQualityCheckNode`：检查生成质量
- `ImageCollectorNode`：收集页面素材
- `ProjectBuilderNode`：构建项目结果

相关代码：

- `CodeGenWorkflow`
- `CodeGenConcurrentWorkflow`
- `CodeGenSubgraphWorkflow`
- `WorkflowContext`
- `WorkflowSseController`

### 5. 预览、下载与部署

代码生成完成后，系统将生成结果保存到指定目录，前端可通过 iframe 预览生成页面。用户也可以下载完整代码包，或将应用部署到静态资源目录并获得访问链接。

相关接口：

```text
GET  /app/download/{appId}
POST /app/deploy
GET  /static/{deployKey}/**
```

相关代码：

- `ProjectDownloadServiceImpl`
- `StaticResourceController`
- `AppServiceImpl#deployApp`

## 项目结构

```text
code-ai-agent
├── code-ai-agent-frontend        # Vue 3 前端
├── src/main/java/com/jinan/codeaiagent
│   ├── ai                         # AI 服务、工具和护栏
│   ├── controller                 # 应用、用户、历史记录、SSE 接口
│   ├── core                       # 代码解析、保存、流式处理
│   ├── langgraph4j                # Agent 工作流编排
│   ├── model                      # DTO、VO、实体和枚举
│   ├── service                    # 业务服务
│   └── monitor                    # AI 调用监控与指标采集
└── code-ai-agent-microservice     # 微服务拆分版本
```

## 本地启动

后端：

```bash
mvn clean package -DskipTests
java -jar target/code-ai-agent-0.0.1-SNAPSHOT.jar
```

前端：

```bash
cd code-ai-agent-frontend
npm install
npm run dev
```

启动前需要按环境配置 MySQL、Redis、模型 Key、对象存储等参数。

## 适合简历描述

基于 LangChain4j + LangGraph4j + Spring Boot 构建 AI 代码生成 Agent 平台，支持应用级对话记忆、工具调用、文件操作、SSE 流式生成、生成结果预览、代码下载和部署发布，实现从用户需求输入到应用生成交付的完整链路。
