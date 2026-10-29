package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.finale.Echoes;
import jp.revati.skyabyss.story.finale.ForgeArena;
import jp.revati.skyabyss.story.finale.ForgeIsle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 戻し手 (炉の島の主。docs/design/50 の 4.2.3・一品一案「歯車を負う鍛冶頭」)。大炉の上の主の間で戦う (ForgeArena が呼び、片付ける)。内の床 (半径 INNER) の中を歩く。
//   打ち下ろし  獲物の足もとに円 (半径 SLAM_R、1.2 秒) → 叩きつけ (傷 ×1.3)
//   薙ぎ        前の 120 度・半径 SWEEP_R (0.9 秒) → 傷 ×1.1
//   どちらも ECHO 後に同じ所でもう一度 (傷 ×1.0。段 3 は ECHO と ECHO × 2 の二度)。打たれた所から離れれば当たらない (Echoes)
//   足跡が燃える PRINTS_EVERY ごとに、主の間の人みなの足もとに残り火の印 → ECHO 後に燃える (動いていれば当たらない)
// 段は体力 66% と 33% (咆哮 2 秒は傷が通らない)。段 2 で打ち手を 2 体呼び、外の輪 (戻り石) が巻き戻り始める。段 3 でも 2 体呼ぶ
public class Rewinder extends CloudMob {
    public static final String ID = "rewinder";
    public static final double HP = 1010;
    public static final double DMG = 20;
    static final int ROAR_TICKS = 40;
    static final double INNER = 5.2;
    static final int SLAM_TELL = 24;
    static final double SLAM_R = 2.6;
    static final int SWEEP_TELL = 18;
    static final double SWEEP_R = 5.0;
    public static final int ECHO = 60;
    static final int PRINTS_EVERY = 300;
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.rewinder"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
    private int phase;
    private int roarFor;
    private int sincePrints;
    private boolean toldPrints;

