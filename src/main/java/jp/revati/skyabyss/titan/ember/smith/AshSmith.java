package jp.revati.skyabyss.titan.ember.smith;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 中ボス「灰の鍛え手」(13 の 5.2節)。灰の民の大炉を守る、背の曲がった大きな鍛冶。背に二つの鞴、右手に大鎚、左手に赤く焼けた塊の火箸
// (大鎚・火箸は別のモデルで、手の受け口 socket_main / socket_off に付く。AshSmithRenderer)。背丈 4.5 ブロック。
//
// 傷のある技はすべて予告 (combat/Telegraph。02 の 5.4節) で出す。印の範囲と当たりは同じ形で、予告は 0.8 秒以上、向き・落ちる点は技の初めに決まる
// (そのあとは追わない)。人数 (部屋にいる人) が増えると、技の本数・数が増える (一人でも、印を見れば傷なしで避けられる)。
// 技 (技ごとの音・動き。人がいなければ回らない):
//   鎚打ち   距離 7 未満。1.0 秒、鎚を高く振り上げる (帯の予告と低い音)。前方 幅 3・長さ 8 の帯を打つ (16)。二人から別の人へ向けた帯が増える (最大 3 本)。
//            打った帯の床に亀裂が走り、1.2 秒後にその帯から熱が噴く (噴気 10 と熱 +20。亀裂の帯も予告で、白い線としゅうという音)
//   鞴の息   距離 10 未満、12 秒ごと。1.5 秒、背の鞴が膨らむ (吸いこむ音)。前方 60 度・長さ 10 の扇に熱風 (8、強い押し、熱 +35)。
//            二人から別の人へ向けた扇が増える (最大 3 本)。吐いたあと 3 秒、背の鞴が白く光り、後ろからの傷が 1.5 倍
//   焼き入れ 30 秒ごと。部屋の焼き入れの槽へ歩き、鎚を沈める。部屋の半分が 6 秒、湯気でおおわれる (濃い白い粒)。
//            鎚が冷えて、次の 3 回の鎚打ちは噴気が出ない代わりに、打ち 1.5 倍 (24) と、打った点から広がる輪 (12。安全な筋が三本、跳べば当たらない)
//   火花散らし 距離 18 未満。0.8 秒の溜めのあと、火花が人の足もとへ弧を描いて落ちる (落ちる所に赤い円 1.0 秒以上。10)。6 つ、人数が増えると 2 人増えるごとに 4 つ増える (最大 14)
//   怒り     体力 50% を初めて切ったとき一度。1.5 秒、前掛けを投げ捨て、鞴が赤から白へ。以後、鎚打ちの間隔が 2 割短い。
//            奥の炉の扉が開き、灰被りが 2 体出る
// 体力 520 ×(1 + 0.6 ×(参加者 - 1))(受ける傷を割る)。防御 12・靭性 6・ノックバック耐性 0.95
public class AshSmith extends EmberMob {
    public static final float HEALTH = 520;
    public static final float ARMOR = 12;
    public static final float TOUGHNESS = 6;
    public static final String SMITH_TAG = "skyabyss_smith_trial";

