package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Set;
import java.util.StringJoiner;

/** {@code /toolkit tphere <players>} - pull the crew to the director's exact spot and angle. */
public final class TpHereCommands {

    /** The {@code tphere} node, op only. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.op("tphere")
                .then(CommandHelper.arg("targets", EntityArgument.players())
                        .executes(TpHereCommands::run));
    }

    private static int run(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ToolkitRuntime.bind(source.getServer());
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        float yaw = source.getRotation().y;
        float pitch = source.getRotation().x;

        StringJoiner names = new StringJoiner(", ");
        int moved = 0;
        for (ServerPlayer target : targets) {
            pull(level, position, yaw, pitch, target);
            names.add(target.getGameProfile().getName());
            moved++;
        }
        if (moved == 0) {
            return CommandHelper.error(source, ToolkitText.of("tphere.none"));
        }
        return CommandHelper.success(source, ToolkitText.of("tphere", names.toString()));
    }

    /**
     * Puts one player on the director's exact spot, yaw and pitch.
     *
     * <p>Takes a {@link Player} rather than a {@code ServerPlayer} so the GameTest for the exact
     * rotation can drive it with {@code GameTestHelper#makeMockPlayer}, which returns a plain
     * {@code Player}. A real crew member goes through
     * {@code ServerPlayer#teleportTo(ServerLevel, double, double, double, float, float)}, which
     * routes via the connection (and through {@code changeDimension} when the level differs);
     * anything else - a mock, a fake player - gets the base
     * {@code Entity#teleportTo(ServerLevel, ..., Set&lt;RelativeMovement&gt;, float, float)}, whose
     * same-level branch is {@code moveTo(x, y, z, yRot, xRot)} plus {@code setYHeadRot}.
     */
    public static boolean pull(ServerLevel level, Vec3 position, float yaw, float pitch,
                               Player target) {
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(level, position.x, position.y, position.z, yaw, pitch);
            return true;
        }
        return target.teleportTo(level, position.x, position.y, position.z, Set.of(), yaw, pitch);
    }

    private TpHereCommands() {
    }
}
