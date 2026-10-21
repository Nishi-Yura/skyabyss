package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormDeck.*;

import java.util.function.BiFunction;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Inscription;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Rest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

// 試練の甲板 (StormDeck) を建てる。鳥の体と背の広場・螺旋 (access) のあと、広間の影 (StormSilhouette) より前に StormBirdBuilder から呼ぶ。
// 甲板はどれも「空に浮かぶ島」: 床の下は先細りの石 (気嚢は羊毛の袋、柱は銅の柱、巣は黒い石の錐、台は羽の逆さ錐) で、
// 外へ開いた所なので kit.put で置く (掘った所として覚えない。覚えると seal が屋根をかけるため)。
// 色の約束: 風 = 白と水色 (気嚢・見張り台)、雷 = 黒い石と銅と橙 (柱・巣)。戸の上の色の線が、渡り場の中で行き先を言う
final class StormDeckBuilder {
    static final BlockState AIR = StormKit.AIR;
    static final BlockState WOOL = Blocks.WHITE_WOOL.defaultBlockState();
    static final BlockState WOOL_BLUE = Blocks.LIGHT_BLUE_WOOL.defaultBlockState();
    static final BlockState DARK = Blocks.DEEPSLATE_TILES.defaultBlockState();
    static final BlockState DARK2 = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    static final BlockState BLACK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    static final BlockState VENT_OFF = Blocks.GRAY_CONCRETE.defaultBlockState();
    static final BlockState VENT_ON = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
    static final BlockState CHISELED = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    static final BlockState RAIL = StormDeco.RAIL;

    private StormDeckBuilder() {
    }

    // build と同じ順の手順 (背を建てる仕事を tick に分けるとき用)。階を置かない間は何もしない
    static void steps(jp.revati.skyabyss.titan.BackBuildJobs.Steps s, StormKit kit) {
        java.util.function.Consumer<Runnable> deck = r -> {
            if (StormDeck.placed()) {
                r.run();
            }
        };
        s.then("deck access", () -> deck.accept(() -> access(kit)));
        s.then("deck hub", () -> deck.accept(() -> hub(kit)));
        s.then("deck bridge", () -> deck.accept(() -> bridge(kit)));
        s.then("deck yard", () -> deck.accept(() -> yard(kit)));
        s.then("deck s02", () -> deck.accept(() -> s02(kit)));
        s.then("deck s03", () -> deck.accept(() -> s03(kit)));
        s.then("deck s09", () -> deck.accept(() -> s09(kit)));
        s.then("deck s10", () -> deck.accept(() -> s10(kit)));
        jp.revati.skyabyss.titan.BackBuildJobs.Steps two = new jp.revati.skyabyss.titan.BackBuildJobs.Steps("storm deck 2");
        StormDeck2Builder.steps(two, kit);
        s.thenJob("deck 2", () -> new jp.revati.skyabyss.titan.BackBuildJobs.Job() {
            @Override
            public boolean step(long deadlineNanos) {
                return !StormDeck.placed() || two.step(deadlineNanos);
            }

            @Override
            public int placed() {
                return 0;
            }
        });
        s.then("deck signs", () -> deck.accept(() -> signs(kit)));
    }

    static void build(StormKit kit) {
        if (!StormDeck.placed()) {
            return;
        }
        access(kit);
        hub(kit);
        bridge(kit);
        yard(kit);
        s02(kit);
        s03(kit);
        s09(kit);
        s10(kit);
        StormDeck2Builder.build(kit);
        signs(kit);
    }

    // ---------------------------------------------------------------- 部品
    static void put(StormKit kit, BlockPos p, BlockState s) {
        kit.put(p.getX(), p.getY(), p.getZ(), s);
    }

    static void clearAbove(StormKit kit, int x, int z, int fy, int h) {
        for (int y = fy + 1; y <= fy + h; y++) {
            kit.put(x, y, z, AIR);
        }
    }

    // 島の床。edge = 縁の石、fill = 中の石 (x, z → 石)
    static void floor(StormKit kit, Island is, BlockState edge, BiFunction<Integer, Integer, BlockState> fill) {
        for (BlockPos p : is.floor()) {
            boolean border = !is.has(p.getX() + 1, p.getZ()) || !is.has(p.getX() - 1, p.getZ()) || !is.has(p.getX(), p.getZ() + 1) || !is.has(p.getX(), p.getZ() - 1);
            kit.put(p.getX(), p.getY(), p.getZ(), border ? edge : fill.apply(p.getX(), p.getZ()));
            clearAbove(kit, p.getX(), p.getZ(), p.getY(), 9);
        }
    }

    static BlockState windFloor(int x, int z) {
        return Math.floorMod(Math.abs(x) - z, 8) < 4 ? StormKit.FEATHER_WHITE : StormKit.QUARTZ;
    }

