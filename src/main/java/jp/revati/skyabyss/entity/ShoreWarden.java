package jp.revati.skyabyss.entity;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.story.ch2.Ch2Mobs;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 潮の番人 (名づき): 沈んだ塩の蔵の主。珊瑚憑きの変種 (背が高く、珊瑚の銛に塩の白い筋)。
// 技はすべて Telegraph で予告する (名づきの技の予告は 1 秒以上)。人数で体力が増える (番兵長・心臓・老いた歩哨と同じ式)。
//   銛の突き     構え 1.1 秒 (THRUST_WARN。床の前の帯 長さ 5・幅 1.8) → 前へ踏み込んで突く (9)。帯の外へ出ればよける
//   満ち潮の輪   銛を頭上へ掲げ、狙った人の足もとに青い輪 (半径 2.6) が 1.2 秒 (RING_WARN) 満ちる → 石突を床へ。
//                輪の中の人に 4 と鈍足 II 2 秒。輪の中の床は FLOOD_TICKS (5 秒) 水に沈み (ShoreFlood)、そのあと元に戻る
// 浅い水の部屋 (12〜14 × 12〜19、天井 6) で戦う前提。水の中でも溺れない
public class ShoreWarden extends GroundFoe {
    public static final float HEALTH = 160f;
    public static final float THRUST_DAMAGE = 9f;
    public static final int THRUST_WARN = 22;
    public static final double THRUST_LENGTH = 5.0;
    public static final double THRUST_HALF_WIDTH = 0.9;
    public static final double THRUST_RANGE = 4.4;
    public static final int RING_WARN = 24;
    public static final double RING_RADIUS = 2.6;
    public static final float RING_DAMAGE = 4f;
    public static final int RING_SLOW_TICKS = 40;
    public static final int FLOOD_TICKS = 100;
    public static final int RING_COLOR = 0x3C8CFF;
    private static final int THRUST_END = 38;
    private static final int RING_END = 42;

    private enum Kind { NONE, THRUST, RING }

