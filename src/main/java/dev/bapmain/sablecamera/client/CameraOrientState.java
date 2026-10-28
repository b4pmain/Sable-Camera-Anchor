package dev.bapmain.sablecamera.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * Per-view orientation smoothing. Smoothing is a quaternion slerp (no per-axis Euler lerp,
 * so no unwrap discontinuities at the poles) with a frame-rate independent factor.
 * A fresh / cleared state snaps to the first target instead of swooping from (0,0,0).
 */
public final class CameraOrientState {

    /**
     * Exponential smoothing rate (1/s). 72/s ≈ the old per-frame factor of 0.7 at 60 FPS
     * (time constant ~14 ms): removes residual jitter without visible lag.
     */
    private static final float SMOOTHING_RATE = 72.0f;
    /** Clamp for frame hitches so a long pause doesn't produce a huge single step. */
    private static final float MAX_DT = 0.25f;

    public static final class State {
        public final Quaternionf rot = new Quaternionf();
        boolean valid;
        long lastNanos;

        /** Moves this state toward {@code target} and returns the smoothed rotation. */
        public Quaternionfc smoothToward(Quaternionfc target) {
            long now = System.nanoTime();
            if (!valid) {
                rot.set(target);
                valid = true;
            } else {
                float dt = Math.min((now - lastNanos) / 1.0e9f, MAX_DT);
                float alpha = 1.0f - (float) Math.exp(-SMOOTHING_RATE * dt);
                rot.slerp(target, alpha).normalize(); // JOML slerp takes the shortest path
            }
            lastNanos = now;
            return rot;
        }
    }

    private static final Int2ObjectOpenHashMap<State> ORIENT = new Int2ObjectOpenHashMap<>();

    private CameraOrientState() {}

    public static State get(int entityId) {
        return ORIENT.computeIfAbsent(entityId, id -> new State());
    }

    public static void clear() {
        ORIENT.clear();
    }
}
