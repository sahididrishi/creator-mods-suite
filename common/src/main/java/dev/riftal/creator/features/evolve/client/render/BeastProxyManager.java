package dev.riftal.creator.features.evolve.client.render;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.client.ClientEvolutionCache;
import dev.riftal.creator.features.evolve.entity.ApexBeast;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One never-spawned {@link ApexBeast} per Apex player, kept in step with that player every frame so
 * GeckoLib has something real to animate.
 *
 * <p>This is the plan's {@code BeastProxyManager}. GeckoLib animates a {@code GeoAnimatable}, not a
 * bag of floats, so drawing the beast in place of a player means handing the renderer an actual
 * {@code ApexBeast} whose position, rotations, walk animation and swing state are copied off the
 * player. The proxy is <b>never added to a level</b>: it is constructed with
 * {@code EntityType#create(Level)}, kept in this map, and read by {@link PlayerRenderSwap} only.
 * Nothing ticks it, nothing collides with it, and {@code helper.assertEntityNotPresent} in the
 * GameTests stays true.
 *
 * <p>Each proxy gets its own entity id, {@code player id + 0x40000000}, so two Apex players in view
 * cannot share GeckoLib animation state (plan section 11.8).
 *
 * <p><b>Client only.</b>
 */
public final class BeastProxyManager {

    /** Keeps proxy ids clear of any real entity id the server could hand out. */
    private static final int PROXY_ID_OFFSET = 0x4000_0000;

    private static final Map<UUID, ApexBeast> PROXIES = new HashMap<>();

    /** Identity of the level the proxies belong to. Never dereferenced beyond the comparison. */
    private static Object lastLevel;

    /**
     * The proxy for this player, synced to the current frame, or null if the entity type could not
     * be instantiated (which would mean the feature never registered).
     */
    public static ApexBeast proxyFor(AbstractClientPlayer player, float partialTicks) {
        Level level = player.level();
        if (level != lastLevel) {
            lastLevel = level;
            PROXIES.clear();
        }

        ApexBeast proxy = PROXIES.get(player.getUUID());
        if (proxy == null) {
            proxy = create(player, level);
            if (proxy == null) {
                return null;
            }
            PROXIES.put(player.getUUID(), proxy);
        }
        syncFrom(proxy, player, partialTicks);
        return proxy;
    }

    /** Drops a player's proxy. Called the moment they stop wearing the beast. */
    public static void forget(UUID playerId) {
        if (!PROXIES.isEmpty()) {
            PROXIES.remove(playerId);
        }
    }

    /** Drops every proxy. Called on disconnect and when the cache is wiped. */
    public static void clear() {
        PROXIES.clear();
        lastLevel = null;
    }

    /** How many proxies are alive. Exposed for the render test and for profiling. */
    public static int size() {
        return PROXIES.size();
    }

    private static ApexBeast create(AbstractClientPlayer player, Level level) {
        ApexBeast proxy;
        try {
            EntityType<ApexBeast> type = EvolveFeature.apexBeast().get();
            proxy = type.create(level);
        } catch (RuntimeException failure) {
            // An unbound registry entry or a refused instantiation: log once per proxy attempt and
            // let the player keep their own body rather than dropping a frame.
            LOG.error("[evolve] could not build an apex render proxy; stage 5 falls back to the "
                    + "player skin", failure);
            return null;
        }
        if (proxy == null) {
            return null;
        }
        proxy.setId(player.getId() + PROXY_ID_OFFSET);
        proxy.setNoAi(true);
        proxy.setSilent(true);
        proxy.setNoGravity(true);
        return proxy;
    }

    /**
     * Copies everything the renderer and the animation controllers read. Position and both rotation
     * pairs are copied with their previous-tick twins so GeckoLib's own interpolation lands on the
     * same place the player's body would have.
     */
    private static void syncFrom(ApexBeast proxy, AbstractClientPlayer player, float partialTicks) {
        proxy.xo = player.xo;
        proxy.yo = player.yo;
        proxy.zo = player.zo;
        proxy.setPos(player.getX(), player.getY(), player.getZ());

        proxy.yRotO = player.yRotO;
        proxy.xRotO = player.xRotO;
        proxy.setYRot(player.getYRot());
        proxy.setXRot(player.getXRot());
        proxy.yBodyRotO = player.yBodyRotO;
        proxy.yBodyRot = player.yBodyRot;
        proxy.yHeadRotO = player.yHeadRotO;
        proxy.yHeadRot = player.yHeadRot;

        // The walk cycle is time-driven in the animation file; the controllers only need "is this
        // thing moving", so advancing once per player tick is both correct and cheap.
        float speed = player.walkAnimation.speed(partialTicks);
        if (proxy.tickCount != player.tickCount) {
            proxy.walkAnimation.update(speed, 1.0F);
        } else {
            proxy.walkAnimation.setSpeed(speed);
        }
        proxy.tickCount = player.tickCount;

        proxy.swinging = player.swinging;
        proxy.oAttackAnim = player.oAttackAnim;
        proxy.attackAnim = player.attackAnim;
        proxy.hurtTime = player.hurtTime;
        proxy.deathTime = player.deathTime;

        proxy.setInvisible(player.isInvisible());
        proxy.setShiftKeyDown(player.isShiftKeyDown());
        proxy.setSprinting(player.isSprinting());
        proxy.setRemainingFireTicks(player.isOnFire() ? 1 : 0);

        if (ClientEvolutionCache.roaring(player.getUUID())) {
            proxy.setRoaringUntil(player.level().getGameTime() + ApexBeast.ROAR_TICKS);
        }
    }

    private BeastProxyManager() {
    }
}
