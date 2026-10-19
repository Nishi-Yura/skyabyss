package jp.revati.skyabyss.titan.ember.heart;

import jp.revati.skyabyss.util.Players;

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

import jp.revati.skyabyss.combat.HeartBalance;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.lore.EmberLore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 余燼の心臓: 余燼の巨神の最終ボス (13 の 5.3節)。熾の底の中央の台の上に、宙に浮いて動かず、向きだけ変える。
// 体力の割合で三段階 (66% 未満で段階 2、33% 未満で段階 3)。変わり目は 3 秒の咆哮で、その間は傷を受けず輪が押し返す (岩鯨・潮殻と同じ)。
//  段階 1 熔岩の殻     : 火の息 (線) / 熱の脈 (床を走る輪) / 熔け玉 (落ちる玉と燃える床)。主題は「熱を読む」
//  段階 2 熔け落ちる殻 : 崩れる床 (区画が溶岩に沈む) / 鞴の嵐 (中心へ引く) / 灰の礫 (人を追う礫)。受ける傷 1.2 倍。主題は「立つ場所が動く」
//  段階 3 余燼         : 冷え (熱が 1 秒に −8、熱 0 で凍え) / 残り火 (温まる場所) / 消えかけの鼓動 (中央の輪) / 灰の帳 (見通しが悪い)。
//                        主題は「熱が足りない」 (ここまでの「熱をためすぎない」の逆)
// 体力 15% 未満で最終段階 (憤怒、模型は段階 3 のまま): 3 秒の咆哮のあと、技が二つ重なる組 (PAIRS_FURY) だけを短い間で出す。
//  憤怒だけの新しい技「炉打ち」(FORGE): 鎚が部屋を三度回って打ち (扇 90 度を 120 度ずつ)、最後に台の上 (中央の円) を打つ。
// 段階 2・3 でも、技の組 (COMBO) が割り込む (二つ目の予告は 16〜30 tick 遅れて始まり、どの予告も 0.8 秒以上)。
// 段階は同期データで全員の画面に届き、模型 (段階ごとの geo) とボスバーの見た目を決める。
// 強さ: 最大体力 HeartBalance.MAX_HP (1000) × 基本の硬さ BASE_RESIST = 実質 2290 (02 の 5.6、13 の 8.2)。防御 9 / 靭性 7。想定は Lv 28〜30
public class EmberHeart extends HeartBoss {
    public static final int PHASE_COUNT = 3;
    public static final float PHASE_TWO_AT = 0.66f;
    public static final float PHASE_THREE_AT = 0.33f;
    public static final int ROAR_TICKS = 60;
    public static final float MAX_HEALTH = HeartBalance.MAX_HP;
    // 基本の硬さ: 受ける傷をこの値で割る (実質の体力 = 1000 × 2.29 = 2290。人数の分はさらに HeartBalance.partyScale を掛ける)
    public static final float BASE_RESIST = 2.29f;
    // 段階 2 (熔け落ちる殻) で受ける傷の倍率
    public static final float EXPOSED_TAKEN = 1.2f;

    // 技の傷 (防具で減る前。13 の 8.3節の表の値)。最も重い一撃 (消えかけの鼓動 37) が、想定の防具 22 / 強靭 16 で想定の体力 38 の 3 割弱
    public static final float BREATH_DAMAGE = 32f;
    public static final float BREATH_HEAT = 15f;
    public static final float PULSE_DAMAGE = 24f;
    public static final float PULSE_HEAT = 10f;
    public static final float ORB_DAMAGE = 28f;
    // 燃える床の燃え立ち一回 (1 秒の予告つき、3 回)。小さな傷は防具でほとんど消えるので、倍にはしない
    public static final float ORB_FLOOR_DAMAGE = 5f;
    public static final float ORB_FLOOR_HEAT = 5f;
    public static final float GALE_DAMAGE = 30f;
    public static final float PELLET_DAMAGE = 18f;
    public static final float FADING_DAMAGE = 37f;
    // 残り火の燃え立ち (温まる代わりの小さな傷。1 秒の予告つき)
    public static final float EMBER_BURN_DAMAGE = 8f;
    public static final float EMBER_HEAT_PER_SECOND = 12f;
    // 段階 3 の冷え: 熱が 1 秒に −8。0 で凍え (移動 −30%、攻撃の速さ −20%)、10 を超えると解ける
    public static final float CHILL_PER_SECOND = 8f;
    public static final float CHILL_FREEZE_AT = 0f;
    public static final float CHILL_THAW_ABOVE = 10f;
    public static final double CHILL_MOVE = -0.30;
    public static final double CHILL_ATTACK_SPEED = -0.20;
    // 引く速さ (ブロック/tick。鞴の嵐は歩けば逆らえる)
    public static final double GALE_PULL = 0.12;

    // 予告の数値 (warn は tick。予告の長さは Telegraph.MIN_WARN_TICKS = 16 以上)
    public static final int BREATH_WARN = 24;
    public static final double BREATH_LENGTH = 16;
    public static final double BREATH_HALF_WIDTH = 1.5;
    public static final int PULSE_WARN = 20;
    public static final double PULSE_START = 3.2;
    public static final double PULSE_REACH = 15.5;
    public static final double LANE_HALF = Math.toRadians(20);
    public static final int ORB_WARN = 30;
    public static final double ORB_RADIUS = 2.3;
    public static final double BLAZE_RADIUS = 2.0;
    public static final int BLAZE_WARN = 20;
    public static final int BLAZE_BURNS = 3;
    public static final int GALE_WARN = 70;
    public static final double GALE_RADIUS = 6;
    public static final float GALE_WAVE_DAMAGE = 15f;
    public static final int PELLET_WARN = 30;
    public static final double PELLET_RADIUS = 1.6;
    public static final int FADING_WARN = 40;
    public static final double FADING_RADIUS = 6;
    public static final float FADING_WAVE_DAMAGE = 18f;
    public static final int EMBER_BURST_FIRST = 40;
    public static final int EMBER_BURST_WARN = 16;
    public static final int ROAR_WARN = 22;
    // 残り火: 半径 2.5、8 秒、四つ
    public static final double EMBER_RADIUS = 2.5;
    public static final int EMBER_TICKS = 160;
    public static final int EMBER_COUNT = 4;
    // 炉打ち (憤怒だけ): 扇 (半径 FORGE_RANGE、片側 45 度) を 120 度ずつ回して三打、最後に中央の円 (半径 5)
    public static final int FORGE_WARN = 24;
    public static final int FORGE_STEP = 16;
    public static final double FORGE_RANGE = 15.5;
    public static final double FORGE_HALF = Math.toRadians(45);
    public static final int FORGE_CORE_WARN = 72;
    public static final double FORGE_CORE_RADIUS = 5;
    public static final float FORGE_DAMAGE = 34f;
    public static final float FORGE_CORE_DAMAGE = 30f;
    public static final int FORGE_TICKS = 80;
    // 技の間 (tick): 段階 1 は 1.4〜1.9 秒、段階 2 は 1.1〜1.5 秒、段階 3 は 1.0〜1.4 秒 (冷えがあるので岩鯨より少し長い)。憤怒は 0.4〜0.7 秒
    public static final int[] GAP_MIN = {28, 22, 20};
    public static final int[] GAP_SPAN = {10, 8, 8};
    public static final int GAP_FURY = 8;
    public static final int GAP_FURY_SPAN = 6;
    // 最初の技までと、咆哮のあとの間
    public static final int FIRST_COOLDOWN = 30;
    public static final int ROAR_COOLDOWN = 30;
    public static final int ENRAGE_COOLDOWN = 20;
    // 憤怒の鼓動 (最後に燃え上がる。間 1.3 秒、強く高く)
    public static final int FURY_BEAT = 26;

