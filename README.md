# AI-Native OS (Java + MySQL)

AI-Native unified operating model with:
- Semantic Kernel for goal-driven orchestration
- Unified capability abstraction (FILE_*, NETWORK_*, COMPUTE_*, RUNTIME_*)
- Self-healing VFS contract (FailureObject + ContextStack + ErrorVector)
- Stateless runtime intent (DesiredState declarative model)

## Stack
- Java 21
- Spring Boot 3.3.x
- MySQL 8.4
- Docker / Docker Compose

## Main Modules
- Planner: kernel/planner/DefaultGoalPlanner
- Policy Gate: kernel/policy/SimplePolicyEngine
- Execution State Machine: kernel/execution/SemanticExecutionEngine
- Capability Fabric: capability/CapabilityRouter + providers
- Self-healing: kernel/healing/FailureAnalyzer + RepairPlanner
- Persistence: goal_execution + goal_trace

## API
- POST /api/goals/plan
- POST /api/goals/execute
- GET /api/goals/health

### Sample Execute Request
`json
{
  "goalId": "goal-1001",
  "naturalLanguageIntent": "Install mysql client and verify in immutable runtime",
  "successCriteria": ["client_installed", "smoke_test_passed"],
  "constraints": {
    "cost": "low",
    "security": "high",
    "simulateFailure": "true"
  },
  "maxRetries": 2,
  "policyProfile": "default"
}
`

With simulateFailure=true, runtime op fails once, then self-healing patch removes failure flag and retries.

## Local Run
`ash
docker compose -f infra/docker-compose.yml up -d --build
`

## VM Deployment
See docs/vm-setup.md.
