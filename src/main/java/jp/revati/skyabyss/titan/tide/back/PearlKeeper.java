package jp.revati.skyabyss.titan.tide.back;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.combat.HeartBalance;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.tide.heart.TideHeartNetwork;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 中ボス「真珠守り」(docs/design/24 の 5 章、08 の 5.2節の型)。息の間の頂 (塔の中の舞台) で、大きな真珠を抱いて渦巻きの殻に籠もる、大きな宿借り。
// 舞台は半径 6 の円 (外は塔の壁。縁から落ちない)。高さは 33 ブロックあり、技が人を空へ舞い上げる (空中戦)。
// 技 (傷のある技はすべて Telegraph。予告は 0.8 秒以上、印と当たりは同じ形。接触の傷はない):
//   吹き上げ  足もとの半径 2 の円が 1.5 秒で吹き上がる。傷 6 と、高く舞い上がる (数秒ゆっくり落ちる)。人数 + 1 本 (最大 7)
//   吹き下ろし  足もとから外へ走る輪 (跳べば、舞い上がっていれば当たらない。安全な筋が 2〜3)。傷 7
//   殻の突進  狙った人へ一直線の帯 (幅 2.6、長さ 9)。傷 12。突っ込んだあと殻が刺さって 2 秒ぐったりし、そのあいだの傷は 1.5 倍。人数で 1〜3 本
//   真珠の弾  足もとの半径 1.5 の円が 1.4 秒で弾ける。傷 8。人数 + 2 個 (最大 8)
//   殻籠もり  体力 66%・33% で殻に閉じこもる (5 秒は傷を受けない)。出るときに輪が走り、そのあと 4 秒ぐったりする (傷 1.5 倍)
// 体力は 500、傷は挑む人数の倍率 (1 + 0.85 × (人数 - 1)。心臓と同じ HeartBalance.partyScale) と硬さ 1.5 で割る。
public class PearlKeeper extends TideMob {
    public static final float HEALTH = 500;
    public static final float ARMOR = 8;
    public static final float TOUGHNESS = 6;
    public static final float BASE_RESIST = 1.5f;
    public static final float UPDRAFT_DAMAGE = 6;
    public static final float DOWNDRAFT_DAMAGE = 7;
    public static final float CHARGE_DAMAGE = 12;
    public static final float VOLLEY_DAMAGE = 8;
    public static final float SHELL_RING_DAMAGE = 7;
    public static final int UPDRAFT_WINDUP = 30;
    public static final int DOWNDRAFT_WINDUP = 36;
    public static final int CHARGE_WINDUP = 24;
    public static final int VOLLEY_WINDUP = 28;
    public static final int SHELL_WINDUP = 20;
    public static final int SHELL_HOLD = 100;
    public static final int TIRED_TICKS = 40;
    public static final int EXPOSED_TICKS = 80;
    public static final double UPDRAFT_RADIUS = 1.8;
    public static final double VOLLEY_RADIUS = 1.5;
    public static final double CHARGE_LENGTH = 9.0;
    public static final double CHARGE_HALF_WIDTH = 1.3;
    public static final double RING_R0 = 1.8;
    public static final double RING_R1 = 9.0;
    private static final double LANE_HALF = Math.toRadians(26);
    public static final float TIRED_MULT = 1.5f;

    // 人数 (n) で増える数。自己テストが同じ式を見る
    public static int updrafts(int n) {
        return Math.min(Math.max(n, 1) + 1, 6);
    }

    public static int volleys(int n) {
        return Math.min(Math.max(n, 1) + 2, 8);
    }

    public static int chargeLines(int n) {
        return n >= 5 ? 3 : n >= 3 ? 2 : 1;
    }

    // 輪が重なる数 (三人から二重。跳び越える輪が続く)
    public static int rings(int n) {
        return n >= 3 ? 2 : 1;
    }