    private final ServerBossEvent bossBar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_6));
    private Kind kind = Kind.NONE;
    private int ringCd = 80;
    private boolean scaled;
    private boolean holdAttacks;
    private final List<ShoreFlood.Patch> floods = new ArrayList<>();

    public ShoreWarden(EntityType<? extends Monster> type, Level level) {
        super(type, level, "shore_warden", "thrust", "ring", "hurt", "death");
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, THRUST_DAMAGE)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ApproachGoal(this, 1.0, 3.2));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    // 人数に応じた体力 (最初の AI tick で。湧かせる側が groupId を入れたあと)
    private void scaleToGroup() {
        scaled = true;
        int n;
        if (groupId() != null) {
            n = Groups.alive((ServerLevel) level(), groupId()).size();
        } else {
            n = (int) level().players().stream().filter(p -> !p.isSpectator() && p.distanceToSqr(this) < 24 * 24).count();
        }
        float scale = StoneWhaleHeart.scaleFor(Math.min(n, jp.revati.skyabyss.party.Parties.MAX_MEMBERS));
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null && scale > 1f) {
            health.setBaseValue(Math.min(1024, health.getBaseValue() * scale));
            setHealth(getMaxHealth());
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!scaled) {
            scaleToGroup();
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 10 == 0) {
            updateBossBarViewers();
        }
        if (cooldown > 0) {
            cooldown--;
        }
        ringCd = Math.max(0, ringCd - 1);
        if (kind == Kind.NONE) {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || cooldown > 0 || holdAttacks) {
                return;
            }
            double d = distanceTo(target);
            boolean los = hasLineOfSight(target);
            if (ringCd == 0 && d <= 12 && los && (d > 3.0 || random.nextFloat() < 0.35f)) {
                begin(Kind.RING, target);
            } else if (d <= THRUST_RANGE && los) {
                begin(Kind.THRUST, target);
            }
            return;
        }
        act++;
        holdYaw();
        switch (kind) {
            case THRUST -> {
                // 満ちた瞬間から 4 tick 踏み込む (0.45 × 4 = 1.8。帯の長さ 5 に収まる)
                if (act >= THRUST_WARN && act < THRUST_WARN + 4) {
                    Vec3 f = forward();
                    setDeltaMovement(f.x * 0.45, getDeltaMovement().y, f.z * 0.45);
                }
                if (act >= THRUST_END) {
                    finish(24);
                }
            }
            case RING -> {
                if (act >= RING_END) {
                    ringCd = 160;
                    finish(24);
                }
            }
            default -> {
            }
        }
    }

    // 試験用・開発用: 技を一つ出させる ("THRUST" "RING")
    public void forceAction(String name, LivingEntity target) {
        if (kind == Kind.NONE && target != null) {
            begin(Kind.valueOf(name), target);
        }
    }

    public void holdAttacks() {
        holdAttacks = true;
    }

    public String actionName() {
        return kind.name();
    }

    // この番人が置いた水のうち、まだ戻っていないもの
    public List<ShoreFlood.Patch> floods() {
        floods.removeIf(p -> !ShoreFlood.active().contains(p));
        return List.copyOf(floods);
    }

    private void begin(Kind next, LivingEntity target) {
        kind = next;
        act = 1;
        jp.revati.skyabyss.combat.BossCue.send(this, "shore_warden", next.name().toLowerCase(java.util.Locale.ROOT), 30);
        lockYawToward(target);
        holdYaw();
        if (next == Kind.THRUST) {
            playAction("thrust");
            startThrust(target);
        } else {
            playAction("ring");
            startRing(target);
        }
    }

    private void finish(int cool) {
        kind = Kind.NONE;
        act = 0;
        cooldown = cool;
    }

    // ---- 銛の突き: 構え 1.1 秒の床の帯 → 踏み込んで突く ----
    private void startThrust(LivingEntity target) {
        Vec3 f = forward();
        double angle = Telegraph.angleOf(f.x, f.z);
        Telegraph.in((ServerLevel) level(), TelegraphShape.line(getX(), getZ(), angle, THRUST_LENGTH, THRUST_HALF_WIDTH))
                .floor(getY()).warn(THRUST_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.SHORE_WARDEN_STANCE.get(), 1.4f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> {
                    server.playSound(null, getX(), getY(), getZ(), MobSounds.SHORE_WARDEN_THRUST.get(), SoundSource.HOSTILE, 1.6f, 1.0f);
                    server.sendParticles(ParticleTypes.SPLASH, getX() + f.x * 2.5, getY() + 0.3, getZ() + f.z * 2.5, 16, 0.6, 0.1, 0.6, 0.1);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), THRUST_DAMAGE);
                    player.knockback(1.0, -f.x, -f.z);
                })
                .start();
    }

    // ---- 満ち潮の輪: 狙った人の足もとに青い輪 1.2 秒 → 石突で床を打ち、輪の中を水に沈める ----
    private void startRing(LivingEntity target) {
        double cx = target.getX();
        double cz = target.getZ();
        int floorY = BlockPos.containing(target.getX(), target.getY() + 0.01, target.getZ()).getY();
        double y = target.getY();
        Telegraph.in((ServerLevel) level(), TelegraphShape.circle(cx, cz, RING_RADIUS))
                .floor(y).warn(RING_WARN).color(RING_COLOR).owner(this).target(target)
                .startSound(MobSounds.SHORE_WARDEN_RING.get(), 1.4f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> {
                    server.playSound(null, cx, y, cz, MobSounds.SHORE_WARDEN_FLOOD.get(), SoundSource.HOSTILE, 1.6f, 1.0f);
                    server.sendParticles(ParticleTypes.SPLASH, cx, y + 0.3, cz, 40, RING_RADIUS * 0.5, 0.2, RING_RADIUS * 0.5, 0.2);
                    server.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, cx, y + 0.2, cz, 20, RING_RADIUS * 0.4, 0.1, RING_RADIUS * 0.4, 0.05);
                    if (isAlive()) {
                        floods.add(ShoreFlood.flood(server, cx, floorY, cz, RING_RADIUS, FLOOD_TICKS));
                    }
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), RING_DAMAGE);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RING_SLOW_TICKS, 1));
                })
                .start();
    }

    // ---- ふつうの部分 ----
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive() && kind == Kind.NONE) {
            playAction("hurt");
        }
        return hit;
    }

    private void updateBossBarViewers() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        for (ServerPlayer p : Players.snapshot(server.players())) {
            if (p.distanceToSqr(this) < 32 * 32 && GroupOwned.visibleTo(this, p)) {
                bossBar.addPlayer(p);
            } else {
                bossBar.removePlayer(p);
            }
        }
    }

    // 置いた水を今すぐ戻す (倒れた・消えたとき)
    private void drainFloods() {
        if (level() instanceof ServerLevel server) {
            for (ShoreFlood.Patch p : floods) {
                ShoreFlood.revert(server.getServer(), p);
            }
        }
        floods.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        if (!level().isClientSide) {
            drainFloods();
        }
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            Telegraph.cancelAll(this);
            bossBar.removeAllPlayers();
            drainFloods();
            playAction("death");
        }
    }

    // 真珠の護り糸 1、塩結晶 2〜4。素材がまだ登録されていなければ落とさない
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Item thread = Ch2Mobs.item(Ch2Mobs.PEARL_WARD_THREAD);
        Item salt = Ch2Mobs.item(Ch2Mobs.SALT_CRYSTAL);
        if (thread != null) {
            spawnAtLocation(new ItemStack(thread));
        }
        if (salt != null) {
            spawnAtLocation(new ItemStack(salt, 2 + random.nextInt(3)));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.SHORE_WARDEN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.SHORE_WARDEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.SHORE_WARDEN_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.SHORE_WARDEN_STEP.get(), 0.4f);
    }
}
