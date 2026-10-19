package jp.revati.skyabyss.titan.travel;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// 一回の「巨神での移動」。呼ばれた巨神を外から操縦する。巨神は一体だけなので、旅も巨神ごとに一つだけ。
//
//   COMING   呼んだ人の上へ泳いでくる (遠ければ雲の向こうから呼び寄せる)
//   WAITING  乗り場の鎖を垂らして待つ。鎖を右クリックすると頭の乗り場へ引き上げられる。呼んだ人が座ると海図が開く
//   BOARDING 行き先が決まった。少し待って (ほかの人が乗る時間) 鎖をたたむ
//   ASCENT   身を起こして雲の上へ昇り、行き先へ向き直る
//   CRUISE   雲の海の上を泳ぐ。遠い旅はしばらく泳いだあと雲の帳へ
//   VEIL     雲の海へ潜る (画面が白く包まれる)。行き先の手前の読み込みを待ち、そこへ移す
//   APPROACH 行き先の上空へ降りていき、鎖の垂れ口を降ろす場所の真上に合わせる
//   LOWERING 鎖を垂らし、乗っている人を一人ずつ鎖で地上へ降ろす
//
// 乗っている人は巨神の「乗客」(vanilla の騎乗) なので、画面は巨神と一緒になめらかに動く。
// 上る・下りるあいだは重力を切って速さを与える (背中の鎖の引き上げと同じやり方)
class Voyage {
    enum Phase { COMING, WAITING, BOARDING, ASCENT, CRUISE, VEIL, APPROACH, LOWERING }

    // 行き先の前もっての読み込み (時間切れ付き。張り直しが止まれば外れる)
    private static final TicketType<ChunkPos> TICKET = TicketType.create("skyabyss_voyage", Comparator.comparingLong(ChunkPos::toLong), 300);
    private static final int TICKET_RADIUS = 2;

    final TitanKind kind;
    final TravelTitan titan;
    UUID leader;
    String leaderName;
    Phase phase = Phase.COMING;
    int phaseTicks;
    int totalTicks;
    // 迎えの場所: 鎖の垂れ口の真下の、立てる位置
    BlockPos pickup;
    // 行き先 (決まるまで null) と、降ろす場所 (着くまで null)
    @Nullable
    TravelNetwork.Dest dest;
    @Nullable
    BlockPos landing;
    // 乗っている人、鎖を上っている人、鎖で降ろしている人
    final Set<UUID> riders = new LinkedHashSet<>();
    final Map<UUID, Climb> climbs = new LinkedHashMap<>();
    final Map<UUID, Climb> lowers = new LinkedHashMap<>();
    // 旅の速さ (少しずつ上げ下げする)
    double speed;
    int cruiseFor;
    @Nullable
    ChunkPos ticketPos;
    long ticketRefreshed;
    boolean veiled;
    int veilMovedAt = -1;
    // 雲の帳の出口 (移すまで)
    @Nullable
    Vec3 veilExit;
    // 呼び寄せ: 雲の向こうの入口へ移すのを待っている
    @Nullable
    Vec3 relocateTo;
    boolean finished;
    // 背中の人に、運ばれていくことを一度だけ知らせる
    boolean toldAboard;
    // 自己テスト: 乗る人がいなくても旅を進める (呼んだ人の不在や空の乗り場で取りやめない)
    boolean test;
    // 寄りきれずに、真上へ移して済ませた回数 (自己テストで 0 を確かめる)
    int snaps;
    // 呼んだ人が迎えの場所から離れている tick 数
    int leaderAway;
    // 出発の待ちの上限 (tick)。近くの仲間がまだ乗っていないあいだ延びる。笛での早い出発では今の tick に縮む
    int boardingLimit = TravelRules.BOARDING_COUNTDOWN;
    // 近くの仲間が全員乗った時刻 (phaseTicks)。乗っていない人がいれば -1
    int allAboardSince = -1;
    // 出発の待ちを近くの全員に見せる帯 (ボスバー)
    @Nullable
    net.minecraft.server.level.ServerBossEvent boardingBar;

    // 鎖を上る (dir > 0) ・下りる (dir < 0) 人の進み具合
    static final class Climb {
        int stage;
        int ticks;
        final boolean up;

        Climb(boolean up) {
            this.up = up;
        }
    }

    Voyage(TitanKind kind, TravelTitan titan, ServerPlayer leader, BlockPos pickup) {
        this.kind = kind;
        this.titan = titan;
        this.leader = leader.getUUID();
        this.leaderName = leader.getGameProfile().getName();
        this.pickup = pickup;
    }

    Entity entity() {
        return titan.asEntity();
    }

    ServerLevel level() {
        return (ServerLevel) entity().level();
    }

    boolean beforeDeparture() {
        return phase == Phase.COMING || phase == Phase.WAITING || phase == Phase.BOARDING;
    }

    boolean involves(UUID id) {
        return riders.contains(id) || climbs.containsKey(id) || lowers.containsKey(id);
    }

    int occupied() {
        return riders.size() + climbs.size();
    }

    // ---------------------------------------------------------------- 毎 tick

    void tick() {
        ServerLevel level = level();
        Entity whale = entity();
        totalTicks++;
        phaseTicks++;
        if (relocateTo == null && veilExit == null) {
            // 泳いでいく少し先を中心に読み込む
            holdMoveTicket(level, new ChunkPos(BlockPos.containing(whale.position().add(whale.getDeltaMovement().scale(16)))));
        }
        tickRiders(level);
        tickClimbs(level);
        tickLowers(level);
        switch (phase) {
            case COMING -> tickComing(level, whale);
            case WAITING -> tickWaiting(level, whale);
            case BOARDING -> tickBoarding(level, whale);
            case ASCENT -> tickAscent(level, whale);
            case CRUISE -> tickCruise(level, whale);
            case VEIL -> tickVeil(level, whale);
            case APPROACH -> tickApproach(level, whale);
            case LOWERING -> tickLowering(level, whale);
        }
        if (dest != null && !beforeDeparture() && phase != Phase.LOWERING && totalTicks % 20 == 0) {
            int seconds = Math.max(1, (int) Math.round(remainingTicks(whale) / 20.0));
            for (ServerPlayer rider : players(riders)) {
                Notice.bar(rider, ModText.hint(kind.text("message.skyabyss.travel.underway"), Voyages.destName(dest), seconds));
            }
        }
    }

