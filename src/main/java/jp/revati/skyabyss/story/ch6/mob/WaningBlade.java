package jp.revati.skyabyss.story.ch6.mob;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Iterator;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.ch6.field.FallingLight;
import jp.revati.skyabyss.story.ch6.rift.MoonRift;
import jp.revati.skyabyss.story.ch6.rift.RiftArena;
import jp.revati.skyabyss.story.ch6.valley.StarLamps;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 残月の剣守 (下弦の淵の主。docs/design/46 の 4.6・一品一案「下弦の剣士」)。淵の真ん中の柱の上の主の間で戦う (RiftArena が呼び、片付ける)。
//   主の残像   主の 3 秒後ろを、同じ模型の残像 (このクラスの echo) がついてくる。主が毎 tick 書く「いた所・向き・始めた技・当てた技」を、
//              残像は 3 秒遅れで読んでなぞり、同じ技を同じ所から出す。残像は傷を受けない。星灯りの中に入った残像は消え、6 秒後に戻る
//   崩し       残像の技が主に当たると、主は崩れて 5 秒膝をつく (傷 ×1.6)。一瞬止めは主を 2 秒止めるが残像は止まらない
//   技         三日月の薙ぎ (前 150 度・4.5、予告 0.8 秒)・踏み込みの突き (線の予告 1 秒、前へ 5 跳んで線の上 7)・飛ぶ三日月の刃 (遠くの人へ)
//   段         体力 66% と 33% (咆哮 2 秒は傷が通らない)。段 2: 残像が二つ (3 秒と 6 秒)。段 3: 床の東の外周が崩れ、人の 3 秒前を人の残像がなぞる
public class WaningBlade extends MoonMob {
    public static final String ID = "waning_blade";
    public static final double HP = 850;
    public static final double DMG = 16;
    public static final int DELAY = 60;
    static final int ROAR_TICKS = 40;
    static final int STAGGER_TICKS = 100;
    static final int SLASH_TELL = 16;
    static final int THRUST_TELL = 20;
    static final int WAVE_TELL = 14;
    static final int LIGHT_GONE = 120;
    private static final EntityDataAccessor<Boolean> ECHO = SynchedEntityData.defineId(WaningBlade.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(WaningBlade.class, EntityDataSerializers.BOOLEAN);
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.waning_blade"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10));

    // 主が書く一 tick の記録。anim = その tick に始めた技の動き、hit = その tick に当てた技
    public record Frame(long time, Vec3 pos, float yaw, @Nullable String anim, @Nullable String hit) {
    }

    private final ArrayDeque<Frame> history = new ArrayDeque<>();
    @Nullable
    private String pendingAnim;
    @Nullable
    private String pendingHit;
    private int phase;
    private int roarFor;
    private int staggerFor;
    private boolean toldStagger;
    // 残像の側
    private int masterId = -1;
    private int delay = DELAY;
    private int hiddenFor;
    private long lastRead = Long.MIN_VALUE;

    public WaningBlade(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "slash", "thrust", "wave", "kneel", "roar", "hurt", "death");
        this.xpReward = 160;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 60);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ECHO, false);
        builder.define(HIDDEN, false);
    }

    public boolean echo() {
        return entityData.get(ECHO);
    }

    // 星灯りで消えている残像
    public boolean hiddenEcho() {
        return entityData.get(HIDDEN);
    }

    public int phase() {
        return phase;
    }

    public boolean staggered() {
        return staggerFor > 0;
    }

    public int delay() {
        return delay;
    }

    // 主の残像を作る (master の 3 秒 / 6 秒後ろ)
    public static WaningBlade spawnEcho(ServerLevel level, WaningBlade master, int delay) {
        WaningBlade e = MoonMobsSetup.WANING_BLADE.get().create(level);
        if (e == null) {
            return null;
        }
        e.entityData.set(ECHO, true);
        e.masterId = master.getId();
        e.delay = delay;
        e.setNoAi(true);
        e.setNoGravity(true);
        e.noPhysics = true;
        e.setInvulnerable(true);
        e.addTag(RiftArena.TAG);
        e.addTag(jp.revati.skyabyss.rpg.GrowthRewards.NO_REWARD);
        e.copyTune(master);
        e.moveTo(master.getX(), master.getY(), master.getZ(), master.getYRot(), 0f);
        level.addFreshEntity(e);
        return e;
    }

    private void copyTune(WaningBlade master) {
        tuneMoon(3, false, false, HP / SECTION_HP[3], master.dmg() / SECTION_DMG[3]);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !echo() && super.isPushable();
    }

    @Override
    public boolean isPickable() {
        return !echo() && super.isPickable();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Fight());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!echo()) {
            bar.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    // 主が技を始めた / 当てた (残像が 3 秒後になぞる)
    void began(String anim) {
        playAction(anim);
        pendingAnim = anim;
    }

    void landed(String hit) {
        pendingHit = hit;
    }

    // ---------------------------------------------------------------- 崩れる (残像の技が当たった)
    public void stagger() {
        if (echo() || staggerFor > 0 || roarFor > 0 || !(level() instanceof ServerLevel sl)) {
            return;
        }
        staggerFor = STAGGER_TICKS;
        playAction("kneel");
        playSound(MoonMobsSetup.BLADE_STAGGER.get(), 2.0f, 0.8f);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 40, 0.8, 1.2, 0.8, 0.12);
        if (!toldStagger) {
            toldStagger = true;
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24), e -> e.isAlive())) {
                Notice.narrate(p, "message.skyabyss.moon.blade_stagger");
            }
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        if (echo()) {
            tickEcho(sl);
            return;
        }
        history.addLast(new Frame(sl.getGameTime(), position(), getYRot(), pendingAnim, pendingHit));
        pendingAnim = null;
        pendingHit = null;
        while (history.size() > DELAY * 2 + 40) {
            history.pollFirst();
        }
        bar.setProgress(getHealth() / getMaxHealth());
        if (roarFor > 0 && --roarFor == 0) {
            AbyssBossBars.roar(bar, false);
        }
        if (staggerFor > 0) {
            staggerFor--;
            getNavigation().stop();
        }
        float f = getHealth() / getMaxHealth();
        int want = f <= 0.33f ? 2 : f <= 0.66f ? 1 : 0;
        if (want > phase && isAlive()) {
            phase = want;
            AbyssBossBars.phase(bar, phase);
            AbyssBossBars.roar(bar, true);
            roarFor = ROAR_TICKS;
            staggerFor = 0;
            began("roar");
            playSound(MoonMobsSetup.BLADE_ROAR.get(), 2.0f, 0.8f);
            if (phase == 1) {
                spawnEcho(sl, this, DELAY * 2);
            } else if (phase == 2) {
                RiftArena.waning(sl, this);
            }
        }
    }

    // 主の記録の、time 以前で一番新しいもの
    @Nullable
    public Frame frameAt(long time) {
        Frame best = null;
        for (Iterator<Frame> it = history.descendingIterator(); it.hasNext();) {
            Frame fr = it.next();
            if (fr.time() <= time) {
                best = fr;
                break;
            }
        }
        return best;
    }

    private void tickEcho(ServerLevel sl) {
        if (!(sl.getEntity(masterId) instanceof WaningBlade master) || !master.isAlive()) {
            discard();
            return;
        }
        long now = sl.getGameTime() - delay;
        if (hiddenFor > 0) {
            if (--hiddenFor == 0) {
                entityData.set(HIDDEN, false);
                setInvisible(false);
            }
        }
        // 主の記録を読む (読み飛ばした tick の技も拾う)
        for (Frame fr : master.history) {
            if (fr.time() <= lastRead || fr.time() > now) {
                continue;
            }
            lastRead = fr.time();
            moveTo(fr.pos().x, fr.pos().y, fr.pos().z, fr.yaw(), 0f);
            setYHeadRot(fr.yaw());
            setYBodyRot(fr.yaw());
            if (hiddenFor > 0) {
                continue;
            }
            if (fr.anim() != null && !fr.anim().equals("roar") && !fr.anim().equals("kneel")) {
                playAction(fr.anim());
                tell(sl, fr.anim());
            }
            if (fr.hit() != null) {
                strikeAs(sl, fr.hit(), master);
            }
        }
        if (hiddenFor == 0 && StarLamps.inLight(sl, position().add(0, 1, 0))) {
            hiddenFor = LIGHT_GONE;
            entityData.set(HIDDEN, true);
            setInvisible(true);
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 30, 0.5, 1.2, 0.5, 0.05);
            playSound(MoonMobsSetup.BLADE_ECHO_FADE.get(), 1.5f, 1.0f);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (echo()) {
            return false;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (roarFor > 0) {
            return false;
        }
        return super.hurt(source, amount * (staggerFor > 0 ? 1.6f : 1.0f));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bar.removeAllPlayers();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bar.removeAllPlayers();
    }

    // ---------------------------------------------------------------- 技の予告と当たり (主も残像も同じ形)
    void tell(ServerLevel sl, String move) {
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        switch (move) {
            case "slash" -> {
                for (int i = -5; i <= 5; i++) {
                    Vec3 d = fwd.yRot((float) Math.toRadians(i * 15));
                    Vec3 p = position().add(d.scale(4.5));
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.15, p.z, 2, 0.05, 0, 0.05, 0);
                }
            }
            case "thrust" -> {
                for (int i = 1; i <= 12; i++) {
                    Vec3 p = position().add(fwd.scale(i));
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.15, p.z, 1, 0, 0, 0, 0);
                }
            }
            default -> {
            }
        }
        playSound(MoonMobsSetup.BLADE_TELL.get(), 1.2f, echo() ? 0.8f : 1.0f);
    }

    // 技を当てる。残像の技は主も打つ (当たると主が崩れる)
    public void strikeAs(ServerLevel sl, String move, @Nullable WaningBlade master) {
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        switch (move) {
            case "slash" -> {
                playSound(MoonMobsSetup.BLADE_SLASH.get(), 1.6f, echo() ? 0.8f : 1.0f);
                for (int i = -5; i <= 5; i++) {
                    Vec3 d = fwd.yRot((float) Math.toRadians(i * 15));
                    Vec3 p = position().add(d.scale(3.0)).add(0, 1.2, 0);
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.0), e -> e.isAlive() && e != this)) {
                    if (!inArc(e.position(), fwd, 4.8, -0.26)) {
                        continue;
                    }
                    hitOne(e, master, 1.1);
                }
            }
            case "thrust" -> {
                playSound(MoonMobsSetup.BLADE_THRUST.get(), 1.6f, echo() ? 0.8f : 1.0f);
                Vec3 a = position().subtract(fwd.scale(5.0));
                Vec3 b = position().add(fwd.scale(2.5));
                for (int i = 0; i <= 10; i++) {
                    Vec3 p = a.add(b.subtract(a).scale(i / 10.0)).add(0, 1.2, 0);
                    sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8.0), e -> e.isAlive() && e != this)) {
                    if (distToSegment(e.position(), a, b) <= 1.3) {
                        hitOne(e, master, 1.25);
                    }
                }
            }
            case "wave" -> {
                Vec3 from = position().add(0, 1.6, 0).add(fwd.scale(1.2));
                FallingLight.shootBlade(sl, this, from, fwd, 0.7, dmg(0.9));
                playSound(MoonMobsSetup.BLADE_WAVE.get(), 1.4f, echo() ? 0.8f : 1.0f);
            }
            default -> {
            }
        }
    }

    private void hitOne(LivingEntity e, @Nullable WaningBlade master, double factor) {
        if (e instanceof Player p && !p.isSpectator()) {
            strike(p, dmg(factor));
        } else if (echo() && e == master) {
            master.stagger();
        }
    }

    boolean inArc(Vec3 p, Vec3 fwd, double r, double minDot) {
        Vec3 to = p.subtract(position());
        double d = to.horizontalDistance();
        return d <= r && Math.abs(to.y) < 3 && (d < 1.2 || new Vec3(to.x, 0, to.z).normalize().dot(fwd) > minDot);
    }

    static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = new Vec3(b.x - a.x, 0, b.z - a.z);
        Vec3 ap = new Vec3(p.x - a.x, 0, p.z - a.z);
        double t = Math.max(0, Math.min(1, ap.dot(ab) / Math.max(1e-6, ab.lengthSqr())));
        return ap.subtract(ab.scale(t)).length() + (Math.abs(p.y - a.y) > 3 ? 99 : 0);
    }

    // ---------------------------------------------------------------- 戦い方 (主だけ。一度に一つの技。技の間に休み)
    final class Fight extends Goal {
        private String move = "";
        private int t;
        private int rest = 40;
        private Vec3 lungeTo = Vec3.ZERO;

        Fight() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity tg = getTarget();
            return !echo() && tg != null && tg.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity tg = getTarget();
            if (tg == null || !(level() instanceof ServerLevel sl)) {
                return;
            }
            if (roarFor > 0 || staggerFor > 0) {
                getNavigation().stop();
                move = "";
                return;
            }
            if (move.isEmpty()) {
                getLookControl().setLookAt(tg, 30f, 30f);
                if (rest > 0) {
                    rest--;
                    approach(tg);
                    return;
                }
                double d = distanceTo(tg);
                begin(d <= 4.5 ? "slash" : d <= 9 ? "thrust" : "wave", tg);
                return;
            }
            t++;
            switch (move) {
                case "slash" -> {
                    if (t == SLASH_TELL) {
                        strikeAs(sl, "slash", null);
                        landed("slash");
                    }
                    if (t >= SLASH_TELL + 12) {
                        end(28);
                    }
                }
                case "thrust" -> {
                    if (t > THRUST_TELL && t <= THRUST_TELL + 4) {
                        Vec3 step = lungeTo.subtract(position()).scale(1.0 / (THRUST_TELL + 5 - t));
                        moveTo(getX() + step.x, getY(), getZ() + step.z, getYRot(), getXRot());
                    }
                    if (t == THRUST_TELL + 4) {
                        strikeAs(sl, "thrust", null);
                        landed("thrust");
                    }
                    if (t >= THRUST_TELL + 16) {
                        end(30);
                    }
                }
                case "wave" -> {
                    if (t == WAVE_TELL) {
                        strikeAs(sl, "wave", null);
                        landed("wave");
                    }
                    if (t >= WAVE_TELL + 14) {
                        end(36);
                    }
                }
                default -> end(20);
            }
        }

        // 主の間の中で、獲物から 3 ほどを保つ
        private void approach(LivingEntity tg) {
            Vec3 c = MoonRift.mark("arena_center").getCenter();
            if (position().distanceTo(c) > 11) {
                getNavigation().moveTo(c.x, c.y, c.z, 1.0);
            } else if (distanceTo(tg) > 3.5 && tickCount % 8 == 0) {
                getNavigation().moveTo(tg, 1.0);
            }
        }

        private void begin(String m, LivingEntity tg) {
            move = m;
            t = 0;
            getNavigation().stop();
            Vec3 to = tg.position().subtract(position());
            setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
            setYBodyRot(getYRot());
            setYHeadRot(getYRot());
            if (m.equals("thrust")) {
                Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
                lungeTo = position().add(fwd.scale(5.0));
                Vec3 c = MoonRift.mark("arena_center").getCenter();
                if (new Vec3(lungeTo.x - c.x, 0, lungeTo.z - c.z).length() > 12.5) {
                    lungeTo = position();
                }
            }
            began(m);
            if (level() instanceof ServerLevel sl) {
                tell(sl, m);
            }
        }

        private void end(int restTicks) {
            move = "";
            t = 0;
            rest = phase >= 2 ? restTicks * 2 / 3 : restTicks;
        }

        @Override
        public void stop() {
            move = "";
        }
    }
}
