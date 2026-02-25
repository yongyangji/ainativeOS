package com.ainativeos.runtime;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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
        return executeInternal(host, port, username, password, null, null, command, timeoutSeconds);
    }

    public CommandExecutionResult executeWithKey(
            String host,
            int port,
            String username,
            String privateKeyPem,
            String passphrase,
            String command,
            int timeoutSeconds
    ) {
        return executeInternal(host, port, username, null, privateKeyPem, passphrase, command, timeoutSeconds);
    }

    public CommandExecutionResult executeWithKeyBase64(
            String host,
            int port,
            String username,
            String privateKeyBase64,
            String passphrase,
            String command,
            int timeoutSeconds
    ) {
        try {
            byte[] decoded = Base64.getDecoder().decode(privateKeyBase64);
            String pem = new String(decoded, StandardCharsets.UTF_8);
            return executeWithKey(host, port, username, pem, passphrase, command, timeoutSeconds);
        } catch (IllegalArgumentException e) {
            return new CommandExecutionResult(false, -1, "", "", 0, "Invalid remotePrivateKeyBase64");
        }
    }

    private CommandExecutionResult executeInternal(
            String host,
            int port,
            String username,
            String password,
            String privateKeyPem,
            String passphrase,
            String command,
            int timeoutSeconds
    ) {
        Instant start = Instant.now();
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            configureIdentity(jsch, privateKeyPem, passphrase);
            session = jsch.getSession(username, host, port);
            if (password != null) {
                session.setPassword(password);
            }

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

    private void configureIdentity(JSch jsch, String privateKeyPem, String passphrase) throws JSchException {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            return;
        }
        byte[] keyBytes = privateKeyPem.getBytes(StandardCharsets.UTF_8);
        byte[] passphraseBytes = (passphrase == null || passphrase.isBlank())
                ? null
                : passphrase.getBytes(StandardCharsets.UTF_8);
        jsch.addIdentity("ainativeos-inline-key", keyBytes, null, passphraseBytes);
    }
}
