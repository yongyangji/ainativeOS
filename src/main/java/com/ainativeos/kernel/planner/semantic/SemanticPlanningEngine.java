package com.ainativeos.kernel.planner.semantic;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.GoalSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义规划引擎。
 * <p>
 * 流程：意图解析 -> 计划图构建 -> 图验证 -> 生成可执行原子操作。
 */
@Component
public class SemanticPlanningEngine {

    private final IntentParser intentParser;
    private final PlanGraphBuilder planGraphBuilder;
    private final PlanVerifier planVerifier;

    public SemanticPlanningEngine(
            IntentParser intentParser,
            PlanGraphBuilder planGraphBuilder,
            PlanVerifier planVerifier
    ) {
        this.intentParser = intentParser;
        this.planGraphBuilder = planGraphBuilder;
        this.planVerifier = planVerifier;
    }

    public PlanningBlueprint build(GoalSpec goalSpec, int defaultTimeoutSeconds) {
        ParsedIntent parsedIntent = intentParser.parse(goalSpec);
        PlanGraph graph = planGraphBuilder.build(goalSpec, parsedIntent);
        List<String> warnings = planVerifier.verify(goalSpec, graph);

        List<AtomicOp> ops = new ArrayList<>();
        int index = 0;
        for (PlanNode node : graph.nodes()) {
            String opId = "op-" + index++ + "-" + node.nodeId();
            boolean rollbackSupported = node.opType().startsWith("SYSTEM_")
                    || node.opType().startsWith("K8S_")
                    || node.opType().startsWith("CLOUD_");
            ops.add(new AtomicOp(
                    opId,
                    node.opType(),
                    node.description(),
                    node.params(),
                    true,
                    rollbackSupported,
                    defaultTimeoutSeconds
            ));
        }
        return new PlanningBlueprint(ops, warnings);
    }
}

