package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;

// 床の印 (decal) の自己テスト (TelegraphSelfTest から呼ぶ。画面には触れない)。
//  1. 形ごとに、描く面 (TelegraphGeometry.area) を標本の点で TelegraphShape.contains と突き合わせる (縁の曖昧な点は除く)
//  2. 満ちる面 (fill) は範囲の内側だけ、進むほど広がり、最後に全体になる。波の安全な筋は輪の通り道を面と二分する
//  3. 縁の線は、範囲の縁の上にある
//  4. パケットの往復: 全項目が戻り、戻した形が元の形と同じ判定をする。壊れた入力は断る
final class TelegraphDecalTest {
    private static final double JITTER = 0.12;

    private TelegraphDecalTest() {
    }

    static void run(MinecraftServer server, List<String> f, List<String> notes) {
        Map<String, TelegraphShape> shapes = new LinkedHashMap<>();
        shapes.put("circle", TelegraphShape.circle(8.5, -3.2, 3.5));
        shapes.put("ring", TelegraphShape.ring(8.5, 8.5, 2.0, 4.5));
        shapes.put("line", TelegraphShape.line(7.5, 6.5, 0.6, 9, 1.4));
        shapes.put("cone", TelegraphShape.cone(8.5, 8.5, 2.0, 6, Math.toRadians(70)));
        shapes.put("cone-wrap", TelegraphShape.cone(0, 0, Math.PI - 0.1, 7, Math.toRadians(50)));
        shapes.put("sweep", TelegraphShape.sweep(8.5, 8.5, 0.3, 1.2, 8, 1.1));
        shapes.put("sweep-back", TelegraphShape.sweep(-4, 5, 2.0, -1.5, 9, 1.0));
        shapes.put("wave", TelegraphShape.wave(8.5, 8.5, 2.0, 8, 0.9, new double[] {1.0, 1.0 + Math.PI}, Math.toRadians(22)));
        shapes.put("wave-wrap", TelegraphShape.wave(0, 0, 3.0, 12, 1.2, new double[] {0.0, 2.2, 4.1}, Math.toRadians(18)));
        shapes.put("wave-nogap", TelegraphShape.wave(0, 0, 3.0, 9, 1.0, new double[0], 0.3));
        Random rnd = new Random(4242);
        int points = 0;
        for (var e : shapes.entrySet()) {
            String name = e.getKey();
            TelegraphShape shape = e.getValue();
            // 当たる範囲の全体 (動くものは、いつかどこかで当たる所)
            BiPredicate<Double, Double> everHit = everHit(shape);
            List<double[]> area = tris(g -> TelegraphGeometry.area(shape, g));
            double[] b = shape.bounds();
            int checked = 0;
            int bad = 0;
            for (int i = 0; i < 5000; i++) {
                double x = b[0] - 0.5 + rnd.nextDouble() * (b[2] - b[0] + 1);
                double z = b[1] - 0.5 + rnd.nextDouble() * (b[3] - b[1] + 1);
                if (ambiguous(everHit, x, z)) {
                    continue;
                }
                checked++;
                if (covered(area, x, z) != everHit.test(x, z)) {
                    bad++;
                }
            }
            check(bad == 0, name + ": " + bad + " of " + checked + " sampled points disagree between the drawn area and the hit area", f);
            check(checked > 3000, name + ": too few usable sample points (" + checked + ")", f);
            points += checked;

            // 満ちる面: 内側だけ・進むほど広がる・最後は全体
            List<double[]> prev = List.of();
            boolean isWave = shape.kind() == TelegraphShape.KIND_WAVE;
            for (double p : new double[] {0.25, 0.6, 1.0}) {
                List<double[]> fill = tris(g -> TelegraphGeometry.fill(shape, p, g));
                int outside = 0;
                int shrank = 0;
                int notFull = 0;
                for (int i = 0; i < 1500; i++) {
                    double x = b[0] + rnd.nextDouble() * (b[2] - b[0]);
                    double z = b[1] + rnd.nextDouble() * (b[3] - b[1]);
                    if (ambiguous(everHit, x, z)) {
                        continue;
                    }
                    boolean in = covered(fill, x, z);
                    if (in && !covered(area, x, z)) {
                        outside++;
                    }
                    if (!prev.isEmpty() && covered(prev, x, z) && !in && !isWave) {
                        shrank++;
                    }
                    if (p == 1.0 && !isWave && shape.kind() != TelegraphShape.KIND_SWEEP && covered(area, x, z) && !in) {
                        notFull++;
                    }
                }
                check(outside == 0, name + ": the fill at " + p + " leaves the area (" + outside + " points)", f);
                check(shrank == 0, name + ": the fill shrank when it advanced to " + p, f);
                check(notFull == 0, name + ": the fill at 1.0 does not reach the whole area (" + notFull + " points)", f);
                prev = fill;
            }

            // 動く形: いまの形 (at) の面も contains と一致する
            if (shape.kind() == TelegraphShape.KIND_WAVE || shape.kind() == TelegraphShape.KIND_SWEEP) {
                for (double p : new double[] {0.0, 0.37, 1.0}) {
                    TelegraphShape now = shape.at(p);
                    List<double[]> nowTris = tris(g -> TelegraphGeometry.area(now, g));
                    BiPredicate<Double, Double> pred = now::contains;
                    int mis = 0;
                    for (int i = 0; i < 2500; i++) {
                        double x = b[0] + rnd.nextDouble() * (b[2] - b[0]);
                        double z = b[1] + rnd.nextDouble() * (b[3] - b[1]);
                        if (ambiguous(pred, x, z)) {
                            continue;
                        }
                        if (covered(nowTris, x, z) != pred.test(x, z)) {
                            mis++;
                        }
                    }
                    check(mis == 0, name + ": the current shape at " + p + " disagrees with its drawn area (" + mis + " points)", f);
                }
            }
            // 振り回す帯: 通ったあとの扇は、通った帯の内側だけ
            if (shape.kind() == TelegraphShape.KIND_SWEEP) {
                List<double[]> swept = tris(g -> TelegraphGeometry.swept(shape, 0.5, g));
                TelegraphShape mid = shape.at(0.5);
                int outside = 0;
                for (int i = 0; i < 1500; i++) {
                    double x = b[0] + rnd.nextDouble() * (b[2] - b[0]);
                    double z = b[1] + rnd.nextDouble() * (b[3] - b[1]);
                    if (covered(swept, x, z) && !everHit.test(x, z) && !mid.contains(x, z)) {
                        outside++;
                    }
                }
                check(outside == 0, name + ": the swept area leaves the sweep (" + outside + " points)", f);
            }
            // 波の安全な筋: 輪の通り道を、面と筋で二分する (どの輪も通らない所だけが筋)
            if (isWave) {
                List<double[]> lanes = tris(g -> TelegraphGeometry.lanes(shape, g));
                TelegraphShape.Wave w = (TelegraphShape.Wave) shape;
                int both = 0;
                int neither = 0;
                int n = 0;
                for (int i = 0; i < 3000; i++) {
                    double x = w.cx() + (rnd.nextDouble() * 2 - 1) * (w.r1() + w.halfThickness());
                    double z = w.cz() + (rnd.nextDouble() * 2 - 1) * (w.r1() + w.halfThickness());
                    double d = Math.hypot(x - w.cx(), z - w.cz());
                    if (d < Math.max(0, w.r0() - w.halfThickness()) + 0.2 || d > w.r1() + w.halfThickness() - 0.2 || ambiguous(everHit, x, z)) {
                        continue;
                    }
                    n++;
                    boolean a = covered(area, x, z);
                    boolean l = covered(lanes, x, z);
                    if (a && l) {
                        both++;
                    }
                    if (!a && !l && w.gaps().length > 0) {
                        neither++;
                    }
                }
                check(both == 0, name + ": a point is both a safe lane and a hit area (" + both + " of " + n + ")", f);
                check(neither == 0 || w.gaps().length == 0, name + ": a point of the wave path is neither hit area nor safe lane (" + neither + " of " + n + ")", f);
            }

            // 縁の線 (静止した形): 線分の真ん中は、範囲の縁の上 (近くで中と外が混ざる)
            if (!isWave && shape.kind() != TelegraphShape.KIND_SWEEP) {
                List<double[]> segs = new ArrayList<>();
                TelegraphGeometry.outline(shape, (x0, z0, x1, z1) -> segs.add(new double[] {x0, z0, x1, z1}));
                int off = 0;
                for (double[] s : segs) {
                    double mx = (s[0] + s[2]) / 2;
                    double mz = (s[1] + s[3]) / 2;
                    if (!ambiguousStrict(shape::contains, mx, mz)) {
                        off++;
                    }
                }
                check(!segs.isEmpty() && off == 0, name + ": " + off + " of " + segs.size() + " outline segments are not on the edge of the area", f);
            }
        }

        // 面の数 (重い描画にならない)
        TelegraphShape big = TelegraphShape.circle(0, 0, 19);
        int triCount = tris(g -> TelegraphGeometry.area(big, g)).size();
        check(triCount < 3000, "a radius-19 circle needs " + triCount + " triangles (limit 3000)", f);

        payloads(server, shapes, f, rnd);
        notes.add("decal: " + shapes.size() + " shapes, " + points + " sampled points (drawn area equals hit area), circle r19 = " + triCount + " triangles, packet round trip");
    }

