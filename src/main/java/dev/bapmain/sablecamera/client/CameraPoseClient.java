package dev.bapmain.sablecamera.client;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * Far-view fallback: world pose streamed by the server (CameraPosePublisher, 20 Hz) when the
 * sub-level isn't available client-side. Rotation is a {@code Camera#rotation()} quaternion
 * and is slerped (no Euler interpolation → no pole flips).
 */
public final class CameraPoseClient {
    private static boolean active = false;

    private static double x0, y0, z0, x1, y1, z1;
    private static final Quaternionf rot0 = new Quaternionf();
    private static final Quaternionf rot1 = new Quaternionf();
    private static final Quaternionf out = new Quaternionf();
    private static long t0ms, t1ms;

    public static void set(double x, double y, double z, Quaternionfc rot) {
        if (active) {
            x0 = x1; y0 = y1; z0 = z1;
            rot0.set(rot1);
            t0ms = t1ms;
        } else {
            x0 = x; y0 = y; z0 = z;
            rot0.set(rot);
            t0ms = System.currentTimeMillis();
        }
        x1 = x; y1 = y; z1 = z;
        rot1.set(rot);
        t1ms = System.currentTimeMillis();
        active = true;
    }

    public static void clear() { active = false; }
    public static boolean isActive() { return active; }

    /** alpha 0..1 from packet timing, or fixed ~0.5–1 if packets are steady */
    public static double x(float partial) { return lerp(x0, x1, alpha(partial)); }
    public static double y(float partial) { return lerp(y0, y1, alpha(partial)); }
    public static double z(float partial) { return lerp(z0, z1, alpha(partial)); }

    /** Interpolated rotation (no extrapolation past the latest packet for rotation). */
    public static Quaternionfc rotation(float partial) {
        float a = (float) Math.min(alpha(partial), 1.0);
        return rot0.slerp(rot1, a, out);
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
}
