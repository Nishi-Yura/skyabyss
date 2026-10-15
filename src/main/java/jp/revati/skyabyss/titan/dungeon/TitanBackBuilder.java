package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ResonanceForgeBlock;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// 岩鯨の背を建てる。ワールドで初めて必要になったとき (サーバー起動時) に一度だけ実行する。
// 配置は TitanBackLayout を参照。ここでは島そのもの・地表の遺跡・祠・鼓動の間・宝物庫を建て、
// 西の翼・東の翼・闇の水路はそれぞれの Builder に任せる
public final class TitanBackBuilder {
    private TitanBackBuilder() {
    }

    public static void build(ServerLevel level, TitanBackData data) {
        long start = System.currentTimeMillis();
        BuildKit kit = new BuildKit(level, RandomSource.create(20261015L));
        // アスレチックと迷路の形は、建てるたびに新しい種で決める
        data.reseed(level.getRandom());
        buildIsland(kit);
        buildSurface(kit);
        buildHub(kit);
        buildTreasury(kit);
        // 鼓動の間の柱は、戸口・はしご・封印・碑文をすべて置いたあとに立てる (それらの前を避けるため)
        kit.pilasters(HUB, 4);
        kit.wallLamps(HUB, 4, FLOOR + 4);
        kit.wallLamps(ENTRY, 3, FLOOR + 3);
        WestWingBuilder.build(kit);
        EastWingBuilder.build(kit);
        MazeBuilder.buildFixtures(kit);
        MazeBuilder.buildMaze(new BuildKit(level, RandomSource.create(data.mazeSeed())), data.mazeSeed());
        // 体内を掘ってから階段を通す (先に階段を作ると、通路の壁でふさがれる)
        buildShrineAndStairs(kit);
        for (BlockPos p : RESONANCE_STONES) {
            kit.set(p, ModBlocks.RESONANCE_STONE.get().defaultBlockState());
        }
        data.markBuilt();
        SkyAbyss.LOGGER.info("Built the stone whale's back in {} ms", System.currentTimeMillis() - start);
    }

    // ---------------------------------------------------------------- 島
    private static void buildIsland(BuildKit kit) {
        RandomSource random = kit.random;
        for (int x = -RX - 2; x <= RX + 2; x++) {
            for (int z = -RZ - 2; z <= RZ + 2; z++) {
                double s = shape(x, z);
                int t = top(x, z);
                // 建て直したときに古い建物が宙に残らないよう、地表の上をきれいにする
                for (int y = (s > 0 ? t + 1 : BASE_Y - 40); y <= t + 18; y++) {
                    if (!kit.get(x, y, z).isAir()) {
                        kit.set(x, y, z, BuildKit.AIR);
                    }
                }
                if (s <= 0) {
                    continue;
                }
                int b = bottom(x, z);
                boolean rim = s < 0.07;
                for (int y = b; y <= t; y++) {
                    BlockState state;
                    int depth = t - y;
                    if (depth == 0) {
                        float r = random.nextFloat();
                        state = r < 0.55f ? Blocks.MOSS_BLOCK.defaultBlockState()
                                : r < 0.9f ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                    } else if (depth <= 2) {
                        state = random.nextFloat() < 0.7f ? Blocks.DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                    } else if (y == b || rim) {
                        float r = random.nextFloat();
                        state = r < 0.65f ? Blocks.DEEPSLATE.defaultBlockState()
                                : r < 0.9f ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                    } else {
                        state = random.nextFloat() < 0.75f ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
                    }
                    kit.set(x, y, z, state);
                }
                // 腹から垂れる根と、縁から垂れるツタ
                if (random.nextFloat() < 0.1f) {
                    kit.set(x, b - 1, z, Blocks.HANGING_ROOTS.defaultBlockState());
                }
                if (rim && random.nextFloat() < 0.35f) {
                    hangVines(kit, x, z, t);
                }
            }
        }
    }

