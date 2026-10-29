package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.ch6.field.FallingLight;
import jp.revati.skyabyss.story.ch6.field.FieldArena;
import jp.revati.skyabyss.story.ch6.field.FieldTicker;
import jp.revati.skyabyss.story.ch6.field.FieldTide;
import jp.revati.skyabyss.story.ch6.field.MoonField;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 望の灯守 (満月の野の主。docs/design/46 の 4.5・一品一案「珠を抱く灯台守」)。満月の台の上の主の間で戦う (FieldArena が呼び、片付ける)。
//   光溜め   満ちのあいだ胸の珠に光を溜める (傷 ×0.35)
//   光放ち   満ちから引きへ移るとき、溜まっていれば (半分より上) 2 秒の予告 (珠が膨らみ、床に光の輪) のあと主の間じゅうに光の波 (傷 ×1.35)。
//            引きで立つ影の柱の陰 (主との間に足場の影の石) にいれば当たらない。予告のあいだに一瞬止めを主に当てると放ちが崩れ、
//            主は 6 秒膝をつく (傷 ×1.6)。放ったあとも 4 秒は空っぽ (傷 ×1.3)
//   灯の薙ぎ 近くでは灯の杖で前の 140 度を薙ぐ (予告 0.9 秒)
//   光の球   遠くの人へ光の球を 3 つ (飛び物。一瞬止めで止まる)
// 段は体力 66% と 33% (咆哮 2 秒は傷が通らない)。段 2 で光の群れを 2 体呼ぶ。段 3 は満ち引きが 2 倍の速さ (FieldTide.fast)
public class FullKeeper extends MoonMob {
    public static final String ID = "full_keeper";
    // 生き物の体力の上限 (1024) に、満月の 1.2 倍でも収まる数
    public static final double HP = 850;
    public static final double DMG = 16;
    static final int ROAR_TICKS = 40;
    static final int CHARGE_FULL = 160;
    static final int RELEASE_TELL = 40;
    static final int KNEEL_TICKS = 120;
    static final int SPENT_TICKS = 80;
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.full_keeper"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10));
    private int phase;
    private int roarFor;
    private int charge;
    private int release = -1;
    private int kneelFor;
    private int spentFor;
    private boolean toldKneel;
    private boolean toldShade;

