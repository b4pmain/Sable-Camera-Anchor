package dev.bapmain.sablecamera.client;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import javax.annotation.Nullable;
import java.util.UUID;

public final class CameraFollowClient {
    private static UUID followId = null;

    private static boolean captured = false;
    private static UUID subId;
    private static float lx, ly, lz, ox, oy, oz, pitch, yaw, roll;

    private CameraFollowClient() {}

    public static void setFollow(@Nullable UUID id) {
        followId = id;
        if (id == null) clearCapture();
    }

    @Nullable
    public static UUID getFollow() {
        return followId;
    }

    public static void clear() {
        followId = null;
        clearCapture();
    }

    public static void captureFrom(CameraAnchorEntity a) {
        followId = a.getUUID();
        subId = a.getAttachedSubLevelId();
        lx = a.getLocalX(); ly = a.getLocalY(); lz = a.getLocalZ();
        ox = a.getOffsetX(); oy = a.getOffsetY(); oz = a.getOffsetZ();
        pitch = a.getLocalPitch(); yaw = a.getLocalYaw(); roll = a.getLocalRoll();
        captured = subId != null;
    }

    public static void clearCapture() {
        captured = false;
        subId = null;
    }

    public static void applyAttach(UUID sub, float lx, float ly, float lz,
                                   float ox, float oy, float oz,
                                   float pitch, float yaw, float roll) {
        subId = sub;
        CameraFollowClient.lx = lx; CameraFollowClient.ly = ly; CameraFollowClient.lz = lz;
        CameraFollowClient.ox = ox; CameraFollowClient.oy = oy; CameraFollowClient.oz = oz;
        CameraFollowClient.pitch = pitch; CameraFollowClient.yaw = yaw; CameraFollowClient.roll = roll;
        captured = sub != null;
    }

    public static boolean hasCapture() { return captured; }
    @Nullable public static UUID capturedSubId() { return subId; }
    public static float lx() { return lx; }
    public static float ly() { return ly; }
    public static float lz() { return lz; }
    public static float ox() { return ox; }
    public static float oy() { return oy; }
    public static float oz() { return oz; }
    public static float pitch() { return pitch; }
    public static float yaw() { return yaw; }
    public static float roll() { return roll; }
}