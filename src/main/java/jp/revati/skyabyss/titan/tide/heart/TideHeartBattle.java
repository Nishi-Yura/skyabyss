package jp.revati.skyabyss.titan.tide.heart;

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
import jp.revati.skyabyss.titan.tide.TideBackBuilder;
import jp.revati.skyabyss.titan.tide.TideTravel;
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

// 殻の底 (心臓の間) の戦い。岩鯨の心臓の間と同じ決まり (BossRoom):
// 5 秒の集合 → 入口が閉じて心臓が現れる → 全員が倒れるか部屋から消えて 3 秒で放棄 (心臓を消して入口を開ける)。
// 心臓が部屋の外へ出たら中央へ戻す。撃破で入口が開き、中央に光の柱 (立つと島へ戻る)。
// 撃破のあと、潮の底の門より奥に 30 秒だれもいないと、背中の試練ごと次の周へ戻る (TideBackTrials のリセット。08 の 7.1節)。
// 出入りは、潮の広間の潮の底の門の下の螺旋の階段 (本番の島)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideHeartBattle {
    public static final int RESET_AFTER_DEFEAT = 20 * 30;
    private static final BossRoom ROOM = new BossRoom();
    private static int afterDefeatIdle;
    private static long lastAsleepNotice;

    private TideHeartBattle() {
    }

    public static BossRoom room() {
        return ROOM;
    }

    // 部屋ごとの仕掛け
    static final BossRoom.Host HOST = new BossRoom.Host() {
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return playersIn(level, TideHeartArena.arena());
        }

        @Override
        public void seal(ServerLevel level) {
            TideHeartArena.seal(level);
            level.playSound(null, BlockPos.containing(TideHeartArena.heartHome()), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
        }

        @Override
        public void open(ServerLevel level) {
            TideHeartArena.open(level);
        }

        @Override
        public void pullIn(ServerLevel level) {
            // 前室と戸口にいる人を、入口の内側 2 マスへ
            AABB ante = TideHeartArena.ante().inflate(0, 1, 0).expandTowards(0, 0, -1);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, ante, p -> p.isAlive() && !p.isSpectator())) {
                moveInside(level, player);
            }
        }

        @Override
        public void letBack(ServerLevel level, Set<UUID> participants) {
            // 戸口 (封印の手前 1 マス) に触れた参加者を中へ通す
            AABB door = new AABB(TideHeartArena.CX - 1, TideHeartArena.FLOOR, TideHeartArena.CZ + TideHeartArena.GATE_Z + 1,
                    TideHeartArena.CX + 2, TideHeartArena.FLOOR + 3, TideHeartArena.CZ + TideHeartArena.GATE_Z + 2);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, door,
                    p -> p.isAlive() && !p.isSpectator() && participants.contains(p.getUUID()))) {
                moveInside(level, player);
                player.displayClientMessage(ModText.battle("message.skyabyss.gate_rejoin"), true);
            }
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, List<ServerPlayer> challengers) {
            TideHeart heart = TideHeartSetup.TIDE_HEART.get().create(level);
            if (heart == null) {
                return null;
            }
            Vec3 home = TideHeartArena.heartHome();
            // 入口 (南) を向いて現れる
            heart.moveTo(home.x, home.y, home.z, 0f, 0f);
            heart.addTag(TitanBackTrials.HEART_TAG);
            heart.bindArena(TideHeartArena.arena(), challengers.size());
            level.addFreshEntity(heart);
            level.playSound(null, home.x, home.y, home.z, TideHeartSetup.ROAR.get(), SoundSource.HOSTILE, 2.5f, 1.1f);
            level.sendParticles(ParticleTypes.SPLASH, home.x, home.y + 2, home.z, 120, 2.0, 1.5, 2.0, 0.3);
            broadcast(level, ModText.battle("message.skyabyss.tide_heart.begin"));
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
        public void broadcast(ServerLevel level, Component message) {
            TideHeartBattle.broadcast(level, message);
        }

        @Override
        public void countdown(ServerLevel level, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            for (ServerPlayer player : playersIn(level, TideHeartArena.arena().inflate(4, 2, 10))) {
                player.displayClientMessage(text, true);
            }
        }

        @Override
        public void cleanup(ServerLevel level) {
            TideHeartArena.drainNow(level);
            for (Entity e : level.getEntitiesOfClass(Entity.class, TideHeartArena.arena().inflate(4),
                    e -> e instanceof TideBubble || e instanceof TidePearl || e.getTags().contains(TitanBackTrials.HEART_TAG))) {
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
        for (ServerPlayer player : playersIn(level, TideHeartArena.arena().inflate(4, 4, 12))) {
            player.sendSystemMessage(message);
        }
    }

    private static void moveInside(ServerLevel level, ServerPlayer player) {
        player.fallDistance = 0;
        double x = Math.max(TideHeartArena.CX - 0.5, Math.min(TideHeartArena.CX + 1.5, player.getX()));
        player.teleportTo(level, x, TideHeartArena.FLOOR, TideHeartArena.CZ + TideHeartArena.GATE_Z - 2 + 0.5, 180f, 0f);
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
        if (level.getGameTime() % 100 == 0 && !ROOM.active()) {
            TideHeartArena.ensureBuilt(level);
        }
        TitanBackData data = TitanBackData.get(level);
        if (data.isHeartDefeated()) {
            tickAfterDefeat(level, data);
            return;
        }
        if (HOST.asleep(level) && !playersIn(level, TideHeartArena.arena()).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            broadcast(level, ModText.deny("message.skyabyss.tide_heart.asleep"));
        }
        ROOM.tick(level, HOST);
    }

    // 潮の底へ降りる (前室へ)
    public static void descend(ServerLevel level, ServerPlayer player) {
        TideHeartArena.ensureBuilt(level);
        Vec3 at = TideHeartArena.anteSpawn();
        player.fallDistance = 0;
        player.teleportTo(level, at.x, at.y, at.z, 180f, 0f);
        level.playSound(null, at.x, at.y, at.z, jp.revati.skyabyss.titan.tide.TideSetup.GATE.get(), SoundSource.PLAYERS, 1.2f, 0.8f);
        player.sendSystemMessage(ModText.lore("message.skyabyss.tide_heart.descend"));
        player.displayClientMessage(ModText.hint("message.skyabyss.tide_heart.exit_hint"), true);
    }

    // 島の到着地点へ戻る
    public static void ascend(ServerLevel level, ServerPlayer player) {
        BlockPos a = TideBackBuilder.arrival();
        player.fallDistance = 0;
        player.teleportTo(level, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 180f, 0f);
        level.playSound(null, a, jp.revati.skyabyss.titan.tide.TideSetup.GATE.get(), SoundSource.PLAYERS, 1.2f, 1.2f);
    }

    // 撃破のあと: 中央の光の柱 (立つと島へ)。部屋と前室に 30 秒だれもいなければ、心臓が戻る
    private static void tickAfterDefeat(ServerLevel level, TitanBackData data) {
        Vec3 c = TideHeartArena.heartHome();
        level.sendParticles(ParticleTypes.END_ROD, c.x, TideHeartArena.FLOOR + (level.getGameTime() % 12), c.z, 3, 0.25, 0.2, 0.25, 0.02);
        level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, TideHeartArena.FLOOR + 1, c.z, 2, 0.4, 0.3, 0.4, 0.02);
        boolean anyone = false;
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (TideHeartArena.arena().contains(player.position()) || TideHeartArena.ante().contains(player.position())) {
                anyone = true;
            }
            if (Math.abs(player.getX() - c.x) < 1.2 && Math.abs(player.getZ() - c.z) < 1.2
                    && player.getY() >= TideHeartArena.FLOOR - 0.5 && player.getY() < TideHeartArena.CEILING) {
                ascend(level, player);
                player.sendSystemMessage(ModText.lore("message.skyabyss.tide_heart.pillar"));
            }
        }
        afterDefeatIdle = anyone ? 0 : afterDefeatIdle + 10;
    }

    // 心臓をもう一度挑めるようにする (背中の進みを戻す)
    public static void reawaken(ServerLevel level, TitanBackData data) {
        data.resetProgress();
        ROOM.reset(level, HOST, false);
        SkyAbyss.LOGGER.info("The heart of the tide shell sleeps again (chamber reset)");
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。部屋の中の人を挑む人とする
    public static boolean startNow(ServerLevel level) {
        TideHeartArena.ensureBuilt(level);
        TitanBackData data = TitanBackData.get(level);
        if (data.isHeartDefeated()) {
            reawaken(level, data);
        }
        if (ROOM.active()) {
            return false;
        }
        return ROOM.start(level, HOST, playersIn(level, TideHeartArena.arena()));
    }

    public static void abandon(ServerLevel level) {
        ROOM.reset(level, HOST, true);
    }

    @Nullable
    public static TideHeart current(ServerLevel level) {
        UUID id = ROOM.bossId();
        return id != null && level.getEntity(id) instanceof TideHeart h ? h : null;
    }

    // 心臓が倒れた (TideHeart.die から)。いま戦っている心臓なら、報酬・進み・記録を渡し、入口と光の柱を開く
    public static void onDefeated(ServerLevel level, TideHeart heart) {
        Set<UUID> ids = ROOM.finish(level, HOST, heart.getUUID());
        if (ids == null) {
            return;
        }
        TitanBackData data = TitanBackData.get(level);
        data.markHeartDefeated();
        // 2 周目から試練が一つ眠る (ワールドで一度でも心臓が倒されたあと)
        jp.revati.skyabyss.titan.tide.back.TideBackState.get(level).markEverDefeated();
        afterDefeatIdle = 0;
        List<ServerPlayer> participants = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                participants.add(p);
            }
        }
        TideLoot.onHeartDefeat(level, participants, ids);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(ModText.progress("message.skyabyss.tide_heart.defeated"));
        }
        Vec3 c = TideHeartArena.heartHome();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.9f);
        SkyAbyss.LOGGER.info("The heart of the tide shell was defeated by {} participant(s)", ids.size());
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
        ROOM.clear();
        TideHeartArena.resetState();
        afterDefeatIdle = 0;
    }
}
