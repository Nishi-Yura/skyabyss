package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Kind;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Modifier;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Platform;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Rare;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Section;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

// 区画の形 (docs/design/36 の 9)。区画の番号と決まり (DepthPlan.plan) から、置く block を順に並べる。
//   置く順 = 壁 → 天井 → 型の景色 (大きな目印。足場より先) → 足場の道 → 脇の岩棚 → 灯り → 足場の縁の光 → 珍しい部屋 → 灯籠・鎖・裂け目。
//   景色は型ごとに一つの「場所」になる: 区画の半径 (ROOM_R) いっぱいに、数十ブロックの大きな目印を置く。
//   道は区画の中心から半径 10 の螺旋 (DepthPlan.route)。景色は道より外 (半径 11 以上) に置くので、道は景色の中を通る。
//   同じ区画なら、いつも同じ並び (自己テストが見る)。ワールドには触らない (DepthBuilder が置く)。
//   座標: Put の x・z は世界の値、y も世界の値 (区画の上 = DepthPlan.TOP_Y)。
//   光の言葉: 型ごとに灯りの種類を変える (暖かい・青白い・赤い・白い)。足場の縁には、どの型も小さな光の棒を一本ずつ立てて道を読めるようにする。
public final class DepthLayout {
    private static final int SALT = 0x2F3D;
    private static final double LAMP_R = 22.5;

    // 置く一つの block (loot があれば箱の中身を入れる)
    public record Put(int x, int y, int z, BlockState state, @Nullable ResourceKey<LootTable> loot) {
    }

    // 型ごとの材料 (lamp は灯り。dark は灯りが消えたときの壁。accent は型の目印)
    public record Palette(BlockState wall, BlockState route, BlockState ledge, BlockState accent, BlockState lamp, BlockState dark, BlockState roof) {
    }

    // 区画の結果。route と ledges は足場 (DepthPlan)。lamps は灯り。veins は脈動する壁 (脈の洞だけ)
    public record Result(Section section, Palette palette, List<Put> puts, List<Platform> route, List<Platform> ledges, List<BlockPos> lamps,
            BlockPos entryFeet, BlockPos restFeet, BlockPos lantern, BlockPos exit, BlockPos hole, BlockPos roomCenter, List<BlockPos> veins) {
        // 置く一覧を捨てた形 (建てたあとに持つ。場所の決まりだけ残す)
        public Result stripped() {
            return new Result(section, palette, List.of(), route, ledges, lamps, entryFeet, restFeet, lantern, exit, hole, roomCenter, veins);
        }
    }

    private final Section s;
    private final int cx;
    private final List<Put> puts = new ArrayList<>();
    private final List<BlockPos> veins = new ArrayList<>();
    // 景色の中の灯り (灯りが消える仕掛けの対象にもなる)
    private final List<BlockPos> extraLamps = new ArrayList<>();
    private final Palette pal;
    private final Random r;

    private DepthLayout(Section s) {
        this.s = s;
        this.cx = s.centerX();
        this.pal = palette(s.kind());
        this.r = new Random(s.seed() ^ SALT);
    }

    public static Result build(Section s) {
        DepthLayout b = new DepthLayout(s);
        return b.run();
    }

