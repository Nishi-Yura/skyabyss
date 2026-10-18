package jp.revati.skyabyss.titan.storm.heart;

import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.Updraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 雷翼の心臓: 雷翼の巨神の最終ボス (12 の 5.3節)。竜骨の間の中央に宙に浮いて現れ、動かず向きだけ変える (段階 2 だけ高さを変える)。
// 体力の割合で三段階 (66% 未満で段階 2、33% 未満で段階 3)。変わり目は 3 秒の咆哮で、その間は傷を受けず輪が押し返す。
// 三つの段階は、三つの試練で覚えたことを一つずつ使う:
//  段階 1 畳んだ翼 (東 = 避雷の柱): 招雷 (輪が柱の近くなら柱へ落ちて柱が満ちる。4 本満ちると翼が裂けて 6 秒落ちる・受ける傷 1.5 倍) /
//                                  風の刃 (頭の高さ。しゃがめば当たらない) / 雷の鎖 (人へ伸びる帯)
//  段階 2 開いた翼 (西 = 昇り風): 心臓は 7 ブロック上がり、4 つの風穴に昇り風が立つ (空中戦)。受ける傷 1.2 倍。
//                                  羽ばたき (昇り風が止まり、空中の人を下へ) / 帯電の床 (床の人だけ) / 羽根の雨 (人の上の輪)
//  段階 3 骨だけの翼 (前 = 光): 心臓は床の 2 ブロック上へ。閃光 (心臓を見ている人だけ。背を向けるか柱の陰) /
//                                  雷の網 (柱のあいだを回る二枚の網。すき間を通る) / 招雷の連打 (追う輪。柱のそばなら柱へ)
// 予告は色の粒 (overrideLimiter。GuardMob.warn) に加え、技ごとに別の音と、画面の予告の印 (StormHeartNetwork.Warn) の三つで出す。
// 強さは 03 の 9.8節の目安 (推奨 Lv24: 体力 650、ふつうの技の一撃 15.5 前後)
public class StormHeart extends HeartBoss {
    public static final int PHASE_COUNT = 3;
    public static final float PHASE_TWO_AT = 0.66f;
    public static final float PHASE_THREE_AT = 0.33f;
    public static final int ROAR_TICKS = 60;
    public static final float MAX_HEALTH = 650f;
    // 段階 2 (開いた翼) と、翼が裂けているあいだに受ける傷の倍率
    public static final float OPEN_WINGS_TAKEN = 1.2f;
    public static final float SPLIT_TAKEN = 1.5f;
    public static final int SPLIT_TICKS = 120;
    // 段階 2 の高低の拍: 昇り風の上の端に 8 秒、床の近く (段階 1 の高さ) へ下りて 8 秒。下りている間は立ったまま殴れる。
    // 高い所に留まりつづけて手が届かなくなるのを防ぐ (ベータで「上がったまま降りてこない」と言われた)
    public static final int HIGH_TICKS = 160;
    public static final int PERCH_TICKS = 160;

