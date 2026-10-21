package net.jji.cullinggames;

import com.mojang.logging.LogUtils;
import net.jji.cullinggames.colony.Barrier;
import net.jji.cullinggames.colony.BarrierSync;
import net.jji.cullinggames.command.CgCommands;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.event.Combat;
import net.jji.cullinggames.event.EventManager;
import net.jji.cullinggames.event.KoganeBar;
import net.jji.cullinggames.event.Penalties;
import net.jji.cullinggames.event.Points;
import net.jji.cullinggames.event.Rewards;
import net.jji.cullinggames.event.Tracker;
import net.jji.cullinggames.network.CgNetwork;
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
        CgNetwork.register();
        MinecraftForge.EVENT_BUS.register(EventManager.class);
        MinecraftForge.EVENT_BUS.register(CgCommands.class);
        MinecraftForge.EVENT_BUS.register(Barrier.class);
        MinecraftForge.EVENT_BUS.register(BarrierSync.class);
        MinecraftForge.EVENT_BUS.register(Points.class);
        // After Points: the kill's points unseal a sealed killer before the cursed energy bonus.
        MinecraftForge.EVENT_BUS.register(Combat.class);
        MinecraftForge.EVENT_BUS.register(Tracker.class);
        MinecraftForge.EVENT_BUS.register(Penalties.class);
        MinecraftForge.EVENT_BUS.register(KoganeBar.class);
        MinecraftForge.EVENT_BUS.register(Rewards.class);
    }
}
