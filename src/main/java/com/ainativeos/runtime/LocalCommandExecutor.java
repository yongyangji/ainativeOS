package com.ainativeos.runtime;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
/**
 * 本地命令执行器。
 * <p>
 * 负责在当前进程所在主机执行 shell 命令，并统一封装输出、退出码、耗时与错误信息。
 */
public class LocalCommandExecutor {

    public CommandExecutionResult execute(List<String> command, int timeoutSeconds) {
        Instant start = Instant.now();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                // 超时强制终止，避免僵尸进程和无限等待
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
            // 捕获标准输出/错误输出，便于上层拼接可观测信息
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
