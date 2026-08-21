package dev.bapmain.sablecamera.mixin;

import dev.bapmain.sablecamera.client.CameraFollowClient;
import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot, float roll);

    private static float lastYaw = 0.0f;
    private static float lastPitch = 0.0f;
    private static float lastRoll = 0.0f;
    private static long lastClientLogMs = 0;

    private static float unwrap(float current, float last) {
        float delta = current - last;
        while (delta > 180.0f) delta -= 360.0f;
        while (delta < -180.0f) delta += 360.0f;
        return last + delta;
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void sablecamera$overrideForAnchor(BlockGetter level, Entity entity,
                                               boolean detached, boolean thirdPersonReverse,
                                               float partialTick, CallbackInfo ci) {

        CameraAnchorEntity anchor = resolveAnchor(entity);
        if (anchor == null) {
            return;
        }

        long t0 = System.nanoTime();

        UUID subId = anchor.getAttachedSubLevelId();
        if (subId == null) {
            return;
        }

        ClientSubLevel clientSub = findClientSubLevel(subId);
        if (clientSub == null) {
            return;
        }

        Object poseObj;
        try {
            poseObj = clientSub.renderPose();
        } catch (Exception e) {
            return;
        }
        if (poseObj == null) {
            return;
        }

        var pose = (dev.ryanhcode.sable.companion.math.Pose3dc) poseObj;
        var rp = pose.rotationPoint();

        Vector3d localPos = new Vector3d(
                rp.x() + anchor.getLocalX() + anchor.getOffsetX(),
                rp.y() + anchor.getLocalY() + anchor.getOffsetY(),
                rp.z() + anchor.getLocalZ() + anchor.getOffsetZ()
        );
        pose.transformPosition(localPos);
        this.setPosition(localPos.x, localPos.y, localPos.z);

        Quaterniondc shipRot = pose.orientation();
        Quaterniond localCam = new Quaterniond()
                .rotateY(Math.toRadians(-anchor.getLocalYaw()))
                .rotateX(Math.toRadians(anchor.getLocalPitch()))
                .rotateZ(Math.toRadians(anchor.getLocalRoll()));
        Quaterniond worldRot = new Quaterniond(shipRot).mul(localCam);

        Vector3d forward = new Vector3d(0.0, 0.0, 1.0);
        Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        worldRot.transform(forward);
        worldRot.transform(up);

        double horizontal = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float pitch = horizontal < 1.0e-5
                ? lastPitch
                : (float) Math.toDegrees(Math.atan2(-forward.y, horizontal));

        Vector3d worldUp = new Vector3d(0.0, 1.0, 0.0);
        Vector3d right = new Vector3d();
        forward.cross(worldUp, right);

        float roll;
        if (right.lengthSquared() < 1.0e-4) {
            roll = lastRoll;
        } else {
            right.normalize();
            Vector3d expectedUp = new Vector3d();
            right.cross(forward, expectedUp);
            expectedUp.normalize();
            roll = (float) Math.toDegrees(Math.atan2(right.dot(up), expectedUp.dot(up)));
        }

        yaw = unwrap(yaw, lastYaw);
        pitch = unwrap(pitch, lastPitch);
        roll = unwrap(roll, lastRoll);

        yaw = lastYaw + (yaw - lastYaw) * 0.7f;
        pitch = lastPitch + (pitch - lastPitch) * 0.25f;
        roll = lastRoll + (roll - lastRoll) * 0.7f;

        lastYaw = yaw;
        lastPitch = pitch;
        lastRoll = roll;

        // Normalize so values don't climb forever
        yaw = Mth.wrapDegrees(yaw);
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
        roll = Mth.wrapDegrees(roll);

        this.setRotation(yaw, pitch, roll);

        long us = (System.nanoTime() - t0) / 1000L;
        long now = System.currentTimeMillis();
        if (now - lastClientLogMs > 1000) {
            lastClientLogMs = now;
            System.out.println(String.format(
                    "[SableCamera][Client] follow=%s pos=(%.1f,%.1f,%.1f) yaw=%.1f pitch=%.1f roll=%.1f mixinUs=%d",
                    CameraFollowClient.getFollow(),
                    localPos.x, localPos.y, localPos.z,
                    yaw, pitch, roll, us));
        }
    }

    /** Prefer follow UUID; fall back to direct spectate entity if ever used. */
    private static CameraAnchorEntity resolveAnchor(Entity cameraEntity) {
        UUID followId = CameraFollowClient.getFollow();
        Minecraft mc = Minecraft.getInstance();

        if (followId != null && mc.player != null && cameraEntity == mc.player) {
            CameraAnchorEntity found = findAnchorByUuid(followId);
            if (found != null) {
                return found;
            }
        }

        if (cameraEntity instanceof CameraAnchorEntity direct) {
            return direct;
        }
        return null;
    }

    private static CameraAnchorEntity findAnchorByUuid(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof CameraAnchorEntity anchor && id.equals(anchor.getUUID())) {
                return anchor;
            }
        }
        return null;
    }

    private static ClientSubLevel findClientSubLevel(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
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