package dev.riftal.creator.features.vault.worldgen;

import dev.riftal.creator.features.vault.VaultFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.List;

/**
 * Ids for the hand-written worldgen JSON under
 * {@code common/src/main/resources/data/creator_vault/worldgen/}.
 *
 * <p>There is no Java registration here on purpose: {@code creator_vault:cursed_vault} is a plain
 * {@code minecraft:jigsaw} structure, so it needs no custom {@code StructureType} and therefore no
 * code at all beyond these constants. Datapack registries are loaded from the jar's data tree by
 * vanilla; this class only exists so the commands, the GameTests and the JUnit resource tests all
 * spell the same ids - {@code VaultStructureDataTest} walks {@link #POOLS} and {@link #PIECES}
 * rather than re-typing them as string literals, so renaming a pool here fails the build instead
 * of silently generating an empty structure.
 *
 * <p>Everything here is a plain constant - no {@code RegistryEntry.get()} - so it is safe to read
 * from a static context.
 */
public final class VaultStructures {

    /** {@code creator_vault:cursed_vault}. */
    public static final ResourceLocation CURSED_VAULT_ID =
            ResourceLocation.fromNamespaceAndPath(VaultFeature.NAMESPACE, "cursed_vault");

    /** The structure itself, {@code data/creator_vault/worldgen/structure/cursed_vault.json}. */
    public static final ResourceKey<Structure> CURSED_VAULT =
            ResourceKey.create(Registries.STRUCTURE, CURSED_VAULT_ID);

    /**
     * {@code #creator_vault:cursed_vault}, a one-entry structure tag. It exists because
     * {@code ServerLevel#findNearestMapStructure} takes a {@link TagKey}, which is what
     * {@code /vault tp} uses.
     */
    public static final TagKey<Structure> CURSED_VAULT_TAG =
            TagKey.create(Registries.STRUCTURE, CURSED_VAULT_ID);

    /**
     * Absolute Y the vault generates at, matching {@code "start_height": {"absolute": -30}} in
     * {@code worldgen/structure/cursed_vault.json}. Deep enough for deepslate everywhere, shallow
     * enough that {@code /vault tp} lands in the entrance hall rather than in bedrock.
     */
    public static final int VAULT_START_Y = -30;

    /** Root of the template pool ids: {@code creator_vault:cursed_vault/<pool>}. */
    public static final String POOL_ROOT = "cursed_vault/";

    /** The pool the structure starts from. */
    public static final ResourceKey<StructureTemplatePool> ENTRANCE_POOL = pool("entrance");

    /** Everything an entrance or corridor can lead into. */
    public static final ResourceKey<StructureTemplatePool> CORRIDORS_POOL = pool("corridors");

    /** The fallback pool that caps any exit whose piece did not fit. */
    public static final ResourceKey<StructureTemplatePool> CORRIDOR_ENDS_POOL = pool("corridor_ends");

    /** The single treasure room, reachable only from the entrance's treasure anchor. */
    public static final ResourceKey<StructureTemplatePool> TREASURE_POOL = pool("treasure");

    /**
     * Caps the treasure anchor when the treasure room does not fit.
     *
     * <p>A separate pool from {@link #CORRIDOR_ENDS_POOL} because a child piece only attaches when
     * one of its own jigsaws is <em>named</em> what the parent jigsaw <em>targets</em>, and
     * {@code corridor_end} wears {@code creator_vault:vault_in}, not
     * {@code creator_vault:treasure_in}.
     */
    public static final ResourceKey<StructureTemplatePool> TREASURE_ENDS_POOL = pool("treasure_ends");

    /** Every pool under {@code data/creator_vault/worldgen/template_pool/cursed_vault/}. */
    public static final List<ResourceKey<StructureTemplatePool>> POOLS = List.of(
            ENTRANCE_POOL, CORRIDORS_POOL, CORRIDOR_ENDS_POOL, TREASURE_POOL, TREASURE_ENDS_POOL);

    /** Every piece name under {@code data/creator_vault/structure/cursed_vault/}. */
    public static final List<String> PIECES = List.of(
            "entrance", "corridor_straight", "corridor_corner", "corridor_t", "corridor_end",
            "trap_room", "treasure_room", "treasure_end");

    private static ResourceKey<StructureTemplatePool> pool(String name) {
        return ResourceKey.create(Registries.TEMPLATE_POOL,
                ResourceLocation.fromNamespaceAndPath(VaultFeature.NAMESPACE, POOL_ROOT + name));
    }

    private VaultStructures() {
    }
}
