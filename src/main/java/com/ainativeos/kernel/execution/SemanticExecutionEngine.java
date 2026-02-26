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
import com.ainativeos.kernel.policy.ExecutionCircuitBreakerService;
import com.ainativeos.kernel.policy.PolicyDecision;
import com.ainativeos.kernel.policy.PolicyEngine;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class SemanticExecutionEngine {

    private final CapabilityRouter capabilityRouter;
    private final PolicyEngine policyEngine;
    private final FailureAnalyzer failureAnalyzer;
    private final RepairPlanner repairPlanner;
    private final ExecutionPolicyProperties executionPolicy;
    private final OperationAuditService operationAuditService;
    private final ExecutionCircuitBreakerService circuitBreakerService;

    public SemanticExecutionEngine(
            CapabilityRouter capabilityRouter,
            PolicyEngine policyEngine,
            FailureAnalyzer failureAnalyzer,
            RepairPlanner repairPlanner,
            ExecutionPolicyProperties executionPolicy,
            OperationAuditService operationAuditService,
            ExecutionCircuitBreakerService circuitBreakerService
    ) {
        this.capabilityRouter = capabilityRouter;
        this.policyEngine = policyEngine;
        this.failureAnalyzer = failureAnalyzer;
        this.repairPlanner = repairPlanner;
        this.executionPolicy = executionPolicy;
        this.operationAuditService = operationAuditService;
        this.circuitBreakerService = circuitBreakerService;
    }

    public GoalExecutionResult run(GoalPlan plan) {
        ExecutionPolicyProperties.ResolvedExecutionPolicy resolvedPolicy = executionPolicy.resolveProfile(
                plan.goalSpec().normalizedPolicyProfile()
        );
        PolicyDecision decision = policyEngine.evaluate(plan);
        List<ExecutionTraceEntry> trace = new ArrayList<>();
        List<AtomicOp> executedOps = new ArrayList<>();

        if (!decision.allowed()) {
            FailureObject blockedFailure = new FailureObject(
                    "failure-policy-blocked",
                    plan.goalSpec().goalId(),
                    "policy-gate",
                    List.of(),
                    List.of(),
                    List.of("Adjust policy profile or constraints"),
                    "policy-blocked",
                    decision.details()
            );
            trace.add(new ExecutionTraceEntry(
                    plan.goalSpec().goalId(),
                    "policy-gate",
                    "POLICY",
                    "policy-engine",
                    ExecutionStatus.BLOCKED,
                    decision.reason() + " (" + decision.policyId() + ")",
                    Instant.now(),
                    0,
                    List.of()
            ));
            return new GoalExecutionResult(
                    plan.goalSpec().goalId(),
                    ExecutionStatus.BLOCKED,
                    decision.reason(),
                    plan.llmUsed(),
                    blockedFailure,
                    trace,
                    Instant.now()
            );
        }

        int maxRetries = plan.goalSpec().maxRetries() > 0
                ? plan.goalSpec().maxRetries()
                : resolvedPolicy.maxRetries();

        Map<String, AtomicOp> opIndex = new HashMap<>();
        Set<String> pending = new HashSet<>();
        Set<String> completed = new HashSet<>();
        for (AtomicOp op : plan.atomicOps()) {
            opIndex.put(op.opId(), op);
            if (!isBranchOnly(op)) {
                pending.add(op.opId());
            }
        }

        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            while (!pending.isEmpty()) {
                List<AtomicOp> ready = pending.stream()
                        .map(opIndex::get)
                        .filter(op -> completed.containsAll(dependenciesOf(op)))
                        .sorted(Comparator.comparing(AtomicOp::opId))
                        .toList();

                if (ready.isEmpty()) {
                    FailureObject dependencyFailure = new FailureObject(
                            "failure-dependency-deadlock",
                            plan.goalSpec().goalId(),
                            "scheduler",
                            List.of(),
                            List.of(),
                            List.of("Check dependsOnOpIds and cycle in plan graph"),
                            "dependency-deadlock",
                            Map.of("pendingOps", pending)
                    );
                    if (resolvedPolicy.rollbackOnFailure()) {
                        rollbackExecutedOps(executedOps);
                    }
                    circuitBreakerService.recordFailure(
                            resolvedPolicy.profile(),
                            resolvedPolicy.circuitBreakerFailureThreshold(),
                            resolvedPolicy.circuitBreakerOpenSeconds()
                    );
                    return new GoalExecutionResult(
                            plan.goalSpec().goalId(),
                            ExecutionStatus.FAILED,
                            "Execution failed due to dependency deadlock",
                            plan.llmUsed(),
                            dependencyFailure,
                            sortTrace(trace),
                            Instant.now()
                    );
                }

                List<Future<OpRunOutcome>> futures = new ArrayList<>();
                for (AtomicOp op : ready) {
                    futures.add(executor.submit(new OpCallable(plan.goalSpec().goalId(), op, maxRetries)));
                }

                OpRunOutcome failed = null;
                for (Future<OpRunOutcome> future : futures) {
                    OpRunOutcome outcome;
                    try {
                        outcome = future.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    }

                    trace.addAll(outcome.traceEntries());
                    pending.remove(outcome.op().opId());
                    if (outcome.success()) {
                        completed.add(outcome.op().opId());
                        executedOps.add(outcome.op());
                    } else if (failed == null) {
                        failed = outcome;
                    }
                }

                if (failed != null) {
                    OpRunOutcome fallbackOutcome = runFallbackIfConfigured(plan.goalSpec().goalId(), failed.op(), opIndex, maxRetries);
                    if (fallbackOutcome != null) {
                        trace.addAll(fallbackOutcome.traceEntries());
                        if (fallbackOutcome.success()) {
                            completed.add(failed.op().opId());
                            continue;
                        }
                    }

                    if (resolvedPolicy.rollbackOnFailure()) {
                        rollbackExecutedOps(executedOps);
                    }
                    circuitBreakerService.recordFailure(
                            resolvedPolicy.profile(),
                            resolvedPolicy.circuitBreakerFailureThreshold(),
                            resolvedPolicy.circuitBreakerOpenSeconds()
                    );
                    return new GoalExecutionResult(
                            plan.goalSpec().goalId(),
                            ExecutionStatus.FAILED,
                            "Execution failed after retries" + (resolvedPolicy.rollbackOnFailure() ? " and rollback completed" : ""),
                            plan.llmUsed(),
                            failed.failureObject(),
                            sortTrace(trace),
                            Instant.now()
                    );
                }
            }
        } finally {
            executor.shutdownNow();
        }

        circuitBreakerService.recordSuccess(resolvedPolicy.profile());

        return new GoalExecutionResult(
                plan.goalSpec().goalId(),
                ExecutionStatus.SUCCEEDED,
                "Goal converged to desired state",
                plan.llmUsed(),
                null,
                sortTrace(trace),
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

    private List<ExecutionTraceEntry> sortTrace(List<ExecutionTraceEntry> trace) {
        return trace.stream()
                .sorted(Comparator.comparing(ExecutionTraceEntry::timestamp))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> dependenciesOf(AtomicOp op) {
        Object raw = op.parameters().get("dependsOnOpIds");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private boolean isBranchOnly(AtomicOp op) {
        Object raw = op.parameters().get("branchOnly");
        return raw != null && Boolean.parseBoolean(String.valueOf(raw));
    }

    private OpRunOutcome runFallbackIfConfigured(String goalId, AtomicOp failedOp, Map<String, AtomicOp> opIndex, int maxRetries) {
        Object fallbackOpIdRaw = failedOp.parameters().get("onFailureOpId");
        if (fallbackOpIdRaw == null) {
            return null;
        }
        AtomicOp fallbackOp = opIndex.get(String.valueOf(fallbackOpIdRaw));
        if (fallbackOp == null) {
            return null;
        }
        return executeOp(goalId, fallbackOp, maxRetries);
    }

    private OpRunOutcome executeOp(String goalId, AtomicOp op, int maxRetries) {
        if (operationAuditService.shouldShortCircuit(goalId, op)) {
            return new OpRunOutcome(
                    op,
                    true,
                    null,
                    List.of(new ExecutionTraceEntry(
                            goalId,
                            op.opId(),
                            op.type(),
                            "audit-short-circuit",
                            ExecutionStatus.SUCCEEDED,
                            "Short-circuited by idempotency audit",
                            Instant.now(),
                            0,
                            List.of()
                    ))
            );
        }

        List<ExecutionTraceEntry> entries = new ArrayList<>();
        AtomicOp currentOp = op;
        FailureObject failureObject = null;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            OpExecutionResult opResult = capabilityRouter.execute(currentOp);
            entries.add(new ExecutionTraceEntry(
                    goalId,
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
                    goalId,
                    currentOp,
                    opResult.success() ? "SUCCEEDED" : "FAILED",
                    opResult.provider(),
                    attempt,
                    opResult.message()
            );
            if (opResult.success()) {
                return new OpRunOutcome(currentOp, true, null, entries);
            }
            failureObject = failureAnalyzer.buildFailure(goalId, currentOp, opResult, attempt);
            if (attempt <= maxRetries) {
                currentOp = repairPlanner.patchForRetry(currentOp, failureObject);
            }
        }
        return new OpRunOutcome(currentOp, false, failureObject, entries);
    }

    private record OpRunOutcome(
            AtomicOp op,
            boolean success,
            FailureObject failureObject,
            List<ExecutionTraceEntry> traceEntries
    ) {
    }

    private class OpCallable implements Callable<OpRunOutcome> {

        private final String goalId;
        private final AtomicOp op;
        private final int maxRetries;

        private OpCallable(String goalId, AtomicOp op, int maxRetries) {
            this.goalId = goalId;
            this.op = op;
            this.maxRetries = maxRetries;
        }

        @Override
        public OpRunOutcome call() {
            return executeOp(goalId, op, maxRetries);
        }
    }
}
