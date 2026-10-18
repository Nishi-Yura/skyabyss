package jp.revati.skyabyss.titan.tide.heart;

import jp.revati.skyabyss.registry.MobSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.tide.TideTravel;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 潮殻の心臓: 潮殻の巨神の最終ボス (08 の 5.3節)。巻貝の殻の奥の心臓。宙に浮いて動かず、向きだけ変える。
// 体力の割合で三段階 (66% 未満で段階2、33% 未満で段階3)。変わり目は 3 秒の咆哮で、その間は傷を受けず輪が押し返す (岩鯨と同じ)。
//  段階1 閉じた殻 : 潮の吐息 (水の帯) / 殻打ち (床を走る輪) / 渦潮 (引いて弾ける)
//  段階2 開いた殻 : 満ち潮と珊瑚の槍 (床が満ち、島へ上がる) / 泡の牢 (浮かせる泡) / 真珠の脈 (追う真珠)。受ける傷 1.2 倍
//  段階3 月の潮   : 引き潮 (強く引いて弾ける。島の上は半分) / 月潮の大波 (島へ逃げる) / 真珠の雨 (島の上にも来る)
// 戦いの主題は「床と島の行き来」。段階は同期データで全員の画面に届き、模型 (段階ごとの geo) とボスバーの見た目を決める。
// 強さは 03 の 9.8節の目安 (推奨 Lv18: 体力 540、ふつうの技の一撃 13.5 前後)
public class TideHeart extends HeartBoss {
    public static final int PHASE_COUNT = 3;
    // 見た目と判定箱の大きさ (前の 1.5 倍。TideHeartSetup の sized と TideHeartRenderer の SCALE を合わせる)
    public static final double BODY_SCALE = 1.5;
    public static final float PHASE_TWO_AT = 0.66f;
    public static final float PHASE_THREE_AT = 0.33f;
    public static final int ROAR_TICKS = 60;
    public static final float MAX_HEALTH = 540f;
    // 段階2 (開いた殻) で受ける傷の倍率
    public static final float OPEN_SHELL_TAKEN = 1.2f;

