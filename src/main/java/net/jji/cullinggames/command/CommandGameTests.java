package net.jji.cullinggames.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.test.TestPlayers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Permission review: what a normal (non-op) player can and can't run. */
@GameTestHolder(CullingGames.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommandGameTests {
    private CommandGameTests() {}

    private static final String[] PLAYER_COMMANDS = {
        "cg", "cg help", "cg points", "cg shop", "cg buy leaderboard", "cg rules", "cg addrule double_points",
        "cg leaderboard", "cg killlog", "cg cashout 1", "cg transfer gt_perm 1"
    };

    private static final String[] ADMIN_COMMANDS = {
        "cg start", "cg stop", "cg stop 5", "cg pause", "cg resume", "cg status", "cg colony list",
        "cg colony add x circle 0 0 10", "cg participant add gt_perm", "cg points get gt_perm",
        "cg points set gt_perm 5", "cg points add gt_perm 5", "cg shop add 5", "cg shop remove 1"
    };

    /** True if the whole command parses for this source (unknown or forbidden commands don't). */
    private static boolean parses(CommandDispatcher<CommandSourceStack> d, CommandSourceStack source, String command) {
        ParseResults<CommandSourceStack> parse = d.parse(command, source);
        return !parse.getReader().canRead() && parse.getExceptions().isEmpty() && parse.getContext().getCommand() != null;
    }

    @GameTest(template = "empty", batch = "permissions")
    public static void playersCannotRunAdminCommands(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h, "gt_perm");
        try {
            CommandDispatcher<CommandSourceStack> d = h.getLevel().getServer().getCommands().getDispatcher();
            CommandSourceStack player = p.createCommandSourceStack().withPermission(0);
            CommandSourceStack op = p.createCommandSourceStack().withPermission(2);
            for (String cmd : PLAYER_COMMANDS) {
                if (!parses(d, player, cmd)) {
                    h.fail("player should be able to run: " + cmd);
                }
            }
            for (String cmd : ADMIN_COMMANDS) {
                if (parses(d, player, cmd)) {
                    h.fail("player must not be able to run: " + cmd);
                }
                if (!parses(d, op, cmd)) {
                    h.fail("admin should be able to run: " + cmd);
                }
            }
        } finally {
            TestPlayers.remove(h, p);
        }
        h.succeed();
    }
}
