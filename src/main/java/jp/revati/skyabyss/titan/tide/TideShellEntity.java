package jp.revati.skyabyss.titan.tide;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.TitanKeeper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 第二の巨神「潮殻の巨神」。甲羅に潮の民の遺跡と珊瑚を乗せ、海の底を歩く山のような大亀 (08 の 2章)。
// - 巡航: 海の底をゆっくり歩く (毎秒 1.6 ブロック)。陸と浅瀬には入らない。遠くからは、甲羅の頂と塔だけが海から出た低い島に見える
// - 干潮の刻 (朝と夕、各 3 分): 刻の 1 分前に近くの人を選び、その人の近くの浅瀬へ寄って、甲羅の縁を水の上に出して休む
// - 休むあいだ、甲羅の後ろ左の縁にある潮門が水から出る。門の口に舟か泳ぎで入ると、背中 (tide_back) へ渡る
// 当たり判定は小さく、見た目は描画側で 8 倍にする (岩鯨と同じ。巨大なモデルには当たり判定を付けない)。
// 位置の y はモデルの底 (geo の y=0)。geo の 1 単位は 0.5 ブロック
public class TideShellEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(TideShellEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> LOOK = SynchedEntityData.defineId(TideShellEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tide_shell.walk");
    private static final RawAnimation REST_ANIM = RawAnimation.begin().thenLoop("animation.tide_shell.rest");
    private static final RawAnimation HEAD_IDLE = RawAnimation.begin().thenLoop("animation.tide_shell.head_idle");
    private static final RawAnimation LOOK_UP = RawAnimation.begin().thenLoop("animation.tide_shell.look");

    // ---- 体の寸法 (ブロック。モデルの geo 単位の半分)。横は左が + (ゲームでは geo の +X が左)
    // 潮門 (geo の gate 骨): 甲羅の後ろ左の縁。口 (geo x 58〜74, y 9〜31, z 55〜73) は横 29〜37、後ろ 27.5〜36.5、高さ 4.5〜15.5
    public static final double GATE_SIDE = 33.0;
    public static final double GATE_BACK = 32.0;
    // 門の口の判定 (横・後ろ・高さの範囲)。休むときの水面は高さ 4 なので、泳いでいる人と舟が入るように水面の下まで広げる
    static final double GATE_SIDE_MIN = 29.0;
    static final double GATE_SIDE_MAX = 39.0;
    static final double GATE_BACK_MIN = 27.5;
    static final double GATE_BACK_MAX = 36.5;
    static final double GATE_UP_MIN = 1.5;
    static final double GATE_UP_MAX = 15.0;
    // 背中から戻るときに出る所 (門の口の外の水の上)
    public static final double EXIT_SIDE = 41.0;
    // 頭の先は前へ 87 ブロック、首の付け根は前へ 50。首を上げる判定は頭の中ほど (前へ 66) から測る
    static final double HEAD_FORWARD = 66.0;
    static final double HEAD_UP = 12.0;
    // 休むときの水面は底から 3.5 ブロック上 (潮門の敷居 geo y=9 が水面の 1 ブロック上)。巡航は底から 29 ブロック上まで沈む (甲羅の頂が海から約 5 ブロック出る)
    static final double REST_DRAFT = 3.5;
    static final double CRUISE_DRAFT_MAX = 29.0;
    static final double CRUISE_DRAFT_MIN = 6.0;
    // 甲羅の頂 (光を測る所)
    static final double DOME_TOP = 34.0;

    // ---- 動き
    static final float CRUISE_SPEED = 0.08f;
    static final float APPROACH_SPEED = 0.2f;
    private static final float TURN_RATE = 0.4f;
    private static final double CRUISE_RISE = 0.025;
    private static final double REST_RISE = 0.05;
    private static final double LOOK_AHEAD = 24.0;
    private static final int APPROACH_GIVE_UP = 4800;
    // コマンドで休ませたときの長さ
    static final int FORCED_REST = 3600;

    enum Mode { CRUISE, APPROACH, REST }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Mode mode = Mode.CRUISE;
    private double targetX;
    private double targetZ;
    private boolean hasTarget;
    private double restX;
    private double restZ;
    private float restYaw;
    private int approachTicks;
    // コマンドで休ませた残り (0 なら刻で決まる)
    private int forcedTicks;
    // 最後に寄る場所を決めた刻 (同じ刻で二度寄らない)
    private long lastWindow = Long.MIN_VALUE;
    // 最後に休んだ場所 (背中から戻るとき、休んでいなければこの岸へ戻す)
    private boolean hasLastRest;
    private double lastRestX;
    private double lastRestZ;
    private int songCooldown = 1200;
    private int stepCooldown = 160;
    private int blockedCooldown;
    private double floorY = Double.NaN;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    public TideShellEntity(EntityType<? extends TideShellEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, (byte) 0);
        builder.define(LOOK, false);
    }

    public boolean isResting() {
        return entityData.get(MODE) == Mode.REST.ordinal();
    }

    public boolean isApproaching() {
        return entityData.get(MODE) == Mode.APPROACH.ordinal();
    }

    Mode mode() {
        return mode;
    }

    private void setMode(Mode m) {
        mode = m;
        entityData.set(MODE, (byte) m.ordinal());
        if (m != Mode.REST) {
            entityData.set(LOOK, false);
        }
    }

    // 向き yaw の巨神の、(横, 上, 前) の点の中心からの位置。横は左が + (岩鯨の offset と同じ回し方)
    public static Vec3 offset(float yaw, double side, double up, double forward) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(fx * forward + fz * side, up, fz * forward - fx * side);
    }

    // 世界の点の、巨神から見た (横, 上, 前)
    Vec3 local(double x, double y, double z) {
        float yaw = getYRot();
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double dx = x - getX();
        double dz = z - getZ();
        return new Vec3(dx * fz - dz * fx, y - getY(), dx * fx + dz * fz);
    }

    // 潮門の口の真ん中 (世界の座標)
    public Vec3 gateCenter() {
        return position().add(offset(getYRot(), GATE_SIDE, (GATE_UP_MIN + GATE_UP_MAX) / 2, -GATE_BACK));
    }

    // 門の口に入っているか
    boolean inGateMouth(Entity e) {
        Vec3 l = local(e.getX(), e.getY(), e.getZ());
        return l.x >= GATE_SIDE_MIN && l.x <= GATE_SIDE_MAX && -l.z >= GATE_BACK_MIN && -l.z <= GATE_BACK_MAX
                && l.y >= GATE_UP_MIN && l.y <= GATE_UP_MAX;
    }

    // 背中から戻るときに出る所 (門の口の外の水面)
    public Vec3 gateExit() {
        Vec3 p = position().add(offset(getYRot(), EXIT_SIDE, 0, -GATE_BACK));
        return new Vec3(p.x, level().getSeaLevel() - 0.4, p.z);
    }

    @Nullable
    public Vec3 lastRestSpot() {
        return hasLastRest ? new Vec3(lastRestX, 0, lastRestZ) : null;
    }

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
        if (tickCount % 20 == 0) {
            sampleFloor(level);
        }
        switch (mode) {
            case CRUISE -> tickCruise(level);
            case APPROACH -> tickApproach(level);
            case REST -> tickRest(level);
        }
        if (--songCooldown <= 0) {
            songCooldown = 2400 + random.nextInt(2400);
            level.playSound(null, getX(), level.getSeaLevel(), getZ(), TideSetup.SONG.get(), SoundSource.AMBIENT, 16f, 0.9f + random.nextFloat() * 0.1f);
        }
    }

    private void tickCruise(ServerLevel level) {
        if (blockedCooldown > 0) {
            blockedCooldown--;
        }
        if (!hasTarget || horizontalDistanceTo(targetX, targetZ) < 24) {
            pickCruiseTarget(level);
        }
        // 前方 24 ブロックの海面に水がない・浅い (読み込まれていれば) なら、向きを変える
        if (tickCount % 10 == 0 && blockedCooldown <= 0) {
            Vec3 ahead = position().add(offset(getYRot(), 0, 0, LOOK_AHEAD));
            if (TideOcean.loaded(level, ahead.x, ahead.z) && !TideOcean.walkable(level, ahead.x, ahead.z, 6)) {
                turnAway(level);
            }
        }
        steer(targetX, targetZ, cruiseHeight(level), CRUISE_SPEED, CRUISE_RISE);
        if (--stepCooldown <= 0) {
            stepCooldown = 160 + random.nextInt(160);
            level.playSound(null, getX(), getY() + 4, getZ(), TideSetup.STEP.get(), SoundSource.AMBIENT, 5f, 0.8f + random.nextFloat() * 0.2f);
        }
        if (tickCount % 100 == 0) {
            tryGather(level);
        }
    }

    // 干潮の刻の 1 分前から刻の終わりまでのあいだに、寄る相手と休む場所を決める (決まらなければ、10 秒ごとにやり直す)
    private void tryGather(ServerLevel level) {
        long day = level.getDayTime();
        if (!TideOcean.isGathering(day) || TideOcean.windowId(day) == lastWindow || !level.isLoaded(blockPosition())) {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (TitanKeeper.eligible(p) && horizontalDistanceTo(p.getX(), p.getZ()) < TitanKeeper.NEAR) {
                players.add(p);
            }
        }
        java.util.Collections.shuffle(players, new java.util.Random(random.nextLong()));
        for (ServerPlayer player : players) {
            Vec3 spot = TideOcean.findRestSpot(level, player.getX(), player.getZ());
            if (spot != null) {
                lastWindow = TideOcean.windowId(day);
                beginApproach(level, spot, 0);
                return;
            }
        }
    }

    // 休む場所 (x, 向き, z) へ向かう。forced > 0 ならコマンドで呼んだ (刻と関係なく、その長さだけ休む)
    void beginApproach(ServerLevel level, Vec3 spot, int forced) {
        restX = spot.x;
        restZ = spot.z;
        restYaw = (float) spot.y;
        forcedTicks = forced;
        approachTicks = 0;
        setMode(Mode.APPROACH);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(restX, player.getY(), restZ) < 200 * 200) {
                player.sendSystemMessage(ModText.lore("message.skyabyss.tide.ebb"));
                player.playNotifySound(TideSetup.SURF.get(), SoundSource.AMBIENT, 0.8f, 1.0f);
            }
        }
    }

    private void tickApproach(ServerLevel level) {
        approachTicks++;
        boolean forced = forcedTicks > 0;
        // 刻が過ぎた (刻で呼ばれたとき) か、長くかかりすぎたら、巡航に戻る
        if ((!forced && !TideOcean.isGathering(level.getDayTime())) || approachTicks > APPROACH_GIVE_UP) {
            setMode(Mode.CRUISE);
            hasTarget = false;
            return;
        }
        double dist = horizontalDistanceTo(restX, restZ);
        double y = level.getSeaLevel() - REST_DRAFT;
        if (dist > 2.0) {
            steer(restX, restZ, dist < 60 ? y : cruiseHeight(level), APPROACH_SPEED, REST_RISE);
            return;
        }
        // 着いた。向きを合わせてから休む (潮門を、選んだ人の側へ向ける)
        setDeltaMovement(Vec3.ZERO);
        float yaw = Mth.approachDegrees(getYRot(), restYaw, TURN_RATE * 2);
        setYRot(yaw);
        rise(y, REST_RISE);
        if (Math.abs(Mth.wrapDegrees(yaw - restYaw)) < 0.5f) {
            // 刻が始まる前に着いたら、刻の始まりまで待つ (コマンドのときは待たない)
            if (forced || TideOcean.isLowTide(level.getDayTime())) {
                startRest(level);
            }
        }
    }

    private void startRest(ServerLevel level) {
        setMode(Mode.REST);
        hasLastRest = true;
        lastRestX = getX();
        lastRestZ = getZ();
        level.playSound(null, getX(), level.getSeaLevel(), getZ(), TideSetup.SONG.get(), SoundSource.AMBIENT, 16f, 0.8f);
    }

    private void tickRest(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        rise(level.getSeaLevel() - REST_DRAFT, REST_RISE);
        boolean done = forcedTicks > 0 ? --forcedTicks <= 0 : !TideOcean.isLowTide(level.getDayTime());
        if (done) {
            // 刻の終わり。縁を沈め、巡航に戻る (背中に人がいても戻る。背中から出る人は、休んだ場所の岸へ戻る)
            forcedTicks = 0;
            setMode(Mode.CRUISE);
            hasTarget = false;
            level.playSound(null, getX(), level.getSeaLevel(), getZ(), TideSetup.SURF.get(), SoundSource.AMBIENT, 8f, 0.8f);
            return;
        }
        if (tickCount % 4 == 0) {
            Vec3 gate = gateCenter();
            AABB around = new AABB(gate, gate).inflate(10);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, around)) {
                if (TitanKeeper.eligible(player) && inGateMouth(player)) {
                    TideTravel.boardFromGate(player, this);
                }
            }
        }
        if (tickCount % 40 == 0) {
            Vec3 gate = gateCenter();
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(gate) < 16 * 16) {
                    player.displayClientMessage(ModText.hint("message.skyabyss.tide.gate_hint"), true);
                }
            }
        }
        if (tickCount % 10 == 0) {
            // 近くに人がいると、首を上げてそちらを見る
            Vec3 head = position().add(offset(getYRot(), 0, HEAD_UP, HEAD_FORWARD));
            boolean look = false;
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && player.distanceToSqr(head) < 56 * 56) {
                    look = true;
                    break;
                }
            }
            entityData.set(LOOK, look);
        }
    }

    // 巡航の高さ: 前方の海底に立つ高さ。ただし甲羅の頂が海から約 5 ブロック出る所より沈まず、潮門の敷居が水に隠れる所より浮かない
    private double cruiseHeight(ServerLevel level) {
        double sea = level.getSeaLevel();
        double stand = Double.isNaN(floorY) ? sea - CRUISE_DRAFT_MAX : floorY - 1.0;
        return Mth.clamp(stand, sea - CRUISE_DRAFT_MAX, sea - CRUISE_DRAFT_MIN);
    }

    private void sampleFloor(ServerLevel level) {
        double best = Double.NaN;
        for (double f : new double[] {0, LOOK_AHEAD}) {
            Vec3 p = position().add(offset(getYRot(), 0, 0, f));
            double y = TideOcean.floorY(level, p.x, p.z);
            if (!Double.isNaN(y) && (Double.isNaN(best) || y > best)) {
                best = y;
            }
        }
        if (!Double.isNaN(best)) {
            floorY = best;
        }
    }

    private void turnAway(ServerLevel level) {
        blockedCooldown = 100;
        Vec3 p = TideOcean.findOceanPoint(level, random, getX(), getZ(), 80, 240, 6, 24);
        if (p != null && !inFront(p, 60)) {
            targetX = p.x;
            targetZ = p.z;
        } else {
            // 海が見つからない (読み込まれていない) ときは、来た方へ引き返す
            Vec3 back = position().add(offset(getYRot() + 120f, 0, 0, 200));
            targetX = back.x;
            targetZ = back.z;
        }
        hasTarget = true;
    }

    // 点が、今向いている方の ±degrees の中にあるか
    private boolean inFront(Vec3 p, float degrees) {
        float to = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90f;
        return Math.abs(Mth.wrapDegrees(to - getYRot())) < degrees;
    }

    // 目的地: 640 ブロック以内の人から 160〜320 ブロック離れた海。近くに人がいなければ、海の中で 300 ブロック先
    private void pickCruiseTarget(ServerLevel level) {
        List<ServerPlayer> near = level.players().stream()
                .filter(p -> TitanKeeper.eligible(p) && horizontalDistanceTo(p.getX(), p.getZ()) < TitanKeeper.NEAR).toList();
        Vec3 p = null;
        if (!near.isEmpty()) {
            ServerPlayer player = near.get(random.nextInt(near.size()));
            p = TideOcean.findOceanPoint(level, random, player.getX(), player.getZ(), 160, 320, 6, 16);
        }
        if (p == null) {
            p = TideOcean.findOceanPoint(level, random, getX(), getZ(), 200, 300, 6, 12);
        }
        if (p == null) {
            // 読み込まれていない先は調べられない。今の向きの 300 ブロック先 (歩きながら前方を確かめる)
            p = position().add(offset(getYRot() + (random.nextFloat() - 0.5f) * 60f, 0, 0, 300));
        }
        targetX = p.x;
        targetZ = p.z;
        hasTarget = true;
    }

    // 目標へ向きを少しずつ変えながら進み、高さを合わせる。目標が横や後ろにあるときは、ゆっくり進みながら向きを変える
    private void steer(double tx, double tz, double ty, float speed, double rise) {
        double dx = tx - getX();
        double dz = tz - getZ();
        float desired = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(getYRot(), desired, TURN_RATE);
        setYRot(yaw);
        double distance = Math.sqrt(dx * dx + dz * dz);
        double forward = Math.min(speed, distance * 0.02);
        if (Math.abs(Mth.wrapDegrees(desired - yaw)) > 60f) {
            forward *= 0.25;
        }
        double vx = -Mth.sin(yaw * Mth.DEG_TO_RAD) * forward;
        double vz = Mth.cos(yaw * Mth.DEG_TO_RAD) * forward;
        Vec3 motion = getDeltaMovement().lerp(new Vec3(vx, 0, vz), 0.05);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        rise(ty, rise);
    }

    private void rise(double ty, double rate) {
        double dy = Mth.clamp(ty - getY(), -rate, rate);
        if (Math.abs(dy) > 1e-4) {
            setPos(getX(), getY() + dy, getZ());
        }
    }

    private double horizontalDistanceTo(double x, double z) {
        return Math.hypot(x - getX(), z - getZ());
    }

    // テスト用: このプレイヤーの近くの浅瀬へ寄せ、休ませる (刻を待たない)。休む場所がなければ false
    public boolean comeRestNear(ServerPlayer player) {
        Vec3 spot = TideOcean.findRestSpot((ServerLevel) level(), player.getX(), player.getZ());
        if (spot == null) {
            return false;
        }
        beginApproach((ServerLevel) level(), spot, FORCED_REST);
        return true;
    }

    // テスト用: その場ですぐに休む
    public void restHere() {
        restX = getX();
        restZ = getZ();
        restYaw = getYRot();
        forcedTicks = FORCED_REST;
        setPos(getX(), level().getSeaLevel() - REST_DRAFT, getZ());
        startRest((ServerLevel) level());
    }

    // 見守り (TideKeeper) が覚えておく状態。呼び直したときに新しい個体へ引き継ぐ
    public CompoundTag saveState() {
        CompoundTag tag = new CompoundTag();
        addAdditionalSaveData(tag);
        return tag;
    }

    // 呼び直した個体に、前の個体の状態を引き継ぐ。場所が変わるので巡航から始める。最後に休んだ場所と、寄った刻は残す
    public void restoreState(CompoundTag tag) {
        readAdditionalSaveData(tag);
        setMode(Mode.CRUISE);
        hasTarget = false;
        forcedTicks = 0;
        approachTicks = 0;
    }

    // 巨神は傷つかない
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

    // 見た目は当たり判定よりずっと大きいので、画面外判定を広げる。
    // モデルは横 ±76、前 87〜後ろ 62 ブロック、底 (位置の y) から塔の上 60 ブロックまで
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(92.0, 0.0, 92.0).expandTowards(0, 60.0, 0);
    }

    // 光を測る所。体の中心は水の底や海底の中にあるので (岩鯨は丘の中で真っ黒になった)、甲羅の頂の上 (海面より上) で測る
    @Override
    public Vec3 getLightProbePosition(float partialTicks) {
        Vec3 p = getPosition(partialTicks);
        return new Vec3(p.x, Math.max(p.y + DOME_TOP + 2.0, level().getSeaLevel() + 1.5), p.z);
    }

    // 位置の補間。サーバーは 2 tick ごとに位置を送るので、それに合わせた steps で滑らかにつなぐ (岩鯨と同じ)
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

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        mode = Mode.values()[Mth.clamp(tag.getInt("Mode"), 0, Mode.values().length - 1)];
        entityData.set(MODE, (byte) mode.ordinal());
        targetX = tag.getDouble("TargetX");
        targetZ = tag.getDouble("TargetZ");
        hasTarget = tag.getBoolean("HasTarget");
        restX = tag.getDouble("RestX");
        restZ = tag.getDouble("RestZ");
        restYaw = tag.getFloat("RestYaw");
        approachTicks = tag.getInt("ApproachTicks");
        forcedTicks = tag.getInt("ForcedTicks");
        lastWindow = tag.contains("LastWindow") ? tag.getLong("LastWindow") : Long.MIN_VALUE;
        hasLastRest = tag.contains("LastRestX");
        lastRestX = tag.getDouble("LastRestX");
        lastRestZ = tag.getDouble("LastRestZ");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Mode", mode.ordinal());
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetZ", targetZ);
        tag.putBoolean("HasTarget", hasTarget);
        tag.putDouble("RestX", restX);
        tag.putDouble("RestZ", restZ);
        tag.putFloat("RestYaw", restYaw);
        tag.putInt("ApproachTicks", approachTicks);
        tag.putInt("ForcedTicks", forcedTicks);
        tag.putLong("LastWindow", lastWindow);
        if (hasLastRest) {
            tag.putDouble("LastRestX", lastRestX);
            tag.putDouble("LastRestZ", lastRestZ);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 20, state -> state.setAndContinue(isResting() ? REST_ANIM : WALK)));
        controllers.add(new AnimationController<>(this, "head", 20, state -> state.setAndContinue(entityData.get(LOOK) ? LOOK_UP : HEAD_IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
