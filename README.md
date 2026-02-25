# AI-Native OS (Java + MySQL)

AI-Native unified operating model with:
- Semantic Kernel for goal-driven orchestration
- Unified capability abstraction (`FILE_*`, `NETWORK_*`, `COMPUTE_*`, `RUNTIME_*`)
- Self-healing VFS contract (`FailureObject` + `ContextStack` + `ErrorVector`)
- Stateless runtime intent (`DesiredState` declarative model)

## Stack
- Java 21
- Spring Boot 3.3.x
- MySQL 8.4
- Docker / Docker Compose

## Main Modules
- Planner: `kernel/planner/DefaultGoalPlanner`
- Policy Gate: `kernel/policy/SimplePolicyEngine`
- Execution State Machine: `kernel/execution/SemanticExecutionEngine`
- Capability Fabric: `capability/CapabilityRouter` + providers
- Runtime Adapter: `runtime/LocalCommandExecutor`
- Self-healing: `kernel/healing/FailureAnalyzer` + `RepairPlanner`
- Persistence: `goal_execution` + `goal_trace`

## API
- `POST /api/goals/plan`
- `POST /api/goals/execute`
- `GET /api/goals/executions?goalId=...`
- `GET /api/goals/{goalId}/trace`
- `GET /api/goals/health`

## API Examples

### 1) Health
Request:
```bash
curl -s http://127.0.0.1:8080/api/goals/health
```

Response:
```json
{
  "service": "ainativeos-control-plane",
  "semanticKernel": "ready",
  "capabilityFabric": "ready",
  "selfHealingVfs": "ready"
}
```

