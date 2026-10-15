package net.jji.cullinggames;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CullingGames.MOD_ID)
public final class CullingGames {
    public static final String MOD_ID = "cullinggames";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CullingGames() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Culling Games loaded");
    }
}
