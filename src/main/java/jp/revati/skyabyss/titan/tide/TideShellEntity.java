package jp.revati.skyabyss.titan.tide;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.TitanKeeper;
import jp.revati.skyabyss.titan.TitanStay;
import jp.revati.skyabyss.titan.travel.TravelTitan;
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
// 巨神での移動 (09 の 16 章): TravelTitan を実装する。旅の間は自分の動き (巡航・干潮) を止め、動きは旅の操縦 (titan.travel.SeaVoyage) が決める。
// 乗り場は潮門の前の水 (泡の柱)、座席は甲羅の頂
public class TideShellEntity extends Entity implements GeoEntity, TravelTitan, jp.revati.skyabyss.titan.end.DormantTitan, jp.revati.skyabyss.titan.solid.SolidTitan {
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(TideShellEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> LOOK = SynchedEntityData.defineId(TideShellEntity.class, EntityDataSerializers.BOOLEAN);
    // 眠りの姿 (TitanDormancy)。起きていれば -1、眠って沈みはじめてからは tick (600 で止まる)
    private static final EntityDataAccessor<Integer> REST = SynchedEntityData.defineId(TideShellEntity.class, EntityDataSerializers.INT);

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
    // 乗り込みの「入り口」(乗り場の輪): 潮門の口は甲羅の縁の中にあって、光の標が体に隠れていた。そこで標と判定を、門の真外の水の上
    // (横 51 = 甲羅の縁の裾から 2 ブロック以上外、後ろ 32、門と同じ並び) に立てる。光の柱と床の紋様はここから海面に立ち、水平の半径 3 の輪に入ると背中へ渡る。
    // 門の口そのもの (横 29〜39) に入っても渡る (従来どおり)
    public static final double DOCK_SIDE = 51.0;
    public static final double DOCK_RADIUS = 3.0;
    // 背中から戻るときに出る所 (乗り場の輪の外の水の上。輪の縁から 6 ブロック先で、戻ってすぐ輪に入って渡り直さない)
    public static final double EXIT_SIDE = 60.0;
    // 頭の先は前へ 87 ブロック、首の付け根は前へ 50。首を上げる判定は頭の中ほど (前へ 66) から測る
    static final double HEAD_FORWARD = 66.0;
    static final double HEAD_UP = 12.0;
    // 休むときの水面は底から 11 ブロック上 (本物の亀のように、腹は水の中に沈み、潮門の口 (高さ 4.5〜15.5) は下の 6.5 が水に浸かって、上の 4.5 が開く。
    // 泳ぎでも舟でも入れる)。旧い値は 3.5 (甲羅が海に浮かぶ島に見えた)。足・ひれ (底が -1 より下の柱) は休むとき海底へ埋めてよい (REST_LEG_SKIP)。
    // 旧い説明: 潮門の敷居 geo y=9 が水面の 1 ブロック上。巡航は底から 29 ブロック上まで沈む (甲羅の頂が海から約 5 ブロック出る)
    public static final double REST_DRAFT = 11.0;
    public static final double REST_LEG_SKIP = -1.0;
    // 休むとき、腹 (底の 7 ブロック) は海底へ沈めてよい。浅い所で腹が地形に押し上げられると甲羅が海面に浮いて見える。海底に腹をうずめて休む亀にする。
    // 沈めてよいのは足・ひれを除いた腹だけで、上の体 (甲羅の縁・首・頭) は海底・岸・丘に触れない
    public static final double REST_BURY = 7.0;
    public static final double CRUISE_DRAFT_MAX = 29.0;
    // 眠るときの沈み: 甲羅の頂が海から 2 ブロックだけ出る (珊瑚の段だけが水の上に残る)
    public static final double SLEEP_DRAFT = 32.0;
    private static final double SLEEP_SINK = 0.04;
    // 去りはじめてからの沈み (眠る深さからさらに 48 ブロック、秒速 1.6 ブロック)
    private static final double DEPART_EXTRA_DRAFT = 48.0;
    private static final double DEPART_SINK = 0.08;
    // 浅い所でも、底から 22 ブロック上までしか浮かない (甲羅の頂が海から 12 ブロックまで。ゲームで見て、もっと浮くと島に見えなかった)
    public static final double CRUISE_DRAFT_MIN = 22.0;
    // 甲羅の頂 (光を測る所。低ポリ案 B の上の段の高さ。塔の高さ約 71 は測らない)
    public static final double DOME_TOP = 47.0;

    // ---- 動き
    static final float CRUISE_SPEED = 0.08f;
    static final float APPROACH_SPEED = 0.2f;
    private static final float TURN_RATE = 0.4f;
    private static final double CRUISE_RISE = 0.025;
    private static final double REST_RISE = 0.05;
    // 前方を確かめる距離。頭は中心から 87 ブロック先まで出るので、近くと遠くの 2 点を見る (24 では頭が陸に乗り上げた)
    private static final double LOOK_AHEAD = 30.0;
    private static final double LOOK_FAR = 60.0;
    // 巡航の目的地と前方に要る水深 (浅い所を歩くと体が大きく出すぎる)
    private static final int CRUISE_DEPTH = 22;
    // 巡航で、標準の高さからこれ以上の持ち上げが要る先 (岸の丘・浅瀬) へは進まず向きを変える (ブロック)
    private static final double MAX_CRUISE_LIFT = 1.5;
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
    // 潮見の貝で呼んだ人 (08 の 2.6節。一つの刻に一人)
    private java.util.UUID requester;
    private String requesterName = "";
    private long requestWindow = Long.MIN_VALUE;
    private int songCooldown = 1200;
    private int stepCooldown = 160;
    private int blockedCooldown;
    private double floorY = Double.NaN;

    // ---- 巨神での移動 (TravelTitan)
    private boolean voyage;
    // 乗り場の泡の柱 (0 = たたんだ、COLUMN_UP = 立ちきった)
    private int column;
    private boolean columnWanted;
    static final int COLUMN_UP = 20;
    // 座席 (横, 上, 前)。低ポリ案 B の甲羅は段になった島 (外の段 34、中の段 41、上の段 47 ブロック) で、中心に塔 (高さ約 71) が立つ。
    // 塔は横 -6〜+6・前 -4〜+6 なので、その前の上の段 (高さ 47、平ら) の前へ 10 と 13 に 4 人ずつ (旧: 前へ 11 と 17 の高さ 34・36)。
    // 高さは低ポリのメッシュ (lowpoly/tide_shell.json) の上面から測った (tools/titan_align_check.py)。座席は面の 0.4 上
    static final double[][] SEATS = {
            {2.0, 47.4, 10.0}, {-2.0, 47.4, 10.0}, {6.0, 47.4, 10.0}, {-6.0, 47.4, 10.0},
            {2.0, 47.4, 13.0}, {-2.0, 47.4, 13.0}, {6.0, 47.4, 13.0}, {-6.0, 47.4, 13.0}};

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
        builder.define(REST, -1);
    }

