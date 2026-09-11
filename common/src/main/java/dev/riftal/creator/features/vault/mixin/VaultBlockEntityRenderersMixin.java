package dev.riftal.creator.features.vault.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens vanilla's block-entity-renderer registry, which is the only thing standing between this
 * feature and an altar that moves.
 *
 * <p>{@code BlockEntityRenderers.register} is <b>private static</b> in 1.21.1
 * ({@code BlockEntityRenderers.java:17}), and this project has no way around that:
 * {@code core.client.ClientRenderers} exposes entity renderers and model layers but no block-entity
 * hook, core is off limits to feature agents (CONTRACT.md section 2), and the shared access widener
 * / access transformer is the core agent's file to change, not ours. The loader-side alternatives -
 * Fabric's {@code BlockEntityRendererRegistry} and NeoForge's
 * {@code EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer} - live in the loader
 * modules, and nothing calls into a feature's loader glue, so they are unreachable too.
 *
 * <p>An {@link Invoker} is the one tool the contract explicitly allows here (section 10.2), it adds
 * no behaviour of its own, and it works identically on both loaders. Registration itself happens in
 * {@code VaultClientSetup#init()}, which both loaders drive during mod setup - the same point
 * vanilla and every other mod register theirs, and well before the first resource reload builds the
 * renderer map in {@code BlockEntityRenderers.createEntityRenderers}.
 *
 * <p>No enabled-guard: this is an accessor, not an injection. It changes nothing on its own, and
 * the only caller is behind {@code VaultFeature#initClient()}, which a disabled feature never gets.
 *
 * <p><b>Client only.</b> Listed under {@code "client"} in {@code creatormods-vault.mixins.json}.
 */
@Mixin(BlockEntityRenderers.class)
public interface VaultBlockEntityRenderersMixin {

    /**
     * Vanilla's own {@code private static <T extends BlockEntity> void register(BlockEntityType<?
     * extends T>, BlockEntityRendererProvider<T>)}, made callable.
     */
    @Invoker("register")
    static <T extends BlockEntity> void creator_vault$register(
            BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> renderProvider) {
        throw new AssertionError("mixin did not apply to BlockEntityRenderers");
    }
}
