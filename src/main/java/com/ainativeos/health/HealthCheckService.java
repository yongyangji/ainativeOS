package com.ainativeos.health;

import com.ainativeos.capability.CapabilityProvider;
import com.ainativeos.kernel.execution.SemanticExecutionEngine;
import com.ainativeos.kernel.healing.FailureAnalyzer;
import com.ainativeos.kernel.healing.RepairPlanner;
import com.ainativeos.kernel.policy.ExecutionCircuitBreakerService;
import com.ainativeos.kernel.policy.TaskRateLimiterService;
import com.ainativeos.persistence.repository.DesiredStateJobRepository;
import com.ainativeos.kernel.planner.GoalPlanner;
import com.ainativeos.runtime.RuntimeCommandDispatcher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
/**
 * 动态健康检查服务。
 * <p>
 * 该服务不依赖静态常量返回，而是实时检查：
 * 1. 数据库可用性（SELECT 1）
 * 2. 语义内核关键组件是否装配
 * 3. 能力层 Provider 是否完整
 * 4. 自愈模块是否装配
 */
public class HealthCheckService {

    private final JdbcTemplate jdbcTemplate;
    private final GoalPlanner goalPlanner;
    private final SemanticExecutionEngine executionEngine;
    private final List<CapabilityProvider> capabilityProviders;
    private final FailureAnalyzer failureAnalyzer;
    private final RepairPlanner repairPlanner;
    private final DesiredStateJobRepository desiredStateJobRepository;
    private final ExecutionCircuitBreakerService circuitBreakerService;
    private final TaskRateLimiterService taskRateLimiterService;
    private final RuntimeCommandDispatcher runtimeCommandDispatcher;

    public HealthCheckService(
            JdbcTemplate jdbcTemplate,
            GoalPlanner goalPlanner,
            SemanticExecutionEngine executionEngine,
            List<CapabilityProvider> capabilityProviders,
            FailureAnalyzer failureAnalyzer,
            RepairPlanner repairPlanner,
            DesiredStateJobRepository desiredStateJobRepository,
            ExecutionCircuitBreakerService circuitBreakerService,
            TaskRateLimiterService taskRateLimiterService,
            RuntimeCommandDispatcher runtimeCommandDispatcher
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.goalPlanner = goalPlanner;
        this.executionEngine = executionEngine;
        this.capabilityProviders = capabilityProviders;
        this.failureAnalyzer = failureAnalyzer;
        this.repairPlanner = repairPlanner;
        this.desiredStateJobRepository = desiredStateJobRepository;
        this.circuitBreakerService = circuitBreakerService;
        this.taskRateLimiterService = taskRateLimiterService;
        this.runtimeCommandDispatcher = runtimeCommandDispatcher;
    }

    public Map<String, Object> check() {
        // 统一健康返回对象，便于前端/运维系统直接消费
        Map<String, Object> response = new HashMap<>();
        response.put("service", "ainativeos-control-plane");
        response.put("timestamp", Instant.now());

        String dbStatus = checkDatabase();
        String semanticKernelStatus = checkSemanticKernel();
        String capabilityFabricStatus = checkCapabilityFabric();
        String selfHealingStatus = checkSelfHealing();
        String reconcileControllerStatus = checkReconcileController();
        String policyCenterStatus = checkPolicyCenter();
        String runtimeAdapterStatus = checkRuntimeAdapters();

        response.put("database", dbStatus);
        response.put("semanticKernel", semanticKernelStatus);
        response.put("capabilityFabric", capabilityFabricStatus);
        response.put("selfHealingVfs", selfHealingStatus);
        response.put("reconcileController", reconcileControllerStatus);
        response.put("policyCenter", policyCenterStatus);
        response.put("runtimeAdapters", runtimeAdapterStatus);

        // 总体状态：全部 ready 为 UP，否则标记为 DEGRADED
        boolean healthy = "ready".equals(dbStatus)
                && "ready".equals(semanticKernelStatus)
                && "ready".equals(capabilityFabricStatus)
                && "ready".equals(selfHealingStatus)
                && "ready".equals(reconcileControllerStatus)
                && "ready".equals(policyCenterStatus)
                && "ready".equals(runtimeAdapterStatus);
        response.put("status", healthy ? "UP" : "DEGRADED");
        return response;
    }

    private String checkDatabase() {
        try {
            // 最轻量的可达性探测；不读取业务表，避免健康检查产生额外副作用
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1 ? "ready" : "degraded";
        } catch (Exception ignored) {
            return "degraded";
        }
    }

    private String checkSemanticKernel() {
        // planner + execution engine 是语义内核最关键的两个装配点
        return goalPlanner != null && executionEngine != null ? "ready" : "degraded";
    }

    private String checkCapabilityFabric() {
        // 能力层至少要有一个 runtime provider，才可执行 RUNTIME_* 操作
        boolean hasRuntimeProvider = capabilityProviders.stream()
                .anyMatch(provider -> "runtime-provider".equals(provider.providerName()));
        return !capabilityProviders.isEmpty() && hasRuntimeProvider ? "ready" : "degraded";
    }

    private String checkSelfHealing() {
        // 失败分析 + 修复规划都存在，才说明自愈链路完整
        return failureAnalyzer != null && repairPlanner != null ? "ready" : "degraded";
    }

    private String checkReconcileController() {
        return desiredStateJobRepository != null ? "ready" : "degraded";
    }

    private String checkPolicyCenter() {
        return circuitBreakerService != null && taskRateLimiterService != null ? "ready" : "degraded";
    }

    private String checkRuntimeAdapters() {
        return runtimeCommandDispatcher != null && !runtimeCommandDispatcher.registeredAdapters().isEmpty()
                ? "ready"
                : "degraded";
    }
}
