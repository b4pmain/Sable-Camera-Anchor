package dev.bapmain.sablecamera.client;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.bapmain.sablecamera.math.CameraMath;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3d;

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
        CameraFollowClient.clear();
        CameraPoseClient.clear();
        CameraOrientState.clear();
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
            ReplayFollowCache.clear();
        }
    }
}
