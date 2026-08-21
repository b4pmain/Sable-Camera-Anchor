package dev.bapmain.sablecamera;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.bapmain.sablecamera.entity.ModEntities;
import dev.bapmain.sablecamera.network.FollowCameraPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.GameType;

public class CameraCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("camanchor")
                .requires(s -> s.hasPermission(2))

                .then(Commands.literal("addcam")
                        .then(Commands.argument("tag", StringArgumentType.string())
                                // no pos → player position
                                .executes(ctx -> addCamera(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "tag"),
                                        null))
                                // with pos → grid on block under player
                                .then(Commands.argument("pos", StringArgumentType.string())
                                        .executes(ctx -> addCamera(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "tag"),
                                                StringArgumentType.getString(ctx, "pos")))
                                )
                        )
                )

                .then(Commands.literal("delcam")
                        // no args → error
                        .executes(ctx -> {
                            ctx.getSource().sendFailure(
                                    Component.literal("Input a tag name first (use \"all\" to delete all)")
                                            .withStyle(ChatFormatting.RED));
                            return 0;
                        })
                        .then(Commands.argument("tag", StringArgumentType.string())
                                .executes(ctx -> {
                                    String tag = StringArgumentType.getString(ctx, "tag");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    int count = 0;

                                    for (Entity entity : level.getEntities().getAll()) {
                                        if (entity instanceof CameraAnchorEntity anchor) {
                                            if (tag.equalsIgnoreCase("all") || anchor.getTags().contains(tag)) {
                                                anchor.discard();
                                                count++;
                                            }
                                        }
                                    }

                                    if (count == 0) {
                                        ctx.getSource().sendFailure(Component.literal("No cameras matched '" + tag + "'")
                                                .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }

                                    int finalCount = count;
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Deleted " + finalCount + " camera(s)"), false);
                                    return finalCount;
                                })
                        )
                )

                .then(Commands.literal("angle")
                        .then(Commands.argument("pitch", FloatArgumentType.floatArg())
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                        .executes(ctx -> setAngle(ctx,
                                                FloatArgumentType.getFloat(ctx, "pitch"),
                                                FloatArgumentType.getFloat(ctx, "yaw"), 0.0,
                                                null))
                                        .then(Commands.argument("roll", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> setAngle(ctx,
                                                        DoubleArgumentType.getDouble(ctx, "pitch"),
                                                        DoubleArgumentType.getDouble(ctx, "yaw"),
                                                        DoubleArgumentType.getDouble(ctx, "roll"),
                                                        null))
                                        .then(Commands.argument("tag", StringArgumentType.word())
                                                .executes(ctx -> setAngle(ctx,
                                                        FloatArgumentType.getFloat(ctx, "pitch"),
                                                        FloatArgumentType.getFloat(ctx, "yaw"),
                                                        DoubleArgumentType.getDouble(ctx, "roll"),
                                                        StringArgumentType.getString(ctx, "tag"))))))))

                .then(Commands.literal("offset")
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> {
                                                    double x = DoubleArgumentType.getDouble(ctx, "x");
                                                    double y = DoubleArgumentType.getDouble(ctx, "y");
                                                    double z = DoubleArgumentType.getDouble(ctx, "z");
                                                    return setOffset(ctx.getSource(), x, y, z, null);
                                                })
                                                .then(Commands.argument("tag", StringArgumentType.string())
                                                        .executes(ctx -> {
                                                            double x = DoubleArgumentType.getDouble(ctx, "x");
                                                            double y = DoubleArgumentType.getDouble(ctx, "y");
                                                            double z = DoubleArgumentType.getDouble(ctx, "z");
                                                            String tag = StringArgumentType.getString(ctx, "tag");
                                                            return setOffset(ctx.getSource(), x, y, z, tag);
                                                        })
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("view")
                        .then(Commands.argument("tag", StringArgumentType.string())
                                .executes(ctx -> viewCamera(ctx.getSource(), StringArgumentType.getString(ctx, "tag")))
                        )
                )
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            java.util.Set<String> tags = new java.util.LinkedHashSet<>();

                            for (Entity entity : level.getEntities().getAll()) {
                                if (entity instanceof CameraAnchorEntity anchor) {
                                    for (String t : anchor.getTags()) {
                                        if (!t.equals("sable_camera")) {
                                            tags.add(t);
                                        }
                                    }
                                }
                            }

                            if (tags.isEmpty()) {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("No cameras found").withStyle(ChatFormatting.GRAY), false);
                            } else {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("Cameras: " + String.join(", ", tags))
                                                .withStyle(ChatFormatting.AQUA), false);
                            }
                            return tags.size();
                        })
                )
                .then(Commands.literal("show")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    int count = 0;

                                    for (Entity entity : level.getEntities().getAll()) {
                                        if (entity instanceof CameraAnchorEntity anchor) {
                                            anchor.setCameraVisible(value);
                                            count++;
                                        }
                                    }

                                    int finalCount = count;
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            (value ? "Showing" : "Hiding") + " " + finalCount + " camera(s)"
                                    ), false);
                                    return finalCount;
                                })
                        )
                )
        );
    }

    private static int addCamera(CommandSourceStack source, String tag, @Nullable String pos)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof CameraAnchorEntity anchor && anchor.getTags().contains(tag)) {
                source.sendFailure(Component.literal("A camera with tag '" + tag + "' already exists")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
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
        anchor.tryAttachToPlayerTracking(player);
        level.addFreshEntity(anchor);

        String where = (pos == null) ? "player position" : pos;
        source.sendSuccess(() -> Component.literal(
                "Added camera '" + tag + "' at " + where
        ), false);
        return 1;
    }

    private static int setAngle(CommandContext<CommandSourceStack> ctx,
                                double pitch, double yaw, double roll,
                                @Nullable String tag) {
        CommandSourceStack source = ctx.getSource();
        int count = 0;

        for (Entity entity : source.getLevel().getEntities().getAll()) {
            if (entity instanceof CameraAnchorEntity anchor) {
                if (tag == null || anchor.getTags().contains(tag)) {
                    anchor.setLocalPose((float) pitch, (float) yaw, (float) roll);
                    count++;
                }
            }
        }

        if (count == 0) {
            source.sendFailure(Component.literal("No matching cameras found"));
            return 0;
        }

        String msg = String.format("Set angle pitch=%.1f yaw=%.1f roll=%.1f on %d camera(s)",
                pitch, yaw, roll, count);
        source.sendSuccess(() -> Component.literal(msg), false);
        return count;
    }

    private static int setOffset(CommandSourceStack source, double x, double y, double z, @Nullable String tag) {
        int count = 0;

        for (Entity entity : source.getLevel().getEntities().getAll()) {
            if (entity instanceof CameraAnchorEntity anchor) {
                if (tag == null || anchor.getTags().contains(tag)) {
                    anchor.setOffset((float) x, (float) y, (float) z);
                    count++;
                }
            }
        }

        if (count == 0) {
            source.sendFailure(Component.literal("No matching camera anchors found"));
            return 0;
        }
        final int finalCount = count;

        source.sendSuccess(() -> Component.literal(
                "Set offset to " + x + ", " + y + ", " + z + " on " + finalCount + " camera(s)"
        ), false);
        return count;
    }

    private static int viewCamera(CommandSourceStack source, String tag) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        CameraAnchorEntity target = null;
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof CameraAnchorEntity anchor && anchor.getTags().contains(tag)) {
                target = anchor;
                break;
            }
        }

        if (target == null) {
            source.sendFailure(Component.literal("No camera found with tag: " + tag)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (target.getAttachedSubLevelId() == null) {
            source.sendFailure(Component.literal("Camera is not attached to a sub-level")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // first view in sesh only return
        if (!VIEW_SESSIONS.containsKey(player.getUUID())) {
            VIEW_SESSIONS.put(player.getUUID(), new ViewState(player));
        }

        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }

        // no more player.setCamera

        PacketDistributor.sendToPlayer(player, new FollowCameraPayload(target.getUUID()));

        source.sendSuccess(() -> Component.literal(
                "Viewing camera: " + tag + " (crouch to exit)"), false);
        return 1;
    }

    private static final java.util.Map<java.util.UUID, ViewState> VIEW_SESSIONS = new java.util.HashMap<>();

    protected static class ViewState {
        final GameType gameMode;
        final double x, y, z;
        final float yRot, xRot;

        ViewState(ServerPlayer player) {
            this.gameMode = player.gameMode.getGameModeForPlayer();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.yRot = player.getYRot();
            this.xRot = player.getXRot();
        }
    }

    static java.util.Map<java.util.UUID, ViewState> getViewSessions() {
        return VIEW_SESSIONS;
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