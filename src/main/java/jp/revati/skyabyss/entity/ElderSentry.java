package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.guide.GuideSetup;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.story.ch1.Ch1Prep;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 老いた歩哨 (名づき): 割れ殻の洞の主。骨の番兵の変種 (背が高く、盾に琥珀の目の割れ、肩に苔)。番兵長の技の読み方の予習で、
// 技はすべて Telegraph で予告する。人数で体力が増える (番兵長・心臓と同じ式)。正面からの傷は半分 (盾)。
//   振り下ろし   予告 0.8 秒の前方の扇 (近接 7)。頭の上へ振りかぶる
//   盾の突進     予告 1.2 秒の床の帯 (前 7.5・幅 2.4)。盾を前へ構え、満ちた瞬間に突っ込む (9)
//   骨笛         予告 0.8 秒、笛を吹く位置 2 か所に印 (傷なし)。骨棘の射手 2 を呼ぶ (同時に 2 体まで)
//   足踏み       予告 1.2 秒の足もとの円 (半径 3.4。8)。片脚を上げて踏み下ろす
public class ElderSentry extends GroundFoe {
    // 技の数値 (印と当たりは同じ形)
    public static final int SWING_WARN = 16;
    public static final double SWING_RANGE = 3.2;
    public static final double SWING_HALF_ANGLE = 50;
    public static final int RUSH_WARN = 24;
    public static final double RUSH_LENGTH = 7.5;
    public static final double RUSH_HALF_WIDTH = 1.2;
    public static final float RUSH_DAMAGE = 9f;
    public static final int HORN_WARN = 16;
    public static final double HORN_MARK_RADIUS = 1.1;
    public static final int MAX_SUMMONS = 1;   // 2026-10-23: 呼ぶ技は重く (2 → 1)
    public static final int STOMP_WARN = 24;
    public static final double STOMP_RADIUS = 3.4;
    public static final float STOMP_DAMAGE = 8f;
    private static final int SWING_END = 30;
    private static final int RUSH_END = 42;
    private static final int HORN_END = 36;
    private static final int STOMP_END = 40;

