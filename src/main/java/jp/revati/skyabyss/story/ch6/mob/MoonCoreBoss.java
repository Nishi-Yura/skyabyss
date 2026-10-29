package jp.revati.skyabyss.story.ch6.mob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.ch6.core.CoreArena;
import jp.revati.skyabyss.story.ch6.core.MoonCore;
import jp.revati.skyabyss.story.ch6.field.FallingLight;
import jp.revati.skyabyss.story.ch6.rift.RiftRemnants;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 月の核 (月の奥の主。docs/design/46 の 4.7・一品一案「天球儀の珠」)。核の間の床の真ん中の上に浮いたまま動かない (CoreArena が呼び、片付ける)。
//   段 1 欠片の盾   四つの月の欠片 (CoreShard) が核のまわり 4.5 を回る。欠片の向き (±22 度) から打った傷は通らない。
//                   欠片の雨 (人の足もとへ降る光)・欠片の薙ぎ (予告 1 秒のあと欠片が半径 12 まで広がって戻る。触れると傷)
//   段 2 巻き戻し   欠片は二つ。10 秒ごとに時計の輪 (5 秒で縮む)。縮みきると体力が輪の出た時へ戻る。輪のあいだに体力の 6% より多く削ると
//                   巻き戻しが崩れ、核は 5 秒止まる (傷 ×1.6)。時の残像 (床の縁に残り鎧の残像を二体、20 秒ごと)
//   段 3 満月落とし 欠片は砕け、四つの台に前の自分たちが現れる (CoreArena.allies)。20 秒ごとに床いっぱいに赤い満月の予告 5 秒 →
//                   満月が落ちる (傷 ×3)。前の自分たちのまわり 3.5 の内は無事
// 段は体力 66% と 33% (咆哮 2 秒は傷が通らない)。一瞬止めは核も欠片も止める (核の tick が止まるので欠片も動かない)
public class MoonCoreBoss extends MoonMob {
    public static final String ID = "moon_core";
    public static final double HP = 850;
    public static final double DMG = 16;
    public static final double SHARD_R = 4.5;
    public static final double SHIELD_DEG = 22;
    public static final double SAFE_R = 3.5;
    static final int ROAR_TICKS = 40;
    static final int RAIN_EVERY = 90;
    static final int SWEEP_EVERY = 220;
    static final int SWEEP_TELL = 20;
    static final int REWIND_EVERY = 200;
    static final int REWIND_RING = 100;
    static final double REWIND_BREAK = 0.06;
    static final int STAGGER_TICKS = 100;
    static final int DROP_EVERY = 400;
    static final int DROP_TELL = 100;
    static final int ECHO_EVERY = 400;
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.moon_core"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10));
    private final List<Integer> shards = new ArrayList<>();
    private int phase;
    private int roarFor;
    private int staggerFor;
    private double shardAngle;
    private int sweep = -1;
    private final Set<Integer> sweptHit = new HashSet<>();
    private int rewind = -1;
    private float rewindHp;
    private int drop = -1;
    private int clock;
    private boolean toldShield;
    private boolean toldBreak;

