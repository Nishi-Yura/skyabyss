package jp.revati.skyabyss.titan.ember.smith;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.BossRoom;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.back.EmberBackTrials;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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

// 鍛え手の間の戦い (13 の 5.2節・7.5節)。岩鯨の番兵長・潮殻の心臓・雷獣と同じ決まり (BossRoom):
// 鍛え手の間に人が入ると 5 秒の集合 → 入口 (SMITH_DOOR) が閉じて、鍛え手が奥から起き上がる → 全員が倒れるか部屋から消えて 3 秒で放棄。
// 撃破で入口が開き、報酬 (AshSmithLoot)、東の熾り石が鳴らせる (EmberBackTrials.markReady EAST)。
// この周に鍛え手を倒した・東の試練が眠っている (EmberBackState.ready(EAST)) あいだは、部屋に入っても戦いは始まらない。
// 戦いの状態はメモリにだけある (試練のリセット・サーバー停止で戻る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AshSmithBattle {
    private static final BossRoom ROOM = new BossRoom();
    private static long lastAsleepNotice;

    private AshSmithBattle() {
    }

    public static BossRoom room() {
        return ROOM;
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(EmberIds.BACK);
    }

    // この周、鍛え手を倒したか (または東の試練が眠っているか)。東の熾り石が鳴らせる
    public static boolean done(ServerLevel level) {
        return EmberBackState.get(level).ready(EmberBackState.Trial.EAST);
    }

    static final class Host implements BossRoom.Host {
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return level.getEntitiesOfClass(ServerPlayer.class, AshSmithArena.BOX.aabb(),
                    p -> p.isAlive() && !p.isSpectator() && AshSmithArena.inside(p.position()));
        }

        @Override
        public void seal(ServerLevel level) {
            AshSmithArena.seal(level);
            level.playSound(null, AshSmithArena.DOOR.center(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
        }

        @Override
        public void open(ServerLevel level) {
            AshSmithArena.open(level);
        }

        @Override
        public void pullIn(ServerLevel level) {
            AABB box = AshSmithArena.ANTE.aabb().minmax(AshSmithArena.DOOR.aabb());
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator())) {
                moveInside(level, p);
            }
        }

        @Override
        public void letBack(ServerLevel level, Set<UUID> participants) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, AshSmithArena.DOOR.aabb().inflate(0.5, 0, 0.5),
                    p -> p.isAlive() && !p.isSpectator() && participants.contains(p.getUUID()))) {
                moveInside(level, p);
                p.displayClientMessage(ModText.battle("message.skyabyss.gate_rejoin"), true);
            }
        }

        private void moveInside(ServerLevel level, ServerPlayer p) {
            Vec3 at = AshSmithArena.ENTRANCE_INSIDE;
            p.fallDistance = 0;
            p.teleportTo(level, at.x, at.y, at.z, 0f, 0f);
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, List<ServerPlayer> challengers) {
            AshSmith smith = EmberSmithSetup.ASH_SMITH.get().create(level);
            if (smith == null) {
                return null;
            }
            Vec3 at = AshSmithArena.HOME.add(0, 0, 4);
            smith.moveTo(at.x, at.y, at.z, 180f, 0f);
            smith.bind(challengers.size());
            if (!level.addFreshEntity(smith)) {
                SkyAbyss.LOGGER.warn("The ash smith could not be added to {} at {}", level.dimension().location(), smith.position());
                return null;
            }
            level.playSound(null, at.x, at.y, at.z, EmberSmithSetup.RAGE.get(), SoundSource.HOSTILE, 3f, 0.7f);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.5, at.z, 40, 1.0, 1.2, 1.0, 0.05);
            broadcast(level, ModText.battle("message.skyabyss.ember.smith.begin"));
            broadcast(level, ModText.hint("message.skyabyss.ember.smith.hint_back"));
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
        public void broadcast(ServerLevel level, Component message) {
            AshSmithBattle.broadcast(level, message);
        }

        @Override
        public void countdown(ServerLevel level, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            AABB box = AshSmithArena.BOX.aabb().minmax(AshSmithArena.ANTE.aabb());
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, Entity::isAlive)) {
                p.displayClientMessage(text, true);
            }
        }

        @Override
        public void cleanup(ServerLevel level) {
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

    static void broadcast(ServerLevel level, Component message) {
        AABB box = AshSmithArena.BOX.aabb().minmax(AshSmithArena.ANTE.aabb()).inflate(2);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, Entity::isAlive)) {
            p.sendSystemMessage(message);
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
        if (done(level)) {
            return;
        }
        Host host = new Host();
        if (host.asleep(level) && !host.playersInside(level).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            broadcast(level, ModText.deny("message.skyabyss.ember.smith.asleep"));
        }
        ROOM.tick(level, host);
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。部屋の中の人を挑む人とする
    public static boolean startNow(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            return false;
        }
        if (!AshSmithArena.isBuilt(level)) {
            AshSmithArena.build(level, AshSmithArena.BOX);
        }
        if (ROOM.active()) {
            return false;
        }
        Host host = new Host();
        return ROOM.start(level, host, host.playersInside(level));
    }

    public static void abandon(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level != null) {
            ROOM.reset(level, new Host(), true);
        }
    }

    // 試練のリセット (EmberBackRooms.onReset): 戦いを放棄し、入口を開け、炉の扉を閉じる
    public static void reset(ServerLevel level) {
        ROOM.reset(level, new Host(), false);
        if (AshSmithArena.isBuilt(level)) {
            AshSmithArena.buildFurnace(level, false);
        }
    }

    // 戦いを最初に戻す (テスト用。東の熾り石の記録は B の試練のリセットが戻す)
    public static void reawaken(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level != null) {
            reset(level);
        }
    }

    @Nullable
    public static AshSmith current(MinecraftServer server) {
        UUID id = ROOM.bossId();
        ServerLevel level = level(server);
        if (id == null || level == null) {
            return null;
        }
        if (level.getEntity(id) instanceof AshSmith s) {
            return s;
        }
        return level.getEntitiesOfClass(AshSmith.class, AshSmithArena.BOX.aabb().inflate(4), e -> e.getUUID().equals(id)).stream().findFirst().orElse(null);
    }

    // 鍛え手が倒れた (AshSmith.die から)。いま戦っている鍛え手なら、記録・報酬を渡して入口を開ける
    static void onDefeated(ServerLevel level, AshSmith smith) {
        if (level.dimension() != EmberIds.BACK) {
            return;
        }
        Set<UUID> ids = ROOM.finish(level, new Host(), smith.getUUID());
        if (ids == null) {
            return;
        }
        // 呼ばれた灰被りは一緒に崩れる
        for (Entity e : level.getEntitiesOfClass(EmberMob.class, AshSmithArena.BOX.aabb(), e -> e.getTags().contains(AshSmith.SMITH_TAG))) {
            e.discard();
        }
        EmberBackTrials.markReady(level, EmberBackState.Trial.EAST);
        AshSmithLoot.onDefeat(level, ids);
        broadcast(level, ModText.lore("message.skyabyss.ember.smith.after"));
        broadcast(level, ModText.progress("message.skyabyss.ember.smith.east_stone_ready"));
        Vec3 c = AshSmithArena.HOME;
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.8f);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1, c.z, 60, 4, 1, 4, 0.05);
        SkyAbyss.LOGGER.info("The ash smith was defeated by {} participant(s)", ids.size());
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
        ROOM.clear();
    }
}
