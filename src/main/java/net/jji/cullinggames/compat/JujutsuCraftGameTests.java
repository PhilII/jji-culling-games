package net.jji.cullinggames.compat;

import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.test.TestPlayers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Only meaningful with Jujutsu Craft in ./libs; passes trivially without it. */
@GameTestHolder(CullingGames.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JujutsuCraftGameTests {
    private JujutsuCraftGameTests() {}

    @GameTest(template = "empty", batch = "jjc")
    public static void cursedEnergyRoundTrip(GameTestHelper h) {
        if (!JujutsuCraft.isLoaded()) {
            CullingGames.LOGGER.warn("Jujutsu Craft not loaded; skipping cursedEnergyRoundTrip");
            h.succeed();
            return;
        }
        ServerPlayer p = TestPlayers.create(h, "gt_jjc");
        try {
            double ct = JujutsuCraft.technique(p);
            double ct2 = JujutsuCraft.secondTechnique(p);
            CullingGames.LOGGER.info("JJC test player: CT {} / {}, CE {} / {}", ct, ct2,
                    JujutsuCraft.cursedEnergy(p), JujutsuCraft.maxCursedEnergy(p));
            if (!JujutsuCraft.setCursedEnergy(p, 1234)) {
                h.fail("could not set CE");
            }
            if (JujutsuCraft.cursedEnergy(p) != 1234) {
                h.fail("CE not 1234 after set");
            }
            JujutsuCraft.setCursedEnergy(p, 0);
            if (JujutsuCraft.cursedEnergy(p) != 0) {
                h.fail("CE not 0 after seal");
            }
            if (JujutsuCraft.technique(p) != ct || JujutsuCraft.secondTechnique(p) != ct2) {
                h.fail("CT changed by CE writes");
            }
        } finally {
            TestPlayers.remove(h, p);
        }
        h.succeed();
    }
}
