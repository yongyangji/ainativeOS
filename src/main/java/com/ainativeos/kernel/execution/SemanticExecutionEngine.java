package com.ainativeos.kernel.execution;

import com.ainativeos.capability.CapabilityRouter;
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
public class SemanticExecutionEngine {

    private final CapabilityRouter capabilityRouter;
    private final PolicyEngine policyEngine;
    private final FailureAnalyzer failureAnalyzer;
    private final RepairPlanner repairPlanner;

    public SemanticExecutionEngine(
            CapabilityRouter capabilityRouter,
            PolicyEngine policyEngine,
            FailureAnalyzer failureAnalyzer,
            RepairPlanner repairPlanner
    ) {
        this.capabilityRouter = capabilityRouter;
        this.policyEngine = policyEngine;
        this.failureAnalyzer = failureAnalyzer;
        this.repairPlanner = repairPlanner;
    }

    public GoalExecutionResult run(GoalPlan plan) {
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

        int maxRetries = plan.goalSpec().normalizedMaxRetries();

        for (AtomicOp op : plan.atomicOps()) {
            boolean success = false;
            AtomicOp currentOp = op;
            FailureObject failureObject = null;

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

                if (opResult.success()) {
                    success = true;
                    executedOps.add(currentOp);
                    break;
                }

                failureObject = failureAnalyzer.buildFailure(plan.goalSpec().goalId(), currentOp, opResult, attempt);
                if (attempt <= maxRetries) {
                    currentOp = repairPlanner.patchForRetry(currentOp, failureObject);
                }
            }

            if (!success) {
                rollbackExecutedOps(executedOps);
                return new GoalExecutionResult(
                        plan.goalSpec().goalId(),
                        ExecutionStatus.FAILED,
                        "Execution failed after retries and rollback completed",
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
        List<AtomicOp> reversed = new ArrayList<>(executedOps);
        Collections.reverse(reversed);
        for (AtomicOp op : reversed) {
            if (op.rollbackSupported()) {
                capabilityRouter.rollback(op);
            }
        }
    }
}
