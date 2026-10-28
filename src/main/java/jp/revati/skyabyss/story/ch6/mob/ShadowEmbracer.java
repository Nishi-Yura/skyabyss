package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.ch6.valley.LampPostBlock;
import jp.revati.skyabyss.story.ch6.valley.MoonValley;
import jp.revati.skyabyss.story.ch6.valley.StarLamps;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 影抱き (新月の谷の主。docs/design/46 の 4.3・選んだ案 B 影の母)。台地の上の主の間で戦う (ValleyArena が呼び、片付ける)。
//   影の守り  暗がりでは傷がほとんど通らない (×0.2)。星灯り (灯りの台・星の印) の中に入ると体が固まり (3 秒)、そのあいだは傷 ×1.6
//   潜る      影に潜って床を走り (影の粒が走る = 予告)、獲物のそばの暗がりから出て、すぐ掴む。灯りの中には出ない
//   掴む      上の二本の腕を伸ばす (0.7 秒の予告)。当たると傷と足止め
//   影を投げる 獲物の足もとに影の円 (1 秒の予告)、そこへ影が落ちる
//   影の波    (段 2 から) 四本の腕を掲げ (1 秒の予告)、床へ叩きつけて広がる輪。跳べば越せる。段 3 では灯りの台を一つ消す
//   分身      (段 2 から) 影に綴じ手の分身を二つ置く (姿の三段目)
// 段は体力 66% と 33% で変わる (咆哮 2 秒、そのあいだは傷が通らない)。ボスバーは深淵の主と同じ色と刻み (AbyssBossBars)
public class ShadowEmbracer extends MoonMob {
    public static final String ID = "shadow_embracer";
    public static final double HP = 1100;
    public static final double DMG = 14;
    private static final EntityDataAccessor<Boolean> SUNK = SynchedEntityData.defineId(ShadowEmbracer.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FROZEN = SynchedEntityData.defineId(ShadowEmbracer.class, EntityDataSerializers.BOOLEAN);
    static final int FREEZE_TICKS = 60;
    static final int ROAR_TICKS = 40;
    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.skyabyss.shadow_embracer"), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private int phase;
    private int frozenFor;
    private int freezeCooldown;
    private int roarFor;

    public ShadowEmbracer(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "sink", "rise", "grab", "throw", "wave", "frozen", "roar", "hurt", "death");
        this.xpReward = 120;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SUNK, false);
        builder.define(FROZEN, false);
    }

    public boolean sunk() {
        return entityData.get(SUNK);
    }

    public boolean frozen() {
        return entityData.get(FROZEN);
    }

    public int phase() {
        return phase;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
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
        bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        bar.setProgress(getHealth() / getMaxHealth());
        if (roarFor > 0 && --roarFor == 0) {
            AbyssBossBars.roar(bar, false);
        }
        if (freezeCooldown > 0) {
            freezeCooldown--;
        }
        if (frozenFor > 0 && --frozenFor == 0) {
            entityData.set(FROZEN, false);
            freezeCooldown = 40;
        }
        // 星灯りの中に出たら固まる
        if (!sunk() && !frozen() && freezeCooldown == 0 && roarFor == 0 && StarLamps.inLight(level(), position().add(0, 1, 0))) {
            frozenFor = FREEZE_TICKS;
            entityData.set(FROZEN, true);
            playAction("frozen");
            playSound(MoonMobsSetup.EMBRACER_FROZEN.get(), 1.5f, 0.9f);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.5, getZ(), 40, 0.8, 1.5, 0.8, 0.05);
            }
        }
        // 段
        float f = getHealth() / getMaxHealth();
        int want = f <= 0.33f ? 2 : f <= 0.66f ? 1 : 0;
        if (want > phase && isAlive()) {
            phase = want;
            AbyssBossBars.phase(bar, phase);
            AbyssBossBars.roar(bar, true);
            roarFor = ROAR_TICKS;
            entityData.set(FROZEN, false);
            frozenFor = 0;
            playAction("roar");
            playSound(MoonMobsSetup.EMBRACER_ROAR.get(), 2.0f, 0.8f);
            if (level() instanceof ServerLevel sl) {
                clones(sl, 2);
            }
        }
        if (tickCount % 60 == 0 && !sunk()) {
            playSound(MoonMobsSetup.EMBRACER_HUM.get(), 1.0f, 0.8f);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (sunk() || roarFor > 0) {
            return false;
        }
        return super.hurt(source, frozen() ? amount * 1.6f : amount * 0.2f);
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

    // 分身: 影に綴じ手を置く (主のまわりの暗がり)
    void clones(ServerLevel sl, int n) {
        for (int i = 0; i < n; i++) {
            Vec3 at = darkSpot(sl, position(), 5, 9);
            if (at == null) {
                continue;
            }
            ShadowStitcher s = MoonMobsSetup.SHADOW_STITCHER.get().create(sl);
            if (s == null) {
                continue;
            }
            s.moveTo(at.x, at.y, at.z, random.nextFloat() * 360f, 0f);
            s.tuneMoon(0, false, true, ShadowStitcher.HP * 0.6, ShadowStitcher.DMG);
            s.addTag(jp.revati.skyabyss.story.ch6.valley.ValleyArena.TAG);
            sl.addFreshEntity(s);
            sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1, at.z, 30, 0.4, 1.0, 0.4, 0.02);
        }
    }

    // 主の間の床で、灯りの外の所 (中心からの半径 min〜max)
    @Nullable
    Vec3 darkSpot(ServerLevel sl, Vec3 near, double min, double max) {
        BlockPos c = MoonValley.mark("arena_center");
        for (int tries = 0; tries < 24; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = min + random.nextDouble() * (max - min);
            Vec3 p = new Vec3(near.x + Math.cos(a) * r, c.getY(), near.z + Math.sin(a) * r);
            if (p.distanceTo(c.getCenter()) > 8.5) {
                continue;
            }
            if (!StarLamps.inLight(sl, p.add(0, 1, 0)) && sl.getBlockState(BlockPos.containing(p)).isAir() && !sl.getBlockState(BlockPos.containing(p).below()).isAir()) {
                return p;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 戦い方 (一度に一つの技。技の間に休み)
    final class Fight extends Goal {
        private String move = "";
        private int t;
        private int rest = 40;
        @Nullable
        private Vec3 spot;
        private int sinceSink;

        Fight() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive();
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
            sinceSink++;
            if (frozen() || roarFor > 0) {
                getNavigation().stop();
                if (frozen() && frozenFor == 1) {
                    // 解けたらすぐ影に潜って離れる
                    begin("sink");
                }
                if (!move.equals("sink")) {
                    return;
                }
            }
            if (!sunk()) {
                getLookControl().setLookAt(tg, 30f, 30f);
            }
            if (move.isEmpty()) {
                if (rest > 0) {
                    rest--;
                    if (distanceTo(tg) > 3.5 && tickCount % 10 == 0) {
                        getNavigation().moveTo(tg, 0.9);
                    }
                    return;
                }
                choose(tg);
                return;
            }
            t++;
            switch (move) {
                case "sink" -> sink(sl, tg);
                case "grab" -> grab(sl, tg);
                case "throw" -> toss(sl, tg);
                case "wave" -> wave(sl);
                default -> end(20);
            }
        }

        private void choose(LivingEntity tg) {
            double d = distanceTo(tg);
            if (sinceSink > 240 || d > 10) {
                begin("sink");
            } else if (phase >= 1 && random.nextInt(3) == 0) {
                begin("wave");
            } else if (d <= 4.5) {
                begin("grab");
            } else {
                begin("throw");
            }
        }

        private void begin(String m) {
            move = m;
            t = 0;
            spot = null;
            getNavigation().stop();
            if (!m.equals("sink")) {
                playAction(m);
            }
        }

        private void end(int restTicks) {
            move = "";
            t = 0;
            rest = phase >= 2 ? restTicks / 2 : restTicks;
        }

        // 潜る (18 tick) → 床の影が獲物のそばへ走る (30 tick) → 出る → 掴む
        private void sink(ServerLevel sl, LivingEntity tg) {
            if (t == 1) {
                playAction("sink");
                playSound(MoonMobsSetup.EMBRACER_SINK.get(), 1.5f, 0.8f);
            }
            if (t == 18) {
                entityData.set(SUNK, true);
                Vec3 to = darkSpot(sl, tg.position(), 2.5, 4.5);
                spot = to != null ? to : tg.position().add(2, 0, 0);
            }
            if (t > 18 && spot != null) {
                // 床を走る影 (予告)
                Vec3 p = position().lerp(spot, Math.min(1.0, (t - 18) / 30.0));
                sl.sendParticles(ParticleTypes.SQUID_INK, p.x, spot.y + 0.1, p.z, 6, 0.5, 0.0, 0.5, 0.01);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, spot.y + 0.1, p.z, 2, 0.4, 0.0, 0.4, 0.0);
            }
            if (t == 48 && spot != null) {
                teleportTo(spot.x, spot.y, spot.z);
                entityData.set(SUNK, false);
                playAction("rise");
                playSound(MoonMobsSetup.EMBRACER_SINK.get(), 1.5f, 1.2f);
                sinceSink = 0;
            }
            if (t == 58) {
                begin("grab");
            }
        }

        // 掴む: 0.7 秒の予告のあと、前 4.5 の内
        private void grab(ServerLevel sl, LivingEntity tg) {
            if (t == 14) {
                playSound(MoonMobsSetup.EMBRACER_GRAB.get(), 1.5f, 1.0f);
                Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
                for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(5.0), e -> e.isAlive())) {
                    Vec3 to = p.position().subtract(position());
                    if (to.horizontalDistance() <= 4.8 && to.normalize().dot(fwd) > 0.2) {
                        strike(p, dmg());
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
                    }
                }
            }
            if (t >= 26) {
                end(30);
            }
        }

        // 影を投げる: 獲物の足もとに影の円 (20 tick)、落ちる
        private void toss(ServerLevel sl, LivingEntity tg) {
            if (t == 1) {
                spot = tg.position();
            }
            if (spot != null && t <= 20 && t % 2 == 0) {
                for (int i = 0; i < 14; i++) {
                    double a = i / 14.0 * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.SQUID_INK, spot.x + Math.cos(a) * 2.0, spot.y + 0.1, spot.z + Math.sin(a) * 2.0, 1, 0, 0, 0, 0);
                }
            }
            if (t == 12) {
                playSound(MoonMobsSetup.EMBRACER_THROW.get(), 1.5f, 0.9f);
            }
            if (t == 20 && spot != null) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, spot.x, spot.y + 0.5, spot.z, 30, 0.8, 0.5, 0.8, 0.02);
                for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(spot, spot).inflate(2.2, 2, 2.2), e -> e.isAlive())) {
                    if (p.position().distanceTo(spot) <= 2.3) {
                        strike(p, dmg(1.1));
                    }
                }
                end(30);
            }
        }

        // 影の波: 1 秒の予告 → 叩きつけて輪が広がる (12 tick で半径 14)。地に足がついている人に当たる
        private void wave(ServerLevel sl) {
            if (t == 4) {
                playSound(MoonMobsSetup.EMBRACER_WAVE.get(), 2.0f, 0.8f);
            }
            if (t == 20) {
                spot = position();
                if (phase >= 2) {
                    snuffOne(sl);
                }
            }
            if (t > 20 && spot != null) {
                double r = (t - 20) * 1.2;
                int n = (int) (r * 6) + 6;
                for (int i = 0; i < n; i++) {
                    double a = i / (double) n * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.SQUID_INK, spot.x + Math.cos(a) * r, spot.y + 0.15, spot.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0);
                }
                for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(spot, spot).inflate(r + 1, 2, r + 1), e -> e.isAlive() && e.onGround())) {
                    double d = p.position().subtract(spot).horizontalDistance();
                    if (Math.abs(d - r) < 0.9 && p.invulnerableTime == 0) {
                        strike(p, dmg(0.9));
                    }
                }
                if (r >= 14) {
                    end(40);
                }
            }
        }

        // 灯りの台を一つ消す (一番近い灯ったもの)
        private void snuffOne(ServerLevel sl) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < 6; i++) {
                BlockPos p = MoonValley.mark("lamp_" + i);
                if (sl.getBlockState(p).getBlock() instanceof LampPostBlock && sl.getBlockState(p).getValue(LampPostBlock.LIT)) {
                    double d = p.distSqr(blockPosition());
                    if (d < bd) {
                        bd = d;
                        best = p;
                    }
                }
            }
            if (best != null) {
                LampPostBlock.snuff(sl, best);
            }
        }

        @Override
        public void stop() {
            move = "";
            if (sunk()) {
                entityData.set(SUNK, false);
            }
        }
    }

    // 倒れたとき近くの人 (主の間の内) を数える
    public List<ServerPlayer> witnesses() {
        return level() instanceof ServerLevel sl ? sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(30), e -> e.isAlive()) : List.of();
    }
}
