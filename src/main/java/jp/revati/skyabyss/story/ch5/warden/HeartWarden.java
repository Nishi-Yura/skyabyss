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
    // 悪夢の決まり (NightmareTier): 爪の二度目・踏み込みの二度目の間、闇の回復の待ち、心臓の輪
    static final int CLAW_AGAIN_TICKS = 20;
    static final int LUNGE_AGAIN_TICKS = 16;
    static final int REGEN_QUIET_TICKS = 120;
    static final double RING_RADIUS = 5;
    static final int RING_WARN = 40;
    static final int RING_EVERY = 240;

    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.skyabyss.heart_warden"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int phase = 1;
    private int cooldown = 60;
    private int snuffing = -1;
    private int snuffTicks;
    private float snuffDamage;
    private boolean waveDone;
    private boolean waveTwoDone;
    private int life;
    // 悪夢の段 (普通の戦いは NORMAL)。数字と技の決まりはここから読む
    private NightmareTier tier = NightmareTier.NORMAL;
    private int clawAgain;
    private int lungeAgain;
    private int lastHitAt;

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

    public NightmareTier tier() {
        return tier;
    }

    // 段は出す前に決める (WardenArena.wake)
    public void setTier(NightmareTier t) {
        tier = t;
    }

    // 人数で体力を合わせる (出たときに一度)。段の体力の倍率を先にかける (普通の戦いは 1 倍)
    public void scaleFor(int players) {
        double hp = BASE_HP * tier.hp() * (1 + 0.6 * Math.max(0, players - 1));
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
        return hasEffect(MobEffects.GLOWING) ? OUTLINE_SHARE : (float) tier.darkShare();
    }

    // 爪・踏み込みの傷 (闇の中の怒り込み)。普通の戦いは今までと同じ数
    private float dmg() {
        return (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * fury() * tier.dmg());
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
        lastHitAt = life;
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
            // 段 6 以降 (影の写しが本物の技を使う): 技は本物と同じ爪・踏み込み。吹き消しはしない
            if (tier.shadesStrike() && --cooldown <= 0) {
                ServerPlayer t = target(level);
                if (t != null) {
                    strike(level, t);
                } else {
                    cooldown = 40;
                }
            }
            return;
        }
        // 鼓動の音 (位置が分かる)
        if (life % 40 == 0) {
            level.playSound(null, at, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.5f, 0.55f);
        }
        updateBar(level);
        updatePhase(level);
        objectives(level);
        tickRegen();
        tickRing(level);
        // 段二から: 重さの落ちる所 (塩の層の主と同じ。一人ずつの足もとに赤い輪。重さの術の中なら軽い)
        if (phase >= 2 && snuffing < 0 && life % 240 == 120) {
            jp.revati.skyabyss.story.ch5.layers.AbyssLords.crush(level, java.util.List.copyOf(bar.getPlayers()), this, 7f);
            crushUntil = life + 70;
        }
        if (snuffing >= 0) {
            tickSnuff(level);
            return;
        }
        chase(level);
        tickFollow(level);
        if (--cooldown <= 0) {
            act(level);
        }
    }

    // 闇の中で、六秒攻撃を受けないと毎秒 0.5% 戻る (段 6 以降)
    private void tickRegen() {
        if (!tier.darkRegen() || lit() || life % 20 != 0 || life - lastHitAt < REGEN_QUIET_TICKS) {
            return;
        }
        heal((float) (getMaxHealth() * 0.005));
    }

    // 心臓の輪 (段 10): 台のまわりに赤い輪を予告してから当てる。台の上に立つ人は、輪から出る
    private void tickRing(ServerLevel level) {
        if (!tier.heartRing() || life % RING_EVERY != RING_EVERY / 2) {
            return;
        }
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart == null) {
            return;
        }
        double d = dmg();
        Telegraph.in(level, TelegraphShape.circle(heart.getX() + 0.5, heart.getZ() + 0.5, RING_RADIUS)).floor(heart.getY()).warn(RING_WARN).danger().owner(this)
                .onHit((lvl, p) -> p.hurt(damageSources().mobAttack(this), (float) d)).start();
    }

    // 爪・踏み込みの二度目 (段 2 と 7)。待ちの数えは tick ごと。二度目は、そのときの相手に向けて出す
    private void tickFollow(ServerLevel level) {
        if (clawAgain > 0 && --clawAgain == 0) {
            ServerPlayer t = target(level);
            if (t != null) {
                claw(level, t);
            }
        }
        if (lungeAgain > 0 && --lungeAgain == 0) {
            ServerPlayer t = target(level);
            if (t != null) {
                lunge(level, t);
            }
        }
    }

    // 仮の見た目: 光の中では煙と魂の粒で体の形、輪郭だけ (鼓動の術) のときは薄く
    // 添えの粒: 体はモデル (client/WardenRenderer) が描くので、光の中では体から立つ魂の粒が少し、輪郭だけのときはもっと少なく
    private void outline(ServerLevel level, boolean l) {
        boolean glow = hasEffect(MobEffects.GLOWING);
        if (!l && !glow) {
            return;
        }
        if (life % (l ? 6 : 12) != 0) {
            return;
        }
        double h = getBbHeight();
        double a = random.nextDouble() * Math.PI * 2;
        double r = getBbWidth() * 0.45;
        level.sendParticles(ParticleTypes.SOUL, getX() + Math.cos(a) * r, getY() + h * (0.35 + random.nextDouble() * 0.4), getZ() + Math.sin(a) * r, 1, 0, 0.03, 0, 0);
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
                for (int k = 0; k < 1; k++) {
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
                wave(level, "message.skyabyss.warden.wave");
            }
        }
        // 段 5 以降: 闇の波がもう一度 (体力 12% で)
        if (phase == 3 && tier.waves() >= 2 && !waveTwoDone && getHealth() / getMaxHealth() <= 0.12f) {
            waveTwoDone = true;
            wave(level, "message.skyabyss.abyss_nm.wave_again");
        }
    }

    // 闇の波: 全部の灯籠と光の術の灯りが消える (鐘の音つき)
    private void wave(ServerLevel level, String key) {
        say(level, key);
        waveAt = life;
        BlockPos heart = AbyssBuild.mark("heart");
        for (int k = 0; k < 3; k++) {
            level.playSound(null, heart == null ? blockPosition() : heart, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3f, 0.4f);
        }
        AbyssBeacons.allOut(level);
        AbyssLight.clear();
    }

    // 目的の帯の一言 (層の主たちが教えた決まりを、いま使う所で。story/ch5/layers/AbyssSections)
    private int waveAt = -100000;
    private int crushUntil;

    private void objectives(ServerLevel level) {
        if (life % 10 != 0) {
            return;
        }
        String key;
        if (snuffing >= 0) {
            key = "warden_snuff";
        } else if (life - waveAt < 300) {
            key = "warden_p3";
        } else if (crushUntil > life) {
            key = "warden_crush";
        } else {
            key = phase == 1 ? "warden_p1" : phase == 2 ? "warden_p2" : "warden_p3";
        }
        for (ServerPlayer p : bar.getPlayers()) {
            jp.revati.skyabyss.story.ch5.layers.AbyssSections.objective(p, "objective.skyabyss.abyss_v2." + key, 40);
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
        for (ServerPlayer p : java.util.List.copyOf(level.players())) {
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
                Telegraph.in(level, TelegraphShape.circle(b.getX() + 0.5, b.getZ() + 0.5, 2.5)).floor(b.getY()).warn(tier.snuffTicks()).harmless().owner(this).start();
                return;
            }
        }
        strike(level, t);
    }

    // 爪か踏み込みを一つ出す (本物も写しも同じ)。近ければ爪、遠ければ踏み込み
    private void strike(ServerLevel level, ServerPlayer t) {
        double dx = t.getX() - getX();
        double dz = t.getZ() - getZ();
        if (dx * dx + dz * dz < 6 * 6) {
            claw(level, t);
            // 段 2 以降: 爪のあと、同じ扇がもう一度 (本物だけ)
            if (!decoy() && tier.clawTwice()) {
                clawAgain = CLAW_AGAIN_TICKS;
            }
            cooldown = 40;
        } else {
            lunge(level, t);
            // 段 7 以降: 踏み込みのあと、少し間を置いて二度目 (本物だけ)
            if (!decoy() && tier.lungeFollow()) {
                lungeAgain = LUNGE_AGAIN_TICKS;
            }
            cooldown = 55;
        }
        cooldown = Math.max(20, cooldown - (phase - 1) * 8);
    }

    // 爪: 前の扇
    private void claw(ServerLevel level, ServerPlayer t) {
        double ang = Telegraph.angleOf(t.getX() - getX(), t.getZ() - getZ());
        double dmg = dmg();
        Telegraph.in(level, TelegraphShape.cone(getX(), getZ(), ang, CLAW_RANGE, Math.toRadians(50))).floor(getY()).warn(CLAW_WARN).danger().owner(this)
                .onHit((lvl, p) -> p.hurt(damageSources().mobAttack(this), (float) dmg)).start();
        playAction("claw");
    }

    // 踏み込み: 直線。予告が終わると線の先へ跳ぶ
    private void lunge(ServerLevel level, ServerPlayer t) {
        double dx = t.getX() - getX();
        double dz = t.getZ() - getZ();
        double ang = Telegraph.angleOf(dx, dz);
        double dmg = dmg();
        double len = Math.min(LUNGE_LENGTH, Math.sqrt(dx * dx + dz * dz) + 2);
        double ux = dx / Math.sqrt(dx * dx + dz * dz);
        double uz = dz / Math.sqrt(dx * dx + dz * dz);
        Vec3 end = new Vec3(getX() + ux * (len - 1), getY(), getZ() + uz * (len - 1));
        Telegraph.in(level, TelegraphShape.line(getX(), getZ(), ang, len, 1.0)).floor(getY()).warn(LUNGE_WARN).danger().owner(this)
                .onImpact(lvl -> teleportTo(end.x, end.y, end.z))
                .onHit((lvl, p) -> p.hurt(damageSources().mobAttack(this), (float) (dmg * 1.2))).start();
        playAction("lunge");
    }

    private void tickSnuff(ServerLevel level) {
        snuffTicks++;
        if (snuffDamage >= SNUFF_BREAK) {
            snuffing = -1;
            cooldown = 50;
            return;
        }
        if (snuffTicks >= tier.snuffTicks()) {
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
