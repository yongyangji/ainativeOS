package com.ainativeos.runtime.spi;

import com.ainativeos.runtime.CommandExecutionResult;

public interface RuntimeAdapter {

    String adapterId();

    default int priority() {
        return 100;
    }

    boolean supports(RuntimeExecutionContext context);

    default RuntimeExecutionContext prepare(RuntimeExecutionContext context) {
        return context;
    }

    CommandExecutionResult execute(RuntimeExecutionContext context);

    default boolean verify(CommandExecutionResult result, RuntimeExecutionContext context) {
        return result.success();
    }

    default void rollback(RuntimeExecutionContext context) {
        // best-effort hook
    }
}
