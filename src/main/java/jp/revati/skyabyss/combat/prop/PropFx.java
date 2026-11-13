package jp.revati.skyabyss.combat.prop;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// 予告 (combat/Telegraph) に付ける技の形 (2026-11-12 の声「敵の攻撃も近接等の直接攻撃以外は全部攻撃モデルを用意しよう」)。
// Telegraph.Builder.prop(PropFx.xxx("種類")) と書くだけで、予告の形に合わせて模型 (combat/prop/AttackProp) が出る。
// look (粒) から自動で出す形より先に使う (prop があれば look からは出さない)。粒はそのまま残る (模型が主役、粒は脇役)。
//   rise      打つ瞬間、真ん中の床から一本突き出る (大きさは予告の広さから)
//   scatter   打つ瞬間、円の中の n か所から小さく突き出る
//   ring      打つ瞬間、輪 (円なら縁) に沿って n 本突き出る
//   along     打つ瞬間、帯に沿って間をあけて突き出る (扇なら真ん中の筋に沿って)
//   fall      予告の始まりから、真ん中へ空から落ちる (打つ瞬間に着く)
//   rain      予告の始まりから、円の中の n か所へ空から落ちる
//   toss      予告の始まりから、持ち主から真ん中 (帯・扇なら先) へ弧を描いて飛ぶ
//   shots     予告の始まりから、持ち主から帯・扇の先へ n 発まっすぐ飛ぶ (扇は広がりに散らす)
public record PropFx(Mode mode, String kind, float size, int count, int linger) {
    public enum Mode {
        RISE, SCATTER, RING, ALONG, FALL, RAIN, TOSS, SHOTS
    }

    public static PropFx rise(String kind) {
        return new PropFx(Mode.RISE, kind, 0f, 1, 18);
    }

    public static PropFx scatter(String kind, int n) {
        return new PropFx(Mode.SCATTER, kind, 0f, n, 14);
    }

    public static PropFx ring(String kind, int n) {
        return new PropFx(Mode.RING, kind, 0f, n, 12);
    }

    public static PropFx along(String kind) {
        return new PropFx(Mode.ALONG, kind, 0f, 0, 14);
    }

    public static PropFx fall(String kind) {
        return new PropFx(Mode.FALL, kind, 0f, 1, 14);
    }

    public static PropFx rain(String kind, int n) {
        return new PropFx(Mode.RAIN, kind, 0f, n, 10);
    }

    public static PropFx toss(String kind) {
        return new PropFx(Mode.TOSS, kind, 0f, 1, 10);
    }

    public static PropFx shots(String kind, int n) {
        return new PropFx(Mode.SHOTS, kind, 0f, n, 4);
    }

    // 大きさを決めて (0 = 予告の広さから)
    public PropFx size(float s) {
        return new PropFx(mode, kind, s, count, linger);
    }

    public PropFx linger(int ticks) {
        return new PropFx(mode, kind, size, count, ticks);
    }

    boolean atStart() {
        return mode == Mode.FALL || mode == Mode.RAIN || mode == Mode.TOSS || mode == Mode.SHOTS;
    }

    // ---------------------------------------------------------------- 出す (Telegraph が呼ぶ)
    // 予告の始まり: 落ちる・飛ぶ形
    public static void start(ServerLevel level, @Nullable PropFx fx, TelegraphShape shape, double floor, int warn, @Nullable Entity owner) {
        if (fx == null || !fx.atStart()) {
            return;
        }
        Vec3 c = center(shape, floor);
        float auto = autoSize(shape);
        switch (fx.mode) {
            case FALL -> AttackProps.fall(level, fx.kind, owner, c, warn, 14, 16f, fx.size > 0 ? fx.size : auto, fx.linger);
            case RAIN -> {
                double r = radius(shape);
                for (int i = 0; i < fx.count; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double d = r * Math.sqrt(level.random.nextDouble()) * 0.85;
                    Vec3 at = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    // 着く時刻を少しずつずらす (同時に落ちると一つの塊に見える)
                    int w = Math.max(4, warn - level.random.nextInt(Math.max(1, Math.min(6, warn / 4))));
                    AttackProps.fall(level, fx.kind, owner, at, w, 12, 14f, fx.size > 0 ? fx.size : 0.7f + level.random.nextFloat() * 0.3f, fx.linger);
                }
            }
            case TOSS -> {
                Vec3 to = far(shape, floor);
                Vec3 from = from(owner, shape, floor);
                AttackProps.toss(level, fx.kind, owner, from, to, warn, Math.min(warn, 20), 6f, fx.size > 0 ? fx.size : Math.min(1.4f, auto), fx.linger);
            }
            case SHOTS -> {
                Vec3 from = from(owner, shape, floor);
                double ang = angle(shape);
                double len = length(shape);
                double spread = shape instanceof TelegraphShape.Cone k ? k.halfAngle() * 0.8 : 0;
                double half = shape instanceof TelegraphShape.Line l ? l.halfWidth() : 0;
                int n = Math.max(1, fx.count);
                for (int i = 0; i < n; i++) {
                    double t = n == 1 ? 0 : (i / (double) (n - 1)) * 2 - 1;
                    double a = ang + spread * t;
                    double side = half * t * 0.6;
                    Vec3 to = new Vec3(from.x + Math.cos(a) * len - Math.sin(a) * side, floor + 0.8, from.z + Math.sin(a) * len + Math.cos(a) * side);
                    Vec3 st = from.add(-Math.sin(a) * side, 0, Math.cos(a) * side);
                    // 帯は打つ瞬間に先へ着く。一発ずつ 2 tick ずらす
                    int w = Math.max(3, warn - (n - 1 - i) * 2);
                    AttackProps.toss(level, fx.kind, owner, st, to, w, Math.min(w, 10), 0.6f, fx.size > 0 ? fx.size : 1f, fx.linger);
                }
            }
            default -> {
            }
        }
    }

