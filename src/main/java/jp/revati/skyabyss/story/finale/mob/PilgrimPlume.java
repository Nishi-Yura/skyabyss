package jp.revati.skyabyss.story.finale.mob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.finale.PromiseArena;
import jp.revati.skyabyss.story.finale.PromiseIsle;
import jp.revati.skyabyss.town.TownGuestEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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

// 巡礼者の羽 (約束の島の主。docs/design/50 の 4.2.4・一品一案「片方の翼」)。雲上の巡礼者の片翼が先触れに降りてくる。
// 月見の岩の上の主の間の真ん中の HOVER 上に浮かび、動かない (PromiseArena が呼び、片付ける)。前の自分たち四人の術を使って越える:
//   羽ばたき  一人の方へ前の扇 (BEAT_R・半角 BEAT_HALF。BEAT_TELL = 1.2 秒、床に扇の光) → 風。傷 ×1.0 と外への押し出し (盾の円の内は傷が半分・押されない)
//   羽の雨    人みなの足もとと床のあちこちに光の円 (RAIN_R。1 秒) → 羽根が落ちる (傷 ×0.9)
//   先触れの印 段 2 から。一人の頭の上に金の輪 (MARK_TELL = 2.5 秒。最後の 0.5 秒は所が決まる) → 羽の槍が落ちる (半径 MARK_R・傷 ×1.6)。
//             崖の私が引きつけている間は、印は崖の私に付く (誰も傷つかない)
//   審きの光  段 3 で JUDGE_EVERY ごと。床いっぱいに金の光 (JUDGE_TELL = 5 秒) → 盾の円の外の人みなに傷 ×3。倒れても野の私が起こしに来る
// 段は体力 66% と 33% (咆哮 2 秒は傷が通らない)。段 2・段 3 で落ち羽を 2 体呼ぶ
public class PilgrimPlume extends CloudMob {
    public static final String ID = "pilgrim_plume";
    public static final double HP = 1060;
    public static final double DMG = 21;
    public static final double HOVER = 3.0;
    static final int ROAR_TICKS = 40;
    static final int BEAT_TELL = 24;
    static final double BEAT_R = 11;
    static final double BEAT_HALF = 50;
    static final double PUSH = 1.4;
    static final int RAIN_TELL = 20;
    static final double RAIN_R = 1.8;
    static final int MARK_TELL = 50;
    static final int MARK_LOCK = 40;
    static final double MARK_R = 3.0;
    static final int JUDGE_EVERY = 500;
    public static final int JUDGE_TELL = 100;
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.pilgrim_plume"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
    private int phase;
    private int roarFor;
    private long judgeAt = Long.MAX_VALUE;
    private boolean toldMark;
    private boolean toldTaunt;
    private boolean toldJudge;

