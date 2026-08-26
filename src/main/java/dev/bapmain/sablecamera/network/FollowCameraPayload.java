package dev.bapmain.sablecamera.network;

import dev.bapmain.sablecamera.SableCameraMod;
import dev.bapmain.sablecamera.client.CameraFollowClient;
import dev.bapmain.sablecamera.client.CameraOrientState;
import dev.bapmain.sablecamera.client.ReplayCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record FollowCameraPayload(@Nullable UUID cameraId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FollowCameraPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(SableCameraMod.MOD_ID, "follow_camera"));

    public static final StreamCodec<FriendlyByteBuf, FollowCameraPayload> CODEC =
            StreamCodec.of(FollowCameraPayload::write, FollowCameraPayload::read);

    private static void write(FriendlyByteBuf buf, FollowCameraPayload payload) {
        buf.writeBoolean(payload.cameraId != null);
        if (payload.cameraId != null) {
            buf.writeUUID(payload.cameraId);
        }
    }

    private static FollowCameraPayload read(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return new FollowCameraPayload(buf.readUUID());
        }
        return new FollowCameraPayload(null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FollowCameraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (ReplayCompat.isInReplay()) {
                return; // check whether in replay or not
            }
            if (payload.cameraId() == null) {
                CameraFollowClient.clear();
                CameraOrientState.clear();
            } else {
                CameraFollowClient.setFollow(payload.cameraId());
            }
        });
    }
}