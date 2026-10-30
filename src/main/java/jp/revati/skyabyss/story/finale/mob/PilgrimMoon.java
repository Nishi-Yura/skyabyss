package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;
import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.finale.Echoes;
import jp.revati.skyabyss.story.finale.PilgrimSeat;
import jp.revati.skyabyss.story.finale.PromiseIsle;
import jp.revati.skyabyss.story.finale.SeatArena;
import jp.revati.skyabyss.town.TownGuestEntity;
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
import net.minecraft.world.phys.Vec3;

// 巡礼者の月 (ラスボス「雲上の巡礼者」の第 3 形態。docs/design/50 の 4.3・一品一案「鎖の月」)。座の真ん中の HOVER 上に浮かび、動かない (SeatArena が呼ぶ)。
// 月を引き寄せて時が乱れる。打ちは同じ所でもう一度起こる (島 3 の Echoes)。前の自分たち四人が加勢する (術は島 4 と同じ。PromiseIsle):
//   月の引き    座の人みなを真ん中へ引き寄せ (PULL_TELL = 2 秒。縮む輪の光。外へ歩けば踏みとどまれる・盾の円の内は引かれない) → 真ん中が弾ける (半径 BURST_R・傷 ×1.5)
//   巻き戻し    人みなの足もとに残り火の印 → 2 秒後と 4 秒後に同じ所が燃える (傷 ×1.0)。動いていれば当たらない
//   満ち欠けの刃 一人の方へ前の扇 (半径 SWEEP_R・半角 SWEEP_HALF、予告 1.2 秒) → 薙ぐ (傷 ×1.2)。同じ扇が 3 秒後にもう一度。崖の私が吼えている間は崖の私の方へ
//   月落とし    段 2 から FALL_EVERY ごと。床いっぱいに月の影 (FALL_TELL = 5 秒) → 盾の円の外の人みなに傷 ×3。倒れても野の私が起こす
// 段は体力 60% と 30% (咆哮 2 秒は傷が通らない)。段 3 は技の間の休みが短い
public class PilgrimMoon extends CloudMob {
    public static final String ID = "pilgrim_moon";
    public static final double HP = 1800;
    public static final double DMG = 24;
    public static final double HOVER = 1.6;
    static final int ROAR_TICKS = 40;
    static final int PULL_TELL = 40;
    static final double BURST_R = 5;
    static final double PULL = 0.07;
    static final int SWEEP_TELL = 24;
    static final double SWEEP_R = 13;
    static final double SWEEP_HALF = 70;
    static final int ECHO = 60;
    static final int FALL_EVERY = 500;
    public static final int FALL_TELL = 100;
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.pilgrim_moon"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10));
    private int phase;
    private int roarFor;
    private long fallAt = Long.MAX_VALUE;
    private boolean toldFall;
    private boolean toldPull;

    public PilgrimMoon(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "pull", "sweep", "rewind", "fall", "call", "hurt", "death");
        this.xpReward = 300;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
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

