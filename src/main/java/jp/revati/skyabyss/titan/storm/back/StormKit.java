package jp.revati.skyabyss.titan.storm.back;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StairBlock;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

// 雷翼の背を建てる道具箱。風の民の石組み: 方解石 (白)・閃緑岩・滑らかな石、銅の帯、骨の梁 (骨のブロック)。
// 掘った所 (carved) を覚え、あとから建てる壁が前に掘った所をふさがないようにする。最後に skin で、掘った所に面した
// 体の芯の凝灰岩を、その場所の壁の石にする
public final class StormKit {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    public static final BlockState DIORITE = Blocks.POLISHED_DIORITE.defaultBlockState();
    public static final BlockState SMOOTH = Blocks.SMOOTH_STONE.defaultBlockState();
    public static final BlockState ANDESITE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    public static final BlockState BONE = Blocks.BONE_BLOCK.defaultBlockState();
    public static final BlockState COPPER = Blocks.WAXED_CUT_COPPER.defaultBlockState();
    public static final BlockState COPPER_BLOCK = Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
    public static final BlockState GRATE = Blocks.WAXED_COPPER_GRATE.defaultBlockState();
    public static final BlockState TILE = Blocks.DEEPSLATE_TILES.defaultBlockState();
    public static final BlockState SLATE = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    public static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    public static final BlockState LAMP = Blocks.SEA_LANTERN.defaultBlockState();
    public static final BlockState GLASS = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
    public static final BlockState LENS = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
    public static final BlockState CORE = Blocks.TUFF.defaultBlockState();
    public static final BlockState FEATHER = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    public static final BlockState FEATHER_WHITE = Blocks.WHITE_CONCRETE.defaultBlockState();

    // 壁の石の種類 (場所ごと)
    public enum Style {
        HALL, BONE, THUNDER, EYE, KEEL
    }

    private final ServerLevel level;
    private final LongOpenHashSet carved = new LongOpenHashSet();

    public StormKit(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel level() {
        return level;
    }

    public LongOpenHashSet carved() {
        return carved;
    }

    public void set(int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, FLAGS);
    }

    public void set(BlockPos p, BlockState s) {
        level.setBlock(p, s, FLAGS);
    }

