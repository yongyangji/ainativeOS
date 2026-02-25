# AI-Native OS Technical Architecture

## 1. Semantic Kernel (Goal-Driven)
- Input: natural language `GoalSpec`
- Planner: build `GoalPlan` with ordered `AtomicOp` graph
- Executor: deterministic state machine with retry, repair, rollback
- Semantic planning engine: `IntentParser -> PlanGraphBuilder -> PlanVerifier`
- Optional LLM reasoner: when enabled, planner merges `SemanticReasoner` hints into parsed intent

### 1. 语义内核（目标驱动）
- 输入：自然语言 `GoalSpec`
- 规划器：构建包含有序 `AtomicOp` 图的 `GoalPlan`
- 执行器：确定性状态机，支持重试、修复、回滚
- 语义规划引擎：`IntentParser -> PlanGraphBuilder -> PlanVerifier`
- 可选 LLM 推理器：启用后将 `SemanticReasoner` 的提示信息合并到意图解析结果

## 2. Unified Abstraction Layer
- Capability router dispatches op types to providers
- Providers are API-first and platform-agnostic
- Current providers: File, Network, Compute, Runtime, System, Kubernetes, Cloud CLI
- Provider productionization: operation audit + idempotent short-circuit

### 2. 统一抽象层
- 能力路由器按 op 类型分发到各 Provider
- Provider 采用 API-first 设计，平台无关
- 当前 Provider：File、Network、Compute、Runtime、System、Kubernetes、Cloud CLI
- 生产化能力：操作审计 + 幂等短路

## 3. Self-healing VFS Contract
- Failures return structured `FailureObject`
- Includes `ContextStack`, `ErrorVector`, patch hints, retry token
- Repair planner mutates op in-memory and retries

### 3. 自愈 VFS 契约
- 失败返回结构化 `FailureObject`
- 包含 `ContextStack`、`ErrorVector`、修复建议和重试令牌
- 修复规划器在内存中修改操作并重试

## 4. Stateless Runtime Model
- Desired end-state represented by `DesiredState`
- Runtime apply modeled as `RUNTIME_APPLY_DECLARATIVE_STATE`
- Runtime provider can execute concrete shell command when requested
- Runtime provider supports local and SSH remote command execution adapters (password or key auth)
- DesiredState reconcile loop supported via `reconcileApplyCommand` + `reconcileVerifyCommand`
- Continuous reconcile controller runs in background via scheduled polling of `desired_state_job`

### 4. 无状态运行时模型
- 目标终态由 `DesiredState` 表达
- 运行时应用动作抽象为 `RUNTIME_APPLY_DECLARATIVE_STATE`
- Runtime Provider 可按需执行具体 shell 命令
- Runtime Provider 支持本地和 SSH 远程命令执行适配（密码或密钥认证）
- 通过 `reconcileApplyCommand` + `reconcileVerifyCommand` 支持 DesiredState 收敛循环
- 持续收敛控制器通过定时轮询 `desired_state_job` 在后台运行

## 5. Persistence and Replay
- `goal_execution`: goal outcome and failure JSON snapshot
- `goal_trace`: per-op execution events for replay/audit
- `execution_audit`: provider-level operation audit and idempotency short-circuit basis
- `desired_state_job`: continuous reconcile controller job state
- Query APIs expose latest execution history and trace timeline

### 5. 持久化与回放
- `goal_execution`：目标结果和失败 JSON 快照
- `goal_trace`：逐操作执行事件，用于回放/审计
- `execution_audit`：Provider 级操作审计与幂等短路依据
- `desired_state_job`：持续收敛控制器任务状态
- 查询 API 提供最新执行历史和轨迹时间线

## 6. Governance and Policy
- Policy engine gates execution before side effects
- Strict profile can block high-risk requests
- Execution policy configured through `ainativeos.execution.*`

### 6. 治理与策略
- 策略引擎在副作用发生前进行执行门控
- Strict 档位可拦截高风险请求
- 执行策略通过 `ainativeos.execution.*` 配置
