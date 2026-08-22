package dev.bapmain.sablecamera.network;

import dev.bapmain.sablecamera.SableCameraMod;
import dev.bapmain.sablecamera.client.CameraPoseClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CameraPosePayload(
        boolean active,
        double x, double y, double z,
        float yaw, float pitch, float roll
) implements CustomPacketPayload {

    public static final Type<CameraPosePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableCameraMod.MOD_ID, "camera_pose"));

    public static final StreamCodec<FriendlyByteBuf, CameraPosePayload> CODEC =
            StreamCodec.of(CameraPosePayload::write, CameraPosePayload::read);

    public static CameraPosePayload clear() {
        return new CameraPosePayload(false, 0, 0, 0, 0, 0, 0);
    }

    public static CameraPosePayload of(double x, double y, double z,
                                       float yaw, float pitch, float roll) {
        return new CameraPosePayload(true, x, y, z, yaw, pitch, roll);
    }

    private static void write(FriendlyByteBuf buf, CameraPosePayload p) {
        buf.writeBoolean(p.active);
        if (p.active) {
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            buf.writeFloat(p.yaw);
            buf.writeFloat(p.pitch);
            buf.writeFloat(p.roll);
        }
    }

    private static CameraPosePayload read(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return clear();
        }
        return of(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CameraPosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!payload.active) {
                CameraPoseClient.clear();
            } else {
                CameraPoseClient.set(payload.x, payload.y, payload.z,
                        payload.yaw, payload.pitch, payload.roll);
            }
        });
    }
}