    // 区画の型ごとの材料と灯りの言葉
    public static Palette palette(Kind k) {
        return switch (k) {
            case INVERTED_FOREST -> new Palette(Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(),
                    Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.MANGROVE_LOG.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState());
            case FLOATING_GRAVES -> new Palette(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
                    Blocks.SEA_LANTERN.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            case PULSE_CAVE -> new Palette(Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(),
                    Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_WART_BLOCK.defaultBlockState(),
                    Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_WART_BLOCK.defaultBlockState());
            case MIRROR_DARK -> new Palette(Blocks.BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState(),
                    Blocks.GLOWSTONE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState());
            case CHAIN_NET -> new Palette(Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
                    Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.CHAIN.defaultBlockState(),
                    Blocks.SEA_LANTERN.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState());
            case LAMP_HALL -> new Palette(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                    Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.SCULK.defaultBlockState(),
                    Blocks.SEA_LANTERN.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.SCULK.defaultBlockState());
            case DRY_WELL -> new Palette(Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState(),
                    Blocks.MUD_BRICKS.defaultBlockState(), Blocks.DRIPSTONE_BLOCK.defaultBlockState(),
                    Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState());
            case HOLLOW_STARS -> new Palette(Blocks.BLACK_CONCRETE.defaultBlockState(), Blocks.END_STONE_BRICKS.defaultBlockState(),
                    Blocks.END_STONE.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(),
                    Blocks.GLOWSTONE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState());
        };
    }

    private Result run() {
        List<Platform> route = DepthPlan.route(s);
        List<Platform> ledges = DepthPlan.ledges(s, route);
        walls();
        roof();
        // 型の景色は足場より先に置く (足場の板を景色で切らないため。足場は後から上書きする)
        decorate(route);
        for (Platform p : route) {
            platform(p, pal.route());
        }
        for (Platform p : ledges) {
            platform(p, pal.ledge());
        }
        edgeLights(route);
        List<BlockPos> lamps = lamps();
        lamps.addAll(extraLamps);
        Platform mid = route.get(route.size() / 2);
        BlockPos room = rare(mid);
        // 裂け目 (最後の広い足場の真ん中を抜く。縁は灯りで囲み、下に穴があることを見せる)
        Platform last = route.get(route.size() - 1);
        BlockPos hole = at(last.dx(), last.dz(), last.ly());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                puts.add(new Put(hole.getX() + dx, hole.getY(), hole.getZ() + dz, Blocks.AIR.defaultBlockState(), null));
            }
        }
        ring(last, 2.0, 2.6, pal.lamp());
        // 灯籠 (道の真ん中の足場に一つ。点けると、そこが再開の所) と、帰りの鎖 (灯籠の横)
        BlockPos entryFeet = feet(route.get(0), 0, 0);
        BlockPos restFeet = feet(mid, 0, 0);
        BlockPos lantern = feet(mid, 1, 0);
        BlockPos exit = feet(mid, -1, 0);
        put(lantern, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, false));
        put(exit, Blocks.CHAIN.defaultBlockState());
        return new Result(s, pal, List.copyOf(puts), route, ledges, lamps, entryFeet, restFeet, lantern, exit, hole, room, List.copyOf(veins));
    }

    // 世界の座標へ (x・z は区画の中心からの水平。ly は区画の上からの段)
    private BlockPos at(double dx, double dz, int ly) {
        return new BlockPos(cx + (int) Math.round(dx), DepthPlan.TOP_Y - ly, (int) Math.round(dz));
    }

    // 足場の上 (立つ所) の一つ上。ox・oy は足場の中心からのずれ
    private BlockPos feet(Platform p, int ox, int oy) {
        BlockPos b = at(p.dx(), p.dz(), p.ly());
        return b.offset(ox, 1 + oy, 0);
    }

    private void put(BlockPos b, BlockState st) {
        puts.add(new Put(b.getX(), b.getY(), b.getZ(), st, null));
    }

    private void put(int rx, int ly, int rz, BlockState st) {
        puts.add(new Put(cx + rx, DepthPlan.TOP_Y - ly, rz, st, null));
    }

    private void loot(BlockPos b, BlockState st, ResourceKey<LootTable> table) {
        puts.add(new Put(b.getX(), b.getY(), b.getZ(), st, table));
    }

    // 円筒の壁 (半径 ROOM_R-1 〜 ROOM_R の一枚)。脈の洞の脈は脈動するので、場所を覚える
    private void walls() {
        int rr = DepthPlan.ROOM_R;
        for (int ly = 0; ly < DepthPlan.HEIGHT; ly++) {
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 >= (rr - 1) * (rr - 1) && d2 < rr * rr) {
                        BlockState w = wallBlock(dx, dz, ly);
                        put(dx, ly, dz, w);
                        if (s.kind() == Kind.PULSE_CAVE && w.equals(pal.accent())) {
                            veins.add(new BlockPos(cx + dx, DepthPlan.TOP_Y - ly, dz));
                        }
                    }
                }
            }
        }
    }

    // 壁の材料 (型ごとの模様: 鏡の闇は三段の鏡の帯、脈の洞は縦の脈、星の空洞は星)
    private BlockState wallBlock(int dx, int dz, int ly) {
        if (s.kind() == Kind.MIRROR_DARK && ly % 24 >= 10 && ly % 24 <= 12) {
            return pal.accent();
        }
        if (s.kind() == Kind.HOLLOW_STARS && r.nextInt(60) == 0) {
            return Blocks.GLOWSTONE.defaultBlockState();
        }
        if (s.kind() == Kind.PULSE_CAVE) {
            // 壁の脈: 四十の区切りのうち五つごとに、縦の帯 (十段のうち六段)
            int seg = Math.floorMod((int) Math.floor((Math.atan2(dz, dx) + Math.PI) * 40 / (2 * Math.PI)), 40);
            if (seg % 5 == 0 && ly % 10 < 6) {
                return pal.accent();
            }
        }
        return pal.wall();
    }

    private void roof() {
        int rr = DepthPlan.ROOM_R;
        for (int dx = -rr; dx <= rr; dx++) {
            for (int dz = -rr; dz <= rr; dz++) {
                if (dx * dx + dz * dz < rr * rr) {
                    put(dx, 0, dz, pal.roof());
                }
            }
        }
    }

    // 足場 (中心から半径 rad の丸い板)
    private void platform(Platform p, BlockState st) {
        int cxr = (int) Math.round(p.dx());
        int czr = (int) Math.round(p.dz());
        int r2 = (int) Math.ceil(p.rad());
        for (int i = -r2; i <= r2; i++) {
            for (int j = -r2; j <= r2; j++) {
                if (i * i + j * j <= p.rad() * p.rad() + 1e-9) {
                    put(cxr + i, p.ly(), czr + j, st);
                }
            }
        }
    }

    private void ring(Platform p, double inner, double outer, BlockState st) {
        int cxr = (int) Math.round(p.dx());
        int czr = (int) Math.round(p.dz());
        int r2 = (int) Math.ceil(outer);
        for (int i = -r2; i <= r2; i++) {
            for (int j = -r2; j <= r2; j++) {
                double d2 = i * i + j * j;
                if (d2 <= outer * outer && d2 >= inner * inner) {
                    put(cxr + i, p.ly(), czr + j, st);
                }
            }
        }
    }

    // 足場の縁の光: 三つに一つの足場に、板の上の一角に光の棒を立てる (道を読むための目印)
    private void edgeLights(List<Platform> route) {
        for (Platform p : route) {
            if (p.k() % 3 == 0 && !p.landing()) {
                int cxr = (int) Math.round(p.dx());
                int czr = (int) Math.round(p.dz());
                put(cxr + 1, p.ly() - 1, czr, Blocks.END_ROD.defaultBlockState());
            }
        }
    }

    // 灯り: 壁の内側に、六段ごとに六方へ (LAMP_HALL は八方。SPARSE_LIGHT は半分)
    private List<BlockPos> lamps() {
        List<BlockPos> out = new ArrayList<>();
        int every = 6;
        int angles = s.kind() == Kind.LAMP_HALL ? 8 : 6;
        if (s.has(Modifier.SPARSE_LIGHT)) {
            every *= 2;
        }
        for (int ly = 6; ly < DepthPlan.HEIGHT - 6; ly += every) {
            for (int a = 0; a < angles; a++) {
                double ang = a * Math.PI * 2 / angles + (ly / every) * 0.3;
                int x = (int) Math.round(Math.cos(ang) * LAMP_R);
                int z = (int) Math.round(Math.sin(ang) * LAMP_R);
                BlockPos b = new BlockPos(cx + x, DepthPlan.TOP_Y - ly, z);
                put(b, pal.lamp());
                out.add(b);
            }
        }
        return out;
    }

    // 縦の柱 (rx・rz の位置に、ly0 から ly1 まで)
    private void column(int rx, int rz, int ly0, int ly1, BlockState st) {
        for (int ly = ly0; ly <= ly1; ly++) {
            put(rx, ly, rz, st);
        }
    }

    // 丸い塊 (中心から半径 rad の球。葉や肉や水晶の塊)
    private void blob(int rx, int ly, int rz, int rad, BlockState st) {
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= rad * rad) {
                        put(rx + dx, ly + dy, rz + dz, st);
                    }
                }
            }
        }
    }

    // 円盤 (ly の段に、中心 rx・rz から半径 rad の丸い板。景色の島や台に使う)
    private void disk(int rx, int ly, int rz, double rad, BlockState st) {
        int r2 = (int) Math.ceil(rad);
        for (int i = -r2; i <= r2; i++) {
            for (int j = -r2; j <= r2; j++) {
                if (i * i + j * j <= rad * rad + 1e-9) {
                    put(rx + i, ly, rz + j, st);
                }
            }
        }
    }

    // 線 (二つの点の間を、一ブロックずつ並べる。座標は区画の中の (x, ly, z))
    private void line(int ax, int ay, int az, int bx, int by, int bz, BlockState st) {
        int n = Math.max(Math.abs(bx - ax), Math.max(Math.abs(by - ay), Math.abs(bz - az)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            put((int) Math.round(ax + (bx - ax) * t), (int) Math.round(ay + (by - ay) * t), (int) Math.round(az + (bz - az) * t), st);
        }
    }

    // 角と半径から、区画の中の水平の位置
    private static int ox(double ang, double rad) {
        return (int) Math.round(Math.cos(ang) * rad);
    }

    private static int oz(double ang, double rad) {
        return (int) Math.round(Math.sin(ang) * rad);
    }

    // 型の景色 (足場より先に置く。景色は半径 11 より外。道は景色の中を通る)
    private void decorate(List<Platform> route) {
        switch (s.kind()) {
            case INVERTED_FOREST -> invertedForest();
            case FLOATING_GRAVES -> floatingGraves();
            case PULSE_CAVE -> pulseCave();
            case MIRROR_DARK -> mirrorDark(route);
            case CHAIN_NET -> chainNet();
            case LAMP_HALL -> lampHall();
            case DRY_WELL -> dryWell();
            case HOLLOW_STARS -> hollowStars();
        }
    }

    // 逆さの森: 天井から逆さに垂れる大木の林。大きな木は幹が三ブロック幅で 36〜58 段、冠は下端に半径 7。
    //   小さな木が天井の間を埋め、冠の縁から根が垂れる。幹の途中から横の枝の板が道の方へ出る (道はその間を跳ぶ)
    private void invertedForest() {
        BlockState leaves = Blocks.MANGROVE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState log = Blocks.MANGROVE_LOG.defaultBlockState();
        BlockState root = Blocks.MANGROVE_ROOTS.defaultBlockState();
        BlockState wood = Blocks.MANGROVE_PLANKS.defaultBlockState();
        for (int a = 0; a < 12; a++) {
            boolean big = a % 2 == 0;
            double ang = a * Math.PI / 6 + 0.3 + (big ? 0.0 : 0.25);
            double rad = big ? 18 : 20;
            int tx = ox(ang, rad);
            int tz = oz(ang, rad);
            int len = big ? 36 + (a * 7) % 23 : 16 + (a * 5) % 10;
            int crown = big ? 7 : 5;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    column(tx + dx, tz + dz, 1, len, log);
                }
            }
            blob(tx, len, tz, crown, leaves);
            put(tx, len + crown + 1, tz, pal.lamp());
            int roots = big ? 8 : 4;
            for (int i = 0; i < roots; i++) {
                double b = i * Math.PI * 2 / roots + a * 0.2;
                int rx = tx + ox(b, crown - 1.5);
                int rz = tz + oz(b, crown - 1.5);
                column(rx, rz, len + 2, len + crown + 2 + (i * 5 + a) % 12, root);
            }
            if (big) {
                // 幹の途中の横の枝 (二段の板。道の方へ十四ブロック)
                int by = len / 2;
                int bx = ox(ang + Math.PI, 14);
                int bz = oz(ang + Math.PI, 14);
                line(tx, by, tz, bx, by, bz, wood);
                line(tx, by + 1, tz, bx, by + 1, bz, wood);
            }
        }
    }

    // 浮かぶ墓場: 墓場の浮き島が五つ、高さを変えて並ぶ。島は直径十六の板 (下は細い)。墓標四つと棺、鎖で天井からつられる。
    //   島と島の間は鎖で結ばれ、その鎖が道の横の景色になる
    private void floatingGraves() {
        BlockState slab = pal.wall();
        BlockState cracked = pal.ledge();
        int px = 0;
        int pz = 0;
        int py = 0;
        boolean hasPrev = false;
        for (int i = 0; i < 5; i++) {
            double ang = i * 1.2566 + 0.5;
            int ix = ox(ang, 17);
            int iz = oz(ang, 17);
            int top = 24 + i * 38;
            disk(ix, top, iz, 8, slab);
            disk(ix, top + 1, iz, 8, cracked);
            disk(ix, top + 2, iz, 6, cracked);
            disk(ix, top + 3, iz, 4, cracked);
            disk(ix, top + 4, iz, 2, slab);
            // 墓標 (四つ。高さ四と石の蓋)
            for (int k = 0; k < 4; k++) {
                int gx = ix + (k % 2 == 0 ? -3 : 3);
                int gz = iz + (k / 2 == 0 ? -3 : 3);
                column(gx, gz, top - 4, top - 1, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                put(gx, top - 5, gz, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
            // 棺 (中央に横たわる箱) と、その上の灯り
            put(ix, top - 1, iz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            put(ix + 1, top - 1, iz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            put(ix, top - 2, iz, Blocks.SOUL_LANTERN.defaultBlockState());
            // 天井からの鎖 (島の両端へ)
            column(ix - 6, iz, 1, top - 1, Blocks.CHAIN.defaultBlockState());
            column(ix + 6, iz, 1, top - 1, Blocks.CHAIN.defaultBlockState());
            // 前の島との間の鎖 (たるんだ線。島の上から上へ)
            if (hasPrev) {
                line(px, py, pz, ix, top - 1, iz, Blocks.CHAIN.defaultBlockState());
            }
            px = ix;
            pz = iz;
            py = top - 1;
            hasPrev = true;
        }
    }

    // 心臓の脈の洞: 壁の脈 (縦の帯) が拍を打つ。肋の輪が六段あり、底の近くに大きな心臓の塊と、そこから天井へ伸びる太い脈
    private void pulseCave() {
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        for (int k = 0; k < 6; k++) {
            int ly0 = 26 + k * 28;
            // 肋の輪 (半径 18〜19 の輪と、縦の骨の棒)
            for (int a = 0; a < 24; a++) {
                double ang = a * Math.PI / 12;
                int rx = ox(ang, 19);
                int rz = oz(ang, 19);
                column(rx, rz, ly0, ly0 + 9, bone);
            }
            for (int a = 0; a < 48; a++) {
                double ang = a * Math.PI / 24;
                put(ox(ang, 18.5), ly0, oz(ang, 18.5), bone);
            }
        }
        // 心臓の塊 (底の近く、壁の側に一つ。二つの房と、下へ尖る先)
        double hang = 0.9;
        int hx = ox(hang, 15);
        int hz = oz(hang, 15);
        int hy = 172;
        int pxh = (int) Math.round(-Math.sin(hang) * 4);
        int pzh = (int) Math.round(Math.cos(hang) * 4);
        blob(hx + pxh, hy, hz + pzh, 5, pal.accent());
        blob(hx - pxh, hy, hz - pzh, 5, pal.accent());
        blob(hx, hy + 6, hz, 4, pal.accent());
        blob(hx, hy + 11, hz, 2, pal.accent());
        // 太い脈 (心臓から天井へ三本)
        for (int i = -1; i <= 1; i++) {
            line(hx + i * 2, 1, hz, hx + i * 2, hy - 4, hz, pal.accent());
        }
        // 底の脈だまり (心臓の下に、赤い灯り)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= 9) {
                    put(hx + dx, HEIGHT_LAST, hz + dz, pal.lamp());
                }
            }
        }
    }

    // 鏡の闇: 色ガラスの柱が六本 (黒曜石の台)。道と同じ数の影が、道の反対側に上下逆に写る (黒曜石の板)
    private void mirrorDark(List<Platform> route) {
        BlockState glass = Blocks.TINTED_GLASS.defaultBlockState();
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        // 写しの道は鉄の板 (黒い壁の中で光を返す)
        for (int a = 0; a < 6; a++) {
            double ang = a * Math.PI / 3 + 0.4;
            int mx = ox(ang, 21);
            int mz = oz(ang, 21);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    column(mx + dx, mz + dz, 1, HEIGHT_LAST - 4, glass);
                    column(mx + dx, mz + dz, HEIGHT_LAST - 3, HEIGHT_LAST, obsidian);
                }
            }
            for (int ly = 9; ly < HEIGHT_LAST - 4; ly += 18) {
                put(mx, ly, mz, pal.lamp());
            }
        }
        // 逆さの影: 道の一つ一つを、上下を逆にして反対側に写す
        for (Platform p : route) {
            int ly = DepthPlan.HEIGHT - 1 - p.ly();
            double ang = Math.atan2(p.dz(), p.dx()) + Math.PI;
            disk(ox(ang, 16), ly, oz(ang, 16), 1.6, Blocks.IRON_BLOCK.defaultBlockState());
        }
    }

    // 鎖の網: 八段ごとに、格子の鎖 (三ブロックごとの糸) を天井から床まで張る。道はその間を通る。
    //   外側には、鎖の帳 (二十四本) が天井から床まで垂れる
    private void chainNet() {
        BlockState chain = Blocks.CHAIN.defaultBlockState();
        for (int ly = 4; ly < HEIGHT_LAST - 1; ly += 12) {
            for (int dx = -24; dx <= 24; dx++) {
                for (int dz = -24; dz <= 24; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 < 81 || d2 > 24 * 24) {
                        continue;
                    }
                    if (dx % 3 == 0) {
                        put(dx, ly, dz, chain.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
                    } else if (dz % 3 == 0) {
                        put(dx, ly, dz, chain.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                    }
                }
            }
        }
        for (int a = 0; a < 24; a++) {
            double ang = a * Math.PI / 12 + 0.1;
            column(ox(ang, 22), oz(ang, 22), 1, HEIGHT_LAST, chain);
        }
    }

    // 灯りを喰う広間: 八本の大きな柱 (三ブロック角。十六段ごとに縁の帯)。柱の内側の面に灯り、その横に黒い口 (黒いコンクリートの塊と、スカルクの縁)
    private void lampHall() {
        BlockState brick = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState band = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4 + 0.2;
            int px = ox(ang, 18);
            int pz = oz(ang, 18);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    column(px + dx, pz + dz, 0, HEIGHT_LAST, brick);
                }
            }
            for (int ly = 8; ly < HEIGHT_LAST; ly += 16) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
                            put(px + dx, ly, pz + dz, band);
                        }
                    }
                }
            }
            // 灯り (柱の内側の面。十六段ごと。灯りの仕掛けの対象)
            int fx = ox(ang, 15);
            int fz = oz(ang, 15);
            for (int ly = 16; ly < HEIGHT_LAST - 8; ly += 16) {
                put(fx, ly, fz, pal.lamp());
                extraLamps.add(new BlockPos(cx + fx, DepthPlan.TOP_Y - ly, fz));
            }
            // 黒い口 (柱の内側の面に埋まる黒い塊。縁はスカルク)
            int my = 150 + (a * 11) % 30;
            blob(fx, my, fz, 2, Blocks.BLACK_CONCRETE.defaultBlockState());
            blob(fx, my, fz, 3, Blocks.SCULK.defaultBlockState());
            put(fx, my, fz, Blocks.BLACK_CONCRETE.defaultBlockState());
        }
    }

    // 静かな井戸: 中心に泥煉瓦の井戸の筒 (内側の半径 4、壁の厚さ二)。十六段ごとに縁の輪 (半径 7〜9) が張り出し、
    //   井戸の縄 (鎖) が天井から底まで。底に静かな水がたまる。天井に口が開き、上から光が落ちる
    private void dryWell() {
        BlockState mud = pal.wall();
        BlockState packed = pal.route();
        for (int ly = 0; ly < DepthPlan.HEIGHT; ly++) {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 >= 25 && d2 <= 49) {
                        put(dx, ly, dz, mud);
                    }
                    if (ly % 16 == 8 && d2 >= 49 && d2 <= 81) {
                        put(dx, ly, dz, packed);
                    }
                }
            }
            if (ly % 30 == 15) {
                put(3, ly, 0, pal.lamp());
                put(-3, ly, 0, pal.lamp());
            }
            if (ly % 48 == 7) {
                // 縁の上の桶 (大釜)
                put(8, ly - 1, 0, Blocks.CAULDRON.defaultBlockState());
                put(-8, ly - 1, 0, Blocks.CAULDRON.defaultBlockState());
            }
        }
        // 天井の口 (上から光が落ちる)
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz <= 16) {
                    put(dx, 0, dz, Blocks.AIR.defaultBlockState());
                }
            }
        }
        // 縄 (井戸の中を天井から底の水まで)
        column(-2, 0, 1, 184, Blocks.CHAIN.defaultBlockState());
        // 底の水 (底の板の上に、静かな水が九ブロック)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= 9) {
                    put(dx, 195, dz, packed);
                    for (int ly = 186; ly <= 194; ly++) {
                        put(dx, ly, dz, Blocks.WATER.defaultBlockState());
                    }
                }
            }
        }
    }

    // 星のない空洞: 空に浮く大きな水晶の星 (三十六)。五つの星座は、星と星を光の線で結ぶ (線は道の目印にもなる)
    private void hollowStars() {
        BlockState amethyst = Blocks.AMETHYST_BLOCK.defaultBlockState();
        BlockState rod = Blocks.END_ROD.defaultBlockState();
        for (int i = 0; i < 36; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rr = 12 + r.nextDouble() * 8;
            int top = 8 + r.nextInt(180);
            star(ox(ang, rr), top, oz(ang, rr), 2 + r.nextInt(2), amethyst);
        }
        for (int c = 0; c < 5; c++) {
            double ang = c * 1.2566 + 0.7;
            int prevX = 0;
            int prevY = 0;
            int prevZ = 0;
            for (int k = 0; k < 4; k++) {
                int sx = ox(ang + k * 0.45, 17);
                int sy = 24 + c * 34 + k * 9 + r.nextInt(3);
                int sz = oz(ang + k * 0.45, 17);
                star(sx, sy, sz, 2, amethyst);
                if (k > 0) {
                    line(prevX, prevY, prevZ, sx, sy, sz, rod);
                }
                prevX = sx;
                prevY = sy;
                prevZ = sz;
            }
        }
    }

    // 星 (一つの大きな水晶の塊。芯に光、上に水晶の群れ)
    private void star(int rx, int ly, int rz, int rad, BlockState crystal) {
        blob(rx, ly, rz, rad, crystal);
        put(rx, ly, rz, Blocks.GLOWSTONE.defaultBlockState());
        put(rx, ly - rad - 1, rz, Blocks.AMETHYST_CLUSTER.defaultBlockState());
        put(rx + rad + 1, ly, rz, Blocks.AMETHYST_CLUSTER.defaultBlockState());
        put(rx - rad - 1, ly, rz, Blocks.AMETHYST_CLUSTER.defaultBlockState());
    }

    // 珍しい部屋 (道の真ん中から外へ、一本の橋)。部屋の中心の座標を返す (なければ道の中心)
    private BlockPos rare(Platform mid) {
        Rare rare = s.rare();
        double ang = Math.atan2(mid.dz(), mid.dx());
        int ly = mid.ly();
        if (rare == Rare.NONE) {
            return at(mid.dx(), mid.dz(), ly);
        }
        // 橋 (道の足場から外へ 7 ブロック)
        for (int t = 2; t <= 8; t++) {
            double rr = 10 + t;
            put((int) Math.round(Math.cos(ang) * rr), ly, (int) Math.round(Math.sin(ang) * rr), pal.route());
        }
        double rc = 19;
        int ox = (int) Math.round(Math.cos(ang) * rc);
        int oz = (int) Math.round(Math.sin(ang) * rc);
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                put(ox + i, ly, oz + j, pal.route());
                put(ox + i, ly + 1, oz + j, pal.wall());
            }
        }
        BlockPos center = new BlockPos(cx + ox, DepthPlan.TOP_Y - ly, oz);
        switch (rare) {
            case TREASURE_HALL -> {
                loot(center.offset(-1, 1, 0), Blocks.CHEST.defaultBlockState(), BuiltInLootTables.ANCIENT_CITY);
                loot(center.offset(1, 1, 0), Blocks.CHEST.defaultBlockState(), BuiltInLootTables.ANCIENT_CITY);
                put(center.offset(-3, 1, -3), pal.lamp());
                put(center.offset(3, 1, 3), pal.lamp());
            }
            case PAST_GRAVE -> {
                // 前の私の墓: 石の台と、遺品の樽
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        if (i != 0 || j != 0) {
                            puts.add(new Put(center.getX() + i, center.getY() + 1, center.getZ() + j, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), null));
                        }
                    }
                }
                loot(center.above(), Blocks.BARREL.defaultBlockState(), BuiltInLootTables.SIMPLE_DUNGEON);
                put(center.offset(0, 1, -2), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
                put(center.offset(0, 2, -2), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
            }
            case WARDEN_FRAGMENT -> {
                // 番人の欠片: 裂けた心臓の柱と、箱
                for (int y = 1; y <= 3; y++) {
                    puts.add(new Put(center.getX(), center.getY() + y, center.getZ(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), null));
                }
                loot(center.offset(2, 1, 0), Blocks.CHEST.defaultBlockState(), BuiltInLootTables.END_CITY_TREASURE);
            }
            default -> {
            }
        }
        return center;
    }

    // 区画の一番下の段 (ly。HEIGHT - 1)
    private static final int HEIGHT_LAST = DepthPlan.HEIGHT - 1;
}
