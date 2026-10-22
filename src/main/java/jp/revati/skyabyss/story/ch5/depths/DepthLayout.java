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
//   置く順 = 壁 → 天井 → 型の目印 (足場より先。足場を切らない) → 足場の道 → 脇の岩棚 → 灯り → 足場の縁の光 → 珍しい部屋 → 灯籠・鎖・裂け目。
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
        // 型の目印は足場より先に置く (足場の板を目印で切らないため。足場は後から上書きする)
        decorate(route);
        for (Platform p : route) {
            platform(p, pal.route());
        }
        for (Platform p : ledges) {
            platform(p, pal.ledge());
        }
        edgeLights(route);
        List<BlockPos> lamps = lamps();
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

    // 壁の材料 (型ごとの模様: 鏡の闇は帯の鏡、脈の洞は縦の脈、星の空洞は星)
    private BlockState wallBlock(int dx, int dz, int ly) {
        if (s.kind() == Kind.MIRROR_DARK && ly % 20 == 10) {
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

    // 灯り: 壁の内側に、八段ごとに四方へ (SPARSE_LIGHT は半分。LAMP_HALL は八方)
    private List<BlockPos> lamps() {
        List<BlockPos> out = new ArrayList<>();
        int every = 8;
        int angles = s.kind() == Kind.LAMP_HALL ? 8 : 4;
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

    // 型の目印 (足場より先に置く)
    private void decorate(List<Platform> route) {
        switch (s.kind()) {
            case INVERTED_FOREST -> invertedForest();
            case FLOATING_GRAVES -> floatingGraves();
            case PULSE_CAVE -> pulseCave();
            case MIRROR_DARK -> mirrorDark();
            case CHAIN_NET -> chainNet();
            case LAMP_HALL -> lampHall();
            case DRY_WELL -> dryWell();
            case HOLLOW_STARS -> hollowStars();
        }
    }

    // 逆さの森: 天井から逆さに生えた木 (幹は天井から、冠は下)。冠の下から根が垂れる
    private void invertedForest() {
        BlockState leaves = Blocks.MANGROVE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int a = 0; a < 6; a++) {
            double ang = a * Math.PI / 3 + 0.3;
            int tx = (int) Math.round(Math.cos(ang) * 17);
            int tz = (int) Math.round(Math.sin(ang) * 17);
            int len = 26 + (a * 11) % 22;
            column(tx, tz, 1, len, Blocks.MANGROVE_LOG.defaultBlockState());
            blob(tx, len, tz, 4, leaves);
            for (int i = 0; i < 4; i++) {
                int ox = i % 2 == 0 ? -2 : 2;
                int oz = (i / 2 == 0 ? -2 : 2);
                column(tx + ox, tz + oz, len + 3, len + 7 + (i + a) % 3, Blocks.MANGROVE_ROOTS.defaultBlockState());
            }
        }
    }

    // 浮かぶ墓場: 足場の外の浮き島に墓標。鎖で天井からつられる
    private void floatingGraves() {
        for (int i = 0; i < 5; i++) {
            double ang = i * 1.2566 + 0.5;
            int ix = (int) Math.round(Math.cos(ang) * 17);
            int iz = (int) Math.round(Math.sin(ang) * 17);
            int top = 26 + i * 36;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 <= 9) {
                        put(ix + dx, top, iz + dz, d2 >= 5 ? pal.ledge() : pal.wall());
                    }
                }
            }
            put(ix, top - 1, iz, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
            put(ix, top - 2, iz, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
            put(ix + 1, top - 1, iz - 1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            put(ix - 1, top - 1, iz + 1, Blocks.SOUL_LANTERN.defaultBlockState());
            column(ix - 2, iz, 1, top - 1, Blocks.CHAIN.defaultBlockState());
            column(ix + 2, iz, 1, top - 1, Blocks.CHAIN.defaultBlockState());
        }
    }

    // 心臓の脈の洞: 壁から赤い瘤が出る (脈は壁の縦の帯。脈動は DepthGimmicks.pulseVeins)
    private void pulseCave() {
        for (int k = 0; k < 6; k++) {
            double ang = k * 1.047 + 0.2;
            int ly = 24 + k * 30;
            blob((int) Math.round(Math.cos(ang) * 22), ly, (int) Math.round(Math.sin(ang) * 22), 3, pal.accent());
        }
    }

    // 鏡の闇: 足場の外の柱の上に黒い鏡 (色ガラス) と、その光。岩棚の上に立つ
    private void mirrorDark() {
        for (Platform p : ledges()) {
            if (p.k() % 6 == 3) {
                int mx = (int) Math.round(p.dx());
                int mz = (int) Math.round(p.dz());
                put(mx, p.ly() - 1, mz, pal.accent());
                put(mx, p.ly() - 2, mz, pal.accent());
                put(mx, p.ly() - 3, mz, pal.lamp());
            }
        }
    }

    // 岩棚は道の横の板 (鏡の柱の台)。道と同じ並びから出す
    private List<Platform> ledges() {
        return DepthPlan.ledges(s, DepthPlan.route(s));
    }

    // 鎖の網: 天井から垂れる鎖の帳 (カーテン) と、段ごとに張られた横糸の輪
    private void chainNet() {
        for (int a = 0; a < 16; a++) {
            double ang = a * Math.PI / 8 + 0.1;
            int rx = (int) Math.round(Math.cos(ang) * 20);
            int rz = (int) Math.round(Math.sin(ang) * 20);
            column(rx, rz, 1, 40 + (a * 37) % 100, Blocks.CHAIN.defaultBlockState());
        }
        for (int lv : new int[] {30, 80, 130, 175}) {
            for (int rad : new int[] {7, 15}) {
                for (int a = 0; a < 24; a++) {
                    double ang = a * Math.PI / 12;
                    int rx = (int) Math.round(Math.cos(ang) * rad);
                    int rz = (int) Math.round(Math.sin(ang) * rad);
                    Direction.Axis axis = Math.abs(Math.sin(ang)) > Math.abs(Math.cos(ang)) ? Direction.Axis.X : Direction.Axis.Z;
                    put(rx, lv, rz, Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, axis));
                }
            }
        }
    }

    // 灯りを喰う広間: 八本の柱に灯りと黒い口 (スカルク)。灯りは柱の中に埋まる
    private void lampHall() {
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4 + 0.2;
            int px = (int) Math.round(Math.cos(ang) * 17);
            int pz = (int) Math.round(Math.sin(ang) * 17);
            column(px, pz, 0, DepthPlan.HEIGHT - 1, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            for (int ly = 12; ly < DepthPlan.HEIGHT; ly += 24) {
                put(px, ly, pz, Blocks.SEA_LANTERN.defaultBlockState());
                if (ly + 12 < DepthPlan.HEIGHT) {
                    put(px, ly + 12, pz, Blocks.SCULK.defaultBlockState());
                }
            }
        }
    }

    // 静かな井戸: 中心に泥煉瓦の井戸の筒。天井に口が開き、底に静かな水がたまる
    private void dryWell() {
        for (int ly = 0; ly < DepthPlan.HEIGHT; ly++) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 >= 16 && d2 <= 25) {
                        put(dx, ly, dz, pal.wall());
                    }
                }
            }
            if (ly % 30 == 15) {
                put(4, ly, 0, pal.lamp());
                put(-4, ly, 0, pal.lamp());
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
        // 底の水 (底の板の上に、静かな水が八ブロック)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= 9) {
                    put(dx, 195, dz, Blocks.PACKED_MUD.defaultBlockState());
                    for (int ly = 186; ly <= 194; ly++) {
                        put(dx, ly, dz, Blocks.WATER.defaultBlockState());
                    }
                }
            }
        }
    }

    // 星のない空洞: 空に浮く小さな水晶の島。まばらな光 (星)
    private void hollowStars() {
        for (int i = 0; i < 10; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rr = 14 + r.nextDouble() * 7;
            int ix = (int) Math.round(Math.cos(ang) * rr);
            int iz = (int) Math.round(Math.sin(ang) * rr);
            int top = 12 + r.nextInt(170);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz <= 4) {
                        put(ix + dx, top, iz + dz, Blocks.AMETHYST_BLOCK.defaultBlockState());
                    }
                }
            }
            put(ix, top, iz, Blocks.GLOWSTONE.defaultBlockState());
            put(ix, top - 1, iz, Blocks.AMETHYST_CLUSTER.defaultBlockState());
        }
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
}
