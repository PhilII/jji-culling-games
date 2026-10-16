package net.jji.cullinggames.colony;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec2;

/** Teleporting players into colonies at safe spots. */
public final class ColonyService {
    private static final Random RANDOM = new Random();
    private static final int SPAWN_ATTEMPTS = 16;

    private ColonyService() {}

    /** Teleports the player to a random safe spot inside the colony. Returns false if the dimension is missing. */
    public static boolean teleportRandom(MinecraftServer server, ServerPlayer player, Colony colony) {
        ServerLevel level = server.getLevel(colony.dimension());
        if (level == null) {
            return false;
        }
        Vec2 spot = colony.center();
        for (int i = 0; i < SPAWN_ATTEMPTS; i++) {
            Vec2 p = colony.randomPoint(RANDOM);
            if (isSafeColumn(level, p)) {
                spot = p;
                break;
            }
        }
        teleport(player, level, spot);
        return true;
    }

    /** Pushes the player back to the nearest point inside the colony. */
    public static boolean pushInside(MinecraftServer server, ServerPlayer player, Colony colony) {
        ServerLevel level = server.getLevel(colony.dimension());
        if (level == null) {
            return false;
        }
        if (player.level() != level) {
            return teleportRandom(server, player, colony);
        }
        teleport(player, level, colony.clampInside(player.getX(), player.getZ()));
        return true;
    }

    private static void teleport(ServerPlayer player, ServerLevel level, Vec2 xz) {
        double y = surfaceY(level, xz);
        player.stopRiding();
        player.teleportTo(level, xz.x, y, xz.y, player.getYRot(), player.getXRot());
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
    }

    /**
     * Where a player can stand at or near this spot: the first block with solid ground and two free blocks,
     * searching down from the spot, then up, then the top of the column. Keeps spawns under a roof under it.
     */
    public static BlockPos safeStand(ServerLevel level, BlockPos near) {
        for (int dy = 0; dy <= 64; dy++) {
            BlockPos p = near.below(dy);
            if (p.getY() <= level.getMinBuildHeight()) {
                break;
            }
            if (canStand(level, p)) {
                return p;
            }
        }
        for (int dy = 1; dy <= 64; dy++) {
            BlockPos p = near.above(dy);
            if (p.getY() >= level.getMaxBuildHeight() - 1) {
                break;
            }
            if (canStand(level, p)) {
                return p;
            }
        }
        return new BlockPos(near.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()), near.getZ());
    }

    private static boolean canStand(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty();
    }

    private static double surfaceY(ServerLevel level, Vec2 xz) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(xz.x), (int) Math.floor(xz.y));
    }

    private static boolean isSafeColumn(ServerLevel level, Vec2 xz) {
        int x = (int) Math.floor(xz.x);
        int z = (int) Math.floor(xz.y);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight()) {
            return false;
        }
        FluidState below = level.getFluidState(new BlockPos(x, y - 1, z));
        return below.isEmpty();
    }
}
