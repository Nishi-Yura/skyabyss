package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.StormTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 雷翼の背の試練の進み (12 の 3〜4・7 章)。岩鯨・潮殻と同じ決まりを、雷翼の背の配置で動かす。
//  - 嵐の拍 (StormBackClock) を進め、荒れる・静まるの 10 秒前に島じゅうに音を鳴らす
//  - 三つの試練 (StormWest・StormEast・EyeMirrors) と風鳴り石。三つが鳴ると広間の床の竜骨の門が開く
//  - 休み場 (hub・w0・w1・e0・e1・f0・heart): 着くと記録、倒れるとそこで目を覚ます (記録は試練の番号つき)。焚き火にしゃがんで触れると尾の間へ
//  - 倒れても持ち物と経験値は預かる (岩鯨と同じ部品 TitanBackTrials.keepBelongings)
//  - リセット: 背中に誰もいなくなって 3 分、または心臓を倒したあと竜骨の門より奥に 30 秒だれもいない。2 周目から一つ眠る
//  - ピースフル: 腰の回廊を封じ、体の中の人は到着へ。試練の敵は消える (嵐の拍は景色として動く)
//
// ほかの担当へ: 試練で湧かせた敵には TRIAL_TAG を付けると、リセットとピースフルで消える。雷獣を倒したら onRaijuDefeated を呼ぶ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormBackTrials {
    public static final String TRIAL_TAG = "skyabyss_storm_trial";
    public static final int RESET_AFTER_IDLE = 20 * 60 * 3;
    public static final int RESET_AFTER_HEART = 20 * 30;
    public static final int TRIAL_XP = 60;
    // 雷翼の推奨レベル (12 の 6.6節)
    public static final int STORM_LEVEL = 24;
    private static final String TAG_CHECKPOINT = "skyabyss_storm_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_storm_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_storm_epoch";
    private static final String TAG_DIED = "skyabyss_died_on_storm";

    private static boolean ready;
    private static int idleTicks;
    private static int afterHeartIdle;
    private static boolean wasPeaceful;
    private static StormBackClock.Phase lastPhase;
    private static final Set<UUID> REVIVING = new HashSet<>();

    private StormBackTrials() {
    }

    static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean onStormBack(Entity e) {
        return e.level().dimension() == StormTravel.STORM_BACK;
    }

    // 嵐に投げ返されたときに戻す所 (最後の休み場、なければ到着)
    public static BlockPos fallbackPoint(ServerPlayer player) {
        ServerLevel back = player.server.getLevel(StormTravel.STORM_BACK);
        if (back == null) {
            return ARRIVAL;
        }
        String cp = validCheckpoint(persisted(player), TitanBackData.get(back).epoch());
        return cp.isEmpty() ? ARRIVAL : restSpawn(back, cp);
    }

    // 試練の中にいるか (嵐に投げ返されたときの傷が 3 になる)
    public static boolean inTrial(ServerPlayer player) {
        return StormBackLayout.inTrial(player.position());
    }

    // ---------------------------------------------------------------- 建てたあと・起動のあと
    public static void afterBuild(ServerLevel level) {
        StormBackState state = StormBackState.get(level);
        state.reset(level.random, null, true);
        applyTrials(level, state);
        ready = true;
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != StormTravel.STORM_BACK) {
            return;
        }
        TitanBackData data = TitanBackData.get(level);
        if (!data.isBuilt() || data.needsBuild(LAYOUT_VERSION)) {
            return;
        }
        StormBackState state = StormBackState.get(level);
        if (data.isHeartDefeated() && !state.everDefeated()) {
            state.markDefeated();
        }
        boolean anyone = level.players().stream().anyMatch(p -> !p.isSpectator());
        if (level.getGameTime() % 10 == 0) {
            tickReset(level, data, state, anyone);
        }
        if (!anyone) {
            return;
        }
        if (!ready) {
            applyTrials(level, state);
            ready = true;
        }
        StormBackClock.Phase warn = StormBackClock.advance(level);
        if (warn != null) {
            horn(level, warn);
        }
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        StormWest.tick(level, state, peaceful);
        StormEast.tick(level, state, peaceful);
        long gt = level.getGameTime();
        if (gt % 4 == 0 && level.players().stream().anyMatch(p -> inFront(p.position()))) {
            EyeMirrors.tick(level, state);
        }
        if (gt % 5 == 0) {
            tickKeel(level, state);
            tickPads(level, state);
            tickPeaceful(level, peaceful);
            tickGateFace(level);
        }
        StormBackClock.Phase phase = StormBackClock.phase(level);
        if (phase != lastPhase || gt % 20 == 0) {
            lastPhase = phase;
            sendGauge(level);
        }
    }

    // 荒れる 10 秒前に低い風の唸り、静まる 10 秒前に遠い高い鳴き声 (島じゅう)
    private static void horn(ServerLevel level, StormBackClock.Phase next) {
        boolean rise = next == StormBackClock.Phase.RISING;
        for (ServerPlayer p : level.players()) {
            level.playSound(null, p.getX(), p.getY() + 6, p.getZ(), rise ? StormSetup.RISE_WARN.get() : StormSetup.FALL_WARN.get(),
                    SoundSource.AMBIENT, 1.4f, 1f);
            p.displayClientMessage(ModText.lore(rise ? "message.skyabyss.storm.storm_soon" : "message.skyabyss.storm.calm_soon"), true);
        }
    }

    // 羽根の門の光る面と、下り風の輪の粒
    private static void tickGateFace(ServerLevel level) {
        if (level.getPlayers(p -> p.distanceToSqr(0, GATE_FLOOR, GATE_Z2) < 64 * 64).isEmpty()) {
            return;
        }
        StormFx.forced(level, ParticleTypes.END_ROD, 0.5, GATE_FLOOR + 3, GATE_Z2 + 0.5, 3, 1.6, 1.6, 0.1, 0.0);
        StormFx.forced(level, ParticleTypes.CLOUD, DOWNDRAFT.getX() + 0.5, DOWNDRAFT.getY() + 2, DOWNDRAFT.getZ() + 0.5, 0, 0, -1, 0, 0.3);
        StormFx.ring(level, ParticleTypes.END_ROD, Vec3.atBottomCenterOf(DOWNDRAFT), 1.4, 8);
    }

    // ---------------------------------------------------------------- 竜骨の門
    private static void tickKeel(ServerLevel level, StormBackState state) {
        if (!state.keelOpen() && state.allRung()) {
            state.setKeelOpen(true);
            StormBirdBuilder.setKeelGate(level, true);
            level.playSound(null, HUB_CENTER, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.6f);
            for (ServerPlayer p : level.players()) {
                p.sendSystemMessage(ModText.lore("message.skyabyss.storm.keel_opened"));
            }
        }
        if (state.keelOpen() && level.getGameTime() % 10 == 0) {
            // 下から風が吹き上がる
            StormFx.forced(level, ParticleTypes.CLOUD, 0.5, HUB.floor() + 0.5, -3.5, 0, 0, 1, 0, 0.3);
        }
    }

    // ---------------------------------------------------------------- 抜け道の床 (石が鳴ると光り、踏むと広間の近くへ)
    private static void tickPads(ServerLevel level, StormBackState state) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            BlockPos below = p.blockPosition().below();
            String to = below.equals(RETURN_WEST) ? "west" : below.equals(RETURN_EAST) ? "east" : below.equals(RETURN_FRONT) ? "front" : null;
            if (to == null) {
                continue;
            }
            StormBackState.Trial t = StormBackState.Trial.byId(to);
            if (t != null && state.rung(t)) {
                BlockPos dest = RETURN_TO.get(to);
                p.fallDistance = 0;
                p.teleportTo(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, p.getYRot(), p.getXRot());
                level.playSound(null, dest, StormSetup.GALE.get(), SoundSource.PLAYERS, 1f, 1.2f);
                p.displayClientMessage(ModText.hint("message.skyabyss.storm.return_path"), true);
            }
        }
    }

    // ---------------------------------------------------------------- 風鳴り石
    public static void useStone(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level.dimension() != StormTravel.STORM_BACK) {
            return;
        }
        StormBackState state = StormBackState.get(level);
        StormBackState.Trial trial = pos.equals(WEST_STONE) ? StormBackState.Trial.WEST
                : pos.equals(EAST_STONE) ? StormBackState.Trial.EAST : pos.equals(FRONT_STONE) ? StormBackState.Trial.FRONT : null;
        if (trial == null) {
            return;
        }
        if (state.rung(trial)) {
            player.displayClientMessage(ModText.hint(state.sleeping() == trial ? "message.skyabyss.storm.trial_sleeping" : "message.skyabyss.storm.stone_rung"), true);
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (trial == StormBackState.Trial.EAST && !state.raijuDefeated()) {
            player.displayClientMessage(ModText.deny("message.skyabyss.storm.stone_silent"), true);
            return;
        }
        ring(level, state, trial, player);
    }

    public static void ring(ServerLevel level, StormBackState state, StormBackState.Trial trial, @Nullable ServerPlayer by) {
        state.setRung(trial, true);
        applyStones(level, state);
        BlockPos pos = stonePos(trial);
        level.playSound(null, pos, StormSetup.RECEIVER_LIT.get(), SoundSource.BLOCKS, 3f, 0.7f);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 3f, 0.8f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);
        if (by != null) {
            by.displayClientMessage(ModText.hint("message.skyabyss.storm.return_opened"), true);
        }
        Component msg = ModText.progress("message.skyabyss.storm.stone_ringing", Component.translatable("trial.skyabyss.storm_" + trial.id()),
                state.rungCount(), 3);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(msg);
        }
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(16), p -> p.isAlive() && !p.isSpectator());
        for (ServerPlayer p : near) {
            Growth.addXp(p, (int) Math.round(TRIAL_XP * GrowthRules.levelFalloff(Growth.get(p).level, STORM_LEVEL)), "growth.skyabyss.reason.storm_trial");
            jp.revati.skyabyss.quest.Quests.signal(p, "storm_trial_" + trial.id());
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, near)) {
            Growth.addXp(mate, (int) Math.round(TRIAL_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, STORM_LEVEL)),
                    "growth.skyabyss.reason.party");
        }
        jp.revati.skyabyss.quest.Quests.signalWithParty(level, near, jp.revati.skyabyss.quest.free.FreeQuestEvents.TRIAL_CLEARED);
        if (state.allRung()) {
            for (ServerPlayer p : level.players()) {
                p.sendSystemMessage(ModText.lore("message.skyabyss.storm.all_rung"));
            }
        }
    }

    public static BlockPos stonePos(StormBackState.Trial t) {
        return switch (t) {
            case WEST -> WEST_STONE;
            case EAST -> EAST_STONE;
            case FRONT -> FRONT_STONE;
        };
    }

    // 三つの石・広間の床の灯り・抜け道の床を、いまの状態に合わせる
    static void applyStones(ServerLevel level, StormBackState state) {
        for (StormBackState.Trial t : StormBackState.Trial.values()) {
            boolean rung = state.rung(t);
            BlockState want = StormBackSetup.CHIME_STONE.get().defaultBlockState().setValue(StormBlocks.ChimeStone.ACTIVE, rung);
            BlockPos s = stonePos(t);
            if (!level.getBlockState(s).equals(want)) {
                level.setBlock(s, want, 3);
            }
            BlockPos lamp = switch (t) {
                case WEST -> LAMP_WEST;
                case EAST -> LAMP_EAST;
                case FRONT -> LAMP_FRONT;
            };
            level.setBlock(lamp, rung ? StormKit.LAMP : StormKit.SLATE, 3);
            BlockPos pad = switch (t) {
                case WEST -> RETURN_WEST;
                case EAST -> RETURN_EAST;
                case FRONT -> RETURN_FRONT;
            };
            level.setBlock(pad, rung ? StormKit.LAMP : StormKit.SLATE, 3);
        }
    }

    // 雷獣を倒した (雷獣の担当が呼ぶ)。巣の真ん中の昇り風が立ち、頂の東の石へ届く
    public static void onRaijuDefeated(ServerLevel level) {
        StormBackState state = StormBackState.get(level);
        state.setRaijuDefeated(true);
        for (ServerPlayer p : level.players()) {
            if (inEast(p.position())) {
                p.displayClientMessage(ModText.lore("message.skyabyss.raiju.defeated"), true);
            }
        }
    }

    // ---------------------------------------------------------------- リセット
    private static void tickReset(ServerLevel level, TitanBackData data, StormBackState state, boolean anyone) {
        if (!anyone) {
            idleTicks += 10;
            if (idleTicks >= RESET_AFTER_IDLE && (state.rungCount() > (state.sleeping() != null ? 1 : 0) || data.isHeartDefeated()
                    || state.receivers() != 0 || state.raijuDefeated() || anyDoor(state))) {
                idleTicks = 0;
                reset(level, false);
            }
        } else {
            idleTicks = 0;
        }
        if (data.isHeartDefeated()) {
            boolean inside = false;
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && pastKeel(p.position())) {
                    inside = true;
                    break;
                }
            }
            afterHeartIdle = inside ? 0 : afterHeartIdle + 10;
            if (afterHeartIdle >= RESET_AFTER_HEART) {
                afterHeartIdle = 0;
                reset(level, true);
            }
        } else {
            afterHeartIdle = 0;
        }
    }

    private static boolean anyDoor(StormBackState state) {
        for (int i = 0; i < EAST_DOORS.length; i++) {
            if (state.eastDoorOpen(i)) {
                return true;
            }
        }
        return false;
    }

    public static void reset(ServerLevel level, boolean announce) {
        reset(level, announce, null, false);
    }

    // 試練を最初に戻す (12 の 7.1節)。試練の番号が進み (古い休み場の記録は無効)、心臓も眠り直す (TitanBackData)
    public static void reset(ServerLevel level, boolean announce, @Nullable StormBackState.Trial forceSleep, boolean noSleep) {
        TitanBackData data = TitanBackData.get(level);
        data.resetProgress();
        StormBackState state = StormBackState.get(level);
        state.reset(level.random, forceSleep, noSleep);
        BlockPos hub = RESTS.get(0).spawn();
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (!p.isSpectator() && StormBackLayout.inTrial(p.position())) {
                p.fallDistance = 0;
                p.teleportTo(level, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, p.getYRot(), 0f);
            }
        }
        applyTrials(level, state);
        lastPhase = null;
        if (announce) {
            for (ServerPlayer p : level.players()) {
                p.sendSystemMessage(ModText.lore("message.skyabyss.storm.trials_renewed"));
                tellSleeping(p, state);
            }
        }
        SkyAbyss.LOGGER.info("Storm back trials reset (sleeping: {}, mirror config {}, west route {})",
                state.sleeping() == null ? "none" : state.sleeping().id(), state.mirrorConfig(), state.westRoute());
    }

    static void applyTrials(ServerLevel level, StormBackState state) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(-150, 100, -120, 150, 170, 130), e -> e.getTags().contains(TRIAL_TAG))) {
            e.discard();
        }
        StormStrike.cancelAll(level);
        StormBirdBuilder.carveWestRoute(new StormKit(level), state.westRoute());
        StormEast.apply(level, state);
        EyeMirrors.apply(level, state, false);
        StormBirdBuilder.setKeelGate(level, state.keelOpen());
        applyStones(level, state);
    }

    static void tellSleeping(ServerPlayer p, StormBackState state) {
        if (state.sleeping() != null) {
            p.sendSystemMessage(ModText.lore("message.skyabyss.storm.sleeping_" + state.sleeping().id()));
        }
    }

    // ---------------------------------------------------------------- ピースフル
    private static void tickPeaceful(ServerLevel level, boolean peaceful) {
        for (int x = -1; x <= 1; x++) {
            for (int y = TAIL_HALL.floor() + 1; y <= TAIL_HALL.floor() + 4; y++) {
                BlockPos p = new BlockPos(x, y, SEAL_Z);
                boolean sealed = level.getBlockState(p).is(ModBlocks.SEALED_GATE.get());
                if (peaceful && !sealed) {
                    level.setBlock(p, ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
                } else if (!peaceful && sealed) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        if (peaceful) {
            for (ServerPlayer p : List.copyOf(level.players())) {
                if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
                    continue;
                }
                if (pastSeal(p.position()) && !onBackTop(p.position())) {
                    toArrival(p);
                    p.sendSystemMessage(ModText.lore("message.skyabyss.storm.peaceful"));
                }
                if (p.tickCount % 40 < 5 && Math.abs(p.getZ() - SEAL_Z) < 4 && Math.abs(p.getX()) < 3) {
                    p.displayClientMessage(ModText.lore("message.skyabyss.storm.peaceful"), true);
                }
            }
            if (!wasPeaceful) {
                for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(-150, 100, -120, 150, 170, 130), e -> e.getTags().contains(TRIAL_TAG))) {
                    e.discard();
                }
                StormStrike.cancelAll(level);
            }
        }
        wasPeaceful = peaceful;
    }

    static void tickPeacefulForTest(ServerLevel level, boolean peaceful) {
        tickPeaceful(level, peaceful);
    }

    // 背の上 (体の外の羽の上)。鳥のいちばん上のブロックの上にいる
    static boolean onBackTop(Vec3 p) {
        int t = StormBirdBuilder.top((int) Math.floor(p.x), (int) Math.floor(p.z));
        return p.y >= t + 0.5;
    }

    static void toArrival(ServerPlayer p) {
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), ARRIVAL.getX() + 0.5, ARRIVAL.getY(), ARRIVAL.getZ() + 0.5, ARRIVAL_YAW, 0f);
    }

    // ---------------------------------------------------------------- 休み場
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != StormTravel.STORM_BACK || player.tickCount % 5 != 0) {
            return;
        }
        int epoch = TitanBackData.get(player.serverLevel()).epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        BlockPos pos = player.blockPosition();
        for (Rest r : RESTS) {
            if (r.area().contains(pos) || pos.distSqr(r.spawn()) < 25) {
                if (!r.id().equals(validCheckpoint(tag, epoch))) {
                    tag.putString(TAG_CHECKPOINT, r.id());
                    tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
                    if (!r.id().equals("hub")) {
                        player.displayClientMessage(ModText.progress("message.skyabyss.checkpoint",
                                Component.translatable("checkpoint.skyabyss.storm_" + r.id())), true);
                        player.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
                    }
                }
                return;
            }
        }
    }

    public static String validCheckpoint(CompoundTag tag, int epoch) {
        String id = tag.getString(TAG_CHECKPOINT);
        if (id.isEmpty() || tag.getInt(TAG_CHECKPOINT_EPOCH) != epoch) {
            return "";
        }
        return REST.containsKey(id) ? id : "";
    }

    public static void writeCheckpoint(Player player, String id, int epoch) {
        CompoundTag tag = persisted(player);
        tag.putString(TAG_CHECKPOINT, id);
        tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
    }

    static void clearCheckpoint(Player player) {
        CompoundTag tag = persisted(player);
        tag.remove(TAG_CHECKPOINT);
        tag.remove(TAG_CHECKPOINT_EPOCH);
    }

    public static BlockPos restSpawn(ServerLevel level, String id) {
        for (Rest r : RESTS) {
            if (r.id().equals(id)) {
                return TitanBackTrials.safeSpot(level, r.spawn());
            }
        }
        return ARRIVAL;
    }

    // ---------------------------------------------------------------- 倒れたとき
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != StormTravel.STORM_BACK || event.isCanceled()) {
            return;
        }
        StormWest.strip(player);
        persisted(player).putBoolean(TAG_DIED, true);
        TitanBackTrials.keepBelongings(player);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }
        CompoundTag tag = persisted(player);
        if (!tag.getBoolean(TAG_DIED)) {
            return;
        }
        tag.remove(TAG_DIED);
        ServerLevel back = player.server.getLevel(StormTravel.STORM_BACK);
        if (back == null) {
            return;
        }
        int epoch = TitanBackData.get(back).epoch();
        String cp = validCheckpoint(tag, epoch);
        BlockPos spawn = cp.isEmpty() ? ARRIVAL : restSpawn(back, cp);
        REVIVING.add(player.getUUID());
        try {
            player.teleportTo(back, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
        } finally {
            REVIVING.remove(player.getUUID());
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
        player.sendSystemMessage(ModText.lore(cp.isEmpty() && tag.contains(TAG_CHECKPOINT) ? "message.skyabyss.rest_point_lost" : "message.skyabyss.revived_on_back"));
        if (cp.isEmpty()) {
            clearCheckpoint(player);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getFrom() == StormTravel.STORM_BACK) {
            StormWest.strip(player);
        }
        if (REVIVING.contains(player.getUUID())) {
            return;
        }
        if (event.getFrom() == StormTravel.STORM_BACK || event.getTo() == StormTravel.STORM_BACK) {
            clearCheckpoint(player);
            ServerLevel back = player.server.getLevel(StormTravel.STORM_BACK);
            if (event.getTo() == StormTravel.STORM_BACK && back != null) {
                persisted(player).putInt(TAG_SEEN_EPOCH, TitanBackData.get(back).epoch());
                tellSleeping(player, StormBackState.get(back));
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != StormTravel.STORM_BACK) {
            return;
        }
        ServerLevel back = player.serverLevel();
        int epoch = TitanBackData.get(back).epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            clearCheckpoint(player);
            if (player.isAlive() && StormBackLayout.inTrial(player.position())) {
                toArrival(player);
                player.sendSystemMessage(ModText.lore("message.skyabyss.rest_point_lost"));
            }
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // ---------------------------------------------------------------- 焚き火・戻しの鏡
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != StormTravel.STORM_BACK || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        if (pos.equals(MIRROR_RESET)) {
            event.setCanceled(true);
            EyeMirrors.resetMirrors(level, player);
            return;
        }
        if (level.getBlockState(pos).is(Blocks.SOUL_CAMPFIRE)) {
            event.setCanceled(true);
            if (player.isShiftKeyDown()) {
                StormWest.strip(player);
                clearCheckpoint(player);
                player.fallDistance = 0;
                player.teleportTo(level, TAIL_CENTER.getX() + 0.5, TAIL_CENTER.getY(), TAIL_CENTER.getZ() + 0.5, 180f, 0f);
                player.sendSystemMessage(ModText.lore("message.skyabyss.storm.gave_up"));
            } else {
                player.displayClientMessage(ModText.hint("message.skyabyss.storm.campfire_hint"), true);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ready = false;
        idleTicks = 0;
        afterHeartIdle = 0;
        wasPeaceful = false;
        lastPhase = null;
        REVIVING.clear();
        StormEast.clear();
        EyeMirrors.clear();
        StormBackClock.force(null);
    }

    // ---------------------------------------------------------------- 風見の目盛り (画面右上)
    static void sendGauge(ServerLevel level) {
        StormBackNetwork.Gauge g = gauge(level);
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, g);
        }
    }

    public static void sendGaugeNow(ServerLevel level) {
        sendGauge(level);
    }

    static StormBackNetwork.Gauge gauge(ServerLevel level) {
        StormBackState state = StormBackState.get(level);
        int stones = 0;
        for (StormBackState.Trial t : StormBackState.Trial.values()) {
            stones += state.rung(t) ? 1 : 0;
        }
        return new StormBackNetwork.Gauge(StormBackClock.phase(level).ordinal(), StormBackClock.ticksLeft(level),
                (float) StormBackClock.strength(level), stones);
    }

    public static Vec3 hubSpawn() {
        return Vec3.atBottomCenterOf(RESTS.get(0).spawn());
    }
}
