package dev.bapmain.sablecamera;

import dev.bapmain.sablecamera.network.CameraPosePayload;
import dev.bapmain.sablecamera.network.FollowCameraPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "sablecamera")
public class ViewSessionHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!CameraCommands.getViewSessions().containsKey(player.getUUID())) {
            return;
        }

        // sneak to exit
        if (!player.isShiftKeyDown()) {
            return;
        }

        endView(player, true);
    }

    /**
     * Logging out mid-view used to leave the player stuck in spectator at the camera
     * location on rejoin. PlayerLoggedOutEvent fires before the player is saved, so
     * restoring here persists the original gamemode / position.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        endView(player, false);
        CameraTrackingForcer.forget(player.getUUID());
    }

    /** New client level → previously sent camera entities are gone client-side. */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        CameraTrackingForcer.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        CameraTrackingForcer.forget(event.getEntity().getUUID());
    }

    /**
     * Ends the view session (if any): clears follow state, restores gamemode and position.
     *
     * @param notifyClient false when the connection is going away (logout)
     */
    static void endView(ServerPlayer player, boolean notifyClient) {
        CameraCommands.getFollowTargets().remove(player.getUUID());
        var state = CameraCommands.getViewSessions().remove(player.getUUID());

        if (notifyClient) {
            PacketDistributor.sendToPlayer(player, FollowCameraPayload.clear());
            PacketDistributor.sendToPlayer(player, CameraPosePayload.clear());
        }

        if (state == null) {
            return;
        }

        player.teleportTo(state.x, state.y, state.z);
        player.setYRot(state.yRot);
        player.setXRot(state.xRot);

        if (player.gameMode.getGameModeForPlayer() != state.gameMode) {
            player.setGameMode(state.gameMode);
        }

        if (notifyClient) {
            player.connection.resetPosition();
        }
    }
}
