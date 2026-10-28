package dev.bapmain.sablecamera.network;

import dev.bapmain.sablecamera.SableCameraMod;
import dev.bapmain.sablecamera.client.CameraPoseClient;
import dev.bapmain.sablecamera.client.ReplayCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * Server → client world pose of the followed camera (far-view fallback).
 * Rotation is a {@code Camera#rotation()}-compatible quaternion (see CameraMath).
 */
public record CameraPosePayload(
        boolean active,
        double x, double y, double z,
        float qx, float qy, float qz, float qw,
        float fov
) implements CustomPacketPayload {

    public static final Type<CameraPosePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableCameraMod.MOD_ID, "camera_pose"));

    public static final StreamCodec<FriendlyByteBuf, CameraPosePayload> CODEC =
            StreamCodec.of(CameraPosePayload::write, CameraPosePayload::read);

    public static CameraPosePayload clear() {
        return new CameraPosePayload(false, 0, 0, 0, 0, 0, 0, 1, 90.0f);
    }

    public static CameraPosePayload of(double x, double y, double z, Quaternionfc rot, float fov) {
        return new CameraPosePayload(true, x, y, z, rot.x(), rot.y(), rot.z(), rot.w(), fov);
    }

    private static void write(FriendlyByteBuf buf, CameraPosePayload p) {
        buf.writeBoolean(p.active);
        if (p.active) {
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            buf.writeFloat(p.qx);
            buf.writeFloat(p.qy);
            buf.writeFloat(p.qz);
            buf.writeFloat(p.qw);
            buf.writeFloat(p.fov);
        }
    }

    private static CameraPosePayload read(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return clear();
        }
        return new CameraPosePayload(true,
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CameraPosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (ReplayCompat.isInReplay()) {
                CameraPoseClient.clear();
                return;
            }
            if (!payload.active) {
                CameraPoseClient.clear();
            } else {
                CameraPoseClient.set(payload.x, payload.y, payload.z,
                        new Quaternionf(payload.qx, payload.qy, payload.qz, payload.qw).normalize(),
                        payload.fov);
            }
        });
    }
}
