package dev.bapmain.sablecamera.mixin;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import java.util.UUID;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void sablecamera$overrideForAnchor(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {

        if (!(entity instanceof CameraAnchorEntity anchor)) {
            return;
        }

        System.out.println("[SableCamera] ===== Frame =====");
        System.out.println("[SableCamera] attachedSubLevelId = " + anchor.getAttachedSubLevelId());
        System.out.println("[SableCamera] local offset = " + anchor.getLocalX() + ", " + anchor.getLocalY() + ", " + anchor.getLocalZ());
        System.out.println("[SableCamera] local pose = pitch " + anchor.getLocalPitch() + " yaw " + anchor.getLocalYaw());

        UUID subId = anchor.getAttachedSubLevelId();
        if (subId == null) {
            System.out.println("[SableCamera] FAIL: attachedSubLevelId is null");
            return;
        }

        ClientSubLevel clientSub = findClientSubLevel(subId);
        System.out.println("[SableCamera] findClientSubLevel → " + clientSub);
        if (clientSub == null) {
            System.out.println("[SableCamera] FAIL: could not find ClientSubLevel for UUID");
            return;
        }

        Object poseObj = null;
        try {
            poseObj = clientSub.renderPose();
            System.out.println("[SableCamera] renderPose() → " + poseObj);
        } catch (Exception e) {
            System.out.println("[SableCamera] renderPose() threw: " + e);
            return;
        }

        if (poseObj == null) {
            System.out.println("[SableCamera] FAIL: pose is null");
            return;
        }

        // Now we have a real pose – do the transform
        var pose = (dev.ryanhcode.sable.companion.math.Pose3dc) poseObj;  // cast to the real type

        Vector3d localPos = new Vector3d(anchor.getLocalX(), anchor.getLocalY(), anchor.getLocalZ());
        pose.transformPosition(localPos);

        // Apply user offset (world-space after transform)
        double x = localPos.x + anchor.getOffsetX();
        double y = localPos.y + anchor.getOffsetY();
        double z = localPos.z + anchor.getOffsetZ();

        this.setPosition(x, y, z);

        // Orientation (optional for now)
        float yawRad = anchor.getLocalYaw() * ((float) Math.PI / 180.0f);
        float pitchRad = anchor.getLocalPitch() * ((float) Math.PI / 180.0f);

        double lx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double ly = -Math.sin(pitchRad);
        double lz =  Math.cos(yawRad) * Math.cos(pitchRad);

        Vector3d localLook = new Vector3d(lx, ly, lz);
        pose.transformNormal(localLook);

        double horizontal = Math.sqrt(localLook.x * localLook.x + localLook.z * localLook.z);
        float worldYaw = (float) (Mth.atan2(-localLook.x, localLook.z) * (180.0 / Math.PI));
        float worldPitch = (float) (Mth.atan2(-localLook.y, horizontal) * (180.0 / Math.PI));

        this.setRotation(worldYaw, worldPitch);

        System.out.println("[SableCamera] Transform applied → " + localPos.x + ", " + localPos.y + ", " + localPos.z);
    }

    private static ClientSubLevel findClientSubLevel(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (!(container instanceof ClientSubLevelContainer clientContainer)) {
            return null;
        }

        for (ClientSubLevel sub : clientContainer.getAllSubLevels()) {
            if (id.equals(sub.getUniqueId())) {
                return sub;
            }
        }
        return null;
    }
}