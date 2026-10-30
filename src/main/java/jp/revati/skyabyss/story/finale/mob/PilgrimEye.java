package jp.revati.skyabyss.story.finale.mob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.finale.PilgrimSeat;
import jp.revati.skyabyss.story.finale.SeatArena;
import jp.revati.skyabyss.story.finale.LampIsle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 巡礼者の目 (ラスボス「雲上の巡礼者」の第 2 形態。docs/design/50 の 4.3・一品一案「雲の瞼の一つ目」)。雲が割れて座の真ん中に浮かび、動かない (SeatArena が呼ぶ)。
// 灯の島の灯守の目と同じ決まりで、もっと大きく速い:
//   回る筋  目から座を回る光の筋 (見えていると重く、ARENA_HOLD より長いと傷)。二本、段 2 は速く、段 3 は三本。輪の四隅の柱が陰
//   凝視    一人を見据え (STARE_TELL = 1.5 秒。光の糸)、その人へ光の柱 (傷 ×1.7)。柱の陰なら当たらない。そのあと CLOSED のあいだ雲の瞼が閉じる (傷 ×1.5)
//   光の雨  座の人みなの足もとと床のあちこちに光の円 (1 秒) → 光が落ちる (傷 ×0.9)
// 段は体力 66% と 33% (咆哮 2 秒は傷が通らない)。段 2・段 3 で落ち羽を 2 体呼ぶ
public class PilgrimEye extends CloudMob {
    public static final String ID = "pilgrim_eye";
    public static final double HP = 1500;
    public static final double DMG = 23;
    public static final double HOVER = 1.0;
    static final int ROAR_TICKS = 40;
    static final int STARE_TELL = 30;
    static final int CLOSED = 60;
    static final int RAIN_TELL = 20;
    static final double RAIN_R = 1.8;
    static final double BEAM_HALF = Math.toRadians(10);
    static final double BEAM_RANGE = 17;
    static final int ARENA_HOLD = 20;
    private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.6f), 1.4f);
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.pilgrim_eye"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
    private int phase;
    private int roarFor;
    private int closedFor;
    private double beamAngle;
    private boolean toldStare;

    public PilgrimEye(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "stare", "closed", "rain", "call", "hurt", "death");
        this.xpReward = 220;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public int phase() {
        return phase;
    }

    public boolean closed() {
        return closedFor > 0;
    }

    public double beamAngle() {
        return beamAngle;
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

    // 目の真ん中 (光の出る所)
    public Vec3 eye() {
        return position().add(0, 3.2, 0);
    }

    // 筋の本数と一周の tick
    int beams() {
        return phase >= 2 ? 3 : 2;
    }

    int period() {
        return phase >= 1 ? 150 : 220;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        BlockPos home = PilgrimSeat.mark("lord_spawn");
        if (Math.abs(getX() - (home.getX() + 0.5)) > 0.3 || Math.abs(getZ() - (home.getZ() + 0.5)) > 0.3 || Math.abs(getY() - (home.getY() + HOVER)) > 0.3) {
            setPos(home.getX() + 0.5, home.getY() + HOVER, home.getZ() + 0.5);
            setDeltaMovement(Vec3.ZERO);
        }
        bar.setProgress(getHealth() / getMaxHealth());
        if (roarFor > 0 && --roarFor == 0) {
            AbyssBossBars.roar(bar, false);
        }
        if (closedFor > 0) {
            closedFor--;
        }
        // 回る筋 (目を閉じているあいだは止まる)
        if (closedFor == 0 && isAlive()) {
            beamAngle = (beamAngle + Math.PI * 2 / period()) % (Math.PI * 2);
        }
        if (tickCount % 2 == 0) {
            List<ServerPlayer> here = SeatArena.present(sl);
            for (ServerPlayer p : here) {
                boolean in = false;
                if (closedFor == 0 && isAlive()) {
                    for (int b = 0; b < beams(); b++) {
                        in |= LampIsle.inBeam(sl, eye(), beamAngle + b * Math.PI * 2 / beams(), BEAM_HALF, BEAM_RANGE, p);
                    }
                }
                LampIsle.gaze(p, in, ARENA_HOLD, dmg(0.35), this);
            }
            if (closedFor == 0 && isAlive() && tickCount % 4 == 0 && !here.isEmpty()) {
                for (int b = 0; b < beams(); b++) {
                    double a = beamAngle + b * Math.PI * 2 / beams();
                    for (double d = 1.5; d <= BEAM_RANGE; d += 1.0) {
                        sl.sendParticles(BEAM, getX() + Math.cos(a) * d, eye().y, getZ() + Math.sin(a) * d, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
        float f = getHealth() / getMaxHealth();
        int want = f <= 0.33f ? 2 : f <= 0.66f ? 1 : 0;
        if (want > phase && isAlive()) {
            phase = want;
            AbyssBossBars.phase(bar, phase);
            AbyssBossBars.roar(bar, true);
            roarFor = ROAR_TICKS;
            closedFor = 0;
            playAction("call");
            playSound(CloudMobsSetup.PILGRIM_ROAR.get(), 2.0f, 0.9f);
            SeatArena.callPlumes(sl, 2);
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
        return super.hurt(source, closedFor > 0 ? amount * 1.5f : amount);
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
        private int rest = 50;
        private boolean lastStare;
        private ServerPlayer mark;
        private final List<Vec3> spots = new ArrayList<>();

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
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            getNavigation().stop();
            if (roarFor > 0 || closedFor > 0) {
                move = "";
                return;
            }
            if (move.isEmpty()) {
                if (getTarget() != null) {
                    getLookControl().setLookAt(getTarget(), 30f, 30f);
                }
                if (rest > 0) {
                    rest--;
                    return;
                }
                List<ServerPlayer> here = SeatArena.present(sl);
                if (here.isEmpty()) {
                    return;
                }
                if (!lastStare) {
                    mark = here.get(random.nextInt(here.size()));
                    begin("stare");
                } else {
                    spots.clear();
                    for (ServerPlayer p : here) {
                        spots.add(p.position());
                    }
                    BlockPos c = PilgrimSeat.mark("lord_spawn");
                    for (int i = 0; i < 5; i++) {
                        double ra = random.nextDouble() * Math.PI * 2;
                        double rd = 3 + random.nextDouble() * 11;
                        spots.add(new Vec3(c.getX() + 0.5 + Math.cos(ra) * rd, c.getY(), c.getZ() + 0.5 + Math.sin(ra) * rd));
                    }
                    begin("rain");
                }
                return;
            }
            t++;
            switch (move) {
                case "stare" -> stare(sl);
                case "rain" -> rain(sl);
                default -> end(30);
            }
        }

        private void begin(String m) {
            move = m;
            t = 0;
            lastStare = m.equals("stare");
            playAction(m);
            playSound(m.equals("stare") ? CloudMobsSetup.PILGRIM_STARE.get() : CloudMobsSetup.PILGRIM_RAIN_TELL.get(), 1.6f, 1.0f);
        }

        private void end(int restTicks) {
            move = "";
            t = 0;
            rest = phase >= 2 ? restTicks * 2 / 3 : restTicks;
        }

        // 凝視: 光の糸 (STARE_TELL) → 見えていれば光の柱 → 目を閉じる
        private void stare(ServerLevel sl) {
            if (mark == null || !mark.isAlive() || mark.level() != sl) {
                end(20);
                return;
            }
            getLookControl().setLookAt(mark, 60f, 60f);
            Vec3 from = eye();
            Vec3 to = mark.getEyePosition();
            if (t < STARE_TELL) {
                if (t % 2 == 0) {
                    // 糸は細くなっていく (粒が減る)
                    int n = (int) Math.max(4, from.distanceTo(to) * (1.0 - t / (double) STARE_TELL) * 3);
                    for (int i = 0; i <= n; i++) {
                        Vec3 q = from.lerp(to, i / (double) n);
                        sl.sendParticles(ParticleTypes.WAX_ON, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                }
                return;
            }
            if (t == STARE_TELL) {
                playSound(CloudMobsSetup.PILGRIM_BEAM.get(), 2.4f, 0.9f);
                boolean seen = LampIsle.visible(sl, from, to);
                Vec3 end = seen ? to : from.add(to.subtract(from).normalize().scale(4));
                for (double d = 0; d <= from.distanceTo(end); d += 0.5) {
                    Vec3 q = from.add(end.subtract(from).normalize().scale(d));
                    sl.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 2, 0.05, 0.05, 0.05, 0.01);
                }
                if (seen) {
                    strike(mark, dmg(1.7));
                    Vec3 away = new Vec3(to.x - from.x, 0, to.z - from.z).normalize();
                    mark.push(away.x * 0.8, 0.3, away.z * 0.8);
                    mark.hurtMarked = true;
                    if (!toldStare) {
                        toldStare = true;
                        Notice.narrate(mark, "message.skyabyss.seat.eye_stare");
                    }
                }
            }
            if (t >= STARE_TELL + 6) {
                closedFor = CLOSED;
                playAction("closed");
                playSound(CloudMobsSetup.PILGRIM_CLOSE.get(), 2.0f, 0.9f);
                end(30);
            }
        }

        // 灯の雨: 足もとの光の円 (RAIN_TELL) → 光が落ちる
        private void rain(ServerLevel sl) {
            if (t < RAIN_TELL) {
                if (t % 3 == 0) {
                    for (Vec3 s : spots) {
                        for (int i = 0; i < 14; i++) {
                            double a = i / 14.0 * Math.PI * 2;
                            sl.sendParticles(ParticleTypes.END_ROD, s.x + Math.cos(a) * RAIN_R, s.y + 0.15, s.z + Math.sin(a) * RAIN_R, 1, 0, 0, 0, 0);
                        }
                    }
                }
                return;
            }
            if (t == RAIN_TELL) {
                playSound(CloudMobsSetup.PILGRIM_RAIN.get(), 2.0f, 1.0f);
                for (Vec3 s : spots) {
                    sl.sendParticles(ParticleTypes.FLASH, s.x, s.y + 0.5, s.z, 1, 0, 0, 0, 0);
                    sl.sendParticles(ParticleTypes.END_ROD, s.x, s.y + 4, s.z, 16, 0.3, 2.0, 0.3, 0.02);
                    for (ServerPlayer p : SeatArena.present(sl)) {
                        if (p.position().subtract(s).horizontalDistance() <= RAIN_R + 0.3 && Math.abs(p.getY() - s.y) < 2) {
                            strike(p, dmg(0.9));
                        }
                    }
                }
            }
            if (t >= RAIN_TELL + 10) {
                end(40);
            }
        }

        @Override
        public void stop() {
            move = "";
        }
    }
}
