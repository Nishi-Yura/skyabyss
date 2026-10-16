package jp.revati.skyabyss.sky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

// 「削られた山頂」の計画。影の通過経路の下にある高い自然の峰を探し、巨大なものが擦ったように
// 頂を平らに削り、進む向きに引きずった溝と、散った岩を残す計画を作る。
//
// プレイヤーの建築は絶対に壊さない。そのため、疑わしいときは削らない (安全側):
//  1. 消すのは自然に生成される地形のブロック (石・花崗岩・閃緑岩・安山岩・凝灰岩・深層岩・方解石・土・草・雪・砂利・氷塊・鉱石と草花) だけ
//  2. 削る所の周り (横に16、上下に12) に、自然のブロック以外が一つでもあれば削らない
//     (松明・作業台・ベッド・板材・丸石・ガラス・柵・扉など、人の手を思わせるものはすべて「自然でない」に入る)。
//     原木だけは、自然の葉 (プレイヤーには置けない) が茂る木の幹なら周りにあってよい (削る所にあれば削らない)
//  3. その範囲に、ブロックエンティティ (チェスト・看板・かまどなど)、防具立て・額縁・乗り物、飼いならした・名前の付いた・
//     消えない設定の生き物がいれば削らない
//  4. プレイヤーがブロックを置いた・固いブロックを掘ったことのあるチャンク (ChapterSkyData) と、プレイヤーが長く過ごしたチャンク
//     (InhabitedTime が1時間を超える。Mod を入れる前からあった古いチャンクは5分) にかかるなら削らない
//  4b. 自然のブロックだけでも「人が積んだ」形 (草の上に積んだ土や石、草の下の空洞、細い柱・細い壁、まっすぐな垂直の面、
//     宙に浮いた薄い屋根・橋、まっ平らな床、バイオームの表層に合わないもの) があれば削らない (builtShape)
//  4c. さらに外 (横に40) の地表付近に、自然でないブロックやブロックの光があれば削らない
//  5. ワールドの初期スポーンから160、だれかの復活地点 (ベッドなど。ログアウト中の人の分も覚えている) から128、
//     いまいるプレイヤーから24ブロック以内は削らない。前の削り跡から64以内も削らない
//  6. 村などの構造物にかかるなら削らない。水や溶岩に触れる所も削らない
//  7. 読み込み済みのチャンクだけを見る (読み込みを起こさない)
//  8. 実際に削るときも、一つずつ「計画したときと同じブロックか」を確かめ、違えばそこで止める (PeakScarEvents)
public final class PeakScar {
    // 「高い」とみなす頂の高さ (海面63から約50上)
    static final int MIN_PEAK_Y = 110;
    // 周り (半径14の8点) の平均より、これだけ高いこと (山頂・尾根)
    static final double MIN_PROMINENCE = 6.0;
    static final int PROMINENCE_RING = 14;
    // 安全のために調べる範囲 (削る所の外側)
    static final int MARGIN = 16;
    static final int BELOW = 12;
    static final int ABOVE = 12;
    // これより長くプレイヤーが近くにいたチャンクは、拠点の近くとみなす (1時間 = 72000tick)
    static final long MAX_INHABITED = 72000L;
    // Mod を入れる前からあったチャンク (置いた記録がない) は、5分 (6000tick) で拠点の近くとみなす
    static final long OLD_MAX_INHABITED = 6000L;
    // 光源や人の手を探す、さらに外の範囲 (削る所から横に40)。地表から4段だけ見る
    static final int OUTER = 40;
    static final int OUTER_DEPTH = 4;
    static final double SPAWN_CLEAR = 160.0;
    static final double HOME_CLEAR = 128.0;
    static final double PLAYER_CLEAR = 24.0;
    static final double SCAR_SPACING = 64.0;
    // 一度に変えるブロックの数の上下限 (少なすぎると気づけない、多すぎると削りすぎ)
    static final int MIN_REMOVED = 16;
    static final int MAX_REMOVED = 700;

