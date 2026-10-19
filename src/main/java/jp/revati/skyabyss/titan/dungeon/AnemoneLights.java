package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 闇の水路 (光海) の「灯」(docs/design/23 の案 B「灯をたずさえる」)。
//
//  - 光る海のイソギンチャク (GLOW_ANEMONE): 水路の入口の小部屋の苗床で受け取り、手に持つとまわりを照らす。灯の台に置くと、一つ減る
//  - 灯の台 (ANEMONE_PEDESTAL): 水路の正しい道の上に 3 つ (STANDS)。灯も 3 つ (LIGHTS)、戸が開くのに要るのも 3 つ (NEED)
//    = 台の数・灯の数・要る数がすべて同じ。「全部の台に灯をともせば、出口の戸が開く」
//  - ともした灯はグループの記録 (BackGroupData.Record の `lamp.<番号>`) に残り、その人たちの画面にだけ灯って見える (GroupOverlays。
//    ブロックのライトレベルがクライアントの光になる)。リセット (記録の進みを消す) で灯が消える。世界のブロックは灯らない
//  - 灯のまわり (CALM_RADIUS) では、水路の顎が、そのグループの人を襲わない (calms。WaterwayMaw の狙いと噛みつきが見る)
//  - 灯が NEED 本そろうと、出口の手前の封印の戸 (exitGate) が、そのグループにだけ開く (GroupGates)。このとき「開く場面」が流れる
//    (音の列・光の流れ・題字。openingCue) 。出口までの床が光の道になり (trailOverlay)、目的の帯の矢印が出口を指す。初回を終えたグループには最初から開く
//  - 灯の台のあいだの区間ごとに近道が一つ (MazePlan.cuts)。灯がその区間の数だけともると、そのグループにだけ開く (戻るときの近道)
//  - 灯の苗床 (ANEMONE_BED): 入口の小部屋と中ほどの休み場。「ともした灯 + 持っている灯」が LIGHTS に満たなければ、一つ摘める (なくしても詰まない)
//  - 入口の小部屋の数え石 (ANEMONE_TALLY) と灯の掲示板 (赤石灯が 3 つ。ともした数だけ灯る)。入口の看板・囁き・目的の帯 (アクションバー) で目的を伝える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AnemoneLights {
    // 灯の台の数・ともせる灯の数 (1 グループ)・出口の戸が開くのに要る灯の数 (三つとも同じ)
    public static final int STANDS = 3;
    public static final int LIGHTS = 3;
    public static final int NEED = 3;
    // 灯のまわりで魚が襲わない半径 (ブロック)
    public static final double CALM_RADIUS = 6.5;
    public static final String FLAG = "lamp.";
    public static final String FIRST_ROOM = "whale_maze";
    // 入口の小部屋の中の、数え石・苗床・灯の掲示板 (迷路の天井の上。床 y 81 の上)
    public static final BlockPos TALLY_POS = new BlockPos(8, MAZE_FLOOR + 5, -25);
    public static final List<BlockPos> ENTRY_BEDS = List.of(new BlockPos(7, MAZE_FLOOR + 5, -17), new BlockPos(8, MAZE_FLOOR + 5, -17),
            new BlockPos(9, MAZE_FLOOR + 5, -17));
    public static final BlockPos ENTRY_BED_POS = ENTRY_BEDS.get(0);
    // 東の壁に埋めた、灯の数の掲示板 (赤石灯 3 つ。左から順にともる)
    public static final List<BlockPos> BOARD = List.of(new BlockPos(11, MAZE_FLOOR + 6, -23), new BlockPos(11, MAZE_FLOOR + 6, -21),
            new BlockPos(11, MAZE_FLOOR + 6, -19));
    // 持ち歩きの光の強さ
    private static final int CARRY_LIGHT = 11;
    // 灯をともした台から、光の道が先へ伸びる長さ (正しい道のマスの数)
    public static final int TRAIL_AHEAD = 6;

    private AnemoneLights() {
    }

    // ---------------------------------------------------------------- 迷路から決まる場所

    private static long cachedSeed = Long.MIN_VALUE;
    private static MazePlan cachedPlan;
    private static List<BlockPos> cachedStands = List.of();
    private static List<BlockPos> cachedGate = List.of();
    private static List<List<BlockPos>> cachedCuts = List.of();
    private static List<BlockPos> cachedTrail = List.of();

    private static synchronized void ensureCache(long seed) {
        if (seed == cachedSeed) {
            return;
        }
        FloodedMaze maze = MazeBuilder.maze(seed);
        MazePlan plan = MazePlan.compute(maze);
        cachedPlan = plan;
        cachedStands = List.copyOf(computeStands(plan));
        cachedGate = List.copyOf(computeGate(maze));
        List<List<BlockPos>> cuts = new ArrayList<>();
        for (MazePlan.Cut cut : plan.cuts) {
            cuts.add(List.copyOf(plan.cutBlocks(cut)));
        }
        cachedCuts = List.copyOf(cuts);
        cachedTrail = List.copyOf(computeTrail(plan));
        cachedSeed = seed;
    }

    public static MazePlan plan(long mazeSeed) {
        ensureCache(mazeSeed);
        return cachedPlan;
    }

    public static List<BlockPos> stands(long mazeSeed) {
        ensureCache(mazeSeed);
        return cachedStands;
    }

    public static List<BlockPos> exitGate(long mazeSeed) {
        ensureCache(mazeSeed);
        return cachedGate;
    }

    // 近道の戸のブロック (近道ごと。plan(...).cuts と同じ順)
    public static List<List<BlockPos>> cutGates(long mazeSeed) {
        ensureCache(mazeSeed);
        return cachedCuts;
    }

    // 光の道の床 (正しい道の順。マスの真ん中・通路の真ん中・…)。息継ぎのくぼみと流れの通路は、床がもう別の物なので入れない
    public static List<BlockPos> trail(long mazeSeed) {
        ensureCache(mazeSeed);
        return cachedTrail;
    }

    public static MazePlan plan(ServerLevel level) {
        return plan(TitanBackData.get(level).mazeSeed());
    }

    public static List<BlockPos> stands(ServerLevel level) {
        return stands(TitanBackData.get(level).mazeSeed());
    }

    public static List<BlockPos> exitGate(ServerLevel level) {
        return exitGate(TitanBackData.get(level).mazeSeed());
    }

    public static List<List<BlockPos>> cutGates(ServerLevel level) {
        return cutGates(TitanBackData.get(level).mazeSeed());
    }

    public static List<BlockPos> trail(ServerLevel level) {
        return trail(TitanBackData.get(level).mazeSeed());
    }

    // 灯の台の場所 (正しい道の上。道を 4 つに分けた 3 つの境。息継ぎのくぼみのマスと出口の手前は避ける)。マスの真ん中の、水の一段目
    public static List<BlockPos> computeStands(FloodedMaze maze) {
        return computeStands(MazePlan.compute(maze));
    }

    public static List<BlockPos> computeStands(MazePlan plan) {
        List<BlockPos> result = new ArrayList<>();
        for (int k : plan.standK) {
            result.add(MazePlan.center(plan.path.get(k)));
        }
        return result;
    }

    // 出口の手前の戸: 最後から二つ目のマスと出口のマスをつなぐ通路 (3×3)。最後の二マスは息継ぎのくぼみにならないので、いつも三段そろう
    public static List<BlockPos> computeGate(FloodedMaze maze) {
        List<int[]> path = maze.solution();
        int n = path.size();
        return MazePlan.passage(path.get(n - 2), path.get(n - 1));
    }

    // 光の道: 入口のマスから出口のマスまで、床の真ん中の石
    public static List<BlockPos> computeTrail(MazePlan plan) {
        List<BlockPos> list = new ArrayList<>();
        for (int k = 0; k < plan.path.size(); k++) {
            if (k > 0) {
                BlockPos pf = MazePlan.passageFloor(plan.path.get(k - 1), plan.path.get(k));
                if (!(plan.currents.contains(k - 1) || plan.isPocket(plan.path.get(k - 1)) || plan.isPocket(plan.path.get(k)))) {
                    list.add(pf);
                }
            }
            if (!plan.isPocket(plan.path.get(k))) {
                BlockPos c = MazePlan.center(plan.path.get(k));
                list.add(new BlockPos(c.getX(), MAZE_FLOOR, c.getZ()));
            }
        }
        return list;
    }

    // 正しい道の添え字 k (範囲 lo〜hi) の光の道の床
    private static List<BlockPos> trailBetween(MazePlan plan, int lo, int hi) {
        List<BlockPos> list = new ArrayList<>();
        for (int k = Math.max(0, lo); k <= Math.min(plan.path.size() - 1, hi); k++) {
            if (k > lo) {
                if (!(plan.currents.contains(k - 1) || plan.isPocket(plan.path.get(k - 1)) || plan.isPocket(plan.path.get(k)))) {
                    list.add(MazePlan.passageFloor(plan.path.get(k - 1), plan.path.get(k)));
                }
            }
            if (!plan.isPocket(plan.path.get(k))) {
                BlockPos c = MazePlan.center(plan.path.get(k));
                list.add(new BlockPos(c.getX(), MAZE_FLOOR, c.getZ()));
            }
        }
        return list;
    }

    // 出口のマスの床の輪 (出口の泡の柱のまわり 8 つ)
    private static List<BlockPos> exitRing() {
        BlockPos c = MazeBuilder.exitBubble();
        List<BlockPos> list = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    list.add(new BlockPos(c.getX() + dx, MAZE_FLOOR, c.getZ() + dz));
                }
            }
        }
        return list;
    }

    // 迷路を掘り直すとき (MazeBuilder.buildMaze の最後): 灯の台・戸・近道の戸・中ほどの苗床を置き、初回の部屋を登録する
    static void build(BuildKit kit, FloodedMaze maze) {
        MazePlan plan = MazePlan.compute(maze);
        BlockState stand = ModBlocks.ANEMONE_PEDESTAL.get().defaultBlockState();
        for (BlockPos p : computeStands(plan)) {
            kit.set(p, stand);
        }
        BlockState gate = ModBlocks.SEALED_GATE.get().defaultBlockState();
        for (BlockPos p : computeGate(maze)) {
            kit.set(p, gate);
        }
        for (MazePlan.Cut cut : plan.cuts) {
            for (BlockPos p : plan.cutBlocks(cut)) {
                kit.set(p, gate);
            }
        }
        BlockPos mid = MazeBuilder.midPocket(maze);
        // 中ほどの休み場: 隅の足場の上 (空気の層の一段目)。復活位置 (北西の隅) と紫水晶 (南東の隅) の間の隅
        kit.set(mid.offset(2, 2, 0), ModBlocks.ANEMONE_BED.get().defaultBlockState());
        registerRoom(maze);
    }

    // 入口の小部屋の数え石・苗床・掲示板 (島を建てるときに一度だけ)
    static void buildEntry(BuildKit kit) {
        kit.set(TALLY_POS, ModBlocks.ANEMONE_TALLY.get().defaultBlockState());
        for (BlockPos bed : ENTRY_BEDS) {
            kit.set(bed, ModBlocks.ANEMONE_BED.get().defaultBlockState());
        }
        // 掲示板: 赤石灯 (消えている) を壁に埋め、上下を彫刻石で縁取る。ともした数だけ、そのグループの画面で灯る
        for (BlockPos p : BOARD) {
            kit.set(p, Blocks.REDSTONE_LAMP.defaultBlockState());
            kit.set(p.above(), BuildKit.CHISELED);
            kit.set(p.below(), BuildKit.CHISELED);
        }
    }

    // 初回を終えたグループには、出口の戸・近道の戸が最初から開き、光の道が見える (既知の道。FirstRun が 1 秒ごとに開け直す)
    private static void registerRoom(FloodedMaze maze) {
        AABB box = new AABB(MAZE_X0, MAZE_FLOOR, MAZE_Z0, MAZE_X0 + MAZE_W * (MAZE_CELL + 1) + 1, MAZE_FLOOR + 6, MAZE_Z0 + MAZE_H * (MAZE_CELL + 1) + 1);
        MazePlan plan = MazePlan.compute(maze);
        List<BlockPos> doors = new ArrayList<>(computeGate(maze));
        for (MazePlan.Cut cut : plan.cuts) {
            doors.addAll(plan.cutBlocks(cut));
        }
        List<BlockPos> guide = new ArrayList<>(computeTrail(plan));
        guide.addAll(exitRing());
        FirstRun.register(FirstRun.Room.firstRun(FIRST_ROOM, TitanTravel.TITAN_BACK, box).doors(doors).guide(guide).guideState(Blocks.SEA_LANTERN.defaultBlockState()));
    }

    // 迷路が別の形に作り直される前に: 灯の記録と、古い戸の上書きを全グループから消す (台の番号と戸の場所が変わるため)
    public static void clearGroups(ServerLevel level, long oldSeed) {
        List<BlockPos> oldDoors = new ArrayList<>(exitGate(oldSeed));
        for (List<BlockPos> cut : cutGates(oldSeed)) {
            oldDoors.addAll(cut);
        }
        BackGroupData data = BackGroupData.get(level);
        for (UUID id : data.ids()) {
            BackGroupData.Record r = data.peek(id);
            if (r == null) {
                continue;
            }
            boolean had = false;
            for (int i = 0; i < STANDS; i++) {
                if (r.has(FLAG + i)) {
                    r.setFlag(FLAG + i, false);
                    had = true;
                }
            }
            had |= GroupGates.release(level, id, oldDoors);
            if (had) {
                GroupOverlays.refresh(level, id);
            }
        }
        OPENINGS.clear();
    }

    // ---------------------------------------------------------------- グループの灯

    public static boolean isLit(BackGroupData.Record r, int index) {
        return r != null && r.flag(FLAG + index);
    }

    public static int litCount(BackGroupData.Record r) {
        int n = 0;
        for (int i = 0; i < STANDS; i++) {
            if (isLit(r, i)) {
                n++;
            }
        }
        return n;
    }

    public static boolean isLamp(ItemStack stack) {
        return stack.is(ModItems.GLOW_ANEMONE.get());
    }

    // グループの人 (その背中にいる人) が持っている灯の数
    public static int heldCount(ServerLevel level, UUID group) {
        int n = 0;
        for (ServerPlayer p : Groups.online(level, group)) {
            n += p.getInventory().countItem(ModItems.GLOW_ANEMONE.get());
        }
        for (ServerPlayer p : extraMembers) {
            if (Groups.isMember(p, group) && !level.players().contains(p)) {
                n += p.getInventory().countItem(ModItems.GLOW_ANEMONE.get());
            }
        }
        return n;
    }

    // 自己テスト用: ワールドに入っていない (FakePlayer の) 持ち主も、グループの手持ちに数える
    static List<ServerPlayer> extraMembers = List.of();

    // 苗床から、あと何本摘めるか (ともした灯 + 持っている灯 が LIGHTS まで)
    public static int growable(ServerLevel level, UUID group) {
        return Math.max(0, LIGHTS - litCount(BackGroupData.get(level).peek(group)) - heldCount(level, group));
    }

    // 台 pos に灯をともす (右クリック)。灯が一つ減る。台でなければ (置いただけの台) 世界全体で灯る
    public static void tryLight(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        int index = stands(level).indexOf(pos);
        if (index < 0) {
            if (!state.getValue(AnemonePedestalBlock.LIT)) {
                level.setBlock(pos, state.setValue(AnemonePedestalBlock.LIT, true), Block.UPDATE_ALL);
                consume(player, stack);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 1.4f);
            }
            return;
        }
        UUID group = Groups.keyOf(player);
        BackGroupData.Record r = GroupProgress.of(level, group);
        if (isLit(r, index)) {
            Notice.bar(player, ModText.hint("message.skyabyss.lamp_already"));
            return;
        }
        if (litCount(r) >= LIGHTS) {
            Notice.bar(player, ModText.deny("message.skyabyss.lamp_enough", LIGHTS));
            return;
        }
        r.setFlag(FLAG + index, true);
        consume(player, stack);
        afterLit(level, group, index, pos, r);
    }

    // 開発用: 灯を消費せずに台 index をともす (AnemoneDev)
    static void devLight(ServerLevel level, UUID group, int index) {
        BackGroupData.Record r = GroupProgress.of(level, group);
        if (isLit(r, index)) {
            return;
        }
        r.setFlag(FLAG + index, true);
        afterLit(level, group, index, stands(level).get(index), r);
    }

    // 灯がともったあとの見せ場: 画面の更新・音と粒・「灯 n / 3」・近道と出口の戸
    private static void afterLit(ServerLevel level, UUID group, int index, BlockPos pos, BackGroupData.Record r) {
        GroupOverlays.refresh(level, group);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.8;
        double z = pos.getZ() + 0.5;
        Groups.sound(level, group, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4f, 1.5f);
        Groups.sound(level, group, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.8f);
        for (ServerPlayer member : Groups.online(level, group)) {
            if (member.distanceToSqr(x, y, z) < 64 * 64) {
                level.sendParticles(member, ParticleTypes.END_ROD, false, x, y, z, 24, 0.3, 0.5, 0.3, 0.04);
            }
        }
        int n = litCount(r);
        Groups.bar(level, group, ModText.progress("message.skyabyss.lamp_lit", n, NEED));
        openingFrom.put(group, index);
        sync(level, group);
    }

    private static void consume(ServerPlayer player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    // 灯の台を調べる (灯を持たずに触れた)
    public static void inspect(ServerLevel level, ServerPlayer player, BlockPos pos) {
        int index = stands(level).indexOf(pos);
        if (index >= 0 && isLit(GroupProgress.of(level, Groups.keyOf(player)), index)) {
            Notice.bar(player, ModText.hint("message.skyabyss.lamp_already"));
        } else {
            Notice.bar(player, ModText.hint("message.skyabyss.lamp_needs", STANDS));
        }
    }

    // 苗床から灯を摘む
    public static void takeFromBed(ServerLevel level, ServerPlayer player, BlockPos pos) {
        UUID group = Groups.keyOf(player);
        if (growable(level, group) <= 0) {
            Notice.bar(player, ModText.deny("message.skyabyss.lamp_bed_full", LIGHTS));
            return;
        }
        ItemStack lamp = new ItemStack(ModItems.GLOW_ANEMONE.get());
        if (!player.getInventory().add(lamp)) {
            player.drop(lamp, false);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.8f, 1.6f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
        Notice.bar(player, ModText.hint("message.skyabyss.lamp_bed_take", heldCount(level, group), LIGHTS));
    }

    // 数え石を読む
    public static void readTally(ServerLevel level, ServerPlayer player) {
        BackGroupData.Record r = GroupProgress.of(level, Groups.keyOf(player));
        Notice.bar(player, ModText.hint("message.skyabyss.lamp_tally", litCount(r), STANDS));
    }

    // 灯の数に合わせて、近道と出口の戸をそのグループにだけ開ける (灯がそろったとき・初回を終えたとき)。何か変わったら true
    public static boolean sync(ServerLevel level, UUID group) {
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        if (r == null) {
            return false;
        }
        int lit = litCount(r);
        boolean changed = false;
        MazePlan plan = plan(level);
        List<List<BlockPos>> cuts = cutGates(level);
        for (int i = 0; i < cuts.size(); i++) {
            if (lit >= plan.cuts.get(i).lit() && !GroupGates.isOpen(level, group, cuts.get(i))) {
                if (GroupGates.open(level, group, cuts.get(i))) {
                    changed = true;
                    BlockPos at = cuts.get(i).get(cuts.get(i).size() / 2);
                    Groups.sound(level, group, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0f, 0.7f);
                    for (ServerPlayer member : Groups.online(level, group)) {
                        Notice.bar(member, ModText.progress("message.skyabyss.lamp_cut_open"));
                    }
                }
            }
        }
        if (lit < NEED) {
            return changed;
        }
        List<BlockPos> gate = exitGate(level);
        if (GroupGates.isOpen(level, group, gate)) {
            return changed;
        }
        if (GroupGates.open(level, group, gate)) {
            startOpening(level, group);
            changed = true;
        }
        return changed;
    }

    // ---------------------------------------------------------------- 出口の戸が開く場面 (音の列・光の流れ・題字)

    // 最後にともした台 (光の流れの始まり)
    private static final Map<UUID, Integer> openingFrom = new HashMap<>();

    private static final class Opening {
        final UUID group;
        final long start;
        final List<Vec3> route;

        Opening(UUID group, long start, List<Vec3> route) {
            this.group = group;
            this.start = start;
            this.route = route;
        }
    }

    private static final List<Opening> OPENINGS = new ArrayList<>();
    // 光の流れが一歩進む間隔 (tick)
    private static final int RIBBON_STEP = 2;
    // 戸の開く音の高さの列 (音階。一音ごとに 5 tick)
    private static final float[] CHIME_PITCHES = {0.84f, 1.0f, 1.26f, 1.5f, 1.68f, 2.0f};

    // 出口の戸が開いた: 場面を始める。光の流れは、最後にともした台から出口の泡の柱へ、道のりの順に走る
    private static void startOpening(ServerLevel level, UUID group) {
        MazePlan plan = plan(level);
        int from = Math.min(plan.standK[Math.min(STANDS - 1, openingFrom.getOrDefault(group, STANDS - 1))], plan.path.size() - 1);
        List<Vec3> route = new ArrayList<>();
        for (int k = from; k < plan.path.size(); k++) {
            BlockPos c = MazePlan.center(plan.path.get(k));
            if (k > from) {
                BlockPos pf = MazePlan.passageFloor(plan.path.get(k - 1), plan.path.get(k));
                route.add(new Vec3(pf.getX() + 0.5, MAZE_FLOOR + 2.2, pf.getZ() + 0.5));
            }
            route.add(new Vec3(c.getX() + 0.5, MAZE_FLOOR + 2.2, c.getZ() + 0.5));
        }
        OPENINGS.add(new Opening(group, level.getGameTime(), route));
        List<BlockPos> gate = exitGate(level);
        BlockPos at = gate.get(gate.size() / 2);
        // 戸の動く重い音 (戸の位置から)、そして全員に聞こえる音の列は openingCue が鳴らす
        Groups.sound(level, group, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.6f, 0.5f);
        Groups.sound(level, group, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.4f, 0.7f);
        for (ServerPlayer member : Groups.online(level, group)) {
            Notice.place(member, ModText.progress("message.skyabyss.lamp_open_title"), ModText.hint("message.skyabyss.lamp_open_sub"));
        }
    }

    // 毎 tick: 動いている場面を進める (光の流れの粒・音の列)
    private static void tickOpenings(ServerLevel level) {
        Iterator<Opening> it = OPENINGS.iterator();
        while (it.hasNext()) {
            Opening o = it.next();
            long e = level.getGameTime() - o.start;
            List<ServerPlayer> members = Groups.online(level, o.group);
            // 全員に聞こえる、上がる音の列 (どこにいても「何かが開いた」と分かる)
            if (e >= 6 && e % 5 == 1 && (e - 6) / 5 < CHIME_PITCHES.length) {
                float pitch = CHIME_PITCHES[(int) ((e - 6) / 5)];
                for (ServerPlayer m : members) {
                    m.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.9f, pitch);
                }
            }
            if (e == 40) {
                for (ServerPlayer m : members) {
                    m.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.3f);
                }
            }
            // 光の流れ: 道のりの順に、一歩ずつ
            if (e >= 10 && (e - 10) % RIBBON_STEP == 0) {
                int step = (int) ((e - 10) / RIBBON_STEP);
                if (step < o.route.size()) {
                    Vec3 p = o.route.get(step);
                    for (ServerPlayer m : members) {
                        if (m.distanceToSqr(p) < 80 * 80) {
                            level.sendParticles(m, ParticleTypes.END_ROD, true, p.x, p.y, p.z, 8, 0.3, 0.3, 0.3, 0.02);
                        }
                    }
                    if (step % 4 == 0) {
                        Groups.sound(level, o.group, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.8f + 0.04f * Math.min(step, 20));
                    }
                } else {
                    it.remove();
                    continue;
                }
            }
            if (members.isEmpty() && e > 200) {
                it.remove();
            }
        }
    }

    // ---------------------------------------------------------------- 魚をなだめる

    // グループ group の人が位置 pos にいるとき、灯のそばで安全か (ともした灯の CALM_RADIUS 以内)
    public static boolean calmedAt(ServerLevel level, UUID group, Vec3 pos) {
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        if (r == null) {
            return false;
        }
        List<BlockPos> stands = stands(level);
        double max = CALM_RADIUS * CALM_RADIUS;
        for (int i = 0; i < stands.size(); i++) {
            if (isLit(r, i)) {
                BlockPos s = stands.get(i);
                if (s.getCenter().distanceToSqr(pos) <= max) {
                    return true;
                }
            }
        }
        return false;
    }

    // 水路の顎が見る: この人は灯のそばにいるか (岩鯨の背の中だけ)
    public static boolean calms(Level level, Entity target) {
        if (!(level instanceof ServerLevel sl) || sl.dimension() != TitanTravel.TITAN_BACK) {
            return false;
        }
        UUID group = Groups.keyOf(target);
        return group != null && calmedAt(sl, group, target.position());
    }

    // ---------------------------------------------------------------- グループごとの見かけ (灯った台・数え石・掲示板・光の道)

    // いまの光の道 (そのグループに見せる床の石の座標)。灯をともした台から先へ TRAIL_AHEAD マス、戸が開いたら入口から出口まで全部と出口の輪
    public static List<BlockPos> trailShown(ServerLevel level, UUID group, BackGroupData.Record r) {
        MazePlan plan = plan(level);
        List<BlockPos> shown = new ArrayList<>();
        if (GroupGates.isOpen(level, group, exitGate(level))) {
            shown.addAll(trail(level));
            shown.addAll(exitRing());
            return shown;
        }
        for (int i = 0; i < STANDS; i++) {
            if (isLit(r, i)) {
                int k = plan.standK[i];
                shown.addAll(trailBetween(plan, k, k + TRAIL_AHEAD));
            }
        }
        return shown;
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        GroupOverlays.register((level, group, out) -> {
            if (level.dimension() != TitanTravel.TITAN_BACK) {
                return;
            }
            BackGroupData.Record r = BackGroupData.get(level).peek(group);
            if (r == null) {
                return;
            }
            List<BlockPos> stands = stands(level);
            BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
            int n = 0;
            for (int i = 0; i < stands.size(); i++) {
                if (isLit(r, i)) {
                    n++;
                    BlockPos s = stands.get(i);
                    out.accept(s, ModBlocks.ANEMONE_PEDESTAL.get().defaultBlockState().setValue(AnemonePedestalBlock.LIT, true));
                    // 台の真上の天井も灯る (灯をともした台が、遠くからも柱の光で分かる)
                    out.accept(new BlockPos(s.getX(), MAZE_FLOOR + 4, s.getZ()), lantern);
                }
            }
            if (n > 0) {
                out.accept(TALLY_POS, ModBlocks.ANEMONE_TALLY.get().defaultBlockState().setValue(AnemoneTallyBlock.COUNT, n));
                for (int i = 0; i < n && i < BOARD.size(); i++) {
                    out.accept(BOARD.get(i), Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
                }
                for (BlockPos p : trailShown(level, group, r)) {
                    out.accept(p, lantern);
                }
            }
        });
    }

    // ---------------------------------------------------------------- 持ち歩きの光・目的の帯・灯の粒

    private static final Map<UUID, BlockPos> CARRY_AT = new HashMap<>();
    private static final Map<UUID, BlockState> CARRY_WAS = new HashMap<>();

    static boolean inMaze(Entity e) {
        return e.getX() >= MAZE_X0 && e.getX() <= MAZE_X0 + MAZE_W * (MAZE_CELL + 1) + 1 && e.getZ() >= MAZE_Z0
                && e.getZ() <= MAZE_Z0 + MAZE_H * (MAZE_CELL + 1) + 1 && e.getY() >= MAZE_FLOOR && e.getY() <= MAZE_FLOOR + 9;
    }

    private static boolean holdsLamp(ServerPlayer p) {
        return isLamp(p.getMainHandItem()) || isLamp(p.getOffhandItem());
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        boolean back = level.dimension() == TitanTravel.TITAN_BACK;
        int t = player.tickCount;
        if (t % 4 == 0) {
            boolean want = back && inMaze(player) && holdsLamp(player) && player.isAlive() && !player.isSpectator();
            updateCarryLight(level, player, want);
        }
        if (!back || player.isSpectator() || !inMaze(player)) {
            return;
        }
        if (t % 10 == 5) {
            if (holdsLamp(player)) {
                level.sendParticles(player, ParticleTypes.END_ROD, false, player.getX(), player.getY() + 1.1, player.getZ(), 1, 0.35, 0.3, 0.35, 0.005);
            }
            glowStands(level, player);
        }
        if (t % 60 == 20) {
            sync(level, Groups.keyOf(player));
        }
        if (t % 20 == 7) {
            band(level, player, Groups.keyOf(player));
        }
    }

    // ---- 目的の帯 (アクションバー。水路にいる間、1 秒ごと): 灯の数・いま何をすればよいか・向かう方向と道のり

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    // 向かう先 (dx, dz) が、いまの体の向きに対してどちらか (↑ = 正面)
    static String arrow(ServerPlayer player, double dx, double dz) {
        float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rel = Mth.wrapDegrees(target - player.getYRot());
        int idx = Math.floorMod(Math.round(rel / 45f), 8);
        return ARROWS[idx];
    }

    // いまの目的の帯の文 (自己テスト・撮影用に、送らずに作る)
    static Component bandText(ServerLevel level, ServerPlayer player, UUID group) {
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        int lit = litCount(r);
        int held = player.getInventory().countItem(ModItems.GLOW_ANEMONE.get());
        MazePlan plan = plan(level);
        boolean inRoom = player.getY() > MAZE_FLOOR + 4.5;
        int[] here = MazePlan.cellAt(player.getX(), player.getZ());
        int[] start = plan.path.get(0);
        if (GroupGates.isOpen(level, group, exitGate(level))) {
            Vec3 way = waypoint(plan, here, plan.path.get(plan.path.size() - 1), inRoom);
            return ModText.objective("message.skyabyss.lamp_band_open", lit, STANDS, arrow(player, way.x - player.getX(), way.z - player.getZ()),
                    meters(plan.distance(here, plan.path.get(plan.path.size() - 1))));
        }
        if (held > 0 || lit >= NEED) {
            // 近い (道のりの) ともしていない台へ
            int best = -1;
            int bestD = Integer.MAX_VALUE;
            for (int i = 0; i < STANDS; i++) {
                if (!isLit(r, i)) {
                    int d = plan.distance(here, plan.path.get(plan.standK[i]));
                    if (d >= 0 && d < bestD) {
                        bestD = d;
                        best = i;
                    }
                }
            }
            if (best >= 0) {
                Vec3 way = waypoint(plan, inRoom ? start : here, plan.path.get(plan.standK[best]), inRoom);
                int d = inRoom ? plan.distance(start, plan.path.get(plan.standK[best])) : bestD;
                return ModText.objective("message.skyabyss.lamp_band", lit, STANDS, arrow(player, way.x - player.getX(), way.z - player.getZ()), meters(d), held);
            }
        }
        // 灯が手元にない: 苗床へ (入口の小部屋の苗床か、中ほどの休み場の苗床。近いほう)
        BlockPos mid = MazeBuilder.midPocket(plan.maze);
        int[] midCell = MazePlan.cellAt(mid.getX(), mid.getZ());
        int dMid = inRoom ? Integer.MAX_VALUE : plan.distance(here, midCell);
        int dEntry = inRoom ? 0 : plan.distance(here, start);
        Vec3 target;
        int dist;
        if (inRoom || dEntry <= dMid || dMid < 0) {
            target = ENTRY_BED_POS.getCenter();
            dist = inRoom ? (int) Math.ceil(Math.sqrt(player.distanceToSqr(target)) / 4.0) : dEntry;
            if (!inRoom) {
                target = new Vec3(MazePlan.center(start).getX() + 0.5, MAZE_FLOOR + 1, MazePlan.center(start).getZ() + 0.5);
                int[] next = plan.nextStep(here, start);
                if (next != null) {
                    BlockPos c = MazePlan.center(next);
                    target = new Vec3(c.getX() + 0.5, MAZE_FLOOR + 1, c.getZ() + 0.5);
                }
            }
        } else {
            int[] next = plan.nextStep(here, midCell);
            BlockPos c = MazePlan.center(next == null ? midCell : next);
            target = new Vec3(c.getX() + 0.5, MAZE_FLOOR + 1, c.getZ() + 0.5);
            dist = dMid;
        }
        return ModText.objective("message.skyabyss.lamp_band_bed", lit, STANDS, arrow(player, target.x - player.getX(), target.z - player.getZ()), meters(dist));
    }

    private static int meters(int cells) {
        return Math.max(0, cells) * (MAZE_CELL + 1);
    }

    // from のマスから to のマスへ向かう次の中継点 (マスの真ん中)。入口の小部屋にいるときは、水への入口 (はしごの穴)
    private static Vec3 waypoint(MazePlan plan, int[] from, int[] to, boolean inRoom) {
        if (inRoom) {
            BlockPos hatch = mazeCell(MAZE_START_I, MAZE_START_J).offset(1, 0, 0);
            return new Vec3(hatch.getX() + 0.5, hatch.getY(), hatch.getZ() + 0.5);
        }
        int[] next = plan.nextStep(from, to);
        BlockPos c = MazePlan.center(next == null ? to : next);
        return new Vec3(c.getX() + 0.5, MAZE_FLOOR + 1, c.getZ() + 0.5);
    }

    private static void band(ServerLevel level, ServerPlayer player, UUID group) {
        Notice.bar(player, bandText(level, player, group));
    }

    // 灯の台の粒 (その人にだけ。近いものだけ): ともした台は白い光が昇り、まだの台は青い火がゆらぐ (遠くからも台の場所が分かる)
    private static void glowStands(ServerLevel level, ServerPlayer player) {
        BackGroupData.Record r = BackGroupData.get(level).peek(Groups.keyOf(player));
        List<BlockPos> stands = stands(level);
        for (int i = 0; i < stands.size(); i++) {
            BlockPos s = stands.get(i);
            if (player.distanceToSqr(s.getX(), s.getY(), s.getZ()) > 48 * 48) {
                continue;
            }
            if (isLit(r, i)) {
                level.sendParticles(player, ParticleTypes.END_ROD, false, s.getX() + 0.5, s.getY() + 1.1, s.getZ() + 0.5, 2, 0.3, 0.45, 0.3, 0.012);
            } else {
                level.sendParticles(player, ParticleTypes.SOUL_FIRE_FLAME, true, s.getX() + 0.5, s.getY() + 1.3, s.getZ() + 0.5, 3, 0.15, 0.5, 0.15, 0.01);
            }
        }
    }

    // 持ち歩きの光: 目の位置の水 (か空気) を、見えない光のブロックにする。動いたら戻す
    static void updateCarryLight(ServerLevel level, ServerPlayer player, boolean want) {
        UUID id = player.getUUID();
        BlockPos at = CARRY_AT.get(id);
        BlockPos next = want ? BlockPos.containing(player.getEyePosition()) : null;
        if (at != null && at.equals(next)) {
            return;
        }
        if (at != null) {
            removeLight(level, id);
        }
        if (next == null) {
            return;
        }
        BlockState cur = level.getBlockState(next);
        boolean water = cur.is(Blocks.WATER) && cur.getFluidState().isSource();
        if (water || cur.isAir()) {
            CARRY_AT.put(id, next);
            CARRY_WAS.put(id, cur);
            level.setBlock(next, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, CARRY_LIGHT).setValue(LightBlock.WATERLOGGED, water),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static void removeLight(ServerLevel level, UUID id) {
        BlockPos at = CARRY_AT.remove(id);
        BlockState was = CARRY_WAS.remove(id);
        if (at != null && was != null && level.isLoaded(at) && level.getBlockState(at).is(Blocks.LIGHT)) {
            level.setBlock(at, was, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // 自己テスト用: 持ち歩きの光が置いてある位置
    public static BlockPos carryLightAt(UUID player) {
        return CARRY_AT.get(player);
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level() instanceof ServerLevel level) {
            removeLight(level, p.getUUID());
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.server != null) {
            ServerLevel from = p.server.getLevel(event.getFrom());
            if (from != null) {
                removeLight(from, p.getUUID());
            }
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        ServerLevel back = event.getServer().getLevel(TitanTravel.TITAN_BACK);
        if (back != null) {
            for (UUID id : new ArrayList<>(CARRY_AT.keySet())) {
                removeLight(back, id);
            }
        }
        CARRY_AT.clear();
        CARRY_WAS.clear();
        OPENINGS.clear();
    }

    // 初回の部屋の登録を、世界が読まれたあとにも (FirstRun の登録は保存されない)。1 秒ごと、背中に人がいるときに見る。
    // 出口の戸が開く場面は毎 tick 進める
    @SubscribeEvent
    static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TitanTravel.TITAN_BACK) {
            return;
        }
        if (!OPENINGS.isEmpty()) {
            tickOpenings(level);
        }
        if (level.getGameTime() % 20 != 11 || level.players().isEmpty()) {
            return;
        }
        if (FirstRun.room(FIRST_ROOM) == null || registeredSeed != TitanBackData.get(level).mazeSeed()) {
            registeredSeed = TitanBackData.get(level).mazeSeed();
            registerRoom(MazeBuilder.maze(registeredSeed));
        }
    }

    private static long registeredSeed = Long.MIN_VALUE;

    // 自己テスト用: いまの迷路で部屋を登録し直したことにする
    static void markRegistered(long seed) {
        registeredSeed = seed;
    }

    // 登録の記録を忘れる (建て直したあと)
    static void forgetRegistered() {
        registeredSeed = Long.MIN_VALUE;
    }

    // 自己テスト用: 動いている「戸が開く場面」の数
    static int openingsRunning() {
        return OPENINGS.size();
    }
}
