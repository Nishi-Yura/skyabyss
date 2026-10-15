package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 岩鯨の背の試練の進行。みんなで何度でも挑めるようにする仕組みをまとめている。
//  - 休憩地点: 休み場に着くと記録され、谷に落ちたり倒れたりすると、そこからやり直せる
//  - 倒れても持ち物と経験値は失わない (巨神の背の上でだけ)。地上のリスポーン地点はそのまま
//  - 番兵の広間: 入ると入口が閉じて二度と開かない (一方通行)。人数に応じた番兵の波を倒しきると出口が開く
//  - 休み場の焚き火にしゃがんで触れると、試練をあきらめて背の入口 (尾の台) へ戻れる
//  - 天の鍵の欠片: 宝物庫の台座から一人一つ。宝箱は試練を越えるたびに満たされる
//  - リセット: 背中に誰もいなくなって3分たつと、石・封印・広間・宝箱が元に戻り、アスレチックと迷路は新しい形になる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanBackTrials {
    private static final String TAG_CHECKPOINT = "skyabyss_checkpoint";
    private static final String TAG_DIED_ON_BACK = "skyabyss_died_on_back";
    public static final String MOB_TAG = "skyabyss_guard";
    private static final int RESET_AFTER_IDLE = 20 * 60 * 3;
    private static final int WAVE_DELAY = 60;

    // ---------------------------------------------------------------- 休憩地点
    private record Checkpoint(String id, Box area, BlockPos spawn) {
    }

    private static final List<Checkpoint> CHECKPOINTS = List.of(
            new Checkpoint("hub", HUB, HUB.center()),
            new Checkpoint("w0", W0, W0.center().offset(0, 0, 2)),
            new Checkpoint("w1", W1, W1.center().offset(0, 0, 2)),
            new Checkpoint("w2", W2, W2.center().offset(2, 0, 0)),
            new Checkpoint("e0", E0, E0.center().offset(0, 0, 2)),
            new Checkpoint("ce1", CE1, CE1.center().offset(2, 0, 0)),
            new Checkpoint("ce2", CE2, CE2.center().offset(2, 0, 0)));
    private static final List<String> WEST_CHECKPOINTS = List.of("w0", "w1", "w2");

    // 倒れた人の持ち物と経験値を、生き返るまで預かっておく
    private record Belongings(ListTag inventory, int level, float progress, int total) {
    }

    private static final Map<UUID, Belongings> KEPT = new HashMap<>();

    // ---------------------------------------------------------------- 番兵の広間
    private record Hall(String id, Box box, List<BlockPos> entrance, List<BlockPos> exit, List<List<String>> waves) {
    }

    private static final List<Hall> HALLS = List.of(
            new Hall("e1", E1, E1_ENTRANCE, E1_EXIT, List.of(
                    List.of("sentinel", "sentinel", "crawler", "crawler"),
                    List.of("archer", "archer", "sentinel", "crawler"))),
            new Hall("e2", E2, E2_ENTRANCE, E2_EXIT, List.of(
                    List.of("archer", "archer", "archer", "sentinel"),
                    List.of("sentinel", "sentinel", "crawler", "crawler", "archer"))),
            new Hall("e3", E3, E3_ENTRANCE, List.of(), List.of(
                    List.of("sentinel", "sentinel", "archer", "crawler", "crawler"),
                    List.of("captain"))));

    private static final class HallState {
        int wave;
        int delay;
        int players;
        // 広間が空に見えてからの時間 (一瞬の取りこぼしでは戻さない)
        int emptyTicks;
    }

    private static final Map<String, HallState> HALL_STATES = new HashMap<>();
    private static boolean cleanedAfterLoad;
    private static int idleTicks;

    private TitanBackTrials() {
    }

    // 倒れても消えないよう、PlayerPersisted (死んでも引き継がれる領域) に書く
    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ---------------------------------------------------------------- 起動時: 背中がまだなければ建てる
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") != null) {
            return;
        }
        ServerLevel back = event.getServer().getLevel(TitanTravel.TITAN_BACK);
        if (back != null) {
            TitanBackData data = TitanBackData.get(back);
            if (!data.isBuilt()) {
                SkyAbyss.LOGGER.info("Building the stone whale's back for this world...");
                TitanBackBuilder.build(back, data);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        HALL_STATES.clear();
        KEPT.clear();
        cleanedAfterLoad = false;
        idleTicks = 0;
    }

    // ---------------------------------------------------------------- 毎tick: 広間の進行とリセット
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TitanTravel.TITAN_BACK || level.getGameTime() % 10 != 0) {
            return;
        }
        TitanBackData data = TitanBackData.get(level);
        if (!data.isBuilt()) {
            return;
        }
        if (!cleanedAfterLoad) {
            // 前回、戦いの途中でサーバーが止まっていたら、その名残を片付ける
            cleanedAfterLoad = true;
            for (Hall hall : HALLS) {
                if (!data.isHallCleared(hall.id())) {
                    resetHall(level, hall, false);
                }
            }
        }
        for (Hall hall : HALLS) {
            if (!data.isHallCleared(hall.id())) {
                tickHall(level, data, hall);
            }
        }
        // 誰もいなくなってしばらくたったら、試練を元に戻す
        if (level.players().isEmpty()) {
            idleTicks += 10;
            if (idleTicks >= RESET_AFTER_IDLE && (data.hasProgress() || anyStoneActive(level))) {
                resetTrials(level);
            }
        } else {
            idleTicks = 0;
        }
    }

    private static void tickHall(ServerLevel level, TitanBackData data, Hall hall) {
        HallState state = HALL_STATES.computeIfAbsent(hall.id(), k -> new HallState());
        List<ServerPlayer> inside = playersInside(level, hall);
        if (state.wave == 0) {
            if (!inside.isEmpty()) {
                state.wave = 1;
                state.players = inside.size();
                seal(level, hall.entrance());
                level.playSound(null, hall.entrance().get(0), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
                broadcast(level, hall.box(), ModText.battle("message.skyabyss.hall_begin"));
                spawnWave(level, hall, state);
            }
            return;
        }
        // 戦いが始まったら、壁ぎわや戸口に立っていても「中にいる」と数える。
        // 全員が倒れて (休み場へ戻されて) 3秒たったときだけ、広間を最初に戻す
        if (playersInHall(level, hall).isEmpty()) {
            state.emptyTicks += 10;
            if (state.emptyTicks >= 60) {
                resetHall(level, hall, true);
            }
            return;
        }
        state.emptyTicks = 0;
        if (aliveGuards(level, hall) > 0) {
            return;
        }
        if (state.delay == 0) {
            state.delay = WAVE_DELAY;
            if (state.wave < hall.waves().size()) {
                broadcast(level, hall.box(), ModText.battle("message.skyabyss.arena_next"));
            }
            return;
        }
        state.delay -= 10;
        if (state.delay > 0) {
            return;
        }
        state.delay = 0;
        if (state.wave >= hall.waves().size()) {
            // 制圧: 出口だけが開く。入口は閉じたまま (戻れない)
            data.markHallCleared(hall.id());
            HALL_STATES.remove(hall.id());
            open(level, hall.exit());
            level.playSound(null, hall.box().center(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2f, 0.8f);
            broadcast(level, hall.box(), ModText.progress(hall.exit().isEmpty() ? "message.skyabyss.arena_cleared" : "message.skyabyss.hall_cleared"));
        } else {
            state.wave++;
            spawnWave(level, hall, state);
        }
    }

    // 中にいた全員が倒れたので、番兵を眠らせて最初に戻す (入口が開き、休み場からやり直せる)。出口は閉じたまま
    private static void resetHall(ServerLevel level, Hall hall, boolean announce) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, hall.box().aabb().inflate(4), e -> e.getTags().contains(MOB_TAG))) {
            e.discard();
        }
        HALL_STATES.remove(hall.id());
        open(level, hall.entrance());
        seal(level, hall.exit());
        if (announce) {
            broadcast(level, hall.box(), ModText.deny("message.skyabyss.arena_reset"));
        }
    }

    private static int aliveGuards(ServerLevel level, Hall hall) {
        return level.getEntitiesOfClass(Mob.class, hall.box().aabb().inflate(3), e -> e.isAlive() && e.getTags().contains(MOB_TAG)).size();
    }

    private static void spawnWave(ServerLevel level, Hall hall, HallState state) {
        List<BlockPos> spawns = EastWingBuilder.spawnPoints(hall.box());
        List<String> wave = new ArrayList<>(hall.waves().get(state.wave - 1));
        // 人数が多いほど番兵も増える (1人増えるごとに2体。番兵長の波では骨の番兵が増える。最大4人分)
        for (int extra = 1; extra < Math.min(state.players, 4); extra++) {
            if (wave.get(0).equals("captain")) {
                wave.add("sentinel");
                wave.add("archer");
            } else {
                wave.add(wave.get(0));
                wave.add(wave.get(1));
            }
        }
        for (int i = 0; i < wave.size(); i++) {
            BlockPos at = wave.get(i).equals("captain") ? hall.box().center() : spawns.get(i % spawns.size());
            spawnGuard(level, wave.get(i), at);
        }
        broadcast(level, hall.box(), ModText.battle("message.skyabyss.arena_wave", state.wave, hall.waves().size()));
    }

    @Nullable
    public static Mob spawnGuard(ServerLevel level, String kind, BlockPos pos) {
        EntityType<? extends Mob> type = switch (kind) {
            case "archer" -> ModEntities.RIB_ARCHER.get();
            case "crawler" -> ModEntities.MOSS_CRAWLER.get();
            case "captain" -> ModEntities.WHALEBONE_CAPTAIN.get();
            default -> ModEntities.BONE_SENTINEL.get();
        };
        Mob mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        mob.addTag(MOB_TAG);
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.4, 0.8, 0.4, 0.02);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
        return mob;
    }

    // 封印された広間の中にいる人 (壁ぎわも含む)
    private static List<ServerPlayer> playersInHall(ServerLevel level, Hall hall) {
        return level.getEntitiesOfClass(ServerPlayer.class, hall.box().aabb().inflate(0, 1, 0), p -> p.isAlive() && !p.isSpectator());
    }

    private static List<ServerPlayer> playersInside(ServerLevel level, Hall hall) {
        // 戸口に立っているだけでは始まらないよう、内側に1マス入ったところから数える
        AABB area = hall.box().aabb().deflate(1, 0, 1);
        return level.getEntitiesOfClass(ServerPlayer.class, area, p -> p.isAlive() && !p.isSpectator());
    }

    // ---------------------------------------------------------------- リセット
    public static void resetTrials(ServerLevel level) {
        TitanBackData data = TitanBackData.get(level);
        for (BlockPos p : RESONANCE_STONES) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ResonanceStoneBlock) {
                level.setBlock(p, s.setValue(ResonanceStoneBlock.ACTIVE, false), 3);
            }
        }
        for (List<BlockPos> gate : List.of(GATE, WEST_RETURN, EAST_RETURN, LOWER_RETURN)) {
            seal(level, gate);
        }
        for (Hall hall : HALLS) {
            resetHall(level, hall, false);
        }
        // アスレチックと迷路を、新しい種で作り直す
        BuildKit kit = new BuildKit(level, RandomSource.create(level.getRandom().nextLong()));
        WestWingBuilder.clearCourses(kit, ParkourCourses.forSeed(data.courseSeed()));
        data.reseed(level.getRandom());
        WestWingBuilder.placeCourses(kit, ParkourCourses.forSeed(data.courseSeed()));
        MazeBuilder.buildMaze(new BuildKit(level, RandomSource.create(data.mazeSeed())), data.mazeSeed());
        TitanBackBuilder.fillChests(level, level.random);
        data.resetProgress();
        idleTicks = 0;
        SkyAbyss.LOGGER.info("The stone whale's trials have been reset");
    }

    private static boolean anyStoneActive(ServerLevel level) {
        for (BlockPos p : RESONANCE_STONES) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ResonanceStoneBlock && s.getValue(ResonanceStoneBlock.ACTIVE)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHallCleared(ServerLevel level, String id) {
        return TitanBackData.get(level).isHallCleared(id);
    }

    static void seal(ServerLevel level, List<BlockPos> blocks) {
        for (BlockPos p : blocks) {
            level.setBlock(p, ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
        }
    }

    static void open(ServerLevel level, List<BlockPos> blocks) {
        for (BlockPos p : blocks) {
            if (level.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    // ---------------------------------------------------------------- 休憩地点・落下
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TitanTravel.TITAN_BACK) {
            return;
        }
        BlockPos pos = player.blockPosition();
        // 谷に落ちたら、直前の休み場へ戻す (闇の水路は谷の真下を通るので、谷の底より上だけを見る)
        if (player.getY() < FALL_LINE && player.getY() > FALL_LINE - 6) {
            for (Box pit : PITS) {
                if (pos.getX() >= pit.x1() - 1 && pos.getX() <= pit.x2() + 1 && pos.getZ() >= pit.z1() - 1 && pos.getZ() <= pit.z2() + 1) {
                    String cp = persisted(player).getString(TAG_CHECKPOINT);
                    returnTo(player, WEST_CHECKPOINTS.contains(cp) ? cp : "w0");
                    player.hurt(player.damageSources().fall(), 3f);
                    player.displayClientMessage(ModText.deny("message.skyabyss.fell_pit"), true);
                    return;
                }
            }
        }
        if (player.tickCount % 5 != 0) {
            return;
        }
        for (Checkpoint cp : CHECKPOINTS) {
            if (cp.area().contains(pos) && pos.getY() > cp.area().floorY() && pos.getY() < cp.area().ceilingY()) {
                CompoundTag tag = persisted(player);
                if (!cp.id().equals(tag.getString(TAG_CHECKPOINT))) {
                    tag.putString(TAG_CHECKPOINT, cp.id());
                    if (!cp.id().equals("hub")) {
                        player.displayClientMessage(ModText.progress("message.skyabyss.checkpoint",
                                Component.translatable("checkpoint.skyabyss." + cp.id())), true);
                        player.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
                    }
                }
                return;
            }
        }
    }

    private static BlockPos checkpointSpawn(String id) {
        for (Checkpoint cp : CHECKPOINTS) {
            if (cp.id().equals(id)) {
                return cp.spawn();
            }
        }
        return arrival();
    }

    private static void returnTo(ServerPlayer player, String id) {
        BlockPos spawn = checkpointSpawn(id);
        player.fallDistance = 0;
        player.teleportTo(player.serverLevel(), spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
    }

    // ---------------------------------------------------------------- 倒れたとき: 持ち物を預かり、最後の休み場で目を覚ます
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() == TitanTravel.TITAN_BACK && !event.isCanceled()) {
            persisted(player).putBoolean(TAG_DIED_ON_BACK, true);
            KEPT.put(player.getUUID(), new Belongings(player.getInventory().save(new ListTag()),
                    player.experienceLevel, player.experienceProgress, player.totalExperience));
        }
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && KEPT.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && KEPT.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        Belongings kept = KEPT.remove(event.getOriginal().getUUID());
        if (kept != null) {
            Player player = event.getEntity();
            player.getInventory().load(kept.inventory());
            player.experienceLevel = kept.level();
            player.experienceProgress = kept.progress();
            player.totalExperience = kept.total();
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }
        CompoundTag tag = persisted(player);
        if (!tag.getBoolean(TAG_DIED_ON_BACK)) {
            return;
        }
        tag.remove(TAG_DIED_ON_BACK);
        ServerLevel back = player.server.getLevel(TitanTravel.TITAN_BACK);
        if (back == null) {
            return;
        }
        // 地上のリスポーン地点 (ベッドなど) は変えず、背中の休み場へ連れ戻すだけ
        BlockPos spawn = checkpointSpawn(tag.getString(TAG_CHECKPOINT));
        player.teleportTo(back, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
        player.sendSystemMessage(ModText.lore("message.skyabyss.revived_on_back"));
    }

    // ---------------------------------------------------------------- 台座と焚き火
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != TitanTravel.TITAN_BACK) {
            return;
        }
        BlockPos pos = event.getPos();
        if (pos.equals(PEDESTAL)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                claimFragment(player);
            }
            return;
        }
        // 休み場の焚き火: しゃがんで触れると、試練をあきらめて尾の台へ戻る
        if (event.getLevel().getBlockState(pos).is(Blocks.SOUL_CAMPFIRE)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                if (player.isShiftKeyDown()) {
                    BlockPos a = arrival();
                    player.teleportTo(player.serverLevel(), a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 180f, 0f);
                    player.sendSystemMessage(ModText.lore("message.skyabyss.gave_up"));
                } else {
                    player.displayClientMessage(ModText.hint("message.skyabyss.campfire_hint"), true);
                }
            }
        }
    }

    private static void claimFragment(ServerPlayer player) {
        TitanBackData data = TitanBackData.get(player.serverLevel());
        if (data.claimFragment(player.getUUID())) {
            ItemStack fragment = TitanBackBuilder.fragment();
            if (!player.getInventory().add(fragment)) {
                player.drop(fragment, false);
            }
            player.level().playSound(null, PEDESTAL, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2f, 0.8f);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, PEDESTAL.getX() + 0.5, PEDESTAL.getY() + 1.2, PEDESTAL.getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.04);
            player.sendSystemMessage(ModText.progress("message.skyabyss.fragment_received"));
        } else {
            player.displayClientMessage(ModText.hint("message.skyabyss.fragment_already"), true);
        }
    }

    private static void broadcast(ServerLevel level, Box area, Component message) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area.aabb().inflate(20))) {
            player.sendSystemMessage(message);
        }
    }
}
