package jp.revati.skyabyss.client.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.AttackLook;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.fx.FxParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 心臓の技・領域の決まりの粒 (docs/design/59「攻撃の見せ方の決まり」)。段ごとに係は一つ:
//   予告 = 床の印 (TelegraphRenderer)、溜め = 光 (AttackLightFx)、当たる物 = 模型 (AreaFx・AttackProps) か光の帯 (AttackLightFx)
//   ここが受け持つのは 当たった瞬間 の少しの粒 (破片と光の粒) と、残り の煙だけ。粒は mod の粒 (fx/FxParticles) だけで、バニラの粒は使わない。
// 数: 一つの攻撃の当たりで IMPACT_MAX まで、残りは 1 tick に LINGER_PER_TICK まで (画面の上限は FxParticle が持つ)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AttackFx {
    // 一つの攻撃で一度に出す粒の上限 (docs/design/59 の 2 章)
    public static final int IMPACT_MAX = 24;
    private static final int LINGER_PER_TICK = 2;
    private static final int LINGER_TICKS = 24;

    private record Lingering(TelegraphClient.Decal decal, FxParticles.Element element, long start) {
    }

    private static final List<Lingering> LINGER = new ArrayList<>();

    private AttackFx() {
    }

    // ---------------------------------------------------------------- 形の点

    static double ground(TelegraphClient.Decal d, double x, double z) {
        double y = d.ground(x, z);
        return Double.isNaN(y) ? d.floor : y;
    }

    static double[] center(TelegraphShape s) {
        double[] b = s.bounds();
        if (s.kind() == TelegraphShape.KIND_CIRCLE || s.kind() == TelegraphShape.KIND_RING || s.kind() == TelegraphShape.KIND_WAVE) {
            return new double[] {s.centerX(), s.centerZ()};
        }
        return new double[] {(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
    }

    static double halfSize(TelegraphShape s) {
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

    static List<double[]> outline(TelegraphShape s, int max) {
        List<double[]> pts = new ArrayList<>();
        double spacing = Math.max(0.5, halfSize(s) * 2 * Math.PI / Math.max(8, max * 2));
        s.outline((x, z) -> pts.add(new double[] {x, z}), spacing);
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
    static double[] source(ClientLevel level, TelegraphClient.Decal d) {
        Entity e = d.owner >= 0 ? level.getEntity(d.owner) : null;
        if (e != null) {
            return new double[] {e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ()};
        }
        double[] c = center(d.shape);
        return new double[] {c[0], ground(d, c[0], c[1]) + 2.5, c[1]};
    }

    // 帯・扇の「先」 (運びの終わり)
    static double[][] axis(ClientLevel level, TelegraphClient.Decal d) {
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
    // 予告・溜め・運びの間は粒を出さない (床の印・光・模型の係)
    static void tick(ClientLevel level, TelegraphClient.Decal d, int t) {
    }

    // ---------------------------------------------------------------- 当たった瞬間 (TelegraphClient から 1 回)

    static void impact(ClientLevel level, TelegraphClient.Decal d) {
        AttackLook look = d.look;
        if (look == AttackLook.NONE || look.roar()) {
            return;
        }
        FxParticles.Element el = FxParticles.Element.of(look.element);
        burst(level, d, el, look.weight);
        if (look.linger != AttackLook.Linger.NONE) {
            LINGER.add(new Lingering(d, el, level.getGameTime()));
            if (LINGER.size() > 48) {
                LINGER.remove(0);
            }
        }
    }

    // 当たりの粒: 破片 2/3、光の粒 1/3。形の中から、外へ少し跳ねる。多くて IMPACT_MAX
    static void burst(ClientLevel level, TelegraphClient.Decal d, FxParticles.Element el, float weight) {
        RandomSource r = level.random;
        TelegraphShape s = d.shape.at(1.0);
        double[] c = center(s);
        double size = halfSize(s);
        int n = (int) Math.min(IMPACT_MAX, Math.round((8 + size * 1.6) * weight));
        net.minecraft.core.particles.SimpleParticleType shard = FxParticles.type(el, FxParticles.Kind.SHARD);
        net.minecraft.core.particles.SimpleParticleType glow = FxParticles.type(el, FxParticles.Kind.GLOW);
        for (int i = 0; i < n; i++) {
            double[] q = inside(s, r);
            if (q == null) {
                continue;
            }
            double y = ground(d, q[0], q[1]) + 0.25;
            double ox = (q[0] - c[0]) / Math.max(1, size);
            double oz = (q[1] - c[1]) / Math.max(1, size);
            if (i % 3 == 2) {
                level.addParticle(glow, q[0], y + 0.4 + r.nextDouble() * 0.8, q[1], ox * 0.05, 0.03 + r.nextDouble() * 0.04, oz * 0.05);
            } else {
                level.addParticle(shard, q[0], y, q[1], ox * 0.12 + (r.nextDouble() - 0.5) * 0.08, 0.22 + r.nextDouble() * 0.22,
                        oz * 0.12 + (r.nextDouble() - 0.5) * 0.08);
            }
        }
    }

    // ---------------------------------------------------------------- 残り (煙一種だけ)

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
        long now = level.getGameTime();
        RandomSource r = level.random;
        for (Iterator<Lingering> it = LINGER.iterator(); it.hasNext();) {
            Lingering l = it.next();
            long age = now - l.start;
            if (age < 0 || age > LINGER_TICKS) {
                it.remove();
                continue;
            }
            if (r.nextDouble() > 1.0 - (double) age / LINGER_TICKS) {
                continue;
            }
            TelegraphShape s = l.decal.shape.at(1.0);
            net.minecraft.core.particles.SimpleParticleType smoke = FxParticles.type(l.element, FxParticles.Kind.SMOKE);
            for (int i = 0; i < LINGER_PER_TICK; i++) {
                double[] q = inside(s, r);
                if (q != null) {
                    level.addParticle(smoke, q[0], ground(l.decal, q[0], q[1]) + 0.3, q[1], (r.nextDouble() - 0.5) * 0.02, 0.02, (r.nextDouble() - 0.5) * 0.02);
                }
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LINGER.clear();
    }
}
