package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.Half;

// 岩鯨の背を建てるための道具箱。部屋を掘る・壁に柱を立てる・天井に骨の梁を渡す・灯りを吊るす、などの部品
final class BuildKit {
    static final int UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    // 体内の石組みの基本の材質
    static final BlockState BRICKS = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    static final BlockState CRACKED = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
    static final BlockState TILES = Blocks.DEEPSLATE_TILES.defaultBlockState();
    static final BlockState POLISHED = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    static final BlockState CHISELED = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    final RandomSource random;
    // いまの材質 (ゾーンごとに替える。ZonePalette)
    private ZonePalette pal = ZonePalette.DEEP;

    BuildKit(ServerLevel level, RandomSource random) {
        this.level = level;
        this.random = random;
    }

    ServerLevel level() {
        return level;
    }

    // 以降の部品 (部屋・柱・梁・灯り・休み場) の材質をゾーンのものにする
    BuildKit palette(ZonePalette p) {
        this.pal = p;
        return this;
    }

    ZonePalette pal() {
        return pal;
    }

    void set(int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, UPDATE);
    }

    void set(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, UPDATE);
    }

    BlockState get(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z));
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    // 壁の石は、ところどころひび割れさせて古びた感じにする
    BlockState wallStone() {
        return random.nextFloat() < 0.18f ? pal.wallAlt() : pal.wall();
    }

    // 内側を空にし、外周1マスを壁・床・天井で覆う。すでに掘った空間はふさがない
    void room(Box box, BlockState floor, BlockState ceiling) {
        room(box, floor, ceiling, false);
    }

    // solid = true: 外周が空気 (体の外や別の空洞) でも壁・床・天井で埋める (体の殻が薄い所に建てる部屋で、壁に穴が空かないように)
    void room(Box box, BlockState floor, BlockState ceiling, boolean solid) {
        for (int x = box.x1() - 1; x <= box.x2() + 1; x++) {
            for (int z = box.z1() - 1; z <= box.z2() + 1; z++) {
                for (int y = box.floorY(); y <= box.ceilingY(); y++) {
                    boolean inside = x >= box.x1() && x <= box.x2() && z >= box.z1() && z <= box.z2()
                            && y > box.floorY() && y < box.ceilingY();
                    if (inside) {
                        set(x, y, z, AIR);
                    } else if (solid || !get(x, y, z).isAir()) {
                        set(x, y, z, y == box.floorY() ? floor : y == box.ceilingY() ? ceiling : wallStone());
                    }
                }
            }
        }
    }

    void room(Box box) {
        room(box, pal.floor(), pal.ceiling(), false);
    }

    // 外周を必ず埋める部屋 (solid)
    void roomSolid(Box box) {
        room(box, pal.floor(), pal.ceiling(), true);
    }

    // 戸口を開ける (壁に x1〜x2 × z1〜z2 × 高さ height の穴)
    void doorway(int x1, int x2, int z1, int z2, int floorY, int height) {
        fill(x1, floorY + 1, z1, x2, floorY + height, z2, AIR);
    }

    // 壁ぎわの柱: 一定の間隔で、壁に沿って立つ磨き石の柱と、彫刻の柱頭。
    // 戸口・封印・はしごの前には立てない (壁の向こうが空いている所、封印のある所、足元が埋まっている所は飛ばす)
    void pilasters(Box box, int spacing) {
        int top = box.ceilingY() - 1;
        for (int x = box.x1() + 2; x < box.x2() - 1; x += spacing) {
            column(x, box.z1(), x, box.z1() - 1, box.floorY() + 1, top);
            column(x, box.z2(), x, box.z2() + 1, box.floorY() + 1, top);
        }
        for (int z = box.z1() + 2; z < box.z2() - 1; z += spacing) {
            column(box.x1(), z, box.x1() - 1, z, box.floorY() + 1, top);
            column(box.x2(), z, box.x2() + 1, z, box.floorY() + 1, top);
        }
    }

    private void column(int x, int z, int behindX, int behindZ, int y1, int y2) {
        for (int y = y1; y <= y1 + 3; y++) {
            BlockState behind = get(behindX, y, behindZ);
            if (behind.isAir() || behind.is(ModBlocks.SEALED_GATE.get()) || !get(x, y, z).isAir()) {
                return;
            }
        }
        for (int y = y1; y <= y2; y++) {
            set(x, y, z, y == y2 ? pal.cap() : y == y1 ? pal.floorAlt() : pal.pillarAlong(Direction.Axis.Y));
        }
    }

    // 壁に埋め込んだ灯り (巨神の体の中で淡く光る、海の灯り)。一定の間隔で、高さ y の壁の石を置き換える。
    // 戸口・封印・柱の前は避ける
    void wallLamps(Box box, int spacing, int y) {
        BlockState lamp = pal.lamp();
        for (int x = box.x1() + 1; x < box.x2(); x += spacing) {
            lamp(x, y, box.z1() - 1, x, box.z1(), lamp);
            lamp(x, y, box.z2() + 1, x, box.z2(), lamp);
        }
        for (int z = box.z1() + 1; z < box.z2(); z += spacing) {
            lamp(box.x1() - 1, y, z, box.x1(), z, lamp);
            lamp(box.x2() + 1, y, z, box.x2(), z, lamp);
        }
    }

    private void lamp(int wx, int y, int wz, int ix, int iz, BlockState lamp) {
        BlockState wall = get(wx, y, wz);
        boolean isWall = wall.is(pal.wall().getBlock()) || wall.is(pal.wallAlt().getBlock())
                || wall.is(Blocks.BASALT) || wall.is(Blocks.BLACKSTONE) || wall.is(pal.ceiling().getBlock());
        if (isWall && get(ix, y, iz).isAir()) {
            set(wx, y, wz, lamp);
        }
    }

    // 天井に渡す、鯨の肋骨のような骨の梁
    void ribs(Box box, int spacing, boolean alongZ) {
        BlockState bone = pal.beamAlong(alongZ ? Direction.Axis.X : Direction.Axis.Z);
        int y = box.ceilingY() - 1;
        if (alongZ) {
            for (int z = box.z1() + 2; z < box.z2(); z += spacing) {
                for (int x = box.x1(); x <= box.x2(); x++) {
                    // 両端は1段下げて弧に見せる
                    int yy = (x == box.x1() || x == box.x2()) ? y - 1 : y;
                    set(x, yy, z, bone);
                }
            }
        } else {
            for (int x = box.x1() + 2; x < box.x2(); x += spacing) {
                for (int z = box.z1(); z <= box.z2(); z++) {
                    int yy = (z == box.z1() || z == box.z2()) ? y - 1 : y;
                    set(x, yy, z, bone);
                }
            }
        }
    }

    // 床の縁取りと、中央の模様
    void floorBorder(Box box) {
        for (int x = box.x1(); x <= box.x2(); x++) {
            for (int z = box.z1(); z <= box.z2(); z++) {
                boolean edge = x == box.x1() || x == box.x2() || z == box.z1() || z == box.z2();
                if (edge && !get(x, box.floorY(), z).isAir()) {
                    set(x, box.floorY(), z, pal.floorAlt());
                }
            }
        }
    }

    // 天井から鎖で吊るした魂のランタン
    // 天井 (y = ceilingY) が固いときだけ吊るす。鎖は天井から途切れずに下りる (空中にぶら下がる鎖を作らない)
    void hangingLantern(int x, int z, int ceilingY, int chainLength) {
        if (get(x, ceilingY, z).isAir()) {
            return;
        }
        for (int i = 1; i <= chainLength; i++) {
            if (!get(x, ceilingY - i, z).isAir()) {
                return;
            }
        }
        for (int i = 1; i <= chainLength; i++) {
            set(x, ceilingY - i, z, Blocks.CHAIN.defaultBlockState());
        }
        set(x, ceilingY - chainLength - 1, z, pal.hangingLantern());
    }

    // 足もとが固いときだけ置く
    void lantern(int x, int y, int z) {
        if (get(x, y - 1, z).isAir()) {
            return;
        }
        set(x, y, z, pal.lantern());
    }

    // 天井から垂れる鍾乳石
    void stalactite(int x, int ceilingY, int z, int length) {
        // 天井が固く、下が空いているときだけ (鎖や灯りと同じ柱に重ねない)
        for (int i = 1; i <= length; i++) {
            if (!get(x, ceilingY - i, z).isAir()) {
                return;
            }
        }
        if (get(x, ceilingY, z).isAir()) {
            return;
        }
        for (int i = 1; i <= length; i++) {
            DripstoneThickness t = i == length ? DripstoneThickness.TIP : i == length - 1 ? DripstoneThickness.FRUSTUM
                    : i == 1 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            set(x, ceilingY - i, z, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN).setValue(PointedDripstoneBlock.THICKNESS, t));
        }
    }

    // 床にところどころ苔を散らす
    void moss(Box box, float chance) {
        for (int x = box.x1(); x <= box.x2(); x++) {
            for (int z = box.z1(); z <= box.z2(); z++) {
                int y = box.floorY() + 1;
                BlockState below = get(x, y - 1, z);
                if (random.nextFloat() < chance && get(x, y, z).isAir() && !below.isAir() && !below.is(Blocks.LADDER)) {
                    set(x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
    }

    // 休み場の設え: 青い焚き火、腰掛け、灯り。案内の看板は置かない (着いたことは焚き火と音で、先の様子は最初の一度だけの囁きで伝える: BackWhispers)
    void restPoint(Box box) {
        restPoint(box, false);
    }

    // smokeless = true: 焚き火の代わりに、煙の出ない「魂の火鉢」(壁の柱の上の魂のランタン。まわりに灯したろうそく)。
    // 閉じた部屋で焚き火の煙が壁や掲示板を覆うのを避ける。しゃがんで触れるとあきらめる働きは焚き火と同じ (TitanBackTrials)
    void restPoint(Box box, boolean smokeless) {
        BlockPos c = box.center();
        set(c.getX(), box.floorY(), c.getZ(), pal.trim());
        if (smokeless) {
            set(c.getX(), box.floorY() + 1, c.getZ(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
            set(c.getX(), box.floorY() + 2, c.getZ(), pal.lantern());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                set(c.getX() + d.getStepX(), box.floorY() + 1, c.getZ() + d.getStepZ(),
                        Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
            }
        } else {
            set(c.getX(), box.floorY() + 1, c.getZ(), jp.revati.skyabyss.relic.RelicBlocks.hearth(jp.revati.skyabyss.relic.RelicStyle.WHALE, true));
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int bx = c.getX() + d.getStepX() * 2;
            int bz = c.getZ() + d.getStepZ() * 2;
            if (bx > box.x1() && bx < box.x2() && bz > box.z1() && bz < box.z2() && get(bx, box.floorY() + 1, bz).isAir()) {
                set(bx, box.floorY() + 1, bz, stairsOf(pal)
                        .setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, Half.BOTTOM));
            }
        }
        set(box.x1(), box.floorY() + 1, box.z1(), Blocks.AMETHYST_CLUSTER.defaultBlockState());
        set(box.x2(), box.floorY() + 1, box.z2(), Blocks.AMETHYST_CLUSTER.defaultBlockState());
        hangingLantern(c.getX() + 1, c.getZ() + 1, box.ceilingY(), 1);
    }

    // ---------------------------------------------------------------- 道しるべ・戸の枠・旗 (版 18。ゾーンごとの案内)
    // 折れ線 (x, z, x, z, ...) をたどる床のマスの並び (始点から終点まで、1 マスずつ)
    static java.util.List<int[]> line(int... xz) {
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int i = 0; i + 3 < xz.length; i += 2) {
            int x = xz[i];
            int z = xz[i + 1];
            int tx = xz[i + 2];
            int tz = xz[i + 3];
            while (x != tx || z != tz) {
                if (cells.isEmpty() || cells.get(cells.size() - 1)[0] != x || cells.get(cells.size() - 1)[1] != z) {
                    cells.add(new int[]{x, z});
                }
                if (x != tx) {
                    x += Integer.signum(tx - x);
                } else {
                    z += Integer.signum(tz - z);
                }
            }
        }
        int[] last = {xz[xz.length - 2], xz[xz.length - 1]};
        if (cells.isEmpty() || cells.get(cells.size() - 1)[0] != last[0] || cells.get(cells.size() - 1)[1] != last[1]) {
            cells.add(last);
        }
        return cells;
    }

    // 床に埋め込んだ灯りの道: 1 つおきに glow (床と同じ高さに平らに)、あいだは base。固い床の上だけ
    void routeStrip(int floorY, java.util.List<int[]> cells, BlockState glow, BlockState base) {
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            if (get(c[0], floorY, c[1]).isAir()) {
                continue;
            }
            set(c[0], floorY, c[1], i % 2 == 0 ? glow : base);
        }
    }

    // 戸の枠: 戸の口の両脇 (柱) と上 (まぐさ)。戸の口の外の壁にだけ置く (空気には置かない)
    void doorFrame(java.util.List<BlockPos> door, int floorY) {
        if (door.isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos p : door) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
            maxY = Math.max(maxY, p.getY());
        }
        boolean alongX = minZ == maxZ;
        for (int y = floorY + 1; y <= maxY + 1; y++) {
            for (int side = -1; side <= 1; side += 2) {
                int x = alongX ? (side < 0 ? minX - 1 : maxX + 1) : minX;
                int z = alongX ? minZ : (side < 0 ? minZ - 1 : maxZ + 1);
                if (!get(x, y, z).isAir()) {
                    set(x, y, z, y == maxY + 1 ? pal.trim() : y == floorY + 1 || y == maxY ? pal.cap() : pal.pillarAlong(Direction.Axis.Y));
                }
            }
        }
        for (int a = alongX ? minX : minZ; a <= (alongX ? maxX : maxZ); a++) {
            int x = alongX ? a : minX;
            int z = alongX ? minZ : a;
            if (!get(x, maxY + 1, z).isAir()) {
                set(x, maxY + 1, z, pal.trim());
            }
        }
    }

    // 壁の旗: 壁 (背の側) が固いときだけ。facing は旗の面が向く側 (部屋の中)
    void wallBanner(int x, int y, int z, Direction facing, Block banner) {
        BlockPos behind = new BlockPos(x, y, z).relative(facing.getOpposite());
        if (!get(x, y, z).isAir() || !level.getBlockState(behind).isFaceSturdy(level, behind, facing)) {
            return;
        }
        set(x, y, z, banner.defaultBlockState().setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, facing));
    }

    // 絨毯 (固い床の上だけ)
    void carpet(int x, int y, int z, Block carpet) {
        if (get(x, y, z).isAir() && !get(x, y - 1, z).isAir()) {
            set(x, y, z, carpet.defaultBlockState());
        }
    }

    // ベンチの階段 (ゾーンの床の材質に近い階段)
    static BlockState stairsOf(ZonePalette p) {
        return switch (p.name()) {
            case "rib" -> Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState();
            case "bell" -> Blocks.TUFF_BRICK_STAIRS.defaultBlockState();
            case "drill", "gallery" -> Blocks.STONE_BRICK_STAIRS.defaultBlockState();
            case "barracks", "library" -> Blocks.SPRUCE_STAIRS.defaultBlockState();
            case "captain" -> Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState();
            case "song" -> Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState();
            case "heartway" -> Blocks.RED_NETHER_BRICK_STAIRS.defaultBlockState();
            case "fin" -> Blocks.TUFF_BRICK_STAIRS.defaultBlockState();
            default -> Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState();
        };
    }

    // 碑 (看板の代わり)。文のキーは lang の "<key>.1" 〜 "<key>.4"
    void wallSign(BlockPos pos, Direction facing, String key) {
        set(pos, Tablets.wallState(facing).setValue(jp.revati.skyabyss.relic.RelicStyle.PROP, jp.revati.skyabyss.relic.RelicStyle.WHALE));
        Tablets.write(level, pos, key);
    }

    void standingSign(BlockPos pos, String key) {
        set(pos, Tablets.standingState().setValue(jp.revati.skyabyss.relic.RelicStyle.PROP, jp.revati.skyabyss.relic.RelicStyle.WHALE));
        Tablets.write(level, pos, key);
    }
}
