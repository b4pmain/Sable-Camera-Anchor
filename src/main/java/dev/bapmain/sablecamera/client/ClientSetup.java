package dev.bapmain.sablecamera.client;

import dev.bapmain.sablecamera.entity.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "sablecamera", value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CAMERA_ANCHOR.get(),
                context -> new net.minecraft.client.renderer.entity.NoopRenderer<>(context));
    }
}