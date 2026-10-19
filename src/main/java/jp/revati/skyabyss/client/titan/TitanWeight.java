package jp.revati.skyabyss.client.titan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import jp.revati.skyabyss.titan.solid.SolidTitan;
import jp.revati.skyabyss.titan.solid.TitanSolids;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

// 巨神の「重さ」の見せ方のうち、描画の側 (docs/design/02 の「重さ」): 地面・海面へ落ちる大きな柔らかい影。
// 影は体の真上から見た輪郭 (TitanVolume.shadow) を 4 ブロックの升目で敷き、升目の角ごとに濃さを変えて縁をぼかす。地形の高さに沿わせる
// (海の上は海面に落ちる)。体が遠い・高いほど薄い。描画は巨神の renderer の render() から呼ぶ (巨神の描画の中でだけ呼ぶこと)
public final class TitanWeight {
    private static final ResourceLocation SHADOW = ResourceLocation.withDefaultNamespace("textures/misc/shadow.png");
    // 影の最大の濃さ
    private static final float MAX_ALPHA = 0.5f;
    // 地面の高さの控え (世界の升目ごと)。1 秒ごとに捨てる
    private static final Long2FloatOpenHashMap GROUND = new Long2FloatOpenHashMap();
    private static long groundStamp = -1;

    private TitanWeight() {
    }

    public static void renderShadow(Entity titan, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        if (!jp.revati.skyabyss.client.lowpoly.LowPoly.titanWeight || !(titan instanceof SolidTitan solid) || !(titan.level() instanceof ClientLevel level)) {
            return;
        }
        TitanVolume volume = TitanVolume.get(solid.volumeName());
        if (volume == null) {
            return;
        }
        long now = level.getGameTime();
        if (now / 20 != groundStamp) {
            groundStamp = now / 20;
            GROUND.clear();
        }
        TitanVolume.Shadow sh = volume.shadow();
        float yaw = Mth.rotLerp(partialTick, titan.yRotO, titan.getYRot());
        double ex = Mth.lerp(partialTick, titan.xOld, titan.getX());
        double ey = Mth.lerp(partialTick, titan.yOld, titan.getY());
        double ez = Mth.lerp(partialTick, titan.zOld, titan.getZ());
        // 体のいちばん下の高さの目安 (局所の 0。眠って沈んでも、そこから測る)
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityShadow(SHADOW));
        Matrix4f m = poseStack.last().pose();
        int cols = sh.nj() + 1;
        float[] gy = new float[(sh.ni() + 1) * cols];
        float[] gx = new float[gy.length];
        float[] gz = new float[gy.length];
        float[] ga = new float[gy.length];
        for (int i = 0; i <= sh.ni(); i++) {
            for (int j = 0; j <= sh.nj(); j++) {
                int k = i * cols + j;
                float a = sh.alphaAt(i, j);
                if (a <= 0.001f) {
                    ga[k] = 0f;
                    continue;
                }
                net.minecraft.world.phys.Vec3 d = TitanSolids.toWorldDelta(yaw, new net.minecraft.world.phys.Vec3(sh.x0() + i * sh.tile(), 0, sh.z0() + j * sh.tile()));
                int bx = Mth.floor(ex + d.x);
                int bz = Mth.floor(ez + d.z);
                float g = ground(level, bx, bz);
                if (Float.isNaN(g) || g > ey + 40) {
                    ga[k] = 0f;
                    continue;
                }
                float height = (float) (ey - g);
                // 高いほど薄く (空を行く巨神の影は、淡く広がる)
                float fade = 1f - Mth.clamp((height - 6f) / 160f, 0f, 0.65f);
                ga[k] = a * MAX_ALPHA * fade;
                gx[k] = (float) d.x;
                gz[k] = (float) d.z;
                gy[k] = (float) (g - ey) + 0.03f;
            }
        }
        for (int i = 0; i < sh.ni(); i++) {
            for (int j = 0; j < sh.nj(); j++) {
                int k00 = i * cols + j;
                int k01 = k00 + 1;
                int k10 = k00 + cols;
                int k11 = k10 + 1;
                if (ga[k00] <= 0f && ga[k01] <= 0f && ga[k10] <= 0f && ga[k11] <= 0f) {
                    continue;
                }
                vertex(buffer, m, gx[k00], gy[k00], gz[k00], ga[k00]);
                vertex(buffer, m, gx[k01], gy[k01], gz[k01], ga[k01]);
                vertex(buffer, m, gx[k11], gy[k11], gz[k11], ga[k11]);
                vertex(buffer, m, gx[k10], gy[k10], gz[k10], ga[k10]);
            }
        }
    }

    private static void vertex(VertexConsumer b, Matrix4f m, float x, float y, float z, float alpha) {
        b.addVertex(m, x, y, z).setColor(1f, 1f, 1f, alpha).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0f, 1f, 0f);
    }

    // 升目 (x, z) の影を落とす高さ (地面・水面の上)。読み込んでいなければ NaN
    private static float ground(ClientLevel level, int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        float cached = GROUND.getOrDefault(key, Float.MIN_VALUE);
        if (cached != Float.MIN_VALUE) {
            return cached;
        }
        float g;
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            g = Float.NaN;
        } else {
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockState below = level.getBlockState(new BlockPos(x, h - 1, z));
            // 水の上は水面 (低い 0.1) に落とす
            g = below.getFluidState().isEmpty() ? h : h - 0.12f;
        }
        GROUND.put(key, g);
        return g;
    }

    // 影の描画のために、体がカメラの視界を出ていても描く範囲 (上から見下ろしたとき、真下の影を出すために下へ伸ばす)
    public static net.minecraft.world.phys.AABB withShadowReach(net.minecraft.world.phys.AABB box, double down) {
        return box.expandTowards(0, -down, 0);
    }

    // カメラが巨神の近く (この距離以内) か。影を描くかの目安
    public static boolean near(Entity titan, double range) {
        Entity cam = Minecraft.getInstance().cameraEntity;
        return cam != null && cam.distanceToSqr(titan) < range * range;
    }
}