    public static int lanes(int n) {
        return n >= 4 ? 3 : 2;
    }

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(PearlKeeper.class, EntityDataSerializers.INT);
    public static final int MODE_NORMAL = 0;
    // 殻に籠もる (傷を受けない)・突進のあとぐったり (傷 1.5 倍)
    public static final int MODE_SHELLED = 1;
    public static final int MODE_TIRED = 2;

    enum Action {
        NONE, UPDRAFT, DOWNDRAFT, CHARGE, VOLLEY, SHELL
    }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private int actionTick;
    private int updraftCooldown = 70;
    private int downdraftCooldown = 130;
    private int chargeCooldown = 170;
    private int volleyCooldown = 100;
    private double[] shellLane = new double[0];
    private int tiredLeft;
    private int exposedLeft;
    private int shells;
    private float multiplier = 1f;
    private Vec3 chargeTo = Vec3.ZERO;
    private Vec3 chargeFrom = Vec3.ZERO;
    @Nullable
    private ServerPlayer target;
    @Nullable
    private List<ServerPlayer> devFighters;
    private int partyOverride;
    private boolean holdAttacks;
    // 試練 (T12 真珠守りの試し) で使うとき: 舞台の円 (中心と半径。NaN = 息の間の頂)・使える技 (null = 全部)・倒れたときの知らせ
    private double arenaCx = Double.NaN;
    private double arenaCz;
    private double arenaR;
    @Nullable
    private java.util.Set<Action> allowed;
    @Nullable
    private java.util.function.Consumer<PearlKeeper> defeatHook;

    public PearlKeeper(EntityType<? extends Monster> type, Level level) {
        super(type, level, "pearl_keeper", "updraft", "downdraft", "charge", "volley", "shell_in", "shell_out", "emerge");
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ATTACK_DAMAGE, CHARGE_DAMAGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, MODE_NORMAL);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    private void setMode(int m) {
        entityData.set(MODE, m);
    }

    @Override
    protected String state() {
        return mode() == MODE_SHELLED ? "shelled" : null;
    }

    @Override
    protected boolean frozen() {
        return mode() == MODE_SHELLED || action != Action.NONE;
    }

    @Override
    protected void registerGoals() {
        // 動きは aiStep の中で決める (技の間は止まる)
    }

    public void setChallengers(int n) {
        multiplier = (float) HeartBalance.partyScale(n);
    }

    public float multiplier() {
        return multiplier;
    }

    // 試験・開発用
    public void holdAttacks() {
        holdAttacks = true;
    }

    public void setDevFighters(@Nullable List<ServerPlayer> players) {
        devFighters = players;
    }

    public void setPartyOverride(int count) {
        partyOverride = count;
    }

    // 試練の舞台: 円の中心と半径 (突進の行き先・吹き上げの散らばりをこの円の中に収める)。名前は技の名前 (UPDRAFT・DOWNDRAFT・CHARGE・VOLLEY)。null = 全部使える
    public void setTrialArena(double cx, double cz, double radius) {
        // 区画の写しのセルの決まりの中 (TrialFrame) では、写しの座標の円にする (使うのは自分の tick)
        net.minecraft.world.phys.Vec3 c = jp.revati.skyabyss.trial.TrialFrame.current().world(new net.minecraft.world.phys.Vec3(cx, 0, cz));
        arenaCx = c.x;
        arenaCz = c.z;
        arenaR = radius;
    }

    public void setAllowedActions(@Nullable java.util.Collection<String> names) {
        if (names == null) {
            allowed = null;
            return;
        }
        java.util.Set<Action> set = java.util.EnumSet.noneOf(Action.class);
        for (String n : names) {
            set.add(Action.valueOf(n.toUpperCase(java.util.Locale.ROOT)));
        }
        allowed = set;
    }

    public void setDefeatHook(@Nullable java.util.function.Consumer<PearlKeeper> hook) {
        defeatHook = hook;
    }

    private boolean may(Action a) {
        return allowed == null || allowed.contains(a);
    }

    private double arenaX() {
        return Double.isNaN(arenaCx) ? getX() : arenaCx;
    }

