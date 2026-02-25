package com.ainativeos.health;

import com.ainativeos.capability.CapabilityProvider;
import com.ainativeos.kernel.execution.SemanticExecutionEngine;
import com.ainativeos.kernel.healing.FailureAnalyzer;
import com.ainativeos.kernel.healing.RepairPlanner;
import com.ainativeos.kernel.planner.GoalPlanner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthCheckService {

    private final JdbcTemplate jdbcTemplate;
    private final GoalPlanner goalPlanner;
    private final SemanticExecutionEngine executionEngine;
    private final List<CapabilityProvider> capabilityProviders;
    private final FailureAnalyzer failureAnalyzer;
    private final RepairPlanner repairPlanner;

    public HealthCheckService(
            JdbcTemplate jdbcTemplate,
            GoalPlanner goalPlanner,
            SemanticExecutionEngine executionEngine,
            List<CapabilityProvider> capabilityProviders,
            FailureAnalyzer failureAnalyzer,
            RepairPlanner repairPlanner
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.goalPlanner = goalPlanner;
        this.executionEngine = executionEngine;
        this.capabilityProviders = capabilityProviders;
        this.failureAnalyzer = failureAnalyzer;
        this.repairPlanner = repairPlanner;
    }

    public Map<String, Object> check() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "ainativeos-control-plane");
        response.put("timestamp", Instant.now());

        String dbStatus = checkDatabase();
        String semanticKernelStatus = checkSemanticKernel();
        String capabilityFabricStatus = checkCapabilityFabric();
        String selfHealingStatus = checkSelfHealing();

        response.put("database", dbStatus);
        response.put("semanticKernel", semanticKernelStatus);
        response.put("capabilityFabric", capabilityFabricStatus);
        response.put("selfHealingVfs", selfHealingStatus);

        boolean healthy = "ready".equals(dbStatus)
                && "ready".equals(semanticKernelStatus)
                && "ready".equals(capabilityFabricStatus)
                && "ready".equals(selfHealingStatus);
        response.put("status", healthy ? "UP" : "DEGRADED");
        return response;
    }

    private String checkDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1 ? "ready" : "degraded";
        } catch (Exception ignored) {
            return "degraded";
        }
    }

    private String checkSemanticKernel() {
        return goalPlanner != null && executionEngine != null ? "ready" : "degraded";
    }

    private String checkCapabilityFabric() {
        boolean hasRuntimeProvider = capabilityProviders.stream()
                .anyMatch(provider -> "runtime-provider".equals(provider.providerName()));
        return !capabilityProviders.isEmpty() && hasRuntimeProvider ? "ready" : "degraded";
    }

    private String checkSelfHealing() {
        return failureAnalyzer != null && repairPlanner != null ? "ready" : "degraded";
    }
}
