package dev.bapmain.sablecamera.client;

public final class ReplayCompat {
    private ReplayCompat() {}

    public static boolean isInReplay() {
        try {
            Class<?> clazz = Class.forName("com.replaymod.replay.ReplayModReplay");
            Object instance = clazz.getField("instance").get(null);
            if (instance == null) return false;
            Object handler = clazz.getMethod("getReplayHandler").invoke(instance);
            return handler != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}