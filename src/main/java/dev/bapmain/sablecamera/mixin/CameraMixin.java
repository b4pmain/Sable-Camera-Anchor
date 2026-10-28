package dev.bapmain.sablecamera.mixin;

import dev.bapmain.sablecamera.client.*;
import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.bapmain.sablecamera.math.CameraMath;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(double x, double y, double z);

    // Camera orientation is written directly as a quaternion: GameRenderer builds the view
    // matrix from Camera#rotation(), so there is no yaw/pitch/roll round trip (no gimbal
    // lock / ±90° pitch snap). xRot/yRot/roll are kept roughly in sync for other readers.
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private float roll;
    @Shadow private boolean detached;

    @Unique private static final float[] sablecamera$euler = new float[3];

    @Inject(method = "setup", at = @At("TAIL"))
    private void sablecamera$overrideForAnchor(BlockGetter level, Entity entity,
                                               boolean detached, boolean thirdPersonReverse,
                                               float partialTick, CallbackInfo ci) {

        ActiveView.beginFrame();

        Minecraft mc = Minecraft.getInstance();
        boolean replay = ReplayCompat.isInReplay();
        boolean liveSelf = !replay && entity == mc.player;

        CameraAnchorEntity anchor = null;

        if (entity instanceof CameraAnchorEntity direct) {
            anchor = direct;
            CameraCatalog.remember(direct);
            if (!replay || !ReplayFollowCache.isActive()) {
                ReplayFollowCache.capture(direct);
            }
        } else if (liveSelf) {
            anchor = resolveAnchor(entity);
        }

        UUID subId;
        float lx, ly, lz, ox, oy, oz;
        float lp, lyaw, lr, fov;
        int orientKey;

        if (replay && ReplayFollowCache.isActive()) {
            subId = ReplayFollowCache.subId();
            lx = ReplayFollowCache.lx();
            ly = ReplayFollowCache.ly();
            lz = ReplayFollowCache.lz();
            ox = ReplayFollowCache.ox();
            oy = ReplayFollowCache.oy();
            oz = ReplayFollowCache.oz();
            lp = ReplayFollowCache.pitch();
            lyaw = ReplayFollowCache.yaw();
            lr = ReplayFollowCache.roll();
            fov = ReplayFollowCache.fov();
            orientKey = Integer.MAX_VALUE;

        } else if (anchor != null) {
            if (!CameraAimClient.isUnlocked()) {
                CameraFollowClient.captureFrom(anchor);
            }

            subId = anchor.getAttachedSubLevelId();
            lx = anchor.getLocalX();
            ly = anchor.getLocalY();
            lz = anchor.getLocalZ();
            ox = anchor.getOffsetX();
            oy = anchor.getOffsetY();
            oz = anchor.getOffsetZ();
            lp = anchor.getLocalPitch();
            lyaw = anchor.getLocalYaw();
            lr = anchor.getLocalRoll();
            fov = anchor.getFov();
            orientKey = anchor.getId();

        } else if (!replay && CameraFollowClient.hasCapture()) {
            subId = CameraFollowClient.capturedSubId();
            lx = CameraFollowClient.lx();
            ly = CameraFollowClient.ly();
            lz = CameraFollowClient.lz();
            ox = CameraFollowClient.ox();
            oy = CameraFollowClient.oy();
            oz = CameraFollowClient.oz();
            lp = CameraFollowClient.pitch();
            lyaw = CameraFollowClient.yaw();
            lr = CameraFollowClient.roll();
            fov = CameraFollowClient.fov();
            orientKey = Integer.MAX_VALUE - 1;

        } else {
            sablecamera$applyServerPoseIfLive(liveSelf, partialTick);
            return;
        }

        if (subId == null) {
            sablecamera$applyServerPoseIfLive(liveSelf, partialTick);
            return;
        }

        if (CameraAimClient.isUnlocked()) {
            lp = CameraAimClient.pitch();
            lyaw = CameraAimClient.yaw();
            lr = CameraAimClient.roll();
            fov = CameraAimClient.fov();
        }

        ClientSubLevel clientSub = findClientSubLevel(subId);

        if (clientSub == null && mc.player != null) {
            try {
                var containing = dev.ryanhcode.sable.Sable.HELPER.getContainingClient(mc.player.position());
                if (containing instanceof ClientSubLevel csl) {
                    clientSub = csl;
                }
            } catch (Exception ignored) {
            }
        }

        if (clientSub == null) {
            sablecamera$applyServerPoseIfLive(liveSelf, partialTick);
            return;
        }

        Pose3dc pose;
        try {
            pose = clientSub.renderPose();
        } catch (Exception e) {
            pose = null;
        }
        if (pose == null) {
            sablecamera$applyServerPoseIfLive(liveSelf, partialTick);
            return;
        }

        Vector3d worldPos = CameraMath.worldPosition(pose, lx, ly, lz, ox, oy, oz);
        this.setPosition(worldPos.x, worldPos.y, worldPos.z);

        Quaterniond worldRot = CameraMath.worldRotation(pose.orientation(), lp, lyaw, lr);
        Quaternionf target = CameraMath.toMinecraftCamera(worldRot, new Quaternionf());

        sablecamera$applyRotation(CameraOrientState.get(orientKey).smoothToward(target));
        sablecamera$markOverride(fov, liveSelf);
    }

    /** Far-view fallback: server-streamed world pose (live view of the local player only). */
    @Unique
    private void sablecamera$applyServerPoseIfLive(boolean liveSelf, float partialTick) {
        if (!liveSelf || !CameraPoseClient.isActive()) {
            return;
        }
        this.setPosition(
                CameraPoseClient.x(partialTick),
                CameraPoseClient.y(partialTick),
                CameraPoseClient.z(partialTick));
        sablecamera$applyRotation(CameraPoseClient.rotation(partialTick));
        float serverFov = CameraAimClient.isUnlocked() ? CameraAimClient.fov() : CameraPoseClient.fov();
        sablecamera$markOverride(serverFov, true);
    }

    /**
     * Records the override for this frame. In live view the player is the actor: report the
     * camera as detached so LevelRenderer draws the local player model (like third person).
     */
    @Unique
    private void sablecamera$markOverride(float fov, boolean liveSelf) {
        ActiveView.mark(fov, liveSelf);
        if (liveSelf) {
            this.detached = true;
        }
    }

    /** Writes a Minecraft-frame rotation into the camera (mirrors Camera#setRotation). */
    @Unique
    private void sablecamera$applyRotation(Quaternionfc q) {
        this.rotation.set(q);
        q.transform(0.0f, 0.0f, -1.0f, this.forwards);
        q.transform(0.0f, 1.0f, 0.0f, this.up);
        q.transform(-1.0f, 0.0f, 0.0f, this.left);

        CameraMath.toEuler(q, sablecamera$euler);
        this.yRot = sablecamera$euler[0];
        this.xRot = sablecamera$euler[1];
        this.roll = sablecamera$euler[2];
    }

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
        if (mc.level == null) return null;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof CameraAnchorEntity a && id.equals(a.getUUID())) return a;
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
