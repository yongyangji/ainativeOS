package com.ainativeos.e2e;

import com.ainativeos.domain.GoalSpec;
import com.ainativeos.llm.LlmPlanHints;
import com.ainativeos.llm.SemanticReasoner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GoalExecutionE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private SemanticReasoner semanticReasoner;

    @Test
    void execute_shouldSucceed_withMockLlmAndRealRuntimeProvider() {
        when(semanticReasoner.reason(any(GoalSpec.class))).thenReturn(Optional.of(
                new LlmPlanHints(List.of("runtime_command"), Map.of("hint", "on"), "mock-llm-rationale")
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {
                  "goalId":"goal-e2e-001",
                  "naturalLanguageIntent":"run local command through runtime provider",
                  "successCriteria":["command_ok"],
                  "constraints":{
                    "runtimeCommand":"echo e2e-ok"
                  },
                  "maxRetries":2,
                  "policyProfile":"default"
                }
                """;

        ResponseEntity<Map> response = restTemplate.exchange(
                "http://127.0.0.1:" + port + "/api/goals/execute",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> payload = response.getBody();
        assertNotNull(payload);
        assertEquals("goal-e2e-001", payload.get("goalId"));
        assertEquals("SUCCEEDED", payload.get("status"));
        assertEquals(true, payload.get("llmUsed"));
        assertEquals("mock-llm-rationale", payload.get("llmRationale"));

        ResponseEntity<List> traceResponse = restTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/api/goals/goal-e2e-001/trace",
                List.class
        );
        assertEquals(200, traceResponse.getStatusCode().value());
        assertNotNull(traceResponse.getBody());
        assertTrue(traceResponse.getBody().size() > 0);
    }
}
