package com.ainativeos.runtime.spi;

import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GpuRuntimeAdapter implements RuntimeAdapter {

    private final LocalCommandExecutor localCommandExecutor;

    public GpuRuntimeAdapter(LocalCommandExecutor localCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
    }

    @Override
    public String adapterId() {
        return "gpu-local-shell";
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public boolean supports(RuntimeExecutionContext context) {
        return "gpu".equalsIgnoreCase(context.value("computeClass"))
                && context.value("remoteHost") == null;
    }

    @Override
    public RuntimeExecutionContext prepare(RuntimeExecutionContext context) {
        CommandExecutionResult check = localCommandExecutor.execute(buildCheckCommand(), 8);
        if (!check.success()) {
            throw new IllegalStateException("GPU adapter requires nvidia-smi");
        }
        return context;
    }

    @Override
    public CommandExecutionResult execute(RuntimeExecutionContext context) {
        return localCommandExecutor.execute(buildLocalShell(context.command()), context.timeoutSeconds());
    }

    private List<String> buildCheckCommand() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of("powershell", "-Command", "Get-Command nvidia-smi -ErrorAction SilentlyContinue");
        }
        return List.of("sh", "-lc", "command -v nvidia-smi");
    }

    private List<String> buildLocalShell(String cmd) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of("powershell", "-Command", cmd);
        }
        return List.of("sh", "-lc", cmd);
    }
}
