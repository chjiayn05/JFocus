package jfocus.ai.rules;

import java.util.List;

/**
 * Storage abstraction for distraction rules.
 */
public interface DistractionRuleRepository {
    void saveRule(RuleListType listType, KeywordRule rule);

    void deleteRule(RuleListType listType, KeywordRule rule);

    boolean matches(RuleListType listType, String text);

    List<KeywordRule> getRules(RuleListType listType);
}
