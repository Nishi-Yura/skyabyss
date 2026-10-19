package jp.revati.skyabyss.titan.storm.raiju;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.storm.back.StormRods;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

// 中ボス「雷獣」(12 の 5.2節)。右手首の瘤に棲む、灰青と青白い毛の狼に近い獣 (ラフ A)。
// 巣の 6 本の避雷の柱の先にうずくまり、雷になって柱から柱へ渡る (雷渡り)。着いた柱が接地していると、痺れて 4 秒落ちる。
//   雷渡り   柱の上で 6〜9 秒ごと (怒ると 4〜6 秒)。1.0 秒、今の柱から次の柱へ光る線 (予告)。次の柱は「いちばん近くの人に近い柱」
//            (その時に接地している柱は選ばない)。線の下 (床の上の帯) の人に 12
//   降りかかり 柱で 2 回渡ったあと、いちばん近くの人へ柱から跳びかかる (0.8 秒の溜め、着く所に輪)。11
//   飛びかかり 床にいて距離 4〜10。0.8 秒 (怒ると 0.6 秒) 身を低くし、跳びかかる。11、少し跳ね飛ばす
//   爪の連打 距離 3 未満。0.5 秒ごとに 3 回、5 ずつ
//   雷鳴の咆哮 体力 50% を初めて切ったとき一度。1.5 秒。人の足もとに招雷の輪を 6 つ (9)、雷蛾を 2 体 (敵の担当の雷蛾があれば)
//   柱へ戻る 床で 10 秒たつと、いちばん近い柱へ跳び上がる (予告なし・傷なし)
// 体力 430。挑む人数の倍率 (1 + 0.6 × (人数 - 1)) で受ける傷を割る。痺れて落ちている間は受ける傷 1.5 倍。
// 予告は粒 (制限を無視)・技ごとの音・動き。動き (跳躍・渡り) は tick ごとに位置を決める (経路探しは床の上だけ)
public class Raiju extends GuardMob {
    public static final float HEALTH = 430;
    public static final float ARMOR = 10;
    public static final float TOUGHNESS = 4;
    public static final float RAIL_DAMAGE = 12;
    public static final float POUNCE_DAMAGE = 11;
    public static final float ROAR_STRIKE_DAMAGE = 9;
    public static final float CLAW_DAMAGE = 5;
    public static final int RAIL_WARN = 20;
    public static final int RAIL_TRAVEL = 5;
    public static final int RAILS_PER_PERCH = 2;
    public static final int STUN_TICKS = 80;
    public static final float STUN_TAKEN = 1.5f;
    public static final int POUNCE_WINDUP = 16;
    public static final int POUNCE_WINDUP_ANGRY = 12;
    public static final int POUNCE_FLIGHT = 10;
    public static final int DESCEND_FLIGHT = 14;
    public static final int CLAW_TICKS = 42;
    // 爪の連打 (扇の印 0.9 秒、そのあと 0.5 秒ごとに 3 回)
    public static final int CLAW_WARN = 18;
    public static final double CLAW_RANGE = 3.6;
    public static final double CLAW_HALF_ANGLE = 70;
    public static final float CLAW_SHOCK_DAMAGE = 4;
    public static final double CLAW_SHOCK_LENGTH = 9;
    // 雷渡りの枝分かれ (人数で増える。着いた柱から別の柱へ)
    public static final float RAIL_FORK_DAMAGE = 9;
    public static final double RAIL_FORK_HALF_WIDTH = 1.0;
    // 飛びかかりの着地の円と、人数で増える雷の円・怒りの雷の輪
    public static final double POUNCE_RADIUS = 2.4;
    public static final double ECHO_RADIUS = 1.6;
    public static final float ECHO_DAMAGE = 8;
    public static final float POUNCE_RING_DAMAGE = 5;
    public static final double POUNCE_RING_REACH = 8.0;
    public static final int ROAR_TICKS = 30;
    public static final int ROAR_RINGS = 6;
    public static final int RING_WARN = 24;
    public static final int GROUND_TICKS = 200;
    public static final int GROUND_AFTER_STUN = 120;
    public static final int RETURN_FLIGHT = 12;
    public static final double RAIL_HALF_WIDTH = 1.3;
    public static final String TRIAL_TAG = "skyabyss_raiju_trial";

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(Raiju.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ANGRY = SynchedEntityData.defineId(Raiju.class, EntityDataSerializers.BOOLEAN);
    public static final int MODE_GROUND = 0;
    public static final int MODE_PERCH = 1;
    public static final int MODE_RAIL = 2;
    public static final int MODE_STUNNED = 3;
    public static final int MODE_AIR = 4;

    public enum Action {
        NONE, RAIL, DESCEND, POUNCE, CLAW, ROAR, RETURN
    }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable
    private RaijuNest.Site site;
    private Action action = Action.NONE;
    private int actionTick;
    private int rod = -1;
    private int dest = -1;
    private int perchTimer;
    private int railsLeft;
    private int groundTimer;
    private int stunTicks;
    private int pounceCooldown = 30;
    private int clawCooldown;
    private boolean roared;
    private float multiplier = 1f;
    // 試験・開発用: 次の技を始めない / 巣にいる人数を実際より多いことにする (0 なら実際の人数) / 次の雷渡りの行き先
    private boolean holdAttacks;
    private int partyOverride;
    private int forcedDest = -1;
    // 飛びかかりの着地点 (技の始めに決まる)
    @Nullable
    private Vec3 pounceLand;
    @Nullable
    private ServerPlayer target;
    private Vec3 aim = Vec3.ZERO;
    // 跳躍・渡りの軌道
    private Vec3 from = Vec3.ZERO;
    private Vec3 to = Vec3.ZERO;
    private int flight;
    private int flightTick;
    private double arc;
    private final Set<UUID> hit = new HashSet<>();

    public Raiju(EntityType<? extends Monster> type, Level level) {
        super(type, level, "raiju");
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ATTACK_DAMAGE, CLAW_DAMAGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, MODE_GROUND);
        builder.define(ANGRY, false);
    }

