package com.ainativeos.kernel.execution;

import com.ainativeos.audit.OperationAuditService;
import com.ainativeos.capability.CapabilityRouter;
import com.ainativeos.config.ExecutionPolicyProperties;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ExecutionStatus;
import com.ainativeos.domain.ExecutionTraceEntry;
import com.ainativeos.domain.FailureObject;
import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.kernel.healing.FailureAnalyzer;
import com.ainativeos.kernel.healing.RepairPlanner;
import com.ainativeos.kernel.policy.PolicyDecision;
import com.ainativeos.kernel.policy.PolicyEngine;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
/**
 * 语义执行引擎（状态机核心）。
 * <p>
 * 主要职责：
 * 1. 执行前策略评估（Policy Gate）
 * 2. 逐个执行原子操作（AtomicOp）
 * 3. 失败时生成 FailureObject 并触发修复重试
 * 4. 超过重试上限后按策略执行回滚
 * 5. 生成完整 trace 供持久化与审计
 */
public class SemanticExecutionEngine {

    private final CapabilityRouter capabilityRouter;
    private final PolicyEngine policyEngine;
    private final FailureAnalyzer failureAnalyzer;
    private final RepairPlanner repairPlanner;
    private final ExecutionPolicyProperties executionPolicy;
    private final OperationAuditService operationAuditService;

    public SemanticExecutionEngine(
            CapabilityRouter capabilityRouter,
            PolicyEngine policyEngine,
            FailureAnalyzer failureAnalyzer,
            RepairPlanner repairPlanner,
            ExecutionPolicyProperties executionPolicy,
            OperationAuditService operationAuditService
    ) {
        this.capabilityRouter = capabilityRouter;
        this.policyEngine = policyEngine;
        this.failureAnalyzer = failureAnalyzer;
        this.repairPlanner = repairPlanner;
        this.executionPolicy = executionPolicy;
        this.operationAuditService = operationAuditService;
    }

    public GoalExecutionResult run(GoalPlan plan) {
        // 第一步：策略门控，禁止不合规目标进入执行阶段
        PolicyDecision decision = policyEngine.evaluate(plan);
        List<ExecutionTraceEntry> trace = new ArrayList<>();
        List<AtomicOp> executedOps = new ArrayList<>();

        if (!decision.allowed()) {
            trace.add(new ExecutionTraceEntry(
                    plan.goalSpec().goalId(),
                    "policy-gate",
                    "POLICY",
                    "policy-engine",
                    ExecutionStatus.BLOCKED,
                    decision.reason(),
                    Instant.now(),
                    0,
                    List.of()
            ));
            return new GoalExecutionResult(
                    plan.goalSpec().goalId(),
                    ExecutionStatus.BLOCKED,
                    decision.reason(),
                    null,
                    trace,
                    Instant.now()
            );
        }

        // 使用请求级重试配置；未配置时使用系统默认值
        int maxRetries = plan.goalSpec().maxRetries() > 0
                ? plan.goalSpec().maxRetries()
                : executionPolicy.getDefaultMaxRetries();

        // 按规划顺序逐步执行原子操作
        for (AtomicOp op : plan.atomicOps()) {
            if (operationAuditService.shouldShortCircuit(plan.goalSpec().goalId(), op)) {
                trace.add(new ExecutionTraceEntry(
                        plan.goalSpec().goalId(),
                        op.opId(),
                        op.type(),
                        "audit-short-circuit",
                        ExecutionStatus.SUCCEEDED,
                        "Short-circuited by idempotency audit",
                        Instant.now(),
                        0,
                        List.of()
                ));
                continue;
            }

            boolean success = false;
            AtomicOp currentOp = op;
            FailureObject failureObject = null;

            // 每个原子操作允许多次尝试（初次 + 重试）
            for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
                OpExecutionResult opResult = capabilityRouter.execute(currentOp);
                trace.add(new ExecutionTraceEntry(
                        plan.goalSpec().goalId(),
                        currentOp.opId(),
                        currentOp.type(),
                        opResult.provider(),
                        opResult.success() ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED,
                        opResult.message(),
                        Instant.now(),
                        attempt,
                        opResult.contextFrames()
                ));
                operationAuditService.record(
                        plan.goalSpec().goalId(),
                        currentOp,
                        opResult.success() ? "SUCCEEDED" : "FAILED",
                        opResult.provider(),
                        attempt,
                        opResult.message()
                );

                if (opResult.success()) {
                    // 当前步骤成功，进入下一步骤
                    success = true;
                    executedOps.add(currentOp);
                    break;
                }

                // 失败：构建结构化失败对象，供修复器消费
                failureObject = failureAnalyzer.buildFailure(plan.goalSpec().goalId(), currentOp, opResult, attempt);
                if (attempt <= maxRetries) {
                    // 未超过上限：在内存中补丁修复后继续尝试
                    currentOp = repairPlanner.patchForRetry(currentOp, failureObject);
                }
            }

            if (!success) {
                // 某一步最终失败，按配置决定是否回滚历史成功步骤
                if (executionPolicy.isRollbackOnFailure()) {
                    rollbackExecutedOps(executedOps);
                }
                return new GoalExecutionResult(
                        plan.goalSpec().goalId(),
                        ExecutionStatus.FAILED,
                        "Execution failed after retries" + (executionPolicy.isRollbackOnFailure() ? " and rollback completed" : ""),
                        failureObject,
                        trace,
                        Instant.now()
                );
            }
        }

        return new GoalExecutionResult(
                plan.goalSpec().goalId(),
                ExecutionStatus.SUCCEEDED,
                "Goal converged to desired state",
                null,
                trace,
                Instant.now()
        );
    }

    private void rollbackExecutedOps(List<AtomicOp> executedOps) {
        // 逆序回滚，确保依赖顺序与执行相反
        List<AtomicOp> reversed = new ArrayList<>(executedOps);
        Collections.reverse(reversed);
        for (AtomicOp op : reversed) {
            if (op.rollbackSupported()) {
                capabilityRouter.rollback(op);
            }
        }
    }
}
