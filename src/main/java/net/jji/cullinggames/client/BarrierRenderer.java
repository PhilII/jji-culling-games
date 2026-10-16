package net.jji.cullinggames.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.network.BarrierSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws each colony's barrier as a black curtain from the bottom of the world to the build limit, like the
 * Culling Game barriers in the anime. Only the parts within render distance are drawn.
 */
@Mod.EventBusSubscriber(modid = CullingGames.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BarrierRenderer {
    /** Segment length along the wall, in blocks. */
    private static final double STEP = 2.0;
    /** Near the ground the curtain has a faint purple tint; it fades to pure black above. */
    private static final int BOTTOM_R = 22, BOTTOM_G = 6, BOTTOM_B = 34;
    private static final int ALPHA = 238;
    private static final int GLOW_HEIGHT = 24;

    private BarrierRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        BarrierSyncPacket barriers = ClientPackets.barriers;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (barriers.walls().isEmpty() || level == null) {
            return;
        }
        String dimension = level.dimension().location().toString();
        Vec3 cam = event.getCamera().getPosition();
        double range = mc.options.getEffectiveRenderDistance() * 16.0;
        float y0 = level.getMinBuildHeight();
        float y1 = level.getMaxBuildHeight();
        // A glow band around the camera's ground level, so the purple tint sits where the player sees it.
        float glowBottom = (float) Math.max(y0, cam.y - GLOW_HEIGHT);
        float glowTop = (float) Math.min(y1, cam.y + GLOW_HEIGHT);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (BarrierSyncPacket.Wall w : barriers.walls()) {
            if (!w.dimension().equals(dimension)) {
                continue;
            }
            if (w.circle()) {
                double cx = w.a() + 0.5;
                double cz = w.b() + 0.5;
                double r = w.c();
                int n = (int) Math.max(32, Math.min(40000, Math.ceil(2 * Math.PI * r / STEP)));
                for (int i = 0; i < n; i++) {
                    double t0 = 2 * Math.PI * i / n;
                    double t1 = 2 * Math.PI * (i + 1) / n;
                    any |= segment(buf, m, cam, range, cx + r * Math.cos(t0), cz + r * Math.sin(t0),
                            cx + r * Math.cos(t1), cz + r * Math.sin(t1), y0, glowBottom, glowTop, y1);
                }
            } else {
                double x0 = w.a(), z0 = w.b(), x1 = w.c() + 1, z1 = w.d() + 1;
                any |= line(buf, m, cam, range, x0, z0, x1, z0, y0, glowBottom, glowTop, y1);
                any |= line(buf, m, cam, range, x1, z0, x1, z1, y0, glowBottom, glowTop, y1);
                any |= line(buf, m, cam, range, x1, z1, x0, z1, y0, glowBottom, glowTop, y1);
                any |= line(buf, m, cam, range, x0, z1, x0, z0, y0, glowBottom, glowTop, y1);
            }
        }
        if (!any) {
            buf.end().release();
            pose.popPose();
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** A straight box edge, cut into STEP-long segments so only the nearby part is drawn. */
    private static boolean line(BufferBuilder buf, Matrix4f m, Vec3 cam, double range, double xa, double za,
                                double xb, double zb, float y0, float g0, float g1, float y1) {
        double len = Math.hypot(xb - xa, zb - za);
        int n = Math.max(1, (int) Math.ceil(len / STEP));
        boolean any = false;
        for (int i = 0; i < n; i++) {
            double f0 = (double) i / n;
            double f1 = (double) (i + 1) / n;
            any |= segment(buf, m, cam, range, xa + (xb - xa) * f0, za + (zb - za) * f0,
                    xa + (xb - xa) * f1, za + (zb - za) * f1, y0, g0, g1, y1);
        }
        return any;
    }

    /** One vertical strip of curtain: black below and above, faint purple around the camera's height. */
    private static boolean segment(BufferBuilder buf, Matrix4f m, Vec3 cam, double range,
                                   double xa, double za, double xb, double zb, float y0, float g0, float g1, float y1) {
        double mx = (xa + xb) / 2 - cam.x;
        double mz = (za + zb) / 2 - cam.z;
        if (mx * mx + mz * mz > range * range) {
            return false;
        }
        float ax = (float) xa, az = (float) za, bx = (float) xb, bz = (float) zb;
        float mid = (g0 + g1) / 2;
        quad(buf, m, ax, az, bx, bz, y0, g0, 0, 0);
        quad(buf, m, ax, az, bx, bz, g0, mid, 0, 1);
        quad(buf, m, ax, az, bx, bz, mid, g1, 1, 0);
        quad(buf, m, ax, az, bx, bz, g1, y1, 0, 0);
        return true;
    }

    /** Vertical quad from (ax, az) to (bx, bz); glowLow/glowHigh = 1 tints that edge purple. */
    private static void quad(BufferBuilder buf, Matrix4f m, float ax, float az, float bx, float bz,
                             float lo, float hi, int glowLow, int glowHigh) {
        if (hi <= lo) {
            return;
        }
        int rl = BOTTOM_R * glowLow, gl = BOTTOM_G * glowLow, bl = BOTTOM_B * glowLow;
        int rh = BOTTOM_R * glowHigh, gh = BOTTOM_G * glowHigh, bh = BOTTOM_B * glowHigh;
        buf.vertex(m, ax, lo, az).color(rl, gl, bl, ALPHA).endVertex();
        buf.vertex(m, bx, lo, bz).color(rl, gl, bl, ALPHA).endVertex();
        buf.vertex(m, bx, hi, bz).color(rh, gh, bh, ALPHA).endVertex();
        buf.vertex(m, ax, hi, az).color(rh, gh, bh, ALPHA).endVertex();
    }
}
