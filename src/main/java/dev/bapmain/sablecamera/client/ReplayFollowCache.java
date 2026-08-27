package dev.bapmain.sablecamera.client;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;

import java.util.UUID;

public final class ReplayFollowCache {
    private static boolean active;
    private static UUID subId;
    private static float lx, ly, lz;
    private static float ox, oy, oz;
    private static float pitch, yaw, roll;

    private ReplayFollowCache() {}

    public static void capture(CameraAnchorEntity a) {
        apply(
                a.getAttachedSubLevelId(),
                a.getLocalX(), a.getLocalY(), a.getLocalZ(),
                a.getOffsetX(), a.getOffsetY(), a.getOffsetZ(),
                a.getLocalPitch(), a.getLocalYaw(), a.getLocalRoll()
        );
    }

    public static void apply(UUID sub,
                             float lx, float ly, float lz,
                             float ox, float oy, float oz,
                             float pitch, float yaw, float roll) {
        ReplayFollowCache.subId = sub;
        ReplayFollowCache.lx = lx;
        ReplayFollowCache.ly = ly;
        ReplayFollowCache.lz = lz;
        ReplayFollowCache.ox = ox;
        ReplayFollowCache.oy = oy;
        ReplayFollowCache.oz = oz;
        ReplayFollowCache.pitch = pitch;
        ReplayFollowCache.yaw = yaw;
        ReplayFollowCache.roll = roll;
        active = sub != null;
    }

    public static void clear() {
        active = false;
        subId = null;
    }

    public static boolean isActive() { return active; }
    public static UUID subId() { return subId; }
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