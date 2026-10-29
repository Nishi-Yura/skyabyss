package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.finale.BellArena;
import jp.revati.skyabyss.story.finale.BellIsle;
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

// 最後の鐘守 (鐘の島の主。docs/design/50 の 4.2.1・一品一案「大鐘を背負う鐘守」)。鐘楼の上の主の間の真ん中から動かない (BellArena が呼び、片付ける)。
//   槌の振り下ろし  近くの人の足もとに光の円 (1.3 秒) → 円の内に傷 ×1.3 と打ち上げ
//   鐘の波          背の鐘をためて (1.4 秒) 鳴らす: 主から輪が広がる (半径 16 まで)。輪が通るとき床に足が着いていると傷。跳べば越せる
//   無音            左手を面にかざし (1.2 秒)、主の間の鐘の固まりをすべて消す (鐘雲の輪がほどける)。まわりの小さな鐘を鳴らして戻す
// 段は体力 66% と 33% (咆哮 2 秒は傷が通らない)。段 2 で亡者を 2 体呼ぶ。段 3 でも 2 体呼び、床の東の半分が鐘雲になる (BellArena.crumble)
public class LastBellkeeper extends CloudMob {
    public static final String ID = "last_bellkeeper";
    // 生き物の体力の上限 (1024) に収まる数
    public static final double HP = 980;
    public static final double DMG = 18;
    static final int ROAR_TICKS = 40;
    static final int SLAM_TELL = 26;
    static final double SLAM_R = 3.0;
    static final int WAVE_TELL = 28;
    static final double WAVE_STEP = 0.6;
    static final double WAVE_MAX = 16;
    static final int SILENCE_TELL = 24;
    static final int SILENCE_EVERY = 300;
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.last_bellkeeper"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
    private int phase;
    private int roarFor;
    private int sinceSilence;
    private boolean toldSilence;
    private boolean toldWave;

    public LastBellkeeper(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "slam", "wave", "silence", "call", "hurt", "death");
        this.xpReward = 180;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
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

