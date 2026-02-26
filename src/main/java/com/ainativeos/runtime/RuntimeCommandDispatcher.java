package com.ainativeos.runtime;

import com.ainativeos.runtime.spi.RuntimeAdapter;
import com.ainativeos.runtime.spi.RuntimeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时命令分发器。
 * 按参数决定本地执行或 SSH 执行。
 */
@Component
public class RuntimeCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RuntimeCommandDispatcher.class);
    private final List<RuntimeAdapter> runtimeAdapters;

    public RuntimeCommandDispatcher(List<RuntimeAdapter> runtimeAdapters) {
        this.runtimeAdapters = runtimeAdapters.stream()
                .sorted(Comparator.comparingInt(RuntimeAdapter::priority))
                .toList();
        log.info("Runtime adapters registered: {}", this.runtimeAdapters.stream().map(RuntimeAdapter::adapterId).toList());
    }

    public CommandExecutionResult execute(Map<String, Object> params, String command, int timeoutSeconds) {
        RuntimeExecutionContext context = new RuntimeExecutionContext(params, command, timeoutSeconds);
        RuntimeAdapter adapter = resolveAdapter(context);
        RuntimeExecutionContext prepared = adapter.prepare(context);
        CommandExecutionResult result = adapter.execute(prepared);
        if (!adapter.verify(result, prepared)) {
            return new CommandExecutionResult(
                    false,
                    result.exitCode(),
                    result.stdout(),
                    result.stderr(),
                    result.durationMs(),
                    "Runtime adapter verify failed: " + adapter.adapterId()
            );
        }
        return result;
    }

    public void rollback(Map<String, Object> params, String command, int timeoutSeconds) {
        RuntimeExecutionContext context = new RuntimeExecutionContext(params, command, timeoutSeconds);
        RuntimeAdapter adapter = resolveAdapter(context);
        adapter.rollback(context);
    }

    public List<Map<String, Object>> registeredAdapters() {
        return runtimeAdapters.stream().map(adapter -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("adapterId", adapter.adapterId());
            item.put("priority", adapter.priority());
            item.put("className", adapter.getClass().getName());
            return item;
        }).toList();
    }

    private RuntimeAdapter resolveAdapter(RuntimeExecutionContext context) {
        return runtimeAdapters.stream()
                .filter(adapter -> adapter.supports(context))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No runtime adapter available"));
    }
}