    @Override
    protected void registerGoals() {
        // 動きは aiStep の中で決める
    }

    // ---------------------------------------------------------------- 戦いの部屋と結びつける (RaijuBattle から)

    public void bind(RaijuNest.Site site, int challengers, int startRod) {
        this.site = site;
        multiplier = scaleFor(challengers);
        addTag(TitanBackTrials.HEART_TAG);
        addTag(TRIAL_TAG);
        perchOn(startRod);
        perchTimer = 60;
        railsLeft = RAILS_PER_PERCH;
    }

    // 挑む人数の倍率 (上限なし。パーティの 6 人で 4.0)
    public static float scaleFor(int challengers) {
        return 1f + 0.6f * (Math.max(1, challengers) - 1);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    private void setMode(int m) {
        entityData.set(MODE, m);
        setNoGravity(m != MODE_GROUND && m != MODE_STUNNED);
    }

    public boolean isAngry() {
        return entityData.get(ANGRY);
    }

    public boolean isStunned() {
        return mode() == MODE_STUNNED;
    }

    public Action currentAction() {
        return action;
    }

    public int rod() {
        return rod;
    }

    public int dest() {
        return dest;
    }

    public float multiplier() {
        return multiplier;
    }

    public boolean roared() {
        return roared;
    }

    public float bossBarProgress() {
        return bossBar.getProgress();
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
        pounceCooldown--;
        clawCooldown--;
        target = pickTarget(server);
        int m = mode();
        if (m == MODE_PERCH || m == MODE_RAIL || m == MODE_AIR) {
            setDeltaMovement(Vec3.ZERO);
        }
        if (!roared && !holdAttacks && getHealth() < getMaxHealth() * 0.5f && action == Action.NONE && (m == MODE_GROUND || m == MODE_PERCH)) {
            begin(server, Action.ROAR);
        }
        if (action != Action.NONE) {
            getNavigation().stop();
            tickAction(server);
            return;
        }
        if (holdAttacks) {
            return;
        }
        switch (m) {
            case MODE_PERCH -> tickPerch(server);
            case MODE_STUNNED -> tickStunned(server);
            case MODE_GROUND -> tickGround(server);
            default -> setMode(MODE_GROUND);
        }
    }

    private void tickPerch(ServerLevel server) {
        holdOnRod();
        if (target != null) {
            face(target.position());
        }
        if (--perchTimer > 0) {
            return;
        }
        if (target == null || site == null) {
            perchTimer = 40;
        } else if (railsLeft > 0) {
            begin(server, Action.RAIL);
        } else {
            begin(server, Action.DESCEND);
        }
    }

    private void tickStunned(ServerLevel server) {
        if (tickCount % 4 == 0) {
            warn(server, ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 6, 0.9, 0.4, 0.9, 0.08);
        }
        if (--stunTicks > 0) {
            return;
        }
        setMode(MODE_GROUND);
        playAction("stand_up");
        groundTimer = GROUND_AFTER_STUN;
        pounceCooldown = Math.max(pounceCooldown, 20);
    }

    private void tickGround(ServerLevel server) {
        if (site != null && --groundTimer <= 0) {
            begin(server, Action.RETURN);
            return;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30f, 30f);
        double dist = horizontalDistance(target.position());
        if (dist < 3.0 && clawCooldown <= 0) {
            begin(server, Action.CLAW);
        } else if (dist >= 4 && dist <= 10 && pounceCooldown <= 0) {
            begin(server, Action.POUNCE);
        } else if (dist > 2.2) {
            getNavigation().moveTo(target, 1.25);
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

    // 戦っている人: 巣の中の、この雷獣のグループの人 (部屋に結びついていなければ 24 ブロック以内。グループのない雷獣なら誰でも)
    public List<ServerPlayer> fighters(ServerLevel server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Players.snapshot(server.players())) {
            if (canFight(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private boolean canFight(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || p.isCreative() || !jp.revati.skyabyss.titan.group.GroupOwned.mayTarget(this, p)) {
            return false;
        }
        return site != null ? site.inside(p.position()) : p.distanceToSqr(this) < 24 * 24;
    }

    // ---- 試験・開発用 ----
    public void holdAttacks() {
        holdAttacks = true;
    }

    public void setPartyOverride(int count) {
        partyOverride = count;
    }

    // 試験用: 怒った状態にする (咆哮は出さない)
    public void makeAngryForTest() {
        roared = true;
        entityData.set(ANGRY, true);
    }

    public int actionTickCount() {
        return actionTick;
    }

    private int present(ServerLevel server) {
        return Math.max(partyOverride, fighters(server).size());
    }

    // ---- 人数 n で増えるもの (12 の 5.2節) ----
    // 雷渡りの枝分かれの線
    public static int railForks(int n) {
        return n >= 6 ? 3 : n >= 4 ? 2 : n >= 2 ? 1 : 0;
    }

    // 飛びかかりのとき、ほかの人の足もとにも落ちる雷の円
    public static int pounceEchoes(int n) {
        return Math.min(Math.max(0, n - 1), 3);
    }

    // 爪の連打に添える雷の線 (ほかの人の方へ)
    public static int clawShocks(int n) {
        return Math.min(Math.max(0, n - 1), 3);
    }

    // 咆哮の招雷の輪の数
    public static int roarRings(int n) {
        return Math.min(12, Math.max(ROAR_RINGS, 2 * n));
    }

    // 技の床の高さと、予告の組み立て (持ち主は雷獣、当たるのは巣で戦っている人)
    private double floorY() {
        return site != null ? site.floor() : getY();
    }

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
        return preset(Telegraph.in(server, shape).floor(floorY()).warn(warn).owner(this).victims(this::canFight).reach(3.5), style);
    }

    // ---------------------------------------------------------------- 技
    public void forceAction(Action a) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        action = Action.NONE;
        setInvisible(false);
        if ((a == Action.RAIL || a == Action.DESCEND) && mode() != MODE_PERCH) {
            int i = nearestRod(position(), false);
            if (i < 0) {
                return;
            }
            perchOn(i);
        }
        if ((a == Action.POUNCE || a == Action.CLAW || a == Action.RETURN) && mode() != MODE_GROUND) {
            land(position());
        }
        begin(server, a);
    }

    // 指定の柱へ雷渡りさせる (自己テスト用。柱の上にいなければ、いちばん近い柱へ移してから)
    public void railTo(int destRod) {
        if (!(level() instanceof ServerLevel server) || site == null) {
            return;
        }
        if (mode() != MODE_PERCH) {
            perchOn(destRod == 0 ? 1 : 0);
        }
        action = Action.NONE;
        forcedDest = destRod;
        begin(server, Action.RAIL);
    }

    private void begin(ServerLevel server, Action a) {
        action = a;
        actionTick = 0;
        if (a.ordinal() != 0) {
            jp.revati.skyabyss.combat.BossCue.send(this, "raiju", a.name().toLowerCase(java.util.Locale.ROOT), 30);
        }
        hit.clear();
        getNavigation().stop();
        switch (a) {
            case RAIL -> {
                dest = forcedDest >= 0 ? forcedDest : chooseDest();
                forcedDest = -1;
                if (dest < 0) {
                    action = Action.NONE;
                    perchTimer = 40;
                    return;
                }
                face(Vec3.atCenterOf(site.rodTop(dest)));
                playAction("rail_charge");
                sound(server, RaijuSetup.RAIL_CHARGE.get(), 2.5f, 1f);
                if (rod >= 0) {
                    railMarkers(server, perchSpot(rod), perchSpot(dest));
                }
            }
            case DESCEND, POUNCE -> {
                aim = target != null ? target.position() : position().add(getLookAngle().scale(5));
                face(aim);
                playAction("pounce_crouch");
                sound(server, RaijuSetup.POUNCE.get(), 2f, a == Action.DESCEND ? 0.85f : 1f);
                int windup = a == Action.DESCEND ? POUNCE_WINDUP : (isAngry() ? POUNCE_WINDUP_ANGRY : POUNCE_WINDUP);
                pounceMarkers(server, windup + (a == Action.DESCEND ? DESCEND_FLIGHT : POUNCE_FLIGHT));
            }
            case CLAW -> {
                playAction("claw");
                if (target != null) {
                    face(target.position());
                }
                clawMarkers(server);
            }
            case ROAR -> {
                roared = true;
                entityData.set(ANGRY, true);
                playAction("roar");
                sound(server, RaijuSetup.ROAR.get(), 4f, 1f);
                broadcast(server, ModText.battle("message.skyabyss.raiju.roar"));
            }
            case RETURN -> {
                int i = nearestRod(position(), true);
                if (i < 0) {
                    i = nearestRod(position(), false);
                }
                if (i < 0 || site == null) {
                    action = Action.NONE;
                    groundTimer = GROUND_TICKS;
                    return;
                }
                dest = i;
                Vec3 top = perchSpot(i);
                face(top);
                playAction("leap_up");
                sound(server, RaijuSetup.POUNCE.get(), 1.5f, 1.3f);
                startFlight(top, RETURN_FLIGHT, 2.5);
            }
            default -> {
            }
        }
    }

    private void tickAction(ServerLevel server) {
        actionTick++;
        switch (action) {
            case RAIL -> tickRail(server);
            case DESCEND -> tickPounce(server, POUNCE_WINDUP, DESCEND_FLIGHT, 2.0, true);
            case POUNCE -> tickPounce(server, isAngry() ? POUNCE_WINDUP_ANGRY : POUNCE_WINDUP, POUNCE_FLIGHT, 1.5, false);
            case CLAW -> tickClaw(server);
            case ROAR -> tickRoar(server);
            case RETURN -> {
                if (flightStep()) {
                    arrive(server, dest, false);
                    finish();
                }
            }
            default -> finish();
        }
    }

    // 雷渡り: 1.0 秒の予告線 → 雷になって 5 tick で渡る → 着いた柱が接地していれば落ちる
    private void tickRail(ServerLevel server) {
        if (site == null || dest < 0 || rod < 0) {
            finish();
            return;
        }
        Vec3 a = perchSpot(rod);
        Vec3 b = perchSpot(dest);
        if (actionTick < RAIL_WARN) {
            holdOnRod();
            if (actionTick % 2 == 1) {
                railLine(server, a, b, actionTick < RAIL_WARN / 2 ? 1.0f : 1.6f);
            }
            if (actionTick % 4 == 0) {
                warn(server, ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 8, 0.6, 0.6, 0.6, 0.1);
            }
            return;
        }
        if (actionTick == RAIL_WARN) {
            setInvisible(true);
            setMode(MODE_RAIL);
            from = a;
            to = b;
            flight = RAIL_TRAVEL;
            flightTick = 0;
            arc = 0;
            sound(server, RaijuSetup.RAIL.get(), 3f, 1f);
        }
        Vec3 before = position();
        boolean done = flightStep();
        // 雷の筋: 通ったところを白く
        Vec3 now = position();
        for (double t = 0; t <= 1; t += 0.12) {
            Vec3 p = before.lerp(now, t);
            warn(server, ParticleTypes.END_ROD, p.x, p.y + 0.9, p.z, 2, 0.15, 0.15, 0.15, 0.01);
            warn(server, ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 0.9, p.z, 3, 0.3, 0.3, 0.3, 0.05);
        }
        if (done) {
            setInvisible(false);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt != null) {
                bolt.setVisualOnly(true);
                bolt.moveTo(b.x, b.y - 1, b.z);
                server.addFreshEntity(bolt);
            }
            railsLeft--;
            arrive(server, dest, true);
            finish();
        }
    }

    // 予告線 (見た目): 柱の先から柱の先へ、高い所の青い線。当たる所の印は Telegraph (railMarkers)
    private void railLine(ServerLevel server, Vec3 a, Vec3 b, float size) {
        DustParticleOptions blue = new DustParticleOptions(new Vector3f(0.65f, 0.85f, 1.0f), size);
        double len = a.distanceTo(b);
        int n = Math.max(6, (int) (len * 2.5));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n);
            warn(server, blue, p.x, p.y + 0.8, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static double trim(double len) {
        return Math.min(0.3, 1.8 / Math.max(len, 1));
    }

    // 雷渡りの当たる所: 線の下の床の帯 (幅 2.6。両端の柱のそば 1.8 ブロックは除く)。印が満ちて、雷になって渡りはじめる瞬間に帯の中の人に 12。
    // 人数で、着いた柱から別の柱 (ほかの人にいちばん近い柱) へ枝分かれの帯が増える (最大 3 本。9)
    private void railMarkers(ServerLevel server, Vec3 a, Vec3 b) {
        railBand(server, a, b, RAIL_HALF_WIDTH, RAIL_DAMAGE, D);
        int forks = railForks(present(server));
        if (forks == 0 || site == null) {
            return;
        }
        List<Integer> picks = new ArrayList<>();
        List<ServerPlayer> others = new ArrayList<>(fighters(server));
        others.sort(Comparator.comparingDouble(pl -> pl.distanceToSqr(b.x, b.y, b.z)));
        for (ServerPlayer pl : others) {
            int r = nearestRod(pl.position(), false);
            if (r >= 0 && r != rod && r != dest && !picks.contains(r)) {
                picks.add(r);
            }
            if (picks.size() >= forks) {
                break;
            }
        }
        for (int i = 0; i < RaijuNest.RODS && picks.size() < forks && picks.size() < partyOverride; i++) {
            if (i != rod && i != dest && !picks.contains(i)) {
                picks.add(i);
            }
        }
        for (int r : picks) {
            railBand(server, b, perchSpot(r), RAIL_FORK_HALF_WIDTH, RAIL_FORK_DAMAGE, D);
        }
    }

    private void railBand(ServerLevel server, Vec3 a, Vec3 b, double halfWidth, float damage, int style) {
        double len = Math.hypot(b.x - a.x, b.z - a.z);
        if (len < 2) {
            return;
        }
        double t = trim(len);
        double angle = Telegraph.angleOf(b.x - a.x, b.z - a.z);
        tg(server, TelegraphShape.line(a.x + (b.x - a.x) * t, a.z + (b.z - a.z) * t, angle, len * (1 - 2 * t), halfWidth), RAIL_WARN + 1, style)
                .onHit((s, player) -> {
                    if (hit.add(player.getUUID())) {
                        player.hurt(attackSource(), damage);
                        s.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
                    }
                })
                .start();
    }

    // 柱に着いた。接地していれば痺れて落ちる、そうでなければ柱の上にうずくまる
    private void arrive(ServerLevel server, int i, boolean fromRail) {
        if (site != null && StormRods.isGrounded(site.rod(i))) {
            stun(server, i);
            return;
        }
        perchOn(i);
        playAction("land");
        if (!fromRail) {
            railsLeft = RAILS_PER_PERCH;
        }
        perchTimer = railInterval();
    }

    private void stun(ServerLevel server, int i) {
        Vec3 at = site.dropPoint(i);
        moveTo(at.x, site.floor(), at.z, getYRot(), 0);
        rod = -1;
        setMode(MODE_STUNNED);
        stunTicks = STUN_TICKS;
        playAction("stun_fall");
        sound(server, RaijuSetup.STUN.get(), 3f, 1f);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, site.floor() + 1, at.z, 60, 1.2, 0.8, 1.2, 0.3);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, site.floor() + 0.5, at.z, 20, 1.0, 0.3, 1.0, 0.02);
        broadcast(server, ModText.battle("message.skyabyss.raiju.stunned"));
        SkyAbyss.LOGGER.debug("Raiju fell off grounded rod {}", i);
    }

    // 跳びかかりの印: 着く所 (技の始めの狙いで決まる。追わない) に半径 2.4 の円が満ちて、着いた瞬間にその中の人に 11。跳んでいる途中は当たらない。
    // 人数で、ほかの人の足もとにも雷の円 (半径 1.6、8。最大 3 つ)。怒ると、着地から雷の輪が広がる (跳べば当たらない。安全な筋が 2 本。3 人から二重)
    private void pounceMarkers(ServerLevel server, int total) {
        Vec3 land = clampToNest(aim);
        pounceLand = land;
        tg(server, TelegraphShape.circle(land.x, land.z, POUNCE_RADIUS), total, D)
                .onImpact(s -> StormStrike.bolt(s, new Vec3(land.x, floorY() + 14, land.z), new Vec3(land.x, floorY(), land.z)))
                .onHit((s, player) -> {
                    if (hit.add(player.getUUID())) {
                        player.hurt(attackSource(), POUNCE_DAMAGE);
                    }
                })
                .start();
        int n = present(server);
        List<Vec3> spots = new ArrayList<>();
        List<ServerPlayer> others = new ArrayList<>(fighters(server));
        others.remove(target);
        java.util.Collections.shuffle(others, new java.util.Random(random.nextLong()));
        for (ServerPlayer pl : others) {
            spots.add(pl.position());
        }
        while (spots.size() < pounceEchoes(n)) {
            double ang = random.nextDouble() * Math.PI * 2;
            double r = 3 + random.nextDouble() * 6;
            spots.add(clampToNest(land.add(Math.cos(ang) * r, 0, Math.sin(ang) * r)));
        }
        for (int i = 0; i < pounceEchoes(n); i++) {
            Vec3 at = spots.get(i);
            tg(server, TelegraphShape.circle(at.x, at.z, ECHO_RADIUS), total, D)
                    .onImpact(s -> StormStrike.bolt(s, new Vec3(at.x, floorY() + 14, at.z), new Vec3(at.x, floorY(), at.z)))
                    .onHit((s, player) -> {
                        if (hit.add(player.getUUID())) {
                            player.hurt(attackSource(), ECHO_DAMAGE);
                        }
                    })
                    .start();
        }
        if (isAngry()) {
            double gapBase = random.nextDouble() * Math.PI * 2;
            double[] gaps = {gapBase, gapBase + Math.PI};
            double gapHalf = Math.toRadians(26);
            int active = (int) Math.ceil((POUNCE_RING_REACH - POUNCE_RADIUS) / 0.45);
            for (int i = 0; i < (n >= 3 ? 2 : 1); i++) {
                tg(server, TelegraphShape.wave(land.x, land.z, POUNCE_RADIUS, POUNCE_RING_REACH, 0.9, gaps, gapHalf), total + i * 10, D)
                        .active(active).jumpClear(0.9)
                        .onHit((s, player) -> player.hurt(attackSource(), POUNCE_RING_DAMAGE))
                        .start();
            }
        }
    }

    // 跳びかかり (床から・柱から)。溜めの間、印の円が満ちる。着く所は技の始めに決まっている (pounceMarkers)
    private void tickPounce(ServerLevel server, int windup, int flightTicks, double height, boolean fromRod) {
        if (actionTick < windup) {
            if (fromRod) {
                holdOnRod();
            }
            face(aim);
            if (actionTick % 3 == 0) {
                warn(server, ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 4, 0.8, 0.3, 0.8, 0.05);
            }
            return;
        }
        if (actionTick == windup) {
            playAction("pounce_leap");
            sound(server, RaijuSetup.CLAW.get(), 2f, 0.7f);
            rod = -1;
            startFlight(pounceLand != null ? pounceLand : clampToNest(aim), flightTicks, height);
        }
        boolean done = flightStep();
        if (done) {
            land(position());
            playAction("pounce_land");
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.2, getZ(), 30, 1.2, 0.1, 1.2, 0.2);
            if (fromRod) {
                groundTimer = GROUND_TICKS;
            }
            pounceCooldown = (isAngry() ? 40 : 55) + random.nextInt(20);
            finish();
        }
    }

