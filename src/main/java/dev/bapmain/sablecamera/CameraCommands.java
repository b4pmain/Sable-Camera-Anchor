package dev.bapmain.sablecamera;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.bapmain.sablecamera.entity.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import dev.ryanhcode.sable.Sable;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.GameType;

public class CameraCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("camanchor")
                .requires(s -> s.hasPermission(2))

                .then(Commands.literal("addcam")
                        .then(Commands.argument("tag", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String tag = StringArgumentType.getString(ctx, "tag");

                                    // ===== DEBUG START =====
                                    System.out.println("[SableCamera] Spawning camera...");
                                    System.out.println("[SableCamera] Player position: " + player.position());

                                    var tracking = Sable.HELPER.getTrackingSubLevel(player);
                                    System.out.println("[SableCamera] getTrackingSubLevel(player) → " + tracking);

                                    if (tracking != null) {
                                        System.out.println("[SableCamera] SubLevel UUID = " + tracking.getUniqueId());
                                    } else {
                                        System.out.println("[SableCamera] FAIL: player is not tracking any sub-level");
                                    }
                                    // ===== DEBUG END =====

                                    // Create the entity (your existing code)
                                    CameraAnchorEntity anchor = new CameraAnchorEntity(ModEntities.CAMERA_ANCHOR.get(), player.level());
                                    anchor.setPos(player.getX(), player.getY(), player.getZ());
                                    anchor.addTag(tag);

                                    // Try to attach
                                    anchor.tryAttachToPlayerTracking(player);

                                    System.out.println("[SableCamera] After attach: attachedSubLevelId = " + anchor.getAttachedSubLevelId());

                                    player.level().addFreshEntity(anchor);

                                    ctx.getSource().sendSuccess(() -> Component.literal("Spawned camera with tag: " + tag), true);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("delcam")
                        .executes(ctx -> kill(ctx.getSource(), null))
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .executes(ctx -> kill(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "tag")))))

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
        );
    }

    private static int spawn(CommandSourceStack source, String tag) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        CameraAnchorEntity anchor = ModEntities.CAMERA_ANCHOR.get().create(level);
        if (anchor == null) return 0;

        anchor.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, source.getRotation().x);
        anchor.addTag(tag);
        anchor.addTag("sable_camera");

        // Try to attach to the sub-level the command source is currently on
        if (source.getEntity() != null) {
            anchor.tryAttachToPlayerTracking(source.getEntity());
        }

        level.addFreshEntity(anchor);
        source.sendSuccess(() -> Component.literal("Spawned camera anchor with tag: " + tag), true);
        return 1;
    }

    private static int kill(CommandSourceStack source, String tag) {
        ServerLevel level = source.getLevel();
        int count = 0;

        for (Entity e : level.getAllEntities()) {
            if (e instanceof CameraAnchorEntity) {
                if (tag == null || e.getTags().contains(tag)) {
                    e.discard();
                    count++;
                }
            }
        }

        int finalCount = count;
        source.sendSuccess(() -> Component.literal("Removed " + finalCount + " camera anchor(s)"), true);
        return count;
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
        source.sendSuccess(() -> Component.literal(msg), true);
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
        ), true);
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
            source.sendFailure(Component.literal("No camera found with tag: " + tag));
            return 0;
        }

        // Switch to spectator only if needed
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }

        // Spectate the camera
        player.setCamera(target);

        source.sendSuccess(() -> Component.literal("Now viewing camera: " + tag), true);
        return 1;
    }
}