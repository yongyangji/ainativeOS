package com.ainativeos.llm;

import com.ainativeos.domain.GoalSpec;

import java.util.Optional;

/**
 * 语义推理器抽象。
 */
public interface SemanticReasoner {
    Optional<LlmPlanHints> reason(GoalSpec goalSpec);
}

