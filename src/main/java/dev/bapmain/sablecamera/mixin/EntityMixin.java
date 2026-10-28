package dev.bapmain.sablecamera.mixin;

import dev.bapmain.sablecamera.client.ActiveView;
import dev.bapmain.sablecamera.client.CameraAimClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects mouse look to the active camera when camera aim is unlocked.
 * While locked (default), mouse movement continues to turn the local player actor as normal.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void sablecamera$redirectTurnWhenUnlocked(double yRot, double xRot, CallbackInfo ci) {
        if (!CameraAimClient.isUnlocked() || !ActiveView.isOverriding()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity self = (Entity) (Object) this;
        if (self == mc.player || self == mc.getCameraEntity()) {
            CameraAimClient.onMouseTurn(yRot, xRot);
            ci.cancel();
        }
    }
}
