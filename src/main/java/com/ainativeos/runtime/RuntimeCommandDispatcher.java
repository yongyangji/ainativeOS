package com.ainativeos.runtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 运行时命令分发器。
 * 按参数决定本地执行或 SSH 执行。
 */
@Component
public class RuntimeCommandDispatcher {

    private final LocalCommandExecutor localCommandExecutor;
    private final SshCommandExecutor sshCommandExecutor;

    public RuntimeCommandDispatcher(LocalCommandExecutor localCommandExecutor, SshCommandExecutor sshCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
        this.sshCommandExecutor = sshCommandExecutor;
    }

    public CommandExecutionResult execute(Map<String, Object> params, String command, int timeoutSeconds) {
        String remoteHost = value(params, "remoteHost");
        String remoteUser = value(params, "remoteUser");
        String privateKeyBase64 = value(params, "remotePrivateKeyBase64");
        String privateKey = value(params, "remotePrivateKey");
        String passphrase = value(params, "remotePassphrase");
        int port = parseIntOrDefault(value(params, "remotePort"), 22);

        if (remoteHost != null && remoteUser != null && privateKeyBase64 != null) {
            return sshCommandExecutor.executeWithKeyBase64(remoteHost, port, remoteUser, privateKeyBase64, passphrase, command, timeoutSeconds);
        }
        if (remoteHost != null && remoteUser != null && privateKey != null) {
            return sshCommandExecutor.executeWithKey(remoteHost, port, remoteUser, privateKey, passphrase, command, timeoutSeconds);
        }
        String remotePassword = value(params, "remotePassword");
        if (remoteHost != null && remoteUser != null && remotePassword != null) {
            return sshCommandExecutor.execute(remoteHost, port, remoteUser, remotePassword, command, timeoutSeconds);
        }
        return localCommandExecutor.execute(buildLocalShell(command), timeoutSeconds);
    }

    private List<String> buildLocalShell(String cmd) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of("powershell", "-Command", cmd);
        }
        return List.of("sh", "-lc", cmd);
    }

    private String value(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private int parseIntOrDefault(String val, int defaultValue) {
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}

