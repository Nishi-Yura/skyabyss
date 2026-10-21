package jp.revati.skyabyss.titan.storm.chapter.mob;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.chapter.Ch3Mobs;
import jp.revati.skyabyss.titan.storm.mob.FeatherWarden;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

// 焼けた羽守 (docs/design/21 の 5.1。名づき): 焼けた見張り塔の頂の主。羽守の変種 (外套が焼け、兜と肩に青白い焦げ筋)。HP 190、鉤槍 10。
// 羽守の技 (鉤槍の薙ぎ・かがみ → 光る帯 → 突撃) は嵐の拍を待たずに使う (地上には背中の拍がない。diveAllowed)。足した技 (予告はどれも 1 秒以上):
//   雷の輪     近くの人がいると、外套を開いて身を沈め、足もとに半径 RING_RADIUS の輪 (RING_WARN 1.2 秒) → 輪の中の人に雷 6 (感電の傷 = lightning_bolt)
//   外套の跳躍 4.5〜10 先の人へ、外套を広げて上へ跳ぶ。落ちる先 (狙った人の足もと) に半径 LEAP_RADIUS の輪 (LEAP_TICKS 1.2 秒) → 輪の中の人に 8
//   柱の雷     ふだんは硬い (傷 GUARD_TAKEN 倍)。頂の柱の雷を受けると (ScorchedWatch.strike → struck)、EXPOSED_TICKS のあいだ傷がよく通る (EXPOSED_TAKEN 倍)
// 人数で体力が増える (番兵長・心臓・潮の番人と同じ式)。落とす物: 雷の護り糸 1 (50%)・銅線 2〜3
public class ScorchedWarden extends FeatherWarden {
    public static final float HEALTH = 190f;
    public static final int RING_WARN = 24;
    public static final double RING_RADIUS = 3.0;
    public static final double RING_TRIGGER = 3.4;
    public static final float RING_DAMAGE = 6f;
    public static final int RING_END = 34;
    public static final int RING_COOLDOWN = 110;
    public static final int LEAP_TICKS = 24;
    public static final double LEAP_RADIUS = 2.4;
    public static final double LEAP_HEIGHT = 5.0;
    public static final double LEAP_MIN = 4.5;
    public static final double LEAP_MAX = 10;
    public static final float LEAP_DAMAGE = 8f;
    public static final int LEAP_COOLDOWN = 160;
    public static final float GUARD_TAKEN = 0.5f;
    public static final float EXPOSED_TAKEN = 1.25f;
    public static final int EXPOSED_TICKS = 200;

    public static final int NONE = 0;
    public static final int RING = 1;
    public static final int LEAP = 2;

    private static final EntityDataAccessor<Integer> SKILL = SynchedEntityData.defineId(ScorchedWarden.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EXPOSED = SynchedEntityData.defineId(ScorchedWarden.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_6);
    private boolean scaled;
    private int skillTicks;
    private int ringCd = 60;
    private int leapCd = 100;
    private int exposed;
    private Vec3 leapFrom = Vec3.ZERO;
    private Vec3 leapTo = Vec3.ZERO;

    public ScorchedWarden(EntityType<? extends Monster> type, Level level) {
        super(type, level, "scorched_warden", "attack", "struck", "ring", "leap", "crouch", "dive", "stunned");
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FeatherWarden.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL, NONE);
        builder.define(EXPOSED, false);
    }

    public int skill() {
        return entityData.get(SKILL);
    }

    public boolean isExposed() {
        return entityData.get(EXPOSED);
    }

    public Vec3 leapTarget() {
        return leapTo;
    }

    // 地上には嵐の拍がないので、突撃はいつでも
    @Override
    protected boolean diveAllowed() {
        return true;
    }

    @Override
    protected boolean holding() {
        return skill() != NONE;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || skill() != NONE;
    }

    @Override
    protected boolean frozen() {
        return super.frozen() || skill() != NONE;
    }

    @Override
    protected String state() {
        return switch (skill()) {
            case RING -> "ring";
            case LEAP -> "leap";
            default -> super.state();
        };
    }

    @Override
    protected RawAnimation stateAnimation(String name) {
        return name.equals("ring") || name.equals("leap") ? hold(name) : super.stateAnimation(name);
    }

    private void setSkill(int s) {
        entityData.set(SKILL, s);
        skillTicks = 0;
    }

