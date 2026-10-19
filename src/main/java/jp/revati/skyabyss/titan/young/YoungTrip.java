package jp.revati.skyabyss.titan.young;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.titan.end.BoardGlow;
import jp.revati.skyabyss.titan.end.TitanEndNetwork;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.travel.LandingFinder;
import jp.revati.skyabyss.titan.travel.TravelNetwork;
import jp.revati.skyabyss.titan.travel.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// 子の一回の旅 (サーバー側)。状態の流れ:
//   APPEAR    足もとに現れる (1.6 秒。歌の粒が集まる)。この間も乗れる
//   WAIT      持ち主 (と同じパーティの人) が乗る。持ち主が乗ると海図が開き、行き先を決める。誰も乗らずに 20 秒、または 2 分で歌に戻る
//   COUNTDOWN 行き先が決まった。仲間が乗るのを待つ (全員乗っていれば 3 秒、乗っていない仲間がいれば 15 秒、最大 30 秒)
//   CARRY     運ぶ。降りられない。遠い旅 (700 ブロック超) は途中で雲の帳 (潮・雲・霞) に包まれて、行き先の手前 200 ブロックへ移る
//   LAND      行き先 (成体の体の外) へ降りて止まる
//   OFFER     着いた。降りられる。成体が行き先なら光の輪が開き、輪に立つと背へ入る (その人たちにだけ)。誰もいなくなれば歌に溶ける
//   DISSOLVE  歌に溶ける (1.5 秒)。乗っている人は地面に降ろされる
public final class YoungTrip {
    public enum Phase { APPEAR, WAIT, COUNTDOWN, CARRY, LAND, OFFER, DISSOLVE }

    // ---- 時間 (tick)
    public static final int WAIT_LIMIT = 20 * 120;
    public static final int EMPTY_LIMIT = 20 * 20;
    public static final int COUNTDOWN_SOLO = 60;
    public static final int COUNTDOWN_PARTY = 300;
    public static final int COUNTDOWN_MAX = 600;
    public static final double PARTY_RADIUS = 64;
    public static final int OFFER_LIMIT = 20 * 150;
    public static final int OFFER_IDLE = 20 * 12;
    public static final int OFFER_IDLE_PLAIN = 20 * 4;
    // ---- 雲の帳
    public static final double VEIL_DISTANCE = 700;
    public static final double VEIL_EXIT = 200;
    public static final int CRUISE_BEFORE_VEIL = 180;
    public static final int VEIL_FADE = 20;
    // 降りる輪 (成体の乗り込み口)
    public static final double RING_RADIUS = 4.5;
    // 行き先の近くで、止まる所を決め直す距離
    private static final double SETTLE_DISTANCE = 170;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("skyabyss_young", Comparator.comparingLong(ChunkPos::toLong), 300);

    final YoungEntity e;
    final UUID owner;
    final String ownerName;
    private Phase phase = Phase.APPEAR;
    private int phaseTicks;
    private int age;
    private int emptyTicks;
    private int carryTicks;
    @Nullable
    private TravelNetwork.Dest dest;
    @Nullable
    private YoungReach.Pad pad;
    private boolean padSettled;
    @Nullable
    private Vec3 roughTarget;
    private double startDistance;
    private YoungMotion motion;
    private int veilStage;
    private int veilTicks;
    private boolean veilDone;
    @Nullable
    private Vec3 veilExit;
    private int offerIdle;
    private boolean extended;
    private int dissolveReason;

    YoungTrip(YoungEntity e, ServerPlayer owner) {
        this.e = e;
        this.owner = owner.getUUID();
        this.ownerName = owner.getGameProfile().getName();
    }

    public Phase phase() {
        return phase;
    }

    @Nullable
    public TravelNetwork.Dest dest() {
        return dest;
    }

    public boolean departed() {
        return phase == Phase.CARRY || phase == Phase.LAND;
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        e.setPhase(next.ordinal());
    }

    ServerLevel level() {
        return (ServerLevel) e.level();
    }

    @Nullable
    ServerPlayer player(UUID id) {
        return YoungCarriers.playerOf(level().getServer(), id);
    }

