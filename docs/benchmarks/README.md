# 效果数据采集说明

本目录只保存可复现的原始数据和由脚本生成的汇总。README 在每种生成模式达到至少 20 次有效样本前，不展示效果数字。

## 已采集数据

2026-10-18 已在公开部署上完成首轮正式采集：

- [生成任务原始数据](raw/generation-20261018.csv)：67 次尝试，包含 60 个服务器终态样本和 7 个客户端网络错误。
- [断线恢复原始数据](raw/reconnect-20261018.csv)：Vue 固定场景 5 次，均由新客户端找回同一 task ID。
- [目录限流原始数据](raw/directory-output-20261018-204925.csv)：固定 1,700 文件夹具的改造前后算法重放。
- [生成任务汇总](results.md) 与 [机器可读汇总](summary.csv)：由汇总脚本直接生成。

## 指标定义

| 指标 | 定义 |
| --- | --- |
| 生成任务平均值 / P95 | `generation_task.startTime` 到 `finishTime`，按 `html`、`multi_file`、`vue_project` 分组；P95 使用 nearest-rank |
| 最终预览成功率 | 终态样本中，任务成功且对应预览 URL 返回 HTTP 2xx/3xx 的比例；失败和超时不从分母删除 |
| 目录工具输出 | 在固定的 1,700 文件夹具上重放改造前后算法，记录精确字符数；token 仅按 `字符数 / 4` 粗略估算 |
| 客户端中断恢复 | 创建任务后断开客户端，再重新登录并通过 `latest`/任务查询接口恢复轮询；记录是否找到同一任务及最终状态 |

服务进程重启不等于透明续跑：当前实现会把心跳过期的任务标记为失败，使用户可以重试，不应写成“服务重启后从断点继续生成”。

## 生成任务批量采集

脚本只使用 Python 标准库。账号密码仅从环境变量读取，不会写入 CSV：

```bash
export BENCHMARK_USER_ACCOUNT='benchmark-account'
export BENCHMARK_USER_PASSWORD='benchmark-password'

python3 scripts/benchmark/run_generation_benchmark.py \
  --mode all \
  --runs-per-mode 20 \
  --confirm-paid-ai-calls
```

该命令会真实调用已配置的 AI 服务并产生费用。每条记录完成后立即落盘到 `docs/benchmarks/raw/`，中途中断不会丢失已完成样本。脚本会校验实际路由类型，路由错误单独记录，不能冒充目标模式样本。

汇总命令：

```bash
python3 scripts/benchmark/summarize_generation_benchmark.py \
  docs/benchmarks/raw/generation-YYYYMMDD-HHMMSS.csv
```

默认每种模式少于 20 条会拒绝生成正式汇总。`--allow-incomplete` 只能用于本地检查格式，不能把草稿数字放进项目 README。

## 客户端中断恢复

固定场景默认选择耗时更长的 Vue 模式：创建任务后丢弃第一个客户端的全部 Cookie，等待 20 秒，再由一个全新的客户端重新登录，通过 `latest` 找回任务并继续轮询：

```bash
python3 scripts/benchmark/run_reconnect_scenario.py \
  --mode vue_project \
  --runs 5 \
  --disconnect-seconds 20 \
  --confirm-paid-ai-calls
```

该场景同样会调用付费 AI 服务。CSV 会同时记录创建时和恢复后的 task ID；两者不同不能算恢复成功。

## 目录读取改造前后对比

```bash
python3 scripts/benchmark/measure_directory_output.py
```

脚本先验证 `ea5783b^` 是未限流实现、`ea5783b` 是带深度/条目/字符上限的实现，再用同一固定夹具重放两套算法。结果写入 `docs/benchmarks/raw/directory-output-*.csv`。

## Grafana 证据要求

微服务版需要先部署 Actuator、Micrometer Prometheus 指标，并让 Prometheus 抓取 app-service。截图必须同时显示：查询语句、统计时间范围、三种 `code_gen_type` 标签和有效样本量。没有这些信息的截图不能作为效果数据证据。

本地启动监控栈（密码只放在未提交的 `.env` 中）：

```bash
docker compose \
  -f docker-compose.microservice.yml \
  -f docker-compose.observability.yml \
  up -d --build
```

Prometheus 与 Grafana 默认只绑定 `127.0.0.1`。app-service 的 Actuator 使用容器内部 `9125` 端口，避免经公开 API 入口暴露运行指标。预置面板为 `Code AI Agent - Generation Tasks`。
