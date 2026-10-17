package jp.revati.skyabyss.titan.ember.heart;

import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.lore.EmberLore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 余燼の心臓: 余燼の巨神の最終ボス (13 の 5.3節)。熾の底の中央の台の上に、宙に浮いて動かず、向きだけ変える。
// 体力の割合で三段階 (66% 未満で段階 2、33% 未満で段階 3)。変わり目は 3 秒の咆哮で、その間は傷を受けず輪が押し返す (岩鯨・潮殻と同じ)。
//  段階 1 熔岩の殻     : 火の息 (線) / 熱の脈 (床を走る輪) / 熔け玉 (落ちる玉と燃える床)。主題は「熱を読む」
//  段階 2 熔け落ちる殻 : 崩れる床 (区画が溶岩に沈む) / 鞴の嵐 (中心へ引く) / 灰の礫 (人を追う礫)。受ける傷 1.2 倍。主題は「立つ場所が動く」
//  段階 3 余燼         : 冷え (熱が 1 秒に −8、熱 0 で凍え) / 残り火 (温まる場所) / 消えかけの鼓動 (中央の輪) / 灰の帳 (見通しが悪い)。
//                        主題は「熱が足りない」 (ここまでの「熱をためすぎない」の逆)
// 段階は同期データで全員の画面に届き、模型 (段階ごとの geo) とボスバーの見た目を決める。
// 強さは 03 の 9.8節の余燼の段 (体力 790、防御 9 / 靭性 7。想定は Lv 28〜30)
public class EmberHeart extends GuardMob {
    public static final int PHASE_COUNT = 3;
    public static final float PHASE_TWO_AT = 0.66f;
    public static final float PHASE_THREE_AT = 0.33f;
    public static final int ROAR_TICKS = 60;
    public static final float MAX_HEALTH = 790f;
    // 段階 2 (熔け落ちる殻) で受ける傷の倍率
    public static final float EXPOSED_TAKEN = 1.2f;

    // 技の傷 (防具で減る前。13 の 5.3節の表の値)
    public static final float BREATH_DAMAGE = 18f;
    public static final float BREATH_HEAT = 15f;
    public static final float PULSE_DAMAGE = 13f;
    public static final float PULSE_HEAT = 10f;
    public static final float ORB_DAMAGE = 15f;
    public static final float ORB_FLOOR_DAMAGE = 3f;
    public static final float ORB_FLOOR_HEAT = 5f;
    public static final float GALE_DAMAGE = 16f;
    public static final float PELLET_DAMAGE = 10f;
    public static final float FADING_DAMAGE = 22f;
    public static final float EMBER_BURN_DAMAGE = 5f;
    public static final float EMBER_HEAT_PER_SECOND = 12f;
    // 段階 3 の冷え: 熱が 1 秒に −8。0 で凍え (移動 −30%、攻撃の速さ −20%)、10 を超えると解ける
    public static final float CHILL_PER_SECOND = 8f;
    public static final float CHILL_FREEZE_AT = 0f;
    public static final float CHILL_THAW_ABOVE = 10f;
    public static final double CHILL_MOVE = -0.30;
    public static final double CHILL_ATTACK_SPEED = -0.20;
    // 引く速さ (ブロック/tick。鞴の嵐は歩けば逆らえる)
    public static final double GALE_PULL = 0.12;
    // 残り火: 半径 2.5、8 秒、四つ
    public static final double EMBER_RADIUS = 2.5;
    public static final int EMBER_TICKS = 160;
    public static final int EMBER_COUNT = 4;
    // 技の間 (tick): 段階 1 は 2.5〜3.3 秒、段階 2 は 2.0〜2.8 秒、段階 3 は 1.8〜2.4 秒
    public static final int[] GAP_MIN = {50, 40, 36};
    public static final int[] GAP_SPAN = {17, 17, 13};

