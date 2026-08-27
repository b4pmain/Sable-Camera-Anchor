package dev.bapmain.sablecamera.network;

import dev.bapmain.sablecamera.SableCameraMod;
import dev.bapmain.sablecamera.client.CameraFollowClient;
import dev.bapmain.sablecamera.client.CameraOrientState;
import dev.bapmain.sablecamera.client.ReplayCompat;
import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record FollowCameraPayload(
        @Nullable UUID cameraId,
        boolean hasAttach,
        @Nullable UUID subId,
        float lx, float ly, float lz,
        float ox, float oy, float oz,
        float pitch, float yaw, float roll
) implements CustomPacketPayload {

    public static final Type<FollowCameraPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableCameraMod.MOD_ID, "follow_camera"));

    public static final StreamCodec<FriendlyByteBuf, FollowCameraPayload> CODEC =
            StreamCodec.of(FollowCameraPayload::write, FollowCameraPayload::read);

    public static FollowCameraPayload clear() {
        return new FollowCameraPayload(null, false, null, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static FollowCameraPayload of(CameraAnchorEntity a) {
        return new FollowCameraPayload(
                a.getUUID(),
                a.getAttachedSubLevelId() != null,
                a.getAttachedSubLevelId(),
                a.getLocalX(), a.getLocalY(), a.getLocalZ(),
                a.getOffsetX(), a.getOffsetY(), a.getOffsetZ(),
                a.getLocalPitch(), a.getLocalYaw(), a.getLocalRoll()
        );
    }

    private static void write(FriendlyByteBuf buf, FollowCameraPayload p) {
        buf.writeBoolean(p.cameraId != null);
        if (p.cameraId != null) buf.writeUUID(p.cameraId);
        buf.writeBoolean(p.hasAttach);
        if (p.hasAttach) {
            buf.writeUUID(p.subId);
            buf.writeFloat(p.lx); buf.writeFloat(p.ly); buf.writeFloat(p.lz);
            buf.writeFloat(p.ox); buf.writeFloat(p.oy); buf.writeFloat(p.oz);
            buf.writeFloat(p.pitch); buf.writeFloat(p.yaw); buf.writeFloat(p.roll);
        }
    }

    private static FollowCameraPayload read(FriendlyByteBuf buf) {
        UUID id = buf.readBoolean() ? buf.readUUID() : null;
        if (!buf.readBoolean()) {
            return new FollowCameraPayload(id, false, null, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return new FollowCameraPayload(
                id, true, buf.readUUID(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FollowCameraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {

            // temporarily do NOT skip replay — live was getting follow=null
            if (payload.cameraId() == null) {
                CameraFollowClient.clear();
                CameraOrientState.clear();
                return;
            }

            CameraFollowClient.setFollow(payload.cameraId());
            if (payload.hasAttach()) {
                CameraFollowClient.applyAttach(
                        payload.subId(),
                        payload.lx(), payload.ly(), payload.lz(),
                        payload.ox(), payload.oy(), payload.oz(),
                        payload.pitch(), payload.yaw(), payload.roll()
                );
            }
        });
    }
}