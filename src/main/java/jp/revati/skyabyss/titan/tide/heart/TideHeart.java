package jp.revati.skyabyss.titan.tide.heart;

import jp.revati.skyabyss.registry.MobSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.entity.GuardMob;
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
public class TideHeart extends GuardMob {
    public static final int PHASE_COUNT = 3;
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

    // 床を走る輪 (殻打ち・咆哮の押し返し)。跳べば当たらない
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

    // 予告の輪のあとに当たる一撃 (珊瑚の槍・真珠の雨)
    private record Strike(double x, double y, double z, int impactAt, boolean spear) {
    }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private Action last = Action.NONE;
    private int actionTicks;
    private int cooldown = 40;
    private float attackYaw;
    private boolean defeated;
    private final List<Ring> rings = new ArrayList<>();
    private final List<Strike> strikes = new ArrayList<>();
    // 泡の牢の線 (心臓の前から、溜めの最初の人の位置まで)
    @Nullable
    private Vec3 bubbleTo;
    // 大波の向き (水平の単位ベクトル。波はこの向きへ進む) と、当たった人
    private Vec3 waveDir = new Vec3(0, 0, 1);
    private final Set<UUID> waveHit = new HashSet<>();
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
        tickStrikes(server);

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
        return getY() + 2.0;
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
            sound(server, TideHeartSetup.ROAR.get(), 3f, 1f);
            server.sendParticles(ParticleTypes.SPLASH, getX(), coreY(), getZ(), 80, 1.6, 1.4, 1.6, 0.3);
        }
        if (actionTicks == 22) {
            sound(server, SoundEvents.GENERIC_SPLASH, 2f, 0.6f);
            rings.add(new Ring(floorY(), false));
        }
        if (actionTicks % 4 == 0 && actionTicks < 56) {
            warn(server, dust(getPhase() == 0 ? 0xF0E4EC : 0xC8DCFA, 1.3f), getX(), coreY(), getZ(), 6, 1.2, 1.2, 1.2, 0.05);
        }
        if (actionTicks >= ROAR_TICKS) {
            entityData.set(DATA_ROARING, false);
            entityData.set(DATA_PHASE, Math.min(PHASE_COUNT - 1, getPhase() + 1));
            sound(server, SoundEvents.BEACON_ACTIVATE, 2.5f, 1.4f);
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), coreY(), getZ(), 60, 1.8, 1.6, 1.8, 0.1);
            finish();
            cooldown = 40;
        }
    }

    // ---- 段階1 ----

    // 潮の吐息 (1.2 秒): 心臓から人へ伸びる水の線。幅 3・長さ 20 の帯を押し流す
    private void tickBreath(ServerLevel server) {
        Vec3 f = forward(attackYaw);
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.BREATH.get(), 2f, 1f);
        }
        if (actionTicks < 24 && actionTicks % 2 == 0) {
            Vec3 side = new Vec3(-f.z, 0, f.x);
            for (double d = 2; d <= 20; d += 1.0) {
                for (int s = -1; s <= 1; s += 2) {
                    warn(server, dust(0x5FD6E0, 0.9f), getX() + f.x * d + side.x * s * 1.5, fy + 0.15, getZ() + f.z * d + side.z * s * 1.5, 1, 0, 0, 0, 0);
                }
                if (((int) d) % 3 == actionTicks % 3) {
                    warn(server, dust(0xBFF6F4, 0.7f), getX() + f.x * d, fy + 0.15, getZ() + f.z * d, 1, 0, 0, 0, 0);
                }
            }
        }
        if (actionTicks >= 24 && actionTicks <= 34 && actionTicks % 2 == 0) {
            for (double d = 2; d <= 20; d += 1.5) {
                server.sendParticles(ParticleTypes.SPLASH, getX() + f.x * d, fy + 0.8, getZ() + f.z * d, 6, 0.8, 0.4, 0.8, 0.2);
                server.sendParticles(ParticleTypes.BUBBLE_POP, getX() + f.x * d, fy + 1.2, getZ() + f.z * d, 2, 0.6, 0.4, 0.6, 0.05);
            }
        }
        if (actionTicks == 24) {
            sound(server, SoundEvents.GENERIC_SPLASH, 2f, 0.7f);
            for (ServerPlayer player : reachable(server, 22)) {
                Vec3 v = player.position().subtract(position()).multiply(1, 0, 1);
                double along = v.dot(f);
                double across = v.subtract(f.scale(along)).length();
                if (along > 0.5 && along < 20 && across < 1.5) {
                    strike(player, BREATH_DAMAGE);
                    player.knockback(1.5, -f.x, -f.z);
                    player.hurtMarked = true;
                }
            }
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 殻打ち (1.0 秒): 殻が開きかけて震え、床を走る輪。跳べば当たらない。輪は 3 秒の鈍足 I
    private void tickShellStrike(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.SHELL.get(), 2f, 1f);
        }
        if (actionTicks < 20 && actionTicks % 3 == 0) {
            double fy = floorY();
            for (int a = 0; a < 360; a += 20) {
                double rad = Math.toRadians(a);
                warn(server, dust(0x9A3FD0, 1.5f), getX() + Math.cos(rad) * 3, fy + 0.2, getZ() + Math.sin(rad) * 3, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks == 20) {
            sound(server, SoundEvents.TURTLE_EGG_CRACK, 2f, 0.4f);
            double fy = floorY();
            server.sendParticles(ParticleTypes.SPLASH, getX(), fy + 0.4, getZ(), 50, 2.0, 0.3, 2.0, 0.2);
            rings.add(new Ring(fy, true));
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 渦潮 (1.5 秒の溜め): 足もとの水の渦。2 秒のあいだ中心へゆっくり引き (歩けば逆らえる)、終わりに半径 4 で弾ける
    private void tickWhirl(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.WHIRL.get(), 2.2f, 1f);
        }
        if (actionTicks < 70 && actionTicks % 2 == 0) {
            // 渦: 回りながら縮む粒の腕
            for (int arm = 0; arm < 4; arm++) {
                for (double r = 1.5; r <= 9; r += 1.5) {
                    double a = Math.toRadians(arm * 90 + actionTicks * 9 + r * 25);
                    warn(server, dust(0x2FA8B8, 1.0f), getX() + Math.cos(a) * r, fy + 0.15, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            // 弾ける範囲 (半径 4)
            if (actionTicks >= 40) {
                for (int a = 0; a < 360; a += 24) {
                    double rad = Math.toRadians(a);
                    warn(server, dust(0xFF7A7A, 1.1f), getX() + Math.cos(rad) * 4, fy + 0.2, getZ() + Math.sin(rad) * 4, 1, 0, 0, 0, 0);
                }
            }
        }
        if (actionTicks < 30 && actionTicks % 6 == 0) {
            for (ServerPlayer player : reachable(server, 20)) {
                warn(server, dust(0x2FA8B8, 0.9f), player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.6, 0.02, 0.6, 0);
            }
        }
        if (actionTicks >= 30 && actionTicks < 70 && actionTicks % 2 == 0) {
            for (ServerPlayer player : reachable(server, 20)) {
                pull(player, WHIRL_PULL);
            }
        }
        if (actionTicks == 70) {
            sound(server, SoundEvents.GENERIC_SPLASH, 2.5f, 0.6f);
            server.sendParticles(ParticleTypes.SPLASH, getX(), fy + 0.5, getZ(), 120, 3.0, 0.5, 3.0, 0.4);
            for (ServerPlayer player : reachable(server, 6)) {
                if (player.position().subtract(position()).multiply(1, 0, 1).length() < 4 && player.getY() < coreY() + 2) {
                    strike(player, WHIRL_DAMAGE);
                    Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                    player.knockback(0.8, -out.x, -out.z);
                    player.hurtMarked = true;
                }
            }
        }
        if (actionTicks >= 82) {
            finish();
        }
    }

    // ---- 段階2 ----

    // 満ち潮と珊瑚の槍 (1.5 秒): 床が 6 秒満ちる (島の上は濡れない)。満ちている間、水の上の人の足もとに輪が出て、槍が 3 回突き上がる
    private void tickFlood(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.FLOOD.get(), 2.5f, 1f);
        }
        if (actionTicks < 30 && actionTicks % 3 == 0) {
            // 溝から水が上がる予告 (外周の輪)
            for (int a = 0; a < 360; a += 10) {
                double rad = Math.toRadians(a + actionTicks * 2);
                double r = isBound() ? 13 : 9;
                warn(server, dust(0x4FC8E8, 1.1f), getX() + Math.cos(rad) * r, fy + 0.2 + actionTicks / 40.0, getZ() + Math.sin(rad) * r, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks == 30 && isBound()) {
            TideHeartArena.startFlood(server, 120);
        }
        // 槍は満ちてから (30 + 20 tick 後) 3 回、30 tick おき
        for (int k = 0; k < 3; k++) {
            if (actionTicks == 50 + k * 30) {
                List<ServerPlayer> players = reachable(server, 22);
                int n = 0;
                for (ServerPlayer p : players) {
                    if (onIsland(p) || n >= 4) {
                        continue;
                    }
                    strikes.add(new Strike(p.getX(), fy, p.getZ(), tickCount + 16, true));
                    n++;
                }
            }
        }
        if (actionTicks >= 130) {
            finish();
        }
    }

    // 泡の牢 (1.5 秒): 人へ伸びる泡の線。線の上にいた人を泡に閉じこめて浮かせる (最長 4 秒)。泡は叩けば割れる
    private void tickBubble(ServerLevel server) {
        LivingEntity target = getTarget();
        Vec3 from = new Vec3(getX(), coreY(), getZ());
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.BUBBLE.get(), 2f, 1f);
            bubbleTo = target != null ? target.position().add(0, 0.9, 0) : null;
        }
        // 0.5 秒前まで人を追い、そこで線が決まる
        if (target != null && actionTicks < 20) {
            bubbleTo = target.position().add(0, 0.9, 0);
        }
        if (bubbleTo != null && actionTicks < 30 && actionTicks % 2 == 0) {
            Vec3 d = bubbleTo.subtract(from);
            int steps = (int) Math.min(40, d.length() / 0.7);
            for (int i = 1; i <= steps; i++) {
                Vec3 p = from.add(d.scale((double) i / steps));
                warn(server, dust(actionTicks < 20 ? 0x2A90E0 : 0x103070, 1.0f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks == 30 && bubbleTo != null) {
            Vec3 d = bubbleTo.subtract(from);
            Vec3 dir = d.normalize();
            double len = d.length() + 2;
            server.playSound(null, bubbleTo.x, bubbleTo.y, bubbleTo.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 2f, 1f);
            int caught = 0;
            for (ServerPlayer player : reachable(server, 24)) {
                Vec3 v = player.position().add(0, 0.9, 0).subtract(from);
                double along = v.dot(dir);
                if (along < 0 || along > len || caught >= 2) {
                    continue;
                }
                if (v.subtract(dir.scale(along)).length() < 1.4 && !TideBubble.isTrapped(server, player)) {
                    TideBubble bubble = new TideBubble(server, player, this);
                    server.addFreshEntity(bubble);
                    caught++;
                }
            }
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 真珠の脈 (1.0 秒): 心臓が白く光り、追ってくる真珠を 3 つ放つ (遅い。叩けば消える)
    private void tickPearl(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.PEARL.get(), 2f, 1f);
        }
        if (actionTicks < 20 && actionTicks % 2 == 0) {
            warn(server, dust(0xFFFFFF, 1.5f), getX(), coreY(), getZ(), 8, 0.9, 0.9, 0.9, 0.02);
        }
        if (actionTicks == 20) {
            List<ServerPlayer> players = reachable(server, 26);
            if (!players.isEmpty()) {
                players.sort(Comparator.comparingDouble(p -> p.distanceToSqr(this)));
                for (int i = 0; i < 3; i++) {
                    ServerPlayer p = players.get(i % players.size());
                    double a = Math.toRadians(attackYaw + 90 + (i - 1) * 60);
                    TidePearl pearl = new TidePearl(server, this, p,
                            new Vec3(getX() - Math.sin(a) * 2.2, coreY() + (i - 1) * 0.6, getZ() + Math.cos(a) * 2.2));
                    server.addFreshEntity(pearl);
                }
            }
        }
        if (actionTicks >= 32) {
            finish();
        }
    }

    // ---- 段階3 ----

    // 引き潮 (2 秒): 床の水が中心へ流れはじめる。3 秒のあいだ中心へ引く (島の上では半分)。終わりに半径 5 で弾ける
    private void tickEbb(ServerLevel server) {
        double fy = floorY();
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.EBB.get(), 2.2f, 1f);
        }
        if (actionTicks < 100 && actionTicks % 2 == 0) {
            // 中心へ流れる水の筋 (外から内へ動く)
            for (int k = 0; k < 16; k++) {
                double a = Math.toRadians(k * 22.5 + 7);
                double r = 14 - ((actionTicks * 0.35 + k * 1.7) % 12);
                warn(server, dust(0x2A70D8, 1.3f), getX() + Math.cos(a) * r, fy + 0.15, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (actionTicks >= 70) {
                for (int a = 0; a < 360; a += 20) {
                    double rad = Math.toRadians(a);
                    warn(server, dust(0xFF7A7A, 1.2f), getX() + Math.cos(rad) * 5, fy + 0.2, getZ() + Math.sin(rad) * 5, 1, 0, 0, 0, 0);
                }
            }
        }
        if (actionTicks >= 40 && actionTicks < 100 && actionTicks % 2 == 0) {
            for (ServerPlayer player : reachable(server, 22)) {
                pull(player, onIsland(player) ? EBB_PULL * 0.5 : EBB_PULL);
            }
        }
        if (actionTicks == 100) {
            sound(server, SoundEvents.GENERIC_SPLASH, 2.5f, 0.5f);
            server.sendParticles(ParticleTypes.SPLASH, getX(), fy + 0.5, getZ(), 160, 4.0, 0.6, 4.0, 0.5);
            for (ServerPlayer player : reachable(server, 7)) {
                Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
                if (out.length() < 5 && player.getY() < coreY() + 2) {
                    strike(player, EBB_DAMAGE);
                    player.knockback(1.0, -out.x, -out.z);
                    player.hurtMarked = true;
                }
            }
        }
        if (actionTicks >= 112) {
            finish();
        }
    }

    // 月潮の大波 (2 秒): 片側の壁ぎわに白い波頭、床に向きの線。高さ 2 の波が部屋を横切る。岩の島の上なら越えられる
    private void tickMoonWave(ServerLevel server) {
        double fy = floorY();
        double half = isBound() ? TideHeartArena.RADIUS : 12;
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.WAVE.get(), 3f, 1f);
            // 大波の向きは四方から一つ (寄せる側の壁ぎわに白い波頭が出る)
            int quarter = random.nextInt(4);
            waveDir = switch (quarter) {
                case 0 -> new Vec3(1, 0, 0);
                case 1 -> new Vec3(-1, 0, 0);
                case 2 -> new Vec3(0, 0, 1);
                default -> new Vec3(0, 0, -1);
            };
            waveHit.clear();
        }
        Vec3 side = new Vec3(-waveDir.z, 0, waveDir.x);
        if (actionTicks < 40 && actionTicks % 2 == 0) {
            // 寄せる側の壁ぎわの白い波頭
            for (double s = -half; s <= half; s += 1.0) {
                double x = getX() - waveDir.x * (half - 0.5) + side.x * s;
                double z = getZ() - waveDir.z * (half - 0.5) + side.z * s;
                warn(server, dust(0xFFFFFF, 1.4f), x, fy + 0.5 + random.nextDouble() * 1.5, z, 1, 0, 0, 0, 0);
            }
            // 床の向きの線 (矢じり)
            if (actionTicks % 6 == 0) {
                for (double s = -half + 2; s <= half - 2; s += 4) {
                    for (double k = -half + 3; k <= half - 3; k += 4) {
                        for (double t = 0; t <= 1.2; t += 0.4) {
                            double x = getX() + waveDir.x * (k + t) + side.x * s;
                            double z = getZ() + waveDir.z * (k + t) + side.z * s;
                            warn(server, dust(0x2A50C8, 1.4f), x, fy + 0.1, z, 1, 0, 0, 0, 0);
                        }
                        for (int sg = -1; sg <= 1; sg += 2) {
                            double x = getX() + waveDir.x * (k + 0.8) + side.x * (s + sg * 0.5);
                            double z = getZ() + waveDir.z * (k + 0.8) + side.z * (s + sg * 0.5);
                            warn(server, dust(0x2A50C8, 1.4f), x, fy + 0.1, z, 1, 0, 0, 0, 0);
                        }
                    }
                }
            }
        }
        if (actionTicks >= 40 && actionTicks <= 40 + (int) (2 * half)) {
            double front = -half + (actionTicks - 40);
            if (actionTicks % 2 == 0) {
                for (double s = -half; s <= half; s += 1.0) {
                    double x = getX() + waveDir.x * front + side.x * s;
                    double z = getZ() + waveDir.z * front + side.z * s;
                    warn(server, dust(0xF4FAFF, 1.8f), x, fy + 1.9, z, 1, 0.1, 0.05, 0.1, 0);
                    warn(server, dust(0x4A80E0, 1.8f), x, fy + 1.3, z, 1, 0.1, 0.1, 0.1, 0);
                    warn(server, dust(0x2A58C0, 1.8f), x, fy + 0.7, z, 1, 0.1, 0.1, 0.1, 0);
                    warn(server, dust(0x1A3A90, 1.8f), x, fy + 0.2, z, 1, 0.1, 0.05, 0.1, 0);
                    if (((int) s) % 2 == 0) {
                        server.sendParticles(ParticleTypes.SPLASH, x, fy + 2.0, z, 6, 0.4, 0.1, 0.4, 0.3);
                    }
                }
            }
            for (ServerPlayer player : reachable(server, half + 4)) {
                Vec3 v = player.position().subtract(position()).multiply(1, 0, 1);
                double along = v.dot(waveDir);
                // 波の高さは 2。島の上 (床 + 2) は越える
                if (Math.abs(along - front) < 1.2 && player.getY() < fy + 1.95 && waveHit.add(player.getUUID())) {
                    strike(player, WAVE_DAMAGE);
                    player.knockback(1.6, -waveDir.x, -waveDir.z);
                    nudge(player, 0, 0.35, 0);
                    player.hurtMarked = true;
                }
            }
        }
        if (actionTicks >= 46 + (int) (2 * half)) {
            finish();
        }
    }

    // 真珠の雨 (1.5 秒): 人の足もとと周りに白い輪。島の上にも来る。当たると少し跳ね上がる
    private void tickPearlRain(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, TideHeartSetup.RAIN.get(), 2f, 1f);
            List<ServerPlayer> players = reachable(server, 24);
            int n = 0;
            for (ServerPlayer p : players) {
                if (n >= 8) {
                    break;
                }
                strikes.add(new Strike(p.getX(), standY(p.getX(), p.getZ()), p.getZ(), tickCount + 30, false));
                n++;
                for (int k = 0; k < 2 && n < 8; k++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double r = 2.5 + random.nextDouble() * 2.0;
                    double x = p.getX() + Math.cos(a) * r;
                    double z = p.getZ() + Math.sin(a) * r;
                    strikes.add(new Strike(x, standY(x, z), z, tickCount + 30, false));
                    n++;
                }
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

    // ---- 輪と一撃 (技の後も続く) ----

    private void tickRings(ServerLevel server) {
        for (Ring ring : rings) {
            ring.radius += 0.8;
            int points = Math.max(8, (int) (2 * Math.PI * ring.radius / 1.4));
            for (int i = 0; i < points; i++) {
                double a = i * 2 * Math.PI / points;
                warn(server, dust(ring.harmful ? 0x9A3FD0 : 0x2A90E0, 1.5f),
                        getX() + Math.cos(a) * ring.radius, ring.floorY + 0.5, getZ() + Math.sin(a) * ring.radius, 1, 0, 0, 0, 0);
            }
            double r = ring.radius + 2;
            AABB area = new AABB(getX() - r, ring.floorY - 1, getZ() - r, getX() + r, ring.floorY + 3, getZ() + r);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach)) {
                Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                double d = to.length();
                if (Math.abs(d - ring.radius) < 1.0 && player.getY() < ring.floorY + 0.9 && ring.hit.add(player.getUUID())) {
                    if (ring.harmful) {
                        strike(player, SHELL_RING_DAMAGE);
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                    }
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
        for (Strike s : strikes) {
            if (tickCount >= s.impactAt()) {
                impact(server, s);
                continue;
            }
            if (tickCount % 3 == 0) {
                double r = s.spear() ? 1.3 : 2.0;
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    warn(server, dust(s.spear() ? 0xE03050 : 0x3A5FE0, 1.3f),
                            s.x() + Math.cos(rad) * r, s.y() + 0.15, s.z() + Math.sin(rad) * r, 1, 0, 0, 0, 0);
                }
            }
            if (!s.spear() && tickCount >= s.impactAt() - 10) {
                double drop = (s.impactAt() - tickCount) / 10.0;
                warn(server, ParticleTypes.END_ROD, s.x(), s.y() + 1 + drop * 8, s.z(), 2, 0.1, 0.2, 0.1, 0);
            }
            remaining.add(s);
        }
        strikes.clear();
        strikes.addAll(remaining);
    }

    private void impact(ServerLevel server, Strike s) {
        double radius = s.spear() ? 1.3 : 2.0;
        server.playSound(null, s.x(), s.y(), s.z(), s.spear() ? TideHeartSetup.SPEAR.get() : SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.HOSTILE, 1.6f, s.spear() ? 1f : 1.4f);
        if (s.spear()) {
            server.sendParticles(dust(0xFF7A80, 1.4f), s.x(), s.y() + 1.0, s.z(), 30, 0.25, 1.0, 0.25, 0.05);
            server.sendParticles(ParticleTypes.SPLASH, s.x(), s.y() + 0.6, s.z(), 20, 0.5, 0.3, 0.5, 0.2);
        } else {
            server.sendParticles(ParticleTypes.END_ROD, s.x(), s.y() + 0.3, s.z(), 16, 0.8, 0.2, 0.8, 0.05);
        }
        AABB area = new AABB(s.x() - radius, s.y() - 1, s.z() - radius, s.x() + radius, s.y() + 2.5, s.z() + radius);
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach)) {
            if (Math.hypot(player.getX() - s.x(), player.getZ() - s.z()) > radius + 0.3) {
                continue;
            }
            strike(player, s.spear() ? SPEAR_DAMAGE : RAIN_DAMAGE);
            nudge(player, 0, s.spear() ? 0.6 : 0.5, 0);
        }
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
        super.remove(reason);
    }

    // 倒れたとき: 試練で湧いた心臓だけが、殻の底の攻略を知らせる (報酬・進み・記録は TideHeartBattle)
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
            if (server.dimension() == TideTravel.TIDE_BACK && getTags().contains(TitanBackTrials.HEART_TAG)) {
                TideHeartBattle.onDefeated(server, this);
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

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_SPLASH;
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
        double cy = getY() + 2.0;
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