    static final ResourceLocation CHILL_SPEED_ID = EmberIds.id("ember_heart_chill_speed");
    static final ResourceLocation CHILL_ATTACK_ID = EmberIds.id("ember_heart_chill_attack");

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(EmberHeart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ROARING = SynchedEntityData.defineId(EmberHeart.class, EntityDataSerializers.BOOLEAN);

    public enum Action {
        NONE(null), ROAR(null),
        BREATH("breath"), PULSE("pulse"), ORB("orb"),
        FLOOR("floor"), GALE("gale"), PELLET("pellet"),
        CHILL("chill"), EMBERS("embers"), FADING("fading"), VEIL("veil");

        final String anim;

        Action(String anim) {
            this.anim = anim;
        }
    }

    // 段階ごとに選ぶ技 (冷えは段階 3 に入った瞬間から効く常時の技で、選ぶ袋には入らない)
    public static final Action[] PHASE_ONE = {Action.BREATH, Action.PULSE, Action.ORB};
    public static final Action[] PHASE_TWO = {Action.FLOOR, Action.GALE, Action.PELLET};
    public static final Action[] PHASE_THREE = {Action.EMBERS, Action.FADING, Action.VEIL};

    // 床を走る輪 (熱の脈・咆哮の押し返し)。跳べば当たらない
    private static final class Ring {
        double radius = 1.5;
        final double floorY;
        final boolean harmful;
        final Set<UUID> hit = new HashSet<>();

        Ring(double floorY, boolean harmful) {
            this.floorY = floorY;
            this.harmful = harmful;
        }
    }

    // 熔け玉の予告のあとに落ちる玉
    private record Orb(double x, double z, int impactAt) {
    }

    // 熔け玉が落ちたあとの燃える床 (4 秒)
    private record Blaze(double x, double z, int endAt) {
    }

    // 残り火 (半径 2.5、8 秒)
    private record Ember(double x, double z, int endAt) {
    }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private Action last = Action.NONE;
    private int actionTicks;
    private int cooldown = 40;
    private float attackYaw;
    private boolean defeated;
    private final List<Ring> rings = new ArrayList<>();
    private final List<Orb> orbs = new ArrayList<>();
    private final List<Blaze> blazes = new ArrayList<>();
    private final List<Ember> embers = new ArrayList<>();
    // 段階 2 の床の自動の崩れ: 次までの tick と、前に沈めた扇
    private int floorTimer = 100;
    private int lastSector = 2;
    // 灰の帳が明けるまでの tick (サーバー側。画面は Veil のパケット)
    private int veilLeft;
    // 凍えている人 (熱 0 で入り、10 を超えて出る)
    private final Set<UUID> chilled = new HashSet<>();
    @Nullable
    private AABB arena;
    private float toughness = 1f;
    // 自己テスト用: 技を自分で選ばない (forceAction だけで動かす)
    private boolean holdAttacks;

    public EmberHeart(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ember_heart", "roar_1", "roar_2", "breath", "pulse", "orb", "floor", "gale", "pellet",
                "chill", "embers", "fading", "veil");
        this.xpReward = 400;
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 16)
                .add(Attributes.ARMOR, 9)
                .add(Attributes.ARMOR_TOUGHNESS, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 0);
        builder.define(DATA_ROARING, false);
    }

    // 段階 (0 から 2)
    public int getPhase() {
        return entityData.get(DATA_PHASE);
    }

    public boolean isRoaring() {
        return entityData.get(DATA_ROARING);
    }

    // テスト用のコマンドと自己テストから: 段階を直接決める
    public void forcePhase(int phase) {
        int next = Mth.clamp(phase, 0, PHASE_COUNT - 1);
        entityData.set(DATA_PHASE, next);
        entityData.set(DATA_ROARING, false);
        action = Action.NONE;
        cooldown = 20;
        if (level() instanceof ServerLevel server && isBound()) {
            if (next == 2) {
                EmberHeartArena.restoreAll(server);
                EmberHeartArena.setCold(server, true);
            } else {
                EmberHeartArena.setCold(server, false);
                clearChill(server);
            }
        }
    }

    public void holdAttacks(boolean hold) {
        this.holdAttacks = hold;
    }

    public Action currentAction() {
        return action;
    }

    public float bossBarProgress() {
        return bossBar.getProgress();
    }

    public Set<UUID> chilledPlayers() {
        return chilled;
    }

    public int emberCount() {
        return embers.size();
    }

    // 残り火の位置 (自己テスト)
    public List<Vec3> emberSpots() {
        List<Vec3> out = new ArrayList<>();
        for (Ember e : embers) {
            out.add(new Vec3(e.x(), EmberHeartArena.SURFACE, e.z()));
        }
        return out;
    }

    public int blazeCount() {
        return blazes.size();
    }

    public int veilTicks() {
        return veilLeft;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24f));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, this::canReach));
    }

    // 戦いの部屋と挑む人数 (EmberHeartBattle が湧かせるときに)
    public void bindArena(AABB arena, int challengers) {
        this.arena = arena;
        this.toughness = scaleFor(challengers);
    }

    // 人数による強さの倍率: 1 + 0.6 × (人数 − 1)。最大体力は変えず、受ける傷を割る (岩鯨・潮殻と同じ)
    public static float scaleFor(int challengers) {
        return 1f + 0.6f * (Math.max(1, challengers) - 1);
    }

    public float toughness() {
        return toughness;
    }

    public boolean isBound() {
        return arena != null;
    }

    // 試練で湧いた心臓は保存しない
    @Override
    public boolean isTrialMob() {
        return getTags().contains(EmberHeartBattle.TAG) || super.isTrialMob();
    }

    private boolean canReach(@Nullable LivingEntity entity) {
        return entity instanceof Player p && p.isAlive() && !p.isSpectator() && (arena == null || arena.contains(p.position()));
    }

    // 体力の割合からの段階
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

    // 段階 3 の鼓動の間 (tick)。体力の割合で、33% で 2.4 秒、0% で 4 秒へ遅くなる
    public static int beatInterval(float fraction) {
        float t = Mth.clamp(1f - fraction / PHASE_THREE_AT, 0f, 1f);
        return Math.round(48 + 32 * t);
    }

    // 鼓動の強さ (遅くなるほど弱くなる)
    public static float beatVolume(float fraction) {
        float t = Mth.clamp(1f - fraction / PHASE_THREE_AT, 0f, 1f);
        return 2.0f - 1.2f * t;
    }

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
        if (cooldown > 0) {
            cooldown--;
        }
        tickRings(server);
        tickOrbs(server);
        tickBlazes(server);
        tickEmbers(server);
        tickVeil(server);
        if (getPhase() == 2 && !isRoaring()) {
            tickChill(server);
            tickBeat(server);
        }
        if (getPhase() == 1 && !isRoaring() && isBound()) {
            tickFloorTimer(server);
        }

        if (action == Action.NONE) {
            if (getPhase() < phaseForHealth()) {
                begin(Action.ROAR, "roar_" + (getPhase() + 1));
                return;
            }
            if (holdAttacks) {
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
            case BREATH -> tickBreath(server);
            case PULSE -> tickPulse(server);
            case ORB -> tickOrb(server);
            case FLOOR -> tickFloor(server);
            case GALE -> tickGale(server);
            case PELLET -> tickPellet(server);
            case CHILL -> tickChillMove(server);
            case EMBERS -> tickEmbersMove(server);
            case FADING -> tickFading(server);
            case VEIL -> tickVeilMove(server);
            default -> {
            }
        }
    }

    // 段階の技から、直前と同じでないものを選ぶ
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

    // テスト用: 技を直接始める
    public void forceAction(Action next) {
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
        attackYaw = getYRot();
        playAction(animation != null ? animation : next.anim);
    }

    private void finish() {
        action = Action.NONE;
        actionTicks = 0;
        int phase = getPhase();
        cooldown = GAP_MIN[phase] + random.nextInt(GAP_SPAN[phase]);
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

    private static Vec3 forward(float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    private void sound(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        server.playSound(null, getX(), getY() + 2, getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    void strike(ServerPlayer player, float amount) {
        player.hurt(attackSource(), amount);
    }

    // 地形から噴く・燃える・燃え立つ傷 (防具で減る。熱の噴き)
    void burn(ServerLevel server, ServerPlayer player, float amount) {
        player.hurt(EmberHeartArena.source(server, EmberIds.HEAT_BURST, this), amount);
    }

    // 心臓の中心の高さ (模型の核)
    private double coreY() {
        return getY() + 2.0;
    }

    // 床の高さ: 部屋に縛られていれば部屋の床の面。そうでなければ真下へたどって最初の床
    double floorY() {
        if (arena != null) {
            return EmberHeartArena.SURFACE;
        }
        return floorAt(Mth.floor(getX()), Mth.floor(getZ()));
    }

    private int floorAt(int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Mth.floor(getY()) + 1, z);
        for (int i = 0; i < 24; i++) {
            pos.move(0, -1, 0);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return pos.getY() + 1;
            }
        }
        return Mth.floor(getY()) - 2;
    }

    List<ServerPlayer> reachable(ServerLevel server, double range) {
        return server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range, 12, range), this::canReach);
    }

    // 人の動きに足す (サーバーの速さは人の本当の速さではないので、クライアントで足す)
    private static void nudge(ServerPlayer player, double x, double y, double z) {
        EmberHeartNetwork.nudge(player, x, y, z);
    }

    // 水平に中心へ引く (2 tick ごとに呼ぶ。足す量は、地面の摩擦で釣り合う速さが speed になる値)
    private void pull(ServerPlayer player, double speed) {
        Vec3 to = new Vec3(getX() - player.getX(), 0, getZ() - player.getZ());
        double d = to.length();
        if (d < 1.5) {
            return;
        }
        Vec3 u = to.scale(1 / d);
        double add = speed * 0.9;
        nudge(player, u.x * add, 0, u.z * add);
    }

    // ---- 咆哮 (段階の変わり目) ----
    private void tickRoar(ServerLevel server) {
        if (actionTicks == 1) {
            entityData.set(DATA_ROARING, true);
            sound(server, EmberHeartSetup.ROAR.get(), 3f, 1f);
            server.sendParticles(ParticleTypes.LAVA, getX(), coreY(), getZ(), 40, 1.6, 1.4, 1.6, 0);
            server.sendParticles(ParticleTypes.FLAME, getX(), coreY(), getZ(), 60, 1.6, 1.4, 1.6, 0.1);
        }
        if (actionTicks == 22) {
            sound(server, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
            rings.add(new Ring(floorY(), false));
        }
        if (actionTicks % 4 == 0 && actionTicks < 56) {
            warn(server, dust(getPhase() == 0 ? 0xFFB040 : 0xE8E0D8, 1.3f), getX(), coreY(), getZ(), 6, 1.2, 1.2, 1.2, 0.05);
        }
        if (actionTicks >= ROAR_TICKS) {
            entityData.set(DATA_ROARING, false);
            int next = Math.min(PHASE_COUNT - 1, getPhase() + 1);
            entityData.set(DATA_PHASE, next);
            sound(server, SoundEvents.BEACON_ACTIVATE, 2.5f, next == 2 ? 0.7f : 1.4f);
            server.sendParticles(ParticleTypes.FLAME, getX(), coreY(), getZ(), 60, 1.8, 1.6, 1.8, 0.1);
            if (next == 1) {
                floorTimer = 80;
            } else if (next == 2) {
                enterPhaseThree(server);
            }
            finish();
            cooldown = 40;
        }
    }

    // 段階 3: 部屋が冷える。血管の光が消え、区画は元に戻り、熱が足りなくなる
    private void enterPhaseThree(ServerLevel server) {
        if (isBound()) {
            EmberHeartArena.restoreAll(server);
            EmberHeartArena.setCold(server, true);
        }
        sound(server, EmberHeartSetup.CHILL.get(), 3f, 1f);
        for (ServerPlayer player : reachable(server, 30)) {
            Notice.narrate(player, EmberLore.HEART_CHILL);
        }
    }

    // ---- 段階 1 ----

    // 火の息 (1.2 秒): 心臓から人へ伸びる赤い線。幅 3・長さ 22 の帯。傷 18、熱 +15
    private void tickBreath(ServerLevel server) {
        Vec3 f = forward(attackYaw);
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.BREATH.get(), 2f, 1f);
        }
        if (actionTicks < 24 && actionTicks % 2 == 0) {
            Vec3 side = new Vec3(-f.z, 0, f.x);
            for (double d = 2; d <= 22; d += 1.0) {
                for (int s = -1; s <= 1; s += 2) {
                    warn(server, dust(0xFF3A18, 0.9f), getX() + f.x * d + side.x * s * 1.5, fy + 0.15, getZ() + f.z * d + side.z * s * 1.5, 1, 0, 0, 0, 0);
                }
                if (((int) d) % 3 == actionTicks % 3) {
                    warn(server, dust(0xFFC060, 0.7f), getX() + f.x * d, fy + 0.15, getZ() + f.z * d, 1, 0, 0, 0, 0);
                }
            }
        }
        if (actionTicks >= 24 && actionTicks <= 34 && actionTicks % 2 == 0) {
            for (double d = 2; d <= 22; d += 1.5) {
                server.sendParticles(ParticleTypes.FLAME, getX() + f.x * d, fy + 0.8, getZ() + f.z * d, 6, 0.8, 0.4, 0.8, 0.05);
                server.sendParticles(ParticleTypes.SMOKE, getX() + f.x * d, fy + 1.2, getZ() + f.z * d, 2, 0.6, 0.4, 0.6, 0.02);
            }
        }
        if (actionTicks == 24) {
            sound(server, SoundEvents.BLAZE_SHOOT, 2f, 0.6f);
            for (ServerPlayer player : reachable(server, 24)) {
                Vec3 v = player.position().subtract(position()).multiply(1, 0, 1);
                double along = v.dot(f);
                double across = v.subtract(f.scale(along)).length();
                if (along > 0.5 && along < 22 && across < 1.5) {
                    strike(player, BREATH_DAMAGE);
                    EmberHeat.add(player, BREATH_HEAT, EmberHeat.Reason.HEART);
                    player.knockback(1.2, -f.x, -f.z);
                    player.hurtMarked = true;
                }
            }
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 熱の脈 (1.0 秒): 心臓が一度強く光り、床を走る輪。跳べば当たらない。傷 13、熱 +10
    private void tickPulse(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.PULSE.get(), 2f, 1f);
        }
        if (actionTicks < 20 && actionTicks % 3 == 0) {
            double fy = floorY();
            for (int a = 0; a < 360; a += 20) {
                double rad = Math.toRadians(a);
                warn(server, dust(0xFF8A20, 1.5f), getX() + Math.cos(rad) * 3, fy + 0.2, getZ() + Math.sin(rad) * 3, 1, 0, 0, 0, 0);
            }
            warn(server, dust(0xFFE8A0, 1.6f), getX(), coreY(), getZ(), 4, 0.5, 0.5, 0.5, 0.02);
        }
        if (actionTicks == 20) {
            sound(server, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.3f);
            double fy = floorY();
            server.sendParticles(ParticleTypes.FLAME, getX(), fy + 0.4, getZ(), 50, 2.0, 0.3, 2.0, 0.1);
            rings.add(new Ring(fy, true));
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 熔け玉 (1.5 秒): 人の足もとに橙の輪が 3 つ。落ちた所は半径 2 に傷 15、そのあと 4 秒燃える床
    private void tickOrb(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.ORB.get(), 2f, 1f);
            List<ServerPlayer> players = reachable(server, 24);
            int n = 0;
            if (!players.isEmpty()) {
                List<ServerPlayer> order = new ArrayList<>(players);
                java.util.Collections.shuffle(order, new java.util.Random(random.nextLong()));
                for (int i = 0; i < 3; i++) {
                    ServerPlayer p = order.get(i % order.size());
                    double x = p.getX();
                    double z = p.getZ();
                    if (i >= order.size()) {
                        double a = random.nextDouble() * Math.PI * 2;
                        double r = 2.5 + random.nextDouble() * 2.5;
                        x += Math.cos(a) * r;
                        z += Math.sin(a) * r;
                    }
                    orbs.add(new Orb(x, z, tickCount + 30));
                    n++;
                }
            }
        }
        if (actionTicks < 30 && actionTicks % 3 == 0) {
            warn(server, dust(0xFFA030, 1.1f), getX(), coreY() + 0.5, getZ(), 6, 0.7, 0.7, 0.7, 0.02);
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // ---- 段階 2 ----

    // 崩れる床 (2.0 秒): 区画の一つの床が赤く割れ、ぴしぴし鳴る。8 秒溶岩に沈み、また冷え殻に戻る。人の立つ扇を優先して割る
    private void tickFloor(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.FLOOR.get(), 1.4f, 1.2f);
            if (isBound()) {
                int k = EmberHeartArena.pickSector(server, lastSector);
                if (k >= 0 && EmberHeartArena.crack(server, k)) {
                    lastSector = k;
                }
            }
        }
        if (actionTicks < 40 && actionTicks % 4 == 0) {
            warn(server, dust(0xFF5018, 1.4f), getX(), coreY(), getZ(), 6, 1.0, 1.0, 1.0, 0.03);
        }
        if (actionTicks >= 44) {
            finish();
        }
    }

    // 段階 2 のあいだ、6〜8 秒ごとに次の区画を割る (同時に沈むのは最大 2 区画)
    private void tickFloorTimer(ServerLevel server) {
        if (--floorTimer > 0) {
            return;
        }
        floorTimer = 120 + random.nextInt(41);
        int k = EmberHeartArena.pickSector(server, lastSector);
        if (k >= 0 && EmberHeartArena.crack(server, k)) {
            lastSector = k;
        }
    }

    // 鞴の嵐 (1.5 秒): 部屋の風が心臓へ流れはじめる。2 秒のあいだ中心へ引き (歩けば逆らえる)、終わりに半径 6 で弾ける。傷 16 と押し
    private void tickGale(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.GALE.get(), 2.4f, 1f);
        }
        if (actionTicks < 70 && actionTicks % 2 == 0) {
            for (int k = 0; k < 16; k++) {
                double a = Math.toRadians(k * 22.5 + actionTicks * 3);
                double r = 14 - ((actionTicks * 0.45 + k * 1.3) % 12);
                warn(server, dust(0xFFB060, 1.2f), getX() + Math.cos(a) * r, fy + 0.4 + (k % 3) * 0.4, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (actionTicks >= 40) {
                for (int a = 0; a < 360; a += 20) {
                    double rad = Math.toRadians(a);
                    warn(server, dust(0xFF4A4A, 1.2f), getX() + Math.cos(rad) * 6, fy + 0.2, getZ() + Math.sin(rad) * 6, 1, 0, 0, 0, 0);
                }
            }
        }
        if (actionTicks >= 30 && actionTicks < 70 && actionTicks % 2 == 0) {
            for (ServerPlayer player : reachable(server, 22)) {
                pull(player, GALE_PULL);
            }
        }
        if (actionTicks == 70) {
            sound(server, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.7f);
            server.sendParticles(ParticleTypes.FLAME, getX(), fy + 0.5, getZ(), 100, 3.0, 0.5, 3.0, 0.2);
            for (ServerPlayer player : reachable(server, 8)) {
                Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                if (out.length() < 6 && player.getY() < coreY() + 2) {
                    strike(player, GALE_DAMAGE);
                    player.knockback(1.0, -out.x, -out.z);
                    player.hurtMarked = true;
                }
            }
        }
        if (actionTicks >= 82) {
            finish();
        }
    }

    // 灰の礫 (1.0 秒): 心臓から黒い礫が 5 つ浮き、人を追う (遅い。叩けば消える)
    private void tickPellet(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.PELLET.get(), 2f, 1f);
        }
        if (actionTicks < 20 && actionTicks % 2 == 0) {
            warn(server, dust(0x303030, 1.5f), getX(), coreY(), getZ(), 8, 0.9, 0.9, 0.9, 0.02);
        }
        if (actionTicks == 20) {
            List<ServerPlayer> players = reachable(server, 26);
            if (!players.isEmpty()) {
                players.sort(Comparator.comparingDouble(p -> p.distanceToSqr(this)));
                for (int i = 0; i < 5; i++) {
                    ServerPlayer p = players.get(i % players.size());
                    double a = Math.toRadians(attackYaw + 90 + (i - 2) * 50);
                    EmberAshPellet pellet = new EmberAshPellet(server, this, p,
                            new Vec3(getX() - Math.sin(a) * 2.2, coreY() + (i - 2) * 0.4, getZ() + Math.cos(a) * 2.2));
                    server.addFreshEntity(pellet);
                }
            }
        }
        if (actionTicks >= 32) {
            finish();
        }
    }

    // ---- 段階 3 ----

    // 冷え (段階 3 のあいだずっと): 熱が 1 秒に −8。熱 0 で凍え (移動 −30%、攻撃の速さ −20%)、熱 10 を超えると解ける。
    // 20 tick ごとに熱を落とし、10 tick ごとに凍えの出入りを見る
    private void tickChill(ServerLevel server) {
        if (!isBound()) {
            return;
        }
        boolean second = tickCount % 20 == 0;
        if (!second && tickCount % 10 != 0) {
            return;
        }
        for (ServerPlayer player : reachable(server, 30)) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (second) {
                EmberHeat.cool(player, CHILL_PER_SECOND);
            }
            float heat = EmberHeat.get(player);
            boolean was = chilled.contains(player.getUUID());
            if (!was && heat <= CHILL_FREEZE_AT) {
                setChilled(player, true);
                Notice.bar(player, ModText.hint(EmberIds.LANG_HEART + "frozen"));
            } else if (was && heat > CHILL_THAW_ABOVE) {
                setChilled(player, false);
            }
        }
    }

    // 凍えの出入り。移動と攻撃の速さの一時の修飾子と、画面の縁の霜
    void setChilled(ServerPlayer player, boolean on) {
        if (on) {
            chilled.add(player.getUUID());
        } else {
            chilled.remove(player.getUUID());
        }
        applyChill(player, on);
    }

    static void applyChill(ServerPlayer player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null) {
            speed.removeModifier(CHILL_SPEED_ID);
            if (on) {
                speed.addTransientModifier(new AttributeModifier(CHILL_SPEED_ID, CHILL_MOVE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if (attack != null) {
            attack.removeModifier(CHILL_ATTACK_ID);
            if (on) {
                attack.addTransientModifier(new AttributeModifier(CHILL_ATTACK_ID, CHILL_ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        EmberHeartNetwork.chill(player, on);
    }

    // 凍えをすべて解く (段階が戻る・倒れる・消える)
    void clearChill(ServerLevel server) {
        for (UUID id : new ArrayList<>(chilled)) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                applyChill(p, false);
            }
        }
        chilled.clear();
    }

    // 段階 3 の鼓動: 体力の割合で間が 2.4 秒 → 4 秒へ、音は弱くなる。拍の 0.6 秒あとに、ごく小さく、ずっと下から同じ拍が返る (音だけ。15 章の判断 5)
    private int nextBeat = 20;
    private int echoAt = -1;

    private void tickBeat(ServerLevel server) {
        if (tickCount >= nextBeat) {
            float frac = getHealth() / getMaxHealth();
            sound(server, EmberHeartSetup.BEAT.get(), beatVolume(frac), 0.8f + 0.2f * frac / PHASE_THREE_AT);
            nextBeat = tickCount + beatInterval(frac);
            echoAt = tickCount + 12;
        }
        if (echoAt > 0 && tickCount >= echoAt) {
            echoAt = -1;
            for (ServerPlayer player : reachable(server, 40)) {
                player.playNotifySound(EmberHeartSetup.ECHO.get(), SoundSource.HOSTILE, 0.12f, 0.9f);
            }
        }
    }

    // 冷えの合図 (テスト用 / 段階 3 に入る瞬間と同じ。部屋の血管の光が消え、暗くなる)
    private void tickChillMove(ServerLevel server) {
        if (actionTicks == 1) {
            enterPhaseThree(server);
        }
        if (actionTicks >= 30) {
            finish();
        }
    }

    // 残り火 (1.0 秒): 心臓から火の粉が 4 つ飛び、部屋の 4 か所に「残り火」(半径 2.5、8 秒)。中に立つと熱 +12/秒、2 秒ごとに小さく燃え立つ 5
    private void tickEmbersMove(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.EMBERS.get(), 2f, 1f);
        }
        if (actionTicks < 20 && actionTicks % 2 == 0) {
            warn(server, dust(0xFF9040, 1.2f), getX(), coreY(), getZ(), 6, 0.6, 0.6, 0.6, 0.05);
        }
        if (actionTicks == 20) {
            placeEmbers(server);
        }
        if (actionTicks >= 30) {
            finish();
        }
    }

    // 残り火を四つ置く: 人のそばを優先し (凍えて遠くまで行けない人のため)、残りは部屋のあちこち。沈んだ区画と割れた区画には置かない
    void placeEmbers(ServerLevel server) {
        List<ServerPlayer> players = reachable(server, 30);
        int placed = 0;
        java.util.List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : players) {
            if (placed >= EMBER_COUNT - 1) {
                break;
            }
            Vec3 at = emberSpot(server, p.getX(), p.getZ(), 3, 7, spots);
            if (at != null) {
                spots.add(at);
                placed++;
            }
        }
        int guard = 0;
        while (placed < EMBER_COUNT && guard++ < 60) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 5 + random.nextDouble() * 8;
            Vec3 at = emberSpot(server, EmberHeartArena.CX + 0.5 + Math.cos(a) * r, EmberHeartArena.CZ + 0.5 + Math.sin(a) * r, 0, 0, spots);
            if (at != null) {
                spots.add(at);
                placed++;
            }
        }
        for (Vec3 at : spots) {
            embers.add(new Ember(at.x, at.z, tickCount + EMBER_TICKS));
            EmberHeartArena.emberLight(server, at.x, at.z, true);
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 20, 0.6, 0.2, 0.6, 0.03);
        }
        // 心臓から火の粉が飛ぶ (見た目)
        for (Vec3 at : spots) {
            Vec3 from = new Vec3(getX(), coreY(), getZ());
            for (int i = 1; i <= 8; i++) {
                Vec3 p = from.add(at.subtract(from).scale(i / 8.0));
                warn(server, ParticleTypes.FLAME, p.x, p.y + Math.sin(i / 8.0 * Math.PI) * 1.5, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    @Nullable
    private Vec3 emberSpot(ServerLevel server, double x, double z, double minOffset, double maxOffset, List<Vec3> taken) {
        for (int tries = 0; tries < 12; tries++) {
            double px = x;
            double pz = z;
            if (maxOffset > 0) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = minOffset + random.nextDouble() * (maxOffset - minOffset);
                px += Math.cos(a) * r;
                pz += Math.sin(a) * r;
            }
            double dx = px - (EmberHeartArena.CX + 0.5);
            double dz = pz - (EmberHeartArena.CZ + 0.5);
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 13.5 || d < 1.5) {
                continue;
            }
            int k = EmberHeartArena.sectorAt(px, pz);
            if (k >= 0 && !EmberHeartArena.standing(k)) {
                continue;
            }
            boolean close = false;
            for (Vec3 o : taken) {
                if (Math.hypot(o.x - px, o.z - pz) < 3.5) {
                    close = true;
                    break;
                }
            }
            if (!close) {
                return new Vec3(px, EmberHeartArena.SURFACE, pz);
            }
        }
        return null;
    }

    // 消えかけの鼓動 (2.0 秒): 心臓が暗くなり、低い一拍。床に半径 6 の白い輪。傷 22 (輪の外にいれば当たらない)
    private void tickFading(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.FADING.get(), 2.5f, 1f);
        }
        if (actionTicks < 40 && actionTicks % 2 == 0) {
            for (int a = 0; a < 360; a += 10) {
                double rad = Math.toRadians(a);
                warn(server, dust(0xF4F0E8, 1.4f), getX() + Math.cos(rad) * 6, fy + 0.2, getZ() + Math.sin(rad) * 6, 1, 0, 0, 0, 0);
            }
            if (actionTicks > 20) {
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a + actionTicks * 4);
                    for (double r = 1.5; r < 6; r += 1.5) {
                        warn(server, dust(0x9A9088, 1.0f), getX() + Math.cos(rad) * r, fy + 0.15, getZ() + Math.sin(rad) * r, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
        if (actionTicks == 40) {
            sound(server, SoundEvents.WARDEN_HEARTBEAT, 3f, 0.5f);
            server.sendParticles(ParticleTypes.ASH, getX(), fy + 0.5, getZ(), 120, 4.0, 0.5, 4.0, 0.1);
            for (ServerPlayer player : reachable(server, 8)) {
                Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                if (out.length() < 6 && player.getY() < coreY() + 3) {
                    strike(player, FADING_DAMAGE);
                    player.knockback(0.8, -out.x, -out.z);
                    player.hurtMarked = true;
                }
            }
        }
        if (actionTicks == 52) {
            for (ServerPlayer player : reachable(server, 40)) {
                player.playNotifySound(EmberHeartSetup.ECHO.get(), SoundSource.HOSTILE, 0.22f, 0.85f);
            }
        }
        if (actionTicks >= 60) {
            finish();
        }
    }

    // 灰の帳 (1.5 秒の予告のあと 5 秒): 天井から灰が落ちはじめ、部屋が灰でおおわれ見通しが悪い。傷はない
    private void tickVeilMove(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.VEIL.get(), 2f, 1f);
        }
        if (actionTicks < 30 && actionTicks % 2 == 0) {
            ashFall(server, 6);
        }
        if (actionTicks == 30) {
            veilLeft = 100;
            for (ServerPlayer player : reachable(server, 30)) {
                EmberHeartNetwork.veil(player, 100);
            }
        }
        if (actionTicks >= 34) {
            finish();
        }
    }

    private void ashFall(ServerLevel server, int count) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * 14;
            warn(server, random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                    getX() + Math.cos(a) * r, EmberHeartArena.CEILING - 1 - random.nextDouble() * 3, getZ() + Math.sin(a) * r, 2, 0.3, 0.2, 0.3, 0.02);
        }
    }

    private void tickVeil(ServerLevel server) {
        if (veilLeft > 0) {
            veilLeft--;
            if (veilLeft % 2 == 0) {
                ashFall(server, 8);
            }
        }
    }

    // ---- 輪・玉・燃える床・残り火 (技の後も続く) ----

    private void tickRings(ServerLevel server) {
        for (Ring ring : rings) {
            ring.radius += 0.8;
            int points = Math.max(8, (int) (2 * Math.PI * ring.radius / 1.4));
            for (int i = 0; i < points; i++) {
                double a = i * 2 * Math.PI / points;
                warn(server, dust(ring.harmful ? 0xFF6A10 : 0xFFC878, 1.5f),
                        getX() + Math.cos(a) * ring.radius, ring.floorY + 0.5, getZ() + Math.sin(a) * ring.radius, 1, 0, 0, 0, 0);
            }
            double r = ring.radius + 2;
            AABB area = new AABB(getX() - r, ring.floorY - 1, getZ() - r, getX() + r, ring.floorY + 3, getZ() + r);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach)) {
                Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                double d = to.length();
                if (Math.abs(d - ring.radius) < 1.0 && player.getY() < ring.floorY + 0.9 && ring.hit.add(player.getUUID())) {
                    if (ring.harmful) {
                        strike(player, PULSE_DAMAGE);
                        EmberHeat.add(player, PULSE_HEAT, EmberHeat.Reason.HEART);
                    }
                    player.knockback(0.9, -to.x, -to.z);
                    player.hurtMarked = true;
                }
            }
        }
        rings.removeIf(w -> w.radius > 20);
    }

    private void tickOrbs(ServerLevel server) {
        if (orbs.isEmpty()) {
            return;
        }
        double fy = floorY();
        List<Orb> remaining = new ArrayList<>();
        for (Orb o : orbs) {
            if (tickCount >= o.impactAt()) {
                impactOrb(server, o, fy);
                continue;
            }
            if (tickCount % 3 == 0) {
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    warn(server, dust(0xFF8A20, 1.3f), o.x() + Math.cos(rad) * 2, fy + 0.15, o.z() + Math.sin(rad) * 2, 1, 0, 0, 0, 0);
                }
            }
            if (tickCount >= o.impactAt() - 10) {
                double drop = (o.impactAt() - tickCount) / 10.0;
                warn(server, ParticleTypes.FLAME, o.x(), fy + 1 + drop * 8, o.z(), 3, 0.1, 0.2, 0.1, 0);
                warn(server, ParticleTypes.LAVA, o.x(), fy + 1 + drop * 8, o.z(), 1, 0.1, 0.1, 0.1, 0);
            }
            remaining.add(o);
        }
        orbs.clear();
        orbs.addAll(remaining);
    }

    private void impactOrb(ServerLevel server, Orb o, double fy) {
        server.playSound(null, o.x(), fy, o.z(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 1.5f);
        server.sendParticles(ParticleTypes.FLAME, o.x(), fy + 0.4, o.z(), 40, 1.0, 0.2, 1.0, 0.1);
        server.sendParticles(ParticleTypes.LAVA, o.x(), fy + 0.6, o.z(), 14, 0.8, 0.2, 0.8, 0);
        AABB area = new AABB(o.x() - 2, fy - 1, o.z() - 2, o.x() + 2, fy + 2.5, o.z() + 2);
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach)) {
            if (Math.hypot(player.getX() - o.x(), player.getZ() - o.z()) <= 2.3) {
                strike(player, ORB_DAMAGE);
                nudge(player, 0, 0.4, 0);
            }
        }
        blazes.add(new Blaze(o.x(), o.z(), tickCount + 80));
        EmberHeartArena.emberLight(server, o.x(), o.z(), true);
    }

    private void tickBlazes(ServerLevel server) {
        if (blazes.isEmpty()) {
            return;
        }
        double fy = floorY();
        List<Blaze> remaining = new ArrayList<>();
        for (Blaze b : blazes) {
            if (tickCount >= b.endAt()) {
                EmberHeartArena.emberLight(server, b.x(), b.z(), false);
                continue;
            }
            if (tickCount % 3 == 0) {
                for (int i = 0; i < 4; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double r = Math.sqrt(random.nextDouble()) * 2.0;
                    warn(server, ParticleTypes.FLAME, b.x() + Math.cos(a) * r, fy + 0.15, b.z() + Math.sin(a) * r, 1, 0, 0.05, 0, 0.01);
                }
                for (int a = 0; a < 360; a += 45) {
                    double rad = Math.toRadians(a);
                    warn(server, dust(0xFF6A10, 1.0f), b.x() + Math.cos(rad) * 2, fy + 0.15, b.z() + Math.sin(rad) * 2, 1, 0, 0, 0, 0);
                }
            }
            if (tickCount % 20 == 0) {
                for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(b.x() - 2, fy - 1, b.z() - 2, b.x() + 2, fy + 1.4, b.z() + 2), this::canReach)) {
                    if (Math.hypot(player.getX() - b.x(), player.getZ() - b.z()) <= 2.0 && player.getY() < fy + 1.2) {
                        burn(server, player, ORB_FLOOR_DAMAGE);
                        EmberHeat.add(player, ORB_FLOOR_HEAT, EmberHeat.Reason.HEART);
                    }
                }
            }
            remaining.add(b);
        }
        blazes.clear();
        blazes.addAll(remaining);
    }

    private void tickEmbers(ServerLevel server) {
        if (embers.isEmpty()) {
            return;
        }
        double fy = floorY();
        List<Ember> remaining = new ArrayList<>();
        for (Ember e : embers) {
            if (tickCount >= e.endAt()) {
                EmberHeartArena.emberLight(server, e.x(), e.z(), false);
                continue;
            }
            if (tickCount % 2 == 0) {
                warn(server, ParticleTypes.FLAME, e.x() + Mth.nextDouble(random, -1.5, 1.5), fy + 0.15, e.z() + Mth.nextDouble(random, -1.5, 1.5), 1, 0, 0.05, 0, 0.01);
            }
            if (tickCount % 4 == 0) {
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    warn(server, dust(0xFFA040, 1.1f), e.x() + Math.cos(rad) * EMBER_RADIUS, fy + 0.15, e.z() + Math.sin(rad) * EMBER_RADIUS, 1, 0, 0, 0, 0);
                }
                warn(server, ParticleTypes.SMALL_FLAME, e.x(), fy + 1.0, e.z(), 1, 0.3, 0.5, 0.3, 0.02);
            }
            if (tickCount % 20 == 0) {
                boolean burst = (e.endAt() - tickCount) % 40 == 0;
                for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(e.x() - EMBER_RADIUS, fy - 1, e.z() - EMBER_RADIUS, e.x() + EMBER_RADIUS, fy + 2, e.z() + EMBER_RADIUS), this::canReach)) {
                    if (Math.hypot(player.getX() - e.x(), player.getZ() - e.z()) <= EMBER_RADIUS && player.getY() < fy + 1.5) {
                        EmberHeat.add(player, EMBER_HEAT_PER_SECOND, EmberHeat.Reason.HEART);
                        if (burst) {
                            burn(server, player, EMBER_BURN_DAMAGE);
                            server.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.3, 0.2, 0.3, 0.05);
                        }
                    }
                }
            }
            remaining.add(e);
        }
        embers.clear();
        embers.addAll(remaining);
    }

    // 技の残り (輪・玉・燃える床・残り火・灰の帳) をすべて消す
    void clearHazards(ServerLevel server) {
        rings.clear();
        orbs.clear();
        for (Blaze b : blazes) {
            EmberHeartArena.emberLight(server, b.x(), b.z(), false);
        }
        blazes.clear();
        for (Ember e : embers) {
            EmberHeartArena.emberLight(server, e.x(), e.z(), false);
        }
        embers.clear();
        veilLeft = 0;
    }

    // 咆哮の間は傷を受けない (殻が鳴って弾く)。段階 2 の熔け落ちた殻は 1.2 倍。人数の分だけ割る
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRoaring()) {
            if (level() instanceof ServerLevel server) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.6f);
                server.sendParticles(ParticleTypes.LAVA, getX(), coreY(), getZ(), 6, 0.8, 0.8, 0.8, 0);
            }
            return false;
        }
        return super.hurt(source, takenFor(getPhase(), amount, toughness));
    }

    // 受ける傷 (段階と人数で)。自己テストからも使う
    public static float takenFor(int phase, float amount, float toughness) {
        return (phase == 1 ? amount * EXPOSED_TAKEN : amount) / toughness;
    }

    private void updateBossBarViewers() {
        for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
            if (!player.isAlive() || player.distanceToSqr(this) > 48 * 48 || player.level() != level()) {
                bossBar.removePlayer(player);
            }
        }
        if (level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.getPlayers(p -> p.distanceToSqr(this) < 40 * 40)) {
                bossBar.addPlayer(player);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel server) {
            clearHazards(server);
            clearChill(server);
        }
        super.remove(reason);
    }

    // 倒れたとき: 試練で湧いた心臓だけが、報酬・進み・記録を EmberHeartBattle へ渡す
    @Override
    public void die(DamageSource source) {
        if (defeated) {
            return;
        }
        defeated = true;
        super.die(source);
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), EmberHeartSetup.DEATH.get(), SoundSource.HOSTILE, 3f, 1f);
            server.sendParticles(ParticleTypes.ASH, getX(), coreY(), getZ(), 160, 1.5, 1.6, 1.5, 0.1);
            server.sendParticles(ParticleTypes.SMOKE, getX(), coreY(), getZ(), 60, 1.5, 1.5, 1.5, 0.05);
            clearHazards(server);
            clearChill(server);
            if (server.dimension() == EmberIds.BACK && getTags().contains(EmberHeartBattle.TAG)) {
                EmberHeartBattle.onDefeated(server, this);
            }
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

    // 段階 3 の鼓動は tickBeat が鳴らす。ほかの段階の環境音 (どくん)
    @Override
    protected SoundEvent getAmbientSound() {
        return getPhase() == 2 ? null : EmberHeartSetup.BEAT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.EMBER_HEART_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.LAVA_EXTINGUISH;
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    // 光の粒 (クライアント): 段階 1 は火の粉と垂れる溶岩、段階 2 は白熱の火花と滴、段階 3 は灰と少しの赤い火の粉 (疎な段階 3 を埋める)
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            return;
        }
        double cy = getY() + 2.0;
        int phase = getPhase();
        if (phase == 0) {
            if (random.nextFloat() < 0.2f) {
                level().addParticle(ParticleTypes.FLAME, getX() + Mth.nextDouble(random, -1.2, 1.2), cy + Mth.nextDouble(random, 0.5, 2.0),
                        getZ() + Mth.nextDouble(random, -1.2, 1.2), 0, 0.03, 0);
            }
        } else if (phase == 1) {
            if (random.nextFloat() < 0.4f) {
                level().addParticle(dust(0xFFE070, 1.0f), getX() + Mth.nextDouble(random, -1.6, 1.6), cy + Mth.nextDouble(random, -1.0, 2.0),
                        getZ() + Mth.nextDouble(random, -1.6, 1.6), 0, 0.03, 0);
            }
            if (random.nextFloat() < 0.25f) {
                level().addParticle(ParticleTypes.DRIPPING_LAVA, getX() + Mth.nextDouble(random, -1.8, 1.8), cy - 0.8,
                        getZ() + Mth.nextDouble(random, -1.8, 1.8), 0, 0, 0);
            }
        } else {
            if (random.nextFloat() < 0.5f) {
                level().addParticle(ParticleTypes.ASH, getX() + Mth.nextDouble(random, -2.2, 2.2), cy + Mth.nextDouble(random, -1.6, 2.4),
                        getZ() + Mth.nextDouble(random, -2.2, 2.2), 0, -0.01, 0);
            }
            if (random.nextFloat() < 0.18f) {
                level().addParticle(dust(0xE04020, 0.9f), getX() + Mth.nextDouble(random, -1.0, 1.0), cy + Mth.nextDouble(random, 0, 2.0),
                        getZ() + Mth.nextDouble(random, -1.0, 1.0), 0, 0.03, 0);
            }
        }
    }

    // 画面の外へ出たことがないよう、ログ用
    static void logBorn(int challengers) {
        SkyAbyss.LOGGER.info("The heart of the embers woke with {} challenger(s)", challengers);
    }
}
