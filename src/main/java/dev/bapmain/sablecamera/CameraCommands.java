package dev.bapmain.sablecamera;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.bapmain.sablecamera.entity.ModEntities;
import dev.bapmain.sablecamera.network.FollowCameraPayload;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CameraCommands {

    /** Reserved tag that targets every camera (delcam / angle / offset). */
    public static final String ALL = "all";
    /** Legacy marker tag, never shown as a camera name. */
    private static final String LEGACY_MARKER_TAG = "sable_camera";

    private static final String USAGE_ADDCAM = "/camanchor addcam <tag> [pos]";
    private static final String USAGE_DELCAM = "/camanchor delcam <tag|all>";
    private static final String USAGE_ANGLE  = "/camanchor angle <pitch> <yaw> [roll] [fov] <tag|all>";
    private static final String USAGE_OFFSET = "/camanchor offset <x> <y> <z> <tag|all>";
    private static final String USAGE_FOV    = "/camanchor fov <fov> <tag|all>";
    private static final String USAGE_VIEW   = "/camanchor view <tag>";
    private static final String USAGE_SHOW   = "/camanchor show <true|false>";

    /** Op level for commands that create / modify cameras. view / exit / list are open to everyone. */
    private static final int ADMIN_LEVEL = 2;

    private static final List<String> GRID_POSITIONS = List.of(
            "topleft", "topcenter", "topright",
            "centerleft", "center", "centerright",
            "bottomleft", "bottomcenter", "bottomright");

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TAGS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(cameraNames(ctx.getSource().getLevel()), builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TAGS_OR_ALL = (ctx, builder) -> {
        List<String> names = new ArrayList<>(cameraNames(ctx.getSource().getLevel()));
        names.add(ALL);
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GRID =
            (ctx, builder) -> SharedSuggestionProvider.suggest(GRID_POSITIONS, builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("camanchor")
                // no root permission: each admin subcommand requires ADMIN_LEVEL itself

                .then(Commands.literal("addcam")
                        .requires(CameraCommands::isAdmin)
                        .executes(ctx -> usage(ctx.getSource(), "Missing camera tag.", USAGE_ADDCAM))
                        .then(Commands.argument("tag", StringArgumentType.string())
                                // no pos → player position
                                .executes(ctx -> addCamera(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "tag"),
                                        null))
                                // with pos → grid on block under player
                                .then(Commands.argument("pos", StringArgumentType.string())
                                        .suggests(SUGGEST_GRID)
                                        .executes(ctx -> addCamera(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "tag"),
                                                StringArgumentType.getString(ctx, "pos")))
                                )
                        )
                )

                .then(Commands.literal("delcam")
                        .requires(CameraCommands::isAdmin)
                        // no args → error
                        .executes(ctx -> usage(ctx.getSource(),
                                "Input a tag name first (use \"all\" to delete all).", USAGE_DELCAM))
                        .then(Commands.argument("tag", StringArgumentType.string())
                                .suggests(SUGGEST_TAGS_OR_ALL)
                                .executes(ctx -> deleteCameras(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "tag")))
                        )
                )

                // Children order matters: numeric nodes are registered before "tag" so that
                // "/angle 10 0 5" parses 5 as roll (→ missing tag) instead of a tag named "5"
                // (same for fov after roll). Omitted roll = 0, omitted fov = unchanged.
                .then(Commands.literal("angle")
                        .requires(CameraCommands::isAdmin)
                        .executes(ctx -> usage(ctx.getSource(),
                                "Missing pitch, yaw and camera tag.", USAGE_ANGLE))
                        .then(Commands.argument("pitch", FloatArgumentType.floatArg())
                                .executes(ctx -> usage(ctx.getSource(),
                                        "Missing yaw and camera tag.", USAGE_ANGLE))
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                        .executes(ctx -> usage(ctx.getSource(),
                                                "Missing camera tag (use \"all\" to target every camera).", USAGE_ANGLE))
                                        .then(Commands.argument("roll", FloatArgumentType.floatArg())
                                                .executes(ctx -> usage(ctx.getSource(),
                                                        "Missing camera tag (use \"all\" to target every camera).", USAGE_ANGLE))
                                                .then(Commands.argument("fov", fovArg())
                                                        .executes(ctx -> usage(ctx.getSource(),
                                                                "Missing camera tag (use \"all\" to target every camera).", USAGE_ANGLE))
                                                        .then(Commands.argument("tag", StringArgumentType.string())
                                                                .suggests(SUGGEST_TAGS_OR_ALL)
                                                                .executes(ctx -> setAngle(ctx.getSource(),
                                                                        FloatArgumentType.getFloat(ctx, "pitch"),
                                                                        FloatArgumentType.getFloat(ctx, "yaw"),
                                                                        FloatArgumentType.getFloat(ctx, "roll"),
                                                                        FloatArgumentType.getFloat(ctx, "fov"),
                                                                        StringArgumentType.getString(ctx, "tag")))))
                                                .then(Commands.argument("tag", StringArgumentType.string())
                                                        .suggests(SUGGEST_TAGS_OR_ALL)
                                                        .executes(ctx -> setAngle(ctx.getSource(),
                                                                FloatArgumentType.getFloat(ctx, "pitch"),
                                                                FloatArgumentType.getFloat(ctx, "yaw"),
                                                                FloatArgumentType.getFloat(ctx, "roll"),
                                                                null,
                                                                StringArgumentType.getString(ctx, "tag")))))
                                        .then(Commands.argument("tag", StringArgumentType.string())
                                                .suggests(SUGGEST_TAGS_OR_ALL)
                                                .executes(ctx -> setAngle(ctx.getSource(),
                                                        FloatArgumentType.getFloat(ctx, "pitch"),
                                                        FloatArgumentType.getFloat(ctx, "yaw"),
                                                        0.0f,
                                                        null,
                                                        StringArgumentType.getString(ctx, "tag")))))))

                .then(Commands.literal("fov")
                        .requires(CameraCommands::isAdmin)
                        .executes(ctx -> usage(ctx.getSource(), "Missing fov and camera tag.", USAGE_FOV))
                        .then(Commands.argument("fov", fovArg())
                                .executes(ctx -> usage(ctx.getSource(),
                                        "Missing camera tag (use \"all\" to target every camera).", USAGE_FOV))
                                .then(Commands.argument("tag", StringArgumentType.string())
                                        .suggests(SUGGEST_TAGS_OR_ALL)
                                        .executes(ctx -> setFov(ctx.getSource(),
                                                FloatArgumentType.getFloat(ctx, "fov"),
                                                StringArgumentType.getString(ctx, "tag"))))))

                .then(Commands.literal("exit")
                        .executes(ctx -> exitView(ctx.getSource())))

                .then(Commands.literal("offset")
                        .requires(CameraCommands::isAdmin)
                        .executes(ctx -> usage(ctx.getSource(),
                                "Missing x, y, z and camera tag.", USAGE_OFFSET))
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .executes(ctx -> usage(ctx.getSource(),
                                        "Missing y, z and camera tag.", USAGE_OFFSET))
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> usage(ctx.getSource(),
                                                "Missing z and camera tag.", USAGE_OFFSET))
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> usage(ctx.getSource(),
                                                        "Missing camera tag (use \"all\" to target every camera).", USAGE_OFFSET))
                                                .then(Commands.argument("tag", StringArgumentType.string())
                                                        .suggests(SUGGEST_TAGS_OR_ALL)
                                                        .executes(ctx -> setOffset(ctx.getSource(),
                                                                DoubleArgumentType.getDouble(ctx, "x"),
                                                                DoubleArgumentType.getDouble(ctx, "y"),
                                                                DoubleArgumentType.getDouble(ctx, "z"),
                                                                StringArgumentType.getString(ctx, "tag")))
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("view")
                        .executes(ctx -> usage(ctx.getSource(), "Missing camera tag.", USAGE_VIEW))
                        .then(Commands.argument("tag", StringArgumentType.string())
                                .suggests(SUGGEST_TAGS)
                                .executes(ctx -> viewCamera(ctx.getSource(), StringArgumentType.getString(ctx, "tag")))
                        )
                )
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            List<String> names = cameraNames(ctx.getSource().getLevel());

                            if (names.isEmpty()) {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("No cameras found").withStyle(ChatFormatting.GRAY), false);
                            } else {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("Cameras: " + String.join(", ", names))
                                                .withStyle(ChatFormatting.AQUA), false);
                            }
                            return names.size();
                        })
                )
                .then(Commands.literal("show")
                        .requires(CameraCommands::isAdmin)
                        .executes(ctx -> usage(ctx.getSource(), "Missing true/false.", USAGE_SHOW))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    List<CameraAnchorEntity> cams = findCameras(ctx.getSource().getLevel(), ALL);
                                    for (CameraAnchorEntity anchor : cams) {
                                        anchor.setCameraVisible(value);
                                    }

                                    int finalCount = cams.size();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            (value ? "Showing" : "Hiding") + " " + finalCount + " camera(s)"
                                    ), false);
                                    return finalCount;
                                })
                        )
                )
        );
    }

    // ---------------------------------------------------------------------
    // Lookup helpers
    // ---------------------------------------------------------------------

    /** Display name of a camera: synched CamTag, falling back to legacy scoreboard tags. */
    public static String nameOf(CameraAnchorEntity anchor) {
        String camTag = anchor.getCamTag();
        if (!camTag.isEmpty()) {
            return camTag;
        }
        for (String t : anchor.getTags()) {
            if (!LEGACY_MARKER_TAG.equals(t)) {
                return t;
            }
        }
        return "";
    }

    /** True if the camera answers to {@code tag} (CamTag or legacy scoreboard tag). */
    public static boolean matchesTag(CameraAnchorEntity anchor, String tag) {
        return tag.equals(anchor.getCamTag()) || anchor.getTags().contains(tag);
    }

    /** All cameras matching {@code tag}; {@link #ALL} (case-insensitive) matches every camera. */
    public static List<CameraAnchorEntity> findCameras(ServerLevel level, String tag) {
        boolean all = ALL.equalsIgnoreCase(tag);
        List<CameraAnchorEntity> out = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof CameraAnchorEntity anchor && (all || matchesTag(anchor, tag))) {
                out.add(anchor);
            }
        }
        return out;
    }

    @Nullable
    public static CameraAnchorEntity findCamera(ServerLevel level, String tag) {
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof CameraAnchorEntity anchor && matchesTag(anchor, tag)) {
                return anchor;
            }
        }
        return null;
    }

    private static List<String> cameraNames(ServerLevel level) {
        Set<String> names = new LinkedHashSet<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof CameraAnchorEntity anchor) {
                String name = nameOf(anchor);
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static int usage(CommandSourceStack source, String problem, String usage) {
        source.sendFailure(Component.literal(problem));
        source.sendFailure(Component.literal("Usage: " + usage).withStyle(ChatFormatting.GRAY));
        return 0;
    }

    private static int noMatch(CommandSourceStack source, String tag) {
        source.sendFailure(Component.literal("No camera found with tag '" + tag + "'")
                .withStyle(ChatFormatting.RED));
        return 0;
    }

    // ---------------------------------------------------------------------
    // Subcommands
    // ---------------------------------------------------------------------

    private static int addCamera(CommandSourceStack source, String tag, @Nullable String pos)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        if (ALL.equalsIgnoreCase(tag)) {
            source.sendFailure(Component.literal("'" + ALL + "' is reserved and cannot be used as a camera tag")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (findCamera(level, tag) != null) {
            source.sendFailure(Component.literal("A camera with tag '" + tag + "' already exists")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (pos != null && !GRID_POSITIONS.contains(pos.toLowerCase())) {
            source.sendFailure(Component.literal("Unknown grid position '" + pos + "'. Valid: "
                    + String.join(", ", GRID_POSITIONS)).withStyle(ChatFormatting.RED));
            return 0;
        }

        // Validate before spawning: an unattached camera is useless and fails later.
        SubLevel subLevel = Sable.HELPER.getTrackingSubLevel(player);
        if (subLevel == null) {
            source.sendFailure(Component.literal(
                    "You must be standing on an assembled Sable sub-level to add a camera")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        double x, y, z;

        if (pos == null) {
            // Exact player position (where they are standing)
            x = player.getX();
            y = player.getY();
            z = player.getZ();
        } else {
            // Grid placement on the block under the player
            BlockPos feet = player.blockPosition().below();
            Vec3 offset = getPlacementOffset(pos);
            x = feet.getX() + offset.x;
            y = feet.getY() + offset.y;
            z = feet.getZ() + offset.z;
        }

        CameraAnchorEntity anchor = new CameraAnchorEntity(ModEntities.CAMERA_ANCHOR.get(), level);
        anchor.setPos(x, y, z);
        anchor.addTag(tag);
        anchor.setCamTag(tag);
        anchor.attachToSubLevel(subLevel);
        level.addFreshEntity(anchor);

        String where = (pos == null) ? "player position" : pos;
        source.sendSuccess(() -> Component.literal(
                "Added camera '" + tag + "' at " + where
        ), false);
        return 1;
    }

    private static int deleteCameras(CommandSourceStack source, String tag) {
        List<CameraAnchorEntity> cams = findCameras(source.getLevel(), tag);
        if (cams.isEmpty()) {
            return noMatch(source, tag);
        }
        for (CameraAnchorEntity anchor : cams) {
            anchor.discard();
        }

        int finalCount = cams.size();
        source.sendSuccess(() ->
                Component.literal("Deleted " + finalCount + " camera(s)"), false);
        return finalCount;
    }

    private static int setAngle(CommandSourceStack source,
                                float pitch, float yaw, float roll,
                                @Nullable Float fov,
                                String tag) {
        List<CameraAnchorEntity> cams = findCameras(source.getLevel(), tag);
        if (cams.isEmpty()) {
            return noMatch(source, tag);
        }
        for (CameraAnchorEntity anchor : cams) {
            anchor.setLocalPose(pitch, yaw, roll);
            if (fov != null) {
                anchor.setFov(fov);
            }
        }

        String msg = fov == null
                ? String.format("Set angle pitch=%.1f yaw=%.1f roll=%.1f on %d camera(s)",
                        pitch, yaw, roll, cams.size())
                : String.format("Set angle pitch=%.1f yaw=%.1f roll=%.1f fov=%.1f on %d camera(s)",
                        pitch, yaw, roll, fov, cams.size());
        refreshViewers(source, cams);
        source.sendSuccess(() -> Component.literal(msg), false);
        return cams.size();
    }

    private static int setFov(CommandSourceStack source, float fov, String tag) {
        List<CameraAnchorEntity> cams = findCameras(source.getLevel(), tag);
        if (cams.isEmpty()) {
            return noMatch(source, tag);
        }
        for (CameraAnchorEntity anchor : cams) {
            anchor.setFov(fov);
        }

        refreshViewers(source, cams);
        String msg = String.format("Set fov=%.1f on %d camera(s)", fov, cams.size());
        source.sendSuccess(() -> Component.literal(msg), false);
        return cams.size();
    }

    private static int setOffset(CommandSourceStack source, double x, double y, double z, String tag) {
        List<CameraAnchorEntity> cams = findCameras(source.getLevel(), tag);
        if (cams.isEmpty()) {
            return noMatch(source, tag);
        }
        for (CameraAnchorEntity anchor : cams) {
            anchor.setOffset((float) x, (float) y, (float) z);
        }

        refreshViewers(source, cams);
        final int finalCount = cams.size();
        source.sendSuccess(() -> Component.literal(
                "Set offset to " + x + ", " + y + ", " + z + " on " + finalCount + " camera(s)"
        ), false);
        return finalCount;
    }

    /**
     * Re-sends the follow payload to anyone currently viewing one of {@code cams}, so the
     * client's captured fallback (used when the entity isn't loaded client-side) stays current.
     */
    private static void refreshViewers(CommandSourceStack source, List<CameraAnchorEntity> cams) {
        if (FOLLOW_TARGETS.isEmpty()) return;
        for (CameraAnchorEntity anchor : cams) {
            for (var entry : FOLLOW_TARGETS.entrySet()) {
                if (!entry.getValue().equals(anchor.getUUID())) continue;
                ServerPlayer viewer = source.getServer().getPlayerList().getPlayer(entry.getKey());
                if (viewer != null) {
                    PacketDistributor.sendToPlayer(viewer, FollowCameraPayload.of(anchor));
                }
            }
        }
    }

    private static int viewCamera(CommandSourceStack source, String tag) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        CameraAnchorEntity target = findCamera(level, tag);
        if (target == null) {
            return noMatch(source, tag);
        }

        UUID subId = target.getAttachedSubLevelId();
        if (subId == null) {
            source.sendFailure(Component.literal("Camera '" + tag + "' is not attached to a sub-level")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Non-spectator view: gamemode and position are untouched, the client only moves
        // the render camera. Player keeps moving / interacting as usual.
        FOLLOW_TARGETS.put(player.getUUID(), target.getUUID());
        PacketDistributor.sendToPlayer(player, FollowCameraPayload.of(target));

        source.sendSuccess(() -> Component.literal(
                "Viewing camera: " + tag + " (Exit Camera key or /camanchor exit to stop)"), false);

        if (!isSubLevelLoaded(level, subId)) {
            source.sendSuccess(() -> Component.literal(
                    "Warning: the ship carrying '" + tag + "' isn't loaded on the server. "
                            + "The view will stay on you until it loads (forceload the ship's area).")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int exitView(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ViewSessionHandler.endView(player, true)) {
            source.sendFailure(Component.literal("You are not viewing a camera"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Stopped viewing camera"), false);
        return 1;
    }

    private static boolean isSubLevelLoaded(ServerLevel level, UUID subId) {
        try {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            return container != null && container.getSubLevel(subId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(ADMIN_LEVEL);
    }

    private static FloatArgumentType fovArg() {
        return FloatArgumentType.floatArg(CameraAnchorEntity.MIN_FOV, CameraAnchorEntity.MAX_FOV);
    }

    /** player UUID → camera entity UUID */
    public static final Map<UUID, UUID> FOLLOW_TARGETS = new HashMap<>();

    static Map<UUID, UUID> getFollowTargets() {
        return FOLLOW_TARGETS;
    }

    private static Vec3 getPlacementOffset(String pos) {
        double x, z;
        switch (pos.toLowerCase()) {
            case "topleft"      -> { x = 0.25; z = 0.25; }
            case "topcenter"    -> { x = 0.50; z = 0.25; }
            case "topright"     -> { x = 0.75; z = 0.25; }
            case "centerleft"   -> { x = 0.25; z = 0.50; }
            case "center"       -> { x = 0.50; z = 0.50; }
            case "centerright"  -> { x = 0.75; z = 0.50; }
            case "bottomleft"   -> { x = 0.25; z = 0.75; }
            case "bottomcenter" -> { x = 0.50; z = 0.75; }
            case "bottomright"  -> { x = 0.75; z = 0.75; }
            default             -> { x = 0.50; z = 0.50; }
        }
        return new Vec3(x, 1.0, z);
    }

}
