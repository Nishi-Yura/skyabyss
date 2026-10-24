package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 部屋を建てる道具 (docs/design/32 の 7 章)。RaidRoom.build(ctx) の中で呼ぶ。
//
//   RaidKit.Cell cell = RaidKit.buildCell(ctx, new RaidKit.Spec(14, 14, 9, RaidKit.BONE));
//
// buildCell は ctx.origin() (床の中心) のまわりに閉じた一室を建て、ctx.cell() に結果を入れる。外は見えない (全部が壁・床・天井で閉じる):
//   - 広間: 床 (原点の高さ) の x ∈ [-hx, hx]、z ∈ [-hz, hz]、立てる空間は床の上 height ブロック。床の縁に飾りの帯 (trim)、天井に灯り (light)
//   - 入口の間 (南 = +z 側): 広間の南の壁の戸 (3×3。entryGate) の奥に、人が運ばれて立つ休み場 (rest) がある小部屋。
//       戸は始まるまで閉じていて、start で枠組みが開く。全滅・fail のあとは人がここへ戻り、戸が閉じ、準備のあと開く
//   - 出口の間 (北 = -z 側): 広間の北の壁の戸 (exitGate) の奥の小部屋 (exitSpot)。越えると枠組みが開く (4 秒後に次の段へ運ばれる)
// 座標は ctx.local(x, dy, z) か cell の値で。広間の中の立つ位置は cell.floor(x, z) (その柱の足元 = 床の上)。
// 戸は setGate(ctx, gate, open) で自分でも開け閉めできる (部屋の途中の仕切りには gateBox で好きな箱を作る)。
// 中の飾り・仕掛けは、build の中で fill / set で足す。足したブロックの範囲は広間の中だけにすること (部屋の外を壊さない)
// チャンクは建てるとき読み込み、部屋が片付くまで保つ (hold / release)
public final class RaidKit {
    private RaidKit() {
    }

    // 部屋の色づけ。floor = 床、trim = 床の縁・柱の根、wall = 壁、ceiling = 天井、light = 天井の灯り、gate = 戸
    public record Palette(BlockState floor, BlockState trim, BlockState wall, BlockState ceiling, BlockState light, BlockState gate) {
    }

