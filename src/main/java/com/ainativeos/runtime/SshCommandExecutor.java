package com.ainativeos.runtime;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

@Component
public class SshCommandExecutor {

    public CommandExecutionResult execute(
            String host,
            int port,
            String username,
            String password,
            String command,
            int timeoutSeconds
    ) {
        Instant start = Instant.now();
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(timeoutSeconds * 1000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);
            channel.connect(timeoutSeconds * 1000);

            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect();
                    session.disconnect();
                    return new CommandExecutionResult(
                            false,
                            -1,
                            stdout.toString(StandardCharsets.UTF_8),
                            stderr.toString(StandardCharsets.UTF_8),
                            Duration.between(start, Instant.now()).toMillis(),
                            "SSH command timeout"
                    );
                }
                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            return new CommandExecutionResult(
                    exitCode == 0,
                    exitCode,
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8),
                    Duration.between(start, Instant.now()).toMillis(),
                    null
            );
        } catch (Exception e) {
            return new CommandExecutionResult(
                    false,
                    -1,
                    "",
                    "",
                    Duration.between(start, Instant.now()).toMillis(),
                    e.getMessage()
            );
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}
