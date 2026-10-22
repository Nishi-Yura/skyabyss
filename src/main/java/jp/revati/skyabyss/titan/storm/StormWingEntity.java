package jp.revati.skyabyss.titan.storm;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.sky.SkyEvents;
import jp.revati.skyabyss.sky.SkyPass;
import jp.revati.skyabyss.titan.TitanKeeper;
import jp.revati.skyabyss.titan.TitanStay;
import jp.revati.skyabyss.titan.storm.StormPerch.Peak;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 第三の巨神「雷翼の巨神」。嵐を纏って雲の下を飛ぶ山のような鳥 (12 の 2 章)。
// - 巡航: 「嵐の中心」のまわりを大きな輪で飛ぶ。中心は毎秒 0.4 ブロックで動く (風と関係なく)
// - 夕立の刻 (7000〜10600): 刻の 1200 tick 前に止まり木を選び、刻の 900 tick 前から降りて、峰に止まる。左の足もとに昇り風 (GaleColumnEntity)
// - 空の影が 640 ブロック以内を通るあいだは、近くの峰へ急いで降りてうずくまる (演出だけ)
// 当たり判定は小さく、見た目は描画側で 8 倍にする (潮殻と同じ)。位置の y はモデルの底 (geo の y=0)
// 地上の人には、この中心の周りを回る巨神として見える。地形は変えない (エンティティと粒だけ)
public class StormWingEntity extends Entity implements GeoEntity, jp.revati.skyabyss.titan.end.DormantTitan, jp.revati.skyabyss.titan.solid.SolidTitan, jp.revati.skyabyss.titan.TitanVisibility.Hidden {
    @Override
    public String visibilityTitan() {
        return jp.revati.skyabyss.progress.Titans.THUNDER_WING;
    }

    // 巨神の見え方 (docs/design/32 の 3.3): 章を終えた人 (とそのパーティでない人) には実体を送らない
    @Override
    public boolean broadcastToPlayer(net.minecraft.server.level.ServerPlayer player) {
        return super.broadcastToPlayer(player) && jp.revati.skyabyss.titan.TitanVisibility.sees(player, jp.revati.skyabyss.progress.Titans.THUNDER_WING);
    }

    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(StormWingEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> LOOK = SynchedEntityData.defineId(StormWingEntity.class, EntityDataSerializers.BOOLEAN);
    // 眠りの姿 (TitanDormancy)。起きていれば -1、眠って峰へ向かいはじめてからは tick (600 で止まる)
    private static final EntityDataAccessor<Integer> REST = SynchedEntityData.defineId(StormWingEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.thunder_wing.fly");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.thunder_wing.glide");
    private static final RawAnimation PERCH_ANIM = RawAnimation.begin().thenPlay("animation.thunder_wing.land").thenLoop("animation.thunder_wing.perch");
    private static final RawAnimation TAKE_OFF_ANIM = RawAnimation.begin().thenPlay("animation.thunder_wing.take_off").thenLoop("animation.thunder_wing.fly");
    private static final RawAnimation COWER_ANIM = RawAnimation.begin().thenLoop("animation.thunder_wing.cower");
    private static final RawAnimation LOOK_ANIM = RawAnimation.begin().thenLoop("animation.thunder_wing.look");

    // ---- 体の寸法 (ブロック)。横は左が + (潮殻と同じ。ゲームの向きは yaw で決まる)
    // 底 (位置の y) から羽根の門 (腰) まで。昇り風の頂はここ
    public static final double GATE_UP = 25.0;
    // 左の足もと (止まり木で昇り風の柱が立つ所): 左へ 6、頭の向きへ -4 (尾の側へ 4)。柱はここから左へ 8 離す
    public static final double FOOT_SIDE = 6.0;
    public static final double FOOT_FORWARD = -4.0;
    public static final double COLUMN_SIDE = 8.0;
    // 頭の先は前へ 60、首を上げる判定の高さ 30
    public static final double HEAD_FORWARD = 60.0;
    public static final double HEAD_UP = 30.0;
    // 翼の幅 (左右それぞれ)。描画のための数字
    public static final double WING_SPAN = 112.0;
    private static final double LOOK_RANGE = 56.0;

    // ---- 巡航
    static final double CRUISE_SPEED = 0.5;
    static final double RING_MIN = 120;
    static final double RING_MAX = 200;
    static final double CENTRE_MIN = 160;
    static final double CENTRE_MAX = 320;
    // 嵐の中心が動く速さ (毎秒 0.4 ブロック)
    static final double CENTRE_DRIFT = 0.02;
    static final double PLAYER_RANGE = 640;
    // 誰からもこれだけ離れたら、中心を選び直す
    static final double LOST_RANGE = 600;
    static final double CRUISE_MIN_Y = 150;
    static final double CRUISE_MAX_Y = 185;
    static final double RISE_RATE = 0.3;

    // ---- 夕立の刻 (時刻は一日の中の tick)
    static final long PERCH_CHOOSE = 5800;
    static final long DESCEND_START = 6100;
    static final long WINDOW_START = 7000;
    static final long WINDOW_END = 10600;
    static final int DESCEND_TICKS = 900;
    // 初めて乗る人のために刻の外で降りたとき、止まっている長さ (5 分。誰もいなければ早じまい)
    static final int SUMMIT_REST = 6000;
    static final int TAKE_OFF_TICKS = 100;
    static final double TAKE_OFF_FORWARD = 0.5;
    // 空の影から隠れる速さと、影を見る距離
    static final double COVER_SPEED = 0.8;
    static final double COWER_RANGE = 640;
    static final double COWER_LORE_RANGE = 160;
    static final double TELL_RANGE = 200;

    public enum Mode { CRUISE, DESCEND, PERCH, TAKE_OFF, COWER }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Mode mode = Mode.CRUISE;
    private int modeTicks;
    // 巡航の輪: 中心、中心の動く向き、輪の半径、今の角度 (ラジアン。向きは角度と同じ)
    private double stormX;
    private double stormZ;
    private double driftX;
    private double driftZ;
    private double radius = 160;
    private double theta;
    private double cruiseGoal = CRUISE_MIN_Y;
    // 止まり木 (地表の点)
    private boolean hasPerch;
    private double perchX;
    private double perchY;
    private double perchZ;
    // 夕立の刻を選んだ日・降りた日 (一日に一度ずつ。日の番号 = 総 tick / 24000)
    private long perchDay = -1;
    private long descendDay = -1;
    // コマンドで止まらせた残り (0 なら刻で決まる)
    private int forcedTicks;
    private final jp.revati.skyabyss.titan.FirstBoardWait summitWait = new jp.revati.skyabyss.titan.FirstBoardWait();
    // 降りるときの渦 (半径を縮めながら止まり木へ)
    private double descendR0;
    private double descendTheta;
    private double descendY0;
    // 空の影から隠れる先
    private boolean hasCover;
    private double coverX;
    private double coverY;
    private double coverZ;
    // 最後に止まった足もと (背中から戻るとき、止まっていなければここへ)
    private boolean hasLastFoot;
    private double lastFootX;
    private double lastFootZ;
    // 昇り風の柱 (止まっているあいだだけ。保存しない)
    @Nullable
    private UUID gale;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    public StormWingEntity(EntityType<? extends StormWingEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, (byte) 0);
        builder.define(LOOK, false);
        builder.define(REST, -1);
    }

