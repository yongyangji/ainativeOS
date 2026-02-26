package com.ainativeos.kernel.planner;

import com.ainativeos.config.ExecutionPolicyProperties;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.DesiredState;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.kernel.planner.semantic.PlanningBlueprint;
import com.ainativeos.kernel.planner.semantic.SemanticPlanningEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
/**
 * 默认目标规划器。
 * <p>
 * 将 GoalSpec 解析为统一的 GoalPlan：
 * - 生成声明式 DesiredState
 * - 组装标准原子操作链（解析、策略、能力映射、运行时应用、验收）
 * - 注入运行时参数（本地命令、远程 SSH 等）
 */
public class DefaultGoalPlanner implements GoalPlanner {

    private final ExecutionPolicyProperties executionPolicy;
    private final SemanticPlanningEngine semanticPlanningEngine;

    public DefaultGoalPlanner(ExecutionPolicyProperties executionPolicy, SemanticPlanningEngine semanticPlanningEngine) {
        this.executionPolicy = executionPolicy;
        this.semanticPlanningEngine = semanticPlanningEngine;
    }

    @Override
    public GoalPlan plan(GoalSpec goalSpec) {
        // 目标期望状态：用于表达最终应收敛到的系统状态
        Map<String, Object> resources = new HashMap<>();
        resources.put("runtime", "immutable-container");
        resources.put("interface", "api-first-capability-fabric");
        resources.put("targetIntent", goalSpec.naturalLanguageIntent());
        DesiredState desiredState = new DesiredState("state-" + goalSpec.goalId(), "Converge declared runtime state", resources);

        ExecutionPolicyProperties.ResolvedExecutionPolicy resolvedPolicy = executionPolicy.resolveProfile(goalSpec.normalizedPolicyProfile());
        int defaultTimeout = resolvedPolicy.opTimeoutSeconds();
        // 通过语义规划引擎生成前置操作（parse/policy/capability + adapter specific ops）
        PlanningBlueprint blueprint = semanticPlanningEngine.build(goalSpec, defaultTimeout);
        List<AtomicOp> ops = new ArrayList<>(blueprint.preRuntimeOps());

        // 4) 运行时应用（本地/远程）参数集合
        Map<String, Object> runtimeParams = new HashMap<>();
        runtimeParams.put("state", desiredState);
        runtimeParams.put("planningWarnings", blueprint.warnings());
        runtimeParams.put("llmUsed", blueprint.llmUsed());
        runtimeParams.put("llmRationale", blueprint.llmRationale());
        runtimeParams.put("policyProfile", resolvedPolicy.profile());
        runtimeParams.put("policyMaxRetries", resolvedPolicy.maxRetries());
        runtimeParams.put("policyRollbackOnFailure", resolvedPolicy.rollbackOnFailure());
        runtimeParams.put("policyOpTimeoutSeconds", resolvedPolicy.opTimeoutSeconds());
        if (goalSpec.constraints() != null) {
            if ("true".equalsIgnoreCase(goalSpec.constraints().getOrDefault("simulateFailure", "false"))) {
                runtimeParams.put("simulateFailure", true);
            }
            if (goalSpec.constraints().containsKey("runtimeCommand")) {
                runtimeParams.put("command", goalSpec.constraints().get("runtimeCommand"));
            }
            if (goalSpec.constraints().containsKey("remoteHost")) {
                runtimeParams.put("remoteHost", goalSpec.constraints().get("remoteHost"));
            }
            if (goalSpec.constraints().containsKey("remotePort")) {
                runtimeParams.put("remotePort", goalSpec.constraints().get("remotePort"));
            }
            if (goalSpec.constraints().containsKey("remoteUser")) {
                runtimeParams.put("remoteUser", goalSpec.constraints().get("remoteUser"));
            }
            if (goalSpec.constraints().containsKey("remotePassword")) {
                runtimeParams.put("remotePassword", goalSpec.constraints().get("remotePassword"));
            }
            if (goalSpec.constraints().containsKey("remotePrivateKey")) {
                runtimeParams.put("remotePrivateKey", goalSpec.constraints().get("remotePrivateKey"));
            }
            if (goalSpec.constraints().containsKey("remotePrivateKeyBase64")) {
                runtimeParams.put("remotePrivateKeyBase64", goalSpec.constraints().get("remotePrivateKeyBase64"));
            }
            if (goalSpec.constraints().containsKey("remotePassphrase")) {
                runtimeParams.put("remotePassphrase", goalSpec.constraints().get("remotePassphrase"));
            }
            if (goalSpec.constraints().containsKey("reconcileApplyCommand")) {
                runtimeParams.put("reconcileApplyCommand", goalSpec.constraints().get("reconcileApplyCommand"));
            }
            if (goalSpec.constraints().containsKey("reconcileVerifyCommand")) {
                runtimeParams.put("reconcileVerifyCommand", goalSpec.constraints().get("reconcileVerifyCommand"));
            }
            if (goalSpec.constraints().containsKey("reconcileMaxRounds")) {
                runtimeParams.put("reconcileMaxRounds", goalSpec.constraints().get("reconcileMaxRounds"));
            }
            if (goalSpec.constraints().containsKey("reconcileIntervalMs")) {
                runtimeParams.put("reconcileIntervalMs", goalSpec.constraints().get("reconcileIntervalMs"));
            }
        }

        List<String> runtimeDependsOn = ops.stream()
                .filter(op -> !isBranchOnly(op))
                .map(AtomicOp::opId)
                .toList();
        runtimeParams.put("dependsOnOpIds", runtimeDependsOn);
        runtimeParams.put("branchOnly", false);

        if (goalSpec.constraints() != null && goalSpec.constraints().containsKey("fallbackCommand")) {
            Map<String, Object> fallbackParams = new HashMap<>(runtimeParams);
            fallbackParams.put("command", goalSpec.constraints().get("fallbackCommand"));
            fallbackParams.put("branchOnly", true);
            fallbackParams.put("dependsOnOpIds", List.of());
            ops.add(new AtomicOp(
                    "op-fallback-apply",
                    "RUNTIME_APPLY_DECLARATIVE_STATE",
                    "Fallback apply branch when primary runtime apply fails",
                    fallbackParams,
                    true,
                    true,
                    defaultTimeout
            ));
            runtimeParams.put("onFailureOpId", "op-fallback-apply");
        }

        ops.add(new AtomicOp("op-apply", "RUNTIME_APPLY_DECLARATIVE_STATE", "Apply desired state to runtime substrate", runtimeParams, true, true, defaultTimeout));

        // 5) 成功判据验证
        Map<String, Object> verifyParams = new HashMap<>();
        verifyParams.put("criteria", goalSpec.successCriteria());
        verifyParams.put("dependsOnOpIds", List.of("op-apply"));
        verifyParams.put("branchOnly", false);
        ops.add(new AtomicOp("op-verify", "COMPUTE_VERIFY_SUCCESS", "Evaluate success criteria", verifyParams, true, false, 20));

        Map<String, Object> planGraphSnapshot = buildPlanGraphSnapshot(ops);
        return new GoalPlan(goalSpec, desiredState, ops, "planner-v3", blueprint.llmUsed(), planGraphSnapshot);
    }

    private Map<String, Object> buildPlanGraphSnapshot(List<AtomicOp> ops) {
        List<Map<String, Object>> nodes = ops.stream().map(op -> {
            List<String> dependsOn = toStringList(op.parameters().get("dependsOnOpIds"));
            String onFailure = op.parameters().get("onFailureOpId") == null
                    ? ""
                    : String.valueOf(op.parameters().get("onFailureOpId"));
            boolean branchOnly = isBranchOnly(op);
            Map<String, Object> node = new HashMap<>();
            node.put("opId", op.opId());
            node.put("opType", op.type());
            node.put("dependsOnOpIds", dependsOn);
            node.put("onFailureOpId", onFailure);
            node.put("branchOnly", branchOnly);
            return node;
        }).collect(Collectors.toList());

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("nodes", nodes);
        snapshot.put("format", "planner-v4-dag");
        return snapshot;
    }

    private boolean isBranchOnly(AtomicOp op) {
        Object raw = op.parameters().get("branchOnly");
        if (raw == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
