# AI-Native OS（Java + MySQL）

AI-Native 统一操作模型，核心能力包括：
- 面向目标的语义内核（Semantic Kernel）编排
- 统一能力抽象（`FILE_*`、`NETWORK_*`、`COMPUTE_*`、`RUNTIME_*`）
- 自愈式 VFS 契约（`FailureObject` + `ContextStack` + `ErrorVector`）
- 声明式无状态运行意图（`DesiredState`）

## 技术栈
- Java 21
- Spring Boot 3.3.x
- MySQL 8.4
- Docker / Docker Compose

## 文档导航
- 架构设计（中英双语）：`docs/architecture.md`
- 优化路线图：`docs/roadmap.md`
- 迭代任务清单：`docs/backlog.md`
- VM 部署手册：`docs/vm-setup.md`

## 核心模块
- 规划器：`kernel/planner/DefaultGoalPlanner`
- 语义规划引擎：`kernel/planner/semantic/*`（意图解析、计划图、验证器）
- LLM 语义推理器：`llm/OpenAiSemanticReasoner`（可选，失败自动回退规则引擎）
- 策略门控：`kernel/policy/SimplePolicyEngine`
- 执行状态机：`kernel/execution/SemanticExecutionEngine`
- 能力总线：`capability/CapabilityRouter` + Providers
- 运行时适配器：`runtime/LocalCommandExecutor`、`runtime/SshCommandExecutor`
- 状态收敛控制器：`runtime/DesiredStateReconciler`
- 自愈模块：`kernel/healing/FailureAnalyzer` + `RepairPlanner`
- 持久化：`goal_execution`、`goal_trace`、`execution_audit`、`desired_state_job`
- 前端测试台：`frontend/index.html`（Nginx 反向代理 `/api`）

## 近期更新（已落地）
- 新增 Docker 前端测试页（`http://<host>:8081`）用于直接调试核心 API。
- `GoalPlan` 与 `GoalExecutionResult` 新增 `llmUsed` 字段。
- LLM 调用链路增加诊断日志（未启用、HTTP 非 2xx、解析失败、异常回退）。
- 架构文档升级为中英双语并新增 roadmap/backlog 文档。
- 执行引擎升级为 DAG 依赖调度：支持无依赖节点并行执行与失败 fallback 分支。
- 计划图快照已持久化到执行记录，支持按 `goalId` 回放。
- 策略中心升级：支持 `policyProfile` 的重试/超时/回滚策略、执行级熔断、任务级限流。
- LLM 路由升级：支持 primary/fallback provider，记录调用耗时/状态码/fallback 命中。
- `GoalPlan` 与 `GoalExecutionResult` 增加 `llmRationale` 字段。
- Runtime 执行层完成 SPI 化：`prepare/execute/verify/rollback` 四阶段，Local/SSH 已迁移为适配器实现。
- 支持 Runtime Adapter 自动注册发现，可通过接口查看当前注册适配器清单。

## API 列表
- `POST /api/goals/plan`
- `POST /api/goals/execute`
- `GET /api/goals/executions?goalId=...`
- `GET /api/goals/{goalId}/trace`
- `GET /api/goals/{goalId}/replay`
- `GET /api/goals/reconcile-jobs?goalId=...`
- `GET /api/goals/runtime-adapters`
- `GET /api/goals/capabilities`
- `GET /api/goals/plugins`
- `GET /api/goals/templates`
- `GET /api/goals/templates/{templateId}/versions`
- `POST /api/goals/templates/execute`
- `POST /api/goals/templates/{templateId}/rollback`
- `GET /api/goals/events?goalId=...`
- `GET /api/goals/health`

## 接口示例

### 1）健康检查
请求：
```bash
curl -s http://127.0.0.1:8080/api/goals/health
```

响应：
```json
{
  "service": "ainativeos-control-plane",
  "status": "UP",
  "database": "ready",
  "semanticKernel": "ready",
  "capabilityFabric": "ready",
  "selfHealingVfs": "ready",
  "reconcileController": "ready",
  "timestamp": "2026-02-25T05:10:00Z"
}
```