    // 技の傷 (防具で減る前。12 の 5.3節の表のまま)
    public static final float SUMMON_DAMAGE = 13f;
    public static final float BLADE_DAMAGE = 14f;
    public static final float CHAIN_DAMAGE = 15.5f;
    public static final float WINGBEAT_DAMAGE = 6f;
    public static final float CHARGE_DAMAGE = 3f;
    public static final float FEATHER_DAMAGE = 10f;
    public static final float FLASH_DAMAGE = 12f;
    public static final float WEB_DAMAGE = 14f;
    public static final float BARRAGE_DAMAGE = 10f;
    // 判定の寸法
    public static final double SUMMON_RADIUS = 1.5;
    public static final double ROD_REACH = 3.0;
    public static final double BLADE_LOW = 1.6;
    public static final double BLADE_HIGH = 2.4;
    public static final double CHAIN_HALF_WIDTH = 1.0;
    public static final double CHAIN_LENGTH = 20;
    public static final double FEATHER_RADIUS = 2.0;
    public static final double FLASH_CONE_DEG = 60;
    public static final double WEB_INNER = 2.0;
    public static final double WEB_OUTER = 14.5;
    public static final double WEB_GAP = 2.6;
    public static final double WEB_HALF_WIDTH = 0.7;
    public static final double WEB_HEIGHT = 2.4;
    public static final int WEB_TICKS = 120;

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(StormHeart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ROARING = SynchedEntityData.defineId(StormHeart.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SPLIT = SynchedEntityData.defineId(StormHeart.class, EntityDataSerializers.BOOLEAN);

    public enum Action {
        NONE(null, 0), ROAR(null, ROAR_TICKS),
        SUMMON("summon", 24), BLADE("blade", 24), CHAIN("chain", 20),
        WINGBEAT("wingbeat", 30), CHARGE("charge", 30), FEATHER("feather", 24),
        FLASH("flash", 40), WEB("web", 30), BARRAGE("barrage", 20);

        final String anim;
        // 予告の長さ (tick)。画面の予告の印の長さにも使う
        public final int warnTicks;

        Action(String anim, int warnTicks) {
            this.anim = anim;
            this.warnTicks = warnTicks;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final Action[] PHASE_ONE = {Action.SUMMON, Action.BLADE, Action.CHAIN};
    public static final Action[] PHASE_TWO = {Action.WINGBEAT, Action.CHARGE, Action.FEATHER};
    public static final Action[] PHASE_THREE = {Action.FLASH, Action.WEB, Action.BARRAGE};

    // 押し返す輪 (咆哮)
    private static final class Ring {
        double radius = 1.5;
        final double floorY;
        final Set<UUID> hit = new HashSet<>();

        Ring(double floorY) {
            this.floorY = floorY;
        }
    }

    // 予告の輪のあとに落ちる一撃 (招雷・羽根の雨・招雷の連打)
    enum StrikeKind { SUMMON, FEATHER, BARRAGE }

    private static final class Strike {
        double x;
        double y;
        double z;
        final int impactAt;
        final StrikeKind kind;
        @Nullable
        final UUID follow;
        final int followUntil;

        Strike(double x, double y, double z, int impactAt, StrikeKind kind, @Nullable UUID follow, int followUntil) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.impactAt = impactAt;
            this.kind = kind;
            this.follow = follow;
            this.followUntil = followUntil;
        }

        double radius() {
            return kind == StrikeKind.FEATHER ? FEATHER_RADIUS : SUMMON_RADIUS;
        }
    }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private Action last = Action.NONE;
    private int actionTicks;
    private int cooldown = 40;
    private int splitTicks;
    private int safeFallTicks;
    // 段階 2 の高低の拍の時計 (咆哮が終わった所から数える)
    private int riseClock;
    private boolean defeated;
    private final List<Ring> rings = new ArrayList<>();
    private final List<Strike> strikes = new ArrayList<>();
    // 雷の鎖の向き (水平の単位ベクトル)。風の刃の進む向き
    private Vec3 chainDir = new Vec3(0, 0, 1);
    private Vec3 bladeDir = new Vec3(0, 0, 1);
    private final Set<UUID> bladeHit = new HashSet<>();
    // 雷の網: 二枚の腕の始めの角度とすき間の位置、人ごとの当たりの待ち
    private double webAngle;
    private final double[] webGap = new double[2];
    private final Map<UUID, Integer> webHitAt = new HashMap<>();
    // 昇り風: 止まっている (羽ばたき) / 強まっている (帯電の床)
    private boolean updraftStopped;
    private boolean updraftStrong;
    // 床の高さの基準 (部屋に縛られていれば部屋の床。そうでなければ湧いた所)
    private double anchorY = Double.NaN;
    @Nullable
    private AABB arena;
    private float toughness = 1f;

    public StormHeart(EntityType<? extends Monster> type, Level level) {
        super(type, level, "storm_heart", "roar_1", "roar_2", "summon", "blade", "chain", "split", "wingbeat", "charge", "feather",
                "flash", "web", "barrage");
        this.xpReward = 400;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 15.5)
                .add(Attributes.ARMOR, 9)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 0);
        builder.define(DATA_ROARING, false);
        builder.define(DATA_SPLIT, false);
    }

    public int getPhase() {
        return entityData.get(DATA_PHASE);
    }

    public boolean isRoaring() {
        return entityData.get(DATA_ROARING);
    }

    public boolean isSplit() {
        return entityData.get(DATA_SPLIT);
    }

    public int splitTicks() {
        return splitTicks;
    }

    public void forcePhase(int phase) {
        entityData.set(DATA_PHASE, Mth.clamp(phase, 0, PHASE_COUNT - 1));
        entityData.set(DATA_ROARING, false);
        endSplit();
        action = Action.NONE;
        updraftStopped = false;
        updraftStrong = false;
        cooldown = 20;
        safeFallTicks = 80;
    }

    public Action currentAction() {
        return action;
    }

    public float bossBarProgress() {
        return bossBar.getProgress();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24f));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, this::canReach));
    }

    public void bindArena(AABB arena, int challengers) {
        this.arena = arena;
        this.anchorY = StormHeartArena.FLOOR;
        this.toughness = scaleFor(challengers);
    }

    // 挑む人数の倍率 (上限なし。パーティの 6 人で 4.0)
    public static float scaleFor(int challengers) {
        return 1f + 0.6f * (Math.max(1, challengers) - 1);
    }

    public float toughness() {
        return toughness;
    }

    public boolean isBound() {
        return arena != null;
    }

    // 狙える・技が届く人: 部屋の中の、この心臓のグループの人 (グループのない心臓なら誰でも)
    private boolean canReach(@Nullable LivingEntity entity) {
        return entity instanceof Player p && p.isAlive() && !p.isSpectator() && (arena == null || arena.contains(p.position()))
                && jp.revati.skyabyss.titan.group.GroupOwned.mayTarget(this, p);
    }

    public static int phaseFor(float fraction) {
        if (fraction < PHASE_THREE_AT) {
            return 2;
        }
        if (fraction < PHASE_TWO_AT) {
            return 1;
        }
        return 0;
    }

    private int phaseForHealth() {
        return phaseFor(getHealth() / getMaxHealth());
    }

    // ---------------------------------------------------------------- 高さ (段階で変わる)
    // 床からの心臓の足もとの高さ: 段階 1 は 1.5、翼が裂けると 0.3、段階 2 は 7 上がって 8.5 (下の端が昇り風の上の端に来る)、
    // 段階 3 は床の 2 上へ下りる
    public static double heightFor(int phase, boolean split) {
        if (split) {
            return 0.3;
        }
        return switch (phase) {
            case 0 -> 1.5;
            case 1 -> StormHeartArena.UPDRAFT_TOP - StormHeartArena.FLOOR;
            default -> 2.0;
        };
    }

    private double floorBase() {
        if (Double.isNaN(anchorY)) {
            anchorY = getY() - heightFor(getPhase(), false);
        }
        return anchorY;
    }

    public double targetY() {
        // 咆哮のあいだは、次の段階の高さへ動きはじめる。段階 2 は、拍で下りているあいだ段階 1 の高さ
        int phase = isRoaring() ? Math.min(PHASE_COUNT - 1, getPhase() + 1) : getPhase();
        if (isPerched()) {
            phase = 0;
        }
        return floorBase() + heightFor(phase, splitTicks > 0);
    }

    // 段階 2 の拍で、いま下りている (床の近くにいる) ときか
    public boolean isPerched() {
        return getPhase() == 1 && !isRoaring() && perchedAt(riseClock);
    }

    // 拍の時計 clock (tick) のとき、下りているか。HIGH_TICKS 高く、PERCH_TICKS 下りる、のくり返し
    public static boolean perchedAt(int clock) {
        return Math.floorMod(clock, HIGH_TICKS + PERCH_TICKS) >= HIGH_TICKS;
    }

    @Override
    protected boolean ownsHeight() {
        return true;
    }

    @Override
    protected double coreHeight() {
        return getY() + 2.2;
    }

    private void moveToHeight() {
        double dy = targetY() - getY();
        if (Math.abs(dy) < 0.01) {
            return;
        }
        double step = Mth.clamp(dy * 0.12, -0.3, 0.3);
        if (Math.abs(step) < 0.02) {
            step = Math.copySign(Math.min(Math.abs(dy), 0.02), dy);
        }
        setPos(getX(), getY() + step, getZ());
    }

    // 段階 2 の高低の拍を進める。下りはじめと上がりはじめに、翼の音と雲を出す
    private void tickRiseClock(ServerLevel server) {
        if (getPhase() != 1 || isRoaring() || isIntroPlaying()) {
            riseClock = 0;
            return;
        }
        riseClock++;
        int cycle = HIGH_TICKS + PERCH_TICKS;
        if (riseClock % cycle == HIGH_TICKS || riseClock % cycle == 0) {
            sound(server, StormHeartSetup.WINGBEAT.get(), 2.5f, riseClock % cycle == 0 ? 1.2f : 0.8f);
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 40, 2.5, 0.4, 2.5, 0.1);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 20 == 0) {
            updateBossBarViewers();
        }
        tickRiseClock(server);
        moveToHeight();
        if (isIntroPlaying()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        tickRings(server);
        tickStrikes(server);
        tickUpdrafts(server);
        if (safeFallTicks > 0) {
            safeFallTicks--;
        }
        if (getPhase() == 1 || isRoaring() || safeFallTicks > 0) {
            // 段階 2 の空中戦と、高さの変わる咆哮のあいだは落下の傷をなくす (羽ばたきで落とされても傷はない。12 の 5.3節)
            for (ServerPlayer p : reachable(server, 24)) {
                p.fallDistance = 0;
            }
        }
        if (splitTicks > 0) {
            tickSplit(server);
            return;
        }
        if (action == Action.NONE) {
            if (getPhase() < phaseForHealth()) {
                begin(Action.ROAR, "roar_" + (getPhase() + 1));
                return;
            }
            LivingEntity target = getTarget();
            if (target != null && !canReach(target)) {
                setTarget(null);
                target = null;
            }
            if (target == null || !target.isAlive()) {
                return;
            }
            faceTarget(target);
            if (cooldown == 0) {
                begin(pickAttack(), null);
            }
            return;
        }
        actionTicks++;
        switch (action) {
            case ROAR -> tickRoar(server);
            case SUMMON -> tickSummon(server);
            case BLADE -> tickBlade(server);
            case CHAIN -> tickChain(server);
            case WINGBEAT -> tickWingbeat(server);
            case CHARGE -> tickCharge(server);
            case FEATHER -> tickFeather(server);
            case FLASH -> tickFlash(server);
            case WEB -> tickWeb(server);
            case BARRAGE -> tickBarrage(server);
            default -> {
            }
        }
    }

    private Action pickAttack() {
        Action[] bag = bagFor(getPhase());
        Action pick;
        int guard = 0;
        do {
            pick = bag[random.nextInt(bag.length)];
            guard++;
        } while (pick == last && guard < 20);
        return pick;
    }

    public static Action[] bagFor(int phase) {
        return switch (phase) {
            case 0 -> PHASE_ONE;
            case 1 -> PHASE_TWO;
            default -> PHASE_THREE;
        };
    }

    public void forceAction(Action next) {
        if (next == Action.NONE) {
            return;
        }
        endSplit();
        begin(next, next == Action.ROAR ? "roar_" + (getPhase() + 1) : null);
    }

    private void begin(Action next, @Nullable String animation) {
        action = next;
        last = next;
        actionTicks = 0;
        LivingEntity target = getTarget();
        if (target != null) {
            faceTarget(target);
        }
        playAction(animation != null ? animation : next.anim);
        if (level() instanceof ServerLevel server && next.warnTicks > 0) {
            StormHeartNetwork.warn(reachable(server, 32), next, next.warnTicks);
        }
    }

    private void finish() {
        action = Action.NONE;
        actionTicks = 0;
        updraftStopped = false;
        updraftStrong = false;
        // 次の技までの間 (段階 1 は 2.5〜3.3 秒、段階 2 は 2.0〜2.8 秒、段階 3 は 1.6〜2.2 秒)
        int base = switch (getPhase()) {
            case 0 -> 50;
            case 1 -> 40;
            default -> 32;
        };
        cooldown = base + random.nextInt(getPhase() == 2 ? 13 : 17);
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(-dx, dz) * 180 / Math.PI);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), scale);
    }

    private void sound(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        server.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    void strike(ServerPlayer player, float amount) {
        player.hurt(attackSource(), amount);
    }

    // 心臓の核の高さ (模型の核。描画 2 倍で足もとから約 2.2)
    public Vec3 core() {
        return new Vec3(getX(), getY() + 2.2, getZ());
    }

    // その場所の床の高さ (部屋では竜骨の筋が 1 段低い。部屋の外では真下へたどる)
    double floorAt(double x, double z) {
        if (arena != null) {
            return StormHeartArena.walkY(x, z);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(getY()) + 1, Mth.floor(z));
        for (int i = 0; i < 16; i++) {
            pos.move(0, -1, 0);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return pos.getY() + 1;
            }
        }
        return floorBase();
    }

    // 部屋の半径 (部屋の外では 12)
    private double roomRadius() {
        return isBound() ? StormHeartArena.RADIUS : 12;
    }

    private double centerX() {
        return isBound() ? StormHeartArena.center().x : getX();
    }

    private double centerZ() {
        return isBound() ? StormHeartArena.center().z : getZ();
    }

    List<ServerPlayer> reachable(ServerLevel server, double range) {
        return server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range, 16, range), this::canReach);
    }

    // 空中にいるか (床から 1.2 より上で、足が着いていない)
    private boolean airborne(ServerPlayer p) {
        return !p.onGround() && p.getY() > floorAt(p.getX(), p.getZ()) + 1.2;
    }

    private static void push(ServerPlayer player, double x, double y, double z) {
        player.setDeltaMovement(player.getDeltaMovement().add(x, y, z));
        player.hurtMarked = true;
    }

    // 稲妻 (上から点へ、折れ線の粒)
    private void bolt(ServerLevel server, double x, double yTop, double z, double yBottom, boolean toRod) {
        double px = x;
        double pz = z;
        int steps = Math.max(6, (int) ((yTop - yBottom) / 0.6));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double y = yTop + (yBottom - yTop) * t;
            if (i % 3 == 0 && i > 0 && i < steps) {
                px = x + (random.nextDouble() - 0.5) * 1.2;
                pz = z + (random.nextDouble() - 0.5) * 1.2;
            }
            double qx = i == steps ? x : px;
            double qz = i == steps ? z : pz;
            warn(server, dust(toRod ? 0xFFE278 : 0xFFFFFF, 1.6f), qx, y, qz, 1, 0, 0, 0, 0);
            if (i % 2 == 0) {
                warn(server, ParticleTypes.ELECTRIC_SPARK, qx, y, qz, 2, 0.1, 0.1, 0.1, 0.05);
            }
        }
        server.sendParticles(ParticleTypes.FLASH, x, yBottom + 0.5, z, 1, 0, 0, 0, 0);
        server.playSound(null, x, yBottom, z, StormHeartSetup.STRIKE.get(), SoundSource.HOSTILE, 2.2f, 0.9f + random.nextFloat() * 0.2f);
    }

    // ---- 咆哮 (段階の変わり目) ----
    private void tickRoar(ServerLevel server) {
        if (actionTicks == 1) {
            entityData.set(DATA_ROARING, true);
            sound(server, StormHeartSetup.ROAR.get(), 3f, 1f);
            server.sendParticles(ParticleTypes.CLOUD, getX(), core().y, getZ(), 60, 1.6, 1.4, 1.6, 0.15);
            safeFallTicks = ROAR_TICKS + 60;
        }
        if (actionTicks == 22) {
            rings.add(new Ring(floorAt(getX(), getZ())));
        }
        if (actionTicks % 4 == 0 && actionTicks < 56) {
            warn(server, dust(getPhase() == 0 ? 0xA8D8FF : 0xF6F0C8, 1.3f), getX(), core().y, getZ(), 6, 1.2, 1.2, 1.2, 0.05);
            warn(server, ParticleTypes.ELECTRIC_SPARK, getX(), core().y, getZ(), 6, 1.6, 1.6, 1.6, 0.2);
        }
        if (actionTicks >= ROAR_TICKS) {
            entityData.set(DATA_ROARING, false);
            entityData.set(DATA_PHASE, Math.min(PHASE_COUNT - 1, getPhase() + 1));
            if (isBound()) {
                StormHeartArena.resetRods(server);
            }
            sound(server, SoundEvents.BEACON_ACTIVATE, 2.5f, 1.4f);
            server.sendParticles(ParticleTypes.END_ROD, getX(), core().y, getZ(), 60, 1.8, 1.6, 1.8, 0.1);
            finish();
            cooldown = 40;
        }
    }

    // ---------------------------------------------------------------- 段階 1
    // 招雷 (1.2 秒): 人それぞれの足もとに白い輪。輪が柱から 3 ブロック以内なら雷は柱へ落ち、柱が 1 段満ちる (満ちた柱は受けない)
    private void tickSummon(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.SUMMON.get(), 2.2f, 1f);
            int n = 0;
            for (ServerPlayer p : reachable(server, 24)) {
                if (n++ >= 6) {
                    break;
                }
                strikes.add(new Strike(p.getX(), floorAt(p.getX(), p.getZ()), p.getZ(), tickCount + Action.SUMMON.warnTicks, StrikeKind.SUMMON, null, 0));
            }
        }
        if (actionTicks < 24 && actionTicks % 3 == 0) {
            warn(server, dust(0xFFFFFF, 1.2f), getX(), core().y + 1.5, getZ(), 6, 1.0, 0.4, 1.0, 0.02);
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 風の刃 (1.2 秒): 片側の壁から、頭の高さ (床から 1.6〜2.4) を白い筋が流れはじめ、三日月の刃が部屋を横切る。しゃがめば当たらない
    private void tickBlade(ServerLevel server) {
        double half = roomRadius();
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.BLADE.get(), 2.5f, 1f);
            int quarter = random.nextInt(4);
            bladeDir = switch (quarter) {
                case 0 -> new Vec3(1, 0, 0);
                case 1 -> new Vec3(-1, 0, 0);
                case 2 -> new Vec3(0, 0, 1);
                default -> new Vec3(0, 0, -1);
            };
            bladeHit.clear();
        }
        Vec3 side = new Vec3(-bladeDir.z, 0, bladeDir.x);
        double cx = centerX();
        double cz = centerZ();
        if (actionTicks < 24 && actionTicks % 2 == 0) {
            // 寄せる側の壁ぎわの白い筋 (頭の高さに二本) と、床の向きの線
            for (double s = -half; s <= half; s += 0.8) {
                double x = cx - bladeDir.x * (half - 1.0) + side.x * s;
                double z = cz - bladeDir.z * (half - 1.0) + side.z * s;
                double fy = floorAt(x, z);
                warn(server, dust(0xF0F8FF, 1.4f), x, fy + BLADE_LOW + 0.1, z, 1, 0, 0, 0, 0);
                warn(server, dust(0xBFE0FF, 1.4f), x, fy + BLADE_HIGH - 0.1, z, 1, 0, 0, 0, 0);
            }
            if (actionTicks % 6 == 0) {
                for (double s = -half + 2; s <= half - 2; s += 4) {
                    for (double k = -half + 3; k <= half - 3; k += 5) {
                        double x = cx + bladeDir.x * k + side.x * s;
                        double z = cz + bladeDir.z * k + side.z * s;
                        double fy = floorAt(x, z);
                        warn(server, dust(0x6FA8E8, 1.2f), x, fy + 0.1, z, 1, 0, 0, 0, 0);
                        warn(server, dust(0x6FA8E8, 1.2f), x + bladeDir.x * 0.6, fy + 0.1, z + bladeDir.z * 0.6, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
        int sweepTicks = (int) Math.ceil(2 * half / 1.2);
        if (actionTicks >= 24 && actionTicks <= 24 + sweepTicks) {
            double front = -half + (actionTicks - 24) * 1.2;
            if (actionTicks == 24) {
                server.playSound(null, cx, StormHeartArena.FLOOR + 2, cz, SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 2f, 1.6f);
            }
            for (double s = -half; s <= half; s += 0.9) {
                // 三日月: 真ん中が前へ出る
                double bow = 1.5 * (1 - (s / half) * (s / half));
                double x = cx + bladeDir.x * (front + bow) + side.x * s;
                double z = cz + bladeDir.z * (front + bow) + side.z * s;
                double fy = floorAt(x, z);
                warn(server, dust(0xFFFFFF, 1.7f), x, fy + 2.0, z, 1, 0.05, 0.25, 0.05, 0);
                if (((int) (s * 2)) % 3 == 0) {
                    warn(server, ParticleTypes.SWEEP_ATTACK, x, fy + 2.0, z, 1, 0, 0, 0, 0);
                }
            }
            for (ServerPlayer player : reachable(server, half + 4)) {
                Vec3 v = new Vec3(player.getX() - cx, 0, player.getZ() - cz);
                double along = v.dot(bladeDir);
                double across = v.dot(side);
                double bow = 1.5 * (1 - Math.min(1, (across / half) * (across / half)));
                if (Math.abs(along - (front + bow)) < 1.0 && bladeHit.add(player.getUUID())) {
                    AABB bb = player.getBoundingBox();
                    if (bladeHits(bb.minY, bb.maxY, floorAt(player.getX(), player.getZ()))) {
                        strike(player, BLADE_DAMAGE);
                        push(player, bladeDir.x * 0.8, 0.15, bladeDir.z * 0.8);
                    }
                }
            }
        }
        if (actionTicks >= 30 + sweepTicks) {
            finish();
        }
    }

    // 風の刃に当たるか (人の体の上下が、床から 1.6〜2.4 の刃にかかるか。しゃがむと背は 1.5)
    public static boolean bladeHits(double minY, double maxY, double floorY) {
        return maxY > floorY + BLADE_LOW && minY < floorY + BLADE_HIGH;
    }

    // 雷の鎖 (1.0 秒): 心臓から人へ伸びる光る線 (0.6 秒まで人を追い、そこで決まる)。幅 2・長さ 20 の帯
    private void tickChain(ServerLevel server) {
        LivingEntity target = getTarget();
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.CHAIN.get(), 2.2f, 1f);
        }
        if (target != null && actionTicks <= 12) {
            Vec3 d = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
            if (d.lengthSqr() > 0.01) {
                chainDir = d.normalize();
            }
        }
        double fy = floorAt(getX(), getZ());
        Vec3 side = new Vec3(-chainDir.z, 0, chainDir.x);
        if (actionTicks < 20 && actionTicks % 2 == 0) {
            for (double d = 1.5; d <= CHAIN_LENGTH; d += 0.8) {
                double x = getX() + chainDir.x * d;
                double z = getZ() + chainDir.z * d;
                double y = Mth.lerp(Math.min(1, d / 6), core().y, floorAt(x, z) + 0.9);
                warn(server, dust(actionTicks <= 12 ? 0xFFE278 : 0xFFFFFF, 1.1f), x, y, z, 1, 0, 0, 0, 0);
                if (actionTicks > 12 && ((int) (d * 1.25)) % 2 == 0) {
                    for (int s = -1; s <= 1; s += 2) {
                        warn(server, dust(0xC8A040, 0.9f), x + side.x * s * CHAIN_HALF_WIDTH, floorAt(x, z) + 0.1,
                                z + side.z * s * CHAIN_HALF_WIDTH, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
        if (actionTicks == 20) {
            for (double d = 1.5; d <= CHAIN_LENGTH; d += 0.5) {
                double x = getX() + chainDir.x * d;
                double z = getZ() + chainDir.z * d;
                warn(server, ParticleTypes.ELECTRIC_SPARK, x, floorAt(x, z) + 1.0, z, 3, 0.3, 0.4, 0.3, 0.1);
                warn(server, dust(0xFFFFFF, 1.6f), x, floorAt(x, z) + 1.0, z, 1, 0.1, 0.3, 0.1, 0);
            }
            server.playSound(null, getX(), fy, getZ(), StormHeartSetup.STRIKE.get(), SoundSource.HOSTILE, 2f, 1.3f);
            for (ServerPlayer player : reachable(server, CHAIN_LENGTH + 2)) {
                Vec3 v = new Vec3(player.getX() - getX(), 0, player.getZ() - getZ());
                double along = v.dot(chainDir);
                double across = Math.abs(v.dot(side));
                if (along > 0.5 && along < CHAIN_LENGTH && across < CHAIN_HALF_WIDTH + 0.3 && player.getY() < core().y + 2) {
                    strike(player, CHAIN_DAMAGE);
                    push(player, chainDir.x * 0.5, 0.2, chainDir.z * 0.5);
                }
            }
        }
        if (actionTicks >= 32) {
            finish();
        }
    }

    // 翼が裂ける: 4 本の柱がすべて満ちた。心臓は 6 秒落ちて、受ける傷 1.5 倍。柱は空に戻る
    private void startSplit(ServerLevel server) {
        splitTicks = SPLIT_TICKS;
        entityData.set(DATA_SPLIT, true);
        action = Action.NONE;
        actionTicks = 0;
        strikes.removeIf(s -> s.kind == StrikeKind.SUMMON);
        playAction("split");
        sound(server, StormHeartSetup.SPLIT.get(), 3f, 1f);
        server.sendParticles(ParticleTypes.END_ROD, getX(), core().y, getZ(), 80, 2.0, 1.5, 2.0, 0.15);
        if (isBound()) {
            for (int i = 0; i < 4; i++) {
                Vec3 tip = StormHeartArena.rodTip(i);
                bolt(server, tip.x, tip.y, tip.z, getY() + 1, true);
            }
            StormHeartArena.resetRods(server);
        }
        StormHeartNetwork.warn(reachable(server, 32), null, 0);
        for (ServerPlayer p : reachable(server, 32)) {
            Notice.bar(p, jp.revati.skyabyss.ModText.battle("message.skyabyss.storm_heart.wings_torn"));
        }
    }

    // テスト用 (コマンドと自己テスト): 翼が裂けたことにする
    public void forceSplitForTest(ServerLevel server) {
        startSplit(server);
    }

    private void tickSplit(ServerLevel server) {
        splitTicks--;
        if (tickCount % 4 == 0) {
            warn(server, ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 6, 1.6, 0.6, 1.6, 0.1);
            warn(server, dust(0xFFE278, 1.0f), getX(), getY() + 1.5, getZ(), 3, 1.4, 0.8, 1.4, 0);
        }
        if (getPhase() < phaseForHealth() || splitTicks <= 0) {
            endSplit();
            cooldown = 30;
        }
    }

    private void endSplit() {
        splitTicks = 0;
        entityData.set(DATA_SPLIT, false);
    }

    // ---------------------------------------------------------------- 段階 2
    // 昇り風: 段階 2 のあいだ、4 つの風穴に立つ。上の端は心臓の下の端。羽ばたきの 2 秒は止まる
    private void tickUpdrafts(ServerLevel server) {
        if (!isBound()) {
            return;
        }
        boolean active = getPhase() == 1 && !isRoaring() && !updraftStopped;
        boolean warnDown = action == Action.WINGBEAT && actionTicks < 30;
        if (tickCount % 2 == 0 && (getPhase() == 1 || (isRoaring() && getPhase() == 0 && actionTicks > 30))) {
            for (int k = 0; k < 4; k++) {
                Vec3 h = StormHeartArena.hole(k);
                double fy = StormHeartArena.walkY(h.x, h.z);
                if (!active && !warnDown) {
                    continue;
                }
                for (int i = 0; i < 3; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double r = random.nextDouble() * StormHeartArena.UPDRAFT_RADIUS;
                    double y = fy + random.nextDouble() * (StormHeartArena.UPDRAFT_TOP - fy);
                    warn(server, dust(warnDown ? 0x6FA8E8 : (updraftStrong ? 0xFFFFFF : 0xD8ECFF), 1.0f),
                            h.x + Math.cos(a) * r, y, h.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                // 上の端の印 (どこまで上がるか)
                warn(server, dust(0xFFFFFF, 0.8f), h.x + (random.nextDouble() - 0.5) * 2.4, StormHeartArena.UPDRAFT_TOP + 0.2,
                        h.z + (random.nextDouble() - 0.5) * 2.4, 1, 0, 0, 0, 0);
                server.sendParticles(ParticleTypes.CLOUD, h.x, fy + (warnDown ? 4 : 0.3), h.z, 1, 0.5, 0.1, 0.5, warnDown ? 0 : 0.05);
            }
        }
        if (!active) {
            return;
        }
        for (ServerPlayer player : reachable(server, 24)) {
            if (StormHeartArena.holeAt(player.getX(), player.getY(), player.getZ()) >= 0) {
                Updraft.lift(player, StormHeartArena.UPDRAFT_TOP);
                if (updraftStrong && player.getY() < StormHeartArena.UPDRAFT_TOP - 1) {
                    player.setDeltaMovement(player.getDeltaMovement().x, Updraft.RISE * 1.4, player.getDeltaMovement().z);
                }
            }
        }
    }

    // 羽ばたき (1.5 秒): 翼を高く上げ、風穴の粒が下を向く。2 秒、昇り風が止まり、空中の人を下へ押す (傷 6)
    private void tickWingbeat(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.WINGBEAT.get(), 2.6f, 1f);
        }
        if (actionTicks == 30) {
            updraftStopped = true;
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 80, 6, 0.5, 6, 0.2);
            for (ServerPlayer player : reachable(server, 24)) {
                if (airborne(player)) {
                    strike(player, WINGBEAT_DAMAGE);
                    player.setDeltaMovement(player.getDeltaMovement().x * 0.3, -1.0, player.getDeltaMovement().z * 0.3);
                    player.hurtMarked = true;
                }
            }
            safeFallTicks = Math.max(safeFallTicks, 60);
        }
        if (actionTicks > 30 && actionTicks < 70 && actionTicks % 5 == 0) {
            for (ServerPlayer player : reachable(server, 24)) {
                if (airborne(player)) {
                    push(player, 0, -0.25, 0);
                }
            }
        }
        if (actionTicks >= 70) {
            updraftStopped = false;
        }
        if (actionTicks >= 76) {
            finish();
        }
    }

    // 帯電の床 (1.5 秒): 床を青い火花が這う。3 秒、床に立つ人に 0.5 秒ごとに 3 と鈍足 I。昇り風の中 (空中) なら当たらない。昇り風は強まる
    private void tickCharge(ServerLevel server) {
        double half = roomRadius();
        double cx = centerX();
        double cz = centerZ();
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.CHARGE.get(), 2.4f, 1f);
            updraftStrong = true;
        }
        if (actionTicks < 90 && actionTicks % 2 == 0) {
            int count = actionTicks < 30 ? 24 : 48;
            for (int i = 0; i < count; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(random.nextDouble()) * (half - 0.5);
                double x = cx + Math.cos(a) * r;
                double z = cz + Math.sin(a) * r;
                warn(server, dust(actionTicks < 30 ? 0x5A8CFF : 0x9CC8FF, 1.1f), x, floorAt(x, z) + 0.1, z, 1, 0, 0, 0, 0);
            }
            if (actionTicks >= 30) {
                for (int i = 0; i < 6; i++) {
                    double x = cx + (random.nextDouble() - 0.5) * 2 * half;
                    double z = cz + (random.nextDouble() - 0.5) * 2 * half;
                    warn(server, ParticleTypes.ELECTRIC_SPARK, x, floorAt(x, z) + 0.2, z, 2, 0.3, 0.1, 0.3, 0.1);
                }
            }
        }
        if (actionTicks >= 30 && actionTicks < 90 && (actionTicks - 30) % 10 == 0) {
            for (ServerPlayer player : reachable(server, half + 2)) {
                if (player.getY() < floorAt(player.getX(), player.getZ()) + 0.6) {
                    strike(player, CHARGE_DAMAGE);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
                }
            }
        }
        if (actionTicks >= 96) {
            finish();
        }
    }

    // 羽根の雨 (1.2 秒): 人の上に白い輪 (空中の人にも)。半径 2、少し下へ押す。輪の高さから 1.5 ずれれば当たらない
    private void tickFeather(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.FEATHER.get(), 2.2f, 1f);
            int n = 0;
            for (ServerPlayer p : reachable(server, 24)) {
                if (n++ >= 6) {
                    break;
                }
                strikes.add(new Strike(p.getX(), p.getY(), p.getZ(), tickCount + Action.FEATHER.warnTicks, StrikeKind.FEATHER, null, 0));
            }
        }
        if (actionTicks >= 34) {
            finish();
        }
    }

    // ---------------------------------------------------------------- 段階 3
    // 閃光 (2.0 秒): 心臓が光を吸い込み (画面が暗くなる)、高まる音、画面の眼の印。心臓の方を向いている (視線から 60 度以内) 人は
    // 盲目 3 秒と 12。背を向けていれば何もない。柱の陰 (心臓との間にブロックがある) でもよい
    private void tickFlash(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.FLASH.get(), 3f, 1f);
            StormHeartNetwork.flash(reachable(server, 32), Action.FLASH.warnTicks, false);
        }
        if (actionTicks < 40 && actionTicks % 2 == 0) {
            // 光を吸い込む (外から心臓へ寄る粒)
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double b = (random.nextDouble() - 0.5) * Math.PI;
                double r = 4.0 - (actionTicks % 10) * 0.3;
                warn(server, dust(0xFFFAE0, 1.0f), getX() + Math.cos(a) * Math.cos(b) * r, core().y + Math.sin(b) * r,
                        getZ() + Math.sin(a) * Math.cos(b) * r, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks == 40) {
            Vec3 core = core();
            server.sendParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 3, 0.5, 0.5, 0.5, 0);
            warn(server, ParticleTypes.END_ROD, core.x, core.y, core.z, 60, 0.4, 0.4, 0.4, 0.6);
            List<ServerPlayer> seen = new ArrayList<>();
            for (ServerPlayer player : reachable(server, 32)) {
                boolean hit = flashHits(level(), player.getEyePosition(), player.getViewVector(1f), core, player);
                if (hit) {
                    strike(player, FLASH_DAMAGE);
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    seen.add(player);
                }
            }
            StormHeartNetwork.flashResult(reachable(server, 32), seen);
        }
        if (actionTicks >= 50) {
            finish();
        }
    }

    // 閃光が当たるか: 心臓の核が視線から 60 度以内にあり、目と核のあいだにブロック (柱・壁) がない
    public static boolean flashHits(Level level, Vec3 eye, Vec3 look, Vec3 core, @Nullable net.minecraft.world.entity.Entity viewer) {
        Vec3 to = core.subtract(eye);
        double len = to.length();
        if (len < 1e-3) {
            return true;
        }
        double cos = look.normalize().dot(to.scale(1 / len));
        if (cos < Math.cos(Math.toRadians(FLASH_CONE_DEG))) {
            return false;
        }
        HitResult hit = level.clip(new ClipContext(eye, core, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                viewer == null ? net.minecraft.world.phys.shapes.CollisionContext.empty() : net.minecraft.world.phys.shapes.CollisionContext.of(viewer)));
        return hit.getType() == HitResult.Type.MISS;
    }

    // 雷の網 (1.5 秒): 4 本の柱の先が光り、柱と柱の間に線。そのあと二枚の網が中心のまわりを回る (一周 6 秒)。
    // 網はそれぞれ一か所すき間がある (すき間の位置は網ごとに違う)。触れると 14
    private void tickWeb(ServerLevel server) {
        double cx = centerX();
        double cz = centerZ();
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.WEB.get(), 2.6f, 1f);
            webAngle = random.nextDouble() * Math.PI * 2;
            for (int s = 0; s < 2; s++) {
                webGap[s] = 4.0 + random.nextDouble() * 7.0;
            }
            webHitAt.clear();
        }
        if (actionTicks < 30 && actionTicks % 2 == 0 && isBound()) {
            for (int i = 0; i < 4; i++) {
                Vec3 a = StormHeartArena.rodTip(i);
                warn(server, ParticleTypes.ELECTRIC_SPARK, a.x, a.y, a.z, 3, 0.2, 0.2, 0.2, 0.1);
                Vec3 b = StormHeartArena.rodTip(i == 0 ? 1 : i == 1 ? 3 : i == 3 ? 2 : 0);
                for (double t = 0; t <= 1; t += 0.08) {
                    Vec3 q = a.lerp(b, t);
                    warn(server, dust(0xAAD2FF, 0.9f), q.x, q.y - 0.4, q.z, 1, 0, 0, 0, 0);
                }
            }
        }
        // 網の腕の向き: 予告のあいだも最初の位置に線を出す (どこから来るか分かる)
        if (actionTicks % 2 == 0 && actionTicks < 30 + WEB_TICKS) {
            int t = Math.max(0, actionTicks - 30);
            for (int s = 0; s < 2; s++) {
                double ang = webAngle + s * Math.PI + t * (2 * Math.PI / WEB_TICKS);
                for (double r = WEB_INNER; r <= WEB_OUTER; r += 0.7) {
                    if (r >= webGap[s] && r <= webGap[s] + WEB_GAP) {
                        continue;
                    }
                    double x = cx + Math.cos(ang) * r;
                    double z = cz + Math.sin(ang) * r;
                    double fy = floorAt(x, z);
                    int color = actionTicks < 30 ? 0x6FA8E8 : 0xD8ECFF;
                    if (actionTicks < 30) {
                        warn(server, dust(color, 1.0f), x, fy + 0.1, z, 1, 0, 0, 0, 0);
                    } else {
                        warn(server, dust(color, 1.3f), x, fy + 0.5, z, 1, 0, 0.1, 0, 0);
                        warn(server, dust(0xFFFFFF, 1.3f), x, fy + 1.4, z, 1, 0, 0.1, 0, 0);
                        warn(server, dust(color, 1.3f), x, fy + 2.2, z, 1, 0, 0.1, 0, 0);
                        if (random.nextFloat() < 0.15f) {
                            warn(server, ParticleTypes.ELECTRIC_SPARK, x, fy + 1.2, z, 1, 0.1, 0.5, 0.1, 0.05);
                        }
                    }
                }
                // すき間の両端に印 (通り道)
                if (actionTicks >= 30) {
                    for (double r : new double[] {webGap[s], webGap[s] + WEB_GAP}) {
                        double x = cx + Math.cos(ang) * r;
                        double z = cz + Math.sin(ang) * r;
                        warn(server, dust(0xFFE278, 1.0f), x, floorAt(x, z) + 0.1, z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
        if (actionTicks >= 30 && actionTicks < 30 + WEB_TICKS) {
            int t = actionTicks - 30;
            for (ServerPlayer player : reachable(server, WEB_OUTER + 2)) {
                double px = player.getX() - cx;
                double pz = player.getZ() - cz;
                for (int s = 0; s < 2; s++) {
                    double ang = webAngle + s * Math.PI + t * (2 * Math.PI / WEB_TICKS);
                    if (webHits(px, pz, ang, webGap[s]) && player.getY() < floorAt(player.getX(), player.getZ()) + WEB_HEIGHT) {
                        Integer at = webHitAt.get(player.getUUID());
                        if (at == null || tickCount - at > 20) {
                            webHitAt.put(player.getUUID(), tickCount);
                            strike(player, WEB_DAMAGE);
                            push(player, -Math.sin(ang) * 0.4, 0.2, Math.cos(ang) * 0.4);
                        }
                    }
                }
            }
        }
        if (actionTicks >= 36 + WEB_TICKS) {
            finish();
        }
    }

    // 網の腕 (中心からの向き ang) に触れているか (人の位置は中心からの (px, pz))。すき間 [gap, gap + WEB_GAP] は通れる
    public static boolean webHits(double px, double pz, double ang, double gap) {
        double ux = Math.cos(ang);
        double uz = Math.sin(ang);
        double along = px * ux + pz * uz;
        double across = Math.abs(-px * uz + pz * ux);
        if (along < WEB_INNER || along > WEB_OUTER || across > WEB_HALF_WIDTH) {
            return false;
        }
        return along < gap || along > gap + WEB_GAP;
    }

    // 招雷の連打 (1.0 秒ごとに 3 回): 人の足もとを追う輪 (0.5 秒追って止まり、0.5 秒後に落ちる)。柱のそばなら柱へ (段階 3 では柱は満ちない)
    private void tickBarrage(ServerLevel server) {
        if (actionTicks == 1 || actionTicks == 21 || actionTicks == 41) {
            sound(server, StormHeartSetup.BARRAGE.get(), 2.2f, 1f + actionTicks / 60f);
            int n = 0;
            for (ServerPlayer p : reachable(server, 24)) {
                if (n++ >= 6) {
                    break;
                }
                strikes.add(new Strike(p.getX(), floorAt(p.getX(), p.getZ()), p.getZ(), tickCount + 20, StrikeKind.BARRAGE, p.getUUID(), tickCount + 10));
            }
            if (actionTicks > 1) {
                StormHeartNetwork.warn(reachable(server, 32), Action.BARRAGE, 20);
            }
        }
        if (actionTicks >= 66) {
            finish();
        }
    }

    // ---------------------------------------------------------------- 輪と一撃 (技の後も続く)
    private void tickRings(ServerLevel server) {
        for (Ring ring : rings) {
            ring.radius += 0.8;
            int points = Math.max(8, (int) (2 * Math.PI * ring.radius / 1.4));
            for (int i = 0; i < points; i++) {
                double a = i * 2 * Math.PI / points;
                warn(server, dust(0xA8D8FF, 1.5f), getX() + Math.cos(a) * ring.radius, ring.floorY + 0.5, getZ() + Math.sin(a) * ring.radius, 1, 0, 0, 0, 0);
            }
            double r = ring.radius + 2;
            AABB area = new AABB(getX() - r, ring.floorY - 2, getZ() - r, getX() + r, ring.floorY + 12, getZ() + r);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach)) {
                Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                double d = to.length();
                if (Math.abs(d - ring.radius) < 1.0 && ring.hit.add(player.getUUID())) {
                    player.knockback(0.9, -to.x, -to.z);
                    player.hurtMarked = true;
                }
            }
        }
        rings.removeIf(w -> w.radius > 20);
    }

    private void tickStrikes(ServerLevel server) {
        if (strikes.isEmpty()) {
            return;
        }
        List<Strike> remaining = new ArrayList<>();
        for (Strike s : new ArrayList<>(strikes)) {
            if (s.follow != null && tickCount < s.followUntil && server.getEntity(s.follow) instanceof ServerPlayer p && canReach(p)) {
                s.x = p.getX();
                s.z = p.getZ();
                s.y = floorAt(p.getX(), p.getZ());
            }
            if (tickCount >= s.impactAt) {
                impact(server, s);
                continue;
            }
            if (tickCount % 2 == 0) {
                double r = s.radius();
                int color = s.kind == StrikeKind.FEATHER ? 0xF0F8FF : (s.follow != null && tickCount < s.followUntil ? 0xFFE278 : 0xFFFFFF);
                for (int a = 0; a < 360; a += 24) {
                    double rad = Math.toRadians(a);
                    warn(server, dust(color, 1.3f), s.x + Math.cos(rad) * r, s.y + (s.kind == StrikeKind.FEATHER ? 2.4 : 0.15),
                            s.z + Math.sin(rad) * r, 1, 0, 0, 0, 0);
                }
                // 輪の中心から柱への線 (柱が受けるか分かる)
                if (s.kind != StrikeKind.FEATHER && isBound()) {
                    int rod = StormHeartArena.nearestRod(s.x, s.z, ROD_REACH);
                    if (rod >= 0 && (s.kind == StrikeKind.BARRAGE || StormHeartArena.charge(rod) < StormHeartArena.ROD_STAGES)) {
                        BlockPos b = StormHeartArena.rod(rod);
                        for (double t = 0; t <= 1; t += 0.2) {
                            warn(server, dust(0xFFE278, 0.8f), Mth.lerp(t, s.x, b.getX() + 0.5), s.y + 0.15, Mth.lerp(t, s.z, b.getZ() + 0.5), 1, 0, 0, 0, 0);
                        }
                    }
                }
            }
            if (s.kind == StrikeKind.FEATHER && tickCount % 3 == 0) {
                warn(server, ParticleTypes.WHITE_ASH, s.x, s.y + 3.0, s.z, 6, 1.2, 0.3, 1.2, 0);
            }
            remaining.add(s);
        }
        strikes.clear();
        strikes.addAll(remaining);
    }

    private void impact(ServerLevel server, Strike s) {
        double ceiling = isBound() ? StormHeartArena.CEILING - 0.5 : s.y + 12;
        if (s.kind == StrikeKind.FEATHER) {
            server.playSound(null, s.x, s.y, s.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.4f, 1.6f);
            server.sendParticles(ParticleTypes.WHITE_ASH, s.x, s.y + 1.5, s.z, 40, 1.2, 1.0, 1.2, 0.05);
            warn(server, ParticleTypes.END_ROD, s.x, s.y + 1.0, s.z, 10, 0.8, 0.5, 0.8, 0.02);
            AABB area = new AABB(s.x - FEATHER_RADIUS, s.y - 1.5, s.z - FEATHER_RADIUS, s.x + FEATHER_RADIUS, s.y + 1.5, s.z + FEATHER_RADIUS);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach)) {
                if (Math.hypot(player.getX() - s.x, player.getZ() - s.z) <= FEATHER_RADIUS + 0.3) {
                    strike(player, FEATHER_DAMAGE);
                    push(player, 0, -0.5, 0);
                }
            }
            return;
        }
        // 招雷・連打: 柱から 3 ブロック以内なら柱へ (招雷は満ちていない柱だけ)
        int rod = isBound() ? StormHeartArena.nearestRod(s.x, s.z, ROD_REACH) : -1;
        if (rod >= 0 && s.kind == StrikeKind.SUMMON && StormHeartArena.charge(rod) >= StormHeartArena.ROD_STAGES) {
            rod = -1;
        }
        if (rod >= 0) {
            Vec3 tip = StormHeartArena.rodTip(rod);
            bolt(server, tip.x, ceiling, tip.z, tip.y, true);
            if (s.kind == StrikeKind.SUMMON && getPhase() == 0) {
                StormHeartArena.addCharge(server, rod);
                if (StormHeartArena.allFull() && splitTicks == 0 && !isRoaring()) {
                    startSplit(server);
                }
            }
            return;
        }
        bolt(server, s.x, ceiling, s.z, s.y, false);
        float damage = s.kind == StrikeKind.SUMMON ? SUMMON_DAMAGE : BARRAGE_DAMAGE;
        AABB area = new AABB(s.x - SUMMON_RADIUS, s.y - 1, s.z - SUMMON_RADIUS, s.x + SUMMON_RADIUS, s.y + 3, s.z + SUMMON_RADIUS);
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach)) {
            if (Math.hypot(player.getX() - s.x, player.getZ() - s.z) <= SUMMON_RADIUS + 0.3) {
                strike(player, damage);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1));
            }
        }
    }

    // ---------------------------------------------------------------- 受ける傷
    // 咆哮の間は傷を受けない。段階 2 は 1.2 倍、翼が裂けている間は 1.5 倍。人数の分だけ割る
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRoaring()) {
            if (level() instanceof ServerLevel server) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.8f);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), core().y, getZ(), 8, 0.8, 0.8, 0.8, 0.1);
            }
            return false;
        }
        return super.hurt(source, takenFor(getPhase(), splitTicks > 0, amount, toughness));
    }

    public static float takenFor(int phase, boolean split, float amount, float toughness) {
        float a = amount;
        if (phase == 1) {
            a *= OPEN_WINGS_TAKEN;
        }
        if (split) {
            a *= SPLIT_TAKEN;
        }
        return a / toughness;
    }

    private void updateBossBarViewers() {
        for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
            if (!player.isAlive() || player.distanceToSqr(this) > 48 * 48 || player.level() != level()
                    || !jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(this, player)) {
                bossBar.removePlayer(player);
            }
        }
        if (level() instanceof ServerLevel server) {
            // 体力のバーは、この心臓が見える (同じグループの) 人にだけ
            for (ServerPlayer player : server.getPlayers(p -> p.distanceToSqr(this) < 40 * 40
                    && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(this, p))) {
                bossBar.addPlayer(player);
            }
        }
    }

    @Override
    protected ServerBossEvent heartBar() {
        return bossBar;
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    // 倒れたとき: 翼が痙攣してだらりと落ち、核が過負荷で弾ける演出 (deathEffects) のあとで、報酬・進み・記録を渡す (onDeathFinished)
    @Override
    public void die(DamageSource source) {
        if (defeated) {
            return;
        }
        defeated = true;
        super.die(source);
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), StormHeartSetup.DEATH.get(), SoundSource.HOSTILE, 3f, 1f);
            server.sendParticles(ParticleTypes.END_ROD, getX(), core().y, getZ(), 120, 1.5, 1.6, 1.5, 0.2);
            warn(server, dust(0xF6F0C8, 1.6f), getX(), core().y, getZ(), 80, 1.5, 1.5, 1.5, 0.1);
            StormHeartNetwork.warn(reachable(server, 32), null, 0);
        }
    }

    @Override
    protected void onDeathFinished(ServerLevel server) {
        if (server.dimension() == StormTravel.STORM_BACK && getTags().contains(TitanBackTrials.HEART_TAG)) {
            StormHeartBattle.onDefeated(server, this);
        }
    }

    // 雷を一本、見た目だけ落とす (傷も火もない。音と閃光だけ)
    private void visualBolt(ServerLevel server) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt != null) {
            bolt.moveTo(getX(), floorBase(), getZ());
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
    }

    // ---- 登場: 小さな火花が明滅し、雷が落ちた瞬間に心臓が現れて、畳んだ翼が開く (animation.storm_heart.intro)
    @Override
    protected void introEffects(ServerLevel server, int tick) {
        double cy = core().y;
        if (tick == 0) {
            sound(server, StormHeartSetup.BEAT.get(), 2f, 1.4f);
        }
        if (tick < 32 && tick % 4 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), cy, getZ(), 2 + tick / 8, 0.4, 0.4, 0.4, 0.1);
        }
        if (tick == 14 || tick == 24) {
            sound(server, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f + tick * 0.01f);
        }
        if (tick == 32) {
            visualBolt(server);
            sound(server, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 0.9f);
            server.sendParticles(ParticleTypes.END_ROD, getX(), cy, getZ(), 80, 0.6, 0.8, 0.6, 0.4);
            warn(server, dust(0xF6F0C8, 1.6f), getX(), cy, getZ(), 60, 1.2, 1.2, 1.2, 0.2);
        }
        if (tick == 40) {
            sound(server, StormHeartSetup.ROAR.get(), 3f, 1.2f);
            server.sendParticles(ParticleTypes.CLOUD, getX(), cy, getZ(), 60, 2.8, 0.6, 2.8, 0.15);
        }
        if (tick >= 36 && tick <= 60 && tick % 3 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Mth.nextDouble(random, -3, 3), cy + Mth.nextDouble(random, -1, 1.5), getZ(), 3, 0.5, 0.5, 0.3, 0.1);
        }
        if (tick == 70) {
            sound(server, SoundEvents.COPPER_HIT, 2f, 0.7f);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), cy, getZ(), 40, 1.8, 1.4, 1.8, 0.2);
        }
    }

    // ---- 倒れる: 翼が痙攣してだらりと落ち、羽が散り、核が過負荷で膨らんで弾ける (animation.storm_heart.death)。体は床の近くへ降りてくる
    @Override
    protected void deathEffects(ServerLevel server, int tick) {
        double cy = core().y;
        double land = floorBase() + 1.0;
        if (tick > 20 && getY() > land + 0.05) {
            setPos(getX(), Math.max(land, getY() - 0.12), getZ());
        }
        if (tick <= 20 && tick % 3 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Mth.nextDouble(random, -3, 3), cy + Mth.nextDouble(random, -1, 1.5), getZ(), 4, 0.4, 0.4, 0.3, 0.1);
        }
        if (tick == 6 || tick == 12) {
            sound(server, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, 1.2f - tick * 0.02f);
        }
        if (tick == 24) {
            sound(server, SoundEvents.PHANTOM_FLAP, 3f, 0.5f);
        }
        if (tick >= 24 && tick <= 50 && tick % 2 == 0) {
            server.sendParticles(ParticleTypes.CLOUD, getX() + Mth.nextDouble(random, -3, 3), cy - 0.5, getZ(), 2, 0.4, 0.2, 0.3, 0.02);
            server.sendParticles(ParticleTypes.END_ROD, getX() + Mth.nextDouble(random, -3, 3), cy, getZ(), 1, 0.3, 0.3, 0.3, 0.02);
        }
        if (tick >= 40 && tick <= 78 && tick % 2 == 0) {
            double a = tick * 0.9;
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(a) * 1.5, cy + Math.sin(a * 0.6), getZ() + Math.sin(a) * 1.5, 2 + (tick - 40) / 10, 0.2, 0.2, 0.2, 0.2);
        }
        if (tick == 60) {
            sound(server, SoundEvents.BEACON_AMBIENT, 3f, 1.8f);
        }
        if (tick == 78) {
            visualBolt(server);
            sound(server, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 0.8f);
            server.sendParticles(ParticleTypes.END_ROD, getX(), cy, getZ(), 150, 0.4, 0.4, 0.4, 0.5);
            warn(server, dust(0xF6F0C8, 1.8f), getX(), cy, getZ(), 100, 1.0, 1.0, 1.0, 0.3);
        }
        if (tick == 84) {
            sound(server, SoundEvents.BEACON_DEACTIVATE, 3f, 1.4f);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return StormHeartSetup.BEAT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.STORM_HEART_HURT.get();
    }

    // 最後の一撃の音 (被弾と同じ)。倒れる演出は deathEffects が音も流すので、ここで別の音を重ねない
    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.STORM_HEART_HURT.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    // 光の粒: 段階 1 は翼の縁の静電、段階 2 は風の白い粒、段階 3 は骨のあいだの火花
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            return;
        }
        double cy = getY() + 2.2;
        int phase = getPhase();
        if (phase == 0) {
            if (random.nextFloat() < 0.2f) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + Mth.nextDouble(random, -1.6, 1.6), cy + Mth.nextDouble(random, -1.5, 1.5),
                        getZ() + Mth.nextDouble(random, -0.8, 0.8), 0, 0, 0);
            }
        } else if (phase == 1) {
            if (random.nextFloat() < 0.4f) {
                level().addParticle(dust(0xD8ECFF, 1.0f), getX() + Mth.nextDouble(random, -3, 3), cy + Mth.nextDouble(random, -1.5, 2.5),
                        getZ() + Mth.nextDouble(random, -1, 1), 0, -0.02, 0);
            }
        } else {
            for (int k = 0; k < 2; k++) {
                if (random.nextFloat() < 0.5f) {
                    level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + Mth.nextDouble(random, -2.8, 2.8), cy + Mth.nextDouble(random, -1.0, 1.4),
                            getZ() + Mth.nextDouble(random, -0.6, 0.6), 0, 0, 0);
                }
            }
        }
    }
}