    // 岩鯨 (石・骨・歌)
    public static final Palette BONE = new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.BONE_BLOCK.defaultBlockState(),
            Blocks.STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.IRON_BARS.defaultBlockState());
    // 潮殻 (石灰・珊瑚・潮)
    public static final Palette TIDE = new Palette(Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState(),
            Blocks.PRISMARINE.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.IRON_BARS.defaultBlockState());
    // 雷翼 (羽・銅・嵐)
    public static final Palette STORM = new Palette(Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState(), Blocks.WAXED_CUT_COPPER.defaultBlockState(),
            Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState(), Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState(),
            Blocks.VERDANT_FROGLIGHT.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState());
    // 余燼 (黒曜・熾・鋳鉄)
    public static final Palette EMBER = new Palette(Blocks.BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(),
            Blocks.IRON_BARS.defaultBlockState());

    // 広間の寸法 (hx・hz は床の中心から縁までの半分、height は床の上の高さ)
    // dark = 暗さそのものが遊びの部屋 (灯りを持つ人のまわりだけ見える)。床の見えない灯りを置かず、brightenFloor も触らない
    public record Spec(int hx, int hz, int height, Palette palette, boolean dark) {
        public Spec(int hx, int hz, int height, Palette palette) {
            this(hx, hz, height, palette, false);
        }
    }

    // 建てた部屋。origin = 床の中心のブロック。inner = 広間の立てる空間、bounds = 部屋全体 (片付けに使う)。dark = Spec.dark と同じ
    public record Cell(BlockPos origin, int hx, int hz, int height, BlockPos rest, BlockPos exitSpot, AABB inner, AABB bounds,
            List<BlockPos> entryGate, List<BlockPos> exitGate, Palette palette, boolean dark) {
        public Cell(BlockPos origin, int hx, int hz, int height, BlockPos rest, BlockPos exitSpot, AABB inner, AABB bounds,
                List<BlockPos> entryGate, List<BlockPos> exitGate, Palette palette) {
            this(origin, hx, hz, height, rest, exitSpot, inner, bounds, entryGate, exitGate, palette, false);
        }

        // 広間の (x, z) (原点からの相対) の、床の上に立つ位置
        public BlockPos floor(int x, int z) {
            return origin.offset(x, 1, z);
        }

        public boolean inArena(double x, double z) {
            return x >= origin.getX() - hx && x <= origin.getX() + hx + 1 && z >= origin.getZ() - hz && z <= origin.getZ() + hz + 1;
        }

        public int minX() {
            return origin.getX() - hx;
        }

        public int maxX() {
            return origin.getX() + hx;
        }

        public int minZ() {
            return origin.getZ() - hz;
        }

        public int maxZ() {
            return origin.getZ() + hz;
        }
    }

    // 入口の間の奥行き (広間の南の壁の外)・出口の間の奥行き
    private static final int ENTRY_DEPTH = 5;
    private static final int EXIT_DEPTH = 3;
    private static final int FLAG = Block.UPDATE_CLIENTS;
    // 床の灯り (見えない光源。明るさ 11 は 12 未満なので、飾りの明るい塊の数には入らない)
    private static final BlockState FLOOR_LIGHT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 11);

    // 部屋を建てる (RaidRoom.build の中で一度)。ctx.cell() にも入る
    public static Cell buildCell(RaidCtx ctx, Spec spec) {
        ServerLevel level = ctx.level();
        BlockPos o = ctx.origin();
        Palette pal = spec.palette();
        int hx = spec.hx();
        int hz = spec.hz();
        int h = spec.height();
        int fy = o.getY();
        // 部屋全体: x ∈ [-hx-1, hx+1]、z ∈ [-hz-1-(EXIT_DEPTH+1), hz+1+(ENTRY_DEPTH+1)]、y ∈ [fy-1, fy+h+1]
        int x0 = o.getX() - hx - 1;
        int x1 = o.getX() + hx + 1;
        int z0 = o.getZ() - hz - 2 - EXIT_DEPTH;
        int z1 = o.getZ() + hz + 2 + ENTRY_DEPTH;
        AABB bounds = new AABB(x0, fy - 1, z0, x1 + 1, fy + h + 2, z1 + 1);
        hold(level, ctx.group(), bounds);
        clear(level, bounds);
        // 殻: 全体を壁で満たしてから中をくり抜く
        fill(level, x0, fy - 1, z0, x1, fy + h + 1, z1, pal.wall());
        fill(level, x0, fy + h + 1, z0, x1, fy + h + 1, z1, pal.ceiling());
        // 広間
        fill(level, o.getX() - hx, fy + 1, o.getZ() - hz, o.getX() + hx, fy + h, o.getZ() + hz, Blocks.AIR.defaultBlockState());
        fill(level, o.getX() - hx, fy, o.getZ() - hz, o.getX() + hx, fy, o.getZ() + hz, pal.floor());
        // 床の縁の帯
        for (int x = -hx; x <= hx; x++) {
            set(level, o.offset(x, 0, -hz), pal.trim());
            set(level, o.offset(x, 0, hz), pal.trim());
        }
        for (int z = -hz; z <= hz; z++) {
            set(level, o.offset(-hx, 0, z), pal.trim());
            set(level, o.offset(hx, 0, z), pal.trim());
        }
        // 床の模様 (中心から四方へ細い筋)
        for (int x = -hx + 1; x < hx; x++) {
            set(level, o.offset(x, 0, 0), pal.trim());
        }
        for (int z = -hz + 1; z < hz; z++) {
            set(level, o.offset(0, 0, z), pal.trim());
        }
        // 天井の灯り (八ブロックおき)。天井の板に埋める
        for (int x = -hx + 3; x <= hx - 2; x += 8) {
            for (int z = -hz + 3; z <= hz - 2; z += 8) {
                set(level, new BlockPos(o.getX() + x, fy + h + 1, o.getZ() + z), pal.light());
            }
        }
        // 床の灯り (見えない灯り): 床から二ブロック上に、四ブロックおきに光源。天井だけでは床の隅が暗い (39 の 3.7 節)。
        // 立つ所 (床の上の一ブロック) は空気のまま、頭の上を通るので歩き・戦いの邪魔にならない。飾りや仕掛けは後から足して上書きできる
        // 暗い部屋 (Spec.dark) は置かない: 灯りは部屋の仕掛け (持つ人のまわり) だけ
        for (int x = -hx + 2; x <= hx - 2 && !spec.dark(); x += 4) {
            for (int z = -hz + 2; z <= hz - 2; z += 4) {
                set(level, new BlockPos(o.getX() + x, fy + 2, o.getZ() + z), FLOOR_LIGHT);
            }
        }
        // 入口の間 (南)
        int ez0 = o.getZ() + hz + 2;
        fill(level, o.getX() - 3, fy + 1, ez0, o.getX() + 3, fy + 4, ez0 + ENTRY_DEPTH - 1, Blocks.AIR.defaultBlockState());
        fill(level, o.getX() - 3, fy, ez0, o.getX() + 3, fy, ez0 + ENTRY_DEPTH - 1, pal.floor());
        fill(level, o.getX() - 1, fy, ez0 + 1, o.getX() + 1, fy, ez0 + 3, pal.trim());
        set(level, new BlockPos(o.getX() - 3, fy + 1, ez0 + ENTRY_DEPTH - 1), Blocks.LANTERN.defaultBlockState());
        set(level, new BlockPos(o.getX() + 3, fy + 1, ez0 + ENTRY_DEPTH - 1), Blocks.LANTERN.defaultBlockState());
        set(level, new BlockPos(o.getX(), fy + 4, ez0 + 2), pal.light());
        // 入口の戸: 広間の南の壁 (z = hz + 1) と、その外の壁 (z = hz + 2 は小部屋の手前なので戸は一枚だけ)。戸は z = hz + 1
        List<BlockPos> entryGate = gateBox(o.getX() - 1, fy + 1, o.getZ() + hz + 1, o.getX() + 1, fy + 3, o.getZ() + hz + 1);
        // 戸の奥 (z = hz + 2) も開けて小部屋とつなぐ。戸の列だけをくり抜く
        fill(level, o.getX() - 1, fy + 1, o.getZ() + hz + 2, o.getX() + 1, fy + 3, o.getZ() + hz + 2, Blocks.AIR.defaultBlockState());
        setGate(level, entryGate, pal, false);
        // 出口の間 (北)
        int xz1 = o.getZ() - hz - 2;
        fill(level, o.getX() - 2, fy + 1, xz1 - EXIT_DEPTH + 1, o.getX() + 2, fy + 4, xz1, Blocks.AIR.defaultBlockState());
        fill(level, o.getX() - 2, fy, xz1 - EXIT_DEPTH + 1, o.getX() + 2, fy, xz1, pal.floor());
        set(level, new BlockPos(o.getX(), fy + 4, xz1 - 1), pal.light());
        List<BlockPos> exitGate = gateBox(o.getX() - 1, fy + 1, o.getZ() - hz - 1, o.getX() + 1, fy + 3, o.getZ() - hz - 1);
        setGate(level, exitGate, pal, false);
        BlockPos rest = new BlockPos(o.getX(), fy + 1, ez0 + 2);
        BlockPos exitSpot = new BlockPos(o.getX(), fy + 1, xz1 - 1);
        AABB inner = new AABB(o.getX() - hx, fy + 1, o.getZ() - hz, o.getX() + hx + 1, fy + h + 1, o.getZ() + hz + 1);
        Cell cell = new Cell(o, hx, hz, h, rest, exitSpot, inner, bounds, entryGate, exitGate, pal, spec.dark());
        ctx.setCell(cell);
        RaidLore.placeTablet(ctx, cell);
        return cell;
    }

    // 飾りや仕掛けの陰で床が暗いときの補い (飾りを置いたあと、build の最後に呼ぶ)。広間の空気の所だけ、床から二ブロック上に step おきに光源を足す
    public static void brightenFloor(RaidCtx ctx, int step) {
        Cell c = ctx.cell();
        if (c.dark()) {
            return;
        }
        ServerLevel level = ctx.level();
        for (int x = -c.hx() + 1; x <= c.hx() - 1; x += step) {
            for (int z = -c.hz() + 1; z <= c.hz() - 1; z += step) {
                BlockPos p = c.origin().offset(x, 2, z);
                if (level.getBlockState(p).isAir()) {
                    set(level, p, FLOOR_LIGHT);
                } else if (level.getBlockState(p.below()).isAir()) {
                    // 頭の上が塞がれた所 (張り出しの下): 足もとの空気に光源を置く (光源は通り抜けられる)
                    set(level, p.below(), FLOOR_LIGHT);
                }
            }
        }
    }

    // 中ボス・心臓の部屋: 運ばれる所 (休み場。全滅のあとに戻る所も) を広間の中の (x, z) へ移す。入口の間を歩かずに、そのまま広間で始まる
    public static Cell restInArena(RaidCtx ctx, int x, int z) {
        Cell c = ctx.cell();
        Cell n = new Cell(c.origin(), c.hx(), c.hz(), c.height(), c.floor(x, z), c.exitSpot(), c.inner(), c.bounds(), c.entryGate(), c.exitGate(), c.palette(), c.dark());
        ctx.setCell(n);
        return n;
    }

    // ---- 戸
    // 箱の中のブロックの位置の一覧 (戸にする箱)
    public static List<BlockPos> gateBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            out.add(p.immutable());
        }
        return out;
    }

    // 戸を開ける (空気) / 閉じる (palette.gate)
    public static void setGate(RaidCtx ctx, List<BlockPos> gate, boolean open) {
        setGate(ctx.level(), gate, ctx.cell().palette(), open);
    }

    private static void setGate(ServerLevel level, List<BlockPos> gate, Palette pal, boolean open) {
        BlockState st = open ? Blocks.AIR.defaultBlockState() : pal.gate();
        for (BlockPos p : gate) {
            level.setBlock(p, st, FLAG);
        }
        if (!gate.isEmpty()) {
            BlockPos c = gate.get(gate.size() / 2);
            level.playSound(null, c, open ? net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN : net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.6f);
        }
    }

    // ---- ブロック
    public static void set(ServerLevel level, BlockPos p, BlockState st) {
        level.setBlock(p, st, FLAG);
    }

    // 箱を一種類で満たす (角を含む)
    public static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState st) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    level.setBlock(new BlockPos(x, y, z), st, FLAG);
                }
            }
        }
    }

    public static void fill(ServerLevel level, BlockPos a, BlockPos b, BlockState st) {
        fill(level, a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), st);
    }

    // 箱を空気に戻す
    public static void clear(ServerLevel level, AABB box) {
        fill(level, (int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX - 1, (int) box.maxY - 1, (int) box.maxZ - 1, Blocks.AIR.defaultBlockState());
    }

    // 広間の中のランダムな床の位置 (縁から margin ブロック内側。足元の高さ)
    public static BlockPos randomSpot(RaidCtx ctx, int margin) {
        Cell c = ctx.cell();
        int x = ctx.rng().nextInt(Math.max(1, 2 * (c.hx() - margin) + 1)) - (c.hx() - margin);
        int z = ctx.rng().nextInt(Math.max(1, 2 * (c.hz() - margin) + 1)) - (c.hz() - margin);
        return c.floor(x, z);
    }

    // ---- チャンクの保持
    private static final TicketType<ChunkPos> TICKET = TicketType.create("skyabyss_raid", Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<UUID, List<ChunkPos>> HELD = new HashMap<>();

    // 箱のチャンクを読み込み、片付けまで保つ
    static void hold(ServerLevel level, UUID group, AABB box) {
        List<ChunkPos> list = HELD.computeIfAbsent(group, k -> new ArrayList<>());
        for (int cx = ((int) Math.floor(box.minX)) >> 4; cx <= ((int) Math.ceil(box.maxX)) >> 4; cx++) {
            for (int cz = ((int) Math.floor(box.minZ)) >> 4; cz <= ((int) Math.ceil(box.maxZ)) >> 4; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(TICKET, cp, 2, cp);
                list.add(cp);
                level.getChunk(cx, cz);
            }
        }
    }

    static void release(ServerLevel level, UUID group) {
        List<ChunkPos> list = HELD.remove(group);
        if (list != null) {
            for (ChunkPos cp : list) {
                level.getChunkSource().removeRegionTicket(TICKET, cp, 2, cp);
            }
        }
    }

    static void clearAll() {
        HELD.clear();
    }
}
