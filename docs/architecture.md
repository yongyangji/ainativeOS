# AI-Native OS Technical Architecture

## 1. Semantic Kernel (Goal-Driven)
- Input: natural language GoalSpec
- Planner: build GoalPlan with ordered AtomicOp graph
- Executor: deterministic state machine with retry, repair, rollback

## 2. Unified Abstraction Layer
- Capability router dispatches op types to providers
- Providers are API-first and platform-agnostic
- Current providers: File, Network, Compute, Runtime

## 3. Self-healing VFS Contract
- Failures return structured FailureObject
- Includes ContextStack, ErrorVector, patch hints, retry token
- Repair planner mutates op in-memory and retries

## 4. Stateless Runtime Model
- Desired end-state represented by DesiredState
- Runtime apply modeled as RUNTIME_APPLY_DECLARATIVE_STATE
- Works with immutable runtime assumption (WASM/container substrate)

## 5. Persistence
- goal_execution: goal outcome and failure JSON snapshot
- goal_trace: per-op execution events for replay/audit

## 6. Governance
- Policy engine gates execution before side effects
- Strict profile can block high-risk requests
