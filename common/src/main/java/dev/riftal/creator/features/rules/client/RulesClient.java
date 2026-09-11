package dev.riftal.creator.features.rules.client;

import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.features.rules.RulesFeature;
import net.minecraft.resources.ResourceLocation;

/**
 * The rule engine's client-side wiring: one HUD layer.
 *
 * <p>The two server-to-client payloads are declared in {@code RulesFeature#registerContent()}
 * instead, because they have to exist on a dedicated server too - it is the side that sends them.
 * Their handler bodies are the only thing that ever touches {@link ClientRuleState}.
 *
 * <p><b>Client only.</b> Loaded from {@code RulesFeature#initClient()} and nowhere else.
 */
public final class RulesClient {

    /** Idempotent: NeoForge calls {@code initClient} from more than one registration event. */
    private static boolean initialised;

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        HudLayers.register(
                ResourceLocation.fromNamespaceAndPath(RulesFeature.NAMESPACE, "active_rules"),
                new ActiveRulesHud());
    }

    private RulesClient() {
    }
}