    public MoonCoreBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "sweep", "rewind", "stagger", "drop", "roar", "hurt", "death");
        this.xpReward = 200;
        setPersistenceRequired();
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public int phase() {
        return phase;
    }

    public boolean staggered() {
        return staggerFor > 0;
    }

    public boolean rewinding() {
        return rewind >= 0;
    }

    public boolean dropping() {
        return drop >= 0;
    }

    public int shardCount() {
        return phase == 0 ? 4 : phase == 1 ? 2 : 0;
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
    }

    @Override
    protected boolean holdsAction(String action) {
        return action.equals("drop");
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
        return position().add(0, 2.5, 0);
    }

    // 欠片の角度 (度。東を 0、時計回り)
    public List<Double> shardAngles() {
        List<Double> out = new ArrayList<>();
        int n = shardCount();
        for (int i = 0; i < n; i++) {
            out.add((shardAngle + 360.0 * i / n) % 360.0);
        }
        return out;
    }

    // その所から核を打つと、欠片に遮られるか
    public boolean shielded(Vec3 from) {
        if (shardCount() == 0) {
            return false;
        }
        double a = Math.toDegrees(Math.atan2(from.z - getZ(), from.x - getX()));
        for (double s : shardAngles()) {
            double d = Math.abs(((a - s) % 360 + 540) % 360 - 180);
            if (d < SHIELD_DEG) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 毎 tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        bar.setProgress(getHealth() / getMaxHealth());
        clock++;
        if (roarFor > 0 && --roarFor == 0) {
            AbyssBossBars.roar(bar, false);
        }
        float f = getHealth() / getMaxHealth();
        int want = f <= 0.33f ? 2 : f <= 0.66f ? 1 : 0;
        if (want > phase && isAlive()) {
            enterPhase(sl, want);
        }
        tickShards(sl);
        if (staggerFor > 0) {
            staggerFor--;
            if (staggerFor % 5 == 0) {
                sl.sendParticles(ParticleTypes.END_ROD, orb().x, orb().y, orb().z, 6, 0.8, 0.8, 0.8, 0.05);
            }
            return;
        }
        if (roarFor > 0) {
            return;
        }
        if (clock % paced(RAIN_EVERY) == 0) {
            rain(sl);
        }
        if (phase < 2 && sweep < 0 && clock % paced(SWEEP_EVERY) == paced(SWEEP_EVERY) / 2) {
            sweep = 0;
            sweptHit.clear();
            playAction("sweep");
            playSound(MoonMobsSetup.CORE_TELL.get(), 2.0f, 0.9f);
        }
        if (phase == 1) {
            if (rewind < 0 && clock % REWIND_EVERY == 0) {
                rewind = 0;
                rewindHp = getHealth();
                playAction("rewind");
                playSound(MoonMobsSetup.CORE_CLOCK.get(), 2.0f, 1.0f);
            }
            if (rewind >= 0) {
                tickRewind(sl);
            }
            if (clock % ECHO_EVERY == ECHO_EVERY / 4) {
                echoes(sl);
            }
        }
        if (phase == 2) {
            if (drop < 0 && clock % DROP_EVERY == DROP_EVERY / 4) {
                drop = 0;
                playAction("drop");
                playSound(MoonMobsSetup.CORE_DROP_TELL.get(), 3.0f, 0.7f);
                for (ServerPlayer p : CoreArena.players(sl)) {
                    Notice.narrate(p, "message.skyabyss.moon.core_drop");
                }
            }
            if (drop >= 0) {
                tickDrop(sl);
            }
        }
    }

    private void enterPhase(ServerLevel sl, int want) {
        phase = want;
        AbyssBossBars.phase(bar, phase);
        AbyssBossBars.roar(bar, true);
        roarFor = ROAR_TICKS;
        staggerFor = 0;
        rewind = -1;
        sweep = -1;
        playAction("roar");
        playSound(MoonMobsSetup.CORE_ROAR.get(), 3.0f, 0.7f);
        // 欠片を減らす (段 2 は二つ、段 3 は砕ける)
        while (shards.size() > shardCount()) {
            Entity e = sl.getEntity(shards.remove(shards.size() - 1));
            if (e != null) {
                sl.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 1.5, e.getZ(), 30, 0.8, 1.5, 0.8, 0.05);
                e.discard();
            }
        }
        sl.playSound(null, getX(), getY(), getZ(), MoonMobsSetup.CORE_SHATTER.get(), net.minecraft.sounds.SoundSource.HOSTILE, 2.5f, 0.8f);
        String msg = phase == 1 ? "message.skyabyss.moon.core_rewind" : "message.skyabyss.moon.core_allies";
        for (ServerPlayer p : CoreArena.players(sl)) {
            Notice.narrate(p, msg);
        }
        if (phase == 1) {
            echoes(sl);
        } else if (phase == 2) {
            CoreArena.allies(sl);
        }
    }

    // 欠片: 足りなければ作り、回して置き直す。薙ぎのあいだは外へ広がって戻る
    private void tickShards(ServerLevel sl) {
        int n = shardCount();
        shards.removeIf(id -> !(sl.getEntity(id) instanceof CoreShard));
        while (shards.size() < n) {
            CoreShard s = MoonMobsSetup.CORE_SHARD.get().create(sl);
            if (s == null) {
                break;
            }
            s.moveTo(getX(), getY(), getZ());
            s.addTag(CoreArena.TAG);
            sl.addFreshEntity(s);
            shards.add(s.getId());
        }
        if (n == 0) {
            return;
        }
        double r = SHARD_R;
        if (sweep >= 0) {
            sweep++;
            if (sweep <= SWEEP_TELL) {
                if (sweep % 3 == 0) {
                    for (double a : shardAngles()) {
                        Vec3 p = position().add(Math.cos(Math.toRadians(a)) * r, 1.5, Math.sin(Math.toRadians(a)) * r);
                        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 4, 0.4, 1.2, 0.4, 0.02);
                    }
                }
            } else {
                int t = sweep - SWEEP_TELL;
                r = t <= 10 ? SHARD_R + (12 - SHARD_R) * t / 10.0 : 12 - (12 - SHARD_R) * (t - 10) / 10.0;
                if (t == 1) {
                    playSound(MoonMobsSetup.CORE_SWEEP.get(), 2.0f, 0.8f);
                }
                if (t >= 20) {
                    sweep = -1;
                    r = SHARD_R;
                }
            }
        } else {
            // 段 5 の位相の重ね (核): 欠片が速く回る
            shardAngle = (shardAngle + (phase == 0 ? 1.2 : 1.8) * (overlaid() ? 1.6 : 1.0)) % 360;
        }
        List<Double> angles = shardAngles();
        for (int i = 0; i < shards.size() && i < angles.size(); i++) {
            Entity e = sl.getEntity(shards.get(i));
            double a = Math.toRadians(angles.get(i));
            double x = getX() + Math.cos(a) * r;
            double z = getZ() + Math.sin(a) * r;
            float yaw = (float) Math.toDegrees(Math.atan2(-(getX() - x), getZ() - z));
            e.moveTo(x, getY() - 1.0, z, yaw, 0f);
            if (sweep > SWEEP_TELL) {
                for (Player p : sl.getEntitiesOfClass(Player.class, e.getBoundingBox().inflate(1.6, 1.0, 1.6), q -> q.isAlive() && !q.isSpectator())) {
                    if (sweptHit.add(p.getId())) {
                        strike(p, dmg(1.0));
                        p.knockback(0.8, getX() - p.getX(), getZ() - p.getZ());
                    }
                }
            }
        }
    }

    // 欠片の雨: 主の間の人の足もとと少し先へ光を降らす
    private void rain(ServerLevel sl) {
        for (ServerPlayer p : CoreArena.players(sl)) {
            for (int i = 0; i < (phase >= 1 ? 3 : 2); i++) {
                Vec3 at = i == 0 ? p.position() : p.position().add((random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8);
                FallingLight.fall(sl, new Vec3(at.x, Math.floor(p.getY()), at.z), dmg(0.5));
            }
        }
    }

    // 時計の輪: 縮みきると巻き戻す。輪のあいだに削り切れば崩れる
    private void tickRewind(ServerLevel sl) {
        rewind++;
        double r = 9.0 * (1 - rewind / (double) REWIND_RING) + 1.0;
        if (rewind % 3 == 0) {
            int n = 24;
            for (int i = 0; i < n; i++) {
                double a = i / (double) n * Math.PI * 2 + rewind * 0.05;
                sl.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, orb().y, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        float lost = rewindHp - getHealth();
        if (lost >= getMaxHealth() * REWIND_BREAK) {
            // 崩れる
            rewind = -1;
            staggerFor = STAGGER_TICKS;
            playAction("stagger");
            playSound(MoonMobsSetup.CORE_BREAK.get(), 2.5f, 0.8f);
            sl.sendParticles(ParticleTypes.FLASH, orb().x, orb().y, orb().z, 2, 0, 0, 0, 0);
            if (!toldBreak) {
                toldBreak = true;
                for (ServerPlayer p : CoreArena.players(sl)) {
                    Notice.narrate(p, "message.skyabyss.moon.core_break");
                }
            }
            return;
        }
        if (rewind >= REWIND_RING) {
            rewind = -1;
            if (getHealth() < rewindHp) {
                setHealth(rewindHp);
                playSound(MoonMobsSetup.CORE_REWIND.get(), 2.5f, 1.0f);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, orb().x, orb().y, orb().z, 60, 1.2, 1.2, 1.2, 0.1);
            }
        }
    }

    // 自己テスト: 巻き戻しの輪をすぐ出す
    public void rewindForTest() {
        rewind = 0;
        rewindHp = getHealth();
    }

    public void tickRewindForTest(ServerLevel sl, int ticks) {
        for (int i = 0; i < ticks && rewind >= 0; i++) {
            tickRewind(sl);
        }
    }

    // 時の残像: 床の縁に残り鎧の残像を二体 (淵の残り影と同じ形で立つ)
    private void echoes(ServerLevel sl) {
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 at = MoonCore.mark("arena_center").getBottomCenter().add(Math.cos(a) * 11, 0, Math.sin(a) * 11);
            RiftRemnants.mark(sl, MoonMobsSetup.RIFT_ARMOR.get(), at, CoreArena.TAG);
        }
    }

    // 満月落とし: 予告 (床いっぱいの赤、前の自分たちのまわりは青白い円) → 落ちる
    private void tickDrop(ServerLevel sl) {
        drop++;
        Vec3 c = MoonCore.mark("arena_center").getBottomCenter();
        List<Vec3> safe = CoreArena.safeSpots(sl);
        if (drop < DROP_TELL) {
            if (drop % 4 == 0) {
                DustParticleOptions red = new DustParticleOptions(new Vector3f(0.9f, 0.15f, 0.15f), 1.6f);
                for (int i = 0; i < 90; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double rr = Math.sqrt(random.nextDouble()) * 16;
                    Vec3 p = c.add(Math.cos(a) * rr, 0.15, Math.sin(a) * rr);
                    if (inSafe(p, safe)) {
                        continue;
                    }
                    sl.sendParticles(red, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                DustParticleOptions blue = new DustParticleOptions(new Vector3f(0.6f, 0.85f, 1.0f), 1.2f);
                for (Vec3 s : safe) {
                    for (int i = 0; i < 20; i++) {
                        double a = i / 20.0 * Math.PI * 2;
                        sl.sendParticles(blue, s.x + Math.cos(a) * SAFE_R, s.y + 0.15, s.z + Math.sin(a) * SAFE_R, 1, 0, 0, 0, 0);
                    }
                }
                // 頭の上に大きな満月の影 (粒の円)
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    double rr = 6 + 6.0 * drop / DROP_TELL;
                    sl.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * rr, c.y + 22 - 12.0 * drop / DROP_TELL, c.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                }
            }
            return;
        }
        drop = -1;
        stopTriggeredAnim("action", "drop");
        playSound(MoonMobsSetup.CORE_DROP.get(), 4.0f, 0.6f);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 2, c.z, 3, 6, 1, 6, 0);
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1, c.z, 200, 10, 1.5, 10, 0.2);
        for (ServerPlayer p : CoreArena.players(sl)) {
            if (!inSafe(p.position(), safe)) {
                strike(p, dmg(3.0));
            }
        }
    }

    public static boolean inSafe(Vec3 p, List<Vec3> safe) {
        for (Vec3 s : safe) {
            if (Math.hypot(p.x - s.x, p.z - s.z) <= SAFE_R) {
                return true;
            }
        }
        return false;
    }

    // 自己テスト: 満月をすぐ落とす (予告を飛ばす)
    public void dropForTest(ServerLevel sl) {
        drop = DROP_TELL - 1;
        tickDrop(sl);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (roarFor > 0) {
            return false;
        }
        Entity src = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        if (src != null && shielded(src.position())) {
            if (level() instanceof ServerLevel sl) {
                playSound(MoonMobsSetup.CORE_BLOCK.get(), 1.0f, 1.2f);
                if (!toldShield && src instanceof ServerPlayer sp) {
                    toldShield = true;
                    Notice.narrate(sp, "message.skyabyss.moon.core_shield");
                }
                Vec3 m = src.position().add(position()).scale(0.5);
                sl.sendParticles(ParticleTypes.CRIT, m.x, m.y + 1.5, m.z, 8, 0.3, 0.3, 0.3, 0.1);
            }
            return false;
        }
        return super.hurt(source, amount * (staggerFor > 0 ? 1.6f : 1.0f));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bar.removeAllPlayers();
        if (level() instanceof ServerLevel sl) {
            for (int id : shards) {
                Entity e = sl.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bar.removeAllPlayers();
    }
}