    private static void hangVines(BuildKit kit, int x, int z, int t) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int ox = x + dir.getStepX();
            int oz = z + dir.getStepZ();
            if (shape(ox, oz) > 0) {
                continue;
            }
            int length = 3 + kit.random.nextInt(9);
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir.getOpposite()), true);
            for (int y = t - 1; y > t - 1 - length && y >= bottom(x, z); y--) {
                kit.set(ox, y, oz, vine);
            }
            return;
        }
    }

    // ---------------------------------------------------------------- 地表: 背骨の道、柱列、門、突き出た肋骨、木々、尾の台
    private static void buildSurface(BuildKit kit) {
        RandomSource random = kit.random;
        for (int z = -104; z <= PLATFORM_Z - 5; z++) {
            for (int x = -1; x <= 1; x++) {
                if (random.nextFloat() < 0.75f) {
                    BlockState path = switch (random.nextInt(3)) {
                        case 0 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                        case 1 -> Blocks.TUFF_BRICKS.defaultBlockState();
                        default -> Blocks.GRAVEL.defaultBlockState();
                    };
                    kit.set(x, top(x, z), z, path);
                }
            }
        }
        // 道の両側の柱列。祠と尾の台のまわりは空ける。いくつかは折れている
        for (int z = -98; z <= 84; z += 14) {
            if (z > SHRINE_Z - 14 && z < SHRINE_Z + 14) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                int height = random.nextFloat() < 0.35f ? 3 + random.nextInt(4) : 9 + random.nextInt(4);
                pillar(kit, 11 * side, z, height);
            }
        }
        gateArch(kit, -70);
        gateArch(kit, 56);
        // 体から突き出た巨大な肋骨の弧
        for (int z : new int[]{-40, -24, 40, 70}) {
            boneArc(kit, z, 26);
        }
        // 苔むした大岩と、アザレアの木
        for (int i = 0; i < 10; i++) {
            int x = (random.nextBoolean() ? 1 : -1) * (16 + random.nextInt(18));
            int z = -95 + random.nextInt(180);
            if (shape(x, z) > 0.15) {
                boulder(kit, x, z);
            }
        }
        var trees = kit.level().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(TreeFeatures.AZALEA_TREE);
        for (int i = 0; i < 14; i++) {
            int x = (random.nextBoolean() ? 1 : -1) * (6 + random.nextInt(28));
            int z = -100 + random.nextInt(190);
            if (shape(x, z) > 0.2 && Math.abs(x) > 4 && !(z > SHRINE_Z - 14 && z < SHRINE_Z + 14)) {
                BlockPos pos = new BlockPos(x, top(x, z) + 1, z);
                trees.ifPresent(t -> t.value().place(kit.level(), kit.level().getChunkSource().getGenerator(), random, pos));
            }
        }
        // 頭の先の見張り台: 二本の角のような柱
        int hz = -108;
        int hy = top(0, hz);
        kit.fill(-3, hy, hz - 2, 3, hy, hz + 2, BuildKit.POLISHED);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 1; i <= 7; i++) {
                kit.set(3 * side + (i > 4 ? side : 0), hy + i, hz - (i > 5 ? 1 : 0), BuildKit.TILES);
            }
        }
        buildPlatform(kit);
    }

    private static void pillar(BuildKit kit, int x, int z, int height) {
        int base = top(x, z) + 1;
        kit.set(x, base, z, BuildKit.CHISELED);
        for (int i = 1; i < height; i++) {
            kit.set(x, base + i, z, i % 3 == 0 ? BuildKit.POLISHED : BuildKit.TILES);
        }
        if (height >= 8) {
            kit.set(x, base + height, z, BuildKit.CHISELED);
        } else {
            kit.set(x, base + height, z, Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
            for (int i = 0; i < 3; i++) {
                int fx = x + kit.random.nextInt(5) - 2;
                int fz = z + kit.random.nextInt(5) - 2;
                kit.set(fx, top(fx, fz) + 1, fz, Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
            }
        }
    }

    // 道をまたぐ石の門 (2本の柱と、段のついた梁)
    private static void gateArch(BuildKit kit, int z) {
        int base = Math.max(top(-5, z), top(5, z)) + 1;
        for (int side = -1; side <= 1; side += 2) {
            for (int y = base - 3; y < base + 10; y++) {
                kit.set(5 * side, y, z, y == base + 9 ? BuildKit.CHISELED : BuildKit.POLISHED);
            }
        }
        for (int x = -6; x <= 6; x++) {
            kit.set(x, base + 10, z, BuildKit.TILES);
        }
        for (int x = -3; x <= 3; x++) {
            kit.set(x, base + 11, z, Math.abs(x) == 3 ? Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST) : BuildKit.CHISELED);
        }
    }

    // 地表を割って突き出た、鯨の肋骨の弧
    private static void boneArc(BuildKit kit, int z, int radius) {
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        int cy = top(0, z) - radius / 2;
        for (int a = 0; a <= 180; a += 2) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(rad) * radius);
            int y = cy + (int) Math.round(Math.sin(rad) * radius);
            if (y > top(x, z)) {
                kit.set(x, y, z, bone);
                kit.set(x, y, z + 1, bone);
            }
        }
    }

    private static void boulder(BuildKit kit, int cx, int cz) {
        int base = top(cx, cz);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {
                    if (x * x + z * z + y * y * 2 <= 5 + kit.random.nextInt(2)) {
                        kit.set(cx + x, base + 1 + y, cz + z, kit.random.nextFloat() < 0.6f
                                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                    }
                }
            }
        }
    }

    // 尾の台: 到着地点。錨石と、そこから縁ごしに垂れる鎖
    private static void buildPlatform(BuildKit kit) {
        int py = platformY();
        for (int x = -6; x <= 6; x++) {
            for (int z = PLATFORM_Z - 8; z <= PLATFORM_Z + 8; z++) {
                if ((x * x) / 36.0 + ((z - PLATFORM_Z) * (z - PLATFORM_Z)) / 64.0 > 1.0) {
                    continue;
                }
                kit.fill(x, py - 3, z, x, py - 1, z, Blocks.TUFF.defaultBlockState());
                boolean ring = (x * x) / 36.0 + ((z - PLATFORM_Z) * (z - PLATFORM_Z)) / 64.0 > 0.6;
                kit.set(x, py, z, ring ? Blocks.TUFF_BRICKS.defaultBlockState() : Blocks.POLISHED_TUFF.defaultBlockState());
                kit.fill(x, py + 1, z, x, py + 5, z, BuildKit.AIR);
            }
        }
        kit.set(anchor(), Blocks.LODESTONE.defaultBlockState());
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 1; i <= 3; i++) {
                kit.set(4 * side, py + i, PLATFORM_Z + 3, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
            }
            kit.set(4 * side, py + 4, PLATFORM_Z + 3, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // 錨石から台の縁へ這う鎖
        for (int z = PLATFORM_Z + 4; z <= PLATFORM_Z + 8; z++) {
            kit.set(0, py + 1, z, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        kit.standingSign(new BlockPos(-2, py + 1, PLATFORM_Z + 3), "sign.skyabyss.anchor");
        kit.set(forge(), ModBlocks.RESONANCE_FORGE.get().defaultBlockState().setValue(ResonanceForgeBlock.FACING, Direction.EAST));
        kit.standingSign(forge().south(), "sign.skyabyss.forge");
    }

    // ---------------------------------------------------------------- 祠と体内への階段
    private static void buildShrineAndStairs(BuildKit kit) {
        int sy = shrineY();
        int z1 = SHRINE_Z - 10;
        int z2 = SHRINE_Z + 10;
        for (int x = -5; x <= 5; x++) {
            for (int z = z1; z <= z2; z++) {
                kit.fill(x, sy - 3, z, x, sy - 1, z, Blocks.TUFF.defaultBlockState());
                boolean border = Math.abs(x) == 5 || z == z1 || z == z2;
                kit.set(x, sy, z, border ? BuildKit.TILES : BuildKit.POLISHED);
                kit.fill(x, sy + 1, z, x, sy + 8, z, BuildKit.AIR);
            }
        }
        // 四隅と中ほどの柱、二段の屋根
        for (int x : new int[]{-5, 5}) {
            for (int z : new int[]{z1, SHRINE_Z, z2}) {
                for (int y = sy + 1; y <= sy + 5; y++) {
                    kit.set(x, y, z, y == sy + 5 ? BuildKit.CHISELED : BuildKit.POLISHED);
                }
            }
        }
        for (int x = -6; x <= 6; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                if ((x * 7 + z * 13) % 6 != 0) {
                    kit.set(x, sy + 6, z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
                }
                if (Math.abs(x) <= 3 && z >= z1 + 3 && z <= z2 - 3) {
                    kit.set(x, sy + 7, z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
                }
            }
        }
        kit.hangingLantern(0, SHRINE_Z + 6, sy + 6, 1);
        // 北へ下りていく階段。1段ごとに1つ低くなり、体内の床の高さで終わる
        BlockState step = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int i = 0; ; i++) {
            int z = SHRINE_Z - 1 - i;
            int y = sy - 1 - i;
            if (y < FLOOR) {
                break;
            }
            for (int x = -2; x <= 2; x++) {
                boolean inside = Math.abs(x) <= 1;
                for (int yy = y; yy <= y + 4; yy++) {
                    if (inside) {
                        kit.set(x, yy, z, yy == y ? (y == FLOOR ? BuildKit.POLISHED : step) : BuildKit.AIR);
                    } else if (yy < sy && !kit.get(x, yy, z).isAir()) {
                        kit.set(x, yy, z, i % 4 == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : BuildKit.BRICKS);
                    }
                }
            }
            if (i % 6 == 3 && y + 5 < sy) {
                kit.lantern(2, y + 2, z);
            }
        }
        kit.standingSign(new BlockPos(4, sy + 1, SHRINE_Z + 4), "sign.skyabyss.shrine");
    }

    // ---------------------------------------------------------------- 鼓動の間
    private static void buildHub(BuildKit kit) {
        kit.room(HUB);
        kit.room(ENTRY);
        // 西・東の翼へ向かう通路
        kit.room(new Box(-15, -10, -23, -21, FLOOR, 4));
        kit.room(new Box(10, 15, -23, -21, FLOOR, 4));
        kit.ribs(HUB, 4, true);
        kit.ribs(ENTRY, 3, true);
        kit.floorBorder(HUB);
        // 中央: 眠っている心臓の台座と、それを囲む模様
        kit.set(0, FLOOR + 1, -22, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
        kit.set(0, FLOOR + 2, -22, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            kit.set(d.getStepX() * 2, FLOOR + 1, -22 + d.getStepZ() * 2,
                    Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -25; z <= -19; z++) {
                int r = Math.max(Math.abs(x), Math.abs(z + 22));
                if (r == 3 || (r == 1 && (x == 0 || z == -22))) {
                    kit.set(x, FLOOR, z, BuildKit.CHISELED);
                }
            }
        }
        for (int[] l : new int[][]{{-5, -16}, {5, -16}, {-5, -28}, {5, -28}, {0, -13}, {-5, -22}, {5, -22}, {0, -30}}) {
            kit.hangingLantern(l[0], l[1], HUB.ceilingY(), 2);
        }
        kit.lantern(-1, FLOOR + 1, -3);
        kit.lantern(1, FLOOR + 1, 2);
        kit.moss(HUB, 0.04f);
        for (int[] s : new int[][]{{-7, -14}, {7, -30}, {-6, -30}, {7, -15}}) {
            kit.stalactite(s[0], HUB.ceilingY(), s[1], 2);
        }
        // 碑文 (封印の扉は宝物庫の壁と一緒に置く)。各試練の入口にも碑文を置く
        kit.wallSign(new BlockPos(3, FLOOR + 3, -32), Direction.SOUTH, "sign.skyabyss.gate");
        kit.wallSign(new BlockPos(-9, FLOOR + 3, -25), Direction.EAST, "sign.skyabyss.chasm");
        kit.wallSign(new BlockPos(9, FLOOR + 3, -25), Direction.WEST, "sign.skyabyss.arena");
        kit.wallSign(new BlockPos(5, FLOOR + 3, -32), Direction.SOUTH, "sign.skyabyss.maze");
        // 西・東の石を鳴らすと開く抜け道 (鼓動の間の側)
        for (BlockPos p : WEST_RETURN) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        BlockState ladderEast = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = FLOOR + 1; y <= 98; y++) {
            kit.set(-9, y, -31, ladderEast);
        }
        for (BlockPos p : EAST_RETURN) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
    }

    // ---------------------------------------------------------------- 宝物庫
    private static void buildTreasury(BuildKit kit) {
        BlockState warded = ModBlocks.WARDED_STONE.get().defaultBlockState();
        kit.wardedRoom(TREASURY, warded);
        for (int x = TREASURY.x1(); x <= TREASURY.x2(); x++) {
            for (int z = TREASURY.z1(); z <= TREASURY.z2(); z++) {
                if ((x + z) % 2 == 0) {
                    kit.set(x, FLOOR, z, BuildKit.CHISELED);
                }
            }
        }
        // 宝物庫の封印 (鼓動の間との境の壁) と、奥の扉
        for (BlockPos p : GATE) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        for (BlockPos p : INNER_GATE) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        // 天の鍵の欠片の台座: 磁石石を、紫水晶とろうそくが囲む
        kit.set(PEDESTAL.below(), Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
        kit.set(PEDESTAL, Blocks.LODESTONE.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos c = PEDESTAL.relative(d);
            kit.set(c, d.getAxis() == Direction.Axis.X ? Blocks.AMETHYST_CLUSTER.defaultBlockState()
                    : Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        }
        kit.set(CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        kit.set(CHEST_2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        fillChests(kit.level(), kit.random);
        kit.wallSign(new BlockPos(3, FLOOR + 3, -44), Direction.SOUTH, "sign.skyabyss.inner_gate");
        kit.wallSign(new BlockPos(3, FLOOR + 3, -32), Direction.SOUTH, "sign.skyabyss.gate");
        kit.wallSign(new BlockPos(5, FLOOR + 3, -32), Direction.SOUTH, "sign.skyabyss.maze");
        kit.wallSign(new BlockPos(-2, FLOOR + 3, -44), Direction.SOUTH, "sign.skyabyss.pedestal");
        for (int[] l : new int[][]{{-4, -36}, {4, -36}, {-4, -42}, {4, -42}}) {
            kit.hangingLantern(l[0], l[1], TREASURY.ceilingY(), 1);
        }
    }

    // 試練を越えるたびに満たされる宝箱 (天の鍵の欠片は台座から一人一つ)。
    // このModの装備 (鯨骨の大剣・岩鯨の甲殻・共鳴の護符) が、毎回いくつか入る
    public static void fillChests(ServerLevel level, RandomSource random) {
        List<Item> armor = List.of(ModItems.STONE_WHALE_HELMET.get(), ModItems.STONE_WHALE_CHESTPLATE.get(),
                ModItems.STONE_WHALE_LEGGINGS.get(), ModItems.STONE_WHALE_BOOTS.get());
        if (level.getBlockEntity(CHEST) instanceof ChestBlockEntity a) {
            a.clearContent();
            a.setItem(13, random.nextFloat() < 0.45f ? new ItemStack(ModItems.WHALEBONE_GREATSWORD.get())
                    : new ItemStack(armor.get(random.nextInt(armor.size()))));
            a.setItem(11, new ItemStack(armor.get(random.nextInt(armor.size()))));
            a.setItem(15, new ItemStack(ModItems.CLOUD_JERKY.get(), 4 + random.nextInt(5)));
            a.setItem(4, new ItemStack(Items.DIAMOND, 2 + random.nextInt(3)));
            a.setItem(22, new ItemStack(Items.GOLDEN_APPLE, 2 + random.nextInt(2)));
            a.setItem(20, new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 4 + random.nextInt(5)));
        }
        if (level.getBlockEntity(CHEST_2) instanceof ChestBlockEntity b) {
            b.clearContent();
            b.setItem(13, random.nextFloat() < 0.6f ? new ItemStack(ModItems.RESONANCE_CHARM.get())
                    : new ItemStack(armor.get(random.nextInt(armor.size()))));
            b.setItem(11, new ItemStack(armor.get(random.nextInt(armor.size()))));
            b.setItem(15, new ItemStack(Items.ECHO_SHARD, 1 + random.nextInt(2)));
            b.setItem(4, new ItemStack(Items.EMERALD, 6 + random.nextInt(8)));
            b.setItem(22, new ItemStack(Items.EXPERIENCE_BOTTLE, 8 + random.nextInt(8)));
            b.setItem(24, new ItemStack(ModItems.RESONANCE_CRYSTAL.get()));
            b.setItem(20, new ItemStack(ModItems.BONE_SPIKE_ARROW.get(), 8 + random.nextInt(9)));
        }
    }

    static BlockState sealed() {
        return ModBlocks.SEALED_GATE.get().defaultBlockState();
    }

    static ItemStack fragment() {
        return new ItemStack(ModItems.SKY_KEY_FRAGMENT.get());
    }
}
