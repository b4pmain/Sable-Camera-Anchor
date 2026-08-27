package dev.bapmain.sablecamera.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = "sablecamera", value = Dist.CLIENT)
public class ClientCameraCommands {

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("camreplay")
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            if (CameraCatalog.all().isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("No remembered cameras"));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("Cameras: " + String.join(", ", CameraCatalog.all().keySet())), false);
                            return 1;
                        }))
                .then(Commands.literal("view")
                        .then(Commands.argument("tag", StringArgumentType.string())
                                .executes(ctx -> {
                                    String tag = StringArgumentType.getString(ctx, "tag");
                                    var e = CameraCatalog.get(tag);
                                    if (e == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown camera: " + tag));
                                        return 0;
                                    }
                                    ReplayFollowCache.apply(
                                            e.subId(), e.lx(), e.ly(), e.lz(),
                                            e.ox(), e.oy(), e.oz(),
                                            e.pitch(), e.yaw(), e.roll());
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Replay view: " + tag + " (Shift to exit)"), false);
                                    return 1;
                                })))
                .then(Commands.literal("rename")
                        .then(Commands.argument("from", StringArgumentType.string())
                                .then(Commands.argument("to", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String from = StringArgumentType.getString(ctx, "from");
                                            String to = StringArgumentType.getString(ctx, "to");
                                            if (!CameraCatalog.rename(from, to)) {
                                                ctx.getSource().sendFailure(Component.literal("No camera named '" + from + "'"));
                                                return 0;
                                            }
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Renamed " + from + " → " + to), false);
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }
}