package jp.revati.skyabyss.titan.storm.heart;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.GroupBossRooms;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackTrials;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
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

// 竜骨の間 (心臓の間) の戦い。岩鯨・潮殻の心臓の間と同じ決まり (GroupBossRoom):
// 5 秒の集合 → 入口が閉じて心臓が現れる → 全員が倒れるか部屋から消えて 3 秒で放棄 (心臓を消して入口を開ける)。
// 心臓が部屋の外へ出たら戻す (戻す先は段階の高さ)。撃破で入口が開き、中央に光の柱 (立つと尾の間へ戻る)。
// グループ (docs/design/10 の 9 章):
//  - 竜骨の間は部屋の占有 (RoomLocks。ROOM): 柱の段と昇り風が戦いで変わるので、一度に一つのグループだけが入れる。
//    ほかのグループは前室へ戻す。持ち主が変わると柱を空に戻す
//  - 戦い・入口の封印・心臓はグループのもの (GroupBossRooms。心臓は GroupOwned で、ほかのグループには見えず狙わない)
//  - 撃破の記録はグループの記録 (BackGroupData の isHeartDefeated)。報酬・欠片は参加した一人ずつ (StormLoot)。
//    撃破のあと、そのグループのだれも竜骨の門より奥にいないまま 30 秒で、そのグループの試練が最初に戻る (StormBackTrials)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormHeartBattle {
    public static final String ROOM = "storm.heart";
    private static final GroupBossRooms ROOMS = new GroupBossRooms();
    private static long lastAsleepNotice;

    private StormHeartBattle() {
    }

    public static GroupBossRooms rooms() {
        return ROOMS;
    }

    public static GroupBossRoom room(UUID group) {
        return ROOMS.of(group);
    }

    @Nullable
    public static ServerLevel back(net.minecraft.server.MinecraftServer server) {
        return server.getLevel(StormTravel.STORM_BACK);
    }

    private static final RoomLocks.Listener LOCK = new RoomLocks.Listener() {
        @Override
        public void acquired(ServerLevel level, UUID group) {
            StormHeartArena.resetRods(level);
        }

        @Override
        public void released(ServerLevel level, UUID formerOwner) {
            GroupBossRoom room = ROOMS.peek(formerOwner);
            if (room != null && room.active()) {
                room.reset(level, HOST, true);
            }
            StormHeartArena.resetRods(level);
        }
    };

    static final GroupBossRoom.Host HOST = new GroupBossRoom.Host() {
        // 戦える人: 部屋の中の、心臓をまだ倒していないグループの人 (倒したグループは光の柱を待つだけ)
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : playersIn(level, StormHeartArena.arena())) {
                if (!StormProgress.of(level, p).heartDefeated()) {
                    out.add(p);
                }
            }
            return out;
        }

        @Override
        public void seal(ServerLevel level, UUID group) {
            StormHeartArena.seal(level, group);
            for (ServerPlayer p : Groups.online(level, group)) {
                p.playNotifySound(SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.5f);
            }
        }

        @Override
        public void open(ServerLevel level, UUID group) {
            StormHeartArena.open(level, group);
        }

        @Override
        public void pullIn(ServerLevel level, UUID group) {
            AABB ante = StormHeartArena.ante().inflate(0, 1, 0).expandTowards(0, 0, 1);
            for (ServerPlayer player : Groups.inBox(level, group, ante)) {
                moveInside(level, player);
            }
        }

        @Override
        public void letBack(ServerLevel level, UUID group, Set<UUID> participants) {
            // 戸口 (封印の手前 1 マス) に触れた参加者を中へ通す
            AABB door = new AABB(StormHeartArena.CX - 1, StormHeartArena.FLOOR, StormHeartArena.GATE_Z - 2,
                    StormHeartArena.CX + 2, StormHeartArena.FLOOR + 3, StormHeartArena.GATE_Z);
            for (ServerPlayer player : Groups.inBox(level, group, door)) {
                if (participants.contains(player.getUUID())) {
                    moveInside(level, player);
                    Notice.bar(player, ModText.battle("message.skyabyss.gate_rejoin"));
                }
            }
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers) {
            StormHeart heart = StormHeartSetup.STORM_HEART.get().create(level);
            if (heart == null) {
                return null;
            }
            Vec3 c = StormHeartArena.center();
            double y = StormHeartArena.FLOOR + StormHeart.heightFor(0, false);
            // 入口 (北) を向いて現れる
            heart.moveTo(c.x, y, c.z, 180f, 0f);
            heart.addTag(TitanBackTrials.HEART_TAG);
            heart.bindArena(StormHeartArena.arena(), challengers.size());
            heart.setGroupId(group);
            level.addFreshEntity(heart);
            StormHeartArena.resetRods(level);
            for (ServerPlayer p : Groups.online(level, group)) {
                if (p.distanceToSqr(c) < 48 * 48) {
                    p.playNotifySound(StormHeartSetup.ROAR.get(), SoundSource.HOSTILE, 2.5f, 1.1f);
                    level.sendParticles(p, ParticleTypes.ELECTRIC_SPARK, false, c.x, y + 2, c.z, 120, 2.0, 1.5, 2.0, 0.3);
                }
            }
            broadcast(level, group, ModText.battle("message.skyabyss.storm_heart.begin"));
            SkyAbyss.LOGGER.info("The heart of the thunder wing woke with {} challenger(s) (group {})", challengers.size(), group);
            return heart;
        }

        @Override
        public Vec3 home() {
            StormHeart heart = current(currentLevel);
            Vec3 c = StormHeartArena.center();
            return new Vec3(c.x, heart != null ? heart.targetY() : StormHeartArena.FLOOR + 3, c.z);
        }

        @Override
        public double leash() {
            return 8;
        }

        @Override
        public void broadcast(ServerLevel level, UUID group, Component message) {
            StormHeartBattle.broadcast(level, group, message);
        }

        @Override
        public void countdown(ServerLevel level, UUID group, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            for (ServerPlayer player : Groups.inBox(level, group, StormHeartArena.arena().inflate(4, 2, 10))) {
                Notice.bar(player, text);
            }
        }

        @Override
        public void cleanup(ServerLevel level, UUID group) {
            for (Entity e : level.getEntitiesOfClass(Entity.class, StormHeartArena.arena().inflate(4),
                    e -> e instanceof StormHeart h && e.getTags().contains(TitanBackTrials.HEART_TAG) && group.equals(h.groupId()))) {
                e.discard();
            }
            StormHeartArena.resetRods(level);
            StormHeartNetwork.warn(Groups.inBox(level, group, StormHeartArena.arena().inflate(4, 2, 10)), null, 0);
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

    // home() が心臓を探す level (tick の間だけ)
    @Nullable
    private static ServerLevel currentLevel;

    // 心臓の間の部屋の仕組み (自己テストが、倒したあとに湧き直さないことを確かめる)
    public static GroupBossRoom.Host host() {
        return HOST;
    }

    static List<ServerPlayer> playersIn(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator());
    }

    // そのグループの、部屋のまわりにいる人へ
    static void broadcast(ServerLevel level, UUID group, Component message) {
        for (ServerPlayer player : Groups.inBox(level, group, StormHeartArena.arena().inflate(4, 4, 12))) {
            Notice.send(player, message);
        }
    }

    private static void moveInside(ServerLevel level, ServerPlayer player) {
        player.fallDistance = 0;
        double x = Math.max(StormHeartArena.CX - 0.5, Math.min(StormHeartArena.CX + 1.5, player.getX()));
        // 入口の内側 2 マス (南を向く)。竜骨の筋は 1 段低い
        double z = StormHeartArena.GATE_Z + 2.5;
        player.teleportTo(level, x, StormHeartArena.walkY(x, z), z, 0f, 0f);
    }

    // 10 tick ごと: 部屋の占有を決め直し (ほかのグループは前室へ)、グループごとの戦いを動かす
    static void tick(ServerLevel level) {
        currentLevel = level;
        try {
            Vec3 ante = StormHeartArena.anteSpawn();
            RoomLocks.update(level, ROOM, StormHeartArena.arena(), BlockPos.containing(ante), RoomLocks.DEFAULT_GRACE, LOCK);
            ROOMS.tick(level, HOST);
        } finally {
            currentLevel = null;
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != StormTravel.STORM_BACK) {
            return;
        }
        if (level.players().isEmpty() || level.getGameTime() % 10 != 0) {
            return;
        }
        if (level.getGameTime() % 100 == 0 && !ROOMS.anyActive()) {
            StormHeartArena.ensureBuilt(level);
        }
        tickAfterDefeat(level);
        if (HOST.asleep(level) && !HOST.playersInside(level).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            for (ServerPlayer p : HOST.playersInside(level)) {
                Notice.send(p, ModText.deny("message.skyabyss.storm_heart.asleep"));
            }
        }
        tick(level);
    }

    // 竜骨の前室へ降りる (テスト用のコマンドと、背中の担当の階段の代わり)
    public static void descend(ServerLevel level, ServerPlayer player) {
        StormHeartArena.ensureBuilt(level);
        Vec3 at = StormHeartArena.anteSpawn();
        player.fallDistance = 0;
        player.teleportTo(level, at.x, at.y, at.z, 0f, 0f);
        level.playSound(null, at.x, at.y, at.z, StormHeartSetup.BEAT.get(), SoundSource.PLAYERS, 1.2f, 0.8f);
        Notice.narrate(player, "message.skyabyss.storm_heart.descend");
    }

    // 光の柱から尾の間へ戻る。尾の間がまだ建っていなければ (背中の鳥がない) 地上の共有スポーン地点へ
    public static void ascend(ServerLevel level, ServerPlayer player) {
        BlockPos tail = StormBackLayout.TAIL_CENTER;
        player.fallDistance = 0;
        if (!level.getBlockState(tail.below()).isAir()) {
            player.teleportTo(level, tail.getX() + 0.5, tail.getY(), tail.getZ() + 0.5, 180f, 0f);
            level.playSound(null, tail, StormHeartSetup.BEAT.get(), SoundSource.PLAYERS, 1.2f, 1.2f);
            return;
        }
        ServerLevel overworld = level.getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ()),
                spawn.getZ() + 0.5, player.getYRot(), 0f);
    }

    // 心臓を倒したグループの人にだけ、中央の光の柱 (立つと尾の間へ)
    private static void tickAfterDefeat(ServerLevel level) {
        Vec3 c = StormHeartArena.center();
        Set<UUID> shown = new LinkedHashSet<>();
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (!player.isAlive() || player.isSpectator() || !StormHeartArena.arena().inflate(2).contains(player.position())) {
                continue;
            }
            if (!StormProgress.of(level, player).heartDefeated()) {
                continue;
            }
            UUID g = Groups.keyOf(player);
            if (shown.add(g)) {
                for (ServerPlayer mate : Groups.online(level, g)) {
                    level.sendParticles(mate, ParticleTypes.END_ROD, false, c.x, StormHeartArena.FLOOR + (level.getGameTime() % 12), c.z, 3, 0.25, 0.2, 0.25, 0.02);
                    level.sendParticles(mate, ParticleTypes.ELECTRIC_SPARK, false, c.x, StormHeartArena.FLOOR + 1, c.z, 2, 0.4, 0.3, 0.4, 0.02);
                }
            }
            if (Math.abs(player.getX() - c.x) < 1.2 && Math.abs(player.getZ() - c.z) < 1.2
                    && player.getY() >= StormHeartArena.FLOOR - 1.5 && player.getY() < StormHeartArena.CEILING) {
                Notice.narrate(player, "message.skyabyss.storm_heart.pillar");
                ascend(level, player);
            }
        }
    }

    // そのグループの心臓をまた挑めるようにする (そのグループの試練も最初に戻る。前の周の番号の休み場は無効)
    public static void reawaken(ServerLevel level, UUID group) {
        StormBackTrials.resetGroup(level, group, false, null, true, false);
        SkyAbyss.LOGGER.info("The heart of the thunder wing sleeps again for group {}", group);
    }

    // グループの試練が戻った (StormBackTrials.resetGroup から): そのグループの戦いを止め、部屋を持っていれば手放す
    public static void onGroupReset(ServerLevel level, UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        if (room != null && (room.active() || room.gathering())) {
            room.reset(level, HOST, false);
        }
        if (group.equals(RoomLocks.owner(ROOM))) {
            RoomLocks.release(ROOM);
            StormHeartArena.resetRods(level);
        }
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。部屋の中のそのグループの人を挑む人とする
    public static boolean startNow(ServerLevel level, UUID group) {
        StormHeartArena.ensureBuilt(level);
        if (StormProgress.of(level, group).heartDefeated()) {
            reawaken(level, group);
        }
        GroupBossRoom room = ROOMS.of(group);
        if (room.active()) {
            return false;
        }
        return room.start(level, HOST, Groups.inBox(level, group, StormHeartArena.arena()));
    }

    public static void abandon(ServerLevel level, UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        if (room != null) {
            room.reset(level, HOST, true);
        }
    }

    // すべてのグループの戦いを止める (コマンド・自己テスト)
    public static void abandonAll(ServerLevel level) {
        ROOMS.resetAll(level, HOST, true);
    }

    // いま戦っている心臓 (部屋の持ち主の。いなければどれか一つ)
    @Nullable
    public static StormHeart current(@Nullable ServerLevel level) {
        if (level == null) {
            return null;
        }
        UUID owner = RoomLocks.owner(ROOM);
        if (owner != null) {
            StormHeart h = current(level, owner);
            if (h != null) {
                return h;
            }
        }
        for (GroupBossRoom room : ROOMS.all()) {
            UUID id = room.bossId();
            if (id != null && level.getEntity(id) instanceof StormHeart h) {
                return h;
            }
        }
        return null;
    }

    @Nullable
    public static StormHeart current(ServerLevel level, UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        UUID id = room == null ? null : room.bossId();
        return id != null && level.getEntity(id) instanceof StormHeart h ? h : null;
    }

    // 心臓が倒れた (StormHeart.die から)。いま戦っている心臓なら、報酬・進み・記録を渡し、入口と光の柱を開く
    public static void onDefeated(ServerLevel level, StormHeart heart) {
        GroupBossRoom room = ROOMS.byBoss(heart.getUUID());
        if (room == null) {
            return;
        }
        UUID group = room.group;
        Set<UUID> ids = room.finish(level, HOST, heart.getUUID());
        if (ids == null) {
            return;
        }
        StormProgress progress = StormProgress.of(level, group);
        progress.record.markHeartDefeated();
        progress.markEverDefeated();
        List<ServerPlayer> participants = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                participants.add(p);
            }
        }
        StormLoot.onHeartDefeat(level, participants, ids);
        Set<ServerPlayer> told = new LinkedHashSet<>(Groups.online(level, group));
        told.addAll(participants);
        for (ServerPlayer player : told) {
            Notice.unlock(player, "message.skyabyss.storm_heart.defeated");
        }
        Vec3 c = StormHeartArena.center();
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(c) < 48 * 48) {
                p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.9f);
            }
        }
        SkyAbyss.LOGGER.info("The heart of the thunder wing was defeated by {} participant(s) (group {})", ids.size(), group);
        jp.revati.skyabyss.titan.end.TitanEnd.onHeartStopped(level, jp.revati.skyabyss.progress.Titans.THUNDER_WING, ids);
    }

    // 心臓が倒れたグループ (自己テスト用): 心臓の持ち主
    @Nullable
    static UUID groupOf(StormHeart heart) {
        return ((GroupOwned) heart).groupId();
    }

    // 試練で湧いた心臓は保存しないが、古いものが残っていれば読み込みのときに消す
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getLevel().dimension() == StormTravel.STORM_BACK
                && event.getEntity() instanceof StormHeart && event.getEntity().getTags().contains(TitanBackTrials.HEART_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ROOMS.clear();
        RoomLocks.release(ROOM);
        StormHeartArena.resetState();
    }
}
