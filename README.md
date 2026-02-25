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
- 策略门控：`kernel/policy/SimplePolicyEngine`
- 执行状态机：`kernel/execution/SemanticExecutionEngine`
- 能力总线：`capability/CapabilityRouter` + Providers
- 运行时适配器：`runtime/LocalCommandExecutor`、`runtime/SshCommandExecutor`
- 自愈模块：`kernel/healing/FailureAnalyzer` + `RepairPlanner`
- 持久化：`goal_execution` + `goal_trace`

## API 列表
- `POST /api/goals/plan`
- `POST /api/goals/execute`
- `GET /api/goals/executions?goalId=...`
- `GET /api/goals/{goalId}/trace`
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
  "semanticKernel": "ready",
  "capabilityFabric": "ready",
  "selfHealingVfs": "ready"
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
  "plannerVersion": "planner-v2"
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
    "plannerVersion": "planner-v2",
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
