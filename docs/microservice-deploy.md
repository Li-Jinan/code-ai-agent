# 微服务公网 Demo 部署指南

## 1. 部署架构

浏览器只访问一个域名，前端无需感知各后端端口：

```text
Browser
  ├─ 主站域名 -> Nginx / frontend:80
       ├─ /                         -> Vue 静态站点
       ├─ /api/user/**              -> user-service:8124
       ├─ /api/**                   -> app-service:8125
  └─ 独立预览域名 -> Nginx / frontend:8080
       └─ /{deployKey}/**           -> 生成应用共享卷（不暴露 /api）

app-service
  ├─ Dubbo -> user-service
  └─ Dubbo -> screenshot-service -> Selenium Chromium -> frontend 内网地址 -> Tencent COS

user-service + app-service -> MySQL / Redis
user-service + app-service + screenshot-service -> Nacos
```

建议服务器至少准备 4 GB 内存；Chromium 与 Java 服务同时运行时，2 GB 机器比较容易触发内存不足。

## 2. 准备环境变量

在服务器项目根目录执行：

```bash
cp deploy.microservice.env.example .env
vim .env
```

必须替换：

- `PUBLIC_BASE_URL`：浏览器最终访问地址，如 `https://agent.example.com`
- `DEPLOY_BASE_URL`：生成站点的独立地址，如 `https://preview.example.com`
- `MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD`
- 默认会使用 Compose 内的 `mysql`、`redis`；复用外部实例时，另外设置
  `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USERNAME`、
  `REDIS_HOST` 和 `REDIS_PORT`，并将 `APP_NETWORK_EXTERNAL=true`、
  `APP_NETWORK_NAME` 指向预先创建且已连接这些实例的专用 Docker 网络
- `DEEPSEEK_API_KEY`（代码生成与类型路由共用；路由失败时会自动使用本地规则）
- `GENERATION_TASK_WORKER_COUNT`、`GENERATION_TASK_QUEUE_CAPACITY`：生成执行槽位和等待队列上限；
  默认分别为 `2` 和 `8`，小规格服务器不要盲目调高
- `GENERATION_TASK_TIMEOUT`：单个后台生成任务超时，默认 `10m`
- `COS_CLIENT_HOST`、`COS_CLIENT_SECRETID`、`COS_CLIENT_SECRETKEY`、`COS_CLIENT_REGION`、`COS_CLIENT_BUCKET`
- 邮箱登录需要再配置 `MAIL_USERNAME`、`MAIL_PASSWORD`

启动前做一次检查：

```bash
./scripts/validate-deploy-env.sh .env
docker compose -f docker-compose.microservice.yml --env-file .env config --quiet
```

`.env` 不要提交到 Git。生产环境会关闭 AI 请求和响应正文日志，避免提示词及模型输出进入日志文件。
两个 URL 都不要带结尾 `/`，并且不能相同。`PUBLIC_BASE_URL` 用于主站及 Cookie CORS 来源，`DEPLOY_BASE_URL` 只用于生成站点，避免 AI 生成的 JavaScript 与登录态处在同一来源。

备案审核期间，可以保留最终的域名 URL，但应将 `WEB_BIND_IP=127.0.0.1`，并暂停域名解析。此时通过服务器本机或 SSH 隧道验收，不向公网开放网站。

## 3. 选择公网入口

### 方案 A：Compose 直接监听 80

```env
HTTP_PUBLIC_PORT=80
PREVIEW_PUBLIC_PORT=8080
WEB_BIND_IP=0.0.0.0
```

此时可暂时使用 `PUBLIC_BASE_URL=http://服务器IP`、`DEPLOY_BASE_URL=http://服务器IP:8080`。云安全组需要放行 `80` 和 `8080`；接入域名与 HTTPS 后应关闭公网 `8080`。

### 方案 B：宿主机已有 Nginx（推荐）

```env
HTTP_PUBLIC_PORT=18080
PREVIEW_PUBLIC_PORT=18081
WEB_BIND_IP=127.0.0.1
INFRA_BIND_IP=127.0.0.1
SERVICE_BIND_IP=127.0.0.1
```

将 `docker/nginx/code-ai-agent.microservice.conf.example` 复制到宿主机 Nginx 站点目录，替换主站与预览域名。主站转发到 `127.0.0.1:18080`，预览域名转发到 `127.0.0.1:18081`。MySQL、Redis、Nacos 和业务端口默认只绑定 `127.0.0.1`，不要向公网开放。

## 4. 构建并启动

推荐使用项目内的部署脚本，它会依次检查 Docker、环境变量和 Compose 配置，再构建并启动全部服务：

```bash
./scripts/deploy.sh .env
```

等价的手动命令是：

```bash
docker compose -f docker-compose.microservice.yml --env-file .env up -d --build
docker compose -f docker-compose.microservice.yml --env-file .env ps
```

