package dev.bapmain.sablecamera.client;

import javax.annotation.Nullable;
import java.util.UUID;

public final class CameraFollowClient {
    private static UUID followId = null;

    private CameraFollowClient() {}

    public static void setFollow(@Nullable UUID id) {
        followId = id;
    }

    @Nullable
    public static UUID getFollow() {
        return followId;
    }

    public static void clear() {
        followId = null;
    }
}