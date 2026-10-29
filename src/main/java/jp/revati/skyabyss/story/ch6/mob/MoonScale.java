package jp.revati.skyabyss.story.ch6.mob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.ch6.cliff.CliffBlocks;
import jp.revati.skyabyss.story.ch6.cliff.MoonCliff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 月秤 (上弦の崖の主。docs/design/46 の 4.4・選んだ案 A 天秤を担ぐ巨人)。崖の頂の主の間 (天秤の大きな皿の上) で戦う (CliffArena が呼び、片付ける)。
//   皿の向き: 入口の側 (西) が人の側、主の出る側 (東) が主の側。主は自分の側から離れない
//   釣り合いの守り  ふだんは傷 ×0.35。錘が人の側の皿に二つ落ちると主の側が持ち上がって主が崩れ (5 秒)、そのあいだ傷 ×1.6。崩れると錘は砕ける
//   錘落とし        人の足もとに影の円 (1.3 秒の予告)、錘が落ちる。円から出れば当たらない。落ちた錘は 15 秒、人の側の皿に残る (重さになる)
//   傾け            棹を担ぎ上げ (1.5 秒の予告。皿の上を砂が縁へ流れる)、皿を人の側へ傾ける。人の側にいる人は縁へ滑る (走るか、跳んで高い主の側へ)
//   叩き割り        (段 2 から) 跳んで叩きつける (1.5 秒の予告の円)。円の床が 12 秒割れて落ちる
//   薙ぎ払い        近くでは棹で前を薙ぐ (予告つき)
// 段は体力 66% と 33% (咆哮 2 秒は傷が通らない)。段 3 は錘を二つずつ落とす。皿から落ちた人は CliffTicker が入口へ戻す (傷 4)
public class MoonScale extends MoonMob {
    public static final String ID = "moon_scale";
    // 生き物の体力の上限 (1024) に、上弦の 1.2 倍でも収まる数
    public static final double HP = 850;
    public static final double DMG = 15;
    static final int ROAR_TICKS = 40;
    static final int TOPPLE_TICKS = 100;
    static final int WEIGHT_LIFE = 300;
    static final int HOLE_LIFE = 240;
    static final double WEIGHT_RADIUS = 2.2;
    static final double SLAM_RADIUS = 2.8;
    static final double TILT_PUSH = 0.1;
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.moon_scale"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10));
    private int phase;
    private int roarFor;
    private int toppleFor;
    private int tiltFor;
    private boolean toldTopple;
    private final List<long[]> landed = new ArrayList<>();
    private final Map<BlockPos, BlockState> holes = new HashMap<>();
    private long holesBack;

