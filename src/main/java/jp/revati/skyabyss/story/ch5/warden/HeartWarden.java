package jp.revati.skyabyss.story.ch5.warden;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 心臓の番人 (docs/design/36 の 6)。心臓の間で、点いた灯籠の光の中と光の術の灯りの中でだけ姿が見える。
//   見え方   LIT (同期): 灯籠の 7 以内か、光の術の灯りの中。闇の中では見えない (描かない)。鼓動の術の輪郭 (光る効き目) は見える
//   傷       光の中 100%・輪郭だけ 60%・闇 25%
//   段       体力 60% で二段目 (影の写し二つ・灯籠の持ち 30 秒)、30% で三段目 (一度だけ闇の波・灯籠の持ち 20 秒)
//   技       爪 (前の扇 r4.5、予告 18)・踏み込み (直線 10×2、予告 22)・吹き消し (灯籠へ寄り 60 tick、輪の予告。傷 40 で止まる)。灯籠が全部消えると傷 ×1.25
//   体力     人数で増える (1 人 600、1 人ごとに +60%)
//   見た目   案 B (肋の獣) の本番モデル (client/WardenRenderer)。動きは playAction で全員に流す (claw・lunge・snuff・phase・hurt・death)
public class HeartWarden extends Monster implements GeoEntity {
    static final String ANIM = "animation.heart_warden.";
    static final String[] ACTIONS = {"claw", "lunge", "snuff", "phase", "hurt", "death"};
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    static final EntityDataAccessor<Boolean> LIT = SynchedEntityData.defineId(HeartWarden.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DECOY = SynchedEntityData.defineId(HeartWarden.class, EntityDataSerializers.BOOLEAN);
    static final double BASE_HP = 600;
    static final float DARK_SHARE = 0.25f;
    static final float OUTLINE_SHARE = 0.6f;
    static final float DARK_FURY = 1.25f;
    static final double CLAW_RANGE = 4.5;
    static final int CLAW_WARN = 18;
    static final double LUNGE_LENGTH = 10;
    static final int LUNGE_WARN = 22;
    static final int SNUFF_TICKS = 60;
    static final float SNUFF_BREAK = 40;

    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.skyabyss.heart_warden"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int phase = 1;
    private int cooldown = 60;
    private int snuffing = -1;
    private int snuffTicks;
    private float snuffDamage;
    private boolean waveDone;
    private int life;

    public HeartWarden(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HP)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LIT, false);
        builder.define(DECOY, false);
    }

    public boolean lit() {
        return entityData.get(LIT);
    }

    public boolean decoy() {
        return entityData.get(DECOY);
    }

    public void makeDecoy() {
        entityData.set(DECOY, true);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(1);
        setHealth(1);
    }

    // 人数で体力を合わせる (出たときに一度)
    public void scaleFor(int players) {
        double hp = BASE_HP * (1 + 0.6 * Math.max(0, players - 1));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        setHealth((float) hp);
    }

    public int phase() {
        return phase;
    }

    // 傷の受け方: 光の中 100%・輪郭 60%・闇 25%
    public float share() {
        if (lit()) {
            return 1f;
        }
        return hasEffect(MobEffects.GLOWING) ? OUTLINE_SHARE : DARK_SHARE;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (decoy()) {
            if (!level().isClientSide) {
                discard();
            }
            return true;
        }
        float a = amount * share();
        if (snuffing >= 0) {
            snuffDamage += a;
        }
        boolean hit = super.hurt(source, a);
        if (hit && isAlive() && !level().isClientSide) {
            playAction("hurt");
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        life++;
        BlockPos at = blockPosition();
        boolean l = AbyssBeacons.litNear(at) || AbyssLight.lampAt(level, at) > 0;
        if (l != lit()) {
            entityData.set(LIT, l);
        }
        outline(level, l);
        if (decoy()) {
            if (life > 30 * 20) {
                discard();
            }
            chase(level);
            return;
        }
        // 鼓動の音 (位置が分かる)
        if (life % 40 == 0) {
            level.playSound(null, at, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.5f, 0.55f);
        }
        updateBar(level);
        updatePhase(level);
        if (snuffing >= 0) {
            tickSnuff(level);
            return;
        }
        chase(level);
        if (--cooldown <= 0) {
            act(level);
        }
    }

    // 仮の見た目: 光の中では煙と魂の粒で体の形、輪郭だけ (鼓動の術) のときは薄く
    private void outline(ServerLevel level, boolean l) {
        boolean glow = hasEffect(MobEffects.GLOWING);
        if (!l && !glow) {
            return;
        }
        int n = l ? 10 : 3;
        double w = getBbWidth() * 0.5;
        double h = getBbHeight();
        for (int k = 0; k < n; k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double y = getY() + random.nextDouble() * h;
            double r = w * (y - getY() > h * 0.75 ? 0.6 : 1.0);
            level.sendParticles(k % 3 == 0 ? ParticleTypes.SOUL : ParticleTypes.LARGE_SMOKE, getX() + Math.cos(a) * r, y, getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
        }
        if (l && life % 4 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() - Math.sin(Math.toRadians(getYRot())) * 0.4, getY() + h * 0.85, getZ() + Math.cos(Math.toRadians(getYRot())) * 0.4, 2, 0.15, 0.05, 0.15, 0);
        }
    }

    private void updateBar(ServerLevel level) {
        bar.setProgress(getHealth() / getMaxHealth());
        List<ServerPlayer> in = WardenArena.inside(level);
        for (ServerPlayer p : in) {
            if (!bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            }
        }
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
            if (!in.contains(p)) {
                bar.removePlayer(p);
            }
        }
    }

    void updatePhase(ServerLevel level) {
        float r = getHealth() / getMaxHealth();
        int want = r > 0.6f ? 1 : r > 0.3f ? 2 : 3;
        if (want > phase) {
            phase = want;
            AbyssBeacons.phase = phase;
            playAction("phase");
            if (phase == 2) {
                for (int k = 0; k < 2; k++) {
                    HeartWarden d = new HeartWarden(WardenSetup.HEART_WARDEN.get(), level);
                    d.makeDecoy();
                    Vec3 off = new Vec3(random.nextGaussian() * 6, 0, random.nextGaussian() * 6);
                    d.moveTo(getX() + off.x, getY(), getZ() + off.z, getYRot(), 0f);
                    level.addFreshEntity(d);
                }
                say(level, "message.skyabyss.warden.shades");
            }
            if (phase == 3 && !waveDone) {
                waveDone = true;
                say(level, "message.skyabyss.warden.wave");
                BlockPos heart = AbyssBuild.mark("heart");
                for (int k = 0; k < 3; k++) {
                    level.playSound(null, heart == null ? blockPosition() : heart, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3f, 0.4f);
                }
                AbyssBeacons.allOut(level);
                AbyssLight.clear();
            }
        }
    }

    private void say(ServerLevel level, String key) {
        for (ServerPlayer p : bar.getPlayers()) {
            Notice.narrate(p, key);
        }
    }

    @Nullable
    private ServerPlayer target(ServerLevel level) {
        ServerPlayer best = null;
        double bd = 40 * 40;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) {
                continue;
            }
            double d = p.distanceToSqr(this);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void chase(ServerLevel level) {
        ServerPlayer t = target(level);
        if (t != null && life % 10 == 0) {
            getNavigation().moveTo(t, 1.0);
        }
    }

    private float fury() {
        return AbyssBeacons.litCount() == 0 ? DARK_FURY : 1f;
    }

    private void act(ServerLevel level) {
        ServerPlayer t = target(level);
        if (t == null) {
            cooldown = 40;
            return;
        }
        // 灯籠が点いていれば、ときどき吹き消しに行く
        if (AbyssBeacons.litCount() > 0 && random.nextInt(3) == 0) {
            int i = AbyssBeacons.nearest(blockPosition(), true);
            BlockPos b = AbyssBeacons.pos(i);
            if (b != null) {
                snuffing = i;
                snuffTicks = 0;
                snuffDamage = 0;
                playAction("snuff");
                getNavigation().moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 1.2);
                Telegraph.in(level, TelegraphShape.circle(b.getX() + 0.5, b.getZ() + 0.5, 2.5)).floor(b.getY()).warn(SNUFF_TICKS).harmless().owner(this).start();
                return;
            }
        }
        double floor = getY();
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * fury();
        double dx = t.getX() - getX();
        double dz = t.getZ() - getZ();
        double ang = Telegraph.angleOf(dx, dz);
        if (dx * dx + dz * dz < 6 * 6) {
            // 爪: 前の扇
            Telegraph.in(level, TelegraphShape.cone(getX(), getZ(), ang, CLAW_RANGE, Math.toRadians(50))).floor(floor).warn(CLAW_WARN).danger().owner(this)
                    .onHit((lvl, p) -> p.hurt(damageSources().mobAttack(this), dmg)).start();
            playAction("claw");
            cooldown = 40;
        } else {
            // 踏み込み: 直線。予告が終わると線の先へ跳ぶ
            double len = Math.min(LUNGE_LENGTH, Math.sqrt(dx * dx + dz * dz) + 2);
            double ux = dx / Math.sqrt(dx * dx + dz * dz);
            double uz = dz / Math.sqrt(dx * dx + dz * dz);
            Vec3 end = new Vec3(getX() + ux * (len - 1), getY(), getZ() + uz * (len - 1));
            Telegraph.in(level, TelegraphShape.line(getX(), getZ(), ang, len, 1.0)).floor(floor).warn(LUNGE_WARN).danger().owner(this)
                    .onImpact(lvl -> teleportTo(end.x, end.y, end.z))
                    .onHit((lvl, p) -> p.hurt(damageSources().mobAttack(this), dmg * 1.2f)).start();
            playAction("lunge");
            cooldown = 55;
        }
        cooldown = Math.max(20, cooldown - (phase - 1) * 8);
    }

    private void tickSnuff(ServerLevel level) {
        snuffTicks++;
        if (snuffDamage >= SNUFF_BREAK) {
            snuffing = -1;
            cooldown = 50;
            return;
        }
        if (snuffTicks >= SNUFF_TICKS) {
            AbyssBeacons.snuff(level, snuffing);
            snuffing = -1;
            cooldown = 30;
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && !decoy()) {
            playAction("death");
            say(level, "message.skyabyss.warden.fall");
            WardenArena.fallen(level, this);
            bar.removeAllPlayers();
            AbyssBeacons.reset(level);
            for (HeartWarden w : level.getEntitiesOfClass(HeartWarden.class, getBoundingBox().inflate(48), HeartWarden::decoy)) {
                w.discard();
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bar.removeAllPlayers();
        super.remove(reason);
    }

    // 動き: 歩きと待機は自動で切り替え、技と被弾・死は playAction でサーバーから流す (全員の画面に同期される)
    public void playAction(String name) {
        triggerAnim("action", name);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        AnimationController<HeartWarden> action = new AnimationController<>(this, "action", 0, state -> PlayState.STOP);
        for (String name : ACTIONS) {
            String id = ANIM + name;
            // 死の姿は最後の姿で止める (消えるまで元に戻らない)
            action.triggerableAnim(name, name.equals("death") ? RawAnimation.begin().thenPlayAndHold(id) : RawAnimation.begin().thenPlay(id));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
