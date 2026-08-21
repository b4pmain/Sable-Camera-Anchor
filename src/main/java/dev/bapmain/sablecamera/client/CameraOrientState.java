package dev.bapmain.sablecamera.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class CameraOrientState {

    public static final class State {
        public float yaw, pitch, roll;
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