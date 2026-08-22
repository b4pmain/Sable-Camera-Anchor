package dev.bapmain.sablecamera;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.bapmain.sablecamera.network.CameraPosePayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "sablecamera")
public class CameraPosePublisher {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {

        Map<UUID, UUID> targets = CameraCommands.getFollowTargets();
        if (targets.isEmpty()) {
            return;
        }

        var server = event.getServer();
        if (server == null) {
            return;
        }

        for (var entry : targets.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }

            CameraAnchorEntity anchor = findAnchor(player, entry.getValue());
            if (anchor == null) {
                continue;
            }

            CameraPosePayload payload = computePose(anchor);
            if (payload != null) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @Nullable
    private static CameraAnchorEntity findAnchor(ServerPlayer player, UUID id) {
        Entity e = player.serverLevel().getEntity(id);
        if (e instanceof CameraAnchorEntity anchor) {
            return anchor;
        }
        return null;
    }

    private static CameraPosePayload computePose(CameraAnchorEntity anchor) {
        UUID subId = anchor.getAttachedSubLevelId();
        if (subId == null) {
            return null;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(anchor.level());
        if (container == null) {
            return null;
        }

        SubLevel sub = container.getSubLevel(subId);
        if (sub == null) {
            return null;
        }

        var pose = sub.logicalPose();
        var rp = pose.rotationPoint();

        Vector3d localPos = new Vector3d(
                rp.x() + anchor.getLocalX() + anchor.getOffsetX(),
                rp.y() + anchor.getLocalY() + anchor.getOffsetY(),
                rp.z() + anchor.getLocalZ() + anchor.getOffsetZ()
        );
        pose.transformPosition(localPos);

        if (Math.abs(localPos.x) > 1.0e6
                || Math.abs(localPos.y) > 1.0e6
                || Math.abs(localPos.z) > 1.0e6) {
            return null;
        }

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
                ? 0f
                : (float) Math.toDegrees(Math.atan2(-forward.y, horizontal));

        Vector3d worldUp = new Vector3d(0.0, 1.0, 0.0);
        Vector3d right = new Vector3d();
        forward.cross(worldUp, right);

        float roll = 0f;
        if (right.lengthSquared() >= 1.0e-4) {
            right.normalize();
            Vector3d expectedUp = new Vector3d();
            right.cross(forward, expectedUp);
            expectedUp.normalize();
            roll = (float) Math.toDegrees(Math.atan2(right.dot(up), expectedUp.dot(up)));
        }

        yaw = Mth.wrapDegrees(yaw);
        pitch = Mth.clamp(pitch, -90f, 90f);
        roll = Mth.wrapDegrees(roll);

        return CameraPosePayload.of(localPos.x, localPos.y, localPos.z, yaw, pitch, roll);
    }
}