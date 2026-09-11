package dev.riftal.creator.features.rules.api;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every rule the feature knows about, in registration order. Filled once from
 * {@code RulesFeature#registerContent()}.
 *
 * <p>Registration order is the order {@code /rule list} prints and the order hooks fan out in, so
 * it is deliberately stable rather than alphabetical.
 */
public final class RuleRegistry {

    private static final Map<String, Rule> RULES = new LinkedHashMap<>();

    /** Adds a rule. A duplicate id replaces the earlier entry and keeps its position. */
    public static void register(Rule rule) {
        RULES.put(rule.id(), rule);
    }

    /** The rule with this id, or {@code null}. */
    public static Rule byId(String id) {
        return id == null ? null : RULES.get(id);
    }

    public static boolean contains(String id) {
        return id != null && RULES.containsKey(id);
    }

    /** Every id, in registration order. Used for command suggestions. */
    public static List<String> ids() {
        return List.copyOf(RULES.keySet());
    }

    /** Every rule, in registration order. */
    public static Collection<Rule> all() {
        return Collections.unmodifiableCollection(RULES.values());
    }

    public static int size() {
        return RULES.size();
    }

    private RuleRegistry() {
    }
}
