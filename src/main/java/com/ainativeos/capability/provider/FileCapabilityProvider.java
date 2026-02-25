package com.ainativeos.capability.provider;

import com.ainativeos.capability.CapabilityProvider;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.OpExecutionResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class FileCapabilityProvider implements CapabilityProvider {

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("FILE_");
    }

    @Override
    public String providerName() {
        return "file-provider";
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        ContextFrame frame = new ContextFrame(
                "file",
                atomicOp.type(),
                providerName(),
                "self-healing-vfs",
                Map.of("op", atomicOp.opId()),
                Instant.now()
        );
        return new OpExecutionResult(true, providerName(), "File operation executed", List.of(frame), null, null);
    }
}