    private double arenaZ() {
        return Double.isNaN(arenaCx) ? getZ() : arenaCz;
    }

    public boolean idle() {
        return action == Action.NONE;
    }

    public int actionTickCount() {
        return actionTick;
    }

    public void forceAction(String name) {
        if (level() instanceof ServerLevel server) {
            target = pickTarget(server);
            start(server, Action.valueOf(name.toUpperCase(java.util.Locale.ROOT)));
        }
    }

    // 現れる (舞台の床から)
    public void emerge(ServerLevel level, Vec3 at) {
        moveTo(at.x, at.y, at.z, getYRot(), 0);
        playAction("emerge");
        sound(TideBackSetup.BECKONER_EMERGE.get(), 3f, 0.8f);
        Vec3 fx = jp.revati.skyabyss.trial.TrialFrame.current().world(at);
        level.sendParticles(ParticleTypes.BUBBLE_POP, fx.x, fx.y + 0.6, fx.z, 60, 1.5, 0.8, 1.5, 0.1);
        invulnerableTicks = 24;
    }

    private int invulnerableTicks;

    // ---------------------------------------------------------------- 狙える人
    private List<ServerPlayer> fighters(ServerLevel server) {
        return devFighters != null ? devFighters : List.of();
    }

    private boolean isFighter(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && level() instanceof ServerLevel server && fighters(server).contains(p);
    }

