package dev.bapmain.sablecamera.network;

import dev.bapmain.sablecamera.CameraCommands;
import dev.bapmain.sablecamera.SableCameraMod;
import dev.bapmain.sablecamera.ViewSessionHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: saves unlocked camera aim (pitch, yaw, roll, fov) when locking. */
public record SaveCameraAimPayload(float pitch, float yaw, float roll, float fov) implements CustomPacketPayload {

    public static final Type<SaveCameraAimPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableCameraMod.MOD_ID, "save_camera_aim"));

    public static final StreamCodec<FriendlyByteBuf, SaveCameraAimPayload> CODEC =
            StreamCodec.of(SaveCameraAimPayload::write, SaveCameraAimPayload::read);

    private static void write(FriendlyByteBuf buf, SaveCameraAimPayload p) {
        buf.writeFloat(p.pitch);
        buf.writeFloat(p.yaw);
        buf.writeFloat(p.roll);
        buf.writeFloat(p.fov);
    }

    private static SaveCameraAimPayload read(FriendlyByteBuf buf) {
        return new SaveCameraAimPayload(
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveCameraAimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && ViewSessionHandler.isViewing(player)) {
                CameraCommands.applyUnlockedAim(player, payload.pitch(), payload.yaw(), payload.roll(), payload.fov());
            }
        });
    }
}
