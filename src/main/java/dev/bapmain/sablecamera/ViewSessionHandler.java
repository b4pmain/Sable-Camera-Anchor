package dev.bapmain.sablecamera;

import dev.bapmain.sablecamera.network.FollowCameraPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "sablecamera")
public class ViewSessionHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var sessions = CameraCommands.getViewSessions();
        var state = sessions.get(player.getUUID());
        if (state == null) {
            return;
        }

        // sneak to exit
        if (!player.isShiftKeyDown()) {
            return;
        }

        sessions.remove(player.getUUID());

        PacketDistributor.sendToPlayer(player, new FollowCameraPayload(null));

        player.teleportTo(state.x, state.y, state.z);
        player.setYRot(state.yRot);
        player.setXRot(state.xRot);

        if (player.gameMode.getGameModeForPlayer() != state.gameMode) {
            player.setGameMode(state.gameMode);
        }

        player.connection.resetPosition();

    }
}