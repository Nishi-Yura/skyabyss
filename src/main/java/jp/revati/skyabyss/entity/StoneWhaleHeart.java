package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.registry.MobSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;


// 岩鯨の心臓: 試練の最後に待つ最終ボス。石の殻に包まれ、空中にとどまって動かない。
// 殻の枚数で三段階に分かれる (体力 66% 未満で段階2、33% 未満で段階3)。
//  段階1 (八枚の殻): 殻の突き / 石棘の掃射 / 跳躍の叩きつけ
//  段階2 (割れた殻): 亀裂の爆発 / 殻の再閉 / 骨の棘の投射
//  段階3 (吊られた殻): 赤熱の光線 / 鎖の振り回し / 殻の落下
// 段階が変わるときは 3 秒の咆哮を挟む。その間は傷を受けず、輪で人を押し返す。
// 段階は同期データで全員の画面に届き、モデルとボスバーの見た目を決める。
public class StoneWhaleHeart extends HeartBoss {
    // 段階の数 (1 から 3 までの模型がある)
    public static final int PHASE_COUNT = 3;
    // 見た目と判定箱の大きさ (前の 1.5 倍。ModEntities の sized と StoneWhaleHeartRenderer の SCALE を合わせる)
    public static final double BODY_SCALE = 1.5;
    // 段階が変わる体力の割合 (段階2 へ 66%、段階3 へ 33%)
    private static final float PHASE_TWO_AT = 0.66f;
    private static final float PHASE_THREE_AT = 0.33f;
    private static final int ROAR_TICKS = 60;

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(StoneWhaleHeart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ROARING = SynchedEntityData.defineId(StoneWhaleHeart.class, EntityDataSerializers.BOOLEAN);

    private enum Action {
        NONE(null), ROAR(null), THRUST("thrust"), SWEEP("sweep"), LEAP("leap"), CRACK("crack"), RECLOSE("reclose"),
        VOLLEY("volley"), BEAM("beam"), CHAIN("chain"), SHELL_DROP("shell_drop");

        final String anim;

        Action(String anim) {
            this.anim = anim;
        }
    }

    private static final Action[] PHASE_ONE = {Action.THRUST, Action.SWEEP, Action.LEAP};
    private static final Action[] PHASE_TWO = {Action.CRACK, Action.RECLOSE, Action.VOLLEY};
    private static final Action[] PHASE_THREE = {Action.BEAM, Action.CHAIN, Action.SHELL_DROP};

    private static final double BEAM_LENGTH = 24;
    private static final double CHAIN_LENGTH = 14;

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private Action last = Action.NONE;
    private int actionTicks;
    private int cooldown = 40;
    private int weakTicks;
    private float attackYaw;
    private boolean defeated;
    private double chainY;
    // 技の始めに決めた本数 (人数による。掃射の腕・光線)
    private int lockedCount = 1;
    private boolean holdAttacks;
    private int partyOverride;
    // 試練で湧いたときの心臓の間の範囲。技と狙いは、この中にいる人だけに向く (前室や回廊には届かない)
    @Nullable
    private AABB arena;
    // 挑む人数による強さ。受ける傷をこの値で割る (1 人で 1.0、1 人増えるごとに +0.6)。
    // 体力の上限 (1024) を超えないよう、体力そのものではなく受ける傷で強くする (実質の体力は 450 × この値)
    private float toughness = 1f;

    public StoneWhaleHeart(EntityType<? extends Monster> type, Level level) {
        super(type, level, "stone_whale_heart", "roar_1", "roar_2", "thrust", "sweep", "leap", "crack", "reclose", "volley", "beam", "chain", "shell_drop");
        this.xpReward = 200;
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 450)
                .add(Attributes.ATTACK_DAMAGE, 12)
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

    // 段階 (0 から 2)。モデルとボスバーの見た目は、この値で決まる
    public int getPhase() {
        return entityData.get(DATA_PHASE);
    }

    // 咆哮の最中か (無敵の三秒)
    public boolean isRoaring() {
        return entityData.get(DATA_ROARING);
    }

    private void setPhase(int phase) {
        entityData.set(DATA_PHASE, phase);
    }

    private void setRoaring(boolean roaring) {
        entityData.set(DATA_ROARING, roaring);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24f));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, this::canReach));
    }

    // 試練の仕組みが、心臓の間の範囲と挑む人数を伝える
    public void bindArena(AABB arena, int challengers) {
        this.arena = arena;
        this.toughness = scaleFor(challengers);
    }

    // 人数による強さの倍率 (心臓と番兵長で共通): 1 + 0.6 × (人数 − 1)
    public static float scaleFor(int challengers) {
        return 1f + 0.6f * (Math.max(1, challengers) - 1);
    }

    public float toughness() {
        return toughness;
    }

    // 技と狙いの対象になれるか。心臓の間に縛られているときは、その内側にいる人だけ
    private boolean canReach(@Nullable LivingEntity entity) {
        return entity instanceof Player p && p.isAlive() && !p.isSpectator() && (arena == null || arena.contains(p.position()))
                && jp.revati.skyabyss.titan.group.GroupOwned.mayTarget(this, p);
    }

    // 体力の割合から、いまあるべき段階
    private int phaseForHealth() {
        float frac = getHealth() / getMaxHealth();
        if (frac < PHASE_THREE_AT) {
            return 2;
        }
        if (frac < PHASE_TWO_AT) {
            return 1;
        }
        return 0;
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
        if (weakTicks > 0) {
            weakTicks--;
        }

        if (action == Action.NONE) {
            if (getPhase() < phaseForHealth()) {
                begin(Action.ROAR, "roar_" + (getPhase() + 1));
                return;
            }
            LivingEntity target = getTarget();
            if (target != null && !canReach(target)) {
                // 部屋の外へ出た人は狙わない
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
            case THRUST -> tickThrust(server);
            case SWEEP -> tickSweep(server);
            case LEAP -> tickLeap(server);
            case CRACK -> tickCrack(server);
            case RECLOSE -> tickReclose(server);
            case VOLLEY -> tickVolley(server);
            case BEAM -> tickBeam(server);
            case CHAIN -> tickChain(server);
            case SHELL_DROP -> tickShellDrop(server);
            default -> {
            }
        }
    }

    // 段階ごとの技から、直前と同じでないものを選ぶ
    private Action pickAttack() {
        Action[] bag = switch (getPhase()) {
            case 0 -> PHASE_ONE;
            case 1 -> PHASE_TWO;
            default -> PHASE_THREE;
        };
        Action pick;
        do {
            pick = bag[random.nextInt(bag.length)];
        } while (pick == last && bag.length > 1);
        return pick;
    }

    private void begin(Action next, String animation) {
        action = next;
        last = next;
        actionTicks = 0;
        attackYaw = getYRot();
        playAction(animation != null ? animation : next.anim);
    }

    private void finish() {
        action = Action.NONE;
        actionTicks = 0;
        // 次の技までの間。段階が進むほど短い
        int base = switch (getPhase()) {
            case 0 -> 50;
            case 1 -> 40;
            default -> 30;
        };
        cooldown = base + random.nextInt(15);
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        setYRot((float) (Mth.atan2(-dx, dz) * 180 / Math.PI));
    }

    // 色つきの粒 (0xRRGGBB と大きさ)
    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), scale);
    }

    // 向きから水平の前方ベクトル (モデルの正面は -Z = 向きの前)
    private static Vec3 forward(float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    private void sound(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        server.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    // 人へ傷を与える。殻が閉じた直後 (弱まっている間) は、この心臓の攻撃は半分になる
    private void strike(ServerPlayer player, float amount) {
        player.hurt(attackSource(), weakTicks > 0 ? amount * 0.5f : amount);
    }

    private double floorY() {
        return floorAt(Mth.floor(getX()), Mth.floor(getZ()));
    }

    // 心臓の間は鯨の体内にあり、真上に天井と背の地表がある。高さの地図では天井の上を返すので、
    // 心臓の高さから真下へたどって最初の床を探す
    private int floorAt(int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Mth.floor(getY()), z);
        for (int i = 0; i < 24; i++) {
            pos.move(0, -1, 0);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return pos.getY() + 1;
            }
        }
        return Mth.floor(getY()) - 4;
    }

    private List<ServerPlayer> nearbyPlayers(ServerLevel server, double range) {
        return server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range, 12, range), this::canReach);
    }

    // ---- 人数に応じた数 (人数 = 心臓の間にいる人。設計書 02 の 5.3)。一人でも避けられ、人数が増えるほど派手になる ----

    public static int thrustLines(int n) {
        return Mth.clamp(n, 1, 3);
    }

    public static int sweepArms(int n) {
        return n >= 3 ? 2 : 1;
    }

    public static int leapRings(int n) {
        return n >= 3 ? 2 : 1;
    }

    public static int crackLines(int n) {
        return Mth.clamp(n, 1, 5);
    }

    public static int volleyLines(int n) {
        return Mth.clamp(n, 1, 4);
    }

    public static int beamCount(int n) {
        return n <= 2 ? 1 : n <= 4 ? 2 : 3;
    }

    public static int chainCount(int n) {
        return n <= 2 ? 1 : n <= 4 ? 2 : 3;
    }

    // 殻の落下: 人の足元に一つずつ (四人まで)、三人からは足元以外にも二つ
    public static int dropTargets(int n) {
        return Mth.clamp(n, 1, 4);
    }

    public static int dropExtras(int n) {
        return n >= 3 ? 2 : 0;
    }

    // 技の数値 (印と当たりは同じ形。tick は 20 で 1 秒)
    public static final int THRUST_WARN = 24;
    public static final double THRUST_LENGTH = 9;
    public static final double THRUST_HALF_WIDTH = 2.0;
    public static final float THRUST_DAMAGE = 11f;
    public static final int SPIKE_WARN = 22;
    public static final double SPIKE_RADIUS = 1.4;
    public static final float SPIKE_DAMAGE = 9f;
    public static final int LEAP_WARN = 24;
    public static final double LEAP_CORE = 4.5;
    public static final float LEAP_CORE_DAMAGE = 10f;
    public static final double LEAP_WAVE_REACH = 16;
    public static final float LEAP_WAVE_DAMAGE = 8f;
    public static final int CRACK_WARN = 30;
    public static final double CRACK_HALF_WIDTH = 1.2;
    public static final float CRACK_DAMAGE = 8f;
    public static final int VOLLEY_WARN = 30;
    public static final double VOLLEY_HALF_WIDTH = 1.0;
    public static final float VOLLEY_DAMAGE = 7f;
    public static final int BEAM_WARN = 40;
    public static final int BEAM_ACTIVE = 24;
    public static final float BEAM_DAMAGE = 10f;
    public static final int CHAIN_WARN = 24;
    public static final int CHAIN_ACTIVE = 36;
    public static final float CHAIN_DAMAGE = 10f;
    public static final int DROP_WARN = 30;
    public static final double DROP_RADIUS = 2.5;
    public static final float DROP_DAMAGE = 12f;
    public static final int ROAR_PUSH_WARN = 22;
    public static final double ROAR_PUSH_RADIUS = 8;

    // 試験・コマンド用: 技を一つ出す (段階に関係なく)。咆哮は段階が進む
    public void forceAttack(String name) {
        begin(Action.valueOf(name.toUpperCase()), null);
    }

    // 試験用: 次の技を始めない (forceAttack で出した技だけを見たいとき)
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
        return Math.max(partyOverride, nearbyPlayers(server, 40).size());
    }

    // 狙う地点 (人の足元) を、重ならないように最大 max か所 (いま狙っている人を先頭に、残りは入れ替える)。
    // 開発用の人数の上書きで人が足りないぶんは、心臓の周りのどこかの点で補う
    private List<Vec3> pickSpots(ServerLevel server, int max) {
        List<ServerPlayer> all = new ArrayList<>(nearbyPlayers(server, 40));
        java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
        LivingEntity main = getTarget();
        if (main instanceof ServerPlayer mp && all.remove(mp)) {
            all.add(0, mp);
        }
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : all) {
            spots.add(p.position());
        }
        while (spots.size() < Math.min(max, partyOverride)) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 5 + random.nextDouble() * 7;
            spots.add(new Vec3(getX() + Math.cos(a) * r, getY(), getZ() + Math.sin(a) * r));
        }
        return spots.size() > max ? new ArrayList<>(spots.subList(0, max)) : spots;
    }

    // 予告の組み立て (形・長さ・色。持ち主は心臓、当たるのは心臓の間の人)
    // style: D = 傷が出る (橙から赤)、S = 安全な筋 (見た目だけ)、H = 押すだけ・見た目だけ (灰色)
    private static final int D = 0;
    private static final int S = 1;
    private static final int H = 2;

    private Telegraph.Builder tg(ServerLevel server, TelegraphShape shape, int warn, int style) {
        Telegraph.Builder b = Telegraph.in(server, shape).floor(floorY()).warn(warn).owner(this).victims(this::canReach).reach(3.5);
        return switch (style) {
            case S -> b.safe();
            case H -> b.harmless();
            default -> b.danger();
        };
    }

    // 向き angle の先にいる人 (狙われた本人の画面に、帯の根もとの輪と矢印を出すため)。いなければ null
    @org.jetbrains.annotations.Nullable
    private java.util.UUID aimedAt(ServerLevel server, double angle) {
        ServerPlayer best = null;
        double bestDiff = 0.15;
        for (ServerPlayer p : nearbyPlayers(server, 40)) {
            double diff = Math.abs(Math.IEEEremainder(angleTo(p.position()) - angle, Math.PI * 2));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = p;
            }
        }
        return best == null ? null : best.getUUID();
    }

    private double angleTo(Vec3 spot) {
        return Telegraph.angleOf(spot.x - getX(), spot.z - getZ());
    }

    // ---- 咆哮 (段階の切り替え) ----

    // 三秒。最初に咆哮し、輪が広がって人を押し返す (傷はない)。押し返される範囲は、始めの 1.1 秒だけ床に描く。
    // 終わると段階が一つ進む (下の二枚の殻が落ちるのはモデル側)
    private void tickRoar(ServerLevel server) {
        if (actionTicks == 1) {
            setRoaring(true);
            sound(server, MobSounds.STONE_HEART_ROAR.get(), 3f, 1.0f);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                    getX(), coreHeight(), getZ(), 60, 1.5, 1.2, 1.5, 0.2);
            tg(server, TelegraphShape.circle(getX(), getZ(), ROAR_PUSH_RADIUS), ROAR_PUSH_WARN, H)
                    .onImpact(s -> sound(s, MobSounds.STONE_HEART_SHOCKWAVE.get(), 2.5f, 1.0f))
                    .onHit((s, player) -> {
                        Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                        player.knockback(1.0, -to.x, -to.z);
                    })
                    .start();
            // 広がる輪 (見た目だけ)
            tg(server, TelegraphShape.wave(getX(), getZ(), 1.5, 20, 0.9, new double[0], 0), ROAR_PUSH_WARN, H).active(30).start();
        }
        if (actionTicks % 4 == 0 && actionTicks < 56) {
            warn(server, dust(0x9CFBFF, 1.2f), getX(), coreHeight(), getZ(), 6, 1.0, 1.0, 1.0, 0.05);
        }
        if (actionTicks >= ROAR_TICKS) {
            setRoaring(false);
            setPhase(getPhase() + 1);
            sound(server, MobSounds.STONE_HEART_PHASE.get(), 3f, 1.0f);
            finish();
            cooldown = 40;
        }
    }

    // ---- 段階1の技 ----

    // 殻の突き: 手前の殻がふくらみ、前へ突き出す (1.2 秒)。印の帯の中の人に当たる。人数が増えると、別の人へ向けて最大三本
    private void tickThrust(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, MobSounds.STONE_HEART_GRIND.get(), 2f, 1.0f);
            List<Vec3> targets = pickSpots(server, thrustLines(present(server)));
            List<Double> angles = new ArrayList<>();
            for (Vec3 p : targets) {
                angles.add(angleTo(p));
            }
            if (angles.isEmpty()) {
                Vec3 f = forward(getYRot());
                angles.add(Telegraph.angleOf(f));
            }
            boolean first = true;
            for (double angle : angles) {
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                boolean sounds = first;
                first = false;
                tg(server, TelegraphShape.line(getX() + dx * 0.5, getZ() + dz * 0.5, angle, THRUST_LENGTH, THRUST_HALF_WIDTH), THRUST_WARN, D).target(aimedAt(server, angle))
                        .onImpact(s -> {
                            if (sounds) {
                                sound(s, MobSounds.STONE_HEART_SLAM.get(), 2.5f, 1.0f);
                            }
                            s.sendParticles(ParticleTypes.CRIT, getX() + dx * 4, getY() + 1.5, getZ() + dz * 4, 24, 1.2, 0.8, 1.2, 0.3);
                        })
                        .onHit((s, player) -> {
                            strike(player, THRUST_DAMAGE);
                            player.knockback(1.2, -dx, -dz);
                        })
                        .start();
            }
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 石棘の掃射: 殻の縁が鳴り、弧を描く腕の先から石の棘が地を割って出る。出る前に半径 1.4 の円が 1.1 秒見える。
    // 腕が通る扇は最初の 1 秒で床に描く。三人からは腕が二本 (反対向き)。棘の間と、円の外が安全
    private void tickSweep(ServerLevel server) {
        if (actionTicks == 1) {
            lockedCount = sweepArms(present(server));
        }
        int arms = lockedCount;
        if (actionTicks == 1) {
            sound(server, MobSounds.STONE_HEART_SPIKE_CHARGE.get(), 2f, 1.0f);
            for (int i = 0; i < arms; i++) {
                double mid = Telegraph.angleOf(forward(attackYaw)) + Math.PI * i;
                // 腕の通る扇 (見た目だけ。当たるのは棘の円)
                tg(server, TelegraphShape.cone(getX(), getZ(), mid, 13, Math.toRadians(62)), 20, D).start();
            }
        }
        if (actionTicks >= 20 && actionTicks <= 68 && actionTicks % 3 == 0) {
            double t = (actionTicks - 20) / 48.0;
            double[] radii = arms == 1 ? new double[] {5, 8.5, 12} : new double[] {6.5, 10.5};
            for (int i = 0; i < arms; i++) {
                double angle = Telegraph.angleOf(forward((float) (attackYaw + (-60 + 120 * t)))) + Math.PI * i;
                for (double d : radii) {
                    double x = getX() + Math.cos(angle) * d;
                    double z = getZ() + Math.sin(angle) * d;
                    stoneSpike(server, x, floorAt(Mth.floor(x), Mth.floor(z)), z);
                }
            }
        }
        if (actionTicks >= 80) {
            finish();
        }
    }

    // 石棘 1 本: 半径 1.4 の円の印 (1.1 秒)。当たると少し跳ね上がる
    private void stoneSpike(ServerLevel server, double x, double y, double z) {
        Telegraph.in(server, TelegraphShape.circle(x, z, SPIKE_RADIUS)).floor(y).warn(SPIKE_WARN).danger().owner(this).victims(this::canReach)
                .onImpact(s -> {
                    s.playSound(null, x, y, z, MobSounds.STONE_HEART_SPIKE_BURST.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
                    s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), x, y + 0.5, z, 20, 0.5, 0.5, 0.5, 0.2);
                })
                .onHit((s, player) -> {
                    strike(player, SPIKE_DAMAGE);
                    player.setDeltaMovement(player.getDeltaMovement().add(0, 0.4, 0));
                    player.hurtMarked = true;
                })
                .start();
    }

    // 跳躍の叩きつけ: 心臓が沈み (1.2 秒、鼓動が鳴る)、足元の円 (半径 3.5) に叩きつけ、そこから輪が広がる。
    // 円の中は傷。輪は跳べば当たらない。輪には安全な筋 (緑の扇) が二本あり、そこは何もない。三人からは輪が 0.7 秒の間をあけて二重
    private void tickLeap(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, MobSounds.STONE_HEART_PULSE.get(), 3f, 1.0f);
            double gapBase = random.nextDouble() * Math.PI * 2;
            double[] gaps = {gapBase, gapBase + Math.PI};
            double gapHalf = Math.toRadians(22);
            tg(server, TelegraphShape.circle(getX(), getZ(), LEAP_CORE), LEAP_WARN, D)
                    .onImpact(s -> {
                        sound(s, MobSounds.STONE_HEART_SHOCKWAVE.get(), 2.5f, 0.85f);
                        s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                                getX(), floorY() + 0.5, getZ(), 50, 2.0, 0.4, 2.0, 0.2);
                    })
                    .onHit((s, player) -> strike(player, LEAP_CORE_DAMAGE))
                    .start();
            for (double gap : gaps) {
                // 安全な筋 (見た目だけ)
                tg(server, TelegraphShape.cone(getX(), getZ(), gap, 14, gapHalf), LEAP_WARN, S).start();
            }
            int rings = leapRings(present(server));
            for (int i = 0; i < rings; i++) {
                int active = (int) Math.ceil((LEAP_WAVE_REACH - LEAP_CORE) / 0.55);
                tg(server, TelegraphShape.wave(getX(), getZ(), LEAP_CORE, LEAP_WAVE_REACH, 0.9, gaps, gapHalf), LEAP_WARN + i * 14, D)
                        .active(active).jumpClear(0.9)
                        .onHit((s, player) -> {
                            strike(player, LEAP_WAVE_DAMAGE);
                            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                            Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                            player.knockback(0.9, -to.x, -to.z);
                        })
                        .start();
            }
        }
        if (actionTicks >= 44) {
            finish();
        }
    }

    // ---- 段階2の技 ----

    // 亀裂の爆発: 人へ向かうひびが光る (1.5 秒)。そのあと、ひびの帯の中に炎が上がる。人数が増えると最大五本 (別の人へ)
    private void tickCrack(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, MobSounds.STONE_HEART_FLAME_CHARGE.get(), 2.5f, 1.0f);
            List<Vec3> targets = pickSpots(server, crackLines(present(server)));
            boolean first = true;
            for (Vec3 p : targets) {
                double angle = angleTo(p);
                double dist = Math.hypot(p.x - getX(), p.z - getZ());
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                double length = Math.max(4, Math.min(26, dist + 4) - 2);
                boolean sounds = first;
                first = false;
                tg(server, TelegraphShape.line(getX() + dx * 2, getZ() + dz * 2, angle, length, CRACK_HALF_WIDTH), CRACK_WARN, D).target(aimedAt(server, angle))
                        .onImpact(s -> {
                            if (sounds) {
                                sound(s, MobSounds.STONE_HEART_FLAME_BURST.get(), 2.5f, 1.0f);
                            }
                            for (double d = 0; d <= length; d += 1.8) {
                                double x = getX() + dx * (2 + d);
                                double z = getZ() + dz * (2 + d);
                                double y = floorAt(Mth.floor(x), Mth.floor(z));
                                s.sendParticles(ParticleTypes.FLAME, x, y + 0.3, z, 6, 0.4, 0.5, 0.4, 0.05);
                                s.sendParticles(ParticleTypes.LAVA, x, y + 0.3, z, 1, 0.3, 0.2, 0.3, 0);
                            }
                        })
                        .onHit((s, player) -> {
                            strike(player, CRACK_DAMAGE);
                            player.setRemainingFireTicks(60);
                        })
                        .start();
            }
        }
        if (actionTicks >= 60) {
            finish();
        }
    }

    // 殻の再閉: 殻が震え (1.5秒)、閉じる。閉じたあと一秒は、この心臓の攻撃が弱まり、傷は一・五倍に効く
    private void tickReclose(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, MobSounds.STONE_HEART_CHAIN.get(), 2f, 0.8f);
        }
        if (actionTicks < 30 && actionTicks % 3 == 0) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                    getX() + random.nextGaussian() * 1.5, getY() + 1.5 + random.nextGaussian(), getZ() + random.nextGaussian() * 1.5,
                    2, 0.1, 0.1, 0.1, 0.05);
        }
        if (actionTicks == 30) {
            sound(server, MobSounds.STONE_HEART_PLATE_LOCK.get(), 2.5f, 1.0f);
            warn(server, dust(0xFFB347, 1.4f), getX(), coreHeight(), getZ(), 60, 1.8, 1.8, 1.8, 0.1);
            weakTicks = 20;
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 骨の棘の投射: 体の棘が人へ向く (1.5 秒)。印の帯 (幅 2、長さ 30) を骨の棘が貫く。人数が増えると最大四本 (別の人へ)。
    // 向きは始めに決まる (人が動けば帯から出られる)
    private void tickVolley(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, MobSounds.STONE_HEART_SPIKE_CHARGE.get(), 1.8f, 1.25f);
            List<Vec3> targets = pickSpots(server, volleyLines(present(server)));
            boolean first = true;
            for (Vec3 p : targets) {
                double angle = angleTo(p);
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                double y0 = getY() + 3.6;
                boolean sounds = first;
                first = false;
                tg(server, TelegraphShape.line(getX() + dx * 1.2, getZ() + dz * 1.2, angle, 30, VOLLEY_HALF_WIDTH), VOLLEY_WARN, D).target(aimedAt(server, angle))
                        .onImpact(s -> {
                            if (sounds) {
                                sound(s, MobSounds.STONE_HEART_VOLLEY_SHOT.get(), 2.5f, 1.0f);
                            }
                            for (double d = 1.5; d <= 30; d += 1.3) {
                                s.sendParticles(ParticleTypes.END_ROD, getX() + dx * d, floorY() + 1.0 + (y0 - floorY() - 1.0) * (1 - d / 30), getZ() + dz * d, 1, 0.05, 0.05, 0.05, 0.01);
                                if (((int) (d / 1.3)) % 3 == 0) {
                                    s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                                            getX() + dx * d, floorY() + 1.0, getZ() + dz * d, 4, 0.2, 0.2, 0.2, 0.1);
                                }
                            }
                        })
                        .onHit((s, player) -> {
                            strike(player, VOLLEY_DAMAGE);
                            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                        })
                        .start();
            }
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // ---- 段階3の技 ----

    // 赤熱の光線: 核が白く光り (2秒)、向きは溜めの最初に決まる。光線が薙ぐ扇 (角度 50°、長さ 24) を床に描く。
    // 扇の外が安全 (人数が増えると光線は最大三本で、扇のあいだに安全な楔が残る)。光線は 1.2 秒でその扇を一度だけ薙ぐ
    private void tickBeam(ServerLevel server) {
        if (actionTicks == 1) {
            lockedCount = beamCount(present(server));
        }
        int count = lockedCount;
        double span = Math.toRadians(count == 3 ? 40 : 50);
        double base = Telegraph.angleOf(forward(attackYaw));
        Vec3 f = forward(attackYaw);
        Vec3 origin = position().add(0, 3.9, 0).add(f.scale(1.0));
        if (actionTicks == 1) {
            sound(server, MobSounds.STONE_HEART_BEAM_CHARGE.get(), 2.5f, 1.0f);
            for (int i = 0; i < count; i++) {
                double mid = base + Math.PI * 2 * i / count;
                boolean sounds = i == 0;
                tg(server, TelegraphShape.sweep(getX(), getZ(), mid - span / 2, span, BEAM_LENGTH, 1.1), BEAM_WARN, D)
                        .active(BEAM_ACTIVE)
                        .onImpact(s -> {
                            if (sounds) {
                                sound(s, MobSounds.STONE_HEART_BEAM_FIRE.get(), 3f, 1.0f);
                            }
                        })
                        .onHit((s, player) -> {
                            strike(player, BEAM_DAMAGE);
                            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                        })
                        .start();
            }
        }
        if (actionTicks < BEAM_WARN) {
            if (actionTicks % 2 == 0) {
                double r = 2.6 * (1 - actionTicks / (double) BEAM_WARN) + 0.3;
                for (int a = 0; a < 360; a += 45) {
                    double rad = Math.toRadians(a + actionTicks * 6);
                    warn(server, dust(0xFF3A2A, 1.0f),
                            origin.x + Math.cos(rad) * r, origin.y + Math.sin(rad) * r, origin.z, 1, 0, 0, 0, 0);
                }
            }
        } else if (actionTicks <= BEAM_WARN + BEAM_ACTIVE) {
            // 光線の芯 (いまの向き)
            double p = (actionTicks - BEAM_WARN) / (double) BEAM_ACTIVE;
            for (int i = 0; i < count; i++) {
                double angle = base + Math.PI * 2 * i / count - span / 2 + span * p;
                for (double d = 1; d <= BEAM_LENGTH; d += 1.6) {
                    warn(server, dust(0xFF3A2A, 1.4f), origin.x + Math.cos(angle) * d, origin.y, origin.z + Math.sin(angle) * d, 1, 0.05, 0.05, 0.05, 0);
                }
            }
        }
        if (actionTicks >= BEAM_WARN + BEAM_ACTIVE + 2) {
            finish();
        }
    }

    // 鎖の振り回し: 鎖が床に線を引き (1.2 秒)、その線が扇を振られる (1.8 秒)。振られる扇を床に描く。
    // 腰の高さを通るので、跳べば越えられる。人数が増えると鎖は最大三本 (一本あたりの扇は 180°÷本数)。扇の外が安全
    private void tickChain(ServerLevel server) {
        if (actionTicks == 1) {
            chainY = floorY();
            sound(server, MobSounds.STONE_HEART_CHAIN.get(), 2.5f, 1.0f);
            int count = chainCount(present(server));
            double span = Math.PI / count;
            double base = Telegraph.angleOf(forward(attackYaw));
            for (int i = 0; i < count; i++) {
                double mid = base + Math.PI * 2 * i / count;
                tg(server, TelegraphShape.sweep(getX(), getZ(), mid - span / 2, span, CHAIN_LENGTH, 1.2), CHAIN_WARN, D)
                        .active(CHAIN_ACTIVE).jumpClear(0.7)
                        .onHit((s, player) -> {
                            strike(player, CHAIN_DAMAGE);
                            Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                            player.knockback(0.9, -to.x, -to.z);
                        })
                        .start();
            }
        }
        if (actionTicks >= 62) {
            finish();
        }
    }

    // 殻の落下: 人の足元に円 (半径 2.5) が広がり (1.5 秒)、そこへ殻の塊が落ちる。四人までは一人に一つ、三人からは足元以外にも二つ
    private void tickShellDrop(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, MobSounds.STONE_HEART_GRIND.get(), 2f, 0.8f);
            int n = present(server);
            for (Vec3 p : pickSpots(server, dropTargets(n))) {
                shellDrop(server, p.x, floorAt(Mth.floor(p.x), Mth.floor(p.z)), p.z);
            }
            for (int i = 0; i < dropExtras(n); i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 4 + random.nextDouble() * 8;
                double x = getX() + Math.cos(a) * r;
                double z = getZ() + Math.sin(a) * r;
                shellDrop(server, x, floorAt(Mth.floor(x), Mth.floor(z)), z);
            }
        }
        if (actionTicks >= 50) {
            finish();
        }
    }

    private void shellDrop(ServerLevel server, double x, double y, double z) {
        Telegraph.in(server, TelegraphShape.circle(x, z, DROP_RADIUS)).floor(y).warn(DROP_WARN).danger().owner(this).victims(this::canReach)
                .onImpact(s -> {
                    s.playSound(null, x, y, z, MobSounds.STONE_HEART_SLAM.get(), SoundSource.HOSTILE, 2.5f, 0.9f);
                    s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), x, y + 0.5, z, 50, 1.4, 0.6, 1.4, 0.25);
                })
                .onHit((s, player) -> {
                    strike(player, DROP_DAMAGE);
                    player.setDeltaMovement(player.getDeltaMovement().add(0, 0.6, 0));
                    player.hurtMarked = true;
                })
                .start();
    }

    // 咆哮の間は傷を受けない。殻が鳴って弾く。殻が閉じた直後は、傷が一・五倍に効く
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRoaring()) {
            if (level() instanceof ServerLevel server) {
                server.playSound(null, getX(), getY(), getZ(), MobSounds.STONE_HEART_GUARD.get(), SoundSource.HOSTILE, 1.8f, 1.0f);
                server.sendParticles(ParticleTypes.CRIT, getX(), coreHeight(), getZ() - 1, 6, 0.8, 0.8, 0.4, 0.1);
            }
            return false;
        }
        return super.hurt(source, (weakTicks > 0 ? amount * 1.5f : amount) / toughness);
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
        super.remove(reason);
    }

    // 倒れたとき: 殻が剥がれて核が砕ける演出 (deathEffects) のあとで、試練の攻略を知らせる (onDeathFinished)。アイテムは落とさない (戦利品表なし)
    @Override
    public void die(DamageSource source) {
        if (defeated) {
            return;
        }
        defeated = true;
        super.die(source);
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                    getX(), coreHeight(), getZ(), 120, 1.2, 1.5, 1.2, 0.2);
            warn(server, dust(0x7FF4FF, 1.5f), getX(), coreHeight(), getZ(), 80, 1.5, 1.5, 1.5, 0.1);
        }
    }

    // 試練で湧いた心臓だけが、背中の攻略を知らせる (スポーンエッグの心臓や、地上の心臓では何も起きない)
    @Override
    protected void onDeathFinished(ServerLevel server) {
        if (server.dimension() == TitanTravel.TITAN_BACK && getTags().contains(TitanBackTrials.HEART_TAG)) {
            TitanBackTrials.onHeartDefeated(server, this);
        }
    }

    @Override
    protected double coreHeight() {
        return getY() + 2.2 * BODY_SCALE;
    }

    // ---- 登場: 床がきしみ、石の殻が床の下から迫り上がり、八枚の板が順に閉じて、核が一つ脈を打つ (animation.stone_whale_heart.intro)
    @Override
    protected void introEffects(ServerLevel server, int tick) {
        double fy = floorY();
        if (tick == 0) {
            sound(server, MobSounds.STONE_HEART_EMERGE.get(), 2.5f, 1.0f);
        }
        if (tick < 56 && tick % 3 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2 + random.nextDouble() * 3.5;
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                    getX() + Math.cos(a) * r, fy + 0.2, getZ() + Math.sin(a) * r, 8, 0.3, 0.2, 0.3, 0.15);
            if (tick % 9 == 0) {
                server.playSound(null, getX() + Math.cos(a) * r, fy, getZ() + Math.sin(a) * r, MobSounds.STONE_HEART_PLATE_LOCK.get(), SoundSource.HOSTILE, 1.2f, 0.7f);
            }
        }
        if (tick == 24) {
            sound(server, MobSounds.STONE_HEART_SLAM.get(), 2.5f, 0.8f);
            for (int k = 0; k < 24; k++) {
                double a = k * Math.PI / 12;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
                        getX() + Math.cos(a) * 3.5, fy + 0.3, getZ() + Math.sin(a) * 3.5, 5, 0.2, 0.3, 0.2, 0.25);
            }
        }
        if (tick >= 54 && tick <= 68 && (tick - 54) % 2 == 0) {
            int i = (tick - 54) / 2;
            double a = Math.toRadians(22.5 + 45 * i);
            double px = getX() + Math.cos(a) * 2.6;
            double pz = getZ() + Math.sin(a) * 2.6;
            server.playSound(null, px, fy + 2, pz, MobSounds.STONE_HEART_PLATE_LOCK.get(), SoundSource.HOSTILE, 1.4f, 0.8f + 0.04f * i);
            server.sendParticles(ParticleTypes.CRIT, px, fy + 2.0, pz, 6, 0.3, 0.8, 0.3, 0.1);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), px, fy + 1.0, pz, 10, 0.3, 0.5, 0.3, 0.15);
        }
        if (tick == 70) {
            sound(server, MobSounds.STONE_HEART_RESONANCE.get(), 3f, 1.0f);
            warn(server, dust(0x7FF4FF, 1.5f), getX(), coreHeight(), getZ(), 60, 1.5, 1.5, 1.5, 0.1);
            server.sendParticles(ParticleTypes.END_ROD, getX(), coreHeight(), getZ(), 30, 1.2, 1.2, 1.2, 0.08);
        }
    }

    // ---- 倒れる: 八枚の板が外へ剥がれ落ち、鎖が切れ、むき出しの核が細かくふるえて、光を吸い込んで砕ける (animation.stone_whale_heart.death)
    @Override
    protected void deathEffects(ServerLevel server, int tick) {
        double cy = coreHeight();
        double fy = floorY();
        if (tick == 14 || tick == 28 || tick == 42 || tick == 56) {
            sound(server, MobSounds.STONE_HEART_PLATE_FALL.get(), 2.5f, 0.9f + 0.05f * (tick / 14));
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + tick;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, k % 2 == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState()),
                        getX() + Math.cos(a) * 3, fy + 0.8, getZ() + Math.sin(a) * 3, 10, 0.4, 0.5, 0.4, 0.2);
            }
        }
        if (tick == 24) {
            sound(server, MobSounds.STONE_HEART_CHAIN_BREAK.get(), 2.5f, 1.0f);
        }
        if (tick == 34 || tick == 50) {
            sound(server, MobSounds.STONE_HEART_PLATE_FALL.get(), 1.6f, 1.2f);
        }
        if (tick >= 60 && tick <= 86 && tick % 2 == 0) {
            double a = tick * 0.5;
            double r = 4.0 * (1 - (tick - 60) / 26.0) + 0.5;
            warn(server, dust(0x7FF4FF, 1.2f), getX() + Math.cos(a) * r, cy + Math.sin(a * 0.7) * 0.8, getZ() + Math.sin(a) * r, 4, 0.1, 0.1, 0.1, 0);
            server.sendParticles(ParticleTypes.END_ROD, getX(), cy, getZ(), 2, 0.4, 0.4, 0.4, 0.02);
        }
        if (tick == 88) {
            sound(server, MobSounds.STONE_HEART_CORE_SHATTER.get(), 3f, 1.0f);
            server.sendParticles(ParticleTypes.END_ROD, getX(), cy, getZ(), 100, 0.3, 0.3, 0.3, 0.35);
            warn(server, dust(0x7FF4FF, 1.8f), getX(), cy, getZ(), 80, 1.0, 1.0, 1.0, 0.2);
        }
        if (tick == 92) {
            sound(server, MobSounds.STONE_HEART_SHARD.get(), 2.5f, 0.9f);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), cy, getZ(), 60, 1.0, 1.0, 1.0, 0.3);
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
        return MobSounds.STONE_WHALE_HEART_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.STONE_WHALE_HEART_HURT.get();
    }

    // 最後の一撃の音 (被弾と同じ)。倒れる演出は deathEffects が音も流すので、ここで別の音を重ねない
    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.STONE_WHALE_HEART_HURT.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.0f + (random.nextFloat() - random.nextFloat()) * 0.08f;
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    // 脈打つ光の粒を、核のあたりからこぼす
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextFloat() < 0.2f) {
            level().addParticle(dust(0x7FF4FF, 1.0f),
                    getX() + Mth.nextDouble(random, -0.8, 0.8), coreHeight() + Mth.nextDouble(random, -1.2, 1.2),
                    getZ() + Mth.nextDouble(random, -0.5, 0.5), 0, 0.02, 0);
        }
    }
}