    // 爪の連打の印: 前の扇 (射程 3.6・半角 70 度。向きは始めに決まる) が満ち、そのあと 0.5 秒ごとに 3 回、扇の中の人に 5。
    // 人数で、ほかの人の方へ爪から雷の線 (長さ 9、4) が走る (最大 3 本)
    private void clawMarkers(ServerLevel server) {
        double angle = target != null ? Telegraph.angleOf(target.getX() - getX(), target.getZ() - getZ())
                : Telegraph.angleOf(Vec3.directionFromRotation(0, getYRot()));
        tg(server, TelegraphShape.cone(getX(), getZ(), angle, CLAW_RANGE, Math.toRadians(CLAW_HALF_ANGLE)), CLAW_WARN, D).target(target)
                .active(20).repeatEvery(10)
                .onHit((s, player) -> {
                    player.hurt(attackSource(), CLAW_DAMAGE);
                    player.invulnerableTime = 0;
                })
                .start();
        int shocks = clawShocks(present(server));
        if (shocks == 0) {
            return;
        }
        List<ServerPlayer> others = new ArrayList<>(fighters(server));
        others.remove(target);
        java.util.Collections.shuffle(others, new java.util.Random(random.nextLong()));
        for (int i = 0; i < shocks; i++) {
            double a = i < others.size() ? Telegraph.angleOf(others.get(i).getX() - getX(), others.get(i).getZ() - getZ())
                    : angle + Math.PI * 2 * (i + 1) / (shocks + 1);
            tg(server, TelegraphShape.line(getX(), getZ(), a, CLAW_SHOCK_LENGTH, 1.0), CLAW_WARN, D).target(i < others.size() ? others.get(i) : null)
                    .onHit((s, player) -> player.hurt(attackSource(), CLAW_SHOCK_DAMAGE))
                    .start();
        }
    }

