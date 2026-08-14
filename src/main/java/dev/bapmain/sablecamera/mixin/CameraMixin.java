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
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot, float roll);

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

        // ===== Orientation (look vector + up vector) =====

        Quaterniondc shipRot = pose.orientation();

        Quaterniond localCam = new Quaterniond()
                .rotateY(Math.toRadians(-anchor.getLocalYaw()))
                .rotateX(Math.toRadians(anchor.getLocalPitch()));

        Quaterniond worldRot = new Quaterniond(shipRot).mul(localCam);

        Vector3d forward = new Vector3d(0.0, 0.0, 1.0);
        Vector3d up      = new Vector3d(0.0, 1.0, 0.0);

        worldRot.transform(forward);
        worldRot.transform(up);

        double horizontal = Math.sqrt(forward.x * forward.x + forward.z * forward.z);

        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));

        float pitch;
        if (horizontal < 1.0e-5) {
            // Nearly vertical – keep previous pitch, avoid noisy atan2
            pitch = lastPitch;
        } else {
            pitch = (float) Math.toDegrees(Math.atan2(-forward.y, horizontal));
        }

// Roll
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

            double dot = expectedUp.dot(up);
            double det = right.dot(up);
            roll = (float) Math.toDegrees(Math.atan2(det, dot));
        }

// ----- Unwrap all three axes -----
        yaw   = unwrap(yaw,   lastYaw);
        pitch = unwrap(pitch, lastPitch);
        roll  = unwrap(roll,  lastRoll);

        yaw   = lastYaw   + (yaw   - lastYaw)   * 0.7f;
        pitch = lastPitch + (pitch - lastPitch) * 0.25f;  // stronger smoothing
        roll  = lastRoll  + (roll  - lastRoll)  * 0.7f;

        lastYaw = yaw;
        lastPitch = pitch;
        lastRoll = roll;

        this.setRotation(yaw, pitch, roll);

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

    private static float lastYaw = 0.0f;
    private static float lastPitch = 0.0f;
    private static float lastRoll = 0.0f;

    private static float unwrap(float current, float last) {
        float delta = current - last;
        while (delta > 180.0f)  delta -= 360.0f;
        while (delta < -180.0f) delta += 360.0f;
        return last + delta;
    }
}