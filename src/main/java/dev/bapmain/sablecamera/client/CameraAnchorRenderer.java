package dev.bapmain.sablecamera.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bapmain.sablecamera.entity.CameraAnchorEntity;
import dev.bapmain.sablecamera.math.CameraMath;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.UUID;

public class CameraAnchorRenderer extends EntityRenderer<CameraAnchorEntity> {

    private static final float BOX = 0.12f;
    private static final float CONE_LEN = 0.8f;
    private static final float CONE_RAD = 0.25f;
    private static final int CONE_SEGMENTS = 8;

    public CameraAnchorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(CameraAnchorEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        CameraCatalog.remember(entity);
        if (!entity.isCameraVisible()) {
            return;
        }

        // Default: entity position, local angles only
        double drawX = entity.getX();
        double drawY = entity.getY();
        double drawZ = entity.getZ();
        Quaterniond frameRot = CameraMath.localRotation(
                entity.getLocalPitch(), entity.getLocalYaw(), entity.getLocalRoll());

        // Apply sub-level transform if attached
        UUID subId = entity.getAttachedSubLevelId();
        if (subId != null) {
            ClientSubLevel clientSub = findClientSubLevel(subId);
            if (clientSub != null) {
                var pose = clientSub.renderPose();
                if (pose != null) {
                    Vector3d worldPos = CameraMath.worldPosition(pose,
                            entity.getLocalX(), entity.getLocalY(), entity.getLocalZ(),
                            entity.getOffsetX(), entity.getOffsetY(), entity.getOffsetZ());
                    drawX = worldPos.x;
                    drawY = worldPos.y;
                    drawZ = worldPos.z;

                    // Same orientation math as the camera mixin (camera frame: cone along +Z)
                    frameRot = CameraMath.worldRotation(pose.orientation(),
                            entity.getLocalPitch(), entity.getLocalYaw(), entity.getLocalRoll());
                }
            }
        }

        poseStack.pushPose();
        poseStack.translate(drawX - entity.getX(), drawY - entity.getY(), drawZ - entity.getZ());

        // Apply camera orientation directly as a quaternion (no Euler round trip)
        poseStack.mulPose(new Quaternionf().set(frameRot));

        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        Matrix4f mat = poseStack.last().pose();

        // Body box (cyan)
        AABB box = new AABB(-BOX, -BOX, -BOX, BOX, BOX, BOX);
        drawBox(vc, mat, box, 0.2f, 0.85f, 1.0f, 1.0f);

        // Look cone (yellow / orange) pointing along +Z in local space
        drawCone(vc, mat, CONE_LEN, CONE_RAD, CONE_SEGMENTS, 1.0f, 0.75f, 0.15f, 1.0f);

        // Center line through the cone
        line(vc, mat, 0, 0, 0, 0, 0, CONE_LEN, 1.0f, 1.0f, 0.3f, 1.0f);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, light);
    }

    private void drawCone(VertexConsumer vc, Matrix4f mat,
                          float length, float radius, int segments,
                          float r, float g, float b, float a) {
        // Apex at origin, base at +Z
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (2 * Math.PI * i / segments);
            float a1 = (float) (2 * Math.PI * (i + 1) / segments);

            float x0 = Mth.cos(a0) * radius;
            float y0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float y1 = Mth.sin(a1) * radius;

            // Side edges (apex → base ring)
            line(vc, mat, 0, 0, 0, x0, y0, length, r, g, b, a);
            // Base ring
            line(vc, mat, x0, y0, length, x1, y1, length, r, g, b, a);
        }
    }

    private void drawBox(VertexConsumer vc, Matrix4f mat, AABB b,
                         float r, float g, float bl, float a) {
        line(vc, mat, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, r, g, bl, a);
        line(vc, mat, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, r, g, bl, a);
        line(vc, mat, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, r, g, bl, a);
        line(vc, mat, b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ, r, g, bl, a);

        line(vc, mat, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, r, g, bl, a);
        line(vc, mat, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, r, g, bl, a);
        line(vc, mat, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, r, g, bl, a);
        line(vc, mat, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, r, g, bl, a);

        line(vc, mat, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, r, g, bl, a);
        line(vc, mat, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, r, g, bl, a);
        line(vc, mat, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, r, g, bl, a);
        line(vc, mat, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, r, g, bl, a);
    }

    private void line(VertexConsumer vc, Matrix4f mat,
                      double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      float r, float g, float b, float a) {
        vc.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(0, 1, 0);
        vc.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(0, 1, 0);
    }

    private static ClientSubLevel findClientSubLevel(UUID id) {
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

    @Override
    public ResourceLocation getTextureLocation(CameraAnchorEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    }
}
