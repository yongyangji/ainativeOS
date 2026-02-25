# AI-Native OS 优化路线图（Roadmap）

更新时间：2026-02-25

## 目标
围绕以下四个方向，将当前项目从“可运行原型”推进到“可扩展平台”：
- 强化 AI 核心调度能力
- 扩展硬件/平台支持范围
- 完善开发者工具链
- 构建应用生态系统

## 分期规划

### P1（2-3 周）：调度与可观测基础
重点：先把“调度可控、可观测、可回滚”做实。

交付：
- Planner v4（DAG 调度模型，替代纯线性序列）
- 执行策略中心（重试/超时/熔断/补偿统一配置）
- LLM 路由基础（provider 选择 + fallback）
- 调度可观测性（每个 atomic op 的耗时/状态/重试次数）

结果指标：
- 关键场景成功率 >= 95%
- 失败任务可定位率 >= 90%
- 核心 API 99% 请求在 2s 内返回计划

### P2（3-4 周）：多平台执行层
重点：将“统一抽象层”从接口扩展到真实平台适配。

交付：
- Runtime Adapter SPI（local/ssh/docker/k8s 统一扩展点）
- K8s Provider（apply/verify/rollback）
- Docker Runtime Provider（镜像运行、状态检查）
- 平台能力矩阵与兼容测试

结果指标：
- 新增 Provider 接入开发时间 <= 2 天
- 跨平台同一 GoalSpec 成功执行率 >= 90%

### P3（4-6 周）：开发者与生态
重点：可用性和可扩展性产品化。

交付：
- CLI（plan/execute/trace/reconcile）
- SDK（Java/Python）
- 插件/Skill 规范（schema、权限、版本）
- 模板市场最小版（部署、巡检、自愈模板）

结果指标：
- 新开发者 1 小时内跑通完整链路
- 模板化任务复用率 >= 60%

## 架构演进要点

### 1. Semantic Kernel
- 从规则驱动扩展为“规则 + LLM + 策略引擎”三层决策。
- 引入优先级队列和任务配额，支持多目标并发调度。

### 2. Unified Abstraction
- 定义稳定 Capability Contract：
  - 输入：`GoalSpec + CapabilityRequest`
  - 输出：`CapabilityResult + Diagnostics`
- 所有 Provider 必须返回统一错误向量格式。

### 3. Self-healing VFS
- 标准化 `FailureObject` 扩展字段：
  - `errorClass`
  - `retryable`
  - `suggestedFixes`
  - `providerDiagnostics`
- 支持“自动修复计划生成 + 再执行”闭环追踪。

### 4. Stateless Runtime
- 执行阶段尽量使用不可变镜像。
- 将运行时状态声明（DesiredState）与一次性执行（runtimeCommand）分离。

## 风险与应对
- LLM 不稳定或限流：多模型 fallback + 缓存 + 限流。
- 平台适配复杂度高：SPI 先行，Provider 分层逐步接入。
- 回滚语义不一致：统一“补偿动作”定义并强制测试。

## 里程碑检查点
- M1：DAG + 策略中心上线，替换现有执行主路径。
- M2：K8s 与 Docker provider 可用，端到端通过。
- M3：CLI/SDK/模板市场最小版上线。
