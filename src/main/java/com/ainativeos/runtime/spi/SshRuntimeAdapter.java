package com.ainativeos.runtime.spi;

import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.SshCommandExecutor;
import org.springframework.stereotype.Component;

@Component
public class SshRuntimeAdapter implements RuntimeAdapter {

    private final SshCommandExecutor sshCommandExecutor;

    public SshRuntimeAdapter(SshCommandExecutor sshCommandExecutor) {
        this.sshCommandExecutor = sshCommandExecutor;
    }

    @Override
    public String adapterId() {
        return "ssh";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(RuntimeExecutionContext context) {
        return context.value("remoteHost") != null && context.value("remoteUser") != null;
    }

    @Override
    public CommandExecutionResult execute(RuntimeExecutionContext context) {
        String remoteHost = context.value("remoteHost");
        String remoteUser = context.value("remoteUser");
        int port = context.valueAsInt("remotePort", 22);
        String command = context.command();
        int timeoutSeconds = context.timeoutSeconds();

        String privateKeyBase64 = context.value("remotePrivateKeyBase64");
        if (privateKeyBase64 != null) {
            return sshCommandExecutor.executeWithKeyBase64(
                    remoteHost,
                    port,
                    remoteUser,
                    privateKeyBase64,
                    context.value("remotePassphrase"),
                    command,
                    timeoutSeconds
            );
        }

        String privateKey = context.value("remotePrivateKey");
        if (privateKey != null) {
            return sshCommandExecutor.executeWithKey(
                    remoteHost,
                    port,
                    remoteUser,
                    privateKey,
                    context.value("remotePassphrase"),
                    command,
                    timeoutSeconds
            );
        }

        String password = context.value("remotePassword");
        if (password != null) {
            return sshCommandExecutor.execute(
                    remoteHost,
                    port,
                    remoteUser,
                    password,
                    command,
                    timeoutSeconds
            );
        }

        return new CommandExecutionResult(
                false,
                -1,
                "",
                "",
                0,
                "SSH auth not configured: provide remotePassword or remotePrivateKeyBase64 or remotePrivateKey"
        );
    }
}
