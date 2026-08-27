package dev.bapmain.sablecamera.entity;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.UUID;

public class CameraAnchorEntity extends Entity {

    private static final EntityDataAccessor<String> DATA_SUBLEVEL_ID =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_LOCAL_X =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_Y =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_Z =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OFFSET_X =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OFFSET_Y =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OFFSET_Z =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_PITCH =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_YAW =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_ROLL =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_VISIBLE =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_CAM_TAG =
            SynchedEntityData.defineId(CameraAnchorEntity.class, EntityDataSerializers.STRING);

    private int snapCooldown = 0;
    private net.minecraft.world.level.ChunkPos ticketChunk;
    public static final java.util.Set<CameraAnchorEntity> LIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public CameraAnchorEntity(EntityType<? extends CameraAnchorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SUBLEVEL_ID, "");
        builder.define(DATA_LOCAL_X, 0.0f);
        builder.define(DATA_LOCAL_Y, 0.0f);
        builder.define(DATA_LOCAL_Z, 0.0f);
        builder.define(DATA_OFFSET_X, 0.0f);
        builder.define(DATA_OFFSET_Y, 0.0f);
        builder.define(DATA_OFFSET_Z, 0.0f);
        builder.define(DATA_LOCAL_PITCH, 0.0f);
        builder.define(DATA_LOCAL_YAW, 0.0f);
        builder.define(DATA_LOCAL_ROLL, 0.0f);
        builder.define(DATA_VISIBLE, false);
        builder.define(DATA_CAM_TAG, "");
    }

    // Public getters (used by the client mixin)

    @Nullable
    public UUID getAttachedSubLevelId() {
        String id = this.entityData.get(DATA_SUBLEVEL_ID);
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public float getLocalX() {
        return this.entityData.get(DATA_LOCAL_X);
    }

    public float getLocalY() {
        return this.entityData.get(DATA_LOCAL_Y);
    }

    public float getLocalZ() {
        return this.entityData.get(DATA_LOCAL_Z);
    }

    public float getLocalPitch() {
        return this.entityData.get(DATA_LOCAL_PITCH);
    }

    public float getLocalYaw() {
        return this.entityData.get(DATA_LOCAL_YAW);
    }

    public float getLocalRoll() {
        return this.entityData.get(DATA_LOCAL_ROLL);
    }

    public float getOffsetX() {
        return this.entityData.get(DATA_OFFSET_X);
    }
    public float getOffsetY() {
        return this.entityData.get(DATA_OFFSET_Y);
    }
    public float getOffsetZ() {
        return this.entityData.get(DATA_OFFSET_Z);
    }

    public String getCamTag() {
        String t = this.entityData.get(DATA_CAM_TAG);
        return t == null ? "" : t;
    }

    public void setCamTag(String tag) {
        this.entityData.set(DATA_CAM_TAG, tag == null ? "" : tag);
    }

    public void setLocalPose(float pitch, float yaw, float roll) {
        this.entityData.set(DATA_LOCAL_PITCH, pitch);
        this.entityData.set(DATA_LOCAL_YAW, yaw);
        this.entityData.set(DATA_LOCAL_ROLL, roll);
    }

    public void setOffset(float x, float y, float z) {
        this.entityData.set(DATA_OFFSET_X, x);
        this.entityData.set(DATA_OFFSET_Y, y);
        this.entityData.set(DATA_OFFSET_Z, z);
    }

    // Attachment

    public void tryAttachToPlayerTracking(Entity player) {
        SubLevel subLevel = Sable.HELPER.getTrackingSubLevel(player);
        if (subLevel != null) {
            attachToSubLevel(subLevel);
        }
    }

    public void attachToSubLevel(SubLevel subLevel) {
        this.entityData.set(DATA_SUBLEVEL_ID, subLevel.getUniqueId().toString());

        Vec3 worldPos = this.position();
        Vec3 localPos = subLevel.logicalPose().transformPositionInverse(worldPos);

        var rp = subLevel.logicalPose().rotationPoint();
        this.entityData.set(DATA_LOCAL_X, (float) (localPos.x - rp.x()));
        this.entityData.set(DATA_LOCAL_Y, (float) (localPos.y - rp.y()));
        this.entityData.set(DATA_LOCAL_Z, (float) (localPos.z - rp.z()));

        this.setLocalPose(this.getXRot(), this.getYRot(), this.getLocalRoll());
    }

    @Override
    public void remove(RemovalReason reason) {
        dropChunkTicket();
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(0, 0, 0);
        // cameramixin
        if (this.level().isClientSide) {
            dev.bapmain.sablecamera.client.CameraAnchorClientHandler.snapToRenderPose(this);
            return;
        }

        if (--snapCooldown > 0) return;
        snapCooldown = 2; // ~10 Hz cheap track

        UUID subId = getAttachedSubLevelId();
        if (subId == null) return;

        SubLevel sub = findServerSubLevel(subId);
        if (sub == null) return;

        var pose = sub.logicalPose();
        var rp = pose.rotationPoint();

        Vector3d localPos = new Vector3d(
                rp.x() + getLocalX() + getOffsetX(),
                rp.y() + getLocalY() + getOffsetY(),
                rp.z() + getLocalZ() + getOffsetZ()
        );
        pose.transformPosition(localPos);

        if (Math.abs(localPos.x) > 1.0e6
                || Math.abs(localPos.y) > 1.0e6
                || Math.abs(localPos.z) > 1.0e6) {
            return;
        }

        double dx = localPos.x - this.getX();
        double dy = localPos.y - this.getY();
        double dz = localPos.z - this.getZ();
        if (dx * dx + dy * dy + dz * dz < 0.01) {
            refreshChunkTicket();
            return;
        }

        this.setPos(localPos.x, localPos.y, localPos.z);
        this.xo = localPos.x;
        this.yo = localPos.y;
        this.zo = localPos.z;
        this.xOld = localPos.x;
        this.yOld = localPos.y;
        this.zOld = localPos.z;

        refreshChunkTicket();
    }

    private void refreshChunkTicket() {
        if (this.level().isClientSide) return;
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;

        var chunk = new net.minecraft.world.level.ChunkPos(this.blockPosition());

        if (ticketChunk != null && ticketChunk.equals(chunk)) {
            return;
        }

        if (ticketChunk != null) {
            sl.getChunkSource().removeRegionTicket(
                    net.minecraft.server.level.TicketType.FORCED,
                    ticketChunk, 1, ticketChunk);
        }

        sl.getChunkSource().addRegionTicket(
                net.minecraft.server.level.TicketType.FORCED,
                chunk, 1, chunk);
        ticketChunk = chunk;
    }

    private void dropChunkTicket() {
        if (this.level().isClientSide) return;
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (ticketChunk == null) return;

        sl.getChunkSource().removeRegionTicket(
                net.minecraft.server.level.TicketType.FORCED,
                ticketChunk, 1, ticketChunk);
        ticketChunk = null;
    }

    @Nullable
    private SubLevel findServerSubLevel(UUID id) {
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(this.level());
            if (container == null) {
                return null;
            }

            return container.getSubLevel(id);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("SubLevelId")) {
            this.entityData.set(DATA_SUBLEVEL_ID, tag.getString("SubLevelId"));
        }
        if (tag.contains("LocalX")) {
            this.entityData.set(DATA_LOCAL_X, tag.getFloat("LocalX"));
            this.entityData.set(DATA_LOCAL_Y, tag.getFloat("LocalY"));
            this.entityData.set(DATA_LOCAL_Z, tag.getFloat("LocalZ"));
        }
        if (tag.contains("OffsetX")) {
            setOffset(
                    tag.getFloat("OffsetX"),
                    tag.getFloat("OffsetY"),
                    tag.getFloat("OffsetZ")
            );
        }
        if (tag.contains("LocalPitch")) {
            this.setLocalPose(tag.getFloat("LocalPitch"), tag.getFloat("LocalYaw"), tag.getFloat("LocalRoll"));
        }
        if (tag.contains("CamTag")) {
            setCamTag(tag.getString("CamTag"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        String id = this.entityData.get(DATA_SUBLEVEL_ID);
        if (id != null && !id.isEmpty()) {
            tag.putString("SubLevelId", id);
        }
        tag.putFloat("LocalX", getLocalX());
        tag.putFloat("LocalY", getLocalY());
        tag.putFloat("LocalZ", getLocalZ());
        tag.putFloat("OffsetX", getOffsetX());
        tag.putFloat("OffsetY", getOffsetY());
        tag.putFloat("OffsetZ", getOffsetZ());
        tag.putFloat("LocalPitch", getLocalPitch());
        tag.putFloat("LocalYaw", getLocalYaw());
        tag.putFloat("LocalRoll", getLocalRoll());
        if (!getCamTag().isEmpty()) {
            tag.putString("CamTag", getCamTag());
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) LIVE.add(this);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        LIVE.remove(this);
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return true;
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() { return true; }

    @Override
    public boolean isPickable() {
        return true; // so it can be spectated in replay mod
    }

    @Override
    public boolean isInvisible() {
        return false; // replaymod canspectate
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    public boolean isCameraVisible() {
        return this.entityData.get(DATA_VISIBLE);
    }

    public void setCameraVisible(boolean visible) {
        this.entityData.set(DATA_VISIBLE, visible);
    }
}