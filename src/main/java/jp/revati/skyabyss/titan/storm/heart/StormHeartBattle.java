package jp.revati.skyabyss.titan.storm.heart;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.BossRoom;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
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

// 竜骨の間 (心臓の間) の戦い。岩鯨・潮殻の心臓の間と同じ決まり (BossRoom):
// 5 秒の集合 → 入口が閉じて心臓が現れる → 全員が倒れるか部屋から消えて 3 秒で放棄 (心臓を消して入口を開ける)。
// 心臓が部屋の外へ出たら戻す (戻す先は段階の高さ)。撃破で入口が開き、中央に光の柱 (立つと尾の間へ戻る)。
// 撃破のあと、部屋と前室に 30 秒だれもいないと、心臓がまた挑めるようになる (試練ごとのリセットは背中の担当の TitanBackReset)。
// 背中の記録 (心臓を倒したか) は TitanBackData (ディメンションごと)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormHeartBattle {
    public static final int RESET_AFTER_DEFEAT = 20 * 30;
    private static final BossRoom ROOM = new BossRoom();
    private static int afterDefeatIdle;
    private static long lastAsleepNotice;

    private StormHeartBattle() {
    }

    public static BossRoom room() {
        return ROOM;
    }

    @Nullable
    public static ServerLevel back(net.minecraft.server.MinecraftServer server) {
        return server.getLevel(StormTravel.STORM_BACK);
    }

    static final BossRoom.Host HOST = new BossRoom.Host() {
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return playersIn(level, StormHeartArena.arena());
        }

        @Override
        public void seal(ServerLevel level) {
            StormHeartArena.seal(level);
            level.playSound(null, BlockPos.containing(StormHeartArena.center()), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
        }

        @Override
        public void open(ServerLevel level) {
            StormHeartArena.open(level);
        }

        @Override
        public void pullIn(ServerLevel level) {
            AABB ante = StormHeartArena.ante().inflate(0, 1, 0).expandTowards(0, 0, 1);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, ante, p -> p.isAlive() && !p.isSpectator())) {
                moveInside(level, player);
            }
        }

        @Override
        public void letBack(ServerLevel level, Set<UUID> participants) {
            // 戸口 (封印の手前 1 マス) に触れた参加者を中へ通す
            AABB door = new AABB(StormHeartArena.CX - 1, StormHeartArena.FLOOR, StormHeartArena.GATE_Z - 2,
                    StormHeartArena.CX + 2, StormHeartArena.FLOOR + 3, StormHeartArena.GATE_Z);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, door,
                    p -> p.isAlive() && !p.isSpectator() && participants.contains(p.getUUID()))) {
                moveInside(level, player);
                Notice.bar(player, ModText.battle("message.skyabyss.gate_rejoin"));
            }
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, List<ServerPlayer> challengers) {
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
            level.addFreshEntity(heart);
            StormHeartArena.resetRods(level);
            level.playSound(null, c.x, y, c.z, StormHeartSetup.ROAR.get(), SoundSource.HOSTILE, 2.5f, 1.1f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, y + 2, c.z, 120, 2.0, 1.5, 2.0, 0.3);
            broadcast(level, ModText.battle("message.skyabyss.storm_heart.begin"));
            SkyAbyss.LOGGER.info("The heart of the thunder wing woke with {} challenger(s)", challengers.size());
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
        public void broadcast(ServerLevel level, Component message) {
            StormHeartBattle.broadcast(level, message);
        }

        @Override
        public void countdown(ServerLevel level, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            for (ServerPlayer player : playersIn(level, StormHeartArena.arena().inflate(4, 2, 10))) {
                Notice.bar(player, text);
            }
        }

        @Override
        public void cleanup(ServerLevel level) {
            for (Entity e : level.getEntitiesOfClass(Entity.class, StormHeartArena.arena().inflate(4),
                    e -> e instanceof StormHeart && e.getTags().contains(TitanBackTrials.HEART_TAG))) {
                e.discard();
            }
            StormHeartArena.resetRods(level);
            StormHeartNetwork.warn(playersIn(level, StormHeartArena.arena().inflate(4, 2, 10)), null, 0);
        }

        @Override
        public boolean asleep(ServerLevel level) {
            return level.getDifficulty() == Difficulty.PEACEFUL;
        }
    };

    // home() が心臓を探す level (tick の間だけ)
    @Nullable
    private static ServerLevel currentLevel;

    static List<ServerPlayer> playersIn(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator());
    }

    static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : playersIn(level, StormHeartArena.arena().inflate(4, 4, 12))) {
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

    static void tick(ServerLevel level) {
        currentLevel = level;
        try {
            ROOM.tick(level, HOST);
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
        if (level.getGameTime() % 100 == 0 && !ROOM.active()) {
            StormHeartArena.ensureBuilt(level);
        }
        TitanBackData data = TitanBackData.get(level);
        if (data.isHeartDefeated()) {
            tickAfterDefeat(level, data);
            return;
        }
        if (HOST.asleep(level) && !playersIn(level, StormHeartArena.arena()).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            broadcast(level, ModText.deny("message.skyabyss.storm_heart.asleep"));
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

    private static void tickAfterDefeat(ServerLevel level, TitanBackData data) {
        Vec3 c = StormHeartArena.center();
        level.sendParticles(ParticleTypes.END_ROD, c.x, StormHeartArena.FLOOR + (level.getGameTime() % 12), c.z, 3, 0.25, 0.2, 0.25, 0.02);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, StormHeartArena.FLOOR + 1, c.z, 2, 0.4, 0.3, 0.4, 0.02);
        boolean anyone = false;
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (StormHeartArena.arena().contains(player.position()) || StormHeartArena.ante().contains(player.position())) {
                anyone = true;
            }
            if (Math.abs(player.getX() - c.x) < 1.2 && Math.abs(player.getZ() - c.z) < 1.2
                    && player.getY() >= StormHeartArena.FLOOR - 1.5 && player.getY() < StormHeartArena.CEILING) {
                Notice.narrate(player, "message.skyabyss.storm_heart.pillar");
                ascend(level, player);
            }
        }
        afterDefeatIdle = anyone ? 0 : afterDefeatIdle + 10;
        if (afterDefeatIdle >= RESET_AFTER_DEFEAT) {
            afterDefeatIdle = 0;
            reawaken(level, data);
        }
    }

    public static void reawaken(ServerLevel level, TitanBackData data) {
        data.resetProgress();
        ROOM.reset(level, HOST, false);
        SkyAbyss.LOGGER.info("The heart of the thunder wing sleeps again (chamber reset)");
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。部屋の中の人を挑む人とする
    public static boolean startNow(ServerLevel level) {
        StormHeartArena.ensureBuilt(level);
        TitanBackData data = TitanBackData.get(level);
        if (data.isHeartDefeated()) {
            reawaken(level, data);
        }
        if (ROOM.active()) {
            return false;
        }
        return ROOM.start(level, HOST, playersIn(level, StormHeartArena.arena()));
    }

    public static void abandon(ServerLevel level) {
        ROOM.reset(level, HOST, true);
    }

    @Nullable
    public static StormHeart current(@Nullable ServerLevel level) {
        if (level == null) {
            return null;
        }
        UUID id = ROOM.bossId();
        return id != null && level.getEntity(id) instanceof StormHeart h ? h : null;
    }

    // 心臓が倒れた (StormHeart.die から)。いま戦っている心臓なら、報酬・進み・記録を渡し、入口と光の柱を開く
    public static void onDefeated(ServerLevel level, StormHeart heart) {
        Set<UUID> ids = ROOM.finish(level, HOST, heart.getUUID());
        if (ids == null) {
            return;
        }
        TitanBackData data = TitanBackData.get(level);
        data.markHeartDefeated();
        afterDefeatIdle = 0;
        List<ServerPlayer> participants = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                participants.add(p);
            }
        }
        StormLoot.onHeartDefeat(level, participants, ids);
        for (ServerPlayer player : level.players()) {
            Notice.unlock(player, "message.skyabyss.storm_heart.defeated");
        }
        Vec3 c = StormHeartArena.center();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.9f);
        SkyAbyss.LOGGER.info("The heart of the thunder wing was defeated by {} participant(s)", ids.size());
        jp.revati.skyabyss.titan.end.TitanEnd.onHeartStopped(level, jp.revati.skyabyss.progress.Titans.THUNDER_WING, ids);
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
        ROOM.clear();
        StormHeartArena.resetState();
        afterDefeatIdle = 0;
    }
}
