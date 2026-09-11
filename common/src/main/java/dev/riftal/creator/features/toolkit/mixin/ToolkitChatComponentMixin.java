package dev.riftal.creator.features.toolkit.mixin;

import dev.riftal.creator.features.toolkit.ToolkitFeature;
import dev.riftal.creator.features.toolkit.client.ClientToolkitState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /toolkit hide chat on} - the chat log stops drawing. Client only.
 *
 * <p>The chat <em>screen</em> (what you see while typing) is a {@code Screen}, not a HUD layer, so
 * the director can still type commands with chat hidden; only the message log disappears.
 */
@Mixin(ChatComponent.class)
public abstract class ToolkitChatComponentMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",
            at = @At("HEAD"),
            cancellable = true)
    private void creator_toolkit$hideChat(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY,
                                          boolean focused, CallbackInfo ci) {
        if (!ToolkitFeature.enabled()) {
            return;
        }
        if (ClientToolkitState.chatHidden()) {
            ci.cancel();
        }
    }
}