    // 技の傷 (防具で減る前)。08 の表の値を、03 の 9.8節の目安 (ふつうの技の一撃 13.5) に合わせて 1〜2 割上げた (ゲームで、動かずに立つ人が推奨 Lv・ダイヤ一式でほぼ勝ててしまったため)
    public static final float BREATH_DAMAGE = 13.5f;
    public static final float SHELL_RING_DAMAGE = 10f;
    public static final float WHIRL_DAMAGE = 12.5f;
    public static final float SPEAR_DAMAGE = 11.5f;
    public static final float BUBBLE_DAMAGE_PER_SECOND = 2f;
    public static final float PEARL_DAMAGE = 9f;
    public static final float EBB_DAMAGE = 15f;
    public static final float WAVE_DAMAGE = 16.5f;
    public static final float RAIN_DAMAGE = 12.5f;
    // 引く速さ (ブロック/tick)。渦潮は歩けば逆らえる、引き潮は島の上では半分
    public static final double WHIRL_PULL = 0.08;
    public static final double EBB_PULL = 0.15;

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(TideHeart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ROARING = SynchedEntityData.defineId(TideHeart.class, EntityDataSerializers.BOOLEAN);

    public enum Action {
        NONE(null), ROAR(null),
        BREATH("breath"), SHELL_STRIKE("shell_strike"), WHIRL("whirl"),
        FLOOD("flood"), BUBBLE("bubble"), PEARL("pearl"),
        EBB("ebb"), MOON_WAVE("moon_wave"), PEARL_RAIN("pearl_rain");

        final String anim;

        Action(String anim) {
            this.anim = anim;
        }
    }

    public static final Action[] PHASE_ONE = {Action.BREATH, Action.SHELL_STRIKE, Action.WHIRL};
    public static final Action[] PHASE_TWO = {Action.FLOOD, Action.BUBBLE, Action.PEARL};
    public static final Action[] PHASE_THREE = {Action.EBB, Action.MOON_WAVE, Action.PEARL_RAIN};

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private Action last = Action.NONE;
    private int actionTicks;
    private int cooldown = 40;
    private float attackYaw;
    private boolean defeated;
    // 技の始めに決めて覚える数・向き (人数で変わる本数、返し波の安全な筋、大波の本数と角)
    private int lockedCount;
    @Nullable
    private double[] ebbGaps;
    private double moonSpan;
    private double moonBase;
    // 試験用: 次の技を始めない。開発用: 心臓の間の人数を多いことにする (0 なら実際の人数)
    private boolean holdAttacks;
    private int partyOverride;
    @Nullable
    private AABB arena;
    private float toughness = 1f;

    public TideHeart(EntityType<? extends Monster> type, Level level) {
        super(type, level, "tide_heart", "roar_1", "roar_2", "breath", "shell_strike", "whirl", "flood", "bubble", "pearl",
                "ebb", "moon_wave", "pearl_rain");
        this.xpReward = 300;
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 13)
                .add(Attributes.ARMOR, 8)
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
        entityData.set(DATA_PHASE, Mth.clamp(phase, 0, PHASE_COUNT - 1));
        entityData.set(DATA_ROARING, false);
        action = Action.NONE;
        cooldown = 20;
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

    // 戦いの部屋と挑む人数 (TideHeartBattle が湧かせるときに)
    public void bindArena(AABB arena, int challengers) {
        this.arena = arena;
        this.toughness = scaleFor(challengers);
    }

    // 人数による強さの倍率: 1 + 0.6 × (人数 − 1)。最大体力は変えず、受ける傷を割る (岩鯨と同じ)
    public static float scaleFor(int challengers) {
        return 1f + 0.6f * (Math.max(1, challengers) - 1);
    }

    public float toughness() {
        return toughness;
    }

    public boolean isBound() {
        return arena != null;
    }

    // 狙える人: 部屋の中の、心臓のグループの人 (GroupOwned。ほかのグループの人は狙わない)
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
            case BREATH -> tickBreath(server);
            case SHELL_STRIKE -> tickShellStrike(server);
            case WHIRL -> tickWhirl(server);
            case FLOOD -> tickFlood(server);
            case BUBBLE -> tickBubble(server);
            case PEARL -> tickPearl(server);
            case EBB -> tickEbb(server);
            case MOON_WAVE -> tickMoonWave(server);
            case PEARL_RAIN -> tickPearlRain(server);
            default -> {
            }
        }
    }

    // 段階の技から、直前と同じでないものを選ぶ。満ち潮の最中は満ち潮を選ばない
    private Action pickAttack() {
        Action[] bag = bagFor(getPhase());
        Action pick;
        int guard = 0;
        do {
            pick = bag[random.nextInt(bag.length)];
            guard++;
        } while ((pick == last || (pick == Action.FLOOD && TideHeartArena.flooding() && isBound())) && guard < 20);
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
        // 次の技までの間 (段階1 は 2.5〜3.3 秒、段階3 は 1.6〜2.2 秒)
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

    private static Vec3 forward(float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    private void sound(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        server.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    void strike(ServerPlayer player, float amount) {
        player.hurt(attackSource(), amount);
    }

    // 心臓の中心の高さ (模型の核)
    private double coreY() {
        return getY() + 2.0 * BODY_SCALE;
    }

    // 床の高さ: 部屋に縛られていれば部屋の床。そうでなければ真下へたどって最初の床
    double floorY() {
        if (arena != null) {
            return TideHeartArena.FLOOR;
        }
        return floorAt(Mth.floor(getX()), Mth.floor(getZ()));
    }

    // 真下へたどって最初の床 (島の上なら島の上)
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

    // 島の上にいるか (部屋の外では、床から 1.5 以上高い所)
    private boolean onIsland(Player player) {
        if (arena != null) {
            return TideHeartArena.onIsland(player.getX(), player.getY(), player.getZ());
        }
        return player.getY() >= floorY() + 1.5;
    }

    List<ServerPlayer> reachable(ServerLevel server, double range) {
        return server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range, 12, range), this::canReach);
    }

    // 人の動きに足す (サーバーの速さは人の本当の速さではないので、クライアントで足す)
    private static void nudge(ServerPlayer player, double x, double y, double z) {
        TideHeartNetwork.nudge(player, x, y, z);
    }

    // ---- 人数で増える数 (n = 心臓の間にいる人数。1〜6)。自己テストが同じ式を見る ----

    // 潮の吐息の帯: 一人なら一本、人数ぶん別の人へ (最大 4)
    public static int breathLines(int n) {
        return Math.min(Math.max(n, 1), 4);
    }

    // 殻打ちの輪: 三人から二重、五人から三重。安全な筋は二本 (五人から三本)
    public static int shellRings(int n) {
        return n >= 5 ? 3 : n >= 3 ? 2 : 1;
    }

    public static int shellLanes(int n) {
        return n >= 5 ? 3 : 2;
    }

    // 渦潮の横の渦: 二人から、人数 - 1 (最大 3)
    public static int whirlPools(int n) {
        return Math.min(Math.max(n - 1, 0), 3);
    }

    // 満ち潮の槍 (一度に突き上がる本数): 人数ぶん (最大 5) と、三人から 1、五人からもう 1 の「だれも立っていない所」
    public static int spearCount(int n) {
        return Math.min(Math.max(n, 1), 5) + (n >= 3 ? 1 : 0) + (n >= 5 ? 1 : 0);
    }

    // 泡の柱: 一人は 1、二人は 2、三人からは人数 (最大 5) + 1
    public static int bubblePillars(int n) {
        return n <= 2 ? Math.max(n, 1) : Math.min(n, 5) + 1;
    }

    // 追う真珠: 一人は 3、二人から 2 + 人数 (最大 8)
    public static int pearlCount(int n) {
        return n <= 1 ? 3 : Math.min(2 + n, 8);
    }

    // 引き潮の返し波 (跳び越える輪): 三人から 1、五人から 2
    public static int ebbRipples(int n) {
        return n >= 5 ? 2 : n >= 3 ? 1 : 0;
    }

    // 月潮の大波の本数 (回る波頭): 三人から 2、五人から 3。一本あたりの回る角は (本数が増えるほど狭い)
    public static int moonBars(int n) {
        return n >= 5 ? 3 : n >= 3 ? 2 : 1;
    }

    public static double moonSpanDegrees(int bars) {
        return bars == 1 ? 110 : bars == 2 ? 90 : 70;
    }

    // 真珠の雨の円の数: 人数 × 3 (最大 14)
    public static int rainCircles(int n) {
        return Math.min(14, 3 * Math.max(n, 1));
    }

    // ---- 範囲と予告の長さ ----
    public static final int BREATH_WARN = 24;
    public static final double BREATH_LENGTH = 17;
    public static final double BREATH_HALF_WIDTH = 1.5;
    public static final int SHELL_WARN = 20;
    public static final double SHELL_CORE = 3.6;
    public static final double SHELL_REACH = 15.5;
    public static final double SHELL_SPEED = 0.6;
    public static final float SHELL_CORE_DAMAGE = 8f;
    public static final int WHIRL_WARN = 70;
    public static final double WHIRL_BLAST = 5.0;
    public static final double WHIRL_POOL = 3.0;
    public static final float WHIRL_POOL_DAMAGE = 9f;
    public static final int SPEAR_WARN = 20;
    public static final double SPEAR_RADIUS = 1.3;
    public static final int BUBBLE_WARN = 30;
    public static final double BUBBLE_RADIUS = 1.6;
    public static final int PEARL_CHASE = 100;
    public static final int PEARL_WARN = 20;
    public static final double PEARL_RADIUS = 2.2;
    public static final int EBB_WARN = 100;
    public static final double EBB_BLAST = 5.5;
    public static final float EBB_RIPPLE_DAMAGE = 9f;
    public static final int MOON_WARN = 40;
    public static final int MOON_ACTIVE = 40;
    public static final double MOON_LENGTH = 15;
    public static final double MOON_HALF_WIDTH = 1.4;
    public static final int RAIN_WARN = 30;
    public static final double RAIN_RADIUS = 2.0;
    public static final double ROAR_PUSH_RADIUS = 8;
    public static final int ROAR_PUSH_WARN = 22;
    // 島の上に立つ人は、床の技 (reach を低くしたもの) に当たらない: 床 + 1.9 より上
    private static final double FLOOR_REACH = 1.9;

    // ---- 開発用・試験用 ----

    // 試験・コマンド用: 次の技を始めない (forceAction で出した技だけを見たいとき)
    public void holdAttacks() {
        holdAttacks = true;
    }

    public int actionTickCount() {
        return actionTicks;
    }

    // 開発用: 心臓の間にいる人数を、実際より多いことにする (一人で複数人の技の絵を撮るため。0 なら実際の人数)
    public void setPartyOverride(int count) {
        partyOverride = count;
    }

    private int present(ServerLevel server) {
        return Math.max(partyOverride, reachable(server, 40).size());
    }

    // 狙う地点 (人の足元) を最大 want か所 (いま狙っている人を先頭に、残りは入れ替える)。
    // fill が真なら、人が足りないぶんは床の適当な点で補う (開発用の人数の上書きと、「だれも立っていない所」の分)
    private List<Vec3> pickSpots(ServerLevel server, int want, boolean fill) {
        List<ServerPlayer> all = new ArrayList<>(reachable(server, 40));
        java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
        LivingEntity main = getTarget();
        if (main instanceof ServerPlayer mp && all.remove(mp)) {
            all.add(0, mp);
        }
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : all) {
            if (spots.size() < want) {
                spots.add(p.position());
            }
        }
        while (fill && spots.size() < want) {
            spots.add(randomFloorSpot());
        }
        return spots;
    }

    // 床の上の適当な点 (心臓から 4〜11。部屋の中なら島の上は避ける)
    private Vec3 randomFloorSpot() {
        double x = getX();
        double z = getZ();
        for (int tries = 0; tries < 12; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 4 + random.nextDouble() * 7;
            x = getX() + Math.cos(a) * r;
            z = getZ() + Math.sin(a) * r;
            if (!isBound() || !nearIsland(x, z)) {
                break;
            }
        }
        return new Vec3(x, standY(x, z), z);
    }

    private static boolean nearIsland(double x, double z) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (Math.hypot(x - (TideHeartArena.CX + 0.5 + sx * TideHeartArena.ISLAND_OFFSET), z - (TideHeartArena.CZ + 0.5 + sz * TideHeartArena.ISLAND_OFFSET)) < 3.6) {
                    return true;
                }
            }
        }
        return false;
    }

    // 予告の組み立て (形・長さ・印の種類。持ち主は心臓、当たるのは心臓の間の人)。床の技: 島の上は当たらない
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
        for (ServerPlayer p : server.players()) {
            if (p.position().equals(spot)) {
                return p;
            }
        }
        return null;
    }

    private Telegraph.Builder tg(ServerLevel server, TelegraphShape shape, int warn, int style) {
        return preset(Telegraph.in(server, shape).floor(floorY()).warn(warn).owner(this).victims(this::canReach).reach(FLOOR_REACH), style);
    }

    // 立っている所への予告 (島の上の人には島の上の高さで円を置く。円の高さ + 1.9 までの人に当たる: 床の円は島の上の人に、島の円は床の人に当たらない)
    private Telegraph.Builder tgAt(ServerLevel server, TelegraphShape shape, double y, int warn, int style) {
        return preset(Telegraph.in(server, shape).floor(y).warn(warn).owner(this).victims(this::canReach).reach(FLOOR_REACH), style);
    }

    private double angleTo(Vec3 spot) {
        return Telegraph.angleOf(spot.x - getX(), spot.z - getZ());
    }

    // 輪が抜ける安全な筋 (角度の一覧)。基準の角から等間隔
    private static double[] lanes(double base, int count) {
        double[] gaps = new double[count];
        for (int i = 0; i < count; i++) {
            gaps[i] = base + Math.PI * 2 * i / count;
        }
        return gaps;
    }

    private static final double LANE_HALF = Math.toRadians(22);

    // 安全な筋の印 (見た目だけ。緑の扇)
    private void laneMarks(ServerLevel server, double[] gaps, int warn) {
        for (double gap : gaps) {
            tg(server, TelegraphShape.cone(getX(), getZ(), gap, 15, LANE_HALF), warn, S).start();
        }
    }

    // 床を走る輪 (跳べば当たらない。安全な筋は何もない)
    private void ripple(ServerLevel server, double r0, int warn, double[] gaps, float damage, boolean slow, boolean first) {
        int active = (int) Math.ceil((SHELL_REACH - r0) / SHELL_SPEED);
        tg(server, TelegraphShape.wave(getX(), getZ(), r0, SHELL_REACH, 0.9, gaps, LANE_HALF), warn, D).active(active).jumpClear(0.9)
                .onImpact(s -> {
                    if (first) {
                        sound(s, SoundEvents.TURTLE_EGG_CRACK, 2f, 0.5f);
                        s.sendParticles(ParticleTypes.SPLASH, getX(), floorY() + 0.4, getZ(), 50, 2.0, 0.3, 2.0, 0.2);
                    }
                })
                .onHit((s, player) -> {
                    strike(player, damage);
                    if (slow) {
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                    }
                    Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                    player.knockback(0.9, -to.x, -to.z);
                    player.hurtMarked = true;
                })
                .start();
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
    // 傷はない。半径 8 の円の中は 1.1 秒の予告のあと押し返される (円の外は動かない)
    private void tickRoar(ServerLevel server) {
        if (actionTicks == 1) {
            entityData.set(DATA_ROARING, true);
            sound(server, TideHeartSetup.ROAR.get(), 3f, 1f);
            server.sendParticles(ParticleTypes.SPLASH, getX(), coreY(), getZ(), 80, 1.6 * BODY_SCALE, 1.4 * BODY_SCALE, 1.6 * BODY_SCALE, 0.3);
            tg(server, TelegraphShape.circle(getX(), getZ(), ROAR_PUSH_RADIUS), ROAR_PUSH_WARN, H).reach(3.5)
                    .onImpact(s -> {
                        sound(s, SoundEvents.GENERIC_SPLASH, 2f, 0.6f);
                        s.sendParticles(ParticleTypes.SPLASH, getX(), floorY() + 0.5, getZ(), 80, 4.0, 0.3, 4.0, 0.4);
                    })
                    .onHit((s, player) -> {
                        Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                        player.knockback(1.0, -to.x, -to.z);
                        player.hurtMarked = true;
                    })
                    .start();
        }
        if (actionTicks % 4 == 0 && actionTicks < 56) {
            warn(server, dust(getPhase() == 0 ? 0xF0E4EC : 0xC8DCFA, 1.3f), getX(), coreY(), getZ(), 6, 1.2 * BODY_SCALE, 1.2 * BODY_SCALE, 1.2 * BODY_SCALE, 0.05);
        }
        if (actionTicks >= ROAR_TICKS) {
            entityData.set(DATA_ROARING, false);
            entityData.set(DATA_PHASE, Math.min(PHASE_COUNT - 1, getPhase() + 1));
            sound(server, SoundEvents.BEACON_ACTIVATE, 2.5f, 1.4f);
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), coreY(), getZ(), 60, 1.8 * BODY_SCALE, 1.6 * BODY_SCALE, 1.8 * BODY_SCALE, 0.1);
            finish();
            cooldown = 40;
        }
    }

    // ---- 段階1 ----

    // 潮の吐息 (1.2 秒): 心臓から人へ伸びる水の線 (幅 3・長さ 20)。人数が増えると、別の人へ向けた線が最大 4 本
    private void tickBreath(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.BREATH.get(), 2f, 1f);
            List<Vec3> targets = pickSpots(server, breathLines(present(server)), true);
            List<Double> angles = new ArrayList<>();
            List<ServerPlayer> aimed = new ArrayList<>();
            for (Vec3 p : targets) {
                angles.add(angleTo(p));
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
                boolean sounds = first;
                first = false;
                tg(server, TelegraphShape.line(getX() + dx * 0.5, getZ() + dz * 0.5, angle, BREATH_LENGTH, BREATH_HALF_WIDTH), BREATH_WARN, D).target(aim)
                        .onImpact(s -> {
                            if (sounds) {
                                sound(s, SoundEvents.GENERIC_SPLASH, 2f, 0.7f);
                            }
                            for (double d = 2.5; d <= BREATH_LENGTH; d += 2.5) {
                                s.sendParticles(ParticleTypes.SPLASH, getX() + dx * d, floorY() + 0.8, getZ() + dz * d, 5, 0.7, 0.4, 0.7, 0.2);
                                s.sendParticles(ParticleTypes.BUBBLE_POP, getX() + dx * d, floorY() + 1.2, getZ() + dz * d, 2, 0.5, 0.4, 0.5, 0.05);
                            }
                        })
                        .onHit((s, player) -> {
                            strike(player, BREATH_DAMAGE);
                            player.knockback(1.5, -dx, -dz);
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 殻打ち (1.0 秒): 殻が開きかけて震え、足もとの円 (半径 3.6) を打ち、そこから床を走る輪が広がる。
    // 円の中は傷。輪は跳べば当たらず、安全な筋 (緑の扇) は何もない。三人から輪は二重 (0.7 秒おき)、五人から三重、筋も三本
    private void tickShellStrike(ServerLevel server) {
        int rings = 1;
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.SHELL.get(), 2f, 1f);
            int n = present(server);
            lockedCount = shellRings(n);
            double[] gaps = lanes(random.nextDouble() * Math.PI * 2, shellLanes(n));
            tg(server, TelegraphShape.circle(getX(), getZ(), SHELL_CORE), SHELL_WARN, D)
                    .onImpact(s -> {
                        sound(s, SoundEvents.TURTLE_EGG_CRACK, 2f, 0.4f);
                        s.sendParticles(ParticleTypes.SPLASH, getX(), floorY() + 0.4, getZ(), 50, 2.0, 0.3, 2.0, 0.2);
                    })
                    .onHit((s, player) -> {
                        strike(player, SHELL_CORE_DAMAGE);
                        Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                        player.knockback(1.0, -to.x, -to.z);
                        player.hurtMarked = true;
                    })
                    .start();
            laneMarks(server, gaps, SHELL_WARN);
            for (int i = 0; i < lockedCount; i++) {
                ripple(server, SHELL_CORE + 1.0, SHELL_WARN + i * 14, gaps, SHELL_RING_DAMAGE, true, i == 0);
            }
        }
        rings = Math.max(1, lockedCount);
        int active = (int) Math.ceil((SHELL_REACH - SHELL_CORE - 1.0) / SHELL_SPEED);
        if (actionTicks >= SHELL_WARN + (rings - 1) * 14 + active + 8) {
            finish();
        }
    }

    // 渦潮 (1.5 秒の溜め): 床の水が中心へ渦を巻く。2 秒のあいだ中心へゆっくり引き (歩けば逆らえる)、終わりに半径 5 の円が弾ける。
    // 二人から、心臓のまわりの渦だまり (半径 3) が別の場所で同時に弾ける (人数 - 1 か所、最大 3)
    private void tickWhirl(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.WHIRL.get(), 2.2f, 1f);
            tg(server, TelegraphShape.circle(getX(), getZ(), WHIRL_BLAST), WHIRL_WARN, D)
                    .onImpact(s -> {
                        sound(s, SoundEvents.GENERIC_SPLASH, 2.5f, 0.6f);
                        s.sendParticles(ParticleTypes.SPLASH, getX(), floorY() + 0.5, getZ(), 120, 3.0, 0.5, 3.0, 0.4);
                    })
                    .onHit((s, player) -> {
                        strike(player, WHIRL_DAMAGE);
                        Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                        player.knockback(0.8, -out.x, -out.z);
                        player.hurtMarked = true;
                    })
                    .start();
            int pools = whirlPools(present(server));
            double base = random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < pools; i++) {
                double a = base + Math.PI * 2 * i / pools;
                double r = 9.5;
                double x = getX() + Math.cos(a) * r;
                double z = getZ() + Math.sin(a) * r;
                double y = standY(x, z);
                tgAt(server, TelegraphShape.circle(x, z, WHIRL_POOL), y, WHIRL_WARN, D)
                        .onImpact(s -> {
                            s.playSound(null, x, y, z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.5f, 0.8f);
                            s.sendParticles(ParticleTypes.SPLASH, x, y + 0.5, z, 60, 1.5, 0.4, 1.5, 0.3);
                        })
                        .onHit((s, player) -> {
                            strike(player, WHIRL_POOL_DAMAGE);
                            Vec3 out = player.position().subtract(x, y, z).multiply(1, 0, 1);
                            player.knockback(0.8, -out.x, -out.z);
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        if (actionTicks < WHIRL_WARN && actionTicks % 4 == 0) {
            // 渦: 回りながら縮む粒の腕 (見た目だけ。傷は円の印のとおり)
            for (int arm = 0; arm < 4; arm++) {
                for (double r = 3.0; r <= 12; r += 3.0) {
                    double a = Math.toRadians(arm * 90 + actionTicks * 9 + r * 25);
                    warn(server, dust(0x9AA4AE, 1.0f), getX() + Math.cos(a) * r, fy + 0.15, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
        }
        if (actionTicks >= 30 && actionTicks < WHIRL_WARN && actionTicks % 2 == 0) {
            for (ServerPlayer player : reachable(server, 22)) {
                pull(player, WHIRL_PULL);
            }
        }
        if (actionTicks >= WHIRL_WARN + 12) {
            finish();
        }
    }

    // ---- 段階2 ----

    // 満ち潮と珊瑚の槍 (1.5 秒): 床が 6 秒満ちる (島の上は濡れない。島の上に緑の印)。満ちている間、床の人の足もとに円が 1 秒出て、槍が 3 回突き上がる。
    // 島の上に立つ人には当たらない。人数ぶんの槍 (最大 5) と、三人から「だれも立っていない所」にも (最大 +2)
    private void tickFlood(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.FLOOD.get(), 2.5f, 1f);
            if (isBound()) {
                // 水に濡れない所 (岩の島の上) を緑で示す
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        double ix = TideHeartArena.CX + 0.5 + sx * TideHeartArena.ISLAND_OFFSET;
                        double iz = TideHeartArena.CZ + 0.5 + sz * TideHeartArena.ISLAND_OFFSET;
                        tgAt(server, TelegraphShape.circle(ix, iz, 2.2), fy + TideHeartArena.ISLAND_HEIGHT, 30, S).start();
                    }
                }
            }
        }
        if (actionTicks < 30 && actionTicks % 3 == 0) {
            // 溝から水が上がる予告 (外周の輪)
            for (int a = 0; a < 360; a += 10) {
                double rad = Math.toRadians(a + actionTicks * 2);
                double r = isBound() ? 13 : 9;
                warn(server, dust(0x9AA4AE, 1.1f), getX() + Math.cos(rad) * r, fy + 0.2 + actionTicks / 40.0, getZ() + Math.sin(rad) * r, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks == 30 && isBound()) {
            TideHeartArena.startFlood(server, 120);
        }
        // 槍は満ちてから (30 + 20 tick 後) 3 回、30 tick おき
        for (int k = 0; k < 3; k++) {
            if (actionTicks == 50 + k * 30) {
                spears(server, fy);
            }
        }
        if (actionTicks >= 50 + 2 * 30 + SPEAR_WARN + 10) {
            finish();
        }
    }

    private void spears(ServerLevel server, double fy) {
        int n = present(server);
        int want = spearCount(n);
        // 床の人 (島の上の人は狙わない) を先頭に、足りないぶんは床の適当な点
        List<ServerPlayer> onFloor = new ArrayList<>();
        for (ServerPlayer p : reachable(server, 24)) {
            if (!onIsland(p)) {
                onFloor.add(p);
            }
        }
        java.util.Collections.shuffle(onFloor, new java.util.Random(random.nextLong()));
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : onFloor) {
            if (spots.size() < Math.min(Math.max(n, 1), 5)) {
                spots.add(p.position());
            }
        }
        while (spots.size() < want) {
            spots.add(randomFloorSpot());
        }
        boolean first = true;
        for (Vec3 p : spots) {
            double x = p.x;
            double z = p.z;
            boolean sounds = first;
            first = false;
            Telegraph.in(server, TelegraphShape.circle(x, z, SPEAR_RADIUS)).floor(fy).warn(SPEAR_WARN).danger().owner(this).victims(this::canReach)
                    .reach(FLOOR_REACH)
                    .onImpact(s -> {
                        s.playSound(null, x, fy, z, TideHeartSetup.SPEAR.get(), SoundSource.HOSTILE, sounds ? 1.6f : 0.9f, 1f);
                        s.sendParticles(dust(0xFF7A80, 1.4f), x, fy + 1.0, z, 30, 0.25, 1.0, 0.25, 0.05);
                        s.sendParticles(ParticleTypes.SPLASH, x, fy + 0.6, z, 20, 0.5, 0.3, 0.5, 0.2);
                    })
                    .onHit((s, player) -> {
                        strike(player, SPEAR_DAMAGE);
                        nudge(player, 0, 0.6, 0);
                    })
                    .start();
        }
    }

    // 泡の牢 (1.5 秒): 人のいる所に泡の柱が立つ (半径 1.6 の円、1.5 秒の予告)。柱の中の人は泡に閉じこめられて浮かぶ (最長 4 秒。泡は叩けば割れる)。
    // 人数が増えると、別の人の足もとに一本ずつ (最大 5) と、三人から「だれも立っていない所」に 1 本
    private void tickBubble(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.BUBBLE.get(), 2f, 1f);
            List<Vec3> spots = pickSpots(server, bubblePillars(present(server)), true);
            boolean first = true;
            for (Vec3 p : spots) {
                double x = p.x;
                double z = p.z;
                double y = standY(x, z);
                boolean sounds = first;
                first = false;
                tgAt(server, TelegraphShape.circle(x, z, BUBBLE_RADIUS), y, BUBBLE_WARN, D)
                        .onImpact(s -> {
                            if (sounds) {
                                s.playSound(null, x, y, z, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 2f, 1f);
                            }
                            s.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y + 0.3, z, 30, 0.5, 0.3, 0.5, 0.1);
                        })
                        .onHit((s, player) -> {
                            if (!TideBubble.isTrapped(s, player)) {
                                s.addFreshEntity(new TideBubble(s, player, this));
                            }
                        })
                        .start();
            }
        }
        if (actionTicks >= BUBBLE_WARN + 12) {
            finish();
        }
    }

    // 真珠の脈 (1.0 秒): 心臓が白く光り、追ってくる真珠を放つ (一人 3 つ、人数が増えると 2 + 人数で最大 8)。
    // 真珠は触れても傷にならない。触れたとき・追いつづけて 5 秒たったときに止まり、足もとに半径 2.2 の円が 1 秒出て弾ける。叩けば消える
    private void tickPearl(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.PEARL.get(), 2f, 1f);
        }
        if (actionTicks < 20 && actionTicks % 2 == 0) {
            warn(server, dust(0xFFFFFF, 1.5f), getX(), coreY(), getZ(), 8, 0.9 * BODY_SCALE, 0.9 * BODY_SCALE, 0.9 * BODY_SCALE, 0.02);
        }
        if (actionTicks == 20) {
            List<ServerPlayer> players = reachable(server, 26);
            if (!players.isEmpty()) {
                players.sort(Comparator.comparingDouble(p -> p.distanceToSqr(this)));
                int count = pearlCount(present(server));
                for (int i = 0; i < count; i++) {
                    ServerPlayer p = players.get(i % players.size());
                    double a = Math.toRadians(attackYaw + 90 + (i - (count - 1) / 2.0) * (180.0 / Math.max(3, count)));
                    TidePearl pearl = new TidePearl(server, this, p,
                            new Vec3(getX() - Math.sin(a) * 3.4, coreY() + (i % 3 - 1) * 0.6, getZ() + Math.cos(a) * 3.4));
                    server.addFreshEntity(pearl);
                }
            }
        }
        if (actionTicks >= 32) {
            finish();
        }
    }

    // 真珠が止まったところの予告 (半径 2.2、1 秒)。返すのは取り消せる予告 (真珠が叩かれたとき)
    Telegraph pearlBurst(ServerLevel server, double x, double z) {
        double y = standY(x, z);
        return tgAt(server, TelegraphShape.circle(x, z, PEARL_RADIUS), y, PEARL_WARN, D)
                .onImpact(s -> {
                    s.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.6f, 1.2f);
                    s.sendParticles(ParticleTypes.END_ROD, x, y + 0.6, z, 24, 0.9, 0.4, 0.9, 0.08);
                })
                .onHit((s, player) -> {
                    strike(player, PEARL_DAMAGE);
                    nudge(player, 0, 0.3, 0);
                })
                .start();
    }

    // ---- 段階3 ----

    // 引き潮 (2 秒の溜めのあと 3 秒): 床の水が中心へ流れる。3 秒のあいだ中心へ引く (島の上では半分)。終わりに半径 5.5 の円が弾ける (予告は最初から)。
    // 三人から、弾けたあと跳び越える輪 (返し波) が一重ずつ増える (五人から二重)。輪の安全な筋は緑
    private void tickEbb(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.EBB.get(), 2.2f, 1f);
            int n = present(server);
            lockedCount = ebbRipples(n);
            tg(server, TelegraphShape.circle(getX(), getZ(), EBB_BLAST), EBB_WARN, D)
                    .onImpact(s -> {
                        sound(s, SoundEvents.GENERIC_SPLASH, 2.5f, 0.5f);
                        s.sendParticles(ParticleTypes.SPLASH, getX(), floorY() + 0.5, getZ(), 160, 4.0, 0.6, 4.0, 0.5);
                    })
                    .onHit((s, player) -> {
                        strike(player, EBB_DAMAGE);
                        Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                        player.knockback(1.0, -out.x, -out.z);
                        player.hurtMarked = true;
                    })
                    .start();
            if (lockedCount > 0) {
                double[] gaps = lanes(random.nextDouble() * Math.PI * 2, shellLanes(n));
                ebbGaps = gaps;
                for (int i = 0; i < lockedCount; i++) {
                    ripple(server, EBB_BLAST + 1.0, EBB_WARN + i * 14, gaps, EBB_RIPPLE_DAMAGE, true, false);
                }
            }
        }
        if (actionTicks == EBB_WARN - 30 && lockedCount > 0 && ebbGaps != null) {
            laneMarks(server, ebbGaps, 30);
        }
        if (actionTicks < EBB_WARN && actionTicks % 4 == 0) {
            // 中心へ流れる水の筋 (外から内へ動く。見た目だけ)
            for (int k = 0; k < 12; k++) {
                double a = Math.toRadians(k * 30 + 7);
                double r = 14 - ((actionTicks * 0.35 + k * 1.7) % 12);
                warn(server, dust(0x9AA4AE, 1.3f), getX() + Math.cos(a) * r, fy + 0.15, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks >= 40 && actionTicks < EBB_WARN && actionTicks % 2 == 0) {
            for (ServerPlayer player : reachable(server, 22)) {
                pull(player, onIsland(player) ? EBB_PULL * 0.5 : EBB_PULL);
            }
        }
        int ripples = lockedCount;
        int active = (int) Math.ceil((SHELL_REACH - EBB_BLAST - 1.0) / SHELL_SPEED);
        if (actionTicks >= EBB_WARN + (ripples > 0 ? (ripples - 1) * 14 + active + 8 : 12)) {
            finish();
        }
    }

    // 月潮の大波 (2 秒): 心臓から伸びる波頭が、印の扇のなかを 2 秒かけて一度薙ぐ (幅 2.8・長さ 15)。波の高さは 2 なので、岩の島の上に立てば越える。
    // 扇の外は何もない。三人から 2 本、五人から 3 本 (一本あたりの扇は狭くなり、扇のあいだに安全な楔が残る)
    private void tickMoonWave(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.WAVE.get(), 3f, 1f);
            int bars = moonBars(present(server));
            lockedCount = bars;
            moonSpan = Math.toRadians(moonSpanDegrees(bars));
            moonBase = random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < bars; i++) {
                double start = moonBase + Math.PI * 2 * i / bars;
                tg(server, TelegraphShape.sweep(getX(), getZ(), start, moonSpan, MOON_LENGTH, MOON_HALF_WIDTH), MOON_WARN, D).active(MOON_ACTIVE)
                        .onImpact(s -> sound(s, TideHeartSetup.WAVE.get(), 2f, 1.3f))
                        .onHit((s, player) -> {
                            strike(player, WAVE_DAMAGE);
                            Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                            player.knockback(1.6, -out.x, -out.z);
                            nudge(player, 0, 0.35, 0);
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        // 波頭の白い波しぶき (当たりの帯のとおり)
        if (actionTicks > MOON_WARN && actionTicks <= MOON_WARN + MOON_ACTIVE && actionTicks % 2 == 0) {
            double p = (actionTicks - MOON_WARN) / (double) MOON_ACTIVE;
            for (int i = 0; i < lockedCount; i++) {
                double a = moonBase + Math.PI * 2 * i / lockedCount + moonSpan * p;
                for (double d = 3; d <= MOON_LENGTH; d += 2.0) {
                    double x = getX() + Math.cos(a) * d;
                    double z = getZ() + Math.sin(a) * d;
                    server.sendParticles(ParticleTypes.SPLASH, x, fy + 1.6, z, 3, 0.3, 0.1, 0.3, 0.2);
                    server.sendParticles(ParticleTypes.CLOUD, x, fy + 1.0, z, 1, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }
        if (actionTicks >= MOON_WARN + MOON_ACTIVE + 6) {
            finish();
        }
    }

    // 真珠の雨 (1.5 秒): 人の足もとと周りに円 (半径 2、1.5 秒の予告)。島の上にも来る。当たると少し跳ね上がる。
    // 人数 × 3 の円 (最大 14)。本人の足もと 1 つと、そのまわり 2 つ
    private void tickPearlRain(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.RAIN.get(), 2f, 1f);
            int n = present(server);
            int total = rainCircles(n);
            List<Vec3> mains = pickSpots(server, Math.max(1, n), true);
            List<Vec3> circles = new ArrayList<>();
            for (Vec3 p : mains) {
                if (circles.size() < total) {
                    circles.add(p);
                }
            }
            int around = 0;
            while (circles.size() < total) {
                Vec3 p = mains.get(around % mains.size());
                around++;
                double a = random.nextDouble() * Math.PI * 2;
                double r = 2.8 + random.nextDouble() * 2.0;
                circles.add(new Vec3(p.x + Math.cos(a) * r, p.y, p.z + Math.sin(a) * r));
            }
            boolean first = true;
            for (Vec3 c : circles) {
                double x = c.x;
                double z = c.z;
                double y = standY(x, z);
                boolean sounds = first;
                first = false;
                tgAt(server, TelegraphShape.circle(x, z, RAIN_RADIUS), y, RAIN_WARN, D)
                        .onImpact(s -> {
                            s.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, sounds ? 1.6f : 0.8f, 1.4f);
                            s.sendParticles(ParticleTypes.END_ROD, x, y + 0.3, z, 16, 0.8, 0.2, 0.8, 0.05);
                        })
                        .onHit((s, player) -> {
                            strike(player, RAIN_DAMAGE);
                            nudge(player, 0, 0.5, 0);
                        })
                        .start();
            }
        }
        // 降ってくる光 (最後の 0.5 秒。見た目だけ)
        if (actionTicks >= RAIN_WARN - 10 && actionTicks < RAIN_WARN && actionTicks % 2 == 0) {
            double drop = (RAIN_WARN - actionTicks) / 10.0;
            for (ServerPlayer p : reachable(server, 24)) {
                warn(server, ParticleTypes.END_ROD, p.getX(), standY(p.getX(), p.getZ()) + 1 + drop * 8, p.getZ(), 2, 0.1, 0.2, 0.1, 0);
            }
        }
        if (actionTicks >= 44) {
            finish();
        }
    }

    // 立てる高さ (部屋では、島なら島の上、ほかは床)
    private double standY(double x, double z) {
        if (isBound()) {
            return TideHeartArena.island(Mth.floor(x), Mth.floor(z)) ? TideHeartArena.FLOOR + TideHeartArena.ISLAND_HEIGHT : TideHeartArena.FLOOR;
        }
        return floorAt(Mth.floor(x), Mth.floor(z));
    }

    // 咆哮の間は傷を受けない (殻が鳴って弾く)。段階2 の開いた殻は 1.2 倍。人数の分だけ割る
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRoaring()) {
            if (level() instanceof ServerLevel server) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.8f);
                server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), coreY(), getZ(), 8, 0.8, 0.8, 0.8, 0.1);
            }
            return false;
        }
        return super.hurt(source, takenFor(getPhase(), amount, toughness));
    }

    // 受ける傷 (段階と人数で)。自己テストからも使う
    public static float takenFor(int phase, float amount, float toughness) {
        return (phase == 1 ? amount * OPEN_SHELL_TAKEN : amount) / toughness;
    }

    private void updateBossBarViewers() {
        for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
            if (!player.isAlive() || player.distanceToSqr(this) > 48 * 48 || player.level() != level()
                    || !jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(this, player)) {
                bossBar.removePlayer(player);
            }
        }
        if (level() instanceof ServerLevel server) {
            // ボスバーは、心臓のグループの人にだけ
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

    // 倒れたとき: 殻が開いて真珠が浮かび、泡になって弾ける演出 (deathEffects) のあとで、報酬・進み・記録を渡す (onDeathFinished。TideHeartBattle)
    @Override
    public void die(DamageSource source) {
        if (defeated) {
            return;
        }
        defeated = true;
        super.die(source);
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), TideHeartSetup.DEATH.get(), SoundSource.HOSTILE, 3f, 1f);
            server.sendParticles(ParticleTypes.SPLASH, getX(), coreY(), getZ(), 160, 1.5, 1.6, 1.5, 0.3);
            warn(server, dust(0xC8DCFA, 1.6f), getX(), coreY(), getZ(), 80, 1.5, 1.5, 1.5, 0.1);
        }
    }

    // 試練で湧いた心臓だけが、殻の底の攻略を知らせる (報酬・進み・記録は TideHeartBattle)
    @Override
    protected void onDeathFinished(ServerLevel server) {
        if (server.dimension() == TideTravel.TIDE_BACK && getTags().contains(TitanBackTrials.HEART_TAG)) {
            TideHeartBattle.onDefeated(server, this);
        }
    }

    @Override
    protected double coreHeight() {
        return coreY();
    }

    // ---- 登場: 水の底から核が浮かび、巻貝の殻がほどけて開き、真珠のまわりを水滴の輪が回りはじめる (animation.tide_heart.intro)
    @Override
    protected void introEffects(ServerLevel server, int tick) {
        double cy = coreY();
        double fy = floorY();
        if (tick == 0) {
            sound(server, TideHeartSetup.BEAT.get(), 3f, 0.6f);
            sound(server, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 2f, 0.7f);
        }
        if (tick < 52 && tick % 4 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 1.5 + random.nextDouble() * 2.5;
            server.sendParticles(ParticleTypes.SPLASH, getX() + Math.cos(a) * r, fy + 0.1 + tick * 0.04, getZ() + Math.sin(a) * r, 12, 0.3, 0.1, 0.3, 0.1);
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX() + Math.cos(a) * r, fy + 0.5 + tick * 0.04, getZ() + Math.sin(a) * r, 4, 0.2, 0.3, 0.2, 0.02);
        }
        if (tick == 8 || tick == 16 || tick == 24) {
            sound(server, TideHeartSetup.BEAT.get(), 2.5f, 0.7f + 0.1f * (tick / 8));
        }
        if (tick == 36) {
            sound(server, SoundEvents.GENERIC_SPLASH, 3f, 0.7f);
            server.sendParticles(ParticleTypes.SPLASH, getX(), cy, getZ(), 100, 1.4, 1.2, 1.4, 0.25);
        }
        if (tick == 52) {
            sound(server, TideHeartSetup.ROAR.get(), 2.5f, 1.4f);
            warn(server, dust(0xC8DCFA, 1.5f), getX(), cy, getZ(), 60, 1.8, 1.5, 1.8, 0.1);
        }
        if (tick == 66) {
            sound(server, SoundEvents.AMETHYST_BLOCK_CHIME, 3f, 0.8f);
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), cy, getZ(), 80, 1.8, 1.5, 1.8, 0.05);
            warn(server, dust(0x78F6DE, 1.2f), getX(), cy, getZ(), 50, 1.6, 1.4, 1.6, 0.05);
        }
    }

    // ---- 倒れる: 殻が震えて開き、真珠が浮かび上がって脈を弱め、泡になって弾ける (animation.tide_heart.death)
    @Override
    protected void deathEffects(ServerLevel server, int tick) {
        double cy = coreY();
        if (tick == 2 || tick == 8 || tick == 14) {
            sound(server, SoundEvents.AMETHYST_BLOCK_HIT, 2f, 0.6f + 0.1f * (tick / 6));
        }
        if (tick == 16) {
            sound(server, SoundEvents.GENERIC_SPLASH, 2.5f, 0.8f);
            server.sendParticles(ParticleTypes.SPLASH, getX(), cy, getZ(), 60, 1.6, 1.4, 1.6, 0.2);
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), cy, getZ(), 30, 1.5, 1.5, 1.5, 0.05);
        }
        if (tick == 28) {
            sound(server, SoundEvents.AMETHYST_CLUSTER_BREAK, 3f, 0.7f);
            server.sendParticles(ParticleTypes.SPLASH, getX(), cy, getZ(), 120, 1.8, 1.5, 1.8, 0.3);
            warn(server, dust(0xC8DCFA, 1.5f), getX(), cy, getZ(), 60, 1.8, 1.5, 1.8, 0.15);
        }
        if (tick >= 28 && tick <= 64 && tick % 3 == 0) {
            double pearl = cy + 1.75 * Math.min(1.0, (tick - 28) / 36.0);
            server.sendParticles(ParticleTypes.END_ROD, getX(), pearl, getZ(), 2, 0.3, 0.3, 0.3, 0.02);
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), pearl, getZ(), 3, 0.4, 0.4, 0.4, 0.02);
        }
        if (tick == 64 || tick == 72 || tick == 80) {
            sound(server, SoundEvents.AMETHYST_BLOCK_CHIME, 3f, 0.8f + 0.2f * ((tick - 64) / 8));
        }
        if (tick == 88) {
            double pearl = cy + 1.75;
            sound(server, SoundEvents.BEACON_DEACTIVATE, 3f, 1.2f);
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), pearl, getZ(), 200, 2.0, 2.0, 2.0, 0.2);
            server.sendParticles(ParticleTypes.SPLASH, getX(), pearl, getZ(), 100, 1.5, 1.5, 1.5, 0.25);
            warn(server, dust(0xC8DCFA, 1.8f), getX(), pearl, getZ(), 120, 1.5, 1.5, 1.5, 0.2);
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
        return TideHeartSetup.BEAT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.TIDE_HEART_HURT.get();
    }

    // 最後の一撃の音 (被弾と同じ)。倒れる演出は deathEffects が音も流すので、ここで別の音を重ねない
    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.TIDE_HEART_HURT.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    // 光の粒: 段階1 は泡、段階2 は光る部屋の青緑、段階3 は回る水滴と銀の粒 (疎な段階3 を埋める)
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            return;
        }
        double cy = getY() + 2.0 * BODY_SCALE;
        int phase = getPhase();
        if (phase == 0) {
            if (random.nextFloat() < 0.15f) {
                level().addParticle(ParticleTypes.BUBBLE_POP, getX() + Mth.nextDouble(random, -1.5, 1.5), cy + Mth.nextDouble(random, -1.5, 1.5),
                        getZ() + Mth.nextDouble(random, -0.6, 0.6), 0, 0.02, 0);
            }
        } else if (phase == 1) {
            if (random.nextFloat() < 0.35f) {
                level().addParticle(dust(0x78F6DE, 1.0f), getX() + Mth.nextDouble(random, -1.6, 1.6), cy + Mth.nextDouble(random, -1.4, 1.4),
                        getZ() + Mth.nextDouble(random, -0.6, 0.6), 0, 0.02, 0);
            }
        } else {
            // 心臓のまわりを回る水滴の輪 (模型の輪の外側にもう一つ、傾けて)
            for (int k = 0; k < 2; k++) {
                double a = (tickCount * 0.09) + k * Math.PI + random.nextDouble() * 0.3;
                double r = 2.6;
                level().addParticle(dust(0xA0FCF4, 0.9f), getX() + Math.cos(a) * r, cy + Math.sin(a * 2) * 0.5, getZ() + Math.sin(a) * r, 0, 0, 0);
            }
            if (random.nextFloat() < 0.5f) {
                level().addParticle(dust(0xE4EEFF, 1.1f), getX() + Mth.nextDouble(random, -2.2, 2.2), cy + Mth.nextDouble(random, -1.6, 2.0),
                        getZ() + Mth.nextDouble(random, -2.2, 2.2), 0, -0.01, 0);
            }
            if (random.nextFloat() < 0.12f) {
                level().addParticle(ParticleTypes.DRIPPING_WATER, getX() + Mth.nextDouble(random, -1.2, 1.2), cy - 1.0,
                        getZ() + Mth.nextDouble(random, -1.2, 1.2), 0, 0, 0);
            }
        }
    }
}
