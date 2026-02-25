package com.ainativeos.kernel.planner;

import com.ainativeos.config.ExecutionPolicyProperties;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.DesiredState;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public DefaultGoalPlanner(ExecutionPolicyProperties executionPolicy) {
        this.executionPolicy = executionPolicy;
    }

    @Override
    public GoalPlan plan(GoalSpec goalSpec) {
        // 目标期望状态：用于表达最终应收敛到的系统状态
        Map<String, Object> resources = new HashMap<>();
        resources.put("runtime", "immutable-container");
        resources.put("interface", "api-first-capability-fabric");
        resources.put("targetIntent", goalSpec.naturalLanguageIntent());
        DesiredState desiredState = new DesiredState("state-" + goalSpec.goalId(), "Converge declared runtime state", resources);

        int defaultTimeout = executionPolicy.getDefaultOpTimeoutSeconds();
        List<AtomicOp> ops = new ArrayList<>();
        // 1) 意图解析
        ops.add(new AtomicOp("op-parse", "COMPUTE_PARSE_INTENT", "Parse natural language goal into executable graph", Map.of(
                "intent", goalSpec.naturalLanguageIntent()
        ), true, false, 20));
        // 2) 策略评估
        ops.add(new AtomicOp("op-policy", "COMPUTE_POLICY_EVAL", "Validate goal against policy profile", Map.of(
                "profile", goalSpec.normalizedPolicyProfile()
        ), true, false, 20));
        // 3) 能力映射
        ops.add(new AtomicOp("op-capability", "COMPUTE_RESOLVE_CAPABILITY", "Resolve provider bindings across OS/cloud", Map.of(
                "constraints", goalSpec.constraints() == null ? Map.of() : goalSpec.constraints()
        ), true, false, 20));

        // 4) 运行时应用（本地/远程）参数集合
        Map<String, Object> runtimeParams = new HashMap<>();
        runtimeParams.put("state", desiredState);
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
        }

        ops.add(new AtomicOp("op-apply", "RUNTIME_APPLY_DECLARATIVE_STATE", "Apply desired state to runtime substrate", runtimeParams, true, true, defaultTimeout));

        // 5) 成功判据验证
        ops.add(new AtomicOp("op-verify", "COMPUTE_VERIFY_SUCCESS", "Evaluate success criteria", Map.of(
                "criteria", goalSpec.successCriteria()
        ), true, false, 20));

        return new GoalPlan(goalSpec, desiredState, ops, "planner-v2");
    }
}
