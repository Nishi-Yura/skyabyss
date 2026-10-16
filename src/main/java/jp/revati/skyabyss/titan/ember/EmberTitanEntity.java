package jp.revati.skyabyss.titan.ember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.titan.TitanKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 第四の巨神「余燼の巨神」。地の中を泳ぐ、山のような火蜥蜴 (13 の 2章)。
// - 潜行 (昼): 土の下を毎秒 1.2 ブロックで進み、背の火山 (尾根) の上だけを地面から 6〜10 ブロック出す。ブロックは壊さず置かない
//   (体はエンティティの描画だけで、地面に埋まった部分は地形に隠れて見えない)。山の下はくぐる。海と川の下には入らない
// - 夜の眠り: 日没の 1 分前から、640 ブロック以内の人 (温石で頼んだ人が先) の近くに眠る場所を探し、地の中を寄っていき、
//   12500 から 30 秒かけて地上へ浮かぶ。顎を地面に下ろして 23000 まで眠り、10 秒かけて沈む
// - 息 (18 秒): 吸う 8 秒・止める 2 秒・吐く 6 秒・間 2 秒。吸うと止めるのあいだだけ、熱の口 (EmberMouthEntity) から背中へ渡れる
// 当たり判定は小さく、見た目は描画側で 8 倍にする (岩鯨・潮殻と同じ。巨大なモデルには当たり判定を付けない)。
// 位置の y はモデルの底 (geo の y=0)。geo の 1 単位は 0.5 ブロック。頭は前 (geo の -Z)
public class EmberTitanEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(EmberTitanEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> BREATH = SynchedEntityData.defineId(EmberTitanEntity.class, EntityDataSerializers.BYTE);

    private static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("animation.ember_titan.swim");
    private static final RawAnimation SLEEP_ANIM = RawAnimation.begin().thenLoop("animation.ember_titan.sleep");
    private static final RawAnimation BREATH_IN_ANIM = RawAnimation.begin().thenPlayAndHold("animation.ember_titan.breath_in");
    private static final RawAnimation BREATH_OUT_ANIM = RawAnimation.begin().thenPlayAndHold("animation.ember_titan.breath_out");
    private static final RawAnimation SMOKE_ANIM = RawAnimation.begin().thenLoop("animation.ember_titan.smoke");

    // ---- 体の寸法 (ブロック。geo の単位の半分)。横は左が + (潮殻の offset と同じ回し方)
    // 鼻先 (geo z -206) は中心の前 103、尾の先 (geo z 248) は後ろ 124。脚を入れた幅は ±60
    public static final double SNOUT_FORWARD = 103.0;
    public static final double TAIL_BACK = 124.0;
    // 舌の上面 (geo y 6)。眠るときは、口の前の地面の高さに舌の上面を合わせる
    public static final double TONGUE_UP = 3.0;
    // 熱の口 (舌の上、幅 5・高さ 4・奥行き 4): 前へ 81〜85 (geo z -170〜-162)、横 ±2.5、舌の上から 4
    public static final double MOUTH_FORWARD = 83.0;
    public static final double MOUTH_DEPTH = 4.0;
    public static final double MOUTH_WIDTH = 5.0;
    public static final double MOUTH_HEIGHT = 4.0;
    // 背中から戻るとき: 口の前 6 ブロックの地面 (2.5節)
    public static final double EXIT_FORWARD = SNOUT_FORWARD + 6.0;
    // 息の扇: 口から 16 ブロック、幅 60 度 (頂点は口の中ほど。口の中にいる人も押し出す)
    static final double FAN_APEX = 92.0;
    static final double FAN_REACH = SNOUT_FORWARD + 16.0;
    static final double FAN_HALF = Math.toRadians(30);
    // 背の火山 (尾根): 中心の前 20 (geo z -40)、火口の縁は底から 68 (geo y 136)
    public static final double RIDGE_FORWARD = 20.0;
    public static final double RIDGE_TOP = 68.0;
    // 潜るとき: 体の底を「前方の地表 − 沈み」にする。火口の縁が地面から約 8 ブロック出る (2.2節の 6〜10)
    public static final double BURROW_SINK = 60.0;
    // 地表を見る所: 火山の 24 ブロック前
    static final double LOOK_AHEAD = RIDGE_FORWARD + 24.0;
    // 水を避けるために見る所: 火山の前 30・60
    static final double[] WATER_LOOK = {RIDGE_FORWARD + 30.0, RIDGE_FORWARD + 60.0};

    // ---- 動き
    static final float SWIM_SPEED = 0.06f;
    static final float APPROACH_SPEED = 0.3f;
    private static final float TURN_RATE = 0.3f;
    // 高さの変化は毎秒 0.5 ブロックまで
    private static final double SWIM_RISE = 0.025;
    static final int RISE_TICKS = 600;
    static final int SINK_TICKS = 200;
    private static final int APPROACH_GIVE_UP = 6000;
    // コマンドで眠らせたときの長さ (3 分)
    static final int FORCED_SLEEP = 3600;

    // ---- 息 (tick)
    public static final int BREATH_IN = 160;
    public static final int BREATH_HOLD = 40;
    public static final int BREATH_OUT = 120;
    public static final int BREATH_PAUSE = 40;
    public static final int BREATH_CYCLE = BREATH_IN + BREATH_HOLD + BREATH_OUT + BREATH_PAUSE;
    static final double INHALE_PULL = 0.02;
    static final double EXHALE_PUSH = 0.12;

    public enum Mode { SWIM, APPROACH, RISE, SLEEP, SINK }

    public enum Breath { IN, HOLD, OUT, PAUSE }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Mode mode = Mode.SWIM;
    private double targetX;
    private double targetZ;
    private boolean hasTarget;
    // 眠る場所 (中心、向き、口の前の地面の高さ)
    private double restX;
    private double restZ;
    private float restYaw;
    private double restGround;
    private int approachTicks;
    private int forcedTicks;
    // 浮く・沈むの始まりの高さと経過
    private double moveFromY;
    private int moveTicks;
    private int breathTick;
    // 最後に寄る場所を決めた夜 (同じ夜で二度寄らない)
    private long lastNight = Long.MIN_VALUE;
    // 最後に眠った場所 (背中から戻るとき、眠っていなければここの口の前へ)
    private boolean hasLastSleep;
    private double lastSleepX;
    private double lastSleepZ;
    private float lastSleepYaw;
    // 温石で「そばで眠って」と頼んだ人と、その夜
    @Nullable
    private UUID requester;
    private String requesterName = "";
    private long requestNight = Long.MIN_VALUE;
    @Nullable
    private UUID mouthId;
    private int rumbleCooldown = 200;
    private int caveCooldown = 100;
    private int blockedCooldown;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    public EmberTitanEntity(EntityType<? extends EmberTitanEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, (byte) 0);
        builder.define(BREATH, (byte) Breath.PAUSE.ordinal());
    }

    // ---------------------------------------------------------------- 状態 (クライアントでも読める)

    public Mode syncedMode() {
        return Mode.values()[Mth.clamp(entityData.get(MODE), 0, Mode.values().length - 1)];
    }

    public Breath syncedBreath() {
        return Breath.values()[Mth.clamp(entityData.get(BREATH), 0, Breath.values().length - 1)];
    }

    // 眠っている (浮かんでいる途中・沈む途中は含まない)
    public boolean isSleeping() {
        return syncedMode() == Mode.SLEEP;
    }

    // 地上に姿がある (浮かぶ・眠る・沈む)
    public boolean isSurfaced() {
        Mode m = syncedMode();
        return m == Mode.RISE || m == Mode.SLEEP || m == Mode.SINK;
    }

    // 熱の口が開いている (眠っていて、吸うか止めるあいだ)
    public boolean mouthOpen() {
        Breath b = syncedBreath();
        return isSleeping() && (b == Breath.IN || b == Breath.HOLD);
    }

    public Mode mode() {
        return mode;
    }

    private void setMode(Mode m) {
        mode = m;
        entityData.set(MODE, (byte) m.ordinal());
        if (m != Mode.SLEEP) {
            entityData.set(BREATH, (byte) Breath.PAUSE.ordinal());
        }
    }

    public static Breath breathAt(int tick) {
        int t = Math.floorMod(tick, BREATH_CYCLE);
        if (t < BREATH_IN) {
            return Breath.IN;
        }
        if (t < BREATH_IN + BREATH_HOLD) {
            return Breath.HOLD;
        }
        return t < BREATH_IN + BREATH_HOLD + BREATH_OUT ? Breath.OUT : Breath.PAUSE;
    }

    // ---------------------------------------------------------------- 体の座標

    // 向き yaw の巨神の、(横, 上, 前) の点の中心からの位置。横は左が + (潮殻と同じ)
    public static Vec3 offset(float yaw, double side, double up, double forward) {
        double fx = EmberGround.forwardX(yaw);
        double fz = EmberGround.forwardZ(yaw);
        return new Vec3(fx * forward + fz * side, up, fz * forward - fx * side);
    }

    // 世界の点の、巨神から見た (横, 上, 前)
    public Vec3 local(double x, double y, double z) {
        float yaw = getYRot();
        double fx = EmberGround.forwardX(yaw);
        double fz = EmberGround.forwardZ(yaw);
        double dx = x - getX();
        double dz = z - getZ();
        return new Vec3(dx * fz - dz * fx, y - getY(), dx * fx + dz * fz);
    }

    // 熱の口の真ん中 (舌の上、判定の箱の底の中央)
    public Vec3 mouthCenter() {
        return position().add(offset(getYRot(), 0, TONGUE_UP, MOUTH_FORWARD));
    }

    // 熱の口の判定の中か (横 ±2.5、前 81〜85、舌の上から 4)
    public boolean inMouth(double x, double y, double z) {
        Vec3 l = local(x, y, z);
        return Math.abs(l.x) <= MOUTH_WIDTH / 2 && l.z >= MOUTH_FORWARD - MOUTH_DEPTH / 2 && l.z <= MOUTH_FORWARD + MOUTH_DEPTH / 2
                && l.y >= TONGUE_UP - 0.5 && l.y <= TONGUE_UP + MOUTH_HEIGHT;
    }

    // 息の扇の中か (口の前 16 ブロック、幅 60 度。口の中の奥から)
    public boolean inBreathFan(double x, double y, double z) {
        Vec3 l = local(x, y, z);
        if (l.z < MOUTH_FORWARD - MOUTH_DEPTH || l.z > FAN_REACH || l.y < TONGUE_UP - 3 || l.y > TONGUE_UP + 14) {
            return false;
        }
        double width = Math.max(MOUTH_WIDTH, (l.z - FAN_APEX) * Math.tan(FAN_HALF) + 8.0);
        return Math.abs(l.x) <= width;
    }

    // 背中から戻る所 (口の前 6 ブロック)。y は使わない
    public Vec3 exitPoint() {
        return position().add(offset(getYRot(), 0, 0, EXIT_FORWARD));
    }

    // 火山の上 (尾根の真上の点)
    public Vec3 ridgePoint() {
        return position().add(offset(getYRot(), 0, RIDGE_TOP, RIDGE_FORWARD));
    }

    // 最後に眠った場所 (中心と向き)。記録がなければ null
    @Nullable
    public Vec3 lastSleepSpot() {
        return hasLastSleep ? new Vec3(lastSleepX, lastSleepYaw, lastSleepZ) : null;
    }

    // 見守りの記録 (読み込まれていないとき) から、最後に眠った場所の口の前を引く
    @Nullable
    public static Vec3 exitFromRecord(CompoundTag state) {
        if (!state.contains("LastSleepX")) {
            return null;
        }
        float yaw = state.getFloat("LastSleepYaw");
        Vec3 o = offset(yaw, 0, 0, EXIT_FORWARD);
        return new Vec3(state.getDouble("LastSleepX") + o.x, yaw, state.getDouble("LastSleepZ") + o.z);
    }

    // ---------------------------------------------------------------- 動き

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
        switch (mode) {
            case SWIM -> tickSwim(level);
            case APPROACH -> tickApproach(level);
            case RISE -> tickRise(level);
            case SLEEP -> tickSleep(level);
            case SINK -> tickSink(level);
        }
        if (mode != Mode.SLEEP && mouthId != null && tickCount % 20 == 0) {
            removeMouth(level);
        }
        if (tickCount % 40 == 0) {
            recordFinders(level);
        }
    }

    private void tickSwim(ServerLevel level) {
        if (blockedCooldown > 0) {
            blockedCooldown--;
        }
        if (!hasTarget || horizontalDistanceTo(targetX, targetZ) < 24) {
            pickSwimTarget(level);
        }
        // 前方 (火山の前 30・60) の地表に水があれば、向きを変える (水の下で光る尾根が泳ぐと潮殻と見分けにくい)
        if (tickCount % 10 == 0 && blockedCooldown <= 0) {
            for (double d : WATER_LOOK) {
                Vec3 ahead = position().add(offset(getYRot(), 0, 0, d));
                if (EmberGround.loaded(level, ahead.x, ahead.z) && !EmberGround.isLand(level, ahead.x, ahead.z)) {
                    turnAway(level);
                    break;
                }
            }
        }
        steer(targetX, targetZ, SWIM_SPEED);
        rise(burrowHeight(level), SWIM_RISE);
        burrowSigns(level);
        if (tickCount % 200 == 0) {
            tryGather(level);
        }
    }

    // 潜っているときの体の底: 前方 24 ブロックの地表 − 沈み。読み込まれていなければ今の高さ
    double burrowHeight(ServerLevel level) {
        Vec3 ahead = position().add(offset(getYRot(), 0, 0, LOOK_AHEAD));
        double s = EmberGround.surfaceY(level, ahead.x, ahead.z);
        return Double.isNaN(s) ? getY() : s - BURROW_SINK;
    }

    // 眠る場所を選ぶ (日没の 1 分前から。見つからなければ 10 秒ごとにやり直す。同じ夜で二度は寄らない)。
    // 温石で頼んだ人がいれば、その人を先に試す
    private void tryGather(ServerLevel level) {
        long day = level.getDayTime();
        long night = EmberGround.nightId(day);
        if (!EmberGround.isGathering(day) || night == lastNight || !level.isLoaded(blockPosition())) {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (TitanKeeper.eligible(p) && horizontalDistanceTo(p.getX(), p.getZ()) < TitanKeeper.NEAR) {
                players.add(p);
            }
        }
        Collections.shuffle(players, new java.util.Random(random.nextLong()));
        ServerPlayer asked = requesterFor(level, night);
        if (asked != null) {
            players.remove(asked);
            players.add(0, asked);
        }
        for (ServerPlayer player : players) {
            EmberGround.Spot spot = EmberGround.findSleepSpot(level, player.getX(), player.getZ());
            if (spot != null) {
                lastNight = night;
                beginApproach(spot, 0);
                return;
            }
        }
    }

    @Nullable
    private ServerPlayer requesterFor(ServerLevel level, long night) {
        if (requester == null || requestNight != night) {
            return null;
        }
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(requester);
        return p != null && p.level() == level && TitanKeeper.eligible(p) ? p : null;
    }

    void beginApproach(EmberGround.Spot spot, int forced) {
        restX = spot.x();
        restZ = spot.z();
        restYaw = spot.yaw();
        restGround = spot.groundY();
        forcedTicks = forced;
        approachTicks = 0;
        setMode(Mode.APPROACH);
    }

    private void tickApproach(ServerLevel level) {
        approachTicks++;
        boolean forced = forcedTicks > 0;
        if ((!forced && !EmberGround.isGathering(level.getDayTime())) || approachTicks > APPROACH_GIVE_UP) {
            setMode(Mode.SWIM);
            hasTarget = false;
            return;
        }
        double dist = horizontalDistanceTo(restX, restZ);
        if (dist > 2.0) {
            steer(restX, restZ, APPROACH_SPEED);
            rise(burrowHeight(level), SWIM_RISE * 4);
            burrowSigns(level);
            return;
        }
        // 着いた。地の中で向きを合わせ、眠りの始まり (12500) を待って浮かぶ (コマンドのときは待たない)
        setDeltaMovement(Vec3.ZERO);
        float yaw = Mth.approachDegrees(getYRot(), restYaw, TURN_RATE * 4);
        setYRot(yaw);
        if (Math.abs(Mth.wrapDegrees(yaw - restYaw)) < 0.5f && (forced || EmberGround.isSleepTime(level.getDayTime()))) {
            startRise(level);
        }
    }

    void startRise(ServerLevel level) {
        setPos(restX, getY(), restZ);
        setYRot(restYaw);
        moveFromY = getY();
        moveTicks = 0;
        setMode(Mode.RISE);
        level.playSound(null, ridgePoint().x, restGround, ridgePoint().z, EmberTitanSetup.RUMBLE.get(), SoundSource.AMBIENT, 12f, 0.7f);
    }

    double sleepHeight() {
        return restGround - TONGUE_UP;
    }

    private void tickRise(ServerLevel level) {
        moveTicks++;
        double t = Math.min(1.0, moveTicks / (double) RISE_TICKS);
        double s = t * t * (3 - 2 * t);
        setPos(getX(), Mth.lerp(s, moveFromY, sleepHeight()), getZ());
        if (moveTicks % 4 == 0) {
            // 浮かぶあいだ、まわりに灰が舞う
            Vec3 c = position().add(offset(getYRot(), 0, 0, 10));
            for (ServerPlayer p : playersWithin(level, 160)) {
                level.sendParticles(p, ParticleTypes.WHITE_ASH, true, c.x, restGround + 4, c.z, 60, 40, 6, 60, 0.02);
                level.sendParticles(p, ParticleTypes.LARGE_SMOKE, true, c.x, restGround + 1, c.z, 6, 30, 1, 50, 0.01);
            }
        }
        if (moveTicks % 100 == 0) {
            rumble(level, 96, 1.0f, 0.6f);
        }
        if (moveTicks >= RISE_TICKS) {
            startSleep(level);
        }
    }

    private void startSleep(ServerLevel level) {
        setPos(getX(), sleepHeight(), getZ());
        setMode(Mode.SLEEP);
        breathTick = 0;
        hasLastSleep = true;
        lastSleepX = getX();
        lastSleepZ = getZ();
        lastSleepYaw = getYRot();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(getX(), player.getY(), getZ()) < 200 * 200) {
                player.sendSystemMessage(ModText.lore(EmberIds.LANG_SURFACE + "asleep"));
            }
        }
    }

    private void tickSleep(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        boolean done = forcedTicks > 0 ? --forcedTicks <= 0 : !EmberGround.isSleepTime(level.getDayTime());
        if (done) {
            // 夜明け。地の中へ沈み、潜行に戻る (背中に人がいても沈む。背中から出る人は、眠った場所の口の前へ戻る)
            forcedTicks = 0;
            moveFromY = getY();
            moveTicks = 0;
            setMode(Mode.SINK);
            removeMouth(level);
            level.playSound(null, getX(), getY() + 10, getZ(), EmberTitanSetup.RUMBLE.get(), SoundSource.AMBIENT, 12f, 0.6f);
            return;
        }
        Breath before = breathAt(breathTick);
        breathTick = (breathTick + 1) % BREATH_CYCLE;
        Breath now = breathAt(breathTick);
        entityData.set(BREATH, (byte) now.ordinal());
        Vec3 mouth = mouthCenter();
        if (now != before || breathTick == 1) {
            if (now == Breath.IN) {
                level.playSound(null, mouth.x, mouth.y, mouth.z, EmberTitanSetup.BREATH_IN.get(), SoundSource.AMBIENT, 6f, 1.0f);
            } else if (now == Breath.OUT) {
                level.playSound(null, mouth.x, mouth.y, mouth.z, EmberTitanSetup.BREATH_OUT.get(), SoundSource.AMBIENT, 6f, 1.0f);
            }
        }
        breathOnPlayers(level, now);
        if (tickCount % 20 == 0) {
            ensureMouth(level);
        }
        if (tickCount % 10 == 0) {
            sleepSigns(level, now);
        }
        if (tickCount % 40 == 0 && now == Breath.IN) {
            mouthHint(level);
        }
    }

    // 息が口の前の人に効く: 吸うあいだは口へ弱く引き (歩けば逆らえる)、吐くあいだは外へ押して 2 秒ごとに 1 秒燃やす
    private void breathOnPlayers(ServerLevel level, Breath now) {
        if (now != Breath.IN && now != Breath.OUT) {
            return;
        }
        Vec3 mouth = mouthCenter();
        float yaw = getYRot();
        double fx = EmberGround.forwardX(yaw);
        double fz = EmberGround.forwardZ(yaw);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(mouth, mouth).inflate(FAN_REACH - MOUTH_FORWARD + 4, 16, FAN_REACH - MOUTH_FORWARD + 4))) {
            if (p.isSpectator() || (p.isCreative() && p.getAbilities().flying) || !inBreathFan(p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            if (now == Breath.IN) {
                Vec3 to = mouth.subtract(p.position());
                double len = Math.hypot(to.x, to.z);
                if (len > 0.5) {
                    p.push(to.x / len * INHALE_PULL, 0, to.z / len * INHALE_PULL);
                    p.hurtMarked = true;
                }
            } else {
                p.push(fx * EXHALE_PUSH, 0.01, fz * EXHALE_PUSH);
                p.hurtMarked = true;
                if (!p.isCreative() && breathTick % 40 == 0) {
                    p.igniteForTicks(20);
                }
            }
        }
    }

    // 眠っているときのしるし: 口の粒、火口の煙、まわりの人に灰が降る (近いほど多い三段)
    private void sleepSigns(ServerLevel level, Breath now) {
        Vec3 mouth = mouthCenter();
        Vec3 snout = position().add(offset(getYRot(), 0, TONGUE_UP + 2, SNOUT_FORWARD + 14));
        Vec3 crater = ridgePoint();
        for (ServerPlayer p : level.players()) {
            double d = Math.hypot(p.getX() - getX(), p.getZ() - getZ());
            int tier = EmberGround.ashTier(d);
            if (tier == 0) {
                continue;
            }
            // 灰はその人のまわりに降る (描画距離の外でも、遠くから巨神を探す手がかりになる)
            int count = tier == 3 ? 24 : tier == 2 ? 10 : 4;
            level.sendParticles(p, ParticleTypes.WHITE_ASH, false, p.getX(), p.getY() + 6, p.getZ(), count, 10, 4, 10, 0.0);
            if (tickCount % 40 == 0) {
                PacketDistributor.sendToPlayer(p, new EmberSurfaceNetwork.AshSky(tier));
            }
            if (d < 160) {
                level.sendParticles(p, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, crater.x, crater.y, crater.z, 3, 3, 1, 3, 0.02);
                level.sendParticles(p, ParticleTypes.LAVA, true, crater.x, crater.y - 1, crater.z, 1, 3, 0, 3, 0.0);
            }
            if (d < 140) {
                if (now == Breath.IN) {
                    // 灰と火の粉が口へ吸いこまれる (速さの向きを口へ)
                    for (int i = 0; i < 6; i++) {
                        double sx = snout.x + (random.nextDouble() - 0.5) * 14;
                        double sz = snout.z + (random.nextDouble() - 0.5) * 14;
                        double sy = snout.y + random.nextDouble() * 4;
                        Vec3 v = mouth.add(0, 2, 0).subtract(sx, sy, sz).normalize();
                        ParticleOptions type = i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.WHITE_ASH;
                        level.sendParticles(p, type, true, sx, sy, sz, 0, v.x, v.y, v.z, 0.4);
                    }
                } else if (now == Breath.OUT) {
                    // 熱い風と灰が吹き出す
                    Vec3 dir = offset(getYRot(), 0, 0, 1);
                    for (int i = 0; i < 5; i++) {
                        double sx = mouth.x + (random.nextDouble() - 0.5) * 3;
                        double sz = mouth.z + (random.nextDouble() - 0.5) * 3;
                        ParticleOptions type = i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE;
                        level.sendParticles(p, type, true, sx, mouth.y + 1 + random.nextDouble() * 2, sz, 0, dir.x, 0.05, dir.z, 0.6);
                    }
                }
            }
        }
    }

    // 口の 24 ブロック以内の人に、吸うあいだだけ 2 秒ごとに「息を吸っているあいだに、口へ」(初めて会った夜だけ)
    private void mouthHint(ServerLevel level) {
        long night = EmberGround.nightId(level.getDayTime());
        Vec3 mouth = mouthCenter();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(mouth) > 24 * 24 && !inBreathFan(p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            CompoundTag data = p.getPersistentData();
            if (!data.contains(HINT_NIGHT)) {
                data.putLong(HINT_NIGHT, night);
            }
            if (data.getLong(HINT_NIGHT) == night) {
                p.displayClientMessage(ModText.hint(EmberIds.LANG_SURFACE + "mouth_hint"), true);
            }
        }
    }

    static final String HINT_NIGHT = "skyabyss_ember_hint_night";

    private void tickSink(ServerLevel level) {
        moveTicks++;
        double t = Math.min(1.0, moveTicks / (double) SINK_TICKS);
        double target = burrowHeight(level);
        setPos(getX(), Mth.lerp(t * t, moveFromY, Math.min(target, moveFromY)), getZ());
        if (moveTicks % 4 == 0) {
            for (ServerPlayer p : playersWithin(level, 128)) {
                level.sendParticles(p, ParticleTypes.WHITE_ASH, true, getX(), getY() + 30, getZ(), 30, 40, 10, 60, 0.02);
            }
        }
        if (moveTicks >= SINK_TICKS) {
            setMode(Mode.SWIM);
            hasTarget = false;
        }
    }

    // 潜っているときのしるし (2.2節): 尾根の割れ目の煙、地鳴りと画面の微振動、動物が逃げる、洞窟の壁の向こうの音
    private void burrowSigns(ServerLevel level) {
        Vec3 ridge = ridgePoint();
        if (tickCount % 10 == 0) {
            for (ServerPlayer p : playersWithin(level, 160)) {
                level.sendParticles(p, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, ridge.x, ridge.y + 1, ridge.z, 2, 2, 1, 2, 0.015);
                level.sendParticles(p, ParticleTypes.WHITE_ASH, true, ridge.x, ridge.y, ridge.z, 12, 8, 3, 8, 0.02);
                level.sendParticles(p, ParticleTypes.ASH, true, ridge.x, ridge.y - 2, ridge.z, 8, 10, 2, 10, 0.0);
            }
        }
        if (--rumbleCooldown <= 0) {
            rumbleCooldown = 200 + random.nextInt(200);
            rumble(level, 48, 0.8f, 0.4f);
        }
        if (tickCount % 40 == 0) {
            // 32 ブロック以内の動物は、尾根から離れるように逃げる
            for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(ridge.x - 32, ridge.y - 80, ridge.z - 32, ridge.x + 32, ridge.y + 16, ridge.z + 32))) {
                double dx = animal.getX() - ridge.x;
                double dz = animal.getZ() - ridge.z;
                double len = Math.max(0.1, Math.hypot(dx, dz));
                if (len < 32) {
                    animal.getNavigation().moveTo(animal.getX() + dx / len * 16, animal.getY(), animal.getZ() + dz / len * 16, 1.5);
                }
            }
        }
        if (--caveCooldown <= 0) {
            caveCooldown = 160 + random.nextInt(160);
            caveSounds(level);
        }
    }

    // 洞窟の中にいる人には、壁の向こうを大きなものが通る音 (岩をこする音と遠い鼓動)。2 章の「壁の向こうの足音」の先触れ
    private void caveSounds(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || axisDistance(p.position()) > 48) {
                continue;
            }
            double surface = EmberGround.surfaceY(level, p.getX(), p.getZ());
            BlockPos head = BlockPos.containing(p.getEyePosition());
            if (Double.isNaN(surface) || p.getY() > surface - 6 || level.canSeeSky(head)) {
                continue;
            }
            if (random.nextInt(3) == 0) {
                p.playNotifySound(EmberTitanSetup.FAR_BEAT.get(), SoundSource.AMBIENT, 0.7f, 0.9f + random.nextFloat() * 0.1f);
            } else {
                p.playNotifySound(EmberTitanSetup.WALL_SCRAPE.get(), SoundSource.AMBIENT, 0.9f, 0.85f + random.nextFloat() * 0.2f);
            }
        }
    }

    // 地鳴り: range ブロック以内 (尾根から) の人に低い音と画面の微振動
    private void rumble(ServerLevel level, double range, float volume, float shake) {
        Vec3 ridge = ridgePoint();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || Math.hypot(p.getX() - ridge.x, p.getZ() - ridge.z) > range) {
                continue;
            }
            p.playNotifySound(EmberTitanSetup.RUMBLE.get(), SoundSource.AMBIENT, volume, 0.8f + random.nextFloat() * 0.2f);
            PacketDistributor.sendToPlayer(p, new EmberSurfaceNetwork.Tremor(60, shake));
        }
    }

    // 見つけた人に伝承を記す (眠っていれば 96 ブロック、潜っていれば尾根から 48 ブロック)
    private void recordFinders(ServerLevel level) {
        Vec3 ridge = ridgePoint();
        double range = isSurfaced() ? 96 : 48;
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && Math.hypot(p.getX() - ridge.x, p.getZ() - ridge.z) < range) {
                Lore.unlock(p, EmberIds.LORE_FOUND, true);
            }
        }
    }

    private List<ServerPlayer> playersWithin(ServerLevel level, double range) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (Math.hypot(p.getX() - getX(), p.getZ() - getZ()) < range) {
                out.add(p);
            }
        }
        return out;
    }

    // 体の軸 (鼻先から尾の先までの線分) から点までの水平の距離
    double axisDistance(Vec3 p) {
        Vec3 l = local(p.x, p.y, p.z);
        double t = Mth.clamp(l.z, -TAIL_BACK, SNOUT_FORWARD);
        return Math.hypot(l.x, l.z - t);
    }

    // ---------------------------------------------------------------- 熱の口

    private void ensureMouth(ServerLevel level) {
        Entity existing = mouthId == null ? null : level.getEntity(mouthId);
        Vec3 c = mouthCenter();
        if (existing instanceof EmberMouthEntity mouth && !mouth.isRemoved()) {
            if (mouth.position().distanceToSqr(c) > 0.01) {
                mouth.moveTo(c.x, c.y, c.z, getYRot(), 0f);
            }
            return;
        }
        EmberMouthEntity mouth = EmberTitanSetup.EMBER_MOUTH.get().create(level);
        if (mouth == null) {
            return;
        }
        mouth.moveTo(c.x, c.y, c.z, getYRot(), 0f);
        mouth.bind(getUUID());
        if (level.addFreshEntity(mouth)) {
            mouthId = mouth.getUUID();
        }
    }

    private void removeMouth(ServerLevel level) {
        if (mouthId != null && level.getEntity(mouthId) instanceof EmberMouthEntity mouth) {
            mouth.discard();
        }
        mouthId = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel level) {
            removeMouth(level);
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------- 温石

    public enum RequestResult { OK, NOT_NOW, TAKEN, NO_SPOT }

    // 温石を握った人の頼み: この夜は自分のそばで眠る。一つの夜に一人だけ (2.6節)
    public RequestResult request(ServerPlayer player) {
        ServerLevel level = (ServerLevel) level();
        long day = level.getDayTime();
        if (!EmberGround.isRequestTime(day) || isSurfaced()) {
            return RequestResult.NOT_NOW;
        }
        long night = EmberGround.nightId(day);
        if (requestNight == night && requester != null && !requester.equals(player.getUUID())) {
            return RequestResult.TAKEN;
        }
        EmberGround.Spot spot = EmberGround.findSleepSpot(level, player.getX(), player.getZ());
        if (spot == null) {
            return RequestResult.NO_SPOT;
        }
        requester = player.getUUID();
        requesterName = player.getGameProfile().getName();
        requestNight = night;
        lastNight = night;
        beginApproach(spot, 0);
        return RequestResult.OK;
    }

    // 今夜、温石で頼んだ人の名前 (いなければ空)
    public String requesterTonight() {
        return requestNight == EmberGround.nightId(level().getDayTime()) ? requesterName : "";
    }

    // ---------------------------------------------------------------- 潜行の目的地

    private void turnAway(ServerLevel level) {
        blockedCooldown = 100;
        Vec3 p = EmberGround.findLandPoint(level, random, getX(), getZ(), 80, 240, 24);
        if (p != null && !inFront(p, 60)) {
            targetX = p.x;
            targetZ = p.z;
        } else {
            // 陸が見つからない (読み込まれていない) ときは、来た方へ引き返す
            Vec3 back = position().add(offset(getYRot() + 120f, 0, 0, 200));
            targetX = back.x;
            targetZ = back.z;
        }
        hasTarget = true;
    }

    private boolean inFront(Vec3 p, float degrees) {
        float to = EmberGround.yawOf(p.x - getX(), p.z - getZ());
        return Math.abs(Mth.wrapDegrees(to - getYRot())) < degrees;
    }

    // 目的地: 640 ブロック以内の人から 160〜320 ブロック離れた陸。近くに人がいなければ、陸の中で 300 ブロック先
    private void pickSwimTarget(ServerLevel level) {
        List<ServerPlayer> near = level.players().stream()
                .filter(p -> TitanKeeper.eligible(p) && horizontalDistanceTo(p.getX(), p.getZ()) < TitanKeeper.NEAR).toList();
        Vec3 p = null;
        if (!near.isEmpty()) {
            ServerPlayer player = near.get(random.nextInt(near.size()));
            p = EmberGround.findLandPoint(level, random, player.getX(), player.getZ(), 160, 320, 16);
        }
        if (p == null) {
            p = EmberGround.findLandPoint(level, random, getX(), getZ(), 260, 320, 12);
        }
        if (p == null) {
            p = position().add(offset(getYRot() + (random.nextFloat() - 0.5f) * 60f, 0, 0, 300));
        }
        targetX = p.x;
        targetZ = p.z;
        hasTarget = true;
    }

    private void steer(double tx, double tz, float speed) {
        double dx = tx - getX();
        double dz = tz - getZ();
        float desired = EmberGround.yawOf(dx, dz);
        float yaw = Mth.approachDegrees(getYRot(), desired, TURN_RATE);
        setYRot(yaw);
        double distance = Math.sqrt(dx * dx + dz * dz);
        double forward = Math.min(speed, distance * 0.02);
        if (Math.abs(Mth.wrapDegrees(desired - yaw)) > 60f) {
            forward *= 0.25;
        }
        Vec3 motion = getDeltaMovement().lerp(new Vec3(EmberGround.forwardX(yaw) * forward, 0, EmberGround.forwardZ(yaw) * forward), 0.05);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
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

    // ---------------------------------------------------------------- テスト用

    // このプレイヤーの近くで眠らせる (夜を待たない)。眠る場所の地の中へ移ってから浮かぶ。眠る場所がなければ false
    public boolean comeSleepNear(ServerPlayer player) {
        ServerLevel level = (ServerLevel) level();
        EmberGround.Spot spot = EmberGround.findSleepSpot(level, player.getX(), player.getZ());
        if (spot == null) {
            return false;
        }
        beginApproach(spot, FORCED_SLEEP);
        removeMouth(level);
        setPos(spot.x(), spot.groundY() - BURROW_SINK, spot.z());
        setYRot(spot.yaw());
        startRise(level);
        return true;
    }

    // その場ですぐに眠る (口の前の地面の高さに合わせる)
    public void sleepHere() {
        ServerLevel level = (ServerLevel) level();
        Vec3 snout = position().add(offset(getYRot(), 0, 0, SNOUT_FORWARD));
        double ground = EmberGround.surfaceY(level, snout.x, snout.z);
        restX = getX();
        restZ = getZ();
        restYaw = getYRot();
        restGround = Double.isNaN(ground) ? getY() + TONGUE_UP : ground;
        forcedTicks = FORCED_SLEEP;
        startSleep(level);
    }

    // 自己テスト用: 息を進める
    void setBreathTick(int tick) {
        breathTick = Math.floorMod(tick, BREATH_CYCLE);
        entityData.set(BREATH, (byte) breathAt(breathTick).ordinal());
    }

    int forcedTicks() {
        return forcedTicks;
    }

    // ---------------------------------------------------------------- 見守り

    public CompoundTag saveState() {
        CompoundTag tag = new CompoundTag();
        addAdditionalSaveData(tag);
        return tag;
    }

    // 呼び直した個体に引き継ぐ。場所が変わるので潜行から始める。最後に眠った場所と、寄った夜・温石の頼みは残す
    public void restoreState(CompoundTag tag) {
        readAdditionalSaveData(tag);
        setMode(Mode.SWIM);
        hasTarget = false;
        forcedTicks = 0;
        approachTicks = 0;
    }

    // ---------------------------------------------------------------- エンティティの決まり

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

    // 見た目は当たり判定よりずっと大きい。モデルは横 ±60、前 103〜後ろ 124 ブロック、底から煙の上まで 92 ブロック
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(128.0, 0.0, 128.0).expandTowards(0, 94.0, 0);
    }

    // 光を測る所。体の中心は地の中にあるので (岩鯨は丘の中で真っ黒になった)、火山の上で測る
    @Override
    public Vec3 getLightProbePosition(float partialTicks) {
        Vec3 p = getPosition(partialTicks);
        Vec3 o = offset(getYRot(), 0, RIDGE_TOP + 3.0, RIDGE_FORWARD);
        return p.add(o);
    }

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
        restGround = tag.getDouble("RestGround");
        approachTicks = tag.getInt("ApproachTicks");
        forcedTicks = tag.getInt("ForcedTicks");
        moveFromY = tag.getDouble("MoveFromY");
        moveTicks = tag.getInt("MoveTicks");
        breathTick = tag.getInt("BreathTick");
        lastNight = tag.contains("LastNight") ? tag.getLong("LastNight") : Long.MIN_VALUE;
        hasLastSleep = tag.contains("LastSleepX");
        lastSleepX = tag.getDouble("LastSleepX");
        lastSleepZ = tag.getDouble("LastSleepZ");
        lastSleepYaw = tag.getFloat("LastSleepYaw");
        requester = tag.hasUUID("Requester") ? tag.getUUID("Requester") : null;
        requesterName = tag.getString("RequesterName");
        requestNight = tag.contains("RequestNight") ? tag.getLong("RequestNight") : Long.MIN_VALUE;
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
        tag.putDouble("RestGround", restGround);
        tag.putInt("ApproachTicks", approachTicks);
        tag.putInt("ForcedTicks", forcedTicks);
        tag.putDouble("MoveFromY", moveFromY);
        tag.putInt("MoveTicks", moveTicks);
        tag.putInt("BreathTick", breathTick);
        tag.putLong("LastNight", lastNight);
        if (hasLastSleep) {
            tag.putDouble("LastSleepX", lastSleepX);
            tag.putDouble("LastSleepZ", lastSleepZ);
            tag.putFloat("LastSleepYaw", lastSleepYaw);
        }
        if (requester != null) {
            tag.putUUID("Requester", requester);
        }
        tag.putString("RequesterName", requesterName);
        tag.putLong("RequestNight", requestNight);
    }

    // ---------------------------------------------------------------- アニメ: 体 (泳ぐ・眠る)、息 (口と頭)、煙 (いつも)
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 20, state -> state.setAndContinue(isSurfaced() ? SLEEP_ANIM : SWIM_ANIM)));
        controllers.add(new AnimationController<>(this, "breath", 10, state -> {
            if (!isSleeping()) {
                return state.setAndContinue(BREATH_OUT_ANIM);
            }
            Breath b = syncedBreath();
            return state.setAndContinue(b == Breath.IN || b == Breath.HOLD ? BREATH_IN_ANIM : BREATH_OUT_ANIM);
        }));
        controllers.add(new AnimationController<>(this, "smoke", 0, state -> state.setAndContinue(SMOKE_ANIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
