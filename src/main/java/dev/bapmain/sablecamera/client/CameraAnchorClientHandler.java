package dev.bapmain.sablecamera.client;

import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3d;

import java.util.UUID;

@EventBusSubscriber(modid = "sablecamera", value = Dist.CLIENT)
public class CameraAnchorClientHandler {

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        Entity cameraEntity = mc.getCameraEntity();

        if (!(cameraEntity instanceof CameraAnchorEntity anchor)) {
            return;
        }

        // Correct client-side lookup by position
        var clientSub = Sable.HELPER.getContainingClient(anchor.position());
        if (clientSub == null) {
            return;
        }

        // Get the smooth render pose
        Pose3dc pose;
        try {
            pose = clientSub.renderPose();
        } catch (Exception e) {
            return;
        }

        if (pose == null) {
            return;
        }

        // ===== Orientation =====
        float yawRad = anchor.getLocalYaw() * ((float) Math.PI / 180.0f);
        float pitchRad = anchor.getLocalPitch() * ((float) Math.PI / 180.0f);

        // Local look vector (Minecraft convention)
        double lx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double ly = -Math.sin(pitchRad);
        double lz =  Math.cos(yawRad) * Math.cos(pitchRad);

        Vector3d localLook = new Vector3d(lx, ly, lz);

        // Transform into world space
        pose.transformNormal(localLook);

        double horizontal = Math.sqrt(localLook.x * localLook.x + localLook.z * localLook.z);

        float worldYaw = (float) (Mth.atan2(-localLook.x, localLook.z) * (180.0 / Math.PI));
        float worldPitch = (float) (Mth.atan2(-localLook.y, horizontal) * (180.0 / Math.PI));

        event.setYaw(worldYaw);
        event.setPitch(worldPitch);
    }

    public static void snapToRenderPose(CameraAnchorEntity anchor) {
        UUID subId = anchor.getAttachedSubLevelId();
        if (subId == null) return;

        ClientSubLevel sub = findClientSubLevel(subId);
        if (sub == null) return;

        var pose = sub.renderPose();
        if (pose == null) return;

        var rp = pose.rotationPoint();
        Vector3d localPos = new Vector3d(
                rp.x() + anchor.getLocalX() + anchor.getOffsetX(),
                rp.y() + anchor.getLocalY() + anchor.getOffsetY(),
                rp.z() + anchor.getLocalZ() + anchor.getOffsetZ()
        );
        pose.transformPosition(localPos);

        if (Math.abs(localPos.x) < 1.0e6
                && Math.abs(localPos.y) < 1.0e6
                && Math.abs(localPos.z) < 1.0e6) {
            anchor.setPos(localPos.x, localPos.y, localPos.z);
        }
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
}