    // 技の数値 (自己テストが見る)
    public static final float SLAM_DAMAGE = 16;
    public static final float COLD_MULT = 1.5f;
    public static final float VENT_DAMAGE = 10;
    public static final float VENT_HEAT = 20;
    public static final float BREATH_DAMAGE = 8;
    public static final float BREATH_HEAT = 35;
    public static final float RING_DAMAGE = 12;
    public static final float SPARK_DAMAGE = 10;
    public static final int SLAM_RANGE = 7;
    public static final int SLAM_WINDUP = 20;
    public static final double SLAM_LENGTH = 8;
    public static final double SLAM_HALF_WIDTH = 1.5;
    public static final int VENT_DELAY = 24;
    public static final int SLAM_COOLDOWN = 70;
    public static final int BREATH_RANGE = 10;
    public static final int BREATH_INHALE = 30;
    public static final double BREATH_HALF_ANGLE = 30;
    public static final double BREATH_LENGTH = 10;
    public static final int BREATH_COOLDOWN = 240;
    public static final int WEAK_TICKS = 60;
    public static final float WEAK_MULT = 1.5f;
    public static final int QUENCH_COOLDOWN = 600;
    public static final int QUENCH_DUNK = 52;
    public static final int STEAM_START = 12;
    public static final int STEAM_TICKS = 120;
    public static final int COLD_SLAMS = 3;
    public static final int SPARKS_RANGE = 18;
    public static final int SPARKS_CHARGE = 16;
    public static final int SPARK_COUNT = 6;
    public static final int SPARK_WARN = 20;
    public static final int SPARK_GAP = 4;
    public static final double SPARK_RADIUS = 1.8;
    public static final int SPARKS_COOLDOWN = 160;
    public static final int RAGE_TICKS = 30;
    public static final float RAGE_SLAM_FACTOR = 0.8f;
    public static final double RING_SPEED = 0.55;
    public static final double RING_MAX = 8;
    public static final double RING_START = 1.2;
    public static final double RING_LANE_HALF = Math.toRadians(24);
    public static final double SPARK_FILL_MAX = 3.0;

    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(AshSmith.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WEAK = SynchedEntityData.defineId(AshSmith.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> COLD = SynchedEntityData.defineId(AshSmith.class, EntityDataSerializers.BOOLEAN);

    public enum Action {
        NONE, SLAM, BREATH, QUENCH, SPARKS, RAGE
    }

    // 試験用: 戦っている人を決める (偽のプレイヤーは level.players() に入らないため)。通常は null
    @Nullable
    public static volatile List<ServerPlayer> testFighters;

    // 試験用: 人へ与える傷の記録 (偽のプレイヤーは傷を受けないので、与えた量をここで数える)。通常は null
    public interface DamageLog {
        void hit(ServerPlayer player, float amount, String kind);
    }

    @Nullable
    public static volatile DamageLog testLog;

    // 試験用: 冷えた鎚の輪の安全な筋の向きを決める (null なら乱数)
    @Nullable
    public static volatile Double testGapBase;

    private void hurtPlayer(ServerPlayer p, DamageSource source, float amount, String kind) {
        DamageLog log = testLog;
        if (log != null) {
            log.hit(p, amount, kind);
        }
        p.hurt(source, amount);
    }

    // 開発用・試験用: 鍛え手の間の外 (平らな世界など) でも、近くの人を戦っている人にして技を出せるようにする (EmberForceDev)
    public void armForDev(java.util.List<ServerPlayer> players, int party) {
        testFighters = players;
        partyOverride = party;
        passive = true;
    }

    // 試験用: 次の鎚打ちから冷えた鎚にする
    public void armCold() {
        coldSlams = COLD_SLAMS;
        entityData.set(COLD, true);
    }

    // 試験用: true なら、技を自分では選ばない (forceAction で呼んだ技だけ回す。怒りは体力で自動)
    public boolean passive;

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private int actionTick;
    private float multiplier = 1f;
    private boolean raged;
    private int slamCooldown = 50;
    private int breathCooldown = 100;
    private int quenchCooldown = 360;
    private int sparksCooldown = 120;
    private int coldSlams;
    private int weakTicks;
    private int steamTicks;
    private int steamSide;
    private BlockPos trough = BlockPos.ZERO;
    private int quenchPhase;
    private int stuck;
    @Nullable
    private ServerPlayer target;
    private Vec3 aimDir = new Vec3(0, 0, 1);
    private Vec3 slamOrigin = Vec3.ZERO;
    private boolean slamCold;
    private final List<Spark> sparks = new ArrayList<>();
    // 開発用: 部屋にいる人数を多いことにする (0 なら実際の人数)
    private int partyOverride;

    // 火花の弧 (見た目だけ): launch tick 後に着地点へ弧を描く。傷と円は Telegraph
    private static final class Spark {
        final Vec3 pos;
        int launch;

        Spark(Vec3 pos, int launch) {
            this.pos = pos;
            this.launch = launch;
        }
    }

    public AshSmith(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ash_smith", "slam", "slam_cold", "breath", "quench", "sparks", "rage");
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.95)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ATTACK_DAMAGE, SLAM_DAMAGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ENRAGED, false);
        builder.define(WEAK, false);
        builder.define(COLD, false);
    }

    @Override
    protected void registerGoals() {
        // 動きは aiStep の中で決める
    }

    // ---------------------------------------------------------------- 戦いの部屋と結びつける (AshSmithBattle から)
    public void bind(int challengers) {
        multiplier = scaleFor(challengers);
        addTag(TitanBackTrials.HEART_TAG);
        addTag(EmberMob.TRIAL_TAG);
        addTag(SMITH_TAG);
    }

    public static float scaleFor(int challengers) {
        return 1f + 0.6f * (Math.max(1, challengers) - 1);
    }

    public float multiplier() {
        return multiplier;
    }

    public Action currentAction() {
        return action;
    }

    public boolean isEnraged() {
        return entityData.get(ENRAGED);
    }

    // 吐いたあとの 3 秒 (背の鞴が白く光る)
    public boolean isWeak() {
        return entityData.get(WEAK);
    }

    // 鎚が冷えている (次の鎚打ちが強い)
    public boolean isCold() {
        return entityData.get(COLD);
    }