    static BlockState thunderFloor(int x, int z) {
        return (x + z & 1) == 0 ? DARK : DARK2;
    }

    // 床の下: 逆さの錐 (半径 r が深さ depth で尖る)。shape: 0 = 錐、1 = 丸い袋 (半球)。mat は (深さ, x, z) → 石
    static void hang(StormKit kit, int cx, int cz, int fy, int r, int depth, int shape, java.util.function.Function<int[], BlockState> mat) {
        for (int d = 1; d <= depth; d++) {
            double t = d / (double) depth;
            double rr = shape == 1 ? r * Math.sqrt(Math.max(0, 1 - t * t)) : r * (1 - t) + 0.5;
            int ri = (int) Math.ceil(rr);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz <= rr * rr + 0.5) {
                        kit.put(cx + dx, fy - d, cz + dz, mat.apply(new int[] {d, cx + dx, cz + dz}));
                    }
                }
            }
        }
    }

    static void hangSquare(StormKit kit, Island is, int depth, java.util.function.Function<int[], BlockState> mat) {
        // 四角の島の下: 逆さの角錐 (半分の幅が depth で 0 に近づく)
        for (int d = 1; d <= depth; d++) {
            int h = Math.max(0, (int) Math.round(is.size() * (1 - d / (double) (depth + 1))));
            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    kit.put(is.cx() + dx, is.y() - d, is.cz() + dz, mat.apply(new int[] {d, is.cx() + dx, is.cz() + dz}));
                }
            }
        }
    }

    static BlockState featherUnder(int[] a) {
        return a[0] % 3 == 0 ? StormKit.COPPER : (a[1] + a[2] & 1) == 0 ? StormKit.FEATHER : StormKit.FEATHER_WHITE;
    }

    static BlockState slateUnder(int[] a) {
        return a[0] % 4 == 0 ? StormKit.COPPER : (a[1] + a[2] & 1) == 0 ? BLACK : DARK;
    }

    static BlockState sacUnder(int[] a) {
        return a[0] % 3 == 0 ? WOOL_BLUE : WOOL;
    }

    // 昇り風の穴の床 (中心 3x3)。ring が光る色
    static void spot(StormKit kit, BlockPos c, boolean on) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean mid = dx == 0 && dz == 0;
                kit.put(c.getX() + dx, c.getY(), c.getZ() + dz, on ? (mid ? StormKit.LAMP : VENT_ON) : VENT_OFF);
            }
        }
    }

    static void rails(StormKit kit, int x1, int x2, int z1, int z2, int fy) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                kit.put(x, fy, z, StormKit.COPPER);
                kit.put(x, fy + 1, z, RAIL);
            }
        }
    }

    // 下へ下げる灯り: 銅の柱が from から to まで、下の 3 マスは海のランタン
    static void underLamp(StormKit kit, int x, int z, int from, int to) {
        for (int y = from; y >= to; y--) {
            kit.put(x, y, z, y <= to + 2 ? StormKit.LAMP : StormKit.COPPER);
        }
    }

    // 碑の台の代わり: 立てた碑 (床の 1 上)
    static void signs(StormKit kit) {
        for (Inscription i : SIGNS) {
            kit.standingTablet(i.pos(), i.key());
        }
    }

    // 灯りの柱 (床の 4 上まで)
    static void lamp(StormKit kit, int x, int fy, int z) {
        StormDeco.lampPost(kit, x, fy, z);
    }

    static void fire(StormKit kit, Rest r) {
        kit.campfire(r.campfire());
        // 渡り場だけ、焚き火の北と南に腰掛け (背もたれは外向き。ほかの島は縁が近いので置かない)
        if (r == REST_YARD) {
            BlockPos c = r.campfire();
            for (int dz : new int[] {-2, 2}) {
                kit.stair(c.getX(), c.getY(), c.getZ() + dz, dz < 0 ? Direction.NORTH : Direction.SOUTH, Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState());
            }
        }
    }

    // 戸の枠 (戸の外側の左右の銅の柱と、上の横木。戸は置かない。TrialRuntime.installGates が置く)
    static void gateFrame(StormKit kit, java.util.List<BlockPos> gate, boolean alongZ) {
        int minA = Integer.MAX_VALUE;
        int maxA = Integer.MIN_VALUE;
        int fix = alongZ ? gate.get(0).getX() : gate.get(0).getZ();
        for (BlockPos p : gate) {
            int a = alongZ ? p.getZ() : p.getX();
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
        }
        for (int a = minA - 1; a <= maxA + 1; a++) {
            for (int y = Y + 1; y <= Y + 7; y++) {
                boolean post = a == minA - 1 || a == maxA + 1;
                boolean lintel = y >= Y + 6;
                int x = alongZ ? fix : a;
                int z = alongZ ? a : fix;
                if (post || lintel) {
                    kit.put(x, y, z, y == Y + 1 || y == Y + 7 || y == Y + 4 ? StormKit.COPPER : StormKit.CALCITE);
                } else if (y > Y + 5) {
                    kit.put(x, y, z, AIR);
                }
            }
        }
        int lampA = (minA + maxA) / 2;
        kit.put(alongZ ? fix : lampA, Y + 8, alongZ ? lampA : fix, StormKit.LAMP);
    }

    // ---------------------------------------------------------------- 背の広場と螺旋 (尾の間から広場へ上る道。橋と甲板より前に置く)
    static void access(StormKit kit) {
        plaza(kit);
        spiral(kit);
    }

    // 背の広場の床 (x -25〜25、z 11〜36)。内側は羽の床、縁は手すり。南の縁の螺旋の戸の口だけあける。灯り・腰掛けは置かない
    private static void plaza(StormKit kit) {
        for (int x = -25; x <= 25; x++) {
            for (int z = 11; z <= 36; z++) {
                boolean inside = x >= PLAZA.x1() && x <= PLAZA.x2() && z >= PLAZA.z1() && z <= PLAZA.z2();
                if (inside) {
                    support(kit, x, z, Y, StormKit.Style.FEATHER);
                    kit.put(x, Y, z, plazaFloor(x, z));
                    clearAbove(kit, x, z, Y, 12);
                } else if (z == PLAZA.z2() + 1 && Math.abs(x - TURRET_X) <= 3) {
                    continue; // 螺旋の戸の口 (spiral が開ける)
                } else {
                    rail(kit, x, z, Y, StormKit.Style.FEATHER);
                }
            }
        }
    }

    // 広場の床: 羽の縞に、真ん中の風見の円盤 (銅と白の同心の四角) と十字
    private static BlockState plazaFloor(int x, int z) {
        int dx = Math.abs(x);
        int dz = Math.abs(z - 23);
        int r = Math.max(dx, dz);
        boolean border = dx == 24 || z == 12 || z == 35;
        if (border) {
            return StormKit.COPPER;
        }
        if (r <= 8) {
            return r == 8 || r == 4 ? StormKit.COPPER : r == 6 || r == 2 ? StormKit.FEATHER_WHITE : StormKit.QUARTZ;
        }
        if (dx <= 1 || dz <= 1) {
            return StormKit.FEATHER_WHITE;
        }
        return StormKit.floor(StormKit.Style.FEATHER, x, z);
    }

    // (x, z) の柱を、鳥の背から床の 1 下まで石で埋める (支えのない所は 10 までで止める)
    private static void support(StormKit kit, int x, int z, int floorY, StormKit.Style s) {
        int from = Math.max(StormBirdBuilder.top(x, z) + 1, floorY - 10);
        for (int y = from; y < floorY; y++) {
            kit.put(x, y, z, StormKit.wall(s, x, y, z));
        }
    }

    // 縁の手すり (床と同じ高さの石の上に低い壁)
    private static void rail(StormKit kit, int x, int z, int floorY, StormKit.Style s) {
        support(kit, x, z, floorY, s);
        kit.put(x, floorY, z, StormKit.wall(s, x, floorY, z));
        kit.put(x, floorY + 1, z, RAIL);
    }

    // 尾の間の北西の螺旋 (体の中)。箱を石で満たして階段を彫り、広場の南の戸を開ける (試練の甲板への道)
    private static void spiral(StormKit kit) {
        int cx = TURRET_X;
        int cz = TURRET_Z;
        int base = StormBackLayout.TAIL_HALL.floor();
        int top = Y + 4;
        // 箱を石で満たす (尾の間の空気も)。戸の穴は後で
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                for (int y = base + 1; y <= top; y++) {
                    kit.put(x, y, z, StormKit.wall(StormKit.Style.FEATHER, x, y, z));
                }
                // 箱の下の床は尾の間のまま
                support(kit, x, z, base, StormKit.Style.FEATHER);
            }
        }
        java.util.List<int[]> ring = StormBirdBuilder.ring5(cx, cz);
        int start = 2;
        for (int k = 0; k < TURRET_STEPS; k++) {
            int[] c = ring.get((start + k) % 16);
            int y = base + 1 + k;
            for (int yy = y + 1; yy <= y + 3; yy++) {
                kit.air(c[0], yy, c[1]);
            }
            kit.put(c[0], y, c[1], StormKit.floor(StormKit.Style.FEATHER, c[0], c[1]));
        }
        // 戸: 下は尾の間の床 (北の壁 cz-3 の 3 マス、高さ 3)。上は広場の床の高さ
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                kit.air(cx + dx, base + dy, TURRET_DOOR_Z);
                kit.air(cx + dx, Y + dy, TURRET_DOOR_Z);
            }
            kit.put(cx + dx, Y, TURRET_DOOR_Z, StormKit.floor(StormKit.Style.FEATHER, cx + dx, TURRET_DOOR_Z));
            kit.put(cx + dx, base, TURRET_DOOR_Z, StormKit.floor(StormKit.Style.FEATHER, cx + dx, TURRET_DOOR_Z));
        }
        // 戸の枠 (銅) と、戸の上の灯り
        for (int sx : new int[] {-2, 2}) {
            for (int y = base + 1; y <= base + 3; y++) {
                kit.put(cx + sx, y, TURRET_DOOR_Z, StormKit.COPPER);
            }
            for (int y = Y + 1; y <= Y + 3; y++) {
                kit.put(cx + sx, y, TURRET_DOOR_Z, StormKit.COPPER);
            }
        }
        kit.put(cx, base + 4, TURRET_DOOR_Z, StormKit.LAMP);
        kit.put(cx, Y + 4, TURRET_DOOR_Z, StormKit.LAMP);
        // 芯の灯り (3 段おき。その段の頭の高さに、芯の面が螺旋に面する所)
        for (int k = 0; k < TURRET_STEPS; k += 3) {
            int[] c = ring.get((start + k) % 16);
            if (Math.abs(c[0] - cx) == 2 && Math.abs(c[1] - cz) == 2) {
                continue;
            }
            int lx = Math.max(cx - 1, Math.min(cx + 1, c[0]));
            int lz = Math.max(cz - 1, Math.min(cz + 1, c[1]));
            kit.put(lx, base + 1 + k + 2, lz, StormKit.LAMP);
        }
        // 屋根の縁の銅の帯
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                if (Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3) {
                    kit.put(x, top, z, StormKit.COPPER);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 風の広間の板と、原型の三つの戸の枠
    static void hub(StormKit kit) {
    }

    // ---------------------------------------------------------------- 橋 (背の広場の南の縁 → 渡り場の北の縁)
    static void bridge(StormKit kit) {
        for (int z = BRIDGE_Z1; z <= BRIDGE_Z2; z++) {
            for (int x = BRIDGE_X1 - 1; x <= BRIDGE_X2 + 1; x++) {
                boolean walk = x >= BRIDGE_X1 && x <= BRIDGE_X2;
                if (walk) {
                    boolean border = x == BRIDGE_X1 || x == BRIDGE_X2;
                    kit.put(x, Y, z, border ? StormKit.COPPER : (x == (BRIDGE_X1 + BRIDGE_X2) / 2 && z % 4 == 3 ? StormKit.LAMP : windFloor(x, z)));
                    kit.put(x, Y - 1, z, StormKit.CALCITE);
                    clearAbove(kit, x, z, Y, 9);
                } else {
                    // 縁の手すり (広場の縁の石の高さ)
                    kit.put(x, Y, z, StormKit.COPPER);
                    kit.put(x, Y + 1, z, RAIL);
                    kit.put(x, Y - 1, z, StormKit.CALCITE);
                    clearAbove(kit, x, z, Y + 1, 8);
                }
            }
            // 橋の下: 二段の羽の梁
            for (int x = BRIDGE_X1 + 1; x <= BRIDGE_X2 - 1; x++) {
                kit.put(x, Y - 2, z, StormKit.FEATHER);
            }
        }
        // 橋の脚 (9 おき。鳥の背から)
        for (int z = BRIDGE_Z1 + 4; z <= BRIDGE_Z2; z += 9) {
            for (int sx : new int[] {BRIDGE_X1 - 1, BRIDGE_X2 + 1}) {
                for (int y = Math.min(StormBirdBuilder.top(sx, z) + 1, Y - 2); y < Y; y++) {
                    kit.put(sx, y, z, StormKit.COPPER);
                }
                lamp(kit, sx, Y + 1, z);
            }
        }
        // 広場の縁を開ける: 広場の南の縁の手すりと灯りの柱を、橋の幅だけ取り除く (z 36)
        for (int x = BRIDGE_X1 - 1; x <= BRIDGE_X2 + 1; x++) {
            for (int y = Y + 1; y <= Y + 6; y++) {
                if (x >= BRIDGE_X1 && x <= BRIDGE_X2) {
                    kit.put(x, y, BRIDGE_Z1 - 1, AIR);
                }
            }
        }
        for (int x = BRIDGE_X1; x <= BRIDGE_X2; x++) {
            kit.put(x, Y, BRIDGE_Z1 - 1, windFloor(x, BRIDGE_Z1 - 1));
        }
        // 向きの矢じるし (南へ): 橋の真ん中、12 おき
        for (int z = BRIDGE_Z1 + 8; z <= BRIDGE_Z2 - 6; z += 12) {
            StormDeco.chevron(kit, (BRIDGE_X1 + BRIDGE_X2) / 2, z, Direction.SOUTH, StormKit.COPPER, zz -> Y);
        }
    }

    // ---------------------------------------------------------------- 渡り場 (x -24〜24、z 64〜82)
    static void yard(StormKit kit) {
        for (int x = -25; x <= 25; x++) {
            for (int z = 63; z <= 83; z++) {
                boolean in = x >= -24 && x <= 24 && z >= 64 && z <= 82;
                if (in) {
                    boolean border = x == -24 || x == 24 || z == 64 || z == 82;
                    int dx = Math.abs(x);
                    int dz = Math.abs(z - 73);
                    boolean ring = Math.max(dx, dz) == 4 || (dx + dz <= 2);
                    kit.put(x, Y, z, border || ring ? StormKit.COPPER : windFloor(x, z));
                    clearAbove(kit, x, z, Y, 12);
                } else {
                    kit.put(x, Y, z, StormKit.COPPER);
                    kit.put(x, Y + 1, z, RAIL);
                    clearAbove(kit, x, z, Y + 1, 8);
                }
            }
        }
        // 橋の口 (北の縁の手すりを橋の幅だけあける)
        for (int x = BRIDGE_X1; x <= BRIDGE_X2; x++) {
            kit.put(x, Y + 1, 63, AIR);
            kit.put(x, Y, 63, StormKit.COPPER);
        }
        // 戸の口 (手すりを戸の幅だけあけ、床を渡す)
        for (java.util.List<BlockPos> g : java.util.List.of(S02_GATE, S03_GATE, S10_GATE, S09_GATE)) {
            for (BlockPos p : g) {
                kit.put(p.getX(), p.getY(), p.getZ(), AIR);
            }
        }
        gateFrame(kit, S02_GATE, true);
        gateFrame(kit, S03_GATE, true);
        gateFrame(kit, S10_GATE, false);
        gateFrame(kit, S09_GATE, false);
        // 下: 逆さの羽の錐 (島の下)
        // 羽軸の道 (床 147〜155、頭上 5) の上を通すので、深さは 3 まで (y 159 より上)
        for (int d = 1; d <= 3; d++) {
            int hx = 24 - d * 3;
            int hz = 9 - d * 2;
            if (hx < 0 || hz < 0) {
                break;
            }
            for (int x = -hx; x <= hx; x++) {
                for (int z = 73 - hz; z <= 73 + hz; z++) {
                    kit.put(x, Y - d, z, featherUnder(new int[] {d, x, z}));
                }
            }
        }
        // 下の灯り: 渡り場の下は羽軸の道と尾羽の背 (歩ける所)。空が見えないので、銅の柱に海のランタンを下げる (建築の監査の暗さ)
        for (int x : new int[] {-16, -9, 9, 16}) {
            for (int z : new int[] {67, 79}) {
                underLamp(kit, x, z, Y - 4, Math.max(StormBirdBuilder.top(x, z) + 3, Y - 15));
            }
        }
        for (int x : new int[] {-19, 19}) {
            underLamp(kit, x, 73, Y - 4, Math.max(StormBirdBuilder.top(x, 73) + 3, Y - 15));
        }
        // 灯りの柱: 四隅と、橋の口・戸のそば
        for (int x : new int[] {-24, 24}) {
            for (int z : new int[] {64, 82}) {
                lamp(kit, x, Y + 1, z);
            }
        }
        lamp(kit, 12, Y + 1, 63);
        lamp(kit, 12, Y + 1, 83);
        lamp(kit, -12, Y + 1, 83);
        lamp(kit, -25, Y + 1, 66);
        lamp(kit, 25, Y + 1, 66);
        lamp(kit, -25, Y + 1, 80);
        lamp(kit, 25, Y + 1, 80);
        // 色の線: 橋の口 (19, 64) から広場の中心 (0, 73) を通り、各戸へ。西と南西 = 水色、東と南東 = 金
        StormDeco.strip(kit, Y, StormDeco.WEST_LINE, StormDeco.WEST_PIP, 4, 0, new int[] {-2, 73}, new int[] {-24, 73});
        StormDeco.strip(kit, Y, StormDeco.EAST_LINE, StormDeco.EAST_PIP, 4, 0, new int[] {2, 73}, new int[] {24, 73});
        StormDeco.strip(kit, Y, StormDeco.WEST_LINE, StormDeco.WEST_PIP, 4, 0, new int[] {-6, 74}, new int[] {-15, 74}, new int[] {-15, 82});
        StormDeco.strip(kit, Y, StormDeco.EAST_LINE, StormDeco.EAST_PIP, 4, 0, new int[] {6, 74}, new int[] {15, 74}, new int[] {15, 82});
        StormDeco.strip(kit, Y, StormKit.COPPER, StormKit.COPPER, 99, 0, new int[] {19, 64}, new int[] {19, 70}, new int[] {5, 70});
        // 休み場・板・羽軸の柱
        fire(kit, REST_YARD);
        StormDeco.featherPillar(kit, -12, 69, Y, 6);
        StormDeco.featherPillar(kit, 12, 69, Y, 6);
    }

    // ---------------------------------------------------------------- S02 気嚢の滑空
    static void s02(StormKit kit) {
        // 戸から始めの台への道 (x -31〜-26)
        approach(kit, -31, -26, 71, 75, true);
        for (int i = 0; i < S02_ISLANDS.length; i++) {
            Island is = S02_ISLANDS[i];
            boolean last = i == S02_ISLANDS.length - 1;
            floor(kit, is, StormKit.COPPER, StormDeckBuilder::windFloor);
            if (last) {
                hangSquare(kit, is, 8, StormDeckBuilder::featherUnder);
            } else if (!is.round()) {
                hang(kit, is.cx(), is.cz(), is.y(), is.size() + 2, is.size() + 4, 1, StormDeckBuilder::sacUnder);
            } else {
                hang(kit, is.cx(), is.cz(), is.y(), is.size() + 2, is.size() + 3, 1, StormDeckBuilder::sacUnder);
            }
            if (!last) {
                // 昇り風の穴。始めの台のものはいつも膨らんでいる (見本)
                spot(kit, s02Spot(i), i == 0);
                // 縁の灯り (円の外周に 4 つ)
                for (int[] d : new int[][] {{is.size(), 0}, {-is.size(), 0}, {0, is.size()}, {0, -is.size()}}) {
                    if (is.has(is.cx() + d[0], is.cz() + d[1])) {
                        kit.put(is.cx() + d[0], is.y(), is.cz() + d[1], StormKit.LAMP);
                    }
                }
            }
        }
        // 始めの台・中ほどの台: 灯りの柱と休み場
        for (int i : new int[] {0, 3, 6}) {
            Island is = S02_ISLANDS[i];
            for (int[] c : new int[][] {{is.x1(), is.z1()}, {is.x1(), is.z2()}, {is.x2(), is.z1()}, {is.x2(), is.z2()}}) {
                lamp(kit, c[0], is.y(), c[1]);
            }
        }
        fire(kit, REST_S02A);
        fire(kit, REST_S02M);
        // 向こうの台: 石
        station(kit, S02_STONE, S02_ISLANDS[6]);
        // 水色の矢じるし: 始めの台から次の気嚢へ (進む向き = 西)
        for (int i = 0; i + 1 < S02_ISLANDS.length - 1; i++) {
            Island a = S02_ISLANDS[i];
            StormDeco.chevron(kit, a.cx() - a.size() + 1, a.cz(), Direction.WEST, StormDeco.WEST_LINE, zz -> a.y());
        }
    }

    // ---------------------------------------------------------------- S03 雷柱の渡り
    static void s03(StormKit kit) {
        approach(kit, 26, 31, 71, 75, false);
        for (int i = 0; i < S03_ISLANDS.length; i++) {
            Island is = S03_ISLANDS[i];
            boolean pillar = !is.round() && is.size() == 2;
            floor(kit, is, pillar ? DARK2 : StormKit.COPPER, StormDeckBuilder::thunderFloor);
            if (pillar) {
                // 5x5 の格子。真ん中は金の床
                kit.put(is.cx(), is.y(), is.cz(), GOLD);
                column(kit, is);
            } else {
                hangSquare(kit, is, 8, StormDeckBuilder::slateUnder);
            }
        }
        for (int i : new int[] {0, 4, 9}) {
            Island is = S03_ISLANDS[i];
            for (int[] c : new int[][] {{is.x1(), is.z1()}, {is.x1(), is.z2()}, {is.x2(), is.z1()}, {is.x2(), is.z2()}}) {
                lamp(kit, c[0], is.y(), c[1]);
            }
        }
        fire(kit, REST_S03A);
        fire(kit, REST_S03M);
        station(kit, S03_STONE, S03_ISLANDS[9]);
        // 金の矢じるし: 進む向き = 東
        for (int i : new int[] {0, 4}) {
            Island a = S03_ISLANDS[i];
            StormDeco.chevron(kit, a.cx() + a.size() - 1, a.cz(), Direction.EAST, StormDeco.EAST_LINE, zz -> a.y());
        }
    }

    // 銅の柱 (5x5。下へ 14、先細り。先に避雷針)
    static void column(StormKit kit, Island is) {
        for (int d = 1; d <= 14; d++) {
            int h = d <= 9 ? 2 : d <= 12 ? 1 : 0;
            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    BlockState s = d % 5 == 0 ? Blocks.WAXED_CHISELED_COPPER.defaultBlockState() : (dx == 0 && dz == 0 ? StormKit.COPPER_BLOCK : StormKit.COPPER);
                    kit.put(is.cx() + dx, is.y() - d, is.cz() + dz, s);
                }
            }
        }
        kit.put(is.cx(), is.y() - 15, is.cz(), Blocks.LIGHTNING_ROD.defaultBlockState().setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, Direction.DOWN));
    }

    // ---------------------------------------------------------------- S09 雷乞いの巣
    static void s09(StormKit kit) {
        approach(kit, 13, 17, 84, 90, false);
        floor(kit, S09_FOYER, StormKit.COPPER, StormDeckBuilder::thunderFloor);
        hangSquare(kit, S09_FOYER, 8, StormDeckBuilder::slateUnder);
        for (int[] c : new int[][] {{S09_FOYER.x1(), S09_FOYER.z1()}, {S09_FOYER.x1(), S09_FOYER.z2()}, {S09_FOYER.x2(), S09_FOYER.z1()},
                {S09_FOYER.x2(), S09_FOYER.z2()}}) {
            lamp(kit, c[0], Y, c[1]);
        }
        fire(kit, REST_S09A);
        // 橋 (控え → 巣)
        for (int x = S09_BRIDGE_X1; x <= S09_BRIDGE_X2 + 1; x++) {
            for (int z = S09_BRIDGE_Z1; z <= S09_BRIDGE_Z2; z++) {
                kit.put(x, Y, z, z == S09_BRIDGE_Z1 + 1 ? StormKit.COPPER : DARK2);
                clearAbove(kit, x, z, Y, 9);
            }
            for (int z : new int[] {S09_BRIDGE_Z1 - 1, S09_BRIDGE_Z2 + 1}) {
                kit.put(x, Y, z, StormKit.COPPER);
                kit.put(x, Y + 1, z, RAIL);
            }
        }
        // 丸い巣
        Island a = S09_ARENA;
        for (BlockPos p : a.floor()) {
            int dx = p.getX() - a.cx();
            int dz = p.getZ() - a.cz();
            double r = Math.sqrt(dx * dx + dz * dz);
            boolean rim = r > a.size() - 0.6;
            boolean ring = Math.abs(r - S09_ROD_R) < 0.6 || r < 2.2;
            kit.put(p.getX(), p.getY(), p.getZ(), rim || ring ? StormKit.COPPER : thunderFloor(p.getX(), p.getZ()));
            clearAbove(kit, p.getX(), p.getZ(), p.getY(), 14);
        }
        // 縁の手すり (半径 13.5。橋と出口の橋のところはあける)
        for (int dx = -a.size() - 1; dx <= a.size() + 1; dx++) {
            for (int dz = -a.size() - 1; dz <= a.size() + 1; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = a.cx() + dx;
                int z = a.cz() + dz;
                if (r > a.size() - 0.3 && r < a.size() + 0.9 && !a.has(x, z)) {
                    boolean bridgeW = x <= S09_BRIDGE_X2 + 2 && z >= S09_BRIDGE_Z1 - 1 && z <= S09_BRIDGE_Z2 + 1;
                    boolean exitE = x >= a.cx() + a.size() - 1 && z >= 102 && z <= 106;
                    if (!bridgeW && !exitE) {
                        kit.put(x, Y, z, StormKit.COPPER);
                        kit.put(x, Y + 1, z, Blocks.BLACKSTONE_WALL.defaultBlockState());
                    }
                }
            }
        }
        hang(kit, a.cx(), a.cz(), Y, a.size(), 13, 0, StormDeckBuilder::slateUnder);
        // 錐の先に灯り (下の尾羽の背が暗くならないように)
        for (int[] d : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            kit.put(a.cx() + d[0], Y - 12, a.cz() + d[1], StormKit.LAMP);
        }
        kit.put(a.cx(), Y - 13, a.cz(), StormKit.LAMP);
        kit.put(a.cx(), Y - 14, a.cz(), StormKit.LAMP);
        // 避雷の柱 6 本 (根元 = 床の 1 上)
        for (BlockPos base : s09Rods()) {
            StormBirdBuilder.rod(kit, base, 0);
        }
        // 巣の真ん中: 銅の祭壇 (低い台)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.put(a.cx() + dx, Y + 1, a.cz() + dz, dx == 0 && dz == 0 ? StormKit.LAMP : StormKit.COPPER);
            }
        }
        // 縁の灯り: 60 度おき (柱の間)
        for (int i = 0; i < 6; i++) {
            double ang = Math.toRadians(30 + 60 * i);
            int x = a.cx() + (int) Math.round(Math.cos(ang) * (a.size() - 0.5));
            int z = a.cz() + (int) Math.round(Math.sin(ang) * (a.size() - 0.5));
            lamp(kit, x, Y, z);
        }
        // 出口の橋と石の島 (戸は installGates が置く)
        for (int x = S09_EXIT_X1; x <= S09_EXIT_X2; x++) {
            for (int z = 102; z <= 106; z++) {
                kit.put(x, Y, z, DARK2);
                clearAbove(kit, x, z, Y, 9);
                if (z == 102 || z == 106) {
                    kit.put(x, Y + 1, z, RAIL);
                }
            }
        }
        floor(kit, S09_EXIT, StormKit.COPPER, StormDeckBuilder::thunderFloor);
        hangSquare(kit, S09_EXIT, 6, StormDeckBuilder::slateUnder);
        station(kit, S09_STONE, S09_EXIT);
        lamp(kit, S09_EXIT.x1(), Y, S09_EXIT.z1());
        lamp(kit, S09_EXIT.x1(), Y, S09_EXIT.z2());
        lamp(kit, S09_EXIT.x2(), Y, S09_EXIT.z1());
        lamp(kit, S09_EXIT.x2(), Y, S09_EXIT.z2());
    }

    // ---------------------------------------------------------------- S10 羽守の見張り台
    static void s10(StormKit kit) {
        approach(kit, -17, -13, 84, 90, true);
        for (int i = 0; i < S10_PLATFORMS.length; i++) {
            Island is = S10_PLATFORMS[i];
            floor(kit, is, StormKit.COPPER, StormDeckBuilder::windFloor);
            hangSquare(kit, is, 8, StormDeckBuilder::featherUnder);
            // 北と南の手すり (東と西は開けて、風の穴から飛べる)
            for (int x = is.x1(); x <= is.x2(); x++) {
                for (int z : new int[] {is.z1(), is.z2()}) {
                    // 始めの台の北の縁は、戸の道の幅だけ手すりをあける
                    if (i == 0 && z == is.z1() && x >= -18 && x <= -12) {
                        continue;
                    }
                    kit.put(x, Y + 1, z, RAIL);
                }
            }
            // 風の穴 (台の西の縁から 3 内側) と見張りの柱 (四隅。盾になる)
            if (i < S10_PLATFORMS.length - 1) {
                spot(kit, s10Vent(i), false);
            }
            for (int[] c : new int[][] {{is.x1() + 2, is.z1() + 2}, {is.x1() + 2, is.z2() - 2}, {is.x2() - 2, is.z1() + 2}, {is.x2() - 2, is.z2() - 2}}) {
                for (int y = Y + 1; y <= Y + 3; y++) {
                    kit.put(c[0], y, c[1], y == Y + 1 ? StormKit.COPPER : StormKit.CALCITE);
                }
                kit.put(c[0], Y + 4, c[1], StormKit.LAMP);
            }
            // 真ん中の見張りの灯 (銅の台と灯り)
            kit.put(is.cx() + 2, Y + 1, is.cz(), StormKit.COPPER);
            kit.put(is.cx() + 2, Y + 2, is.cz(), StormKit.LAMP);
        }
        fire(kit, REST_S10A);
        fire(kit, REST_S10M);
        station(kit, S10_STONE, S10_PLATFORMS[4]);
    }

    // ---------------------------------------------------------------- 共通
    // 戸の外の道 (幅 5。長い辺の両側に手すり)。wind = 風の色の床、そうでなければ雷の色の床
    static void approach(StormKit kit, int x1, int x2, int z1, int z2, boolean wind) {
        boolean alongX = x2 - x1 > z2 - z1;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean border = alongX ? z == z1 || z == z2 : x == x1 || x == x2;
                kit.put(x, Y, z, border ? StormKit.COPPER : (wind ? windFloor(x, z) : thunderFloor(x, z)));
                clearAbove(kit, x, z, Y, 9);
            }
        }
        // 手すり (長い辺の外側の一列)
        for (int p = alongX ? x1 : z1; p <= (alongX ? x2 : z2); p++) {
            for (int side : new int[] {-1, 1}) {
                int x = alongX ? p : (side < 0 ? x1 - 1 : x2 + 1);
                int z = alongX ? (side < 0 ? z1 - 1 : z2 + 1) : p;
                kit.put(x, Y, z, StormKit.COPPER);
                kit.put(x, Y + 1, z, RAIL);
            }
        }
        // 下: 羽の梁
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                kit.put(x, Y - 1, z, StormKit.CALCITE);
            }
        }
    }

    // 石の台 (石のある島): 床の石 (チャイムの石) とその台
    static void station(StormKit kit, BlockPos stone, Island is) {
        kit.put(stone.getX(), stone.getY() - 1, stone.getZ(), CHISELED);
        kit.put(stone.getX(), stone.getY(), stone.getZ(), jp.revati.skyabyss.trial.TrialGoal.mark());
        kit.put(stone.getX(), stone.getY() + 1, stone.getZ(), AIR);
    }
}