    @Override
    public int restTicks() {
        return entityData.get(REST);
    }

    // 干潮の刻で休んでいる (眠っているあいだは、潮門は出ない)
    public boolean isResting() {
        return entityData.get(MODE) == Mode.REST.ordinal() && entityData.get(REST) < 0;
    }

    public boolean isApproaching() {
        return entityData.get(MODE) == Mode.APPROACH.ordinal();
    }

    // 潮門を開けておく残り tick (テスト用)
    public int stayLeft() {
        return mode == Mode.REST ? forcedTicks : 0;
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

    // 乗り場の輪の真ん中の海面 (世界の座標)
    public Vec3 dockCenter() {
        Vec3 p = position().add(offset(getYRot(), DOCK_SIDE, 0, -GATE_BACK));
        return new Vec3(p.x, level().getSeaLevel() - 0.1, p.z);
    }

    // 乗り込める所に入っているか: 乗り場の輪 (門の真外の水の上) か、門の口そのもの
    boolean inGateMouth(Entity e) {
        Vec3 l = local(e.getX(), e.getY(), e.getZ());
        // 見える枠より少し広く (横・奥行き 1.5、上下 1)
        boolean mouth = l.x >= GATE_SIDE_MIN - 1.5 && l.x <= GATE_SIDE_MAX + 1.5 && -l.z >= GATE_BACK_MIN - 1.5 && -l.z <= GATE_BACK_MAX + 1.5
                && l.y >= GATE_UP_MIN - 1 && l.y <= GATE_UP_MAX + 1;
        // 輪: 水平の半径 DOCK_RADIUS (海面から下 2・上 12 ブロック。泳いでいる人も舟も、空を飛ぶ人も入る)
        // 高さは海面から測る (底を持ち上げて休んでいても、輪は海面にある)
        double above = e.getY() - (level().getSeaLevel() - 0.1);
        boolean dock = Math.hypot(l.x - DOCK_SIDE, -l.z - GATE_BACK) <= DOCK_RADIUS && above >= -2.0 && above <= 12.0;
        return mouth || dock;
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
        // 先の地形は 5 tick ごと (今の位置の分は、動かしたあとの rise が毎 tick 測る)
        if (tickCount % 5 == 0) {
            sampleAhead(level);
        }
        if (voyage) {
            tickVoyage(level);
            return;
        }
        if (tickDormant(level)) {
            return;
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

    private boolean wasDormant;

    // 眠り (docs/design/08): 心臓が止まると歌をやめ、光が消え、海へゆっくり沈んで、珊瑚の段だけが水の上に残る。目覚めると巡航に戻る
    // 戻り = 眠っている (通常の動きを飛ばす)
    private boolean tickDormant(ServerLevel level) {
        int age = jp.revati.skyabyss.titan.end.TitanDormancy.age(level.getServer(), jp.revati.skyabyss.progress.Titans.TIDE_SHELL);
        if (age < 0) {
            if (wasDormant) {
                wasDormant = false;
                entityData.set(REST, -1);
                setMode(Mode.CRUISE);
                hasTarget = false;
                songCooldown = 400;
                level.playSound(null, getX(), level.getSeaLevel(), getZ(), TideSetup.SONG.get(), SoundSource.AMBIENT, 16f, 0.8f);
            }
            return false;
        }
        if (!wasDormant) {
            wasDormant = true;
            forcedTicks = 0;
            setMode(Mode.REST);
            entityData.set(LOOK, false);
        }
        setDeltaMovement(Vec3.ZERO);
        // 眠って 10 秒からは、珊瑚の段も水の下へ沈みきる (海の底へ去る)。40 秒で地上から取り除く
        boolean leaving = jp.revati.skyabyss.titan.end.TitanDormancy.isDeparting(age);
        double sinkTo = level.getSeaLevel() - SLEEP_DRAFT - (leaving ? DEPART_EXTRA_DRAFT : 0);
        riseRaw(sinkTo, leaving ? DEPART_SINK : SLEEP_SINK);
        if (tickCount % 8 == 0 && getY() > sinkTo + 0.5) {
            // 沈むあいだ、甲羅のまわりの海面に泡と波紋
            for (ServerPlayer p : Players.snapshot(level.players())) {
                if (p.distanceToSqr(this) < 300 * 300) {
                    double y = level.getSeaLevel() + 0.5;
                    level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.BUBBLE_POP, true, getX(), y, getZ(), 30, 28, 0.3, 28, 0.02);
                    level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.SPLASH, true, getX(), y, getZ(), 20, 30, 0.2, 30, 0.1);
                }
            }
        }
        if (tickCount % 5 == 0) {
            entityData.set(REST, Math.min(age, jp.revati.skyabyss.titan.end.TitanDormancy.SYNC_CAP));
        }
        if (age >= jp.revati.skyabyss.titan.end.TitanDormancy.DEPART_TICKS) {
            jp.revati.skyabyss.titan.end.TitanDeparture.finish(this, jp.revati.skyabyss.progress.Titans.TIDE_SHELL);
        }
        return true;
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
            for (double d : new double[] {LOOK_AHEAD, LOOK_FAR}) {
                Vec3 ahead = position().add(offset(getYRot(), 0, 0, d));
                if (TideOcean.loaded(level, ahead.x, ahead.z) && !TideOcean.walkable(level, ahead.x, ahead.z, 6)) {
                    turnAway(level);
                    break;
                }
            }
            // 進む先の海底・岸・丘に体が触れないために、今の高さから少しでも持ち上がらねばならない (浅瀬・岸の丘が体の幅に入る) なら、持ち上がらずに向きを変える (浅瀬を避けて回る)
            if (blockedCooldown <= 0 && Double.isFinite(groundAhead) && groundAhead > getY() + MAX_CRUISE_LIFT) {
                turnAway(level);
            }
        }
        steer(targetX, targetZ, cruiseHeight(level), CRUISE_SPEED, CRUISE_RISE);
        if (--stepCooldown <= 0) {
            stepCooldown = 160 + random.nextInt(160);
            level.playSound(null, getX(), getY() + 4, getZ(), TideSetup.STEP.get(), SoundSource.AMBIENT, 5f, 0.8f + random.nextFloat() * 0.2f);
        }
        if (tickCount % 200 == 0) {
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
        for (ServerPlayer p : Players.snapshot(level.players())) {
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
        for (ServerPlayer player : Players.snapshot(level.players())) {
            if (player.distanceToSqr(restX, player.getY(), restZ) < 200 * 200) {
                Notice.narrate(player, "message.skyabyss.tide.ebb");
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
        // 潮門を開けておく長さは、地上の干潮の刻ではなく 20 分 (TitanStay)。背の中の潮 (TideClock) は別の時計で、触らない
        forcedTicks = TitanStay.TICKS;
        hasLastRest = true;
        lastRestX = getX();
        lastRestZ = getZ();
        level.playSound(null, getX(), level.getSeaLevel(), getZ(), TideSetup.SONG.get(), SoundSource.AMBIENT, 16f, 0.8f);
    }

    private final TitanStay.Warner stayWarner = new TitanStay.Warner();

    private void tickRest(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        rise(level.getSeaLevel() - REST_DRAFT, REST_RISE);
        stayWarner.tick(level, position(), forcedTicks, "message.skyabyss.stay_ending.tide");
        // 居座りの早じまい (20 分の滞在のあいだだけ): 誰もいないのが 3 分続いたら去る
        if (forcedTicks > 0 && stayWarner.vacantTooLong(level, position(), jp.revati.skyabyss.progress.Titans.TIDE_SHELL)) {
            forcedTicks = 1;
        }
        boolean done = forcedTicks > 0 ? --forcedTicks <= 0 : !TideOcean.isLowTide(level.getDayTime());
        if (done) {
            // 刻の終わり。縁を沈め、巡航に戻る (背中に人がいても戻る。背中から出る人は、休んだ場所の岸へ戻る)
            forcedTicks = 0;
            setMode(Mode.CRUISE);
            hasTarget = false;
            level.playSound(null, getX(), level.getSeaLevel(), getZ(), TideSetup.SURF.get(), SoundSource.AMBIENT, 8f, 0.8f);
            return;
        }
        {
            // 乗り場の光 (乗れる人にだけ見える。眠っているあいだは出ない)。標は門の真外の水の上の輪 (体に隠れない)。粒は輪と、門の口にも少し
            Vec3 d = dockCenter();
            Vec3 g = gateCenter();
            double surface = d.y;
            if (jp.revati.skyabyss.titan.TitanGround.enabled) {
                jp.revati.skyabyss.titan.end.BoardGlow.emit(level, jp.revati.skyabyss.progress.Titans.TIDE_SHELL, List.of(d.add(0, 1.5, 0), d.add(0, 5, 0), d.add(offset(getYRot(), -9, 0, 0)).add(0, 1.5, 0), g),
                        // 戸口の標 (昇る柱ではない。クライアントが光の門・水面の道・内向きの山形を描く。beamFrom〜To は 1 ブロックだけ)
                        new jp.revati.skyabyss.titan.end.BoardGlow.Shape(d.add(0, 2.0, 0), (float) DOCK_RADIUS, 2.0f, (float) DOCK_RADIUS, getYRot(), surface, surface + 1, 1.6f));
            } else {
                // 開発用 (/skyabyss dev titanground false): 直す前の標 (門の口に枠と柱。体に隠れる) を出して、見比べる
                jp.revati.skyabyss.titan.end.BoardGlow.emit(level, jp.revati.skyabyss.progress.Titans.TIDE_SHELL, List.of(g, g.add(0, 5, 0), g.add(0, -5, 0)),
                        new jp.revati.skyabyss.titan.end.BoardGlow.Shape(g, (float) (GATE_SIDE_MAX - GATE_SIDE_MIN) / 2f, (float) (GATE_UP_MAX - GATE_UP_MIN) / 2f,
                                (float) (GATE_BACK_MAX - GATE_BACK_MIN) / 2f, getYRot(), g.y - 6, g.y + 46, 1.6f));
            }
        }
        if (tickCount % 4 == 0) {
            Vec3 d = dockCenter();
            AABB around = new AABB(d, d).inflate(DOCK_RADIUS + 14);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, around)) {
                if (TitanKeeper.eligible(player) && inGateMouth(player)) {
                    TideTravel.boardFromGate(player, this);
                }
            }
        }
        if (tickCount % 40 == 0) {
            Vec3 dock = dockCenter();
            for (ServerPlayer player : Players.snapshot(level.players())) {
                if (player.distanceToSqr(dock) < 20 * 20) {
                    Notice.bar(player, ModText.hint("message.skyabyss.tide.gate_hint"));
                }
            }
        }
        if (tickCount % 10 == 0) {
            // 近くに人がいると、首を上げてそちらを見る
            Vec3 head = position().add(offset(getYRot(), 0, HEAD_UP, HEAD_FORWARD));
            boolean look = false;
            for (ServerPlayer player : Players.snapshot(level.players())) {
                if (!player.isSpectator() && player.distanceToSqr(head) < 56 * 56) {
                    look = true;
                    break;
                }
            }
            entityData.set(LOOK, look);
        }
    }

    // 巡航の高さ: 前方の海底に立つ高さ。ただし甲羅の頂が海から約 5 ブロック出る所より沈まず、12 ブロック出る所より浮かない
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
        Vec3 p = TideOcean.findOceanPoint(level, random, getX(), getZ(), 80, 240, CRUISE_DEPTH, 24);
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
            p = TideOcean.findOceanPoint(level, random, player.getX(), player.getZ(), 160, 320, CRUISE_DEPTH, 16);
        }
        if (p == null) {
            p = TideOcean.findOceanPoint(level, random, getX(), getZ(), 200, 300, CRUISE_DEPTH, 12);
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

    // 高さを ty へ寄せる。ただし体が海底・岸・木・丘にめり込む高さには下げない (groundNow = 今の位置で要る最低の高さ、groundAhead = 進む先まで見た最低の高さ)。
    // 先に地形が高くなるときは、ゆっくり持ち上がりながら先回りする。それでも間に合わない分は、めり込む前に押し上げる
    private void rise(double ty, double rate) {
        if (level() instanceof ServerLevel serverLevel) {
            sampleNow(serverLevel);
        }
        double want = Math.max(ty, Math.max(groundNow, groundAhead));
        double r = rate;
        if (want > getY()) {
            // 持ち上げるべき差が大きいほど、少しだけ速く (最大で秒 10 ブロック)
            r = Math.max(rate, Math.min(0.5, (want - getY()) * 0.08));
        }
        double dy = Mth.clamp(want - getY(), -rate, r);
        double ny = getY() + dy;
        if (ny < groundNow) {
            ny = groundNow;
        }
        if (Math.abs(ny - getY()) > 1e-4) {
            setPos(getX(), ny, getZ());
        }
    }

    // 地形に関わらず高さを寄せる (眠って海へ沈み去る演出だけ)
    private void riseRaw(double ty, double rate) {
        double dy = Mth.clamp(ty - getY(), -rate, rate);
        if (Math.abs(dy) > 1e-4) {
            setPos(getX(), getY() + dy, getZ());
        }
    }

    // 体の下の地形 (海底・岸・木・丘) を、体積の柱ごとに測る。5 tick ごと。今の位置の分と、進む先 6〜48 ブロックまで見た分
    private static final double[] GROUND_LOOK = {6.0, 12.0, 18.0, 24.0, 32.0, 40.0, 48.0};
    private double groundNow = Double.NEGATIVE_INFINITY;
    private double groundAhead = Double.NEGATIVE_INFINITY;

    // 進む先の地形 (巡航・接近で動いているあいだだけ。止まっているときは、前の地形に持ち上げられない。寄っていくときは、休む場所の先までは見ない)
    private void sampleAhead(ServerLevel level) {
        jp.revati.skyabyss.titan.solid.TitanVolume volume = jp.revati.skyabyss.titan.solid.TitanVolume.get(volumeName());
        groundAhead = Double.NEGATIVE_INFINITY;
        // 休む場所の手前 60 ブロックからは、足・ひれと腹を海底へ沈めてよい (REST_BURY)。先読みは、その分を数えられないので、今の位置の下限 (sampleNow) に任せる
        boolean settling = mode == Mode.APPROACH && horizontalDistanceTo(restX, restZ) < 60;
        if (mode != Mode.REST && !settling && getDeltaMovement().horizontalDistanceSqr() > 1.0e-4) {
            double cap = mode == Mode.APPROACH ? horizontalDistanceTo(restX, restZ) : Double.MAX_VALUE;
            double[] distances = java.util.Arrays.stream(GROUND_LOOK).filter(d -> d < cap).toArray();
            if (distances.length > 0) {
                groundAhead = jp.revati.skyabyss.titan.TitanGround.requiredAhead(level, jp.revati.skyabyss.titan.TitanGround.Surface.FLOOR, volume,
                        getX(), getZ(), getYRot(), distances, 1);
            }
        }
    }

    // 今の位置・向きの下限 (動かしたあとに毎 tick。前後左右 3 ブロック先の地形まで数えて、崖のへりで跳ばないよう先に持ち上がり始める)
    private void sampleNow(ServerLevel level) {
        // 休む・休む場所へ寄る最後の 60 ブロックは、足・ひれを海底に埋めてよい (腹だけが海底・岸・丘に触れない)
        boolean settling = mode == Mode.REST || (mode == Mode.APPROACH && horizontalDistanceTo(restX, restZ) < 60);
        groundNow = jp.revati.skyabyss.titan.TitanGround.requiredYPadded(level, jp.revati.skyabyss.titan.TitanGround.Surface.FLOOR,
                jp.revati.skyabyss.titan.solid.TitanVolume.get(volumeName()), getX(), getZ(), getYRot(), jp.revati.skyabyss.titan.TitanGround.MARGIN, 3.0,
                settling ? REST_LEG_SKIP : Double.NEGATIVE_INFINITY);
        if (settling && Double.isFinite(groundNow)) {
            groundNow -= REST_BURY;
        }
    }

    // 試験用: 今の位置・向きで体が地形にめり込まない最低の高さ (間引かずに測る)
    public double groundRequiredY() {
        return jp.revati.skyabyss.titan.TitanGround.requiredY((ServerLevel) level(), jp.revati.skyabyss.titan.TitanGround.Surface.FLOOR,
                jp.revati.skyabyss.titan.solid.TitanVolume.get(volumeName()), getX(), getZ(), getYRot());
    }

    private double horizontalDistanceTo(double x, double z) {
        return Math.hypot(x - getX(), z - getZ());
    }

    public enum RequestResult { OK, NOT_NOW, TAKEN, NO_SPOT }

    // 潮見の貝 (08 の 2.6節): 刻の 1 分前から刻の途中までに、自分のそばへ寄るよう頼む。一つの刻に一人 (すでに寄る場所を決めていれば NOT_NOW)
    public RequestResult request(ServerPlayer player) {
        ServerLevel level = (ServerLevel) level();
        long day = level.getDayTime();
        long window = TideOcean.windowId(day);
        if (requester != null && requestWindow == window && !requester.equals(player.getUUID())) {
            return RequestResult.TAKEN;
        }
        if (voyage || mode != Mode.CRUISE || !TideOcean.isGathering(day) || lastWindow == window) {
            return RequestResult.NOT_NOW;
        }
        Vec3 spot = TideOcean.findRestSpot(level, player.getX(), player.getZ());
        if (spot == null) {
            return RequestResult.NO_SPOT;
        }
        requester = player.getUUID();
        requesterName = player.getGameProfile().getName();
        requestWindow = window;
        lastWindow = window;
        beginApproach(level, spot, 0);
        return RequestResult.OK;
    }

    // この刻に呼んだ人の名前 (いなければ空)
    public String requesterNow() {
        return requestWindow == TideOcean.windowId(level().getDayTime()) ? requesterName : "";
    }

    // 呼び貝 (titan.travel.TitanCalls): 干満・昼夜に関わらず、その人にいちばん近い浅瀬へ寄って、20 分 (TitanStay) 潮門を開ける。
    // 遠くにいるときは、休む場所から 90〜140 ブロックの沖から歩いてくる。休む場所がなければ false
    public boolean callNear(ServerPlayer player) {
        ServerLevel level = (ServerLevel) level();
        Vec3 spot = TideOcean.findCallSpot(level, player.getX(), player.getZ());
        if (spot == null) {
            return false;
        }
        if (horizontalDistanceTo(spot.x, spot.z) > 160) {
            Vec3 from = TideOcean.findOceanPoint(level, random, spot.x, spot.z, 90, 140, 6, 24);
            Vec3 at = from != null ? from : new Vec3(spot.x + 100, 0, spot.z);
            setPos(at.x, level.getSeaLevel() - REST_DRAFT, at.z);
        }
        beginApproach(level, spot, FORCED_REST);
        return true;
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

    // 試験用: 巡航で (x, z) へ向かう
    void testCruiseTo(double x, double z) {
        setMode(Mode.CRUISE);
        targetX = x;
        targetZ = z;
        hasTarget = true;
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

    // ---------------------------------------------------------------- 巨神での移動 (TravelTitan)
    private void tickVoyage(ServerLevel level) {
        column = Mth.clamp(column + (columnWanted ? 1 : -1), 0, COLUMN_UP);
        boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0e-4;
        // 歩いている間は歩く姿、止まっている間は休む姿 (首を上げない)
        entityData.set(MODE, (byte) (moving ? Mode.CRUISE.ordinal() : Mode.REST.ordinal()));
        entityData.set(LOOK, false);
        if (moving && --stepCooldown <= 0) {
            stepCooldown = 100 + random.nextInt(80);
            level.playSound(null, getX(), level.getSeaLevel(), getZ(), TideSetup.STEP.get(), SoundSource.AMBIENT, 6f, 0.8f + random.nextFloat() * 0.2f);
        }
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public void beginVoyage() {
        voyage = true;
        columnWanted = false;
        column = 0;
        forcedTicks = 0;
        approachTicks = 0;
        hasTarget = false;
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void endVoyage(int restFor) {
        voyage = false;
        columnWanted = false;
        column = 0;
        ejectPassengers();
        setDeltaMovement(Vec3.ZERO);
        hasTarget = false;
        approachTicks = 0;
        if (restFor > 0 && level() instanceof ServerLevel level) {
            // 着いた浜の沖でしばらく休み、潮門を開けておく (その地の人も背中へ渡れる)
            restX = getX();
            restZ = getZ();
            restYaw = getYRot();
            forcedTicks = restFor;
            startRest(level);
        } else {
            setMode(Mode.CRUISE);
        }
    }

    @Override
    public boolean inVoyage() {
        return voyage;
    }

    // 乗り場の泡の柱を立てる (NaN でたたむ)。柱の位置は旅の操縦が決め、泡も旅の操縦が出す
    @Override
    public void setVoyageChain(double ground) {
        columnWanted = !Double.isNaN(ground);
    }

    @Override
    public boolean isVoyageChainDown() {
        return voyage && columnWanted && column >= COLUMN_UP;
    }

    @Override
    public boolean isChainFolded() {
        return column <= 0;
    }

    // 乗り場: 潮門の前の水の上 (背中から戻るときに出る所と同じ)
    @Override
    public Vec3 boardingChainOffset(float yaw) {
        return offset(yaw, DOCK_SIDE, 0, -GATE_BACK);
    }

    // 潮門の前 → 門の敷居 → 甲羅の肩 → 塔の横 → 塔の前の座席の手前 (世界の座標)
    @Override
    public List<Vec3> boardingPath() {
        float yaw = getYRot();
        double sea = level().getSeaLevel();
        List<Vec3> points = new ArrayList<>();
        Vec3 dock = position().add(offset(yaw, DOCK_SIDE, 0, -GATE_BACK));
        points.add(new Vec3(dock.x, sea + 0.6, dock.z));
        points.add(position().add(offset(yaw, GATE_SIDE, REST_DRAFT + 7.0, -GATE_BACK)));
        points.add(position().add(offset(yaw, 20.0, 27.0, -17.0)));
        points.add(position().add(offset(yaw, 9.0, 37.0, 0.0)));
        points.add(position().add(offset(yaw, 6.0, 36.0, 11.0)));
        return points;
    }

    @Override
    public int seatCount() {
        return SEATS.length;
    }

    @Override
    public Vec3 seatPosition(int index) {
        double[] seat = SEATS[Mth.clamp(index, 0, SEATS.length - 1)];
        return position().add(offset(getYRot(), seat[0], seat[1], seat[2]));
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, net.minecraft.world.entity.EntityDimensions dimensions, float partialTick) {
        int index = getPassengers().indexOf(entity);
        double[] seat = SEATS[Mth.clamp(index, 0, SEATS.length - 1)];
        return offset(getYRot(), seat[0], seat[1], seat[2]);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return voyage && getPassengers().size() < SEATS.length;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        return passenger.position();
    }

    // 一人だけ乗せていても、巨神をそのプレイヤーの持ち物として保存しない・ログアウトで消さない (岩鯨と同じ)
    @Override
    public boolean hasExactlyOnePlayerPassenger() {
        return false;
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
    // モデルは横 ±68、前 87.5〜後ろ 61.5 ブロック、底 (位置の y) から塔の上 73 ブロックまで (低ポリの寸法。塔が高くなったので余裕を持って 80)
    // 体に入れない (titan/solid)。体積は tools/gen_titan_volumes.py が近景のメッシュから焼く。入ってよいのは乗り場の通り道だけ
    @Override
    public String volumeName() {
        return "tide_shell";
    }

    @Override
    public java.util.List<jp.revati.skyabyss.titan.solid.SolidTitan.Corridor> corridors() {
        return CORRIDORS;
    }

    private static final java.util.List<jp.revati.skyabyss.titan.solid.SolidTitan.Corridor> CORRIDORS = java.util.List.of(
            // 潮門の口 (横 29〜39、後ろ 27.5〜36.5) とその前の水の上 (乗り場の舟寄せ)
            new jp.revati.skyabyss.titan.solid.SolidTitan.Corridor(27.0, 46.0, -5.0, 17.0, -39.0, -25.0),
            new jp.revati.skyabyss.titan.solid.SolidTitan.Corridor(38.0, 62.0, -10.0, 20.0, -44.0, -20.0));

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(92.0, 0.0, 92.0).expandTowards(0, 80.0, 0);
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
        requester = tag.hasUUID("Requester") ? tag.getUUID("Requester") : null;
        requesterName = tag.getString("RequesterName");
        requestWindow = tag.contains("RequestWindow") ? tag.getLong("RequestWindow") : Long.MIN_VALUE;
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
        if (requester != null) {
            tag.putUUID("Requester", requester);
        }
        tag.putString("RequesterName", requesterName);
        tag.putLong("RequestWindow", requestWindow);
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