    public boolean raged() {
        return raged;
    }

    public int coldSlams() {
        return coldSlams;
    }

    public int steamTicks() {
        return steamTicks;
    }

    public int steamSide() {
        return steamSide;
    }

    // 飛んでいる火花 (弧がまだ描かれていないもの)
    public int sparkCount() {
        return sparks.size();
    }

    // 開発用: 部屋にいる人数を、実際より多いことにする (一人で複数人の技の絵を撮るため。0 なら実際の人数)
    public void setPartyOverride(int count) {
        partyOverride = count;
    }

    int present(ServerLevel server) {
        return Math.max(partyOverride, fighters(server).size());
    }

    // 床の高さ (人が立つ高さ)。鍛え手の間の中ならその床。ほかの場所 (開発用の平らな世界) では足もと
    private double floorY() {
        return AshSmithArena.inside(position()) ? AshSmithArena.FLOOR + 1 : getY();
    }

    // 人数による技の本数・数 (一人でも、印を見れば傷なしで避けられる)
    public static int slamLines(int n) {
        return n >= 4 ? 3 : n >= 2 ? 2 : 1;
    }

    public static int breathCones(int n) {
        return Math.min(3, 1 + Math.max(1, n) / 2);
    }

    public static int sparkTotal(int n) {
        return Math.min(14, Math.max(SPARK_COUNT, 2 + 2 * Math.max(1, n)));
    }

    // 予告の組み立て (形・長さ・印の種類。持ち主は鍛え手、当たるのは部屋の中の戦っている人)
    // 印の種類 (Telegraph の preset): D = 傷が出る (danger)、S = 安全な筋 (safe)、H = 押すだけ・見た目だけ (harmless)
    private static final int D = 0;
    private static final int S = 1;
    private static final int H = 2;

    private static Telegraph.Builder preset(Telegraph.Builder b, int style) {
        return switch (style) {
            case S -> b.safe();
            case H -> b.harmless();
            default -> b.danger();
        };
    }

    private Telegraph.Builder tg(ServerLevel server, TelegraphShape shape, int warn, int style) {
        return preset(Telegraph.in(server, shape).floor(floorY()).warn(warn).owner(this)
                .victims(p -> fighters(server).contains(p)).reach(3.5), style);
    }

    // 狙う方向・地点の候補: 戦っている人 (いま狙っている人を先頭に、残りは入れ替え)。人がいなくて人数の上書きだけあるときは空
    private List<ServerPlayer> shuffledFighters(ServerLevel server) {
        List<ServerPlayer> all = new ArrayList<>(fighters(server));
        java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
        if (target != null && all.remove(target)) {
            all.add(0, target);
        }
        return all;
    }

    public float bossBarProgress() {
        return bossBar.getProgress();
    }

    @Override
    protected String state() {
        return null;
    }