    List<ServerPlayer> riders() {
        List<ServerPlayer> list = new ArrayList<>();
        for (Entity p : e.getPassengers()) {
            if (p instanceof ServerPlayer sp) {
                list.add(sp);
            }
        }
        return list;
    }

    boolean ownerAboard() {
        return e.getPassengers().stream().anyMatch(p -> p.getUUID().equals(owner));
    }

    // 持ち主と同じパーティ (パーティがなければ持ち主だけ)
    boolean mayRide(ServerPlayer p) {
        return p.getUUID().equals(owner) || Groups.keyOf(p.server, p.getUUID()).equals(Groups.keyOf(p.server, owner)) && !Groups.keyOf(p.server, owner).equals(owner);
    }

    // ---------------------------------------------------------------- 行き先

    // 持ち主が行き先を決めた。決められなければ理由の鍵を返す
    @Nullable
    String choose(TravelNetwork.Dest d) {
        if (phase != Phase.WAIT) {
            return "message.skyabyss.young.not_now";
        }
        String why = d.lock().isEmpty() ? YoungReach.refuse(level(), e.kind, e.position(), new BlockPos(d.x(), d.y(), d.z())) : d.lock();
        if (!why.isEmpty()) {
            return "gui.skyabyss.young.lock." + why;
        }
        if (Math.hypot(d.x() + 0.5 - e.getX(), d.z() + 0.5 - e.getZ()) < YoungReach.MIN_DISTANCE) {
            return "message.skyabyss.travel.too_close";
        }
        dest = d;
        setPhase(Phase.COUNTDOWN);
        extended = false;
        for (ServerPlayer rider : riders()) {
            Notice.unlock(rider, "message.skyabyss.young.chosen", YoungReach.destName(d));
        }
        return null;
    }

    // ---------------------------------------------------------------- tick

    void tick() {
        age++;
        phaseTicks++;
        ServerLevel level = level();
        List<ServerPlayer> riders = riders();
        e.sweepSeats();
        for (ServerPlayer p : riders) {
            p.fallDistance = 0;
        }
        ServerPlayer lead = player(owner);
        switch (phase) {
            case APPEAR -> {
                motes(level, 2);
                if (phaseTicks >= YoungEntity.APPEAR_TICKS) {
                    setPhase(Phase.WAIT);
                }
            }
            case WAIT -> tickWait(level, riders, lead);
            case COUNTDOWN -> tickCountdown(level, riders, lead);
            case CARRY -> tickCarry(level, riders);
            case LAND -> tickLand(level, riders);
            case OFFER -> tickOffer(level, riders);
            case DISSOLVE -> tickDissolve(level);
        }
    }

    private void tickWait(ServerLevel level, List<ServerPlayer> riders, @Nullable ServerPlayer lead) {
        if (lead == null || !lead.isAlive() || lead.level().dimension() != Level.OVERWORLD || lead.isSpectator()) {
            dissolve("gone", riders);
            return;
        }
        emptyTicks = riders.isEmpty() ? emptyTicks + 1 : 0;
        if (emptyTicks > EMPTY_LIMIT || age > WAIT_LIMIT + 200 && dest == null) {
            dissolve("faded", riders);
            return;
        }
        if (phaseTicks % 40 == 0) {
            motes(level, 1);
        }
    }

