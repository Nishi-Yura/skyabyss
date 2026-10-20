package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupEvents;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 潮殻の背の試練の進行 (08 の 3〜4・7 章)。岩鯨の TitanBackTrials と同じ決まりを、潮殻の背の配置で動かす。
// 背中は一つだが、試練の進みはグループ (パーティ、または一人) ごと (TideBackState.Group。08 の 7.5節):
//  - 潮の時計 (TideClock) は島で一つ。潮の溜め (広間の水盤・庭の水路・干潟) は潮で満ち引きする。干潟の門は潮に関わらずいつも開いている
//  - 三つの試練 (TideGarden・TideWatch・TideFlats) は占有する部屋 (TideRooms)。潮鳴り石・広間の灯り・抜け道の床は、
//    鳴らしたグループにだけ光って見える (見かけの上書き GroupOverlays)。抜け道は踏んだ人のグループの石で動く
//  - 三つの石を鳴らしたグループは、潮に関わらず、そのグループにだけ潮の底の門が開く (満潮は泳いで下りる)
//  - 休み場 (hub・g0・g1・s0・f0・heart): 着くと記録、倒れるとそこで目を覚ます (記録はその人のグループの試練の番号つき)。
//    焚き火にしゃがんで触れると浜へ
//  - 倒れても持ち物と経験値は預かる (岩鯨と同じ部品 TitanBackTrials.keepBelongings)
//  - リセットはグループごと: そのグループの誰も島にいなくなって 3 分 (GroupProgress.idleGroups)、
//    またはそのグループが心臓を倒したあと、そのグループの誰も潮の底の門より奥にいないまま 30 秒。
//    心臓を一度でも倒したグループは、2 周目から試練が一つ眠る (TideBackState.Group.reset)
//  - 島に誰もいなくなって 3 分: 部屋の占有をすべて手放し、試練の敵を片付ける (グループの進みはそのまま)
//  - ピースフル: 縁のくぐり道を封じ、試練の中の人は浜へ。試練の敵は消える (潮の時計は景色として動く)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideBackTrials {
    // 島じゅう (試練の敵を片付ける範囲)
    static final AABB ISLAND_BOX = new AABB(-135, 40, -145, 135, 230, 130);
    public static final TideField FIELD = new TideField();
    public static final int RESET_AFTER_IDLE = GroupProgress.IDLE_TICKS;
    public static final int RESET_AFTER_HEART = 20 * 30;
    public static final int TRIAL_XP = 50;
    private static final String TAG_CHECKPOINT = "skyabyss_tide_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_tide_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_tide_epoch";
    private static final String TAG_DIED = "skyabyss_died_on_tide";

    private static boolean ready;
    private static int idleTicks;
    // グループごとの「心臓を倒したあと、潮の底の門より奥に誰もいない」tick
    private static final Map<UUID, Integer> AFTER_HEART = new HashMap<>();
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

    // その人のグループの進み
    public static TideBackState.Group state(ServerLevel level, ServerPlayer player) {
        return TideBackState.of(level, Groups.keyOf(player));
    }

    // その人のグループの試練の番号
    static int epochOf(ServerLevel level, ServerPlayer player) {
        return GroupProgress.epoch(level, Groups.keyOf(player));
    }

    // ---------------------------------------------------------------- 見かけの上書きと、グループが変わったときの後始末
    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GroupOverlays.register(TideBackTrials::overlay);
            GroupOverlays.register(Breath::overlay);
            GroupEvents.addMembershipHook(TideBackTrials::membershipChanged);
            BreathGuide.register();
        });
    }

    // 鳴らしたグループにだけ見せる: 潮鳴り石の光・広間の床の灯り・抜け道の床・西の祭壇の真珠の光
    static void overlay(ServerLevel level, UUID group, java.util.function.BiConsumer<BlockPos, net.minecraft.world.level.block.state.BlockState> out) {
        if (level.dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        BackGroupData.Record record = BackGroupData.get(level).peek(group);
        if (record == null) {
            return;
        }
        TideBackState.Group state = new TideBackState.Group(group, record);
        for (TideBackState.Trial t : TideBackState.Trial.values()) {
            if (!state.rung(t)) {
                continue;
            }
            out.accept(stonePos(t), TideBackSetup.CHIME_STONE.get().defaultBlockState().setValue(TideChimeStoneBlock.ACTIVE, true));
            out.accept(lampPos(t), Blocks.SEA_LANTERN.defaultBlockState());
            out.accept(padPos(t), TideKit.LAMP);
            if (t == TideBackState.Trial.WEST) {
                out.accept(STONE_WEST.above(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
            }
        }
    }

    // グループの鍵が変わった人 (パーティを組んだ・入った・抜けた) が潮殻の背にいる: 記録が変わるので、試練の体内にいれば広間へ移す
    static void membershipChanged(ServerPlayer player) {
        if (player.level().dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // 庭の真珠は持ち越さない (持ち主のグループの真珠なら大貝へ戻す。もうそのグループの人ではないので、ここでは戻すだけ)
        if (TideGarden.holds(player)) {
            TideGarden.strip(player);
            TideGarden.returnPearl(level, "message.skyabyss.tide.pearl_left_garden");
        }
        int epoch = epochOf(level, player);
        CompoundTag tag = persisted(player);
        boolean differs = tag.getInt(TAG_SEEN_EPOCH) != epoch;
        if (inTrialBody(player.position()) && player.isAlive() && !player.isSpectator()) {
            clearCheckpoint(player);
            BlockPos hub = RESTS.get(0).spawn();
            player.fallDistance = 0;
            player.teleportTo(level, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, player.getYRot(), 0f);
            Notice.narrate(player, "message.skyabyss.rest_point_lost");
        } else if (differs) {
            clearCheckpoint(player);
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
        PacketDistributor.sendToPlayer(player, gauge(level, player));
    }

    // 試練の中 (リセットで広間へ移す所): 三つの試練の部屋と、潮の底の門より奥
    static boolean inTrialBody(Vec3 pos) {
        if (pastBottomGate(pos)) {
            return true;
        }
        for (Area a : trialAreas()) {
            if (a.aabb().contains(pos)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 建てたあと・起動のあとの用意
    // 島を建てた直後 (TideBackBuilder から)。扉を既定の形で置き、部屋を最初の形にし、試練の敵を片付ける
    public static void afterBuild(ServerLevel level) {
        TideRooms.releaseAll(level);
        for (Entity e : level.getEntitiesOfClass(Entity.class, ISLAND_BOX, e -> e.getTags().contains(TideMob.TRIAL_TAG)
                && !(e instanceof jp.revati.skyabyss.titan.tide.heart.TideHeart))) {
            e.discard();
        }
        GroupGates.place(level, TideGates.flats(), false);
        GroupGates.place(level, TideGates.bottom(), false);
        GroupGates.place(level, TideGates.bottomFixed(), false);
        GroupGates.place(level, TideGates.mouth(), true);
        for (int k = 0; k < 3; k++) {
            GroupGates.place(level, TideGates.door(k), false);
        }
        GroupGates.place(level, TideGates.cave(), true);
        TideFins.placeGates(level, true);
        // 試練の階の戸 (引いた三つだけ、そのグループに開く。docs/design/29)
        jp.revati.skyabyss.trial.TrialRuntime.installGates(level);
        level.setBlock(new BlockPos(0, HUB_FLOOR, 0), TideKit.LAMP, 3);
        TideGarden.reset(level, null);
        TideWatch.reset(level, null);
        TideFlats.reset(level, null);
        applyStones(level);
        initPools(level);
    }

    // 建て直し (古い配置の島): どのグループの進みも最初に戻す (試練の番号が進み、古い休み場の記録は無効になる)
    public static void resetAllRecords(ServerLevel level) {
        BackGroupData data = BackGroupData.get(level);
        for (UUID id : data.ids()) {
            BackGroupData.Record r = data.peek(id);
            if (r != null) {
                new TideBackState.Group(id, r).reset(level.random, null, true);
            }
        }
    }

    // 潮の溜め (水を置く所) を、いまのブロックから作る
    public static void initPools(ServerLevel level) {
        FIELD.clear();
        // 広間の水盤 (床 70 の上の 3 段)。干潟の門は閉じているものとして囲う (扉はいつもブロックなので、水はそこで止まる)
        List<BlockPos> hubSeeds = List.of(new BlockPos(0, 0, 12), new BlockPos(12, 0, -12), new BlockPos(-12, 0, -12), new BlockPos(20, 0, 14));
        FIELD.put("hub", HUB_FLOOR + 1, TideField.flood(level, -HUB_R - 1, 44, -HUB_R - 1, 30, hubSeeds, HUB_FLOOR + 1, 3,
                p -> p.getZ() <= FLATS_GATE_Z || (Math.abs(p.getX()) <= BOTTOM_GATE_R && Math.abs(p.getZ()) <= BOTTOM_GATE_R && p.getY() <= HUB_FLOOR)));
        initGardenPool(level);
        // 干潟 (床 63 の上の 2 段)。干潟の口の階段の外へは出ない
        List<BlockPos> flatSeeds = List.of(new BlockPos(0, 0, -100), new BlockPos(-30, 0, -110), new BlockPos(30, 0, -95), new BlockPos(0, 0, -125));
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

    // 目標の段の数。潮は島で一つなので、どの溜めも時計だけで決まる (干潟は、持ち主のグループの招き潮でも満ちる)
    static int target(ServerLevel level, TideField.Pool p) {
        double full = TideClock.fullness(level);
        return switch (p.id) {
            case "hub" -> TideClock.levelFor(full, 3);
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
        boolean anyone = level.players().stream().anyMatch(p -> !p.isSpectator());
        if (level.getGameTime() % 10 == 0) {
            tickReset(level, anyone);
        }
        if (!anyone) {
            return;
        }
        if (!ready) {
            TideGates.ensureAll(level);
            initPools(level);
            applyStones(level);
        }
        TideClock.advance(level);
        tickHorns(level);
        FIELD.tick(level, p -> target(level, p));
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        if (level.getGameTime() % 10 == 0) {
            TideRooms.tick(level);
        }
        TideFins.tick(level);
        TideGarden.tick(level, peaceful);
        if (!peaceful) {
            TideWatch.tick(level, false);
        }
        TideFlats.tick(level, peaceful);
        Breath.tick(level, peaceful);
        BreathSummit.tick(level, peaceful);
        if (level.getGameTime() % 5 == 0) {
            tickGates(level);
            tickPads(level);
            tickPeaceful(level, peaceful);
            tickFrost(level);
            TideGuide.tick(level);
        }
        TideClock.Phase phase = TideClock.phase(level);
        if (phase != lastPhase || level.getGameTime() % 20 == 0) {
            lastPhase = phase;
            sendGauge(level);
        }
    }

    // 満ちはじめる 10 秒前に低い潮鳴り、引きはじめる 10 秒前に高い音 (島じゅう。潮は全員のもの)
    private static void tickHorns(ServerLevel level) {
        if (TideClock.forced() != null) {
            return;
        }
        int t = TideBackState.get(level).clock();
        boolean low = t == TideClock.LOW_TICKS - TideClock.WARN_TICKS;
        boolean high = t == TideClock.LOW_TICKS + TideClock.RISE_TICKS + TideClock.HIGH_TICKS - TideClock.WARN_TICKS;
        if (!low && !high) {
            return;
        }
        for (ServerPlayer p : Players.snapshot(level.players())) {
            level.playSound(null, p.getX(), p.getY() + 8, p.getZ(), low ? TideBackSetup.HORN_LOW.get() : TideBackSetup.HORN_HIGH.get(),
                    SoundSource.AMBIENT, 1.4f, 1f);
            Notice.bar(p, ModText.lore(low ? "message.skyabyss.tide.rising_soon" : "message.skyabyss.tide.falling_soon"));
        }
    }

    // 干潟の門: 潮に関わらず、背中にいるどのグループにもいつも開いている (満潮の広間は水の中を泳いでくぐる。潮招きと戦うグループには TideFlats が閉じる)。
    // ワールドのブロックは閉じた扉のままにする (開いた扉は水を通し、満ちた広間の水がトンネルへ漏れる)。開けるのはグループの上書きだけ。
    // 潮の底の門: 三つの石を鳴らしたグループにだけ、潮に関わらず開く (満潮でも水の中を泳いで下りられる)
    private static void tickGates(ServerLevel level) {
        List<BlockPos> flats = TideGates.flats();
        if (!GroupGates.isGate(level, flats.get(0))) {
            GroupGates.place(level, flats, false);
        }
        for (UUID group : presentGroups(level)) {
            if (!TideFlats.flatsSealedFor(group)) {
                GroupGates.open(level, group, flats);
            }
        }
        for (UUID group : presentGroups(level)) {
            TideBackState.Group state = TideBackState.of(level, group);
            if (!state.bottomOpen() && state.rungCount() >= 3) {
                state.markBottomOpen();
                openBottom(level, group);
                for (ServerPlayer p : Groups.online(level, group)) {
                    Notice.narrate(p, "message.skyabyss.tide.bottom_opened");
                    notify(p, SoundEvents.BEACON_ACTIVATE, 3f, 0.6f);
                    notify(p, jp.revati.skyabyss.titan.tide.TideSetup.SONG.get(), 3f, 1.1f);
                }
            } else if (state.bottomOpen() && !GroupGates.isOpen(level, group, TideGates.bottom())) {
                openBottom(level, group);
            }
        }
    }

    // その人にだけ鳴らす
    static void notify(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.playNotifySound(sound, SoundSource.BLOCKS, volume, pitch);
    }

    // 背中にいる人 (観戦者を除く) のグループ
    static List<UUID> presentGroups(ServerLevel level) {
        List<UUID> list = new ArrayList<>();
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isSpectator()) {
                continue;
            }
            UUID g = Groups.keyOf(p);
            if (!list.contains(g)) {
                list.add(g);
            }
        }
        return list;
    }

    // 自己テスト用
    static void tickGatesForTest(ServerLevel level) {
        tickGates(level);
    }

    static void tickPeacefulForTest(ServerLevel level, boolean peaceful) {
        tickPeaceful(level, peaceful);
    }

    // 潮の底の門の格子 (西の列の 3 マス) を、そのグループにだけ沈める
    static void openBottom(ServerLevel level, UUID group) {
        GroupGates.open(level, group, TideGates.bottom());
    }

    // 抜け道の床 (石が鳴ると光る。踏むと広間の近くへ)。踏んだ人のグループの石で決める
    private static void tickPads(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            BlockPos under = p.blockPosition().below();
            TideBackState.Trial trial = under.equals(RETURN_WEST) ? TideBackState.Trial.WEST
                    : under.equals(RETURN_EAST) ? TideBackState.Trial.EAST : under.equals(RETURN_FRONT) ? TideBackState.Trial.FRONT : null;
            if (trial == null || !state(level, p).rung(trial)) {
                continue;
            }
            BlockPos to = switch (trial) {
                case WEST -> RETURN_WEST_TO;
                case EAST -> RETURN_EAST_TO;
                case FRONT -> RETURN_FRONT_TO;
            };
            if (TideGarden.holds(p)) {
                continue;
            }
            p.fallDistance = 0;
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
            level.playSound(null, to, jp.revati.skyabyss.titan.tide.TideSetup.GATE.get(), SoundSource.PLAYERS, 1f, 1.3f);
            Notice.bar(p, ModText.hint("message.skyabyss.tide.return_path"));
        }
    }

    // 氷渡りの氷は背中では溶かす (満潮の水の上を歩けないように。8 章)
    private static void tickFrost(ServerLevel level) {
        for (ServerPlayer p : Players.snapshot(level.players())) {
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

    // ---------------------------------------------------------------- 潮鳴り石 (触れた人のグループの石)
    public static void useStone(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        if (level.dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        // 試練のプールの新作の石 (試練の階。docs/design/29): プールが扱う
        if (jp.revati.skyabyss.trial.TrialRuntime.useStone(level, player, pos) != null) {
            return;
        }
        TideBackState.Group state = state(level, player);
        TideBackState.Trial trial = pos.equals(STONE_WEST) ? TideBackState.Trial.WEST
                : pos.equals(STONE_EAST) ? TideBackState.Trial.EAST : pos.equals(STONE_FRONT) ? TideBackState.Trial.FRONT : null;
        if (trial == null) {
            return;
        }
        if (state.rung(trial)) {
            jp.revati.skyabyss.titan.group.TrialSteles.tellWho(player, jp.revati.skyabyss.titan.group.TrialSteles.TIDE, trial.id());
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

    // 試練のプールの新作の石 (引いていない今週の試練): そのグループの人にだけ鳴らして光らせる
    public static void notifyGroup(ServerLevel level, UUID group, BlockPos pos) {
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 48 * 48) {
                notify(p, TideBackSetup.CHIME.get(), 3f, 1f);
                level.sendParticles(p, ParticleTypes.END_ROD, false, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);
            }
        }
    }

    // 石を鳴らす (そのグループの記録に書き、そのグループの人にだけ光って見せる)
    public static void ring(ServerLevel level, TideBackState.Group state, TideBackState.Trial trial, @Nullable ServerPlayer by) {
        state.ring(trial);
        GroupOverlays.refresh(level, state.id);
        BlockPos pos = stonePos(trial);
        for (ServerPlayer p : Groups.online(level, state.id)) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 48 * 48) {
                notify(p, TideBackSetup.CHIME.get(), 3f, 1f);
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);
        if (by != null) {
            Notice.bar(by, ModText.hint("message.skyabyss.tide.return_opened"));
        }
        Component msg = ModText.progress("message.skyabyss.tide.stone_ringing", Component.translatable("trial.skyabyss.tide_" + trial.id()),
                state.rungCount(), 3);
        Groups.tell(level, state.id, msg);
        // 試練の踏破の経験値 (石の近くにいた、そのグループの人。仲間には分け前) と、サブクエストの「試練を踏破する」
        List<ServerPlayer> near = Groups.inBox(level, state.id, new AABB(pos).inflate(16));
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
        jp.revati.skyabyss.titan.group.TrialSteles.onRung(level, state.id, jp.revati.skyabyss.titan.group.TrialSteles.TIDE, trial.id(), null, by);
        if (state.rungCount() >= 3) {
            for (ServerPlayer p : Groups.online(level, state.id)) {
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

    static BlockPos lampPos(TideBackState.Trial t) {
        return switch (t) {
            case WEST -> LAMP_WEST;
            case EAST -> LAMP_EAST;
            case FRONT -> LAMP_FRONT;
        };
    }

    static BlockPos padPos(TideBackState.Trial t) {
        return switch (t) {
            case WEST -> RETURN_WEST;
            case EAST -> RETURN_EAST;
            case FRONT -> RETURN_FRONT;
        };
    }

    // 三つの石・広間の床の灯り・抜け道の床を、ワールドでは「まだ鳴っていない」形にする (鳴らしたグループには見かけの上書きで見せる)
    static void applyStones(ServerLevel level) {
        for (TideBackState.Trial t : TideBackState.Trial.values()) {
            net.minecraft.world.level.block.state.BlockState want = TideBackSetup.CHIME_STONE.get().defaultBlockState().setValue(TideChimeStoneBlock.ACTIVE, false);
            if (!level.getBlockState(stonePos(t)).equals(want)) {
                level.setBlock(stonePos(t), want, 3);
            }
            level.setBlock(lampPos(t), TideKit.DARK, 3);
            level.setBlock(padPos(t), TideKit.DARK, 3);
        }
        level.setBlock(STONE_WEST.above(), Blocks.AIR.defaultBlockState(), 3);
    }

    // ---------------------------------------------------------------- リセット (グループごと)
    private static void tickReset(ServerLevel level, boolean anyone) {
        if (!anyone) {
            idleTicks += 10;
            if (idleTicks == RESET_AFTER_IDLE) {
                cleanupWorld(level);
            }
        } else {
            idleTicks = 0;
        }
        // そのグループの誰も島にいないまま 3 分たち、試練が進んでいたグループ
        for (UUID group : GroupProgress.idleGroups(level)) {
            BackGroupData.Record r = BackGroupData.get(level).peek(group);
            if (r != null && new TideBackState.Group(group, r).hasTrialProgress()) {
                resetGroup(level, group, false, null, false);
            }
        }
        // 心臓を倒したグループ: そのグループの誰も潮の底の門より奥にいなくなって 30 秒たったら、次の周へ
        BackGroupData data = BackGroupData.get(level);
        for (UUID group : data.ids()) {
            BackGroupData.Record r = data.peek(group);
            if (r == null || !r.isHeartDefeated()) {
                AFTER_HEART.remove(group);
                continue;
            }
            boolean inside = false;
            for (ServerPlayer p : Groups.online(level, group)) {
                if (!p.isSpectator() && pastBottomGate(p.position())) {
                    inside = true;
                    break;
                }
            }
            int t = inside ? 0 : AFTER_HEART.getOrDefault(group, 0) + 10;
            AFTER_HEART.put(group, t);
            if (t >= RESET_AFTER_HEART) {
                resetGroup(level, group, true, null, false);
            }
        }
    }

    // 島に誰もいなくなって久しい: 部屋の占有をすべて手放し、残った試練の敵を片付ける (グループの進みはそのまま)
    static void cleanupWorld(ServerLevel level) {
        TideRooms.releaseAll(level);
        TideHeartBattle.releaseAll(level);
        for (Entity e : level.getEntitiesOfClass(Entity.class, ISLAND_BOX, e -> e.getTags().contains(TideMob.TRIAL_TAG))) {
            e.discard();
        }
        lastPhase = null;
    }

    // そのグループの試練を最初に戻す (08 の 7.1節)。心臓も眠り直し、試練の番号が進む (古い休み場の記録は無効になる)。
    // ほかのグループの進み・部屋・敵・人には触れない
    public static void resetGroup(ServerLevel level, UUID group, boolean announce, @Nullable TideBackState.Trial forceSleep, boolean noSleep) {
        // そのグループの試練が元に戻る = 鼓動が戻る。眠っている巨神は、最短の長さを過ぎていれば目覚める (titan/end)
        jp.revati.skyabyss.titan.end.TitanDormancy.requestWake(level.getServer(), jp.revati.skyabyss.progress.Titans.TIDE_SHELL);
        TideHeartBattle.reawaken(level, group);
        TideRooms.releaseGroup(level, group);
        TideBackState.Group state = TideBackState.of(level, group);
        state.reset(level.random, forceSleep, noSleep);
        // 試練のプールのセル (引きは残る。動かしていたセルと戸の上書きを戻す)
        jp.revati.skyabyss.trial.TrialRuntime.onGroupReset(level, group);
        GroupOverlays.refresh(level, group);
        AFTER_HEART.remove(group);
        // そのグループの試練の敵 (印のある個体) を片付ける
        for (Entity e : level.getEntitiesOfClass(Entity.class, ISLAND_BOX,
                e -> e.getTags().contains(TideMob.TRIAL_TAG) && e instanceof GroupOwned o && group.equals(o.groupId()))) {
            e.discard();
        }
        // 試練の中の、そのグループの人は、潮の広間へ移す
        BlockPos hub = RESTS.get(0).spawn();
        for (ServerPlayer p : Groups.online(level, group)) {
            if (inTrialBody(p.position()) && !p.isSpectator()) {
                p.fallDistance = 0;
                p.teleportTo(level, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, p.getYRot(), 0f);
            }
            persisted(p).putInt(TAG_SEEN_EPOCH, state.epoch());
        }
        if (announce) {
            for (ServerPlayer p : Groups.online(level, group)) {
                Notice.narrate(p, "message.skyabyss.tide.trials_renewed");
                tellQuiet(p, state);
            }
        }
        SkyAbyss.LOGGER.info("Tide back trials reset for group {} (quiet: {}, east config {}, garden route {})", group,
                state.quiet() == null ? "none" : state.quiet().id(), state.eastConfig(), state.gardenRoute());
    }

    // 記録のあるグループをすべて戻す (コマンドで、人を指さないとき)
    public static void resetAllGroups(ServerLevel level, boolean announce, @Nullable TideBackState.Trial forceSleep, boolean noSleep) {
        for (UUID id : BackGroupData.get(level).ids()) {
            resetGroup(level, id, announce, forceSleep, noSleep);
        }
    }

    static void tellQuiet(ServerPlayer p, TideBackState.Group state) {
        if (state.quiet() != null) {
            Notice.narrate(p, "message.skyabyss.tide.quiet_" + state.quiet().id());
        }
    }

    // ---------------------------------------------------------------- ピースフル
    private static void tickPeaceful(ServerLevel level, boolean peaceful) {
        // くぐり道の封印 (ピースフルのあいだだけ。難しさは島で一つなので、ワールドの既定)
        List<BlockPos> cave = TideGates.cave();
        if (TideGates.defaultOpen(level, cave.get(0)) == peaceful || !GroupGates.isGate(level, cave.get(0))) {
            GroupGates.setDefault(level, cave, !peaceful);
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
                        || new AABB(UPPER_GATE_X - 3, UPPER_1.floor(), UPPER_GATE_Z - 4, UPPER_HALL_X2 + 2, UPPER_1.floor() + 9, UPPER_GATE_Z + 5).contains(p.position())
                        || Breath.activeBox().inflate(2).contains(p.position()) || BreathGuide.nearDoor(p.position())
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
                for (Entity e : level.getEntitiesOfClass(Entity.class, ISLAND_BOX, e -> e.getTags().contains(TideMob.TRIAL_TAG))) {
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

    // ---------------------------------------------------------------- 休み場 (記録の番号は、その人のグループの試練の番号)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TideTravel.TIDE_BACK || player.tickCount % 5 != 0) {
            return;
        }
        int epoch = epochOf(player.serverLevel(), player);
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

    // 倒れて目覚める休み場。その休み場が戦いの部屋の前室で、そのグループがいま戦っている最中なら、鼓動の間 (hub) にする
    // (岩鯨の背と同じ。戦場のすぐそばで目覚めないように。02 の 7.3、brushup P1-1)
    public static String respawnCheckpoint(java.util.UUID group, String cp) {
        return isFightNear(group, cp) ? "hub" : cp;
    }

    static boolean isFightNear(java.util.UUID group, String cp) {
        return switch (cp) {
            case "heart" -> jp.revati.skyabyss.titan.tide.heart.TideHeartBattle.fighting(group);
            default -> false;
        };
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
        int epoch = epochOf(back, player);
        String cp = validCheckpoint(tag, epoch);
        BlockPos spawn = cp.isEmpty() ? ARRIVAL : restSpawn(back, respawnCheckpoint(Groups.keyOf(player), cp));
        REVIVING.add(player.getUUID());
        try {
            player.teleportTo(back, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
        } finally {
            REVIVING.remove(player.getUUID());
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
        jp.revati.skyabyss.titan.group.RespawnGuard.begin(player);
        Notice.narrate(player, cp.isEmpty() && tag.contains(TAG_CHECKPOINT) ? "message.skyabyss.rest_point_lost" : "message.skyabyss.revived_on_back");
        if (cp.isEmpty()) {
            clearCheckpoint(player);
        }
    }

    // 乗り込み・地上へ戻る (次元の移動) では休み場の記録を消す。背中で倒れて連れ戻すときだけは残す。
    // 背中を出る人の真珠は取り上げる (庭の持ち主のグループの真珠なら大貝へ戻す)。乗り込んだ人には、そのグループの眠っている試練を知らせる
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getFrom() == TideTravel.TIDE_BACK) {
            ServerLevel back = player.server.getLevel(TideTravel.TIDE_BACK);
            if (back != null) {
                TideGarden.dropOut(back, player, TideRooms.owns(TideRooms.GARDEN, player));
            }
        }
        if (REVIVING.contains(player.getUUID())) {
            return;
        }
        if (event.getFrom() == TideTravel.TIDE_BACK || event.getTo() == TideTravel.TIDE_BACK) {
            clearCheckpoint(player);
            ServerLevel back = player.server.getLevel(TideTravel.TIDE_BACK);
            if (event.getTo() == TideTravel.TIDE_BACK && back != null) {
                persisted(player).putInt(TAG_SEEN_EPOCH, epochOf(back, player));
                tellQuiet(player, state(back, player));
            }
        }
    }

    // ログイン: 背中にいて、最後に見た試練の番号 (その人のグループの) が違い (その間にリセットされた・グループが変わった)、
    // 試練の中にいるなら浜へ。真珠は持ち越さない (いまの庭の持ち主のグループの、取り出し中の真珠でなければ取り上げる)
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        ServerLevel back = player.serverLevel();
        if (TideGarden.holds(player) && !(TideGarden.pearlOut() && TideRooms.owns(TideRooms.GARDEN, player))) {
            TideGarden.strip(player);
        }
        int epoch = epochOf(back, player);
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            clearCheckpoint(player);
            if (player.isAlive() && inTrialBody(player.position())) {
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
        if (BreathSummit.useBlock(level, pos, player)) {
            event.setCanceled(true);
            return;
        }
        if (level.getBlockState(pos).is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get())) {
            event.setCanceled(true);
            if (player.isShiftKeyDown()) {
                TideGarden.dropOut(level, player, TideRooms.owns(TideRooms.GARDEN, player));
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
                && (event.getEntity() instanceof ReefNest || event.getEntity() instanceof TideBeckoner || event.getEntity() instanceof PearlKeeper)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        FIELD.clear();
        ready = false;
        idleTicks = 0;
        AFTER_HEART.clear();
        wasPeaceful = false;
        lastPhase = null;
        REVIVING.clear();
        TideRooms.forget();
        TideGarden.clear();
        TideWatch.clear();
        TideGuide.clear();
        TideFlats.clear();
        Breath.clear();
        BreathSummit.clear();
        TideClock.force(null);
    }

    // ---------------------------------------------------------------- 潮見の目盛り (画面右上。鳴った石の数と招き潮は、その人のグループのもの)
    static void sendGauge(ServerLevel level) {
        for (ServerPlayer p : Players.snapshot(level.players())) {
            PacketDistributor.sendToPlayer(p, gauge(level, p));
        }
    }

    public static void sendGaugeNow(ServerLevel level) {
        sendGauge(level);
    }

    static TideBackNetwork.Gauge gauge(ServerLevel level, ServerPlayer player) {
        TideClock.Phase phase = TideClock.phase(level);
        int left = TideClock.ticksLeft(level);
        TideBackState.Group state = state(level, player);
        boolean alert = TideFlats.beckonAlert() && TideRooms.owns(TideRooms.FLATS, player);
        TideGuide.Goal goal = TideGuide.goalFor(level, player);
        return new TideBackNetwork.Gauge(phase.ordinal(), left, (float) TideClock.fullness(level), alert, state.rungCount(), goal.id(), goal.done(),
                goal.total(), goal.a(), goal.b(), goal.c());
    }

    public static Vec3 hubSpawn() {
        return Vec3.atBottomCenterOf(RESTS.get(0).spawn());
    }
}