    public BlockState get(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z));
    }

    public boolean isCarved(int x, int y, int z) {
        return carved.contains(BlockPos.asLong(x, y, z));
    }

    // 空ける (掘った所として覚える)
    public void air(int x, int y, int z) {
        carved.add(BlockPos.asLong(x, y, z));
        set(x, y, z, AIR);
    }

    // 掘った所でなければ置く (前に掘った所をふさがない)
    public void solid(int x, int y, int z, BlockState s) {
        if (!isCarved(x, y, z)) {
            set(x, y, z, s);
        }
    }

    // 置いて、掘った所の印も消す (床・段・梁など、掘った所の中にわざと置くもの)
    public void put(int x, int y, int z, BlockState s) {
        carved.remove(BlockPos.asLong(x, y, z));
        set(x, y, z, s);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    put(x, y, z, s);
                }
            }
        }
    }

    public void carve(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    air(x, y, z);
                }
            }
        }
    }

    // 場所の壁の石。位置のハッシュで混ぜる (乱数なし)
    public static BlockState wall(Style style, int x, int y, int z) {
        int h = Math.floorMod(x * 73856093 ^ y * 19349663 ^ z * 83492791, 97);
        return switch (style) {
            case HALL -> y % 6 == 0 ? COPPER : h < 40 ? CALCITE : h < 75 ? DIORITE : SMOOTH;
            case BONE -> h < 45 ? CALCITE : h < 80 ? BONE : DIORITE;
            case THUNDER -> h < 6 ? COPPER : h < 55 ? TILE : h < 85 ? SLATE : ANDESITE;
            case EYE -> h < 50 ? CALCITE : h < 80 ? QUARTZ : DIORITE;
            case KEEL -> h < 40 ? CALCITE : h < 70 ? SMOOTH : h < 90 ? ANDESITE : BONE;
        };
    }

    public static BlockState floor(Style style, int x, int z) {
        int h = Math.floorMod(x * 31 + z * 17 + (x ^ z) * 7, 11);
        return switch (style) {
            case HALL -> h < 6 ? SMOOTH : h < 9 ? ANDESITE : CALCITE;
            case BONE -> h < 7 ? BONE : CALCITE;
            case THUNDER -> h < 6 ? SLATE : TILE;
            case EYE -> h < 7 ? QUARTZ : CALCITE;
            case KEEL -> h < 6 ? ANDESITE : SMOOTH;
        };
    }

    // 箱の部屋: 内側を空け、外周 1 マスを壁・床・天井にする (前に掘った所はふさがない)。
    // lampEvery > 0 なら、長い方の壁に lampEvery ごとに灯り (床から 3 の高さ)
    public void room(StormBackLayout.Area a, Style style, int lampEvery) {
        room(a.x1(), a.x2(), a.z1(), a.z2(), a.floor(), a.height(), style, lampEvery);
    }

    public void room(int x1, int x2, int z1, int z2, int floor, int height, Style style, int lampEvery) {
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                for (int y = floor; y <= floor + height + 1; y++) {
                    boolean in = x >= x1 && x <= x2 && z >= z1 && z <= z2 && y > floor && y <= floor + height;
                    if (in) {
                        air(x, y, z);
                    } else if (y == floor && x >= x1 && x <= x2 && z >= z1 && z <= z2) {
                        put(x, y, z, floor(style, x, z));
                    } else {
                        solid(x, y, z, wall(style, x, y, z));
                    }
                }
            }
        }
        if (lampEvery > 0) {
            int y = floor + 3;
            if (y <= floor + height) {
                for (int x = x1 + 2; x <= x2 - 2; x += lampEvery) {
                    solid(x, y, z1 - 1, LAMP);
                    solid(x, y, z2 + 1, LAMP);
                }
                for (int z = z1 + 2; z <= z2 - 2; z += lampEvery) {
                    solid(x1 - 1, y, z, LAMP);
                    solid(x2 + 1, y, z, LAMP);
                }
            }
        }
    }

    // 丸い洞 (中心 c、半径 r、床 floor、天井は丸く ceiling まで)。外周に壁
    public void cave(BlockPos c, int r, int floor, int ceiling, Style style, int lamps) {
        int span = ceiling - floor;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                // 丸天井の高さ (端で低く、真ん中で ceiling)
                double t = Math.min(1, d / (r + 0.5));
                int top = floor + 3 + (int) Math.round((span - 3) * Math.sqrt(Math.max(0, 1 - t * t)));
                if (d <= r + 0.5) {
                    put(x, floor, z, floor(style, x, z));
                    for (int y = floor + 1; y <= top; y++) {
                        air(x, y, z);
                    }
                    solid(x, top + 1, z, wall(style, x, top + 1, z));
                } else if (d <= r + 1.6) {
                    for (int y = floor; y <= floor + span + 1; y++) {
                        solid(x, y, z, wall(style, x, y, z));
                    }
                }
            }
        }
        // 壁の灯り
        for (int i = 0; i < lamps; i++) {
            double a = Math.PI * 2 * i / lamps + 0.3;
            int x = c.getX() + (int) Math.round(Math.cos(a) * (r + 1));
            int z = c.getZ() + (int) Math.round(Math.sin(a) * (r + 1));
            solid(x, floor + 3, z, LAMP);
        }
    }

    public void stair(int x, int y, int z, Direction facing, BlockState base) {
        put(x, y, z, base.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
    }

    // 休み場の焚き火 (青い焚き火。しゃがんで触れると、試練をあきらめて尾の間へ戻る)
    public void campfire(BlockPos p) {
        put(p.getX(), p.getY() - 1, p.getZ(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        put(p.getX(), p.getY(), p.getZ(), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        air(p.getX(), p.getY() + 1, p.getZ());
    }

    // 碑 (看板の代わり)。文のキーは lang の "<key>.1"〜"<key>.4"
    public void wallSign(BlockPos pos, Direction facing, String key) {
        put(pos.getX(), pos.getY(), pos.getZ(), Tablets.wallState(facing));
        Tablets.write(level, pos, key);
    }

    // 立てた碑
    public void standingTablet(BlockPos pos, String key) {
        put(pos.getX(), pos.getY(), pos.getZ(), Tablets.standingState());
        Tablets.write(level, pos, key);
    }

    // 掘った所に面した、体の芯の凝灰岩を壁の石にする (どの場所の石かは styleAt で決める)
    public int skin(java.util.function.Function<BlockPos, Style> styleAt) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        long[] all = carved.toLongArray();
        for (long l : all) {
            int x = BlockPos.getX(l);
            int y = BlockPos.getY(l);
            int z = BlockPos.getZ(l);
            for (Direction d : Direction.values()) {
                m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                if (level.getBlockState(m).is(Blocks.TUFF)) {
                    level.setBlock(m, wall(styleAt.apply(m), m.getX(), m.getY(), m.getZ()), FLAGS);
                    n++;
                }
            }
        }
        return n;
    }

    // 掘った所の横と上が、掘っていない空気 (鳥の外の空など) なら壁でふさぐ (天井の縁のすき間・部屋が体の外へはみ出した所)。
    // 外へ開いていてよい所 (到着の台・羽根の門・背の上へ出る段・天窓) は open で除く。ふさいだ数を返す
    public int seal(java.util.function.Predicate<BlockPos> open, java.util.function.Function<BlockPos, Style> styleAt) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long l : carved.toLongArray()) {
            int x = BlockPos.getX(l);
            int y = BlockPos.getY(l);
            int z = BlockPos.getZ(l);
            if (open.test(new BlockPos(x, y, z))) {
                continue;
            }
            for (Direction d : Direction.values()) {
                if (d == Direction.DOWN) {
                    continue;
                }
                m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                if (carved.contains(m.asLong()) || open.test(m) || !level.getBlockState(m).isAir()) {
                    continue;
                }
                level.setBlock(m, wall(styleAt.apply(m), m.getX(), m.getY(), m.getZ()), FLAGS);
                n++;
            }
        }
        return n;
    }

    // 角の隙間をふさぐ: 掘った所 c の隣 (d1 と d2 の方向) がどちらも丸ごとの壁なのに、その斜め (c + d1 + d2) だけ空気、という角。
    // seal は掘った所の面した所しか見ないので、屋根の縁・壁の縁の斜めが抜けて、屋根が浮いて見えたり光が漏れたりする。ふさいだ数を返す
    public int sealCorners(java.util.function.Predicate<BlockPos> open, java.util.function.Function<BlockPos, Style> styleAt) {
        int n = 0;
        BlockPos.MutableBlockPos a = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos b = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos x = new BlockPos.MutableBlockPos();
        Direction[] all = Direction.values();
        for (long l : carved.toLongArray()) {
            int cx = BlockPos.getX(l);
            int cy = BlockPos.getY(l);
            int cz = BlockPos.getZ(l);
            for (Direction d1 : all) {
                for (Direction d2 : all) {
                    if (d1.getAxis() == d2.getAxis() || d1.ordinal() > d2.ordinal()) {
                        continue;
                    }
                    a.set(cx + d1.getStepX(), cy + d1.getStepY(), cz + d1.getStepZ());
                    b.set(cx + d2.getStepX(), cy + d2.getStepY(), cz + d2.getStepZ());
                    x.set(cx + d1.getStepX() + d2.getStepX(), cy + d1.getStepY() + d2.getStepY(), cz + d1.getStepZ() + d2.getStepZ());
                    if (carved.contains(a.asLong()) || carved.contains(b.asLong()) || carved.contains(x.asLong()) || open.test(x) || !level.getBlockState(x).isAir()) {
                        continue;
                    }
                    if (!level.getBlockState(a).isCollisionShapeFullBlock(level, a) || !level.getBlockState(b).isCollisionShapeFullBlock(level, b)) {
                        continue;
                    }
                    level.setBlock(x, wall(styleAt.apply(x), x.getX(), x.getY(), x.getZ()), FLAGS);
                    n++;
                }
            }
        }
        return n;
    }
}
