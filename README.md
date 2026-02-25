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

## Runtime Command Mode
- If `constraints.runtimeCommand` is provided, runtime provider executes command via local shell.
- Windows: `powershell -Command <runtimeCommand>`
- Linux: `sh -lc <runtimeCommand>`

## Remote SSH Runtime Mode
- If `constraints.remoteHost`, `remoteUser`, `remotePassword` are provided, `runtimeCommand` is executed via SSH on the remote host.
- Optional: `constraints.remotePort` (default `22`).

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

## Local Run
```bash
docker compose -f infra/docker-compose.yml up -d --build
```

## VM Deployment
See `docs/vm-setup.md`.