    public MoonScale(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "throw", "tilt", "slam", "sweep", "topple", "roar", "hurt", "death");
        this.xpReward = 140;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 60);
    }

    public int phase() {
        return phase;
    }

    public boolean toppled() {
        return toppleFor > 0;
    }

    public boolean tilting() {
        return tiltFor > 0;
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

    // ---------------------------------------------------------------- 皿の向き
    static Vec3 center() {
        return MoonCliff.mark("arena_center").getCenter();
    }

    // 人の側 (入口の側) を向く水平の単位
    static Vec3 west() {
        Vec3 c = center();
        Vec3 e = MoonCliff.mark("arena_entry").getCenter();
        Vec3 d = new Vec3(e.x - c.x, 0, e.z - c.z);
        return d.normalize();
    }

    // 人の側へのずれ (正 = 人の側)
    static double side(Vec3 p) {
        Vec3 c = center();
        return (p.x - c.x) * west().x + (p.z - c.z) * west().z;
    }

    static boolean onPlate(Vec3 p) {
        Vec3 c = center();
        return Math.hypot(p.x - c.x, p.z - c.z) <= 14.3 && p.y >= c.y - 1.5 && p.y <= c.y + 4;
    }

    // ---------------------------------------------------------------- 毎 tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        bar.setProgress(getHealth() / getMaxHealth());
        if (roarFor > 0 && --roarFor == 0) {
            AbyssBossBars.roar(bar, false);
        }
        if (toppleFor > 0) {
            toppleFor--;
        }
        if (tiltFor > 0) {
            tiltFor--;
            tiltPush(sl);
        }
        expireWeights(sl);
        if (!holes.isEmpty() && sl.getGameTime() >= holesBack) {
            mendHoles(sl);
        }
        float f = getHealth() / getMaxHealth();
        int want = f <= 0.33f ? 2 : f <= 0.66f ? 1 : 0;
        if (want > phase && isAlive()) {
            phase = want;
            AbyssBossBars.phase(bar, phase);
            AbyssBossBars.roar(bar, true);
            roarFor = ROAR_TICKS;
            toppleFor = 0;
            tiltFor = 0;
            playAction("roar");
            playSound(MoonMobsSetup.SCALE_ROAR.get(), 2.0f, 0.7f);
        }
        if (tickCount % 80 == 0) {
            playSound(MoonMobsSetup.SCALE_CREAK.get(), 0.8f, 0.7f);
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
        return super.hurt(source, toppled() ? amount * 1.6f : amount * 0.35f);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bar.removeAllPlayers();
        if (level() instanceof ServerLevel sl) {
            tidy(sl);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl) {
            tidy(sl);
        }
        super.remove(reason);
        bar.removeAllPlayers();
    }

    // 片付け: 落ちた錘を消し、割れた床を戻す
    public void tidy(ServerLevel sl) {
        for (long[] w : landed) {
            BlockPos p = BlockPos.of(w[0]);
            if (sl.getBlockState(p).is(CliffBlocks.MOON_WEIGHT.get())) {
                sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        landed.clear();
        mendHoles(sl);
    }

    // ---------------------------------------------------------------- 傾け: 人の側にいて地に足がついている人を縁へ押す
    private void tiltPush(ServerLevel sl) {
        Vec3 w = west();
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(center(), center()).inflate(16, 4, 16), e -> e.isAlive() && !e.isSpectator())) {
            if (!onPlate(p.position()) || side(p.position()) < 1.0 || !p.onGround()) {
                continue;
            }
            p.push(w.x * TILT_PUSH, 0, w.z * TILT_PUSH);
            p.hurtMarked = true;
        }
        if (tiltFor % 4 == 0) {
            // 縁へ流れる砂
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * 13;
                Vec3 at = center().add(Math.cos(a) * r, -0.4, Math.sin(a) * r);
                if (side(at) > 0.5) {
                    sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, w.x, 0.0, w.z, 0.25);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 錘 (人の側の皿に落ちた錘。15 秒で消える)
    private void expireWeights(ServerLevel sl) {
        long now = sl.getGameTime();
        for (Iterator<long[]> it = landed.iterator(); it.hasNext(); ) {
            long[] w = it.next();
            if (now >= w[1]) {
                BlockPos p = BlockPos.of(w[0]);
                if (sl.getBlockState(p).is(CliffBlocks.MOON_WEIGHT.get())) {
                    sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), p.getX() + 0.5, p.getY() + 0.4,
                            p.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
                }
                it.remove();
            }
        }
    }

    public int landedWeights() {
        return landed.size();
    }

    // 錘が落ちた所: 人の側の皿の上なら残す。二つになったら主が崩れる
    void weightLands(ServerLevel sl, Vec3 at) {
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), at.x, at.y + 0.3, at.z, 30, 0.6, 0.3, 0.6, 0.1);
        playSound(MoonMobsSetup.SCALE_WEIGHT.get(), 1.4f, 0.8f);
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(WEIGHT_RADIUS, 2.5, WEIGHT_RADIUS), e -> e.isAlive())) {
            if (p.position().subtract(at).horizontalDistance() <= WEIGHT_RADIUS + 0.3) {
                strike(p, dmg(1.3));
            }
        }
        BlockPos floor = BlockPos.containing(at.x, center().y - 1, at.z);
        BlockPos on = floor.above();
        if (onPlate(at) && side(at) > 0.5 && !sl.getBlockState(floor).isAir() && sl.getBlockState(on).isAir()) {
            sl.setBlock(on, CliffBlocks.MOON_WEIGHT.get().defaultBlockState(), 3);
            landed.add(new long[] {on.asLong(), sl.getGameTime() + WEIGHT_LIFE});
            if (landed.size() >= 2) {
                topple(sl);
            }
        }
    }

    // 崩れる: 主の側が持ち上がり、主が膝をつく (5 秒、傷 ×1.6)。錘は砕ける
    public void topple(ServerLevel sl) {
        toppleFor = TOPPLE_TICKS;
        tiltFor = 0;
        playAction("topple");
        playSound(MoonMobsSetup.SCALE_TOPPLE.get(), 2.0f, 0.8f);
        for (long[] w : landed) {
            w[1] = sl.getGameTime() + 2;
        }
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 3, getZ(), 40, 1.0, 1.5, 1.0, 0.05);
        if (!toldTopple) {
            toldTopple = true;
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24), e -> e.isAlive())) {
                Notice.narrate(p, "message.skyabyss.moon.scale_topple");
            }
        }
    }

    // ---------------------------------------------------------------- 叩き割り: 円の床を割る (12 秒で戻る)
    void breakFloor(ServerLevel sl, Vec3 at) {
        int y = (int) Math.floor(center().y) - 1;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > SLAM_RADIUS * SLAM_RADIUS - 1) {
                    continue;
                }
                BlockPos p = BlockPos.containing(at.x + dx, y, at.z + dz);
                if (!onPlate(p.getCenter().add(0, 1, 0)) || holes.containsKey(p)) {
                    continue;
                }
                BlockState st = sl.getBlockState(p);
                if (st.isAir() || st.is(Blocks.IRON_BLOCK)) {
                    continue;
                }
                holes.put(p, st);
                sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.05);
            }
        }
        holesBack = sl.getGameTime() + HOLE_LIFE;
    }

    void mendHoles(ServerLevel sl) {
        for (Map.Entry<BlockPos, BlockState> e : holes.entrySet()) {
            if (sl.getBlockState(e.getKey()).isAir()) {
                sl.setBlock(e.getKey(), e.getValue(), 3);
            }
        }
        holes.clear();
    }

    // ---------------------------------------------------------------- 戦い方 (一度に一つの技。技の間に休み)
    final class Fight extends Goal {
        private String move = "";
        private int t;
        private int rest = 50;
        private final List<Vec3> spots = new ArrayList<>();
        @Nullable
        private Vec3 slamAt;

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
            if (roarFor > 0 || toppled()) {
                getNavigation().stop();
                move = "";
                return;
            }
            getLookControl().setLookAt(tg, 30f, 30f);
            if (move.isEmpty()) {
                if (rest > 0) {
                    rest--;
                    keepSide(tg);
                    return;
                }
                choose(tg);
                return;
            }
            t++;
            switch (move) {
                case "throw" -> throwWeights(sl);
                case "tilt" -> tilt(sl);
                case "slam" -> slam(sl, tg);
                case "sweep" -> sweep(sl);
                default -> end(20);
            }
        }

        // 主の側から離れない (皿の真ん中から主の側へ 2〜9)
        private void keepSide(LivingEntity tg) {
            Vec3 c = center();
            Vec3 w = west();
            double s = side(position());
            if (s > -1.5 || position().distanceTo(c) > 10) {
                Vec3 home = c.add(w.scale(-5));
                getNavigation().moveTo(home.x, c.y, home.z, 0.9);
            } else if (distanceTo(tg) > 6 && side(tg.position()) < 0 && tickCount % 10 == 0) {
                getNavigation().moveTo(tg, 0.8);
            }
        }

        private void choose(LivingEntity tg) {
            double d = distanceTo(tg);
            if (d <= 5.0) {
                begin("sweep");
            } else if (phase >= 1 && random.nextInt(4) == 0) {
                begin("slam");
            } else if (random.nextInt(3) == 0) {
                begin("tilt");
            } else {
                begin("throw");
            }
        }

        private void begin(String m) {
            move = m;
            t = 0;
            spots.clear();
            slamAt = null;
            getNavigation().stop();
            playAction(m);
        }

        private void end(int restTicks) {
            move = "";
            t = 0;
            rest = phase >= 2 ? restTicks * 2 / 3 : restTicks;
        }

        // 錘落とし: 人の足もとに影の円 (26 tick)、錘が落ちる。段 3 は二人 (二つ)
        private void throwWeights(ServerLevel sl) {
            if (t == 1) {
                List<Player> ps = sl.getEntitiesOfClass(Player.class, new AABB(center(), center()).inflate(16, 5, 16), e -> e.isAlive() && !e.isSpectator()
                        && onPlate(e.position()));
                java.util.Collections.shuffle(ps, new java.util.Random(random.nextLong()));
                int n = phase >= 2 ? 2 : 1;
                for (int i = 0; i < Math.min(n, ps.size()); i++) {
                    spots.add(ps.get(i).position());
                }
                if (spots.isEmpty() && getTarget() != null) {
                    spots.add(getTarget().position());
                }
                playSound(MoonMobsSetup.SCALE_THROW.get(), 1.5f, 0.9f);
            }
            if (t <= 26 && t % 2 == 0) {
                for (Vec3 s : spots) {
                    for (int i = 0; i < 16; i++) {
                        double a = i / 16.0 * Math.PI * 2;
                        sl.sendParticles(ParticleTypes.SQUID_INK, s.x + Math.cos(a) * WEIGHT_RADIUS, s.y + 0.1, s.z + Math.sin(a) * WEIGHT_RADIUS, 1, 0, 0, 0, 0);
                    }
                    double h = 10.0 * (1.0 - t / 26.0);
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), s.x, s.y + 1 + h, s.z, 3, 0.3, 0.2, 0.3, 0);
                }
            }
            if (t == 26) {
                for (Vec3 s : spots) {
                    weightLands(sl, s);
                }
                end(40);
            }
        }

        // 傾け: 30 tick の予告 → 70 tick (段 2 から 90) 人の側へ傾く
        private void tilt(ServerLevel sl) {
            if (t == 1) {
                playSound(MoonMobsSetup.SCALE_TILT.get(), 2.0f, 0.7f);
            }
            if (t < 30 && t % 3 == 0) {
                Vec3 w = west();
                for (int i = 0; i < 6; i++) {
                    Vec3 at = center().add(w.scale(2 + random.nextDouble() * 10)).add(-w.z * (random.nextDouble() * 20 - 10), -0.4, w.x * (random.nextDouble() * 20 - 10));
                    if (onPlate(at.add(0, 1, 0))) {
                        sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, w.x, 0, w.z, 0.12);
                    }
                }
            }
            if (t == 30) {
                tiltFor = phase >= 1 ? 90 : 70;
                playSound(MoonMobsSetup.SCALE_TILT.get(), 2.0f, 0.5f);
            }
            if (t >= 30 + tiltFor) {
                end(30);
            }
        }

        // 叩き割り: 獲物の足もとに円 (30 tick)、跳んで叩きつけ、円の床が割れる
        private void slam(ServerLevel sl, LivingEntity tg) {
            if (t == 1) {
                slamAt = tg.position();
                if (!onPlate(slamAt)) {
                    slamAt = center().add(west().scale(4));
                }
            }
            if (slamAt != null && t <= 30 && t % 2 == 0) {
                for (int i = 0; i < 18; i++) {
                    double a = i / 18.0 * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.SQUID_INK, slamAt.x + Math.cos(a) * SLAM_RADIUS, slamAt.y + 0.1, slamAt.z + Math.sin(a) * SLAM_RADIUS, 1, 0, 0, 0, 0);
                }
            }
            if (t == 12 && slamAt != null) {
                Vec3 d = slamAt.subtract(position());
                setDeltaMovement(d.x * 0.08, 1.0, d.z * 0.08);
                hasImpulse = true;
            }
            if (t == 30 && slamAt != null) {
                setPos(slamAt.x, slamAt.y, slamAt.z);
                playSound(MoonMobsSetup.SCALE_SLAM.get(), 2.0f, 0.7f);
                sl.sendParticles(ParticleTypes.EXPLOSION, slamAt.x, slamAt.y + 0.3, slamAt.z, 2, 0.5, 0.1, 0.5, 0);
                for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(slamAt, slamAt).inflate(SLAM_RADIUS + 1, 3, SLAM_RADIUS + 1), e -> e.isAlive())) {
                    if (p.position().subtract(slamAt).horizontalDistance() <= SLAM_RADIUS + 0.4) {
                        strike(p, dmg(1.5));
                        p.push(0, 0.5, 0);
                        p.hurtMarked = true;
                    }
                }
                breakFloor(sl, slamAt);
            }
            if (t >= 44) {
                end(40);
            }
        }

        // 薙ぎ払い: 前の 120 度・5.5 の内 (16 tick の予告)
        private void sweep(ServerLevel sl) {
            if (t == 16) {
                playSound(MoonMobsSetup.SCALE_SWEEP.get(), 1.5f, 0.8f);
                Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
                for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(6.0), e -> e.isAlive())) {
                    Vec3 to = p.position().subtract(position());
                    if (to.horizontalDistance() <= 5.5 && new Vec3(to.x, 0, to.z).normalize().dot(fwd) > 0.45) {
                        strike(p, dmg(1.2));
                        p.knockback(1.2, getX() - p.getX(), getZ() - p.getZ());
                    }
                }
            }
            if (t >= 28) {
                end(30);
            }
        }

        @Override
        public void stop() {
            move = "";
        }
    }
}
