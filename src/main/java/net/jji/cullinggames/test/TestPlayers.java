package net.jji.cullinggames.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Shared helpers for GameTests. */
public final class TestPlayers {
    private TestPlayers() {}

    /**
     * GameTestHelper.makeMockServerPlayerInLevel() crashes on Forge 1.20.1 (its Connection has no channel).
     * An EmbeddedChannel gives the connection a real pipeline, so placeNewPlayer works.
     */
    public static ServerPlayer create(GameTestHelper h, String name) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    public static void remove(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) {
            h.getLevel().getServer().getPlayerList().remove(p);
        }
    }
}
