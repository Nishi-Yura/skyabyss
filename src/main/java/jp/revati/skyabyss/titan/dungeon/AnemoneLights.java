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
//  - 周回 (32 の 4 章、R5。RouteLength): 灯は一人一度に二つまで (CARRY_LONG)、中ほどの苗床は眠っていて摘めない (入口の苗床だけ)。
//    二つともしたら入口へ戻って三つ目を摘むので、道のりが 1.5〜2 倍になる (戻りに近道が効く)。初回は今のまま (三つまとめて持て、中ほどでも摘める)
//  - 入口の小部屋の数え石 (ANEMONE_TALLY) と灯の掲示板 (赤石灯が 3 つ。ともした数だけ灯る)。入口の看板・囁き・目的の帯 (アクションバー) で目的を伝える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AnemoneLights {
    // 灯の台の数・ともせる灯の数 (1 グループ)・出口の戸が開くのに要る灯の数 (三つとも同じ)
    public static final int STANDS = 3;
    public static final int LIGHTS = 3;
    public static final int NEED = 3;
    // 周回で一人が一度に持てる灯の数
    public static final int CARRY_LONG = 2;
    // 灯のまわりで魚が襲わない半径 (ブロック)
    public static final double CALM_RADIUS = 6.5;
    public static final String FLAG = "lamp.";
    // 入口の小部屋の中の、数え石・苗床・灯の掲示板 (迷路の天井の上。床 y 81 の上)
    public static final BlockPos TALLY_POS = new BlockPos(MAZE_ENTRY_ROOM.x2() - 2, MAZE_FLOOR + 5, MAZE_ENTRY_ROOM.z1());
    public static final List<BlockPos> ENTRY_BEDS = List.of(new BlockPos(MAZE_ENTRY_ROOM.x2() - 3, MAZE_FLOOR + 5, MAZE_ENTRY_ROOM.z2()),
            new BlockPos(MAZE_ENTRY_ROOM.x2() - 2, MAZE_FLOOR + 5, MAZE_ENTRY_ROOM.z2()), new BlockPos(MAZE_ENTRY_ROOM.x2() - 1, MAZE_FLOOR + 5, MAZE_ENTRY_ROOM.z2()));
    public static final BlockPos ENTRY_BED_POS = ENTRY_BEDS.get(0);
    // 2 つめの入口の小部屋の苗床
    public static final BlockPos ENTRY_BED_POS2 = new BlockPos(MAZE_ENTRY_ROOM2.x2(), MAZE_FLOOR + 5, MAZE_ENTRY_ROOM2.z2());
    // 東の壁に埋めた、灯の数の掲示板 (赤石灯 3 つ。左から順にともる)
    public static final List<BlockPos> BOARD = List.of(new BlockPos(MAZE_ENTRY_ROOM.x2() + 1, MAZE_FLOOR + 6, MAZE_ENTRY_ROOM.z1() + 3),
            new BlockPos(MAZE_ENTRY_ROOM.x2() + 1, MAZE_FLOOR + 6, MAZE_ENTRY_ROOM.z1() + 6), new BlockPos(MAZE_ENTRY_ROOM.x2() + 1, MAZE_FLOOR + 6, MAZE_ENTRY_ROOM.z1() + 9));
    // 持ち歩きの光の強さ
    private static final int CARRY_LIGHT = 11;
    // 灯をともした台から、光の道が先へ伸びる長さ (正しい道のマスの数)

    private AnemoneLights() {
    }

    // ---------------------------------------------------------------- 迷路から決まる場所

    private static long cachedSeed = Long.MIN_VALUE;
    private static MazePlan cachedPlan;
    private static List<BlockPos> cachedStands = List.of();
    private static List<BlockPos> cachedGate = List.of();
    private static List<List<BlockPos>> cachedCuts = List.of();

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
        kit.set(mid.offset(MAZE_CELL - 1, 2, 0), ModBlocks.ANEMONE_BED.get().defaultBlockState());
    }

    // 入口の小部屋の数え石・苗床・掲示板 (島を建てるときに一度だけ)
    static void buildEntry(BuildKit kit) {
        kit.set(TALLY_POS, ModBlocks.ANEMONE_TALLY.get().defaultBlockState());
        for (BlockPos bed : ENTRY_BEDS) {
            kit.set(bed, ModBlocks.ANEMONE_BED.get().defaultBlockState());
        }
        kit.set(ENTRY_BED_POS2, ModBlocks.ANEMONE_BED.get().defaultBlockState());
        // 掲示板: 赤石灯 (消えている) を壁に埋め、上下を彫刻石で縁取る。ともした数だけ、そのグループの画面で灯る
        for (BlockPos p : BOARD) {
            kit.set(p, Blocks.REDSTONE_LAMP.defaultBlockState());
            kit.set(p.above(), BuildKit.CHISELED);
            kit.set(p.below(), BuildKit.CHISELED);
        }
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

    // その人が一度に持てる灯の数 (周回は CARRY_LONG、初回は LIGHTS)
    public static int carryLimit(ServerLevel level, UUID group) {
        return jp.revati.skyabyss.trial.RouteLength.extended(level, group) ? CARRY_LONG : LIGHTS;
    }

    // 苗床から灯を摘む
    public static void takeFromBed(ServerLevel level, ServerPlayer player, BlockPos pos) {
        UUID group = Groups.keyOf(player);
        if (growable(level, group) <= 0) {
            Notice.bar(player, ModText.deny("message.skyabyss.lamp_bed_full", LIGHTS));
            return;
        }
        boolean entryBed = ENTRY_BEDS.contains(pos) || pos.equals(ENTRY_BED_POS2);
        if (!entryBed && jp.revati.skyabyss.trial.RouteLength.extended(level, group)) {
            Notice.bar(player, ModText.deny("message.skyabyss.lamp_bed_dormant"));
            return;
        }
        int limit = carryLimit(level, group);
        if (limit < LIGHTS && player.getInventory().countItem(ModItems.GLOW_ANEMONE.get()) >= limit) {
            Notice.bar(player, ModText.deny("message.skyabyss.lamp_one_at_a_time"));
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
    // 戸の開く音の高さの列 (音階。一音ごとに 5 tick)
    private static final float[] CHIME_PITCHES = {0.84f, 1.0f, 1.26f, 1.5f, 1.68f, 2.0f};

    // 出口の戸が開いた: 場面を始める。光の流れは、最後にともした台から出口の泡の柱へ、道のりの順に走る
    private static void startOpening(ServerLevel level, UUID group) {
        MazePlan plan = plan(level);
        int from = Math.min(plan.standK[Math.min(STANDS - 1, openingFrom.getOrDefault(group, STANDS - 1))], plan.path.size() - 1);
        List<Vec3> route = List.of();
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
            if (e > 60) {
                it.remove();
                continue;
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
            return ModText.objective("message.skyabyss.lamp_band_open", lit, STANDS);
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
                return ModText.objective("message.skyabyss.lamp_band", lit, STANDS, held);
            }
        }
        // 灯が手元にない: 苗床へ (入口の小部屋の苗床か、中ほどの休み場の苗床。近いほう)
        BlockPos mid = MazeBuilder.midPocket(plan.maze);
        int[] midCell = MazePlan.cellAt(mid.getX(), mid.getZ());
        int dMid = inRoom ? Integer.MAX_VALUE : plan.distance(here, midCell);
        int dEntry = inRoom ? 0 : plan.distance(here, start);
        return ModText.objective("message.skyabyss.lamp_band_bed", lit, STANDS);
    }

    private static void band(ServerLevel level, ServerPlayer player, UUID group) {
        LampBand.send(player, bandText(level, player, group));
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

    // 出口の戸が開く場面は毎 tick 進める
    @SubscribeEvent
    static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TitanTravel.TITAN_BACK) {
            return;
        }
        if (!OPENINGS.isEmpty()) {
            tickOpenings(level);
        }
    }

    // 自己テスト用: 動いている「戸が開く場面」の数
    static int openingsRunning() {
        return OPENINGS.size();
    }

    // ---------------------------------------------------------------- 道のりの見積もり (自己テスト。32 の 4 章の 1.5〜2 倍)
    // 一人で、入口の苗床から灯を摘み、三つの台をともして出口へ歩く道のり (迷路のマスの数)。carry は一度に持てる灯の数。
    // 灯が切れたら、いちばん得な苗床 (入口・二つめの入口・中ほど) へ寄ってから次の台へ。近道はともした数で開いたものを通る
    public static int soloWalk(long mazeSeed, int carry) {
        return soloWalk(mazeSeed, carry, 7, true);
    }

    // beds: 使う苗床のビット (1 = 入口、2 = 二つめの入口、4 = 中ほど)。cuts: 近道を通るか
    public static int soloWalk(long mazeSeed, int carry, int bedMask, boolean useCuts) {
        MazePlan plan = plan(mazeSeed);
        FloodedMaze maze = plan.maze;
        int[] start = {MAZE_START_I, MAZE_START_J};
        int[] start2 = {MAZE_START2_I, MAZE_START2_J};
        BlockPos midPos = MazeBuilder.midPocket(maze);
        int[] mid = MazePlan.cellAt(midPos.getX(), midPos.getZ());
        List<int[]> beds = new ArrayList<>();
        if ((bedMask & 1) != 0) {
            beds.add(start);
        }
        if ((bedMask & 2) != 0) {
            beds.add(start2);
        }
        if ((bedMask & 4) != 0) {
            beds.add(mid);
        }
        int lit0 = useCuts ? 0 : -100;
        int[] pos = start;
        int held = carry;
        int total = 0;
        for (int i = 0; i < STANDS; i++) {
            int[] stand = plan.path.get(plan.standK[i]);
            if (held == 0) {
                int best = Integer.MAX_VALUE;
                int[] via = null;
                for (int[] b : beds) {
                    int d = walkDist(plan, pos, b, lit0 + i) + walkDist(plan, b, stand, lit0 + i);
                    if (d >= 0 && d < best) {
                        best = d;
                        via = b;
                    }
                }
                total += walkDist(plan, pos, via, lit0 + i);
                pos = via;
                held = carry;
            }
            total += walkDist(plan, pos, stand, lit0 + i);
            pos = stand;
            held--;
        }
        return total + walkDist(plan, pos, new int[] {MAZE_EXIT_I, MAZE_EXIT_J}, lit0 + STANDS);
    }

    // 迷路の道のり。lit 本ともしたあとに開いている近道も通る
    private static int walkDist(MazePlan plan, int[] from, int[] to, int lit) {
        FloodedMaze maze = plan.maze;
        int[][] d = new int[maze.width()][maze.height()];
        for (int[] row : d) {
            java.util.Arrays.fill(row, -1);
        }
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        d[from[0]][from[1]] = 0;
        q.add(from);
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[0] == to[0] && c[1] == to[1]) {
                return d[c[0]][c[1]];
            }
            List<int[]> next = new ArrayList<>();
            for (int[] dir : FloodedMaze.DIRS) {
                if (maze.linked(c[0], c[1], dir[0], dir[1])) {
                    next.add(new int[] {c[0] + dir[0], c[1] + dir[1]});
                }
            }
            for (MazePlan.Cut cut : plan.cuts) {
                if (lit >= cut.lit()) {
                    if (cut.a()[0] == c[0] && cut.a()[1] == c[1]) {
                        next.add(cut.b());
                    } else if (cut.b()[0] == c[0] && cut.b()[1] == c[1]) {
                        next.add(cut.a());
                    }
                }
            }
            for (int[] n : next) {
                if (d[n[0]][n[1]] < 0) {
                    d[n[0]][n[1]] = d[c[0]][c[1]] + 1;
                    q.add(n);
                }
            }
        }
        return -1;
    }
}
