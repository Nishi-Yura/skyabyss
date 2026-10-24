package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.client.skill.fx.FxDraw;
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
//   溜め (手もとに集まる) → 放ち (形が出る) → 当たり (閃光・衝撃の殻) → 残り (形が消える・灯が残る)
// 主役は形 (client/skill/fx/FxDraw の板: 弧・筋・輪・円盤・扇・柱・稲妻・殻)。粒は当たりの火花だけの脇役。
// 段取りは技ごとに fx/ の三つのファイルで決める。決めていない技は generic (下) を使う。
//
// 予算 (1 発動・1 tick・全体): 形は 1 発動で同時に CAST_PRIMS 個まで (消えた形は数えない。長く降り続く技も最後まで描く)、全体は FxDraw.MAX_PRIMS 個まで。
// 粒は 1 発動 CAST_CAP 粒まで、1 tick は BASE_BUDGET (画面の粒の設定で 1/2・1/5)。
// 形も粒も手元だけで足す (通信は合図の 1 通だけ)。同時に動く発動は MAX_CASTS まで。
// 環境変数 SKYABYSS_SKILLFX_LOG があると、発動が終わるたびに使った形と粒の数を log へ出す (撮影での計測用)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkillFx {
    public static final int BASE_BUDGET = 160;
    public static final int CAST_CAP = 48;
    public static final int CAST_PRIMS = 24;
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
        // 発動の瞬間の目の位置と向き (前へ伸ばす形・狙いの点に使う。動いた後も変えない)
        public final Vec3 origin;
        public final Vec3 look;
        public final Vec3 fwd;
        public final Vec3 right;
        // サーバーが決めた当たりの点 (合図に載っていれば。天の驟雨の降る中心など)。無ければ null
        @javax.annotation.Nullable
        public Vec3 target;
        int age;
        int spent;
        // 足した形の数 (記録用) と、いま残っている形の数 (上限を見る)
        public int prims;
        public int live;
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

        // 残りの長さ (最後の段取りの後も、この tick まで形が残る)
        public void lasts(int tick) {
            duration = Math.max(duration, tick);
        }

        public RandomSource random() {
            return level.random;
        }

        // 手元 (右手の少し前・下)。一人称でも三人称でも、ここに粒があれば見える
        public Vec3 hand() {
            return origin.add(fwd.scale(1.1)).add(right.scale(0.5)).add(0, -0.6, 0);
        }

        // 足もと (今の位置)
        public Vec3 feet() {
            return caster.position().add(0, 0.05, 0);
        }

        // 腰の高さ (足もと + 1)
        public Vec3 waist() {
            return caster.position().add(0, 1.0, 0);
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
        c.target = payload.target();
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
        // 溜め (tick 0) の段取りは、発動の瞬間に一度だけ走らせる (毎 tick の確かめは age を先に進めてから見るので、0 には届かない)
        for (Step s : c.steps) {
            if (s.at == 0) {
                s.cue.run(c);
            }
        }
        CASTS.add(c);
    }

    // 段取りを決めていない技の既定: 手に集まり、足もとの輪が広がり、狙いで閃く
    static void generic(Cast c) {
        c.at(0, x -> burst(x, rune(), x.hand(), 4, 0.12, 0.02));
        c.at(3, x -> FxDraw.add(x, FxDraw.Prim.ring(x.feet(), 1.0, 4.0).color(0x8CF6FF).width(0.3).life(12)));
        c.at(6, x -> flash(x, x.aim(3.0)));
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

    // rune() は碑文の火花 (青緑・浮く)。sky() は空の粒 (流れる)。脇役の粒にだけ使う
    public static ParticleOptions rune() {
        return Skills.RUNE_SPARK.get();
    }

    public static ParticleOptions sky() {
        return Skills.SKY_MOTE.get();
    }

    // カメラから CAMERA_CLEAR ブロック以内の粒は出さない (目の前の大きな四角を作らない)
    public static final double CAMERA_CLEAR = 2.4;

    // 手元の一粒
    public static boolean put(Cast c, ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = x - cam.x;
        double dy = y - cam.y;
        double dz = z - cam.z;
        if (dx * dx + dy * dy + dz * dz < CAMERA_CLEAR * CAMERA_CLEAR) {
            return false;
        }
        c.level.addParticle(p, x, y, z, vx, vy, vz);
        return true;
    }

    // 中心のまわりに散らす (火花・灰の脇役。形には使わない)
    public static void burst(Cast c, ParticleOptions p, Vec3 at, int n, double spread, double speed) {
        int k = spend(c, n);
        RandomSource r = c.level.random;
        for (int i = 0; i < k; i++) {
            put(c, p, at.x + sym(r) * spread, at.y + sym(r) * spread, at.z + sym(r) * spread,
                    sym(r) * speed, sym(r) * speed, sym(r) * speed);
        }
    }

    // 閃光: 当たりの一点の明るい芯 (約 1.5 ブロック四方・濃さ 0.8、6 tick)。
    // 昔のバニラの閃光 (FLASH) は画面いっぱいの白い四角になるので使わない。カメラに向く板を一枚だけ描く
    public static void flash(Cast c, Vec3 at) {
        FxDraw.add(c, FxDraw.Prim.beam(at.add(0, -0.75, 0), 1.5).color(0xFFFFFF).width(1.5).alpha(0.8f).life(6)
                .ease(FxDraw.Ease.FULL));
    }

    // 技の当たりの火花 (技ごとの色): 小さな白い閃光と、色のついた柔らかい粒 6 つ (カメラに向く板)。
    // 白い十字の粒 (バニラの火花・碑文の粒) は当たりに使わない。形と同じ板で描くので、技の見た目がそろう
    public static void hitSpark(Cast c, Vec3 at, int rgb) {
        FxDraw.add(c, FxDraw.Prim.beam(at.add(0, -0.3, 0), 0.6).color(0xFFFFFF).width(0.9).alpha(0.8f).life(4)
                .ease(FxDraw.Ease.FULL));
        RandomSource r = c.level.random;
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 0.25 + r.nextDouble() * 0.25;
            Vec3 p = at.add(Math.cos(a) * d, r.nextDouble() * 0.5, Math.sin(a) * d);
            FxDraw.add(c, FxDraw.Prim.beam(p, 0.22).color(rgb).width(0.16).alpha(0.9f).life(8).ease(FxDraw.Ease.OUT).glow(0.5));
        }
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
            SkyAbyss.LOGGER.info("[skillfx] {} rank {} chain {} self {} shapes {} (cap {}) particles {} (cap {}) ticks {}",
                    c.skill.id, c.rank, c.chain, c.self, c.prims, CAST_PRIMS, c.spent, CAST_CAP, c.duration);
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
