package com.ainativeos.capability.provider;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import com.ainativeos.runtime.SshCommandExecutor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCapabilityProviderTest {

    @Test
    void shouldFailWhenSimulateFailureEnabled() {
        RuntimeCapabilityProvider provider = new RuntimeCapabilityProvider(new LocalCommandExecutor(), new SshCommandExecutor());
        AtomicOp op = new AtomicOp(
                "op-1",
                "RUNTIME_APPLY_DECLARATIVE_STATE",
                "runtime",
                Map.of("simulateFailure", true),
                true,
                true,
                5
        );

        OpExecutionResult result = provider.execute(op);
        assertFalse(result.success());
        assertTrue(result.message().contains("failed"));
    }
}
