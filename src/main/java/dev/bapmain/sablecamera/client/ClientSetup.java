package dev.bapmain.sablecamera.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bapmain.sablecamera.entity.ModEntities;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "sablecamera", value = Dist.CLIENT)
public class ClientSetup {

    public static final String KEY_CATEGORY = "key.categories.sablecamera";

    public static final KeyMapping EXIT_VIEW_KEY = new KeyMapping(
            "key.sablecamera.exit",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSPACE,
            KEY_CATEGORY
    );

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CAMERA_ANCHOR.get(), CameraAnchorRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(EXIT_VIEW_KEY);
    }

}
