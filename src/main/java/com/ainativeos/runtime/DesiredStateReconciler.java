package com.ainativeos.runtime;

import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 声明式状态收敛控制器（Reconcile Loop）。
 */
@Component
public class DesiredStateReconciler {

    public ReconcileResult reconcile(
            String applyCommand,
            String verifyCommand,
            int maxRounds,
            long intervalMs,
            Function<String, CommandExecutionResult> executor
    ) {
        CommandExecutionResult lastApply = null;
        CommandExecutionResult lastVerify = null;
        for (int round = 1; round <= Math.max(1, maxRounds); round++) {
            lastApply = executor.apply(applyCommand);
            if (!lastApply.success()) {
                return new ReconcileResult(false, round, "apply command failed", lastApply, null);
            }

            lastVerify = executor.apply(verifyCommand);
            if (lastVerify.success()) {
                return new ReconcileResult(true, round, "state converged", lastApply, lastVerify);
            }

            if (round < maxRounds && intervalMs > 0) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new ReconcileResult(false, round, "reconcile interrupted", lastApply, lastVerify);
                }
            }
        }
        return new ReconcileResult(false, Math.max(1, maxRounds), "verify command did not pass", lastApply, lastVerify);
    }
}

