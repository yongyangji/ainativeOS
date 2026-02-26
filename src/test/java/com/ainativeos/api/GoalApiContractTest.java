package com.ainativeos.api;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.DesiredState;
import com.ainativeos.domain.ExecutionStatus;
import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.health.HealthCheckService;
import com.ainativeos.persistence.repository.DesiredStateJobRepository;
import com.ainativeos.persistence.repository.GoalExecutionRepository;
import com.ainativeos.persistence.repository.GoalTraceRepository;
import com.ainativeos.plugin.PluginManifest;
import com.ainativeos.plugin.PluginRegistryService;
import com.ainativeos.runtime.RuntimeCommandDispatcher;
import com.ainativeos.capability.CapabilityRouter;
import com.ainativeos.service.SemanticKernelService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GoalController.class)
class GoalApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SemanticKernelService semanticKernelService;
    @MockBean
    private GoalExecutionRepository goalExecutionRepository;
    @MockBean
    private GoalTraceRepository goalTraceRepository;
    @MockBean
    private DesiredStateJobRepository desiredStateJobRepository;
    @MockBean
    private HealthCheckService healthCheckService;
    @MockBean
    private RuntimeCommandDispatcher runtimeCommandDispatcher;
    @MockBean
    private CapabilityRouter capabilityRouter;
    @MockBean
    private PluginRegistryService pluginRegistryService;

    @Test
    void planResponse_shouldContainContractFields() throws Exception {
        GoalSpec spec = new GoalSpec("goal-contract-001", "test plan", List.of("ok"), Map.of(), 2, "default");
        GoalPlan plan = new GoalPlan(
                spec,
                new DesiredState("state-1", "summary", Map.of("k", "v")),
                List.of(new AtomicOp("op-1", "COMPUTE_PARSE_INTENT", "desc", Map.of(), true, false, 20)),
                "planner-v3",
                true,
                "llm rationale",
                Map.of("format", "planner-v4-dag")
        );
        Mockito.when(semanticKernelService.plan(Mockito.any())).thenReturn(plan);

        mockMvc.perform(post("/api/goals/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goalId":"goal-contract-001",
                                  "naturalLanguageIntent":"test plan",
                                  "successCriteria":["ok"],
                                  "constraints":{},
                                  "maxRetries":2,
                                  "policyProfile":"default"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalSpec.goalId").value("goal-contract-001"))
                .andExpect(jsonPath("$.plannerVersion").value("planner-v3"))
                .andExpect(jsonPath("$.llmUsed").value(true))
                .andExpect(jsonPath("$.llmRationale").value("llm rationale"))
                .andExpect(jsonPath("$.planGraph.format").value("planner-v4-dag"));
    }

    @Test
    void executeResponse_shouldContainContractFields() throws Exception {
        GoalExecutionResult executionResult = new GoalExecutionResult(
                "goal-contract-002",
                ExecutionStatus.SUCCEEDED,
                "Goal converged to desired state",
                true,
                "llm rationale",
                null,
                List.of(),
                Instant.now()
        );
        GoalPlan plan = new GoalPlan(
                new GoalSpec("goal-contract-002", "test execute", List.of("ok"), Map.of(), 2, "default"),
                new DesiredState("state-1", "summary", Map.of()),
                List.of(),
                "planner-v3",
                true,
                "llm rationale",
                Map.of()
        );
        Mockito.when(semanticKernelService.plan(Mockito.any())).thenReturn(plan);
        Mockito.when(semanticKernelService.execute(Mockito.any())).thenReturn(executionResult);

        mockMvc.perform(post("/api/goals/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goalId":"goal-contract-002",
                                  "naturalLanguageIntent":"test execute",
                                  "successCriteria":["ok"],
                                  "constraints":{},
                                  "maxRetries":2,
                                  "policyProfile":"default"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value("goal-contract-002"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.llmUsed").value(true))
                .andExpect(jsonPath("$.llmRationale").value("llm rationale"))
                .andExpect(jsonPath("$.trace").isArray());
    }

    @Test
    void capabilities_shouldReturnDictionaryShape() throws Exception {
        Mockito.when(capabilityRouter.capabilityDictionary()).thenReturn(List.of(
                Map.of("provider", "runtime-provider", "supportedOps", List.of("RUNTIME_APPLY_DECLARATIVE_STATE"))
        ));

        mockMvc.perform(get("/api/goals/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("runtime-provider"))
                .andExpect(jsonPath("$[0].supportedOps[0]").value("RUNTIME_APPLY_DECLARATIVE_STATE"));
    }

    @Test
    void plugins_shouldReturnManifestShape() throws Exception {
        Mockito.when(pluginRegistryService.list()).thenReturn(List.of(
                new PluginManifest(
                        "echo-plugin",
                        "Echo Plugin",
                        "1.0.0",
                        "desc",
                        "echo ok",
                        true,
                        true,
                        List.of("COMPUTE_PARSE_INTENT"),
                        Map.of(),
                        Map.of(),
                        Map.of()
                )
        ));

        mockMvc.perform(get("/api/goals/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pluginId").value("echo-plugin"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].requiredCapabilities[0]").value("COMPUTE_PARSE_INTENT"));
    }
}
