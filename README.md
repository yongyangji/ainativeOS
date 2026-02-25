# AI-Native OS (Java Control Plane)

## Architecture Mapping
- Semantic Kernel: `SemanticKernelService` + planner/executor APIs.
- Unified Abstraction: represented as atomic operations and capability resolution stage.
- Self-healing VFS contract: `FailureObject`, `ContextFrame`, `ErrorVector` domain models.
- Stateless Runtime: `DesiredState` object and declarative execution stage.

## Run Locally
1. Start MySQL:
   - `docker compose -f infra/docker-compose.yml up -d`
2. Start service:
   - `mvn spring-boot:run`

## Test API
- Plan goal:
`POST /api/goals/plan`
```json
{
  "goalId": "goal-001",
  "naturalLanguageIntent": "Install and verify mysql client in sandbox runtime",
  "successCriteria": ["client_installed", "smoke_test_passed"],
  "constraints": {
    "cost": "low",
    "security": "high"
  }
}
```

## VM Deployment Notes (172.23.115.116)
- See `docs/vm-setup.md` for Docker + MySQL setup and app run instructions.
