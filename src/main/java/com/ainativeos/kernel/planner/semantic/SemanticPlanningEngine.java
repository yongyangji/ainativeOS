package com.ainativeos.kernel.planner.semantic;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.llm.LlmPlanHints;
import com.ainativeos.llm.SemanticReasoner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final SemanticReasoner semanticReasoner;

    public SemanticPlanningEngine(
            IntentParser intentParser,
            PlanGraphBuilder planGraphBuilder,
            PlanVerifier planVerifier,
            SemanticReasoner semanticReasoner
    ) {
        this.intentParser = intentParser;
        this.planGraphBuilder = planGraphBuilder;
        this.planVerifier = planVerifier;
        this.semanticReasoner = semanticReasoner;
    }

    public PlanningBlueprint build(GoalSpec goalSpec, int defaultTimeoutSeconds) {
        ParsedIntent parsedIntent = mergeLlmHints(goalSpec, intentParser.parse(goalSpec));
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

    private ParsedIntent mergeLlmHints(GoalSpec goalSpec, ParsedIntent parsedIntent) {
        Optional<LlmPlanHints> llmHintsOpt = semanticReasoner.reason(goalSpec);
        if (llmHintsOpt.isEmpty()) {
            return parsedIntent;
        }

        LlmPlanHints llmHints = llmHintsOpt.get();
        Map<String, String> mergedConstraints = new HashMap<>(parsedIntent.inferredConstraints());
        mergedConstraints.putAll(llmHints.suggestedConstraints());

        List<String> mergedActions = new ArrayList<>(parsedIntent.inferredActions());
        for (String action : llmHints.suggestedActions()) {
            if (!mergedActions.contains(action)) {
                mergedActions.add(action);
            }
        }
        return new ParsedIntent(
                parsedIntent.goalId(),
                parsedIntent.normalizedIntent(),
                mergedActions,
                mergedConstraints
        );
    }
}