    // 同じパーティで、子の近くにいて、まだ乗っていない人
    List<ServerPlayer> mateNotAboard(ServerLevel level) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : YoungCarriers.online(level.getServer())) {
            if (p.getUUID().equals(owner) || p.level() != level || !p.isAlive() || p.isSpectator() || p.getVehicle() == e || !mayRide(p)) {
                continue;
            }
            if (p.distanceToSqr(e) <= PARTY_RADIUS * PARTY_RADIUS && !p.isPassenger()) {
                list.add(p);
            }
        }
        return list;
    }

    private void tickCountdown(ServerLevel level, List<ServerPlayer> riders, @Nullable ServerPlayer lead) {
        if (lead == null || !lead.isAlive() || !ownerAboard()) {
            // 持ち主が降りた: 取りやめて、行き先を決め直せる状態へ
            dest = null;
            setPhase(Phase.WAIT);
            for (ServerPlayer rider : riders) {
                Notice.hint(rider, "message.skyabyss.young.cancelled");
            }
            return;
        }
        if (riders.isEmpty()) {
            dissolve("faded", riders);
            return;
        }
        List<ServerPlayer> waiting = mateNotAboard(level);
        int limit = waiting.isEmpty() ? COUNTDOWN_SOLO : (extended ? COUNTDOWN_MAX : COUNTDOWN_PARTY);
        if (!waiting.isEmpty() && phaseTicks >= COUNTDOWN_PARTY && !extended) {
            extended = true;
            limit = COUNTDOWN_MAX;
        }
        if (phaseTicks % 20 == 0 && phaseTicks > 0) {
            int left = Math.max(0, (limit - phaseTicks + 19) / 20);
            for (ServerPlayer rider : riders) {
                Notice.bar(rider, waiting.isEmpty() ? ModText.hint("message.skyabyss.young.countdown", left)
                        : ModText.hint("message.skyabyss.young.countdown_wait", left, waiting.size()));
            }
            for (ServerPlayer mate : waiting) {
                Notice.bar(mate, ModText.hint("message.skyabyss.young.mate_calling", ownerName, left));
            }
        }
        if (phaseTicks >= limit) {
            depart(level, riders);
        }
    }

    private void depart(ServerLevel level, List<ServerPlayer> riders) {
        if (dest == null) {
            setPhase(Phase.WAIT);
            return;
        }
        origin(level);
        setPhase(Phase.CARRY);
        e.setMoving(true);
        motion = new YoungMotion(e);
        carryTicks = 0;
        veilStage = 0;
        veilDone = false;
        level.playSound(null, e.getX(), e.getY(), e.getZ(), departSound(), SoundSource.PLAYERS, 3f, 1f);
        for (ServerPlayer rider : riders) {
            Notice.narrate(rider, e.kind.text("depart"), YoungReach.destName(dest));
            Voyages.markSafe(rider, BlockPos.containing(e.position()));
        }
        SkyAbyss.LOGGER.info("{}'s {} departs for {} with {} riders", ownerName, e.kind.model, dest.id(), riders.size());
    }

    private void origin(ServerLevel level) {
        startDistance = Math.hypot(dest.x() + 0.5 - e.getX(), dest.z() + 0.5 - e.getZ());
        BlockPos dp = new BlockPos(dest.x(), dest.y(), dest.z());
        roughTarget = null;
        pad = null;
        padSettled = false;
        Vec3 from = e.position();
        if (dest.id().startsWith("t:")) {
            Vec3 at = YoungReach.adultPos(level, dest.id().substring(2));
            Vec3 c = at != null ? at : Vec3.atBottomCenterOf(dp);
            double ang = Math.atan2(from.z - c.z, from.x - c.x);
            roughTarget = new Vec3(c.x + Math.cos(ang) * 60, 0, c.z + Math.sin(ang) * 60);
        } else {
            roughTarget = new Vec3(dest.x() + 0.5, 0, dest.z() + 0.5);
        }
    }

    // 目指す点 (水平)。止まる所が決まっていればそこ
    private Vec3 target() {
        return pad != null ? pad.pos() : roughTarget != null ? roughTarget : e.position();
    }

    private double distanceToTarget() {
        Vec3 t = target();
        return Math.hypot(t.x - e.getX(), t.z - e.getZ());
    }

    private void settlePad(ServerLevel level) {
        if (dest == null) {
            return;
        }
        pad = YoungReach.padFor(level, e.kind, dest.id(), new BlockPos(dest.x(), dest.y(), dest.z()), e.position());
        padSettled = true;
    }

    private void hold(ServerLevel level, Vec3 at) {
        ChunkPos cp = new ChunkPos(BlockPos.containing(at));
        level.getChunkSource().addRegionTicket(TICKET, cp, 2, cp);
    }

    private void tickCarry(ServerLevel level, List<ServerPlayer> riders) {
        if (riders.isEmpty()) {
            dissolve("faded", riders);
            return;
        }
        carryTicks++;
        if (!padSettled && distanceToTarget() < SETTLE_DISTANCE) {
            settlePad(level);
        }
        hold(level, target());
        double dist = distanceToTarget();
        if (tickVeil(level, riders, dist)) {
            return;
        }
        // 降りる準備: 飛ぶ子は 50 ブロック手前から降り始める。地上の子はそのまま寄っていく
        double landFrom = e.kind.flies() ? 50 : 8;
        if (dist < landFrom && padSettled) {
            setPhase(Phase.LAND);
            return;
        }
        YoungMotion.Result r = motion.step(level, target().x, target().z, dist, padSettled ? 3.0 : 40.0, Double.NaN);
        if (r == YoungMotion.Result.ARRIVED && !padSettled) {
            settlePad(level);
        } else if (r == YoungMotion.Result.BLOCKED) {
            stuck(level, riders);
        }
        trail(level);
        if (carryTicks % 80 == 0) {
            level.playSound(null, e.getX(), e.getY(), e.getZ(), moveSound(), SoundSource.PLAYERS, 1.2f, 1f);
        }
    }

    // 道がふさがれた: 行き先まで 140 ブロック以内ならここで止まる。遠ければ雲の帳で行き先の手前へ。それも無理なら歌に戻る
    private void stuck(ServerLevel level, List<ServerPlayer> riders) {
        double dist = distanceToTarget();
        motion.blocked = 0;
        if (dist < 140) {
            pad = new YoungReach.Pad(e.position(), false);
            padSettled = true;
            setPhase(Phase.LAND);
            return;
        }
        if (!veilDone && veilStage == 0) {
            startVeil(level, riders, true);
            if (veilStage != 0) {
                return;
            }
        }
        dissolve("blocked", riders);
    }

    // 雲の帳。戻り値 true = この tick の動きは帳が受け持った
    private boolean tickVeil(ServerLevel level, List<ServerPlayer> riders, double dist) {
        if (veilStage == 0 && !veilDone && startDistance > VEIL_DISTANCE && carryTicks >= CRUISE_BEFORE_VEIL && dist > VEIL_EXIT + 160) {
            startVeil(level, riders, false);
        }
        if (veilStage == 0) {
            return false;
        }
        veilTicks++;
        if (veilStage == 1) {
            // 白く (青く) なりながら、速さを落として進む
            motion.speed = Math.max(0.4, motion.speed - 0.02);
            Vec3 t = target();
            motion.step(level, t.x, t.z, dist, 3.0, Double.NaN);
            if (veilExit != null) {
                hold(level, veilExit);
            }
            if (veilTicks == VEIL_FADE && veilExit != null) {
                int cx = Mth.floor(veilExit.x) >> 4;
                int cz = Mth.floor(veilExit.z) >> 4;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        level.getChunk(cx + dx, cz + dz);
                    }
                }
            }
            if (veilTicks >= VEIL_FADE + 8 && veilExit != null) {
                Vec3 exit = settledExit(level, veilExit);
                Vec3 t2 = target();
                float yaw = (float) (Mth.atan2(t2.z - exit.z, t2.x - exit.x) * Mth.RAD_TO_DEG) - 90f;
                e.setYRot(yaw);
                e.teleportTo(exit.x, exit.y, exit.z);
                SkyAbyss.LOGGER.info("{}'s {} moved through the veil to {}, {}", ownerName, e.kind.model, (int) exit.x, (int) exit.z);
                veilStage = 2;
                veilTicks = 0;
                veilExit = null;
            }
            return true;
        }
        // 移したあと。25 tick 進んで、帳を上げる
        if (veilTicks >= 25) {
            for (ServerPlayer rider : riders) {
                PacketDistributor.sendToPlayer(rider, new TravelNetwork.Veil(false, veilStyle()));
            }
            veilStage = 0;
            veilDone = true;
        }
        return false;
    }

    private int veilStyle() {
        return e.kind.gait == YoungKind.Gait.SEA ? 1 : 0;
    }

    private void startVeil(ServerLevel level, List<ServerPlayer> riders, boolean forced) {
        Vec3 exit = pickExit(level);
        if (exit == null) {
            veilDone = true;
            return;
        }
        veilExit = exit;
        veilStage = 1;
        veilTicks = 0;
        if (motion == null) {
            motion = new YoungMotion(e);
        }
        for (ServerPlayer rider : riders) {
            PacketDistributor.sendToPlayer(rider, new TravelNetwork.Veil(true, veilStyle()));
            rider.playNotifySound(veilSound(), SoundSource.AMBIENT, 1f, 1f);
            Notice.narrate(rider, e.kind.text("veil"));
        }
    }

    // 行き先の手前 VEIL_EXIT の、今の向きの延長上。子の得意に合わない所なら null (帳はやめて、そのまま進む)
    @Nullable
    private Vec3 pickExit(ServerLevel level) {
        Vec3 t = target();
        Vec3 from = e.position();
        double d = Math.hypot(t.x - from.x, t.z - from.z);
        if (d < VEIL_EXIT + 100) {
            return null;
        }
        double ux = (t.x - from.x) / d;
        double uz = (t.z - from.z) / d;
        double x = t.x - ux * VEIL_EXIT;
        double z = t.z - uz * VEIL_EXIT;
        switch (e.kind.gait) {
            case SEA -> {
                BlockPos sea = YoungReach.seaNear(level, BlockPos.containing(x, level.getSeaLevel(), z));
                if (sea == null || Math.hypot(sea.getX() - x, sea.getZ() - z) > 300) {
                    return null;
                }
                return new Vec3(sea.getX() + 0.5, 0, sea.getZ() + 0.5);
            }
            case GROUND -> {
                // 陸の上でなければ帳はやめる (読み込んで確かめるのは移す直前)
                return new Vec3(x, 0, z);
            }
            default -> {
                return new Vec3(x, 0, z);
            }
        }
    }

    // 移す直前に、出口の地形で高さを決める (チャンクは読み込んである)
    private Vec3 settledExit(ServerLevel level, Vec3 exit) {
        if (e.kind.flies()) {
            double max = level.getSeaLevel();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double h = YoungMotion.height(level, Heightmap.Types.WORLD_SURFACE, exit.x + dx * 24, exit.z + dz * 24);
                    if (!Double.isNaN(h)) {
                        max = Math.max(max, h);
                    }
                }
            }
            double y = Math.min(max + e.kind.clearance + 8, level.getMaxBuildHeight() - 12);
            if (e.kind.gait == YoungKind.Gait.SKY) {
                y = Math.max(y, level.getSeaLevel() + 26);
            }
            return new Vec3(exit.x, y, exit.z);
        }
        YoungReach.Pad p = YoungReach.settle(level, e.kind, exit.x, exit.z);
        return p.pos();
    }

    private void tickLand(ServerLevel level, List<ServerPlayer> riders) {
        if (riders.isEmpty()) {
            dissolve("faded", riders);
            return;
        }
        hold(level, target());
        if (pad == null) {
            settlePad(level);
        }
        Vec3 t = target();
        double landY = e.kind.flies() ? pad.pos().y + 0.1 : Double.NaN;
        YoungMotion.Result r = motion.step(level, t.x, t.z, 0, 2.5, landY);
        trail(level);
        if (r == YoungMotion.Result.ARRIVED || phaseTicks > 20 * 25) {
            land(level, riders);
        } else if (r == YoungMotion.Result.BLOCKED && phaseTicks > 60) {
            land(level, riders);
        }
    }

    private void land(ServerLevel level, List<ServerPlayer> riders) {
        e.setMoving(false);
        e.setDeltaMovement(Vec3.ZERO);
        motion.speed = 0;
        setPhase(Phase.OFFER);
        offerIdle = 0;
        boolean titan = dest != null && dest.id().startsWith("t:");
        level.playSound(null, e.getX(), e.getY(), e.getZ(), arriveSound(), SoundSource.PLAYERS, 3f, 1f);
        for (ServerPlayer rider : riders) {
            Voyages.arrived(rider, e.kind, dest);
            Voyages.clearSafe(rider);
            if (titan) {
                Notice.narrate(rider, "message.skyabyss.young.arrived_titan", YoungReach.destName(dest));
                Notice.hint(rider, "message.skyabyss.young.arrived_titan_hint");
            } else {
                Notice.narrate(rider, e.kind.text("arrived"), YoungReach.destName(dest));
            }
        }
        SkyAbyss.LOGGER.info("{}'s {} arrived at {}", ownerName, e.kind.model, dest == null ? "?" : dest.id());
    }

    // ---------------------------------------------------------------- 着いたあと

    private void tickOffer(ServerLevel level, List<ServerPlayer> riders) {
        boolean titan = dest != null && dest.id().startsWith("t:");
        String titanId = titan ? dest.id().substring(2) : "";
        int near = 0;
        if (titan) {
            YoungOffers.tick(this, level, titanId, riders);
            near = YoungOffers.waiting(this, level);
        }
        boolean idle = riders.isEmpty() && near == 0;
        offerIdle = idle ? offerIdle + 1 : 0;
        int limit = titan ? OFFER_IDLE : OFFER_IDLE_PLAIN;
        if (offerIdle >= limit) {
            dissolve("done", riders);
        } else if (phaseTicks > (titan ? OFFER_LIMIT : 20 * 45)) {
            dissolve("done", riders);
        }
    }

    // 降りる場所 (子の中心から横へ 3 ブロック。席の側)
    Vec3 dismountSpot(LivingEntity passenger) {
        int slot = e.slotOf(passenger);
        double side = slot >= 0 && e.kind.seat(slot)[0] < 0 ? -1 : 1;
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double along = slot >= 0 ? e.kind.seat(slot)[2] * 0.4 : 0;
        double x = e.getX() + fz * side * 3.0 + fx * along;
        double z = e.getZ() - fx * side * 3.0 + fz * along;
        double y = e.getY();
        if (!e.kind.flies() || phase == Phase.OFFER || phase == Phase.WAIT || phase == Phase.COUNTDOWN || phase == Phase.DISSOLVE) {
            double h = YoungMotion.height(level(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!Double.isNaN(h) && Math.abs(h - e.getY()) < 6) {
                y = Math.max(h, e.getY() - 1.0);
            }
        }
        return new Vec3(x, y, z);
    }

    // ---------------------------------------------------------------- 終わり

    void dissolve(String reason, List<ServerPlayer> riders) {
        if (phase == Phase.DISSOLVE) {
            return;
        }
        boolean airborne = !riders.isEmpty() && isAirborne();
        if (veilStage != 0 || veilDone) {
            for (ServerPlayer rider : riders) {
                PacketDistributor.sendToPlayer(rider, new TravelNetwork.Veil(false, veilStyle()));
            }
        }
        veilStage = 0;
        e.setMoving(false);
        if (airborne) {
            // 宙に残さない: 地面へ降ろして、ゆっくり落ちる効果をつける
            for (ServerPlayer rider : riders) {
                setDown(rider);
            }
        }
        for (ServerPlayer rider : riders) {
            YoungCarriers.allowDismount(rider, rider::stopRiding);
            Voyages.clearSafe(rider);
            if (!reason.equals("done")) {
                Notice.hint(rider, "message.skyabyss.young.dissolved." + reason);
            }
        }
        dissolveReason = reason.hashCode();
        ServerPlayer lead = player(owner);
        if (lead != null) {
            Voyages.setCooldown(lead, 100);
        }
        level().playSound(null, e.getX(), e.getY(), e.getZ(), dissolveSound(), SoundSource.PLAYERS, 2.5f, 1.4f);
        setPhase(Phase.DISSOLVE);
    }

    private boolean isAirborne() {
        double h = YoungMotion.height(level(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getX(), e.getZ());
        return Double.isNaN(h) || e.getY() > h + 2.5;
    }

    // 子の真下の地面 (なければ安全な所) へ
    private void setDown(ServerPlayer p) {
        ServerLevel level = level();
        BlockPos spot = LandingFinder.find(level, BlockPos.containing(e.getX(), level.getSeaLevel(), e.getZ()), 2, 8);
        YoungCarriers.allowDismount(p, p::stopRiding);
        p.teleportTo(level, spot.getX() + 0.5, spot.getY() + 0.1, spot.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 8, 0, false, false, true));
    }

    private void tickDissolve(ServerLevel level) {
        motes(level, 3);
        for (Entity p : new ArrayList<>(e.getPassengers())) {
            if (p instanceof ServerPlayer sp) {
                YoungCarriers.allowDismount(sp, sp::stopRiding);
            }
        }
        if (phaseTicks >= YoungEntity.DISSOLVE_TICKS) {
            e.discard();
        }
    }

    // ---------------------------------------------------------------- 見せ方・音

    private void motes(ServerLevel level, int amount) {
        for (int i = 0; i < amount; i++) {
            level.sendParticles(ParticleTypes.END_ROD, e.getX() + (level.random.nextDouble() - 0.5) * 6, e.getY() + 0.5 + level.random.nextDouble() * 3,
                    e.getZ() + (level.random.nextDouble() - 0.5) * 6, 1, 0, 0.05, 0, 0.02);
        }
        if (phaseTicks % 6 == 0) {
            level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + 3.0, e.getZ(), 1, 1.5, 0.3, 1.5, 0.0);
        }
    }

    private void trail(ServerLevel level) {
        if (e.tickCount % 3 != 0) {
            return;
        }
        ParticleOptions p = switch (e.kind.gait) {
            case SKY -> ParticleTypes.CLOUD;
            case SEA -> ParticleTypes.SPLASH;
            case RIDGE -> ParticleTypes.ELECTRIC_SPARK;
            case GROUND -> ParticleTypes.SMALL_FLAME;
        };
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        double bx = e.getX() + Mth.sin(yaw) * 4;
        double bz = e.getZ() - Mth.cos(yaw) * 4;
        level.sendParticles(p, bx, e.getY() + 0.6, bz, 3, 1.4, 0.3, 1.4, 0.02);
    }

    private SoundEvent departSound() {
        return switch (e.kind) {
            case TIDE -> jp.revati.skyabyss.titan.travel.TravelSetup.TIDE_DEPART_SOUND.get();
            default -> jp.revati.skyabyss.titan.travel.TravelSetup.DEPART_SOUND.get();
        };
    }

    private SoundEvent moveSound() {
        return switch (e.kind) {
            case TIDE -> jp.revati.skyabyss.titan.travel.TravelSetup.TIDE_CURRENT_SOUND.get();
            case STORM -> jp.revati.skyabyss.titan.storm.StormSetup.WING_CALL.get();
            case EMBER -> jp.revati.skyabyss.titan.ember.EmberTitanSetup.RUMBLE.get();
            default -> jp.revati.skyabyss.titan.travel.TravelSetup.VEIL_SOUND.get();
        };
    }

    private SoundEvent veilSound() {
        return e.kind == YoungKind.TIDE ? jp.revati.skyabyss.titan.travel.TravelSetup.TIDE_CURRENT_SOUND.get() : jp.revati.skyabyss.titan.travel.TravelSetup.VEIL_SOUND.get();
    }

    private SoundEvent arriveSound() {
        return YoungCarriers.callSound(e.kind);
    }

    private SoundEvent dissolveSound() {
        return YoungCarriers.callSound(e.kind);
    }

    // ---------------------------------------------------------------- 光の輪 (YoungOffers が使う)

    Vec3 ringCentre() {
        return e.position();
    }

    TitanEndNetwork.Beam beam(String titan) {
        Vec3 c = ringCentre();
        return new TitanEndNetwork.Beam("young_" + titan, BoardGlow.colour(titan), c.x, c.y + 1.2, c.z, (float) RING_RADIUS, 1.6f, (float) RING_RADIUS, 0f,
                c.y, c.y + BoardGlow.MIN_BEAM, 1.0f, BoardGlow.BEAM_TTL);
    }

    boolean titanEligible(ServerPlayer p, String titan) {
        return Progression.canBoard(p, titan);
    }

    int dissolveCode() {
        return dissolveReason;
    }
}