    public PilgrimPlume(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "beat", "rain", "mark", "judge", "call", "hurt", "death");
        this.xpReward = 200;
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

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        BlockPos home = PromiseIsle.mark("lord_spawn");
        if (Math.abs(getX() - (home.getX() + 0.5)) > 0.3 || Math.abs(getZ() - (home.getZ() + 0.5)) > 0.3 || Math.abs(getY() - (home.getY() + HOVER)) > 0.3) {
            setPos(home.getX() + 0.5, home.getY() + HOVER, home.getZ() + 0.5);
            setDeltaMovement(Vec3.ZERO);
        }
        bar.setProgress(getHealth() / getMaxHealth());
        if (roarFor > 0 && --roarFor == 0) {
            AbyssBossBars.roar(bar, false);
        }
        if (tickCount % 10 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.9, getZ(), 2, 0.4, 0.4, 0.4, 0.01);
        }
        float f = getHealth() / getMaxHealth();
        int want = f <= 0.33f ? 2 : f <= 0.66f ? 1 : 0;
        if (want > phase && isAlive()) {
            phase = want;
            AbyssBossBars.phase(bar, phase);
            AbyssBossBars.roar(bar, true);
            roarFor = ROAR_TICKS;
            playAction("call");
            playSound(CloudMobsSetup.WING_CALL.get(), 2.0f, 0.9f);
            PromiseArena.callPlumes(sl, 2);
            if (phase == 2) {
                judgeAt = sl.getGameTime() + ROAR_TICKS + 60;
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

    // 扇の内か (翼の真下から dir へ BEAT_R、半角 BEAT_HALF)
    boolean inBeat(Vec3 dir, Vec3 p) {
        Vec3 to = new Vec3(p.x - getX(), 0, p.z - getZ());
        double d = to.length();
        if (d > BEAT_R + 0.3 || Math.abs(p.y - (getY() - HOVER)) > 3) {
            return false;
        }
        return d < 1.0 || to.normalize().dot(dir) >= Math.cos(Math.toRadians(BEAT_HALF));
    }

    // ---------------------------------------------------------------- 戦い方 (一度に一つの技。技の間に休み。審きの光が来たらそれを先に)
    final class Fight extends Goal {
        private String move = "";
        private int t;
        private int rest = 60;
        private int turn;
        private Vec3 dir = new Vec3(0, 0, 1);
        private Entity mark;
        private Vec3 markAt = Vec3.ZERO;
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
            if (roarFor > 0) {
                move = "";
                return;
            }
            if (move.isEmpty()) {
                if (getTarget() != null) {
                    getLookControl().setLookAt(getTarget(), 30f, 30f);
                }
                List<ServerPlayer> here = PromiseArena.present(sl);
                if (here.isEmpty()) {
                    return;
                }
                if (phase >= 2 && sl.getGameTime() >= judgeAt) {
                    begin("judge");
                    judgeAt = sl.getGameTime() + JUDGE_EVERY;
                    return;
                }
                if (rest > 0) {
                    rest--;
                    return;
                }
                String[] order = phase >= 1 ? new String[] {"beat", "mark", "rain"} : new String[] {"beat", "rain"};
                String m = order[turn++ % order.length];
                if (m.equals("beat")) {
                    ServerPlayer tg = here.get(random.nextInt(here.size()));
                    Vec3 to = new Vec3(tg.getX() - getX(), 0, tg.getZ() - getZ());
                    dir = to.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : to.normalize();
                } else if (m.equals("rain")) {
                    spots.clear();
                    for (ServerPlayer p : here) {
                        spots.add(p.position());
                    }
                    BlockPos c = PromiseIsle.mark("lord_spawn");
                    for (int i = 0; i < 4; i++) {
                        double a = random.nextDouble() * Math.PI * 2;
                        double d = 3 + random.nextDouble() * 7;
                        spots.add(new Vec3(c.getX() + 0.5 + Math.cos(a) * d, c.getY(), c.getZ() + 0.5 + Math.sin(a) * d));
                    }
                } else {
                    TownGuestEntity cliff = PromiseIsle.taunting(sl);
                    mark = cliff != null && PromiseArena.box().contains(cliff.position()) ? cliff : here.get(random.nextInt(here.size()));
                }
                begin(m);
                return;
            }
            t++;
            switch (move) {
                case "beat" -> beat(sl);
                case "rain" -> rain(sl);
                case "mark" -> mark(sl);
                case "judge" -> judge(sl);
                default -> end(30);
            }
        }

        private void begin(String m) {
            move = m;
            t = 0;
            playAction(m);
            playSound(switch (m) {
                case "beat" -> CloudMobsSetup.WING_BEAT_TELL.get();
                case "rain" -> CloudMobsSetup.WING_RAIN_TELL.get();
                case "mark" -> CloudMobsSetup.WING_MARK.get();
                default -> CloudMobsSetup.WING_JUDGE_TELL.get();
            }, 1.8f, 1.0f);
        }

        private void end(int restTicks) {
            move = "";
            t = 0;
            rest = phase >= 2 ? restTicks * 2 / 3 : restTicks;
        }

        // 羽ばたき: 床に扇の光 (BEAT_TELL) → 風
        private void beat(ServerLevel sl) {
            Vec3 base = new Vec3(getX(), getY() - HOVER, getZ());
            if (t < BEAT_TELL) {
                if (t % 3 == 0) {
                    for (double d = 2; d <= BEAT_R; d += 1.5) {
                        int n = (int) (d * 1.4);
                        for (int i = 0; i <= n; i++) {
                            double a = Math.toRadians(-BEAT_HALF + 2 * BEAT_HALF * i / n);
                            Vec3 q = base.add(dir.yRot((float) a).scale(d));
                            sl.sendParticles(ParticleTypes.WAX_ON, q.x, q.y + 0.15, q.z, 1, 0, 0, 0, 0);
                        }
                    }
                }
                return;
            }
            if (t == BEAT_TELL) {
                playSound(CloudMobsSetup.WING_BEAT.get(), 2.0f, 1.0f);
                for (double d = 2; d <= BEAT_R; d += 1.0) {
                    Vec3 q = base.add(dir.scale(d));
                    sl.sendParticles(ParticleTypes.CLOUD, q.x, q.y + 0.6, q.z, 4, d * 0.3, 0.3, d * 0.3, 0.05);
                }
                for (ServerPlayer p : PromiseArena.present(sl)) {
                    if (!inBeat(dir, p.position())) {
                        continue;
                    }
                    strike(p, dmg());
                    if (!PromiseIsle.shielded(p)) {
                        Vec3 away = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
                        away = away.lengthSqr() < 0.01 ? dir : away.normalize();
                        p.push(away.x * PUSH, 0.35, away.z * PUSH);
                        p.hurtMarked = true;
                    }
                }
            }
            if (t >= BEAT_TELL + 10) {
                end(40);
            }
        }

        // 羽の雨: 光の円 (RAIN_TELL) → 羽根が落ちる
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
                playSound(CloudMobsSetup.WING_RAIN.get(), 1.8f, 1.0f);
                for (Vec3 s : spots) {
                    sl.sendParticles(ParticleTypes.END_ROD, s.x, s.y + 4, s.z, 14, 0.3, 2.0, 0.3, 0.02);
                    sl.sendParticles(ParticleTypes.CRIT, s.x, s.y + 0.3, s.z, 10, RAIN_R * 0.4, 0.1, RAIN_R * 0.4, 0.05);
                    for (ServerPlayer p : PromiseArena.present(sl)) {
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

        // 先触れの印: 頭の上の金の輪と足もとの円 (MARK_LOCK まで付いていく) → 羽の槍が落ちる
        private void mark(ServerLevel sl) {
            if (mark == null || !mark.isAlive() || mark.level() != sl) {
                end(20);
                return;
            }
            if (t <= MARK_LOCK) {
                markAt = mark.position();
            }
            if (t == 1 && mark instanceof TownGuestEntity) {
                for (ServerPlayer p : PromiseArena.present(sl)) {
                    if (!toldTaunt) {
                        Notice.narrate(p, "message.skyabyss.isle4.lord_mark_taunt");
                    }
                }
                toldTaunt = true;
            } else if (t == 1 && mark instanceof ServerPlayer mp && !toldMark) {
                toldMark = true;
                Notice.narrate(mp, "message.skyabyss.isle4.lord_mark");
            }
            if (t < MARK_TELL) {
                if (t % 3 == 0) {
                    for (int i = 0; i < 10; i++) {
                        double a = i / 10.0 * Math.PI * 2 + t * 0.1;
                        sl.sendParticles(ParticleTypes.WAX_ON, mark.getX() + Math.cos(a) * 0.6, mark.getY() + mark.getBbHeight() + 0.8, mark.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
                    }
                    for (int i = 0; i < 18; i++) {
                        double a = i / 18.0 * Math.PI * 2;
                        sl.sendParticles(t > MARK_LOCK ? ParticleTypes.FLAME : ParticleTypes.WAX_ON, markAt.x + Math.cos(a) * MARK_R, markAt.y + 0.15,
                                markAt.z + Math.sin(a) * MARK_R, 1, 0, 0, 0, 0);
                    }
                }
                return;
            }
            if (t == MARK_TELL) {
                playSound(CloudMobsSetup.WING_PLUNGE.get(), 2.0f, 1.0f);
                for (double y = 8; y >= 0; y -= 0.5) {
                    sl.sendParticles(ParticleTypes.END_ROD, markAt.x, markAt.y + y, markAt.z, 2, 0.05, 0.05, 0.05, 0.01);
                }
                sl.sendParticles(ParticleTypes.FLASH, markAt.x, markAt.y + 0.5, markAt.z, 1, 0, 0, 0, 0);
                for (ServerPlayer p : PromiseArena.present(sl)) {
                    if (p.position().subtract(markAt).horizontalDistance() <= MARK_R + 0.3 && Math.abs(p.getY() - markAt.y) < 2.5) {
                        strike(p, dmg(1.6));
                    }
                }
            }
            if (t >= MARK_TELL + 10) {
                end(40);
            }
        }

        // 審きの光: 床いっぱいの金の光と、谷の私の盾の円 (JUDGE_TELL) → 盾の円の外の人みなに傷
        private void judge(ServerLevel sl) {
            BlockPos c = PromiseIsle.mark("lord_spawn");
            if (t == 1) {
                for (ServerPlayer p : PromiseArena.present(sl)) {
                    if (!toldJudge) {
                        Notice.narrate(p, "message.skyabyss.isle4.lord_judge");
                    }
                }
                toldJudge = true;
            }
            if (t < JUDGE_TELL) {
                if (t % 4 == 0) {
                    for (int i = 0; i < 40; i++) {
                        double a = random.nextDouble() * Math.PI * 2;
                        double d = Math.sqrt(random.nextDouble()) * 12;
                        sl.sendParticles(ParticleTypes.WAX_ON, c.getX() + 0.5 + Math.cos(a) * d, c.getY() + 0.2, c.getZ() + 0.5 + Math.sin(a) * d, 1, 0, 0.1, 0, 0);
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
            if (t == JUDGE_TELL) {
                playSound(CloudMobsSetup.WING_JUDGE.get(), 2.4f, 1.0f);
                sl.sendParticles(ParticleTypes.FLASH, c.getX() + 0.5, c.getY() + 2, c.getZ() + 0.5, 3, 4, 1, 4, 0);
                sl.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 6, c.getZ() + 0.5, 120, 6, 4, 6, 0.1);
                for (ServerPlayer p : PromiseArena.present(sl)) {
                    if (!PromiseIsle.shielded(p)) {
                        strike(p, dmg(3.0));
                    }
                }
            }
            if (t >= JUDGE_TELL + 14) {
                end(50);
            }
        }

        @Override
        public void stop() {
            move = "";
        }
    }
}