    @Override
    public int restTicks() {
        return entityData.get(REST);
    }

    // ---- 外から見る

    // 止まり木にとどまる残り tick (テスト用)
    public int stayLeft() {
        return mode == Mode.PERCH ? forcedTicks : 0;
    }

    public Mode mode() {
        return mode;
    }

    public boolean isPerched() {
        return mode == Mode.PERCH;
    }

    // うずくまっているか (空の影から隠れている。三章の見せ場 chapter/StormShowcase が見る)
    public boolean cowering() {
        return mode == Mode.COWER && entityData.get(REST) < 0;
    }

    // 今の動きになってからの tick
    public int modeTicks() {
        return modeTicks;
    }

    public boolean hasLastFoot() {
        return hasLastFoot;
    }

    public double lastFootX() {
        return lastFootX;
    }

    public double lastFootZ() {
        return lastFootZ;
    }

    // 左の足もと (今の向きでの位置。y は使わない)
    public Vec3 footPoint() {
        Vec3 o = offset(getYRot(), FOOT_SIDE, 0, FOOT_FORWARD);
        return new Vec3(getX() + o.x, getY(), getZ() + o.z);
    }

    // 向き yaw (度) の巨神から見た (横, 上, 前) の点の位置。横は左が +、前は頭の向きが + (潮殻と同じ)
    public static Vec3 offset(float yawDeg, double side, double up, double forward) {
        double fx = -Mth.sin(yawDeg * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yawDeg * Mth.DEG_TO_RAD);
        return new Vec3(fx * forward + fz * side, up, fz * forward - fx * side);
    }

    // 夕立の刻か (一日の中の時刻)
    public static boolean inWindow(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= WINDOW_START && t < WINDOW_END;
    }

    // ---- 外から頼まれる (コマンド)

    // 近くの峰へ降りて止まる。forced tick のあいだ止まり続ける (夕立の刻を待たない)。峰がなければ false
    public boolean comeRest(ServerPlayer player, int forced) {
        ServerLevel level = (ServerLevel) level();
        Peak p = StormPerch.find(level, player.getX(), player.getZ(), 24, 96);
        if (p == null) {
            return false;
        }
        perchX = p.x();
        perchY = p.y();
        perchZ = p.z();
        hasPerch = true;
        beginDescend(forced);
        return true;
    }

    // 決まった峰 (StormPeak.perch) へ降りて止まる。風見鶏で、峰に限る世界のとき
    public boolean comeRestAt(Peak p, int forced) {
        if (p == null) {
            return false;
        }
        perchX = p.x();
        perchY = p.y();
        perchZ = p.z();
        hasPerch = true;
        beginDescend(forced);
        return true;
    }

