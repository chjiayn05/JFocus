package jfocus.ai.rules;

import java.util.List;

/**
 * Storage abstraction for distraction rules.
 */
public interface DistractionRuleRepository {
    void saveRule(RuleListType listType, AppWindowRule rule);

    void deleteRule(RuleListType listType, AppWindowRule rule);

    boolean matches(RuleListType listType, String appName, String windowTitle);

    List<AppWindowRule> getRules(RuleListType listType);
}
