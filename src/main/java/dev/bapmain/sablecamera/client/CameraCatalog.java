package dev.bapmain.sablecamera.client;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CameraCatalog {
    public record Entry(java.util.UUID subId, float lx, float ly, float lz,
                        float ox, float oy, float oz,
                        float pitch, float yaw, float roll) {}

    private static final Map<String, Entry> BY_TAG = new LinkedHashMap<>();

    private CameraCatalog() {}

    public static void remember(CameraAnchorEntity a) {
        if (a.getAttachedSubLevelId() == null) return;
        Entry e = from(a);

        for (Entry existing : BY_TAG.values()) {
            if (sameCamera(existing, e)) {
                return; // already listed (including after rename)
            }
        }

        String name = a.getCamTag();
        if (name.isEmpty()) {
            for (String tag : a.getTags()) {
                if (!"sable_camera".equals(tag)) {
                    name = tag;
                    break;
                }
            }
        }
        if (name.isEmpty()) {
            name = "cam-" + a.getUUID().toString().substring(0, 8);
        }
        BY_TAG.put(name, e);
    }

    public static boolean rename(String from, String to) {
        Entry e = BY_TAG.remove(from);
        if (e == null) return false;
        BY_TAG.remove(to); // don’t keep both if 'to' already existed
        BY_TAG.put(to, e);
        return true;
    }

    private static boolean sameCamera(Entry a, Entry b) {
        return a.subId().equals(b.subId())
                && a.lx() == b.lx() && a.ly() == b.ly() && a.lz() == b.lz()
                && a.ox() == b.ox() && a.oy() == b.oy() && a.oz() == b.oz();
    }

    private static Entry from(CameraAnchorEntity a) {
        return new Entry(
                a.getAttachedSubLevelId(),
                a.getLocalX(), a.getLocalY(), a.getLocalZ(),
                a.getOffsetX(), a.getOffsetY(), a.getOffsetZ(),
                a.getLocalPitch(), a.getLocalYaw(), a.getLocalRoll()
        );
    }

    public static Entry get(String tag) { return BY_TAG.get(tag); }
    public static Map<String, Entry> all() { return BY_TAG; }
}