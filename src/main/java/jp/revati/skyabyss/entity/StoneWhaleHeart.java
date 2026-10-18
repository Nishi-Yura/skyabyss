package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.registry.MobSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

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

    // 落ちてくるものの種類。骨の棘、足元の炎、殻の塊
    private enum Kind { BONE, FIRE, SHELL }

    // 衝撃の輪。床をはい、跳び越えない相手に当たる (harmful が偽なら押し返すだけ)
    private static final class Wave {
        double radius = 1.5;
        final double floorY;
        final boolean harmful;
        final Set<UUID> hit = new HashSet<>();

        Wave(double floorY, boolean harmful) {
            this.floorY = floorY;
            this.harmful = harmful;
        }
    }

    // 落ちてくる (または噴き出す) 一撃。予告の輪が impactAt まで見え、そのあと当たる
    private record Spike(double x, double y, double z, int impactAt, Kind kind) {
    }

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
    private final List<Wave> waves = new ArrayList<>();
    private final List<Spike> spikes = new ArrayList<>();
    private final List<double[]> cracks = new ArrayList<>();
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

        tickWaves(server);
        tickSpikes(server);

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
            if (cooldown == 0) {
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

    // ---- 咆哮 (段階の切り替え) ----

    // 三秒。最初に咆哮し、中ほどで輪が人を押し返す。終わると段階が一つ進む (下の二枚の殻が落ちるのはモデル側)
    private void tickRoar(ServerLevel server) {
        if (actionTicks == 1) {
            setRoaring(true);
            sound(server, SoundEvents.WARDEN_ROAR, 3f, 0.6f);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                    getX(), getY() + 2.2, getZ(), 60, 1.5, 1.2, 1.5, 0.2);
        }
        if (actionTicks == 22) {
            sound(server, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.7f);
            waves.add(new Wave(floorY(), false));
        }
        if (actionTicks % 4 == 0 && actionTicks < 56) {
            warn(server, dust(0x9CFBFF, 1.2f), getX(), getY() + 2.2, getZ(), 6, 1.0, 1.0, 1.0, 0.05);
        }
        if (actionTicks >= ROAR_TICKS) {
            setRoaring(false);
            setPhase(getPhase() + 1);
            sound(server, SoundEvents.BEACON_ACTIVATE, 3f, 0.6f);
            finish();
            cooldown = 40;
        }
    }

    // ---- 段階1の技 ----

    // 殻の突き: 手前の殻がふくらみ (1秒)、前へ突き出す。前の帯の中の人に当たる
    private void tickThrust(ServerLevel server) {
        Vec3 f = forward(getYRot());
        if (actionTicks == 1) {
            sound(server, SoundEvents.DEEPSLATE_BREAK, 1.5f, 0.5f);
        }
        if (actionTicks < 24 && actionTicks % 2 == 0) {
            for (int i = -2; i <= 2; i++) {
                Vec3 side = new Vec3(-f.z, 0, f.x).scale(i * 1.1);
                warn(server, dust(0xA8C8D0, 1.0f),
                        getX() + f.x * 3.2 + side.x, getY() + 2.0, getZ() + f.z * 3.2 + side.z, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks == 24) {
            sound(server, SoundEvents.ANVIL_LAND, 2f, 0.5f);
            server.sendParticles(ParticleTypes.CRIT, getX() + f.x * 4, getY() + 2.0, getZ() + f.z * 4, 30, 1.5, 1.2, 1.5, 0.3);
            for (ServerPlayer player : nearbyPlayers(server, 8)) {
                Vec3 v = player.position().subtract(position()).multiply(1, 0, 1);
                double along = v.dot(f);
                double across = v.subtract(f.scale(along)).length();
                if (along > 0.5 && along < 7 && across < 2.6 && player.getY() < getY() + 3) {
                    strike(player, 12f);
                    player.knockback(1.2, -f.x, -f.z);
                }
            }
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 石棘の掃射: 殻の縁が鳴り (1秒)、弧を描く腕の先から石の棘が地を割って出る。棘の間に入れば避けられる
    private void tickSweep(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, SoundEvents.BONE_BLOCK_BREAK, 1.6f, 0.5f);
        }
        if (actionTicks < 20 && actionTicks % 3 == 0) {
            for (int a = 0; a < 360; a += 30) {
                double rad = Math.toRadians(a);
                warn(server, dust(0xFF5A5A, 1.0f),
                        getX() + Math.cos(rad) * 4.5, getY() - 1.0, getZ() + Math.sin(rad) * 4.5, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks >= 20 && actionTicks <= 68 && actionTicks % 3 == 0) {
            double t = (actionTicks - 20) / 48.0;
            Vec3 f = forward((float) (attackYaw + (-60 + 120 * t)));
            for (double d : new double[] {5, 8.5, 12}) {
                double x = getX() + f.x * d;
                double z = getZ() + f.z * d;
                spikes.add(new Spike(x, floorAt(Mth.floor(x), Mth.floor(z)), z, tickCount + 22, Kind.BONE));
            }
        }
        if (actionTicks >= 80) {
            finish();
        }
    }

    // 跳躍の叩きつけ: 心臓が沈み (1秒、鼓動が鳴る)、着地の輪が床を広がる。足元の人は直接打たれる
    private void tickLeap(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, SoundEvents.WARDEN_HEARTBEAT, 2.5f, 0.5f);
        }
        if (actionTicks < 24 && actionTicks % 3 == 0) {
            double fy = floorY();
            for (int a = 0; a < 360; a += 20) {
                double rad = Math.toRadians(a);
                warn(server, dust(0x7FF4FF, 1.2f),
                        getX() + Math.cos(rad) * 3, fy + 0.2, getZ() + Math.sin(rad) * 3, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks == 24) {
            sound(server, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.6f);
            double fy = floorY();
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    getX(), fy + 0.5, getZ(), 50, 2.0, 0.4, 2.0, 0.2);
            waves.add(new Wave(fy, true));
            for (ServerPlayer player : nearbyPlayers(server, 4)) {
                if (player.getY() < fy + 0.9 && player.position().subtract(position()).multiply(1, 0, 1).length() < 3.5) {
                    strike(player, 10f);
                }
            }
        }
        if (actionTicks >= 44) {
            finish();
        }
    }

    // ---- 段階2の技 ----

    // 亀裂の爆発: 人へ向かう三本のひびが光る (1.5秒)。そのあと、ひびの線に沿って床から炎が上がる
    private void tickCrack(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, SoundEvents.BLAZE_AMBIENT, 2f, 0.5f);
            cracks.clear();
            List<ServerPlayer> players = nearbyPlayers(server, 26);
            players.sort(Comparator.comparingDouble(p -> p.distanceToSqr(this)));
            Vec3 f = forward(getYRot());
            for (int i = 0; i < Math.min(3, players.size()); i++) {
                ServerPlayer p = players.get(i);
                double dx = p.getX() - getX();
                double dz = p.getZ() - getZ();
                double len = Math.hypot(dx, dz);
                if (len < 0.01) {
                    dx = f.x;
                    dz = f.z;
                    len = 1;
                }
                double ux = dx / len;
                double uz = dz / len;
                cracks.add(new double[] {getX(), getZ(), p.getX() + ux * 4, p.getZ() + uz * 4});
            }
        }
        if (actionTicks < 30 && actionTicks % 2 == 0) {
            for (double[] line : cracks) {
                double len = Math.hypot(line[2] - line[0], line[3] - line[1]);
                for (double d = 2; d <= len; d += 1.2) {
                    double t = d / len;
                    double x = line[0] + (line[2] - line[0]) * t;
                    double z = line[1] + (line[3] - line[1]) * t;
                    warn(server, dust(0xFF8A2A, 0.9f), x, floorAt(Mth.floor(x), Mth.floor(z)) + 0.1, z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (actionTicks == 30) {
            sound(server, SoundEvents.FIRECHARGE_USE, 2f, 0.6f);
            for (double[] line : cracks) {
                double len = Math.hypot(line[2] - line[0], line[3] - line[1]);
                int i = 0;
                for (double d = 3; d <= len; d += 1.6, i++) {
                    double t = d / len;
                    double x = line[0] + (line[2] - line[0]) * t;
                    double z = line[1] + (line[3] - line[1]) * t;
                    spikes.add(new Spike(x, floorAt(Mth.floor(x), Mth.floor(z)), z, tickCount + 6 + i * 2, Kind.FIRE));
                }
            }
        }
        if (actionTicks >= 60) {
            finish();
        }
    }

    // 殻の再閉: 殻が震え (1.5秒)、閉じる。閉じたあと一秒は、この心臓の攻撃が弱まり、傷は一・五倍に効く
    private void tickReclose(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, SoundEvents.CHAIN_PLACE, 1.6f, 0.5f);
        }
        if (actionTicks < 30 && actionTicks % 3 == 0) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                    getX() + random.nextGaussian() * 1.5, getY() + 1.5 + random.nextGaussian(), getZ() + random.nextGaussian() * 1.5,
                    2, 0.1, 0.1, 0.1, 0.05);
        }
        if (actionTicks == 30) {
            sound(server, SoundEvents.ANVIL_LAND, 2f, 0.6f);
            warn(server, dust(0xFFB347, 1.4f), getX(), getY() + 2.0, getZ(), 60, 1.8, 1.8, 1.8, 0.1);
            weakTicks = 20;
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // 骨の棘の投射: 体の棘が人へ向く (1.5秒)。その線の上へ、三本の棘を放つ
    private void tickVolley(ServerLevel server) {
        LivingEntity target = getTarget();
        if (actionTicks == 1) {
            sound(server, SoundEvents.CROSSBOW_LOADING_END.value(), 1.5f, 0.5f);
        }
        if (target != null && actionTicks < 30 && actionTicks % 2 == 0) {
            Vec3 from = position().add(0, 2.4, 0);
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 d = to.subtract(from);
            int steps = (int) Math.min(40, d.length() / 0.8);
            for (int i = 1; i <= steps; i++) {
                Vec3 p = from.add(d.scale((double) i / steps));
                warn(server, dust(0xFFD0A0, 0.7f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks == 30 && target != null) {
            sound(server, SoundEvents.SKELETON_SHOOT, 2f, 0.6f);
            Vec3 origin = position().add(0, 2.4, 0).add(forward(getYRot()).scale(1.2));
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(origin);
            double base = Math.atan2(aim.z, aim.x);
            double horiz = Math.hypot(aim.x, aim.z);
            for (double spread : new double[] {-0.12, 0, 0.12}) {
                BoneSpike spike = new BoneSpike(server, this);
                spike.setPos(origin.x, origin.y, origin.z);
                double a = base + spread;
                spike.shoot(Math.cos(a) * horiz, aim.y, Math.sin(a) * horiz, 1.6f, 0f);
                server.addFreshEntity(spike);
            }
        }
        if (actionTicks >= 40) {
            finish();
        }
    }

    // ---- 段階3の技 ----

    // 赤熱の光線: 核が白く光り (2秒)、向きは溜めの最初に決まる。光線は細く一秒ほど薙ぐ
    private void tickBeam(ServerLevel server) {
        Vec3 f = forward(attackYaw);
        Vec3 origin = position().add(0, 2.6, 0).add(f.scale(1.0));
        if (actionTicks == 1) {
            sound(server, SoundEvents.BEACON_POWER_SELECT, 2f, 0.6f);
        }
        if (actionTicks < 40) {
            if (actionTicks % 2 == 0) {
                double r = 2.6 * (1 - actionTicks / 40.0) + 0.3;
                for (int a = 0; a < 360; a += 45) {
                    double rad = Math.toRadians(a + actionTicks * 6);
                    warn(server, dust(0xFF3A2A, 1.0f),
                            origin.x + Math.cos(rad) * r, origin.y + Math.sin(rad) * r, origin.z, 1, 0, 0, 0, 0);
                }
            }
            if (actionTicks % 4 == 0) {
                for (double d = 2; d <= 12; d += 1.0) {
                    warn(server, dust(0xFF8A7A, 0.6f), origin.x + f.x * d, origin.y, origin.z + f.z * d, 1, 0, 0, 0, 0);
                }
            }
        } else if (actionTicks < 64) {
            if (actionTicks == 40) {
                sound(server, SoundEvents.BEACON_ACTIVATE, 2.5f, 1.6f);
            }
            double p = (actionTicks - 40) / 24.0;
            Vec3 dir = forward((float) (attackYaw + (-8 + 16 * p)));
            for (double d = 1; d <= BEAM_LENGTH; d += 0.8) {
                warn(server, dust(0xFF3A2A, 1.4f),
                        origin.x + dir.x * d, origin.y, origin.z + dir.z * d, 1, 0.05, 0.05, 0.05, 0);
            }
            if (actionTicks % 6 == 0) {
                AABB area = new AABB(origin, origin.add(dir.scale(BEAM_LENGTH))).inflate(1.5, 2, 1.5);
                for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach)) {
                    Vec3 v = player.position().add(0, player.getBbHeight() * 0.6, 0).subtract(origin);
                    double along = v.dot(dir);
                    if (along < 0 || along > BEAM_LENGTH) {
                        continue;
                    }
                    Vec3 perp = v.subtract(dir.scale(along));
                    if (perp.length() < 1.1) {
                        strike(player, 10f);
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                    }
                }
            }
        }
        if (actionTicks >= 66) {
            finish();
        }
    }

    // 鎖の振り回し: 鎖が床に線を引き (1.2秒)、その線が半円を描いて振られる。線を跳び越えれば当たらない
    private void tickChain(ServerLevel server) {
        if (actionTicks == 1) {
            chainY = floorY();
            sound(server, SoundEvents.CHAIN_STEP, 2f, 0.5f);
        }
        if (actionTicks < 24 && actionTicks % 2 == 0) {
            Vec3 f = forward(attackYaw);
            for (double d = 1; d <= CHAIN_LENGTH; d += 1.0) {
                warn(server, dust(0x8A1C1C, 1.0f), getX() + f.x * d, chainY + 0.1, getZ() + f.z * d, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks >= 24 && actionTicks <= 60) {
            double t = (actionTicks - 24) / 36.0;
            Vec3 f = forward((float) (attackYaw + (-90 + 180 * t)));
            for (double d = 1; d <= CHAIN_LENGTH; d += 1.5) {
                warn(server, dust(0xB02A2A, 1.2f), getX() + f.x * d, chainY + 1.0, getZ() + f.z * d, 1, 0, 0, 0, 0);
            }
            if (actionTicks % 4 == 0) {
                for (ServerPlayer player : nearbyPlayers(server, CHAIN_LENGTH + 2)) {
                    Vec3 v = player.position().subtract(getX(), chainY, getZ());
                    double along = v.dot(f);
                    if (along < 0 || along > CHAIN_LENGTH) {
                        continue;
                    }
                    double across = v.subtract(f.scale(along)).length();
                    // 鎖は腰の高さを通る。跳び越えた人は当たらない
                    if (across < 1.2 && player.getY() < chainY + 1.0) {
                        strike(player, 10f);
                        player.knockback(0.9, f.x, f.z);
                    }
                }
            }
        }
        if (actionTicks >= 62) {
            finish();
        }
    }

    // 殻の落下: 人の足元に二つの影が広がり (1.5秒)、そこへ殻の塊が落ちる
    private void tickShellDrop(ServerLevel server) {
        if (actionTicks == 1) {
            sound(server, SoundEvents.GRINDSTONE_USE, 1.8f, 0.4f);
            List<ServerPlayer> players = nearbyPlayers(server, 26);
            players.sort(Comparator.comparingDouble(p -> p.distanceToSqr(this)));
            for (int i = 0; i < Math.min(2, players.size()); i++) {
                ServerPlayer p = players.get(i);
                spikes.add(new Spike(p.getX(), floorAt(Mth.floor(p.getX()), Mth.floor(p.getZ())), p.getZ(), tickCount + 30, Kind.SHELL));
            }
        }
        if (actionTicks >= 50) {
            finish();
        }
    }

    // ---- 輪と落下物 (技の途中でも、技の後も続く) ----

    // 輪を広げ、跳び越えなかった相手に当てる。輪は一人一度だけ当たる
    private void tickWaves(ServerLevel server) {
        for (Wave wave : waves) {
            wave.radius += 0.8;
            int points = Math.max(8, (int) (2 * Math.PI * wave.radius / 1.6));
            for (int i = 0; i < points; i++) {
                double a = i * 2 * Math.PI / points;
                warn(server, dust(0x7FF4FF, 1.2f),
                        getX() + Math.cos(a) * wave.radius, wave.floorY + 0.6, getZ() + Math.sin(a) * wave.radius, 1, 0, 0, 0, 0);
            }
            double r = wave.radius + 2;
            AABB area = new AABB(getX() - r, wave.floorY - 1, getZ() - r, getX() + r, wave.floorY + 3, getZ() + r);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach)) {
                Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                double d = to.length();
                // 地に立ったままの人だけに当たる (跳べば輪の下を抜けられる)
                if (Math.abs(d - wave.radius) < 1.0 && player.getY() < wave.floorY + 0.9 && wave.hit.add(player.getUUID())) {
                    if (wave.harmful) {
                        strike(player, 8f);
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    }
                    player.knockback(0.9, -to.x, -to.z);
                }
            }
        }
        waves.removeIf(w -> w.radius > 22);
    }

    // 落ちてくる (噴き出す) ものたち: 予告の輪が見え、impactAt で当たる
    private void tickSpikes(ServerLevel server) {
        if (spikes.isEmpty()) {
            return;
        }
        List<Spike> remaining = new ArrayList<>();
        for (Spike s : spikes) {
            if (tickCount >= s.impactAt()) {
                impact(server, s);
                continue;
            }
            if (tickCount % 4 == 0) {
                int color = switch (s.kind()) {
                    case BONE -> 0xFF5A5A;
                    case FIRE -> 0xFF8A2A;
                    case SHELL -> 0x6B6B78;
                };
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    warn(server, dust(color, 1.0f),
                            s.x() + Math.cos(rad) * 1.4, s.y() + 0.1, s.z() + Math.sin(rad) * 1.4, 1, 0, 0, 0, 0);
                }
            }
            if (s.kind() != Kind.FIRE && tickCount >= s.impactAt() - 12) {
                double drop = (s.impactAt() - tickCount) / 12.0;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                (s.kind() == Kind.SHELL ? Blocks.STONE_BRICKS : Blocks.BONE_BLOCK).defaultBlockState()),
                        s.x(), s.y() + 1 + drop * 9, s.z(), 2, 0.05, 0.1, 0.05, 0);
            }
            remaining.add(s);
        }
        spikes.clear();
        spikes.addAll(remaining);
    }

    private void impact(ServerLevel server, Spike s) {
        switch (s.kind()) {
            case BONE -> {
                server.playSound(null, s.x(), s.y(), s.z(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.6f, 0.6f);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                        s.x(), s.y() + 0.5, s.z(), 30, 0.6, 0.4, 0.6, 0.2);
                hurtArea(server, s, 1.4, 9f, 0.4);
            }
            case FIRE -> {
                server.playSound(null, s.x(), s.y(), s.z(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2f, 0.9f);
                server.sendParticles(ParticleTypes.FLAME, s.x(), s.y() + 0.3, s.z(), 18, 0.5, 0.6, 0.5, 0.05);
                server.sendParticles(ParticleTypes.LAVA, s.x(), s.y() + 0.3, s.z(), 4, 0.4, 0.2, 0.4, 0);
                for (ServerPlayer player : areaPlayers(server, s, 1.2)) {
                    strike(player, 8f);
                    player.setRemainingFireTicks(60);
                }
            }
            case SHELL -> {
                server.playSound(null, s.x(), s.y(), s.z(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.2f, 0.5f);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                        s.x(), s.y() + 0.5, s.z(), 60, 1.4, 0.6, 1.4, 0.25);
                hurtArea(server, s, 2.5, 12f, 0.6);
            }
        }
    }

    private List<ServerPlayer> areaPlayers(ServerLevel server, Spike s, double radius) {
        AABB area = new AABB(s.x() - radius, s.y() - 1, s.z() - radius, s.x() + radius, s.y() + 2.5, s.z() + radius);
        return server.getEntitiesOfClass(ServerPlayer.class, area, this::canReach);
    }

    private void hurtArea(ServerLevel server, Spike s, double radius, float amount, double lift) {
        for (ServerPlayer player : areaPlayers(server, s, radius)) {
            strike(player, amount);
            player.setDeltaMovement(player.getDeltaMovement().add(0, lift, 0));
            player.hurtMarked = true;
        }
    }

    // 咆哮の間は傷を受けない。殻が鳴って弾く。殻が閉じた直後は、傷が一・五倍に効く
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRoaring()) {
            if (level() instanceof ServerLevel server) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.5f);
                server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.2, getZ() - 1, 6, 0.8, 0.8, 0.4, 0.1);
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
                    getX(), getY() + 2.2, getZ(), 120, 1.2, 1.5, 1.2, 0.2);
            warn(server, dust(0x7FF4FF, 1.5f), getX(), getY() + 2.2, getZ(), 80, 1.5, 1.5, 1.5, 0.1);
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
        return getY() + 2.2;
    }

    // ---- 登場: 床がきしみ、石の殻が床の下から迫り上がり、八枚の板が順に閉じて、核が一つ脈を打つ (animation.stone_whale_heart.intro)
    @Override
    protected void introEffects(ServerLevel server, int tick) {
        double fy = floorY();
        if (tick == 0) {
            sound(server, SoundEvents.WARDEN_EMERGE, 2f, 0.6f);
        }
        if (tick < 56 && tick % 3 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2 + random.nextDouble() * 3.5;
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                    getX() + Math.cos(a) * r, fy + 0.2, getZ() + Math.sin(a) * r, 8, 0.3, 0.2, 0.3, 0.15);
            if (tick % 9 == 0) {
                server.playSound(null, getX() + Math.cos(a) * r, fy, getZ() + Math.sin(a) * r, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
            }
        }
        if (tick == 24) {
            sound(server, SoundEvents.DEEPSLATE_BREAK, 2.5f, 0.5f);
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
            server.playSound(null, px, fy + 2, pz, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0f, 0.5f + 0.04f * i);
            server.sendParticles(ParticleTypes.CRIT, px, fy + 2.0, pz, 6, 0.3, 0.8, 0.3, 0.1);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), px, fy + 1.0, pz, 10, 0.3, 0.5, 0.3, 0.15);
        }
        if (tick == 70) {
            sound(server, SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, 0.5f);
            sound(server, SoundEvents.BELL_RESONATE, 2f, 0.5f);
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
            sound(server, SoundEvents.STONE_BREAK, 2.5f, 0.6f);
            sound(server, SoundEvents.ANVIL_LAND, 1.2f, 0.4f);
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + tick;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, k % 2 == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState()),
                        getX() + Math.cos(a) * 3, fy + 0.8, getZ() + Math.sin(a) * 3, 10, 0.4, 0.5, 0.4, 0.2);
            }
        }
        if (tick == 24) {
            sound(server, SoundEvents.CHAIN_BREAK, 2.5f, 0.6f);
        }
        if (tick == 34 || tick == 50) {
            sound(server, SoundEvents.SHIELD_BREAK, 1.5f, 0.7f);
        }
        if (tick >= 60 && tick <= 86 && tick % 2 == 0) {
            double a = tick * 0.5;
            double r = 4.0 * (1 - (tick - 60) / 26.0) + 0.5;
            warn(server, dust(0x7FF4FF, 1.2f), getX() + Math.cos(a) * r, cy + Math.sin(a * 0.7) * 0.8, getZ() + Math.sin(a) * r, 4, 0.1, 0.1, 0.1, 0);
            server.sendParticles(ParticleTypes.END_ROD, getX(), cy, getZ(), 2, 0.4, 0.4, 0.4, 0.02);
        }
        if (tick == 88) {
            sound(server, SoundEvents.BEACON_DEACTIVATE, 3f, 0.7f);
            server.sendParticles(ParticleTypes.END_ROD, getX(), cy, getZ(), 100, 0.3, 0.3, 0.3, 0.35);
            warn(server, dust(0x7FF4FF, 1.8f), getX(), cy, getZ(), 80, 1.0, 1.0, 1.0, 0.2);
        }
        if (tick == 92) {
            sound(server, SoundEvents.AMETHYST_CLUSTER_BREAK, 2.5f, 0.5f);
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

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.STONE_WHALE_HEART_DEATH.get();
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
                    getX() + Mth.nextDouble(random, -0.8, 0.8), getY() + 2.2 + Mth.nextDouble(random, -0.8, 0.8),
                    getZ() + Mth.nextDouble(random, -0.5, 0.5), 0, 0.02, 0);
        }
    }
}