### 2) Plan Goal
Request:
```bash
curl -s -X POST http://127.0.0.1:8080/api/goals/plan \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "goal-plan-001",
    "naturalLanguageIntent": "install mysql client and run smoke test",
    "successCriteria": ["client_installed", "smoke_test_passed"],
    "constraints": {
      "cost": "low",
      "security": "high"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

Response (trimmed):
```json
{
  "goalSpec": {
    "goalId": "goal-plan-001",
    "naturalLanguageIntent": "install mysql client and run smoke test",
    "successCriteria": ["client_installed", "smoke_test_passed"],
    "constraints": {"cost": "low", "security": "high"},
    "maxRetries": 2,
    "policyProfile": "default"
  },
  "desiredState": {
    "stateId": "state-goal-plan-001",
    "summary": "Converge declared runtime state",
    "declaredResources": {
      "runtime": "immutable-container",
      "interface": "api-first-capability-fabric",
      "targetIntent": "install mysql client and run smoke test"
    }
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

### Sample Execute Request
```json
{
  "goalId": "goal-1001",
  "naturalLanguageIntent": "Install mysql client and verify in immutable runtime",
  "successCriteria": ["client_installed", "smoke_test_passed"],
  "constraints": {
    "cost": "low",
    "security": "high",
    "simulateFailure": "true",
    "runtimeCommand": "echo runtime command from semantic kernel"
  },
  "maxRetries": 2,
  "policyProfile": "default"
}
```

### 3) Execute Goal (Local Runtime Command)
Request:
```bash
curl -s -X POST http://127.0.0.1:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "goal-exec-local-001",
    "naturalLanguageIntent": "execute runtime command locally",
    "successCriteria": ["command_ok"],
    "constraints": {
      "runtimeCommand": "echo hello-local"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

Response (trimmed):
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
  ],
  "completedAt": "2026-02-25T04:49:35.763416274Z"
}
```

### 4) Execute Goal (Self-healing Retry Path)
Request (`simulateFailure=true` makes first apply fail, then patch+retry):
```bash
curl -s -X POST http://127.0.0.1:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "goal-exec-heal-001",
    "naturalLanguageIntent": "test self-healing",
    "successCriteria": ["command_ok"],
    "constraints": {
      "runtimeCommand": "echo healed",
      "simulateFailure": "true"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

Response behavior:
- `op-apply` attempt `1`: `FAILED`
- `op-apply` attempt `2`: `SUCCEEDED`
- final `status`: `SUCCEEDED`

## Runtime Command Mode
- If `constraints.runtimeCommand` is provided, runtime provider executes command via local shell.
- Windows: `powershell -Command <runtimeCommand>`
- Linux: `sh -lc <runtimeCommand>`

## Remote SSH Runtime Mode
- If `constraints.remoteHost`, `remoteUser`, `remotePassword` are provided, `runtimeCommand` is executed via SSH on the remote host.
- Optional: `constraints.remotePort` (default `22`).
- Key auth is also supported and preferred:
  - `remotePrivateKeyBase64` (recommended)
  - or `remotePrivateKey`
  - optional `remotePassphrase`

```json
{
  "goalId": "remote-goal-001",
  "naturalLanguageIntent": "run command on remote runtime host",
  "successCriteria": ["remote_ok"],
  "constraints": {
    "runtimeCommand": "hostname",
    "remoteHost": "172.23.115.116",
    "remotePort": "22",
    "remoteUser": "jiyongyang",
    "remotePassword": "123456"
  },
  "maxRetries": 2,
  "policyProfile": "default"
}
```

### 5) Execute Goal (Remote SSH Key Auth)
Request:
```bash
curl -s -X POST http://127.0.0.1:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "goal-exec-ssh-key-001",
    "naturalLanguageIntent": "execute command over ssh key",
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

Response (trimmed):
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

### 6) Query Executions
Request:
```bash
curl -s "http://127.0.0.1:8080/api/goals/executions?goalId=goal-exec-ssh-key-001"
```

Response:
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

### 7) Query Trace
Request:
```bash
curl -s "http://127.0.0.1:8080/api/goals/goal-exec-ssh-key-001/trace"
```

Response (trimmed):
```json
[
  {"opId": "op-parse", "status": "SUCCEEDED", "attempt": 1},
  {"opId": "op-policy", "status": "SUCCEEDED", "attempt": 1},
  {"opId": "op-capability", "status": "SUCCEEDED", "attempt": 1},
  {"opId": "op-apply", "status": "SUCCEEDED", "attempt": 1},
  {"opId": "op-verify", "status": "SUCCEEDED", "attempt": 1}
]
```

## Field Dictionary

### GoalSpec (request body for `/api/goals/plan` and `/api/goals/execute`)
| Field | Type | Required | Description | Example |
|---|---|---|---|---|
| `goalId` | `string` | Yes | Goal unique identifier. | `goal-exec-001` |
| `naturalLanguageIntent` | `string` | Yes | Natural language goal description. | `install mysql client and verify` |
| `successCriteria` | `string[]` | Yes | Success conditions for verification stage. | `["client_installed","smoke_test_passed"]` |
| `constraints` | `object<string,string>` | No | Runtime and policy constraints map. | `{ "runtimeCommand":"hostname" }` |
| `maxRetries` | `number` | No | Max retries for failed atomic operations. Default from config if `<=0`. | `2` |
| `policyProfile` | `string` | No | Policy profile name. Empty/null -> `default`. | `default` |

### `constraints` common keys
| Key | Required | Description | Example |
|---|---|---|---|
| `runtimeCommand` | No | Shell command executed in runtime stage. | `echo hello` |
| `simulateFailure` | No | If `true`, first runtime apply intentionally fails to test self-healing. | `true` |
| `remoteHost` | No | SSH target host/IP for remote runtime execution. | `172.23.115.116` |
| `remotePort` | No | SSH target port, default `22`. | `22` |
| `remoteUser` | No | SSH username. | `jiyongyang` |
| `remotePassword` | No | SSH password (fallback auth mode). | `123456` |
| `remotePrivateKeyBase64` | No | Base64 encoded private key content (preferred). | `<base64-pem>` |
| `remotePrivateKey` | No | Raw PEM private key text. | `-----BEGIN OPENSSH PRIVATE KEY-----...` |
| `remotePassphrase` | No | Passphrase for encrypted private key. | `my-passphrase` |

### GoalPlan (response from `/api/goals/plan`)
| Field | Type | Description |
|---|---|---|
| `goalSpec` | `GoalSpec` | Input goal after validation and normalization context. |
| `desiredState` | `object` | Declarative target state for runtime convergence. |
| `atomicOps` | `AtomicOp[]` | Planned execution steps in order. |
| `plannerVersion` | `string` | Planner implementation version identifier. |

### GoalExecutionResult (response from `/api/goals/execute`)
| Field | Type | Description |
|---|---|---|
| `goalId` | `string` | Echoed goal id. |
| `status` | `string` | Final status: `SUCCEEDED`, `FAILED`, `BLOCKED`. |
| `message` | `string` | Human-readable summary of execution outcome. |
| `failureObject` | `object \| null` | Structured failure detail when terminal failure occurs. |
| `trace` | `ExecutionTraceEntry[]` | Full step-by-step execution trace. |
| `completedAt` | `datetime` | Completion timestamp (UTC). |

### FailureObject (inside `GoalExecutionResult.failureObject`)
| Field | Type | Description |
|---|---|---|
| `failureId` | `string` | Failure event unique id. |
| `goalId` | `string` | Related goal id. |
| `failedOpId` | `string` | Failed atomic op id. |
| `contextStack` | `ContextFrame[]` | Runtime context stack at failure time. |
| `errorVectors` | `ErrorVector[]` | Structured error vectors for reasoning/repair. |
| `patchHints` | `string[]` | Suggested patch/recovery hints. |
| `retryToken` | `string` | Token associated with retry attempt chain. |
| `diagnostics` | `object` | Provider-specific diagnostic payload. |

### Execution list API (`GET /api/goals/executions`)
| Field | Type | Description |
|---|---|---|
| `id` | `number` | Execution row id. |
| `goalId` | `string` | Goal id. |
| `status` | `string` | Final status. |
| `summary` | `string` | Summary message persisted for query view. |
| `plannerVersion` | `string` | Planner version used for this run. |
| `createdAt` | `datetime` | Persisted execution time. |

### Trace API (`GET /api/goals/{goalId}/trace`)
| Field | Type | Description |
|---|---|---|
| `id` | `number` | Trace row id. |
| `goalId` | `string` | Goal id. |
| `opId` | `string` | Atomic op id. |
| `opType` | `string` | Atomic op type. |
| `provider` | `string` | Provider that handled this op. |
| `status` | `string` | Per-step status. |
| `message` | `string` | Per-step result message. |
| `attempt` | `number` | Attempt index (starts at 1). |
| `timestamp` | `datetime` | Step event time. |

### Key Auth Example
```json
{
  "goalId": "remote-goal-key-001",
  "naturalLanguageIntent": "run command with ssh key auth",
  "successCriteria": ["remote_ok"],
  "constraints": {
    "runtimeCommand": "hostname",
    "remoteHost": "172.23.115.116",
    "remotePort": "22",
    "remoteUser": "jiyongyang",
    "remotePrivateKeyBase64": "<base64-pem>",
    "remotePassphrase": ""
  },
  "maxRetries": 2,
  "policyProfile": "default"
}
```

## Local Run
```bash
docker compose -f infra/docker-compose.yml up -d --build
```

## VM Deployment
See `docs/vm-setup.md`.
