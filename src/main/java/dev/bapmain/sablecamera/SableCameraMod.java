package dev.bapmain.sablecamera;

import com.mojang.logging.LogUtils;
import dev.bapmain.sablecamera.entity.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(SableCameraMod.MOD_ID)
public class SableCameraMod {
    public static final String MOD_ID = "sablecamera";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SableCameraMod(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Sable Camera Anchor loaded");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CameraCommands.register(event.getDispatcher());
    }
}