    static final ResourceLocation CHILL_SPEED_ID = EmberIds.id("ember_heart_chill_speed");
    static final ResourceLocation CHILL_ATTACK_ID = EmberIds.id("ember_heart_chill_attack");

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(EmberHeart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ROARING = SynchedEntityData.defineId(EmberHeart.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(EmberHeart.class, EntityDataSerializers.BOOLEAN);

    public enum Action {
        NONE(null), ROAR(null),
        BREATH("breath"), PULSE("pulse"), ORB("orb"),
        FLOOR("floor"), GALE("gale"), PELLET("pellet"),
        CHILL("chill"), EMBERS("embers"), FADING("fading"), VEIL("veil"),
        // 炉打ち (憤怒だけ。模型の動きは消えかけの鼓動を使う) と、技の組 (二つの技を重ねる。動きは一つ目のもの)
        FORGE("fading"), COMBO(null);

        final String anim;

        Action(String anim) {
            this.anim = anim;
        }
    }

    // 段階ごとに選ぶ技 (冷えは段階 3 に入った瞬間から効く常時の技で、選ぶ袋には入らない)
    public static final Action[] PHASE_ONE = {Action.BREATH, Action.PULSE, Action.ORB};
    public static final Action[] PHASE_TWO = {Action.FLOOR, Action.GALE, Action.PELLET};
    public static final Action[] PHASE_THREE = {Action.EMBERS, Action.FADING, Action.VEIL};

    // 技の組 (二つの技を、二つ目を offset tick 遅らせて重ねる。length は組の長さ = 遅い方の技の終わり)。
    // 選ぶのは、避け方が違い (帯と円、輪と足もと、扇と線)、灰の帳 (見通しが悪い) と鞴の嵐 (凍えた足では逆らいにくい) を段階 3 に含まない組だけ
    public record Pair(Action first, Action second, int offset, int length) {
    }

    public static final Pair[] PAIRS_TWO = {new Pair(Action.FLOOR, Action.PELLET, 16, 48), new Pair(Action.GALE, Action.ORB, 20, 82)};
    public static final Pair[] PAIRS_THREE = {new Pair(Action.FADING, Action.ORB, 24, 64), new Pair(Action.EMBERS, Action.BREATH, 20, 60)};
    public static final Pair[] PAIRS_FURY = {new Pair(Action.FADING, Action.ORB, 24, 64), new Pair(Action.EMBERS, Action.BREATH, 20, 60),
            new Pair(Action.FORGE, Action.EMBERS, 30, FORGE_TICKS), new Pair(Action.PULSE, Action.ORB, 16, 56),
            new Pair(Action.FORGE, Action.BREATH, 30, FORGE_TICKS)};
    // 組が割り込む割合 (段階 1 / 2 / 3)。憤怒は組だけ
    public static final double[] COMBO_CHANCE = {0, 0.3, 0.45};

    // 熔け玉の予告のあとに落ちる玉
    private record Orb(double x, double z, int impactAt) {
    }

    // 熔け玉が落ちたあとの燃える床 (4 秒)
    private record Blaze(double x, double z, int endAt) {
    }

    // 残り火 (半径 2.5、8 秒)。startAt は置いた tick (小さな燃え立ちの予告の時計)
    private record Ember(double x, double z, int startAt, int endAt) {
    }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private Action last = Action.NONE;
    private int actionTicks;
    private int cooldown = FIRST_COOLDOWN;
    private float attackYaw;
    private boolean defeated;
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
    private float toughness = BASE_RESIST;
    // 憤怒の咆哮の最中か / 技の組の進み (組の中の技は finish を呼んでも終わらない)
    private boolean enrageRoar;
    @Nullable
    private Pair pair;
    private boolean suppressFinish;
    // 自己テスト用: 技を自分で選ばない (forceAction だけで動かす)
    private boolean holdAttacks;
    // 開発用: 熾の底にいる人数を多いことにする (0 なら実際の人数)
    private int partyOverride;
    // 回り続けへの追い技 (combat/HeartPursuit) と、体力が半分を切ったときの領域「炉心」(titan/domain/HearthCoreDomain)
    private final jp.revati.skyabyss.combat.HeartPursuit pursuit;
    @Nullable
    private jp.revati.skyabyss.titan.domain.HearthCoreDomain domain;
    public static final float DOMAIN_AT = 0.5f;
    // 試験・開発用: 領域を展開しない (憤怒までの技だけを見たいとき)
    private boolean domainEnabled = true;

    public void setDomainEnabled(boolean enabled) {
        this.domainEnabled = enabled;
    }
    // 回っている人への狙いの先読み (予告の長さに近い tick)
    private static final int AIM_TICKS = 24;

    public EmberHeart(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ember_heart", "roar_1", "roar_2", "breath", "pulse", "orb", "floor", "gale", "pellet",
                "chill", "embers", "fading", "veil");
        this.xpReward = 400;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.pursuit = new jp.revati.skyabyss.combat.HeartPursuit(this,
                new jp.revati.skyabyss.combat.HeartPursuit.Theme("ember", PULSE_DAMAGE, ParticleTypes.FLAME, SoundEvents.BLAZE_SHOOT, 1.2f),
                this::canReach, this::strike,
                () -> level() instanceof ServerLevel s ? reachable(s, 40) : List.<ServerPlayer>of(),
                () -> isBound() ? new Vec3(EmberHeartArena.CX + 0.5, getY(), EmberHeartArena.CZ + 0.5) : position(),
                this::floorY, () -> isBound() ? 13.0 : 12.0);
    }

    public jp.revati.skyabyss.combat.HeartPursuit pursuit() {
        return pursuit;
    }

    @Nullable
    public jp.revati.skyabyss.titan.domain.HearthCoreDomain domain() {
        return domain;
    }

    // 領域「炉心」を始める (体力が半分を切ったとき一度だけ。試験・開発用にも)
    public void startDomain(ServerLevel server) {
        if (domain != null) {
            return;
        }
        double cx = isBound() ? EmberHeartArena.CX + 0.5 : getX();
        double cz = isBound() ? EmberHeartArena.CZ + 0.5 : getZ();
        domain = new jp.revati.skyabyss.titan.domain.HearthCoreDomain(this, cx, cz, floorY(), isBound() ? 13.0 : 12.0, this::canReach, this::strike,
                () -> reachable(server, 40));
        domain.begin(server);
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
        builder.define(DATA_ENRAGED, false);
    }

    // 段階 (0 から 2)
    public int getPhase() {
        return entityData.get(DATA_PHASE);
    }

    public boolean isRoaring() {
        return entityData.get(DATA_ROARING);
    }

    // 最終段階 (憤怒) か。模型は段階 3 のまま、ボスバーが赤くなる
    public boolean isEnraged() {
        return entityData.get(DATA_ENRAGED);
    }

    // テスト用のコマンドと自己テストから: 段階を直接決める
    public void forcePhase(int phase) {
        int next = Mth.clamp(phase, 0, PHASE_COUNT - 1);
        entityData.set(DATA_PHASE, next);
        entityData.set(DATA_ROARING, false);
        entityData.set(DATA_ENRAGED, false);
        enrageRoar = false;
        pair = null;
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
        this.toughness = toughnessFor(challengers);
    }

    // 人数による強さの倍率: HeartBalance.partyScale = 1 + 0.85 × (人数 − 1)。最大体力は変えず、受ける傷を割る (岩鯨と同じ)
    public static float scaleFor(int challengers) {
        return (float) HeartBalance.partyScale(challengers);
    }

    // 受ける傷を割る値 (基本の硬さ × 人数の倍率)
    public static float toughnessFor(int challengers) {
        return BASE_RESIST * scaleFor(challengers);
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
        return entity instanceof Player p && p.isAlive() && !p.isSpectator() && (arena == null || arena.contains(p.position()))
                && jp.revati.skyabyss.titan.group.GroupOwned.mayTarget(this, p);
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
        if (isIntroPlaying()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        tickOrbs(server);
        tickBlazes(server);
        tickEmbers(server);
        tickVeil(server);
        if (getPhase() == 2 && !isRoaring()) {
            tickChill(server);
            tickBeat(server);
        }
        boolean domainRunning = domain != null && domain.running();
        if (getPhase() == 1 && !isRoaring() && isBound() && !domainRunning) {
            tickFloorTimer(server);
        }
        pursuit.tick(server, action != Action.ROAR && !domainRunning);
        if (domainRunning) {
            LivingEntity watched = getTarget();
            if (watched != null && canReach(watched)) {
                faceTarget(watched);
            }
            domain.tick(server);
            if (!domain.running()) {
                cooldown = 40;
            }
            return;
        }

        if (action == Action.NONE) {
            if (getPhase() < phaseForHealth()) {
                begin(Action.ROAR, "roar_" + (getPhase() + 1));
                return;
            }
            if (!isEnraged() && getPhase() == PHASE_COUNT - 1 && getHealth() / getMaxHealth() < HeartBalance.ENRAGE_AT) {
                enrageRoar = true;
                begin(Action.ROAR, "roar_2");
                return;
            }
            if (holdAttacks) {
                return;
            }
            if (domain == null && domainEnabled && getPhase() >= 1 && getHealth() / getMaxHealth() < DOMAIN_AT && !reachable(server, 40).isEmpty()) {
                startDomain(server);
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
            case FORGE -> tickForge(server);
            case COMBO -> tickCombo(server);
            default -> {
            }
        }
    }

    // 段階の技から、直前と同じでないものを選ぶ。段階 2・3 はときどき技の組、憤怒は組だけ
    private Action pickAttack() {
        Pair[] pairs = pairsFor(getPhase(), isEnraged());
        if (pairs != null && (isEnraged() || random.nextDouble() < COMBO_CHANCE[getPhase()])) {
            pair = pairs[random.nextInt(pairs.length)];
            return Action.COMBO;
        }
        Action[] bag = bagFor(getPhase());
        Action pick;
        int guard = 0;
        do {
            pick = bag[random.nextInt(bag.length)];
            guard++;
        } while (pick == last && guard < 20);
        return pick;
    }

    @Nullable
    public static Pair[] pairsFor(int phase, boolean enraged) {
        if (enraged) {
            return PAIRS_FURY;
        }
        return switch (phase) {
            case 1 -> PAIRS_TWO;
            case 2 -> PAIRS_THREE;
            default -> null;
        };
    }

    public static Action[] bagFor(int phase) {
        return switch (phase) {
            case 0 -> PHASE_ONE;
            case 1 -> PHASE_TWO;
            default -> PHASE_THREE;
        };
    }

    // テスト用: 技を直接始める
    // 技の組 (COMBO) は、いまの段階の組 (段階 1 では段階 2 の組) から一つ選ぶ
    public void forceAction(Action next) {
        if (next == Action.COMBO) {
            Pair[] pairs = pairsFor(getPhase(), isEnraged());
            Pair[] from = pairs != null ? pairs : PAIRS_TWO;
            forcePair(from[random.nextInt(from.length)]);
            return;
        }
        begin(next, next == Action.ROAR ? "roar_" + (getPhase() + 1) : null);
    }

    // テスト用: 技の組を直接始める
    public void forcePair(Pair p) {
        pair = p;
        begin(Action.COMBO, null);
    }

    // テスト用: 憤怒に入れる (咆哮なし。段階 3 にする)
    public void forceEnraged(boolean on) {
        if (on && getPhase() != PHASE_COUNT - 1) {
            forcePhase(PHASE_COUNT - 1);
        }
        entityData.set(DATA_ENRAGED, on);
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
        playAction(animation != null ? animation : next == Action.COMBO && pair != null ? pair.first().anim : next.anim);
        cue(next == Action.COMBO && pair != null ? pair.first() : next);
    }

    // 技の名前 (ボスバーの下の小さな一行。BossCue)。咆哮・冷え・組は出さない (組は一つ目・二つ目それぞれ)
    private void cue(Action a) {
        int warn = switch (a) {
            case BREATH -> BREATH_WARN;
            case PULSE -> PULSE_WARN;
            case ORB -> ORB_WARN;
            case FLOOR -> 40;
            case GALE -> 60;
            case PELLET -> PELLET_WARN;
            case EMBERS -> EMBER_BURST_FIRST;
            case FADING -> FADING_WARN;
            case VEIL -> 40;
            case FORGE -> FORGE_WARN;
            default -> 0;
        };
        if (warn > 0) {
            jp.revati.skyabyss.combat.BossCue.send(this, "ember", a.name().toLowerCase(java.util.Locale.ROOT), Math.min(warn, 60));
        }
    }

    private void finish() {
        if (suppressFinish) {
            return;
        }
        action = Action.NONE;
        actionTicks = 0;
        pair = null;
        int phase = getPhase();
        cooldown = isEnraged() ? GAP_FURY + random.nextInt(GAP_FURY_SPAN) : GAP_MIN[phase] + random.nextInt(GAP_SPAN[phase]);
    }

    // 技の組: 一つ目はそのまま、二つ目は offset tick 遅れて始まる (どの技も自分の tick を数え直して動く)
    private void tickCombo(ServerLevel server) {
        Pair p = pair;
        if (p == null) {
            finish();
            return;
        }
        runPart(server, p.first(), actionTicks);
        if (actionTicks > p.offset()) {
            if (actionTicks == p.offset() + 1) {
                cue(p.second());
            }
            runPart(server, p.second(), actionTicks - p.offset());
        }
        if (actionTicks >= p.length()) {
            finish();
        }
    }

    private void runPart(ServerLevel server, Action part, int ticks) {
        int savedTicks = actionTicks;
        Action saved = action;
        actionTicks = ticks;
        action = part;
        suppressFinish = true;
        try {
            switch (part) {
                case BREATH -> tickBreath(server);
                case PULSE -> tickPulse(server);
                case ORB -> tickOrb(server);
                case FLOOR -> tickFloor(server);
                case GALE -> tickGale(server);
                case PELLET -> tickPellet(server);
                case EMBERS -> tickEmbersMove(server);
                case FADING -> tickFading(server);
                case FORGE -> tickForge(server);
                default -> {
                }
            }
        } finally {
            suppressFinish = false;
            actionTicks = savedTicks;
            action = saved;
        }
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
        return getY() + 3.0;
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
            // 押し返す輪 (傷はない)。広がる範囲は、始めの 1.1 秒だけ床に描く
            tg(server, TelegraphShape.wave(getX(), getZ(), 2.5, 18, 1.0, new double[0], 0), ROAR_WARN, H)
                    .active((int) Math.ceil((18 - 2.5) / 0.8))
                    .onImpact(s -> sound(s, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f))
                    .onHit((s, player) -> {
                        Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                        player.knockback(0.9, -to.x, -to.z);
                        player.hurtMarked = true;
                    })
                    .start();
        }
        if (actionTicks % 4 == 0 && actionTicks < 56) {
            if (enrageRoar) {
                // 憤怒: 消えかけた火が最後に燃え上がる (赤い粒と炎)
                warn(server, dust(0xFF3A1A, 1.5f), getX(), coreY(), getZ(), 8, 1.3, 1.3, 1.3, 0.05);
                warn(server, ParticleTypes.FLAME, getX(), coreY(), getZ(), 6, 1.2, 1.2, 1.2, 0.08);
            }
            warn(server, dust(getPhase() == 0 ? 0xFFB040 : 0xE8E0D8, 1.3f), getX(), coreY(), getZ(), 6, 1.2, 1.2, 1.2, 0.05);
        }
        if (actionTicks >= ROAR_TICKS && enrageRoar) {
            entityData.set(DATA_ROARING, false);
            enrageRoar = false;
            entityData.set(DATA_ENRAGED, true);
            bossBar.setColor(BossEvent.BossBarColor.RED);
            sound(server, SoundEvents.BLAZE_SHOOT, 3f, 0.5f);
            sound(server, EmberHeartSetup.BEAT.get(), 3f, 1.3f);
            server.sendParticles(ParticleTypes.LAVA, getX(), coreY(), getZ(), 50, 1.8, 1.6, 1.8, 0);
            server.sendParticles(ParticleTypes.FLAME, getX(), coreY(), getZ(), 90, 1.8, 1.6, 1.8, 0.15);
            nextBeat = tickCount + 10;
            finish();
            cooldown = ENRAGE_COOLDOWN;
            return;
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
            cooldown = ROAR_COOLDOWN;
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

    // ---- 予告の部品 (combat/Telegraph) の組み立てと人数 ----

    // 試験用: 輪の安全な筋の向きを決める (null なら乱数)
    @Nullable
    public static volatile Double testGapBase;

    // 開発用: 熾の底にいる人数を、実際より多いことにする (一人で複数人の技の絵を撮るため。0 なら実際の人数)
    public void setPartyOverride(int count) {
        partyOverride = count;
    }

    int present(ServerLevel server) {
        return Math.max(partyOverride, reachable(server, 30).size());
    }

    // 人数による技の数・本数 (一人でも、印を見れば傷なしで避けられる)
    public static int breathLines(int n) {
        return Math.min(5, 1 + (Math.max(1, n) + 1) / 2);
    }

    public static int pulseRings(int n) {
        return 1 + (n >= 3 ? 1 : 0) + (n >= 5 ? 1 : 0);
    }

    public static int orbCount(int n) {
        return Math.min(9, 2 + Math.max(1, n));
    }

    public static int galeWaves(int n) {
        return n >= 4 ? 2 : 1;
    }

    public static int pelletCount(int n) {
        return Math.min(9, 4 + Math.max(1, n));
    }

    public static int floorCracks(int n) {
        return n >= 4 ? 2 : 1;
    }

    public static int fadingWaves(int n) {
        return n >= 3 ? 2 : 1;
    }

    // 予告の組み立て (形・長さ・印の種類。持ち主は心臓、当たるのは熾の底の人)
    // 印の種類 (Telegraph の preset): D = 傷が出る (danger)、S = 安全な筋 (safe)、H = 押すだけ・見た目だけ (harmless)
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

    // 人の足もとの点 (pickSpots などが返した点) の人。床の適当な点なら null (帯・線の狙いの印に使う)
    private static ServerPlayer playerAt(ServerLevel server, Vec3 spot) {
        ServerPlayer best = null;
        double bestD = 7.0 * 7.0;
        for (ServerPlayer p : Players.snapshot(server.players())) {
            double d = p.position().distanceToSqr(spot);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private Telegraph.Builder tg(ServerLevel server, TelegraphShape shape, int warn, int style) {
        return preset(Telegraph.in(server, shape).floor(floorY()).warn(warn).owner(this).victims(this::canReach).reach(3.5), style);
    }

    // 輪の安全な筋 (緑の扇。見た目だけ。当たりはなく、輪が抜ける)
    private double[] laneGaps() {
        Double fixed = testGapBase;
        double base = fixed != null ? fixed : random.nextDouble() * Math.PI * 2;
        return new double[] {base, base + Math.PI * 2 / 3, base + Math.PI * 4 / 3};
    }

    private void showLanes(ServerLevel server, double[] gaps, int warn) {
        for (double gap : gaps) {
            tg(server, TelegraphShape.cone(getX(), getZ(), gap, 13, LANE_HALF), warn, S).start();
        }
    }

    // 狙う地点: 人の足もとを、重ならないように count か所 (いま狙っている人を先頭に、人数より多いぶんは人のまわり)。
    // 人がいなくて開発用の人数の上書きだけあるときは、心臓のまわりの点で補う。部屋の外へははみ出さない
    List<Vec3> spots(ServerLevel server, int count, double fillMin, double fillMax) {
        List<ServerPlayer> all = new ArrayList<>(reachable(server, 30));
        java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
        LivingEntity main = getTarget();
        if (main instanceof ServerPlayer mp && all.remove(mp)) {
            all.add(0, mp);
        }
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double x;
            double z;
            if (all.isEmpty()) {
                if (partyOverride <= 0) {
                    break;
                }
                double a = random.nextDouble() * Math.PI * 2;
                double r = 5 + random.nextDouble() * 7;
                x = getX() + Math.cos(a) * r;
                z = getZ() + Math.sin(a) * r;
            } else {
                ServerPlayer p = all.get(i % all.size());
                // 回り続けている人には、予告が満ちる頃にいる所を狙う (combat/AntiStrafe)
                Vec3 aimed = pursuit.strafe().aim(p, AIM_TICKS);
                x = aimed.x;
                z = aimed.z;
                if (i >= all.size()) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double r = fillMin + random.nextDouble() * (fillMax - fillMin);
                    x += Math.cos(a) * r;
                    z += Math.sin(a) * r;
                }
            }
            double dx = x - (EmberHeartArena.CX + 0.5);
            double dz = z - (EmberHeartArena.CZ + 0.5);
            double d = Math.hypot(dx, dz);
            if (d > 13.0) {
                x = EmberHeartArena.CX + 0.5 + dx * 13.0 / d;
                z = EmberHeartArena.CZ + 0.5 + dz * 13.0 / d;
            }
            out.add(new Vec3(x, floorY(), z));
        }
        return out;
    }

    // ---- 段階 1 ----

    // 火の息 (1.2 秒): 心臓から人へ伸びる赤い帯 (幅 3・長さ 22)。傷 18、熱 +15、押す。
    // 人数が増えると別の人へ向けて最大五本。向きは技の初めに決まり、そのあとは追わない
    private void tickBreath(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.BREATH.get(), 2f, 1f);
            List<Double> angles = new ArrayList<>();
            List<ServerPlayer> aimed = new ArrayList<>();
            for (Vec3 p : spots(server, breathLines(present(server)), 4, 9)) {
                angles.add(Telegraph.angleOf(p.x - getX(), p.z - getZ()));
                aimed.add(playerAt(server, p));
            }
            if (angles.isEmpty()) {
                angles.add(Telegraph.angleOf(forward(attackYaw)));
                aimed.add(null);
            }
            boolean first = true;
            for (int c = 0; c < angles.size(); c++) {
                double angle = angles.get(c);
                ServerPlayer aim = aimed.get(c);
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                boolean lead = first;
                first = false;
                tg(server, TelegraphShape.line(getX() + dx * 0.5, getZ() + dz * 0.5, angle, BREATH_LENGTH, BREATH_HALF_WIDTH), BREATH_WARN, D).target(aim)
                        .onImpact(s -> {
                            if (lead) {
                                sound(s, SoundEvents.BLAZE_SHOOT, 2f, 0.6f);
                            }
                            double fy = floorY();
                            for (double d = 2; d <= BREATH_LENGTH; d += 2.0) {
                                s.sendParticles(ParticleTypes.FLAME, getX() + dx * d, fy + 0.8, getZ() + dz * d, 6, 0.8, 0.4, 0.8, 0.05);
                                s.sendParticles(ParticleTypes.SMOKE, getX() + dx * d, fy + 1.2, getZ() + dz * d, 2, 0.6, 0.4, 0.6, 0.02);
                            }
                        })
                        .onHit((s, player) -> {
                            strike(player, BREATH_DAMAGE);
                            EmberHeat.add(player, BREATH_HEAT, EmberHeat.Reason.HEART);
                            player.knockback(1.2, -dx, -dz);
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 熱の脈 (1.0 秒): 心臓が一度強く光り、床を走る輪 (安全な筋が三本。跳べば当たらない)。傷 13、熱 +10。
    // 三人から輪が二重、五人から三重 (0.7 秒の間をあける)
    private void tickPulse(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.PULSE.get(), 2f, 1f);
            double[] gaps = laneGaps();
            showLanes(server, gaps, PULSE_WARN);
            int rings = pulseRings(present(server));
            int active = (int) Math.ceil((PULSE_REACH - PULSE_START) / 0.8);
            for (int i = 0; i < rings; i++) {
                boolean lead = i == 0;
                tg(server, TelegraphShape.wave(getX(), getZ(), PULSE_START, PULSE_REACH, 0.9, gaps, LANE_HALF), PULSE_WARN + i * 14, D)
                        .active(active).jumpClear(0.9)
                        .onImpact(s -> {
                            if (lead) {
                                sound(s, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.3f);
                                s.sendParticles(ParticleTypes.FLAME, getX(), floorY() + 0.4, getZ(), 50, 2.0, 0.3, 2.0, 0.1);
                            }
                        })
                        .onHit((s, player) -> {
                            strike(player, PULSE_DAMAGE);
                            EmberHeat.add(player, PULSE_HEAT, EmberHeat.Reason.HEART);
                            Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                            player.knockback(0.9, -to.x, -to.z);
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        if (actionTicks < 20 && actionTicks % 3 == 0) {
            warn(server, dust(0xFFE8A0, 1.6f), getX(), coreY(), getZ(), 4, 0.8, 0.8, 0.8, 0.02);
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 熔け玉 (1.5 秒): 人の足もとに橙の円。円の中は傷 15 (半径 2.3)、落ちた所は 4 秒燃える床 (一秒ごとの燃え立ちにも予告がある)。
    // 数は 人数 + 2 (最大 9)。人数より多いぶんは人のまわりへ
    private void tickOrb(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.ORB.get(), 2f, 1f);
            for (Vec3 at : spots(server, orbCount(present(server)), 2.5, 5)) {
                double x = at.x;
                double z = at.z;
                orbs.add(new Orb(x, z, tickCount + ORB_WARN));
                tg(server, TelegraphShape.circle(x, z, ORB_RADIUS), ORB_WARN, D)
                        .onImpact(s -> impactOrb(s, x, z))
                        .onHit((s, player) -> {
                            strike(player, ORB_DAMAGE);
                            nudge(player, 0, 0.4, 0);
                        })
                        .start();
            }
        }
        if (actionTicks < 30 && actionTicks % 3 == 0) {
            warn(server, dust(0xFFA030, 1.1f), getX(), coreY() + 0.5, getZ(), 6, 0.9, 0.9, 0.9, 0.02);
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // ---- 段階 2 ----

    // 崩れる床 (2.0 秒): 区画の一つの床が赤く割れ、ぴしぴし鳴る。印は区画の形、8 秒溶岩に沈み、また冷え殻に戻る。人の立つ扇を優先して割る。
    // 四人から二区画を同時に
    private void tickFloor(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.FLOOR.get(), 1.4f, 1.2f);
            if (isBound()) {
                crackSectors(server, floorCracks(present(server)));
            }
        }
        if (actionTicks < 40 && actionTicks % 4 == 0) {
            warn(server, dust(0xFF5018, 1.4f), getX(), coreY(), getZ(), 6, 1.0, 1.0, 1.0, 0.03);
        }
        if (actionTicks >= 44) {
            finish();
        }
    }

    // 区画を割り、その形の予告を出す (予告の最後に、区画にいた人が呑まれる。沈む瞬間の呑まれは熾の底が見るが、同じ範囲で一度だけ)
    private void crackSectors(ServerLevel server, int count) {
        for (int i = 0; i < count; i++) {
            int k = EmberHeartArena.pickSector(server, lastSector);
            if (k >= 0 && EmberHeartArena.crack(server, k)) {
                lastSector = k;
                tg(server, new EmberSectorShape(k), EmberHeartArena.CRACK_TICKS, D)
                        .onHit((s, player) -> {
                            // 区画が戻された・やり直された (放棄・段階 3 への切り替え) あとは、古い予告で呑まない
                            if (EmberHeartArena.cracking(k) || EmberHeartArena.sunk(k)) {
                                EmberHeartArena.engulf(s, player, this);
                            }
                        })
                        .start();
            }
        }
    }

    // 段階 2 のあいだ、6〜8 秒ごとに次の区画を割る (同時に沈むのは最大 2 区画)
    private void tickFloorTimer(ServerLevel server) {
        if (--floorTimer > 0) {
            return;
        }
        floorTimer = 120 + random.nextInt(41);
        crackSectors(server, 1);
    }

    // 鞴の嵐 (3.5 秒): 部屋の風が心臓へ流れはじめ、2 秒のあいだ中心へ引く (歩けば逆らえる)。終わりに半径 6 の円が弾けて 16 と押し、
    // そのあと外へ走る輪 (安全な筋が三本。跳べば当たらない) 8。四人からは輪が二重
    private void tickGale(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.GALE.get(), 2.4f, 1f);
            double[] gaps = laneGaps();
            tg(server, TelegraphShape.circle(getX(), getZ(), GALE_RADIUS), GALE_WARN, D)
                    .onImpact(s -> {
                        sound(s, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.7f);
                        s.sendParticles(ParticleTypes.FLAME, getX(), floorY() + 0.5, getZ(), 100, 3.0, 0.5, 3.0, 0.2);
                    })
                    .onHit((s, player) -> {
                        Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                        strike(player, GALE_DAMAGE);
                        player.knockback(1.0, -out.x, -out.z);
                        player.hurtMarked = true;
                    })
                    .start();
            showLanes(server, gaps, GALE_WARN);
            int active = (int) Math.ceil((PULSE_REACH - GALE_RADIUS - 0.8) / 0.8);
            for (int i = 0; i < galeWaves(present(server)); i++) {
                tg(server, TelegraphShape.wave(getX(), getZ(), GALE_RADIUS + 0.8, PULSE_REACH, 0.9, gaps, LANE_HALF), GALE_WARN + i * 14, D)
                        .active(active).jumpClear(0.9)
                        .onHit((s, player) -> {
                            strike(player, GALE_WAVE_DAMAGE);
                            Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                            player.knockback(0.8, -out.x, -out.z);
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        if (actionTicks < 70 && actionTicks % 2 == 0) {
            for (int k = 0; k < 16; k++) {
                double a = Math.toRadians(k * 22.5 + actionTicks * 3);
                double r = 14 - ((actionTicks * 0.45 + k * 1.3) % 12);
                warn(server, dust(0xFFB060, 1.2f), getX() + Math.cos(a) * r, fy + 0.4 + (k % 3) * 0.4, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks >= 30 && actionTicks < 70 && actionTicks % 2 == 0) {
            for (ServerPlayer player : reachable(server, 22)) {
                pull(player, GALE_PULL);
            }
        }
        if (actionTicks >= 82) {
            finish();
        }
    }

    // 灰の礫 (1.0 秒の溜め): 心臓から黒い礫が浮き、人の足もとの円 (半径 1.6) へ落ちる。円は 1.5 秒前から見え、中は傷 10。
    // 落ちる前に礫を叩けば円ごと消える。数は 人数 + 4 (最大 9)
    private void tickPellet(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.PELLET.get(), 2f, 1f);
        }
        if (actionTicks < 20 && actionTicks % 2 == 0) {
            warn(server, dust(0x303030, 1.5f), getX(), coreY(), getZ(), 8, 1.2, 1.2, 1.2, 0.02);
        }
        if (actionTicks == 20) {
            List<Vec3> targets = spots(server, pelletCount(present(server)), 2.5, 6);
            int count = targets.size();
            for (int i = 0; i < count; i++) {
                Vec3 land = targets.get(i);
                double a = Math.toRadians(attackYaw + 90 + (i - (count - 1) / 2.0) * (360.0 / Math.max(5, count)));
                Vec3 from = new Vec3(getX() - Math.sin(a) * 3.0, coreY() + ((i % 3) - 1) * 0.5, getZ() + Math.cos(a) * 3.0);
                EmberAshPellet pellet = new EmberAshPellet(server, this, from, land, PELLET_WARN);
                Telegraph t = tg(server, TelegraphShape.circle(land.x, land.z, PELLET_RADIUS), PELLET_WARN, D)
                        .onImpact(s -> pellet.land())
                        .onHit((s, player) -> strike(player, PELLET_DAMAGE))
                        .start();
                pellet.bind(t);
                server.addFreshEntity(pellet);
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
            if (isEnraged()) {
                // 憤怒: 最後に燃え上がって、鼓動が速く強くなる
                sound(server, EmberHeartSetup.BEAT.get(), 2.4f, 1.15f);
                nextBeat = tickCount + FURY_BEAT;
            } else {
                sound(server, EmberHeartSetup.BEAT.get(), beatVolume(frac), 0.8f + 0.2f * frac / PHASE_THREE_AT);
                nextBeat = tickCount + beatInterval(frac);
            }
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
            embers.add(new Ember(at.x, at.z, tickCount, tickCount + EMBER_TICKS));
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

    // 消えかけの鼓動 (2.0 秒): 心臓が暗くなり、低い一拍。床に半径 6 の白い円 (傷 22。円の外にいれば当たらない)。
    // 一拍のあと、外へ走る輪 (安全な筋が三本。跳べば当たらない) 10。三人からは輪が二重
    private void tickFading(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, EmberHeartSetup.FADING.get(), 2.5f, 1f);
            double[] gaps = laneGaps();
            tg(server, TelegraphShape.circle(getX(), getZ(), FADING_RADIUS), FADING_WARN, D)
                    .onImpact(s -> {
                        sound(s, SoundEvents.WARDEN_HEARTBEAT, 3f, 0.5f);
                        s.sendParticles(ParticleTypes.ASH, getX(), floorY() + 0.5, getZ(), 120, 4.0, 0.5, 4.0, 0.1);
                    })
                    .onHit((s, player) -> {
                        Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                        strike(player, FADING_DAMAGE);
                        player.knockback(0.8, -out.x, -out.z);
                        player.hurtMarked = true;
                    })
                    .start();
            showLanes(server, gaps, FADING_WARN);
            int active = (int) Math.ceil((PULSE_REACH - FADING_RADIUS - 1.4) / 0.8);
            for (int i = 0; i < fadingWaves(present(server)); i++) {
                tg(server, TelegraphShape.wave(getX(), getZ(), FADING_RADIUS + 1.4, PULSE_REACH, 0.9, gaps, LANE_HALF), FADING_WARN + i * 16, D)
                        .active(active).jumpClear(0.9)
                        .onHit((s, player) -> {
                            strike(player, FADING_WAVE_DAMAGE);
                            Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                            player.knockback(0.6, -out.x, -out.z);
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        if (actionTicks < 40 && actionTicks % 4 == 0) {
            warn(server, dust(0x9A9088, 1.2f), getX(), coreY(), getZ(), 4, 1.0, 1.0, 1.0, 0.02);
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

    // 炉打ち (4.0 秒。憤怒だけ、組の中で出る): 鎚が部屋を回って三度打つ。扇 (半径 15.5、片側 45 度) が 120 度ずつ回り、
    // 1.2 / 2.0 / 2.8 秒に当たる (三つとも初めから見える。扇のあいだの筋 30 度と、打ち終わった扇は安全)。最後に 3.6 秒で台の上 (中央の円、半径 5) を打つ。
    // 一打目は狙っている人の向き、回る向きは毎回ちがう。傷 34 (扇) / 30 (中央)
    private void tickForge(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, SoundEvents.ANVIL_PLACE, 2.5f, 0.5f);
            sound(server, EmberHeartSetup.PULSE.get(), 2f, 0.7f);
            double base = Telegraph.angleOf(forward(attackYaw));
            double turn = (random.nextBoolean() ? 1 : -1) * Math.PI * 2 / 3;
            for (int i = 0; i < 3; i++) {
                double angle = base + turn * i;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                boolean lead = i == 0;
                tg(server, TelegraphShape.cone(getX(), getZ(), angle, FORGE_RANGE, FORGE_HALF), FORGE_WARN + i * FORGE_STEP, D)
                        .onImpact(s -> {
                            s.playSound(null, getX(), floorY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, lead ? 2.5f : 2.0f, 0.5f);
                            double fy = floorY();
                            for (double d = 3; d <= FORGE_RANGE; d += 2.5) {
                                s.sendParticles(ParticleTypes.FLAME, getX() + dx * d, fy + 0.4, getZ() + dz * d, 8, 1.2, 0.2, 1.2, 0.05);
                                s.sendParticles(ParticleTypes.LAVA, getX() + dx * d, fy + 0.5, getZ() + dz * d, 1, 0.8, 0.1, 0.8, 0);
                            }
                        })
                        .onHit((s, player) -> {
                            strike(player, FORGE_DAMAGE);
                            player.knockback(0.8, -dx, -dz);
                            player.hurtMarked = true;
                        })
                        .start();
            }
            tg(server, TelegraphShape.circle(getX(), getZ(), FORGE_CORE_RADIUS), FORGE_CORE_WARN, D)
                    .onImpact(s -> {
                        sound(s, SoundEvents.ANVIL_LAND, 3f, 0.4f);
                        s.sendParticles(ParticleTypes.FLAME, getX(), floorY() + 0.5, getZ(), 80, 2.5, 0.4, 2.5, 0.12);
                    })
                    .onHit((s, player) -> {
                        Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                        strike(player, FORGE_CORE_DAMAGE);
                        player.knockback(1.0, -out.x, -out.z);
                        player.hurtMarked = true;
                    })
                    .start();
        }
        if (actionTicks < 70 && actionTicks % 3 == 0) {
            warn(server, dust(0xFF5A20, 1.4f), getX(), coreY(), getZ(), 6, 1.0, 1.0, 1.0, 0.04);
        }
        if (actionTicks >= FORGE_TICKS) {
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

    // ---- 玉・燃える床・残り火 (技の後も続く。傷は、どれも予告 (Telegraph) の印の中だけ) ----

    // 落ちてくる玉の見た目 (傷と円は Telegraph。ここは粒だけ)
    private void tickOrbs(ServerLevel server) {
        if (orbs.isEmpty()) {
            return;
        }
        double fy = floorY();
        orbs.removeIf(o -> tickCount >= o.impactAt());
        for (Orb o : orbs) {
            if (tickCount >= o.impactAt() - 10) {
                double drop = (o.impactAt() - tickCount) / 10.0;
                warn(server, ParticleTypes.FLAME, o.x(), fy + 1 + drop * 8, o.z(), 3, 0.1, 0.2, 0.1, 0);
                warn(server, ParticleTypes.LAVA, o.x(), fy + 1 + drop * 8, o.z(), 1, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    // 玉が落ちた: 爆ぜて、4 秒燃える床になる (一秒ごとの燃え立ちにも 1 秒の予告の円が出る)
    private void impactOrb(ServerLevel server, double x, double z) {
        double fy = floorY();
        server.playSound(null, x, fy, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 1.5f);
        server.sendParticles(ParticleTypes.FLAME, x, fy + 0.4, z, 40, 1.0, 0.2, 1.0, 0.1);
        server.sendParticles(ParticleTypes.LAVA, x, fy + 0.6, z, 14, 0.8, 0.2, 0.8, 0);
        blazes.add(new Blaze(x, z, tickCount + 80));
        EmberHeartArena.emberLight(server, x, z, true);
        blazeBurn(server, x, z, BLAZE_BURNS);
    }

    // 燃える床の燃え立ち一回 (予告 1.0 秒のあと、その円の中だけ。当たる瞬間に次の一回を予告する)
    private void blazeBurn(ServerLevel server, double x, double z, int left) {
        tg(server, TelegraphShape.circle(x, z, BLAZE_RADIUS), BLAZE_WARN, D)
                .onImpact(s -> {
                    if (left > 1 && isAlive()) {
                        blazeBurn(s, x, z, left - 1);
                    }
                })
                .onHit((s, player) -> {
                    burn(s, player, ORB_FLOOR_DAMAGE);
                    EmberHeat.add(player, ORB_FLOOR_HEAT, EmberHeat.Reason.HEART);
                })
                .start();
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
            }
            remaining.add(b);
        }
        blazes.clear();
        blazes.addAll(remaining);
    }

    // 残り火: 熱を上げる場所 (傷ではない)。小さな燃え立ち (5) は 2 秒ごとに、1 秒前から円の予告が出る
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
            int age = tickCount - e.startAt();
            int first = EMBER_BURST_FIRST - EMBER_BURST_WARN;
            if (age == first || age == first + 40 || age == first + 80) {
                tg(server, TelegraphShape.circle(e.x(), e.z(), EMBER_RADIUS), EMBER_BURST_WARN, D)
                        .onHit((s, player) -> {
                            burn(s, player, EMBER_BURN_DAMAGE);
                            s.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.3, 0.2, 0.3, 0.05);
                        })
                        .start();
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
                for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(e.x() - EMBER_RADIUS, fy - 1, e.z() - EMBER_RADIUS, e.x() + EMBER_RADIUS, fy + 2, e.z() + EMBER_RADIUS), this::canReach)) {
                    if (Math.hypot(player.getX() - e.x(), player.getZ() - e.z()) <= EMBER_RADIUS && player.getY() < fy + 1.5) {
                        EmberHeat.add(player, EMBER_HEAT_PER_SECOND, EmberHeat.Reason.HEART);
                    }
                }
            }
            remaining.add(e);
        }
        embers.clear();
        embers.addAll(remaining);
    }

    // 技の残り (予告・玉・燃える床・残り火・灰の帳) をすべて消す
    void clearHazards(ServerLevel server) {
        Telegraph.cancelAll(this);
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
        if (domain != null && domain.invulnerable()) {
            if (level() instanceof ServerLevel server) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.6f);
                server.sendParticles(ParticleTypes.LAVA, getX(), coreY(), getZ(), 6, 0.8, 0.8, 0.8, 0);
            }
            return false;
        }
        float before = getHealth();
        boolean result = super.hurt(source, takenFor(getPhase(), amount, toughness) * (domain != null ? domain.takenMultiplier() : 1f));
        if (domain != null && level() instanceof ServerLevel server) {
            domain.damaged(server, before - getHealth());
        }
        return result;
    }

    // 受ける傷 (段階と人数で)。自己テストからも使う
    public static float takenFor(int phase, float amount, float toughness) {
        return (phase == 1 ? amount * EXPOSED_TAKEN : amount) / toughness;
    }

    // 手ごたえの数 (HeartBalance / 自己テスト用)。段階 1 / 2 / 3 / 憤怒。
    // action = 技の長さの平均 tick (組が割り込む分を入れた)。hits = 選ばれる技の一撃 (組の中の技も並べる。灰の帳は 0)。
    // 段階 2 は熔け落ちた殻で受ける傷が 1.2 倍だが、式では硬さ 2.29 のまま数える (実際は少し短くなる側)
    public static HeartBalance.Profile balanceProfile() {
        int[] action = new int[4];
        double[] singles = {mean(40, 40, 40), mean(44, 82, 32), mean(30, 60, 34)};
        for (int phase = 0; phase < 3; phase++) {
            Pair[] pairs = pairsFor(phase, false);
            double chance = COMBO_CHANCE[phase];
            action[phase] = (int) Math.round(pairs == null ? singles[phase] : singles[phase] * (1 - chance) + meanLength(pairs) * chance);
        }
        action[3] = (int) Math.round(meanLength(PAIRS_FURY));
        return new HeartBalance.Profile("ember", MAX_HEALTH, BASE_RESIST,
                new int[] {GAP_MIN[0] + GAP_SPAN[0] / 2, GAP_MIN[1] + GAP_SPAN[1] / 2, GAP_MIN[2] + GAP_SPAN[2] / 2, GAP_FURY + GAP_FURY_SPAN / 2},
                action,
                new double[] {0, COMBO_CHANCE[1], COMBO_CHANCE[2], 1.0},
                new float[][] {
                        {BREATH_DAMAGE, PULSE_DAMAGE, ORB_DAMAGE},
                        {EmberHeartArena.ENGULF_DAMAGE, GALE_DAMAGE, PELLET_DAMAGE, ORB_DAMAGE},
                        {EMBER_BURN_DAMAGE, FADING_DAMAGE, 0f, ORB_DAMAGE, BREATH_DAMAGE},
                        {FADING_DAMAGE, ORB_DAMAGE, BREATH_DAMAGE, FORGE_DAMAGE, FORGE_CORE_DAMAGE, ORB_DAMAGE, PULSE_DAMAGE}},
                3 * ROAR_TICKS);
    }

    private static double mean(int... v) {
        double sum = 0;
        for (int x : v) {
            sum += x;
        }
        return sum / v.length;
    }

    private static double meanLength(Pair[] pairs) {
        double sum = 0;
        for (Pair p : pairs) {
            sum += p.length();
        }
        return sum / pairs.length;
    }

    private void updateBossBarViewers() {
        for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
            if (!player.isAlive() || player.distanceToSqr(this) > 48 * 48 || player.level() != level()) {
                bossBar.removePlayer(player);
            }
        }
        if (level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.getPlayers(p -> p.distanceToSqr(this) < 40 * 40 && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(this, p))) {
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
        if (domain != null && level() instanceof ServerLevel server) {
            domain.abort(server);
        }
        if (level() instanceof ServerLevel server) {
            clearHazards(server);
            clearChill(server);
        }
        super.remove(reason);
    }

    // 倒れたとき: 鼓動が遅くなり、熱が引いて、灰になって崩れる演出 (deathEffects) のあとで、報酬・進み・記録を EmberHeartBattle へ渡す (onDeathFinished)
    @Override
    public void die(DamageSource source) {
        if (defeated) {
            return;
        }
        defeated = true;
        super.die(source);
        bossBar.removeAllPlayers();
        if (domain != null && level() instanceof ServerLevel server) {
            domain.abort(server);
        }
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), EmberHeartSetup.DEATH.get(), SoundSource.HOSTILE, 3f, 1f);
            server.sendParticles(ParticleTypes.ASH, getX(), coreY(), getZ(), 160, 1.5, 1.6, 1.5, 0.1);
            server.sendParticles(ParticleTypes.SMOKE, getX(), coreY(), getZ(), 60, 1.5, 1.5, 1.5, 0.05);
            clearHazards(server);
            clearChill(server);
        }
    }

    // 試練で湧いた心臓だけが、報酬・進み・記録を EmberHeartBattle へ渡す
    @Override
    protected void onDeathFinished(ServerLevel server) {
        if (server.dimension() == EmberIds.BACK && getTags().contains(EmberHeartBattle.TAG)) {
            EmberHeartBattle.onDefeated(server, this);
        }
    }

    @Override
    protected double coreHeight() {
        return coreY();
    }

    // ---- 登場: 灰の塊が鼓動のたびに熱を帯び、最後に殻を割って熱を吐く (animation.ember_heart.intro)
    // 鼓動は 0.6・1.2・1.7・2.1・2.4 秒 (tools/install_heart_cinematics.py の EM_THUMPS)
    @Override
    protected void introEffects(ServerLevel server, int tick) {
        double cy = coreY();
        if (tick == 0) {
            sound(server, SoundEvents.FIRE_AMBIENT, 2f, 0.6f);
            sound(server, SoundEvents.LAVA_AMBIENT, 2f, 0.7f);
        }
        int[] thumps = {12, 24, 34, 42, 48};
        for (int k = 0; k < thumps.length; k++) {
            if (tick == thumps[k]) {
                sound(server, EmberHeartSetup.BEAT.get(), 2.5f, 0.6f + 0.1f * k);
                server.sendParticles(ParticleTypes.LAVA, getX(), cy, getZ(), 4 + 2 * k, 0.8, 0.6, 0.8, 0);
                server.sendParticles(ParticleTypes.FLAME, getX(), cy, getZ(), 8 + 4 * k, 0.9, 0.7, 0.9, 0.02);
                server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), cy + 0.5, getZ(), 4, 0.7, 0.4, 0.7, 0.02);
            }
        }
        if (tick < 50 && tick % 5 == 0) {
            server.sendParticles(ParticleTypes.ASH, getX(), cy, getZ(), 6, 1.2, 1.0, 1.2, 0.02);
            server.sendParticles(ParticleTypes.SMOKE, getX(), cy - 0.5, getZ(), 3, 0.8, 0.5, 0.8, 0.02);
        }
        if (tick == 50) {
            sound(server, SoundEvents.FIRECHARGE_USE, 3f, 0.6f);
            sound(server, SoundEvents.LAVA_POP, 3f, 0.6f);
            server.sendParticles(ParticleTypes.LAVA, getX(), cy, getZ(), 40, 1.0, 1.0, 1.0, 0);
            server.sendParticles(ParticleTypes.FLAME, getX(), cy, getZ(), 60, 1.2, 1.2, 1.2, 0.12);
            server.sendParticles(ParticleTypes.ASH, getX(), cy, getZ(), 80, 1.6, 1.4, 1.6, 0.05);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), cy, getZ(), 30, 1.2, 1.2, 1.2, 0.06);
        }
        if (tick == 58) {
            sound(server, EmberHeartSetup.ROAR.get(), 3f, 1.1f);
        }
        if (tick == 76) {
            sound(server, SoundEvents.FIRE_AMBIENT, 2f, 0.9f);
        }
    }

    // ---- 倒れる: 鼓動がゆっくりになり、熱が引いて冷え、殻がひび割れて灰になって崩れる (animation.ember_heart.death)
    @Override
    protected void deathEffects(ServerLevel server, int tick) {
        double cy = coreY();
        if (tick == 18 || tick == 40 || tick == 70) {
            sound(server, EmberHeartSetup.BEAT.get(), 2.5f, tick == 18 ? 0.8f : (tick == 40 ? 0.7f : 0.55f));
        }
        if (tick % 4 == 0 && tick < 90) {
            server.sendParticles(ParticleTypes.ASH, getX(), cy, getZ(), 3, 1.2, 1.2, 1.2, 0.01);
        }
        if (tick == 36 || tick == 60) {
            sound(server, SoundEvents.FIRE_EXTINGUISH, 2f, 0.8f);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), cy, getZ(), 20, 1.2, 1.0, 1.2, 0.03);
        }
        if (tick >= 56 && tick <= 86 && tick % 3 == 0) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()),
                    getX() + Mth.nextDouble(random, -1.5, 1.5), cy - 0.5, getZ() + Mth.nextDouble(random, -1.5, 1.5), 8, 0.4, 0.3, 0.4, 0.1);
        }
        if (tick == 56) {
            sound(server, SoundEvents.BASALT_BREAK, 2.5f, 0.6f);
        }
        if (tick == 74) {
            sound(server, SoundEvents.LAVA_POP, 3f, 0.8f);
            server.sendParticles(ParticleTypes.FLAME, getX(), cy, getZ(), 40, 0.8, 0.8, 0.8, 0.1);
            server.sendParticles(ParticleTypes.LAVA, getX(), cy, getZ(), 20, 0.8, 0.8, 0.8, 0);
        }
        if (tick == 88) {
            sound(server, SoundEvents.FIRE_EXTINGUISH, 3f, 0.6f);
            server.sendParticles(ParticleTypes.ASH, getX(), cy, getZ(), 200, 2.0, 1.8, 2.0, 0.05);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), cy, getZ(), 40, 1.5, 1.2, 1.5, 0.04);
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

    // 最後の一撃の音 (被弾と同じ)。倒れる演出は deathEffects が音も流すので、ここで別の音を重ねない
    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.EMBER_HEART_HURT.get();
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
        double cy = getY() + 3.0;
        int phase = getPhase();
        if (phase == 0) {
            if (random.nextFloat() < 0.2f) {
                level().addParticle(ParticleTypes.FLAME, getX() + Mth.nextDouble(random, -1.8, 1.8), cy + Mth.nextDouble(random, 0.5, 3),
                        getZ() + Mth.nextDouble(random, -1.8, 1.8), 0, 0.03, 0);
            }
        } else if (phase == 1) {
            if (random.nextFloat() < 0.4f) {
                level().addParticle(dust(0xFFE070, 1.0f), getX() + Mth.nextDouble(random, -2.4, 2.4), cy + Mth.nextDouble(random, -1.5, 3),
                        getZ() + Mth.nextDouble(random, -2.4, 2.4), 0, 0.03, 0);
            }
            if (random.nextFloat() < 0.25f) {
                level().addParticle(ParticleTypes.DRIPPING_LAVA, getX() + Mth.nextDouble(random, -2.7, 2.7), cy - 0.8,
                        getZ() + Mth.nextDouble(random, -2.7, 2.7), 0, 0, 0);
            }
        } else {
            if (random.nextFloat() < 0.5f) {
                level().addParticle(ParticleTypes.ASH, getX() + Mth.nextDouble(random, -3.3, 3.3), cy + Mth.nextDouble(random, -2.4, 3.6),
                        getZ() + Mth.nextDouble(random, -3.3, 3.3), 0, -0.01, 0);
            }
            if (random.nextFloat() < 0.18f) {
                level().addParticle(dust(0xE04020, 0.9f), getX() + Mth.nextDouble(random, -1.5, 1.5), cy + Mth.nextDouble(random, 0, 3),
                        getZ() + Mth.nextDouble(random, -1.5, 1.5), 0, 0.03, 0);
            }
        }
    }

    // 画面の外へ出たことがないよう、ログ用
    static void logBorn(int challengers) {
        SkyAbyss.LOGGER.info("The heart of the embers woke with {} challenger(s)", challengers);
    }
}
