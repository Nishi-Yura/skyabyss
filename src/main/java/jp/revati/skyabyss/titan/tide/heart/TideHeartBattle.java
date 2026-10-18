package jp.revati.skyabyss.titan.tide.heart;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.GroupBossRooms;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.tide.TideBackBuilder;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 殻の底 (心臓の間) の戦い。岩鯨の心臓の間と同じ決まりを、グループごとの戦い (GroupBossRoom) で動かす:
// 5 秒の集合 → 入口が閉じて心臓が現れる → 全員が倒れるか部屋から消えて 3 秒で放棄 (心臓を消して入口を開ける)。
// 心臓が部屋の外へ出たら中央へ戻す。撃破で入口が開き、中央に光の柱 (立つと島へ戻る。そのグループが倒したときだけ)。
// 心臓の満ち潮は部屋の床に本物の水を置くので、殻の底 (部屋と前室) は占有する部屋 (RoomLocks の tide_heart。一度に一つのグループ)。
// 入口の扉も、戦いのあいだはワールドの既定で閉じる (水を止めるため。ほかのグループはそもそも入れない)。
// 撃破はそのグループの記録 (BackGroupData.Record) に書く。ほかのグループの進みには触れない。
// 撃破のあと、そのグループの誰も潮の底の門より奥にいなくなって 30 秒で、そのグループの試練が次の周へ戻る (TideBackTrials のリセット。08 の 7.1節)。
// 出入りは、潮の広間の潮の底の門の下の螺旋の階段 (本番の島)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideHeartBattle {
    public static final int RESET_AFTER_DEFEAT = 20 * 30;
    public static final String ROOM_ID = "tide_heart";
    private static final GroupBossRooms ROOMS = new GroupBossRooms();
    private static long lastAsleepNotice;

    private TideHeartBattle() {
    }

    // そのグループの戦い
    public static GroupBossRoom room(UUID group) {
        return ROOMS.of(group);
    }

    // 殻の底を持っているグループ (いなければ null)
    @Nullable
    public static UUID owner() {
        return RoomLocks.owner(ROOM_ID);
    }

    // 占有の箱 (戦いの部屋と前室) と、追い出す先 (休み場 heart の小部屋)
    static AABB lockBox() {
        return new AABB(TideHeartArena.CX - TideHeartArena.RADIUS, TideHeartArena.FLOOR - 1, TideHeartArena.CZ - TideHeartArena.RADIUS,
                TideHeartArena.CX + TideHeartArena.RADIUS + 1, TideHeartArena.CEILING, TideHeartArena.CZ + TideHeartArena.ANTE_Z1 + 1);
    }

    static BlockPos eject() {
        return TideBackLayout.RESTS.get(5).spawn();
    }

    static final RoomLocks.Listener LOCK = new RoomLocks.Listener() {
        @Override
        public void acquired(ServerLevel level, UUID group) {
            TideHeartArena.ensureBuilt(level);
            TideHeartArena.drainNow(level);
        }

        @Override
        public void released(ServerLevel level, UUID formerOwner) {
            GroupBossRoom room = ROOMS.peek(formerOwner);
            if (room != null && (room.active() || room.gathering())) {
                room.reset(level, HOST, false);
            }
            HOST.cleanup(level, formerOwner);
            HOST.open(level, formerOwner);
        }
    };

    // 部屋ごとの仕掛け
    static final GroupBossRoom.Host HOST = new GroupBossRoom.Host() {
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return playersIn(level, TideHeartArena.arena());
        }

        @Override
        public void seal(ServerLevel level, UUID group) {
            TideHeartArena.seal(level);
            for (ServerPlayer p : Groups.online(level, group)) {
                if (p.distanceToSqr(TideHeartArena.heartHome()) < 48 * 48) {
                    p.playNotifySound(SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
                }
            }
        }

        @Override
        public void open(ServerLevel level, UUID group) {
            GroupGates.release(level, group, TideHeartArena.gate());
            TideHeartArena.open(level);
        }

        @Override
        public void pullIn(ServerLevel level, UUID group) {
            // 前室と戸口にいる、そのグループの人を、入口の内側 2 マスへ
            AABB ante = TideHeartArena.ante().inflate(0, 1, 0).expandTowards(0, 0, -1);
            for (ServerPlayer player : Groups.inBox(level, group, ante)) {
                moveInside(level, player);
            }
        }

        @Override
        public void letBack(ServerLevel level, UUID group, Set<UUID> participants) {
            // 戸口 (封印の手前 1 マス) に触れた参加者を中へ通す
            AABB door = new AABB(TideHeartArena.CX - 1, TideHeartArena.FLOOR, TideHeartArena.CZ + TideHeartArena.GATE_Z + 1,
                    TideHeartArena.CX + 2, TideHeartArena.FLOOR + 3, TideHeartArena.CZ + TideHeartArena.GATE_Z + 2);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, door,
                    p -> p.isAlive() && !p.isSpectator() && participants.contains(p.getUUID()) && Groups.isMember(p, group))) {
                moveInside(level, player);
                Notice.bar(player, ModText.battle("message.skyabyss.gate_rejoin"));
            }
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers) {
            TideHeart heart = TideHeartSetup.TIDE_HEART.get().create(level);
            if (heart == null) {
                return null;
            }
            Vec3 home = TideHeartArena.heartHome();
            // 入口 (南) を向いて現れる
            heart.moveTo(home.x, home.y, home.z, 0f, 0f);
            heart.addTag(TitanBackTrials.HEART_TAG);
            heart.setGroupId(group);
            // 挑む人数 (パーティは 6 人まで。倍率の式に上限はない)
            heart.bindArena(TideHeartArena.arena(), challengers.size());
            level.addFreshEntity(heart);
            for (ServerPlayer p : Groups.online(level, group)) {
                if (p.distanceToSqr(home) < 48 * 48) {
                    p.playNotifySound(TideHeartSetup.ROAR.get(), SoundSource.HOSTILE, 2.5f, 1.1f);
                }
            }
            level.sendParticles(ParticleTypes.SPLASH, home.x, home.y + 2, home.z, 120, 2.0, 1.5, 2.0, 0.3);
            broadcast(level, group, ModText.battle("message.skyabyss.tide_heart.begin"));
            SkyAbyss.LOGGER.info("The heart of the tide shell woke with {} challenger(s)", challengers.size());
            return heart;
        }

        @Override
        public Vec3 home() {
            return TideHeartArena.heartHome();
        }

        @Override
        public double leash() {
            return 6;
        }

        @Override
        public void broadcast(ServerLevel level, UUID group, Component message) {
            TideHeartBattle.broadcast(level, group, message);
        }

        @Override
        public void countdown(ServerLevel level, UUID group, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            for (ServerPlayer player : Groups.inBox(level, group, TideHeartArena.arena().inflate(4, 2, 10))) {
                Notice.bar(player, text);
            }
        }

        @Override
        public void cleanup(ServerLevel level, UUID group) {
            TideHeartArena.drainNow(level);
            for (Entity e : level.getEntitiesOfClass(Entity.class, TideHeartArena.arena().inflate(4),
                    e -> (e instanceof TideBubble || e instanceof TidePearl || e.getTags().contains(TitanBackTrials.HEART_TAG))
                            && (!(e instanceof GroupOwned o) || o.groupId() == null || group.equals(o.groupId())))) {
                e.discard();
            }
        }

        @Override
        public boolean asleep(ServerLevel level) {
            return level.getDifficulty() == Difficulty.PEACEFUL;
        }

        @Override
        public boolean defeated(ServerLevel level, UUID group) {
            var record = jp.revati.skyabyss.titan.group.BackGroupData.get(level).peek(group);
            return record != null && record.isHeartDefeated();
        }
    };

    // 心臓の間の部屋の仕組み (自己テストが、倒したあとに湧き直さないことを確かめる)
    public static GroupBossRoom.Host host() {
        return HOST;
    }

    static List<ServerPlayer> playersIn(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator());
    }

    // 部屋のまわりにいる、そのグループの人へ
    static void broadcast(ServerLevel level, UUID group, Component message) {
        for (ServerPlayer player : Groups.inBox(level, group, TideHeartArena.arena().inflate(4, 4, 12))) {
            Notice.send(player, message);
        }
    }

    private static void moveInside(ServerLevel level, ServerPlayer player) {
        player.fallDistance = 0;
        double x = Math.max(TideHeartArena.CX - 0.5, Math.min(TideHeartArena.CX + 1.5, player.getX()));
        player.teleportTo(level, x, TideHeartArena.FLOOR, TideHeartArena.CZ + TideHeartArena.GATE_Z - 2 + 0.5, 180f, 0f);
    }

    static boolean defeated(ServerLevel level, UUID group) {
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        return r != null && r.isHeartDefeated();
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        if (level.players().isEmpty()) {
            return;
        }
        TideHeartArena.tickFlood(level);
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        if (level.getGameTime() % 100 == 0 && !ROOMS.anyActive()) {
            TideHeartArena.ensureBuilt(level);
        }
        UUID owner = RoomLocks.update(level, ROOM_ID, lockBox(), eject(), RoomLocks.DEFAULT_GRACE, LOCK);
        // 持ち主でないグループの戦い (持ち主が変わった直後) は止める
        for (GroupBossRoom room : ROOMS.all()) {
            if (!room.group.equals(owner) && (room.active() || room.gathering())) {
                room.reset(level, HOST, true);
            }
        }
        tickPillar(level);
        if (owner == null) {
            return;
        }
        if (defeated(level, owner)) {
            return;
        }
        if (HOST.asleep(level) && !Groups.inBox(level, owner, TideHeartArena.arena()).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            broadcast(level, owner, ModText.deny("message.skyabyss.tide_heart.asleep"));
        }
        ROOMS.of(owner).tick(level, HOST);
    }

    // 潮の底へ降りる (前室へ)
    public static void descend(ServerLevel level, ServerPlayer player) {
        TideHeartArena.ensureBuilt(level);
        Vec3 at = TideHeartArena.anteSpawn();
        player.fallDistance = 0;
        player.teleportTo(level, at.x, at.y, at.z, 180f, 0f);
        level.playSound(null, at.x, at.y, at.z, jp.revati.skyabyss.titan.tide.TideSetup.GATE.get(), SoundSource.PLAYERS, 1.2f, 0.8f);
        Notice.narrate(player, "message.skyabyss.tide_heart.descend");
        Notice.bar(player, ModText.hint("message.skyabyss.tide_heart.exit_hint"));
    }

    // 島の到着地点へ戻る
    public static void ascend(ServerLevel level, ServerPlayer player) {
        BlockPos a = TideBackBuilder.arrival();
        player.fallDistance = 0;
        player.teleportTo(level, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 180f, 0f);
        level.playSound(null, a, jp.revati.skyabyss.titan.tide.TideSetup.GATE.get(), SoundSource.PLAYERS, 1.2f, 1.2f);
    }

    // 撃破のあと: 中央の光の柱。立った人のグループが心臓を倒していれば島へ戻す (柱の粒は、持ち主のグループが倒していれば出す)
    private static void tickPillar(ServerLevel level) {
        Vec3 c = TideHeartArena.heartHome();
        UUID owner = owner();
        if (owner != null && defeated(level, owner)) {
            level.sendParticles(ParticleTypes.END_ROD, c.x, TideHeartArena.FLOOR + (level.getGameTime() % 12), c.z, 3, 0.25, 0.2, 0.25, 0.02);
            level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, TideHeartArena.FLOOR + 1, c.z, 2, 0.4, 0.3, 0.4, 0.02);
        }
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (Math.abs(player.getX() - c.x) < 1.2 && Math.abs(player.getZ() - c.z) < 1.2
                    && player.getY() >= TideHeartArena.FLOOR - 0.5 && player.getY() < TideHeartArena.CEILING
                    && defeated(level, Groups.keyOf(player))) {
                ascend(level, player);
                Notice.narrate(player, "message.skyabyss.tide_heart.pillar");
            }
        }
    }

    // そのグループの戦いを止める (グループのリセットから。記録を戻すのは TideBackTrials.resetGroup)。殻の底を持っていれば手放す
    public static void reawaken(ServerLevel level, UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        if (room != null && (room.active() || room.gathering())) {
            room.reset(level, HOST, false);
        }
        if (group.equals(owner())) {
            RoomLocks.release(ROOM_ID);
            LOCK.released(level, group);
        }
        SkyAbyss.LOGGER.info("The heart of the tide shell sleeps again for group {}", group);
    }

    // すべての戦いを止め、殻の底を手放す (島に誰もいなくなって久しいとき)
    public static void releaseAll(ServerLevel level) {
        ROOMS.resetAll(level, HOST, false);
        UUID owner = owner();
        if (owner != null) {
            RoomLocks.release(ROOM_ID);
            LOCK.released(level, owner);
        }
    }

    // 殻の底をそのグループに持たせる (自己テストとコマンド。前の持ち主の戦いは止める)
    public static void claim(ServerLevel level, UUID group) {
        UUID before = RoomLocks.claim(level, ROOM_ID, group);
        if (before != null && !before.equals(group)) {
            LOCK.released(level, before);
        }
        LOCK.acquired(level, group);
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。部屋の中の、そのグループの人を挑む人とする。
    // そのグループがもう倒していれば、そのグループの試練を戻してから
    public static boolean startNow(ServerLevel level, UUID group) {
        TideHeartArena.ensureBuilt(level);
        if (defeated(level, group)) {
            jp.revati.skyabyss.titan.tide.back.TideBackTrials.resetGroup(level, group, false, null, true);
        }
        claim(level, group);
        GroupBossRoom room = ROOMS.of(group);
        if (room.active()) {
            return false;
        }
        return room.start(level, HOST, Groups.inBox(level, group, TideHeartArena.arena()));
    }

    public static void abandon(ServerLevel level, UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        if (room != null) {
            room.reset(level, HOST, true);
        }
    }

    // そのグループの戦っている心臓
    @Nullable
    public static TideHeart current(ServerLevel level, UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        UUID id = room == null ? null : room.bossId();
        return id != null && level.getEntity(id) instanceof TideHeart h ? h : null;
    }

    // いま殻の底で戦っている心臓 (持ち主のグループの)
    @Nullable
    public static TideHeart current(ServerLevel level) {
        UUID owner = owner();
        return owner == null ? null : current(level, owner);
    }

    // 心臓が倒れた (TideHeart.die から)。いま戦っている心臓なら、そのグループに報酬・進み・記録を渡し、入口と光の柱を開く
    public static void onDefeated(ServerLevel level, TideHeart heart) {
        GroupBossRoom room = ROOMS.byBoss(heart.getUUID());
        if (room == null) {
            return;
        }
        Set<UUID> ids = room.finish(level, HOST, heart.getUUID());
        if (ids == null) {
            return;
        }
        UUID group = room.group;
        BackGroupData.get(level).of(group).markHeartDefeated();
        // 2 周目から試練が一つ眠る (そのグループが一度でも心臓を倒したあと)
        jp.revati.skyabyss.titan.tide.back.TideBackState.of(level, group).markEverDefeated();
        List<ServerPlayer> participants = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                participants.add(p);
            }
        }
        TideLoot.onHeartDefeat(level, participants, ids);
        for (ServerPlayer player : Groups.online(level, group)) {
            Notice.unlock(player, "message.skyabyss.tide_heart.defeated");
        }
        Vec3 c = TideHeartArena.heartHome();
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(c) < 48 * 48) {
                p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.9f);
            }
        }
        SkyAbyss.LOGGER.info("The heart of the tide shell was defeated by {} participant(s)", ids.size());
        jp.revati.skyabyss.titan.end.TitanEnd.onHeartStopped(level, jp.revati.skyabyss.progress.Titans.TIDE_SHELL, ids);
    }

    // 試練で湧いた心臓・泡・真珠は保存しないが、古いものが残っていれば読み込みのときに消す
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getLevel().dimension() == TideTravel.TIDE_BACK) {
            Entity e = event.getEntity();
            if (e instanceof TideBubble || e instanceof TidePearl || (e instanceof TideHeart && e.getTags().contains(TitanBackTrials.HEART_TAG))) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ROOMS.clear();
        RoomLocks.release(ROOM_ID);
        TideHeartArena.resetState();
    }
}
