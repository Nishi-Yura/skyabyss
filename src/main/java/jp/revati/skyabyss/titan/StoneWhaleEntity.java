package jp.revati.skyabyss.titan;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModSounds;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 第一の巨神「岩鯨」。背中に古代の遺跡を乗せ、雲の高さをゆっくり泳ぐ。
// ときどきプレイヤーの近くまで降りてきて、背中の尾の側にある錨石から脇腹ごしに鎖を地上へ垂らす。
// 鎖を右クリックすると、巻き上げられて背中へ引き上げられる。
public class StoneWhaleEntity extends Entity implements GeoEntity, TravelTitan, jp.revati.skyabyss.titan.end.DormantTitan, jp.revati.skyabyss.titan.solid.SolidTitan {
    private static final EntityDataAccessor<Float> CHAIN = SynchedEntityData.defineId(StoneWhaleEntity.class, EntityDataSerializers.FLOAT);
    // 鎖が頭の乗り場から垂れているか (巨神での移動。titan.travel)。false なら背中の錨石から垂れる
    private static final EntityDataAccessor<Boolean> HEAD_CHAIN = SynchedEntityData.defineId(StoneWhaleEntity.class, EntityDataSerializers.BOOLEAN);
    // 眠りの姿 (TitanDormancy)。起きていれば -1、眠って沈みはじめてからは tick (600 で止まる)
    private static final EntityDataAccessor<Integer> REST = SynchedEntityData.defineId(StoneWhaleEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.stone_whale.swim");

    // 鎖の付け根まわり (採用案 B のモデルに合わせる。geo の 1 単位は 0.5 ブロック。ゲームでは geo の +X が左)
    // 錨石 (geo の chain 骨、左のわき腹 x 19.5〜22.5、y 37〜41、z 60〜62 単位) の中心は、
    //   後ろ 61 単位 = 30.5 ブロック、左へ 21 単位 = 10.5 ブロック、上へ 39 単位 = 19.5 ブロック
    // 体の左わき腹は z=60 付近で x=19.5 単位 (横 9.75 ブロック)。鎖はその外側 (中心 21 単位) から真下へ垂れる
    // 鎖の先頭 (付け根): 錨石の真下、model の鎖の 3 節 (y 28〜36 単位) の下端 y=28 単位 = 14 ブロック
    private static final double CHAIN_BACK = 30.5;
    private static final double ANCHOR_SIDE = 10.5;
    public static final double ANCHOR_UP = 19.5;
    private static final double CHAIN_SIDE = 10.5;
    public static final double CHAIN_UP = 14.0;
    // 鎖の先は地面の何ブロック上で止まるか (REST の鎖の伸び)
    private static final double CHAIN_END_HEIGHT = 2.5;
    // 鎖の伸び縮みの速さ (ブロック/tick)。地面の高さが変わっても急に跳ねないようにする
    private static final float CHAIN_GROW = 0.6f;
    private static final float CHAIN_SHRINK = 1.2f;
    private static final float CRUISE_SPEED = 0.2f;
    private static final float APPROACH_SPEED = 0.12f;
    private static final float TURN_RATE = 0.5f;
    private static final double CRUISE_CLEARANCE = 75.0;
    private static final double REST_CLEARANCE = 34.0;
    private static final int REST_DURATION = TitanStay.TICKS;
    private final TitanStay.Warner stayWarner = new TitanStay.Warner();
    // 降下の途中で目的地に着けないまま、これより長くたったらあきらめる
    private static final int APPROACH_GIVE_UP = 2400;
    // 降下の条件を満たす人 (Ch1Descent) を探す範囲。見守り (TitanKeeper.NEAR = 640) が岩鯨を人の 640 ブロック以内に保つので、その外までは要らない。
    // 以前は 320 で、岩鯨が 320〜640 ブロック先を巡っている間は、条件が整っても降りなかった (first_hour_log B-5)
    public static final double DESCENT_RANGE = 700;
    // 条件を満たす人がいないあいだ、降下を調べ直す間隔 (tick)。以前は 600〜1200 (30〜60 秒) だった
    public static final int DESCENT_RECHECK = 100;
    // 遠い人のもとへ向かうときの速さ: 距離に比例して速く (遠くからは巡航の 2 倍強)、近づくと APPROACH_SPEED まで落とす
    static final float HOMING_MAX_SPEED = 0.45f;
    // 停泊のあと、次に降りてよくなるまでの待ち (tick)。以前は 6000〜9000 (5〜7.5 分)
    static final int STAY_COOLDOWN_MIN = 3600;
    static final int STAY_COOLDOWN_SPREAD = 1200;

    private enum Mode { CRUISE, APPROACH, REST }

    // ---- 巨神での移動 (titan.travel) の乗り場。頭の上の平らな所 (geo の z -76〜-116 単位、高さ 64 単位前後)
    // 座席 (横, 上, 前)。横は左が +。高さは低ポリ案 C のメッシュの頭の上面から測った (tools/titan_align_check.py。旧キューブより 0.2〜0.8 低い)。頭の付け根 (前 30 ブロック) から 12〜16 ブロック先なので、泳ぎの揺れは 1 ブロック未満
    private static final double[][] SEATS = {
            {-1.5, 32.0, 46.0}, {1.5, 32.0, 46.0}, {-1.5, 32.0, 42.0}, {1.5, 32.0, 42.0},
            {-4.5, 31.2, 46.0}, {4.5, 31.2, 46.0}, {-4.5, 31.2, 42.0}, {4.5, 31.2, 42.0}};
    // 乗り場の鎖: 頭の上の留め具 → 頭の丸みに沿った曲がり目 → 左の頬の外の垂れ口 (横, 上)。前へ 44 ブロックの所
    private static final double HEAD_CHAIN_FORWARD = 44.0;
    private static final double[][] HEAD_CHAIN_PATH = {{6.0, 31.6}, {13.0, 25.2}, {14.5, 22.5}};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Mode mode = Mode.CRUISE;
    private double targetX;
    private double targetZ;
    private boolean hasTarget;
    private int restCooldown = 2400;
    private int restTicks;
    private int approachTicks;
    private double approachAngle;
    private int songCooldown = 200;
    // 章の出来事の間、鎖を垂らさず歌わない残り tick (保存しない。読み込み直したら普通に降ろす)
    private int hold;
    private double groundY = 64;
    // 旅の操縦中 (保存しない。読み込み直したら自分で泳ぐ)
    private boolean voyage;
    private double voyageGround = Double.NaN;
    private double surroundingMax = 64;
    // 眠りの始まりで低い所にいた (停泊していた) とき、昇って去っている (沈まない)
    private boolean departing;
    // 眠っているあいだ (TitanDormancy)。沈みの目標の高さ (まだ決めていなければ NaN)
    private double sleepY = Double.NaN;
    private boolean wasDormant;
    @Nullable
    private WhaleChainEntity chainHandle;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    public StoneWhaleEntity(EntityType<? extends StoneWhaleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHAIN, 0f);
        builder.define(HEAD_CHAIN, false);
        builder.define(REST, -1);
    }

