package net.jji.cullinggames;

import com.mojang.logging.LogUtils;
import net.jji.cullinggames.colony.Barrier;
import net.jji.cullinggames.command.CgCommands;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.event.EventManager;
import net.jji.cullinggames.event.KoganeBar;
import net.jji.cullinggames.event.Penalties;
import net.jji.cullinggames.event.Points;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CullingGames.MOD_ID)
public final class CullingGames {
    public static final String MOD_ID = "cullinggames";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CullingGames(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, CgConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(EventManager.class);
        MinecraftForge.EVENT_BUS.register(CgCommands.class);
        MinecraftForge.EVENT_BUS.register(Barrier.class);
        MinecraftForge.EVENT_BUS.register(Points.class);
        MinecraftForge.EVENT_BUS.register(Penalties.class);
        MinecraftForge.EVENT_BUS.register(KoganeBar.class);
    }
}
