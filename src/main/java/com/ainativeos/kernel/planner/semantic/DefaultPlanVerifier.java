package com.ainativeos.kernel.planner.semantic;

import com.ainativeos.domain.GoalSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认计划验证器。
 */
@Component
public class DefaultPlanVerifier implements PlanVerifier {

    @Override
    public List<String> verify(GoalSpec goalSpec, PlanGraph graph) {
        List<String> warnings = new ArrayList<>();
        if (goalSpec.successCriteria() == null || goalSpec.successCriteria().isEmpty()) {
            warnings.add("successCriteria is empty; verify stage may be weak");
        }

        Set<String> nodeIds = new HashSet<>();
        graph.nodes().forEach(node -> {
            if (!nodeIds.add(node.nodeId())) {
                warnings.add("duplicated plan node id: " + node.nodeId());
            }
        });
        graph.nodes().forEach(node -> {
            for (String dependency : node.dependsOnNodeIds()) {
                if (!nodeIds.contains(dependency)) {
                    warnings.add("node dependency missing: " + node.nodeId() + " -> " + dependency);
                }
            }
            if (node.onFailureNodeId() != null && !node.onFailureNodeId().isBlank() && !nodeIds.contains(node.onFailureNodeId())) {
                warnings.add("node failure-branch target missing: " + node.nodeId() + " -> " + node.onFailureNodeId());
            }
        });

        boolean hasRuntimeIntent = goalSpec.constraints() != null && goalSpec.constraints().containsKey("runtimeCommand");
        if (!hasRuntimeIntent) {
            warnings.add("runtimeCommand not provided; runtime apply may become no-op");
        }
        return warnings;
    }
}