    // 消してよい自然の地形
    static final Set<Block> REMOVABLE = Set.of(
            Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.DEEPSLATE, Blocks.CALCITE,
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT,
            Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.GRAVEL, Blocks.PACKED_ICE,
            Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.EMERALD_ORE, Blocks.LAPIS_ORE,
            Blocks.REDSTONE_ORE, Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
    // 消してよい草花と雪 (固くないもの)
    static final Set<Block> VEGETATION = Set.of(
            Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH, Blocks.SNOW);
    // 周りにあってよい (消さない) 自然のもの
    static final Set<Block> PASSABLE = Set.of(
            Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR, Blocks.WATER, Blocks.LAVA, Blocks.ICE, Blocks.BLUE_ICE,
            Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.SWEET_BERRY_BUSH, Blocks.MOSS_CARPET);
    // 切り口を白い岩 (方解石) に変える石
    static final Set<Block> WHITENS = Set.of(Blocks.STONE, Blocks.GRANITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.DEEPSLATE, Blocks.DIORITE);
    // 散った岩
    private static final BlockState[] DEBRIS = {
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};

    public record Candidate(int x, int y, int z) {
    }

    public record Change(BlockPos pos, BlockState expect, BlockState to) {
    }

    public record Plan(BlockPos center, float dirX, float dirZ, List<Change> changes, int removed, AABB area) {
    }

    // plan が null なら reason が理由
    public record Result(@Nullable Plan plan, String reason) {
        static Result no(String reason) {
            return new Result(null, reason);
        }
    }

    private PeakScar() {
    }

    static boolean removable(BlockState s) {
        return REMOVABLE.contains(s.getBlock()) || vegetation(s);
    }

    static boolean vegetation(BlockState s) {
        return VEGETATION.contains(s.getBlock()) || s.is(BlockTags.SMALL_FLOWERS);
    }

    static boolean air(BlockState s) {
        return s.isAir();
    }

    // 自然に生成されたとみなすブロック。ここに入らないものはすべて「人の手かもしれない」
    static boolean natural(BlockState s) {
        Block b = s.getBlock();
        if (REMOVABLE.contains(b) || PASSABLE.contains(b) || vegetation(s) || s.isAir()) {
            return true;
        }
        // 葉は「自然に生えた」(persistent でない) ものだけ。プレイヤーが置いた葉は persistent になる
        return b instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT);
    }

    // 皮をはいでいない原木 (はいだ原木は斧で作るので、人の手)
    static boolean wildLog(BlockState s) {
        return s.is(BlockTags.LOGS) && !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("stripped");
    }

