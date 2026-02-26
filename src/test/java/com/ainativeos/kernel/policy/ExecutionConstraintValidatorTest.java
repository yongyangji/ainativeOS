package com.ainativeos.kernel.policy;

import com.ainativeos.domain.GoalSpec;
import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionConstraintValidatorTest {

    @Mock
    private LocalCommandExecutor localCommandExecutor;

    private ExecutionConstraintValidator validator;

    @BeforeEach
    void setUp() {
        this.validator = new ExecutionConstraintValidator(localCommandExecutor);
    }

    @Test
    void validate_shouldFailOnUnsupportedComputeClass() {
        GoalSpec spec = new GoalSpec("g1", "intent", List.of("ok"), Map.of("computeClass", "fpga"), 1, "default");

        ExecutionConstraintValidator.ValidationResult result = validator.validate(spec);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("Unsupported computeClass"));
    }

    @Test
    void validate_shouldFailOnInvalidBooleanFlag() {
        GoalSpec spec = new GoalSpec("g1", "intent", List.of("ok"), Map.of("requiresDocker", "maybe"), 1, "default");

        ExecutionConstraintValidator.ValidationResult result = validator.validate(spec);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("requiresDocker"));
    }

    @Test
    void validate_shouldFailRemoteModeWithoutAuth() {
        GoalSpec spec = new GoalSpec(
                "g1",
                "intent",
                List.of("ok"),
                Map.of("remoteHost", "127.0.0.1", "remoteUser", "root", "remotePort", "22"),
                1,
                "default"
        );

        ExecutionConstraintValidator.ValidationResult result = validator.validate(spec);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("remote mode requires"));
    }

    @Test
    void validate_shouldFailWhenRequiredCommandsMissing() {
        mockInstalledCommands(Set.of("docker"));
        GoalSpec spec = new GoalSpec(
                "g1",
                "intent",
                List.of("ok"),
                Map.of("requiredCommands", "docker,kubectl"),
                1,
                "default"
        );

        ExecutionConstraintValidator.ValidationResult result = validator.validate(spec);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("missing command(s)"));
        assertTrue(String.valueOf(result.details().get("missingCommands")).contains("kubectl"));
    }

    @Test
    void validate_shouldPassRemoteModeWithPasswordAuth() {
        GoalSpec spec = new GoalSpec(
                "g1",
                "intent",
                List.of("ok"),
                Map.of(
                        "remoteHost", "127.0.0.1",
                        "remoteUser", "root",
                        "remotePassword", "123456",
                        "remotePort", "2222"
                ),
                1,
                "default"
        );

        ExecutionConstraintValidator.ValidationResult result = validator.validate(spec);

        assertTrue(result.valid());
    }

    private void mockInstalledCommands(Set<String> installed) {
        when(localCommandExecutor.execute(anyList(), anyInt())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> cmd = invocation.getArgument(0, List.class);
            String script = cmd.get(cmd.size() - 1);
            String target = parseTargetCommand(script);
            boolean ok = target != null && installed.contains(target);
            return new CommandExecutionResult(ok, ok ? 0 : 1, "", ok ? "" : "not found", 1, null);
        });
    }

    private String parseTargetCommand(String script) {
        if (script.contains("command -v ")) {
            return script.substring(script.indexOf("command -v ") + "command -v ".length()).trim();
        }
        if (script.contains("Get-Command ")) {
            String right = script.substring(script.indexOf("Get-Command ") + "Get-Command ".length()).trim();
            int blank = right.indexOf(' ');
            return blank > 0 ? right.substring(0, blank) : right;
        }
        return null;
    }
}