    // ---------------------------------------------------------------- 毎 tick
    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 10 == 0) {
            updateBossBar(server);
        }
        if (isDeadOrDying()) {
            return;
        }
        tickEffects(server);
        slamCooldown--;
        breathCooldown--;
        quenchCooldown--;
        sparksCooldown--;
        target = pickTarget(server);
        if (!raged && getHealth() < getMaxHealth() * 0.5f && action == Action.NONE) {
            begin(server, Action.RAGE);
        }
        if (action != Action.NONE) {
            tickAction(server);
            return;
        }
        if (target == null || passive) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30f, 30f);
        faceToward(target.position());
        double dist = horizontalDistance(target.position());
        if (quenchCooldown <= 0) {
            begin(server, Action.QUENCH);
        } else if (dist < BREATH_RANGE && breathCooldown <= 0) {
            begin(server, Action.BREATH);
        } else if (dist < SLAM_RANGE && slamCooldown <= 0) {
            begin(server, Action.SLAM);
        } else if (dist < SPARKS_RANGE && dist >= 6 && sparksCooldown <= 0) {
            begin(server, Action.SPARKS);
        } else if (dist > 4.5) {
            getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    @Nullable
    private ServerPlayer pickTarget(ServerLevel server) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : fighters(server)) {
            double d = distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    // 戦っている人: 部屋の中の人
    public List<ServerPlayer> fighters(ServerLevel server) {
        List<ServerPlayer> forced = testFighters;
        if (forced != null) {
            return forced;
        }
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Players.snapshot(server.players())) {
            if (p.isAlive() && !p.isSpectator() && !p.isCreative() && AshSmithArena.inside(p.position())
                    && jp.revati.skyabyss.titan.group.GroupOwned.mayTarget(this, p)) {
                out.add(p);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 技
    public void forceAction(Action a) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        action = Action.NONE;
        target = pickTarget(server);
        begin(server, a);
    }

    private void begin(ServerLevel server, Action a) {
        action = a;
        actionTick = 0;
        stuck = 0;
        getNavigation().stop();
        switch (a) {
            case SLAM -> {
                slamCold = coldSlams > 0;
                Vec3 to = target != null ? target.position().subtract(position()) : getLookAngle();
                aimDir = new Vec3(to.x, 0, to.z);
                aimDir = aimDir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : aimDir.normalize();
                slamOrigin = position();
                faceToward(position().add(aimDir));
                playAction(slamCold ? "slam_cold" : "slam");
                sound(server, EmberSmithSetup.SLAM.get(), 2.5f, slamCold ? 0.7f : 0.85f);
                startSlams(server);
            }
            case BREATH -> {
                playAction("breath");
                sound(server, EmberSmithSetup.INHALE.get(), 3f, 1f);
                broadcast(server, ModText.battle("message.skyabyss.ember.smith.breath"));
                startBreath(server);
            }
            case QUENCH -> {
                trough = AshSmithArena.nearestTrough(position());
                quenchPhase = 0;
                broadcast(server, ModText.battle("message.skyabyss.ember.smith.quench"));
            }
            case SPARKS -> {
                playAction("sparks");
                sound(server, EmberSmithSetup.SPARKS.get(), 2.5f, 0.8f);
            }
            case RAGE -> {
                raged = true;
                entityData.set(ENRAGED, true);
                playAction("rage");
                sound(server, EmberSmithSetup.RAGE.get(), 4f, 1f);
                broadcast(server, ModText.battle("message.skyabyss.ember.smith.rage"));
            }
            default -> {
            }
        }
    }

    private void tickAction(ServerLevel server) {
        actionTick++;
        switch (action) {
            case SLAM -> tickSlam(server);
            case BREATH -> tickBreath(server);
            case QUENCH -> tickQuench(server);
            case SPARKS -> tickSparks(server);
            case RAGE -> tickRage(server);
            default -> finish();
        }
    }

    private void finish() {
        action = Action.NONE;
        actionTick = 0;
    }

    // ---- 鎚打ち
    // 帯は技の初めに決まる (向きは溜めの始まりで、そのあとは追わない)。二人から、別の人へ向けた帯が増える
    private void startSlams(ServerLevel server) {
        List<Vec3> dirs = new ArrayList<>();
        dirs.add(aimDir);
        int want = slamLines(present(server));
        for (ServerPlayer p : shuffledFighters(server)) {
            if (dirs.size() >= want) {
                break;
            }
            if (p == target) {
                continue;
            }
            Vec3 d = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            if (d.lengthSqr() < 1e-4) {
                continue;
            }
            addDirection(dirs, d.normalize());
        }
        // 人が足りない (開発用の人数の上書き): 主の向きから左右へ広げる
        for (int i = 1; dirs.size() < want && i < 8; i++) {
            addDirection(dirs, rotateY(aimDir, Math.toRadians(40 * ((i + 1) / 2) * (i % 2 == 1 ? 1 : -1))));
        }
        boolean lead = true;
        for (Vec3 d : dirs) {
            slamStrip(server, d, lead);
            lead = false;
        }
    }

    // 向きの一覧に、近すぎない向きだけ足す (25 度以上あける)
    private static void addDirection(List<Vec3> dirs, Vec3 d) {
        for (Vec3 o : dirs) {
            if (o.dot(d) > Math.cos(Math.toRadians(25))) {
                return;
            }
        }
        dirs.add(d);
    }

    private void slamStrip(ServerLevel server, Vec3 dir, boolean lead) {
        Vec3 origin = slamOrigin;
        boolean cold = slamCold;
        float damage = SLAM_DAMAGE * (cold ? COLD_MULT : 1f);
        double angle = Telegraph.angleOf(dir.x, dir.z);
        tg(server, TelegraphShape.line(origin.x, origin.z, angle, SLAM_LENGTH, SLAM_HALF_WIDTH), SLAM_WINDUP, D)
                .onImpact(s -> {
                    Vec3 impact = origin.add(dir.scale(3.5));
                    if (lead) {
                        sound(s, EmberSmithSetup.SLAM.get(), 3.5f, cold ? 0.6f : 0.75f);
                    }
                    warn(s, ParticleTypes.EXPLOSION, impact.x, impact.y + 0.2, impact.z, 1, 0, 0, 0, 0);
                    warn(s, ParticleTypes.POOF, impact.x, impact.y + 0.2, impact.z, 24, 1.2, 0.1, 1.2, 0.08);
                    if (!cold) {
                        crackStrip(s, origin, dir, angle, lead);
                    }
                })
                .onHit((s, p) -> hurtPlayer(p, attackSource(), damage, "slam"))
                .start();
        if (cold && lead) {
            // 冷えた鎚: 打った点から広がる輪 (安全な筋が三本。跳べば当たらない)。打つ 1 秒前から、輪の始まりと筋が見える
            Vec3 impact = origin.add(dir.scale(3.5));
            Double fixed = testGapBase;
            double base = fixed != null ? fixed : random.nextDouble() * Math.PI * 2;
            double[] gaps = {base, base + Math.PI * 2 / 3, base + Math.PI * 4 / 3};
            for (double gap : gaps) {
                tg(server, TelegraphShape.cone(impact.x, impact.z, gap, RING_MAX + 1, RING_LANE_HALF), SLAM_WINDUP, S).start();
            }
            tg(server, TelegraphShape.wave(impact.x, impact.z, RING_START, RING_MAX, 0.9, gaps, RING_LANE_HALF), SLAM_WINDUP, D)
                    .active((int) Math.ceil((RING_MAX - RING_START) / RING_SPEED)).jumpClear(0.6)
                    .onHit((s, p) -> hurtPlayer(p, attackSource(), RING_DAMAGE, "ring"))
                    .start();
        }
    }

    // 打った帯に亀裂が走り、1.2 秒後にその帯から噴く (噴く帯も予告で、白い線としゅうという音)
    private void crackStrip(ServerLevel server, Vec3 origin, Vec3 dir, double angle, boolean lead) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (double t = 0.8; t <= SLAM_LENGTH; t += 0.55) {
            Vec3 c = origin.add(dir.scale(t)).add(side.scale(Math.sin(t * 2.3) * 0.5));
            warn(server, ParticleTypes.END_ROD, c.x, origin.y + 0.15, c.z, 1, 0.02, 0, 0.02, 0);
        }
        if (lead) {
            sound(server, EmberSmithSetup.INHALE.get(), 1.2f, 2.0f);
        }
        tg(server, TelegraphShape.line(origin.x, origin.z, angle, SLAM_LENGTH, SLAM_HALF_WIDTH), VENT_DELAY, D)
                .onImpact(s -> {
                    sound(s, EmberSmithSetup.BLAST.get(), lead ? 2.5f : 1.2f, 0.9f);
                    for (double t = 0.8; t <= SLAM_LENGTH; t += 0.6) {
                        Vec3 c = origin.add(dir.scale(t));
                        warn(s, ParticleTypes.FLAME, c.x, origin.y + 0.3, c.z, 4, SLAM_HALF_WIDTH * 0.5, 0.2, SLAM_HALF_WIDTH * 0.5, 0.12);
                        warn(s, ParticleTypes.LAVA, c.x, origin.y + 0.3, c.z, 1, 0.5, 0.1, 0.5, 0.0);
                    }
                })
                .onHit((s, p) -> {
                    hurtPlayer(p, damage(EmberIds.HEAT_BURST, this), VENT_DAMAGE, "vent");
                    EmberHeat.add(p, VENT_HEAT, EmberHeat.Reason.SMITH);
                })
                .start();
    }

    private void tickSlam(ServerLevel server) {
        if (actionTick < SLAM_WINDUP) {
            if (actionTick % 4 == 0) {
                // 鎚の影: 帯の上を暗い煙で
                for (int i = 0; i < 4; i++) {
                    double t = 1 + random.nextDouble() * (SLAM_LENGTH - 1);
                    Vec3 p = slamOrigin.add(aimDir.scale(t));
                    warn(server, ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.1, p.z, 2, SLAM_HALF_WIDTH * 0.6, 0.05, SLAM_HALF_WIDTH * 0.6, 0.0);
                }
            }
            return;
        }
        if (actionTick == SLAM_WINDUP && slamCold) {
            coldSlams--;
            if (coldSlams <= 0) {
                entityData.set(COLD, false);
            }
        }
        if (actionTick >= 34) {
            slamCooldown = Math.round(SLAM_COOLDOWN * (raged ? RAGE_SLAM_FACTOR : 1f));
            finish();
        }
    }

    // 帯 (原点から dir へ長さ len、半幅 half) の中か (水平のみ。足もとが床の近く)
    static boolean inStrip(Vec3 p, Vec3 origin, Vec3 dir, double len, double half) {
        double dx = p.x - origin.x;
        double dz = p.z - origin.z;
        double t = dx * dir.x + dz * dir.z;
        double lat = Math.abs(dx * -dir.z + dz * dir.x);
        return t >= 0 && t <= len && lat <= half + 0.3;
    }

    // ---- 鞴の息
    // 扇は技の初めに決まる (吸っているあいだ、向きは追わない)。二人から、別の人へ向けた扇が増える
    private void startBreath(ServerLevel server) {
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        List<Vec3> dirs = new ArrayList<>();
        dirs.add(look);
        int want = breathCones(present(server));
        for (ServerPlayer p : shuffledFighters(server)) {
            if (dirs.size() >= want) {
                break;
            }
            Vec3 d = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            if (d.lengthSqr() < 1e-4) {
                continue;
            }
            addDirection(dirs, d.normalize());
        }
        for (int i = 1; dirs.size() < want && i < 8; i++) {
            addDirection(dirs, rotateY(look, Math.toRadians(70 * ((i + 1) / 2) * (i % 2 == 1 ? 1 : -1))));
        }
        boolean lead = true;
        for (Vec3 d : dirs) {
            boolean first = lead;
            lead = false;
            tg(server, TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(d.x, d.z), BREATH_LENGTH, Math.toRadians(BREATH_HALF_ANGLE)), BREATH_INHALE, D)
                    .onImpact(s -> {
                        if (first) {
                            sound(s, EmberSmithSetup.BLAST.get(), 3.5f, 1f);
                        }
                        for (int i = 0; i < 30; i++) {
                            double a = Math.toRadians((random.nextDouble() - 0.5) * 2 * BREATH_HALF_ANGLE);
                            double r = 1.5 + random.nextDouble() * (BREATH_LENGTH - 1.5);
                            Vec3 q = position().add(rotateY(d, a).scale(r)).add(0, 1.0 + random.nextDouble(), 0);
                            warn(s, i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, q.x, q.y, q.z, 2, 0.2, 0.2, 0.2, 0.02);
                        }
                    })
                    .onHit((s, p) -> {
                        hurtPlayer(p, attackSource(), BREATH_DAMAGE, "breath");
                        Vec3 away = p.position().subtract(position());
                        away = new Vec3(away.x, 0, away.z).normalize();
                        p.setDeltaMovement(away.x * 1.9, 0.45, away.z * 1.9);
                        p.hurtMarked = true;
                        EmberHeat.add(p, BREATH_HEAT, EmberHeat.Reason.SMITH);
                    })
                    .start();
        }
    }

    private void tickBreath(ServerLevel server) {
        if (actionTick < BREATH_INHALE) {
            if (actionTick % 3 == 0) {
                Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
                // 吸いこむ粒: 前から体の背へ
                for (int i = 0; i < 6; i++) {
                    double d = 3 + random.nextDouble() * 5;
                    double a = Math.toRadians((random.nextDouble() - 0.5) * 70);
                    Vec3 dir = rotateY(look, a);
                    Vec3 p = position().add(dir.scale(d)).add(0, 1.2 + random.nextDouble() * 2, 0);
                    Vec3 v = position().add(0, 2.2, 0).subtract(p).scale(0.12);
                    warn(server, ParticleTypes.CLOUD, p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
                }
            }
            return;
        }
        if (actionTick == BREATH_INHALE) {
            weakTicks = WEAK_TICKS;
            entityData.set(WEAK, true);
            breathCooldown = BREATH_COOLDOWN;
        }
        if (actionTick >= BREATH_INHALE + 14) {
            finish();
        }
    }

    static Vec3 rotateY(Vec3 v, double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    // 扇 (原点から向き look へ、半角 30 度、長さ 10) の中か
    static boolean inFan(Vec3 p, Vec3 origin, Vec3 look) {
        double dx = p.x - origin.x;
        double dz = p.z - origin.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > BREATH_LENGTH || d < 1e-4) {
            return d < 1e-4;
        }
        double cos = (dx * look.x + dz * look.z) / (d * Math.sqrt(look.x * look.x + look.z * look.z));
        return cos >= Math.cos(Math.toRadians(BREATH_HALF_ANGLE));
    }

    // ---- 焼き入れ
    private void tickQuench(ServerLevel server) {
        Vec3 stand = AshSmithArena.troughStand(trough);
        if (quenchPhase == 0) {
            double d = horizontalDistance(stand);
            if (d <= 1.6 || stuck > 120) {
                if (d > 1.6) {
                    moveTo(stand.x, stand.y, stand.z, getYRot(), 0);
                }
                quenchPhase = 1;
                actionTick = 0;
                getNavigation().stop();
                faceToward(new Vec3(trough.getX() + 0.5, stand.y, trough.getZ() + 0.5));
                playAction("quench");
                return;
            }
            stuck++;
            getNavigation().moveTo(stand.x, stand.y, stand.z, 1.15);
            return;
        }
        if (actionTick == STEAM_START) {
            steamTicks = STEAM_TICKS;
            steamSide = AshSmithArena.steamSide(trough);
            coldSlams = COLD_SLAMS;
            entityData.set(COLD, true);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.HOSTILE, 3f, 0.7f);
            sound(server, EmberSmithSetup.BLAST.get(), 2f, 0.5f);
            warn(server, ParticleTypes.CLOUD, trough.getX() + 0.5, trough.getY() + 2.0, trough.getZ() + 0.5, 60, 1.4, 0.8, 1.4, 0.1);
        }
        if (actionTick >= QUENCH_DUNK) {
            quenchCooldown = QUENCH_COOLDOWN;
            slamCooldown = Math.max(slamCooldown, 20);
            finish();
        }
    }

    // ---- 火花散らし
    private void tickSparks(ServerLevel server) {
        if (target != null && actionTick < SPARKS_CHARGE) {
            faceToward(target.position());
        }
        if (actionTick < SPARKS_CHARGE) {
            if (actionTick % 3 == 0) {
                Vec3 hand = handPos();
                warn(server, ParticleTypes.LAVA, hand.x, hand.y, hand.z, 2, 0.2, 0.2, 0.2, 0.0);
            }
            return;
        }
        if (actionTick == SPARKS_CHARGE) {
            List<ServerPlayer> ps = fighters(server);
            sound(server, EmberSmithSetup.BLAST.get(), 1.5f, 1.6f);
            if (!ps.isEmpty()) {
                int total = sparkTotal(present(server));
                for (int i = 0; i < total; i++) {
                    ServerPlayer p = ps.get(i % ps.size());
                    double ox = i < ps.size() ? 0 : (random.nextDouble() * 2 - 1) * SPARK_FILL_MAX;
                    double oz = i < ps.size() ? 0 : (random.nextDouble() * 2 - 1) * SPARK_FILL_MAX;
                    double x = p.getX() + ox;
                    double z = p.getZ() + oz;
                    double dx = x - (AshSmithArena.CX + 0.5);
                    double dz = z - (AshSmithArena.CZ + 0.5);
                    double d = Math.hypot(dx, dz);
                    if (AshSmithArena.inside(position()) && d > AshSmithArena.RADIUS - 1.5) {
                        x = AshSmithArena.CX + 0.5 + dx * (AshSmithArena.RADIUS - 1.5) / d;
                        z = AshSmithArena.CZ + 0.5 + dz * (AshSmithArena.RADIUS - 1.5) / d;
                    }
                    startSpark(server, x, z, i);
                }
            }
        }
        if (actionTick >= SPARKS_CHARGE + 12) {
            sparksCooldown = SPARKS_COOLDOWN + random.nextInt(60);
            finish();
        }
    }

    // 火花一つ: 足もとの円 (半径 1.8。予告 1.0 秒以上、4 tick ずつずらして落ちる)。弧の見た目は tickEffects
    private void startSpark(ServerLevel server, double x, double z, int index) {
        double y = floorY();
        sparks.add(new Spark(new Vec3(x, y, z), index * SPARK_GAP));
        tg(server, TelegraphShape.circle(x, z, SPARK_RADIUS), SPARK_WARN + index * SPARK_GAP, D)
                .onImpact(s -> {
                    warn(s, ParticleTypes.LAVA, x, y + 0.2, z, 14, 0.5, 0.2, 0.5, 0.1);
                    warn(s, ParticleTypes.FLAME, x, y + 0.2, z, 18, 0.5, 0.3, 0.5, 0.08);
                    s.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.5f, 0.8f);
                })
                .onHit((s, p) -> hurtPlayer(p, attackSource(), SPARK_DAMAGE, "spark"))
                .start();
    }

    private Vec3 handPos() {
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 side = new Vec3(-look.z, 0, look.x);
        return position().add(look.scale(1.8)).add(side.scale(0.9)).add(0, 2.0, 0);
    }

    // ---- 怒り
    private void tickRage(ServerLevel server) {
        if (actionTick % 4 == 0) {
            warn(server, ParticleTypes.FLAME, getX(), getY() + 2.4, getZ(), 10, 0.8, 0.8, 0.8, 0.05);
        }
        if (actionTick == RAGE_TICKS / 2) {
            AshSmithArena.buildFurnace(server, true);
            server.playSound(null, AshSmithArena.FURNACE_DOOR.minX(), AshSmithArena.FLOOR + 2, AshSmithArena.FURNACE_DOOR.minZ(),
                    SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 3f, 0.5f);
            for (Vec3 at : AshSmithArena.furnaceSpawns()) {
                AshThrall thrall = EmberMobSetup.ASH_THRALL.get().create(server);
                if (thrall == null) {
                    continue;
                }
                thrall.moveTo(at.x, at.y, at.z, 200f, 0f);
                thrall.addTag(EmberMob.TRIAL_TAG);
                thrall.addTag(SMITH_TAG);
                thrall.setPersistenceRequired();
                thrall.setGroupId(groupId());
                server.addFreshEntity(thrall);
                warn(server, ParticleTypes.FLAME, at.x, at.y + 1, at.z, 20, 0.4, 0.8, 0.4, 0.05);
            }
        }
        if (actionTick >= RAGE_TICKS) {
            finish();
        }
    }

    // ---------------------------------------------------------------- 続いている効果 (火花の弧・湯気・弱み。傷は Telegraph が出す)
    private void tickEffects(ServerLevel server) {
        Iterator<Spark> si = sparks.iterator();
        while (si.hasNext()) {
            Spark sp = si.next();
            if (--sp.launch <= 0) {
                sparkArc(server, handPos(), sp.pos);
                sound(server, EmberSmithSetup.SPARKS.get(), 1.2f, 1.3f);
                si.remove();
            }
        }
        if (steamTicks > 0) {
            steamTicks--;
            if (steamTicks % 2 == 0) {
                steamParticles(server);
            }
        }
        if (weakTicks > 0) {
            weakTicks--;
            if (weakTicks == 0) {
                entityData.set(WEAK, false);
            }
        }
    }

    private void sparkArc(ServerLevel server, Vec3 from, Vec3 to) {
        for (double t = 0; t <= 1; t += 0.06) {
            Vec3 p = from.lerp(to, t).add(0, 4.0 * t * (1 - t) * 3.5, 0);
            warn(server, ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    private void steamParticles(ServerLevel server) {
        double cx = AshSmithArena.CX + 0.5;
        double cz = AshSmithArena.CZ + 0.5;
        for (int i = 0; i < 28; i++) {
            double x = cx + steamSide * random.nextDouble() * (AshSmithArena.RADIUS - 0.5);
            double z = cz + (random.nextDouble() * 2 - 1) * (AshSmithArena.RADIUS - 1);
            double dx = x - cx;
            double dz = z - cz;
            if (dx * dx + dz * dz > (AshSmithArena.RADIUS - 0.5) * (AshSmithArena.RADIUS - 0.5)) {
                continue;
            }
            double y = AshSmithArena.FLOOR + 1.0 + random.nextDouble() * 4.0;
            warn(server, ParticleTypes.CLOUD, x, y, z, 1, 0.5, 0.4, 0.5, 0.01);
        }
    }

    // ---------------------------------------------------------------- 位置・向き
    private void faceToward(Vec3 p) {
        double dx = p.x - getX();
        double dz = p.z - getZ();
        if (dx * dx + dz * dz < 1e-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private double horizontalDistance(Vec3 p) {
        double dx = p.x - getX();
        double dz = p.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void sound(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        server.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    private void broadcast(ServerLevel server, Component message) {
        for (ServerPlayer p : fighters(server)) {
            Notice.bar(p, message);
        }
    }

    // ---------------------------------------------------------------- 傷
    // 受ける傷: 参加者の人数で割る。吐いたあとの 3 秒は、後ろからの傷が 1.5 倍
    public static float takenFor(float amount, float multiplier, boolean weakFromBehind) {
        return amount / multiplier * (weakFromBehind ? WEAK_MULT : 1f);
    }

    // 傷の出どころが体の後ろ側か (鞴のある背)
    public boolean isBehind(@Nullable Vec3 from) {
        if (from == null) {
            return false;
        }
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        double dx = from.x - getX();
        double dz = from.z - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-4) {
            return false;
        }
        return (dx * look.x + dz * look.z) / (d * Math.sqrt(look.x * look.x + look.z * look.z)) < -0.2;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return super.hurt(source, amount);
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL)) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        boolean back = isWeak() && isBehind(from);
        return super.hurt(source, takenFor(amount, multiplier, back));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bossBar.removeAllPlayers();
        entityData.set(WEAK, false);
        entityData.set(COLD, false);
        sparks.clear();
        if (level() instanceof ServerLevel server) {
            AshSmithBattle.onDefeated(server, this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        Telegraph.cancelAll(this);
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    private void updateBossBar(ServerLevel server) {
        List<ServerPlayer> keep = new ArrayList<>();
        for (ServerPlayer p : Players.snapshot(server.players())) {
            if (isAlive() && p.distanceToSqr(this) < 48 * 48 && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(this, p)) {
                keep.add(p);
            }
        }
        for (ServerPlayer p : new ArrayList<>(bossBar.getPlayers())) {
            if (!keep.contains(p)) {
                bossBar.removePlayer(p);
            }
        }
        for (ServerPlayer p : keep) {
            if (!bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return EmberSmithSetup.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return EmberSmithSetup.DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.0f;
    }
}
