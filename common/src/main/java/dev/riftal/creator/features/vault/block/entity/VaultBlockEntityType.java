package dev.riftal.creator.features.vault.block.entity;

import com.mojang.datafixers.types.Type;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A {@link BlockEntityType} this feature can actually build from its own package.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>The obvious call, {@code BlockEntityType.Builder.of(Foo::new, block).build(null)}, does not
 * compile here. In vanilla 1.21.1 the factory interface is package-private
 * (<code>$MCSRC/net/minecraft/world/level/block/entity/BlockEntityType.java</code>):
 *
 * <pre>{@code
 * line 315:  @FunctionalInterface
 * line 316:  interface BlockEntitySupplier<T extends BlockEntity> {     // no `public`
 * line 317:      T create(BlockPos pos, BlockState state);
 * line 318:  }
 * line 329:  public static <T extends BlockEntity> BlockEntityType.Builder<T> of(
 *                    BlockEntityType.BlockEntitySupplier<? extends T> factory, Block... validBlocks)
 * line 289:  public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory,
 *                                   Set<Block> validBlocks, Type<?> dataType)
 * }</pre>
 *
 * <p>{@code Builder.of} is public but its parameter type is not, so a lambda or method reference
 * targeting it cannot be written from {@code dev.riftal.creator.features.vault} — javac reports
 * "BlockEntitySupplier is not public in BlockEntityType; cannot be accessed from outside package".
 * Loaders normally paper over this with an access widener / access transformer, but {@code :common}
 * compiles against unmodified NeoForm-decompiled vanilla and those files are core-owned, so the
 * vault feature fixes it inside its own package instead.
 *
 * <h2>How it works</h2>
 *
 * <p>{@code BlockEntityType} is a plain {@code public class} (not final) and {@code create} is a
 * plain public method (not final) — both verified in the decompiled source and in the
 * NeoForge-patched jar. So this subclass:
 *
 * <ul>
 *   <li>passes {@code null} for the inaccessible factory parameter — the {@code null} literal needs
 *       no access to the parameter's type, at compile time or at run time (the constant pool holds
 *       the interface name only inside the constructor descriptor, which is never access-checked),
 *       so this works identically on all three compile classpaths and on both loaders;</li>
 *   <li>keeps its own public {@link Factory} and overrides {@link #create} to use it, which is the
 *       only method that ever read the superclass field.</li>
 * </ul>
 *
 * <p>{@code validBlocks} is passed through untouched, so {@code isValid} and NeoForge's
 * {@code getValidBlocks()} behave exactly as they do for a vanilla-built type, and the
 * {@code dataType} is {@code null} just as {@code build(null)} would have left it.
 *
 * @param <T> the block entity this type creates
 */
public final class VaultBlockEntityType<T extends BlockEntity> extends BlockEntityType<T> {

    /** Public stand-in for the package-private {@code BlockEntityType.BlockEntitySupplier}. */
    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    private final Factory<? extends T> factory;

    private VaultBlockEntityType(Factory<? extends T> factory, Set<Block> validBlocks) {
        super(null, validBlocks, (Type<?>) null);
        this.factory = factory;
    }

    /**
     * Drop-in replacement for {@code BlockEntityType.Builder.of(factory, blocks).build(null)}.
     *
     * @param factory     constructor reference, {@code (BlockPos, BlockState) -> T}
     * @param validBlocks every block this block entity may sit in
     */
    public static <T extends BlockEntity> BlockEntityType<T> of(Factory<? extends T> factory,
                                                                Block... validBlocks) {
        return new VaultBlockEntityType<>(factory, Set.of(validBlocks));
    }

    @Override
    public T create(BlockPos pos, BlockState state) {
        return this.factory.create(pos, state);
    }
}