    public FullKeeper(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "charge", "release", "kneel", "sweep", "orbs", "roar", "hurt", "death");
        this.xpReward = 160;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 60);
    }

    public int phase() {
        return phase;
    }

    public int charge() {
        return charge;
    }

    // 自己テスト: 光を溜めきった所から
    public void chargeForTest(int c) {
        charge = c;
    }

    public boolean releasing() {
        return release >= 0;
    }

    public boolean kneeling() {
        return kneelFor > 0;
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

    Vec3 orb() {
        return position().add(0, 2.7, 0);
    }

    // ---------------------------------------------------------------- 一瞬止めに当たった (MomentStop.freeze から)
    public void stopped() {
        if (release < 0 || !(level() instanceof ServerLevel sl)) {
            return;
        }
        release = -1;
        charge = 0;
        kneelFor = KNEEL_TICKS;
        playAction("kneel");
        playSound(MoonMobsSetup.KEEPER_KNEEL.get(), 2.0f, 0.8f);
        sl.sendParticles(ParticleTypes.END_ROD, orb().x, orb().y, orb().z, 50, 1.0, 1.0, 1.0, 0.15);
        if (!toldKneel) {
            toldKneel = true;
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24), e -> e.isAlive())) {
                Notice.narrate(p, "message.skyabyss.moon.keeper_kneel");
            }
        }
    }

    // 光溜めの構えは放つ (か膝をつく) まで保つ。溜めの最中は被弾の動きで構えを崩さない
    @Override
    protected boolean holdsAction(String action) {
        return action.equals("charge");
    }

    @Override
    protected boolean hurtAnimBlocked() {
        return charge > 0 && release < 0;
    }

    // ---------------------------------------------------------------- 毎 tick
    @Override
    public void tick() {
        super.tick();
        // 段 5 の位相の重ね (野): 主が立っているあいだ、満ち引きが 2 倍の速さ
        if (!level().isClientSide && overlaid() && !jp.revati.skyabyss.story.ch6.field.FieldTide.fast()) {
            jp.revati.skyabyss.story.ch6.field.FieldTide.setFast(true);
        }
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        bar.setProgress(getHealth() / getMaxHealth());
        if (roarFor > 0 && --roarFor == 0) {
            AbyssBossBars.roar(bar, false);
        }
        if (kneelFor > 0) {
            kneelFor--;
        }
        if (spentFor > 0) {
            spentFor--;
        }
        FieldTide.Tide tide = FieldTide.tide(sl);
        if (release < 0 && kneelFor == 0 && roarFor == 0) {
            if (tide == FieldTide.Tide.BRIGHT) {
                if (charge == 0) {
                    playAction("charge");
                }
                charge = Math.min(CHARGE_FULL, charge + (FieldTide.fast() ? 2 : 1));
                if (tickCount % 4 == 0) {
                    // 珠へ光が集まる
                    double a = random.nextDouble() * Math.PI * 2;
                    Vec3 o = orb();
                    sl.sendParticles(ParticleTypes.END_ROD, o.x + Math.cos(a) * 3, o.y + 1, o.z + Math.sin(a) * 3, 0, -Math.cos(a), -0.3, -Math.sin(a), 0.15);
                }
            } else if (tide == FieldTide.Tide.TO_DIM && charge >= CHARGE_FULL / 2) {
                release = 0;
                playAction("release");
                playSound(MoonMobsSetup.KEEPER_CHARGE.get(), 2.0f, 0.9f);
            } else if (tide != FieldTide.Tide.BRIGHT) {
                if (charge > 0) {
                    // 溜めきらずに引きへ移った: 構えを解く
                    stopTriggeredAnim("action", "charge");
                }
                charge = 0;
            }
        }
        if (release >= 0) {
            tickRelease(sl);
        }
        float f = getHealth() / getMaxHealth();
        int want = f <= 0.33f ? 2 : f <= 0.66f ? 1 : 0;
        if (want > phase && isAlive()) {
            phase = want;
            AbyssBossBars.phase(bar, phase);
            AbyssBossBars.roar(bar, true);
            roarFor = ROAR_TICKS;
            release = -1;
            kneelFor = 0;
            playAction("roar");
            playSound(MoonMobsSetup.KEEPER_ROAR.get(), 2.0f, 0.8f);
            if (phase == 1) {
                for (int i = 0; i < 2; i++) {
                    BlockPos at = BlockPos.containing(position().add((i == 0 ? 4 : -4), 0, 3));
                    MoonMob m = FieldTicker.put(sl, MoonMobsSetup.LIGHT_SWARM.get(), at);
                    if (m != null) {
                        m.addTag(FieldArena.TAG);
                    }
                }
            } else if (phase == 2) {
                FieldTide.setFast(true);
            }
        }
    }

    // 光放ち: 予告 (RELEASE_TELL) → 波
    private void tickRelease(ServerLevel sl) {
        release++;
        Vec3 c = MoonField.mark("arena_center").getCenter();
        Vec3 o = orb();
        if (release < RELEASE_TELL) {
            if (release % 3 == 0) {
                double r = 15.0 * release / RELEASE_TELL;
                int n = 20 + (int) (r * 4);
                for (int i = 0; i < n; i++) {
                    double a = i / (double) n * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y - 0.4, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                sl.sendParticles(ParticleTypes.FLASH, o.x, o.y, o.z, 1, 0, 0, 0, 0);
            }
            return;
        }
        if (release == RELEASE_TELL) {
            playSound(MoonMobsSetup.KEEPER_RELEASE.get(), 2.5f, 0.8f);
            for (int i = 0; i < 60; i++) {
                double a = i / 60.0 * Math.PI * 2;
                sl.sendParticles(ParticleTypes.END_ROD, o.x, o.y, o.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.9);
            }
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(17, 6, 17), e -> e.isAlive() && !e.isSpectator())) {
                if (shaded(sl, p)) {
                    continue;
                }
                strike(p, dmg(1.35));
                if (!toldShade) {
                    toldShade = true;
                    Notice.narrate(p, "message.skyabyss.moon.keeper_shade");
                }
            }
            release = -1;
            charge = 0;
            spentFor = SPENT_TICKS;
        }
    }

    // 主の珠と人の目の間に足場 (影の柱や岩) があるか
    boolean shaded(ServerLevel sl, Player p) {
        HitResult hit = sl.clip(new ClipContext(orb(), p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (roarFor > 0) {
            return false;
        }
        float k = kneelFor > 0 ? 1.6f : spentFor > 0 ? 1.3f : charge > 0 && release < 0 ? 0.35f : 1.0f;
        return super.hurt(source, amount * k);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bar.removeAllPlayers();
        FieldTide.setFast(false);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && overlaid()) {
            jp.revati.skyabyss.story.ch6.field.FieldTide.setFast(false);
        }
        super.remove(reason);
        bar.removeAllPlayers();
    }

    // ---------------------------------------------------------------- 戦い方 (一度に一つの技。技の間に休み)
    final class Fight extends Goal {
        private String move = "";
        private int t;
        private int rest = 40;
        private int shots;

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
            if (roarFor > 0 || kneelFor > 0 || release >= 0) {
                getNavigation().stop();
                move = "";
                return;
            }
            getLookControl().setLookAt(tg, 30f, 30f);
            if (move.isEmpty()) {
                if (rest > 0) {
                    rest--;
                    approach(tg);
                    return;
                }
                double d = distanceTo(tg);
                begin(d <= 5.5 ? "sweep" : "orbs");
                return;
            }
            t++;
            switch (move) {
                case "sweep" -> sweep(sl);
                case "orbs" -> orbs(sl, tg);
                default -> end(20);
            }
        }

        // 主の間の中で、獲物から 6 ほどを保つ
        private void approach(LivingEntity tg) {
            Vec3 c = MoonField.mark("arena_center").getCenter();
            if (position().distanceTo(c) > 11) {
                getNavigation().moveTo(c.x, c.y, c.z, 0.9);
            } else if (distanceTo(tg) > 7 && tickCount % 10 == 0) {
                getNavigation().moveTo(tg, 0.8);
            }
        }

        private void begin(String m) {
            move = m;
            t = 0;
            shots = 0;
            getNavigation().stop();
            playAction(m);
        }

        private void end(int restTicks) {
            move = "";
            t = 0;
            rest = paced(phase >= 2 ? restTicks * 2 / 3 : restTicks);
        }

        // 灯の薙ぎ: 前の 140 度・6 の内 (18 tick の予告)
        private void sweep(ServerLevel sl) {
            if (t < 18 && t % 3 == 0) {
                Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
                for (int i = -4; i <= 4; i++) {
                    Vec3 d = fwd.yRot((float) Math.toRadians(i * 17.5));
                    Vec3 p = position().add(d.scale(5.0));
                    sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.2, p.z, 1, 0, 0, 0, 0);
                }
            }
            if (t == 18) {
                playSound(MoonMobsSetup.KEEPER_SWEEP.get(), 1.5f, 0.8f);
                Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
                for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(6.5), e -> e.isAlive())) {
                    Vec3 to = p.position().subtract(position());
                    if (to.horizontalDistance() <= 6.0 && new Vec3(to.x, 0, to.z).normalize().dot(fwd) > 0.34) {
                        strike(p, dmg(1.2));
                        p.knockback(1.0, getX() - p.getX(), getZ() - p.getZ());
                    }
                }
            }
            if (t >= 30) {
                end(30);
            }
        }

        // 光の球: 6 tick ごとに 3 つ (段 3 は 5 つ)
        private void orbs(ServerLevel sl, LivingEntity tg) {
            int n = phase >= 2 ? 5 : 3;
            if (t >= 12 && (t - 12) % 6 == 0 && shots < n) {
                shots++;
                Vec3 from = orb().add(Vec3.directionFromRotation(0, getYRot()).scale(1.5));
                Vec3 aim = tg.position().add(0, 1.0, 0).add(tg.getDeltaMovement().scale(6));
                FallingLight.shoot(sl, FullKeeper.this, from, aim.subtract(from), 0.6, dmg(0.8));
                playSound(MoonMobsSetup.KEEPER_ORB.get(), 1.0f, 1.2f);
            }
            if (t >= 12 + n * 6 + 10) {
                end(40);
            }
        }

        @Override
        public void stop() {
            move = "";
        }
    }
}