    // その場で止まる (地表の上)。テスト用
    public void restHere(int forced) {
        ServerLevel level = (ServerLevel) level();
        double gy = StormPerch.surfaceAt(level, getX(), getZ());
        perchX = getX();
        perchY = Double.isNaN(gy) ? getY() : gy;
        double need = jp.revati.skyabyss.titan.TitanGround.requiredAnyYaw(level, jp.revati.skyabyss.titan.TitanGround.Surface.TOP,
                jp.revati.skyabyss.titan.solid.TitanVolume.get(volumeName()), getX(), getZ(), 1);
        if (Double.isFinite(need)) {
            perchY = Math.max(perchY, need);
        }
        perchZ = getZ();
        hasPerch = true;
        forcedTicks = forced;
        arrive(level);
    }

    // 見守り (TitanKeeper) が覚えておく状態
    public CompoundTag saveState() {
        CompoundTag tag = new CompoundTag();
        addAdditionalSaveData(tag);
        return tag;
    }

    // 呼び直した個体へ引き継ぐ。巡航から始め、夕立の刻の選びと降りた日、最後に止まった足もとを残す
    public void restoreState(CompoundTag tag) {
        perchDay = tag.getLong("PerchDay");
        descendDay = tag.getLong("DescendDay");
        hasLastFoot = tag.getBoolean("HasLastFoot");
        lastFootX = tag.getDouble("LastFootX");
        lastFootZ = tag.getDouble("LastFootZ");
        setMode(Mode.CRUISE);
        hasPerch = false;
        forcedTicks = 0;
    }

    // 呼び直した・出した直後に、嵐の中心を (x, z) に置く。輪の上の、ランダムな角度から始める
    public void initCentre(double x, double z, double y, net.minecraft.util.RandomSource random) {
        stormX = x;
        stormZ = z;
        radius = RING_MIN + random.nextDouble() * (RING_MAX - RING_MIN);
        double a = random.nextDouble() * Math.PI * 2;
        driftX = Math.cos(a);
        driftZ = Math.sin(a);
        theta = random.nextDouble() * Math.PI * 2;
        moveTo(stormX + radius * Math.cos(theta), y, stormZ + radius * Math.sin(theta), (float) Math.toDegrees(theta), 0f);
    }