    private void tickClaw(ServerLevel server) {
        if (actionTick == 15 || actionTick == 25 || actionTick == 35) {
            sound(server, RaijuSetup.CLAW.get(), 1.6f, 1f + (actionTick - 15) * 0.01f);
        }
        if (actionTick == 17 || actionTick == 27 || actionTick == 37) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            Vec3 tip = position().add(look.scale(1.8));
            warn(server, ParticleTypes.SWEEP_ATTACK, tip.x, getY() + 1.0, tip.z, 1, 0, 0, 0, 0);
            warn(server, ParticleTypes.ELECTRIC_SPARK, tip.x, getY() + 1.0, tip.z, 10, 0.6, 0.4, 0.6, 0.1);
        }
        if (actionTick >= CLAW_TICKS) {
            clawCooldown = 30;
            finish();
        }
    }

    // 咆哮 (1.5 秒): そのあと人の足もとに招雷の輪 (半径 1.5。1.2 秒で満ちる)。輪が巣の避雷の柱から 3 ブロック以内なら雷は柱へ (傷なし)。
    // 人数で輪が増える (6 → 12 個)
    private void tickRoar(ServerLevel server) {
        if (mode() == MODE_PERCH) {
            holdOnRod();
        }
        if (actionTick % 4 == 0) {
            warn(server, ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 20, 1.5, 1, 1.5, 0.2);
        }
        if (actionTick < ROAR_TICKS) {
            return;
        }
        List<ServerPlayer> fs = fighters(server);
        int rings = roarRings(present(server));
        for (int i = 0; i < rings && !fs.isEmpty(); i++) {
            ServerPlayer p = fs.get(i % fs.size());
            Vec3 at = p.position();
            if (i >= fs.size()) {
                double ang = random.nextDouble() * Math.PI * 2;
                double r = 2.2 + random.nextDouble() * 3.0;
                at = clampToNest(at.add(Math.cos(ang) * r, 0, Math.sin(ang) * r));
            }
            roarStrike(server, at);
        }
        spawnMoths(server, 2);
        finish();
    }

    private void roarStrike(ServerLevel server, Vec3 at) {
        double y = floorY();
        BlockPos rodPos = site != null ? StormRods.find(server, new Vec3(at.x, y, at.z), StormStrike.ROD_REACH) : null;
        if (rodPos != null && StormRods.get(server, rodPos) == null) {
            rodPos = null;
        }
        final BlockPos toRod = rodPos;
        Telegraph.Builder b = tg(server, TelegraphShape.circle(at.x, at.z, StormStrike.RADIUS), RING_WARN, D)
                .onImpact(s -> {
                    if (toRod != null) {
                        Vec3 tip = new Vec3(toRod.getX() + 0.5, toRod.getY() + StormRods.HEIGHT, toRod.getZ() + 0.5);
                        StormStrike.bolt(s, tip.add(0, 18, 0), tip);
                    } else {
                        StormStrike.bolt(s, new Vec3(at.x, y + 18, at.z), new Vec3(at.x, y, at.z));
                    }
                });
        if (toRod != null) {
            // 避雷針へ落ちる稲妻には傷がない。黄色は避雷針の印 (特別な色として残す。danger の橙赤にはしない)
            b.color(0xFFE278);
        }
        if (toRod == null) {
            b.onHit((s, player) -> {
                player.hurt(attackSource(), ROAR_STRIKE_DAMAGE);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false, true));
            });
        }
        b.start();
    }

    // 敵の担当の雷蛾 (static_moth) があれば湧かせる。まだ無ければ何もしない
    private void spawnMoths(ServerLevel server, int count) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "static_moth")).orElse(null);
        if (type == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Entity moth = type.create(server);
            if (moth == null) {
                continue;
            }
            double ang = random.nextDouble() * Math.PI * 2;
            Vec3 at = clampToNest(position().add(Math.cos(ang) * 4, 0, Math.sin(ang) * 4));
            moth.moveTo(at.x, (site != null ? site.floor() : getY()) + 3, at.z, random.nextFloat() * 360, 0);
            moth.addTag(TitanBackTrials.HEART_TAG);
            moth.addTag(TRIAL_TAG);
            // 雷蛾も雷獣のグループのもの (ほかのグループには見えず、狙わない)
            if (moth instanceof jp.revati.skyabyss.titan.group.GroupOwned owned) {
                owned.setGroupId(groupId());
            }
            server.addFreshEntity(moth);
        }
    }

    private void finish() {
        action = Action.NONE;
        actionTick = 0;
        hit.clear();
    }

    // ---------------------------------------------------------------- 位置
    private Vec3 perchSpot(int i) {
        BlockPos top = site.rodTop(i);
        return new Vec3(top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5);
    }

    private void perchOn(int i) {
        if (site == null || i < 0) {
            return;
        }
        rod = i;
        Vec3 p = perchSpot(i);
        setMode(MODE_PERCH);
        moveTo(p.x, p.y, p.z, getYRot(), 0);
        setDeltaMovement(Vec3.ZERO);
    }

    private void holdOnRod() {
        if (site != null && rod >= 0) {
            Vec3 p = perchSpot(rod);
            if (distanceToSqr(p) > 0.01) {
                setPos(p.x, p.y, p.z);
            }
        }
    }

    private void land(Vec3 at) {
        setMode(MODE_GROUND);
        double y = site != null ? site.floor() : at.y;
        moveTo(at.x, y, at.z, getYRot(), 0);
        rod = -1;
    }

    private void startFlight(Vec3 end, int ticks, double height) {
        from = position();
        to = end;
        flight = Math.max(1, ticks);
        flightTick = 0;
        arc = height;
        if (mode() != MODE_RAIL) {
            setMode(MODE_AIR);
        }
    }

    // 軌道を一歩進める。着いたら true
    private boolean flightStep() {
        flightTick++;
        double t = Math.min(1, flightTick / (double) flight);
        Vec3 p = from.lerp(to, t).add(0, arc * 4 * t * (1 - t), 0);
        setPos(p.x, p.y, p.z);
        setDeltaMovement(Vec3.ZERO);
        return flightTick >= flight;
    }

    private Vec3 clampToNest(Vec3 p) {
        if (site == null) {
            return p;
        }
        Vec3 c = site.home();
        Vec3 d = new Vec3(p.x - c.x, 0, p.z - c.z);
        double max = RaijuNest.RADIUS - 2.0;
        if (d.length() > max) {
            d = d.normalize().scale(max);
        }
        return new Vec3(c.x + d.x, site.floor(), c.z + d.z);
    }

    private void face(Vec3 p) {
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

    // 次の柱: いちばん近くの人にいちばん近い柱 (今の柱と、いま接地している柱は除く)
    private int chooseDest() {
        if (site == null) {
            return -1;
        }
        Vec3 ref = target != null ? target.position() : position();
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int pass = 0; pass < 2 && best < 0; pass++) {
            for (int i = 0; i < RaijuNest.RODS; i++) {
                if (i == rod || pass == 0 && StormRods.isGrounded(site.rod(i))) {
                    continue;
                }
                Vec3 b = Vec3.atBottomCenterOf(site.rodBase(i));
                double d = (b.x - ref.x) * (b.x - ref.x) + (b.z - ref.z) * (b.z - ref.z);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
        }
        return best;
    }

    private int nearestRod(Vec3 ref, boolean skipGrounded) {
        if (site == null) {
            return -1;
        }
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < RaijuNest.RODS; i++) {
            if (skipGrounded && StormRods.isGrounded(site.rod(i))) {
                continue;
            }
            Vec3 b = Vec3.atBottomCenterOf(site.rodBase(i));
            double d = b.distanceToSqr(ref.x, b.y, ref.z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private int railInterval() {
        return isAngry() ? 80 + random.nextInt(41) : 120 + random.nextInt(61);
    }

    private void ring(ServerLevel server, Vec3 at, float r, Vector3f color) {
        DustParticleOptions dust = new DustParticleOptions(color, 1.3f);
        double y = site != null ? site.floor() + 0.1 : at.y + 0.1;
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18;
            warn(server, dust, at.x + Math.cos(a) * r, y, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private void sound(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        server.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    private void broadcast(ServerLevel server, net.minecraft.network.chat.Component message) {
        for (ServerPlayer p : fighters(server)) {
            Notice.bar(p, message);
        }
    }

    // ---------------------------------------------------------------- 傷
    public static float takenFor(float amount, float multiplier, boolean stunned) {
        return amount / multiplier * (stunned ? STUN_TAKEN : 1f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return super.hurt(source, amount);
        }
        if (mode() == MODE_RAIL || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypes.IN_WALL)) {
            return false;
        }
        boolean ok = super.hurt(source, takenFor(amount, multiplier, isStunned()));
        if (ok && isAlive()) {
            triggerAnim("hurt", "hurt");
        }
        return ok;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bossBar.removeAllPlayers();
        setInvisible(false);
        if (level() instanceof ServerLevel server) {
            setMode(MODE_GROUND);
            playAction("death");
            RaijuBattle.onDefeated(server, this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
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

    // ---------------------------------------------------------------- アニメーション
    // move (待機・走り。床にいて技を使っていないとき) / state (柱の上) / hurt / action (技。サーバーから流す)
    private static RawAnimation anim(String name, boolean loop) {
        String full = "animation.raiju." + name;
        return loop ? RawAnimation.begin().thenLoop(full) : RawAnimation.begin().thenPlay(full);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = anim("idle", true);
        RawAnimation walk = anim("walk", true);
        RawAnimation perch = anim("perch", true);
        controllers.add(new AnimationController<>(this, "move", 4, s -> {
            if (mode() != MODE_GROUND || isDeadOrDying()) {
                return PlayState.STOP;
            }
            return s.setAndContinue(s.isMoving() ? walk : idle);
        }));
        controllers.add(new AnimationController<>(this, "state", 4, s -> {
            if (mode() != MODE_PERCH || isDeadOrDying()) {
                return PlayState.STOP;
            }
            return s.setAndContinue(perch);
        }));
        controllers.add(new AnimationController<>(this, "hurt", 0, s -> PlayState.STOP).triggerableAnim("hurt", anim("hurt", false)));
        AnimationController<Raiju> act = new AnimationController<>(this, "action", 0, s -> PlayState.STOP);
        for (String name : new String[] {"rail_charge", "land", "pounce_crouch", "pounce_leap", "pounce_land", "claw", "roar",
                "stand_up", "leap_up", "death"}) {
            act.triggerableAnim(name, anim(name, false));
        }
        act.triggerableAnim("stun_fall", RawAnimation.begin().thenPlay("animation.raiju.stun_fall").thenLoop("animation.raiju.stunned"));
        controllers.add(act);
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
        return RaijuSetup.AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RaijuSetup.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RaijuSetup.DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.2f;
    }
}
