package dev.riftal.creator.features.colossus.client;

import dev.riftal.creator.core.hud.HudLayers;
import dev.riftal.creator.core.hud.HudText;
import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.colossus.ArenaRing;
import dev.riftal.creator.features.colossus.BossPhase;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The one HUD element this feature adds: a phase 3 ring gauge under the crosshair.
 *
 * <p>The vanilla boss bar already carries health and phase colour, so the only thing the player
 * cannot see is how much floor is left. The gauge shows the closing ring as a fraction of the
 * arena and turns red with a warning line the moment the player steps outside it.
 *
 * <p>Honours F1 and only searches for a boss every {@value #SCAN_INTERVAL_TICKS} ticks - a HUD
 * layer runs once per frame and must never scan entities per frame.
 *
 * <p><b>Client only.</b>
 */
public final class ColossusHud {

    /** Ticks between entity scans. */
    public static final int SCAN_INTERVAL_TICKS = 10;

    /** How far the HUD looks for a Colossus. */
    public static final double SCAN_RADIUS = 72.0D;

    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 5;
    private static final int BACKDROP_ARGB = 0x90000000;
    private static final int SAFE_ARGB = 0xFFFFAA33;
    private static final int DANGER_ARGB = 0xFFFF4422;
    private static final int LABEL_ARGB = 0xFFFFCC66;

    private static long nextScanTick = Long.MIN_VALUE;
    @Nullable
    private static AshenColossusEntity cachedBoss;

    /** Call from {@code ColossusFeature#initClient()}. */
    public static void register(ResourceLocation id) {
        HudLayers.register(id, ColossusHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (HudLayers.hudHidden()) {
            // Drop the cache before returning, not after. F1 is the plan's own recording setup
            // ("hidden HUD except boss bar/hotbar"), so this is the branch that runs for the whole
            // shoot - and a static field still pointing at a dead boss pins that boss, and through
            // it the entire ClientLevel, for the rest of the session.
            cachedBoss = null;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            cachedBoss = null;
            return;
        }

        AshenColossusEntity boss = nearestBoss(minecraft, player);
        if (boss == null || boss.isDeadOrDying() || boss.getPhase() < BossPhase.P3.index()) {
            return;
        }

        Vec3 centre = boss.arenaCentreVec();
        double ringRadius = boss.getRingRadius();
        double distance = MathUtil.horizontalDistance(centre, player.position());
        boolean outside = distance > ringRadius;

        int arenaRadius = Math.max(1, boss.getArenaRadius());
        float progress = (float) MathUtil.clamp(
                (ringRadius - ArenaRing.MIN_RADIUS) / Math.max(1.0D, arenaRadius - ArenaRing.MIN_RADIUS),
                0.0D, 1.0D);

        int centreX = graphics.guiWidth() / 2;
        int barX = centreX - BAR_WIDTH / 2;
        int barY = graphics.guiHeight() - 74;

        HudText.drawBar(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, progress,
                BACKDROP_ARGB, outside ? DANGER_ARGB : SAFE_ARGB);

        Component label = outside
                ? Component.translatable("hud.creator_colossus.ring_warning")
                : Component.translatable("hud.creator_colossus.ring",
                        String.format(Locale.ROOT, "%.1f", ringRadius));
        String text = label.getString();
        HudText.drawCentered(graphics, text, centreX, barY - 11, outside ? DANGER_ARGB : LABEL_ARGB);
    }

    @Nullable
    private static AshenColossusEntity nearestBoss(Minecraft minecraft, LocalPlayer player) {
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        // Scan on a fixed interval and never per frame - including the "no boss anywhere" case,
        // which is the one a naive `cachedBoss == null` check turns into a per-frame entity sweep.
        // The second test catches a world swap, where the game clock jumps backwards.
        if (now >= nextScanTick || now + SCAN_INTERVAL_TICKS < nextScanTick) {
            nextScanTick = now + SCAN_INTERVAL_TICKS;
            cachedBoss = Selection.nearest(minecraft.level, AshenColossusEntity.class,
                    player.position(), SCAN_RADIUS, boss -> true);
        } else if (cachedBoss != null && !cachedBoss.isAlive()) {
            cachedBoss = null;
        }
        return cachedBoss;
    }

    private ColossusHud() {
    }
}
