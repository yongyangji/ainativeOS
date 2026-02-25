package com.ainativeos.runtime;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LocalCommandExecutor {

    public CommandExecutionResult execute(List<String> command, int timeoutSeconds) {
        Instant start = Instant.now();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandExecutionResult(
                        false,
                        -1,
                        "",
                        "",
                        Duration.between(start, Instant.now()).toMillis(),
                        "Command timeout"
                );
            }

            int exitCode = process.exitValue();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandExecutionResult(
                    exitCode == 0,
                    exitCode,
                    stdout,
                    stderr,
                    Duration.between(start, Instant.now()).toMillis(),
                    null
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandExecutionResult(
                    false,
                    -1,
                    "",
                    "",
                    Duration.between(start, Instant.now()).toMillis(),
                    e.getMessage()
            );
        } catch (IOException e) {
            return new CommandExecutionResult(
                    false,
                    -1,
                    "",
                    "",
                    Duration.between(start, Instant.now()).toMillis(),
                    e.getMessage()
            );
        }
    }
}
