package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
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
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.heart.TideHeartBattle;
import jp.revati.skyabyss.titan.tide.heart.TideLoot;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 潮殻の背の試練の進行 (08 の 3〜4・7 章)。岩鯨の TitanBackTrials と同じ決まりを、潮殻の背の配置で動かす。
//  - 潮の時計 (TideClock) を進め、潮の溜め (広間の水盤・庭の水路・干潟) を満ち引きさせる。干潟の門は干潮のあいだだけ開く
//  - 三つの試練 (TideGarden・TideWatch・TideFlats) と潮鳴り石。三つが鳴ると水盤が引ききり、潮の底の門が開く
//  - 休み場 (hub・g0・g1・s0・f0・heart): 着くと記録、倒れるとそこで目を覚ます (記録は試練の番号つき)。焚き火にしゃがんで触れると浜へ
//  - 倒れても持ち物と経験値は預かる (岩鯨と同じ部品 TitanBackTrials.keepBelongings)
//  - リセット: 島に誰もいなくなって 3 分、または心臓を倒したあと潮の底の門より奥に 30 秒だれもいない。
//    2 周目から試練が一つ眠る (TideBackState.reset)
//  - ピースフル: 縁のくぐり道を封じ、試練の中の人は浜へ。試練の敵は消える (潮の時計は景色として動く)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideBackTrials {
    public static final TideField FIELD = new TideField();
    public static final int RESET_AFTER_IDLE = 20 * 60 * 3;
    public static final int RESET_AFTER_HEART = 20 * 30;
    public static final int TRIAL_XP = 50;
    private static final String TAG_CHECKPOINT = "skyabyss_tide_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_tide_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_tide_epoch";
    private static final String TAG_DIED = "skyabyss_died_on_tide";

    private static boolean ready;
    private static int idleTicks;
    private static int afterHeartIdle;
    private static boolean wasPeaceful;
    private static TideClock.Phase lastPhase;
    private static final Set<UUID> REVIVING = new HashSet<>();

    private TideBackTrials() {
    }

    static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean onTideBack(Entity e) {
        return e.level().dimension() == TideTravel.TIDE_BACK;
    }

    // ---------------------------------------------------------------- 建てたあと・起動のあとの用意
    // 島を建てた直後 (TideBackBuilder から)。種を引き、試練を最初の形にする
    public static void afterBuild(ServerLevel level) {
        TideBackState state = TideBackState.get(level);
        state.reset(level.random, null, true);
        applyTrials(level, state);
        initPools(level);
    }

    // 潮の溜め (水を置く所) を、いまのブロックから作る
    public static void initPools(ServerLevel level) {
        FIELD.clear();
        // 広間の水盤 (床 70 の上の 3 段)。干潟の門は閉じているものとして囲う
        List<BlockPos> hubSeeds = List.of(new BlockPos(0, 0, 6), new BlockPos(6, 0, -6), new BlockPos(-6, 0, -6), new BlockPos(14, 0, 8));
        FIELD.put("hub", HUB_FLOOR + 1, TideField.flood(level, -HUB_R - 1, 26, -HUB_R - 1, 20, hubSeeds, HUB_FLOOR + 1, 3,
                p -> p.getZ() <= FLATS_GATE_Z || (Math.abs(p.getX()) <= BOTTOM_GATE_R && Math.abs(p.getZ()) <= BOTTOM_GATE_R && p.getY() <= HUB_FLOOR)));
        initGardenPool(level);
        // 干潟 (床 63 の上の 2 段)。干潟の口の階段の外へは出ない
        List<BlockPos> flatSeeds = List.of(new BlockPos(0, 0, -85 + FLATS_DZ), new BlockPos(-20, 0, -100 + FLATS_DZ), new BlockPos(20, 0, -76 + FLATS_DZ));
        FIELD.put("flats", FLATS_FLOOR + 1, TideField.flood(level, FLATS.x1(), FLATS.x2(), FLATS.z1(), FLATS.z2(), flatSeeds, FLATS_FLOOR + 1, FLATS_DEPTH,
                p -> false));
        ready = true;
    }

    static void initGardenPool(ServerLevel level) {
        List<BlockPos> seeds = new ArrayList<>();
        for (Channel c : Channel.values()) {
            List<BlockPos> cells = TideIslandBuilder.channelCells(c);
            seeds.add(cells.get(cells.size() / 2));
        }
        seeds.add(new BlockPos(CLAM[0] + 3, 0, CLAM[1]));
        FIELD.put("garden", GARDEN_FLOOR + 1, TideField.flood(level, GARDEN.x1(), GARDEN.x2(), GARDEN.z1(), GARDEN.z2(), seeds, GARDEN_FLOOR + 1,
                GARDEN_DEPTH, p -> false));
    }

    // 目標の段の数
    static int target(ServerLevel level, TideField.Pool p) {
        TideBackState state = TideBackState.get(level);
        double full = TideClock.fullness(level);
        return switch (p.id) {
            case "hub" -> state.bottomOpen() || state.rungCount() >= 3 ? 0 : TideClock.levelFor(full, 3);
            case "flats" -> Math.max(TideClock.levelFor(full, FLATS_DEPTH), TideFlats.beckonLevel());
            default -> TideClock.levelFor(full, p.depth());
        };
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        TitanBackData data = TitanBackData.get(level);
        if (!data.isBuilt() || data.needsBuild(LAYOUT_VERSION)) {
            return;
        }
        TideBackState state = TideBackState.get(level);
        boolean anyone = level.players().stream().anyMatch(p -> !p.isSpectator());
        if (level.getGameTime() % 10 == 0) {
            tickReset(level, data, state, anyone);
        }
        if (!anyone) {
            return;
        }
        if (!ready) {
            initPools(level);
            TideGarden.reset(level, state);
            TideWatch.reset(level, state);
            TideFlats.reset(level, state);
            applyStones(level, state);
        }
        TideClock.advance(level);
        tickHorns(level, state);
        FIELD.tick(level, p -> target(level, p));
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        TideGarden.tick(level, state, peaceful);
        if (!peaceful) {
            TideWatch.tick(level, state, false);
        }
        TideFlats.tick(level, state, peaceful);
        if (level.getGameTime() % 5 == 0) {
            tickGates(level, state);
            tickPads(level, state);
            tickPeaceful(level, peaceful);
            tickFrost(level);
        }
        TideClock.Phase phase = TideClock.phase(level);
        if (phase != lastPhase || level.getGameTime() % 20 == 0) {
            lastPhase = phase;
            sendGauge(level);
        }
    }

    // 満ちはじめる 10 秒前に低い潮鳴り、引きはじめる 10 秒前に高い音 (島じゅう)
    private static void tickHorns(ServerLevel level, TideBackState state) {
        if (TideClock.forced() != null) {
            return;
        }
        int t = state.clock();
        boolean low = t == TideClock.LOW_TICKS - TideClock.WARN_TICKS;
        boolean high = t == TideClock.LOW_TICKS + TideClock.RISE_TICKS + TideClock.HIGH_TICKS - TideClock.WARN_TICKS;
        if (!low && !high) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            level.playSound(null, p.getX(), p.getY() + 8, p.getZ(), low ? TideBackSetup.HORN_LOW.get() : TideBackSetup.HORN_HIGH.get(),
                    SoundSource.AMBIENT, 1.4f, 1f);
            Notice.bar(p, ModText.lore(low ? "message.skyabyss.tide.rising_soon" : "message.skyabyss.tide.falling_soon"));
        }
    }

    // 干潟の門: 干潮のあいだ (広間の水が引ききってから) だけ開く。潮招きの戦いの間は閉じたまま
    private static void tickGates(ServerLevel level, TideBackState state) {
        TideField.Pool hub = FIELD.pool("hub");
        boolean open = TideClock.phase(level) == TideClock.Phase.LOW && hub != null && hub.level() == 0 && !TideFlats.fightSealed();
        boolean changed = false;
        for (int x = -1; x <= 1; x++) {
            for (int y = HUB_FLOOR + 1; y <= HUB_FLOOR + 3; y++) {
                BlockPos p = new BlockPos(x, y, FLATS_GATE_Z);
                BlockState s = level.getBlockState(p);
                if (open && s.is(ModBlocks.SEALED_GATE.get())) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    changed = true;
                } else if (!open && !s.is(ModBlocks.SEALED_GATE.get())) {
                    // 戸口に立っている人は、広間の側へ出す
                    for (ServerPlayer pl : level.getEntitiesOfClass(ServerPlayer.class, new AABB(p))) {
                        pl.teleportTo(pl.getX(), pl.getY(), FLATS_GATE_Z + 1.6);
                    }
                    level.setBlock(p, ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
                    changed = true;
                }
            }
        }
        if (changed) {
            level.playSound(null, 0.5, HUB_FLOOR + 2, FLATS_GATE_Z, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.6f);
        }
        // 潮の底の門: 三つの石が鳴り、水盤が引ききったら開く (以後リセットまで水盤は満ちない)
        if (!state.bottomOpen() && state.rungCount() >= 3 && hub != null && hub.level() == 0) {
            state.markBottomOpen();
            openBottom(level, true);
            for (ServerPlayer p : level.players()) {
                Notice.narrate(p, "message.skyabyss.tide.bottom_opened");
            }
            level.playSound(null, 0.5, HUB_FLOOR + 1, 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.6f);
            level.playSound(null, 0.5, HUB_FLOOR + 1, 0.5, jp.revati.skyabyss.titan.tide.TideSetup.SONG.get(), SoundSource.AMBIENT, 3f, 1.1f);
        }
    }

    // 自己テスト用
    static void tickGatesForTest(ServerLevel level, TideBackState state) {
        tickGates(level, state);
    }

    static void tickPeacefulForTest(ServerLevel level, boolean peaceful) {
        tickPeaceful(level, peaceful);
    }

    // 潮の底の門の格子 (西の列の 3 マスが沈み、螺旋の階段の上が開く)
    static void openBottom(ServerLevel level, boolean open) {
        for (int z = -1; z <= 1; z++) {
            BlockPos p = new BlockPos(-1, HUB_FLOOR, z);
            level.setBlock(p, open ? Blocks.AIR.defaultBlockState() : ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
            if (open) {
                level.sendParticles(ParticleTypes.BUBBLE_POP, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.05);
            }
        }
        for (int x = 0; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(new BlockPos(x, HUB_FLOOR, z), x == 0 && z == 0 ? TideKit.LAMP : ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
            }
        }
    }

    // 抜け道の床 (石が鳴ると光る。踏むと広間の近くへ)
    private static void tickPads(ServerLevel level, TideBackState state) {
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            BlockPos under = p.blockPosition().below();
            BlockPos to = null;
            if (under.equals(RETURN_WEST) && state.rung(TideBackState.Trial.WEST)) {
                to = RETURN_WEST_TO;
            } else if (under.equals(RETURN_EAST) && state.rung(TideBackState.Trial.EAST)) {
                to = RETURN_EAST_TO;
            } else if (under.equals(RETURN_FRONT) && state.rung(TideBackState.Trial.FRONT)) {
                to = RETURN_FRONT_TO;
            }
            if (to != null) {
                if (TideGarden.holds(p)) {
                    continue;
                }
                p.fallDistance = 0;
                p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
                level.playSound(null, to, jp.revati.skyabyss.titan.tide.TideSetup.GATE.get(), SoundSource.PLAYERS, 1f, 1.3f);
                Notice.bar(p, ModText.hint("message.skyabyss.tide.return_path"));
            }
        }
    }

    // 氷渡りの氷は背中では溶かす (満潮の水の上を歩けないように。8 章)
    private static void tickFrost(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            BlockPos c = p.blockPosition();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy = -2; dy <= 0; dy++) {
                        BlockPos q = c.offset(dx, dy, dz);
                        if (level.getBlockState(q).is(Blocks.FROSTED_ICE)) {
                            level.setBlock(q, Blocks.WATER.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 潮鳴り石
    public static void useStone(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        if (level.dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        TideBackState state = TideBackState.get(level);
        TideBackState.Trial trial = pos.equals(STONE_WEST) ? TideBackState.Trial.WEST
                : pos.equals(STONE_EAST) ? TideBackState.Trial.EAST : pos.equals(STONE_FRONT) ? TideBackState.Trial.FRONT : null;
        if (trial == null) {
            return;
        }
        if (state.rung(trial)) {
            Notice.bar(player, ModText.hint(state.sleeping() == trial ? "message.skyabyss.tide.trial_sleeping" : "message.skyabyss.tide.stone_rung"));
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        switch (trial) {
            case WEST -> {
                if (!TideGarden.placePearl(level, player)) {
                    return;
                }
            }
            case FRONT -> {
                if (!state.beckonerDefeated()) {
                    Notice.bar(player, ModText.deny("message.skyabyss.tide.front_silent"));
                    return;
                }
            }
            default -> {
            }
        }
        ring(level, state, trial, player);
    }

    static void ring(ServerLevel level, TideBackState state, TideBackState.Trial trial, @Nullable ServerPlayer by) {
        state.ring(trial);
        applyStones(level, state);
        BlockPos pos = stonePos(trial);
        level.playSound(null, pos, TideBackSetup.CHIME.get(), SoundSource.BLOCKS, 3f, 1f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);
        if (by != null) {
            Notice.bar(by, ModText.hint("message.skyabyss.tide.return_opened"));
        }
        Component msg = ModText.progress("message.skyabyss.tide.stone_ringing", Component.translatable("trial.skyabyss.tide_" + trial.id()),
                state.rungCount(), 3);
        for (ServerPlayer p : level.players()) {
            Notice.send(p, msg);
        }
        // 試練の踏破の経験値 (石の近くにいた人。仲間には分け前) と、サブクエストの「試練を踏破する」
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(16), p -> p.isAlive() && !p.isSpectator());
        for (ServerPlayer p : near) {
            Growth.addXp(p, (int) Math.round(TRIAL_XP * GrowthRules.levelFalloff(Growth.get(p).level, TideLoot.TIDE_LEVEL)), "growth.skyabyss.reason.tide_trial");
            jp.revati.skyabyss.quest.Quests.signal(p, "tide_trial_" + trial.id());
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, near)) {
            Growth.addXp(mate, (int) Math.round(TRIAL_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, TideLoot.TIDE_LEVEL)),
                    "growth.skyabyss.reason.party");
        }
        jp.revati.skyabyss.quest.Quests.signalWithParty(level, near, jp.revati.skyabyss.quest.free.FreeQuestEvents.TRIAL_CLEARED);
        if (state.rungCount() >= 3) {
            for (ServerPlayer p : level.players()) {
                Notice.narrate(p, "message.skyabyss.tide.all_rung");
            }
        }
    }

    static BlockPos stonePos(TideBackState.Trial t) {
        return switch (t) {
            case WEST -> STONE_WEST;
            case EAST -> STONE_EAST;
            case FRONT -> STONE_FRONT;
        };
    }

    // 三つの石・広間の床の灯り・抜け道の床を、いまの状態に合わせる
    static void applyStones(ServerLevel level, TideBackState state) {
        for (TideBackState.Trial t : TideBackState.Trial.values()) {
            boolean rung = state.rung(t);
            BlockPos s = stonePos(t);
            BlockState want = TideBackSetup.CHIME_STONE.get().defaultBlockState().setValue(TideChimeStoneBlock.ACTIVE, rung);
            if (!level.getBlockState(s).equals(want)) {
                level.setBlock(s, want, 3);
            }
            BlockPos lamp = switch (t) {
                case WEST -> LAMP_WEST;
                case EAST -> LAMP_EAST;
                case FRONT -> LAMP_FRONT;
            };
            level.setBlock(lamp, rung ? Blocks.SEA_LANTERN.defaultBlockState() : TideKit.DARK, 3);
            BlockPos pad = switch (t) {
                case WEST -> RETURN_WEST;
                case EAST -> RETURN_EAST;
                case FRONT -> RETURN_FRONT;
            };
            level.setBlock(pad, rung ? TideKit.LAMP : TideKit.DARK, 3);
        }
        BlockPos pearlTop = STONE_WEST.above();
        level.setBlock(pearlTop, state.rung(TideBackState.Trial.WEST) ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
    }

    // ---------------------------------------------------------------- リセット
    private static void tickReset(ServerLevel level, TitanBackData data, TideBackState state, boolean anyone) {
        if (!anyone) {
            idleTicks += 10;
            if (idleTicks >= RESET_AFTER_IDLE && (state.rungCount() > (state.sleeping() != null ? 1 : 0) || data.isHeartDefeated()
                    || state.nestsBrokenAny() || TideGarden.pearlOut())) {
                idleTicks = 0;
                reset(level, false);
            }
        } else {
            idleTicks = 0;
        }
        // 心臓を倒したあと、潮の底の門より奥に誰もいなくなって 30 秒たったら、次の周へ
        if (data.isHeartDefeated()) {
            boolean inside = false;
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && pastBottomGate(p.position())) {
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

    // 試練を最初に戻す (08 の 7.1節)。心臓も眠り直し、試練の番号が進む (古い休み場の記録は無効になる)
    public static void reset(ServerLevel level, boolean announce) {
        reset(level, announce, null, false);
    }

    public static void reset(ServerLevel level, boolean announce, @Nullable TideBackState.Trial forceSleep, boolean noSleep) {
        TitanBackData data = TitanBackData.get(level);
        jp.revati.skyabyss.titan.end.TitanDormancy.requestWake(level.getServer(), jp.revati.skyabyss.progress.Titans.TIDE_SHELL);
        TideHeartBattle.reawaken(level, data);
        TideBackState state = TideBackState.get(level);
        state.reset(level.random, forceSleep, noSleep);
        // 試練の中の人は、潮の広間へ移す
        BlockPos hub = RESTS.get(0).spawn();
        for (ServerPlayer p : List.copyOf(level.players())) {
            boolean inTrial = pastBottomGate(p.position());
            for (Area a : trialAreas()) {
                if (a.aabb().contains(p.position())) {
                    inTrial = true;
                }
            }
            if (inTrial && !p.isSpectator()) {
                p.fallDistance = 0;
                p.teleportTo(level, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, p.getYRot(), 0f);
            }
        }
        applyTrials(level, state);
        initGardenPool(level);
        lastPhase = null;
        if (announce) {
            for (ServerPlayer p : level.players()) {
                Notice.narrate(p, "message.skyabyss.tide.trials_renewed");
                tellSleeping(p, state);
            }
        }
        SkyAbyss.LOGGER.info("Tide back trials reset (sleeping: {}, east config {}, garden route {})",
                state.sleeping() == null ? "none" : state.sleeping().id(), state.eastConfig(), state.gardenRoute());
    }

    static void applyTrials(ServerLevel level, TideBackState state) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(-80, 40, -150, 80, 130, 90), e -> e.getTags().contains(TideMob.TRIAL_TAG)
                && !(e instanceof jp.revati.skyabyss.titan.tide.heart.TideHeart))) {
            e.discard();
        }
        TideGarden.reset(level, state);
        TideWatch.reset(level, state);
        TideFlats.reset(level, state);
        openBottom(level, state.bottomOpen());
        applyStones(level, state);
    }

    static void tellSleeping(ServerPlayer p, TideBackState state) {
        if (state.sleeping() != null) {
            Notice.narrate(p, "message.skyabyss.tide.sleeping_" + state.sleeping().id());
        }
    }

    // ---------------------------------------------------------------- ピースフル
    private static void tickPeaceful(ServerLevel level, boolean peaceful) {
        // くぐり道の封印 (ピースフルのあいだだけ)
        int f = TideIslandBuilder.caveFloor(CAVE_SEAL_Z);
        for (int x = CAVE_X1; x <= CAVE_X2; x++) {
            for (int y = f + 1; y <= f + 4; y++) {
                BlockPos p = new BlockPos(x, y, CAVE_SEAL_Z);
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
                // 浜の側: 浜・くぐり道の封印の手前・潮門と到着の段と廊下・甲羅の上へ上る段・甲羅の上 (外) と頂の塔 (21 章)
                boolean beachSide = BEACH.aabb().inflate(2, 4, 16).contains(p.position())
                        || (p.getZ() > CAVE_SEAL_Z && p.getX() > CAVE_X1 - 2 && p.getX() < CAVE_X2 + 2)
                        || new AABB(GATE_X - 3, SEA - 3, GATE_MOUTH_Z1 - 3, BEACH.x1(), GATE_FLOOR + 12, GATE_MOUTH_Z2 + 3).contains(p.position())
                        || new AABB(TOP_STAIR_X1 - 1, BEACH.floor(), TOP_STAIR_Z1 - 1, BEACH.x1(), TOP_Y + 4, TOP_STAIR_Z1 + 6).contains(p.position())
                        || UPPER_ROOMS.stream().anyMatch(a -> a.aabb().inflate(2, 2, 2).contains(p.position()))
                        || p.position().distanceTo(ARRIVAL.getCenter()) < 14 || TideWhispers.onShellTop(p.blockPosition());
                if (!beachSide) {
                    toArrival(p);
                    Notice.narrate(p, "message.skyabyss.trials_peaceful");
                }
                if (p.tickCount % 40 < 5 && Math.abs(p.getZ() - CAVE_SEAL_Z) < 4 && p.getX() > CAVE_X1 - 2 && p.getX() < CAVE_X2 + 2) {
                    Notice.bar(p, ModText.lore("message.skyabyss.trials_peaceful"));
                }
            }
            if (!wasPeaceful) {
                for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(-80, 40, -150, 80, 130, 90), e -> e.getTags().contains(TideMob.TRIAL_TAG))) {
                    e.discard();
                }
                TideFlats.resetFight(level, false);
            }
        }
        wasPeaceful = peaceful;
    }

    static void toArrival(ServerPlayer p) {
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), ARRIVAL.getX() + 0.5, ARRIVAL.getY(), ARRIVAL.getZ() + 0.5, ARRIVAL_YAW, 0f);
    }

    // ---------------------------------------------------------------- 休み場
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TideTravel.TIDE_BACK || player.tickCount % 5 != 0) {
            return;
        }
        int epoch = TitanBackData.get(player.serverLevel()).epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        BlockPos pos = player.blockPosition();
        for (Rest r : RESTS) {
            if (r.area().contains(pos) || (r.id().equals("hub") && pos.distSqr(r.spawn()) < 25)) {
                if (!r.id().equals(validCheckpoint(tag, epoch))) {
                    tag.putString(TAG_CHECKPOINT, r.id());
                    tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
                    if (!r.id().equals("hub")) {
                        Notice.bar(player, ModText.progress("message.skyabyss.checkpoint",
                                Component.translatable("checkpoint.skyabyss.tide_" + r.id())));
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
        for (Rest r : RESTS) {
            if (r.id().equals(id)) {
                return id;
            }
        }
        return "";
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

    // 休み場の復活位置 (立てる場所を探す)。記録がなければ到着地点
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
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TideTravel.TIDE_BACK || event.isCanceled()) {
            return;
        }
        TideGarden.onDeath(player.serverLevel(), player);
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
        ServerLevel back = player.server.getLevel(TideTravel.TIDE_BACK);
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
        Notice.narrate(player, cp.isEmpty() && tag.contains(TAG_CHECKPOINT) ? "message.skyabyss.rest_point_lost" : "message.skyabyss.revived_on_back");
        if (cp.isEmpty()) {
            clearCheckpoint(player);
        }
    }

    // 乗り込み・地上へ戻る (次元の移動) では休み場の記録を消す。背中で倒れて連れ戻すときだけは残す。
    // 背中を出る人の真珠は大貝へ戻す。乗り込んだ人には、眠っている試練を知らせる
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getFrom() == TideTravel.TIDE_BACK) {
            ServerLevel back = player.server.getLevel(TideTravel.TIDE_BACK);
            if (back != null && TideGarden.holds(player)) {
                TideGarden.strip(player);
                TideGarden.returnPearl(back, "message.skyabyss.tide.pearl_left_garden");
            }
        }
        if (REVIVING.contains(player.getUUID())) {
            return;
        }
        if (event.getFrom() == TideTravel.TIDE_BACK || event.getTo() == TideTravel.TIDE_BACK) {
            clearCheckpoint(player);
            ServerLevel back = player.server.getLevel(TideTravel.TIDE_BACK);
            if (event.getTo() == TideTravel.TIDE_BACK && back != null) {
                persisted(player).putInt(TAG_SEEN_EPOCH, TitanBackData.get(back).epoch());
                tellSleeping(player, TideBackState.get(back));
            }
        }
    }

    // ログイン: 背中にいて、最後に見た試練の番号が古く (その間にリセットされた)、試練の中にいるなら浜へ。真珠は持ち越さない
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        ServerLevel back = player.serverLevel();
        if (TideGarden.holds(player) && !TideGarden.pearlOut()) {
            TideGarden.strip(player);
        }
        int epoch = TitanBackData.get(back).epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            clearCheckpoint(player);
            boolean inTrial = pastBottomGate(player.position());
            for (Area a : trialAreas()) {
                inTrial |= a.aabb().contains(player.position());
            }
            if (player.isAlive() && inTrial) {
                toArrival(player);
                Notice.narrate(player, "message.skyabyss.rest_point_lost");
            }
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // ---------------------------------------------------------------- 焚き火・大貝
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != TideTravel.TIDE_BACK || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        if (TideGarden.useClam(level, pos, player)) {
            event.setCanceled(true);
            return;
        }
        if (level.getBlockState(pos).is(Blocks.SOUL_CAMPFIRE)) {
            event.setCanceled(true);
            if (player.isShiftKeyDown()) {
                if (TideGarden.holds(player)) {
                    TideGarden.strip(player);
                    TideGarden.returnPearl(level, "message.skyabyss.tide.pearl_left_garden");
                }
                clearCheckpoint(player);
                toArrival(player);
                Notice.narrate(player, "message.skyabyss.tide.gave_up");
            } else {
                Notice.bar(player, ModText.hint("message.skyabyss.tide.campfire_hint"));
            }
        }
    }

    // 試練で湧いた敵 (保存しない印つき) が古いワールドに残っていれば、読み込みで消す
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getLevel().dimension() == TideTravel.TIDE_BACK
                && (event.getEntity() instanceof ReefNest || event.getEntity() instanceof TideBeckoner)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        FIELD.clear();
        ready = false;
        idleTicks = 0;
        afterHeartIdle = 0;
        wasPeaceful = false;
        lastPhase = null;
        REVIVING.clear();
        TideGarden.clear();
        TideWatch.clear();
        TideFlats.clear();
        TideClock.force(null);
    }

    // ---------------------------------------------------------------- 潮見の目盛り (画面右上)
    static void sendGauge(ServerLevel level) {
        TideBackNetwork.Gauge g = gauge(level);
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, g);
        }
    }

    public static void sendGaugeNow(ServerLevel level) {
        sendGauge(level);
    }

    static TideBackNetwork.Gauge gauge(ServerLevel level) {
        TideClock.Phase phase = TideClock.phase(level);
        int left = TideClock.ticksLeft(level);
        TideBackState state = TideBackState.get(level);
        return new TideBackNetwork.Gauge(phase.ordinal(), left, (float) TideClock.fullness(level), TideFlats.beckonAlert(), state.rungCount());
    }

    public static Vec3 hubSpawn() {
        return Vec3.atBottomCenterOf(RESTS.get(0).spawn());
    }
}
