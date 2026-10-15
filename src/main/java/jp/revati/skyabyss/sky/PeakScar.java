package jp.revati.skyabyss.sky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

// 「削られた山頂」の計画。影の通過経路の下にある高い自然の峰を探し、巨大なものが擦ったように
// 頂を平らに削り、進む向きに引きずった溝と、散った岩を残す計画を作る。
//
// プレイヤーの建築は絶対に壊さない。そのため、疑わしいときは削らない (安全側):
//  1. 消すのは自然に生成される地形のブロック (石・花崗岩・閃緑岩・安山岩・凝灰岩・深層岩・方解石・土・草・雪・砂利・氷塊・鉱石と草花) だけ
//  2. 削る所の周り (横に16、上下に12) に、自然のブロック以外が一つでもあれば削らない
//     (松明・作業台・ベッド・板材・丸石・ガラス・柵・扉など、人の手を思わせるものはすべて「自然でない」に入る)
//  3. その範囲に、ブロックエンティティ (チェスト・看板・かまどなど)、防具立て・額縁・乗り物、飼いならした・名前の付いた・
//     消えない設定の生き物がいれば削らない
//  4. プレイヤーがブロックを置いたことのあるチャンク (ChapterSkyData) と、プレイヤーが長く過ごしたチャンク
//     (InhabitedTime が1時間を超える) にかかるなら削らない
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

    // 経路 (origin から dir へ length) の左右 lateral の範囲で、高い峰を探す。読み込み済みのチャンクだけを見る。高い順
    public static List<Candidate> findCandidates(ServerLevel level, double originX, double originZ, float dirX, float dirZ,
                                                 double length, double lateral) {
        List<Candidate> found = new ArrayList<>();
        for (double s = 0; s <= length; s += 8) {
            for (double l = -lateral; l <= lateral; l += 8) {
                int x = Mth.floor(originX + dirX * s - dirZ * l);
                int z = Mth.floor(originZ + dirZ * s + dirX * l);
                int h = top(level, x, z);
                if (h < MIN_PEAK_Y) {
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
                boolean duplicate = false;
                for (Candidate c : found) {
                    if (Math.abs(c.x() - x) < 24 && Math.abs(c.z() - z) < 24) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    found.add(new Candidate(x, h, z));
                }
            }
        }
        found.sort(Comparator.comparingInt(Candidate::y).reversed());
        return found;
    }

    // 地形の一番上の固いブロックの高さ (葉は数えない)。読み込まれていなければ最低の値
    static int top(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return Integer.MIN_VALUE;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    // 周りの8点の平均よりどれだけ高いか (読み込まれていない点があれば 0)
    static double prominence(ServerLevel level, int x, int z, int h) {
        double sum = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int t = top(level, x + Mth.floor(Math.cos(a) * PROMINENCE_RING), z + Mth.floor(Math.sin(a) * PROMINENCE_RING));
            if (t == Integer.MIN_VALUE) {
                return 0;
            }
            sum += t;
        }
        return h - sum / 8.0;
    }

    // 削り方の大きさ。strength (0〜1) は月の近さで、近いほど深く長い
    record Shape(double halfLength, double halfWidth, int depth, int grooves, double drag, int debris) {
        static Shape of(float strength) {
            float s = Mth.clamp(strength, 0f, 1f);
            return new Shape(7.0 + 4.0 * s, 3.0 + 1.2 * s, 2 + Math.round(2 * s), s < 0.5f ? 2 : 3, 6.0 + 6.0 * s, 6 + Math.round(6 * s));
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

        // チャンク: 読み込み済み・プレイヤーが置いていない・長く過ごしていない
        ChapterSkyData data = ChapterSkyData.get(level);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return Result.no("chunk not loaded");
                }
                if (data.wasPlaced(ChunkPos.asLong(cx, cz))) {
                    return Result.no("player placed blocks here");
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                if (chunk.getInhabitedTime() > MAX_INHABITED) {
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
        int maxY = minY;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                maxY = Math.max(maxY, surface);
                for (int y = minY; y <= surface; y++) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (!natural(s)) {
                        return Result.no("artifact " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath()
                                + " at " + x + " " + y + " " + z);
                    }
                }
            }
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
                    if (WHITENS.contains(below.getBlock()) && random.nextFloat() < 0.75f) {
                        whiten.add(new Change(m.immutable(), below, Blocks.CALCITE.defaultBlockState()));
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
                    boolean ok = true;
                    List<Change> col = new ArrayList<>();
                    for (int yy = surface; yy >= y; yy--) {
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

    // 人の持ち物かもしれないエンティティ: 防具立て・額縁・絵・乗り物・トロッコなど (生き物でないもの)、
    // 飼いならした・名前の付いた・消えない設定の・リードでつないだ生き物、プレイヤー
    static boolean belongsToSomeone(Entity e) {
        if (e instanceof ItemEntity) {
            return false;
        }
        if (e instanceof net.minecraft.world.entity.player.Player) {
            return true;
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
