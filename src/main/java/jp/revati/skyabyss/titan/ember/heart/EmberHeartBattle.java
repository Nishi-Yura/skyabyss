package jp.revati.skyabyss.titan.ember.heart;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.BossRoom;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackRooms;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 熾の底 (心臓の間) の戦い。岩鯨・潮殻の心臓の間と同じ決まり (BossRoom):
// 5 秒の集合 → 入口が閉じて心臓が現れる → 全員が倒れるか部屋から消えて 3 秒で放棄 (心臓を消して入口を開ける)。
// 心臓が部屋の外へ出たら中央へ戻す。撃破で入口が開き、中央の台に光の柱 (立つと炉心の広間へ戻る)。
// 撃破のあと、熾の底と前室に 30 秒だれもいないと、心臓がもう一度挑める (reawaken。B の試練のリセットからも呼ばれる)。
// 撃破の記録 (この周の心臓が止まったか) は ember_back に保存する (EmberHeartData)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberHeartBattle {
    // 試練で湧いた心臓の印 (ディスクに保存しない)
    public static final String TAG = "skyabyss_ember_heart";
    public static final int RESET_AFTER_DEFEAT = 20 * 30;
    private static final BossRoom ROOM = new BossRoom();
    private static int afterDefeatIdle;
    private static long lastAsleepNotice;

    private EmberHeartBattle() {
    }

    public static BossRoom room() {
        return ROOM;
    }

    // ---------------------------------------------------------------- 記録 (この周の心臓が止まったか)
    public static class EmberHeartData extends SavedData {
        private static final String NAME = "skyabyss_ember_heart";
        private static final Factory<EmberHeartData> FACTORY = new Factory<>(EmberHeartData::new, EmberHeartData::load);
        private boolean defeated;
        private int defeats;

        public static EmberHeartData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
        }

        private static EmberHeartData load(CompoundTag tag, HolderLookup.Provider registries) {
            EmberHeartData d = new EmberHeartData();
            d.defeated = tag.getBoolean("Defeated");
            d.defeats = tag.getInt("Defeats");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("Defeated", defeated);
            tag.putInt("Defeats", defeats);
            return tag;
        }

        public boolean isDefeated() {
            return defeated;
        }

        public int defeats() {
            return defeats;
        }

        void markDefeated() {
            defeated = true;
            defeats++;
            setDirty();
        }

        void reset() {
            defeated = false;
            setDirty();
        }
    }

    public static boolean isDefeated(ServerLevel level) {
        return EmberHeartData.get(level).isDefeated();
    }

    // 部屋ごとの仕掛け
    static final BossRoom.Host HOST = new BossRoom.Host() {
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return playersIn(level, EmberHeartArena.arena());
        }

        @Override
        public void seal(ServerLevel level) {
            EmberHeartArena.seal(level);
            level.playSound(null, BlockPos.containing(EmberHeartArena.heartHome()), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.4f);
        }

        @Override
        public void open(ServerLevel level) {
            EmberHeartArena.open(level);
        }

        @Override
        public void pullIn(ServerLevel level) {
            // 前室と戸口にいる人を、入口の橋の奥へ
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, EmberHeartArena.ante().inflate(0, 1, 0),
                    p -> p.isAlive() && !p.isSpectator())) {
                moveInside(level, player);
            }
        }

        @Override
        public void letBack(ServerLevel level, Set<UUID> participants) {
            // 戸口 (封印の手前 1 マス) に触れた参加者を中へ通す
            EmberBackLayout.Box d = EmberBackLayout.HEART_DOOR;
            AABB door = new AABB(d.minX() - 0.5, d.minY() - 1, d.minZ() - 2, d.maxX() + 1.5, d.maxY() + 1, d.minZ() - 0.2);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, door,
                    p -> p.isAlive() && !p.isSpectator() && participants.contains(p.getUUID()))) {
                moveInside(level, player);
                Notice.bar(player, ModText.battle("message.skyabyss.gate_rejoin"));
            }
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, List<ServerPlayer> challengers) {
            EmberHeart heart = EmberHeartSetup.EMBER_HEART.get().create(level);
            if (heart == null) {
                return null;
            }
            Vec3 home = EmberHeartArena.heartHome();
            // 入口 (北) を向いて現れる
            heart.moveTo(home.x, home.y, home.z, 180f, 0f);
            heart.addTag(TAG);
            heart.bindArena(EmberHeartArena.arena(), challengers.size());
            level.addFreshEntity(heart);
            level.playSound(null, home.x, home.y, home.z, EmberHeartSetup.ROAR.get(), SoundSource.HOSTILE, 2.5f, 1.1f);
            level.sendParticles(ParticleTypes.FLAME, home.x, home.y + 2, home.z, 120, 2.0, 1.5, 2.0, 0.2);
            broadcast(level, ModText.battle(EmberIds.LANG_HEART + "begin"));
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
        public void broadcast(ServerLevel level, Component message) {
            EmberHeartBattle.broadcast(level, message);
        }

        @Override
        public void countdown(ServerLevel level, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            for (ServerPlayer player : playersIn(level, EmberHeartArena.arena().inflate(4, 2, 10))) {
                Notice.bar(player, text);
            }
        }

        @Override
        public void cleanup(ServerLevel level) {
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
    };

    static List<ServerPlayer> playersIn(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator());
    }

    static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : playersIn(level, EmberHeartArena.arena().inflate(4, 4, 12))) {
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
        if (ROOM.active()) {
            EmberHeartArena.tick(level, current(level));
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        if (level.getGameTime() % 100 == 0 && !ROOM.active() && nearRoom(level)) {
            EmberHeartArena.ensureBuilt(level);
        }
        if (isDefeated(level)) {
            tickAfterDefeat(level);
            return;
        }
        if (HOST.asleep(level) && !playersIn(level, EmberHeartArena.arena()).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            broadcast(level, ModText.deny(EmberIds.LANG_HEART + "asleep"));
        }
        if (!EmberHeartArena.isBuilt(level) && !playersIn(level, EmberHeartArena.arena()).isEmpty()) {
            return;
        }
        ROOM.tick(level, HOST);
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
        level.playSound(null, hub.floor(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    // 撃破のあと: 中央の台に光の柱 (立つと広間へ)。部屋と前室に 30 秒だれもいなければ、心臓が戻る
    private static void tickAfterDefeat(ServerLevel level) {
        Vec3 c = EmberHeartArena.heartHome();
        level.sendParticles(ParticleTypes.ASH, c.x, EmberHeartArena.SURFACE + 1 + (level.getGameTime() % 12), c.z, 4, 0.5, 0.3, 0.5, 0.01);
        level.sendParticles(ParticleTypes.END_ROD, c.x, EmberHeartArena.SURFACE + (level.getGameTime() % 12), c.z, 2, 0.2, 0.2, 0.2, 0.01);
        boolean anyone = false;
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (EmberHeartArena.arena().contains(player.position()) || EmberHeartArena.ante().contains(player.position())) {
                anyone = true;
            }
            if (Math.abs(player.getX() - c.x) < 1.2 && Math.abs(player.getZ() - c.z) < 1.2
                    && player.getY() >= EmberHeartArena.SURFACE - 0.5 && player.getY() < EmberHeartArena.CEILING) {
                ascend(level, player);
                Notice.narrate(player, EmberIds.LANG_HEART + "pillar");
            }
        }
        afterDefeatIdle = anyone ? 0 : afterDefeatIdle + 10;
        if (afterDefeatIdle >= RESET_AFTER_DEFEAT) {
            reawaken(level);
        }
    }

    // 心臓をもう一度挑めるようにする (熾の底を建て直す。B の試練のリセットからも呼ばれる)
    public static void reawaken(ServerLevel level) {
        EmberHeartData data = EmberHeartData.get(level);
        boolean was = data.isDefeated();
        data.reset();
        ROOM.reset(level, HOST, false);
        afterDefeatIdle = 0;
        if (was || !EmberHeartArena.isBuilt(level)) {
            EmberHeartArena.build(level);
        }
        SkyAbyss.LOGGER.info("The heart of the embers sleeps again (chamber reset)");
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。部屋の中の人を挑む人とする
    public static boolean startNow(ServerLevel level) {
        EmberHeartArena.ensureBuilt(level);
        if (isDefeated(level)) {
            reawaken(level);
        }
        if (ROOM.active()) {
            return false;
        }
        return ROOM.start(level, HOST, playersIn(level, EmberHeartArena.arena()));
    }

    public static void abandon(ServerLevel level) {
        ROOM.reset(level, HOST, true);
    }

    @Nullable
    public static EmberHeart current(ServerLevel level) {
        UUID id = ROOM.bossId();
        return id != null && level.getEntity(id) instanceof EmberHeart h ? h : null;
    }

    // 心臓が倒れた (EmberHeart.die から)。いま戦っている心臓なら、報酬・進み・記録を渡し、入口と光の柱を開く
    public static void onDefeated(ServerLevel level, EmberHeart heart) {
        Set<UUID> ids = ROOM.finish(level, HOST, heart.getUUID());
        if (ids == null) {
            return;
        }
        EmberHeartData.get(level).markDefeated();
        // 2 周目から試練が一つ眠る (ワールドで一度でも心臓が倒されたあと)
        EmberBackState.get(level).markEverDefeated();
        afterDefeatIdle = 0;
        List<ServerPlayer> participants = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                participants.add(p);
            }
        }
        EmberLoot.onHeartDefeat(level, participants, ids);
        for (ServerPlayer player : level.players()) {
            Notice.unlock(player, EmberIds.LANG_HEART + "defeated");
        }
        Vec3 c = EmberHeartArena.heartHome();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3f, 0.6f);
        SkyAbyss.LOGGER.info("The heart of the embers was stilled by {} participant(s)", ids.size());
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
        ROOM.clear();
        EmberHeartArena.resetState();
        afterDefeatIdle = 0;
    }

    // 骨組みの受け口 (EmberBackRooms): 体を建てるとき熾の底を建て、試練のリセットのとき心臓を眠らせ直す
    static void registerHooks() {
        EmberHeartArena.registerRoom();
        EmberBackRooms.onReset("heart", (level, sleeping) -> reawaken(level));
    }
}
