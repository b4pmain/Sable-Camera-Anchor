package dev.bapmain.sablecamera.client;

/**
 * What the camera override did in the most recent {@code Camera#setup} (once per frame).
 * Read by the FOV / HUD / hand / block-outline hooks later in the same frame.
 * (GameRenderer#shouldRenderBlockOutline runs just before Camera#setup, so it sees the
 * previous frame's value; harmless one-frame lag.)
 */
public final class ActiveView {

    private static boolean overriding;
    private static boolean live;
    private static float fov = 90.0f;

    private ActiveView() {}

    /** Called at the start of every override pass. */
    public static void beginFrame() {
        overriding = false;
        live = false;
    }

    /**
     * @param fov  camera FOV in degrees
     * @param live true when the local player is the actor (non-replay live view):
     *             hand / crosshair / block outline / view bobbing are hidden and the
     *             player model is rendered.
     */
    public static void mark(float fov, boolean live) {
        overriding = true;
        ActiveView.live = live;
        ActiveView.fov = fov;
    }

    /** The render camera was moved to a camera anchor this frame (live or replay). */
    public static boolean isOverriding() { return overriding; }

    /** Live (non-replay) view with the local player as the actor. */
    public static boolean isLiveOverride() { return overriding && live; }

    public static float fov() { return fov; }

    /**
     * Live view requested but nothing could drive the camera this frame (sub-level not on
     * the client and no server pose: the ship isn't loaded anywhere).
     */
    public static boolean isUnavailable() {
        return !overriding && CameraFollowClient.isViewing() && !ReplayCompat.isInReplay();
    }
}