    // 打つ瞬間: 突き出る形
    public static void impact(ServerLevel level, @Nullable PropFx fx, TelegraphShape shape, double floor, @Nullable Entity owner) {
        if (fx == null || fx.atStart()) {
            return;
        }
        Vec3 c = center(shape, floor);
        float auto = autoSize(shape);
        switch (fx.mode) {
            case RISE -> AttackProps.rise(level, fx.kind, owner, c, level.random.nextFloat() * 360f, fx.size > 0 ? fx.size : auto, fx.linger);
            case SCATTER -> {
                double r = radius(shape);
                for (int i = 0; i < fx.count; i++) {
                    double a = (i + level.random.nextDouble() * 0.7) / fx.count * Math.PI * 2;
                    double d = i == 0 ? 0 : r * (0.35 + 0.5 * level.random.nextDouble());
                    AttackProps.rise(level, fx.kind, owner, c.add(Math.cos(a) * d, 0, Math.sin(a) * d), level.random.nextFloat() * 360f,
                            (fx.size > 0 ? fx.size : 0.75f) * (0.8f + 0.4f * level.random.nextFloat()), fx.linger);
                }
            }
            case RING -> {
                double r = shape instanceof TelegraphShape.Ring g ? (g.inner() + g.outer()) / 2 : radius(shape) * 0.85;
                float sz = fx.size > 0 ? fx.size : (float) Mth.clamp((shape instanceof TelegraphShape.Ring g ? g.outer() - g.inner() : 2.0) / 3.0, 0.55, 1.2);
                double off = level.random.nextDouble();
                for (int i = 0; i < fx.count; i++) {
                    double a = (i + off) / fx.count * Math.PI * 2;
                    AttackProps.rise(level, fx.kind, owner, c.add(Math.cos(a) * r, 0, Math.sin(a) * r), (float) Math.toDegrees(a) + 90f,
                            sz * (0.85f + 0.3f * level.random.nextFloat()), fx.linger);
                }
            }
            case ALONG -> {
                Vec3 o = origin(shape, floor);
                double ang = angle(shape);
                double len = length(shape);
                double half = shape instanceof TelegraphShape.Line l ? l.halfWidth() : 1.0;
                float sz = fx.size > 0 ? fx.size : (float) Mth.clamp(half * 0.55, 0.5, 1.1);
                double step = Math.max(1.6, half * 2.2);
                int n = 0;
                for (double d = Math.min(1.8, len * 0.3); d < len && n < 12; d += step, n++) {
                    AttackProps.rise(level, fx.kind, owner, o.add(Math.cos(ang) * d, 0, Math.sin(ang) * d), (float) Math.toDegrees(ang) + n * 53f,
                            sz * (0.85f + 0.3f * level.random.nextFloat()), fx.linger);
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 動く予告 (広がる輪 Wave・振り回す帯 Sweep)
    // 打つ瞬間から active tick のあいだに、輪の頭・帯の腕が通る所へ順に突き出す (riseLater)。輪の安全な筋 (gaps) には出さない。
    // kind = null なら何もしない。size 0 = 輪の厚さ・帯の幅から
    public static void motion(ServerLevel level, @Nullable String kind, float size, TelegraphShape shape, double floor, int active, @Nullable Entity owner) {
        if (kind == null) {
            return;
        }
        int span = Math.max(8, active);
        int budget = 110;
        if (shape instanceof TelegraphShape.Wave w) {
            float sz = size > 0 ? size : (float) Mth.clamp(w.halfThickness() * 0.75, 0.45, 0.9);
            int steps = Math.max(2, (int) Math.ceil((w.r1() - w.r0()) / 3.0));
            for (int k = 0; k <= steps && budget > 0; k++) {
                double r = w.r0() + (w.r1() - w.r0()) * k / steps;
                int n = (int) Mth.clamp(Math.round(2 * Math.PI * r / 3.2), 5, 20);
                double off = level.random.nextDouble() * Math.PI * 2 / n;
                int delay = (int) Math.round(span * (double) k / steps);
                for (int i = 0; i < n && budget > 0; i++) {
                    double a = off + i * Math.PI * 2 / n;
                    if (inGap(w.gaps(), w.gapHalf(), a)) {
                        continue;
                    }
                    Vec3 at = new Vec3(w.cx() + Math.cos(a) * r, floor, w.cz() + Math.sin(a) * r);
                    AttackProps.riseLater(level, kind, owner, at, (float) Math.toDegrees(a) + 90f, sz * (0.85f + 0.3f * level.random.nextFloat()), 6, delay);
                    budget--;
                }
            }
        } else if (shape instanceof TelegraphShape.Sweep sw) {
            float sz = size > 0 ? size : (float) Mth.clamp(sw.halfWidth() * 0.6, 0.45, 0.9);
            int steps = (int) Mth.clamp(Math.round(Math.abs(sw.span()) / Math.toRadians(14)), 3, 12);
            for (int k = 0; k <= steps && budget > 0; k++) {
                double a = sw.start() + sw.span() * k / steps;
                int delay = (int) Math.round(span * (double) k / steps);
                for (double d = 2.0; d <= sw.length() && budget > 0; d += 2.6) {
                    Vec3 at = new Vec3(sw.x() + Math.cos(a) * d, floor, sw.z() + Math.sin(a) * d);
                    AttackProps.riseLater(level, kind, owner, at, (float) Math.toDegrees(a) + k * 37f, sz * (0.85f + 0.3f * level.random.nextFloat()), 6, delay);
                    budget--;
                }
            }
        }
    }

    static boolean inGap(double[] gaps, double gapHalf, double a) {
        for (double g : gaps) {
            double d = Math.abs(Mth.wrapDegrees(Math.toDegrees(a - g)));
            if (Math.toRadians(d) <= gapHalf) {
                return true;
            }
        }
        return false;
    }

    // 動く予告の、見た目 (AttackLook) の属性からの形 (石 → 骨の棘、水 → 水の柱、雷 → 晶の棘、余燼 → 熔岩の柱)。見た目が無ければ null。
    // 咆哮 (*_ROAR) は押し返すだけなので、攻撃の形は出さない (2026-11-13 の声「咆哮に攻撃のモデル使ったらだめじゃない？飛ぶだけだから」)
    @Nullable
    public static String motionKind(@Nullable jp.revati.skyabyss.combat.AttackLook look) {
        if (look == null || look == jp.revati.skyabyss.combat.AttackLook.NONE || look.roar()) {
            return null;
        }
        return switch (look.element) {
            case WATER -> "water_spout";
            case STORM -> "crystal_spike";
            case EMBER -> "lava_pillar";
            default -> "bone_spike";
        };
    }

    // ---------------------------------------------------------------- 形の測り
    static Vec3 center(TelegraphShape s, double floor) {
        return new Vec3(s.centerX(), floor, s.centerZ());
    }

    static float autoSize(TelegraphShape s) {
        return (float) Mth.clamp(radius(s) / 2.0, 0.7, 2.4);
    }

    static double radius(TelegraphShape s) {
        if (s instanceof TelegraphShape.Circle c) {
            return c.r();
        }
        if (s instanceof TelegraphShape.Ring g) {
            return g.outer();
        }
        double[] b = s.bounds();
        return Math.max(b[2] - b[0], b[3] - b[1]) / 2;
    }

    // 帯・扇の根元 (円は真ん中)
    static Vec3 origin(TelegraphShape s, double floor) {
        if (s instanceof TelegraphShape.Line l) {
            return new Vec3(l.x(), floor, l.z());
        }
        if (s instanceof TelegraphShape.Cone k) {
            return new Vec3(k.x(), floor, k.z());
        }
        return center(s, floor);
    }

    static double angle(TelegraphShape s) {
        if (s instanceof TelegraphShape.Line l) {
            return l.angle();
        }
        if (s instanceof TelegraphShape.Cone k) {
            return k.angle();
        }
        return 0;
    }

    static double length(TelegraphShape s) {
        if (s instanceof TelegraphShape.Line l) {
            return l.length();
        }
        if (s instanceof TelegraphShape.Cone k) {
            return k.range();
        }
        return radius(s);
    }

    // 飛ぶ物の行き先: 帯・扇は先、円は真ん中
    static Vec3 far(TelegraphShape s, double floor) {
        if (s instanceof TelegraphShape.Line || s instanceof TelegraphShape.Cone) {
            Vec3 o = origin(s, floor);
            double a = angle(s);
            double len = length(s);
            return o.add(Math.cos(a) * len, 0.6, Math.sin(a) * len);
        }
        return center(s, floor);
    }

    // 飛ぶ物の出どころ: 持ち主の胸の高さ (いなければ帯の根元・円の上)
    static Vec3 from(@Nullable Entity owner, TelegraphShape s, double floor) {
        if (owner != null) {
            return owner.position().add(0, owner.getBbHeight() * 0.65, 0);
        }
        Vec3 o = origin(s, floor);
        return s instanceof TelegraphShape.Circle ? o.add(0, 12, 0) : o.add(0, 1.4, 0);
    }
}