    void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        hoverYaw = null;
        hoverHeightCache = Double.NaN;
    }

    // ---- 迎え
    void tickComing(ServerLevel level, Entity whale) {
        if (relocateTo != null) {
            // 雲の向こうの入口のチャンクが読み込まれるのを待ってから移す (読み込まれていない所へ移すと止まってしまう)
            holdMoveTicket(level, new ChunkPos(BlockPos.containing(relocateTo)));
            if (loaded(level, relocateTo) || phaseTicks > TravelRules.VEIL_WAIT_LIMIT) {
                whale.teleportTo(relocateTo.x, relocateTo.y, relocateTo.z);
                whale.setYRot(yawTowards(whale.position(), Vec3.atCenterOf(pickup)));
                relocateTo = null;
                level.playSound(null, whale.getX(), whale.getY(), whale.getZ(), ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 24f, 0.8f);
            }
            return;
        }
        double hoverY = hoverHeight(level, whale, pickup);
        boolean arrived = flyToHover(level, whale, pickup, hoverY);
        if (arrived || phaseTicks > TravelRules.COMING_LIMIT) {
            if (!arrived) {
                snaps++;
                // 地形の都合などで寄りきれない。迎えの場所の上へ寄せる (呼んだ人の真上には落とさない)
                float yaw = hoverYaw(whale, Vec3.atBottomCenterOf(pickup));
                Vec3 center = TitanKind.centerForChainAt(titan, Vec3.atBottomCenterOf(pickup), yaw, hoverY);
                whale.setYRot(yaw);
                whale.teleportTo(center.x, center.y, center.z);
            }
            whale.setDeltaMovement(Vec3.ZERO);
            speed = 0;
            titan.setVoyageChain(pickup.getY());
            setPhase(Phase.WAITING);
            level.playSound(null, whale.getX(), whale.getY(), whale.getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.AMBIENT, 6f, 0.8f);
            ServerPlayer lead = player(leader);
            if (lead != null) {
                Notice.narrate(lead, kind.text("message.skyabyss.travel.arrived_pickup"));
            }
        }
    }

    // ---- 乗り場で待つ
    void tickWaiting(ServerLevel level, Entity whale) {
        hover(whale);
        titan.setVoyageChain(pickup.getY());
        if (test) {
            return;
        }
        boolean leaderAboard = riders.contains(leader);
        boolean leaderClimbing = climbs.containsKey(leader);
        if (!leaderAboard && !leaderClimbing) {
            // 呼んだ人が乗る前にいなくなった (ログアウト・遠くへ行った)。ほかに乗っている人がいれば、その人に任せる
            ServerPlayer lead = player(leader);
            // 少し離れただけ (横に 96 ブロックまで、または 10 秒まで) では取りやめない
            boolean away = lead == null || lead.level() != level
                    || Math.hypot(lead.getX() - pickup.getX(), lead.getZ() - pickup.getZ()) > 96;
            leaderAway = away ? leaderAway + 1 : 0;
            boolean gone = lead == null || lead.level() != level || leaderAway > 200;
            if (gone && !passLeadership()) {
                if (riders.isEmpty() && climbs.isEmpty()) {
                    cancel(kind.text("message.skyabyss.travel.cancel.gone"), true);
                    return;
                }
            }
            if (phaseTicks > TravelRules.WAIT_FOR_LEADER && riders.isEmpty() && climbs.isEmpty()) {
                cancel(kind.text("message.skyabyss.travel.cancel.waited"), true);
                return;
            }
        }
        if (phaseTicks > TravelRules.WAIT_FOR_LEADER + TravelRules.CHOOSE_LIMIT) {
            cancel(kind.text("message.skyabyss.travel.cancel.waited"), true);
        }
    }

    // ---- 行き先が決まった。少し待って出発
    void tickBoarding(ServerLevel level, Entity whale) {
        hover(whale);
        if (dest != null && !pendingCancel && !test) {
            List<ServerPlayer> notAboard = partyNotAboard(level);
            boardingLimit = TravelRules.boardingLimit(boardingLimit, phaseTicks, !notAboard.isEmpty());
            // 近くの仲間が全員乗った (上っている人もいない): 5 秒で出発。だれかが近づいてきたら、数え直す
            boolean everyoneAboard = notAboard.isEmpty() && climbs.isEmpty() && !riders.isEmpty();
            if (!everyoneAboard) {
                allAboardSince = -1;
            } else if (allAboardSince < 0) {
                allAboardSince = phaseTicks;
            }
            boardingLimit = TravelRules.autoDepartLimit(boardingLimit, allAboardSince, phaseTicks);
        }
        int left = boardingLimit - phaseTicks;
        if (left > 0) {
            titan.setVoyageChain(pickup.getY());
            if (dest != null && !pendingCancel) {
                updateBoardingBar(level, left);
            }
            return;
        }
        closeBoardingBar();
        if (!climbs.isEmpty()) {
            // 上っている途中の人を待つ
            return;
        }
        if (riders.isEmpty() && !test) {
            cancel(kind.text("message.skyabyss.travel.cancel.empty"), true);
            return;
        }
        titan.setVoyageChain(Double.NaN);
        if (titan.isChainFolded() || phaseTicks > boardingLimit + 60) {
            setPhase(Phase.ASCENT);
            Vec3 at = whale.position();
            level.playSound(null, at.x, at.y, at.z, kind.departSound().get(), SoundSource.AMBIENT, 12f, 1.0f);
            level.playSound(null, at.x, at.y, at.z, ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 20f, 0.9f);
            for (ServerPlayer rider : players(riders)) {
                // 途中でログアウトしても、行き先へ降ろす
                Voyages.markSafe(rider, destFeet());
                showTitle(rider, Component.translatable(kind.text("title.skyabyss.travel.depart")),
                        Component.translatable(kind.text("title.skyabyss.travel.depart.sub"), Voyages.destName(dest)));
            }
            holdTicket(level, new ChunkPos(destFeet()));
            tellAboard(level, "message.skyabyss.travel.aboard_departs");
        }
    }

    // 同じパーティで、乗り場の近くにいて、まだ乗っていない (上っている途中も含む) 人
    List<ServerPlayer> partyNotAboard(ServerLevel level) {
        List<ServerPlayer> list = new ArrayList<>();
        UUID group = jp.revati.skyabyss.titan.group.Groups.keyOf(level.getServer(), leader);
        double r2 = TravelRules.BOARDING_PARTY_RADIUS * TravelRules.BOARDING_PARTY_RADIUS;
        for (ServerPlayer p : jp.revati.skyabyss.titan.group.Groups.alive(level, group)) {
            if (!riders.contains(p.getUUID()) && !lowers.containsKey(p.getUUID())
                    && p.distanceToSqr(Vec3.atCenterOf(pickup)) <= r2) {
                list.add(p);
            }
        }
        return list;
    }

    // 近くの全員に出発までの残りを見せる。呼んだ人には、笛で早く出発できることも知らせる
    void updateBoardingBar(ServerLevel level, int left) {
        if (boardingBar == null) {
            boardingBar = new net.minecraft.server.level.ServerBossEvent(Component.empty(),
                    net.minecraft.world.BossEvent.BossBarColor.WHITE, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
        }
        boolean extended = boardingLimit > TravelRules.BOARDING_COUNTDOWN;
        boolean auto = allAboardSince >= 0;
        boardingBar.setName(ModText.hint(kind.text(auto ? "message.skyabyss.travel.all_aboard"
                : extended ? "message.skyabyss.travel.boarding_wait" : "message.skyabyss.travel.boarding"), (left + 19) / 20));
        boardingBar.setProgress(Mth.clamp(left / (float) Math.max(1, auto ? TravelRules.AUTO_DEPART : boardingLimit), 0f, 1f));
        boardingBar.setColor(auto ? net.minecraft.world.BossEvent.BossBarColor.GREEN
                : extended ? net.minecraft.world.BossEvent.BossBarColor.YELLOW : net.minecraft.world.BossEvent.BossBarColor.WHITE);
        double r2 = TravelRules.BOARDING_PARTY_RADIUS * TravelRules.BOARDING_PARTY_RADIUS;
        Vec3 at = Vec3.atCenterOf(pickup);
        for (ServerPlayer p : new ArrayList<>(boardingBar.getPlayers())) {
            if (p.level() != level || p.distanceToSqr(at) > r2 * 1.5 && !riders.contains(p.getUUID())) {
                boardingBar.removePlayer(p);
            }
        }
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.distanceToSqr(at) <= r2 || riders.contains(p.getUUID())) {
                boardingBar.addPlayer(p);
            }
        }
        if (phaseTicks % 100 == 50) {
            callParty(level, false);
        }
        if (phaseTicks % 40 == 0 && !(allAboardSince >= 0)) {
            // まだ乗っていない仲間がいるあいだ、呼んだ人に残り人数を知らせる (出発は全員乗れば自動)
            ServerPlayer lead = player(leader);
            if (lead != null && riders.contains(leader)) {
                Notice.bar(lead, ModText.hint(kind.text("message.skyabyss.travel.waiting_party"), partyNotAboard(level).size()));
            }
        }
    }

    // まだ乗っていない同じパーティの人 (同じ空にいる全員) に、鎖の方角と距離を知らせる
    void callParty(ServerLevel level, boolean first) {
        UUID group = jp.revati.skyabyss.titan.group.Groups.keyOf(level.getServer(), leader);
        for (ServerPlayer p : jp.revati.skyabyss.titan.group.Groups.alive(level, group)) {
            if (riders.contains(p.getUUID()) || climbs.containsKey(p.getUUID()) || lowers.containsKey(p.getUUID())
                    || p.getUUID().equals(leader)) {
                continue;
            }
            int far = (int) Math.round(Math.hypot(p.getX() - pickup.getX(), p.getZ() - pickup.getZ()));
            Component text = ModText.hint(kind.text("message.skyabyss.travel.party_call"), leaderName,
                    Component.translatable("message.skyabyss.travel.compass." + SeaVoyage.compass(p.blockPosition(), pickup)), far);
            if (first) {
                Notice.unlock(p, kind.text("message.skyabyss.travel.party_call"), leaderName,
                        Component.translatable("message.skyabyss.travel.compass." + SeaVoyage.compass(p.blockPosition(), pickup)), far);
            } else {
                Notice.bar(p, text);
            }
        }
    }

    void closeBoardingBar() {
        if (boardingBar != null) {
            boardingBar.removeAllPlayers();
            boardingBar = null;
        }
    }

    // ---- 雲の上へ
    void tickAscent(ServerLevel level, Entity whale) {
        holdTicket(level, new ChunkPos(destFeet()));
        double travelY = travelAltitude(level, whale);
        Vec3 target = Vec3.atCenterOf(destFeet());
        float yaw = Mth.approachDegrees(whale.getYRot(), yawTowards(whale.position(), target), TravelRules.TURN_RATE * 1.5f);
        speed = Math.min(0.6, speed + 0.01);
        double vy = Mth.clamp((travelY - whale.getY()) * 0.03, -0.25, 0.35);
        move(whale, yaw, speed, vy);
        if (Math.abs(whale.getY() - travelY) < 2 || phaseTicks > 240) {
            double distance = horizontal(whale.position(), target);
            cruiseFor = distance > TravelRules.VEIL_DISTANCE ? TravelRules.cruiseTicks(distance) : Integer.MAX_VALUE;
            setPhase(Phase.CRUISE);
        }
    }

    // ---- 雲の海の上を泳ぐ
    void tickCruise(ServerLevel level, Entity whale) {
        holdTicket(level, new ChunkPos(destFeet()));
        Vec3 target = Vec3.atCenterOf(destFeet());
        double distance = horizontal(whale.position(), target);
        float yaw = Mth.approachDegrees(whale.getYRot(), yawTowards(whale.position(), target), TravelRules.TURN_RATE);
        speed = Math.min(TravelRules.TRAVEL_SPEED, speed + 0.015);
        double vy = Mth.clamp((travelAltitude(level, whale) - whale.getY()) * 0.03, -0.25, 0.25);
        move(whale, yaw, speed, vy);
        if (totalTicks % 30 == 0) {
            // 吹き抜ける風に、ちぎれた雲が混じる
            for (ServerPlayer rider : players(riders)) {
                level.sendParticles(rider, ParticleTypes.CLOUD, true, rider.getX(), rider.getY() + 1, rider.getZ(), 6, 3, 1.5, 3, 0.05);
            }
        }
        if (phaseTicks >= cruiseFor && distance > TravelRules.VEIL_EXIT + 180) {
            setPhase(Phase.VEIL);
            veiled = true;
            veilMovedAt = -1;
            for (ServerPlayer rider : players(riders)) {
                PacketDistributor.sendToPlayer(rider, new TravelNetwork.Veil(true, kind.style().ordinal()));
                rider.playNotifySound(kind.veilSound().get(), SoundSource.AMBIENT, 1f, 1f);
                Notice.narrate(rider, kind.text("message.skyabyss.travel.veil"));
            }
            return;
        }
        if (distance < TravelRules.VEIL_EXIT) {
            setPhase(Phase.APPROACH);
        }
    }

    // ---- 雲の帳。白くなりきったら、行き先の手前へ移す
    void tickVeil(ServerLevel level, Entity whale) {
        BlockPos feet = destFeet();
        holdTicket(level, new ChunkPos(feet));
        Vec3 target = Vec3.atCenterOf(feet);
        if (veilMovedAt < 0) {
            if (veilExit == null) {
                // 出口: 行き先の手前、今の向きの延長上
                Vec3 from = whale.position();
                Vec3 dir = new Vec3(target.x - from.x, 0, target.z - from.z).normalize();
                veilExit = new Vec3(target.x - dir.x * TravelRules.VEIL_EXIT, 0, target.z - dir.z * TravelRules.VEIL_EXIT);
            }
            // 出口のまわりを読み込ませる (移した先で巨神が止まらないように)
            holdMoveTicket(level, new ChunkPos(BlockPos.containing(veilExit)));
            // 雲の中へ沈みながら、速さを落として進む
            speed = Math.max(0.4, speed - 0.03);
            move(whale, whale.getYRot(), speed, -0.15);
            boolean ready = loaded(level, veilExit) && loaded(level, target);
            if (phaseTicks >= TravelRules.VEIL_FADE + 10 && (ready || phaseTicks > TravelRules.VEIL_WAIT_LIMIT)) {
                Vec3 exit = veilExit;
                double y = Math.max(TravelRules.TRAVEL_Y, surfaceMax(level, exit) + TravelRules.TERRAIN_CLEARANCE);
                y = Math.min(y, level.getMaxBuildHeight() - 50);
                whale.teleportTo(exit.x, y, exit.z);
                whale.setYRot(yawTowards(whale.position(), target));
                veilMovedAt = phaseTicks;
                veilExit = null;
                SkyAbyss.LOGGER.info("Titan voyage: moved {} through the cloud veil to {}, {}", kind.id(), (int) exit.x, (int) exit.z);
            }
            return;
        }
        move(whale, whale.getYRot(), speed, 0);
        if (phaseTicks - veilMovedAt >= 25) {
            for (ServerPlayer rider : players(riders)) {
                PacketDistributor.sendToPlayer(rider, new TravelNetwork.Veil(false, kind.style().ordinal()));
            }
            veiled = false;
            setPhase(Phase.APPROACH);
        }
    }

    // ---- 行き先の上空へ降りる
    void tickApproach(ServerLevel level, Entity whale) {
        BlockPos feet = destFeet();
        holdTicket(level, new ChunkPos(feet));
        if (landing == null && level.getChunkSource().hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) {
            landing = LandingFinder.find(level, destPos(), 1, 10);
            // 降ろす場所が決まったので、止まる高さを測り直す
            hoverHeightCache = Double.NaN;
        }
        BlockPos spot = landing != null ? landing : feet;
        double hoverY = hoverHeight(level, whale, spot);
        boolean arrived = flyToHover(level, whale, spot, hoverY);
        if (arrived || phaseTicks > TravelRules.COMING_LIMIT) {
            if (!arrived) {
                snaps++;
                float yaw = hoverYaw(whale, Vec3.atBottomCenterOf(spot));
                Vec3 center = TitanKind.centerForChainAt(titan, Vec3.atBottomCenterOf(spot), yaw, hoverY);
                whale.setYRot(yaw);
                whale.teleportTo(center.x, center.y, center.z);
            }
            if (landing == null) {
                landing = LandingFinder.find(level, destPos(), 1, 10);
            }
            whale.setDeltaMovement(Vec3.ZERO);
            speed = 0;
            setPhase(Phase.LOWERING);
            level.playSound(null, whale.getX(), whale.getY(), whale.getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.AMBIENT, 6f, 0.8f);
            for (ServerPlayer rider : players(riders)) {
                showTitle(rider, Component.literal(Voyages.destName(dest).getString()), Component.translatable(kind.text("title.skyabyss.travel.arrive.sub")));
            }
            tellAboard(level, "message.skyabyss.travel.aboard_arrives");
        }
    }

    // ---- 鎖で降ろす
    void tickLowering(ServerLevel level, Entity whale) {
        hover(whale);
        BlockPos spot = landing != null ? landing : destFeet();
        titan.setVoyageChain(spot.getY());
        // 鎖が下りきってから (または 6 秒たってから)、一人ずつ降ろす
        if ((titan.isVoyageChainDown() || phaseTicks > 120) && phaseTicks % 8 == 0 && !riders.isEmpty()) {
            UUID next = riders.iterator().next();
            ServerPlayer rider = player(next);
            riders.remove(next);
            if (rider != null) {
                startLower(rider);
            }
        }
        if (riders.isEmpty() && lowers.isEmpty() && phaseTicks > 20) {
            finish(true);
        } else if (phaseTicks > 120 + TravelRules.LOWER_LIMIT + riders.size() * 8 + 40) {
            // 何かで降ろしきれない人がいる。緩やかに落ちる効果をつけて放す
            for (UUID id : new ArrayList<>(lowers.keySet())) {
                ServerPlayer p = player(id);
                if (p != null) {
                    releaseLower(p, true);
                }
            }
            lowers.clear();
            for (ServerPlayer p : players(riders)) {
                dismount(p);
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60, 0, false, false, true));
                Voyages.clearSafe(p);
            }
            riders.clear();
            finish(true);
        }
    }

    // ---------------------------------------------------------------- 乗る・降りる

    // 鎖を右クリックした
    void grab(ServerPlayer player) {
        if (!(phase == Phase.WAITING || phase == Phase.BOARDING) || involves(player.getUUID())) {
            return;
        }
        if (occupied() >= titan.seatCount()) {
            Notice.bar(player, ModText.deny("message.skyabyss.travel.full"));
            return;
        }
        if (phase == Phase.BOARDING && phaseTicks >= boardingLimit) {
            Notice.bar(player, ModText.deny("message.skyabyss.travel.leaving"));
            return;
        }
        player.stopRiding();
        player.setNoGravity(true);
        climbs.put(player.getUUID(), new Climb(true));
        // ログアウトしても宙に残さない: 迎えの場所へ戻す
        Voyages.markSafe(player, pickup);
        level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.PLAYERS, 2f, 1.1f);
        Notice.bar(player, ModText.hint(kind.text("message.skyabyss.travel.climbing")));
    }

    void tickClimbs(ServerLevel level) {
        for (UUID id : new ArrayList<>(climbs.keySet())) {
            ServerPlayer p = player(id);
            Climb c = climbs.get(id);
            if (p == null || !p.isAlive() || p.level() != level) {
                climbs.remove(id);
                if (p != null) {
                    p.setNoGravity(false);
                }
                continue;
            }
            c.ticks++;
            List<Vec3> path = titan.boardingPath();
            p.fallDistance = 0;
            Vec3 velocity;
            if (c.stage == 0) {
                Vec3 hang = path.get(0);
                velocity = new Vec3((hang.x - p.getX()) * 0.3, TravelRules.CLIMB_UP, (hang.z - p.getZ()) * 0.3);
                if (p.getY() >= hang.y - 0.6) {
                    c.stage = 1;
                }
            } else if (c.stage < path.size()) {
                velocity = towards(p.position(), path.get(c.stage), TravelRules.CLIMB_ALONG);
                if (p.position().distanceTo(path.get(c.stage)) < 0.9) {
                    c.stage++;
                }
            } else {
                velocity = Vec3.ZERO;
            }
            if (c.ticks % 6 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHAIN.defaultBlockState()),
                        p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
            }
            if (c.ticks % 20 == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.PLAYERS, 1.0f, 1.25f);
            }
            if (c.stage >= path.size() || c.ticks > TravelRules.CLIMB_LIMIT) {
                climbs.remove(id);
                p.setNoGravity(false);
                p.setDeltaMovement(Vec3.ZERO);
                seat(p);
                continue;
            }
            p.setDeltaMovement(velocity);
            p.hurtMarked = true;
        }
    }

    void seat(ServerPlayer p) {
        Entity whale = entity();
        if (occupied() >= titan.seatCount() || !p.startRiding(whale, true)) {
            // 座れなかった (満席)。鎖で降ろし直す
            startLower(p);
            return;
        }
        riders.add(p.getUUID());
        p.fallDistance = 0;
        Voyages.markSafe(p, pickup);
        if (p.getUUID().equals(leader)) {
            if (dest == null) {
                Voyages.sendChart(p, this);
            }
            Notice.narrate(p, kind.text("message.skyabyss.travel.seated_leader"));
        } else {
            Notice.narrate(p, kind.text("message.skyabyss.travel.seated"), leaderName);
        }
    }

    // 鎖で地上へ降ろし始める (乗り場から降りる・着いた)
    void startLower(ServerPlayer p) {
        riders.remove(p.getUUID());
        climbs.remove(p.getUUID());
        dismount(p);
        p.setNoGravity(true);
        p.fallDistance = 0;
        lowers.put(p.getUUID(), new Climb(false));
    }

    void tickLowers(ServerLevel level) {
        BlockPos spot = phase == Phase.LOWERING ? (landing != null ? landing : destFeet()) : pickup;
        for (UUID id : new ArrayList<>(lowers.keySet())) {
            ServerPlayer p = player(id);
            Climb c = lowers.get(id);
            if (p == null || !p.isAlive() || p.level() != level) {
                lowers.remove(id);
                if (p != null) {
                    p.setNoGravity(false);
                }
                continue;
            }
            c.ticks++;
            p.fallDistance = 0;
            List<Vec3> path = titan.boardingPath();
            // 道すじを逆にたどる: 留め具 → 曲がり目 → 垂れ口 → 鎖を下へ
            int last = path.size() - 1;
            Vec3 velocity;
            if (c.stage <= last) {
                Vec3 point = path.get(last - c.stage);
                velocity = towards(p.position(), point, TravelRules.CLIMB_ALONG);
                if (p.position().distanceTo(point) < 0.9) {
                    c.stage++;
                }
            } else {
                Vec3 hang = path.get(0);
                velocity = new Vec3((hang.x - p.getX()) * 0.3, -TravelRules.LOWER_DOWN, (hang.z - p.getZ()) * 0.3);
                if (p.onGround() || p.getY() <= spot.getY() + 0.2 || p.isInWater()) {
                    releaseLower(p, false);
                    lowers.remove(id);
                    continue;
                }
            }
            if (c.ticks > TravelRules.LOWER_LIMIT) {
                releaseLower(p, true);
                lowers.remove(id);
                continue;
            }
            if (c.ticks % 20 == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.PLAYERS, 1.0f, 0.95f);
            }
            p.setDeltaMovement(velocity);
            p.hurtMarked = true;
        }
    }

    // 降ろし終えた。stuck なら (届かなかった) 緩やかに落ちる効果を長めに
    void releaseLower(ServerPlayer p, boolean stuck) {
        p.setNoGravity(false);
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, stuck ? 20 * 30 : 40, 0, false, false, true));
        Voyages.clearSafe(p);
        if (phase == Phase.LOWERING) {
            Voyages.arrived(p, kind, dest);
        }
    }

    void dismount(ServerPlayer p) {
        if (p.getVehicle() == entity()) {
            Voyages.allowDismount(p, () -> p.stopRiding());
        }
    }

    // ---------------------------------------------------------------- 終わり

    // 取りやめる。乗っている人は鎖で迎えの場所へ降ろす (出発の前だけ。出発のあとは行き先まで運ぶ)
    void cancel(String key, boolean lowerRiders) {
        closeBoardingBar();
        ServerPlayer lead = player(leader);
        if (lead != null) {
            Notice.warn(lead, kind.text(key));
            Voyages.setCooldown(lead, TravelRules.COOLDOWN_CANCELLED);
        }
        if (lowerRiders && (!riders.isEmpty() || !climbs.isEmpty() || !lowers.isEmpty())) {
            // 乗っている人を降ろしてから終える
            for (UUID id : new ArrayList<>(climbs.keySet())) {
                ServerPlayer p = player(id);
                if (p != null) {
                    startLower(p);
                }
            }
            for (UUID id : new ArrayList<>(riders)) {
                ServerPlayer p = player(id);
                if (p != null) {
                    startLower(p);
                } else {
                    riders.remove(id);
                }
            }
            dest = null;
            landing = pickup;
            phase = Phase.BOARDING;
            pendingCancel = true;
            return;
        }
        finish(false);
    }

    // 取りやめの途中 (乗っている人を降ろしている)
    boolean pendingCancel;

    void tickCancelling() {
        totalTicks++;
        ServerLevel level = level();
        tickLowers(level);
        hover(entity());
        titan.setVoyageChain(pickup.getY());
        if (lowers.isEmpty() || totalTicks > 20 * 60 * 10) {
            finish(false);
        }
    }

    void finish(boolean arrived) {
        if (finished) {
            return;
        }
        finished = true;
        closeBoardingBar();
        ServerLevel level = level();
        for (ServerPlayer p : players(riders)) {
            dismount(p);
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60, 0, false, false, true));
            Voyages.clearSafe(p);
        }
        for (UUID id : new ArrayList<>(climbs.keySet())) {
            ServerPlayer p = player(id);
            if (p != null) {
                p.setNoGravity(false);
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 30, 0, false, false, true));
                Voyages.clearSafe(p);
            }
        }
        for (UUID id : new ArrayList<>(lowers.keySet())) {
            ServerPlayer p = player(id);
            if (p != null) {
                p.setNoGravity(false);
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 30, 0, false, false, true));
                Voyages.clearSafe(p);
            }
        }
        riders.clear();
        climbs.clear();
        lowers.clear();
        if (veiled) {
            veiled = false;
        }
        releaseTicket(level);
        if (!entity().isRemoved()) {
            titan.endVoyage(arrived ? TravelRules.REST_AFTER : 0);
        }
        if (arrived && dest != null) {
            SkyAbyss.LOGGER.info("Titan voyage: {} arrived {} ticks ({} s) after the destination was chosen (estimate {} s)",
                    kind.id(), totalTicks - chosenAt, (totalTicks - chosenAt) / 20, estimateAtChoose);
        }
        if (arrived) {
            ServerPlayer lead = player(leader);
            if (lead != null) {
                Voyages.setCooldown(lead, TravelRules.COOLDOWN);
            } else {
                Voyages.setCooldownOffline(level.getServer(), leader, TravelRules.COOLDOWN);
            }
        }
    }

    // 巨神が消えた (コマンドで呼び直された等)。乗っている人は緩やかに落とす
    void lost() {
        for (UUID id : involvedIds()) {
            ServerPlayer p = player(id);
            if (p != null) {
                PacketDistributor.sendToPlayer(p, new TravelNetwork.Veil(false, kind.style().ordinal()));
                Notice.warn(p, kind.text("message.skyabyss.travel.lost"));
            }
        }
        finish(false);
    }

    List<UUID> involvedIds() {
        List<UUID> ids = new ArrayList<>(riders);
        ids.addAll(climbs.keySet());
        ids.addAll(lowers.keySet());
        return ids;
    }

    // 乗っている人が降りずに鞍から外れた (死亡・テレポート・ログアウト)
    void tickRiders(ServerLevel level) {
        Entity whale = entity();
        for (UUID id : new ArrayList<>(riders)) {
            ServerPlayer p = player(id);
            if (p == null) {
                riders.remove(id);
                continue;
            }
            if (p.getVehicle() != whale) {
                riders.remove(id);
                if (p.isAlive() && p.level() == level && !p.onGround()) {
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60, 0, false, false, true));
                }
                Voyages.clearSafe(p);
                if (veiled) {
                    PacketDistributor.sendToPlayer(p, new TravelNetwork.Veil(false, kind.style().ordinal()));
                }
                continue;
            }
            p.fallDistance = 0;
        }
        if (phase != Phase.WAITING && phase != Phase.COMING && phase != Phase.LOWERING && phase != Phase.BOARDING && riders.isEmpty() && !test) {
            // 旅の途中で誰もいなくなった
            finish(false);
        }
    }

    // 呼んだ人がいなくなったら、乗っている人の一人に行き先を任せる
    boolean passLeadership() {
        for (ServerPlayer p : players(riders)) {
            leader = p.getUUID();
            leaderName = p.getGameProfile().getName();
            Notice.narrate(p, "message.skyabyss.travel.new_leader");
            if (dest == null) {
                Voyages.sendChart(p, this);
            }
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 飛び方

    // 迎え・降ろす場所 (鎖の垂れ口の真下が spot) の上空へ寄っていく。着いたら true
    // 寄っていくときの、着いたときの向き。段階の初めに一度だけ決める (今の向きで目標を回し直すと、
    // 目標が巨神と一緒に回ってしまい、いつまでも円を描いて着かない。2026-10-16 の試遊で見つかった)
    @Nullable
    Float hoverYaw;

    // 止まる高さ: 鎖の下の地面の 30 上。止まる位置のまわりに丘があれば、その上 (体が丘に埋まると、
    // 光の届かない所にいることになって巨神が真っ黒に描かれ、頭の乗り場も土に埋まるため)
    double hoverHeightCache = Double.NaN;

    double hoverHeight(ServerLevel level, Entity whale, BlockPos spot) {
        if (Double.isNaN(hoverHeightCache)) {
            Vec3 point = Vec3.atBottomCenterOf(spot);
            Vec3 center = TitanKind.centerForChainAt(titan, point, hoverYaw(whale, point), 0);
            // 体の中ほど (中心) と頭 (鎖の垂れ口の真下) のまわり
            double hills = Math.max(surfaceMax(level, center), surfaceMax(level, point));
            hoverHeightCache = Math.min(level.getMaxBuildHeight() - 50, Math.max(spot.getY() + TravelRules.HOVER_CLEARANCE, hills + 8));
        }
        return hoverHeightCache;
    }

    float hoverYaw(Entity whale, Vec3 point) {
        if (hoverYaw == null) {
            hoverYaw = yawTowards(whale.position(), point);
        }
        return hoverYaw;
    }

    boolean flyToHover(ServerLevel level, Entity whale, BlockPos spot, double hoverY) {
        Vec3 point = Vec3.atBottomCenterOf(spot);
        float finalYaw = hoverYaw(whale, point);
        Vec3 center = TitanKind.centerForChainAt(titan, point, finalYaw, hoverY);
        double distance = horizontal(whale.position(), center);
        float yaw = whale.getYRot();
        if (distance > 30) {
            // 遠いうちは、止まる位置 (決めた向きで垂れ口が目標の真上に来る所) へ向き直りながら進む
            yaw = Mth.approachDegrees(yaw, yawTowards(whale.position(), center), TravelRules.TURN_RATE * 1.5f);
        } else {
            // 近くでは、決めた向きへ合わせながらまっすぐ寄せる
            yaw = Mth.approachDegrees(yaw, finalYaw, TravelRules.TURN_RATE * 2f);
            whale.setYRot(yaw);
        }
        double wantSpeed = Mth.clamp(distance * 0.03, 0.06, TravelRules.TRAVEL_SPEED);
        speed = speed < wantSpeed ? Math.min(wantSpeed, speed + 0.02) : wantSpeed;
        double targetY = distance > 120 ? Math.max(hoverY, Math.min(whale.getY(), travelAltitude(level, whale))) : hoverY;
        double vy = Mth.clamp((targetY - whale.getY()) * 0.04, -0.4, 0.3);
        if (distance > 30) {
            move(whale, yaw, speed, vy);
        } else {
            // 近くでは向きを止め、まっすぐ寄せる
            Vec3 d = new Vec3(center.x - whale.getX(), 0, center.z - whale.getZ());
            Vec3 h = d.lengthSqr() > 1e-6 ? d.normalize().scale(Math.min(speed, d.length())) : Vec3.ZERO;
            Vec3 motion = new Vec3(h.x, vy, h.z);
            whale.setDeltaMovement(motion);
            whale.move(MoverType.SELF, motion);
        }
        return distance < 1.0 && Math.abs(whale.getY() - hoverY) < 1.0 && Math.abs(Mth.degreesDifference(yaw, finalYaw)) < 2f;
    }

    void hover(Entity whale) {
        whale.setDeltaMovement(Vec3.ZERO);
    }

    static void move(Entity whale, float yaw, double speed, double vy) {
        whale.setYRot(yaw);
        double vx = -Mth.sin(yaw * Mth.DEG_TO_RAD) * speed;
        double vz = Mth.cos(yaw * Mth.DEG_TO_RAD) * speed;
        Vec3 motion = new Vec3(vx, vy, vz);
        whale.setDeltaMovement(motion);
        whale.move(MoverType.SELF, motion);
    }

    // 雲の上の高さ。下の地形が高ければ、その上
    double travelAltitude(ServerLevel level, Entity whale) {
        double max = surfaceMax(level, whale.position());
        Vec3 ahead = whale.position().add(whale.getLookAngle().multiply(1, 0, 1).normalize().scale(64));
        max = Math.max(max, surfaceMax(level, ahead));
        return Math.min(level.getMaxBuildHeight() - 50, Math.max(TravelRules.TRAVEL_Y, max + TravelRules.TERRAIN_CLEARANCE));
    }

    // 読み込まれているチャンクだけで、まわりの一番高い地表を見る
    static double surfaceMax(ServerLevel level, Vec3 at) {
        double max = level.getSeaLevel();
        for (int i = -1; i <= 1; i++) {
            for (int k = -1; k <= 1; k++) {
                int x = Mth.floor(at.x) + i * 32;
                int z = Mth.floor(at.z) + k * 32;
                if (level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    max = Math.max(max, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
                }
            }
        }
        return max;
    }

    static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90f;
    }

    static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    static Vec3 towards(Vec3 from, Vec3 to, double speed) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        return len < 1e-6 ? Vec3.ZERO : d.scale(Math.min(speed, len) / len);
    }

    // 残りの時間の見積もり (tick)
    // 残りの時間の見積もり (tick)。行き先を決めたときの見積もりから、たった時間を引く (数字が戻らないように)。
    // 着く手前で見積もりを使い切ったら、降りていく分だけを出す
    int remainingTicks(Entity whale) {
        int left = estimateAtChoose * 20 - (totalTicks - chosenAt);
        int floor = phase == Phase.APPROACH ? 120 : 60;
        return Math.max(floor, left);
    }

    // 行き先を決めたとき (totalTicks) と、そのときの見積もり (秒)
    int chosenAt;
    int estimateAtChoose;

    // 行き先を受け付ける。断るなら理由の翻訳キーを返す (受け付けたら null)。潮殻は近くに海のない行き先を断る
    @Nullable
    String accept(TravelNetwork.Dest d) {
        return null;
    }

    // 見積もりの秒数 (巨神の型ごと)
    int estimate(double distance) {
        return TravelRules.estimateSeconds(distance);
    }

    void chosen(TravelNetwork.Dest d) {
        dest = d;
        chosenAt = totalTicks;
        estimateAtChoose = estimate(Math.hypot(d.x() + 0.5 - pickup.getX(), d.z() + 0.5 - pickup.getZ()));
        phase = Phase.BOARDING;
        phaseTicks = 0;
        boardingLimit = test ? TravelRules.BOARDING_ESTIMATE : TravelRules.BOARDING_COUNTDOWN;
    }

    BlockPos destPos() {
        return dest == null ? pickup : new BlockPos(dest.x(), dest.y(), dest.z());
    }

    // 行き先の、立てる位置 (碑なら碑の上。着く前の目安とログアウト時の降ろし先)
    BlockPos destFeet() {
        if (landing != null) {
            return landing;
        }
        return dest == null ? pickup : (dest.kind() == 1 ? destPos().above() : destPos());
    }

    // ---------------------------------------------------------------- 読み込み

    void holdTicket(ServerLevel level, ChunkPos pos) {
        long now = level.getGameTime();
        if (pos.equals(ticketPos) && now - ticketRefreshed < 100) {
            return;
        }
        if (ticketPos != null && !pos.equals(ticketPos)) {
            level.getChunkSource().removeRegionTicket(TICKET, ticketPos, TICKET_RADIUS, ticketPos);
        }
        level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_RADIUS, pos);
        ticketPos = pos;
        ticketRefreshed = now;
    }

    void releaseTicket(ServerLevel level) {
        if (ticketPos != null) {
            level.getChunkSource().removeRegionTicket(TICKET, ticketPos, TICKET_RADIUS, ticketPos);
            ticketPos = null;
        }
        if (moveTicketPos != null) {
            level.getChunkSource().removeRegionTicket(TICKET, moveTicketPos, MOVE_RADIUS, moveTicketPos);
            moveTicketPos = null;
        }
    }

    // 巨神のまわり (半径 3 チャンク = 前後 48 ブロック) を読み込んでおく。泳いでいく先が読み込まれていないと、
    // 巨神がチャンクの外へ出て見えなくなり、見守りに見失われるため。移す先 (雲の帳の出口・呼び寄せの入口) にも使う
    private static final int MOVE_RADIUS = 3;
    @Nullable
    private ChunkPos moveTicketPos;
    private long moveTicketRefreshed;

    void holdMoveTicket(ServerLevel level, ChunkPos pos) {
        long now = level.getGameTime();
        if (pos.equals(moveTicketPos) && now - moveTicketRefreshed < 100) {
            return;
        }
        if (moveTicketPos != null && !pos.equals(moveTicketPos)) {
            level.getChunkSource().removeRegionTicket(TICKET, moveTicketPos, MOVE_RADIUS, moveTicketPos);
        }
        level.getChunkSource().addRegionTicket(TICKET, pos, MOVE_RADIUS, pos);
        moveTicketPos = pos;
        moveTicketRefreshed = now;
    }

    static boolean loaded(ServerLevel level, Vec3 at) {
        return level.getChunkSource().hasChunk(Mth.floor(at.x) >> 4, Mth.floor(at.z) >> 4);
    }

    // ---------------------------------------------------------------- 小物

    // 背中 (専用のディメンション) にいる人へ、足もとの巨神が泳ぎだした・着いたことを知らせる
    void tellAboard(ServerLevel level, String key) {
        ServerLevel back = level.getServer().getLevel(TitanTravel.TITAN_BACK);
        if (back != null && kind == TitanKind.STONE_WHALE) {
            for (ServerPlayer p : Players.snapshot(back.players())) {
                Notice.narrate(p, key);
            }
        }
    }

    @Nullable
    ServerPlayer player(UUID id) {
        return level().getServer().getPlayerList().getPlayer(id);
    }

    List<ServerPlayer> players(Set<UUID> ids) {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = player(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    static List<ServerPlayer> nearbyPlayers(ServerLevel level, Vec3 at, double radius) {
        return level.players().stream().filter(p -> p.distanceToSqr(at) < radius * radius).toList();
    }

    static void showTitle(ServerPlayer player, Component title, Component subtitle) {
        Notice.place(player, title.copy().withStyle(net.minecraft.ChatFormatting.GOLD), subtitle.copy().withStyle(s -> s.withColor(0x7FD6D0).withItalic(true)));
    }
}
