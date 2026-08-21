package dev.bapmain.sablecamera;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "sablecamera")
public class LagDebug {

    private static long lastNs = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.nanoTime();
        if (lastNs != 0) {
            long ms = (now - lastNs) / 1_000_000L;
            // hitches log when ms hits 100+
            if (ms > 100) {
                System.out.println("[SableCamera][Server] TICK GAP " + ms + " ms");
            }
        }
        lastNs = now;
    }
}