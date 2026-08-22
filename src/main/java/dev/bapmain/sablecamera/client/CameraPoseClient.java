package dev.bapmain.sablecamera.client;

import net.minecraft.util.Mth;

public final class CameraPoseClient {
    private static boolean active = false;

    private static double x0, y0, z0, x1, y1, z1;
    private static float yaw0, pitch0, roll0, yaw1, pitch1, roll1;
    private static long t0ms, t1ms;

    public static void set(double x, double y, double z, float yaw, float pitch, float roll) {
        if (active) {
            x0 = x1; y0 = y1; z0 = z1;
            yaw0 = yaw1; pitch0 = pitch1; roll0 = roll1;
            t0ms = t1ms;
        } else {
            x0 = x; y0 = y; z0 = z;
            yaw0 = yaw; pitch0 = pitch; roll0 = roll;
            t0ms = System.currentTimeMillis();
        }
        x1 = x; y1 = y; z1 = z;
        yaw1 = yaw; pitch1 = pitch; roll1 = roll;
        t1ms = System.currentTimeMillis();
        active = true;
    }

    public static void clear() { active = false; }
    public static boolean isActive() { return active; }

    /** alpha 0..1 from packet timing, or fixed ~0.5–1 if packets are steady */
    public static double x(float partial) { return lerp(x0, x1, alpha(partial)); }
    public static double y(float partial) { return lerp(y0, y1, alpha(partial)); }
    public static double z(float partial) { return lerp(z0, z1, alpha(partial)); }

    public static float yaw(float partial) {
        return lerpAngle(yaw0, yaw1, (float) alpha(partial));
    }
    public static float pitch(float partial) {
        return (float) lerp(pitch0, pitch1, alpha(partial));
    }
    public static float roll(float partial) {
        return lerpAngle(roll0, roll1, (float) alpha(partial));
    }

    private static double alpha(float partialTick) {
        // Prefer time-based extrapolation between packets (~50ms at 20 Hz)
        long now = System.currentTimeMillis();
        double span = Math.max(1, t1ms - t0ms);
        double a = (now - t1ms) / span + 1.0; // 1 = at latest packet, >1 extrapolate slightly
        return Mth.clamp(a, 0.0, 1.25);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float d = Mth.wrapDegrees(b - a);
        return a + d * t;
    }
}