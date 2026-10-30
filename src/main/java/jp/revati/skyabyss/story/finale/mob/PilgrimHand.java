package jp.revati.skyabyss.story.finale.mob;

import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.story.finale.BellIsle;
import jp.revati.skyabyss.story.finale.PilgrimSeat;
import jp.revati.skyabyss.story.finale.PromiseIsle;
import jp.revati.skyabyss.story.finale.SeatArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 巡礼者の手 (ラスボス「雲上の巡礼者」の第 1 形態。docs/design/50 の 4.3・一品一案「雲の白の大きな手」)。座の輪の外 (印 hand_<e|w>) に浮かぶ。
// 二つで一組 (SeatArena が呼ぶ)。技は一度に一つ、技の間に休み。もう片方が倒れると休みが短くなる:
//   叩きつけ  一人の頭上へ手が寄り (SLAM_TELL = 1.3 秒。足もとに円 SLAM_R) → 叩きつける (傷 ×1.4)。そのあと STUCK のあいだ床に手を突いたまま (受ける傷 ×1.5。打てる隙)
//   削る      輪の一方 (人の方。半角 SCRAPE_HALF) へ指を構え (SCRAPE_TELL = 1.5 秒。輪に掻く跡の光) → 掻く: その辺りの鐘の固まりを消し (足場が雲に戻る)、中の人に傷 ×1.0 と外への押し出し
public class PilgrimHand extends CloudMob {
    public static final String ID = "pilgrim_hand";
    public static final double HP = 760;
    public static final double DMG = 22;
    static final int SLAM_TELL = 26;
    static final double SLAM_R = 3.5;
    static final int STUCK = 60;
    static final int RISE = 20;
    static final int SCRAPE_TELL = 30;
    static final double SCRAPE_HALF = 40;
    static final double PUSH = 1.0;
    private final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.pilgrim_hand"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10));
    private BlockPos home = BlockPos.ZERO;
    private String move = "";
    private int t;
    private int rest = 40;
    private int turn;
    private Vec3 spot = Vec3.ZERO;
    private Vec3 from = Vec3.ZERO;
    private double angle;
    private static boolean toldStuck;
    private static boolean toldScrape;

    public PilgrimHand(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "slam", "rise", "scrape", "hurt", "death");
        this.xpReward = 120;
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

    public void setHome(BlockPos at, int firstRest) {
        home = at.immutable();
        rest = firstRest + 40;
    }

    public boolean stuck() {
        return move.equals("stuck");
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
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    Vec3 homePos() {
        return new Vec3(home.getX() + 0.5, home.getY() + Math.sin(tickCount * 0.05) * 0.6, home.getZ() + 0.5);
    }

    void faceCenter() {
        BlockPos c = PilgrimSeat.mark("arena");
        setYRot((float) Math.toDegrees(Math.atan2(-(c.getX() + 0.5 - getX()), c.getZ() + 0.5 - getZ())));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl) || home == BlockPos.ZERO) {
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        bar.setProgress(getHealth() / getMaxHealth());
        if (!isAlive()) {
            return;
        }
        faceCenter();
        if (move.isEmpty()) {
            // 現れたばかりは雲の下から上ってくる
            Vec3 h = homePos();
            setPos(getX() + (h.x - getX()) * 0.1, getY() + (h.y - getY()) * 0.1, getZ() + (h.z - getZ()) * 0.1);
            if (rest > 0) {
                rest--;
                return;
            }
            List<ServerPlayer> here = SeatArena.present(sl).stream().filter(p -> !Downed.isDowned(p)).toList();
            if (here.isEmpty()) {
                return;
            }
            ServerPlayer tg = here.get(random.nextInt(here.size()));
            from = position();
            t = 0;
            if (turn++ % 3 == 2) {
                move = "scrape";
                BlockPos c = PilgrimSeat.mark("arena");
                angle = Math.atan2(tg.getZ() - (c.getZ() + 0.5), tg.getX() - (c.getX() + 0.5));
                playAction("scrape");
                playSound(CloudMobsSetup.HAND_SCRAPE_TELL.get(), 2.0f, 1.0f);
            } else {
                move = "slam";
                spot = tg.position();
                playAction("slam");
                playSound(CloudMobsSetup.HAND_SLAM_TELL.get(), 2.0f, 1.0f);
            }
            return;
        }
        t++;
        switch (move) {
            case "slam" -> slam(sl);
            case "stuck" -> {
                if (t % 10 == 0) {
                    sl.sendParticles(ParticleTypes.WAX_OFF, getX(), getY() + 1.5, getZ(), 6, 1.2, 1.0, 1.2, 0.02);
                }
                if (t >= STUCK) {
                    move = "rise";
                    t = 0;
                    from = position();
                    playAction("rise");
                }
            }
            case "rise" -> {
                Vec3 h = homePos();
                double k = Math.min(1.0, t / (double) RISE);
                setPos(from.x + (h.x - from.x) * k, from.y + (h.y - from.y) * k, from.z + (h.z - from.z) * k);
                if (t >= RISE) {
                    end();
                }
            }
            case "scrape" -> scrape(sl);
            default -> end();
        }
    }

