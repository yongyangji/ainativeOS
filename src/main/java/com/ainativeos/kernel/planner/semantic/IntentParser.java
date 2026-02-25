package com.ainativeos.kernel.planner.semantic;

import com.ainativeos.domain.GoalSpec;

/**
 * 意图解析器抽象。
 */
public interface IntentParser {
    ParsedIntent parse(GoalSpec goalSpec);
}