    // 主の間の床の上 (人の足の高さ)
    static double floorY() {
        return BellIsle.mark("arena").getY() + 1;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        // 真ん中から動かない
        BlockPos home = BellIsle.mark("lord_spawn");
        if (Math.abs(getX() - (home.getX() + 0.5)) > 0.3 || Math.abs(getZ() - (home.getZ() + 0.5)) > 0.3) {
            setPos(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            setDeltaMovement(Vec3.ZERO);
        }
        bar.setProgress(getHealth() / getMaxHealth());
        sinceSilence++;
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
            playSound(CloudMobsSetup.KEEPER_CALL.get(), 2.0f, 0.8f);
            BellArena.callMourners(sl, 2);
            if (phase == 2) {
                BellArena.crumble(sl);
                for (ServerPlayer p : BellArena.present(sl)) {
                    Notice.narrate(p, "message.skyabyss.isle1.lord_crumble");
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
        private Vec3 spot = Vec3.ZERO;
        private final Set<Integer> hit = new HashSet<>();
        private boolean lastWave;

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
            getNavigation().stop();
            if (roarFor > 0) {
                move = "";
                return;
            }
            if (move.isEmpty()) {
                getLookControl().setLookAt(tg, 30f, 30f);
                if (rest > 0) {
                    rest--;
                    return;
                }
                if (sinceSilence >= SILENCE_EVERY && BellIsle.ringingNear(sl, BellIsle.mark("arena"), 14) > 0) {
                    begin("silence");
                } else if (distanceTo(tg) <= 7.5 && (lastWave || random.nextFloat() < 0.6f)) {
                    begin("slam");
                    spot = tg.position();
                } else {
                    begin("wave");
                }
                return;
            }
            t++;
            switch (move) {
                case "slam" -> slam(sl);
                case "wave" -> wave(sl);
                case "silence" -> silence(sl);
                default -> end(20);
            }
        }

        private void begin(String m) {
            move = m;
            t = 0;
            hit.clear();
            lastWave = m.equals("wave");
            playAction(m);
            switch (m) {
                case "slam" -> playSound(CloudMobsSetup.KEEPER_SLAM_TELL.get(), 1.6f, 0.9f);
                case "wave" -> playSound(CloudMobsSetup.KEEPER_WAVE_TELL.get(), 1.6f, 0.9f);
                default -> playSound(CloudMobsSetup.KEEPER_SILENCE.get(), 1.4f, 0.8f);
            }
        }

        private void end(int restTicks) {
            move = "";
            t = 0;
            rest = phase >= 2 ? restTicks * 2 / 3 : restTicks;
        }

        // 槌の振り下ろし: 足もとの光の円 (SLAM_TELL) → 円の内に傷
        private void slam(ServerLevel sl) {
            if (t < SLAM_TELL) {
                getLookControl().setLookAt(spot.x, spot.y, spot.z);
                if (t % 3 == 0) {
                    int n = 20;
                    for (int i = 0; i < n; i++) {
                        double a = i / (double) n * Math.PI * 2;
                        sl.sendParticles(ParticleTypes.END_ROD, spot.x + Math.cos(a) * SLAM_R, spot.y + 0.15, spot.z + Math.sin(a) * SLAM_R, 1, 0, 0, 0, 0);
                    }
                    sl.sendParticles(ParticleTypes.WAX_OFF, spot.x, spot.y + 0.2, spot.z, 6, SLAM_R * 0.4, 0, SLAM_R * 0.4, 0);
                }
                return;
            }
            if (t == SLAM_TELL + 3) {
                playSound(CloudMobsSetup.KEEPER_SLAM.get(), 2.0f, 0.8f);
                sl.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.4, spot.z, 2, 0.6, 0.1, 0.6, 0);
                sl.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.2, spot.z, 24, SLAM_R * 0.5, 0.1, SLAM_R * 0.5, 0.08);
                for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(spot, spot).inflate(SLAM_R + 0.3, 2.0, SLAM_R + 0.3), e -> e.isAlive() && !e.isSpectator())) {
                    if (p.position().subtract(spot).horizontalDistance() <= SLAM_R + 0.3) {
                        strike(p, dmg(1.3));
                        p.push(0, 0.55, 0);
                        p.hurtMarked = true;
                    }
                }
            }
            if (t >= 40) {
                end(30);
            }
        }

        // 鐘の波: ため (WAVE_TELL) → 主から輪が広がる。輪の通る所で床に足が着いていると傷
        private void wave(ServerLevel sl) {
            Vec3 c = position();
            if (t < WAVE_TELL) {
                if (t % 4 == 0) {
                    sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 3.5, c.z, 6, 0.8, 0.8, 0.8, 0.02);
                }
                return;
            }
            if (t == WAVE_TELL + 2) {
                playSound(CloudMobsSetup.KEEPER_WAVE.get(), 2.5f, 0.7f);
            }
            double r = (t - WAVE_TELL - 2) * WAVE_STEP;
            if (r < 0) {
                return;
            }
            if (r <= WAVE_MAX) {
                int n = 16 + (int) (r * 5);
                for (int i = 0; i < n; i += (t % 2 == 0 ? 1 : 2)) {
                    double a = i / (double) n * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, floorY() + 0.25, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r + 1, 4, r + 1), e -> e.isAlive() && !e.isSpectator())) {
                    double d = p.position().subtract(c).horizontalDistance();
                    if (Math.abs(d - r) <= 0.8 && p.getY() - floorY() < 0.6 && hit.add(p.getId())) {
                        strike(p, dmg());
                        if (!toldWave && p instanceof ServerPlayer sp) {
                            toldWave = true;
                            Notice.narrate(sp, "message.skyabyss.isle1.lord_wave");
                        }
                    }
                }
                return;
            }
            end(40);
        }

        // 無音: かざし (SILENCE_TELL) → 主の間の鐘の固まりを消す
        private void silence(ServerLevel sl) {
            if (t < SILENCE_TELL) {
                if (t % 3 == 0) {
                    sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 3.6, getZ(), 2, 0.6, 0.3, 0.6, 0.01);
                }
                return;
            }
            if (t == SILENCE_TELL) {
                sinceSilence = 0;
                BellIsle.silence(sl, BellIsle.mark("arena"), 20);
                sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 3.0, getZ(), 1, 0, 0, 0, 0);
                if (!toldSilence) {
                    toldSilence = true;
                    for (ServerPlayer p : BellArena.present(sl)) {
                        Notice.narrate(p, "message.skyabyss.isle1.lord_silence");
                    }
                }
            }
            if (t >= 48) {
                end(20);
            }
        }

        @Override
        public void stop() {
            move = "";
        }
    }
}
