package dev.bapmain.sablecamera;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

import static dev.bapmain.sablecamera.entity.CameraAnchorEntity.LIVE;

@EventBusSubscriber(modid = "sablecamera")
public class CameraTrackingForcer {

    private static int cooldown = 0;
    private static final Map<UUID, Set<Integer>> SENT = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if ((++cooldown % 10) != 0) return;

        var server = event.getServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            for (CameraAnchorEntity cam : LIVE ) {

                for (ServerPlayer player : level.players()) {
                    if (!player.connection.getConnection().isConnected()) continue;
                    if (player.level() != cam.level()) continue;

                    Set<Integer> known = SENT.computeIfAbsent(player.getUUID(), u -> new HashSet<>());

                    if (!known.contains(cam.getId())) {
                        player.connection.send(new ClientboundAddEntityPacket(
                                cam.getId(),
                                cam.getUUID(),
                                cam.getX(), cam.getY(), cam.getZ(),
                                cam.getXRot(), cam.getYRot(),
                                cam.getType(),
                                0,
                                Vec3.ZERO,
                                cam.getYHeadRot()
                        ));
                        var data = cam.getEntityData().getNonDefaultValues();
                        if (data != null) {
                            player.connection.send(new ClientboundSetEntityDataPacket(cam.getId(), data));
                        }
                        known.add(cam.getId());
                    } else {
                        // already spawned - only update position
                        player.connection.send(new ClientboundTeleportEntityPacket(cam));
                    }
                }
            }
        }
    }
}