首次构建会下载 Maven、Node 和 Selenium Chromium 镜像，耗时会比普通 Spring Boot 项目长。Compose 会等待 MySQL、Redis、Nacos、Selenium 及后端服务健康后再启动下游服务。

查看日志：

```bash
docker compose -f docker-compose.microservice.yml --env-file .env logs -f app-service
docker compose -f docker-compose.microservice.yml --env-file .env logs -f user-service
docker compose -f docker-compose.microservice.yml --env-file .env logs -f screenshot-service
```

## 5. 验证完整链路

先在服务器本机验证统一入口（将 `18080` 替换成 `.env` 中的 `HTTP_PUBLIC_PORT`）：

```bash
curl -fsS http://127.0.0.1:18080/
curl -fsS http://127.0.0.1:18080/api/health
curl -i http://127.0.0.1:18080/api/user/get/login
curl -fsS http://127.0.0.1:18081/preview-health
```

最后一条在未登录时返回业务未登录信息是正常的，关键是请求必须到达 user 服务，而不是返回前端 `index.html`。

统一入口可访问后，运行自动冒烟测试。它会检查前端、app 路由、演示账号登录和 Redis Session：

```bash
./scripts/smoke-test.sh https://agent.example.com demo demo123456
```

浏览器验证顺序：

1. 打开 `PUBLIC_BASE_URL`。
2. 使用 `demo / demo123456` 登录；登录页提供“一键填入”。
3. 创建应用并观察 SSE 流式生成。
4. 点击部署，确认返回 `${DEPLOY_BASE_URL}/{deployKey}/`。
5. 确认部署页可访问，并在稍后刷新作品列表检查 COS 封面。

演示账号由 user 服务在启动时自动创建或修复，且禁止在页面中修改密码。可通过 `DEMO_ACCOUNT_ENABLED=false` 关闭。

## 6. 域名与 HTTPS

先将主站和预览域名的 `A` 记录都解析到服务器公网 IP。确认 HTTP 可访问后，再用 Certbot 或 acme.sh 分别签发证书，并启用示例配置中的两个 443 server。此时 `.env` 中的两个 URL 都必须使用最终的 `https://` 地址，然后重新构建前端：

```bash
docker compose -f docker-compose.microservice.yml --env-file .env up -d --build frontend
```

`DEPLOY_BASE_URL` 会同时注入前端运行时配置和后端部署链接生成逻辑。修改域名后至少需要重新创建 `frontend` 与 `app-service` 容器；`runtime-config.js` 使用 `no-store`，因此不再依赖旧的前端构建缓存。

每次修改域名后必须执行：

```bash
./scripts/validate-deploy-env.sh .env
./scripts/deploy.sh .env
```

校验会拒绝 HTTPS 主站搭配 HTTP 预览站、旧公网 `8080` 端口、相同来源以及带路径/查询参数的错误配置；冒烟测试还会比对浏览器实际收到的运行时域名。

## 7. 更新、备份与排障

更新代码：

```bash
git pull
docker compose -f docker-compose.microservice.yml --env-file .env up -d --build
```

停止服务但保留数据：

```bash
docker compose -f docker-compose.microservice.yml --env-file .env down
```

不要执行 `down -v`，它会删除 MySQL、Redis 和生成应用的命名卷。

升级或调整配置前，先创建数据库备份：

```bash
./scripts/backup-mysql.sh .env
```

生成任务并发约束和心跳回收首次上线时，需要在备份后、停止所有 `app-service` 实例的状态下，手工执行一次 `sql/migration/20261017_generation_task_lease_and_constraints.sql`，再部署新版本。该迁移会把升级前遗留的 `pending/running` 任务标记为失败，避免旧任务被误认为仍在执行；用户可重新提交。后续全新数据库会从初始化 SQL 自动创建 `running_app_id`、心跳字段和唯一索引。

备份默认写入项目根目录的 `backups/`，文件权限为 `600`，该目录已被 Git 忽略。也可以把第二个参数指定为挂载的数据盘目录：

```bash
./scripts/backup-mysql.sh .env /data/code-ai-agent-backups
```

恢复备份会覆盖现有业务数据，因此没有放进自动部署脚本。需要恢复时，应先停掉 `app-service` 与 `user-service`，确认备份文件和目标数据库无误后再手工导入。

常见排障顺序：

1. `docker compose ... ps`：先找出 `unhealthy` 的容器。
2. `docker compose ... logs --tail=200 <service>`：检查实际失败原因。
3. AI 失败：检查两个 API Key、额度及服务器到模型 API 的网络。
4. 登录失败：检查 MySQL、Redis 健康状态及浏览器是否保存 `/api` 下的 Session Cookie。
5. Vue 部署失败：在 app-service 中执行 `node --version` 和 `npm --version`。
6. 封面失败：检查 selenium 状态、COS 权限以及 `http://frontend:8080/{deployKey}/` 是否能从容器访问。
