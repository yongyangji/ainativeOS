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
- 持久化：`goal_execution` + `goal_trace`

## API 列表
- `POST /api/goals/plan`
- `POST /api/goals/execute`
- `GET /api/goals/executions?goalId=...`
- `GET /api/goals/{goalId}/trace`
- `GET /api/goals/reconcile-jobs?goalId=...`
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
  "plannerVersion": "planner-v3"
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

### GoalExecutionResult（`/api/goals/execute` 响应）
| 字段 | 类型 | 说明 |
|---|---|---|
| `goalId` | `string` | 目标 ID。 |
| `status` | `string` | 最终状态：`SUCCEEDED`、`FAILED`、`BLOCKED`。 |
| `message` | `string` | 执行结果摘要。 |
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

## VM 部署
见 `docs/vm-setup.md`。
