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

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
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
// 傷のある技はすべて Telegraph (combat。02 の 5.4節): 印と当たりは同じ形。人数 n (心臓の間にいる人) で円・線・腕が増える。
//  段階 1 畳んだ翼 (東 = 避雷の柱): 招雷 (円が満ちる。円が柱の近くなら柱へ落ちて柱が満ちる。4 本満ちると翼が裂けて 6 秒落ちる・受ける傷 1.5 倍) /
//                                  風の刃 (回る帯。頭の高さ。しゃがめば当たらない) / 雷の鎖 (心臓 → 人 → 人 と渡る線)
//  段階 2 開いた翼 (西 = 昇り風): 心臓は 4 ブロック上がり (足もと 5.5)、4 つの風穴に昇り風が立つ (空中戦)。受ける傷 1.2 倍。
//                                  羽ばたき (風穴の下降風と、跳べる突風の輪) / 帯電の床 (床の円。空中なら当たらない) / 羽根の雨 (人の上の円)
//  段階 3 骨だけの翼 (前 = 光): 心臓は床の 2 ブロック上へ。閃光 (人へ向く扇の中で、心臓を見ている人だけ。背を向けるか柱の陰) /
//                                  雷の網 (回る雷の腕。跳べば越える) / 招雷の連打 (円が三度。柱のそばなら柱へ)
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
    public static final double CHAIN_NODE_RADIUS = 1.8;
    public static final double FEATHER_RADIUS = 2.0;
    public static final double FLASH_CONE_DEG = 60;
    // 風の刃 (回る帯): 長さ・半幅・予告・振れる tick。人数ぶん増えても、振れる角の合計は 180 度のまま
    public static final double BLADE_REACH = 13;
    public static final double BLADE_HALF_WIDTH = 1.3;
    public static final int BLADE_WARN = 24;
    public static final int BLADE_ACTIVE = 30;
    // 雷の網 (回る腕。跳び越えられる): 長さ・半幅・振れる tick。腕の数が増えても、振れる角の合計は 180 度
    public static final double WEB_REACH = 13;
    public static final double WEB_HALF_WIDTH = 1.0;
    public static final int WEB_TICKS = 80;
    public static final double WEB_JUMP = 1.0;
    // 帯電の床の円の半径と当たりの続く tick (0.5 秒ごと、3 秒)
    public static final double CHARGE_RADIUS = 3.0;
    public static final int CHARGE_ACTIVE = 50;
    // 閃光の扇 (一本あたり半角 22 度・射程 13)
    public static final double FLASH_RANGE = 13;
    public static final double FLASH_HALF_ANGLE = 22;
    // 咆哮の押し返し (円)
    public static final double ROAR_PUSH_RADIUS = 7.5;
    // 突風の輪 (羽ばたき): 輪の中の筋 (安全な床) の半角と、風穴の下降風の半径
    public static final double GUST_GAP_DEG = 24;
    public static final double DOWNDRAFT_RADIUS = StormHeartArena.UPDRAFT_RADIUS + 0.5;
    // 模型の核の高さ (足もとから。模型は描画で 3 倍)
    public static final double CORE_OFFSET = 3.3;

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
    // 風の刃の腕 (始めの角度の一覧と、一本の振れる角)。雷の網の腕も同じ形
    private double[] armStart = new double[0];
    private double armSpan;
    // 雷の鎖: 一度の鎖で同じ人に二度当たらない
    private final Set<UUID> chainHit = new HashSet<>();
    // 閃光を受けた人 (画面の白い瞬きに使う)
    private final List<ServerPlayer> flashSeen = new ArrayList<>();
    // 試験・開発用: 次の技を始めない / 心臓の間にいる人数を実際より多いことにする (0 なら実際の人数)
    private boolean holdAttacks;
    private int partyOverride;
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
        Telegraph.cancelAll(this);
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
    // 床からの心臓の足もとの高さ: 段階 1 は 1.5、翼が裂けると 0.3、段階 2 は 5.5 (下の端が昇り風の上の端に来る。判定箱の高さ 6 で天井の下に収まる)、
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
        return getY() + CORE_OFFSET;
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
            if (cooldown == 0 && !holdAttacks) {
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

    // 心臓の核の高さ (模型の核。描画 3 倍で足もとから 3.3)
    public Vec3 core() {
        return new Vec3(getX(), getY() + CORE_OFFSET, getZ());
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

    // ---- 試験・開発用 ----
    // 次の技を始めない (forceAction で出した技だけを見たいとき)
    public void holdAttacks() {
        holdAttacks = true;
    }

    // 心臓の間にいる人数を、実際より多いことにする (一人で複数人の技の絵を撮るため。0 なら実際の人数)
    public void setPartyOverride(int count) {
        partyOverride = count;
    }

    public int actionTickCount() {
        return actionTicks;
    }

    int present(ServerLevel server) {
        return Math.max(partyOverride, reachable(server, 40).size());
    }

    // ---- 人数 n で増えるもの (一人でも、印を見れば傷なしで避けられる。12 の 5.3節) ----
    // 風の刃・雷の網の腕の数 (振れる角の合計は 180 度のまま。数が増えるぶん一本は狭く短くなる)
    public static int bladeArms(int n) {
        return n >= 5 ? 3 : n >= 3 ? 2 : 1;
    }

    public static int webArms(int n) {
        return n >= 5 ? 4 : n >= 3 ? 3 : 2;
    }

    // 雷の鎖が渡る人の数 (心臓 → 人 → 人 …)
    public static int chainTargets(int n) {
        return Math.min(n, 5);
    }

    // 帯電の床の円の数
    public static int chargePads(int n) {
        return Math.min(7, 2 + n);
    }

    // 閃光の扇の数
    public static int flashFans(int n) {
        return Math.min(n, 4);
    }

    // 突風の輪の数 (3 人から二重)
    public static int gustRings(int n) {
        return n >= 3 ? 2 : 1;
    }

    // 人の足もとのほかに落ちる雷・羽根の数 (3 人から)
    public static int extraStrikes(int n) {
        return n >= 5 ? 3 : n >= 3 ? 2 : 0;
    }

    // 技の床の高さ。部屋の中は床 (竜骨の筋が 1 段低くても同じ高さで測る)
    private double floorY() {
        return isBound() ? StormHeartArena.FLOOR : floorAt(getX(), getZ());
    }

    // 予告の組み立て (形・長さ・色。持ち主は心臓、当たるのは心臓の間の人)
    private Telegraph.Builder tg(ServerLevel server, TelegraphShape shape, int warn, int color) {
        return Telegraph.in(server, shape).floor(floorY()).warn(warn).color(color).owner(this).victims(this::canReach).reach(3.5);
    }

    // 部屋の中のどこかの点 (壁から margin 離す)
    private Vec3 randomSpot(double margin) {
        double a = random.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(random.nextDouble()) * Math.max(2, roomRadius() - margin);
        return new Vec3(centerX() + Math.cos(a) * r, floorY(), centerZ() + Math.sin(a) * r);
    }

    // 狙う地点 (人の足もと) を最大 max か所。いま狙っている人を先頭に、残りは入れ替える。
    // 開発用の人数の上書きで人が足りないぶんは、部屋のどこかの点で補う
    private List<Vec3> pickSpots(ServerLevel server, int max) {
        List<ServerPlayer> all = new ArrayList<>(reachable(server, 40));
        java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
        LivingEntity main = getTarget();
        if (main instanceof ServerPlayer mp && all.remove(mp)) {
            all.add(0, mp);
        }
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : all) {
            if (spots.size() >= max) {
                break;
            }
            spots.add(p.position());
        }
        while (spots.size() < Math.min(max, partyOverride)) {
            spots.add(randomSpot(2.5));
        }
        return spots;
    }

    // 招雷の輪 1 つ: 半径 radius の円が満ちて、落ちる。輪が避雷の柱から 3 ブロック以内なら雷は柱へ落ち (傷なし。charges なら柱が 1 段満ちる)、
    // そうでなければ円の中の人に damage と短い鈍足。柱へ落ちる輪は黄色く、柱へ線が伸びる
    private void lightningCircle(ServerLevel server, double x, double y, double z, double radius, int warn, float damage, boolean charges) {
        int rod = isBound() ? StormHeartArena.nearestRod(x, z, ROD_REACH) : -1;
        if (rod >= 0 && charges && StormHeartArena.charge(rod) >= StormHeartArena.ROD_STAGES) {
            rod = -1;
        }
        final int toRod = rod;
        double ceiling = isBound() ? StormHeartArena.CEILING - 0.5 : y + 12;
        Telegraph.Builder b = tg(server, TelegraphShape.circle(x, z, radius), warn, toRod >= 0 ? 0xFFE278 : 0xFFFFFF)
                .floor(y)
                .onImpact(s -> {
                    if (toRod >= 0) {
                        Vec3 tip = StormHeartArena.rodTip(toRod);
                        bolt(s, tip.x, ceiling, tip.z, tip.y, true);
                        if (charges && getPhase() == 0) {
                            StormHeartArena.addCharge(s, toRod);
                            if (StormHeartArena.allFull() && splitTicks == 0 && !isRoaring()) {
                                startSplit(s);
                            }
                        }
                    } else {
                        bolt(s, x, ceiling, z, y, false);
                    }
                });
        if (toRod < 0) {
            b.onHit((s, player) -> {
                strike(player, damage);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1));
            });
        } else {
            BlockPos base = StormHeartArena.rod(toRod);
            for (double t = 0; t <= 1; t += 0.1) {
                warn(server, dust(0xFFE278, 0.9f), Mth.lerp(t, x, base.getX() + 0.5), y + 0.2, Mth.lerp(t, z, base.getZ() + 0.5), 1, 0, 0, 0, 0);
            }
        }
        b.start();
    }

    // ---- 咆哮 (段階の変わり目) ----
    // 3 秒。咆哮し、足もとの円 (半径 7.5。始めの 1.1 秒だけ印) に入っている人を押し返す (傷なし)。広がる輪は見た目だけ
    private void tickRoar(ServerLevel server) {
        if (actionTicks == 1) {
            entityData.set(DATA_ROARING, true);
            sound(server, StormHeartSetup.ROAR.get(), 3f, 1f);
            server.sendParticles(ParticleTypes.CLOUD, getX(), core().y, getZ(), 60, 1.6, 1.4, 1.6, 0.15);
            safeFallTicks = ROAR_TICKS + 60;
            tg(server, TelegraphShape.circle(getX(), getZ(), ROAR_PUSH_RADIUS), 22, 0xA8D8FF)
                    .onHit((s, player) -> {
                        Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                        player.knockback(0.9, -to.x, -to.z);
                        player.hurtMarked = true;
                    })
                    .start();
            tg(server, TelegraphShape.wave(getX(), getZ(), 1.5, Math.max(8, roomRadius()), 0.9, new double[0], 0), 22, 0xA8D8FF).active(24).start();
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
    // 招雷 (1.2 秒): 人それぞれの足もとに半径 1.5 の円が満ちて雷が落ちる。3 人から、人のいない所にも 2〜3 か所落ちる。
    // 円が柱から 3 ブロック以内なら雷は柱へ落ち (傷なし)、柱が 1 段満ちる (満ちた柱は受けない)。柱のそばに立てば自分への雷は柱が受ける
    private void tickSummon(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.SUMMON.get(), 2.2f, 1f);
            List<Vec3> spots = pickSpots(server, 6);
            for (int i = 0; i < extraStrikes(present(server)); i++) {
                spots.add(randomSpot(2.5));
            }
            for (Vec3 spot : spots) {
                lightningCircle(server, spot.x, floorY(), spot.z, SUMMON_RADIUS, Action.SUMMON.warnTicks, SUMMON_DAMAGE, true);
            }
        }
        if (actionTicks < 24 && actionTicks % 3 == 0) {
            warn(server, dust(0xFFFFFF, 1.2f), getX(), core().y + 1.5, getZ(), 6, 1.0, 0.4, 1.0, 0.02);
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 風の刃 (1.2 秒): 心臓を中心に、風の帯 (幅 2.6・長さ 13) が回る。振れる扇を床に描いて満ち、そのあと帯が一度その扇を薙ぐ (1.5 秒)。
    // 頭の高さ (床から 1.6〜2.4) を通るので、**しゃがめば当たらない**。人数で腕が 1 → 2 → 3 本 (扇の合計は 180 度のまま)
    private void tickBlade(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.BLADE.get(), 2.5f, 1f);
            int arms = bladeArms(present(server));
            LivingEntity target = getTarget();
            double base = target != null ? Telegraph.angleOf(target.getX() - getX(), target.getZ() - getZ()) : random.nextDouble() * Math.PI * 2;
            armSpan = Math.PI / arms;
            armStart = new double[arms];
            for (int i = 0; i < arms; i++) {
                double mid = base + i * 2 * Math.PI / arms;
                armStart[i] = mid - armSpan / 2;
                boolean first = i == 0;
                tg(server, TelegraphShape.sweep(getX(), getZ(), armStart[i], armSpan, BLADE_REACH, BLADE_HALF_WIDTH), BLADE_WARN, 0xBFE0FF)
                        .active(BLADE_ACTIVE)
                        .victims(p -> canReach(p) && bladeCrosses(p))
                        .onImpact(s -> {
                            if (first) {
                                s.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 2f, 1.6f);
                            }
                        })
                        .onHit((s, player) -> {
                            Vec3 away = player.position().subtract(position()).multiply(1, 0, 1);
                            away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                            strike(player, BLADE_DAMAGE);
                            push(player, away.x * 0.8, 0.15, away.z * 0.8);
                        })
                        .start();
            }
        }
        // 頭の高さの白い筋: 予告のあいだは扇の始めの縁に、薙いでいるあいだは帯の上に
        if (actionTicks % 2 == 0 && actionTicks <= BLADE_WARN + BLADE_ACTIVE) {
            double progress = actionTicks < BLADE_WARN ? 0 : (double) (actionTicks - BLADE_WARN) / BLADE_ACTIVE;
            for (double start : armStart) {
                double ang = start + armSpan * progress;
                for (double d = 2.5; d <= BLADE_REACH; d += actionTicks < BLADE_WARN ? 2.2 : 1.1) {
                    warn(server, dust(0xFFFFFF, 1.5f), getX() + Math.cos(ang) * d, floorY() + 2.0, getZ() + Math.sin(ang) * d, 1, 0.05, 0.25, 0.05, 0);
                }
            }
        }
        if (actionTicks >= BLADE_WARN + BLADE_ACTIVE + 8) {
            finish();
        }
    }

    private boolean bladeCrosses(ServerPlayer player) {
        AABB bb = player.getBoundingBox();
        return bladeHits(bb.minY, bb.maxY, floorAt(player.getX(), player.getZ()));
    }

    // 風の刃に当たるか (人の体の上下が、床から 1.6〜2.4 の刃にかかるか。しゃがむと背は 1.5)
    public static boolean bladeHits(double minY, double maxY, double floorY) {
        return maxY > floorY + BLADE_LOW && minY < floorY + BLADE_HIGH;
    }

    // 雷の鎖 (1.0 秒): 稲妻が心臓から人へ、人から次の人へ渡る。人の位置 (始めに決まる) を結ぶ幅 2 の線と、人の位置の円 (半径 1.8) が満ちる。
    // 一人のときは、人の方へ伸びる長さ 20 の線。人数で渡る人が増える (最大 5 人。近い順につなぐ)。同じ鎖に二度は当たらない
    private void tickChain(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.CHAIN.get(), 2.2f, 1f);
            chainHit.clear();
            List<Vec3> targets = pickSpots(server, chainTargets(present(server)));
            if (targets.isEmpty()) {
                targets.add(position().add(Vec3.directionFromRotation(0, getYRot()).scale(8)));
            }
            if (targets.size() == 1) {
                Vec3 t = targets.get(0);
                chainSegment(server, getX(), getZ(), Telegraph.angleOf(t.x - getX(), t.z - getZ()), CHAIN_LENGTH, true);
            } else {
                List<Vec3> rest = new ArrayList<>(targets);
                double cx = getX();
                double cz = getZ();
                boolean first = true;
                while (!rest.isEmpty()) {
                    Vec3 next = rest.get(0);
                    for (Vec3 t : rest) {
                        if (Math.hypot(t.x - cx, t.z - cz) < Math.hypot(next.x - cx, next.z - cz)) {
                            next = t;
                        }
                    }
                    rest.remove(next);
                    double len = Math.hypot(next.x - cx, next.z - cz);
                    if (len > 0.5) {
                        chainSegment(server, cx, cz, Telegraph.angleOf(next.x - cx, next.z - cz), len, first);
                    }
                    chainNode(server, next.x, next.z);
                    cx = next.x;
                    cz = next.z;
                    first = false;
                }
            }
        }
        if (actionTicks >= 32) {
            finish();
        }
    }

    private void chainSegment(ServerLevel server, double x, double z, double angle, double length, boolean sound) {
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        tg(server, TelegraphShape.line(x, z, angle, length, CHAIN_HALF_WIDTH), Action.CHAIN.warnTicks, 0xFFE278)
                .onImpact(s -> {
                    if (sound) {
                        s.playSound(null, getX(), floorY(), getZ(), StormHeartSetup.STRIKE.get(), SoundSource.HOSTILE, 2f, 1.3f);
                    }
                    for (double d = 0.5; d <= length; d += 0.8) {
                        warn(s, ParticleTypes.ELECTRIC_SPARK, x + dx * d, floorY() + 1.0, z + dz * d, 2, 0.3, 0.4, 0.3, 0.1);
                    }
                })
                .onHit((s, player) -> {
                    if (chainHit.add(player.getUUID())) {
                        strike(player, CHAIN_DAMAGE);
                        push(player, dx * 0.5, 0.2, dz * 0.5);
                    }
                })
                .start();
    }

    private void chainNode(ServerLevel server, double x, double z) {
        tg(server, TelegraphShape.circle(x, z, CHAIN_NODE_RADIUS), Action.CHAIN.warnTicks, 0xFFE278)
                .onImpact(s -> warn(s, ParticleTypes.ELECTRIC_SPARK, x, floorY() + 1.0, z, 12, 0.6, 0.6, 0.6, 0.2))
                .onHit((s, player) -> {
                    if (chainHit.add(player.getUUID())) {
                        strike(player, CHAIN_DAMAGE);
                        push(player, 0, 0.2, 0);
                    }
                })
                .start();
    }

    // 翼が裂ける: 4 本の柱がすべて満ちた。心臓は 6 秒落ちて、受ける傷 1.5 倍。柱は空に戻る
    private void startSplit(ServerLevel server) {
        splitTicks = SPLIT_TICKS;
        entityData.set(DATA_SPLIT, true);
        action = Action.NONE;
        actionTicks = 0;
        Telegraph.cancelAll(this);
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

    // 羽ばたき (1.5 秒): 翼を高く上げ、風穴の粒が下を向く。次の二つが印になる:
    //  - 風穴の下降風: 4 つの風穴の円 (半径 2.1)。円の中の空中の人に 6 と下向きの力 (昇り風に乗っている人への技)
    //  - 突風の輪: 心臓から広がる輪 (3 人から二重)。跳べば当たらない。輪には安全な筋が 2 本あり、そこは何もない。当たると 6 と外への押し
    // 2 秒、昇り風が止まる
    private void tickWingbeat(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.WINGBEAT.get(), 2.6f, 1f);
            if (isBound()) {
                for (int k = 0; k < 4; k++) {
                    Vec3 h = StormHeartArena.hole(k);
                    tg(server, TelegraphShape.circle(h.x, h.z, DOWNDRAFT_RADIUS), Action.WINGBEAT.warnTicks, 0x6FA8E8)
                            .reach(StormHeartArena.UPDRAFT_TOP - StormHeartArena.FLOOR + 3)
                            .victims(p -> canReach(p) && airborne(p))
                            .onHit((s, player) -> {
                                strike(player, WINGBEAT_DAMAGE);
                                player.setDeltaMovement(player.getDeltaMovement().x * 0.3, -1.0, player.getDeltaMovement().z * 0.3);
                                player.hurtMarked = true;
                            })
                            .start();
                }
            }
            double gapBase = random.nextDouble() * Math.PI * 2;
            double[] gaps = {gapBase, gapBase + Math.PI};
            double gapHalf = Math.toRadians(GUST_GAP_DEG);
            double reach = Math.max(8, roomRadius() - 1);
            for (double gap : gaps) {
                // 安全な筋 (見た目だけ)
                tg(server, TelegraphShape.cone(getX(), getZ(), gap, reach, gapHalf), Action.WINGBEAT.warnTicks, 0x58FF9A).start();
            }
            int rings = gustRings(present(server));
            for (int i = 0; i < rings; i++) {
                int active = (int) Math.ceil((reach - 2.5) / 0.55);
                tg(server, TelegraphShape.wave(getX(), getZ(), 2.5, reach, 0.9, gaps, gapHalf), Action.WINGBEAT.warnTicks + i * 14, 0xA8D8FF)
                        .active(active).jumpClear(0.9)
                        .onHit((s, player) -> {
                            strike(player, WINGBEAT_DAMAGE);
                            Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                            player.knockback(0.7, -to.x, -to.z);
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        if (actionTicks == 30) {
            updraftStopped = true;
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 80, 6, 0.5, 6, 0.2);
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
        if (actionTicks >= 76 + 14 * (gustRings(present(server)) - 1)) {
            finish();
        }
    }

    // 帯電の床 (1.5 秒): 床の円 (半径 3) の中を青い火花が這い、3 秒のあいだ 0.5 秒ごとに、円に立つ人へ 3 と鈍足 I。
    // **昇り風の中 (空中) なら当たらない**。円の数は 3 → 7 (人数 + 2)。人の足もとのほか、残りは部屋のあちこち。昇り風は強まる
    private void tickCharge(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.CHARGE.get(), 2.4f, 1f);
            updraftStrong = true;
            int count = chargePads(present(server));
            List<Vec3> pads = new ArrayList<>(pickSpots(server, count));
            int guard = 0;
            while (pads.size() < count && guard++ < 60) {
                Vec3 c = randomSpot(CHARGE_RADIUS + 0.5);
                boolean ok = true;
                for (Vec3 q : pads) {
                    if (Math.hypot(c.x - q.x, c.z - q.z) < CHARGE_RADIUS * 1.8) {
                        ok = false;
                    }
                }
                if (ok) {
                    pads.add(c);
                }
            }
            for (Vec3 c : pads) {
                tg(server, TelegraphShape.circle(c.x, c.z, CHARGE_RADIUS), Action.CHARGE.warnTicks, 0x5A8CFF)
                        .active(CHARGE_ACTIVE).repeatEvery(10).jumpClear(0.6)
                        .onHit((s, player) -> {
                            strike(player, CHARGE_DAMAGE);
                            player.invulnerableTime = 0;
                            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
                        })
                        .start();
            }
        }
        if (actionTicks >= 30 + CHARGE_ACTIVE + 10) {
            finish();
        }
    }

    // 羽根の雨 (1.2 秒): 人の位置に白い輪 (半径 2。空中の人にはその高さに) が満ちて、羽根が降る。10 と下への力。
    // 輪の高さから 1.5 ずれれば当たらない。3 人から、人のいない所にも 2〜3 か所
    private void tickFeather(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.FEATHER.get(), 2.2f, 1f);
            List<Vec3> spots = pickSpots(server, 6);
            for (int i = 0; i < extraStrikes(present(server)); i++) {
                spots.add(randomSpot(2.5));
            }
            for (Vec3 spot : spots) {
                double x = spot.x;
                double y = spot.y;
                double z = spot.z;
                tg(server, TelegraphShape.circle(x, z, FEATHER_RADIUS), Action.FEATHER.warnTicks, 0xF0F8FF)
                        .floor(y).reach(1.5)
                        .onImpact(s -> {
                            s.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.4f, 1.6f);
                            s.sendParticles(ParticleTypes.WHITE_ASH, x, y + 1.5, z, 40, 1.2, 1.0, 1.2, 0.05);
                            warn(s, ParticleTypes.END_ROD, x, y + 1.0, z, 10, 0.8, 0.5, 0.8, 0.02);
                        })
                        .onHit((s, player) -> {
                            strike(player, FEATHER_DAMAGE);
                            push(player, 0, -0.5, 0);
                        })
                        .start();
            }
        }
        if (actionTicks < 24 && actionTicks % 3 == 0) {
            warn(server, ParticleTypes.WHITE_ASH, getX(), core().y, getZ(), 6, 3.0, 0.6, 3.0, 0);
        }
        if (actionTicks >= 34) {
            finish();
        }
    }

    // ---------------------------------------------------------------- 段階 3
    // 閃光 (2.0 秒): 心臓が光を吸い込み (画面が暗くなる)、高まる音、画面の眼の印。人のいる方へ光の扇 (半角 22 度・射程 13) が満ちる。
    // 扇の中で、心臓の方を向いている (視線から 60 度以内) 人は盲目 3 秒と 12。扇から出る・背を向ける・柱の陰 (心臓との間にブロックがある) のどれかでよい。
    // 扇は人数で 1 → 4 本 (別の人へ)
    private void tickFlash(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.FLASH.get(), 3f, 1f);
            StormHeartNetwork.flash(reachable(server, 32), Action.FLASH.warnTicks, false);
            flashSeen.clear();
            for (Vec3 spot : pickSpots(server, flashFans(present(server)))) {
                double angle = Telegraph.angleOf(spot.x - getX(), spot.z - getZ());
                tg(server, TelegraphShape.cone(getX(), getZ(), angle, FLASH_RANGE, Math.toRadians(FLASH_HALF_ANGLE)), Action.FLASH.warnTicks, 0xFFFAE0)
                        .victims(p -> canReach(p) && flashHits(level(), p.getEyePosition(), p.getViewVector(1f), core(), p))
                        .onHit((s, player) -> {
                            strike(player, FLASH_DAMAGE);
                            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                            flashSeen.add(player);
                        })
                        .start();
            }
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
        }
        if (actionTicks == 42) {
            // 扇の当たりは予告の最後の tick (40) で済んでいる。当たった人には白い瞬き
            StormHeartNetwork.flashResult(reachable(server, 32), new ArrayList<>(flashSeen));
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

    // 雷の網 (1.5 秒): 4 本の柱の先が光り、柱と柱の間に線。心臓を中心に、雷の腕 (幅 2・長さ 13) が回る。振れる扇を床に描いて満ち、
    // そのあと腕が 4 秒かけてその扇を一度通る。**跳べば越えられる**。触れると 14。腕は 2 本 → 3 本 → 4 本 (人数。扇の合計は 180 度のまま)
    private void tickWeb(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, StormHeartSetup.WEB.get(), 2.6f, 1f);
            int arms = webArms(present(server));
            double base = random.nextDouble() * Math.PI * 2;
            armSpan = Math.PI / arms;
            armStart = new double[arms];
            for (int i = 0; i < arms; i++) {
                double mid = base + i * 2 * Math.PI / arms;
                armStart[i] = mid - armSpan / 2;
                double armAngle = mid;
                tg(server, TelegraphShape.sweep(centerX(), centerZ(), armStart[i], armSpan, WEB_REACH, WEB_HALF_WIDTH), Action.WEB.warnTicks, 0x6FA8E8)
                        .active(WEB_TICKS).jumpClear(WEB_JUMP)
                        .onHit((s, player) -> {
                            strike(player, WEB_DAMAGE);
                            Vec3 to = player.position().subtract(centerX(), 0, centerZ());
                            double a = Math.atan2(to.z, to.x) + Math.PI / 2;
                            push(player, Math.cos(a) * 0.4, 0.2, Math.sin(a) * 0.4);
                        })
                        .start();
            }
        }
        if (actionTicks < 30 && actionTicks % 4 == 0 && isBound()) {
            for (int i = 0; i < 4; i++) {
                Vec3 a = StormHeartArena.rodTip(i);
                warn(server, ParticleTypes.ELECTRIC_SPARK, a.x, a.y, a.z, 3, 0.2, 0.2, 0.2, 0.1);
                Vec3 b = StormHeartArena.rodTip(i == 0 ? 1 : i == 1 ? 3 : i == 3 ? 2 : 0);
                for (double t = 0; t <= 1; t += 0.12) {
                    Vec3 q = a.lerp(b, t);
                    warn(server, dust(0xAAD2FF, 0.9f), q.x, q.y - 0.4, q.z, 1, 0, 0, 0, 0);
                }
            }
        }
        // 回っている腕の稲妻 (印の上を通る)
        if (actionTicks >= Action.WEB.warnTicks && actionTicks <= Action.WEB.warnTicks + WEB_TICKS && actionTicks % 2 == 0) {
            double progress = (double) (actionTicks - Action.WEB.warnTicks) / WEB_TICKS;
            for (double start : armStart) {
                double ang = start + armSpan * progress;
                for (double r = 2.0; r <= WEB_REACH; r += 1.3) {
                    double x = centerX() + Math.cos(ang) * r;
                    double z = centerZ() + Math.sin(ang) * r;
                    double fy = floorY();
                    warn(server, dust(0xD8ECFF, 1.3f), x, fy + 0.5, z, 1, 0, 0.1, 0, 0);
                    warn(server, dust(0xFFFFFF, 1.3f), x, fy + 1.4, z, 1, 0, 0.1, 0, 0);
                    if (random.nextFloat() < 0.12f) {
                        warn(server, ParticleTypes.ELECTRIC_SPARK, x, fy + 1.2, z, 1, 0.1, 0.5, 0.1, 0.05);
                    }
                }
            }
        }
        if (actionTicks >= Action.WEB.warnTicks + WEB_TICKS + 6) {
            finish();
        }
    }

    // 招雷の連打 (1.0 秒ごとに 3 回): 人の足もとに半径 1.5 の円が満ちて (1.0 秒) 落ちる。3 人から、人のいない所にも 2〜3 か所。
    // 円は始めの位置で決まり、そのあとは追わない。柱のそばなら柱へ (傷なし。段階 3 では柱は満ちない)
    private void tickBarrage(ServerLevel server) {
        if (actionTicks == 1 || actionTicks == 21 || actionTicks == 41) {
            sound(server, StormHeartSetup.BARRAGE.get(), 2.2f, 1f + actionTicks / 60f);
            List<Vec3> spots = pickSpots(server, 6);
            for (int i = 0; i < extraStrikes(present(server)); i++) {
                spots.add(randomSpot(2.5));
            }
            for (Vec3 spot : spots) {
                lightningCircle(server, spot.x, floorY(), spot.z, SUMMON_RADIUS, 20, BARRAGE_DAMAGE, false);
            }
            if (actionTicks > 1) {
                StormHeartNetwork.warn(reachable(server, 32), Action.BARRAGE, 20);
            }
        }
        if (actionTicks >= 66) {
            finish();
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
        double cy = getY() + CORE_OFFSET;
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