    @Override
    public int restTicks() {
        return entityData.get(REST);
    }

    // 腹から垂れている鎖の長さ (0 = たたんでいる)
    public float chainLength() {
        return this.entityData.get(CHAIN);
    }

    // 鎖の付け根 (背中の縁) の位置。向きに合わせて回す
    public Vec3 chainTop() {
        return position().add(chainTopOffset(getYRot()));
    }

    // 鎖の付け根の、岩鯨の中心からの位置 (乗り場の鎖か背中の鎖か)
    public Vec3 chainTopOffset(float yaw) {
        if (entityData.get(HEAD_CHAIN)) {
            return boardingChainOffset(yaw);
        }
        return chainTop(0, 0, 0, yaw);
    }

    // 付け根から留め具までの、たるまない鎖の折れ線 (付け根 → 留め具の順。描画用)
    public List<Vec3> chainSpanOffsets(float yaw) {
        if (entityData.get(HEAD_CHAIN)) {
            List<Vec3> points = new java.util.ArrayList<>();
            for (int i = HEAD_CHAIN_PATH.length - 1; i >= 0; i--) {
                points.add(offset(yaw, HEAD_CHAIN_PATH[i][0], HEAD_CHAIN_PATH[i][1], HEAD_CHAIN_FORWARD));
            }
            return points;
        }
        return List.of(chainTop(0, 0, 0, yaw), chainAnchor(0, 0, 0, yaw));
    }

    public boolean isHeadChain() {
        return entityData.get(HEAD_CHAIN);
    }

