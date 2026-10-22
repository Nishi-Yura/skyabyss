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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

// 区画の形 (docs/design/36 の 9)。区画の番号と決まり (DepthPlan.plan) から、置く block を順に並べる。
//   置く順 = 壁 → 天井 → 足場の道 → 脇の岩棚 → 灯り → 型の飾り → 珍しい部屋 → 灯籠・鎖・裂け目 (後のものが上書き)。
//   同じ区画なら、いつも同じ並び (自己テストが見る)。ワールドには触らない (DepthBuilder が置く)。
//   座標: Put の x・z は世界の値、y も世界の値 (区画の上 = DepthPlan.TOP_Y)。
public final class DepthLayout {
    private static final int SALT = 0x2F3D;

    // 置く一つの block (loot があれば箱の中身を入れる)
    public record Put(int x, int y, int z, BlockState state, @Nullable ResourceKey<LootTable> loot) {
    }

    // 型ごとの材料
    public record Palette(BlockState wall, BlockState route, BlockState ledge, BlockState accent, BlockState lamp, BlockState dark, BlockState roof) {
    }

    // 区画の結果。route と ledges は足場 (DepthPlan)。lamps は灯り (LIGHTS_OUT と FOG_HIDE が見る)
    public record Result(Section section, Palette palette, List<Put> puts, List<Platform> route, List<Platform> ledges, List<BlockPos> lamps,
            BlockPos entryFeet, BlockPos restFeet, BlockPos lantern, BlockPos exit, BlockPos hole, BlockPos roomCenter) {
        // 置く一覧を捨てた形 (建てたあとに持つ。場所の決まりだけ残す)
        public Result stripped() {
            return new Result(section, palette, List.of(), route, ledges, lamps, entryFeet, restFeet, lantern, exit, hole, roomCenter);
        }
    }

    private final Section s;
    private final int cx;
    private final List<Put> puts = new ArrayList<>();
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

    // 区画の型ごとの材料
    public static Palette palette(Kind k) {
        return switch (k) {
            case INVERTED_FOREST -> new Palette(Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(),
                    Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.MANGROVE_ROOTS.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState());
            case FLOATING_GRAVES -> new Palette(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
                    Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            case PULSE_CAVE -> new Palette(Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(),
                    Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_WART_BLOCK.defaultBlockState(),
                    Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_WART_BLOCK.defaultBlockState());
            case MIRROR_DARK -> new Palette(Blocks.BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState(),
                    Blocks.SEA_LANTERN.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState());
            case CHAIN_NET -> new Palette(Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
                    Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.CHAIN.defaultBlockState(),
                    Blocks.LANTERN.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState());
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
        // 型の飾りは足場より先に置く (足場の板を飾りで切らないため。足場は後から上書きする)
        decorate(route);
        for (Platform p : route) {
            platform(p, pal.route());
        }
        for (Platform p : ledges) {
            platform(p, pal.ledge());
        }
        List<BlockPos> lamps = lamps(route);
        Platform mid = route.get(route.size() / 2);
        BlockPos room = rare(mid);
        // 裂け目 (最後の広い足場の真ん中を抜く。縁は飾り)
        Platform last = route.get(route.size() - 1);
        BlockPos hole = at(last.dx(), last.dz(), last.ly());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                puts.add(new Put(hole.getX() + dx, hole.getY(), hole.getZ() + dz, Blocks.AIR.defaultBlockState(), null));
            }
        }
        ring(last, 2.0, 2.6, pal.accent());
        // 灯籠 (道の真ん中の足場に一つ。点けると、そこが再開の所) と、帰りの鎖 (灯籠の横)
        BlockPos entryFeet = feet(route.get(0), 0, 0);
        BlockPos restFeet = feet(mid, 0, 0);
        BlockPos lantern = feet(mid, 1, 0);
        BlockPos exit = feet(mid, -1, 0);
        put(lantern, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, false));
        put(exit, Blocks.CHAIN.defaultBlockState());
        return new Result(s, pal, List.copyOf(puts), route, ledges, lamps, entryFeet, restFeet, lantern, exit, hole, room);
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

