package jp.revati.skyabyss.titan.storm.raiju;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
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
    public static final int CLAW_TICKS = 32;
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
        if (!roared && getHealth() < getMaxHealth() * 0.5f && action == Action.NONE && (m == MODE_GROUND || m == MODE_PERCH)) {
            begin(server, Action.ROAR);
        }
        if (action != Action.NONE) {
            getNavigation().stop();
            tickAction(server);
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

    // 戦っている人: 巣の中の人 (部屋に結びついていなければ 24 ブロック以内)
    public List<ServerPlayer> fighters(ServerLevel server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
                continue;
            }
            if (site != null ? site.inside(p.position()) : p.distanceToSqr(this) < 24 * 24) {
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
        begin(server, Action.RAIL);
        dest = destRod;
    }

    private void begin(ServerLevel server, Action a) {
        action = a;
        actionTick = 0;
        hit.clear();
        getNavigation().stop();
        switch (a) {
            case RAIL -> {
                dest = chooseDest();
                if (dest < 0) {
                    action = Action.NONE;
                    perchTimer = 40;
                    return;
                }
                face(Vec3.atCenterOf(site.rodTop(dest)));
                playAction("rail_charge");
                sound(server, RaijuSetup.RAIL_CHARGE.get(), 2.5f, 1f);
            }
            case DESCEND, POUNCE -> {
                aim = target != null ? target.position() : position().add(getLookAngle().scale(5));
                face(aim);
                playAction("pounce_crouch");
                sound(server, RaijuSetup.POUNCE.get(), 2f, a == Action.DESCEND ? 0.85f : 1f);
            }
            case CLAW -> {
                playAction("claw");
                if (target != null) {
                    face(target.position());
                }
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
            railHit(server, a, b);
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

    // 予告線: 柱の先から柱の先へ (高い所) と、その下の床の帯 (当たる所)
    private void railLine(ServerLevel server, Vec3 a, Vec3 b, float size) {
        DustParticleOptions blue = new DustParticleOptions(new Vector3f(0.65f, 0.85f, 1.0f), size);
        DustParticleOptions white = new DustParticleOptions(new Vector3f(1.0f, 1.0f, 0.92f), size);
        double len = a.distanceTo(b);
        int n = Math.max(6, (int) (len * 2.5));
        double floor = site != null ? site.floor() + 0.1 : getY();
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n);
            warn(server, blue, p.x, p.y + 0.8, p.z, 1, 0.05, 0.05, 0.05, 0);
            double t = i / (double) n;
            if (t > trim(len) && t < 1 - trim(len)) {
                Vec3 side = new Vec3(-(b.z - a.z), 0, b.x - a.x).normalize().scale(RAIL_HALF_WIDTH);
                warn(server, white, p.x + side.x, floor, p.z + side.z, 1, 0.02, 0, 0.02, 0);
                warn(server, white, p.x - side.x, floor, p.z - side.z, 1, 0.02, 0, 0.02, 0);
                if (i % 2 == 0) {
                    warn(server, blue, p.x, floor, p.z, 1, 0.1, 0, 0.1, 0);
                }
            }
        }
    }

    private static double trim(double len) {
        return Math.min(0.3, 1.8 / Math.max(len, 1));
    }

    // 線の下 (床の上の帯、両端の柱のそば 1.8 ブロックは除く) の人に 12
    private void railHit(ServerLevel server, Vec3 a, Vec3 b) {
        double len = a.distanceTo(b);
        for (ServerPlayer p : fighters(server)) {
            double t = segmentT(p.position(), a, b);
            if (t <= trim(len) || t >= 1 - trim(len)) {
                continue;
            }
            Vec3 q = a.lerp(b, t);
            double dx = p.getX() - q.x;
            double dz = p.getZ() - q.z;
            if (dx * dx + dz * dz <= RAIL_HALF_WIDTH * RAIL_HALF_WIDTH) {
                p.hurt(attackSource(), RAIL_DAMAGE);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
            }
        }
    }

    private static double segmentT(Vec3 p, Vec3 a, Vec3 b) {
        double vx = b.x - a.x;
        double vz = b.z - a.z;
        double l2 = vx * vx + vz * vz;
        if (l2 < 1e-6) {
            return 0;
        }
        return Mth.clamp(((p.x - a.x) * vx + (p.z - a.z) * vz) / l2, 0, 1);
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

    // 跳びかかり (床から・柱から)。溜めの間は着く所に黄色い輪、跳んでいる間と着いたときに当たる
    private void tickPounce(ServerLevel server, int windup, int flightTicks, double height, boolean fromRod) {
        if (actionTick < windup) {
            if (fromRod) {
                holdOnRod();
            }
            if (target != null && actionTick < windup - 6) {
                aim = target.position();
            }
            face(aim);
            if (actionTick % 3 == 0) {
                ring(server, aim, 2.0f, new Vector3f(1.0f, 0.86f, 0.35f));
                warn(server, ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 4, 0.8, 0.3, 0.8, 0.05);
            }
            return;
        }
        if (actionTick == windup) {
            playAction("pounce_leap");
            sound(server, RaijuSetup.CLAW.get(), 2f, 0.7f);
            Vec3 land = clampToNest(aim);
            rod = -1;
            startFlight(land, flightTicks, height);
        }
        boolean done = flightStep();
        for (ServerPlayer p : fighters(server)) {
            if (!hit.contains(p.getUUID()) && p.getBoundingBox().intersects(getBoundingBox().inflate(0.4))) {
                hit.add(p.getUUID());
                p.hurt(attackSource(), POUNCE_DAMAGE);
            }
        }
        if (done) {
            land(position());
            playAction("pounce_land");
            for (ServerPlayer p : fighters(server)) {
                if (!hit.contains(p.getUUID()) && p.distanceToSqr(this) < 2.2 * 2.2 && Math.abs(p.getY() - getY()) < 2) {
                    hit.add(p.getUUID());
                    p.hurt(attackSource(), POUNCE_DAMAGE);
                }
            }
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.2, getZ(), 30, 1.2, 0.1, 1.2, 0.2);
            if (fromRod) {
                groundTimer = GROUND_TICKS;
            }
            pounceCooldown = (isAngry() ? 40 : 55) + random.nextInt(20);
            finish();
        }
    }

    private void tickClaw(ServerLevel server) {
        if (target != null) {
            face(target.position());
        }
        if (actionTick == 6 || actionTick == 16 || actionTick == 26) {
            sound(server, RaijuSetup.CLAW.get(), 1.6f, 1f + (actionTick - 6) * 0.01f);
        }
        if (actionTick == 10 || actionTick == 20 || actionTick == 30) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            Vec3 tip = position().add(look.scale(1.8));
            warn(server, ParticleTypes.SWEEP_ATTACK, tip.x, getY() + 1.0, tip.z, 1, 0, 0, 0, 0);
            warn(server, ParticleTypes.ELECTRIC_SPARK, tip.x, getY() + 1.0, tip.z, 10, 0.6, 0.4, 0.6, 0.1);
            for (ServerPlayer p : fighters(server)) {
                Vec3 rel = p.position().subtract(position());
                double d = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
                double cos = d < 1e-3 ? 1 : (rel.x * look.x + rel.z * look.z) / d;
                if (d < 3.6 && cos > 0.34 && Math.abs(rel.y) < 2.5) {
                    p.hurt(attackSource(), CLAW_DAMAGE);
                    p.invulnerableTime = 0;
                }
            }
        }
        if (actionTick >= CLAW_TICKS) {
            clawCooldown = 30;
            finish();
        }
    }

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
        for (int i = 0; i < ROAR_RINGS && !fs.isEmpty(); i++) {
            ServerPlayer p = fs.get(i % fs.size());
            Vec3 at = p.position();
            if (i >= fs.size()) {
                double ang = random.nextDouble() * Math.PI * 2;
                double r = 2.2 + random.nextDouble() * 3.0;
                at = clampToNest(at.add(Math.cos(ang) * r, 0, Math.sin(ang) * r));
            }
            double y = site != null ? site.floor() : at.y;
            StormStrike.call(server, new Vec3(at.x, y, at.z), RING_WARN, ROAR_STRIKE_DAMAGE, this);
        }
        spawnMoths(server, 2);
        finish();
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
            p.displayClientMessage(message, true);
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
        for (ServerPlayer p : server.players()) {
            if (isAlive() && p.distanceToSqr(this) < 48 * 48) {
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
