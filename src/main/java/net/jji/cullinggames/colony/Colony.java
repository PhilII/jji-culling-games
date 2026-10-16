package net.jji.cullinggames.colony;

import java.util.Random;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

/**
 * A colony is a column of the world (all heights) in one dimension: a circle or an axis-aligned box.
 * Coordinates are block coordinates; containment is tested against the player's exact position.
 */
public final class Colony {
    public enum Shape { CIRCLE, BOX }

    /** Players pushed back in land this far inside the edge. */
    private static final double EDGE_MARGIN = 1.5;

    private final String name;
    private final ResourceKey<Level> dimension;
    private final Shape shape;
    // CIRCLE: centerX, centerZ, radius. BOX: minX, minZ, maxX, maxZ (inclusive blocks).
    private final int a, b, c, d;

    private Colony(String name, ResourceKey<Level> dimension, Shape shape, int a, int b, int c, int d) {
        this.name = name;
        this.dimension = dimension;
        this.shape = shape;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    public static Colony circle(String name, ResourceKey<Level> dim, int centerX, int centerZ, int radius) {
        return new Colony(name, dim, Shape.CIRCLE, centerX, centerZ, radius, 0);
    }

    public static Colony box(String name, ResourceKey<Level> dim, int x1, int z1, int x2, int z2) {
        return new Colony(name, dim, Shape.BOX, Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
    }

    public String name() {
        return name;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public Shape shape() {
        return shape;
    }

    /** CIRCLE: {centerX, centerZ, radius, 0}. BOX: {minX, minZ, maxX, maxZ}, inclusive blocks. */
    public int[] geometry() {
        return new int[] {a, b, c, d};
    }

    public boolean contains(ResourceKey<Level> dim, double x, double z) {
        if (!dimension.equals(dim)) {
            return false;
        }
        if (shape == Shape.CIRCLE) {
            double dx = x - (a + 0.5);
            double dz = z - (b + 0.5);
            return dx * dx + dz * dz <= (double) c * c;
        }
        return x >= a && x < c + 1 && z >= b && z < d + 1;
    }

    /** Nearest point at least EDGE_MARGIN inside the colony (x, z). */
    public Vec2 clampInside(double x, double z) {
        if (shape == Shape.CIRCLE) {
            double cx = a + 0.5;
            double cz = b + 0.5;
            double dx = x - cx;
            double dz = z - cz;
            double len = Math.sqrt(dx * dx + dz * dz);
            double max = Math.max(0, c - EDGE_MARGIN);
            if (len <= max) {
                return new Vec2((float) x, (float) z);
            }
            if (len < 1e-6) {
                return new Vec2((float) cx, (float) cz);
            }
            return new Vec2((float) (cx + dx / len * max), (float) (cz + dz / len * max));
        }
        return new Vec2((float) clamp(x, a + EDGE_MARGIN, c + 1 - EDGE_MARGIN),
                (float) clamp(z, b + EDGE_MARGIN, d + 1 - EDGE_MARGIN));
    }

    /** A uniformly random point inside the colony (x, z), at least EDGE_MARGIN from the edge. */
    public Vec2 randomPoint(Random rnd) {
        if (shape == Shape.CIRCLE) {
            double r = Math.max(0, c - EDGE_MARGIN) * Math.sqrt(rnd.nextDouble());
            double t = rnd.nextDouble() * Math.PI * 2;
            return new Vec2((float) (a + 0.5 + r * Math.cos(t)), (float) (b + 0.5 + r * Math.sin(t)));
        }
        double lo = a + EDGE_MARGIN, hi = c + 1 - EDGE_MARGIN;
        double loz = b + EDGE_MARGIN, hiz = d + 1 - EDGE_MARGIN;
        return new Vec2((float) (lo + rnd.nextDouble() * Math.max(0, hi - lo)),
                (float) (loz + rnd.nextDouble() * Math.max(0, hiz - loz)));
    }

    public Vec2 center() {
        if (shape == Shape.CIRCLE) {
            return new Vec2(a + 0.5f, b + 0.5f);
        }
        return new Vec2((a + c + 1) / 2f, (b + d + 1) / 2f);
    }

    public String describe() {
        String where = shape == Shape.CIRCLE
                ? "circle center " + a + " " + b + " radius " + c
                : "box " + a + " " + b + " to " + c + " " + d;
        return name + " (" + dimension.location() + ", " + where + ")";
    }

    private static double clamp(double v, double lo, double hi) {
        if (lo > hi) {
            return (lo + hi) / 2;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("dim", dimension.location().toString());
        t.putString("shape", shape.name());
        t.putIntArray("v", new int[] {a, b, c, d});
        return t;
    }

    static Colony load(CompoundTag t) {
        ResourceLocation dimId = ResourceLocation.tryParse(t.getString("dim"));
        int[] v = t.getIntArray("v");
        if (dimId == null || v.length != 4) {
            return null;
        }
        Shape shape = "BOX".equals(t.getString("shape")) ? Shape.BOX : Shape.CIRCLE;
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
        return new Colony(t.getString("name"), dim, shape, v[0], v[1], v[2], v[3]);
    }
}