    // ---- 毎 tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, 0);
                lerpSteps--;
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        modeTicks++;
        if (tickDormant(level)) {
            return;
        }
        switch (mode) {
            case CRUISE -> tickCruise(level);
            case DESCEND -> tickDescend(level);
            case PERCH -> tickPerch(level);
            case TAKE_OFF -> tickTakeOff(level);
            case COWER -> tickCower(level);
        }
        if (mode == Mode.PERCH) {
            ensureGale(level);
        }
    }

    private boolean wasDormant;
    // 眠る場所 (峰の地表)
    private double sleepX;
    private double sleepY;
    private double sleepZ;

    // 眠り (docs/design/12): 心臓が止まると稲妻がやみ、近くの峰へ降りて翼を畳み、うずくまって岩の峰のようになる。目覚めると飛び立つ。
    // 戻り = 眠っている (通常の動きを飛ばす)
    private boolean tickDormant(ServerLevel level) {
        int age = jp.revati.skyabyss.titan.end.TitanDormancy.age(level.getServer(), jp.revati.skyabyss.progress.Titans.THUNDER_WING);
        if (age < 0) {
            if (wasDormant) {
                wasDormant = false;
                departing = false;
                entityData.set(REST, -1);
                hasCover = false;
                jp.revati.skyabyss.titan.TitanVisibility.playSound(level, jp.revati.skyabyss.progress.Titans.THUNDER_WING, getX(), getY(), getZ(), StormSetup.WING_CALL.get(), SoundSource.AMBIENT, 12f, 0.8f);
                beginTakeOff(level);
            }
            return false;
        }
        if (!wasDormant) {
            wasDormant = true;
            forcedTicks = 0;
            dropGale(level);
            chooseSleepSpot(level);
            setMode(Mode.COWER);
        }
        if (jp.revati.skyabyss.titan.end.TitanDormancy.isDeparting(age)) {
            // 眠って 10 秒: 峰で羽ばたいて飛び立ち、そのまま空の向こうへ去る (40 秒で地上から取り除く)
            tickDeparture(level, age);
        } else {
            double dx = sleepX - getX();
            double dz = sleepZ - getZ();
            double dist = Math.hypot(dx, dz);
            if (dist > 1.0) {
                setYRot(Mth.approachDegrees(getYRot(), (float) Math.toDegrees(Math.atan2(-dx, dz)), 3f));
                double step = Math.min(COVER_SPEED, dist);
                double ny = getY() + Mth.clamp(sleepY - getY(), -COVER_SPEED, COVER_SPEED);
                setPos(getX() + dx / dist * step, ny, getZ() + dz / dist * step);
            } else {
                setPos(sleepX, sleepY, sleepZ);
            }
        }
        if (tickCount % 5 == 0) {
            entityData.set(REST, Math.min(age, jp.revati.skyabyss.titan.end.TitanDormancy.SYNC_CAP));
        }
        if (age >= jp.revati.skyabyss.titan.end.TitanDormancy.DEPART_TICKS) {
            jp.revati.skyabyss.titan.end.TitanDeparture.finish(this, jp.revati.skyabyss.progress.Titans.THUNDER_WING);
        }
        return true;
    }

    private boolean departing;

    // 去る: 飛び立つ姿勢 (TAKE_OFF の動き) で、向きのまま前へ・上へ、だんだん速く。電光の粒を引く
    private void tickDeparture(ServerLevel level, int age) {
        if (!departing) {
            departing = true;
            setMode(Mode.TAKE_OFF);
            jp.revati.skyabyss.titan.TitanVisibility.playSound(level, jp.revati.skyabyss.progress.Titans.THUNDER_WING, getX(), getY(), getZ(), StormSetup.WING_CALL.get(), SoundSource.AMBIENT, 16f, 0.7f);
        }
        float ramp = jp.revati.skyabyss.titan.end.TitanDormancy.departProgress(age);
        double yaw = Math.toRadians(getYRot());
        double forward = 0.3 + 1.2 * ramp;
        setPos(getX() - Math.sin(yaw) * forward, getY() + 0.15 + 0.35 * ramp, getZ() + Math.cos(yaw) * forward);
        if (tickCount % 3 == 0) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) < 400 * 400) {
                    level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, true, getX(), getY(), getZ(), 14, 18, 6, 18, 0.2);
                    level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.CLOUD, true, getX(), getY() - 4, getZ(), 10, 24, 4, 24, 0.02);
                }
            }
        }
    }

    private void chooseSleepSpot(ServerLevel level) {
        var rest = jp.revati.skyabyss.titan.end.TitanDormancy.rest(level.getServer(), jp.revati.skyabyss.progress.Titans.THUNDER_WING);
        Vec3 spot = rest == null ? null : rest.spot();
        if (spot == null) {
            Peak p = StormPerch.find(level, getX(), getZ(), 0, 160);
            if (p != null) {
                spot = new Vec3(p.x(), p.y(), p.z());
            } else {
                double gy = StormPerch.surfaceAt(level, getX(), getZ());
                spot = new Vec3(getX(), Double.isNaN(gy) ? getY() : gy, getZ());
            }
        }
        sleepX = spot.x;
        sleepY = spot.y;
        sleepZ = spot.z;
    }

    private void dropGale(ServerLevel level) {
        Entity e = gale == null ? null : level.getEntity(gale);
        if (e != null) {
            e.discard();
        }
        gale = null;
    }

    private void tickCruise(ServerLevel level) {
        if (tickCount % 20 == 0) {
            keepCentreNearPlayers(level);
            scheduleDay(level);
            if (mode == Mode.CRUISE) {
                answerSummitWait(level);
            }
        }
        driftCentre();
        theta += CRUISE_SPEED / radius;
        moveRing();
        if (tickCount % 10 == 0) {
            cruiseGoal = cruiseTarget(level);
        }
        setPos(getX(), getY() + Mth.clamp(cruiseGoal - getY(), -RISE_RATE, RISE_RATE), getZ());
        if (tickCount % 10 == 0) {
            startCowerIfPassing(level);
        }
    }

    // 嵐の中心を毎秒 0.4 ブロックで動かす (風とは関係なく)
    private void driftCentre() {
        stormX += driftX * CENTRE_DRIFT;
        stormZ += driftZ * CENTRE_DRIFT;
    }

    // 輪の上の今の角度へ置く。向きは進む向き (接線)
    private void moveRing() {
        double x = stormX + radius * Math.cos(theta);
        double z = stormZ + radius * Math.sin(theta);
        setYRot((float) Math.toDegrees(theta));
        setPos(x, getY(), z);
    }

    // 中心の選び直し: どのプレイヤーからも LOST_RANGE より離れたら、一人の近くへ置き直す
    private void keepCentreNearPlayers(ServerLevel level) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (TitanKeeper.eligible(p)) {
                best = Math.min(best, Math.hypot(p.getX() - getX(), p.getZ() - getZ()));
            }
        }
        if (best > LOST_RANGE) {
            repick(level);
        }
    }

    // 人の近く (CENTRE_MIN〜MAX) へ嵐の中心を置く。人がいなければ動かさない
    private void repick(ServerLevel level) {
        List<ServerPlayer> players = level.players().stream().filter(TitanKeeper::eligible).toList();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer p = players.get(level.random.nextInt(players.size()));
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = CENTRE_MIN + level.random.nextDouble() * (CENTRE_MAX - CENTRE_MIN);
        stormX = p.getX() + Math.cos(a) * d;
        stormZ = p.getZ() + Math.sin(a) * d;
        radius = RING_MIN + level.random.nextDouble() * (RING_MAX - RING_MIN);
        double da = level.random.nextDouble() * Math.PI * 2;
        driftX = Math.cos(da);
        driftZ = Math.sin(da);
        theta = Math.atan2(getZ() - stormZ, getX() - stormX);
    }

    // 巡航の高さ: 前方 30〜90 ブロックの一番高い地表 + 60 と 150 の高い方、ただし 185 まで (雲の上へ出ない)
    private double cruiseTarget(ServerLevel level) {
        double yaw = Math.toRadians(getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double maxSurface = Double.NaN;
        for (double d : new double[] {30, 60, 90}) {
            double s = StormPerch.surfaceAt(level, getX() + fx * d, getZ() + fz * d);
            if (!Double.isNaN(s) && (Double.isNaN(maxSurface) || s > maxSurface)) {
                maxSurface = s;
            }
        }
        double want = Double.isNaN(maxSurface) ? CRUISE_MIN_Y : Math.max(maxSurface + 60, CRUISE_MIN_Y);
        return Math.min(want, CRUISE_MAX_Y);
    }

    // 夕立の刻の段取り (一日に一度): 刻の 1200 tick 前に止まり木を選び、刻の 900 tick 前から降りる
    private void scheduleDay(ServerLevel level) {
        long dt = level.getDayTime();
        long t = Math.floorMod(dt, 24000L);
        long day = Math.floorDiv(dt, 24000L);
        if (t >= PERCH_CHOOSE && t < DESCEND_START && perchDay != day) {
            if (choosePerch(level)) {
                perchDay = day;
            }
        } else if (t >= DESCEND_START && t < WINDOW_START && hasPerch && perchDay == day && descendDay != day) {
            descendDay = day;
            beginDescend(0);
        }
    }

    // 初めて乗る人が峰で 30 秒待ったら、刻の外でも降りる (fun_review P3-1。FirstBoardWait)。
    // 夕立の刻の段取りの間 (止まり木を選んでから刻の終わりまで) は、ふだんどおり刻に任せる
    private void answerSummitWait(ServerLevel level) {
        long t = Math.floorMod(level.getDayTime(), 24000L);
        if (t >= PERCH_CHOOSE && t < WINDOW_END) {
            summitWait.tick(level, jp.revati.skyabyss.progress.Titans.THUNDER_WING, null, 20);
            return;
        }
        boolean peakOnly = StormPeak.restricted(level.getServer());
        BlockPos centre = peakOnly ? StormPeak.peak(level.getServer()) : null;
        ServerPlayer p;
        if (peakOnly) {
            p = summitWait.tick(level, jp.revati.skyabyss.progress.Titans.THUNDER_WING, centre, 20);
        } else {
            // 跡地のない世界: 空の見える高い所に立つ人のまわり
            ServerPlayer high = null;
            for (ServerPlayer q : Players.snapshot(level.players())) {
                if (jp.revati.skyabyss.titan.FirstBoardWait.waiting(q, jp.revati.skyabyss.progress.Titans.THUNDER_WING)
                        && level.canSeeSky(q.blockPosition().above()) && WeathercockItem.high(level, q.blockPosition())
                        && Math.hypot(q.getX() - getX(), q.getZ() - getZ()) < PLAYER_RANGE) {
                    high = q;
                    break;
                }
            }
            p = summitWait.tick(level, jp.revati.skyabyss.progress.Titans.THUNDER_WING, high == null ? null : high.blockPosition(), 20);
        }
        if (p == null) {
            return;
        }
        Peak peak = peakOnly ? StormPeak.perch(level) : StormPerch.find(level, p.getX(), p.getZ(), 24, 96);
        if (peak == null) {
            return;
        }
        perchX = peak.x();
        perchY = peak.y();
        perchZ = peak.z();
        hasPerch = true;
        tell(level, "message.skyabyss.storm.summit_noticed");
        beginDescend(SUMMIT_REST);
    }

    // 640 ブロック以内のプレイヤーを一人選び、その近くの峰を止まり木にする。
    // 三章の跡地があれば、止まるのはその峰だけ (峰の近くに人がいるとき。docs/design/33 の 3.1。StormPeak)
    private boolean choosePerch(ServerLevel level) {
        boolean peakOnly = StormPeak.restricted(level.getServer());
        List<ServerPlayer> near = level.players().stream()
                .filter(p -> TitanKeeper.eligible(p) && Math.hypot(p.getX() - getX(), p.getZ() - getZ()) < PLAYER_RANGE
                        && (!peakOnly || StormPeak.near(p)))
                .toList();
        if (near.isEmpty()) {
            return false;
        }
        ServerPlayer p = near.get(level.random.nextInt(near.size()));
        Peak peak = peakOnly ? StormPeak.perch(level) : StormPerch.find(level, p.getX(), p.getZ(), 24, 96);
        if (peak == null) {
            return false;
        }
        perchX = peak.x();
        perchY = peak.y();
        perchZ = peak.z();
        hasPerch = true;
        tell(level, "message.skyabyss.storm.perching");
        return true;
    }

    // 降りる: 今の位置から止まり木の上へ、輪を縮めながら (渦を描いて) 降りる
    private void beginDescend(int forced) {
        descendR0 = Math.max(1, Math.hypot(getX() - perchX, getZ() - perchZ));
        descendTheta = Math.atan2(getZ() - perchZ, getX() - perchX);
        descendY0 = getY();
        forcedTicks = forced;
        setMode(Mode.DESCEND);
    }

    private void tickDescend(ServerLevel level) {
        double p = Math.min(1.0, modeTicks / (double) DESCEND_TICKS);
        double r = descendR0 * (1 - p);
        // 中心に近いほど角速度を抑える (回りすぎて見えないように)
        descendTheta += Math.min(0.2, CRUISE_SPEED / Math.max(r, 1));
        double x = perchX + r * Math.cos(descendTheta);
        double z = perchZ + r * Math.sin(descendTheta);
        double y = descendY0 + (perchY - descendY0) * p;
        setYRot((float) Math.toDegrees(descendTheta));
        setPos(x, Math.max(y, terrainFloor(level)), z);
        if (p >= 1.0) {
            setPos(perchX, perchY, perchZ);
            arrive(level);
        }
    }

    // 止まり木に着いた
    private void arrive(ServerLevel level) {
        Vec3 foot = footPoint();
        hasLastFoot = true;
        lastFootX = foot.x;
        lastFootZ = foot.z;
        setMode(Mode.PERCH);
        // 止まり木には 20 分とどまる (夕立の刻の長さでは去らない。TitanStay)
        forcedTicks = TitanStay.TICKS;
        jp.revati.skyabyss.titan.TitanVisibility.playSound(level, jp.revati.skyabyss.progress.Titans.THUNDER_WING, getX(), getY(), getZ(), StormSetup.WING_CALL.get(), SoundSource.AMBIENT, 12f, 0.9f);
    }

    private final TitanStay.Warner stayWarner = new TitanStay.Warner();

    private void tickPerch(ServerLevel level) {
        setPos(perchX, perchY, perchZ);
        if (tickCount % 10 == 0) {
            updateLook(level);
        }
        if (forcedTicks > 0) {
            stayWarner.tick(level, position(), forcedTicks, "message.skyabyss.stay_ending.storm");
            // 居座りの早じまい: 誰もいないのが 3 分続いたら飛び立つ
            if (stayWarner.vacantTooLong(level, position(), jp.revati.skyabyss.progress.Titans.THUNDER_WING)) {
                forcedTicks = 1;
            }
            if (--forcedTicks <= 0) {
                // 20 分たった: 夕立の刻の内でも飛び立つ
                beginTakeOff(level);
            }
            return;
        }
        // 刻が終わったら飛び立つ
        if (!inWindow(level.getDayTime())) {
            beginTakeOff(level);
        }
    }

    // 首を上げる: 頭から 56 ブロック以内に人 (観戦者を除く) がいる
    private void updateLook(ServerLevel level) {
        Vec3 head = position().add(offset(getYRot(), 0, HEAD_UP, HEAD_FORWARD));
        boolean look = false;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (!p.isSpectator() && p.distanceToSqr(head) < LOOK_RANGE * LOOK_RANGE) {
                look = true;
                break;
            }
        }
        entityData.set(LOOK, look);
    }

    private void beginTakeOff(ServerLevel level) {
        forcedTicks = 0;
        if (mode == Mode.PERCH) {
            tell(level, "message.skyabyss.storm.taking_off");
        }
        setMode(Mode.TAKE_OFF);
    }

    // 近くの人 (止まり木から TELL_RANGE 以内) に一行
    private void tell(ServerLevel level, String key) {
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (TitanKeeper.eligible(p) && Math.hypot(p.getX() - perchX, p.getZ() - perchZ) < TELL_RANGE) {
                Notice.bar(p, jp.revati.skyabyss.ModText.lore(key));
            }
        }
    }

    // 飛び立つ: 前へ進みながら上がる。終わったら、今いる所をそのまま輪の上の点として巡航に入る (向きは進む向きと同じ)
    private void tickTakeOff(ServerLevel level) {
        double yaw = Math.toRadians(getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double y = Math.min(cruiseTarget(level), getY() + RISE_RATE);
        setPos(getX() + fx * TAKE_OFF_FORWARD, Math.max(y, terrainFloor(level)), getZ() + fz * TAKE_OFF_FORWARD);
        if (modeTicks >= TAKE_OFF_TICKS) {
            // 輪の中心は進む向きの右の方 (接線が今の向きと一致するように置く)
            radius = 160;
            stormX = getX() - radius * Math.cos(yaw);
            stormZ = getZ() - radius * Math.sin(yaw);
            theta = yaw;
            driftX = Math.cos(yaw + Math.PI / 2);
            driftZ = Math.sin(yaw + Math.PI / 2);
            cruiseGoal = cruiseTarget(level);
            setMode(Mode.CRUISE);
        }
    }

    // 空の影が近くを通るあいだ、近くの峰へ急いで降り、うずくまる (演出だけ)
    private void startCowerIfPassing(ServerLevel level) {
        SkyPass pass = SkyEvents.activePass();
        if (pass == null || !pass.isActive(level.getGameTime())) {
            return;
        }
        double age = pass.age(level.getGameTime(), 0f);
        if (Math.hypot(pass.centerX(age) - getX(), pass.centerZ(age) - getZ()) >= COWER_RANGE) {
            return;
        }
        Peak p = StormPerch.find(level, getX(), getZ(), 0, 96);
        hasCover = p != null;
        if (p != null) {
            coverX = p.x();
            coverY = p.y();
            coverZ = p.z();
        }
        setMode(Mode.COWER);
    }

    private void tickCower(ServerLevel level) {
        // うずくまる雷翼を 160 ブロック以内で見た人に、伝承「うずくまる巨神」を記す (12 の 11.1 の約束)
        if (modeTicks % 20 == 0) {
            for (ServerPlayer p : Players.snapshot(level.players())) {
                if (TitanKeeper.eligible(p) && p.distanceToSqr(this) < COWER_LORE_RANGE * COWER_LORE_RANGE && p.hasLineOfSight(this)) {
                    jp.revati.skyabyss.lore.Lore.unlock(p, jp.revati.skyabyss.titan.storm.lore.StormLore.LORE_COWER, true);
                }
            }
        }
        SkyPass pass = SkyEvents.activePass();
        if (pass == null || !pass.isActive(level.getGameTime())) {
            beginTakeOff(level);
            return;
        }
        if (hasCover) {
            // 峰へ急ぐ。着いたら、そこでうずくまる
            double dx = coverX - getX();
            double dz = coverZ - getZ();
            double dist = Math.hypot(dx, dz);
            if (dist > 1.0) {
                setYRot(Mth.approachDegrees(getYRot(), (float) Math.toDegrees(Math.atan2(-dx, dz)), 3f));
                double step = Math.min(COVER_SPEED, dist);
                double ny = getY() + Mth.clamp(coverY - getY(), -COVER_SPEED, COVER_SPEED);
                // (ny は今の高さから峰の高さへ寄せる。地形にめり込む高さには下げない)
                setPos(getX() + dx / dist * step, Math.max(ny, terrainFloor(level)), getZ() + dz / dist * step);
            } else {
                setPos(coverX, coverY, coverZ);
            }
        }
    }

    // 体が丘・木・峰にめり込まない最低の高さ (今の位置・向きで。4 tick ごとに測り直す。降りる・飛び立つ・峰へ急ぐあいだの下限)
    private double groundNow = Double.NEGATIVE_INFINITY;
    private int groundAt = -100;

    private double terrainFloor(ServerLevel level) {
        if (tickCount - groundAt >= 4) {
            groundAt = tickCount;
            groundNow = jp.revati.skyabyss.titan.TitanGround.requiredY(level, jp.revati.skyabyss.titan.TitanGround.Surface.TOP,
                    jp.revati.skyabyss.titan.solid.TitanVolume.get(volumeName()), getX(), getZ(), getYRot(), jp.revati.skyabyss.titan.TitanGround.MARGIN, 1);
        }
        return groundNow;
    }

    // 昇り風の柱: 止まっているあいだ、左の足もとから 8 ブロック左に一本立てる
    private void ensureGale(ServerLevel level) {
        Entity e = gale == null ? null : level.getEntity(gale);
        if (e instanceof GaleColumnEntity) {
            return;
        }
        Vec3 o = offset(getYRot(), FOOT_SIDE + COLUMN_SIDE, 0, FOOT_FORWARD);
        GaleColumnEntity col = StormSetup.GALE_COLUMN.get().create(level);
        if (col == null) {
            return;
        }
        col.setup(getUUID(), getX() + o.x, getZ() + o.z, getY() + GATE_UP);
        level.addFreshEntity(col);
        gale = col.getUUID();
    }

    // 位置を滑らかに (サーバーは 2 tick ごとに送る)
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (distanceToSqr(x, y, z) > 64.0 * 64.0) {
            this.lerpSteps = 0;
            setPos(x, y, z);
            setYRot(yRot % 360f);
            this.xo = x;
            this.yo = y;
            this.zo = z;
            this.yRotO = getYRot();
            return;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = Math.max(1, steps);
    }

    private void setMode(Mode m) {
        mode = m;
        modeTicks = 0;
        entityData.set(MODE, (byte) m.ordinal());
        if (m != Mode.PERCH) {
            entityData.set(LOOK, false);
        }
    }

    // ---- 巨神は傷つかない・つかめない

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 768.0 * 768.0;
    }

    // 見た目は当たり判定よりずっと大きいので、画面外判定を広げる (翼の幅 ±112、体の長さ ±60、塔の高さ 30)
    // 体に入れない (titan/solid)。体積は tools/gen/world/gen_titan_volumes.py が近景のメッシュから焼く。入ってよいのは乗り場の通り道だけ
    @Override
    public String volumeName() {
        return "thunder_wing";
    }

    @Override
    public java.util.List<jp.revati.skyabyss.titan.solid.SolidTitan.Corridor> corridors() {
        return CORRIDORS;
    }

    private static final java.util.List<jp.revati.skyabyss.titan.solid.SolidTitan.Corridor> CORRIDORS = java.util.List.of(
            // 昇り風の柱 (左へ 14、前へ -4) の根もとから腰の門の上まで
            new jp.revati.skyabyss.titan.solid.SolidTitan.Corridor(8.0, 20.0, -400.0, 30.0, -10.0, 2.0));

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(WING_SPAN, 0.0, WING_SPAN).expandTowards(0, GATE_UP + 40, 0).expandTowards(0, -110.0, 0);
    }

    // 光を測る所: 体の中ほど (地上の影にならないよう、少し上)
    @Override
    public Vec3 getLightProbePosition(float partialTicks) {
        Vec3 p = getPosition(partialTicks);
        return new Vec3(p.x, p.y + GATE_UP, p.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        mode = Mode.values()[Mth.clamp(tag.getInt("Mode"), 0, Mode.values().length - 1)];
        entityData.set(MODE, (byte) mode.ordinal());
        modeTicks = tag.getInt("ModeTicks");
        stormX = tag.getDouble("StormX");
        stormZ = tag.getDouble("StormZ");
        driftX = tag.getDouble("DriftX");
        driftZ = tag.getDouble("DriftZ");
        radius = tag.getDouble("Radius");
        theta = tag.getDouble("Theta");
        cruiseGoal = tag.getDouble("CruiseGoal");
        hasPerch = tag.getBoolean("HasPerch");
        perchX = tag.getDouble("PerchX");
        perchY = tag.getDouble("PerchY");
        perchZ = tag.getDouble("PerchZ");
        perchDay = tag.getLong("PerchDay");
        descendDay = tag.getLong("DescendDay");
        forcedTicks = tag.getInt("ForcedTicks");
        descendR0 = tag.getDouble("DescendR0");
        descendTheta = tag.getDouble("DescendTheta");
        descendY0 = tag.getDouble("DescendY0");
        hasCover = tag.getBoolean("HasCover");
        coverX = tag.getDouble("CoverX");
        coverY = tag.getDouble("CoverY");
        coverZ = tag.getDouble("CoverZ");
        hasLastFoot = tag.getBoolean("HasLastFoot");
        lastFootX = tag.getDouble("LastFootX");
        lastFootZ = tag.getDouble("LastFootZ");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("ModeTicks", modeTicks);
        tag.putDouble("StormX", stormX);
        tag.putDouble("StormZ", stormZ);
        tag.putDouble("DriftX", driftX);
        tag.putDouble("DriftZ", driftZ);
        tag.putDouble("Radius", radius);
        tag.putDouble("Theta", theta);
        tag.putDouble("CruiseGoal", cruiseGoal);
        tag.putBoolean("HasPerch", hasPerch);
        tag.putDouble("PerchX", perchX);
        tag.putDouble("PerchY", perchY);
        tag.putDouble("PerchZ", perchZ);
        tag.putLong("PerchDay", perchDay);
        tag.putLong("DescendDay", descendDay);
        tag.putInt("ForcedTicks", forcedTicks);
        tag.putDouble("DescendR0", descendR0);
        tag.putDouble("DescendTheta", descendTheta);
        tag.putDouble("DescendY0", descendY0);
        tag.putBoolean("HasCover", hasCover);
        tag.putDouble("CoverX", coverX);
        tag.putDouble("CoverY", coverY);
        tag.putDouble("CoverZ", coverZ);
        tag.putBoolean("HasLastFoot", hasLastFoot);
        tag.putDouble("LastFootX", lastFootX);
        tag.putDouble("LastFootZ", lastFootZ);
    }

    // ---- アニメ (モードは同期の値から読む。クライアントでも同じ)

    private Mode syncedMode() {
        return Mode.values()[entityData.get(MODE)];
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 20, state -> state.setAndContinue(switch (syncedMode()) {
            case CRUISE -> FLY;
            case DESCEND -> GLIDE;
            case PERCH -> PERCH_ANIM;
            case TAKE_OFF -> TAKE_OFF_ANIM;
            case COWER -> COWER_ANIM;
        })));
        controllers.add(new AnimationController<>(this, "head", 10, state -> entityData.get(LOOK)
                ? state.setAndContinue(LOOK_ANIM) : PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // コマンドの表示用
    public String modeName() {
        return mode.name().toLowerCase(java.util.Locale.ROOT);
    }
}