### 2）目标规划（Plan）
请求：
```bash
curl -s -X POST http://127.0.0.1:8080/api/goals/plan \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "goal-plan-001",
    "naturalLanguageIntent": "安装 mysql 客户端并执行冒烟测试",
    "successCriteria": ["client_installed", "smoke_test_passed"],
    "constraints": {
      "cost": "low",
      "security": "high"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

响应（节选）：
```json
{
  "goalSpec": {
    "goalId": "goal-plan-001",
    "naturalLanguageIntent": "安装 mysql 客户端并执行冒烟测试",
    "successCriteria": ["client_installed", "smoke_test_passed"],
    "constraints": {"cost": "low", "security": "high"},
    "maxRetries": 2,
    "policyProfile": "default"
  },
  "desiredState": {
    "stateId": "state-goal-plan-001",
    "summary": "Converge declared runtime state"
  },
  "atomicOps": [
    {"opId": "op-parse", "type": "COMPUTE_PARSE_INTENT"},
    {"opId": "op-policy", "type": "COMPUTE_POLICY_EVAL"},
    {"opId": "op-capability", "type": "COMPUTE_RESOLVE_CAPABILITY"},
    {"opId": "op-apply", "type": "RUNTIME_APPLY_DECLARATIVE_STATE"},
    {"opId": "op-verify", "type": "COMPUTE_VERIFY_SUCCESS"}
  ],
  "plannerVersion": "planner-v3",
  "llmUsed": false
}
```

### 3）目标执行（本地运行命令）
请求：
```bash
curl -s -X POST http://127.0.0.1:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "goal-exec-local-001",
    "naturalLanguageIntent": "本地执行命令",
    "successCriteria": ["command_ok"],
    "constraints": {
      "runtimeCommand": "echo hello-local"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

响应（节选）：
```json
{
  "goalId": "goal-exec-local-001",
  "status": "SUCCEEDED",
  "message": "Goal converged to desired state",
  "llmUsed": false,
  "failureObject": null,
  "trace": [
    {"opId": "op-parse", "status": "SUCCEEDED", "attempt": 1},
    {"opId": "op-policy", "status": "SUCCEEDED", "attempt": 1},
    {"opId": "op-capability", "status": "SUCCEEDED", "attempt": 1},
    {
      "opId": "op-apply",
      "status": "SUCCEEDED",
      "message": "Runtime command executed successfully: hello-local",
      "attempt": 1
    },
    {"opId": "op-verify", "status": "SUCCEEDED", "attempt": 1}
  ]
}
```

### 4）目标执行（自愈重试路径）
请求（`simulateFailure=true` 会让首次 `op-apply` 失败，然后自动修复并重试）：
```bash
curl -s -X POST http://127.0.0.1:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "goal-exec-heal-001",
    "naturalLanguageIntent": "测试自愈能力",
    "successCriteria": ["command_ok"],
    "constraints": {
      "runtimeCommand": "echo healed",
      "simulateFailure": "true"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

预期行为：
- `op-apply` 第 1 次：`FAILED`
- `op-apply` 第 2 次：`SUCCEEDED`
- 最终状态：`SUCCEEDED`

### 5）目标执行（远程 SSH Key）
请求：
```bash
curl -s -X POST http://127.0.0.1:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "goal-exec-ssh-key-001",
    "naturalLanguageIntent": "通过 SSH Key 在远端执行命令",
    "successCriteria": ["remote_ok"],
    "constraints": {
      "runtimeCommand": "hostname",
      "remoteHost": "172.23.115.116",
      "remotePort": "22",
      "remoteUser": "jiyongyang",
      "remotePrivateKeyBase64": "<base64-pem>"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

响应（节选）：
```json
{
  "goalId": "goal-exec-ssh-key-001",
  "status": "SUCCEEDED",
  "trace": [
    {
      "opId": "op-apply",
      "status": "SUCCEEDED",
      "message": "Runtime command executed successfully: <remote-hostname>",
      "attempt": 1
    }
  ]
}
```

### 6）查询执行记录
请求：
```bash
curl -s "http://127.0.0.1:8080/api/goals/executions?goalId=goal-exec-ssh-key-001"
```

响应：
```json
[
  {
    "id": 12,
    "goalId": "goal-exec-ssh-key-001",
    "status": "SUCCEEDED",
    "summary": "Goal converged to desired state",
    "plannerVersion": "planner-v3",
    "createdAt": "2026-02-25T04:49:35.763416Z"
  }
]
```

### 7）查询执行轨迹
请求：
```bash
curl -s "http://127.0.0.1:8080/api/goals/goal-exec-ssh-key-001/trace"
```

响应（节选）：
```json
[
  {"opId": "op-parse", "status": "SUCCEEDED", "attempt": 1},
  {"opId": "op-policy", "status": "SUCCEEDED", "attempt": 1},
  {"opId": "op-capability", "status": "SUCCEEDED", "attempt": 1},
  {"opId": "op-apply", "status": "SUCCEEDED", "attempt": 1},
  {"opId": "op-verify", "status": "SUCCEEDED", "attempt": 1}
]
```

### 8）查询持续收敛任务
请求：
```bash
curl -s "http://127.0.0.1:8080/api/goals/reconcile-jobs?goalId=goal-continuous-001"
```

### 9）按 goalId 回放计划图与执行轨迹
请求：
```bash
curl -s "http://127.0.0.1:8080/api/goals/goal-ui-001/replay"
```

响应（节选）：
```json
{
  "goalId": "goal-ui-001",
  "latestExecutionId": 18,
  "planGraph": {
    "format": "planner-v4-dag",
    "nodes": [
      {"opId":"op-0-node-parse","dependsOnOpIds":[],"onFailureOpId":"","branchOnly":false},
      {"opId":"op-1-node-policy","dependsOnOpIds":["op-0-node-parse"],"onFailureOpId":"","branchOnly":false}
    ]
  },
  "trace": [
    {"opId":"op-0-node-parse","status":"SUCCEEDED","attempt":1}
  ]
}
```

### 10）查询平台能力字典
请求：
```bash
curl -s "http://127.0.0.1:8080/api/goals/capabilities"
```

响应（节选）：
```json
[
  {
    "provider": "k8s-provider",
    "supportedOps": ["K8S_APPLY_MANIFEST","K8S_VERIFY_DEPLOYMENT","K8S_ROLLBACK_DEPLOYMENT","K8S_EXECUTE"]
  },
  {
    "provider": "docker-provider",
    "supportedOps": ["DOCKER_RUN_IMAGE","DOCKER_VERIFY_CONTAINER","DOCKER_ROLLBACK_CONTAINER","DOCKER_EXECUTE"]
  }
]
```

### 11）查询插件清单
请求：
```bash
curl -s "http://127.0.0.1:8080/api/goals/plugins"
```

响应（节选）：
```json
[
  {
    "pluginId":"echo-plugin",
    "name":"Echo Plugin",
    "version":"1.0.0",
    "enabled":true,
    "isolatedProcess":true,
    "requiredCapabilities":["COMPUTE_PARSE_INTENT"]
  }
]
```

响应示例：
```json
[
  {
    "id": 1,
    "goalId": "goal-continuous-001",
    "status": "ACTIVE",
    "failCount": 0,
    "lastMessage": "continuous reconcile job created",
    "nextRunAt": "2026-02-25T07:52:09.774996Z",
    "updatedAt": "2026-02-25T07:52:09.774999Z"
  }
]
```

## 运行模式说明

### Runtime Adapter SPI（EPIC-4）
- SPI 接口：`RuntimeAdapter`（`prepare/execute/verify/rollback`）
- 执行分发：`RuntimeCommandDispatcher` 基于 `List<RuntimeAdapter>` 自动发现并按优先级路由
- 已迁移适配器：
  - `ssh`（优先级高，满足 remote 参数时命中）
  - `local-shell`（兜底）

可用适配器查询：
```bash
curl -s "http://127.0.0.1:8080/api/goals/runtime-adapters"
```

响应示例：
```json
[
  {"adapterId":"ssh","priority":10,"className":"com.ainativeos.runtime.spi.SshRuntimeAdapter"},
  {"adapterId":"local-shell","priority":200,"className":"com.ainativeos.runtime.spi.LocalRuntimeAdapter"}
]
```

### Docker/K8s Provider（EPIC-5）
- Docker provider：支持 `DOCKER_RUN_IMAGE`、`DOCKER_VERIFY_CONTAINER`、`DOCKER_ROLLBACK_CONTAINER`
- Kubernetes provider：支持 `K8S_APPLY_MANIFEST`、`K8S_VERIFY_DEPLOYMENT`、`K8S_ROLLBACK_DEPLOYMENT`
- 可通过 `GET /api/goals/capabilities` 查询当前平台能力字典

### 资源标签与环境约束（EPIC-6）
- 支持 `computeClass`：`cpu`、`gpu`、`highmem`
- 运行时会按标签路由到不同 Runtime Adapter（可通过 `/api/goals/runtime-adapters` 查看）
- 执行前策略校验会验证环境约束，不满足会提前 `BLOCKED`

常用约束键：
- `computeClass=gpu|highmem|cpu`
- `requiresDocker=true`
- `requiresKubectl=true`
- `requiredCommands=cmd1,cmd2`
- `minMemoryGb=16`（配合 `computeClass=highmem`）

### CLI 与 Java SDK（EPIC-7）
- CLI 入口：`com.ainativeos.cli.AinativeOsCli`
- Java SDK 核心类：
  - `com.ainativeos.sdk.AinativeOsClient`
  - `com.ainativeos.sdk.AinativeOsClientConfig`
  - `com.ainativeos.sdk.model.*`（强类型请求/响应）

CLI 示例：
```bash
# 先打包
mvn -DskipTests package

# 执行 plan
java -cp target/ainativeos-0.1.0-SNAPSHOT.jar com.ainativeos.cli.AinativeOsCli \
  plan --base-url http://127.0.0.1:8080 --file ./goal-spec.json

# 查询 trace
java -cp target/ainativeos-0.1.0-SNAPSHOT.jar com.ainativeos.cli.AinativeOsCli \
  trace --base-url http://127.0.0.1:8080 --goal-id goal-ui-001
```

Java SDK 示例：
```java
AinativeOsClient client = new AinativeOsClient(
    AinativeOsClientConfig.defaults("http://127.0.0.1:8080")
);
GoalPlanResponse plan = client.plan(request);
GoalExecutionResponse result = client.execute(request);
List<TraceEventItem> trace = client.trace(request.goalId());
```

Python SDK（`python-sdk`）：
- 包名：`ainativeos-sdk`
- 安装：`pip install -e python-sdk`
- 示例：`python python-sdk/examples/basic_demo.py`

### 测试与 CI/CD（EPIC-8）
- 契约测试：`GoalApiContractTest`（校验 `/plan`、`/execute`、`/capabilities` 响应字段契约）
- E2E 冒烟：`GoalExecutionE2ETest`（mock LLM + real runtime provider）
- CI 流水线：`.github/workflows/ci.yml`
  - Java 21 + Maven Test + Package + Docker Build
- Release 流水线：`.github/workflows/release.yml`
  - Tag 触发（`v*`），自动打包 jar、构建镜像、发布 GitHub Release

### 插件系统（EPIC-9）
- manifest 规范：`plugins/*.json`（`pluginId/version/entryCommand/requiredCapabilities/inputSchema/outputSchema`）
- 注册加载：`PluginRegistryService`
- 执行隔离：`PluginCapabilityProvider` 通过独立进程执行插件命令
- 权限沙箱：`PluginSandboxService`
  - 高风险能力（`SYSTEM_/K8S_/CLOUD_/DOCKER_/RUNTIME_`）需要显式审批令牌
  - 令牌配置：`PLUGINS_HIGH_RISK_APPROVAL_TOKEN`

### 模板中心与事件总线（EPIC-10）
- 内置模板：部署、巡检、自愈（首次启动自动写入 `template_version`）
- 模板版本管理：支持查询版本和回滚到指定版本
- 一键执行模板：`POST /api/goals/templates/execute`
- Webhook 事件推送：目标执行完成后推送 `goal.execution.completed`
- 事件投递审计：落库 `event_delivery`，可通过 `GET /api/goals/events?goalId=...` 查询

### 本地命令模式
- 当传入 `constraints.runtimeCommand` 时，运行时会在本地 shell 执行命令。
- Windows：`powershell -Command <runtimeCommand>`
- Linux：`sh -lc <runtimeCommand>`

### 远程 SSH 模式
- 当传入 `remoteHost` + `remoteUser` + 认证信息（密码或私钥）时，会在远端执行 `runtimeCommand`。
- 可选 `remotePort`，默认 `22`。

### SSH 认证优先级
1. `remotePrivateKeyBase64`（推荐）
2. `remotePrivateKey`
3. `remotePassword`（兜底）

### 声明式收敛（Reconcile）参数
当你希望“应用命令 + 验证命令”循环执行直到收敛，可传入：
- `reconcileApplyCommand`
- `reconcileVerifyCommand`
- `reconcileMaxRounds`（可选，默认 5）
- `reconcileIntervalMs`（可选，默认 2000）

示例：
```json
{
  "goalId": "goal-reconcile-001",
  "naturalLanguageIntent": "converge runtime state",
  "successCriteria": ["reconcile_ok"],
  "constraints": {
    "runtimeCommand": "echo fallback-runtime-command",
    "reconcileApplyCommand": "kubectl apply -f deploy.yaml",
    "reconcileVerifyCommand": "kubectl get deploy my-app -o jsonpath='{.status.availableReplicas}' | grep 1",
    "reconcileMaxRounds": "8",
    "reconcileIntervalMs": "3000"
  },
  "maxRetries": 2,
  "policyProfile": "default"
}
```

### 持续收敛控制器（后台）
如需由后台控制器持续监控并自动收敛（而非只在请求内执行一次），可增加：
- `continuousReconcile=true`

示例：
```json
{
  "goalId": "goal-continuous-001",
  "naturalLanguageIntent": "continuous desired state reconcile",
  "successCriteria": ["reconcile_ok"],
  "constraints": {
    "runtimeCommand": "echo fallback",
    "continuousReconcile": "true",
    "reconcileApplyCommand": "echo apply",
    "reconcileVerifyCommand": "echo verify"
  },
  "maxRetries": 2,
  "policyProfile": "default"
}
```

## LLM 配置（可选）
默认关闭。开启后，规划器会尝试调用 LLM 生成额外动作/约束建议；调用失败会自动回退规则解析。

```yaml
ainativeos:
  llm:
    enabled: true
    provider: openai
    endpoint: https://api.openai.com/v1/chat/completions
    api-key: ${LLM_API_KEY}
    model: gpt-4o-mini
    timeout-seconds: 20
```

Docker Compose 场景下，推荐在 `infra/.env` 中配置 `LLM_*`（模板见 `infra/.env.example`）。
`infra/.env` 已加入 `.gitignore`，不会提交到仓库。

DeepSeek 示例（推荐）：
```dotenv
LLM_ENABLED=true
LLM_PROVIDER=deepseek
LLM_ENDPOINT=https://api.deepseek.com/chat/completions
LLM_API_KEY=your-deepseek-api-key
LLM_MODEL=deepseek-chat
LLM_TIMEOUT_SECONDS=30
```

Primary/Fallback 路由示例：
```dotenv
LLM_ENABLED=true
LLM_PROVIDER=deepseek
LLM_ENDPOINT=https://api.deepseek.com/chat/completions
LLM_API_KEY=primary-key
LLM_MODEL=deepseek-chat
LLM_FALLBACK_PROVIDER=openai
LLM_FALLBACK_ENDPOINT=https://api.openai.com/v1/chat/completions
LLM_FALLBACK_API_KEY=fallback-key
LLM_FALLBACK_MODEL=gpt-4o-mini
```

执行策略示例（可按 profile 覆盖）：
```yaml
ainativeos:
  execution:
    default-max-retries: 2
    rollback-on-failure: true
    default-op-timeout-seconds: 60
    circuit-breaker-failure-threshold: 3
    circuit-breaker-open-seconds: 60
    rate-limit-per-minute: 120
    profiles:
      strict:
        max-retries: 1
        op-timeout-seconds: 45
        circuit-breaker-failure-threshold: 2
        rate-limit-per-minute: 60
      relaxed:
        max-retries: 4
        op-timeout-seconds: 120
        circuit-breaker-failure-threshold: 5
        rate-limit-per-minute: 300
```

生效验证：
```bash
sudo docker compose -f infra/docker-compose.yml up -d --build --force-recreate control-plane
sudo docker compose -f infra/docker-compose.yml exec control-plane sh -lc 'echo $LLM_ENABLED; echo $LLM_PROVIDER; [ -n "$LLM_API_KEY" ] && echo API_KEY_SET=true || echo API_KEY_SET=false'
```

Webhook 事件推送配置示例：
```dotenv
EVENTS_ENABLED=true
EVENT_WEBHOOK_URLS=http://127.0.0.1:9000/hook,http://127.0.0.1:9001/hook
EVENT_TIMEOUT_SECONDS=5
```

## 字段字典

### GoalSpec（`/api/goals/plan` 与 `/api/goals/execute` 请求体）
| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `goalId` | `string` | 是 | 目标唯一标识。 | `goal-exec-001` |
| `naturalLanguageIntent` | `string` | 是 | 自然语言目标描述。 | `安装 mysql 客户端并验证` |
| `successCriteria` | `string[]` | 是 | 成功判据列表。 | `["client_installed","smoke_test_passed"]` |
| `constraints` | `object<string,string>` | 否 | 运行/策略约束键值集合。 | `{ "runtimeCommand":"hostname" }` |
| `maxRetries` | `number` | 否 | 原子操作失败后的最大重试次数；`<=0` 使用默认配置。 | `2` |
| `policyProfile` | `string` | 否 | 策略档位；空值自动归一化为 `default`。 | `default` |

### constraints 常用键
| 键 | 必填 | 说明 | 示例 |
|---|---|---|---|
| `runtimeCommand` | 否 | 运行时阶段要执行的命令。 | `echo hello` |
| `simulateFailure` | 否 | 设为 `true` 时首轮 apply 人工失败，用于验证自愈链路。 | `true` |
| `remoteHost` | 否 | SSH 目标地址。 | `172.23.115.116` |
| `remotePort` | 否 | SSH 端口，默认 `22`。 | `22` |
| `remoteUser` | 否 | SSH 用户名。 | `jiyongyang` |
| `remotePassword` | 否 | SSH 密码（兜底认证）。 | `123456` |
| `remotePrivateKeyBase64` | 否 | Base64 编码的私钥内容（推荐）。 | `<base64-pem>` |
| `remotePrivateKey` | 否 | 原始 PEM 私钥文本。 | `-----BEGIN OPENSSH PRIVATE KEY-----...` |
| `remotePassphrase` | 否 | 私钥口令（私钥加密时使用）。 | `my-passphrase` |

### GoalPlan（`/api/goals/plan` 响应）
| 字段 | 类型 | 说明 |
|---|---|---|
| `goalSpec` | `GoalSpec` | 校验后的目标输入。 |
| `desiredState` | `object` | 目标声明式状态。 |
| `atomicOps` | `AtomicOp[]` | 规划后的原子步骤序列。 |
| `plannerVersion` | `string` | 规划器版本标识。 |
| `llmUsed` | `boolean` | 本次规划是否命中 LLM 推理。 |
| `llmRationale` | `string` | LLM 推理摘要（失败回退时通常为空）。 |
| `planGraph` | `object` | 可回放 DAG 快照（节点/依赖/失败分支）。 |

### GoalExecutionResult（`/api/goals/execute` 响应）
| 字段 | 类型 | 说明 |
|---|---|---|
| `goalId` | `string` | 目标 ID。 |
| `status` | `string` | 最终状态：`SUCCEEDED`、`FAILED`、`BLOCKED`。 |
| `message` | `string` | 执行结果摘要。 |
| `llmUsed` | `boolean` | 本次执行对应计划是否命中 LLM 推理。 |
| `llmRationale` | `string` | 规划阶段 LLM 推理摘要。 |
| `failureObject` | `object \| null` | 终态失败时的结构化失败对象。 |
| `trace` | `ExecutionTraceEntry[]` | 全链路执行轨迹。 |
| `completedAt` | `datetime` | 完成时间（UTC）。 |

### FailureObject（`GoalExecutionResult.failureObject`）
| 字段 | 类型 | 说明 |
|---|---|---|
| `failureId` | `string` | 失败事件 ID。 |
| `goalId` | `string` | 所属目标 ID。 |
| `failedOpId` | `string` | 失败原子步骤 ID。 |
| `contextStack` | `ContextFrame[]` | 失败时上下文栈。 |
| `errorVectors` | `ErrorVector[]` | 结构化错误向量。 |
| `patchHints` | `string[]` | 修复建议。 |
| `retryToken` | `string` | 重试链路标记。 |
| `diagnostics` | `object` | Provider 诊断信息。 |

### 执行记录查询（`GET /api/goals/executions`）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 执行记录主键。 |
| `goalId` | `string` | 目标 ID。 |
| `status` | `string` | 最终状态。 |
| `summary` | `string` | 执行摘要。 |
| `plannerVersion` | `string` | 规划器版本。 |
| `createdAt` | `datetime` | 记录创建时间。 |

### 轨迹查询（`GET /api/goals/{goalId}/trace`）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 轨迹记录主键。 |
| `goalId` | `string` | 目标 ID。 |
| `opId` | `string` | 原子步骤 ID。 |
| `opType` | `string` | 原子步骤类型。 |
| `provider` | `string` | 执行该步骤的 Provider。 |
| `status` | `string` | 步骤状态。 |
| `message` | `string` | 步骤消息。 |
| `attempt` | `number` | 重试序号（从 1 开始）。 |
| `timestamp` | `datetime` | 事件时间。 |

## 本地运行
```bash
docker compose -f infra/docker-compose.yml up -d --build
```

启动后访问：
- 后端 API：`http://127.0.0.1:8080/api/goals/health`
- 前端测试页：`http://127.0.0.1:8081`

前端页面内置以下接口测试按钮：
- `GET /api/goals/health`
- `POST /api/goals/plan`
- `POST /api/goals/execute`
- `GET /api/goals/executions`
- `GET /api/goals/{goalId}/trace`
- `GET /api/goals/{goalId}/replay`
- `GET /api/goals/reconcile-jobs`
- `GET /api/goals/capabilities`


## 常见问题排障

### 1）`llmUsed=false` 是否代表代码未生效？
不一定。`llmUsed=false` 还可能由以下原因导致：
- `LLM_ENABLED` 不是 `true`
- `LLM_API_KEY` 为空或未注入容器
- LLM 接口返回非 2xx/超时/响应格式不符合预期（系统会自动回退规则引擎）

建议检查：
```bash
sudo docker compose -f infra/docker-compose.yml logs --tail=200 control-plane
```

### 2）`8081` 前端页面无法访问
先确认容器是否存在并映射端口：
```bash
sudo docker compose -f infra/docker-compose.yml ps
curl -I http://127.0.0.1:8081
```
若 VM 内可访问而外部不可访问，通常是防火墙未放行：
```bash
sudo ufw allow 8080/tcp
sudo ufw allow 8081/tcp
sudo ufw reload
```

## VM 部署
见 `docs/vm-setup.md`
