package dev.bapmain.sablecamera;

import dev.bapmain.sablecamera.network.CameraPosePayload;
import dev.bapmain.sablecamera.network.FollowCameraPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the (non-spectator) view session. Viewing no longer touches the player's
 * gamemode or position: a session is just an entry in {@link CameraCommands#FOLLOW_TARGETS}
 * plus the client-side render override. Exit is explicit (/camanchor exit or the Exit
 * keybind → ExitViewPayload); sneaking is normal gameplay again.
 */
@EventBusSubscriber(modid = "sablecamera")
public class ViewSessionHandler {

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

    public static boolean isViewing(ServerPlayer player) {
        return CameraCommands.getFollowTargets().containsKey(player.getUUID());
    }

    /**
     * Ends the view session (if any) and tells the client to drop its camera override.
     *
     * @param notifyClient false when the connection is going away (logout)
     * @return true if the player was viewing
     */
    public static boolean endView(ServerPlayer player, boolean notifyClient) {
        boolean wasViewing = CameraCommands.getFollowTargets().remove(player.getUUID()) != null;

        if (notifyClient) {
            PacketDistributor.sendToPlayer(player, FollowCameraPayload.clear());
            PacketDistributor.sendToPlayer(player, CameraPosePayload.clear());
        }
        return wasViewing;
    }
}
