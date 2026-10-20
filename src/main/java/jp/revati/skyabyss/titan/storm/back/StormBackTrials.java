package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
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
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.heart.StormHeartBattle;
import jp.revati.skyabyss.titan.storm.raiju.RaijuBattle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
//  - 嵐の拍 (StormBackClock) を進め、荒れる・静まるの 10 秒前に島じゅうに音を鳴らす (拍は全員に同じ)
//  - 三つの試練 (StormWest・StormEast・EyeMirrors) と風鳴り石。三つが鳴ると広間の床の竜骨の門が開く
//  - 休み場 (hub・w0・w1・e0・e1・f0・heart): 着くと記録、倒れるとそこで目を覚ます (記録はグループの試練の番号つき)。焚き火にしゃがんで触れると尾の間へ
//  - 倒れても持ち物と経験値は預かる (岩鯨と同じ部品 TitanBackTrials.keepBelongings)
//  - ピースフル: 腰の回廊を封じ、体の中の人は到着へ。試練の敵は消える (嵐の拍は景色として動く)
//
// グループ (docs/design/10 の 9 章): 背中は一つで、進みはグループ (パーティ、または一人) ごと (StormProgress)。
//  - 扉 (封印の扉のブロック。いつも置いてある): 竜骨の門の栓・東の骨の戸 3 枚・嘴の小部屋の戸は、既定で閉じていて、
//    そのグループの進みが開けた所だけ、そのグループに開く (GroupGates)。ピースフルの封印は全員に同じ (既定を変える)
//  - 見かけ (当たり判定の変わらない物): 鳴った風鳴り石・広間の床の灯り・抜け道の床は、グループの記録から各人の画面に出す (overlay)
//  - 部屋の占有 (RoomLocks): 眼の間 (鏡と受け羽) と東の回廊 (柱の段・招雷・予告線) は一度に一つのグループだけ。
//    竜骨の間 (心臓) と雷獣の巣は StormHeartBattle・RaijuBattle が持つ
//  - リセットはグループごと: そのグループのだれも背中にいないまま 3 分 (GroupProgress.idleGroups)、
//    または心臓を倒したあと、そのグループのだれも竜骨の門より奥にいないまま 30 秒。2 周目から試練が一つ眠る
//  - 背中にだれもいなくなって 3 分: 背中そのものを片付ける (試練の敵・雷・部屋の占有、風切りの骨の道を引き直す)
//  - パーティを組む・入る・抜けると記録が変わる: 体の中 (試練の中) にいる人は広間へ移す (onMembershipChanged)
//
// ほかの担当へ: 試練で湧かせた敵には TRIAL_TAG とグループ (GroupOwned.setGroupId) を付けると、そのグループのリセットとピースフルで消える。
// 雷獣を倒したら onRaijuDefeated(level, group) を呼ぶ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormBackTrials {
    public static final String TRIAL_TAG = "skyabyss_storm_trial";
    public static final int RESET_AFTER_IDLE = GroupProgress.IDLE_TICKS;
    public static final int RESET_AFTER_HEART = 20 * 30;
    public static final int TRIAL_XP = 60;
    // 雷翼の推奨レベル (12 の 6.6節)
    public static final int STORM_LEVEL = 24;
    // 部屋の占有の名前
    public static final String EYE_ROOM = "storm.eye";
    public static final String EAST_ROOM = "storm.east";
    private static final String TAG_CHECKPOINT = "skyabyss_storm_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_storm_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_storm_epoch";
    private static final String TAG_DIED = "skyabyss_died_on_storm";
    private static final AABB BODY = new AABB(-330, 100, -190, 330, 200, 200);

    private static boolean ready;
    // 背中にだれもいない時間 (背中そのものの片付け)
    private static int idleTicks;
    // 心臓を倒したグループの、竜骨の門より奥にだれもいない時間
    private static final Map<UUID, Integer> AFTER_HEART = new HashMap<>();
    private static boolean wasPeaceful;
    private static StormBackClock.Phase lastPhase;
    private static final Set<UUID> REVIVING = new HashSet<>();

    static {
        GroupEvents.addMembershipHook(StormBackTrials::onMembershipChanged);
        GroupOverlays.register(StormBackTrials::overlay);
    }

    private static final RoomLocks.Listener EYE_LISTENER = new RoomLocks.Listener() {
        @Override
        public void acquired(ServerLevel level, UUID group) {
            EyeMirrors.apply(level, StormProgress.of(level, group));
        }

        @Override
        public void released(ServerLevel level, UUID formerOwner) {
            EyeMirrors.applyNeutral(level);
        }
    };

    private static final RoomLocks.Listener EAST_LISTENER = new RoomLocks.Listener() {
        @Override
        public void acquired(ServerLevel level, UUID group) {
            StormEast.apply(level, StormProgress.of(level, group));
        }

        @Override
        public void released(ServerLevel level, UUID formerOwner) {
            StormEast.applyNeutral(level);
        }
    };

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

    // その人のグループの試練の番号 (休み場の記録の番号)
    static int epochOf(ServerLevel back, ServerPlayer player) {
        return GroupProgress.epoch(back, Groups.keyOf(player));
    }

    // 嵐に投げ返されたときに戻す所 (最後の休み場、なければ到着)
    public static BlockPos fallbackPoint(ServerPlayer player) {
        ServerLevel back = player.server.getLevel(StormTravel.STORM_BACK);
        if (back == null) {
            return ARRIVAL;
        }
        String cp = validCheckpoint(persisted(player), epochOf(back, player));
        return cp.isEmpty() ? ARRIVAL : restSpawn(back, cp);
    }

    // 試練の中にいるか (嵐に投げ返されたときの傷が 3 になる)
    public static boolean inTrial(ServerPlayer player) {
        return StormBackLayout.inTrial(player.position());
    }

    // ---------------------------------------------------------------- 建てたあと・起動のあと
    // 建て直したあと: 全グループの進みを戻し (形が変わったので)、背中そのものを置き直す
    public static void afterBuild(ServerLevel level) {
        for (UUID id : BackGroupData.get(level).ids()) {
            resetGroup(level, id, false, null, true, false);
        }
        applyWorld(level);
        ready = true;
    }

    // 背中そのもの (全員に同じ所) を置き直す。何度呼んでも同じ: 風切りの骨の道、竜骨の門の下の形、扉のブロック、
    // 風鳴り石・灯り・抜け道の床の既定の姿 (まだ鳴っていない)、持ち主のいない部屋
    static void applyWorld(ServerLevel level) {
        registerFirstRun();
        StormExtraRules.registerRods(level);
        StormBirdBuilder.carveWestRoute(new StormKit(level), StormBackState.get(level).westRoute());
        StormBirdBuilder.carveKeel(level);
        placeGates(level);
        // 試練のプール (docs/design/29): 広間の三つの出口の戸と、試練の甲板の四つの戸
        jp.revati.skyabyss.trial.TrialRuntime.installGates(level);
        applyStones(level);
        UUID east = RoomLocks.owner(EAST_ROOM);
        if (east == null) {
            StormEast.applyNeutral(level);
        } else {
            StormEast.apply(level, StormProgress.of(level, east));
        }
        UUID eye = RoomLocks.owner(EYE_ROOM);
        if (eye == null) {
            EyeMirrors.applyNeutral(level);
        } else {
            EyeMirrors.apply(level, StormProgress.of(level, eye));
        }
    }

    // 初回と周回 (21 の 1.7): 肋の間と雷の回路は、周回のグループには既知の道 (道しるべが光る。仕掛けはそのまま。敵は出さない)
    static void registerFirstRun() {
        jp.revati.skyabyss.titan.group.FirstRun.register(jp.revati.skyabyss.titan.group.FirstRun.Room
                .firstRun("storm.west.rib", StormTravel.STORM_BACK, RIB_HALL.aabb())
                .guide(StormRib.guideCells()).guideState(StormKit.LAMP));
        jp.revati.skyabyss.titan.group.FirstRun.register(jp.revati.skyabyss.titan.group.FirstRun.Room
                .firstRun("storm.east.circuit", StormTravel.STORM_BACK, StormEast.room())
                .guide(StormCircuit.solutionCells()).guideState(StormCircuit.B_LIT));
    }

    // ---------------------------------------------------------------- 扉 (グループごと)
    // ピースフルの封印 (腰の回廊。全員に同じ)
    static List<BlockPos> sealBlocks() {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -CORRIDOR_HALF; x <= CORRIDOR_HALF; x++) {
            for (int y = TAIL_HALL.floor() + 1; y <= TAIL_HALL.floor() + 5; y++) {
                out.add(new BlockPos(x, y, SEAL_Z));
            }
        }
        return out;
    }

    // 扉のブロックを置く (既定: 竜骨の門・骨の戸・嘴の戸は閉じている。封印はピースフルのときだけ閉じている)
    static void placeGates(ServerLevel level) {
        GroupGates.place(level, StormBirdBuilder.keelPlug(), false);
        for (int i = 0; i < EAST_DOORS.length; i++) {
            GroupGates.place(level, StormEast.doorBlocks(i), false);
        }
        GroupGates.place(level, EyeMirrors.beakDoor(), false);
        GroupGates.place(level, sealBlocks(), level.getDifficulty() != Difficulty.PEACEFUL);
    }

    // そのグループの扉を、記録のとおりに開け閉めする (リセットのあと・着いたとき・パーティが変わったとき)
    public static void syncGates(ServerLevel level, UUID group) {
        StormProgress p = StormProgress.of(level, group);
        for (int i = 0; i < EAST_DOORS.length; i++) {
            GroupGates.set(level, group, StormEast.doorBlocks(i), p.eastDoorPassable(i));
        }
        GroupGates.set(level, group, EyeMirrors.beakDoor(), p.beakOpen());
        GroupGates.set(level, group, StormBirdBuilder.keelPlug(), p.keelOpen());
    }

    // ---------------------------------------------------------------- 見かけ (グループごと)
    // 鳴った風鳴り石・広間の床の灯り・抜け道の床 (当たり判定は同じ)。ワールドのブロックはまだ鳴っていない姿
    static void overlay(ServerLevel level, UUID group, BiConsumer<BlockPos, BlockState> out) {
        if (level.dimension() != StormTravel.STORM_BACK) {
            return;
        }
        StormRib.overlay(level, group, out);
        StormProgress p = StormProgress.peek(level, group);
        if (p == null) {
            return;
        }
        for (StormBackState.Trial t : StormBackState.Trial.values()) {
            if (p.rung(t)) {
                out.accept(stonePos(t), jp.revati.skyabyss.trial.TrialGoal.reached());
                out.accept(lampPos(t), StormKit.LAMP);
                out.accept(padPos(t), StormKit.LAMP);
            }
        }
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
        boolean anyone = level.players().stream().anyMatch(p -> !p.isSpectator());
        if (level.getGameTime() % 10 == 0) {
            tickReset(level, anyone);
        }
        if (!anyone) {
            return;
        }
        if (!ready) {
            applyWorld(level);
            ready = true;
        }
        long gt = level.getGameTime();
        if (gt % 10 == 0) {
            tickRooms(level);
        }
        StormBackClock.Phase warn = StormBackClock.advance(level);
        if (warn != null) {
            horn(level, warn);
        }
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        StormWest.tick(level, peaceful);
        StormExtraRules.tick(level, peaceful);
        StormRib.tick(level);
        StormEast.tick(level, RoomLocks.owner(EAST_ROOM), peaceful);
        if (gt % 4 == 0) {
            UUID eye = RoomLocks.owner(EYE_ROOM);
            if (eye != null) {
                EyeMirrors.tick(level, StormProgress.of(level, eye));
            }
        }
        if (gt % 5 == 0) {
            tickKeel(level);
            tickPads(level);
            tickPeaceful(level, peaceful);
            tickGateFace(level);
            StormHelm.tick(level);
            StormGuide.tick(level);
        }
        StormBackClock.Phase phase = StormBackClock.phase(level);
        if (phase != lastPhase || gt % 20 == 0) {
            lastPhase = phase;
            sendGauge(level);
        }
    }

    // 眼の間の占有の箱 (眼の間と嘴の小部屋)
    static AABB eyeRoom() {
        return EYE_HALL.aabb().minmax(BEAK_ROOM.aabb());
    }

    // 部屋の占有 (10 tick ごと): 最初に入ったグループが持ち主。ほかのグループは入口の外の休み場へ戻す
    static void tickRooms(ServerLevel level) {
        RoomLocks.update(level, EYE_ROOM, eyeRoom(), rest("f0"), RoomLocks.DEFAULT_GRACE, EYE_LISTENER);
        RoomLocks.update(level, EAST_ROOM, StormEast.room(), rest("e0"), RoomLocks.DEFAULT_GRACE, EAST_LISTENER);
    }

    // 荒れる 10 秒前に低い風の唸り、静まる 10 秒前に遠い高い鳴き声 (島じゅう)
    private static void horn(ServerLevel level, StormBackClock.Phase next) {
        boolean rise = next == StormBackClock.Phase.RISING;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            level.playSound(null, p.getX(), p.getY() + 6, p.getZ(), rise ? StormSetup.RISE_WARN.get() : StormSetup.FALL_WARN.get(),
                    SoundSource.AMBIENT, 1.4f, 1f);
            Notice.bar(p, ModText.lore(rise ? "message.skyabyss.storm.storm_soon" : "message.skyabyss.storm.calm_soon"));
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

    // 背中にいるグループ (重複なし)
    static List<UUID> presentGroups(ServerLevel level) {
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer p : Players.snapshot(level.players())) {
            UUID k = Groups.keyOf(p);
            if (!out.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 竜骨の門 (グループごと)
    private static void tickKeel(ServerLevel level) {
        for (UUID group : presentGroups(level)) {
            StormProgress p = StormProgress.of(level, group);
            if (!p.keelOpen() && p.allRung()) {
                openKeel(level, p);
            }
            if (p.keelOpen() && level.getGameTime() % 10 == 0) {
                // 下から風が吹き上がる (開いたグループにだけ見える)
                StormFx.to(group, () -> StormFx.forced(level, ParticleTypes.CLOUD, 0.5, HUB.floor() + 0.5, HUB_CENTER.getZ() + 0.5, 0, 0, 1, 0, 0.3));
            }
        }
    }

    static void openKeel(ServerLevel level, StormProgress p) {
        p.setKeelOpen(true);
        GroupGates.open(level, p.group, StormBirdBuilder.keelPlug());
        groupSound(level, p.group, HUB_CENTER, SoundEvents.BEACON_ACTIVATE, 3f, 0.6f);
        for (ServerPlayer pl : Groups.online(level, p.group)) {
            Notice.narrate(pl, "message.skyabyss.storm.keel_opened");
        }
    }

    // ---------------------------------------------------------------- 抜け道の床 (石が鳴ると光り、踏むと広間の近くへ。その人のグループの石で)
    private static void tickPads(ServerLevel level) {
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isSpectator()) {
                continue;
            }
            BlockPos below = p.blockPosition().below();
            String to = below.equals(RETURN_WEST) ? "west" : below.equals(RETURN_EAST) ? "east" : below.equals(RETURN_FRONT) ? "front" : null;
            if (to == null) {
                continue;
            }
            StormBackState.Trial t = StormBackState.Trial.byId(to);
            if (t != null && StormProgress.of(level, p).rung(t)) {
                BlockPos dest = RETURN_TO.get(to);
                p.fallDistance = 0;
                p.teleportTo(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, p.getYRot(), p.getXRot());
                level.playSound(null, dest, StormSetup.GALE.get(), SoundSource.PLAYERS, 1f, 1.2f);
                Notice.bar(p, ModText.hint("message.skyabyss.storm.return_path"));
            }
        }
    }

    // ---------------------------------------------------------------- 風鳴り石 (グループごと)
    public static void useStone(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level.dimension() != StormTravel.STORM_BACK) {
            return;
        }
        // 試練の甲板の新作の石 (docs/design/29)
        if (jp.revati.skyabyss.trial.TrialRuntime.useStone(level, player, pos) != null) {
            return;
        }
        StormProgress p = StormProgress.of(level, player);
        StormBackState.Trial trial = pos.equals(WEST_STONE) ? StormBackState.Trial.WEST
                : pos.equals(EAST_STONE) ? StormBackState.Trial.EAST : pos.equals(FRONT_STONE) ? StormBackState.Trial.FRONT : null;
        if (trial == null) {
            return;
        }
        if (p.rung(trial)) {
            if (p.sleeping() == trial) {
                Notice.bar(player, ModText.hint("message.skyabyss.storm.trial_sleeping"));
            } else {
                jp.revati.skyabyss.titan.group.TrialSteles.tellWho(player, jp.revati.skyabyss.titan.group.TrialSteles.STORM, trial.id());
            }
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (trial == StormBackState.Trial.EAST && !p.raijuDefeated()) {
            Notice.bar(player, ModText.deny("message.skyabyss.storm.stone_silent"));
            return;
        }
        ring(level, p, trial, player);
    }

    // 石を鳴らす (そのグループの記録に)。音・粒・知らせ・経験値はそのグループの人へ (石の 16 ブロック以内の人と、いないパーティの仲間)
    public static void ring(ServerLevel level, StormProgress p, StormBackState.Trial trial, @Nullable ServerPlayer by) {
        p.setRung(trial, true);
        GroupOverlays.refresh(level, p.group);
        BlockPos pos = stonePos(trial);
        groupSound(level, p.group, pos, StormSetup.RECEIVER_LIT.get(), 3f, 0.7f);
        groupSound(level, p.group, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, 0.8f);
        StormFx.to(p.group, () -> StormFx.forced(level, ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05));
        if (by != null) {
            Notice.bar(by, ModText.hint("message.skyabyss.storm.return_opened"));
        }
        Component msg = ModText.progress("message.skyabyss.storm.stone_ringing", Component.translatable("trial.skyabyss.storm_" + trial.id()),
                p.rungCount(), 3);
        Groups.tell(level, p.group, msg);
        List<ServerPlayer> near = Groups.inBox(level, p.group, new AABB(pos).inflate(16));
        for (ServerPlayer pl : near) {
            Growth.addXp(pl, (int) Math.round(TRIAL_XP * GrowthRules.levelFalloff(Growth.get(pl).level, STORM_LEVEL)), "growth.skyabyss.reason.storm_trial");
            jp.revati.skyabyss.quest.Quests.signal(pl, "storm_trial_" + trial.id());
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, near)) {
            Growth.addXp(mate, (int) Math.round(TRIAL_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, STORM_LEVEL)),
                    "growth.skyabyss.reason.party");
        }
        jp.revati.skyabyss.quest.Quests.signalWithParty(level, near, jp.revati.skyabyss.quest.free.FreeQuestEvents.TRIAL_CLEARED);
        jp.revati.skyabyss.titan.group.TrialSteles.onRung(level, p.group, jp.revati.skyabyss.titan.group.TrialSteles.STORM, trial.id(), null, by);
        if (p.allRung()) {
            for (ServerPlayer pl : Groups.online(level, p.group)) {
                Notice.narrate(pl, "message.skyabyss.storm.all_rung");
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

    static BlockPos lampPos(StormBackState.Trial t) {
        return switch (t) {
            case WEST -> LAMP_WEST;
            case EAST -> LAMP_EAST;
            case FRONT -> LAMP_FRONT;
        };
    }

    static BlockPos padPos(StormBackState.Trial t) {
        return switch (t) {
            case WEST -> RETURN_WEST;
            case EAST -> RETURN_EAST;
            case FRONT -> RETURN_FRONT;
        };
    }

    // 三つの石・広間の床の灯り・抜け道の床のワールドの姿 (まだ鳴っていない)。鳴った姿はグループごとの見かけ (overlay)
    static void applyStones(ServerLevel level) {
        for (StormBackState.Trial t : StormBackState.Trial.values()) {
            BlockState want = jp.revati.skyabyss.trial.TrialGoal.mark();
            BlockPos s = stonePos(t);
            if (!level.getBlockState(s).equals(want)) {
                level.setBlock(s, want, 3);
            }
            if (!level.getBlockState(lampPos(t)).equals(StormKit.SLATE)) {
                level.setBlock(lampPos(t), StormKit.SLATE, 3);
            }
            if (!level.getBlockState(padPos(t)).equals(StormKit.SLATE)) {
                level.setBlock(padPos(t), StormKit.SLATE, 3);
            }
        }
    }

    // そのグループの人の近くでだけ鳴る音
    static void groupSound(ServerLevel level, UUID group, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        ClientboundSoundPacket packet = new ClientboundSoundPacket(holder, SoundSource.BLOCKS, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                volume, pitch, level.random.nextLong());
        double range = Math.max(16, volume * 16);
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if ((p.isSpectator() || Groups.isMember(p, group)) && p.distanceToSqr(Vec3.atCenterOf(pos)) < range * range && p.connection != null) {
                p.connection.send(packet);
            }
        }
    }

    // 雷獣を倒した (雷獣の担当が、倒したグループで呼ぶ)。そのグループに、巣の真ん中の昇り風が立ち、頂の東の石へ届く
    public static void onRaijuDefeated(ServerLevel level, UUID group) {
        StormProgress.of(level, group).setRaijuDefeated(true);
        for (ServerPlayer p : Groups.online(level, group)) {
            if (inEast(p.position())) {
                Notice.bar(p, ModText.lore("message.skyabyss.raiju.defeated"));
            }
        }
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
        // そのグループのだれも背中にいないまま 3 分
        if (level.getGameTime() % 100 == 0) {
            for (UUID group : GroupProgress.idleGroups(level)) {
                if (StormProgress.of(level, group).needsReset()) {
                    resetGroup(level, group, false, null, false, true);
                }
            }
        }
        // 心臓を倒したあと、そのグループのだれも竜骨の門より奥にいないまま 30 秒
        BackGroupData groups = BackGroupData.get(level);
        for (UUID group : groups.ids()) {
            BackGroupData.Record r = groups.peek(group);
            if (r == null || !r.isHeartDefeated()) {
                AFTER_HEART.remove(group);
                continue;
            }
            boolean inside = false;
            for (ServerPlayer p : Groups.online(level, group)) {
                if (!p.isSpectator() && pastKeel(p.position())) {
                    inside = true;
                    break;
                }
            }
            int t = inside ? 0 : AFTER_HEART.getOrDefault(group, 0) + 10;
            if (t >= RESET_AFTER_HEART) {
                AFTER_HEART.remove(group);
                resetGroup(level, group, true, null, false, true);
            } else {
                AFTER_HEART.put(group, t);
            }
        }
    }

    // 背中にだれもいなくなって 3 分: 背中そのものを片付ける (グループの進みは消さない。グループごとの放置のリセットは別)
    static void cleanupWorld(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, BODY, e -> e.getTags().contains(TRIAL_TAG))) {
            e.discard();
        }
        StormStrike.cancelAll(level);
        for (String room : List.of(EYE_ROOM, EAST_ROOM, StormHeartBattle.ROOM, RaijuBattle.ROOM)) {
            RoomLocks.release(room);
        }
        StormBackState.get(level).reroll(level.random);
        StormHelm.clear();
        StormRib.clear();
        ready = false;
        lastPhase = null;
        SkyAbyss.LOGGER.info("Storm back cleaned up (nobody on the back; west route {})", StormBackState.get(level).westRoute());
    }

    // 試練を最初に戻す (コマンド・建て直し): 記録のあるすべてのグループ
    public static void resetAll(ServerLevel level, boolean announce, @Nullable StormBackState.Trial forceSleep, boolean noSleep) {
        for (UUID id : BackGroupData.get(level).ids()) {
            resetGroup(level, id, announce, forceSleep, noSleep, true);
        }
    }

    // そのグループの試練を最初に戻す (12 の 7.1節)。記録の番号が進み (古い休み場の記録は無効)、心臓も眠り直す。
    // そのグループが持っている部屋を手放し、そのグループの試練の敵と雷を消し、moveOut ならそのグループの試練の中の人を広間へ
    public static void resetGroup(ServerLevel level, UUID group, boolean announce, @Nullable StormBackState.Trial forceSleep, boolean noSleep,
            boolean moveOut) {
        // そのグループの試練が元に戻る = 鼓動が戻る。眠っている巨神は、最短の長さを過ぎていれば目覚める (titan/end)
        jp.revati.skyabyss.titan.end.TitanDormancy.requestWake(level.getServer(), jp.revati.skyabyss.progress.Titans.THUNDER_WING);
        StormProgress p = StormProgress.of(level, group);
        p.reset(level.random, forceSleep, noSleep);
        AFTER_HEART.remove(group);
        StormHelm.clearGroup(level, group);
        EyeMirrors.forget(group);
        if (group.equals(RoomLocks.owner(EYE_ROOM))) {
            RoomLocks.release(EYE_ROOM);
            EyeMirrors.applyNeutral(level);
        }
        if (group.equals(RoomLocks.owner(EAST_ROOM))) {
            RoomLocks.release(EAST_ROOM);
            StormEast.applyNeutral(level);
        }
        StormHeartBattle.onGroupReset(level, group);
        RaijuBattle.onGroupReset(level, group);
        jp.revati.skyabyss.trial.TrialRuntime.onGroupReset(level, group);
        syncGates(level, group);
        GroupOverlays.refresh(level, group);
        if (moveOut) {
            BlockPos hub = rest("hub");
            for (ServerPlayer pl : Groups.online(level, group)) {
                if (!pl.isSpectator() && StormBackLayout.inTrial(pl.position())) {
                    pl.fallDistance = 0;
                    pl.teleportTo(level, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, pl.getYRot(), 0f);
                }
            }
        }
        for (Entity e : level.getEntitiesOfClass(Entity.class, BODY,
                e -> e.getTags().contains(TRIAL_TAG) && e instanceof GroupOwned owned && group.equals(owned.groupId()))) {
            e.discard();
        }
        StormStrike.cancel(level, group);
        if (announce) {
            for (ServerPlayer pl : Groups.online(level, group)) {
                Notice.narrate(pl, "message.skyabyss.storm.trials_renewed");
                tellSleeping(pl, p);
            }
        }
        SkyAbyss.LOGGER.info("Storm back trials reset for group {} (sleeping: {}, mirror config {})", group,
                p.sleeping() == null ? "none" : p.sleeping().id(), p.mirrorConfig());
    }

    static void tellSleeping(ServerPlayer pl, StormProgress p) {
        if (p.sleeping() != null) {
            Notice.narrate(pl, "message.skyabyss.storm.sleeping_" + p.sleeping().id());
        }
    }

    // ---------------------------------------------------------------- パーティが変わった人
    // 記録が変わるので、体の中 (試練の中) にいる人は広間へ移す。休み場の記録の番号が変われば記録を消す。扉を新しい記録に合わせる
    static void onMembershipChanged(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != StormTravel.STORM_BACK) {
            return;
        }
        UUID group = Groups.keyOf(player);
        syncGates(level, group);
        int epoch = GroupProgress.epoch(level, group);
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_CHECKPOINT_EPOCH) != epoch) {
            clearCheckpoint(player);
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
        if (player.isAlive() && !player.isSpectator() && StormBackLayout.inTrial(player.position())) {
            BlockPos hub = rest("hub");
            player.fallDistance = 0;
            player.teleportTo(level, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, player.getYRot(), 0f);
            Notice.narrate(player, "message.skyabyss.storm.party_changed");
        }
        PacketDistributor.sendToPlayer(player, gauge(level, player));
    }

    // ---------------------------------------------------------------- ピースフル (全員に同じ)
    private static void tickPeaceful(ServerLevel level, boolean peaceful) {
        GroupGates.setDefault(level, sealBlocks(), !peaceful);
        if (peaceful) {
            for (ServerPlayer p : List.copyOf(level.players())) {
                if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
                    continue;
                }
                if (pastSeal(p.position()) && !onBackTop(p.position())) {
                    toArrival(p);
                    Notice.narrate(p, "message.skyabyss.storm.peaceful");
                }
                if (p.tickCount % 40 < 5 && Math.abs(p.getZ() - SEAL_Z) < 4 && Math.abs(p.getX()) < 3) {
                    Notice.bar(p, ModText.lore("message.skyabyss.storm.peaceful"));
                }
            }
            if (!wasPeaceful) {
                for (Entity e : level.getEntitiesOfClass(Entity.class, BODY, e -> e.getTags().contains(TRIAL_TAG))) {
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
        int epoch = epochOf(player.serverLevel(), player);
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
                        Notice.bar(player, ModText.progress("message.skyabyss.checkpoint",
                                Component.translatable("checkpoint.skyabyss.storm_" + r.id())));
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

    // 倒れて目覚める休み場。その休み場が戦いの部屋の前室で、そのグループがいま戦っている最中なら、鼓動の間 (hub) にする
    // (岩鯨の背と同じ。戦場のすぐそばで目覚めないように。02 の 7.3、brushup P1-1)
    public static String respawnCheckpoint(java.util.UUID group, String cp) {
        return isFightNear(group, cp) ? "hub" : cp;
    }

    static boolean isFightNear(java.util.UUID group, String cp) {
        return switch (cp) {
            case "heart" -> jp.revati.skyabyss.titan.storm.heart.StormHeartBattle.fighting(group);
            case "e1" -> jp.revati.skyabyss.titan.storm.raiju.RaijuBattle.fighting(group);
            default -> false;
        };
    }

    // 試練の甲板のセルから、その人を休み場へ戻す (落ちた・占有されている)。その休み場を最後の休み場にする
    public static void returnToRest(ServerPlayer player, String id) {
        ServerLevel back = (ServerLevel) player.level();
        BlockPos at = restSpawn(back, id);
        writeCheckpoint(player, id, epochOf(back, player));
        player.stopRiding();
        player.fallDistance = 0;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.teleportTo(back, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0f);
        player.hurtMarked = true;
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
                persisted(player).putInt(TAG_SEEN_EPOCH, epochOf(back, player));
                syncGates(back, Groups.keyOf(player));
                tellSleeping(player, StormProgress.of(back, player));
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != StormTravel.STORM_BACK) {
            return;
        }
        ServerLevel back = player.serverLevel();
        int epoch = epochOf(back, player);
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            clearCheckpoint(player);
            if (player.isAlive() && StormBackLayout.inTrial(player.position())) {
                toArrival(player);
                Notice.narrate(player, "message.skyabyss.rest_point_lost");
            }
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
        syncGates(back, Groups.keyOf(player));
    }

    // ---------------------------------------------------------------- 焚き火・戻しの鏡
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != StormTravel.STORM_BACK || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        if (pos.equals(MIRROR_RESET) || pos.equals(MIRROR_RESET_HALL)) {
            event.setCanceled(true);
            EyeMirrors.resetMirrors(level, player);
            return;
        }
        if (level.getBlockState(pos).is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get())) {
            event.setCanceled(true);
            if (player.isShiftKeyDown()) {
                if (jp.revati.skyabyss.trial.TrialRoute.propose(player)) {
                    return;
                }
                StormWest.strip(player);
                clearCheckpoint(player);
                player.fallDistance = 0;
                player.teleportTo(level, TAIL_CENTER.getX() + 0.5, TAIL_CENTER.getY(), TAIL_CENTER.getZ() + 0.5, 180f, 0f);
                Notice.narrate(player, "message.skyabyss.storm.gave_up");
            } else {
                Notice.bar(player, ModText.hint("message.skyabyss.storm.campfire_hint"));
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ready = false;
        idleTicks = 0;
        AFTER_HEART.clear();
        wasPeaceful = false;
        lastPhase = null;
        REVIVING.clear();
        RoomLocks.release(EYE_ROOM);
        RoomLocks.release(EAST_ROOM);
        StormEast.clear();
        StormCircuit.clear();
        StormHelm.clear();
        StormRib.clear();
        EyeMirrors.clear();
        StormGuide.clear();
        StormBackClock.force(null);
    }

    // ---------------------------------------------------------------- 風見の目盛り (画面右上。石の数はその人のグループの)
    static void sendGauge(ServerLevel level) {
        for (ServerPlayer p : Players.snapshot(level.players())) {
            PacketDistributor.sendToPlayer(p, gauge(level, p));
        }
    }

    public static void sendGaugeNow(ServerLevel level) {
        sendGauge(level);
    }

    static StormBackNetwork.Gauge gauge(ServerLevel level, ServerPlayer player) {
        StormGuide.Goal goal = StormGuide.goalFor(level, player);
        // 拍の舵で固定中なら、その拍と固定の残りを見せる
        StormHelm.Lock lock = StormHelm.lockOf(level, StormHelm.trialAt(player.position()), Groups.keyOf(player));
        int phase = lock != null ? lock.phase().ordinal() : StormBackClock.phase(level).ordinal();
        int left = lock != null ? (int) (lock.end() - level.getGameTime()) : StormBackClock.ticksLeft(level);
        float strength = lock != null ? (lock.phase() == StormBackClock.Phase.STORM ? 1f : 0f) : (float) StormBackClock.strength(level);
        return new StormBackNetwork.Gauge(phase, left, strength, StormProgress.of(level, player).rungCount(), goal.id(), goal.done(), goal.total(), goal.a(),
                goal.b(), goal.c(), lock != null);
    }

    public static Vec3 hubSpawn() {
        return Vec3.atBottomCenterOf(rest("hub"));
    }

    // そのグループで、扉がその人にとって開いているか (自己テスト・コマンド)
    public static boolean gateOpenFor(ServerLevel level, UUID group, List<BlockPos> door) {
        return GroupGates.isOpen(level, group, door);
    }

    static boolean isGate(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).is(ModBlocks.SEALED_GATE.get());
    }
}
