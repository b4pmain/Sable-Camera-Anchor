package dev.bapmain.sablecamera.entity;

import dev.bapmain.sablecamera.SableCameraMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SableCameraMod.MOD_ID);

    public static final Supplier<EntityType<CameraAnchorEntity>> CAMERA_ANCHOR =
            ENTITY_TYPES.register("camera_anchor", () ->
                    EntityType.Builder.of(CameraAnchorEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(256)
                            .updateInterval(1)
                            .fireImmune()
                            .build("camera_anchor")   // ← just the string name
            );
}