    // 向き yaw の岩鯨の、(横, 上, 前) の点の中心からの位置。横は左が + (chainTop と同じ回し方)
    public static Vec3 offset(float yaw, double side, double up, double forward) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(fx * forward + fz * side, up, fz * forward - fx * side);
    }

    public static Vec3 chainTop(double x, double y, double z, float yaw) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(x - fx * CHAIN_BACK + fz * CHAIN_SIDE, y + CHAIN_UP, z - fz * CHAIN_BACK - fx * CHAIN_SIDE);
    }

    // 鎖を背中に留めている所 (描画用)
    public static Vec3 chainAnchor(double x, double y, double z, float yaw) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(x - fx * CHAIN_BACK + fz * ANCHOR_SIDE, y + ANCHOR_UP, z - fz * CHAIN_BACK - fx * ANCHOR_SIDE);
    }

    public Vec3 chainBottom() {
        Vec3 top = chainTop();
        return new Vec3(top.x, top.y - chainLength(), top.z);
    }

    // 鎖が地面近くまで下りきっているか
    public boolean isChainDown() {
        return (mode == Mode.REST || voyage) && chainLength() > 4f && chainBottom().y <= groundY + CHAIN_END_HEIGHT + 0.5;
    }

    // 鎖が地上へ届いているか、クライアントでも使える形。isChainDown は mode と groundY (どちらもサーバーだけの値) を見るので、
    // クライアントでは mode が既定の CRUISE のままで、いつも偽になる (目的の帯が、鎖が降りても「鎖が降りるまで」のままだった。first_hour.txt の通しで発見)。
    // クライアントでは、同期されている鎖の長さと、鎖の下端の下の地面の高さ (読み込み済みの地形) で見る
    public boolean isChainReaching() {
        if (!level().isClientSide) {
            return isChainDown();
        }
        if (chainLength() <= 4f) {
            return false;
        }
        Vec3 bottom = chainBottom();
        double ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(bottom.x), Mth.floor(bottom.z));
        return bottom.y <= ground + CHAIN_END_HEIGHT + 0.5;
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
        if (voyage) {
            // 動きは旅の操縦 (titan.travel.Voyage) が決める。ここでは鎖だけを伸び縮みさせる
            tickVoyageChain(level);
            return;
        }
        if (tickCount % 20 == 0) {
            sampleGround(level);
        }
        if (tickDormant(level)) {
            return;
        }
        switch (mode) {
            case CRUISE -> tickCruise(level);
            case APPROACH -> tickApproach(level);
            case REST -> tickRest(level);
        }
        if (hold > 0 && songCooldown < 200) {
            // 出来事の間は歌わない
            songCooldown = 200;
        }
        if (--songCooldown <= 0) {
            songCooldown = 1600 + random.nextInt(1600);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 16f, 0.9f + random.nextFloat() * 0.15f);
        }
    }

    private void tickCruise(ServerLevel level) {
        if (!hasTarget || horizontalDistanceTo(targetX, targetZ) < 30) {
            pickCruiseTarget(level);
        }
        double altitude = Math.min(level.getMaxBuildHeight() - 40, Math.max(surroundingMax + CRUISE_CLEARANCE, 170));
        steer(targetX, targetZ, altitude, CRUISE_SPEED);
        retractChain();

        if (--restCooldown <= 0) {
            // 一章の降下の条件 (story.ch1.Ch1Descent。docs/design/21 の 3.2): 条件を満たす人 (グループの誰か) がいないあいだは、雲の下を巡って降りない
            ServerPlayer player = jp.revati.skyabyss.story.ch1.Ch1Descent.pick(level, getX(), getZ(), DESCENT_RANGE);
            if (player == null) {
                restCooldown = DESCENT_RECHECK;
                return;
            }
            // プレイヤーの少し横に降り、鎖がすぐ近くに届くようにする
            approachAngle = random.nextDouble() * Math.PI * 2;
            targetX = player.getX() + Math.cos(approachAngle) * 14;
            targetZ = player.getZ() + Math.sin(approachAngle) * 14;
            hasTarget = true;
            mode = Mode.APPROACH;
            approachTicks = 0;
        }
    }

    // 降下の途中の速さ (遠いほど速く、近づくと APPROACH_SPEED)。純粋な式 (自己テストが見る)
    public static float approachSpeed(double distance) {
        return (float) Math.min(HOMING_MAX_SPEED, Math.max(APPROACH_SPEED, distance * 0.004));
    }

    private void tickApproach(ServerLevel level) {
        // 遠いあいだは、条件を満たす人の動きを追って目標を直す (1 秒おき。人が 24 ブロック以上動いたときだけ)
        if (tickCount % 20 == 0 && horizontalDistanceTo(targetX, targetZ) > 60) {
            ServerPlayer player = called ? null : jp.revati.skyabyss.story.ch1.Ch1Descent.pick(level, getX(), getZ(), DESCENT_RANGE);
            if (player != null) {
                double nx = player.getX() + Math.cos(approachAngle) * 14;
                double nz = player.getZ() + Math.sin(approachAngle) * 14;
                if ((nx - targetX) * (nx - targetX) + (nz - targetZ) * (nz - targetZ) > 24 * 24) {
                    targetX = nx;
                    targetZ = nz;
                }
            }
        }
        steer(targetX, targetZ, surroundingMax + REST_CLEARANCE, approachSpeed(horizontalDistanceTo(targetX, targetZ)));
        retractChain();
        boolean arrived = horizontalDistanceTo(targetX, targetZ) < 6 && Math.abs(getY() - (surroundingMax + REST_CLEARANCE)) < 2;
        if (!arrived && ++approachTicks > APPROACH_GIVE_UP) {
            // 地形や高さの都合で降りきれない。近くまで来ていればそこで休み、遠ければ巡航に戻ってやり直す
            if (horizontalDistanceTo(targetX, targetZ) < 24) {
                arrived = true;
            } else {
                mode = Mode.CRUISE;
                hasTarget = false;
                called = false;
                restCooldown = DESCENT_RECHECK;
                return;
            }
        }
        if (arrived) {
            mode = Mode.REST;
            restTicks = REST_DURATION;
            // 一章の中盤の出来事 (story.ch1.WhaleHush。docs/design/02 の 2.3): 初めて降りるときだけ、空の影が通り過ぎるまで歌わず、鎖を垂らさない。
            // 呼び笛で呼ばれたときは出来事を起こさない (初めての降下の場面は、笛より前の出会いのためにとっておく)
            int held = called ? 0 : jp.revati.skyabyss.story.ch1.WhaleHush.onArrive(this, level);
            called = false;
            if (held > 0) {
                hold = held;
                restTicks += held;
            } else {
                level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.AMBIENT, 6f, 0.8f);
                level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 16f, 0.85f);
                for (ServerPlayer player : Players.snapshot(level.players())) {
                    if (player.distanceToSqr(this) < 200 * 200) {
                        Notice.narrate(player, "message.skyabyss.titan_descends");
                    }
                }
            }
        }
    }

    private void tickRest(ServerLevel level) {
        // ほとんど止まり、ゆっくり漂う
        setDeltaMovement(getDeltaMovement().scale(0.9));
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        float wanted = (float) Math.max(0, chainTop().y - groundY - CHAIN_END_HEIGHT);
        if (hold > 0) {
            // 出来事の間は鎖をたたんだまま
            hold--;
            wanted = 0;
        }
        float chain = chainLength();
        float step = chain < wanted ? Math.min(wanted - chain, CHAIN_GROW) : -Math.min(chain - wanted, CHAIN_SHRINK);
        entityData.set(CHAIN, chain + step);
        if (isChainDown()) {
            keepChainHandle(level);
            jp.revati.skyabyss.titan.end.BoardGlow.emit(level, jp.revati.skyabyss.progress.Titans.STONE_WHALE, chainGlow(), chainShape(level));
            tickStandBoard(level);
            if (tickCount % 40 == 0) {
                hintNearbyPlayers(level);
            }
        }
        stayWarner.tick(level, position(), restTicks, "message.skyabyss.stay_ending.whale");
        // 居座りの早じまい (rpg_brushup B5): 近くにも背中にも誰もいないのが 3 分続いたら去る (戻れば数え直し)
        if (stayWarner.vacantTooLong(level, position(), jp.revati.skyabyss.progress.Titans.STONE_WHALE)) {
            restTicks = 0;
        }
        if (--restTicks <= 0) {
            mode = Mode.CRUISE;
            hasTarget = false;
            restCooldown = STAY_COOLDOWN_MIN + random.nextInt(STAY_COOLDOWN_SPREAD);
            dropChainHandle();
            level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.AMBIENT, 6f, 0.7f);
        }
    }

    // 眠り (docs/design/02 の 7.2節): 心臓が止まると歌をやめ、光が消え、鎖を巻き上げて、雲の海へゆっくり沈む。目覚めると巡航に戻って歌いだす。
    // 戻り = 眠っている (通常の動きを飛ばす)
    private boolean tickDormant(ServerLevel level) {
        int age = jp.revati.skyabyss.titan.end.TitanDormancy.age(level.getServer(), jp.revati.skyabyss.progress.Titans.STONE_WHALE);
        if (age < 0) {
            if (wasDormant) {
                wasDormant = false;
                sleepY = Double.NaN;
                entityData.set(REST, -1);
                // 鼓動が戻った: すぐ巡航に戻り、少ししてから歌う
                mode = Mode.CRUISE;
                hasTarget = false;
                restCooldown = 1800 + random.nextInt(1200);
                songCooldown = 400;
                level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 16f, 0.8f);
            }
            return false;
        }
        if (!wasDormant) {
            wasDormant = true;
            dropChainHandle();
            hold = 0;
            // 沈む先: 今から 50 ブロック下。ただし周りの地形より 22 ブロックは上に残す。
            // 地上に降りて停泊 (REST・20 分の滞在 TitanStay) していたときは、眠りが勝つ: 沈む余地がほとんど無いので (停泊の高さは地形の 34 ブロック上)、
            // 動かずに地上の人の真上に居座ってしまう。そのときは巡航の高さまで昇って雲の海へ去る (sleepY は昇る先)
            double far = Math.min(level.getMaxBuildHeight() - 40, Math.max(surroundingMax + CRUISE_CLEARANCE, 170));
            if (getY() < far - 20) {
                sleepY = far;
                departing = true;
            } else {
                sleepY = Math.max(surroundingMax + 22, getY() - 50);
                departing = false;
            }
        }
        // 鎖を巻き上げ、動きを止めて、ゆっくり沈む
        retractChain();
        setDeltaMovement(Vec3.ZERO);
        if (departing && !Double.isNaN(sleepY) && getY() < sleepY) {
            // 昇って去る (秒速 5 ブロック。80 ブロックなら 16 秒。体のまわりに雲がまとわりつく)
            setPos(getX(), Math.min(sleepY, getY() + 0.25), getZ());
            if (tickCount % 3 == 0) {
                for (ServerPlayer p : Players.snapshot(level.players())) {
                    if (p.distanceToSqr(this) < 300 * 300) {
                        Vec3 c = position().add(0, -4, 0);
                        level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.CLOUD, true, c.x, c.y, c.z, 12, 36, 5, 36, 0.01);
                    }
                }
            }
        } else if (!departing && !Double.isNaN(sleepY) && getY() > sinkTarget(age)) {
            // 去りはじめたら (眠って 10 秒)、さらに深く・速く沈む
            double rate = jp.revati.skyabyss.titan.end.TitanDormancy.isDeparting(age) ? 0.07 + 0.2 * jp.revati.skyabyss.titan.end.TitanDormancy.departProgress(age) : 0.07;
            setPos(getX(), Math.max(sinkTarget(age), getY() - rate), getZ());
            if (tickCount % 3 == 0) {
                // 雲の海へ沈むように、体のまわりに雲がまとわりつく
                for (ServerPlayer p : Players.snapshot(level.players())) {
                    if (p.distanceToSqr(this) < 300 * 300) {
                        Vec3 c = position().add(0, 14, 0);
                        level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.CLOUD, true, c.x, c.y, c.z, 12, 36, 5, 36, 0.01);
                    }
                }
            }
        }
        if (tickCount % 5 == 0) {
            entityData.set(REST, Math.min(age, jp.revati.skyabyss.titan.end.TitanDormancy.SYNC_CAP));
        }
        // 眠って 40 秒: 雲の海の向こうへ去る (地上から取り除く。見守りは眠りのあいだ呼び直さない)
        if (age >= jp.revati.skyabyss.titan.end.TitanDormancy.DEPART_TICKS) {
            jp.revati.skyabyss.titan.end.TitanDeparture.finish(this, jp.revati.skyabyss.progress.Titans.STONE_WHALE);
        }
        return true;
    }

    // 沈む先 (去りはじめたら、地形より 8 ブロック上まで 30 ブロック深く)
    private double sinkTarget(int age) {
        return jp.revati.skyabyss.titan.end.TitanDormancy.isDeparting(age) ? Math.max(surroundingMax + 8, sleepY - 30) : sleepY;
    }

    // 章の出来事 (story.ch1.WhaleHush) が終わった: 鎖を降ろし、歌をまた許す
    public void releaseHold() {
        if (hold > 0 || mode == Mode.REST) {
            hold = 0;
            songCooldown = Math.max(songCooldown, 1200);
            if (level() instanceof ServerLevel level) {
                level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.AMBIENT, 6f, 0.8f);
            }
        }
    }

    public boolean isHolding() {
        return hold > 0;
    }

    // 鎖の乗り込み口の光の点 (付け根から下端まで 7 ブロックおき。下端が右クリックを受ける所)
    private List<Vec3> chainGlow() {
        Vec3 top = chainTop();
        Vec3 bottom = chainBottom();
        List<Vec3> points = new java.util.ArrayList<>();
        for (double y = bottom.y; y < top.y; y += 7.0) {
            points.add(new Vec3(top.x, y, top.z));
        }
        points.add(new Vec3(top.x, top.y, top.z));
        return points;
    }

    // 鎖の下の地面のブロックの上面の高さ (鎖の下端から下へ探す)。鎖は地面から少し浮いて終わるので、乗り口の陣はその真下の地面に描く
    private static double groundTopBelow(ServerLevel level, Vec3 from) {
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos(Mth.floor(from.x), Mth.floor(from.y), Mth.floor(from.z));
        for (int i = 0; i < 24; i++) {
            var shape = level.getBlockState(m).getCollisionShape(level, m);
            if (!shape.isEmpty()) {
                return m.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
            m.move(0, -1, 0);
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(from.x), Mth.floor(from.z));
    }

    // 乗り口の光の形: 枠の中心を鎖の下端ではなく、その真下の地面の上面に置く (クライアントが平たい陣として地面にそわせて描く)
    private jp.revati.skyabyss.titan.end.BoardGlow.Shape chainShape(ServerLevel level) {
        Vec3 top = chainTop();
        Vec3 bottom = chainBottom();
        double ground = groundTopBelow(level, bottom);
        return new jp.revati.skyabyss.titan.end.BoardGlow.Shape(new Vec3(bottom.x, ground, bottom.z), 1.6f, 2.6f, 1.6f, 0f,
                ground, Math.max(top.y, ground + jp.revati.skyabyss.titan.end.BoardGlow.MIN_BEAM), 0.9f);
    }

    // 陣の半径 (StoneWhaleEntity の乗り口。クライアントの MarkerArt.SIGIL_R の内側)
    public static final double STAND_RADIUS = 2.0;
    private final java.util.Map<java.util.UUID, Integer> standCooldown = new java.util.HashMap<>();

    // 鎖の下の陣の中に立つと乗る (右クリックでも乗れる)。乗れない・満席などの知らせは 3 秒に一度まで
    private void tickStandBoard(ServerLevel level) {
        if (tickCount % 4 != 0) {
            return;
        }
        Vec3 bottom = chainBottom();
        double ground = groundTopBelow(level, bottom);
        for (ServerPlayer player : Players.snapshot(level.players())) {
            double dx = player.getX() - bottom.x;
            double dz = player.getZ() - bottom.z;
            if (dx * dx + dz * dz > STAND_RADIUS * STAND_RADIUS || player.getY() < ground - 0.6 || player.getY() > ground + 1.2) {
                // 陣から出た: 次に入ったときは、すぐ (待たずに) 数える
                standCooldown.remove(player.getUUID());
                continue;
            }
            if (player.isSpectator() || !player.isAlive() || player.isPassenger() || !player.onGround()
                    || jp.revati.skyabyss.titan.TitanTravel.isLifting(player.getUUID())) {
                continue;
            }
            int last = standCooldown.getOrDefault(player.getUUID(), -1000);
            if (tickCount - last < 60) {
                continue;
            }
            standCooldown.put(player.getUUID(), tickCount);
            jp.revati.skyabyss.titan.TitanTravel.startLift(player, this, jp.revati.skyabyss.readiness.BoardReadiness.Mode.WALK_IN);
        }
    }

    // 鎖の根元に、右クリックを受け取る見えない当たり判定を置き続ける
    private void keepChainHandle(ServerLevel level) {
        Vec3 bottom = chainBottom();
        if (chainHandle == null || chainHandle.isRemoved()) {
            // 以前の当たり判定が残っていれば消す (右クリックが二重に届かないように)
            for (WhaleChainEntity stray : level.getEntitiesOfClass(WhaleChainEntity.class, getBoundingBox().inflate(120, 120, 120))) {
                stray.discard();
            }
            chainHandle = ModEntities.WHALE_CHAIN.get().create(level);
            if (chainHandle == null) {
                return;
            }
            chainHandle.moveTo(bottom.x, bottom.y, bottom.z);
            level.addFreshEntity(chainHandle);
        } else {
            chainHandle.moveTo(bottom.x, bottom.y, bottom.z);
        }
    }

    private void dropChainHandle() {
        if (chainHandle != null) {
            chainHandle.discard();
            chainHandle = null;
        }
    }

    private void hintNearbyPlayers(ServerLevel level) {
        Vec3 bottom = chainBottom();
        for (ServerPlayer player : Players.snapshot(level.players())) {
            if (player.distanceToSqr(bottom) < 12 * 12 && !player.isPassenger()) {
                Notice.bar(player, ModText.hint(voyage ? "message.skyabyss.travel.chain_hint" : "message.skyabyss.chain_hint"));
            }
        }
    }

    // ---------------------------------------------------------------- 巨神での移動 (TravelTitan)

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public void beginVoyage() {
        voyage = true;
        voyageGround = Double.NaN;
        mode = Mode.CRUISE;
        hasTarget = false;
        restTicks = 0;
        approachTicks = 0;
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void endVoyage(int restFor) {
        voyage = false;
        voyageGround = Double.NaN;
        ejectPassengers();
        dropChainHandle();
        entityData.set(HEAD_CHAIN, false);
        entityData.set(CHAIN, 0f);
        hasTarget = false;
        approachTicks = 0;
        if (restFor > 0) {
            // その場で休み、背中の鎖を垂らす (着いた先の人も背中へ乗れる)
            mode = Mode.REST;
            restTicks = restFor;
        } else {
            mode = Mode.CRUISE;
            restCooldown = Math.max(restCooldown, 2400);
        }
        if (level() instanceof ServerLevel level) {
            sampleGround(level);
        }
    }

    @Override
    public boolean inVoyage() {
        return voyage;
    }

    @Override
    public void setVoyageChain(double ground) {
        voyageGround = ground;
        if (!Double.isNaN(ground)) {
            groundY = ground;
        }
    }

    @Override
    public boolean isVoyageChainDown() {
        return voyage && entityData.get(HEAD_CHAIN) && isChainDown();
    }

    @Override
    public boolean isChainFolded() {
        return chainLength() <= 0.01f;
    }

    @Override
    public Vec3 boardingChainOffset(float yaw) {
        double[] hang = HEAD_CHAIN_PATH[HEAD_CHAIN_PATH.length - 1];
        return offset(yaw, hang[0], hang[1], HEAD_CHAIN_FORWARD);
    }

    // 垂れ口から曲がり目と留め具を経て、座席の手前までの道すじ (世界の座標。垂れ口 → 留め具の順)
    @Override
    public List<Vec3> boardingPath() {
        List<Vec3> points = new java.util.ArrayList<>();
        for (int i = HEAD_CHAIN_PATH.length - 1; i >= 0; i--) {
            points.add(position().add(offset(getYRot(), HEAD_CHAIN_PATH[i][0], HEAD_CHAIN_PATH[i][1] + 0.4, HEAD_CHAIN_FORWARD)));
        }
        return points;
    }

    @Override
    public int seatCount() {
        return SEATS.length;
    }

    @Override
    public Vec3 seatPosition(int index) {
        double[] s = SEATS[Mth.clamp(index, 0, SEATS.length - 1)];
        return position().add(offset(getYRot(), s[0], s[1], s[2]));
    }

    // 乗り場の鎖は、背中の鎖をたたんでから頭へ付け替える (垂れたまま頭へ飛ばないように)
    private void tickVoyageChain(ServerLevel level) {
        float chain = chainLength();
        if (!entityData.get(HEAD_CHAIN)) {
            dropChainHandle();
            if (chain > 0) {
                entityData.set(CHAIN, Math.max(0f, chain - CHAIN_SHRINK));
                return;
            }
            entityData.set(HEAD_CHAIN, true);
        }
        if (Double.isNaN(voyageGround)) {
            retractChain();
            return;
        }
        float wanted = (float) Math.max(0, chainTop().y - voyageGround - CHAIN_END_HEIGHT);
        float step = chain < wanted ? Math.min(wanted - chain, CHAIN_GROW) : -Math.min(chain - wanted, CHAIN_SHRINK);
        entityData.set(CHAIN, chain + step);
        if (isChainDown()) {
            keepChainHandle(level);
            jp.revati.skyabyss.titan.end.BoardGlow.emit(level, jp.revati.skyabyss.progress.Titans.STONE_WHALE, chainGlow(), chainShape(level));
            tickStandBoard(level);
            if (tickCount % 40 == 0) {
                hintNearbyPlayers(level);
            }
        } else {
            dropChainHandle();
        }
    }

    // 乗り場の座席。何人目かで決める (降りた人がいると詰める)
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, net.minecraft.world.entity.EntityDimensions dimensions, float partialTick) {
        int index = getPassengers().indexOf(entity);
        double[] s = SEATS[Mth.clamp(index, 0, SEATS.length - 1)];
        return offset(getYRot(), s[0], s[1], s[2]);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return voyage && getPassengers().size() < SEATS.length;
    }

    // 降りるときに岩鯨の中心の上へ飛ばさない (降ろす道すじは旅の操縦が決める)
    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        return passenger.position();
    }

    // 一人だけ乗せていても、岩鯨をそのプレイヤーの持ち物として保存しない・ログアウトで消さない
    // (バニラは「プレイヤー一人だけを乗せた乗り物」をプレイヤーと一緒に保存し、ログアウトで取り除く)
    @Override
    public boolean hasExactlyOnePlayerPassenger() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        dropChainHandle();
        super.remove(reason);
    }

    private void retractChain() {
        dropChainHandle();
        float chain = chainLength();
        if (chain > 0) {
            entityData.set(CHAIN, Math.max(0f, chain - CHAIN_SHRINK));
        }
    }

    // 目標へ向きを少しずつ変えながら進み、高さを合わせる
    private void steer(double tx, double tz, double ty, float speed) {
        double[] kept = TitanAvoid.clamp(level(), tx, tz);
        tx = kept[0];
        tz = kept[1];
        double dx = tx - getX();
        double dz = tz - getZ();
        float desired = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(getYRot(), desired, TURN_RATE);
        setYRot(yaw);
        double distance = Math.sqrt(dx * dx + dz * dz);
        double forward = Math.min(speed, distance * 0.02);
        double vx = -Mth.sin(yaw * Mth.DEG_TO_RAD) * forward;
        double vz = Mth.cos(yaw * Mth.DEG_TO_RAD) * forward;
        double vy = Mth.clamp((ty - getY()) * 0.02, -0.12, 0.12);
        Vec3 motion = getDeltaMovement().lerp(new Vec3(vx, vy, vz), 0.05);
        setDeltaMovement(motion);
        move(net.minecraft.world.entity.MoverType.SELF, motion);
    }

    private void pickCruiseTarget(ServerLevel level) {
        ServerPlayer player = randomPlayer(level, 640);
        double angle = random.nextDouble() * Math.PI * 2;
        if (player != null) {
            // プレイヤーから少し離れた所を横切るように泳ぐ
            double r = 120 + random.nextDouble() * 140;
            targetX = player.getX() + Math.cos(angle) * r;
            targetZ = player.getZ() + Math.sin(angle) * r;
        } else {
            targetX = getX() + Math.cos(angle) * 300;
            targetZ = getZ() + Math.sin(angle) * 300;
        }
        hasTarget = true;
    }

    private void sampleGround(ServerLevel level) {
        int x = Mth.floor(getX());
        int z = Mth.floor(getZ());
        Vec3 top = chainTop();
        if (level.getChunkSource().hasChunk(Mth.floor(top.x) >> 4, Mth.floor(top.z) >> 4)) {
            groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(top.x), Mth.floor(top.z));
        }
        double max = groundY;
        for (int i = -1; i <= 1; i++) {
            for (int k = -1; k <= 1; k++) {
                int sx = x + i * 40;
                int sz = z + k * 40;
                // 読み込まれていないチャンクは調べない (同期読み込みや地形生成を起こさないため)
                if (level.getChunkSource().hasChunk(sx >> 4, sz >> 4)) {
                    max = Math.max(max, level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz));
                }
            }
        }
        // 体の下の丘・木・峰に体のどこもめり込まない高さ (体積の柱ごと。休む高さ = surroundingMax + REST_CLEARANCE を下回らない)
        double need = TitanGround.requiredY(level, TitanGround.Surface.TOP, jp.revati.skyabyss.titan.solid.TitanVolume.get("stone_whale"), getX(), getZ(), getYRot(),
                TitanGround.MARGIN, 2);
        if (Double.isFinite(need)) {
            max = Math.max(max, need - REST_CLEARANCE);
        }
        surroundingMax = max;
    }

    private double horizontalDistanceTo(double x, double z) {
        return Math.hypot(x - getX(), z - getZ());
    }

    @Nullable
    private ServerPlayer nearestPlayer(ServerLevel level, double range) {
        ServerPlayer best = null;
        double bestDistance = range;
        for (ServerPlayer player : Players.snapshot(level.players())) {
            double d = horizontalDistanceTo(player.getX(), player.getZ());
            if (!player.isSpectator() && d < bestDistance) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }

    @Nullable
    private ServerPlayer randomPlayer(ServerLevel level, double range) {
        List<ServerPlayer> near = level.players().stream()
                .filter(p -> !p.isSpectator() && horizontalDistanceTo(p.getX(), p.getZ()) < range)
                .toList();
        return near.isEmpty() ? null : near.get(random.nextInt(near.size()));
    }

    // 呼ばれて降りてくる途中か (その人のそばへ向かう。ほかの人へ目標を替えず、初めての降下の場面も起こさない)
    private boolean called;

    // 呼び笛 (titan.travel.TitanCalls): その人のすぐそばへ降りて、20 分 (TitanStay) 背の鎖を垂らす。昼夜・天気・一章の降下の条件に関わらず
    public void callNear(ServerPlayer player) {
        double angle = random.nextDouble() * Math.PI * 2;
        approachAngle = angle;
        targetX = player.getX() + Math.cos(angle) * 14;
        targetZ = player.getZ() + Math.sin(angle) * 14;
        hasTarget = true;
        if (mode == Mode.REST) {
            dropChainHandle();
        }
        hold = 0;
        called = true;
        mode = Mode.APPROACH;
        approachTicks = 0;
    }

    public boolean isCalled() {
        return called;
    }

    // テスト用: すぐにこのプレイヤーのそばへ降りてくる
    public void comeRestNear(ServerPlayer player) {
        targetX = player.getX() + 10;
        targetZ = player.getZ() + 10;
        hasTarget = true;
        mode = Mode.APPROACH;
        approachTicks = 0;
    }

    // テスト用: その場ですぐに止まり、鎖を降ろして 20 分 (TitanStay) 乗せる
    public void restHere() {
        mode = Mode.REST;
        restTicks = REST_DURATION;
        hold = 0;
    }

    // 残りの滞在 tick (テスト用)
    public int stayLeft() {
        return mode == Mode.REST ? restTicks : 0;
    }

    public boolean isResting() {
        return mode == Mode.REST;
    }

    // 見守り (WhaleKeeper) が覚えておく状態。呼び直したときに新しい個体へ引き継ぐ
    public CompoundTag saveState() {
        CompoundTag tag = new CompoundTag();
        addAdditionalSaveData(tag);
        return tag;
    }

    // 呼び直した個体に、前の個体の状態を引き継ぐ。場所が変わるので、巡航から始め、鎖はたたむ。
    // 降下までの待ち時間は残すが、長すぎても短すぎてもいけないので 30 秒〜2 分に収める
    public void restoreState(CompoundTag tag) {
        readAdditionalSaveData(tag);
        mode = Mode.CRUISE;
        hasTarget = false;
        restTicks = 0;
        approachTicks = 0;
        restCooldown = Mth.clamp(restCooldown, 600, 2400);
        entityData.set(CHAIN, 0f);
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 768.0 * 768.0;
    }

    // 見た目は当たり判定よりずっと大きいので、画面外判定を広げる
    // 体に入れない (titan/solid)。体積は tools/gen_titan_volumes.py が近景のメッシュから焼く。入ってよいのは乗り場の通り道だけ
    @Override
    public String volumeName() {
        return "stone_whale";
    }

    @Override
    public java.util.List<jp.revati.skyabyss.titan.solid.SolidTitan.Corridor> corridors() {
        return CORRIDORS;
    }

    private static final java.util.List<jp.revati.skyabyss.titan.solid.SolidTitan.Corridor> CORRIDORS = java.util.List.of(
            // 背中の鎖 (後ろへ 30.5、左へ 10.5) の下から巻き上げの終わりまで
            new jp.revati.skyabyss.titan.solid.SolidTitan.Corridor(5.0, 16.0, -400.0, 18.0, -35.5, -25.5),
            // 乗り場の鎖 (頭の左の頬、前へ 44) の下から頭の上の座席まで
            new jp.revati.skyabyss.titan.solid.SolidTitan.Corridor(8.0, 20.0, -400.0, 34.0, 39.0, 49.0));

    @Override
    public AABB getBoundingBoxForCulling() {
        // モデルは体の底が付け根の上 6.75 ブロック、背の上端が 46.5 ブロック (前後は -66〜81 ブロック)。上はそこまで届くようにする
        return getBoundingBox().inflate(100.0, 50.0, 100.0).expandTowards(0, -Math.max(110.0, chainLength() - CHAIN_UP), 0);
    }

    // 位置の補間。サーバーは2tickごとに位置を送るので、それに合わせた steps で滑らかにつなぐ
    // (以前は10tickかけて追いかけていたため、描画がサーバーの位置より遅れ、鎖の先が鎖の根元とずれていた)
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // 遠くへ移った (旅の雲の中の移動・呼び直し) ときは、間を補間せずにその場へ置く
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
        targetX = tag.getDouble("TargetX");
        targetZ = tag.getDouble("TargetZ");
        hasTarget = tag.getBoolean("HasTarget");
        restCooldown = tag.getInt("RestCooldown");
        restTicks = tag.getInt("RestTicks");
        approachTicks = tag.getInt("ApproachTicks");
        entityData.set(CHAIN, tag.getFloat("Chain"));
        if (tag.contains("SleepY")) {
            sleepY = tag.getDouble("SleepY");
            wasDormant = true;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Mode", mode.ordinal());
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetZ", targetZ);
        tag.putBoolean("HasTarget", hasTarget);
        tag.putInt("RestCooldown", restCooldown);
        tag.putInt("RestTicks", restTicks);
        tag.putInt("ApproachTicks", approachTicks);
        // 旅の途中 (頭の鎖) の長さは残さない。読み込み直した岩鯨は自分で泳ぐ
        tag.putFloat("Chain", voyage || entityData.get(HEAD_CHAIN) ? 0f : chainLength());
        if (wasDormant && !Double.isNaN(sleepY)) {
            tag.putDouble("SleepY", sleepY);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "swim", 0, state -> state.setAndContinue(SWIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
