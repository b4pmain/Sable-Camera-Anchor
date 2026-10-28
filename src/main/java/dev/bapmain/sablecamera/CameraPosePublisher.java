package dev.bapmain.sablecamera;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.bapmain.sablecamera.math.CameraMath;
import dev.bapmain.sablecamera.network.CameraPosePayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
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

        java.util.List<ServerPlayer> lost = null;

        for (var entry : targets.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }

            CameraAnchorEntity anchor = findAnchor(player, entry.getValue());
            if (anchor == null) {
                // Camera deleted, or player changed dimension: end the view instead of
                // leaving the client on a stale captured pose.
                if (lost == null) lost = new java.util.ArrayList<>();
                lost.add(player);
                continue;
            }

            CameraPosePayload payload = computePose(anchor);
            if (payload != null) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }

        if (lost != null) {
            for (ServerPlayer player : lost) {
                ViewSessionHandler.endView(player, true);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Camera view ended: camera no longer available"), true);
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
        Vector3d worldPos = CameraMath.worldPosition(pose,
                anchor.getLocalX(), anchor.getLocalY(), anchor.getLocalZ(),
                anchor.getOffsetX(), anchor.getOffsetY(), anchor.getOffsetZ());

        if (!CameraMath.isSane(worldPos)) {
            return null;
        }

        Quaterniond worldRot = CameraMath.worldRotation(pose.orientation(),
                anchor.getLocalPitch(), anchor.getLocalYaw(), anchor.getLocalRoll());
        Quaternionf mcRot = CameraMath.toMinecraftCamera(worldRot, new Quaternionf());

        return CameraPosePayload.of(worldPos.x, worldPos.y, worldPos.z, mcRot, anchor.getFov());
    }
}