    void end() {
        move = "";
        t = 0;
        boolean alone = SeatArena.parts((ServerLevel) level()).stream().filter(e -> e instanceof PilgrimHand).count() <= 1;
        rest = alone ? 34 : 60;
    }

    // 叩きつけ: 頭上へ寄って (予告の円) → 叩きつけ → 床に手を突いたまま
    private void slam(ServerLevel sl) {
        if (t < SLAM_TELL) {
            Vec3 above = spot.add(0, 5.5, 0);
            double k = Math.min(1.0, t / (double) (SLAM_TELL - 6));
            setPos(from.x + (above.x - from.x) * k, from.y + (above.y - from.y) * k, from.z + (above.z - from.z) * k);
            if (t % 3 == 0) {
                for (int i = 0; i < 20; i++) {
                    double a = i / 20.0 * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.WAX_ON, spot.x + Math.cos(a) * SLAM_R, spot.y + 0.15, spot.z + Math.sin(a) * SLAM_R, 1, 0, 0, 0, 0);
                }
            }
            return;
        }
        setPos(spot.x, spot.y, spot.z);
        playSound(CloudMobsSetup.HAND_SLAM.get(), 2.4f, 1.0f);
        sl.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.4, spot.z, 40, SLAM_R * 0.5, 0.3, SLAM_R * 0.5, 0.08);
        for (ServerPlayer p : SeatArena.present(sl)) {
            if (p.position().subtract(spot).horizontalDistance() <= SLAM_R + 0.3 && Math.abs(p.getY() - spot.y) < 2.5) {
                strike(p, dmg(1.4));
            }
        }
        if (!toldStuck) {
            toldStuck = true;
            for (ServerPlayer p : SeatArena.present(sl)) {
                Notice.narrate(p, "message.skyabyss.seat.hand_stuck");
            }
        }
        move = "stuck";
        t = 0;
    }

    // 削る: 輪の一方へ構えて (掻く跡の光) → 掻く
    private void scrape(ServerLevel sl) {
        BlockPos c = PilgrimSeat.mark("arena");
        Vec3 mid = new Vec3(c.getX() + 0.5 + Math.cos(angle) * 12, c.getY() + 1, c.getZ() + 0.5 + Math.sin(angle) * 12);
        if (t < SCRAPE_TELL) {
            Vec3 to = mid.add(Math.cos(angle) * 6, 3, Math.sin(angle) * 6);
            double k = Math.min(1.0, t / (double) (SCRAPE_TELL - 8));
            setPos(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k, from.z + (to.z - from.z) * k);
            if (t % 3 == 0) {
                for (double d = 8; d <= 16; d += 2) {
                    for (double a = -SCRAPE_HALF; a <= SCRAPE_HALF; a += 8) {
                        double r = angle + Math.toRadians(a);
                        sl.sendParticles(ParticleTypes.WAX_ON, c.getX() + 0.5 + Math.cos(r) * d, c.getY() + 1.15, c.getZ() + 0.5 + Math.sin(r) * d, 1, 0, 0, 0, 0);
                    }
                }
            }
            return;
        }
        if (t == SCRAPE_TELL) {
            playSound(CloudMobsSetup.HAND_SCRAPE.get(), 2.4f, 1.0f);
            int n = BellIsle.silence(sl, BlockPos.containing(mid), 12);
            for (double a = -SCRAPE_HALF; a <= SCRAPE_HALF; a += 6) {
                double r = angle + Math.toRadians(a);
                sl.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5 + Math.cos(r) * 12, c.getY() + 1.3, c.getZ() + 0.5 + Math.sin(r) * 12, 3, 1.5, 0.2, 1.5, 0.03);
            }
            for (ServerPlayer p : SeatArena.present(sl)) {
                Vec3 to = new Vec3(p.getX() - (c.getX() + 0.5), 0, p.getZ() - (c.getZ() + 0.5));
                double d = to.length();
                double diff = Math.abs(Math.atan2(Math.sin(Math.atan2(to.z, to.x) - angle), Math.cos(Math.atan2(to.z, to.x) - angle)));
                if (d >= 7 && d <= 17 && diff <= Math.toRadians(SCRAPE_HALF) && Math.abs(p.getY() - (c.getY() + 1)) < 3) {
                    strike(p, dmg());
                    if (!PromiseIsle.shielded(p)) {
                        Vec3 out = to.normalize();
                        p.push(out.x * PUSH, 0.3, out.z * PUSH);
                        p.hurtMarked = true;
                    }
                }
            }
            if (n > 0 && !toldScrape) {
                toldScrape = true;
                for (ServerPlayer p : SeatArena.present(sl)) {
                    Notice.narrate(p, "message.skyabyss.seat.hand_scrape");
                }
            }
        }
        if (t >= SCRAPE_TELL + 6) {
            move = "rise";
            t = 0;
            from = position();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return super.hurt(source, stuck() ? amount * 1.5f : amount);
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
}
