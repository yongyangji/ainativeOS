package com.ainativeos.cli;

import com.ainativeos.sdk.AinativeOsClient;
import com.ainativeos.sdk.AinativeOsClientConfig;
import com.ainativeos.sdk.model.GoalSpecRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class AinativeOsCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0];
        Map<String, String> options = parseOptions(args);
        String baseUrl = options.getOrDefault("base-url", "http://127.0.0.1:8080");
        int retries = parseIntOrDefault(options.get("retries"), 1);
        AinativeOsClient client = new AinativeOsClient(
                new AinativeOsClientConfig(baseUrl, 20, retries, 300)
        );

        Object payload = switch (command) {
            case "plan" -> client.plan(readGoalSpec(options));
            case "execute" -> client.execute(readGoalSpec(options));
            case "trace" -> client.trace(required(options, "goal-id"));
            case "jobs" -> client.reconcileJobs(options.get("goal-id"));
            case "executions" -> client.executions(options.get("goal-id"));
            case "health" -> client.health();
            case "runtime-adapters" -> client.runtimeAdapters();
            case "capabilities" -> client.capabilities();
            default -> {
                printUsage();
                yield Map.of("error", "unknown command");
            }
        };

        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
    }

    private static GoalSpecRequest readGoalSpec(Map<String, String> options) throws Exception {
        String file = required(options, "file");
        String json = Files.readString(Path.of(file));
        return MAPPER.readValue(json, GoalSpecRequest.class);
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + key);
        }
        return value;
    }

    private static int parseIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) {
                continue;
            }
            String key = token.substring(2);
            String value = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            }
            options.put(key, value);
        }
        return options;
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -cp <jar> com.ainativeos.cli.AinativeOsCli <command> [options]

                Commands:
                  plan --file <goal-spec.json> [--base-url http://127.0.0.1:8080]
                  execute --file <goal-spec.json> [--base-url http://127.0.0.1:8080]
                  trace --goal-id <goalId> [--base-url ...]
                  jobs [--goal-id <goalId>] [--base-url ...]
                  executions [--goal-id <goalId>] [--base-url ...]
                  health [--base-url ...]
                  runtime-adapters [--base-url ...]
                  capabilities [--base-url ...]
                """);
    }
}
