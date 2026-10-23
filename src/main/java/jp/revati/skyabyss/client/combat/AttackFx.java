package jp.revati.skyabyss.client.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.AttackLook;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 心臓の技・領域の決まりの見た目 (combat/AttackLook の四つの段: 溜め・運び・当たり・残り)。床の赤い印は TelegraphRenderer のまま、これは粒だけ足す。
// 粒の数: 1 tick の上限 (BASE_BUDGET。画面の粒の設定で減る) を、いま見た目のある予告の数で分ける。人数が増えて予告が増えても、合計は上限を超えない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AttackFx {
    private static final int BASE_BUDGET = 900;
    // 全体の濃さ (2026-10-23 の撮影で「まだ薄い」ため 2.2 倍。上限 BASE_BUDGET は変わらず効く)
    private static final double INTENSITY = 2.2;
    // 当たったあとの残りの長さ (tick)
    private static final int LINGER_TICKS = 26;
    private static final int SHOCK_TICKS = 8;

    private static int budget;
    private static long budgetTick = Long.MIN_VALUE;
    private static int looksThisTick;
    private static int looksLastTick = 1;

    private record Lingering(TelegraphClient.Decal decal, AttackLook look, long start) {
    }

    private static final List<Lingering> LINGER = new ArrayList<>();

    private AttackFx() {
    }

    // ---------------------------------------------------------------- 上限

    private static void refresh(ClientLevel level) {
        long now = level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            looksLastTick = Math.max(1, looksThisTick);
            looksThisTick = 0;
            float scale = switch (Minecraft.getInstance().options.particles().get()) {
                case ALL -> 1f;
                case DECREASED -> 0.5f;
                case MINIMAL -> 0.2f;
            };
            budget = Math.round(BASE_BUDGET * scale);
        }
    }

    // この予告に今 tick 使ってよい数 (want は「一つだけのとき」の数)
    private static int take(ClientLevel level, AttackLook look, double want) {
        refresh(level);
        double share = Math.max(0.3, Math.min(1.0, 6.0 / looksLastTick));
        int n = (int) Math.round(want * INTENSITY * look.weight * share);
        if (n <= 0 && want > 0 && level.random.nextDouble() < want * INTENSITY * share) {
            n = 1;
        }
        n = Math.min(n, budget);
        budget -= Math.max(0, n);
        return Math.max(0, n);
    }

    // ---------------------------------------------------------------- 属性ごとの粒

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
    }

    private static final DustParticleOptions BONE_DUST = dust(0xE8DCC0, 1.8f);
    private static final DustParticleOptions CORE_CYAN = dust(0x8CF6FF, 1.7f);
    private static final DustParticleOptions FOAM = dust(0xF2FFFF, 2.0f);
    private static final DustParticleOptions SEA = dust(0x58D6E8, 1.7f);
    private static final DustParticleOptions VOLT = dust(0xE6F6FF, 1.6f);
    private static final DustParticleOptions VOLT_GOLD = dust(0xFFF0A0, 1.4f);
    private static final DustParticleOptions HEAT = dust(0xFF8A2A, 1.8f);
    private static final DustParticleOptions CINDER = dust(0xFFC860, 1.3f);

    private static ParticleOptions debris(AttackLook.Element el, RandomSource r) {
        return switch (el) {
            case STONE -> new BlockParticleOption(ParticleTypes.BLOCK, switch (r.nextInt(3)) {
                case 0 -> Blocks.BONE_BLOCK.defaultBlockState();
                case 1 -> Blocks.DEEPSLATE.defaultBlockState();
                default -> Blocks.STONE.defaultBlockState();
            });
            case WATER -> r.nextBoolean() ? ParticleTypes.SPLASH : new BlockParticleOption(ParticleTypes.BLOCK,
                    r.nextBoolean() ? Blocks.PRISMARINE.defaultBlockState() : Blocks.BRAIN_CORAL_BLOCK.defaultBlockState());
            // 雷: 羽 (StormFeatherParticle) と火花。end_rod は目の近くで大きな白い四角に見えた
            case STORM -> r.nextInt(3) == 0 ? feather() : ParticleTypes.ELECTRIC_SPARK;
            case EMBER -> r.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME;
        };
    }

    // 細かい粒 (吸い込み・湧き上がり。速さに従う種類)
    private static ParticleOptions mote(AttackLook.Element el, RandomSource r) {
        return switch (el) {
            case STONE -> r.nextBoolean() ? ParticleTypes.CRIT : new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState());
            case WATER -> r.nextInt(3) == 0 ? ParticleTypes.GLOW : ParticleTypes.SPLASH;
            case STORM -> r.nextBoolean() ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD;
            case EMBER -> r.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME;
        };
    }

    // 色の粒 (その場で光る)
    private static ParticleOptions tint(AttackLook.Element el, RandomSource r) {
        return switch (el) {
            case STONE -> r.nextInt(3) == 0 ? CORE_CYAN : BONE_DUST;
            case WATER -> r.nextBoolean() ? FOAM : SEA;
            case STORM -> r.nextBoolean() ? VOLT : VOLT_GOLD;
            case EMBER -> r.nextBoolean() ? HEAT : CINDER;
        };
    }

    // 煙・しぶきの塊
    private static ParticleOptions puff(AttackLook.Element el, RandomSource r) {
        return switch (el) {
            case STONE -> r.nextBoolean() ? ParticleTypes.POOF : ParticleTypes.DUST_PLUME;
            case WATER -> r.nextBoolean() ? ParticleTypes.CLOUD : ParticleTypes.SPLASH;
            case STORM -> ParticleTypes.CLOUD;
            case EMBER -> r.nextBoolean() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE;
        };
    }

    // 空から落ちる兆し
    private static ParticleOptions falling(AttackLook.Element el, RandomSource r) {
        return switch (el) {
            case STONE -> new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState());
            case WATER -> ParticleTypes.FALLING_WATER;
            // 雷の空から降るもの = 雷翼の羽 (羽の雨)。小さく、揺れて回り、目の近くでは薄れる
            case STORM -> feather();
            case EMBER -> r.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.FALLING_LAVA;
        };
    }

    private static ParticleOptions feather() {
        return jp.revati.skyabyss.rpg.skill.Skills.STORM_FEATHER.get();
    }

    // ---------------------------------------------------------------- 形の点

    private static double ground(TelegraphClient.Decal d, double x, double z) {
        double y = d.ground(x, z);
        return Double.isNaN(y) ? d.floor : y;
    }

    private static double[] center(TelegraphShape s) {
        double[] b = s.bounds();
        if (s.kind() == TelegraphShape.KIND_CIRCLE || s.kind() == TelegraphShape.KIND_RING || s.kind() == TelegraphShape.KIND_WAVE) {
            return new double[] {s.centerX(), s.centerZ()};
        }
        return new double[] {(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
    }

    private static double halfSize(TelegraphShape s) {
        double[] b = s.bounds();
        return Math.max(0.8, Math.max(b[2] - b[0], b[3] - b[1]) / 2);
    }

    // 形の中の点 (見つからなければ null)
    private static double[] inside(TelegraphShape s, RandomSource r) {
        double[] b = s.bounds();
        for (int i = 0; i < 8; i++) {
            double x = b[0] + r.nextDouble() * (b[2] - b[0]);
            double z = b[1] + r.nextDouble() * (b[3] - b[1]);
            if (s.contains(x, z)) {
                return new double[] {x, z};
            }
        }
        return null;
    }

    private static List<double[]> outline(TelegraphShape s, int max) {
        List<double[]> pts = new ArrayList<>();
        double spacing = Math.max(0.5, halfSize(s) * 2 * Math.PI / Math.max(8, max * 2));
        s.outline((x, z) -> pts.add(new double[] {x, z}), spacing);
        return thin(pts, max);
    }

    private static List<double[]> edge(TelegraphShape s, double progress, int max) {
        List<double[]> pts = new ArrayList<>();
        s.edge(progress, (x, z) -> pts.add(new double[] {x, z}), Math.max(0.5, halfSize(s) / 10));
        return thin(pts, max);
    }

    private static List<double[]> thin(List<double[]> pts, int max) {
        if (pts.size() <= max || max <= 0) {
            return max <= 0 ? List.of() : pts;
        }
        List<double[]> out = new ArrayList<>(max);
        double step = (double) pts.size() / max;
        for (int i = 0; i < max; i++) {
            out.add(pts.get((int) (i * step)));
        }
        return out;
    }

    // 技の元 (主の体の中ほど)。主が見えなければ形の中心の少し上
    private static double[] source(ClientLevel level, TelegraphClient.Decal d) {
        Entity e = d.owner >= 0 ? level.getEntity(d.owner) : null;
        if (e != null) {
            return new double[] {e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ()};
        }
        double[] c = center(d.shape);
        return new double[] {c[0], ground(d, c[0], c[1]) + 2.5, c[1]};
    }

    // 帯・扇の「先」 (運びの終わり)
    private static double[][] axis(ClientLevel level, TelegraphClient.Decal d) {
        double[] p = d.shape.params();
        int k = d.shape.kind();
        if (k == TelegraphShape.KIND_LINE || k == TelegraphShape.KIND_CONE) {
            double len = p[3];
            double x0 = p[0];
            double z0 = p[1];
            return new double[][] {{x0, z0}, {x0 + Math.cos(p[2]) * len, z0 + Math.sin(p[2]) * len}};
        }
        double[] s = source(level, d);
        double[] c = center(d.shape);
        return new double[][] {{s[0], s[2]}, c};
    }

    // ---------------------------------------------------------------- 毎 tick (TelegraphClient から)

    static void tick(ClientLevel level, TelegraphClient.Decal d, int t) {
        AttackLook look = d.look;
        if (look == AttackLook.NONE || t < 0) {
            return;
        }
        refresh(level);
        looksThisTick++;
        if (t < d.warn) {
            double p = Math.min(1.0, (double) t / Math.max(1, d.warn));
            windup(level, d, look, t, p);
            preTravel(level, d, look, t);
            return;
        }
        if (d.active > 0 && t < d.warn + d.active) {
            double p = Math.min(1.0, (double) (t - d.warn) / d.active);
            during(level, d, look, d.shape.at(p));
        } else if (t < d.warn + 3 && look.travel == AttackLook.Travel.BEAM) {
            beam(level, d, look, d.shape.at(1.0), 0.6);
        }
    }

    // ---- 溜め
    private static void windup(ClientLevel level, TelegraphClient.Decal d, AttackLook look, int t, double p) {
        RandomSource r = level.random;
        TelegraphShape s = d.shape;
        AttackLook.Element el = look.element;
        switch (look.windup) {
            case GATHER -> {
                double[] src = source(level, d);
                int n = take(level, look, 2 + 6 * p);
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double rad = 3.5 + r.nextDouble() * 3.5;
                    double x = src[0] + Math.cos(a) * rad;
                    double z = src[2] + Math.sin(a) * rad;
                    double y = src[1] - 1.5 + r.nextDouble() * 3;
                    double k = 0.11 + 0.08 * p;
                    level.addParticle(el == AttackLook.Element.STONE ? ParticleTypes.CRIT : mote(el, r), x, y, z,
                            (src[0] - x) * k, (src[1] - y) * k, (src[2] - z) * k);
                }
                core(level, d, look, src, p);
            }
            case RISE -> {
                int n = take(level, look, 2 + 7 * p);
                for (int i = 0; i < n; i++) {
                    double[] q = inside(s, r);
                    if (q != null) {
                        double y = ground(d, q[0], q[1]);
                        level.addParticle(i % 3 == 0 ? tint(el, r) : mote(el, r), q[0], y + 0.1, q[1], 0, 0.04 + 0.12 * p, 0);
                    }
                }
            }
            case CREEP -> {
                List<double[]> front = edge(s, p, take(level, look, 6 + 4 * p));
                for (double[] q : front) {
                    double y = ground(d, q[0], q[1]);
                    level.addParticle(mote(el, r), q[0], y + 0.15, q[1], 0, 0.08, 0);
                }
                if (t % 3 == 0) {
                    for (double[] q : outline(s, take(level, look, 5))) {
                        level.addParticle(tint(el, r), q[0], ground(d, q[0], q[1]) + 0.2, q[1], 0, 0.01, 0);
                    }
                }
            }
            case CRACK -> {
                double[] c = center(s);
                double reach = halfSize(s) * p;
                int lines = s.kind() == TelegraphShape.KIND_LINE ? 1 : 6;
                int n = take(level, look, 2 + 4 * p);
                for (int i = 0; i < n; i++) {
                    int line = r.nextInt(lines);
                    double x;
                    double z;
                    if (s.kind() == TelegraphShape.KIND_LINE) {
                        double[] pr = s.params();
                        double along = pr[3] * p * (0.75 + 0.25 * r.nextDouble());
                        x = pr[0] + Math.cos(pr[2]) * along;
                        z = pr[1] + Math.sin(pr[2]) * along;
                    } else {
                        double a = line * Math.PI * 2 / lines + (d.id % 7) * 0.4 + Math.sin(reach * 1.7 + line) * 0.12;
                        double rr = reach * (0.8 + 0.2 * r.nextDouble());
                        x = c[0] + Math.cos(a) * rr;
                        z = c[1] + Math.sin(a) * rr;
                    }
                    if (!s.contains(x, z)) {
                        continue;
                    }
                    double y = ground(d, x, z);
                    level.addParticle(debris(el, r), x, y + 0.1, z, 0, 0.12, 0);
                    level.addParticle(puff(el, r), x, y + 0.1, z, 0, 0.02, 0);
                }
            }
            case FLICKER -> {
                int n = take(level, look, 1 + 4 * p);
                for (int i = 0; i < n; i++) {
                    double[] q = inside(s, r);
                    if (q != null) {
                        double y = ground(d, q[0], q[1]);
                        level.addParticle(mote(el, r), q[0], y + 0.1 + r.nextDouble() * 0.6, q[1], (r.nextDouble() - 0.5) * 0.2, 0.05, (r.nextDouble() - 0.5) * 0.2);
                    }
                }
                if (p > 0.65 && t % 3 == 0) {
                    double[] q = inside(s, r);
                    if (q != null) {
                        double y = ground(d, q[0], q[1]);
                        int m = take(level, look, 5);
                        for (int i = 0; i < m; i++) {
                            level.addParticle(tint(el, r), q[0], y + i * 0.6, q[1], 0, 0, 0);
                        }
                    }
                }
            }
            case SKY -> {
                int n = take(level, look, 2 + 4 * p);
                for (int i = 0; i < n; i++) {
                    double[] q = inside(s, r);
                    if (q != null) {
                        double y = ground(d, q[0], q[1]);
                        level.addParticle(falling(el, r), q[0], y + 7 + r.nextDouble() * 5, q[1], 0, -0.25 - 0.3 * p, 0);
                    }
                }
                // 落ちてくる影: 縁から締まる輪
                if (t % 2 == 0) {
                    double[] c = center(s);
                    double rad = halfSize(s) * (1.0 - 0.55 * p);
                    int m = take(level, look, 8);
                    for (int i = 0; i < m; i++) {
                        double a = Math.PI * 2 * i / Math.max(1, m) + t * 0.05;
                        double x = c[0] + Math.cos(a) * rad;
                        double z = c[1] + Math.sin(a) * rad;
                        if (s.contains(x, z)) {
                            level.addParticle(tint(el, r), x, ground(d, x, z) + 0.15, z, 0, 0, 0);
                        }
                    }
                }
            }
            case ORBIT -> {
                double[] src = source(level, d);
                int n = take(level, look, 6);
                double rad = 0.8 + 2.6 * (1 - p);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / Math.max(1, n) + t * (0.18 + 0.3 * p);
                    double y = src[1] + Math.sin(a * 2 + t * 0.2) * 0.6;
                    level.addParticle(i % 2 == 0 ? tint(el, r) : mote(el, r), src[0] + Math.cos(a) * rad, y, src[2] + Math.sin(a) * rad, 0, 0, 0);
                }
            }
            case SPIRAL -> {
                double[] c = center(s);
                double big = halfSize(s);
                int n = take(level, look, 4 + 6 * p);
                for (int i = 0; i < n; i++) {
                    double f = r.nextDouble();
                    double rad = big * (0.25 + 0.75 * f);
                    double a = t * 0.22 + f * 7 + i * 0.9;
                    double x = c[0] + Math.cos(a) * rad;
                    double z = c[1] + Math.sin(a) * rad;
                    if (!s.contains(x, z)) {
                        continue;
                    }
                    double y = ground(d, x, z);
                    // 渦: 接線の向き + 少し内へ
                    double tx = -Math.sin(a) * 0.18 - Math.cos(a) * 0.05;
                    double tz = Math.cos(a) * 0.18 - Math.sin(a) * 0.05;
                    level.addParticle(i % 3 == 0 ? puff(el, r) : mote(el, r), x, y + 0.2 + p * 0.8 * f, z, tx, 0.02 + 0.05 * p, tz);
                }
            }
            default -> {
            }
        }
        // 溜めの終わり際: 技の元が光る (吸い込み・回る技は上で描く)
        if (p > 0.45 && t % 3 == 0 && look.windup != AttackLook.Windup.GATHER && look.windup != AttackLook.Windup.ORBIT && d.owner >= 0) {
            core(level, d, look, source(level, d), p);
        }
    }

    private static void core(ClientLevel level, TelegraphClient.Decal d, AttackLook look, double[] src, double p) {
        RandomSource r = level.random;
        int n = take(level, look, 1 + 3 * p);
        for (int i = 0; i < n; i++) {
            level.addParticle(tint(look.element, r), src[0] + (r.nextDouble() - 0.5) * 1.2, src[1] + (r.nextDouble() - 0.5) * 1.2,
                    src[2] + (r.nextDouble() - 0.5) * 1.2, 0, 0, 0);
        }
    }

    // ---- 運び (当たる直前: 飛ぶ弾・落ちる塊)
    private static final int LEAD = 10;

    private static void preTravel(ClientLevel level, TelegraphClient.Decal d, AttackLook look, int t) {
        int left = d.warn - t;
        if (left > LEAD) {
            return;
        }
        double u = 1.0 - (double) left / LEAD;
        RandomSource r = level.random;
        AttackLook.Element el = look.element;
        switch (look.travel) {
            case ARC -> {
                double[] src = source(level, d);
                double[] c = center(d.shape);
                double gy = ground(d, c[0], c[1]);
                double x = src[0] + (c[0] - src[0]) * u;
                double z = src[2] + (c[1] - src[2]) * u;
                double y = src[1] + (gy - src[1]) * u + 4.5 * 4 * u * (1 - u);
                int n = take(level, look, 6);
                for (int i = 0; i < n; i++) {
                    level.addParticle(i < 2 ? tint(el, r) : mote(el, r), x + (r.nextDouble() - 0.5) * 0.5, y + (r.nextDouble() - 0.5) * 0.5,
                            z + (r.nextDouble() - 0.5) * 0.5, 0, 0, 0);
                }
                level.addParticle(puff(el, r), x, y, z, 0, 0.01, 0);
            }
            case FALL -> {
                double[] c = center(d.shape);
                double gy = ground(d, c[0], c[1]);
                double y = gy + 14 * (1 - u);
                int n = take(level, look, 7);
                double w = Math.min(2.2, halfSize(d.shape) * 0.4);
                for (int i = 0; i < n; i++) {
                    level.addParticle(i % 2 == 0 ? debris(el, r) : falling(el, r), c[0] + (r.nextDouble() - 0.5) * w, y + r.nextDouble(),
                            c[1] + (r.nextDouble() - 0.5) * w, 0, -0.6, 0);
                }
            }
            case SHOTS -> {
                double[][] ax = axis(level, d);
                double[] src = source(level, d);
                int n = take(level, look, 9);
                for (int k = 0; k < 3; k++) {
                    double uu = u * 1.4 - k * 0.2;
                    if (uu < 0 || uu > 1) {
                        continue;
                    }
                    double x = ax[0][0] + (ax[1][0] - ax[0][0]) * uu;
                    double z = ax[0][1] + (ax[1][1] - ax[0][1]) * uu;
                    double y = src[1] + (ground(d, x, z) + 1.0 - src[1]) * Math.min(1, uu * 3);
                    for (int i = 0; i < n / 3; i++) {
                        level.addParticle(i == 0 ? tint(el, r) : mote(el, r), x, y, z, (ax[1][0] - ax[0][0]) * 0.01, 0, (ax[1][1] - ax[0][1]) * 0.01);
                    }
                }
            }
            default -> {
            }
        }
    }

    // ---- 振り回し・広がる波の最中
    private static void during(ClientLevel level, TelegraphClient.Decal d, AttackLook look, TelegraphShape now) {
        RandomSource r = level.random;
        AttackLook.Element el = look.element;
        switch (look.travel) {
            case SWEEP, WAVE -> {
                boolean wave = look.travel == AttackLook.Travel.WAVE;
                for (double[] q : outline(now, take(level, look, wave ? 18 : 14))) {
                    double y = ground(d, q[0], q[1]);
                    level.addParticle(r.nextInt(3) == 0 ? puff(el, r) : debris(el, r), q[0], y + 0.2, q[1], 0, wave ? 0.22 : 0.1, 0);
                }
                if (!wave) {
                    beam(level, d, look, now, 0.25);
                }
            }
            case BEAM -> beam(level, d, look, now, 0.5);
            default -> {
                // 続く技 (active) は、形の中で湧く
                double[] q = inside(now, r);
                if (q != null && take(level, look, 1) > 0) {
                    level.addParticle(mote(el, r), q[0], ground(d, q[0], q[1]) + 0.2, q[1], 0, 0.1, 0);
                }
            }
        }
    }

    // 技の元から帯の先へ
    private static void beam(ClientLevel level, TelegraphClient.Decal d, AttackLook look, TelegraphShape now, double density) {
        RandomSource r = level.random;
        AttackLook.Element el = look.element;
        double[][] ax;
        if (d.shape.kind() == TelegraphShape.KIND_SWEEP) {
            // 振り回しの帯: 元から、いまの帯の先端へ
            double[] src = source(level, d);
            List<double[]> o = outline(now, 24);
            double[] far = o.isEmpty() ? center(d.shape) : o.get(0);
            double best = -1;
            for (double[] q : o) {
                double dd = Math.hypot(q[0] - src[0], q[1] - src[2]);
                if (dd > best) {
                    best = dd;
                    far = q;
                }
            }
            ax = new double[][] {{src[0], src[2]}, far};
        } else {
            ax = axis(level, d);
        }
        double len = Math.hypot(ax[1][0] - ax[0][0], ax[1][1] - ax[0][1]);
        int n = take(level, look, Math.min(48, len * 2.2 * density));
        double[] src = source(level, d);
        for (int i = 0; i < n; i++) {
            double u = r.nextDouble();
            double x = ax[0][0] + (ax[1][0] - ax[0][0]) * u;
            double z = ax[0][1] + (ax[1][1] - ax[0][1]) * u;
            double gy = ground(d, x, z) + 1.0;
            double y = src[1] + (gy - src[1]) * Math.min(1, u * 4);
            double j = el == AttackLook.Element.STORM ? 0.5 : 0.25;
            level.addParticle(i % 4 == 0 ? tint(el, r) : (i % 4 == 1 ? puff(el, r) : debris(el, r)), x + (r.nextDouble() - 0.5) * j, y + (r.nextDouble() - 0.5) * j,
                    z + (r.nextDouble() - 0.5) * j, (ax[1][0] - ax[0][0]) / Math.max(1, len) * 0.25, 0.02, (ax[1][1] - ax[0][1]) / Math.max(1, len) * 0.25);
        }
    }

    // ---------------------------------------------------------------- 当たった瞬間 (TelegraphClient から 1 回)

    static void impact(ClientLevel level, TelegraphClient.Decal d) {
        AttackLook look = d.look;
        if (look == AttackLook.NONE) {
            return;
        }
        RandomSource r = level.random;
        AttackLook.Element el = look.element;
        TelegraphShape s = d.shape.at(1.0);
        double[] c = center(s);
        double gy = ground(d, c[0], c[1]);
        double size = halfSize(s);
        double big = Math.min(1.6, 0.5 + size / 6);
        if (look.travel == AttackLook.Travel.BOLT) {
            bolt(level, d, look, c[0], gy, c[1]);
        }
        if (look.travel == AttackLook.Travel.BEAM) {
            beam(level, d, look, d.shape.at(1.0), 1.0);
        }
        switch (look.impact) {
            case BURST -> {
                int n = take(level, look, 34 * big);
                for (int i = 0; i < n; i++) {
                    double[] q = inside(s, r);
                    if (q == null) {
                        continue;
                    }
                    double y = ground(d, q[0], q[1]);
                    level.addParticle(debris(el, r), q[0], y + 0.2, q[1], (q[0] - c[0]) * 0.03, 0.25 + r.nextDouble() * 0.3, (q[1] - c[1]) * 0.03);
                }
                int m = take(level, look, 8 * big);
                for (int i = 0; i < m; i++) {
                    double[] q = inside(s, r);
                    if (q != null) {
                        // 羽の雨の当たり: 雲の白い塊ではなく、舞い上がる羽
                        level.addParticle(look == AttackLook.STORM_FEATHER ? feather() : puff(el, r), q[0], ground(d, q[0], q[1]) + 0.4, q[1], 0, 0.05, 0);
                    }
                }
                if (size > 3) {
                    level.addParticle(el == AttackLook.Element.WATER ? ParticleTypes.SPLASH : ParticleTypes.EXPLOSION, c[0], gy + 0.5, c[1], 0, 0, 0);
                }
            }
            case COLUMN -> {
                double w = Math.min(1.4, size * 0.45);
                int n = take(level, look, 26 * big);
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double rr = r.nextDouble() * w;
                    level.addParticle(i % 3 == 0 ? tint(el, r) : (el == AttackLook.Element.WATER ? ParticleTypes.SPLASH : debris(el, r)),
                            c[0] + Math.cos(a) * rr, gy + 0.2 + r.nextDouble() * 4.5, c[1] + Math.sin(a) * rr, 0, 0.35 + r.nextDouble() * 0.35, 0);
                }
                for (double[] q : outline(s, take(level, look, 12))) {
                    level.addParticle(puff(el, r), q[0], ground(d, q[0], q[1]) + 0.2, q[1], (q[0] - c[0]) * 0.04, 0.04, (q[1] - c[1]) * 0.04);
                }
            }
            case PIERCE -> {
                double[][] ax = axis(level, d);
                int n = take(level, look, 30 * big);
                double dx = ax[1][0] - ax[0][0];
                double dz = ax[1][1] - ax[0][1];
                double len = Math.max(1, Math.hypot(dx, dz));
                for (int i = 0; i < n; i++) {
                    double u = r.nextDouble();
                    double x = ax[0][0] + dx * u;
                    double z = ax[0][1] + dz * u;
                    double side = (r.nextBoolean() ? 1 : -1) * (0.1 + r.nextDouble() * 0.2);
                    level.addParticle(i % 3 == 0 ? ParticleTypes.CRIT : debris(el, r), x, ground(d, x, z) + 0.3 + r.nextDouble() * 1.2, z,
                            -dz / len * side, 0.08 + r.nextDouble() * 0.15, dx / len * side);
                }
                if (take(level, look, 2) > 0) {
                    level.addParticle(ParticleTypes.SWEEP_ATTACK, ax[1][0], ground(d, ax[1][0], ax[1][1]) + 1, ax[1][1], 0, 0, 0);
                }
            }
            case RING -> {
                for (double[] q : outline(s, take(level, look, 26))) {
                    double y = ground(d, q[0], q[1]);
                    level.addParticle(r.nextBoolean() ? debris(el, r) : puff(el, r), q[0], y + 0.2, q[1], (q[0] - c[0]) * 0.02, 0.2 + r.nextDouble() * 0.2,
                            (q[1] - c[1]) * 0.02);
                }
            }
            case FLASH -> {
                level.addParticle(ParticleTypes.FLASH, c[0], gy + 1.2, c[1], 0, 0, 0);
                int n = take(level, look, 28 * big);
                for (int i = 0; i < n; i++) {
                    double[] q = inside(s, r);
                    if (q == null) {
                        continue;
                    }
                    level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : mote(el, r), q[0], ground(d, q[0], q[1]) + 0.3 + r.nextDouble(), q[1],
                            (r.nextDouble() - 0.5) * 0.3, 0.1 + r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.3);
                }
            }
            case FISSURE -> {
                double[][] ax = axis(level, d);
                double dx = ax[1][0] - ax[0][0];
                double dz = ax[1][1] - ax[0][1];
                double len = Math.max(1, Math.hypot(dx, dz));
                int jets = Math.min(take(level, look, len / 1.3), 24);
                for (int i = 0; i < jets; i++) {
                    double u = (i + r.nextDouble() * 0.5) / Math.max(1, jets);
                    double x = ax[0][0] + dx * u;
                    double z = ax[0][1] + dz * u;
                    double y = ground(d, x, z);
                    level.addParticle(ParticleTypes.LAVA, x, y + 0.2, z, 0, 0, 0);
                    level.addParticle(ParticleTypes.FLAME, x, y + 0.2, z, 0, 0.3 + r.nextDouble() * 0.2, 0);
                    level.addParticle(debris(el, r), x, y + 0.2, z, (r.nextDouble() - 0.5) * 0.2, 0.3, (r.nextDouble() - 0.5) * 0.2);
                }
            }
            default -> {
            }
        }
        if (look.linger != AttackLook.Linger.NONE) {
            LINGER.add(new Lingering(d, look, level.getGameTime()));
            if (LINGER.size() > 64) {
                LINGER.remove(0);
            }
        }
    }

    // 空から地面までの稲妻 (折れ線)
    private static void bolt(ClientLevel level, TelegraphClient.Decal d, AttackLook look, double x, double gy, double z) {
        RandomSource r = level.random;
        int n = take(level, look, 30);
        double px = x;
        double pz = z;
        double top = gy + 18;
        for (int i = 0; i < n; i++) {
            double f = (double) i / Math.max(1, n);
            double y = top - (top - gy) * f;
            px += (r.nextDouble() - 0.5) * 0.7;
            pz += (r.nextDouble() - 0.5) * 0.7;
            // 地面に向かって真下へ寄せる
            px += (x - px) * 0.25;
            pz += (z - pz) * 0.25;
            level.addParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, px, y, pz, 0, 0, 0);
        }
        level.addParticle(ParticleTypes.FLASH, x, gy + 0.5, z, 0, 0, 0);
    }

    // ---------------------------------------------------------------- 残り

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            LINGER.clear();
            return;
        }
        if (LINGER.isEmpty()) {
            return;
        }
        refresh(level);
        long now = level.getGameTime();
        RandomSource r = level.random;
        for (Iterator<Lingering> it = LINGER.iterator(); it.hasNext();) {
            Lingering l = it.next();
            long age = now - l.start;
            if (age < 0 || age > LINGER_TICKS) {
                it.remove();
                continue;
            }
            TelegraphClient.Decal d = l.decal;
            TelegraphShape s = d.shape.at(1.0);
            AttackLook.Element el = l.look.element;
            double fade = 1.0 - (double) age / LINGER_TICKS;
            switch (l.look.linger) {
                case SHOCK -> {
                    if (age <= SHOCK_TICKS) {
                        double[] c = center(s);
                        double sc = 1.0 + age * 0.14;
                        for (double[] q : outline(s, take(level, l.look, 20))) {
                            double x = c[0] + (q[0] - c[0]) * sc;
                            double z = c[1] + (q[1] - c[1]) * sc;
                            level.addParticle(r.nextBoolean() ? tint(el, r) : puff(el, r), x, ground(d, x, z) + 0.2, z, 0, 0.03, 0);
                        }
                    } else {
                        settle(level, d, l.look, s, fade, r);
                    }
                }
                default -> settle(level, d, l.look, s, fade, r);
            }
        }
    }

    private static void settle(ClientLevel level, TelegraphClient.Decal d, AttackLook look, TelegraphShape s, double fade, RandomSource r) {
        int n = take(level, look, 2.5 * fade);
        AttackLook.Element el = look.element;
        for (int i = 0; i < n; i++) {
            double[] q = inside(s, r);
            if (q == null) {
                continue;
            }
            double y = ground(d, q[0], q[1]);
            switch (look.linger) {
                case DUST, SHOCK -> level.addParticle(i % 2 == 0 ? new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BONE_BLOCK.defaultBlockState())
                        : puff(el, r), q[0], y + 0.4 + r.nextDouble() * 1.5, q[1], 0, 0.01, 0);
                case FOAM -> level.addParticle(i % 2 == 0 ? FOAM : ParticleTypes.BUBBLE_POP, q[0], y + 0.15, q[1], 0, 0.02, 0);
                case STATIC -> level.addParticle(ParticleTypes.ELECTRIC_SPARK, q[0], y + 0.1 + r.nextDouble() * 0.8, q[1],
                        (r.nextDouble() - 0.5) * 0.15, 0.05, (r.nextDouble() - 0.5) * 0.15);
                case SMOLDER -> level.addParticle(i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE, q[0], y + 0.15, q[1], 0, 0.04, 0);
                default -> {
                }
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LINGER.clear();
    }

    // 自己テスト・確認用: 一つの見た目の粒の数の目安 (上限の計算だけ)
    public static int budgetFor(int looks) {
        return (int) Math.round(BASE_BUDGET * Math.max(0.3, Math.min(1.0, 6.0 / Math.max(1, looks))));
    }
}
