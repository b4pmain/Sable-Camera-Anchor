package dev.bapmain.sablecamera.network;

import dev.bapmain.sablecamera.SableCameraMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SableCameraMod.MOD_ID).versioned("1");

        registrar.playToClient(
                FollowCameraPayload.TYPE,
                FollowCameraPayload.CODEC,
                FollowCameraPayload::handle
        );
    }
}