    // ---------------------------------------------------------------- パケット

    private static void payloads(MinecraftServer server, Map<String, TelegraphShape> shapes, List<String> f, Random rnd) {
        for (var e : shapes.entrySet()) {
            TelegraphShape shape = e.getValue();
            TelegraphStartPayload sent = TelegraphStartPayload.of(1234, shape, -60.0, 3, 28, shape.kind() == TelegraphShape.KIND_SWEEP ? 12 : 0, 0xFF5A5A);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            TelegraphStartPayload.STREAM_CODEC.encode(buf, sent);
            int bytes = buf.readableBytes();
            TelegraphStartPayload got = TelegraphStartPayload.STREAM_CODEC.decode(buf);
            String name = e.getKey();
            check(buf.readableBytes() == 0, name + ": the packet decode left " + buf.readableBytes() + " bytes", f);
            check(got.id() == 1234 && got.kind() == shape.kind() && got.floor() == -60f && got.ageAtSend() == 3 && got.warn() == 28
                    && got.active() == sent.active() && got.rgb() == 0xFF5A5A && java.util.Arrays.equals(got.params(), sent.params()),
                    name + ": a packet field changed on the round trip", f);
            check(bytes < 120, name + ": the start packet is " + bytes + " bytes (compact: under 120)", f);
            TelegraphShape back = got.shape();
            check(back != null && back.getClass() == shape.getClass(), name + ": the shape came back as " + (back == null ? "null" : back.getClass().getSimpleName()), f);
            if (back != null) {
                double[] b = shape.bounds();
                BiPredicate<Double, Double> a = everHit(shape);
                BiPredicate<Double, Double> c = everHit(back);
                int mis = 0;
                for (int i = 0; i < 2000; i++) {
                    double x = b[0] + rnd.nextDouble() * (b[2] - b[0]);
                    double z = b[1] + rnd.nextDouble() * (b[3] - b[1]);
                    if (!ambiguous(a, x, z) && a.test(x, z) != c.test(x, z)) {
                        mis++;
                    }
                }
                check(mis == 0, name + ": the decoded shape disagrees with the original on " + mis + " points", f);
            }
        }
        check(TelegraphShape.decode(99, new double[] {1, 2, 3}) == null, "an unknown shape kind should decode to null", f);
        check(TelegraphShape.decode(TelegraphShape.KIND_CIRCLE, new double[] {1, 2}) == null, "a short circle should decode to null", f);
        RegistryFriendlyByteBuf bad = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        bad.writeVarInt(1);
        bad.writeByte(0);
        bad.writeVarInt(100000);
        boolean refused = false;
        try {
            TelegraphStartPayload.STREAM_CODEC.decode(bad);
        } catch (RuntimeException ex) {
            refused = true;
        }
        check(refused, "a packet with an absurd parameter count should be refused", f);
        RegistryFriendlyByteBuf cancel = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        TelegraphCancelPayload.STREAM_CODEC.encode(cancel, new TelegraphCancelPayload(77));
        check(TelegraphCancelPayload.STREAM_CODEC.decode(cancel).id() == 77, "the cancel packet lost its id", f);
    }

