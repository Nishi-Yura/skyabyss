package jp.revati.skyabyss.titan.ember.smith;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
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
// 技 (予告は粒 (制限を無視)・技ごとの音・動き。人がいなければ回らない):
//   鎚打ち   距離 7 未満。1.0 秒、鎚を高く振り上げる (帯の予告と低い音)。前方 幅 3・長さ 8 の帯を打つ (16)。
//            打った帯の床に亀裂が走り、1.2 秒後にその帯から熱が噴く (噴気 10 と熱 +20。予告は亀裂の白い光としゅうという音)
//   鞴の息   距離 10 未満、12 秒ごと。1.5 秒、背の鞴が膨らむ (吸いこむ音)。前方 60 度・長さ 10 の扇に熱風 (8、強い押し、熱 +35)。
//            吐いたあと 3 秒、背の鞴が白く光り、後ろからの傷が 1.5 倍
//   焼き入れ 30 秒ごと。部屋の焼き入れの槽へ歩き、鎚を沈める。部屋の半分が 6 秒、湯気でおおわれる (濃い白い粒)。
//            鎚が冷えて、次の 3 回の鎚打ちは噴気が出ない代わりに、打ち 1.5 倍 (24) と、床を走る輪 (12。跳べば当たらない)
//   火花散らし 距離 18 未満。0.8 秒の溜めのあと、火花が人の足もとへ弧を描いて 6 つ (落ちる所に赤い輪 1.0 秒。10)
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
    public static final double RING_MAX = 9;

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

    private void hurtPlayer(ServerPlayer p, DamageSource source, float amount, String kind) {
        DamageLog log = testLog;
        if (log != null) {
            log.hit(p, amount, kind);
        }
        p.hurt(source, amount);
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
    private final List<Vent> vents = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final Set<java.util.UUID> ringHit = new HashSet<>();

    // 噴く予定の帯 (打った帯)。ticks が 0 で噴く
    private static final class Vent {
        final Vec3 origin;
        final Vec3 dir;
        int ticks = VENT_DELAY;

        Vent(Vec3 origin, Vec3 dir) {
            this.origin = origin;
            this.dir = dir;
        }
    }

    // 床を走る輪 (冷えた鎚)。半径が広がる。跳んでいる (床から 0.6 以上) と当たらない
    private static final class Ring {
        final Vec3 center;
        double radius = 1;
        final Set<java.util.UUID> hit = new HashSet<>();

        Ring(Vec3 center) {
            this.center = center;
        }
    }

    // 火花: launch tick 後に着地点へ弧、land tick 後に着く
    private static final class Spark {
        final Vec3 pos;
        int launch;
        int land = -1;

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

    public int ventCount() {
        return vents.size();
    }

    public int ringCount() {
        return rings.size();
    }

    public int sparkCount() {
        return sparks.size();
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
        for (ServerPlayer p : server.players()) {
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
            }
            case BREATH -> {
                playAction("breath");
                sound(server, EmberSmithSetup.INHALE.get(), 3f, 1f);
                broadcast(server, ModText.battle("message.skyabyss.ember.smith.breath"));
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
    private void tickSlam(ServerLevel server) {
        if (actionTick < SLAM_WINDUP) {
            if (actionTick % 2 == 0) {
                stripOutline(server, slamOrigin, aimDir, slamCold ? new Vector3f(0.55f, 0.75f, 1.0f) : new Vector3f(0.35f, 0.30f, 0.30f), 1.4f);
            }
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
        if (actionTick == SLAM_WINDUP) {
            float damage = SLAM_DAMAGE * (slamCold ? COLD_MULT : 1f);
            for (ServerPlayer p : fighters(server)) {
                if (inStrip(p.position(), slamOrigin, aimDir, SLAM_LENGTH, SLAM_HALF_WIDTH)) {
                    hurtPlayer(p, attackSource(), damage, "slam");
                }
            }
            Vec3 impact = slamOrigin.add(aimDir.scale(3.5));
            sound(server, EmberSmithSetup.SLAM.get(), 3.5f, slamCold ? 0.6f : 0.75f);
            warn(server, ParticleTypes.EXPLOSION, impact.x, impact.y + 0.2, impact.z, 1, 0, 0, 0, 0);
            warn(server, ParticleTypes.POOF, impact.x, impact.y + 0.2, impact.z, 24, 1.2, 0.1, 1.2, 0.08);
            if (slamCold) {
                coldSlams--;
                if (coldSlams <= 0) {
                    entityData.set(COLD, false);
                }
                rings.add(new Ring(impact));
            } else {
                vents.add(new Vent(slamOrigin, aimDir));
                sound(server, EmberSmithSetup.INHALE.get(), 1.2f, 2.0f);
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

    private void stripOutline(ServerLevel server, Vec3 origin, Vec3 dir, Vector3f color, float size) {
        DustParticleOptions dust = new DustParticleOptions(color, size);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (double t = 0.5; t <= SLAM_LENGTH; t += 0.8) {
            Vec3 c = origin.add(dir.scale(t));
            for (int s = -1; s <= 1; s += 2) {
                Vec3 e = c.add(side.scale(s * SLAM_HALF_WIDTH));
                warn(server, dust, e.x, origin.y + 0.1, e.z, 1, 0, 0, 0, 0);
            }
        }
        Vec3 end = origin.add(dir.scale(SLAM_LENGTH));
        for (double w = -SLAM_HALF_WIDTH; w <= SLAM_HALF_WIDTH; w += 0.75) {
            Vec3 e = end.add(side.scale(w));
            warn(server, dust, e.x, origin.y + 0.1, e.z, 1, 0, 0, 0, 0);
        }
    }

    // ---- 鞴の息
    private void tickBreath(ServerLevel server) {
        if (actionTick < BREATH_INHALE) {
            if (target != null && actionTick < BREATH_INHALE - 6) {
                faceToward(target.position());
            }
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
            if (actionTick % 4 == 0) {
                fanOutline(server, new Vector3f(1.0f, 0.55f, 0.15f));
            }
            return;
        }
        if (actionTick == BREATH_INHALE) {
            Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
            sound(server, EmberSmithSetup.BLAST.get(), 3.5f, 1f);
            for (ServerPlayer p : fighters(server)) {
                if (inFan(p.position(), position(), look)) {
                    hurtPlayer(p, attackSource(), BREATH_DAMAGE, "breath");
                    Vec3 away = p.position().subtract(position());
                    away = new Vec3(away.x, 0, away.z).normalize();
                    p.setDeltaMovement(away.x * 1.9, 0.45, away.z * 1.9);
                    p.hurtMarked = true;
                    EmberHeat.add(p, BREATH_HEAT, EmberHeat.Reason.SMITH);
                }
            }
            for (int i = 0; i < 30; i++) {
                double a = Math.toRadians((random.nextDouble() - 0.5) * 2 * BREATH_HALF_ANGLE);
                double d = 1.5 + random.nextDouble() * (BREATH_LENGTH - 1.5);
                Vec3 p = position().add(rotateY(look, a).scale(d)).add(0, 1.0 + random.nextDouble(), 0);
                warn(server, i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.02);
            }
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

    private void fanOutline(ServerLevel server, Vector3f color) {
        DustParticleOptions dust = new DustParticleOptions(color, 1.3f);
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        for (int e = -1; e <= 1; e += 2) {
            Vec3 edge = rotateY(look, Math.toRadians(e * BREATH_HALF_ANGLE));
            for (double d = 1.5; d <= BREATH_LENGTH; d += 1.2) {
                Vec3 p = position().add(edge.scale(d));
                warn(server, dust, p.x, position().y + 0.1, p.z, 1, 0, 0, 0, 0);
            }
        }
        for (double a = -BREATH_HALF_ANGLE; a <= BREATH_HALF_ANGLE; a += 7.5) {
            Vec3 p = position().add(rotateY(look, Math.toRadians(a)).scale(BREATH_LENGTH));
            warn(server, dust, p.x, position().y + 0.1, p.z, 1, 0, 0, 0, 0);
        }
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
                for (int i = 0; i < SPARK_COUNT; i++) {
                    ServerPlayer p = ps.get(i % ps.size());
                    double ox = i < ps.size() ? 0 : (random.nextDouble() - 0.5) * 3.0;
                    double oz = i < ps.size() ? 0 : (random.nextDouble() - 0.5) * 3.0;
                    sparks.add(new Spark(new Vec3(p.getX() + ox, AshSmithArena.FLOOR + 1.0, p.getZ() + oz), i * SPARK_GAP));
                }
            }
        }
        if (actionTick >= SPARKS_CHARGE + 12) {
            sparksCooldown = SPARKS_COOLDOWN + random.nextInt(60);
            finish();
        }
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

    // ---------------------------------------------------------------- 続いている効果 (噴く帯・輪・火花・湯気・弱み)
    private void tickEffects(ServerLevel server) {
        Iterator<Vent> vi = vents.iterator();
        while (vi.hasNext()) {
            Vent v = vi.next();
            v.ticks--;
            if (v.ticks % 2 == 0) {
                crackParticles(server, v);
            }
            if (v.ticks == VENT_DELAY - 2 || v.ticks == 10) {
                sound(server, EmberSmithSetup.INHALE.get(), 1.0f, 2.2f);
            }
            if (v.ticks <= 0) {
                burst(server, v);
                vi.remove();
            }
        }
        Iterator<Ring> ri = rings.iterator();
        while (ri.hasNext()) {
            Ring r = ri.next();
            r.radius += RING_SPEED;
            ringParticles(server, r);
            for (ServerPlayer p : fighters(server)) {
                double dx = p.getX() - r.center.x;
                double dz = p.getZ() - r.center.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (!r.hit.contains(p.getUUID()) && Math.abs(d - r.radius) <= 0.9 && p.getY() - r.center.y < 0.6) {
                    r.hit.add(p.getUUID());
                    hurtPlayer(p, attackSource(), RING_DAMAGE, "ring");
                }
            }
            if (r.radius > RING_MAX) {
                ri.remove();
            }
        }
        Iterator<Spark> si = sparks.iterator();
        while (si.hasNext()) {
            Spark s = si.next();
            if (s.land < 0) {
                if (--s.launch <= 0) {
                    s.land = SPARK_WARN;
                    sparkArc(server, handPos(), s.pos);
                    sound(server, EmberSmithSetup.SPARKS.get(), 1.2f, 1.3f);
                }
                continue;
            }
            if (s.land % 2 == 0) {
                sparkRing(server, s.pos, s.land / (float) SPARK_WARN);
            }
            if (--s.land <= 0) {
                for (ServerPlayer p : fighters(server)) {
                    double dx = p.getX() - s.pos.x;
                    double dz = p.getZ() - s.pos.z;
                    if (dx * dx + dz * dz <= SPARK_RADIUS * SPARK_RADIUS && p.getY() - s.pos.y < 2.0) {
                        hurtPlayer(p, attackSource(), SPARK_DAMAGE, "spark");
                    }
                }
                warn(server, ParticleTypes.LAVA, s.pos.x, s.pos.y + 0.2, s.pos.z, 14, 0.5, 0.2, 0.5, 0.1);
                warn(server, ParticleTypes.FLAME, s.pos.x, s.pos.y + 0.2, s.pos.z, 18, 0.5, 0.3, 0.5, 0.08);
                server.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.5f, 0.8f);
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

    private void crackParticles(ServerLevel server, Vent v) {
        DustParticleOptions white = new DustParticleOptions(new Vector3f(1.0f, 1.0f, 0.95f), 1.1f);
        Vec3 side = new Vec3(-v.dir.z, 0, v.dir.x);
        for (double t = 0.8; t <= SLAM_LENGTH; t += 0.55) {
            double zig = Math.sin(t * 2.3) * 0.5;
            Vec3 c = v.origin.add(v.dir.scale(t)).add(side.scale(zig));
            warn(server, ParticleTypes.END_ROD, c.x, v.origin.y + 0.15, c.z, 1, 0.02, 0, 0.02, 0);
            if (random.nextInt(3) == 0) {
                warn(server, white, c.x + side.x * 0.5, v.origin.y + 0.15, c.z + side.z * 0.5, 1, 0, 0, 0, 0);
            }
        }
    }

    private void burst(ServerLevel server, Vent v) {
        sound(server, EmberSmithSetup.BLAST.get(), 2.5f, 0.9f);
        Vec3 side = new Vec3(-v.dir.z, 0, v.dir.x);
        for (double t = 0.8; t <= SLAM_LENGTH; t += 0.6) {
            Vec3 c = v.origin.add(v.dir.scale(t));
            warn(server, ParticleTypes.FLAME, c.x, v.origin.y + 0.3, c.z, 4, SLAM_HALF_WIDTH * 0.5, 0.2, SLAM_HALF_WIDTH * 0.5, 0.12);
            warn(server, ParticleTypes.LAVA, c.x, v.origin.y + 0.3, c.z, 1, 0.5, 0.1, 0.5, 0.0);
        }
        for (ServerPlayer p : fighters(server)) {
            if (inStrip(p.position(), v.origin, v.dir, SLAM_LENGTH, SLAM_HALF_WIDTH)) {
                hurtPlayer(p, damage(EmberIds.HEAT_BURST, this), VENT_DAMAGE, "vent");
                EmberHeat.add(p, VENT_HEAT, EmberHeat.Reason.SMITH);
            }
        }
    }

    private void ringParticles(ServerLevel server, Ring r) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.7f, 0.85f, 1.0f), 1.5f);
        int n = Math.max(12, (int) (r.radius * 6));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            Vec3 p = r.center.add(Math.cos(a) * r.radius, 0.15, Math.sin(a) * r.radius);
            if (AshSmithArena.inside(p)) {
                warn(server, dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void sparkArc(ServerLevel server, Vec3 from, Vec3 to) {
        for (double t = 0; t <= 1; t += 0.06) {
            Vec3 p = from.lerp(to, t).add(0, 4.0 * t * (1 - t) * 3.5, 0);
            warn(server, ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    private void sparkRing(ServerLevel server, Vec3 at, float left) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.05f), 1.4f);
        double r = SPARK_RADIUS * (0.55 + 0.45 * (1 - left));
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI * 2 / 20;
            warn(server, dust, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
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
        vents.clear();
        rings.clear();
        sparks.clear();
        if (level() instanceof ServerLevel server) {
            AshSmithBattle.onDefeated(server, this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    private void updateBossBar(ServerLevel server) {
        List<ServerPlayer> keep = new ArrayList<>();
        for (ServerPlayer p : server.players()) {
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