    // 円筒の壁 (半径 ROOM_R-1 〜 ROOM_R の一枚)
    private void walls() {
        int rr = DepthPlan.ROOM_R;
        for (int ly = 0; ly < DepthPlan.HEIGHT; ly++) {
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 >= (rr - 1) * (rr - 1) && d2 < rr * rr) {
                        put(dx, ly, dz, wallBlock(dx, dz, ly));
                    }
                }
            }
        }
    }

    // 壁の材料 (型ごとの模様: 鏡の闇は帯の鏡、灰を使わない代わりに、星の空洞は星)
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

    // 灯り: 壁の内側に、段ごとに。SPARSE_LIGHT は半分、LAMP_HALL は二倍。HOLLOW_STARS は少なく
    private List<BlockPos> lamps(List<Platform> route) {
        List<BlockPos> out = new ArrayList<>();
        int every = s.kind() == Kind.LAMP_HALL ? 8 : 16;
        int angles = s.kind() == Kind.LAMP_HALL ? 8 : 4;
        if (s.has(Modifier.SPARSE_LIGHT)) {
            every *= 2;
        }
        if (s.kind() == Kind.HOLLOW_STARS) {
            every *= 2;
        }
        for (int ly = 12; ly < DepthPlan.HEIGHT - 6; ly += every) {
            for (int a = 0; a < angles; a++) {
                double ang = a * Math.PI * 2 / angles + (ly / every) * 0.3;
                int x = (int) Math.round(Math.cos(ang) * 22.5);
                int z = (int) Math.round(Math.sin(ang) * 22.5);
                BlockPos b = new BlockPos(cx + x, DepthPlan.TOP_Y - ly, z);
                put(b, pal.lamp());
                out.add(b);
            }
        }
        return out;
    }

    // 型の飾り (足場の上の墓・壁の脈・鎖・井戸・根・柱)
    private void decorate(List<Platform> route) {
        switch (s.kind()) {
            case INVERTED_FOREST -> {
                // 天井から垂れる幹 (根の柱)
                for (int a = 0; a < 12; a++) {
                    double ang = a * Math.PI / 6 + 0.1;
                    int len = 6 + (a * 7) % 9;
                    for (int ly = 1; ly <= len; ly++) {
                        put((int) Math.round(Math.cos(ang) * 18), ly, (int) Math.round(Math.sin(ang) * 18), pal.accent());
                    }
                }
            }
            case FLOATING_GRAVES -> {
                for (Platform p : route) {
                    if (p.k() % 4 == 2 && !p.landing()) {
                        BlockPos b = at(p.dx() + 1, p.dz(), p.ly());
                        put(b.above(), pal.accent());
                        put(b.above(2), pal.accent());
                    }
                }
            }
            case CHAIN_NET -> {
                for (int ly = 8; ly < DepthPlan.HEIGHT; ly += 16) {
                    for (int a = 0; a < 8; a++) {
                        double ang = a * Math.PI / 4;
                        for (int t = 3; t <= 23; t++) {
                            put((int) Math.round(Math.cos(ang) * t), ly, (int) Math.round(Math.sin(ang) * t), pal.accent());
                        }
                    }
                }
            }
            case DRY_WELL -> {
                // 中心の井戸の縁 (壁と同じ材料の筒)
                for (int ly = 0; ly < DepthPlan.HEIGHT; ly++) {
                    for (int dx = -5; dx <= 5; dx++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            int d2 = dx * dx + dz * dz;
                            if (d2 >= 16 && d2 <= 25) {
                                put(dx, ly, dz, pal.wall());
                            }
                        }
                    }
                }
            }
            case LAMP_HALL -> {
                // 灯りを吸う口 (床の縁に散る)
                for (Platform p : route) {
                    if (p.k() % 3 == 1) {
                        BlockPos b = at(p.dx() - 2, p.dz(), p.ly());
                        put(b, pal.accent());
                    }
                }
            }
            case PULSE_CAVE -> {
                // 足場の脇の脈 (赤い塊)
                for (Platform p : route) {
                    if (p.k() % 5 == 3 && !p.landing()) {
                        BlockPos b = at(p.dx(), p.dz() - 2, p.ly());
                        put(b, pal.accent());
                    }
                }
            }
            case MIRROR_DARK -> {
                // 足場の脇に鏡の柱
                for (Platform p : route) {
                    if (p.k() % 6 == 4 && !p.landing()) {
                        BlockPos b = at(p.dx() - 2, p.dz(), p.ly());
                        put(b.above(), pal.accent());
                    }
                }
            }
            case HOLLOW_STARS -> {
                // 天井の星 (疎らに)
                for (int dx = -22; dx <= 22; dx += 4) {
                    for (int dz = -22; dz <= 22; dz += 4) {
                        if (r.nextInt(3) == 0 && dx * dx + dz * dz < 20 * 20) {
                            put(dx, 0, dz, pal.accent());
                        }
                    }
                }
            }
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