    Vec3 floor() {
        BlockPos c = PilgrimSeat.mark("lord_spawn");
        return new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        Vec3 f = floor();
        if (Math.abs(getX() - f.x) > 0.3 || Math.abs(getZ() - f.z) > 0.3 || Math.abs(getY() - (f.y + HOVER)) > 0.3) {
            setPos(f.x, f.y + HOVER, f.z);
            setDeltaMovement(Vec3.ZERO);
        }
        bar.setProgress(getHealth() / getMaxHealth());
        if (roarFor > 0 && --roarFor == 0) {
            AbyssBossBars.roar(bar, false);
        }
        if (tickCount % 10 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 3.2, getZ(), 2, 1.4, 1.4, 1.4, 0.01);
        }
        float hp = getHealth() / getMaxHealth();
        int want = hp <= 0.30f ? 2 : hp <= 0.60f ? 1 : 0;
        if (want > phase && isAlive()) {
            phase = want;
            AbyssBossBars.phase(bar, phase);
            AbyssBossBars.roar(bar, true);
            roarFor = ROAR_TICKS;
            playAction("call");
            playSound(CloudMobsSetup.PILGRIM_ROAR.get(), 2.4f, 0.8f);
            if (phase == 1) {
                fallAt = sl.getGameTime() + ROAR_TICKS + 60;
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

    // 扇の内か (月の真下から dir へ SWEEP_R、半角 SWEEP_HALF)
    boolean inSweep(Vec3 dir, Vec3 p) {
        Vec3 f = floor();
        Vec3 to = new Vec3(p.x - f.x, 0, p.z - f.z);
        double d = to.length();
        if (d > SWEEP_R + 0.3 || Math.abs(p.y - f.y) > 3) {
            return false;
        }
        return d < 1.0 || to.normalize().dot(dir) >= Math.cos(Math.toRadians(SWEEP_HALF));
    }

    // ---------------------------------------------------------------- 戦い方 (一度に一つの技。月落としが来たらそれを先に)
    final class Fight extends Goal {
        private String move = "";
        private int t;
        private int rest = 60;
        private int turn;
        private Vec3 dir = new Vec3(0, 0, 1);

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
            if (roarFor > 0) {
                move = "";
                return;
            }
            if (move.isEmpty()) {
                List<ServerPlayer> here = SeatArena.present(sl);
                if (here.isEmpty()) {
                    return;
                }
                if (phase >= 1 && sl.getGameTime() >= fallAt) {
                    begin("fall");
                    fallAt = sl.getGameTime() + FALL_EVERY;
                    return;
                }
                if (rest > 0) {
                    rest--;
                    return;
                }
                String m = new String[] {"sweep", "rewind", "pull"}[turn++ % 3];
                if (m.equals("sweep")) {
                    TownGuestEntity cliff = PromiseIsle.taunting(sl);
                    Vec3 at = cliff != null ? cliff.position() : here.get(random.nextInt(here.size())).position();
                    Vec3 to = new Vec3(at.x - getX(), 0, at.z - getZ());
                    dir = to.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : to.normalize();
                }
                begin(m);
                return;
            }
            t++;
            switch (move) {
                case "pull" -> pull(sl);
                case "sweep" -> sweep(sl);
                case "rewind" -> rewind(sl);
                case "fall" -> fall(sl);
                default -> end(30);
            }
        }

        private void begin(String m) {
            move = m;
            t = 0;
            playAction(m);
            playSound(switch (m) {
                case "pull" -> CloudMobsSetup.MOON_PULL_TELL.get();
                case "sweep" -> CloudMobsSetup.MOON_SWEEP.get();
                case "rewind" -> CloudMobsSetup.MOON_REWIND.get();
                default -> CloudMobsSetup.MOON_FALL_TELL.get();
            }, 2.0f, 1.0f);
        }

        private void end(int restTicks) {
            move = "";
            t = 0;
            rest = phase >= 2 ? restTicks / 2 : restTicks;
        }

        // 月の引き: 縮む輪の光と引き寄せ → 真ん中が弾ける
        private void pull(ServerLevel sl) {
            Vec3 f = floor();
            if (t < PULL_TELL) {
                double r = 16 - 14 * t / (double) PULL_TELL;
                if (t % 2 == 0) {
                    for (int i = 0; i < 28; i++) {
                        double a = i / 28.0 * Math.PI * 2;
                        sl.sendParticles(ParticleTypes.END_ROD, f.x + Math.cos(a) * r, f.y + 0.3, f.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    }
                    for (int i = 0; i < 12; i++) {
                        double a = i / 12.0 * Math.PI * 2;
                        sl.sendParticles(ParticleTypes.WAX_ON, f.x + Math.cos(a) * BURST_R, f.y + 0.15, f.z + Math.sin(a) * BURST_R, 1, 0, 0, 0, 0);
                    }
                }
                for (ServerPlayer p : SeatArena.present(sl)) {
                    if (PromiseIsle.shielded(p) || Downed.isDowned(p)) {
                        continue;
                    }
                    Vec3 in = new Vec3(f.x - p.getX(), 0, f.z - p.getZ());
                    if (in.lengthSqr() > 1) {
                        in = in.normalize();
                        p.push(in.x * PULL, 0, in.z * PULL);
                        p.hurtMarked = true;
                    }
                    if (!toldPull) {
                        toldPull = true;
                        Notice.narrate(p, "message.skyabyss.seat.moon_pull");
                    }
                }
                return;
            }
            if (t == PULL_TELL) {
                playSound(CloudMobsSetup.MOON_PULL.get(), 2.4f, 1.0f);
                sl.sendParticles(ParticleTypes.FLASH, f.x, f.y + 1, f.z, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.END_ROD, f.x, f.y + 1, f.z, 60, BURST_R * 0.5, 0.5, BURST_R * 0.5, 0.15);
                for (ServerPlayer p : SeatArena.present(sl)) {
                    if (p.position().subtract(f).horizontalDistance() <= BURST_R + 0.3 && Math.abs(p.getY() - f.y) < 3) {
                        strike(p, dmg(1.5));
                    }
                }
            }
            if (t >= PULL_TELL + 10) {
                end(40);
            }
        }

        // 満ち欠けの刃: 扇の光 → 薙ぐ。同じ扇が ECHO 後にもう一度
        private void sweep(ServerLevel sl) {
            Vec3 f = floor();
            if (t < SWEEP_TELL) {
                if (t % 3 == 0) {
                    for (double d = 2; d <= SWEEP_R; d += 1.5) {
                        int n = (int) (d * 2);
                        for (int i = 0; i <= n; i++) {
                            double a = Math.toRadians(-SWEEP_HALF + 2 * SWEEP_HALF * i / n);
                            Vec3 q = f.add(dir.yRot((float) a).scale(d));
                            sl.sendParticles(ParticleTypes.WAX_ON, q.x, q.y + 0.15, q.z, 1, 0, 0, 0, 0);
                        }
                    }
                }
                return;
            }
            if (t == SWEEP_TELL) {
                playSound(CloudMobsSetup.MOON_SWEEP.get(), 2.4f, 0.8f);
                for (double d = 2; d <= SWEEP_R; d += 1.5) {
                    Vec3 q = f.add(dir.scale(d));
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, q.x, q.y + 1, q.z, 1, 0, 0, 0, 0);
                }
                for (ServerPlayer p : SeatArena.present(sl)) {
                    if (inSweep(dir, p.position())) {
                        strike(p, dmg(1.2));
                    }
                }
                Echoes.cone(sl, f, dir, SWEEP_R, SWEEP_HALF, ECHO, dmg(), PilgrimMoon.this);
            }
            if (t >= SWEEP_TELL + 10) {
                end(40);
            }
        }

        // 巻き戻し: 人みなの足もとが 2 秒後と 4 秒後にもう一度燃える
        private void rewind(ServerLevel sl) {
            if (t == 10) {
                for (ServerPlayer p : SeatArena.present(sl)) {
                    if (Downed.isDowned(p)) {
                        continue;
                    }
                    Echoes.circle(sl, p.position(), 2.2, 40, dmg(), PilgrimMoon.this);
                    Echoes.circle(sl, p.position(), 2.2, 80, dmg(), PilgrimMoon.this);
                }
            }
            if (t >= 30) {
                end(50);
            }
        }

        // 月落とし: 床いっぱいの月の影と、谷の私の盾の円 → 盾の円の外の人みなに傷
        private void fall(ServerLevel sl) {
            Vec3 f = floor();
            if (t == 1) {
                for (ServerPlayer p : SeatArena.present(sl)) {
                    if (!toldFall) {
                        Notice.narrate(p, "message.skyabyss.seat.moon_fall");
                    }
                }
                toldFall = true;
            }
            if (t < FALL_TELL) {
                if (t % 4 == 0) {
                    for (int i = 0; i < 50; i++) {
                        double a = random.nextDouble() * Math.PI * 2;
                        double d = Math.sqrt(random.nextDouble()) * 16;
                        sl.sendParticles(ParticleTypes.SQUID_INK, f.x + Math.cos(a) * d, f.y + 0.3, f.z + Math.sin(a) * d, 1, 0, 0.05, 0, 0);
                    }
                    TownGuestEntity valley = PromiseIsle.active(sl, 1);
                    if (valley != null) {
                        for (int i = 0; i < 24; i++) {
                            double a = i / 24.0 * Math.PI * 2;
                            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, valley.getX() + Math.cos(a) * PromiseIsle.SHIELD_R, valley.getY() + 0.3,
                                    valley.getZ() + Math.sin(a) * PromiseIsle.SHIELD_R, 1, 0, 0.05, 0, 0);
                        }
                    }
                }
                return;
            }
            if (t == FALL_TELL) {
                playSound(CloudMobsSetup.MOON_FALL.get(), 2.6f, 0.9f);
                sl.sendParticles(ParticleTypes.FLASH, f.x, f.y + 2, f.z, 3, 4, 1, 4, 0);
                sl.sendParticles(ParticleTypes.END_ROD, f.x, f.y + 3, f.z, 160, 8, 2, 8, 0.12);
                for (ServerPlayer p : SeatArena.present(sl)) {
                    if (!PromiseIsle.shielded(p)) {
                        strike(p, dmg(3.0));
                    }
                }
            }
            if (t >= FALL_TELL + 14) {
                end(50);
            }
        }

        @Override
        public void stop() {
            move = "";
        }
    }
}
