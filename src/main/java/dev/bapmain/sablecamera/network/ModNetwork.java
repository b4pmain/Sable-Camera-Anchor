package dev.bapmain.sablecamera.network;

import dev.bapmain.sablecamera.SableCameraMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // "2": CameraPosePayload carries a quaternion instead of yaw/pitch/roll
        // "3": FOV on Follow/CameraPose payloads, C2S ExitViewPayload
        PayloadRegistrar registrar = event.registrar(SableCameraMod.MOD_ID).versioned("3");

        registrar.playToClient(
                FollowCameraPayload.TYPE,
                FollowCameraPayload.CODEC,
                FollowCameraPayload::handle
        );

        registrar.playToClient(
                CameraPosePayload.TYPE,
                CameraPosePayload.CODEC,
                CameraPosePayload::handle
        );

        registrar.playToServer(
                ExitViewPayload.TYPE,
                ExitViewPayload.CODEC,
                ExitViewPayload::handle
        );
    }
}
