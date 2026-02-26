package com.ainativeos.runtime.spi;

import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HighMemRuntimeAdapter implements RuntimeAdapter {

    private final LocalCommandExecutor localCommandExecutor;

    public HighMemRuntimeAdapter(LocalCommandExecutor localCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
    }

    @Override
    public String adapterId() {
        return "highmem-local-shell";
    }

    @Override
    public int priority() {
        return 80;
    }

    @Override
    public boolean supports(RuntimeExecutionContext context) {
        return "highmem".equalsIgnoreCase(context.value("computeClass"))
                && context.value("remoteHost") == null;
    }

    @Override
    public CommandExecutionResult execute(RuntimeExecutionContext context) {
        return localCommandExecutor.execute(buildLocalShell(context.command()), context.timeoutSeconds());
    }

    private List<String> buildLocalShell(String cmd) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of("powershell", "-Command", cmd);
        }
        return List.of("sh", "-lc", cmd);
    }
}
