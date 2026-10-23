package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.client.skill.fx.FxFrontline;
import jp.revati.skyabyss.rpg.client.skill.fx.FxRanged;
import jp.revati.skyabyss.rpg.client.skill.fx.FxSupport;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.Skills;
import jp.revati.skyabyss.rpg.skill.net.SkillFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 技の見た目 (手元だけで組む)。サーバーの合図 (SkillFxPayload) を受けて、段取りを 1 tick ずつ進める:
//   溜め (手のまわりに集まる) → 放ち (前へ伸びる筋・地面の輪) → 当たり (閃光・衝撃の輪・音の層) → 残り (粒が漂って消える)
// 段取りは技ごとに Plan で決める (fx/ の三つのファイル)。決めていない技は generic (下) を使う。
//
// 粒の予算 (手元の 1 tick あたりと、1 発動あたり): 1 tick は BASE_BUDGET (画面の粒の設定で 1/2・1/5)、1 発動は CAST_CAP まで。
// 粒は手元だけで足す (level.addParticle) ので、通信は合図の 1 通だけ。同時に出る発動は MAX_CASTS まで。
// 環境変数 SKYABYSS_SKILLFX_LOG があると、発動が終わるたびに使った粒の数を log へ出す (撮影での計測用)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkillFx {
    public static final int BASE_BUDGET = 480;
    public static final int CAST_CAP = 240;
    public static final int MAX_CASTS = 10;
    private static final boolean LOG = System.getenv("SKYABYSS_SKILLFX_LOG") != null;

    // 技ごとの段取り: Cast に at(tick, ...) で置いていく
    public static final Map<Skill, Consumer<Cast>> PLANS = new EnumMap<>(Skill.class);

    static {
        FxFrontline.register(PLANS);
        FxSupport.register(PLANS);
        FxRanged.register(PLANS);
    }

    private static final List<Cast> CASTS = new ArrayList<>();
    private static int frameBudget;
    private static long frameTick = Long.MIN_VALUE;

    // 画面の揺れ (自分の発動だけ。小さく、すぐ止まる)
    private static float kickAmp;
    private static int kickAge;
    private static final int KICK_TICKS = 5;

    private SkillFx() {
    }

    // ================================================================ 段取りの入れ物

    public interface Cue {
        void run(Cast c);
    }

    private record Step(int at, Cue cue) {
    }

    public static final class Cast {
        public final ClientLevel level;
        public final Skill skill;
        public final Player caster;
        public final boolean self;
        public final int rank;
        public final boolean chain;
        // 発動の瞬間の目の位置と向き (前へ伸ばす線・狙いの点に使う。動いた後も変えない)
        public final Vec3 origin;
        public final Vec3 look;
        public final Vec3 fwd;
        public final Vec3 right;
        int age;
        int spent;
        int duration;
        private final List<Step> steps = new ArrayList<>();

        Cast(ClientLevel level, Skill skill, Player caster, boolean self, int rank, boolean chain) {
            this.level = level;
            this.skill = skill;
            this.caster = caster;
            this.self = self;
            this.rank = rank;
            this.chain = chain;
            this.origin = caster.getEyePosition();
            this.look = caster.getLookAngle();
            Vec3 f = new Vec3(look.x, 0, look.z);
            this.fwd = f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
            this.right = new Vec3(-fwd.z, 0, fwd.x);
        }

        // この tick (発動から数えて) に動かす
        public void at(int tick, Cue cue) {
            steps.add(new Step(tick, cue));
            duration = Math.max(duration, tick);
        }

        // 残りの長さ (最後の段取りの後も、この tick まで粒が漂う)
        public void lasts(int tick) {
            duration = Math.max(duration, tick);
        }

        public RandomSource random() {
            return level.random;
        }

        // 手元 (右手の少し前・下)。一人称でも三人称でも、ここに粒があれば見える
        public Vec3 hand() {
            return origin.add(fwd.scale(0.55)).add(right.scale(0.38)).add(0, -0.42, 0);
        }

        // 足もと (今の位置)
        public Vec3 feet() {
            return caster.position().add(0, 0.05, 0);
        }

        // 発動の向きに、この距離だけ先の点 (地面の高さは足もと基準)
        public Vec3 ahead(double dist) {
            return new Vec3(origin.x + fwd.x * dist, feet().y, origin.z + fwd.z * dist);
        }

        // 狙いの向き (視線そのもの) に先の点
        public Vec3 aim(double dist) {
            return origin.add(look.scale(dist));
        }
    }

    // ================================================================ 受け取り

    public static void onCast(SkillFxPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Entity e = level.getEntity(payload.caster());
        if (!(e instanceof Player caster)) {
            return;
        }
        Skill skill = Skill.byId(payload.skill());
        if (skill == null) {
            return;
        }
        Cast c = new Cast(level, skill, caster, caster == mc.player, payload.rank(), payload.chain());
        Consumer<Cast> plan = PLANS.get(skill);
        if (plan == null) {
            generic(c);
        } else {
            plan.accept(c);
        }
        if (c.steps.isEmpty()) {
            return;
        }
        if (CASTS.size() >= MAX_CASTS) {
            CASTS.remove(0);
        }
        CASTS.add(c);
    }

    // 段取りを決めていない技の既定: 手に集まり、足もとの輪が広がり、狙いで閃く
    static void generic(Cast c) {
        c.at(0, x -> burst(x, rune(), x.hand(), 6, 0.12, 0.02));
        c.at(4, x -> ring(x, rune(), x.feet(), 1.6, 14, 0.25, 0.05));
        c.at(6, x -> flash(x, x.aim(3.0)));
        c.at(8, x -> burst(x, rune(), x.aim(2.4), 10, 0.5, 0.06));
        c.lasts(18);
    }

    // ================================================================ 粒の予算

    private static void refresh(ClientLevel level) {
        long now = level.getGameTime();
        if (now != frameTick) {
            frameTick = now;
            float scale = switch (Minecraft.getInstance().options.particles().get()) {
                case ALL -> 1f;
                case DECREASED -> 0.5f;
                case MINIMAL -> 0.2f;
            };
            frameBudget = Math.round(BASE_BUDGET * scale);
        }
    }

    // この発動が今使ってよい粒の数 (want まで。残りは発動の上限と tick の上限で減る)
    public static int spend(Cast c, int want) {
        refresh(c.level);
        int n = Math.min(want, Math.min(frameBudget, CAST_CAP - c.spent));
        if (n <= 0) {
            return 0;
        }
        frameBudget -= n;
        c.spent += n;
        return n;
    }

    // ================================================================ 道具 (段取りから呼ぶ)

    // rune() は碑文の火花 (青緑・浮く)。sky() は空の粒 (流れる)。どちらも自分で光る
    public static ParticleOptions rune() {
        return Skills.RUNE_SPARK.get();
    }

    public static ParticleOptions sky() {
        return Skills.SKY_MOTE.get();
    }

    // 中心のまわりに散らす (速さは ±speed)
    public static void burst(Cast c, ParticleOptions p, Vec3 at, int n, double spread, double speed) {
        int k = spend(c, n);
        RandomSource r = c.level.random;
        for (int i = 0; i < k; i++) {
            c.level.addParticle(p, at.x + sym(r) * spread, at.y + sym(r) * spread, at.z + sym(r) * spread,
                    sym(r) * speed, sym(r) * speed, sym(r) * speed);
        }
    }

    // 地面 (水平) の輪。outward の速さで外へ広がる
    public static void ring(Cast c, ParticleOptions p, Vec3 center, double radius, int n, double lift, double outward) {
        int k = spend(c, n);
        RandomSource r = c.level.random;
        for (int i = 0; i < k; i++) {
            double a = Math.PI * 2 * (i + r.nextDouble() * 0.5) / Math.max(1, k);
            double cs = Math.cos(a);
            double sn = Math.sin(a);
            c.level.addParticle(p, center.x + cs * radius, center.y + lift, center.z + sn * radius,
                    cs * outward, 0.0, sn * outward);
        }
    }

    // 二点のあいだに一列に並べる (前へ伸びる筋・鎖・線)。jitter で揺らす
    public static void line(Cast c, ParticleOptions p, Vec3 a, Vec3 b, int n, double jitter, double speed) {
        int k = spend(c, n);
        RandomSource r = c.level.random;
        for (int i = 0; i < k; i++) {
            double t = k <= 1 ? 0 : (double) i / (k - 1);
            Vec3 q = a.lerp(b, t);
            c.level.addParticle(p, q.x + sym(r) * jitter, q.y + sym(r) * jitter, q.z + sym(r) * jitter,
                    sym(r) * speed, sym(r) * speed, sym(r) * speed);
        }
    }

    // 閃光 (白い星の粒を重ねて一瞬だけ)
    public static void flash(Cast c, Vec3 at) {
        if (spend(c, 2) < 2) {
            return;
        }
        c.level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
        c.level.addParticle(ParticleTypes.FLASH, at.x, at.y + 0.2, at.z, 0, 0, 0);
    }

    // 段取りの音を一つ重ねる (音程は少しずらす)。サーバーの音 (技ごとの効果音) とは別の層として鳴る
    public static void sound(Cast c, SoundEvent s, Vec3 at, float volume, float pitch) {
        RandomSource r = c.level.random;
        c.level.playLocalSound(at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch * (0.93f + r.nextFloat() * 0.14f), false);
    }

    // 自分の発動だけ、画面を小さく揺らす (deg は度。Config の画面の揺れの設定で止まる)
    public static void kick(Cast c, float deg) {
        if (!c.self || !Config.SCREEN_SHAKE.get()) {
            return;
        }
        kickAmp = Math.max(kickAmp * 0.5f, deg);
        kickAge = 0;
    }

    private static double sym(RandomSource r) {
        return r.nextDouble() * 2 - 1;
    }

    // ================================================================ 時間を進める

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CASTS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        refresh(mc.level);
        if (kickAmp > 0f) {
            kickAge++;
            if (kickAge > KICK_TICKS) {
                kickAmp = 0f;
            }
        }
        for (Iterator<Cast> it = CASTS.iterator(); it.hasNext();) {
            Cast c = it.next();
            c.age++;
            if (c.caster.isRemoved() || c.age > c.duration) {
                finish(c);
                it.remove();
                continue;
            }
            for (Step s : c.steps) {
                if (s.at == c.age) {
                    s.cue.run(c);
                }
            }
        }
    }

    private static void finish(Cast c) {
        if (LOG) {
            SkyAbyss.LOGGER.info("[skillfx] {} rank {} chain {} self {} particles {} (cap {}) ticks {}",
                    c.skill.id, c.rank, c.chain, c.self, c.spent, CAST_CAP, c.duration);
        }
    }

    // 画面の揺れ (発動した本人の画面だけ。すぐ減って消える)
    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (kickAmp <= 0f) {
            return;
        }
        float age = kickAge + (float) event.getPartialTick();
        float k = Math.max(0f, 1f - age / KICK_TICKS);
        k *= k;
        event.setRoll(event.getRoll() + (float) Math.sin(age * 3.1) * kickAmp * k);
        event.setPitch(event.getPitch() + (float) Math.cos(age * 2.3) * kickAmp * 0.5f * k);
    }
}
