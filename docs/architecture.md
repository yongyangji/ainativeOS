# AI-Native OS Technical Architecture

## 1. Semantic Kernel (Goal-Driven)
- Input: natural language `GoalSpec`
- Planner: build `GoalPlan` with ordered `AtomicOp` graph
- Executor: deterministic state machine with retry, repair, rollback
- Semantic planning engine: `IntentParser -> PlanGraphBuilder -> PlanVerifier`

## 2. Unified Abstraction Layer
- Capability router dispatches op types to providers
- Providers are API-first and platform-agnostic
- Current providers: File, Network, Compute, Runtime, System, Kubernetes, Cloud CLI

## 3. Self-healing VFS Contract
- Failures return structured `FailureObject`
- Includes `ContextStack`, `ErrorVector`, patch hints, retry token
- Repair planner mutates op in-memory and retries

## 4. Stateless Runtime Model
- Desired end-state represented by `DesiredState`
- Runtime apply modeled as `RUNTIME_APPLY_DECLARATIVE_STATE`
- Runtime provider can execute concrete shell command when requested
- Runtime provider supports local and SSH remote command execution adapters (password or key auth)
- DesiredState reconcile loop supported via `reconcileApplyCommand` + `reconcileVerifyCommand`

## 5. Persistence and Replay
- `goal_execution`: goal outcome and failure JSON snapshot
- `goal_trace`: per-op execution events for replay/audit
- Query APIs expose latest execution history and trace timeline

## 6. Governance and Policy
- Policy engine gates execution before side effects
- Strict profile can block high-risk requests
- Execution policy configured through `ainativeos.execution.*`
