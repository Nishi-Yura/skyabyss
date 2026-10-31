package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.finale.founder.FounderArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 裏ボス「初代の空見」(docs/design/53 の 5 章)。鐘の内 (FounderArena) で一騎打ち。人の大きさで、歩いて追う。
//   拍子   舌が壁を打つたびに鐘が鳴る (段 1 = 8 秒・段 2 = 6 秒・段 3 = 4 秒)。歌の技は鐘の音と一緒に始まる
//   耳     目が見えないので「一番うるさい人」を狙う (直前 2 秒に始祖を打った +3・技を使った +2・走った +2・歩いた +1・しゃがみ 0)。
//          しゃがんで 6 より離れている人は、最後に聞こえた所を打つ (鐘が鳴ると、みなの居場所が聞こえ直す)
//   聞き耳 12 秒に一度ほど 1.2 秒、耳を澄ます。この間に打つと傷は通らず、すぐ返しの一撃 (×2.0)。待てば 1.5 秒の大きな隙 (受ける傷 ×1.25)
//   竿の技 三連の突き (線 0.5 秒ずつ、三度目だけ 0.4 遅い)・薙ぎ (前の半円 0.8 秒)・踏み込み (線 0.7 秒、8 先まで)
//   歌     1 影の帯 / 2 しゃがむ輪 / 3 見上げない閃光 / 4 月の円と遅くなる床 / 5 床の鼓動三つ (跳ぶ) / 6 光の蓋 (内へ) / 7 二人に分かれる (段 3)
//   段     体力 66%・33% で 2 秒の語り (傷が通らない)。段 3 は歌を二行重ねる (1+4・2+6・3+5)
//   終わり 体力が尽きても倒れず、膝をついてやっつめを歌い (6 秒)、鐘の音に溶ける (FounderArena.defeated)
//   人数   受ける傷を HeartBalance.partyScale(鐘の内の人数) で割る
//   写し   ななつの写し (echo) は同じ型で、小鐘を鳴らさない。打つと割れて打った人によろめき。本物に 3 回当てるか 12 秒で消える
public class FounderBoss extends CloudMob {
    public static final String ID = "founder";
    public static final double HP = 1000;
    public static final double DMG = 20;
    static final int ROAR_TICKS = 40;
    static final int FINAL_TICKS = 120;
    static final int[] BEAT = {160, 120, 80};
    static final int[] VERSE_EVERY = {400, 300, 240};
    static final int LISTEN_EVERY = 240;
    static final int LISTEN_TICKS = 24;
    static final int OPENING_TICKS = 30;
    static final double ARENA_R = 16.4;
    static final double QUIET_FAR = 6;
    static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.08f, 0.07f, 0.12f), 2.2f);
    static final DustParticleOptions LIGHT = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.55f), 1.4f);
    static final DustParticleOptions MOONLIGHT = new DustParticleOptions(new Vector3f(0.72f, 0.82f, 1.0f), 1.6f);

    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.founder"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10));
    private int phase;
    private int roarFor;
    private int finalFor = -1;
    private int beatIn = 80;
    private int verseIn = 200;
    private int listenIn = LISTEN_EVERY;
    private int listening;
    private int opening;
    private int lastVerse;
    private boolean echo;
    private UUID realId;
    private int echoLife;
    private int realHits;
    private final Map<UUID, Long> heardHit = new HashMap<>();
    private final Map<UUID, Vec3> heardAt = new HashMap<>();
    private final Set<String> told = new HashSet<>();
    // 背の効果 (竿の技と同時に進む): 影の帯・光の蓋・床の鼓動・月の遅い床
    private int shadowT = -1;
    private Vec3 shadowDir = Vec3.ZERO;
    private final Set<Integer> shadowHit = new HashSet<>();
    private int lidT = -1;
    private Vec3 lidAt = Vec3.ZERO;
    private int pulseT = -1;
    private int slowT;
    private Vec3 slowAt = Vec3.ZERO;

    public FounderBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "thrust", "sweep", "dash", "listen", "counter", "sing", "spin", "leap", "moon", "beat", "lid", "split", "call",
                "kneel", "hurt", "death");
        this.xpReward = 400;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.SAFE_FALL_DISTANCE, 60);
    }

    public int phase() {
        return phase;
    }

    public boolean echo() {
        return echo;
    }

    public boolean finishing() {
        return finalFor >= 0;
    }

    public boolean listening() {
        return listening > 0;
    }

    public void makeEcho(FounderBoss real) {
        echo = true;
        realId = real.getUUID();
        echoLife = 240;
        bar.setVisible(false);
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
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!echo) {
            bar.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bar.removeAllPlayers();
    }

    // ---------------------------------------------------------------- 耳
    // 技を使った (SkillManager.cast から)。鐘の内の始祖がそれを聞く
    public static void heardSkill(ServerPlayer p) {
        if (p.level() instanceof ServerLevel sl) {
            for (FounderBoss b : FounderArena.bosses(sl)) {
                b.heardHit.put(p.getUUID(), sl.getGameTime() - 20);
                b.heardAt.put(p.getUUID(), p.position());
            }
        }
    }

    int loudness(ServerPlayer p) {
        long now = level().getGameTime();
        int s = 0;
        Long hit = heardHit.get(p.getUUID());
        if (hit != null && now - hit <= 40) {
            s += 3;
        }
        if (p.isSprinting()) {
            s += 2;
        } else if (!p.isCrouching() && p.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
            s += 1;
        }
        return s;
    }

    // 狙う人と、狙う所 (しゃがんで離れている人は、最後に聞こえた所)
    ServerPlayer loudest() {
        ServerPlayer best = null;
        int bs = -1;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : FounderArena.present((ServerLevel) level())) {
            int s = loudness(p);
            double d = distanceToSqr(p);
            if (s > bs || s == bs && d < bd) {
                best = p;
                bs = s;
                bd = d;
            }
        }
        return best;
    }

    Vec3 aimAt(ServerPlayer p) {
        boolean quiet = loudness(p) == 0 && p.isCrouching() && distanceTo(p) > QUIET_FAR;
        if (!quiet) {
            heardAt.put(p.getUUID(), p.position());
            return p.position();
        }
        return heardAt.getOrDefault(p.getUUID(), p.position());
    }

    void hearEveryone(ServerLevel sl) {
        for (ServerPlayer p : FounderArena.present(sl)) {
            heardAt.put(p.getUUID(), p.position());
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        bar.setProgress(getHealth() / getMaxHealth());
        if (echo) {
            echoTick(sl);
            return;
        }
        if (finalFor >= 0) {
            finale(sl);
            return;
        }
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
            sl.playSound(null, blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0f, 0.6f);
            for (ServerPlayer p : FounderArena.present(sl)) {
                Notice.narrate(p, "message.skyabyss.founder.phase" + (phase + 1));
            }
            beatIn = Math.min(beatIn, 20);
        }
        // 拍子
        if (--beatIn <= 0) {
            beatIn = BEAT[phase];
            toll(sl);
        }
        if (verseIn > 0) {
            verseIn--;
        }
        if (listenIn > 0) {
            listenIn--;
        }
        if (opening > 0) {
            opening--;
        }
        // 歩くときの腰の小鐘 (本物だけが鳴らす。ななつの見分け)
        if (tickCount % 16 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.001) {
            sl.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 0.5f, 1.9f);
            sl.sendParticles(ParticleTypes.NOTE, getX(), getY() + 1.0, getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
        effects(sl);
    }

    void toll(ServerLevel sl) {
        BlockPos c = FounderArena.mark("clapper");
        sl.playSound(null, c, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0f, 0.5f);
        sl.playSound(null, c, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.4f, 0.5f);
        double a = random.nextDouble() * Math.PI * 2;
        BlockPos ce = FounderArena.mark("center");
        sl.sendParticles(ParticleTypes.END_ROD, ce.getX() + 0.5 + Math.cos(a) * 15, ce.getY() + 9, ce.getZ() + 0.5 + Math.sin(a) * 15, 30, 0.6, 2.5, 0.6, 0.02);
        hearEveryone(sl);
    }

    // ---------------------------------------------------------------- 受ける傷
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (roarFor > 0 || finalFor >= 0 || level().isClientSide) {
            return false;
        }
        ServerPlayer by = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (by != null) {
            heardHit.put(by.getUUID(), level().getGameTime());
            heardAt.put(by.getUUID(), by.position());
        }
        if (echo) {
            if (by != null) {
                shatterEcho(by);
            }
            return false;
        }
        // 聞き耳の最中: 傷は通らず、返す
        if (listening > 0 && by != null) {
            counter(by);
            return false;
        }
        float scale = (float) jp.revati.skyabyss.combat.HeartBalance.partyScale(Math.max(1, FounderArena.present((ServerLevel) level()).size()));
        float a = amount / scale * (opening > 0 ? 1.25f : 1f);
        if (by != null) {
            realHits++;
        }
        if (a >= getHealth() - 1 && isAlive()) {
            beginFinal();
            return false;
        }
        return super.hurt(source, a);
    }

    void counter(ServerPlayer by) {
        listening = 0;
        playAction("counter");
        level().playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.6f, 1.6f);
        if (by.distanceTo(this) <= 9) {
            Vec3 d = by.position().subtract(position()).normalize();
            if (by.distanceTo(this) > 3) {
                // 離れて打った人へは、竿ごと一息に詰める
                moveTo(by.getX() - d.x * 1.6, by.getY(), by.getZ() - d.z * 1.6, getYRot(), 0f);
            }
            strike(by, dmg(2.0));
            by.push(d.x * 1.1, 0.35, d.z * 1.1);
            by.hurtMarked = true;
        }
        tell(by, "listen");
    }

    void tell(ServerPlayer p, String what) {
        if (told.add(what + p.getUUID())) {
            Notice.narrate(p, "message.skyabyss.founder.tell." + what);
        }
    }

    void beginFinal() {
        finalFor = 0;
        setHealth(1f);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        AbyssBossBars.roar(bar, true);
        playAction("kneel");
        for (FounderBoss e : FounderArena.echoes((ServerLevel) level())) {
            e.discard();
        }
        shadowT = -1;
        lidT = -1;
        pulseT = -1;
        slowT = 0;
    }

    // 膝をついて、やっつめを歌う (語り三行) → 鐘の音に溶ける
    void finale(ServerLevel sl) {
        finalFor++;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (finalFor == 20 || finalFor == 55 || finalFor == 90) {
            int line = finalFor == 20 ? 1 : finalFor == 55 ? 2 : 3;
            for (ServerPlayer p : FounderArena.present(sl)) {
                Notice.narrate(p, "message.skyabyss.founder.end" + line);
            }
            sl.playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.2f, 0.8f + line * 0.15f);
        }
        if (finalFor % 4 == 0) {
            sl.sendParticles(LIGHT, getX(), getY() + 1.2, getZ(), 4, 0.4, 0.8, 0.4, 0);
        }
        if (finalFor >= FINAL_TICKS) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.2, getZ(), 120, 0.6, 1.2, 0.6, 0.08);
            sl.playSound(null, blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.5f, 1.2f);
            FounderArena.defeated(sl, this);
            discard();
        }
    }

    // ---------------------------------------------------------------- 写し (ななつ)
    void echoTick(ServerLevel sl) {
        FounderBoss real = FounderArena.real(sl);
        if (real == null || --echoLife <= 0 || real.echoStart >= 0 && real.realHits - real.echoStart >= 3) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.2, getZ(), 20, 0.3, 0.8, 0.3, 0.02);
            if (real != null) {
                real.echoStart = -1;
            }
            discard();
        }
    }

    // ななつで分かれたときの、本物に当たった数 (-1 = 写しはいない)
    private int echoStart = -1;

    void shatterEcho(ServerPlayer by) {
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.2, getZ(), 40, 0.4, 0.9, 0.4, 0.1);
        sl.playSound(null, blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.6f, 0.7f);
        strike(by, dmg(1.0));
        by.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
        by.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
        FounderBoss real = FounderArena.real(sl);
        if (real != null) {
            real.tell(by, "echo");
            real.echoStart = -1;
        }
        discard();
    }

    // ---------------------------------------------------------------- 背の効果 (竿の技と同時に)
    void effects(ServerLevel sl) {
        List<ServerPlayer> here = FounderArena.present(sl);
        BlockPos ce = FounderArena.mark("center");
        Vec3 c = new Vec3(ce.getX() + 0.5, ce.getY(), ce.getZ() + 0.5);
        // 1 影の帯: 20 tick は始まりの縁を見せ、そこから 60 tick で端から端へ (幅 6)
        if (shadowT >= 0) {
            shadowT++;
            Vec3 side = new Vec3(-shadowDir.z, 0, shadowDir.x);
            double front = shadowT < 20 ? -ARENA_R : -ARENA_R + (shadowT - 20) * (ARENA_R * 2 + 6) / 60.0;
            for (double s = -ARENA_R; s <= ARENA_R; s += 1.2) {
                for (double b = 0; b <= 6; b += shadowT < 20 ? 6 : 2) {
                    Vec3 q = c.add(shadowDir.scale(front - b)).add(side.scale(s));
                    if (q.subtract(c).horizontalDistance() <= ARENA_R) {
                        sl.sendParticles(SHADOW, q.x, c.y + 0.15, q.z, 1, 0.2, 0, 0.2, 0);
                    }
                }
            }
            if (shadowT >= 20) {
                for (ServerPlayer p : here) {
                    double along = p.position().subtract(c).dot(shadowDir);
                    if (along <= front && along >= front - 6 && shadowHit.add(p.getId())) {
                        strike(p, dmg(1.4));
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                        tell(p, "shadow");
                    }
                }
            }
            if (shadowT > 86) {
                shadowT = -1;
            }
        }
        // 6 光の蓋: 40 tick で壁から半径 5 まで閉じ、80 tick 保つ。外は毎秒 ×0.5
        if (lidT >= 0) {
            lidT++;
            double r = lidT < 40 ? ARENA_R + 2 - (ARENA_R + 2 - 5) * lidT / 40.0 : 5;
            if (lidT % 2 == 0) {
                int n = (int) (r * 4);
                for (int i = 0; i < n; i++) {
                    double a = i / (double) n * Math.PI * 2;
                    sl.sendParticles(LIGHT, lidAt.x + Math.cos(a) * r, c.y + 0.3 + (i % 3), lidAt.z + Math.sin(a) * r, 1, 0, 0.2, 0, 0);
                }
            }
            if (lidT >= 40 && lidT % 20 == 0) {
                for (ServerPlayer p : here) {
                    if (p.position().subtract(lidAt).horizontalDistance() > r + 0.4) {
                        strike(p, dmg(0.5));
                        tell(p, "lid");
                    }
                }
            }
            if (lidT >= 120) {
                lidT = -1;
            }
        }
        // 5 床の鼓動: 20・40・60 tick に打つ。打つ 6 tick 前に真ん中から輪。床に足が着いていると ×0.6 と打ち上げ
        if (pulseT >= 0) {
            pulseT++;
            int k = pulseT % 20;
            if (pulseT <= 60 && k == 14) {
                for (int i = 0; i < 48; i++) {
                    double a = i / 48.0 * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x + Math.cos(a) * 3, c.y + 0.1, c.z + Math.sin(a) * 3, 1, 0, 0, 0, 0);
                }
                sl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
            }
            if (pulseT <= 60 && k == 0 && pulseT > 0) {
                sl.playSound(null, ce, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0f, 0.7f);
                for (ServerPlayer p : here) {
                    if (p.onGround()) {
                        strike(p, dmg(0.6));
                        p.push(0, 0.5, 0);
                        p.hurtMarked = true;
                        tell(p, "pulse");
                    }
                }
            }
            if (pulseT > 62) {
                pulseT = -1;
            }
        }
        // 4 月の落ちた所: 遅くなる床 (6 秒)
        if (slowT > 0) {
            slowT--;
            if (slowT % 4 == 0) {
                sl.sendParticles(MOONLIGHT, slowAt.x, c.y + 0.1, slowAt.z, 6, 2.0, 0, 2.0, 0);
            }
            for (ServerPlayer p : here) {
                if (p.position().subtract(slowAt).horizontalDistance() <= 4) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2));
                }
            }
        }
    }

    // ---------------------------------------------------------------- 戦い方 (一度に一つの技)
    final class Fight extends Goal {
        private String move = "";
        private int t;
        private int rest = 30;
        private Vec3 aim = Vec3.ZERO;
        private Vec3 dir = Vec3.ZERO;
        private final Set<Integer> hit = new HashSet<>();
        private int lastPole;

        Fight() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return isAlive() && finalFor < 0 && !FounderArena.present((ServerLevel) level()).isEmpty();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel sl) || finalFor >= 0) {
                return;
            }
            ServerPlayer tg = loudest();
            if (tg == null) {
                return;
            }
            setTarget(tg);
            if (roarFor > 0) {
                move = "";
                getNavigation().stop();
                return;
            }
            if (!move.isEmpty()) {
                t++;
                run(sl, tg);
                return;
            }
            Vec3 at = aimAt(tg);
            getLookControl().setLookAt(at.x, at.y + 1.2, at.z);
            // 歌の技は鐘の音と一緒に (拍子の始まりの 2 tick の内)
            if (!echo && verseIn <= 0 && beatIn >= BEAT[phase] - 2) {
                verse(sl, tg);
                return;
            }
            if (rest > 0) {
                rest--;
                chase(at);
                return;
            }
            if (!echo && listenIn <= 0 && opening <= 0) {
                begin("listen", at);
                return;
            }
            double d = position().distanceTo(at);
            if (d > 7.5) {
                begin("dash", at);
            } else if (d <= 4.6) {
                begin(lastPole == 0 && random.nextFloat() < 0.6f ? "sweep" : "thrust", at);
            } else {
                chase(at);
            }
        }

        void chase(Vec3 at) {
            if (position().distanceTo(at) > 3.2) {
                getNavigation().moveTo(at.x, at.y, at.z, 1.0);
            } else {
                getNavigation().stop();
            }
        }

        void begin(String m, Vec3 at) {
            move = m;
            t = 0;
            hit.clear();
            aim = at;
            Vec3 d = at.subtract(position());
            dir = new Vec3(d.x, 0, d.z).lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, getYRot()) : new Vec3(d.x, 0, d.z).normalize();
            setYRot((float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
            setYBodyRot(getYRot());
            setYHeadRot(getYRot());
            getNavigation().stop();
            playAction(m);
            if (m.equals("thrust")) {
                lastPole = 1;
            } else if (m.equals("sweep")) {
                lastPole = 0;
            }
            if (m.equals("listen")) {
                listening = LISTEN_TICKS;
                listenIn = LISTEN_EVERY;
                level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 0.8f, 2.0f);
            }
        }

        void end(int restTicks) {
            move = "";
            t = 0;
            rest = phase >= 2 ? restTicks * 2 / 3 : restTicks;
        }

        // 歌の一行を選んで始める (段 3 は二行の重ねも)
        void verse(ServerLevel sl, ServerPlayer tg) {
            verseIn = VERSE_EVERY[phase];
            int max = phase == 0 ? 3 : phase == 1 ? 6 : 7;
            int v;
            do {
                v = 1 + random.nextInt(max);
            } while (v == lastVerse && max > 1);
            lastVerse = v;
            if (phase == 2 && v != 7 && random.nextFloat() < 0.5f) {
                // 重ね: 1+4・2+6・3+5 (片方は背の効果で、もう片方は始祖の技)
                int[][] pairs = {{1, 4}, {6, 2}, {5, 3}};
                int[] pr = pairs[random.nextInt(pairs.length)];
                sing(sl, pr[0]);
                startEffect(sl, pr[0], tg);
                sing(sl, pr[1]);
                startVerse(sl, pr[1], tg);
                return;
            }
            sing(sl, v);
            startVerse(sl, v, tg);
        }

        void sing(ServerLevel sl, int v) {
            for (ServerPlayer p : FounderArena.present(sl)) {
                Notice.alert(p, "message.skyabyss.founder.verse" + v);
            }
            BlockPos m = FounderArena.mark("verse_" + v);
            sl.sendParticles(LIGHT, m.getX() + 0.5, m.getY() + 0.3, m.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0);
            sl.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1.4f, 0.6f + v * 0.1f);
        }

        // 背の効果で済む行 (重ねの片方)
        void startEffect(ServerLevel sl, int v, ServerPlayer tg) {
            switch (v) {
                case 1 -> startShadow();
                case 6 -> {
                    lidT = 0;
                    lidAt = position();
                }
                case 5 -> pulseT = 0;
                default -> {
                }
            }
        }

        void startShadow() {
            double a = random.nextInt(4) * Math.PI / 2 + Math.PI / 4;
            shadowDir = new Vec3(Math.cos(a), 0, Math.sin(a));
            shadowT = 0;
            shadowHit.clear();
        }

        void startVerse(ServerLevel sl, int v, ServerPlayer tg) {
            Vec3 at = aimAt(tg);
            switch (v) {
                case 1 -> {
                    startShadow();
                    begin("sing", at);
                }
                case 2 -> begin("spin", at);
                case 3 -> begin("leap", at);
                case 4 -> begin("moon", at);
                case 5 -> {
                    pulseT = 0;
                    begin("beat", at);
                }
                case 6 -> begin("lid", at);
                default -> begin("split", at);
            }
        }

        void run(ServerLevel sl, ServerPlayer tg) {
            switch (move) {
                case "thrust" -> thrust(sl);
                case "sweep" -> sweep(sl);
                case "dash" -> dash(sl);
                case "listen" -> listenMove(sl);
                case "counter" -> end(20);
                case "sing" -> {
                    if (t >= 24) {
                        end(10);
                    }
                }
                case "spin" -> spin(sl);
                case "leap" -> leap(sl);
                case "moon" -> moon(sl);
                case "beat" -> {
                    if (t >= 72) {
                        end(10);
                    }
                }
                case "lid" -> {
                    if (t == 10) {
                        lidT = 0;
                        lidAt = position();
                    }
                    if (t >= 40) {
                        end(4);
                    }
                }
                case "split" -> split(sl);
                default -> end(10);
            }
        }

        // 細い線の予告 (竿の先の向き)
        void line(ServerLevel sl, double len) {
            for (double s = 1; s <= len; s += 0.5) {
                Vec3 q = position().add(dir.scale(s));
                sl.sendParticles(ParticleTypes.WAX_OFF, q.x, getY() + 0.15, q.z, 1, 0, 0, 0, 0);
            }
        }

        void lineHit(ServerLevel sl, double len, double width, double factor) {
            for (ServerPlayer p : FounderArena.present(sl)) {
                Vec3 r = p.position().subtract(position());
                double along = r.dot(dir);
                double side = Math.abs(r.x * -dir.z + r.z * dir.x);
                if (along >= 0 && along <= len && side <= width && hit.add(p.getId() * 31 + t)) {
                    strike(p, dmg(factor));
                }
            }
        }

        // 三連の突き: 11・26・49 tick に突く (三度目だけ 0.4 秒遅い)
        void thrust(ServerLevel sl) {
            if (t < 49 && t % 3 == 0) {
                line(sl, 4.4);
            }
            if (t == 11 || t == 26 || t == 49) {
                lineHit(sl, 4.4, 0.9, 1.0);
                sl.playSound(null, blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.0f, 1.3f);
            }
            if (t >= 58) {
                end(26);
            }
        }

        // 薙ぎ: 前の半円 (半径 4.5)。16 tick の予告
        void sweep(ServerLevel sl) {
            if (t < 16 && t % 3 == 0) {
                for (int i = -9; i <= 9; i++) {
                    double a = Math.atan2(dir.z, dir.x) + i * Math.PI / 18;
                    sl.sendParticles(ParticleTypes.WAX_OFF, getX() + Math.cos(a) * 4.5, getY() + 0.15, getZ() + Math.sin(a) * 4.5, 1, 0, 0, 0, 0);
                }
            }
            if (t == 17) {
                sl.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4f, 0.7f);
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + dir.x * 2, getY() + 1.2, getZ() + dir.z * 2, 3, 1.2, 0.2, 1.2, 0);
                for (ServerPlayer p : FounderArena.present(sl)) {
                    Vec3 r = p.position().subtract(position());
                    if (r.horizontalDistance() <= 4.7 && r.dot(dir) >= 0) {
                        strike(p, dmg(1.3));
                    }
                }
            }
            if (t >= 28) {
                end(30);
            }
        }

        // 踏み込み: 14 tick の線の予告 → 6 tick で 8 先まで
        void dash(ServerLevel sl) {
            if (t < 14) {
                if (t % 2 == 0) {
                    line(sl, 8);
                }
                return;
            }
            if (t <= 20) {
                Vec3 to = position().add(dir.scale(8.0 / 6));
                if (to.subtract(Vec3.atCenterOf(FounderArena.mark("center"))).horizontalDistance() < ARENA_R - 1) {
                    setPos(to.x, getY(), to.z);
                }
                for (ServerPlayer p : FounderArena.present(sl)) {
                    if (p.distanceTo(FounderBoss.this) <= 1.6 && hit.add(p.getId())) {
                        strike(p, dmg(1.2));
                        p.push(dir.x * 0.8, 0.3, dir.z * 0.8);
                        p.hurtMarked = true;
                    }
                }
                sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.2, 0.1, 0.2, 0);
                return;
            }
            if (t >= 28) {
                end(24);
            }
        }

        // 聞き耳: 24 tick 構え (打たれたら返す) → 30 tick の隙
        void listenMove(ServerLevel sl) {
            if (listening > 0) {
                listening--;
                if (t % 3 == 0) {
                    for (int i = 0; i < 16; i++) {
                        double a = i / 16.0 * Math.PI * 2;
                        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * 1.2, getY() + 0.1, getZ() + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
                    }
                }
                if (listening == 0) {
                    opening = OPENING_TICKS;
                }
                return;
            }
            if (t >= LISTEN_TICKS + OPENING_TICKS) {
                end(10);
            }
        }

        // 2 ふたつ: 24 tick の予告 → 輪が床の端まで (頭の高さ)。しゃがんでいれば当たらない
        void spin(ServerLevel sl) {
            if (t < 24) {
                if (t % 3 == 0) {
                    for (int i = 0; i < 24; i++) {
                        double a = i / 24.0 * Math.PI * 2;
                        sl.sendParticles(LIGHT, getX() + Math.cos(a) * 2.2, getY() + 1.6, getZ() + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
                    }
                }
                return;
            }
            double r = (t - 24) * 1.6;
            if (r <= ARENA_R + 2) {
                int n = 12 + (int) (r * 3);
                for (int i = 0; i < n; i++) {
                    double a = i / (double) n * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + Math.cos(a) * r, getY() + 1.6, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                if (t == 24) {
                    sl.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.5f);
                }
                for (ServerPlayer p : FounderArena.present(sl)) {
                    double d = p.position().subtract(position()).horizontalDistance();
                    if (Math.abs(d - r) <= 1.0 && !p.isCrouching() && p.getPose() != Pose.SWIMMING && hit.add(p.getId())) {
                        strike(p, dmg(1.6));
                        tell(p, "crouch");
                    }
                }
                return;
            }
            if (t >= 44) {
                end(20);
            }
        }

        // 3 みっつ: 舌に跳び上がり (12 tick)、30 tick 光を集めて閃光。見上げている人 (視線が水平より 15 度より上) に目くらましと傷。降りる
        void leap(ServerLevel sl) {
            BlockPos cl = FounderArena.mark("clapper");
            Vec3 top = new Vec3(cl.getX() + 0.5, cl.getY(), cl.getZ() + 0.5);
            if (t <= 12) {
                Vec3 from = position();
                Vec3 to = from.add(top.subtract(from).scale(1.0 / Math.max(1, 13 - t)));
                setPos(to.x, to.y, to.z);
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            if (t < 42) {
                setPos(top.x, top.y, top.z);
                setDeltaMovement(Vec3.ZERO);
                if (t % 2 == 0) {
                    sl.sendParticles(LIGHT, top.x, top.y + 3.5, top.z, 8, 1.2, 0.6, 1.2, 0);
                    sl.sendParticles(ParticleTypes.END_ROD, top.x, top.y + 2.5, top.z, 2, 0.4, 0.4, 0.4, 0.02);
                }
                return;
            }
            if (t == 42) {
                sl.playSound(null, cl, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0f, 1.4f);
                sl.sendParticles(ParticleTypes.FLASH, top.x, top.y + 2, top.z, 1, 0, 0, 0, 0);
                for (ServerPlayer p : FounderArena.present(sl)) {
                    if (p.getXRot() < -15f) {
                        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                        strike(p, dmg(1.2));
                        tell(p, "lookup");
                    }
                }
                return;
            }
            if (t <= 54) {
                Vec3 land = aim.subtract(Vec3.atCenterOf(FounderArena.mark("center"))).horizontalDistance() < ARENA_R - 2 ? aim
                        : Vec3.atBottomCenterOf(FounderArena.mark("center"));
                Vec3 to = position().add(land.subtract(position()).scale(1.0 / Math.max(1, 55 - t)));
                setPos(to.x, to.y, to.z);
                setDeltaMovement(Vec3.ZERO);
                if (t == 54) {
                    sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 20, 1.5, 0.1, 1.5, 0.05);
                    for (ServerPlayer p : FounderArena.present(sl)) {
                        if (p.distanceTo(FounderBoss.this) <= 2.6) {
                            strike(p, dmg(0.8));
                        }
                    }
                }
                return;
            }
            if (t >= 62) {
                end(16);
            }
        }

        // 4 よっつ: 暗くなり、狙った人の足もとに月の円 (半径 4、40 tick) → 落ちる (×2.0)。落ちた所は 6 秒遅くなる床
        void moon(ServerLevel sl) {
            if (t == 1) {
                for (ServerPlayer p : FounderArena.present(sl)) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0));
                }
            }
            if (t < 40) {
                if (t % 2 == 0) {
                    for (int i = 0; i < 28; i++) {
                        double a = i / 28.0 * Math.PI * 2;
                        sl.sendParticles(MOONLIGHT, aim.x + Math.cos(a) * 4, aim.y + 0.15, aim.z + Math.sin(a) * 4, 1, 0, 0, 0, 0);
                    }
                    double h = 14 * (1 - t / 40.0);
                    sl.sendParticles(ParticleTypes.END_ROD, aim.x, aim.y + 1 + h, aim.z, 6, 0.8, 0.8, 0.8, 0);
                }
                return;
            }
            if (t == 40) {
                sl.playSound(null, BlockPos.containing(aim), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 1.6f);
                sl.sendParticles(ParticleTypes.END_ROD, aim.x, aim.y + 0.5, aim.z, 80, 2.0, 0.4, 2.0, 0.1);
                for (ServerPlayer p : FounderArena.present(sl)) {
                    if (p.position().subtract(aim).horizontalDistance() <= 4.2) {
                        strike(p, dmg(2.0));
                        tell(p, "moon");
                    }
                }
                slowT = 120;
                slowAt = aim;
            }
            if (t >= 48) {
                end(16);
            }
        }

        // 7 ななつ: 二回り (24 tick) して二人に分かれる
        void split(ServerLevel sl) {
            if (t == 24) {
                FounderBoss e = FounderArena.spawnEcho(sl, FounderBoss.this);
                if (e != null) {
                    echoStart = realHits;
                    for (ServerPlayer p : FounderArena.present(sl)) {
                        tell(p, "split");
                    }
                }
            }
            if (t >= 30) {
                end(10);
            }
        }

        @Override
        public void stop() {
            move = "";
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BELL_RESONATE;
    }

    // 開発用 (FounderArena のコマンド): 段を進める・歌の一行をすぐ始める
    public void devPhase(int p) {
        float f = p <= 1 ? 1f : p == 2 ? 0.6f : 0.3f;
        setHealth(getMaxHealth() * f);
    }

    public void devListen() {
        listening = LISTEN_TICKS;
    }

    public void devStopListen() {
        listening = 0;
        opening = 0;
    }

    public void devFinish() {
        beginFinal();
    }

    public void devVerse(int v) {
        ServerLevel sl = (ServerLevel) level();
        ServerPlayer tg = loudest();
        if (tg == null) {
            return;
        }
        for (Goal g : goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).toList()) {
            if (g instanceof Fight fight) {
                fight.sing(sl, v);
                fight.startVerse(sl, v, tg);
            }
        }
    }
}
