package dev.riftal.creator.features.toolkit.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.client.ClientToolkitState;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mark key. Client only.
 *
 * <p>Tapping {@code M} in game sends {@code mark_pressed} to the server - the same thing
 * {@code /toolkit take mark} does, except no chat box ever opens, which is the whole point: on
 * camera the director's hand moves and the HUD flashes, and nothing else happens.
 *
 * <p>The core library exposes no key-mapping registry, so this is a raw key check rather than a
 * rebindable {@code KeyMapping}. It only fires with no screen open, on the main window, with a
 * player in the world.
 */
@Mixin(KeyboardHandler.class)
public abstract class ToolkitKeyboardHandlerMixin {

    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"))
    private void creator_toolkit$markKey(long windowPointer, int key, int scanCode, int action,
                                         int modifiers, CallbackInfo ci) {
        if (!CreatorMods.isEnabled(ToolkitFeature.ID)) {
            return;
        }
        if (action != InputConstants.PRESS || key != ToolkitFeature.MARK_KEY || modifiers != 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen != null || minecraft.player == null
                || minecraft.level == null) {
            return;
        }
        if (windowPointer != minecraft.getWindow().getWindow()) {
            return;
        }
        ClientToolkitState.sendMark();
    }
}
