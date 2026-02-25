package com.ainativeos.api;

import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.service.SemanticKernelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final SemanticKernelService semanticKernelService;

    public GoalController(SemanticKernelService semanticKernelService) {
        this.semanticKernelService = semanticKernelService;
    }

    @PostMapping("/plan")
    public GoalPlan plan(@Valid @RequestBody GoalSpec goalSpec) {
        return semanticKernelService.plan(goalSpec);
    }

    @PostMapping("/execute")
    public GoalExecutionResult execute(@Valid @RequestBody GoalSpec goalSpec) {
        GoalPlan plan = semanticKernelService.plan(goalSpec);
        return semanticKernelService.execute(plan);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "ainativeos-control-plane",
                "semanticKernel", "ready",
                "capabilityFabric", "ready",
                "selfHealingVfs", "ready"
        );
    }
}
