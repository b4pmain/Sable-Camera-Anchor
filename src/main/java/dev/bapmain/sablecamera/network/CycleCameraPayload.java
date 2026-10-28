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

/** Client → server: cycle to the previous (-1) or next (+1) camera while in viewing mode. */
public record CycleCameraPayload(int step) implements CustomPacketPayload {

    public static final Type<CycleCameraPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableCameraMod.MOD_ID, "cycle_camera"));

    public static final StreamCodec<FriendlyByteBuf, CycleCameraPayload> CODEC =
            StreamCodec.of(CycleCameraPayload::write, CycleCameraPayload::read);

    private static void write(FriendlyByteBuf buf, CycleCameraPayload p) {
        buf.writeVarInt(p.step);
    }

    private static CycleCameraPayload read(FriendlyByteBuf buf) {
        return new CycleCameraPayload(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CycleCameraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && ViewSessionHandler.isViewing(player)) {
                int step = payload.step() < 0 ? -1 : 1;
                CameraCommands.cycleCamera(player, step);
            }
        });
    }
}
