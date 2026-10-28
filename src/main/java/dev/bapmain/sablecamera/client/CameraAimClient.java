package dev.bapmain.sablecamera.client;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import net.minecraft.util.Mth;

/**
 * Client-side unlocked aim + FOV state while in viewing mode.
 * When unlocked, mouse look adjusts local pitch/yaw and scroll / '-' / '=' adjust FOV.
 * Locking persists the edited values to the camera (server C2S in live view, catalog/cache in replay).
 */
public final class CameraAimClient {

    private static boolean unlocked = false;
    private static float pitch;
    private static float yaw;
    private static float roll;
    private static float fov = CameraAnchorEntity.DEFAULT_FOV;

    private CameraAimClient() {}

    public static boolean isUnlocked() {
        return unlocked;
    }

    public static void unlock(float initialPitch, float initialYaw, float initialRoll, float initialFov) {
        pitch = initialPitch;
        yaw = initialYaw;
        roll = initialRoll;
        fov = Mth.clamp(initialFov, CameraAnchorEntity.MIN_FOV, CameraAnchorEntity.MAX_FOV);
        unlocked = true;
    }

    public static void lock() {
        unlocked = false;
    }

    public static void reset() {
        unlocked = false;
    }

    /**
     * Applies mouse turn deltas (vanilla Entity#turn passes yRot, xRot before 0.15f scaling).
     * Positive pitch in Sable Camera Anchor looks UP, whereas vanilla xRot positive is DOWN,
     * so we subtract {@code xRot * 0.15f}.
     */
    public static void onMouseTurn(double yRot, double xRot) {
        float dPitch = (float) xRot * 0.15f;
        float dYaw = (float) yRot * 0.15f;
        pitch = Mth.clamp(pitch - dPitch, -90.0f, 90.0f);
        yaw = Mth.wrapDegrees(yaw + dYaw);
    }

    public static void adjustFov(float delta) {
        fov = Mth.clamp(fov + delta, CameraAnchorEntity.MIN_FOV, CameraAnchorEntity.MAX_FOV);
    }

    public static float pitch() { return pitch; }
    public static float yaw() { return yaw; }
    public static float roll() { return roll; }
    public static float fov() { return fov; }
}
