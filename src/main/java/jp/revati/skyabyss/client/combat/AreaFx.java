package jp.revati.skyabyss.client.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGlowTexture;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import jp.revati.skyabyss.combat.AttackLook;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 範囲攻撃の模型 (2026-11-13 の声「心臓の範囲攻撃、別モデル用意したら？ モデル残るからわかりにくいのと、範囲攻撃なのに間開いてて、
// 予告の見た目と合ってない」)。
// 見た目 (AttackLook) の付いた予告 (心臓・領域の技) が打つ瞬間に、予告の形をすき間なく埋めるように、属性の一区画の模型
// (lowpoly/area_<属性>_a|b、tools/gen/mobs/attack_props/build_area_props.py) を並べて床から突き出す。
//   止まった形 (円・帯・扇・輪): 打つ瞬間に真ん中 (帯・扇は根元) から外へ 2 tick で波打って出て、HOLD tick 留まり、SINK tick で沈んで消える
//   動く形 (広がる輪 Wave・振り回す帯 Sweep): 動くあいだ、いまの輪・帯の上に出ていて、動き終わったら沈む
// 当たり判定は変えない (Telegraph のまま)。出ているのは当たる瞬間の前後だけで、次の予告が出るころには消えている。
// 置く所: 一区画は 1.6 ブロック。円・輪は同心の輪、帯は列、扇は弧の列に並べ、区画の床の割れ板 (半径 0.97 ブロック) が隣と重なる間隔にする。
// 模型が床に描かれる (サーバーの実体ではない) ので、見えるのは予告の印が届く人 (同じグループ) だけ。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AreaFx {
    // 一区画の間隔 (ブロック)。模型の割れ板は半径 15.5/16 ブロック
    static final double CELL = 1.6;
    static final int RISE = 3;
    // 留まる長さ。撮影 (tools/shots/area_props.txt) では環境変数 SKYABYSS_AREA_HOLD で延ばす (打ってから消えるまで 0.5 秒で、絵を撮るのが間に合わない)
    static final int HOLD = holdTicks();
    static final int SINK = 5;
    // 波打って出る遅れ (真ん中から縁まで)
    static final float RIPPLE = 2.5f;
    // 床の下に隠れる深さ (ブロック)。模型の高さ (1.5 ブロック前後) より少し深い
    static final float DEPTH = 1.7f;
    // 一つの予告に置く数の上限 (広い円は区画を大きくして収める)
    static final int MAX_TILES = 240;

    private static int holdTicks() {
        try {
            String v = System.getenv("SKYABYSS_AREA_HOLD");
            return v == null ? 4 : Math.max(0, Math.min(200, Integer.parseInt(v.trim())));
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    // 打ったあと、この技の予告の印を残しておく長さ (範囲の模型が沈みきるまで)
    static int after(AttackLook look) {
        return look != null && look.area() ? HOLD + SINK + 1 : 0;
    }

    record Tile(double x, double z, float yaw, float scale, boolean b, float delay) {
    }

    // 止まった形の置き場所は一度だけ求める (予告の番号ごと)
    private static final Map<Integer, List<Tile>> STATIC = new HashMap<>();


    private AreaFx() {
    }

    static String meshOf(AttackLook.Element el, boolean b) {
        String base = switch (el) {
            case WATER -> "area_water";
            case STORM -> "area_storm";
            case EMBER -> "area_ember";
            default -> "area_stone";
        };
        return base + (b ? "_b" : "_a");
    }

    // ---------------------------------------------------------------- 描く
    @SubscribeEvent
    static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !LowPoly.enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            STATIC.clear();
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        // 型ごとに自前の BufferBuilder に溜めて、最後に型ごとに描く
        Map<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> builders = new java.util.LinkedHashMap<>();
        java.util.Set<Integer> live = new java.util.HashSet<>();
        for (TelegraphClient.Decal d : TelegraphClient.decals()) {
            live.add(d.id);
            if (d.look == null || !d.look.area()) {
                continue;
            }
            float t = (float) (level.getGameTime() - d.localStart) + partial;
            int act = Math.max(0, d.active);
            float end = d.warn + act + HOLD;
            if (t < d.warn || t > end + SINK) {
                continue;
            }
            List<Tile> tiles;
            float since;
            if (act > 0 && (d.shape instanceof TelegraphShape.Wave || d.shape instanceof TelegraphShape.Sweep)) {
                double p = Mth.clamp((t - d.warn) / act, 0f, 1f);
                tiles = place(d.shape.at(p), d.shape, d.id);
                since = t - d.warn;
                // 動く形は波打たせない (いまの輪・帯の上にそろって出ている)
                tiles = tiles.stream().map(k -> new Tile(k.x, k.z, k.yaw, k.scale, k.b, 0f)).toList();
            } else {
                tiles = STATIC.computeIfAbsent(d.id, k -> place(d.shape, d.shape, d.id));
                since = t - d.warn;
            }
            for (Tile k : tiles) {
                float h = height(since - k.delay, since, end - d.warn);
                if (h <= 0.001f) {
                    continue;
                }
                double y = d.ground(k.x, k.z);
                if (Double.isNaN(y)) {
                    continue;
                }
                LowPolyMesh mesh = LowPolyMeshes.get(meshOf(d.look.element, k.b));
                if (mesh == null) {
                    continue;
                }
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(k.x, y + 0.6, k.z));
                pose.pushPose();
                pose.translate(k.x - cam.x, y - cam.y - (1f - Math.min(1f, h)) * DEPTH * k.scale, k.z - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(k.yaw));
                float s = k.scale;
                // 出る瞬間は少し縦に伸び、沈むときは縦に縮む
                pose.scale(s, s * (0.55f + 0.45f * h), s);
                RenderType body = RenderType.entityCutoutNoCull(mesh.texture);
                LowPoly.drawAll(pose, builder(builders, body), mesh, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                ResourceLocation glow = glowOf(mesh);
                if (glow != null) {
                    RenderType g = RenderType.entityTranslucentEmissive(glow);
                    LowPoly.drawAll(pose, builder(builders, g), mesh, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                }
                pose.popPose();
            }
        }
        STATIC.keySet().retainAll(live);
        for (Map.Entry<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> e : builders.entrySet()) {
            com.mojang.blaze3d.vertex.MeshData md = e.getValue().build();
            if (md != null) {
                e.getKey().draw(md);
            }
        }
    }

    private static final Map<RenderType, com.mojang.blaze3d.vertex.ByteBufferBuilder> MEMORY = new HashMap<>();

    private static com.mojang.blaze3d.vertex.BufferBuilder builder(Map<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> open, RenderType type) {
        return open.computeIfAbsent(type, t -> new com.mojang.blaze3d.vertex.BufferBuilder(MEMORY.computeIfAbsent(t, k -> new com.mojang.blaze3d.vertex.ByteBufferBuilder(1 << 16)),
                t.mode(), t.format()));
    }

    // 床から出ている割合 (0 = 隠れる、1 = 出きり、少し行き過ぎる)。age は区画の出始めからの時間、since は打つ瞬間からの時間、
    // hold は打つ瞬間から沈み始めるまで (沈むのは全部の区画いっしょ。予告の印が消えるまでに沈みきる)
    static float height(float age, float since, float hold) {
        if (age < 0) {
            return 0f;
        }
        float out;
        if (age < RISE) {
            float u = age / RISE;
            out = 1.12f * (1 - (1 - u) * (1 - u) * (1 - u));
        } else if (age < RISE + 2) {
            out = 1.12f - 0.12f * (age - RISE) / 2f;
        } else {
            out = 1f;
        }
        if (since > hold) {
            out *= Mth.clamp(1f - (since - hold) / SINK, 0f, 1f);
        }
        return out;
    }

    private static final Map<ResourceLocation, ResourceLocation> GLOW = new HashMap<>();

    private static ResourceLocation glowOf(LowPolyMesh mesh) {
        if (GLOW.containsKey(mesh.texture)) {
            return GLOW.get(mesh.texture);
        }
        ResourceLocation mask = mesh.texture.withPath(p -> p.substring(0, p.length() - 4) + "_glowmask.png");
        ResourceLocation g = Minecraft.getInstance().getResourceManager().getResource(mask).isPresent() ? LowPolyGlowTexture.of(mesh.texture, mask) : null;
        GLOW.put(mesh.texture, g);
        return g;
    }

    // ---------------------------------------------------------------- 置く所
    // shape = いまの形 (動く形はその瞬間の輪・帯)、whole = 予告の形 (広がる輪の数を最後の大きさで決める)
    static List<Tile> place(TelegraphShape shape, TelegraphShape whole, int seed) {
        List<Tile> out = new ArrayList<>();
        if (shape instanceof TelegraphShape.Circle c) {
            rings(out, c.cx(), c.cz(), 0, c.r(), null, 0, seed, 0);
        } else if (shape instanceof TelegraphShape.Ring g) {
            rings(out, g.cx(), g.cz(), g.inner(), g.outer(), null, 0, seed, 0);
        } else if (shape instanceof TelegraphShape.Band b) {
            int fixed = whole instanceof TelegraphShape.Wave w ? (int) Math.ceil(Math.PI * 2 * w.r1() / CELL) : 0;
            rings(out, b.cx(), b.cz(), Math.max(0, b.r() - b.halfThickness()), b.r() + b.halfThickness(), b.gaps(), b.gapHalf(), seed, fixed);
        } else if (shape instanceof TelegraphShape.Line l) {
            line(out, l.x(), l.z(), l.angle(), l.length(), l.halfWidth(), seed);
        } else if (shape instanceof TelegraphShape.Cone k) {
            cone(out, k.x(), k.z(), k.angle(), k.range(), k.halfAngle(), seed);
        } else {
            grid(out, shape, seed);
        }
        return out;
    }

    // 同心の輪 (r0..r1 の帯の中、外の輪は縁から少し内)。gaps の安全な筋には置かない。fixed > 0 なら一番外の輪の数をこれに固定 (広がる輪が動いても数が変わらない)
    static void rings(List<Tile> out, double cx, double cz, double r0, double r1, double[] gaps, double gapHalf, int seed, int fixed) {
        double cell = cellFor(Math.PI * (r1 * r1 - r0 * r0));
        float sc = (float) (cell / CELL);
        double edge = Math.max(r0, r1 - 0.55 * sc);
        double step = cell * 0.87;
        List<Double> radii = new ArrayList<>();
        for (double r = edge; r > r0 + step * 0.5 - 1e-6; r -= step) {
            radii.add(r);
        }
        if (radii.isEmpty()) {
            radii.add((r0 + r1) / 2);
        }
        double last = radii.get(radii.size() - 1);
        boolean center = r0 <= 0 && last > step * 0.75;
        int idx = 0;
        for (int ri = 0; ri < radii.size(); ri++) {
            double r = radii.get(ri);
            int n = Math.max(1, (int) Math.ceil(Math.PI * 2 * r / cell));
            if (fixed > 0 && ri == 0) {
                n = Math.max(n, fixed);
            }
            double off = hash(seed, ri) * Math.PI * 2;
            for (int i = 0; i < n; i++) {
                double a = off + i * Math.PI * 2 / n;
                if (gaps != null && inGap(gaps, gapHalf + 0.55 * sc / Math.max(1.0, r), a)) {
                    continue;
                }
                out.add(tile(cx + Math.cos(a) * r, cz + Math.sin(a) * r, sc, seed, idx++, (float) (r / Math.max(1e-3, r1))));
            }
        }
        if (center || (r0 <= 0 && radii.get(0) < cell * 0.6)) {
            out.add(tile(cx, cz, sc, seed, idx, 0f));
        }
    }

    // 帯: 根元から先へ列、幅は横に何枚か
    static void line(List<Tile> out, double x, double z, double angle, double length, double halfWidth, int seed) {
        double w = halfWidth * 2;
        int cols = Math.max(1, (int) Math.ceil((w - 0.4) / CELL));
        double colW = w / cols;
        float sc = (float) Math.max(Mth.clamp(colW / CELL * 1.1, 0.6, 1.0), cellFor(length * w) / CELL);
        double step = CELL * sc * 0.87;
        double ca = Math.cos(angle), sa = Math.sin(angle);
        double first = Math.min(0.5 * sc, length / 2);
        double last = Math.max(first, length - 0.45 * sc);
        int rows = Math.max(1, (int) Math.ceil((last - first) / step) + 1);
        int idx = 0;
        for (int r = 0; r < rows; r++) {
            double d = rows == 1 ? first : first + (last - first) * r / (rows - 1);
            for (int j = 0; j < cols; j++) {
                double side = -halfWidth + (j + 0.5) * colW;
                out.add(tile(x + ca * d - sa * side, z + sa * d + ca * side, sc, seed, idx++, (float) (d / Math.max(1e-3, length))));
            }
        }
    }

    // 扇: 根元からの距離ごとの弧に並べる
    static void cone(List<Tile> out, double x, double z, double angle, double range, double halfAngle, int seed) {
        double cell = cellFor(range * range * halfAngle);
        float sc = (float) (cell / CELL);
        double step = cell * 0.87;
        int idx = 0;
        for (double d = Math.max(0.6, 0.5 * sc); d <= range - 0.45 * sc + 1e-6; d += step) {
            double arc = 2 * d * halfAngle;
            int n = Math.max(1, (int) Math.ceil(arc / cell));
            double margin = Math.min(halfAngle * 0.5, 0.45 * sc / d);
            for (int i = 0; i < n; i++) {
                double a = n == 1 ? angle : angle - halfAngle + margin + (2 * (halfAngle - margin)) * i / (n - 1);
                out.add(tile(x + Math.cos(a) * d, z + Math.sin(a) * d, sc, seed, idx++, (float) (d / range)));
            }
        }
    }

    // そのほかの形: 六角の格子の点のうち、形の中にあるもの
    static void grid(List<Tile> out, TelegraphShape s, int seed) {
        double[] b = s.bounds();
        double cell = cellFor((b[2] - b[0]) * (b[3] - b[1]) * 0.7);
        float sc = (float) (cell / CELL);
        double dz = cell * 0.87;
        double cx = s.centerX(), cz = s.centerZ();
        double maxR = Math.max(1e-3, Math.hypot(b[2] - b[0], b[3] - b[1]) / 2);
        int row = 0, idx = 0;
        for (double z = b[1]; z <= b[3]; z += dz, row++) {
            for (double x = b[0] + (row % 2) * cell / 2; x <= b[2]; x += cell) {
                if (s.contains(x, z)) {
                    out.add(tile(x, z, sc, seed, idx++, (float) (Math.hypot(x - cx, z - cz) / maxR)));
                }
            }
        }
    }

    // 区画の間隔: 置く数が上限を超える広さなら区画を大きくする
    static double cellFor(double area) {
        double per = CELL * CELL * 0.87;
        return area / per > MAX_TILES ? Math.sqrt(area / (MAX_TILES * 0.87)) : CELL;
    }

    static Tile tile(double x, double z, float sc, int seed, int i, float frac) {
        float j = 0.9f + 0.2f * (float) hash(seed, i * 3 + 1);
        return new Tile(x, z, (float) (hash(seed, i * 3 + 2) * 360.0), sc * j, hash(seed, i * 3 + 3) < 0.5, frac * RIPPLE);
    }

    static double hash(int seed, int i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    static boolean inGap(double[] gaps, double half, double a) {
        for (double g : gaps) {
            if (Math.abs(Mth.wrapDegrees(Math.toDegrees(a - g))) <= Math.toDegrees(half)) {
                return true;
            }
        }
        return false;
    }
}
