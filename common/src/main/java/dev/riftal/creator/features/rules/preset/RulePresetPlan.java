package dev.riftal.creator.features.rules.preset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What applying a preset would change, worked out without touching the engine.
 *
 * <p>{@code RuleManager.applyPreset} is the headline command of the whole feature
 * ({@code /rule preset chaos} flips six rules at once) and it used to be untestable, because every
 * path through it needed a live {@link net.minecraft.server.MinecraftServer}. The decision - which
 * rules go off, which come on, and in what order - is pure set arithmetic, so it lives here and is
 * unit-tested directly.
 *
 * @param disable ids to switch off, in the order they were switched on
 * @param enable  ids to switch on, in the order the preset names them
 */
public record RulePresetPlan(List<String> disable, List<String> enable) {

    public RulePresetPlan {
        disable = List.copyOf(disable);
        enable = List.copyOf(enable);
    }

    /**
     * Diffs {@code active} against {@code preset}.
     *
     * <p>A {@code replace} preset switches off every active rule it does not name; either way a
     * rule that is already active is left alone, which is what makes {@code onEnable} run at most
     * once per enable.
     */
    public static RulePresetPlan of(Collection<String> active, RulePreset preset) {
        List<String> disable = new ArrayList<>();
        if (preset.replace()) {
            for (String id : active) {
                if (!preset.rules().contains(id)) {
                    disable.add(id);
                }
            }
        }
        List<String> enable = new ArrayList<>();
        for (String id : preset.rules()) {
            if (!active.contains(id)) {
                enable.add(id);
            }
        }
        return new RulePresetPlan(disable, enable);
    }

    /** True when applying the preset would not change anything. */
    public boolean isEmpty() {
        return disable.isEmpty() && enable.isEmpty();
    }
}
