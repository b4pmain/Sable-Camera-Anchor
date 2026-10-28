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

    public static final KeyMapping PREV_CAMERA_KEY = new KeyMapping(
            "key.sablecamera.prev_camera",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT,
            KEY_CATEGORY
    );

    public static final KeyMapping NEXT_CAMERA_KEY = new KeyMapping(
            "key.sablecamera.next_camera",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT,
            KEY_CATEGORY
    );

    public static final KeyMapping TOGGLE_LOCK_KEY = new KeyMapping(
            "key.sablecamera.toggle_lock",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            KEY_CATEGORY
    );

    public static final KeyMapping FOV_DEC_KEY = new KeyMapping(
            "key.sablecamera.fov_decrease",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_MINUS,
            KEY_CATEGORY
    );

    public static final KeyMapping FOV_INC_KEY = new KeyMapping(
            "key.sablecamera.fov_increase",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
            KEY_CATEGORY
    );

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CAMERA_ANCHOR.get(), CameraAnchorRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(EXIT_VIEW_KEY);
        event.register(PREV_CAMERA_KEY);
        event.register(NEXT_CAMERA_KEY);
        event.register(TOGGLE_LOCK_KEY);
        event.register(FOV_DEC_KEY);
        event.register(FOV_INC_KEY);
    }

}
