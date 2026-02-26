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
        ), List.of(), null, false));
        // node-policy 与 node-capability 只依赖解析节点，可被并行调度。
        nodes.add(new PlanNode("node-policy", "COMPUTE_POLICY_EVAL", "Evaluate policy profile", Map.of(
                "profile", goalSpec.normalizedPolicyProfile()
        ), List.of("node-parse"), null, false));
        nodes.add(new PlanNode("node-capability", "COMPUTE_RESOLVE_CAPABILITY", "Resolve capability providers", Map.of(
                "constraints", goalSpec.constraints() == null ? Map.of() : goalSpec.constraints()
        ), List.of("node-parse"), null, false));

        if (goalSpec.constraints() != null && goalSpec.constraints().containsKey("packageName")) {
            nodes.add(new PlanNode("node-system-install", "SYSTEM_PACKAGE_INSTALL", "Install package with system adapter", Map.of(
                    "packageName", goalSpec.constraints().get("packageName"),
                    "packageInstallCommand", goalSpec.constraints().getOrDefault("packageInstallCommand", "")
            ), List.of("node-policy", "node-capability"), null, false));
        }

        if (goalSpec.constraints() != null && goalSpec.constraints().containsKey("k8sManifestPath")) {
            nodes.add(new PlanNode("node-k8s-apply", "K8S_APPLY_MANIFEST", "Apply kubernetes manifest", Map.of(
                    "manifestPath", goalSpec.constraints().get("k8sManifestPath")
            ), List.of("node-policy", "node-capability"), null, false));
            if (goalSpec.constraints().containsKey("k8sDeploymentName")) {
                nodes.add(new PlanNode("node-k8s-verify", "K8S_VERIFY_DEPLOYMENT", "Verify kubernetes deployment rollout", Map.of(
                        "deploymentName", goalSpec.constraints().get("k8sDeploymentName"),
                        "namespace", goalSpec.constraints().getOrDefault("k8sNamespace", "default"),
                        "verifyTimeoutSeconds", goalSpec.constraints().getOrDefault("k8sVerifyTimeoutSeconds", "120")
                ), List.of("node-k8s-apply"), "node-k8s-rollback", false));
                nodes.add(new PlanNode("node-k8s-rollback", "K8S_ROLLBACK_DEPLOYMENT", "Rollback kubernetes deployment", Map.of(
                        "deploymentName", goalSpec.constraints().get("k8sDeploymentName"),
                        "namespace", goalSpec.constraints().getOrDefault("k8sNamespace", "default")
                ), List.of(), null, true));
            }
        }

        if (goalSpec.constraints() != null && goalSpec.constraints().containsKey("cloudCommand")) {
            nodes.add(new PlanNode("node-cloud-exec", "CLOUD_EXECUTE", "Execute cloud CLI command", Map.of(
                    "cloudProvider", goalSpec.constraints().getOrDefault("cloudProvider", "generic"),
                    "cloudCommand", goalSpec.constraints().get("cloudCommand")
            ), List.of("node-policy", "node-capability"), null, false));
        }

        if (goalSpec.constraints() != null && goalSpec.constraints().containsKey("dockerImage")) {
            String containerName = goalSpec.constraints().getOrDefault("dockerContainerName", "ainativeos-workload");
            nodes.add(new PlanNode("node-docker-run", "DOCKER_RUN_IMAGE", "Run docker image", Map.of(
                    "image", goalSpec.constraints().get("dockerImage"),
                    "containerName", containerName,
                    "runArgs", goalSpec.constraints().getOrDefault("dockerRunArgs", "")
            ), List.of("node-policy", "node-capability"), "node-docker-rollback", false));
            nodes.add(new PlanNode("node-docker-verify", "DOCKER_VERIFY_CONTAINER", "Verify docker container running", Map.of(
                    "containerName", containerName
            ), List.of("node-docker-run"), "node-docker-rollback", false));
            nodes.add(new PlanNode("node-docker-rollback", "DOCKER_ROLLBACK_CONTAINER", "Rollback docker container", Map.of(
                    "containerName", containerName
            ), List.of(), null, true));
        }

        if (goalSpec.constraints() != null && goalSpec.constraints().containsKey("pluginId")) {
            Map<String, Object> pluginParams = new java.util.HashMap<>();
            pluginParams.put("pluginId", goalSpec.constraints().get("pluginId"));
            if (goalSpec.constraints().containsKey("pluginApprovalToken")) {
                pluginParams.put("pluginApprovalToken", goalSpec.constraints().get("pluginApprovalToken"));
            }
            if (goalSpec.constraints().containsKey("pluginInputJson")) {
                pluginParams.put("pluginInputJson", goalSpec.constraints().get("pluginInputJson"));
            }
            nodes.add(new PlanNode(
                    "node-plugin-exec",
                    "PLUGIN_EXECUTE",
                    "Execute plugin skill via plugin provider",
                    pluginParams,
                    List.of("node-policy", "node-capability"),
                    null,
                    false
            ));
        }

        return new PlanGraph(nodes);
    }
}