    // ---------------------------------------------------------------- 部品

    // いつかどこかで当たる所 (動く形は、その途中の形のどれかに入る点)
    private static BiPredicate<Double, Double> everHit(TelegraphShape shape) {
        if (shape.kind() == TelegraphShape.KIND_WAVE || shape.kind() == TelegraphShape.KIND_SWEEP) {
            int n = shape.kind() == TelegraphShape.KIND_WAVE ? 96 : 240;
            List<TelegraphShape> steps = new ArrayList<>();
            for (int i = 0; i <= n; i++) {
                steps.add(shape.at((double) i / n));
            }
            return (x, z) -> {
                for (TelegraphShape s : steps) {
                    if (s.contains(x, z)) {
                        return true;
                    }
                }
                return false;
            };
        }
        return shape::contains;
    }

    // 縁のすぐ近く (十字に振った点のどれかが違う判定) の点は、突き合わせから外す
    private static boolean ambiguous(BiPredicate<Double, Double> pred, double x, double z) {
        boolean v = pred.test(x, z);
        return pred.test(x + JITTER, z) != v || pred.test(x - JITTER, z) != v || pred.test(x, z + JITTER) != v || pred.test(x, z - JITTER) != v
                || pred.test(x + JITTER * 0.7, z + JITTER * 0.7) != v || pred.test(x - JITTER * 0.7, z - JITTER * 0.7) != v
                || pred.test(x + JITTER * 0.7, z - JITTER * 0.7) != v || pred.test(x - JITTER * 0.7, z + JITTER * 0.7) != v;
    }

    // 縁の上の点 (近くで中と外が混ざる)。弦の誤差の分だけ、少し広めに見る
    private static boolean ambiguousStrict(BiPredicate<Double, Double> pred, double x, double z) {
        return ambiguous(pred, x, z);
    }

    private static List<double[]> tris(java.util.function.Consumer<TelegraphGeometry.Tri> source) {
        List<double[]> out = new ArrayList<>();
        source.accept((x0, z0, x1, z1, x2, z2) -> out.add(new double[] {x0, z0, x1, z1, x2, z2}));
        return out;
    }

    private static boolean covered(List<double[]> tris, double x, double z) {
        for (double[] t : tris) {
            double d1 = (x - t[2]) * (t[1] - t[3]) - (t[0] - t[2]) * (z - t[3]);
            double d2 = (x - t[4]) * (t[3] - t[5]) - (t[2] - t[4]) * (z - t[5]);
            double d3 = (x - t[0]) * (t[5] - t[1]) - (t[4] - t[0]) * (z - t[1]);
            boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
            boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
            if (!(neg && pos)) {
                return true;
            }
        }
        return false;
    }

    private static void check(boolean ok, String message, List<String> f) {
        if (!ok) {
            f.add("decal: " + message);
        }
    }
}
