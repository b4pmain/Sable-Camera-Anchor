package dev.bapmain.sablecamera.client;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side catalog of every camera the client has seen (live or in a replay).
 * Keyed by display name for /camreplay, but identity is the camera entity UUID
 * (stable across ReplayMod playback), so:
 * - a camera first seen before its synched CamTag arrived gets upgraded from
 *   its {@code cam-xxxxxxxx} placeholder to the real name;
 * - offset / angle changes refresh the existing entry instead of duplicating it;
 * - user renames (/camreplay rename) are never overwritten.
 */
public final class CameraCatalog {
    public record Entry(UUID subId, float lx, float ly, float lz,
                        float ox, float oy, float oz,
                        float pitch, float yaw, float roll,
                        float fov) {}

    private static final Map<String, Entry> BY_TAG = new LinkedHashMap<>();
    /** camera entity UUID → current catalog name */
    private static final Map<UUID, String> NAME_BY_ENTITY = new HashMap<>();
    /** names that are auto-generated placeholders and may be replaced by a real CamTag */
    private static final Set<String> PLACEHOLDER_NAMES = new HashSet<>();

    private CameraCatalog() {}

    public static void remember(CameraAnchorEntity a) {
        if (a.getAttachedSubLevelId() == null) return;
        Entry e = from(a);
        UUID id = a.getUUID();
        String realTag = a.getCamTag();

        String current = NAME_BY_ENTITY.get(id);
        if (current != null && BY_TAG.containsKey(current)) {
            if (PLACEHOLDER_NAMES.contains(current) && !realTag.isEmpty()) {
                // Synched CamTag arrived after we first saw the entity: upgrade the name.
                BY_TAG.remove(current);
                PLACEHOLDER_NAMES.remove(current);
                put(id, uniqueName(realTag), e, false);
            } else {
                BY_TAG.put(current, e); // refresh data, keeps insertion order
            }
            return;
        }

        boolean placeholder = realTag.isEmpty();
        String base = placeholder ? "cam-" + id.toString().substring(0, 8) : realTag;
        put(id, uniqueName(base), e, placeholder);
    }

    public static boolean rename(String from, String to) {
        Entry e = BY_TAG.remove(from);
        if (e == null) return false;

        if (BY_TAG.remove(to) != null) {
            // don't keep both if 'to' already existed; its camera gets re-added under its own name if seen again
            NAME_BY_ENTITY.values().removeIf(to::equals);
            PLACEHOLDER_NAMES.remove(to);
        }
        BY_TAG.put(to, e);
        PLACEHOLDER_NAMES.remove(from); // user-chosen names are never auto-replaced
        NAME_BY_ENTITY.replaceAll((uuid, name) -> name.equals(from) ? to : name);
        return true;
    }

    private static void put(UUID id, String name, Entry e, boolean placeholder) {
        BY_TAG.put(name, e);
        NAME_BY_ENTITY.put(id, name);
        if (placeholder) {
            PLACEHOLDER_NAMES.add(name);
        }
    }

    private static String uniqueName(String base) {
        if (!BY_TAG.containsKey(base)) return base;
        int n = 2;
        while (BY_TAG.containsKey(base + "-" + n)) n++;
        return base + "-" + n;
    }

    private static Entry from(CameraAnchorEntity a) {
        return new Entry(
                a.getAttachedSubLevelId(),
                a.getLocalX(), a.getLocalY(), a.getLocalZ(),
                a.getOffsetX(), a.getOffsetY(), a.getOffsetZ(),
                a.getLocalPitch(), a.getLocalYaw(), a.getLocalRoll(),
                a.getFov()
        );
    }

    public static Entry get(String tag) { return BY_TAG.get(tag); }
    public static String nameOf(UUID entityId) { return NAME_BY_ENTITY.getOrDefault(entityId, ""); }
    public static Map<String, Entry> all() { return BY_TAG; }
}