    // ---------------------------------------------------------------- 毎 tick
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (isExposed() && random.nextFloat() < 0.4f) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.05, 0);
            }
            return;
        }
        if (!scaled) {
            scaleToGroup();
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 10 == 0) {
            updateBossBarViewers();
        }
        if (exposed > 0 && --exposed == 0) {
            entityData.set(EXPOSED, false);
        }
        ringCd = Math.max(0, ringCd - 1);
        leapCd = Math.max(0, leapCd - 1);
        switch (skill()) {
            case RING -> {
                getNavigation().stop();
                if (++skillTicks >= RING_END) {
                    setSkill(NONE);
                }
            }
            case LEAP -> tickLeap();
            default -> pickSkill();
        }
    }

    private void pickSkill() {
        LivingEntity t = getTarget();
        if (state3() != State.IDLE || !fair(t) || !onGround()) {
            return;
        }
        double flat = Math.sqrt(t.distanceToSqr(getX(), t.getY(), getZ()));
        if (ringCd == 0 && flat <= RING_TRIGGER && Math.abs(t.getY() - getY()) < 2.5) {
            startRing();
        } else if (leapCd == 0 && flat >= LEAP_MIN && flat <= LEAP_MAX && Math.abs(t.getY() - getY()) < 3 && hasLineOfSight(t) && random.nextFloat() < 0.04f) {
            startLeap(t);
        }
    }

    // 雷の輪 (足もと。予告 1.2 秒)
    public void startRing() {
        setSkill(RING);
        ringCd = RING_COOLDOWN;
        getNavigation().stop();
        double cx = getX();
        double cz = getZ();
        Telegraph.in((ServerLevel) level(), TelegraphShape.circle(cx, cz, RING_RADIUS))
                .floor(getY()).warn(RING_WARN).danger().owner(this)
                .startSound(Ch3Mobs.WARDEN_RING.get(), 1.2f, 1.0f)
                .victims(p -> fair(p) && canAttack(p))
                .onImpact(server -> {
                    server.playSound(null, cx, getY(), cz, Ch3Mobs.WARDEN_RING_BURST.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
                    for (int i = 0; i < 20; i++) {
                        double a = Math.PI * 2 * i / 20;
                        warn(server, ParticleTypes.ELECTRIC_SPARK, cx + Math.cos(a) * RING_RADIUS, getY() + 0.3, cz + Math.sin(a) * RING_RADIUS, 2, 0.05, 0.2, 0.05, 0.05);
                    }
                })
                .onHit((server, player) -> player.hurt(server.damageSources().lightningBolt(), RING_DAMAGE))
                .start();
    }

    // 外套の跳躍 (落ちる先に輪。予告 1.2 秒)
    public void startLeap(LivingEntity t) {
        setSkill(LEAP);
        leapCd = LEAP_COOLDOWN;
        getNavigation().stop();
        leapFrom = position();
        leapTo = new Vec3(t.getX(), getY(), t.getZ());
        face(t, 180f);
        setNoGravity(true);
        playSound(Ch3Mobs.WARDEN_LEAP.get(), 1.2f, 1.0f);
        Vec3 at = leapTo;
        Telegraph.in((ServerLevel) level(), TelegraphShape.circle(at.x, at.z, LEAP_RADIUS))
                .floor(at.y).warn(LEAP_TICKS).danger().owner(this)
                .victims(p -> fair(p) && canAttack(p))
                .onImpact(server -> {
                    server.playSound(null, at.x, at.y, at.z, Ch3Mobs.WARDEN_LAND.get(), SoundSource.HOSTILE, 1.4f, 0.9f);
                    warn(server, ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 20, 0.8, 0.05, 0.8, 0.05);
                })
                .onHit((server, player) -> player.hurt(attackSource(), LEAP_DAMAGE))
                .start();
    }

    private void tickLeap() {
        skillTicks++;
        double k = Math.min(1.0, skillTicks / (double) LEAP_TICKS);
        Vec3 p = leapFrom.lerp(leapTo, k).add(0, LEAP_HEIGHT * 4 * k * (1 - k), 0);
        move(MoverType.SELF, p.subtract(position()));
        setDeltaMovement(Vec3.ZERO);
        if (skillTicks >= LEAP_TICKS) {
            setNoGravity(false);
            setSkill(NONE);
        }
    }

    // 頂の柱の雷を受けた (ScorchedWatch.strike が傷の前に呼ぶ)。しばらく傷がよく通る
    public void struck() {
        exposed = EXPOSED_TICKS;
        entityData.set(EXPOSED, true);
        if (!level().isClientSide) {
            playAction("struck");
            playSound(Ch3Mobs.WARDEN_STRUCK.get(), 1.4f, 1.0f);
            if (skill() == LEAP) {
                setNoGravity(false);
            }
        }
    }

    // 受ける傷の倍率 (純粋な部分): 柱の雷を受けたあとは EXPOSED_TAKEN、ふだんは GUARD_TAKEN
    public static float taken(boolean exposed) {
        return exposed ? EXPOSED_TAKEN : GUARD_TAKEN;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= taken(isExposed());
        }
        return super.hurt(source, amount);
    }

    // ---------------------------------------------------------------- 人数・ボスの帯
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

    @Override
    public void remove(Entity.RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            Telegraph.cancelAll(this);
            bossBar.removeAllPlayers();
            setNoGravity(false);
        }
    }

    // 雷の護り糸 50%、銅線 2〜3
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Item thread = Ch3Mobs.item(Ch3Mobs.THUNDER_WARD_THREAD);
        Item wire = Ch3Mobs.item(Ch3Mobs.COPPER_WIRE);
        if (thread != null && random.nextFloat() < 0.5f) {
            spawnAtLocation(new ItemStack(thread));
        }
        if (wire != null) {
            spawnAtLocation(new ItemStack(wire, 2 + random.nextInt(2)));
        }
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return Ch3Mobs.WARDEN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Ch3Mobs.WARDEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Ch3Mobs.WARDEN_DEATH.get();
    }
}
