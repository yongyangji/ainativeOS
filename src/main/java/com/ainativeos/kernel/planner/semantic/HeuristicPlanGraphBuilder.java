package com.ainativeos.kernel.planner.semantic;

import com.ainativeos.domain.GoalSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启发式计划图构建器。
 */
@Component
public class HeuristicPlanGraphBuilder implements PlanGraphBuilder {

    @Override
    public PlanGraph build(GoalSpec goalSpec, ParsedIntent parsedIntent) {
        List<PlanNode> nodes = new ArrayList<>();
        nodes.add(new PlanNode("node-parse", "COMPUTE_PARSE_INTENT", "Parse natural language goal", Map.of(
                "intent", goalSpec.naturalLanguageIntent()
        )));
        nodes.add(new PlanNode("node-policy", "COMPUTE_POLICY_EVAL", "Evaluate policy profile", Map.of(
                "profile", goalSpec.normalizedPolicyProfile()
        )));
        nodes.add(new PlanNode("node-capability", "COMPUTE_RESOLVE_CAPABILITY", "Resolve capability providers", Map.of(
                "constraints", goalSpec.constraints() == null ? Map.of() : goalSpec.constraints()
        )));

        if (goalSpec.constraints() != null && goalSpec.constraints().containsKey("packageName")) {
            nodes.add(new PlanNode("node-system-install", "SYSTEM_PACKAGE_INSTALL", "Install package with system adapter", Map.of(
                    "packageName", goalSpec.constraints().get("packageName"),
                    "packageInstallCommand", goalSpec.constraints().getOrDefault("packageInstallCommand", "")
            )));
        }

        if (goalSpec.constraints() != null && goalSpec.constraints().containsKey("k8sManifestPath")) {
            nodes.add(new PlanNode("node-k8s-apply", "K8S_APPLY_MANIFEST", "Apply kubernetes manifest", Map.of(
                    "manifestPath", goalSpec.constraints().get("k8sManifestPath")
            )));
        }

        if (goalSpec.constraints() != null && goalSpec.constraints().containsKey("cloudCommand")) {
            nodes.add(new PlanNode("node-cloud-exec", "CLOUD_EXECUTE", "Execute cloud CLI command", Map.of(
                    "cloudProvider", goalSpec.constraints().getOrDefault("cloudProvider", "generic"),
                    "cloudCommand", goalSpec.constraints().get("cloudCommand")
            )));
        }

        return new PlanGraph(nodes);
    }
}

