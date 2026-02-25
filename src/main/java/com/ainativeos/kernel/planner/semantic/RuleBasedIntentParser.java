package com.ainativeos.kernel.planner.semantic;

import com.ainativeos.domain.GoalSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于规则的意图解析器（MVP）。
 */
@Component
public class RuleBasedIntentParser implements IntentParser {

    @Override
    public ParsedIntent parse(GoalSpec goalSpec) {
        String normalized = goalSpec.naturalLanguageIntent().toLowerCase(Locale.ROOT).trim();
        List<String> actions = new ArrayList<>();
        Map<String, String> inferred = new HashMap<>();

        if (normalized.contains("install")) {
            actions.add("INSTALL_PACKAGE");
        }
        if (normalized.contains("k8s") || normalized.contains("kubernetes")) {
            actions.add("K8S_APPLY");
        }
        if (normalized.contains("cloud")) {
            actions.add("CLOUD_EXECUTE");
        }
        if (normalized.contains("verify") || normalized.contains("check")) {
            actions.add("VERIFY");
        }

        if (goalSpec.constraints() != null) {
            inferred.putAll(goalSpec.constraints());
        }
        return new ParsedIntent(goalSpec.goalId(), normalized, actions, inferred);
    }
}

