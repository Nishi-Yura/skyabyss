package jp.revati.skyabyss.titan.ember.back;

import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 余燼の背の試練の進み (13 の 3.5節・7 章): 熾り石・抜け道の光る床・熾の底の門・リセット・眠る試練・休み場・ピースフル。
// ほかの担当が呼ぶ API (この名前は変えない):
//   markReady(level, trial)        石を鳴らせるようにする (東: E が鍛え手を倒したとき、南: C3 が火床を 100 にしたとき。西は奥の岸に着けばいつでも)
//   ring(level, trial, player)     石を鳴らす (ふつうは人が石を右クリックしたとき useStone から。テスト用)
//   sleeping(level, room)          この周、その部屋が眠っているか (east が眠ると smith も)
//   EmberBackState.get(level).seed() リセットの種 (島の並び・打つ順を決める)
//   inTrial(pos)・toHub(player)    試練の箱の中か / 広間の休み場へ移す (熱は 0)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberBackTrials {
    public static final int RESET_AFTER_IDLE = 20 * 60 * 3;
    public static final int RESET_AFTER_HEART = 20 * 30;
    public static final int TRIAL_XP = 60;
    public static final int EMBER_LEVEL = 30;
    // 炉の火が三度弱まってから門が開くまで (tick)
    public static final int GATE_OPEN_TICKS = 70;
    // ピースフルのあいだ喉の坂を封じる所 (広間の手前)
    public static final int PEACEFUL_SEAL_Z = -60;
    private static final String TAG_CHECKPOINT = "skyabyss_ember_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_ember_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_ember_epoch";
    private static final String TAG_DIED = "skyabyss_died_on_ember";

    private static int idleTicks;
    private static int afterHeartIdle;
    private static boolean wasPeaceful;
    // 門の開く演出の残り (0 = 動いていない)
    private static int gateTimer;
    private static final Map<UUID, Integer> ON_PAD = new HashMap<>();
    private static final Set<UUID> REVIVING = new HashSet<>();

    private EmberBackTrials() {
    }

    public static boolean onBack(ServerLevel level) {
        return level.dimension() == EmberIds.BACK;
    }

    static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ---------------------------------------------------------------- 起動・建てたあと
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel back = event.getServer().getLevel(EmberIds.BACK);
        if (back != null) {
            EmberBodyBuilder.ensureBuilt(back);
        }
    }

    // 体を建てた直後 (EmberBodyBuilder から)。種を引き、試練を最初の形にする (眠らせない)
    static void afterBuild(ServerLevel level) {
        EmberBackState state = EmberBackState.get(level);
        state.reset(level.random, null, true);
        EmberBackRooms.resetAll(level, state::roomSleeping);
        gateTimer = 0;
        applyGate(level, false);
        applyStones(level, state);
    }

    // ---------------------------------------------------------------- API
    public static void markReady(ServerLevel level, EmberBackState.Trial trial) {
        EmberBackState state = EmberBackState.get(level);
        if (state.ready(trial)) {
            return;
        }
        state.markReady(trial);
        BlockPos s = stonePos(trial);
        level.playSound(null, s, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2f, 0.7f);
        level.sendParticles(ParticleTypes.FLAME, s.getX() + 0.5, s.getY() + 1.1, s.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(s)) < 40 * 40) {
                p.sendSystemMessage(ModText.hint(EmberIds.LANG_BACK + "stone_ready"));
            }
        }
    }

    public static boolean sleeping(ServerLevel level, String room) {
        return EmberBackState.get(level).roomSleeping(room);
    }

    public static BlockPos stonePos(EmberBackState.Trial t) {
        return switch (t) {
            case WEST -> STONE_WEST;
            case EAST -> STONE_EAST;
            case SOUTH -> STONE_SOUTH;
        };
    }

    static BlockPos padPos(EmberBackState.Trial t) {
        return switch (t) {
            case WEST -> PAD_WEST;
            case EAST -> PAD_EAST;
            case SOUTH -> PAD_SOUTH;
        };
    }

    static BlockPos shortcutTo(EmberBackState.Trial t) {
        return switch (t) {
            case WEST -> SHORTCUT_WEST_TO;
            case EAST -> SHORTCUT_EAST_TO;
            case SOUTH -> SHORTCUT_SOUTH_TO;
        };
    }

    // 試練の箱 (と熾の底) の中か
    public static boolean inTrial(Vec3 pos) {
        for (String room : TRIAL_ROOMS) {
            if (ROOMS.get(room).aabb().inflate(0, 2, 0).contains(pos)) {
                return true;
            }
        }
        return pastGate(pos);
    }

    // 熾の底の門より奥 (門の下の螺旋・前室・熾の底)
    public static boolean pastGate(Vec3 pos) {
        return HEART_ANTE.aabb().inflate(1, 0, 1).contains(pos) || HEART.aabb().contains(pos)
                || new AABB(GATE_SHAFT.minX() - 1, FLOOR_HEART, GATE_SHAFT.minZ() - 1, GATE_SHAFT.maxX() + 2, 84.9, HEART_ANTE.minZ() + 1).contains(pos);
    }

    // ---------------------------------------------------------------- 熾り石
    public static void useStone(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!onBack(level)) {
            return;
        }
        EmberBackState.Trial trial = null;
        for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
            if (stonePos(t).equals(pos)) {
                trial = t;
            }
        }
        if (trial == null) {
            return;
        }
        EmberBackState state = EmberBackState.get(level);
        if (state.rung(trial)) {
            player.displayClientMessage(ModText.hint(EmberIds.LANG_BACK + (state.sleeping(trial) ? "trial_sleeping" : "stone_rung")), true);
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (trial != EmberBackState.Trial.WEST && !state.ready(trial)) {
            player.displayClientMessage(ModText.deny(EmberIds.LANG_BACK + trial.id() + "_silent"), true);
            return;
        }
        ring(level, trial, player);
    }

    public static void ring(ServerLevel level, EmberBackState.Trial trial, @Nullable ServerPlayer by) {
        EmberBackState state = EmberBackState.get(level);
        if (state.rung(trial)) {
            return;
        }
        state.ring(trial);
        applyStones(level, state);
        BlockPos pos = stonePos(trial);
        level.playSound(null, pos, EmberBackSetup.STONE_RING.get(), SoundSource.BLOCKS, 3f, 1f);
        level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.03);
        if (by != null) {
            by.displayClientMessage(ModText.hint(EmberIds.LANG_BACK + "shortcut_opened"), true);
        }
        Component msg = ModText.progress(EmberIds.LANG_BACK + "stone_ringing", Component.translatable("trial.skyabyss.ember_" + trial.id()),
                state.rungCount(), 3);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(msg);
        }
        // 試練の踏破の経験値 (石の近くにいた人。仲間には分け前) と、サブクエストの「試練を踏破する」
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(20), p -> p.isAlive() && !p.isSpectator());
        for (ServerPlayer p : near) {
            Growth.addXp(p, (int) Math.round(TRIAL_XP * GrowthRules.levelFalloff(Growth.get(p).level, EMBER_LEVEL)), "growth.skyabyss.reason.ember_trial");
            jp.revati.skyabyss.quest.Quests.signal(p, "ember_trial_" + trial.id());
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, near)) {
            Growth.addXp(mate, (int) Math.round(TRIAL_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, EMBER_LEVEL)),
                    "growth.skyabyss.reason.party");
        }
        jp.revati.skyabyss.quest.Quests.signalWithParty(level, near, jp.revati.skyabyss.quest.free.FreeQuestEvents.TRIAL_CLEARED);
        checkAllRung(level, state);
    }

    // 三つそろったら、炉の火を三度弱めて門を開く
    static void checkAllRung(ServerLevel level, EmberBackState state) {
        if (state.rungCount() >= 3 && !state.gateOpen() && gateTimer == 0) {
            gateTimer = GATE_OPEN_TICKS;
            for (ServerPlayer p : level.players()) {
                p.sendSystemMessage(ModText.lore(EmberIds.LANG_BACK + "all_rung"));
            }
        }
    }

    // 三つの石・抜け道の床を、いまの状態に合わせる
    static void applyStones(ServerLevel level, EmberBackState state) {
        for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
            boolean rung = state.rung(t);
            BlockPos s = stonePos(t);
            BlockState want = EmberBackSetup.KINDLE_STONE.get().defaultBlockState().setValue(KindleStoneBlock.ACTIVE, rung);
            if (!level.getBlockState(s).equals(want)) {
                level.setBlock(s, want, 3);
            }
            BlockPos pad = padPos(t);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState p = rung ? (dx == 0 && dz == 0 ? EmberKit.EMBERLIGHT : Blocks.SHROOMLIGHT.defaultBlockState())
                            : (dx == 0 && dz == 0 ? EmberKit.RUIN_CHISELED : EmberKit.RUIN_DARK);
                    level.setBlock(pad.offset(dx, 0, dz), p, 3);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 熾の底の門 (広間の中央の大きな炉の底の格子)
    // closed: 格子 (封印) と、その上の炉の火。open: どちらも無く、下の螺旋の段が見える。炉の囲いの南の口も開く
    static void applyGate(ServerLevel level, boolean open) {
        int cx = HUB_CENTER.getX();
        int cz = HUB_CENTER.getZ();
        int fy = HUB_CENTER.getY();
        BlockState gate = ModBlocks.SEALED_GATE.get().defaultBlockState();
        BlockState fire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(cx + dx, fy, cz + dz), open ? Blocks.AIR.defaultBlockState() : gate, 3);
                level.setBlock(new BlockPos(cx + dx, fy + 1, cz + dz), open ? Blocks.AIR.defaultBlockState() : fire, 3);
            }
            for (int y = fy + 1; y <= fy + 2; y++) {
                level.setBlock(new BlockPos(cx + dx, y, cz + 2), open ? Blocks.AIR.defaultBlockState()
                        : (y == fy + 1 ? EmberKit.RUIN_CHISELED : EmberKit.RUIN), 3);
            }
        }
    }

    // 炉の火をともす・消す (門の開く演出)
    private static void setFire(ServerLevel level, boolean lit) {
        int cx = HUB_CENTER.getX();
        int cz = HUB_CENTER.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = new BlockPos(cx + dx, HUB_CENTER.getY() + 1, cz + dz);
                BlockState s = level.getBlockState(p);
                if (s.is(Blocks.CAMPFIRE)) {
                    level.setBlock(p, s.setValue(CampfireBlock.LIT, lit), 3);
                }
            }
        }
    }

    private static void tickGate(ServerLevel level, EmberBackState state) {
        if (gateTimer <= 0) {
            return;
        }
        int t = GATE_OPEN_TICKS - gateTimer;
        BlockPos c = HUB_CENTER.above();
        // 三度弱まる (0・20・40 tick で消え、10 tick あとにまた灯る)
        if (t == 0 || t == 20 || t == 40) {
            setFire(level, false);
            level.playSound(null, c, EmberBackSetup.FORGE_DIM.get(), SoundSource.BLOCKS, 3f, 1f - t / 100f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 30, 1, 0.5, 1, 0.02);
        } else if (t == 10 || t == 30) {
            setFire(level, true);
        }
        gateTimer -= 1;
        if (gateTimer == 0) {
            openGate(level, state);
        }
    }

    // 門を開く (コマンド・テストからも)
    public static void openGate(ServerLevel level, EmberBackState state) {
        gateTimer = 0;
        state.markGateOpen();
        applyGate(level, true);
        BlockPos c = HUB_CENTER;
        level.playSound(null, c, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 3f, 0.5f);
        level.playSound(null, c, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2f, 0.6f);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(ModText.lore(EmberIds.LANG_BACK + "gate_open"));
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !onBack(level)) {
            return;
        }
        EmberBackState state = EmberBackState.get(level);
        tickGate(level, state);
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        TitanBackData data = TitanBackData.get(level);
        if (data.isHeartDefeated() && !state.everDefeated()) {
            state.markEverDefeated();
        }
        boolean anyone = false;
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator()) {
                anyone = true;
                break;
            }
        }
        tickReset(level, data, state, anyone);
        tickPeaceful(level, level.getDifficulty() == Difficulty.PEACEFUL);
    }

    // ---------------------------------------------------------------- 抜け道の光る床・休み場の記録 (人ごと)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel()) || player.tickCount % 5 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // 光る床: 鳴った石のそばの 3 × 3 に 0.5 秒いると、広間の扉の内側へ
        EmberBackState state = EmberBackState.get(level);
        boolean onPad = false;
        if (!player.isSpectator()) {
            for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
                BlockPos pad = padPos(t);
                if (state.rung(t) && Math.abs(player.getX() - (pad.getX() + 0.5)) < 1.6 && Math.abs(player.getZ() - (pad.getZ() + 0.5)) < 1.6
                        && player.getY() >= pad.getY() + 0.9 && player.getY() < pad.getY() + 2.5) {
                    onPad = true;
                    int n = ON_PAD.merge(player.getUUID(), 5, Integer::sum);
                    if (n >= 10) {
                        ON_PAD.remove(player.getUUID());
                        BlockPos to = shortcutTo(t);
                        player.fallDistance = 0;
                        player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYRot(), 0f);
                        level.playSound(null, to, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
                        player.displayClientMessage(ModText.lore(EmberIds.LANG_BACK + "shortcut"), true);
                    }
                }
            }
        }
        if (!onPad) {
            ON_PAD.remove(player.getUUID());
        }
        // 休み場の記録
        int epoch = TitanBackData.get(level).epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        BlockPos pos = player.blockPosition();
        for (Rest r : RESTS) {
            if (r.area().grow(1).contains(pos)) {
                if (!r.name().equals(validCheckpoint(tag, epoch))) {
                    tag.putString(TAG_CHECKPOINT, r.name());
                    tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
                    if (!r.name().equals("hub")) {
                        player.displayClientMessage(ModText.progress("message.skyabyss.checkpoint",
                                Component.translatable("checkpoint.skyabyss.ember_" + r.name())), true);
                        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f);
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
        return EmberIds.RESTS.contains(id) ? id : "";
    }

    public static String checkpoint(ServerPlayer player) {
        return validCheckpoint(persisted(player), TitanBackData.get(player.serverLevel()).epoch());
    }

    static void clearCheckpoint(Player player) {
        CompoundTag tag = persisted(player);
        tag.remove(TAG_CHECKPOINT);
        tag.remove(TAG_CHECKPOINT_EPOCH);
    }

    // 休み場の復活位置 (焚き火の前の台の上。立てる場所を探す)
    public static BlockPos restSpawn(ServerLevel level, String id) {
        for (Rest r : RESTS) {
            if (r.name().equals(id)) {
                return TitanBackTrials.safeSpot(level, r.floor().above());
            }
        }
        return ARRIVAL.above();
    }

    public static void toArrival(ServerPlayer p) {
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), ARRIVAL.getX() + 0.5, ARRIVAL.getY() + 1, ARRIVAL.getZ() + 0.5, ARRIVAL_YAW, 0f);
    }

    // 広間の休み場へ移す (リセット・ピースフル)。熱は 0
    public static void toHub(ServerPlayer p) {
        BlockPos h = rest("hub").floor().above();
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), h.getX() + 0.5, h.getY(), h.getZ() + 0.5, rest("hub").yaw(), 0f);
        EmberHeat.reset(p);
    }

    public static Vec3 hubSpawn() {
        return Vec3.atBottomCenterOf(rest("hub").floor().above());
    }

    // ---------------------------------------------------------------- リセット
    private static void tickReset(ServerLevel level, TitanBackData data, EmberBackState state, boolean anyone) {
        if (!anyone) {
            idleTicks += 10;
            if (idleTicks >= RESET_AFTER_IDLE && (state.rungCount() > (state.sleeping() != null ? 1 : 0) || data.isHeartDefeated() || state.gateOpen())) {
                idleTicks = 0;
                reset(level, false);
            }
        } else {
            idleTicks = 0;
        }
        // 心臓を倒したあと、熾の底の門より奥に誰もいなくなって 30 秒たったら、次の周へ
        if (data.isHeartDefeated()) {
            boolean inside = false;
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && pastGate(p.position())) {
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

    public static void reset(ServerLevel level, boolean announce) {
        reset(level, announce, null, false);
    }

    // 試練を最初に戻す (7.1節)。試練の中の人は広間へ (熱は 0)、試練の番号が進む (古い休み場の記録は無効)。2 周目からは一つ眠る
    public static void reset(ServerLevel level, boolean announce, @Nullable EmberBackState.Trial forceSleep, boolean noSleep) {
        TitanBackData data = TitanBackData.get(level);
        EmberBackState state = EmberBackState.get(level);
        if (data.isHeartDefeated()) {
            state.markEverDefeated();
        }
        data.resetProgress();
        state.reset(level.random, forceSleep, noSleep);
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (!p.isSpectator() && inTrial(p.position())) {
                toHub(p);
            }
        }
        gateTimer = 0;
        EmberBackRooms.resetAll(level, state::roomSleeping);
        applyGate(level, false);
        applyStones(level, state);
        if (announce) {
            for (ServerPlayer p : level.players()) {
                p.sendSystemMessage(ModText.lore(EmberIds.LANG_BACK + "trials_renewed"));
                tellSleeping(p, state);
            }
        }
        SkyAbyss.LOGGER.info("Ember back trials reset (sleeping: {}, seed {})", state.sleeping() == null ? "none" : state.sleeping().id(), state.seed());
    }

    // 眠る試練をコマンドで決める (今の周に効かせる)
    public static void setSleeping(ServerLevel level, @Nullable EmberBackState.Trial t) {
        EmberBackState state = EmberBackState.get(level);
        state.setSleeping(t);
        EmberBackRooms.resetAll(level, state::roomSleeping);
        applyStones(level, state);
        checkAllRung(level, state);
    }

    static void tellSleeping(ServerPlayer p, EmberBackState state) {
        if (state.sleeping() != null) {
            p.sendSystemMessage(ModText.lore(EmberIds.LANG_BACK + "sleeping_" + state.sleeping().id()));
        }
    }

    // ---------------------------------------------------------------- ピースフル (7.4節): 喉の坂の上を封じる。口と見送りの段は歩ける
    static boolean peacefulSide(ServerPlayer p) {
        return p.getZ() < PEACEFUL_SEAL_Z;
    }

    private static void tickPeaceful(ServerLevel level, boolean peaceful) {
        for (int x = -3; x <= 3; x++) {
            int f = EmberBodyBuilder.throatFloor(PEACEFUL_SEAL_Z);
            for (int y = f + 1; y <= f + 5; y++) {
                BlockPos p = new BlockPos(x, y, PEACEFUL_SEAL_Z);
                boolean sealed = level.getBlockState(p).is(ModBlocks.SEALED_GATE.get());
                if (peaceful && !sealed && level.getBlockState(p).isAir()) {
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
                if (!peacefulSide(p)) {
                    toArrival(p);
                    EmberHeat.reset(p);
                    p.sendSystemMessage(ModText.lore("message.skyabyss.trials_peaceful"));
                } else if (p.tickCount % 40 < 10 && Math.abs(p.getZ() - PEACEFUL_SEAL_Z) < 5) {
                    p.displayClientMessage(ModText.lore(EmberIds.LANG_BACK + "peaceful"), true);
                }
            }
        }
        wasPeaceful = peaceful;
    }

    // ---------------------------------------------------------------- 倒れたとき (7.3節)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel()) || event.isCanceled()) {
            return;
        }
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
        ServerLevel back = player.server.getLevel(EmberIds.BACK);
        if (back == null) {
            return;
        }
        int epoch = TitanBackData.get(back).epoch();
        String cp = validCheckpoint(tag, epoch);
        BlockPos spawn = cp.isEmpty() ? ARRIVAL.above() : restSpawn(back, cp);
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

    // 乗り込み・地上へ戻る (次元の移動) では休み場の記録を消す。背中で倒れて連れ戻すときだけは残す。乗り込んだ人には、眠っている試練を知らせる
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || REVIVING.contains(player.getUUID())) {
            return;
        }
        if (event.getFrom() == EmberIds.BACK || event.getTo() == EmberIds.BACK) {
            clearCheckpoint(player);
            ServerLevel back = player.server.getLevel(EmberIds.BACK);
            if (event.getTo() == EmberIds.BACK && back != null) {
                persisted(player).putInt(TAG_SEEN_EPOCH, TitanBackData.get(back).epoch());
                tellSleeping(player, EmberBackState.get(back));
            }
        }
    }

    // ログイン: 背中にいて、その間にリセットされていて、試練の中にいるなら到着点へ
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel())) {
            return;
        }
        int epoch = TitanBackData.get(player.serverLevel()).epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            clearCheckpoint(player);
            if (player.isAlive() && inTrial(player.position())) {
                toArrival(player);
                player.sendSystemMessage(ModText.lore("message.skyabyss.rest_point_lost"));
            }
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // 焚き火: しゃがんで触れると、試練をあきらめて到着点へ
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel())) {
            return;
        }
        if (player.serverLevel().getBlockState(event.getPos()).is(Blocks.SOUL_CAMPFIRE)) {
            event.setCanceled(true);
            if (player.isShiftKeyDown()) {
                clearCheckpoint(player);
                toArrival(player);
                EmberHeat.reset(player);
                player.sendSystemMessage(ModText.lore(EmberIds.LANG_BACK + "gave_up"));
            } else {
                player.displayClientMessage(ModText.hint(EmberIds.LANG_BACK + "campfire_hint"), true);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        idleTicks = 0;
        afterHeartIdle = 0;
        wasPeaceful = false;
        gateTimer = 0;
        ON_PAD.clear();
        REVIVING.clear();
    }

    // テスト用
    static boolean gateOpening() {
        return gateTimer > 0;
    }
}
