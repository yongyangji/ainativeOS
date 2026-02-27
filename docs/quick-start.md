# AinativeOS 用户快速指南

## 🚀 5分钟快速入门

### 1. 环境准备
```bash
# 确保已安装 Docker 和 Docker Compose
docker --version
docker compose version
```

### 2. 启动服务
```bash
# 克隆项目（如果尚未克隆）
git clone <repository-url>
cd ainativeOS

# 启动所有服务
docker compose -f infra/docker-compose.yml up -d --build
```

### 3. 验证服务运行状态
```bash
# 检查控制平面健康状态
curl http://localhost:8080/api/goals/health

# 检查前端测试页面
curl -I http://localhost:8081
```

### 4. 第一个目标：本地命令执行
```bash
curl -X POST http://localhost:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "my-first-goal",
    "naturalLanguageIntent": "执行简单的本地命令",
    "successCriteria": ["command_executed"],
    "constraints": {
      "runtimeCommand": "echo Hello AinativeOS!"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

### 5. 查看执行结果
```bash
# 查询执行历史
curl "http://localhost:8080/api/goals/executions?goalId=my-first-goal"

# 查看详细执行轨迹
curl "http://localhost:8080/api/goals/my-first-goal/trace"
```

## 📖 核心概念快速理解

### 目标 (Goal)
- **自然语言意图**：用人类语言描述你想做什么
- **成功标准**：如何判断目标已达成
- **约束条件**：执行时的限制和参数

### 原子操作 (AtomicOp)
- 系统将目标分解为可执行的原子步骤
- 每个操作类型对应一种能力（FILE_*, NETWORK_*, COMPUTE_*, RUNTIME_*）

### 自愈机制
- 执行失败时自动分析原因
- 尝试修复并重试
- 支持回滚操作

## 🔧 常用场景示例

### 场景1：本地文件操作
```bash
curl -X POST http://localhost:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "create-log-file",
    "naturalLanguageIntent": "创建一个日志文件",
    "successCriteria": ["file_created"],
    "constraints": {
      "runtimeCommand": "echo \"$(date): Application started\" > /tmp/app.log"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

### 场景2：远程SSH执行
```bash
curl -X POST http://localhost:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "check-remote-status",
    "naturalLanguageIntent": "检查远程服务器状态",
    "successCriteria": ["remote_accessible", "system_info_collected"],
    "constraints": {
      "runtimeCommand": "uptime && free -h",
      "remoteHost": "your-server-ip",
      "remoteUser": "username",
      "remotePassword": "your-password"
    },
    "maxRetries": 2,
    "policyProfile": "default"
  }'
```

### 场景3：持续收敛（自动修复）
```bash
curl -X POST http://localhost:8080/api/goals/execute \
  -H "Content-Type: application/json" \
  -d '{
    "goalId": "ensure-service-running",
    "naturalLanguageIntent": "确保Nginx服务正在运行",
    "successCriteria": ["service_running"],
    "constraints": {
      "runtimeCommand": "systemctl status nginx || systemctl start nginx",
      "reconcileApplyCommand": "systemctl start nginx",
      "reconcileVerifyCommand": "systemctl is-active nginx",
      "continuousReconcile": "true",
      "reconcileMaxRounds": "5",
      "reconcileIntervalMs": "3000"
    },
    "maxRetries": 3,
    "policyProfile": "default"
  }'
```

## 🎯 高级功能

### LLM集成（可选）
在 `infra/.env` 中配置：
```env
LLM_ENABLED=true
LLM_PROVIDER=deepseek
LLM_ENDPOINT=https://api.deepseek.com/chat/completions
LLM_API_KEY=your-api-key
LLM_MODEL=deepseek-chat
```

### 插件系统
查看可用插件：
```bash
curl "http://localhost:8080/api/goals/plugins"
```

### 平台能力查询
```bash
curl "http://localhost:8080/api/goals/capabilities"
```

## 🛠️ 故障排查

### 1. 服务启动失败
```bash
# 查看日志
docker compose -f infra/docker-compose.yml logs control-plane

# 检查数据库连接
docker compose -f infra/docker-compose.yml exec mysql mysql -uainativeos -painativeos_pwd -e "SELECT 1"
```

### 2. API调用失败
```bash
# 检查API端点
curl -v http://localhost:8080/api/goals/health

# 查看错误日志
docker compose -f infra/docker-compose.yml logs --tail=100 control-plane
```

### 3. SSH连接失败
- 确认SSH服务正在运行：`systemctl status ssh`
- 检查防火墙设置
- 验证用户名和密码/密钥

## 📊 监控和调试

### 前端测试页面
访问：http://localhost:8081
- 提供所有核心API的可视化测试界面
- 支持预填充请求体和一键发送

### 执行轨迹查看
```bash
# 查看特定目标的完整执行轨迹
curl "http://localhost:8080/api/goals/{goalId}/trace"

# 回放执行过程
curl "http://localhost:8080/api/goals/{goalId}/replay"
```

## 🔄 持续集成

### 使用CI/CD模板
项目已包含GitHub Actions配置：
- 自动运行测试
- 构建Docker镜像
- 发布到GitHub Packages

## 📚 下一步
1. **阅读详细文档**：查看 `docs/architecture.md` 了解架构设计
2. **探索API**：使用前端测试页面尝试不同API
3. **定制开发**：根据需要开发自定义插件
4. **生产部署**：参考 `docs/vm-setup.md` 进行生产环境部署

## 💡 小贴士
- 所有API调用都会返回结构化JSON，便于自动化处理
- 使用 `policyProfile` 参数控制执行策略（strict/relaxed/default）
- 执行失败时会自动生成修复建议和重试令牌
- 可以通过Webhook接收执行完成通知

---

**需要帮助？**
- 查看详细文档：`docs/` 目录
- 报告问题：GitHub Issues
- 联系维护者：项目README中的联系方式