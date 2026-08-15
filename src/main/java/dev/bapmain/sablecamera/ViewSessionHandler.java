package dev.bapmain.sablecamera;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "sablecamera")
public class ViewSessionHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var sessions = CameraCommands.getViewSessions(); // expose the map
        CameraCommands.ViewState state = sessions.get(player.getUUID());
        if (state == null) {
            return;
        }

        // Still spectating one of our cameras → do nothing
        if (player.getCamera() instanceof CameraAnchorEntity) {
            return;
        }

        // Exited spectate (crouch / /spectate) → restore
        sessions.remove(player.getUUID());

        player.teleportTo(state.x, state.y, state.z);
        player.setYRot(state.yRot);
        player.setXRot(state.xRot);

        if (player.gameMode.getGameModeForPlayer() != state.gameMode) {
            player.setGameMode(state.gameMode);
        }
    }
}