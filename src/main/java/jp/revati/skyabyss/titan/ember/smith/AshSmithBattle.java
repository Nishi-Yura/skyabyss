package jp.revati.skyabyss.titan.ember.smith;

import jp.revati.skyabyss.notice.Notice;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.back.EmberBackTrials;
import jp.revati.skyabyss.titan.ember.back.EmberRooms;
import jp.revati.skyabyss.titan.ember.back.trial.Forge;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.GroupBossRooms;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 鍛え手の間の戦い (13 の 5.2節・7.5節)。岩鯨の番兵長・潮殻の心臓・雷獣と同じ決まり (GroupBossRoom。グループごとの戦い):
// 鍛え手の間に人が入ると 5 秒の集合 → 入口 (SMITH_DOOR) がそのグループにだけ閉じて、鍛え手が奥から起き上がる → 全員が倒れるか部屋から消えて 3 秒で放棄。
// 撃破で入口がそのグループに開き、報酬 (AshSmithLoot)、そのグループの東の熾り石が鳴らせる (EmberBackTrials.markReady EAST)。
// そのグループがこの周に鍛え手を倒した・東の試練が眠っている (EmberBackState.ready(EAST)) あいだは、部屋に入っても戦いは始まらない。
// 鍛えの間と鍛え手の間は一度に一つのグループ (EmberRooms の east)。鍛え手と呼んだ灰被りはそのグループのもの (GroupOwned)。
// 戦いの状態はメモリにだけある (部屋の持ち主が替わるとき・サーバー停止で戻る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AshSmithBattle {
    // 部屋の中に人がいないまま戦いを始めるとき (自己テスト) のグループ
    public static final UUID TEST_GROUP = UUID.nameUUIDFromBytes("skyabyss ash smith test group".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final GroupBossRooms ROOMS = new GroupBossRooms();
    @Nullable
    private static UUID lastGroup;
    private static long lastAsleepNotice;

    private AshSmithBattle() {
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

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(EmberIds.BACK);
    }

    // そのグループが、この周に鍛え手を倒したか (または東の試練が眠っているか)。東の熾り石が鳴らせる
    public static boolean done(ServerLevel level, UUID group) {
        return EmberBackState.of(level, group).ready(EmberBackState.Trial.EAST);
    }

    // 部屋の持ち主 (いなければ最後に戦ったグループ) について (コマンドと自己テスト)
    public static boolean done(ServerLevel level) {
        UUID g = EmberRooms.owner(EmberRooms.EAST);
        if (g == null) {
            g = lastGroup;
        }
        return g != null && done(level, g);
    }

    static final class Host implements GroupBossRoom.Host {
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            UUID owner = EmberRooms.owner(EmberRooms.EAST);
            return level.getEntitiesOfClass(ServerPlayer.class, AshSmithArena.BOX.aabb(),
                    p -> p.isAlive() && !p.isSpectator() && AshSmithArena.inside(p.position()) && (owner == null || Groups.isMember(p, owner)));
        }

        @Override
        public void seal(ServerLevel level, UUID group) {
            AshSmithArena.seal(level, group);
            EmberBackTrials.soundTo(level, group, AshSmithArena.DOOR.center().getCenter(), SoundEvents.IRON_DOOR_CLOSE, 2f, 0.5f);
        }

        @Override
        public void open(ServerLevel level, UUID group) {
            AshSmithArena.open(level, group);
        }

        @Override
        public void pullIn(ServerLevel level, UUID group) {
            AABB box = AshSmithArena.ANTE.aabb().minmax(AshSmithArena.DOOR.aabb());
            for (ServerPlayer p : Groups.inBox(level, group, box)) {
                moveInside(level, p);
            }
        }

        @Override
        public void letBack(ServerLevel level, UUID group, Set<UUID> participants) {
            for (ServerPlayer p : Groups.inBox(level, group, AshSmithArena.DOOR.aabb().inflate(0.5, 0, 0.5))) {
                if (participants.contains(p.getUUID())) {
                    moveInside(level, p);
                    Notice.bar(p, ModText.battle("message.skyabyss.gate_rejoin"));
                }
            }
        }

        private void moveInside(ServerLevel level, ServerPlayer p) {
            Vec3 at = AshSmithArena.ENTRANCE_INSIDE;
            p.fallDistance = 0;
            p.teleportTo(level, at.x, at.y, at.z, 0f, 0f);
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers) {
            AshSmith smith = EmberSmithSetup.ASH_SMITH.get().create(level);
            if (smith == null) {
                return null;
            }
            Vec3 at = AshSmithArena.HOME.add(0, 0, 4);
            smith.moveTo(at.x, at.y, at.z, 180f, 0f);
            smith.bind(challengers.size());
            smith.setGroupId(group);
            if (!level.addFreshEntity(smith)) {
                SkyAbyss.LOGGER.warn("The ash smith could not be added to {} at {}", level.dimension().location(), smith.position());
                return null;
            }
            lastGroup = group;
            EmberBackTrials.soundTo(level, group, at, EmberSmithSetup.RAGE.get(), 3f, 0.7f);
            EmberBackTrials.particlesTo(level, group, ParticleTypes.FLAME, at.x, at.y + 1.5, at.z, 40, 1.0, 1.2, 1.0, 0.05);
            broadcast(level, group, ModText.battle("message.skyabyss.ember.smith.begin"));
            broadcast(level, group, ModText.hint("message.skyabyss.ember.smith.hint_back"));
            SkyAbyss.LOGGER.info("The ash smith woke with {} challenger(s)", challengers.size());
            return smith;
        }

        @Override
        public Vec3 home() {
            return AshSmithArena.HOME;
        }

        @Override
        public double leash() {
            return 17;
        }

        @Override
        public void broadcast(ServerLevel level, UUID group, Component message) {
            AshSmithBattle.broadcast(level, group, message);
        }

        @Override
        public void countdown(ServerLevel level, UUID group, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            AABB box = AshSmithArena.BOX.aabb().minmax(AshSmithArena.ANTE.aabb());
            for (ServerPlayer p : Groups.inBox(level, group, box)) {
                Notice.bar(p, text);
            }
        }

        @Override
        public void cleanup(ServerLevel level, UUID group) {
            for (Entity e : level.getEntitiesOfClass(Entity.class, AshSmithArena.BOX.aabb().inflate(6), e -> e.getTags().contains(AshSmith.SMITH_TAG))) {
                e.discard();
            }
            if (AshSmithArena.isBuilt(level)) {
                AshSmithArena.buildFurnace(level, false);
            }
        }

        @Override
        public boolean asleep(ServerLevel level) {
            return level.getDifficulty() == Difficulty.PEACEFUL;
        }
    }

    public static Host host() {
        return new Host();
    }

    // そのグループの、部屋のまわりの人へ
    static void broadcast(ServerLevel level, UUID group, Component message) {
        AABB box = AshSmithArena.BOX.aabb().minmax(AshSmithArena.ANTE.aabb()).inflate(2);
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.isAlive() && box.contains(p.position())) {
                Notice.send(p, message);
            }
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != EmberIds.BACK || level.players().isEmpty()) {
            return;
        }
        if (level.getGameTime() % 10 != 0 || !level.isLoaded(AshSmithArena.MARK) || !AshSmithArena.isBuilt(level)) {
            return;
        }
        UUID owner = EmberRooms.owner(EmberRooms.EAST);
        if (owner != null && done(level, owner) && !ROOMS.anyActive()) {
            return;
        }
        Host host = new Host();
        if (owner != null && host.asleep(level) && !host.playersInside(level).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            broadcast(level, owner, ModText.deny("message.skyabyss.ember.smith.asleep"));
        }
        ROOMS.tick(level, host);
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。部屋の中の人を挑む人とする。部屋の中に人がいなければ TEST_GROUP で
    public static boolean startNow(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            return false;
        }
        List<ServerPlayer> inside = new Host().playersInside(level);
        return startNow(level, inside.isEmpty() ? TEST_GROUP : Groups.keyOf(inside.get(0)));
    }

    public static boolean startNow(ServerLevel level, UUID group) {
        if (!AshSmithArena.isBuilt(level)) {
            AshSmithArena.build(level, AshSmithArena.BOX);
        }
        lastGroup = group;
        RoomLocks.claim(EmberRooms.EAST.lock(), group, level.getGameTime());
        GroupBossRoom room = ROOMS.of(group);
        if (room.active()) {
            return false;
        }
        Host host = new Host();
        return room.start(level, host, host.playersInside(level).stream().filter(p -> Groups.isMember(p, group)).toList());
    }

    // 鍛えの間の扉 (C2) が開いて 5 秒の予告が済んだ (Forge.onDoorOpened)。部屋が建っていなければ建て、
    // 扉を開けたグループの人がもう中にいれば (予告のうちに入った人) 集合を待たずに始める。誰も入っていなければ、人が入ったとき (onLevelTick) に 5 秒の集合から始まる
    public static void onForgeDoorOpened(ServerLevel level) {
        if (level.dimension() != EmberIds.BACK) {
            return;
        }
        UUID group = Forge.doorGroup();
        if (group == null) {
            group = EmberRooms.owner(EmberRooms.EAST);
        }
        if (group == null || done(level, group)) {
            return;
        }
        if (!AshSmithArena.isBuilt(level)) {
            AshSmithArena.build(level, AshSmithArena.BOX);
        }
        Host host = new Host();
        GroupBossRoom room = ROOMS.of(group);
        if (room.active() || host.asleep(level)) {
            return;
        }
        UUID g = group;
        List<ServerPlayer> inside = host.playersInside(level).stream().filter(p -> Groups.isMember(p, g)).toList();
        if (!inside.isEmpty()) {
            room.start(level, host, inside);
        }
    }

    public static void abandon(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level != null) {
            ROOMS.resetAll(level, new Host(), true);
        }
    }

    // 部屋を最初に戻す (部屋の持ち主が替わるとき。EmberBackRooms の smith): 戦いを放棄し (入口はそのグループに開け直す)、炉の扉を閉じる
    public static void reset(ServerLevel level) {
        ROOMS.resetAll(level, new Host(), false);
        if (AshSmithArena.isBuilt(level)) {
            AshSmithArena.buildFurnace(level, false);
        }
    }

    // 戦いを最初に戻す (テスト用。東の熾り石の記録はグループのリセットが戻す)
    public static void reawaken(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level != null) {
            reset(level);
        }
    }

    @Nullable
    public static AshSmith current(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            return null;
        }
        for (GroupBossRoom room : ROOMS.all()) {
            UUID id = room.bossId();
            if (!room.active() || id == null) {
                continue;
            }
            if (level.getEntity(id) instanceof AshSmith s) {
                return s;
            }
            AshSmith found = level.getEntitiesOfClass(AshSmith.class, AshSmithArena.BOX.aabb().inflate(4), e -> e.getUUID().equals(id)).stream()
                    .findFirst().orElse(null);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // 鍛え手が倒れた (AshSmith.die から)。いま戦っている鍛え手なら、そのグループに記録・報酬を渡して入口を開ける
    static void onDefeated(ServerLevel level, AshSmith smith) {
        if (level.dimension() != EmberIds.BACK) {
            return;
        }
        GroupBossRoom room = ROOMS.byBoss(smith.getUUID());
        if (room == null) {
            return;
        }
        UUID group = room.group;
        Set<UUID> ids = room.finish(level, new Host(), smith.getUUID());
        if (ids == null) {
            return;
        }
        // 呼ばれた灰被りは一緒に崩れる
        for (Entity e : level.getEntitiesOfClass(EmberMob.class, AshSmithArena.BOX.aabb(), e -> e.getTags().contains(AshSmith.SMITH_TAG))) {
            e.discard();
        }
        EmberBackTrials.markReady(level, group, EmberBackState.Trial.EAST);
        AshSmithLoot.onDefeat(level, ids);
        broadcast(level, group, ModText.lore("message.skyabyss.ember.smith.after"));
        broadcast(level, group, ModText.progress("message.skyabyss.ember.smith.east_stone_ready"));
        Vec3 c = AshSmithArena.HOME;
        EmberBackTrials.soundTo(level, group, c, SoundEvents.BEACON_ACTIVATE, 3f, 0.8f);
        EmberBackTrials.particlesTo(level, group, ParticleTypes.CLOUD, c.x, c.y + 1, c.z, 60, 4, 1, 4, 0.05);
        SkyAbyss.LOGGER.info("The ash smith was defeated by {} participant(s) of group {}", ids.size(), group);
    }

    // 試練で湧いた鍛え手 (と呼んだ灰被り) は保存しないが、古いものが残っていれば読み込みのときに消す
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(AshSmith.SMITH_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ROOMS.clear();
        lastGroup = null;
    }
}
