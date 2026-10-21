package jp.revati.skyabyss.titan.young;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.titan.travel.LandingFinder;
import jp.revati.skyabyss.titan.travel.TitanCalls;
import jp.revati.skyabyss.titan.travel.TravelNetwork;
import jp.revati.skyabyss.titan.travel.TravelSetup;
import jp.revati.skyabyss.titan.travel.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 巨神の子 (docs/design/28) のまとめ役。笛で呼ぶ・乗る・行き先を決める・降りる、と、それを支える見張り (降りられない・落ちない・ログアウト)。
// 子は呼んだ人 (持ち主) のもの。同じパーティの人は、持ち主の子に乗れる (最大 6 人)。成体の巨神は動かさない。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class YoungCarriers {
    // 持ち主 → 今の子 (一人に一体)
    private static final Map<UUID, YoungEntity> BY_OWNER = new HashMap<>();
    // 自己テスト用の偽の人 (PlayerList にいない)。本番では空
    private static final Map<UUID, ServerPlayer> TEST_PLAYERS = new HashMap<>();

    public static void testPlayer(ServerPlayer p, boolean add) {
        if (add) {
            TEST_PLAYERS.put(p.getUUID(), p);
        } else {
            TEST_PLAYERS.remove(p.getUUID());
        }
    }

    // 今いる人 (本物 + 自己テストの偽の人)
    static List<ServerPlayer> online(net.minecraft.server.MinecraftServer server) {
        List<ServerPlayer> list = new java.util.ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer p : TEST_PLAYERS.values()) {
            if (!list.contains(p)) {
                list.add(p);
            }
        }
        return list;
    }

    @Nullable
    static ServerPlayer playerOf(net.minecraft.server.MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p != null ? p : TEST_PLAYERS.get(id);
    }
    // いま降ろしてよい人 (降りる操作を止めない)
    private static final Set<UUID> RELEASING = new HashSet<>();
    // 呼べる距離: 子に乗りに行ける距離 (持ち主のパーティの人が、笛で持ち主の子へ乗るとき)
    public static final double JOIN_RANGE = 48;
    // 右クリックで乗れる距離
    public static final double BOARD_RANGE = 12;
    private YoungCarriers() {
    }

    // ---------------------------------------------------------------- 見張りの記録

    @Nullable
    public static YoungEntity of(UUID owner) {
        YoungEntity e = BY_OWNER.get(owner);
        return e != null && !e.isRemoved() ? e : null;
    }

    // この人は子に乗っている最中か (体に入れない仕組みの除外・自己テスト)
    public static boolean riding(UUID player) {
        for (YoungEntity e : BY_OWNER.values()) {
            for (Entity p : e.getPassengers()) {
                if (p.getUUID().equals(player)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void forget(YoungEntity e) {
        UUID owner = e.owner();
        if (owner != null && BY_OWNER.get(owner) == e) {
            BY_OWNER.remove(owner);
        }
    }

    static void allowDismount(ServerPlayer player, Runnable action) {
        RELEASING.add(player.getUUID());
        try {
            action.run();
        } finally {
            RELEASING.remove(player.getUUID());
        }
    }

    static SoundEvent callSound(YoungKind kind) {
        return switch (kind) {
            case TIDE -> TravelSetup.TIDE_CALL_SOUND.get();
            case STORM -> jp.revati.skyabyss.titan.storm.StormSetup.WING_CALL.get();
            case EMBER -> jp.revati.skyabyss.titan.ember.EmberTitanSetup.RUMBLE.get();
            default -> TravelSetup.CALL_SOUND.get();
        };
    }

    // ---------------------------------------------------------------- 笛

    // 笛を吹いた。受け付けたら true (断ったときは理由を見せて false)。
    // 笛はいつでも子を呼ぶ (2026-10-21 の試遊の声)。待ち・戦闘中・水の中・眠っている巨神・ほかの乗り物では断らない。
    // 断るのは、どうしても呼べないときだけ: オーバーワールドの外 (背の中は ReturnBeacons が先に受ける)、場面の最中、
    // 出会っていない巨神、仲間を乗せて運んでいる自分の子がいるとき、潮殻の稚貝で近くに水がまったくないとき
    public static boolean whistle(ServerPlayer player, String titan) {
        YoungKind kind = YoungKind.ofTitan(titan);
        if (kind == null) {
            return false;
        }
        Component name = Progression.titanName(titan);
        if (player.level().dimension() != Level.OVERWORLD) {
            Notice.bar(player, ModText.deny("message.skyabyss.titan_call.wrong_dimension", name));
            return false;
        }
        if (jp.revati.skyabyss.cinematic.Cinematics.active(player)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        // もう子がいる: 乗っていれば海図を開き直す。近くなら乗る。遠くて誰も乗っていなければ、足もとへ呼び直す
        YoungEntity mine = of(player.getUUID());
        if (mine != null) {
            YoungTrip trip = mine.trip();
            if (player.getVehicle() == mine) {
                if (trip != null && trip.phase() == YoungTrip.Phase.WAIT) {
                    sendChart(mine, player);
                }
                return true;
            }
            if (trip != null && mine.level() == level && mine.distanceTo(player) <= JOIN_RANGE + bondRange(player.server, player.getUUID()) && boardable(mine)) {
                tryBoard(mine, player);
                return true;
            }
            if (mine.riderCount() > 0) {
                Notice.bar(player, ModText.hint("message.skyabyss.young.have_one"));
                return false;
            }
            mine.discard();
        }
        // 仲間の子が近くにいる: 乗る
        YoungEntity mate = mateCarrier(player);
        if (mate != null) {
            if (player.isPassenger()) {
                player.stopRiding();
            }
            tryBoard(mate, player);
            return true;
        }
        if (!TitanCalls.met(player, titan)) {
            Notice.bar(player, ModText.deny("message.skyabyss.titan_call.not_met", name));
            return false;
        }
        Vec3 spot = findSpot(level, player, kind);
        if (spot == null) {
            Notice.bar(player, ModText.deny(kind == YoungKind.TIDE ? "message.skyabyss.young.refuse.no_sea" : "message.skyabyss.young.refuse.no_room", name));
            return false;
        }
        // ほかの乗り物 (馬・舟など) からは降ろしてから呼ぶ
        if (player.isPassenger()) {
            player.stopRiding();
        }
        Voyages.state(player).readyAt = 0;
        YoungEntity young = spawn(level, kind, player, spot);
        level.playSound(null, spot.x, spot.y, spot.z, callSound(kind), SoundSource.PLAYERS, 3f, 1.4f);
        Notice.narrate(player, kind.text("called"));
        SkyAbyss.LOGGER.info("{} called a {} at {}", player.getGameProfile().getName(), kind.model, BlockPos.containing(spot));
        return young != null;
    }

    // 自己テスト・コマンド向け: 子を spot に出す (呼ぶ条件は見ない)
    @Nullable
    public static YoungEntity spawn(ServerLevel level, YoungKind kind, ServerPlayer owner, Vec3 spot) {
        YoungEntity young = YoungSetup.type(kind).create(level);
        if (young == null) {
            return null;
        }
        young.moveTo(spot.x, spot.y, spot.z, owner.getYRot(), 0f);
        young.setOwner(owner);
        level.addFreshEntity(young);
        BY_OWNER.put(owner.getUUID(), young);
        return young;
    }

    // 持ち主の転生の絆による、呼べる・乗れる距離の足し (docs/design/31 の B14)
    private static double bondRange(net.minecraft.server.MinecraftServer server, UUID owner) {
        return jp.revati.skyabyss.rebirth.Rebirth.youngRange(server, owner);
    }

    private static boolean boardable(YoungEntity e) {
        int p = e.phase();
        return p == YoungEntity.APPEAR || p == YoungEntity.WAIT || p == YoungEntity.COUNTDOWN;
    }

    // 同じパーティの人の子で、近くにいて乗れるもの
    @Nullable
    private static YoungEntity mateCarrier(ServerPlayer player) {
        YoungEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (YoungEntity e : BY_OWNER.values()) {
            YoungTrip trip = e.trip();
            if (e.isRemoved() || trip == null || e.level() != player.level() || !boardable(e) || !trip.mayRide(player)) {
                continue;
            }
            double d = e.distanceToSqr(player);
            double range = JOIN_RANGE + bondRange(player.server, e.owner());
            if (d <= range * range && d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    // 子が現れる所: 人の前 (向いている方) の、体が入る開けた地面。潮殻の稚貝は水の上 (24 ブロック以内の水際、なければ 64 まで)。
    // 陸の子は、開けた地面がなければ段差の大きい所・人の足もと・頭の上の地表の順にゆるめる (笛はいつでも応える)
    @Nullable
    static Vec3 findSpot(ServerLevel level, ServerPlayer player, YoungKind kind) {
        if (kind.gait == YoungKind.Gait.SEA) {
            BlockPos water = YoungReach.waterNear(level, player.blockPosition(), 24);
            if (water == null) {
                water = YoungReach.waterNear(level, player.blockPosition(), 64);
            }
            if (water == null) {
                return null;
            }
            double h = YoungMotion.height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, water.getX() + 0.5, water.getZ() + 0.5);
            return new Vec3(water.getX() + 0.5, Double.isNaN(h) ? level.getSeaLevel() - 0.35 : h - 0.35, water.getZ() + 0.5);
        }
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        for (int ring = 0; ring < 8; ring++) {
            double r = 4.5 + ring * 1.5;
            for (int k = 0; k < 12; k++) {
                double a = (k % 2 == 0 ? 1 : -1) * (k / 2) * 0.55;
                double dx = fx * Math.cos(a) - fz * Math.sin(a);
                double dz = fz * Math.cos(a) + fx * Math.sin(a);
                double x = player.getX() + dx * r;
                double z = player.getZ() + dz * r;
                double h = YoungMotion.height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Double.isNaN(h) || Math.abs(h - player.getY()) > 3) {
                    continue;
                }
                if (level.getFluidState(BlockPos.containing(x, h - 1, z)).isSource()) {
                    continue;
                }
                AABB box = new AABB(x - 2.3, h + 0.05, z - 2.3, x + 2.3, h + 3.4, z + 2.3);
                if (level.noCollision(box)) {
                    return new Vec3(x, h, z);
                }
            }
        }
        return looseSpot(level, player, fx, fz);
    }

    // ゆるめた子の出る所: 段差 12 まで・水の上も可 → 人の足もと (狭くても) → 頭の上の地表
    static Vec3 looseSpot(ServerLevel level, ServerPlayer player, double fx, double fz) {
        for (int ring = 0; ring < 6; ring++) {
            double r = 3.5 + ring * 2.0;
            for (int k = 0; k < 8; k++) {
                double a = (k % 2 == 0 ? 1 : -1) * (k / 2) * 0.8;
                double dx = fx * Math.cos(a) - fz * Math.sin(a);
                double dz = fz * Math.cos(a) + fx * Math.sin(a);
                double x = player.getX() + dx * r;
                double z = player.getZ() + dz * r;
                double h = YoungMotion.height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Double.isNaN(h) || Math.abs(h - player.getY()) > 12) {
                    continue;
                }
                if (level.noCollision(new AABB(x - 1.2, h + 0.05, z - 1.2, x + 1.2, h + 2.4, z + 1.2))) {
                    return new Vec3(x, h, z);
                }
            }
        }
        if (level.noCollision(new AABB(player.getX() - 0.9, player.getY() + 0.05, player.getZ() - 0.9, player.getX() + 0.9, player.getY() + 2.0, player.getZ() + 0.9))) {
            return player.position();
        }
        double h = YoungMotion.height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getX(), player.getZ());
        return new Vec3(player.getX(), Double.isNaN(h) ? player.getY() : h, player.getZ());
    }

    // ---------------------------------------------------------------- 乗る

    // 子に乗る (右クリック・笛)。持ち主と同じパーティの人だけ
    static void tryBoard(YoungEntity young, ServerPlayer player) {
        YoungTrip trip = young.trip();
        if (trip == null || young.isRemoved()) {
            return;
        }
        if (!trip.mayRide(player)) {
            Notice.bar(player, ModText.deny("message.skyabyss.young.not_yours", trip.ownerName));
            return;
        }
        if (player.getVehicle() == young) {
            return;
        }
        if (!boardable(young)) {
            Notice.bar(player, ModText.hint("message.skyabyss.young.too_late"));
            return;
        }
        if (player.isPassenger() || player.distanceTo(young) > BOARD_RANGE + 36 + bondRange(player.server, young.owner())) {
            return;
        }
        if (young.riderCount() >= YoungKind.SEATS) {
            Notice.bar(player, ModText.deny("message.skyabyss.young.full"));
            return;
        }
        if (!young.assignSeat(player) || !player.startRiding(young, true)) {
            young.sweepSeats();
            return;
        }
        player.fallDistance = 0;
        Voyages.markSafe(player, player.blockPosition());
        if (player.getUUID().equals(trip.owner)) {
            Notice.narrate(player, young.kind.text("seated_leader"));
            if (trip.phase() == YoungTrip.Phase.WAIT || trip.phase() == YoungTrip.Phase.APPEAR) {
                sendChart(young, player);
            }
        } else {
            Notice.narrate(player, young.kind.text("seated"), trip.ownerName);
            ServerPlayer lead = trip.player(trip.owner);
            if (lead != null) {
                Notice.hint(lead, "message.skyabyss.young.mate_boarded", player.getDisplayName(), young.riderCount());
            }
        }
    }

    // ---------------------------------------------------------------- 海図

    static void sendChart(YoungEntity young, ServerPlayer player) {
        YoungTrip trip = young.trip();
        if (trip == null) {
            return;
        }
        boolean leader = player.getUUID().equals(trip.owner);
        List<TravelNetwork.Dest> dests = YoungReach.list(player, young.kind, young.position());
        // 壊された碑は、この海図では「失われた碑」として灰色で見せ (選べない)、記録からは外す。次に開いたときには出ない
        Voyages.pruneLost(Voyages.state(player), jp.revati.skyabyss.titan.travel.TravelWorldData.get(player.server));
        PacketDistributor.sendToPlayer(player, new TravelNetwork.Chart(young.kind.titan, leader, trip.ownerName, (int) young.getX(), (int) young.getZ(),
                trip.dest() == null ? "" : trip.dest().id(), dests));
    }

    // 決める人が行き先を選んだ
    public static void choose(ServerPlayer player, String id) {
        YoungEntity young = ridden(player);
        if (young == null) {
            return;
        }
        YoungTrip trip = young.trip();
        if (trip == null || !trip.owner.equals(player.getUUID())) {
            if (trip != null) {
                Notice.hint(player, "message.skyabyss.young.not_leader", trip.ownerName);
            }
            return;
        }
        if (trip.phase() != YoungTrip.Phase.WAIT) {
            return;
        }
        TravelNetwork.Dest chosen = null;
        for (TravelNetwork.Dest d : YoungReach.list(player, young.kind, young.position())) {
            if (d.id().equals(id)) {
                chosen = d;
            }
        }
        if (chosen == null || (!chosen.valid() && chosen.lock().isEmpty())) {
            Notice.bar(player, ModText.deny("message.skyabyss.travel.dest_lost"));
            sendChart(young, player);
            return;
        }
        String refused = trip.choose(chosen);
        if (refused != null) {
            Notice.bar(player, ModText.deny(refused));
            sendChart(young, player);
        }
    }

    // 出発の前に降りる (海図の「降りる」)
    public static void disembark(ServerPlayer player) {
        YoungEntity young = ridden(player);
        if (young == null) {
            return;
        }
        int p = young.phase();
        if (p == YoungEntity.CARRY || p == YoungEntity.LAND) {
            Notice.bar(player, ModText.hint("message.skyabyss.young.no_dismount"));
            return;
        }
        allowDismount(player, player::stopRiding);
    }

    // 碑を忘れる
    public static void forgetWaymark(ServerPlayer player, String id) {
        if (!id.startsWith("w")) {
            return;
        }
        try {
            Voyages.state(player).forgetWaymark(BlockPos.of(Long.parseLong(id.substring(1))));
        } catch (NumberFormatException ignored) {
            return;
        }
        YoungEntity young = ridden(player);
        if (young != null && young.trip() != null && young.trip().phase() == YoungTrip.Phase.WAIT) {
            sendChart(young, player);
        }
    }

    @Nullable
    private static YoungEntity ridden(ServerPlayer player) {
        return player.getVehicle() instanceof YoungEntity y && !y.isRemoved() ? y : null;
    }

    // ---------------------------------------------------------------- 見張り

    // 行き先に着くまでは降りられない (しゃがんでも降りない)。出発の前・着いたあとは降りられる
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onDismount(EntityMountEvent event) {
        if (!event.isDismounting() || !(event.getEntityMounting() instanceof ServerPlayer player) || !(event.getEntityBeingMounted() instanceof YoungEntity young)) {
            return;
        }
        if (RELEASING.contains(player.getUUID()) || !player.isAlive() || player.isRemoved() || young.isRemoved()) {
            return;
        }
        int phase = young.phase();
        if (phase != YoungEntity.CARRY && phase != YoungEntity.LAND) {
            return;
        }
        event.setCanceled(true);
        if (player.isShiftKeyDown()) {
            Notice.bar(player, ModText.hint("message.skyabyss.young.no_dismount"));
        }
    }

    // 乗っている人は、壁に埋まる・落ちる・ぶつかるで傷つかない
    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.getVehicle() instanceof YoungEntity)) {
            return;
        }
        if (event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypes.FALL) || event.getSource().is(DamageTypes.FLY_INTO_WALL)
                || event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    // ログアウト: 子から降ろし、次のログインで降ろす場所 (印) を残す。ログアウトのイベントは保存より前に来る
    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.getVehicle() instanceof YoungEntity young)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos spot = young.getY() > level.getSeaLevel() + 2 || young.phase() == YoungEntity.CARRY || young.phase() == YoungEntity.LAND
                ? LandingFinder.find(level, BlockPos.containing(young.getX(), level.getSeaLevel(), young.getZ()), 2, 8)
                : player.blockPosition();
        allowDismount(player, player::stopRiding);
        player.setNoGravity(false);
        player.fallDistance = 0;
        Voyages.markSafe(player, spot);
        PacketDistributor.sendToPlayer(player, new TravelNetwork.Veil(false, 0));
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        BY_OWNER.clear();
        RELEASING.clear();
    }

    // ---------------------------------------------------------------- コマンド・自己テスト向け

    public static List<YoungEntity> all() {
        return List.copyOf(BY_OWNER.values());
    }

    // 今の子を全部歌に戻す (乗っている人は地面へ降ろす)。戻した数
    public static int endAll() {
        int n = 0;
        for (YoungEntity e : List.copyOf(BY_OWNER.values())) {
            YoungTrip trip = e.trip();
            if (trip != null && !e.isRemoved()) {
                trip.dissolve("done", trip.riders());
                n++;
            }
        }
        return n;
    }

    // 子が現れる所 (笛と同じ決まり。条件は見ない)
    @Nullable
    public static Vec3 spotFor(ServerPlayer player, YoungKind kind) {
        return findSpot(player.serverLevel(), player, kind);
    }

    // 海図を使わずに行き先を決める (コマンド・自己テスト)。決められなければ理由の鍵
    @Nullable
    public static String chooseDirect(ServerPlayer player, TravelNetwork.Dest d) {
        YoungEntity young = ridden(player);
        if (young == null || young.trip() == null) {
            return "message.skyabyss.young.not_now";
        }
        return young.trip().choose(d);
    }
}