    // 原木が自然の木の一部か: 原木をたどって上へ登り (32まで)、いちばん上の原木のまわり (横に2、上に3) に
    // 自然の葉 (persistent でない) が3つ以上あれば自然の木。プレイヤーは persistent でない葉を置けないので、
    // 原木だけの柱や小屋はここで弾かれる。結果は幹のいちばん上ごとに覚える
    static boolean naturalTree(ServerLevel level, BlockPos start, java.util.Map<Long, Boolean> memo) {
        BlockPos.MutableBlockPos p = start.mutable();
        for (int i = 0; i < 32 && wildLog(level.getBlockState(p.above())); i++) {
            p.move(0, 1, 0);
        }
        long key = p.asLong();
        Boolean known = memo.get(key);
        if (known != null) {
            return known;
        }
        int leaves = 0;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2 && leaves < 3; dx++) {
            for (int dz = -2; dz <= 2 && leaves < 3; dz++) {
                for (int dy = -1; dy <= 3 && leaves < 3; dy++) {
                    q.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                    if (!level.isLoaded(q)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(q);
                    if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
                        leaves++;
                    }
                }
            }
        }
        boolean natural = leaves >= 3;
        memo.put(key, natural);
        return natural;
    }

    // 経路 (origin から dir へ length) の左右 lateral の範囲で、高い峰を探す。読み込み済みのチャンクだけを見る。高い順
    public static List<Candidate> findCandidates(ServerLevel level, double originX, double originZ, float dirX, float dirZ,
                                                 double length, double lateral) {
        List<Candidate> found = new ArrayList<>();
        for (double s = 0; s <= length; s += 8) {
            for (double l = -lateral; l <= lateral; l += 8) {
                int x = Mth.floor(originX + dirX * s - dirZ * l);
                int z = Mth.floor(originZ + dirZ * s + dirX * l);
                int h = top(level, x, z);
                if (h < MIN_PEAK_Y || near(found, x, z, 16)) {
                    continue;
                }
                // 近くの一番高い所まで登る
                for (int step = 0; step < 16; step++) {
                    int bx = x;
                    int bz = z;
                    int bh = h;
                    for (int dx = -2; dx <= 2; dx += 2) {
                        for (int dz = -2; dz <= 2; dz += 2) {
                            int nh = top(level, x + dx, z + dz);
                            if (nh > bh) {
                                bh = nh;
                                bx = x + dx;
                                bz = z + dz;
                            }
                        }
                    }
                    if (bh == h) {
                        break;
                    }
                    x = bx;
                    z = bz;
                    h = bh;
                }
                if (prominence(level, x, z, h) < MIN_PROMINENCE) {
                    continue;
                }
                if (!near(found, x, z, 24)) {
                    found.add(new Candidate(x, h, z));
                }
            }
        }
        found.sort(Comparator.comparingInt(Candidate::y).reversed());
        return found;
    }

    private static boolean near(List<Candidate> found, int x, int z, int distance) {
        for (Candidate c : found) {
            if (Math.abs(c.x() - x) < distance && Math.abs(c.z() - z) < distance) {
                return true;
            }
        }
        return false;
    }

    // 地形の一番上の固いブロックの高さ (葉は数えない)。読み込まれていなければ最低の値
    static int top(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return Integer.MIN_VALUE;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    // 周りの8点の平均よりどれだけ高いか (読み込まれていない点があれば 0)。
    // 8点のうち6点以上が3ブロック以上低いこと (山頂と尾根は通り、台地の縁や崖の上は通らない)
    static double prominence(ServerLevel level, int x, int z, int h) {
        double sum = 0;
        int lower = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int t = top(level, x + Mth.floor(Math.cos(a) * PROMINENCE_RING), z + Mth.floor(Math.sin(a) * PROMINENCE_RING));
            if (t == Integer.MIN_VALUE) {
                return 0;
            }
            sum += t;
            if (t <= h - 3) {
                lower++;
            }
        }
        return lower >= 6 ? h - sum / 8.0 : 0;
    }

    // 削り方の大きさ。strength (0〜1) は月の近さで、近いほど深く長い
    record Shape(double halfLength, double halfWidth, int depth, int grooves, double drag, int debris) {
        static Shape of(float strength) {
            float s = Mth.clamp(strength, 0f, 1f);
            return new Shape(7.0 + 4.0 * s, 3.0 + 1.2 * s, 2 + Math.round(2 * s), s < 0.5f ? 2 : 3, 9.0 + 7.0 * s, 6 + Math.round(6 * s));
        }

        Shape shallower() {
            return new Shape(halfLength, halfWidth, depth - 1, grooves, drag, debris);
        }
    }

    // 候補の頂を削る計画を作る。安全の確かめに一つでも引っかかれば削らない (理由を返す)
    public static Result plan(ServerLevel level, Candidate c, float dirX, float dirZ, float strength, RandomSource random,
                              double playerClear) {
        if (level.dimension() != Level.OVERWORLD) {
            return Result.no("not overworld");
        }
        double len = Math.hypot(dirX, dirZ);
        if (len < 1.0e-3) {
            return Result.no("no direction");
        }
        float dx = (float) (dirX / len);
        float dz = (float) (dirZ / len);
        String far = clearOfPeople(level, c.x(), c.z(), playerClear);
        if (far != null) {
            return Result.no(far);
        }
        Shape shape = Shape.of(strength);
        while (true) {
            Result r = build(level, c, dx, dz, shape, random);
            if (r.plan() == null && r.reason().equals("too large") && shape.depth() > 1) {
                shape = shape.shallower();
                continue;
            }
            return r;
        }
    }

    // 人のいる・いた場所から十分離れているか (離れていなければ理由)
    @Nullable
    static String clearOfPeople(ServerLevel level, int x, int z, double playerClear) {
        BlockPos spawn = level.getSharedSpawnPos();
        if (horizontal(spawn.getX(), spawn.getZ(), x, z) < SPAWN_CLEAR) {
            return "near world spawn";
        }
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && horizontal(p.getX(), p.getZ(), x, z) < playerClear) {
                return "player nearby";
            }
            BlockPos home = p.getRespawnPosition();
            if (home != null && p.getRespawnDimension() == Level.OVERWORLD && horizontal(home.getX(), home.getZ(), x, z) < HOME_CLEAR) {
                return "near a home";
            }
        }
        ChapterSkyData data = ChapterSkyData.get(level);
        for (ChapterSkyData.Entry e : data.entries().values()) {
            if (e.home != null && horizontal(e.home.getX(), e.home.getZ(), x, z) < HOME_CLEAR) {
                return "near a home";
            }
        }
        for (ChapterSkyData.Scar s : data.scars()) {
            if (horizontal(s.pos().getX(), s.pos().getZ(), x, z) < SCAR_SPACING) {
                return "near another scar";
            }
        }
        return null;
    }

    private static double horizontal(double ax, double az, double bx, double bz) {
        return Math.hypot(ax - bx, az - bz);
    }

    private static Result build(ServerLevel level, Candidate c, float dx, float dz, Shape shape, RandomSource random) {
        double reach = shape.halfLength() + shape.drag() + 8.0;
        int minX = Mth.floor(c.x() - reach) - MARGIN;
        int maxX = Mth.floor(c.x() + reach) + MARGIN;
        int minZ = Mth.floor(c.z() - reach) - MARGIN;
        int maxZ = Mth.floor(c.z() + reach) + MARGIN;
        int baseCut = c.y() - shape.depth();
        int minY = Math.max(level.getMinBuildHeight(), baseCut - 3 - BELOW);

        int outMinX = minX - (OUTER - MARGIN);
        int outMaxX = maxX + (OUTER - MARGIN);
        int outMinZ = minZ - (OUTER - MARGIN);
        int outMaxZ = maxZ + (OUTER - MARGIN);

        // チャンク (外の範囲まで): 読み込み済み・プレイヤーが置いて (掘って) いない・長く過ごしていない。
        // Mod を入れる前からあったチャンクは置いた記録がないので、過ごした時間の上限をずっと短くする
        ChapterSkyData data = ChapterSkyData.get(level);
        for (int cx = outMinX >> 4; cx <= outMaxX >> 4; cx++) {
            for (int cz = outMinZ >> 4; cz <= outMaxZ >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return Result.no("chunk not loaded");
                }
                if (data.wasPlaced(ChunkPos.asLong(cx, cz))) {
                    return Result.no("player placed blocks here");
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                long limit = ChapterSky.freshChunk(chunk) ? MAX_INHABITED : OLD_MAX_INHABITED;
                if (chunk.getInhabitedTime() > limit) {
                    return Result.no("players spent long here");
                }
            }
        }

        // 構造物 (村・遺跡など)
        for (int x = minX; x <= maxX; x += 8) {
            for (int z = minZ; z <= maxZ; z += 8) {
                if (level.structureManager().getStructureWithPieceAt(new BlockPos(x, c.y(), z), s -> true).isValid()
                        || level.structureManager().getStructureWithPieceAt(new BlockPos(x, baseCut - BELOW, z), s -> true).isValid()) {
                    return Result.no("structure");
                }
            }
        }

        // 周りのブロックがすべて自然のものか (地表より上は空気なので、各列の一番上の何かまで)
        // 原木だけは、自然に生えた木 (幹の上に、自然の葉が茂っている) なら周りにあってよい
        int maxY = minY;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Map<Long, Boolean> trees = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                maxY = Math.max(maxY, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
            }
        }
        // 調べたブロックを覚えておき (Grid)、あとで「人が積んだ形」を探す
        Grid grid = new Grid(minX, minY, minZ, maxX - minX + 1, maxY - minY + 2, maxZ - minZ + 1);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = minY; y <= surface; y++) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (!natural(s) && !(wildLog(s) && naturalTree(level, m, trees))) {
                        return Result.no("non-natural " + name(s) + " at " + x + " " + y + " " + z);
                    }
                    grid.set(x, y, z, code(s));
                }
            }
        }
        if (gridProbe != null) {
            gridProbe.accept(grid);
        }
        String built = builtShape(level, grid);
        if (built != null) {
            return Result.no(built);
        }
        // さらに外: 峰の高さにある地表 (4段) に自然でないブロック、または地表のすぐ上にブロックの光があれば削らない
        String outer = nearbySigns(level, minX, maxX, minZ, maxZ, outMinX, outMaxX, outMinZ, outMaxZ, minY, trees);
        if (outer != null) {
            return Result.no(outer);
        }
        maxY = Math.min(level.getMaxBuildHeight() - 1, maxY + ABOVE);
        AABB area = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);

        // ブロックエンティティ
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                for (BlockPos p : level.getChunk(cx, cz).getBlockEntities().keySet()) {
                    if (area.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) {
                        return Result.no("block entity");
                    }
                }
            }
        }

        // 人の持ち物らしいエンティティ
        for (Entity e : level.getEntities((Entity) null, area.inflate(0, 8, 0), PeakScar::belongsToSomeone)) {
            return Result.no("owned entity " + e.getType().builtInRegistryHolder().key().location().getPath());
        }

        // 計画を作る: 削る所 (楕円) は頂を平らに、進む向きにかけて少しずつ深く。溝を数本、前へ引きずる
        List<Change> removals = new ArrayList<>();
        List<Change> whiten = new ArrayList<>();
        List<Change> debris = new ArrayList<>();
        java.util.Set<Long> touched = new java.util.HashSet<>();
        double[] grooveSides = new double[shape.grooves()];
        double spacing = shape.halfWidth() * 0.62;
        for (int i = 0; i < grooveSides.length; i++) {
            grooveSides[i] = (i - (grooveSides.length - 1) / 2.0) * spacing;
        }
        int innerMinX = Mth.floor(c.x() - reach);
        int innerMaxX = Mth.floor(c.x() + reach);
        int innerMinZ = Mth.floor(c.z() - reach);
        int innerMaxZ = Mth.floor(c.z() + reach);
        // 雪の峰か: 削る所の地表の4割以上が雪なら、切り口は「むき出しの灰色の岩」にする (白い方解石では雪に紛れる)。
        // 岩の峰なら、切り口を白い方解石にする (「切り口の岩はまだ白かった」)
        int columns = 0;
        int snowTops = 0;
        for (int x = innerMinX; x <= innerMaxX; x++) {
            for (int z = innerMinZ; z <= innerMaxZ; z++) {
                double rx = x + 0.5 - (c.x() + 0.5);
                double rz = z + 0.5 - (c.z() + 0.5);
                if (sq((rx * dx + rz * dz) / shape.halfLength()) + sq((-rx * dz + rz * dx) / shape.halfWidth()) <= 1.0) {
                    columns++;
                    if (snow(level.getBlockState(m.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z)))) {
                        snowTops++;
                    }
                }
            }
        }
        boolean snowy = snowTops > columns * 0.4;
        for (int x = innerMinX; x <= innerMaxX; x++) {
            for (int z = innerMinZ; z <= innerMaxZ; z++) {
                double rx = x + 0.5 - (c.x() + 0.5);
                double rz = z + 0.5 - (c.z() + 0.5);
                double along = rx * dx + rz * dz;
                double side = -rx * dz + rz * dx;
                boolean inCut = sq(along / shape.halfLength()) + sq(side / shape.halfWidth()) <= 1.0;
                boolean inGroove = false;
                for (double g : grooveSides) {
                    if (Math.abs(side - g) < 0.6) {
                        inGroove = true;
                        break;
                    }
                }
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (inCut) {
                    int cut = Mth.floor(baseCut - 0.15 * along);
                    int floor = inGroove ? cut - 1 : cut;
                    if (surface <= floor) {
                        continue;
                    }
                    for (int y = surface; y > floor; y--) {
                        BlockState s = level.getBlockState(m.set(x, y, z));
                        if (s.isAir()) {
                            continue;
                        }
                        if (!removable(s)) {
                            return Result.no("cannot remove " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
                        }
                        if (!s.getFluidState().isEmpty()) {
                            return Result.no("fluid");
                        }
                        removals.add(new Change(m.immutable(), s, Blocks.AIR.defaultBlockState()));
                    }
                    BlockState below = level.getBlockState(m.set(x, floor, z));
                    Change floorChange = floor(below, m.immutable(), snowy, random);
                    if (floorChange != null) {
                        whiten.add(floorChange);
                    }
                    touched.add(BlockPos.asLong(x, 0, z));
                } else if (inGroove && along > 0 && along < shape.halfLength() + shape.drag()) {
                    // 引きずった溝: 削った所の前へ、地表を一段だけ削る。先へ行くほど途切れがち
                    double t = (along - shape.halfLength()) / shape.drag();
                    if (t > 0 && random.nextFloat() < t * 0.7f) {
                        continue;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (y < MIN_PEAK_Y - 20) {
                        continue;
                    }
                    // 雪は掘り抜いて (3段まで)、下の岩を見せる
                    int bottom = y;
                    while (bottom > y - 3 && snow(level.getBlockState(m.set(x, bottom, z)))) {
                        bottom--;
                    }
                    if (bottom < y && !removable(level.getBlockState(m.set(x, bottom, z)))) {
                        bottom++;
                    }
                    boolean ok = true;
                    List<Change> col = new ArrayList<>();
                    for (int yy = surface; yy >= bottom; yy--) {
                        BlockState s = level.getBlockState(m.set(x, yy, z));
                        if (s.isAir()) {
                            continue;
                        }
                        if (!removable(s) || !s.getFluidState().isEmpty()) {
                            ok = false;
                            break;
                        }
                        col.add(new Change(m.immutable(), s, Blocks.AIR.defaultBlockState()));
                    }
                    if (ok) {
                        removals.addAll(col);
                        Change floorChange = floor(level.getBlockState(m.set(x, bottom - 1, z)), m.immutable(), snowy, random);
                        if (floorChange != null) {
                            whiten.add(floorChange);
                        }
                        touched.add(BlockPos.asLong(x, 0, z));
                    }
                }
            }
        }

        // 削った所と溝の近くに水や溶岩がないこと (流れ込ませない)
        for (Change ch : removals) {
            for (int ox = -2; ox <= 2; ox++) {
                for (int oy = -2; oy <= 2; oy++) {
                    for (int oz = -2; oz <= 2; oz++) {
                        if (!level.getFluidState(m.set(ch.pos().getX() + ox, ch.pos().getY() + oy, ch.pos().getZ() + oz)).isEmpty()) {
                            return Result.no("fluid");
                        }
                    }
                }
            }
        }

        int removed = 0;
        for (Change ch : removals) {
            if (!vegetation(ch.expect())) {
                removed++;
            }
        }
        if (removed > MAX_REMOVED) {
            return Result.no("too large");
        }
        if (removed < MIN_REMOVED) {
            return Result.no("too small");
        }

        // 散った岩: 削った所の先の地面に、数個だけ転がす
        for (int tries = 0; tries < shape.debris() * 6 && debris.size() < shape.debris(); tries++) {
            double along = shape.halfLength() + 2 + random.nextDouble() * (shape.drag() + 6);
            double side = (random.nextDouble() * 2 - 1) * (shape.halfWidth() + 3);
            int x = Mth.floor(c.x() + 0.5 + dx * along - dz * side);
            int z = Mth.floor(c.z() + 0.5 + dz * along + dx * side);
            if (touched.contains(BlockPos.asLong(x, 0, z)) || x < minX + MARGIN / 2 || x > maxX - MARGIN / 2
                    || z < minZ + MARGIN / 2 || z > maxZ - MARGIN / 2) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockState ground = level.getBlockState(m.set(x, y - 1, z));
            BlockState at = level.getBlockState(m.set(x, y, z));
            if (y - 1 < minY || !REMOVABLE.contains(ground.getBlock()) || !(at.isAir() || vegetation(at))) {
                continue;
            }
            touched.add(BlockPos.asLong(x, 0, z));
            debris.add(new Change(new BlockPos(x, y, z), at, DEBRIS[random.nextInt(DEBRIS.length)]));
        }

        // 上から順に消す (支えを先に消さない)。それから切り口を白く、最後に岩を置く
        removals.sort(Comparator.comparingInt((Change ch) -> -ch.pos().getY()));
        List<Change> all = new ArrayList<>(removals.size() + whiten.size() + debris.size());
        all.addAll(removals);
        all.addAll(whiten);
        all.addAll(debris);
        return new Result(new Plan(new BlockPos(c.x(), c.y(), c.z()), dx, dz, List.copyOf(all), removed, area), "ok");
    }

    static String name(BlockState s) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
    }

    // ---- 「人が積んだ」形を探す ----
    // 調べた範囲のブロックを、種類の番号で覚える。0 = 空き (空気・草花・雪の層)、1 = そのほかの自然のもの (原木・葉・水・氷など)、
    // 2 以上 = 消してよい地形 (REMOVABLE) の種類ごとの番号。範囲の外は -1 (分からない。どの形にも数えない)
    static final byte OPEN = 0;
    static final byte OTHER = 1;
    private static final Map<Block, Byte> CODES = new HashMap<>();
    static final byte GRASS;
    static final byte SNOW_CODE;
    static final byte POWDER_CODE;
    private static final Set<Byte> SOILS;

    static {
        byte next = 2;
        for (Block b : REMOVABLE.stream().sorted(Comparator.comparing(Block::getDescriptionId)).toList()) {
            CODES.put(b, next++);
        }
        GRASS = CODES.get(Blocks.GRASS_BLOCK);
        SNOW_CODE = CODES.get(Blocks.SNOW_BLOCK);
        POWDER_CODE = CODES.get(Blocks.POWDER_SNOW);
        SOILS = Set.of(CODES.get(Blocks.GRASS_BLOCK), CODES.get(Blocks.DIRT), CODES.get(Blocks.COARSE_DIRT), CODES.get(Blocks.PODZOL),
                CODES.get(Blocks.ROOTED_DIRT));
    }

    static byte code(BlockState s) {
        if (s.isAir() || (vegetation(s) && s.getFluidState().isEmpty())) {
            return OPEN;
        }
        Byte c = CODES.get(s.getBlock());
        return c == null ? OTHER : c;
    }

    static final class Grid {
        final int x0;
        final int y0;
        final int z0;
        final int sx;
        final int sy;
        final int sz;
        private final byte[] cells;

        Grid(int x0, int y0, int z0, int sx, int sy, int sz) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.cells = new byte[sx * sy * sz];
        }

        void set(int x, int y, int z, byte c) {
            cells[((x - x0) * sz + (z - z0)) * sy + (y - y0)] = c;
        }

        // 範囲の外は -1
        byte at(int x, int y, int z) {
            int ix = x - x0;
            int iy = y - y0;
            int iz = z - z0;
            if (ix < 0 || iy < 0 || iz < 0 || ix >= sx || iy >= sy || iz >= sz) {
                return -1;
            }
            return cells[(ix * sz + iz) * sy + iy];
        }

        boolean solid(int x, int y, int z) {
            return at(x, y, z) >= 2;
        }

        boolean open(int x, int y, int z) {
            return at(x, y, z) == OPEN;
        }
    }

    // 自己テストが、本物の山で各判定がどれだけ当たるかを数えるための口 (ふだんは null)
    @Nullable
    static java.util.function.Consumer<Grid> gridProbe;

    // 自然の地形としては不自然な形 (人が石・土・草だけで積んだもの) を探す。見つかれば理由
    //  - 草ブロックの上に固いブロック (草は上がふさがると土に変わる。生成では草の上に何も積まれない)
    //  - 草ブロックのすぐ下が空きで、3段以内に床がある (草でふさいだ穴・草の屋根)
    //  - 細い柱 (四方が空きの固いブロックが4段。塔・足場の柱)
    //  - 細い壁 (両側が空きの固いブロックが、縦3×横3。小屋の壁・塀)
    //  - 積んだ箱・台 (上がまっ平らな4×4で、四方の側面が3段まっすぐ切り立つ)
    //  - まっ平らな床 (雪以外の同じ種類のブロックが同じ高さで7×7、上がすべて空き)
    //  - バイオームの表層に合わない土 (砂漠・荒野・浜の地表に草や土)
    @Nullable
    static String builtShape(ServerLevel level, Grid g) {
        int x1 = g.x0 + g.sx;
        int y1 = g.y0 + g.sy;
        int z1 = g.z0 + g.sz;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = g.x0; x < x1; x++) {
            for (int z = g.z0; z < z1; z++) {
                for (int y = g.y0; y < y1; y++) {
                    byte c = g.at(x, y, z);
                    if (c < 2) {
                        continue;
                    }
                    String what = null;
                    if (stackedOnGrass(g, x, y, z)) {
                        what = "a block stacked on grass";
                    } else if (hollowUnderGrass(g, x, y, z)) {
                        what = "hollow under grass";
                    } else if (pillar(g, x, y, z, PILLAR_H)) {
                        what = "a thin pillar";
                    } else if (thinWall(g, x, y, z, WALL_H, WALL_W)) {
                        what = "a thin wall";
                    } else if (raisedBox(g, x, y, z, BOX_W, BOX_H)) {
                        what = "a raised box";
                    } else if (flatFloor(g, x, y, z, FLOOR)) {
                        what = "a flat floor of " + name(level.getBlockState(m.set(x, y, z)));
                    } else if (SOILS.contains(c) && g.open(x, y + 1, z) && foreignSoil(level, m.set(x, y, z))) {
                        what = "soil that does not belong to the biome";
                    }
                    if (what != null) {
                        return "built shape: " + what + " at " + x + " " + y + " " + z;
                    }
                }
            }
        }
        return null;
    }

    // しきい値は、生成された本物の山 (尖った峰・凍った峰・岩の峰) で一度も当たらない大きさにした (自己テストで数えている)。
    // 細い柱3段・宙に浮いた薄い板・まっすぐな垂直の面・5×5の平らな面は、自然の山でもよく当たるので使わない
    static final int PILLAR_H = 4;
    static final int WALL_H = 3;
    static final int WALL_W = 3;
    static final int BOX_W = 4;
    static final int BOX_H = 3;
    static final int FLOOR = 7;

    static boolean stackedOnGrass(Grid g, int x, int y, int z) {
        return g.at(x, y, z) == GRASS && g.solid(x, y + 1, z);
    }

    // 草のすぐ下が空きで、その下 3 段以内に床がある (草でふさいだ穴・低い草の屋根)。
    // 生成の山では、宙に浮いた草や張り出しの縁の草 (下が 4 段以上空き) がよくあるので、それは数えない
    static boolean hollowUnderGrass(Grid g, int x, int y, int z) {
        if (g.at(x, y, z) != GRASS || !g.open(x, y - 1, z)) {
            return false;
        }
        for (int d = 2; d <= HOLLOW_MAX + 1; d++) {
            if (g.solid(x, y - d, z)) {
                return true;
            }
            if (!g.open(x, y - d, z)) {
                return false;
            }
        }
        return false;
    }

    static final int HOLLOW_MAX = 3;

    private static boolean pillarCell(Grid g, int x, int y, int z) {
        return g.solid(x, y, z) && g.open(x + 1, y, z) && g.open(x - 1, y, z) && g.open(x, y, z + 1) && g.open(x, y, z - 1);
    }

    // 四方が空きの固いブロックが h 段
    static boolean pillar(Grid g, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            if (!pillarCell(g, x, y + i, z)) {
                return false;
            }
        }
        return true;
    }

    // 縦 h × 横 w の、厚さ1の壁 (x の両側が空きで z へ並ぶ、または z の両側が空きで x へ並ぶ)
    static boolean thinWall(Grid g, int x, int y, int z, int h, int w) {
        boolean alongZ = true;
        boolean alongX = true;
        for (int dy = 0; dy < h && (alongZ || alongX); dy++) {
            for (int d = 0; d < w; d++) {
                alongZ = alongZ && g.solid(x, y + dy, z + d) && g.open(x - 1, y + dy, z + d) && g.open(x + 1, y + dy, z + d);
                alongX = alongX && g.solid(x + d, y + dy, z) && g.open(x + d, y + dy, z - 1) && g.open(x + d, y + dy, z + 1);
            }
        }
        return alongZ || alongX;
    }

    // 積んだ箱・台: (x, z) から n×n の固いブロックの柱が、上は同じ高さ y でまっ平ら (上がすべて空き)、
    // 四方の側面は h 段まっすぐ切り立っている (側面の外がすべて空き)
    static boolean raisedBox(Grid g, int x, int y, int z, int n, int h) {
        for (int dx = 0; dx < n; dx++) {
            for (int dz = 0; dz < n; dz++) {
                if (!g.open(x + dx, y + 1, z + dz)) {
                    return false;
                }
                for (int dy = 0; dy < h; dy++) {
                    if (!g.solid(x + dx, y - dy, z + dz)) {
                        return false;
                    }
                }
            }
        }
        for (int dy = 0; dy < h; dy++) {
            for (int i = 0; i < n; i++) {
                if (!g.open(x - 1, y - dy, z + i) || !g.open(x + n, y - dy, z + i) || !g.open(x + i, y - dy, z - 1)
                        || !g.open(x + i, y - dy, z + n)) {
                    return false;
                }
            }
        }
        return true;
    }

    // 雪以外の同じ種類・同じ高さで、上が空きのブロックが n×n
    static boolean flatFloor(Grid g, int x, int y, int z, int n) {
        byte c = g.at(x, y, z);
        if (c < 2 || c == SNOW_CODE || c == POWDER_CODE) {
            return false;
        }
        for (int dx = 0; dx < n; dx++) {
            for (int dz = 0; dz < n; dz++) {
                if (g.at(x + dx, y, z + dz) != c || !g.open(x + dx, y + 1, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    // 砂漠・荒野・浜の地表に草や土はない (運び込まれたもの)
    static boolean foreignSoil(ServerLevel level, BlockPos pos) {
        return foreignBiome(testBiome != null ? testBiome : level.getBiome(pos));
    }

    static boolean foreignBiome(Holder<Biome> biome) {
        return biome.is(Tags.Biomes.IS_DESERT) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_BEACH);
    }

    // 自己テストが、固定の峰を置いた場所のバイオームを決めるための口 (ふだんは null。そのときは本当のバイオームを見る)。
    // 固定の峰は空中に置くので、そこのバイオームはワールドの種しだい (砂漠なら頂の草が「運び込まれた土」になる)
    @Nullable
    static Holder<Biome> testBiome;

    // 削る範囲のさらに外 (横に OUTER まで) の、峰の高さにある地表を見る。
    // 地表のすぐ上にブロックの光 (松明・ランタンなど) があるか、外の地表から OUTER_DEPTH 段に自然でないブロックがあれば理由
    @Nullable
    private static String nearbySigns(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int outMinX, int outMaxX, int outMinZ,
                                      int outMaxZ, int minY, Map<Long, Boolean> trees) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = outMinX; x <= outMaxX; x++) {
            for (int z = outMinZ; z <= outMaxZ; z++) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (top < minY) {
                    continue;
                }
                if (level.getBrightness(LightLayer.BLOCK, m.set(x, top + 1, z)) > 0) {
                    return "light nearby at " + x + " " + (top + 1) + " " + z;
                }
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    continue;
                }
                for (int y = Math.max(minY, top - OUTER_DEPTH + 1); y <= top; y++) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (!natural(s) && !(wildLog(s) && naturalTree(level, m, trees))) {
                        return "non-natural " + name(s) + " nearby at " + x + " " + y + " " + z;
                    }
                }
            }
        }
        return null;
    }

    static boolean snow(BlockState s) {
        return s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW);
    }

    // 削ったあとの底のブロックをどう変えるか (変えないなら null)。
    // 雪の峰: 底に残った雪を灰色の岩 (石・安山岩) にする。岩の峰: 石の底の4分の3を白い方解石にする
    @Nullable
    private static Change floor(BlockState below, BlockPos pos, boolean snowy, RandomSource random) {
        if (snowy) {
            if (below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.POWDER_SNOW)) {
                return new Change(pos, below, (random.nextFloat() < 0.7f ? Blocks.STONE : Blocks.ANDESITE).defaultBlockState());
            }
            return null;
        }
        if (WHITENS.contains(below.getBlock()) && random.nextFloat() < 0.75f) {
            return new Change(pos, below, Blocks.CALCITE.defaultBlockState());
        }
        return null;
    }

    // 人の持ち物かもしれないエンティティ: 防具立て・額縁・絵・乗り物・トロッコなど (生き物でないもの)、
    // 飼いならした・名前の付いた・消えない設定の・リードでつないだ生き物、プレイヤー
    static boolean belongsToSomeone(Entity e) {
        if (e instanceof ItemEntity) {
            return false;
        }
        if (e instanceof net.minecraft.world.entity.player.Player player) {
            // 観戦モードの人は、ブロックに触れないので数えない
            return !player.isSpectator();
        }
        if (!(e instanceof Mob mob)) {
            return true;
        }
        if (mob.hasCustomName() || mob.isPersistenceRequired() || mob.isLeashed() || mob.isVehicle() || mob.isPassenger()) {
            return true;
        }
        return mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null;
    }

    private static double sq(double v) {
        return v * v;
    }
}
