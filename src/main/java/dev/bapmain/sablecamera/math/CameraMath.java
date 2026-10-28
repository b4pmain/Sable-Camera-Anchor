package dev.bapmain.sablecamera.math;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Shared camera pose math (client mixin, debug renderer, server pose publisher).
 *
 * <h2>Frames</h2>
 * <ul>
 *   <li><b>Camera frame</b> (what local pitch/yaw/roll build): +Z forward, +Y up, +X left.
 *       Same frame the debug cone is drawn in.</li>
 *   <li><b>Minecraft camera frame</b> ({@code Camera#rotation()}): -Z forward, +Y up, -X left.
 *       The view matrix is built directly from this quaternion, so writing it avoids any
 *       yaw/pitch/roll round trip (and therefore gimbal lock / pole flips).</li>
 * </ul>
 *
 * <h2>Angle convention</h2>
 * Positive local pitch looks <b>up</b> (inverted from vanilla's xRot, by design).
 * Yaw follows vanilla (0 = ship +Z, positive turns toward -X); roll is unchanged.
 */
public final class CameraMath {

    /** Camera frame → Minecraft camera frame (half turn around Y). */
    private static final Quaterniondc FRAME_TO_MC = new Quaterniond().rotationY(Math.PI);

    private CameraMath() {}

    /** Local (ship-relative) camera rotation in the camera frame. */
    public static Quaterniond localRotation(float pitch, float yaw, float roll) {
        return new Quaterniond()
                .rotateY(Math.toRadians(-yaw))
                .rotateX(Math.toRadians(-pitch)) // negative: positive pitch = up
                .rotateZ(Math.toRadians(roll));
    }

    /** World rotation in the camera frame: ship orientation × local camera rotation. */
    public static Quaterniond worldRotation(Quaterniondc shipRot, float pitch, float yaw, float roll) {
        return new Quaterniond(shipRot).mul(localRotation(pitch, yaw, roll));
    }

    /** Converts a camera-frame rotation into a {@code Camera#rotation()}-compatible quaternion. */
    public static Quaternionf toMinecraftCamera(Quaterniondc frameRot, Quaternionf dest) {
        Quaterniond q = new Quaterniond(frameRot).mul(FRAME_TO_MC);
        return dest.set(q).normalize();
    }

    /** World position of a camera anchored at local + offset (relative to the rotation point). */
    public static Vector3d worldPosition(Pose3dc pose,
                                         float lx, float ly, float lz,
                                         float ox, float oy, float oz) {
        var rp = pose.rotationPoint();
        Vector3d p = new Vector3d(rp.x() + lx + ox, rp.y() + ly + oy, rp.z() + lz + oz);
        pose.transformPosition(p);
        return p;
    }

    /** Guards against garbage poses (unloaded / uninitialised sub-levels). */
    public static boolean isSane(Vector3d p) {
        return Math.abs(p.x) < 1.0e6 && Math.abs(p.y) < 1.0e6 && Math.abs(p.z) < 1.0e6;
    }

    /**
     * Best-effort vanilla {yRot, xRot, roll} in degrees for a Minecraft camera quaternion.
     * Only for code that reads {@code Camera#getYRot()/getXRot()/getRoll()}; rendering uses
     * the quaternion itself. Yaw/roll are ill-defined at exactly ±90° pitch, which is harmless here.
     */
    public static void toEuler(Quaternionfc mcRot, float[] out) {
        Vector3f f = mcRot.transform(0.0f, 0.0f, -1.0f, new Vector3f());
        Vector3f u = mcRot.transform(0.0f, 1.0f, 0.0f, new Vector3f());

        float yRot = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
        float xRot = (float) Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, -f.y))));

        float roll = 0.0f;
        Vector3f right = f.cross(0.0f, 1.0f, 0.0f, new Vector3f());
        if (right.lengthSquared() > 1.0e-6f) {
            right.normalize();
            Vector3f expectedUp = right.cross(f, new Vector3f()).normalize();
            roll = (float) Math.toDegrees(Math.atan2(right.dot(u), expectedUp.dot(u)));
        }

        out[0] = yRot;
        out[1] = xRot;
        out[2] = roll;
    }
}