    private int present(ServerLevel server) {
        return Math.max(partyOverride, Math.max(1, fighters(server).size()));
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
        if (invulnerableTicks > 0) {
            invulnerableTicks--;
        }
        if (isDeadOrDying()) {
            return;
        }
        if (tiredLeft > 0 && --tiredLeft == 0 && mode() == MODE_TIRED) {
            setMode(MODE_NORMAL);
            playAction("shell_out");
        }
        if (exposedLeft > 0 && --exposedLeft == 0 && mode() == MODE_TIRED) {
            setMode(MODE_NORMAL);
        }
        updraftCooldown--;
        downdraftCooldown--;
        chargeCooldown--;
        volleyCooldown--;
        target = pickTarget(server);
        // 殻籠もり: 体力 66%・33%
        if (!holdAttacks && action == Action.NONE && shells < 2 && getHealth() < getMaxHealth() * (shells == 0 ? 0.66f : 0.33f) && mode() == MODE_NORMAL) {
            shells++;
            start(server, Action.SHELL);
        }
        if (action != Action.NONE) {
            getNavigation().stop();
            tickAction(server);
            return;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 20f, 20f);
        if (holdAttacks || mode() != MODE_NORMAL) {
            return;
        }
        double dist = Math.sqrt(distanceToSqr(target));
        if (chargeCooldown <= 0 && dist > 3.0 && may(Action.CHARGE)) {
            start(server, Action.CHARGE);
        } else if (updraftCooldown <= 0 && may(Action.UPDRAFT)) {
            start(server, Action.UPDRAFT);
        } else if (downdraftCooldown <= 0 && may(Action.DOWNDRAFT)) {
            start(server, Action.DOWNDRAFT);
        } else if (volleyCooldown <= 0 && may(Action.VOLLEY)) {
            start(server, Action.VOLLEY);
        } else if (dist > 4.0) {
            getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    // ---------------------------------------------------------------- 予告の組み立て
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

    private Telegraph.Builder tg(ServerLevel server, TelegraphShape shape, double floor, int warn, int style) {
        return preset(Telegraph.in(server, shape).floor(floor).warn(warn).owner(this).victims(this::isFighter).reach(3.5), style);
    }

    private static double[] gaps(double base, int count) {
        double[] g = new double[count];
        for (int i = 0; i < count; i++) {
            g[i] = base + Math.PI * 2 * i / count;
        }
        return g;
    }

    // 床を走る輪 (跳べば当たらない。安全な筋は緑の扇)
    private void ripple(ServerLevel server, double cx, double cz, double floor, double r0, double r1, int warn, double[] gaps, float damage) {
        int active = (int) Math.ceil((r1 - r0) / 0.55);
        tg(server, TelegraphShape.wave(cx, cz, r0, r1, 0.9, gaps, LANE_HALF), floor, warn, D).active(active).jumpClear(0.9)
                .onHit((s, p) -> {
                    p.hurt(attackSource(), damage);
                    Vec3 away = p.position().subtract(cx, floor, cz).multiply(1, 0, 1);
                    if (away.lengthSqr() > 1e-4) {
                        p.knockback(0.5, -away.x, -away.z);
                        p.hurtMarked = true;
                    }
                })
                .start();
        for (double gap : gaps) {
            tg(server, TelegraphShape.cone(cx, cz, gap, r1, LANE_HALF), floor, warn, S).start();
        }
    }

    private void start(ServerLevel server, Action a) {
        action = a;
        actionTick = 0;
        if (a.ordinal() != 0) {
            jp.revati.skyabyss.combat.BossCue.send(this, "pearl_keeper", a.name().toLowerCase(java.util.Locale.ROOT), 30);
        }
        double ground = groundY(position());
        switch (a) {
            case UPDRAFT -> {
                playAction("updraft");
                sound(TideBackSetup.BECKONER_BREATH.get(), 2.5f, 0.7f);
                telegraphUpdraft(server, ground);
            }
            case DOWNDRAFT -> {
                playAction("downdraft");
                sound(TideBackSetup.BECKONER_RAISE.get(), 2.5f, 0.8f);
                double[] lane = gaps(random.nextDouble() * Math.PI * 2, lanes(present(server)));
                for (int i = 0; i < rings(present(server)); i++) {
                    ripple(server, getX(), getZ(), ground, RING_R0, RING_R1, DOWNDRAFT_WINDUP + i * 14, lane, DOWNDRAFT_DAMAGE);
                }
            }
            case CHARGE -> {
                playAction("charge");
                sound(TideBackSetup.BECKONER_RAISE.get(), 2.5f, 1.1f);
                telegraphCharge(server, ground);
            }
            case VOLLEY -> {
                playAction("volley");
                sound(TideBackSetup.BECKONER_BECKON.get(), 2.5f, 1.2f);
                telegraphVolley(server, ground);
            }
            case SHELL -> {
                playAction("shell_in");
                sound(TideBackSetup.BECKONER_ROAR.get(), 3f, 0.9f);
                setMode(MODE_SHELLED);
                invulnerableTicks = SHELL_WINDUP + SHELL_HOLD;
                shellLane = gaps(random.nextDouble() * Math.PI * 2, lanes(present(server)));
            }
            default -> {
            }
        }
    }

    private void tickAction(ServerLevel server) {
        actionTick++;
        switch (action) {
            case UPDRAFT -> {
                if (actionTick == UPDRAFT_WINDUP) {
                    updraftCooldown = 130 + random.nextInt(40);
                }
                if (actionTick >= UPDRAFT_WINDUP + 14) {
                    finish();
                }
            }
            case DOWNDRAFT -> {
                if (actionTick == DOWNDRAFT_WINDUP) {
                    sound(TideBackSetup.BECKONER_SLAM.get(), 3f, 0.8f);
                    downdraftCooldown = 170 + random.nextInt(40);
                }
                if (actionTick >= DOWNDRAFT_WINDUP + 26 + 14 * (rings(present(server)) - 1)) {
                    finish();
                }
            }
            case CHARGE -> tickCharge(server);
            case VOLLEY -> {
                if (actionTick == VOLLEY_WINDUP) {
                    volleyCooldown = 120 + random.nextInt(40);
                }
                if (actionTick >= VOLLEY_WINDUP + 12) {
                    finish();
                }
            }
            case SHELL -> tickShell(server);
            default -> finish();
        }
    }

    // 吹き上げ: 人数 + 1 本。一本目は狙う人の足もと、残りは別の人の足もと (足りなければ舞台のあちこち)
    private void telegraphUpdraft(ServerLevel server, double ground) {
        int count = updrafts(present(server));
        List<ServerPlayer> all = new ArrayList<>(fighters(server));
        java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
        if (target != null && all.remove(target)) {
            all.add(0, target);
        }
        for (int i = 0; i < count; i++) {
            double cx;
            double cz;
            ServerPlayer aim = null;
            if (i < all.size()) {
                aim = all.get(i);
                cx = aim.getX();
                cz = aim.getZ();
            } else {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 1.5 + random.nextDouble() * 3.5;
                cx = arenaX() + Math.cos(a) * r;
                cz = arenaZ() + Math.sin(a) * r;
                if (Double.isNaN(arenaCx) && (devFighters != null || partyOverride > 0)) {
                    cx = getX() + Math.cos(a) * (3 + r);
                    cz = getZ() + Math.sin(a) * (3 + r);
                }
            }
            double fx = cx;
            double fz = cz;
            tg(server, TelegraphShape.circle(cx, cz, UPDRAFT_RADIUS), ground, UPDRAFT_WINDUP, D)
                    .onImpact(s -> {
                        s.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, fx, ground + 0.3, fz, 50, 0.8, 5.0, 0.8, 0.1);
                        s.sendParticles(ParticleTypes.END_ROD, fx, ground + 1.0, fz, 12, 0.7, 3.0, 0.7, 0.05);
                        s.playSound(null, fx, ground, fz, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 2f, 0.8f);
                    })
                    .onHit((s, p) -> {
                        p.hurt(attackSource(), UPDRAFT_DAMAGE);
                        TideHeartNetwork.nudge(p, 0, 1.0, 0);
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 70, 0, false, false, true));
                    })
                    .start();
        }
    }

    // 殻の突進: 狙った人へ一直線 (人数で 1〜3 本、別の人へ)。突っ込んだあと殻が刺さってぐったりする
    private void telegraphCharge(ServerLevel server, double ground) {
        int n = present(server);
        double[] angles = new double[chargeLines(n)];
        Vec3 to = target != null ? target.position().subtract(position()) : getLookAngle();
        angles[0] = Telegraph.angleOf(to.x, to.z);
        List<ServerPlayer> others = new ArrayList<>(fighters(server));
        others.remove(target);
        java.util.Collections.shuffle(others, new java.util.Random(random.nextLong()));
        for (int i = 1; i < angles.length; i++) {
            if (i - 1 < others.size()) {
                ServerPlayer o = others.get(i - 1);
                angles[i] = Telegraph.angleOf(o.getX() - getX(), o.getZ() - getZ());
            } else {
                angles[i] = angles[0] + (i % 2 == 1 ? 1 : -1) * Math.toRadians(40) * ((i + 1) / 2);
            }
        }
        setYRot((float) (Mth.atan2(Math.sin(angles[0]), Math.cos(angles[0])) * Mth.RAD_TO_DEG) - 90f);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        chargeFrom = position();
        double len = CHARGE_LENGTH;
        // 突っ込む先: 主の帯の終わり (舞台の外へは出ない)
        Vec3 end = position().add(Math.cos(angles[0]) * len, 0, Math.sin(angles[0]) * len);
        chargeTo = clampToArena(end);
        for (int i = 0; i < angles.length; i++) {
            double angle = angles[i];
            boolean first = i == 0;
            tg(server, TelegraphShape.line(getX(), getZ(), angle, len, CHARGE_HALF_WIDTH), ground, CHARGE_WINDUP, D).target(first ? target : null)
                    .onImpact(s -> {
                        for (double f = 1; f <= len; f += 0.9) {
                            s.sendParticles(ParticleTypes.CLOUD, chargeFrom.x + Math.cos(angle) * f, ground + 0.3, chargeFrom.z + Math.sin(angle) * f, 2, 0.4, 0.1, 0.4, 0.05);
                        }
                        if (first) {
                            sound(TideBackSetup.BECKONER_SLAM.get(), 3f, 1.0f);
                        }
                    })
                    .onHit((s, p) -> p.hurt(attackSource(), CHARGE_DAMAGE))
                    .start();
        }
    }

    // 舞台の円の中へ (中心から 5 まで)
    private Vec3 clampToArena(Vec3 p) {
        double dx = p.x - arenaX();
        double dz = p.z - arenaZ();
        double d = Math.hypot(dx, dz);
        double max = (Double.isNaN(arenaCx) ? 6.0 : arenaR) - 1.0;
        if (d <= max || d < 1e-6) {
            return p;
        }
        return new Vec3(arenaX() + dx / d * max, p.y, arenaZ() + dz / d * max);
    }

    private void tickCharge(ServerLevel server) {
        if (actionTick == CHARGE_WINDUP) {
            // 突っ込む (数 tick で終わりの点まで)。位置は舞台の中だけ
            setMode(MODE_TIRED);
            tiredLeft = TIRED_TICKS;
            exposedLeft = TIRED_TICKS;
        }
        if (actionTick > CHARGE_WINDUP && actionTick <= CHARGE_WINDUP + 6) {
            double t = (actionTick - CHARGE_WINDUP) / 6.0;
            Vec3 p = chargeFrom.lerp(chargeTo, t);
            moveTo(p.x, p.y, p.z, getYRot(), 0);
        }
        if (actionTick >= CHARGE_WINDUP + 8) {
            chargeCooldown = 190 + random.nextInt(50);
            finish();
        }
    }

    // 真珠の弾: 足もとの円。人数 + 2 個 (最大 8)。人のいない所のぶんは、人のまわりへ
    private void telegraphVolley(ServerLevel server, double ground) {
        int count = volleys(present(server));
        List<ServerPlayer> all = new ArrayList<>(fighters(server));
        java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
        if (target != null && all.remove(target)) {
            all.add(0, target);
        }
        for (int i = 0; i < count; i++) {
            double cx;
            double cz;
            ServerPlayer aim = null;
            if (!all.isEmpty() && i < all.size()) {
                aim = all.get(i);
                cx = aim.getX();
                cz = aim.getZ();
            } else {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 1.5 + random.nextDouble() * 3.8;
                if (!all.isEmpty()) {
                    ServerPlayer base = all.get(i % all.size());
                    cx = base.getX() + Math.cos(a) * 2.5;
                    cz = base.getZ() + Math.sin(a) * 2.5;
                } else {
                    cx = getX() + Math.cos(a) * (3 + r);
                    cz = getZ() + Math.sin(a) * (3 + r);
                }
            }
            double fx = cx;
            double fz = cz;
            tg(server, TelegraphShape.circle(cx, cz, VOLLEY_RADIUS), ground, VOLLEY_WINDUP, D)
                    .onImpact(s -> {
                        s.sendParticles(ParticleTypes.BUBBLE_POP, fx, ground + 0.5, fz, 22, 0.7, 0.5, 0.7, 0.08);
                        s.sendParticles(ParticleTypes.END_ROD, fx, ground + 0.8, fz, 6, 0.5, 0.5, 0.5, 0.03);
                        s.playSound(null, fx, ground, fz, TideBackSetup.PEARL_RETURN.get(), SoundSource.HOSTILE, 1.4f, 1.3f);
                    })
                    .onHit((s, p) -> {
                        p.hurt(attackSource(), VOLLEY_DAMAGE);
                        TideHeartNetwork.nudge(p, 0, 0.35, 0);
                    })
                    .start();
        }
    }

    // 殻籠もり: 殻に閉じこもり (5 秒は傷を受けない)、途中 2 回輪が走り、出てから 4 秒ぐったりする
    private void tickShell(ServerLevel server) {
        double ground = groundY(position());
        if (actionTick == SHELL_WINDUP + 40 || actionTick == SHELL_WINDUP + 70) {
            double[] lane = shellLane;
            for (int i = 0; i < rings(present(server)); i++) {
                ripple(server, getX(), getZ(), ground, RING_R0, RING_R1, 28 + i * 14, lane, SHELL_RING_DAMAGE);
            }
        }
        if (actionTick % 6 == 0 && actionTick < SHELL_WINDUP + SHELL_HOLD) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.4, getZ(), 3, 0.4, 0.3, 0.4, 0.02);
        }
        if (actionTick == SHELL_WINDUP + SHELL_HOLD) {
            setMode(MODE_TIRED);
            tiredLeft = EXPOSED_TICKS;
            exposedLeft = EXPOSED_TICKS;
            playAction("shell_out");
            sound(TideBackSetup.BECKONER_EMERGE.get(), 3f, 1.2f);
        }
        if (actionTick >= SHELL_WINDUP + SHELL_HOLD + 12) {
            finish();
        }
    }

    private void finish() {
        action = Action.NONE;
        actionTick = 0;
    }

    private double groundY(Vec3 p) {
        BlockPos b = BlockPos.containing(p.x, getY() + 2, p.z);
        for (int i = 0; i < 6; i++) {
            if (!level().getBlockState(b).getCollisionShape(level(), b).isEmpty()) {
                return b.getY() + 1;
            }
            b = b.below();
        }
        return getY();
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        playSound(sound, volume, pitch);
    }

    // ---------------------------------------------------------------- 傷
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) {
            return super.hurt(source, amount);
        }
        if (invulnerableTicks > 0 || mode() == MODE_SHELLED || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            if (mode() == MODE_SHELLED && level() instanceof ServerLevel server) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.9f);
                server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 2, getZ(), 6, 0.6, 0.6, 0.6, 0.1);
            }
            return false;
        }
        float scaled = takenFor(amount, multiplier, mode() == MODE_TIRED);
        boolean hit = super.hurt(source, scaled);
        if (hit) {
            playSound(TideBackSetup.BECKONER_HURT.get(), 1.5f, 1.1f + random.nextFloat() * 0.2f);
        }
        return hit;
    }

    // 受ける傷 (自己テストからも使う): 人数の倍率と硬さで割り、ぐったりのあいだは 1.5 倍
    public static float takenFor(float amount, float multiplier, boolean tired) {
        float a = amount / (multiplier * BASE_RESIST);
        return tired ? a * TIRED_MULT : a;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bossBar.removeAllPlayers();
        playSound(TideBackSetup.BECKONER_DEATH.get(), 4f, 1.2f);
        setMode(MODE_NORMAL);
        if (defeatHook != null) {
            defeatHook.accept(this);
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
            if (p.distanceToSqr(this) < 48 * 48 && isAlive() && GroupOwned.visibleTo(this, p)) {
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
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TideBackSetup.BECKONER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TideBackSetup.BECKONER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TideBackSetup.BECKONER_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.2f;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    // 手ごたえの数 (HeartBalance / 自己テスト用)。心臓と同じ式に通す: 段階 = 殻に入るまで / 66% / 33% / 最後
    public static HeartBalance.Profile balanceProfile() {
        return new HeartBalance.Profile("tide_shell", HEALTH, BASE_RESIST,
                new int[] {70, 60, 50, 45},
                new int[] {46, 48, 50, 52},
                new double[] {0.1, 0.15, 0.2, 0.25},
                new float[][] {
                        {UPDRAFT_DAMAGE, DOWNDRAFT_DAMAGE, VOLLEY_DAMAGE, CHARGE_DAMAGE},
                        {UPDRAFT_DAMAGE, DOWNDRAFT_DAMAGE, VOLLEY_DAMAGE, CHARGE_DAMAGE},
                        {UPDRAFT_DAMAGE, DOWNDRAFT_DAMAGE, VOLLEY_DAMAGE, CHARGE_DAMAGE},
                        {UPDRAFT_DAMAGE, DOWNDRAFT_DAMAGE, VOLLEY_DAMAGE, CHARGE_DAMAGE}},
                2 * (SHELL_WINDUP + SHELL_HOLD));
    }
}
