# AI-Native OS 迭代任务清单（Backlog）

更新时间：2026-02-25

## P1：强化 AI 核心调度能力

### EPIC-1 调度模型升级（Planner v4）
1. 设计 `PlanGraph` 持久化结构（节点、依赖、分支）
- 验收标准：可序列化存储 DAG，支持按 goalId 回放。
2. 执行器支持并行节点调度
- 验收标准：无依赖节点可并发执行，结果可追踪。
3. 增加失败分支与补偿分支
- 验收标准：任一关键节点失败时可进入 fallback/rollback 分支。

### EPIC-2 策略中心
1. 定义策略模型：`timeout/retry/circuitBreaker/rollback`
- 验收标准：策略可在 `policyProfile` 中配置并生效。
2. 增加执行级熔断
- 验收标准：连续失败触发熔断，返回结构化原因。
3. 增加任务级限流
- 验收标准：超配额请求被限流并可审计。

### EPIC-3 LLM 路由与可观测
1. 增加 LLM provider 路由器
- 验收标准：支持 primary/fallback provider 配置。
2. 记录 LLM 调用指标
- 验收标准：记录耗时、状态码、是否命中 fallback。
3. 增加 plan 级 `llmRationale` 透出
- 验收标准：`/plan` 可返回简要推理摘要（可脱敏）。

## P2：扩展硬件/平台支持范围

### EPIC-4 Runtime Adapter SPI
1. 抽象接口：`prepare/execute/verify/rollback`
- 验收标准：本地与 SSH 迁移到 SPI 无行为回归。
2. Provider 注册与发现机制
- 验收标准：新增 provider 无需改执行核心代码。

### EPIC-5 Docker/K8s Provider
1. Docker provider：镜像执行与状态探测
- 验收标准：可运行容器任务并返回统一结果。
2. K8s provider：apply/verify/rollback
- 验收标准：支持 deployment 场景最小闭环。
3. 平台能力字典
- 验收标准：API 可查询当前可用能力清单。

### EPIC-6 资源调度标签
1. 支持 `computeClass`（cpu/gpu/highmem）
- 验收标准：可按标签路由到不同 provider。
2. 支持执行环境约束校验
- 验收标准：不满足约束时提前失败并返回原因。

## P3：完善开发者工具链

### EPIC-7 CLI 与 SDK
1. CLI 命令：`plan/execute/trace/jobs`
- 验收标准：可覆盖主要 REST API 能力。
2. Java SDK
- 验收标准：提供强类型请求响应模型和重试逻辑。
3. Python SDK
- 验收标准：支持最小可用示例和发布包。

### EPIC-8 测试与 CI/CD
1. 增加契约测试（API schema）
- 验收标准：接口字段变更可被检测。
2. 增加 E2E 测试（mock LLM + real provider）
- 验收标准：主干分支每次提交自动执行。
3. 发布流水线
- 验收标准：自动构建镜像、打版本、输出变更日志。

## P4：构建应用生态系统

### EPIC-9 插件/Skill 系统
1. 定义插件清单（manifest）
- 验收标准：包含输入输出 schema、权限、版本信息。
2. 插件加载器与隔离机制
- 验收标准：插件故障不影响核心执行器。
3. 权限沙箱
- 验收标准：高风险能力需要显式授权。

### EPIC-10 模板与市场
1. 内置模板：部署、巡检、自愈
- 验收标准：3 个模板可一键执行。
2. 模板仓库与版本管理
- 验收标准：支持模板版本回滚。
3. Webhook/事件总线
- 验收标准：执行状态可推送到外部系统。

## 优先级建议
1. 先做 EPIC-1/2/3（核心调度）
2. 再做 EPIC-4/5（平台扩展）
3. 同步做 EPIC-8（测试与发布保障）
4. 最后做 EPIC-9/10（生态扩展）

## Definition of Done（统一）
- 代码合并前有单测/集成测试
- 文档更新（README/architecture/backlog）
- 提供可复现示例请求
- 错误路径有结构化返回并可审计
