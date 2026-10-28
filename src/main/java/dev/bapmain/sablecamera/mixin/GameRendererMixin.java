package dev.bapmain.sablecamera.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bapmain.sablecamera.client.ActiveView;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Live (non-spectator) camera view: the player is the actor and stays in first person
 * internally, so hide everything that only makes sense from the player's eyes.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /** First-person hand / held item, plus in-wall / fire / underwater screen overlays. */
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void sablecamera$hideHand(Camera camera, float partialTick, Matrix4f projection, CallbackInfo ci) {
        if (ActiveView.isLiveOverride()) {
            ci.cancel();
        }
    }

    /** Block selection outline from the player's crosshair ray. */
    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void sablecamera$hideBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        if (ActiveView.isLiveOverride()) {
            cir.setReturnValue(false);
        }
    }

    /** View bobbing from the player walking would shake the remote camera. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void sablecamera$noBob(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (ActiveView.isLiveOverride()) {
            ci.cancel();
        }
    }

    /** Hurt tilt of the player would shake the remote camera. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void sablecamera$noHurtBob(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (ActiveView.isLiveOverride()) {
            ci.cancel();
        }
    }
}