    public Rewinder(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "slam", "sweep", "prints", "call", "hurt", "death");
        this.xpReward = 200;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 60);
    }

    public int phase() {
        return phase;
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

    // 段 3 は二度もどる
    void echoCircle(ServerLevel sl, Vec3 c, double r, float dmg) {
        Echoes.circle(sl, c, r, ECHO, dmg, this);
        if (phase >= 2) {
            Echoes.circle(sl, c, r, ECHO * 2, dmg, this);
        }
    }

    void echoCone(ServerLevel sl, Vec3 c, Vec3 dir, float dmg) {
        Echoes.cone(sl, c, dir, SWEEP_R, 60, ECHO, dmg, this);
        if (phase >= 2) {
            Echoes.cone(sl, c, dir, SWEEP_R, 60, ECHO * 2, dmg, this);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        // 内の床から落ちたら真ん中へ戻る
        BlockPos home = ForgeIsle.mark("lord_spawn");
        if (getY() < home.getY() - 2.5) {
            setPos(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            setDeltaMovement(Vec3.ZERO);
            resetFallDistance();
        }
        bar.setProgress(getHealth() / getMaxHealth());
        sincePrints++;
        if (roarFor > 0 && --roarFor == 0) {
            AbyssBossBars.roar(bar, false);
        }
        float f = getHealth() / getMaxHealth();
        int want = f <= 0.33f ? 2 : f <= 0.66f ? 1 : 0;
        if (want > phase && isAlive()) {
            phase = want;
            AbyssBossBars.phase(bar, phase);
            AbyssBossBars.roar(bar, true);
            roarFor = ROAR_TICKS;
            playAction("call");
            playSound(CloudMobsSetup.REWINDER_CALL.get(), 2.0f, 0.8f);
            ForgeArena.callStrikers(sl, 2);
            if (phase == 1) {
                ForgeIsle.arenaRewinds = true;
                for (ServerPlayer p : ForgeArena.present(sl)) {
                    Notice.narrate(p, "message.skyabyss.isle3.lord_ring");
                }
            } else {
                for (ServerPlayer p : ForgeArena.present(sl)) {
                    Notice.narrate(p, "message.skyabyss.isle3.lord_twice");
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (roarFor > 0) {
            return false;
        }
        return super.hurt(source, amount);
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

    // ---------------------------------------------------------------- 戦い方 (一度に一つの技。技の間に休み)
    final class Fight extends Goal {
        private String move = "";
        private int t;
        private int rest = 40;
        private int repath;
        private Vec3 spot = Vec3.ZERO;
        private Vec3 dir = Vec3.ZERO;

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
            if (roarFor > 0) {
                getNavigation().stop();
                move = "";
                return;
            }
            if (move.isEmpty()) {
                getLookControl().setLookAt(tg, 30f, 30f);
                approach(tg);
                if (rest > 0) {
                    rest--;
                    return;
                }
                double d = distanceTo(tg);
                if (sincePrints >= PRINTS_EVERY && !ForgeArena.present(sl).isEmpty()) {
                    begin("prints");
                } else if (d <= 4.5) {
                    begin("sweep");
                    dir = new Vec3(tg.getX() - getX(), 0, tg.getZ() - getZ()).normalize();
                    spot = position();
                } else if (d <= 8) {
                    begin("slam");
                    spot = tg.position();
                }
                return;
            }
            t++;
            getNavigation().stop();
            switch (move) {
                case "slam" -> slam(sl);
                case "sweep" -> sweep(sl);
                case "prints" -> prints(sl);
                default -> end(20);
            }
        }

        // 内の床の中で、獲物の方へ寄る
        private void approach(LivingEntity tg) {
            if (--repath > 0) {
                return;
            }
            repath = 10;
            BlockPos c = ForgeIsle.mark("lord_spawn");
            Vec3 center = new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            Vec3 want = tg.position();
            Vec3 off = new Vec3(want.x - center.x, 0, want.z - center.z);
            if (off.length() > INNER - 1) {
                off = off.normalize().scale(INNER - 1);
            }
            Vec3 goal = center.add(off);
            if (distanceTo(tg) > 3.0 && position().distanceTo(goal) > 1.2) {
                getNavigation().moveTo(goal.x, center.y, goal.z, 0.9);
            } else {
                getNavigation().stop();
            }
        }

        private void begin(String m) {
            move = m;
            t = 0;
            getNavigation().stop();
            playAction(m);
            switch (m) {
                case "slam" -> playSound(CloudMobsSetup.REWINDER_SLAM_TELL.get(), 1.6f, 0.9f);
                case "sweep" -> playSound(CloudMobsSetup.STRIKER_TELL.get(), 1.4f, 0.7f);
                default -> playSound(CloudMobsSetup.REWINDER_PRINTS.get(), 1.6f, 0.8f);
            }
        }

        private void end(int restTicks) {
            move = "";
            t = 0;
            rest = phase >= 2 ? restTicks * 2 / 3 : restTicks;
        }

        private void slam(ServerLevel sl) {
            getLookControl().setLookAt(spot.x, spot.y, spot.z);
            if (t < SLAM_TELL) {
                if (t % 3 == 0) {
                    for (int i = 0; i < 20; i++) {
                        double a = i / 20.0 * Math.PI * 2;
                        sl.sendParticles(ParticleTypes.FLAME, spot.x + Math.cos(a) * SLAM_R, spot.y + 0.15, spot.z + Math.sin(a) * SLAM_R, 1, 0, 0, 0, 0);
                    }
                }
                return;
            }
            if (t == SLAM_TELL + 2) {
                playSound(CloudMobsSetup.REWINDER_SLAM.get(), 2.0f, 0.8f);
                sl.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.4, spot.z, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + 0.3, spot.z, 8, SLAM_R * 0.4, 0.1, SLAM_R * 0.4, 0);
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(spot, spot).inflate(SLAM_R + 0.5, 2, SLAM_R + 0.5), e -> e.isAlive() && !e.isSpectator())) {
                    if (p.position().subtract(spot).horizontalDistance() <= SLAM_R + 0.3) {
                        strike(p, dmg(1.3));
                    }
                }
                echoCircle(sl, spot, SLAM_R, dmg());
            }
            if (t >= SLAM_TELL + 14) {
                end(30);
            }
        }

        private void sweep(ServerLevel sl) {
            if (t < SWEEP_TELL) {
                if (t % 3 == 0) {
                    for (int i = -6; i <= 6; i++) {
                        Vec3 d = dir.yRot((float) Math.toRadians(i * 10));
                        Vec3 p = spot.add(d.scale(SWEEP_R));
                        sl.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.15, p.z, 1, 0, 0, 0, 0);
                    }
                }
                return;
            }
            if (t == SWEEP_TELL + 1) {
                playSound(CloudMobsSetup.REWINDER_SWEEP.get(), 1.8f, 0.8f);
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(spot, spot).inflate(SWEEP_R + 0.5, 2, SWEEP_R + 0.5), e -> e.isAlive() && !e.isSpectator())) {
                    Vec3 to = new Vec3(p.getX() - spot.x, 0, p.getZ() - spot.z);
                    if (to.length() <= SWEEP_R + 0.3 && (to.length() < 1 || to.normalize().dot(dir) >= 0.5)) {
                        strike(p, dmg(1.1));
                        p.knockback(0.8, spot.x - p.getX(), spot.z - p.getZ());
                    }
                }
                echoCone(sl, spot, dir, dmg());
            }
            if (t >= SWEEP_TELL + 12) {
                end(30);
            }
        }

        // 足跡が燃える: 今いる所に印 → ECHO 後に燃える
        private void prints(ServerLevel sl) {
            if (t == 16) {
                sincePrints = 0;
                for (ServerPlayer p : ForgeArena.present(sl)) {
                    Echoes.circle(sl, p.position(), 2.0, ECHO, dmg(), Rewinder.this);
                    if (!toldPrints) {
                        toldPrints = true;
                        Notice.narrate(p, "message.skyabyss.isle3.lord_prints");
                    }
                }
            }
            if (t >= 32) {
                end(20);
            }
        }

        @Override
        public void stop() {
            move = "";
        }
    }
}
