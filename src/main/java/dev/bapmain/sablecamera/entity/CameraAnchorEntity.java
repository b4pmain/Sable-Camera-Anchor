package dev.bapmain.sablecamera.entity;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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

    public CameraAnchorEntity(EntityType<? extends CameraAnchorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setInvisible(true);
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
    }

    // ===== Public getters (used by the client mixin) =====

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

    public void setLocalPose(float pitch, float yaw, float roll) {
        this.entityData.set(DATA_LOCAL_PITCH, pitch);
        this.entityData.set(DATA_LOCAL_YAW, yaw);
        this.entityData.set(DATA_LOCAL_ROLL, roll);
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

    public void setOffset(float x, float y, float z) {
        this.entityData.set(DATA_OFFSET_X, x);
        this.entityData.set(DATA_OFFSET_Y, y);
        this.entityData.set(DATA_OFFSET_Z, z);
    }

    // ===== Attachment =====

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

        // Store relative to rotation point (small numbers, float-safe)
        var rp = subLevel.logicalPose().rotationPoint();
        this.entityData.set(DATA_LOCAL_X, (float) (localPos.x - rp.x()));
        this.entityData.set(DATA_LOCAL_Y, (float) (localPos.y - rp.y()));
        this.entityData.set(DATA_LOCAL_Z, (float) (localPos.z - rp.z()));

        this.setLocalPose(this.getXRot(), this.getYRot(), this.getLocalRoll());
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(0, 0, 0);

        // cameramixin
        if (this.level().isClientSide) {
            return;
        }

        UUID subId = getAttachedSubLevelId();
        if (subId == null) {
            return;
        }

        SubLevel subLevel = findServerSubLevel(subId);
        if (subLevel == null) {
            return;
        }

        var pose = subLevel.logicalPose();
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

        this.setPos(localPos.x, localPos.y, localPos.z);

        this.xo = localPos.x;
        this.yo = localPos.y;
        this.zo = localPos.z;
        this.xOld = localPos.x;
        this.yOld = localPos.y;
        this.zOld = localPos.z;
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
    }

    @Override
    public boolean isPickable() {
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