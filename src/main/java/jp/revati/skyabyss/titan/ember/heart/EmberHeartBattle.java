package jp.revati.skyabyss.titan.ember.heart;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackRooms;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.back.EmberBackTrials;
import jp.revati.skyabyss.titan.ember.back.EmberRooms;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.GroupBossRooms;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 熾の底 (心臓の間) の戦い。岩鯨・潮殻の心臓の間と同じ決まり (GroupBossRoom。グループごとの戦い):
// 5 秒の集合 → 入口がそのグループにだけ閉じて心臓が現れる → 全員が倒れるか部屋から消えて 3 秒で放棄 (心臓を消して入口を開ける)。
// 心臓が部屋の外へ出たら中央へ戻す。撃破で入口が開き、中央の台に光の柱 (立つと炉心の広間へ戻る)。
// 熾の底は区画が溶岩に沈む (ブロックが動く) ので、一度に一つのグループ (EmberRooms の heart)。心臓・礫はそのグループのもの (GroupOwned)。
// 撃破の記録はグループの記録 (BackGroupData.Record の心臓の撃破)。そのグループの誰も門の奥にいないまま 30 秒たつと、
// そのグループの試練が次の周になる (EmberBackTrials のグループのリセット)。部屋は持ち主が出ていくと最初に戻る (reawaken)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberHeartBattle {
    // 試練で湧いた心臓の印 (ディスクに保存しない)
    public static final String TAG = "skyabyss_ember_heart";
    // 部屋の中に人がいないまま戦いを始めるとき (自己テスト) のグループ
    public static final UUID TEST_GROUP = UUID.nameUUIDFromBytes("skyabyss ember heart test group".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final GroupBossRooms ROOMS = new GroupBossRooms();
    // 最後に戦いを始めたグループ (コマンドと自己テストが見る)
    @Nullable
    private static UUID lastGroup;
    // 建ててから心臓が倒れたか (部屋を戻すときに建て直す)
    private static boolean defeatedSinceBuild;
    private static long lastAsleepNotice;

    private EmberHeartBattle() {
    }

    public static GroupBossRooms rooms() {
        return ROOMS;
    }

    // 最後に戦いを始めたグループの戦い (コマンドと自己テスト)
    // 戦いの最中か (そのグループの部屋が集合中か戦闘中)。倒れた人の復活先の判断に使う (P1-1)
    public static boolean fighting(UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        return room != null && (room.active() || room.gathering());
    }

    public static GroupBossRoom room() {
        return ROOMS.of(lastGroup != null ? lastGroup : TEST_GROUP);
    }

    @Nullable
    public static UUID lastGroup() {
        return lastGroup;
    }

    // そのグループが、この周の心臓を倒したか
    public static boolean isDefeated(ServerLevel level, UUID group) {
        return EmberBackState.of(level, group).heartDefeated();
    }

    // 最後に戦ったグループが倒したか (コマンドと自己テスト)
    public static boolean isDefeated(ServerLevel level) {
        return lastGroup != null && isDefeated(level, lastGroup);
    }

    // 部屋ごとの仕掛け
    static final GroupBossRoom.Host HOST = new GroupBossRoom.Host() {
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            // 部屋の持ち主 (占有) がいれば、その人たちだけ (ほかのグループの人は 10 tick のうちに外へ出される)
            UUID owner = EmberRooms.owner(EmberRooms.HEART);
            List<ServerPlayer> in = playersIn(level, EmberHeartArena.arena());
            return owner == null ? in : in.stream().filter(p -> Groups.isMember(p, owner)).toList();
        }

        @Override
        public void seal(ServerLevel level, UUID group) {
            EmberHeartArena.seal(level, group);
            EmberBackTrials.soundTo(level, group, EmberHeartArena.heartHome(), SoundEvents.IRON_DOOR_CLOSE, 2f, 0.4f);
        }

        @Override
        public void open(ServerLevel level, UUID group) {
            EmberHeartArena.open(level, group);
        }

        @Override
        public void pullIn(ServerLevel level, UUID group) {
            // 前室と戸口にいる、そのグループの人を、入口の橋の奥へ
            for (ServerPlayer player : Groups.inBox(level, group, EmberHeartArena.ante().inflate(0, 1, 0))) {
                moveInside(level, player);
            }
        }

        @Override
        public void letBack(ServerLevel level, UUID group, Set<UUID> participants) {
            // 戸口 (封印の手前 1 マス) に触れた参加者を中へ通す
            EmberBackLayout.Box d = EmberBackLayout.HEART_DOOR;
            AABB door = new AABB(d.minX() - 0.5, d.minY() - 1, d.minZ() - 2, d.maxX() + 1.5, d.maxY() + 1, d.minZ() - 0.2);
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
            EmberHeart heart = EmberHeartSetup.EMBER_HEART.get().create(level);
            if (heart == null) {
                return null;
            }
            Vec3 home = EmberHeartArena.heartHome();
            // 入口 (北) を向いて現れる
            heart.moveTo(home.x, home.y, home.z, 180f, 0f);
            heart.addTag(TAG);
            heart.bindArena(EmberHeartArena.arena(), challengers.size());
            heart.setGroupId(group);
            level.addFreshEntity(heart);
            lastGroup = group;
            EmberBackTrials.soundTo(level, group, home, EmberHeartSetup.ROAR.get(), 2.5f, 1.1f);
            EmberBackTrials.particlesTo(level, group, ParticleTypes.FLAME, home.x, home.y + 2, home.z, 120, 2.0, 1.5, 2.0, 0.2);
            broadcast(level, group, ModText.battle(EmberIds.LANG_HEART + "begin"));
            EmberHeart.logBorn(challengers.size());
            return heart;
        }

        @Override
        public Vec3 home() {
            return EmberHeartArena.heartHome();
        }

        @Override
        public double leash() {
            return 6;
        }

        @Override
        public void broadcast(ServerLevel level, UUID group, Component message) {
            EmberHeartBattle.broadcast(level, group, message);
        }

        @Override
        public void countdown(ServerLevel level, UUID group, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            for (ServerPlayer player : Groups.inBox(level, group, EmberHeartArena.arena().inflate(4, 2, 10))) {
                Notice.bar(player, text);
            }
        }

        @Override
        public void cleanup(ServerLevel level, UUID group) {
            EmberHeartArena.cleanup(level);
            clearChill(level);
            for (Entity e : level.getEntitiesOfClass(Entity.class, EmberHeartArena.arena().inflate(4),
                    e -> e instanceof EmberAshPellet || e.getTags().contains(TAG))) {
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

    // そのグループの、部屋のまわりの人へ
    static void broadcast(ServerLevel level, UUID group, Component message) {
        for (ServerPlayer player : Groups.inBox(level, group, EmberHeartArena.arena().inflate(4, 4, 12))) {
            Notice.send(player, message);
        }
    }

    private static void moveInside(ServerLevel level, ServerPlayer player) {
        player.fallDistance = 0;
        Vec3 at = EmberHeartArena.insideSpawn(player.getX());
        player.teleportTo(level, at.x, at.y, at.z, 0f, 0f);
    }

    // 凍えをすべて解く (部屋の人と、サーバーにいる全員。一時の修飾子なので、残っていれば消す)
    static void clearChill(ServerLevel level) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            EmberHeart.applyChill(p, false);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != EmberIds.BACK) {
            return;
        }
        if (level.players().isEmpty()) {
            return;
        }
        if (ROOMS.anyActive()) {
            EmberHeartArena.tick(level, current(level));
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        if (level.getGameTime() % 100 == 0 && !ROOMS.anyActive() && nearRoom(level)) {
            EmberHeartArena.ensureBuilt(level);
        }
        UUID owner = EmberRooms.owner(EmberRooms.HEART);
        if (owner != null && isDefeated(level, owner) && !ROOMS.anyActive()) {
            tickAfterDefeat(level, owner);
            return;
        }
        if (HOST.asleep(level) && owner != null && !HOST.playersInside(level).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            broadcast(level, owner, ModText.deny(EmberIds.LANG_HEART + "asleep"));
        }
        if (!EmberHeartArena.isBuilt(level) && !playersIn(level, EmberHeartArena.arena()).isEmpty()) {
            return;
        }
        ROOMS.tick(level, HOST);
    }

    // 部屋の 48 ブロック以内に人がいるか (離れた人のために部屋を読み込ませない)
    private static boolean nearRoom(ServerLevel level) {
        Vec3 c = EmberHeartArena.heartHome();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(c) < 64 * 64) {
                return true;
            }
        }
        return false;
    }

    // 熾の底へ降りる (前室へ。B の休み場 heart の南)
    public static void descend(ServerLevel level, ServerPlayer player) {
        EmberHeartArena.ensureBuilt(level);
        Vec3 at = EmberHeartArena.anteSpawn();
        // 前室 (B の体) がまだ建っていない開発中の島では、部屋の入口の橋の奥へ
        if (!level.getBlockState(BlockPos.containing(at)).isAir() || !level.getBlockState(BlockPos.containing(at).above()).isAir()) {
            at = EmberHeartArena.insideSpawn(0.5);
        }
        player.fallDistance = 0;
        player.teleportTo(level, at.x, at.y, at.z, 0f, 0f);
        Notice.narrate(player, EmberIds.LANG_HEART + "descend");
        Notice.bar(player, ModText.hint(EmberIds.LANG_HEART + "exit_hint"));
    }

    // 炉心の広間へ戻る (休み場 hub の脇)
    public static void ascend(ServerLevel level, ServerPlayer player) {
        EmberBackLayout.Rest hub = EmberBackLayout.rest("hub");
        player.fallDistance = 0;
        player.teleportTo(level, hub.floor().getX() + 0.5, hub.floor().getY() + 1, hub.floor().getZ() + 3.5, hub.yaw(), 0f);
        player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    // 撃破のあと (部屋の持ち主が倒したグループのとき): 中央の台に光の柱 (立つと広間へ)
    private static void tickAfterDefeat(ServerLevel level, UUID group) {
        Vec3 c = EmberHeartArena.heartHome();
        EmberBackTrials.particlesTo(level, group, ParticleTypes.ASH, c.x, EmberHeartArena.SURFACE + 1 + (level.getGameTime() % 12), c.z, 4, 0.5, 0.3, 0.5,
                0.01);
        EmberBackTrials.particlesTo(level, group, ParticleTypes.END_ROD, c.x, EmberHeartArena.SURFACE + (level.getGameTime() % 12), c.z, 2, 0.2, 0.2, 0.2,
                0.01);
        for (ServerPlayer player : Groups.alive(level, group)) {
            if (Math.abs(player.getX() - c.x) < 1.2 && Math.abs(player.getZ() - c.z) < 1.2
                    && player.getY() >= EmberHeartArena.SURFACE - 0.5 && player.getY() < EmberHeartArena.CEILING) {
                ascend(level, player);
                Notice.narrate(player, EmberIds.LANG_HEART + "pillar");
            }
        }
    }

    // 部屋を最初に戻す (部屋の持ち主が替わるとき。EmberBackRooms の heart)。戦いを止め、心臓が倒れたあとなら熾の底を建て直す
    public static void reawaken(ServerLevel level) {
        ROOMS.resetAll(level, HOST, false);
        if (defeatedSinceBuild || !EmberHeartArena.isBuilt(level)) {
            EmberHeartArena.build(level);
            defeatedSinceBuild = false;
        }
        SkyAbyss.LOGGER.info("The heart of the embers sleeps again (chamber reset)");
    }

    // 最後に戦ったグループの心臓をもう一度挑めるようにする (テスト用のコマンドと自己テスト。部屋も戻す)
    public static void reawakenForTest(ServerLevel level) {
        if (lastGroup != null) {
            var r = EmberBackState.of(level, lastGroup).record();
            if (r != null) {
                r.clearHeartDefeated();
            }
        }
        reawaken(level);
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。部屋の中の人を挑む人とする。部屋の中に人がいなければ TEST_GROUP で
    public static boolean startNow(ServerLevel level) {
        List<ServerPlayer> inside = playersIn(level, EmberHeartArena.arena());
        return startNow(level, inside.isEmpty() ? TEST_GROUP : Groups.keyOf(inside.get(0)));
    }

    public static boolean startNow(ServerLevel level, UUID group) {
        EmberHeartArena.ensureBuilt(level);
        lastGroup = group;
        if (isDefeated(level, group)) {
            reawakenForTest(level);
        }
        // 部屋をそのグループの持ち物にする (ほかのグループの人は次の見回りで外へ出る)
        RoomLocks.claim(EmberRooms.HEART.lock(), group, level.getGameTime());
        GroupBossRoom room = ROOMS.of(group);
        if (room.active()) {
            return false;
        }
        List<ServerPlayer> inside = playersIn(level, EmberHeartArena.arena()).stream().filter(p -> Groups.isMember(p, group)).toList();
        return room.start(level, HOST, inside);
    }

    // すべての戦いを放棄する (コマンド・自己テスト)
    public static void abandon(ServerLevel level) {
        ROOMS.resetAll(level, HOST, true);
    }

    // いま戦っている心臓 (一度に一つのグループなので、多くても一つ)
    @Nullable
    public static EmberHeart current(ServerLevel level) {
        for (GroupBossRoom room : ROOMS.all()) {
            UUID id = room.bossId();
            if (room.active() && id != null && level.getEntity(id) instanceof EmberHeart h) {
                return h;
            }
        }
        return null;
    }

    // 心臓が倒れた (EmberHeart.die から)。いま戦っている心臓なら、そのグループに報酬・進み・記録を渡し、入口と光の柱を開く
    public static void onDefeated(ServerLevel level, EmberHeart heart) {
        GroupBossRoom room = ROOMS.byBoss(heart.getUUID());
        if (room == null) {
            return;
        }
        UUID group = room.group;
        Set<UUID> ids = room.finish(level, HOST, heart.getUUID());
        if (ids == null) {
            return;
        }
        // この周の撃破 (グループの記録)。2 周目からそのグループの試練が一つ眠る
        EmberBackState.of(level, group).markHeartDefeated();
        defeatedSinceBuild = true;
        List<ServerPlayer> participants = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                participants.add(p);
            }
        }
        EmberLoot.onHeartDefeat(level, participants, ids);
        for (ServerPlayer player : Groups.online(level, group)) {
            Notice.unlock(player, EmberIds.LANG_HEART + "defeated");
        }
        EmberBackTrials.soundTo(level, group, EmberHeartArena.heartHome(), SoundEvents.BEACON_DEACTIVATE, 3f, 0.6f);
        SkyAbyss.LOGGER.info("The heart of the embers was stilled by {} participant(s) of group {}", ids.size(), group);
        jp.revati.skyabyss.titan.end.TitanEnd.onHeartStopped(level, jp.revati.skyabyss.progress.Titans.EMBER, ids);
    }

    // 試練で湧いた心臓・礫は保存しないが、古いものが残っていれば読み込みのときに消す
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getLevel().dimension() == EmberIds.BACK) {
            Entity e = event.getEntity();
            if (e instanceof EmberAshPellet || (e instanceof EmberHeart && e.getTags().contains(TAG))) {
                event.setCanceled(true);
            }
        }
    }

    // 凍えは一時の修飾子。死んだ・部屋を出た・ログアウトした人から外す
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EmberHeart.applyChill(player, false);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EmberHeart.applyChill(player, false);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EmberHeart.applyChill(player, false);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ROOMS.clear();
        EmberHeartArena.resetState();
        lastGroup = null;
        defeatedSinceBuild = false;
    }

    // 骨組みの受け口 (EmberBackRooms): 体を建てるとき熾の底を建て、部屋の持ち主が替わるとき部屋を戻す
    static void registerHooks() {
        EmberHeartArena.registerRoom();
        EmberBackRooms.onReset("heart", (level, sleeping) -> reawaken(level));
    }
}