    private enum Kind { NONE, SWING, RUSH, HORN, STOMP }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_6);
    private Kind kind = Kind.NONE;
    private int rushCd = 60;
    private int stompCd = 80;
    private int hornCd = 400;
    private boolean scaled;
    // 試験用: 自分では技を選ばない (forceAction で出させる)
    private boolean holdAttacks;
    private final List<UUID> summons = new ArrayList<>();
    private final List<Vec3> hornSpots = new ArrayList<>();

    public ElderSentry(EntityType<? extends Monster> type, Level level) {
        super(type, level, "elder_sentry", "attack", "charge", "whistle", "stomp", "hurt", "death");
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 140)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ApproachGoal(this, 1.0, 2.2));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
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
        rushCd = Math.max(0, rushCd - 1);
        stompCd = Math.max(0, stompCd - 1);
        hornCd = Math.max(0, hornCd - 1);
        if (kind == Kind.NONE) {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || cooldown > 0 || holdAttacks) {
                return;
            }
            double d = distanceTo(target);
            boolean los = hasLineOfSight(target);
            if (hornCd == 0 && liveSummons() < MAX_SUMMONS && d < 16 && los && getHealth() < getMaxHealth() * 0.85f) {
                begin(Kind.HORN, target);
            } else if (d <= STOMP_RADIUS && stompCd == 0 && random.nextFloat() < 0.5f) {
                begin(Kind.STOMP, target);
            } else if (d <= 2.8 && los) {
                begin(Kind.SWING, target);
            } else if (d >= 5 && d <= 14 && rushCd == 0 && los) {
                begin(Kind.RUSH, target);
            }
            return;
        }
        act++;
        holdYaw();
        switch (kind) {
            case SWING -> tickSwing();
            case RUSH -> tickRush();
            case HORN -> tickHorn();
            case STOMP -> tickStomp();
            default -> {
            }
        }
    }

    // 試験用・開発用: 技を一つ出させる ("SWING" "RUSH" "HORN" "STOMP")。技の最中なら何もしない
    public void forceAction(String name, LivingEntity target) {
        if (kind == Kind.NONE && target != null) {
            begin(Kind.valueOf(name), target);
        }
    }

    public void holdAttacks() {
        holdAttacks = true;
    }

    // 試験用: いまの技の名前 (NONE / SWING / RUSH / HORN / STOMP)
    public String actionName() {
        return kind.name();
    }

    private void begin(Kind next, LivingEntity target) {
        kind = next;
        act = 1;
        if (next.ordinal() != 0) {
            jp.revati.skyabyss.combat.BossCue.send(this, "elder_sentry", next.name().toLowerCase(java.util.Locale.ROOT), 30);
        }
        lockYawToward(target);
        holdYaw();
        switch (next) {
            case SWING -> {
                playAction("attack");
                startSwing(target);
            }
            case RUSH -> {
                playAction("charge");
                startRush(target);
            }
            case HORN -> {
                playAction("whistle");
                startHorn();
            }
            case STOMP -> {
                playAction("stomp");
                startStomp(target);
            }
            default -> {
            }
        }
    }

    private void finish(int cool) {
        kind = Kind.NONE;
        act = 0;
        cooldown = cool;
    }

    private double floorY() {
        return getY();
    }

    // ---- 振り下ろし ----
    private void startSwing(LivingEntity target) {
        Vec3 f = forward();
        double angle = Telegraph.angleOf(f.x, f.z);
        Telegraph.in((ServerLevel) level(), TelegraphShape.cone(getX(), getZ(), angle, SWING_RANGE, Math.toRadians(SWING_HALF_ANGLE)))
                .floor(floorY()).warn(SWING_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.ELDER_SENTRY_SWING.get(), 1.0f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 1.6, getY() + 1.2, getZ() + f.z * 1.6, 2, 0.6, 0.2, 0.6, 0))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
                    player.knockback(0.8, -f.x, -f.z);
                })
                .start();
    }

    private void tickSwing() {
        if (act >= SWING_END) {
            finish(24);
        }
    }

    // ---- 盾の突進 ----
    private void startRush(LivingEntity target) {
        Vec3 f = forward();
        double angle = Telegraph.angleOf(f.x, f.z);
        Telegraph.in((ServerLevel) level(), TelegraphShape.line(getX(), getZ(), angle, RUSH_LENGTH, RUSH_HALF_WIDTH))
                .floor(floorY()).warn(RUSH_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.ELDER_SENTRY_CHARGE.get(), 1.4f, 0.9f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.POOF, getX() + f.x, getY() + 0.2, getZ() + f.z, 8, 0.5, 0.1, 0.5, 0.03))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), RUSH_DAMAGE);
                    player.knockback(1.4, -f.x, -f.z);
                })
                .start();
    }

    private void tickRush() {
        // 満ちた瞬間から 8 tick 前へ突っ込む (帯の長さ 7.5 に収まる: 0.9 × 8 = 7.2)
        if (act >= RUSH_WARN && act < RUSH_WARN + 8) {
            Vec3 f = forward();
            setDeltaMovement(f.x * 0.9, getDeltaMovement().y, f.z * 0.9);
        }
        if (act >= RUSH_END) {
            rushCd = 120;
            finish(30);
        }
    }

    // ---- 骨笛: 射手 2 を呼ぶ ----
    private void startHorn() {
        hornSpots.clear();
        Vec3 f = forward();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        for (int s = -1; s <= 1; s += 2) {
            hornSpots.add(position().add(f.scale(2.5)).add(side.scale(3.0 * s)));
        }
        level().playSound(null, getX(), getY(), getZ(), MobSounds.ELDER_SENTRY_HORN.get(), SoundSource.HOSTILE, 2f, 1f);
        for (Vec3 p : hornSpots) {
            // 傷はなく、呼ばれる場所の印だけ (無害)
            Telegraph.in((ServerLevel) level(), TelegraphShape.circle(p.x, p.z, HORN_MARK_RADIUS))
                    .floor(getY()).warn(HORN_WARN).harmless().owner(this).start();
        }
    }

    private void tickHorn() {
        if (act == HORN_WARN) {
            for (Vec3 p : hornSpots) {
                summonArcher(p);
            }
            hornCd = 720;
        }
        if (act >= HORN_END) {
            finish(30);
        }
    }

    private void summonArcher(Vec3 at) {
        if (liveSummons() >= MAX_SUMMONS) {
            return;
        }
        ServerLevel server = (ServerLevel) level();
        RibArcher archer = ModEntities.RIB_ARCHER.get().create(server);
        if (archer == null) {
            return;
        }
        archer.moveTo(at.x, getY(), at.z, getYRot() + 180f, 0f);
        archer.addTag(GuardMob.TEMP_TAG);
        archer.setGroupId(groupId());
        archer.finalizeSpawn(server, server.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
        if (server.addFreshEntity(archer)) {
            summons.add(archer.getUUID());
            server.sendParticles(ParticleTypes.POOF, at.x, getY() + 0.5, at.z, 10, 0.3, 0.5, 0.3, 0.02);
            if (getTarget() != null) {
                archer.setTarget(getTarget());
            }
        }
    }

    // 呼んだ射手のうち生きている数
    public int liveSummons() {
        if (!(level() instanceof ServerLevel server)) {
            return 0;
        }
        summons.removeIf(id -> {
            Entity e = server.getEntity(id);
            return e == null || !e.isAlive();
        });
        return summons.size();
    }

    // ---- 足踏み ----
    private void startStomp(LivingEntity target) {
        double cx = getX();
        double cz = getZ();
        Telegraph.in((ServerLevel) level(), TelegraphShape.circle(cx, cz, STOMP_RADIUS))
                .floor(floorY()).warn(STOMP_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.ELDER_SENTRY_CHARGE.get(), 1.2f, 0.6f)
                .victims(this::canHit)
                .onImpact(server -> {
                    server.playSound(null, cx, getY(), cz, MobSounds.ELDER_SENTRY_STOMP.get(), SoundSource.HOSTILE, 2f, 0.9f);
                    server.sendParticles(ParticleTypes.POOF, cx, getY() + 0.1, cz, 24, STOMP_RADIUS * 0.5, 0.1, STOMP_RADIUS * 0.5, 0.04);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), STOMP_DAMAGE);
                    Vec3 away = player.position().subtract(cx, 0, cz).multiply(1, 0, 1);
                    player.knockback(1.0, -away.x, -away.z);
                })
                .start();
    }

    private void tickStomp() {
        if (act >= STOMP_END) {
            stompCd = 100;
            finish(30);
        }
    }

    // ---- 受ける ----
    // 正面からの傷は半分 (盾。番兵の変種)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (from != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            Vec3 look = getViewVector(1f).multiply(1, 0, 1).normalize();
            Vec3 toSource = from.subtract(position()).multiply(1, 0, 1).normalize();
            if (look.dot(toSource) > 0.5) {
                amount *= 0.5f;
                level().playSound(null, getX(), getY(), getZ(), MobSounds.ELDER_SENTRY_GUARD.get(), SoundSource.HOSTILE, 1f, 0.8f + random.nextFloat() * 0.2f);
            }
        }
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
            boolean near = p.distanceToSqr(this) < 32 * 32 && GroupOwned.visibleTo(this, p);
            if (near) {
                bossBar.addPlayer(p);
            } else {
                bossBar.removePlayer(p);
            }
        }
    }

    private void dismissSummons() {
        if (level() instanceof ServerLevel server) {
            for (UUID id : summons) {
                Entity e = server.getEntity(id);
                if (e != null) {
                    server.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.8, e.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                    e.discard();
                }
            }
        }
        summons.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            Telegraph.cancelAll(this);
            bossBar.removeAllPlayers();
            dismissSummons();
            playAction("death");
        }
    }

    // 歩哨の割符 (人ごと。持っていない人に一つ)、岩殻の護り糸 70%、骨片 2〜4
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        ResourceLocation tallyId = ResourceLocation.tryParse(Ch1Prep.SENTRY_TALLY);
        var tally = tallyId == null ? null : BuiltInRegistries.ITEM.get(tallyId);
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isSpectator() || p.distanceToSqr(this) > 40 * 40
                    || (groupId() != null && !groupId().equals(Groups.keyOf(p)))) {
                continue;
            }
            if (tally != null && p.getInventory().countItem(tally) == 0) {
                ItemStack stack = new ItemStack(tally);
                if (!p.getInventory().add(stack)) {
                    p.drop(stack, false);
                }
                Notice.narrate(p, "message.skyabyss.ch1.sentry.tally");
            }
        }
        if (random.nextFloat() < 0.7f) {
            spawnAtLocation(new ItemStack(GuideSetup.ROCK_SHELL_THREAD.get()));
        }
        spawnAtLocation(new ItemStack(GuideSetup.BONE_CHIP.get(), 2 + random.nextInt(3)));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.ELDER_SENTRY_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.ELDER_SENTRY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.ELDER_SENTRY_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.ELDER_SENTRY_STEP.get(), 0.4f);
    }
}
