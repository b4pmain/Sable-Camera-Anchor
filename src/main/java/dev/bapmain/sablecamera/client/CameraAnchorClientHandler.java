package dev.bapmain.sablecamera.client;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.bapmain.sablecamera.math.CameraMath;
import dev.bapmain.sablecamera.network.CycleCameraPayload;
import dev.bapmain.sablecamera.network.ExitViewPayload;
import dev.bapmain.sablecamera.network.SaveCameraAimPayload;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "sablecamera", value = Dist.CLIENT)
public class CameraAnchorClientHandler {

    // Orientation override for spectated anchors used to live here (ComputeCameraAngles).
    // It was redundant: that event fires inside Camera#setup, before CameraMixin's TAIL
    // injection, which fully overrides the pose anyway.

    public static void snapToRenderPose(CameraAnchorEntity anchor) {
        UUID subId = anchor.getAttachedSubLevelId();
        if (subId == null) return;

        ClientSubLevel sub = findClientSubLevel(subId);
        if (sub == null) return;

        var pose = sub.renderPose();
        if (pose == null) return;

        Vector3d worldPos = CameraMath.worldPosition(pose,
                anchor.getLocalX(), anchor.getLocalY(), anchor.getLocalZ(),
                anchor.getOffsetX(), anchor.getOffsetY(), anchor.getOffsetZ());

        if (CameraMath.isSane(worldPos)) {
            anchor.setPos(worldPos.x, worldPos.y, worldPos.z);
        }
    }

