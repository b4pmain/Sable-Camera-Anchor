package dev.bapmain.sablecamera.network;

import dev.bapmain.sablecamera.SableCameraMod;
import dev.bapmain.sablecamera.ViewSessionHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: the player pressed the Exit Camera keybind. */
public record ExitViewPayload() implements CustomPacketPayload {

    public static final ExitViewPayload INSTANCE = new ExitViewPayload();

    public static final Type<ExitViewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableCameraMod.MOD_ID, "exit_view"));

    public static final StreamCodec<FriendlyByteBuf, ExitViewPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExitViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ViewSessionHandler.endView(player, true);
            }
        });
    }
}
