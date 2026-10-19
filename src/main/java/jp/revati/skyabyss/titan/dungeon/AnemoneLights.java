package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 闇の水路の「灯」(docs/design/23 の案 B「灯をたずさえる」)。
//
//  - 光る海のイソギンチャク (GLOW_ANEMONE): 水路の入口の小部屋の苗床で受け取り、手に持つとまわりを照らす。灯の台に置くと、一つ減る
//  - 灯の台 (ANEMONE_PEDESTAL): 水路の正しい道の上に 5 つ (stands)。グループごとに、灯をともせるのは 4 つまで (LIGHTS)
//  - ともした灯はグループの記録 (BackGroupData.Record の `lamp.<番号>`) に残り、その人たちの画面にだけ灯って見える (GroupOverlays。
//    ブロックのライトレベルがクライアントの光になる)。リセット (記録の進みを消す) で灯が消える。世界のブロックは灯らない
//  - 灯のまわり (CALM_RADIUS) では、水路の顎が、そのグループの人を襲わない (calms。WaterwayMaw の狙いと噛みつきが見る)
//  - 灯が NEED 本そろうと、出口の手前の封印の戸 (exitGate) が、そのグループにだけ開く (GroupGates)。初回を終えたグループには最初から開く
//    (FirstRun の「既知の道」)
//  - 灯の苗床 (ANEMONE_BED): 入口の小部屋と中ほどの休み場。「ともした灯 + 持っている灯」が LIGHTS に満たなければ、一つ摘める (なくしても詰まない)
//  - 入口の数え石 (ANEMONE_TALLY): ともした灯の数だけ丸が灯る。入口の小部屋の看板・囁き・目的の帯 (アクションバー) で、目的を伝える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AnemoneLights {
    // 灯の台の数・ともせる灯の数 (1 グループ)・出口の戸が開くのに要る灯の数
    public static final int STANDS = 5;
    public static final int LIGHTS = 4;
    public static final int NEED = 3;
    // 灯のまわりで魚が襲わない半径 (ブロック)
    public static final double CALM_RADIUS = 6.5;
    public static final String FLAG = "lamp.";
    public static final String FIRST_ROOM = "whale_maze";
    // 入口の小部屋の中の、数え石と苗床 (迷路の天井の上。床 y 81 の上)
    public static final BlockPos TALLY_POS = new BlockPos(MAZE_ENTRY_ROOM.x2(), MAZE_FLOOR + 5, MAZE_ENTRY_ROOM.z2() - 1);
    public static final BlockPos ENTRY_BED_POS = new BlockPos(MAZE_ENTRY_ROOM.x2(), MAZE_FLOOR + 5, MAZE_ENTRY_ROOM.z2());
    // 2 つめの入口の小部屋の苗床
    public static final BlockPos ENTRY_BED_POS2 = new BlockPos(MAZE_ENTRY_ROOM2.x2(), MAZE_FLOOR + 5, MAZE_ENTRY_ROOM2.z2());
    // 持ち歩きの光の強さ
    private static final int CARRY_LIGHT = 11;

    private AnemoneLights() {
    }

    // ---------------------------------------------------------------- 迷路から決まる場所

    private static long cachedSeed = Long.MIN_VALUE;
    private static List<BlockPos> cachedStands = List.of();
    private static List<BlockPos> cachedGate = List.of();

    private static synchronized void ensureCache(long seed) {
        if (seed == cachedSeed) {
            return;
        }
        FloodedMaze maze = MazeBuilder.maze(seed);
        cachedStands = List.copyOf(computeStands(maze));
        cachedGate = List.copyOf(computeGate(maze));
        cachedSeed = seed;
    }

    public static List<BlockPos> stands(long mazeSeed) {
        ensureCache(mazeSeed);
        return cachedStands;
    }

    public static List<BlockPos> exitGate(long mazeSeed) {
        ensureCache(mazeSeed);
        return cachedGate;
    }

    public static List<BlockPos> stands(ServerLevel level) {
        return stands(TitanBackData.get(level).mazeSeed());
    }

    public static List<BlockPos> exitGate(ServerLevel level) {
        return exitGate(TitanBackData.get(level).mazeSeed());
    }

    // 灯の台の場所 (正しい道の上。道を 6 つに分けた 5 つの境。息継ぎのくぼみのマスは避ける)。マスの真ん中の、水の一段目
    public static List<BlockPos> computeStands(FloodedMaze maze) {
        List<int[]> path = maze.solution();
        int n = path.size();
        List<BlockPos> result = new ArrayList<>();
        int prev = 0;
        for (int i = 0; i < STANDS; i++) {
            int k = Math.round((n - 1) * (i + 1) / (STANDS + 1f));
            if (k <= prev) {
                k = prev + 1;
            }
            if (k % MazeBuilder.POCKET_EVERY == 0) {
                k++;
            }
            result.add(standPos(path.get(k)));
            prev = k;
        }
        return result;
    }

    private static BlockPos standPos(int[] c) {
        return mazeCell(c[0], c[1]).offset(MAZE_CELL / 2, 0, MAZE_CELL / 2);
    }

    // 出口の手前の戸: 最後から二つ目のマスと出口のマスをつなぐ通路 (3×3。息継ぎのくぼみなら、壁にした一番上の段は除く)
    public static List<BlockPos> computeGate(FloodedMaze maze) {
        List<int[]> path = maze.solution();
        int n = path.size();
        int[] a = path.get(n - 2);
        int[] b = path.get(n - 1);
        BlockPos ca = mazeCell(a[0], a[1]);
        int dx = b[0] - a[0];
        int dz = b[1] - a[1];
        boolean pocket = (n - 2) % MazeBuilder.POCKET_EVERY == 0;
        List<BlockPos> list = new ArrayList<>();
        for (int t = 0; t < MAZE_CELL; t++) {
            int x = dx != 0 ? ca.getX() + (dx > 0 ? MAZE_CELL : -1) : ca.getX() + t;
            int z = dz != 0 ? ca.getZ() + (dz > 0 ? MAZE_CELL : -1) : ca.getZ() + t;
            for (int y = ca.getY(); y <= ca.getY() + (pocket ? 1 : 2); y++) {
                list.add(new BlockPos(x, y, z));
            }
        }
        return list;
    }

    // 迷路を掘り直すとき (MazeBuilder.buildMaze の最後): 灯の台・戸・中ほどの苗床を置き、初回の部屋を登録する
    static void build(BuildKit kit, FloodedMaze maze) {
        BlockState stand = ModBlocks.ANEMONE_PEDESTAL.get().defaultBlockState();
        for (BlockPos p : computeStands(maze)) {
            kit.set(p, stand);
        }
        BlockState gate = ModBlocks.SEALED_GATE.get().defaultBlockState();
        for (BlockPos p : computeGate(maze)) {
            kit.set(p, gate);
        }
        BlockPos mid = MazeBuilder.midPocket(maze);
        // 中ほどの休み場: 隅の足場の上 (空気の層の一段目)。復活位置 (北西の隅) と紫水晶 (南東の隅) の間の隅
        kit.set(mid.offset(MAZE_CELL - 1, 2, 0), ModBlocks.ANEMONE_BED.get().defaultBlockState());
        registerRoom(maze);
    }

    // 入口の小部屋の数え石と苗床 (島を建てるときに一度だけ)
    static void buildEntry(BuildKit kit) {
        kit.set(TALLY_POS, ModBlocks.ANEMONE_TALLY.get().defaultBlockState());
        kit.set(ENTRY_BED_POS, ModBlocks.ANEMONE_BED.get().defaultBlockState());
        kit.set(ENTRY_BED_POS2, ModBlocks.ANEMONE_BED.get().defaultBlockState());
    }

    // 初回を終えたグループには、出口の戸が最初から開く (既知の道。FirstRun が 1 秒ごとに開け直す)
    private static void registerRoom(FloodedMaze maze) {
        AABB box = new AABB(MAZE_X0, MAZE_FLOOR, MAZE_Z0, MAZE_X0 + MAZE_W * (MAZE_CELL + 1) + 1, MAZE_FLOOR + 6, MAZE_Z0 + MAZE_H * (MAZE_CELL + 1) + 1);
        FirstRun.register(FirstRun.Room.firstRun(FIRST_ROOM, TitanTravel.TITAN_BACK, box).doors(computeGate(maze)));
    }

    // 迷路が別の形に作り直される前に: 灯の記録と、古い戸の上書きを全グループから消す (台の番号と戸の場所が変わるため)
    public static void clearGroups(ServerLevel level, long oldSeed) {
        List<BlockPos> oldGate = exitGate(oldSeed);
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
            had |= GroupGates.release(level, id, oldGate);
            if (had) {
                GroupOverlays.refresh(level, id);
            }
        }
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
            Notice.bar(player, ModText.hint("message.skyabyss.lamp_needs"));
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
        Notice.bar(player, ModText.hint("message.skyabyss.lamp_tally", litCount(r), STANDS, NEED, LIGHTS));
    }

    // 灯の数に合わせて、出口の戸をそのグループにだけ開ける (灯が NEED そろったとき・初回を終えたとき)
    public static boolean sync(ServerLevel level, UUID group) {
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        if (r == null || litCount(r) < NEED) {
            return false;
        }
        List<BlockPos> gate = exitGate(level);
        if (GroupGates.isOpen(level, group, gate)) {
            return false;
        }
        boolean changed = GroupGates.open(level, group, gate);
        if (changed) {
            BlockPos at = gate.get(gate.size() / 2);
            Groups.sound(level, group, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.4f, 0.7f);
            for (ServerPlayer member : Groups.online(level, group)) {
                Notice.hint(member, "message.skyabyss.lamp_gate_open");
            }
        }
        return changed;
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

    // ---------------------------------------------------------------- グループごとの見かけ (灯った台・数え石)

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
            int n = 0;
            for (int i = 0; i < stands.size(); i++) {
                if (isLit(r, i)) {
                    n++;
                    out.accept(stands.get(i), ModBlocks.ANEMONE_PEDESTAL.get().defaultBlockState().setValue(AnemonePedestalBlock.LIT, true));
                }
            }
            if (n > 0) {
                out.accept(TALLY_POS, ModBlocks.ANEMONE_TALLY.get().defaultBlockState().setValue(AnemoneTallyBlock.COUNT, n));
            }
        });
    }

    // ---------------------------------------------------------------- 持ち歩きの光・目的の帯・灯の粒

    private static final Map<UUID, BlockPos> CARRY_AT = new HashMap<>();
    private static final Map<UUID, BlockState> CARRY_WAS = new HashMap<>();

    static boolean inMaze(Entity e) {
        return e.getX() >= MAZE_X0 && e.getX() <= MAZE_X0 + MAZE_W * (MAZE_CELL + 1) + 1 && e.getZ() >= MAZE_Z0
                && e.getZ() <= MAZE_Z0 + MAZE_H * (MAZE_CELL + 1) + 1 && e.getY() >= MAZE_FLOOR && e.getY() <= MAZE_FLOOR + 8;
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
            UUID group = Groups.keyOf(player);
            sync(level, group);
            band(level, player, group);
        }
    }

    // 目的の帯 (アクションバー。水路の中にいる間、3 秒ごと): いま何をすればよいか
    private static void band(ServerLevel level, ServerPlayer player, UUID group) {
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        int lit = litCount(r);
        if (GroupGates.isOpen(level, group, exitGate(level))) {
            Notice.bar(player, ModText.objective("message.skyabyss.lamp_band_open"));
        } else {
            Notice.bar(player, ModText.objective("message.skyabyss.lamp_band", lit, NEED, player.getInventory().countItem(ModItems.GLOW_ANEMONE.get())));
        }
    }

    // ともした台の粒 (その人にだけ。近いものだけ)
    private static void glowStands(ServerLevel level, ServerPlayer player) {
        BackGroupData.Record r = BackGroupData.get(level).peek(Groups.keyOf(player));
        if (r == null) {
            return;
        }
        List<BlockPos> stands = stands(level);
        for (int i = 0; i < stands.size(); i++) {
            BlockPos s = stands.get(i);
            if (isLit(r, i) && player.distanceToSqr(s.getX(), s.getY(), s.getZ()) < 40 * 40) {
                level.sendParticles(player, ParticleTypes.END_ROD, false, s.getX() + 0.5, s.getY() + 1.1, s.getZ() + 0.5, 2, 0.3, 0.45, 0.3, 0.012);
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
    }

    // 初回の部屋の登録を、世界が読まれたあとにも (FirstRun の登録は保存されない)。1 秒ごと、背中に人がいるときに見る
    @SubscribeEvent
    static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TitanTravel.TITAN_BACK || level.getGameTime() % 20 != 11
                || level.players().isEmpty()) {
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
}