    /**
     * Live view state is static client-side. Without this, leaving mid-view and rejoining
     * kept the camera override active (player back in survival but rendering from the camera).
     * ReplayFollowCache is intentionally kept: it must survive ReplayMod timeline jumps.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetLiveView();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        resetLiveView();
    }

    public static void resetLiveView() {
        CameraAimClient.reset();
        CameraFollowClient.clear();
        CameraPoseClient.clear();
        CameraOrientState.clear();
        ActiveView.beginFrame();
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!CameraAimClient.isUnlocked() || !ActiveView.isOverriding()) {
            return;
        }
        double deltaY = event.getScrollDeltaY();
        if (deltaY != 0.0) {
            // Scroll up (+deltaY) zooms in (-FOV); scroll down (-deltaY) zooms out (+FOV)
            CameraAimClient.adjustFov((float) -deltaY);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(
                        String.format("Camera FOV: %.0f", CameraAimClient.fov()))
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ActiveView.isOverriding() && event.usedConfiguredFov()) {
            event.setFOV(ActiveView.fov());
        }
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (ActiveView.isLiveOverride() && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        if (!ActiveView.isOverriding() || !VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        boolean replay = ReplayCompat.isInReplay();
        String camName = replay ? ReplayFollowCache.activeTag() : CameraFollowClient.tag();
        if (camName.isEmpty() && !replay && CameraFollowClient.getFollow() != null) {
            camName = CameraCatalog.nameOf(CameraFollowClient.getFollow());
        }
        if (camName.isEmpty()) {
            camName = "camera";
        }

        boolean unlocked = CameraAimClient.isUnlocked();
        String status = unlocked ? "UNLOCKED" : "LOCKED";
        int statusColor = unlocked ? 0xFFFF55 : 0x55FF55;
        String text = String.format("CAM: %s  |  FOV: %.0f°  |  ", camName, ActiveView.fov());

        var font = mc.font;
        var gfx = event.getGuiGraphics();
        int x = 8;
        int y = 8;
        gfx.drawString(font, text, x, y, 0xFFFFFF, true);
        gfx.drawString(font, status, x + font.width(text), y, statusColor, true);
    }

    public static ClientSubLevel findClientSubLevel(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (!(container instanceof ClientSubLevelContainer clientContainer)) {
            return null;
        }
        for (ClientSubLevel sub : clientContainer.getAllSubLevels()) {
            if (id.equals(sub.getUniqueId())) {
                return sub;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof CameraAnchorEntity a) {
            // Synched data (sub-level, CamTag) may not have arrived yet; the periodic
            // sweep in onClientTick picks it up / upgrades the name once it does.
            CameraCatalog.remember(a);
        }
    }

    private static int catalogSweep = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.level != null && (++catalogSweep % 10) == 0) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof CameraAnchorEntity a) {
                    CameraCatalog.remember(a);
                }
            }
        }

        if (ReplayCompat.isInReplay()
                && ReplayFollowCache.isActive()
                && mc.options.keyShift.isDown()) {
            CameraAimClient.reset();
            ReplayFollowCache.clear();
        }

        while (ClientSetup.EXIT_VIEW_KEY.consumeClick()) {
            if (ReplayCompat.isInReplay() && ReplayFollowCache.isActive()) {
                CameraAimClient.reset();
                ReplayFollowCache.clear();
                mc.player.displayClientMessage(Component.literal("Exited replay camera view"), true);
            } else if (CameraFollowClient.isViewing() || CameraPoseClient.isActive()) {
                resetLiveView();
                PacketDistributor.sendToServer(ExitViewPayload.INSTANCE);
                mc.player.displayClientMessage(Component.literal("Exited camera view"), true);
            }
        }

        while (ClientSetup.TOGGLE_LOCK_KEY.consumeClick()) {
            if (ActiveView.isOverriding()) {
                toggleAimLock(mc);
            }
        }

        int fovStep = 0;
        while (ClientSetup.FOV_DEC_KEY.consumeClick()) {
            fovStep--;
        }
        while (ClientSetup.FOV_INC_KEY.consumeClick()) {
            fovStep++;
        }
        if (fovStep != 0 && CameraAimClient.isUnlocked() && ActiveView.isOverriding()) {
            CameraAimClient.adjustFov(fovStep);
            mc.player.displayClientMessage(Component.literal(
                    String.format("Camera FOV: %.0f", CameraAimClient.fov()))
                    .withStyle(ChatFormatting.YELLOW), true);
        }

        int cycleStep = 0;
        while (ClientSetup.PREV_CAMERA_KEY.consumeClick()) {
            cycleStep--;
        }
        while (ClientSetup.NEXT_CAMERA_KEY.consumeClick()) {
            cycleStep++;
        }

        if (cycleStep != 0) {
            if (ReplayCompat.isInReplay() && ReplayFollowCache.isActive()) {
                CameraAimClient.reset();
                cycleReplayCamera(mc, cycleStep);
            } else if (!ReplayCompat.isInReplay() && (CameraFollowClient.isViewing() || CameraPoseClient.isActive())) {
                CameraAimClient.reset();
                PacketDistributor.sendToServer(new CycleCameraPayload(cycleStep));
            }
        }
    }

    private static void toggleAimLock(Minecraft mc) {
        boolean replay = ReplayCompat.isInReplay();
        if (!CameraAimClient.isUnlocked()) {
            float p, y, r, f;
            if (replay && ReplayFollowCache.isActive()) {
                p = ReplayFollowCache.pitch();
                y = ReplayFollowCache.yaw();
                r = ReplayFollowCache.roll();
                f = ReplayFollowCache.fov();
            } else {
                p = CameraFollowClient.pitch();
                y = CameraFollowClient.yaw();
                r = CameraFollowClient.roll();
                f = CameraFollowClient.fov();
            }
            CameraAimClient.unlock(p, y, r, f);
            mc.player.displayClientMessage(Component.literal(
                    "Camera aim UNLOCKED (mouse to aim, scroll/-/+ for FOV, L to lock & save)")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else {
            float p = CameraAimClient.pitch();
            float y = CameraAimClient.yaw();
            float r = CameraAimClient.roll();
            float f = CameraAimClient.fov();
            CameraAimClient.lock();

            if (replay && ReplayFollowCache.isActive()) {
                ReplayFollowCache.updateAnglesAndFov(p, y, r, f);
                String tag = ReplayFollowCache.activeTag();
                if (!tag.isEmpty()) {
                    CameraCatalog.updateAnglesAndFov(tag, p, y, r, f);
                }
            } else {
                CameraFollowClient.updateAnglesAndFov(p, y, r, f);
                UUID id = CameraFollowClient.getFollow();
                if (id != null && mc.level != null) {
                    for (Entity e : mc.level.entitiesForRendering()) {
                        if (e instanceof CameraAnchorEntity a && id.equals(a.getUUID())) {
                            a.setLocalPose(p, y, r);
                            a.setFov(f);
                            CameraCatalog.remember(a);
                            break;
                        }
                    }
                }
                PacketDistributor.sendToServer(new SaveCameraAimPayload(p, y, r, f));
            }
            mc.player.displayClientMessage(Component.literal(
                    String.format("Camera aim LOCKED & saved (pitch=%.1f yaw=%.1f fov=%.0f)", p, y, f))
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    private static void cycleReplayCamera(Minecraft mc, int step) {
        List<String> tags = new ArrayList<>(CameraCatalog.all().keySet());
        if (tags.isEmpty()) {
            mc.player.displayClientMessage(Component.literal("No remembered cameras").withStyle(ChatFormatting.RED), true);
            return;
        }
        int currentIndex = tags.indexOf(ReplayFollowCache.activeTag());
        int n = tags.size();
        int nextIndex = currentIndex < 0 ? 0 : Math.floorMod(currentIndex + step, n);
        String nextTag = tags.get(nextIndex);
        CameraCatalog.Entry e = CameraCatalog.get(nextTag);
        if (e == null) return;

        CameraOrientState.clear();
        ReplayFollowCache.apply(
                nextTag,
                e.subId(), e.lx(), e.ly(), e.lz(),
                e.ox(), e.oy(), e.oz(),
                e.pitch(), e.yaw(), e.roll(), e.fov());
        mc.player.displayClientMessage(Component.literal("Camera: " + nextTag + " (" + (nextIndex + 1) + "/" + n + ")")
                .withStyle(ChatFormatting.AQUA), true);
    }
}
