package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.ParkourCourses.Island;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 西の翼「裂け目の回廊」(版 18 で作り直し): 谷を渡る長いアスレチック。
//   西の前室 (休み場) → 肋骨の谷 → 第一の休み場 → 竪穴 → 第二の休み場 → 崩れ橋 → 共鳴石の岩棚
// 谷はどれも床が抜け落ち、はるか下に溶岩が光る。足場は筋の通った一本の道 (ParkourCourses): 飛び石 → 島 (休み場を兼ねる) → 骨の橋。
// 落ちると直前の休み場 (島を含む) へ戻される (TitanBackTrials)。最後の岩棚から、鼓動の間へ下りる抜け道が開く。
// 材質は「肋骨」(ZonePalette.RIB): 熱で黒ずんだ磨き黒石・骨・玄武岩。谷の天井には肋骨の梁が渡る
final class WestWingBuilder {
    private WestWingBuilder() {
    }

    static void build(BuildKit kit) {
        kit.palette(ZonePalette.RIB);
        kit.room(W0);
        kit.roomSolid(WA);
        kit.room(W1);
        kit.roomSolid(WB);
        kit.room(W2);
        kit.roomSolid(WC);
        // 部屋どうしをつなぐ戸口 (壁の厚さ 1。幅 5、高さ 6)
        kit.doorway(WA.x2() + 1, WA.x2() + 1, -38, -34, FLOOR, 6);                    // 西の前室 → 肋骨の谷
        kit.doorway(-52, -48, WA.z1() - 1, WA.z1() - 1, W1.floorY(), 6);               // 肋骨の谷 → 第一の休み場
        kit.doorway(-49, -45, WB.z2() + 1, WB.z2() + 1, W1.floorY(), 6);               // 第一の休み場 → 竪穴
        kit.doorway(-46, -42, WB.z1() - 1, WB.z1() - 1, W2.floorY(), 6);               // 竪穴 → 第二の休み場
        kit.room(W2_CONN1);                                                              // 第二の休み場 → 崩れ橋
        kit.room(W2_CONN2);
        for (Box conn : List.of(W2_CONN1, W2_CONN2)) {
            kit.wallLamps(conn, 3, conn.floorY() + 4);
            kit.lantern(conn.x1() + 1, conn.floorY() + 1, conn.z1());
        }

        // 谷: 床を抜き、はるか下を溶岩にする
        for (Box pit : MAIN_PITS) {
            pit(kit, pit);
        }
        // 前の谷 (版 31。周回だけ。trial/LongWalk): 休み場 WP0 → 前の谷 WP → 通路 WPC → W0 の南の壁の戸 (WP_GATE)。
        // 別の乱数の道具で建てる (今までの部屋の壁や飾りの乱数の並びを変えない = 見かけを変えない)
        BuildKit pre = preKit(kit);
        pre.room(WPC);
        pre.room(WP0);
        pre.roomSolid(WP);
        pre.doorway(-22, -18, WP.z2() + 1, WP.z2() + 1, FLOOR, 6);                    // 休み場 WP0 → 前の谷
        pre.doorway(-33, -29, WP.z1() - 1, WP.z1() - 1, FLOOR, 6);                    // 前の谷 → 通路
        pre.wallLamps(WPC, 4, FLOOR + 4);
        pre.lantern(WPC.x1() + 1, FLOOR + 1, WPC.z2());
        pit(pre, WP);
        // 谷の入口と出口の岩棚 (道の両端)
        for (CourseGenerator.Spec spec : ParkourCourses.ALL) {
            ledge(kit, spec.start());
            ledge(kit, spec.end());
        }
        // 戸の足もとの石までつなぐ (共鳴石の岩棚。谷の東の縁の列)
        kit.fill(WC.x2() - 5, WEST_LEDGE_Y, -54, WC.x2(), WEST_LEDGE_Y, -50, kit.pal().floor());

        decorate(kit);
        ribWindows(kit);
        preDecorate(pre);
        for (int i = 0; i < ParkourCourses.ISLANDS.size(); i++) {
            for (Island isl : ParkourCourses.ISLANDS.get(i)) {
                island(kit, isl);
            }
        }
        placeCourses(kit, ParkourCourses.forSeed(0));
        pathLights(kit);

        // 前の谷の出口の戸 (W0 の南の壁)。W0 の内側の柱・灯りが戸の前に立っていたら除く
        for (BlockPos p : WP_GATE) {
            kit.set(p.getX(), p.getY(), W0.z2(), BuildKit.AIR);
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        kit.palette(ZonePalette.DEEP);
    }

    // 今までの三つの谷 (乱数を使う飾りはこの三つだけ。前の谷は preKit で)
    private static final List<Box> MAIN_PITS = List.of(WA, WB, WC);

    private static BuildKit preKit(BuildKit kit) {
        BuildKit pre = new BuildKit(kit.level(), net.minecraft.util.RandomSource.create(20261031L));
        pre.palette(ZonePalette.RIB);
        return pre;
    }

    // 前の谷の休み場・谷の飾り・灯り (版 31)
    private static void preDecorate(BuildKit kit) {
        kit.ribs(WP, 6, false);
        kit.pilasters(WP, 5);
        for (int i = 0; i < 24; i++) {
            boolean alongX = kit.random.nextBoolean();
            int x = alongX ? WP.x1() + kit.random.nextInt(WP.x2() - WP.x1() + 1) : (kit.random.nextBoolean() ? WP.x1() : WP.x2());
            int z = alongX ? (kit.random.nextBoolean() ? WP.z1() : WP.z2()) : WP.z1() + kit.random.nextInt(WP.z2() - WP.z1() + 1);
            if (i % 3 == 0) {
                kit.hangingLantern(x, z, WP.ceilingY(), 3);
            } else {
                kit.stalactite(x, WP.ceilingY(), z, 1 + kit.random.nextInt(3));
            }
        }
        kit.wallLamps(WP, 5, FLOOR + 7);
        kit.restPoint(WP0);
        kit.wallLamps(WP0, 3, WP0.floorY() + 4);
        kit.pilasters(WP0, 3);
        kit.moss(WP0, 0.05f);
        kit.lantern(-22, FLOOR + 1, WP.z2() + 1);
        kit.lantern(-29, FLOOR + 1, WP.z1() - 1);
    }

    private static void pit(BuildKit kit, Box pit) {
        for (int x = pit.x1(); x <= pit.x2(); x++) {
            for (int z = pit.z1(); z <= pit.z2(); z++) {
                for (int y = PIT_LAVA_Y + 1; y <= FLOOR; y++) {
                    kit.set(x, y, z, BuildKit.AIR);
                }
                kit.set(x, PIT_LAVA_Y, z, Blocks.LAVA.defaultBlockState());
                kit.set(x, PIT_LAVA_Y - 1, z, Blocks.BLACKSTONE.defaultBlockState());
            }
        }
        // 谷の壁の下のほうは、熱で黒ずんだ石にする
        for (int y = PIT_LAVA_Y - 1; y <= FLOOR; y++) {
            for (int x = pit.x1() - 1; x <= pit.x2() + 1; x++) {
                blacken(kit, x, y, pit.z1() - 1);
                blacken(kit, x, y, pit.z2() + 1);
            }
            for (int z = pit.z1() - 1; z <= pit.z2() + 1; z++) {
                blacken(kit, pit.x1() - 1, y, z);
                blacken(kit, pit.x2() + 1, y, z);
            }
        }
    }

    // 肋骨の窓: 谷の西の壁 (鯨の左のわき腹) で、外までの殻が薄い所 (7 ブロック以内) を、肋骨の間の裂け目にする。
    // 4 マスごとに骨の柱 (肋骨) を残し、その間は外の面までくり抜き、谷の壁の面にガラスをはめる。谷から雲と空が見える
    // (外へは出られず、窓の奥に立つ所もない)
    static final int WINDOW_Y1 = FLOOR + 4;
    static final int WINDOW_Y2 = FLOOR + 13;
    static final int WINDOW_DEPTH = 7;

    static void ribWindows(BuildKit kit) {
        BlockState rib = Blocks.BONE_BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (Box pit : MAIN_PITS) {
            int wall = pit.x1() - 1;
            for (int z = pit.z1(); z <= pit.z2(); z++) {
                for (int y = WINDOW_Y1; y <= WINDOW_Y2; y++) {
                    int outside = Integer.MIN_VALUE;
                    for (int x = wall - 1; x >= wall - WINDOW_DEPTH; x--) {
                        if (WhaleShape.at(x, y, z) == null) {
                            outside = x;
                            break;
                        }
                    }
                    if (outside == Integer.MIN_VALUE) {
                        continue;
                    }
                    boolean isRib = Math.floorMod(z - pit.z1(), 5) == 0 || y == WINDOW_Y1 || y == WINDOW_Y2;
                    for (int x = wall; x > outside; x--) {
                        if (isRib) {
                            kit.set(x, y, z, rib);
                        } else {
                            kit.set(x, y, z, x == wall ? Blocks.GLASS.defaultBlockState() : BuildKit.AIR);
                        }
                    }
                }
            }
        }
    }

    private static void blacken(BuildKit kit, int x, int y, int z) {
        if (!kit.get(x, y, z).isAir()) {
            kit.set(x, y, z, kit.random.nextFloat() < 0.5f ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
        }
    }

    private static void ledge(BuildKit kit, List<int[]> cells) {
        for (int[] c : cells) {
            kit.set(c[0], c[1], c[2], kit.pal().floor());
        }
    }

    // 島: 3×3 の石の台 (休み場)。下は先の細い岩の塊になって谷へ垂れ、四隅に骨の柱と灯りが立つ
    private static void island(BuildKit kit, Island i) {
        BlockState brick = kit.pal().wall();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean center = dx == 0 && dz == 0;
                kit.set(i.cx() + dx, i.y(), i.cz() + dz, center ? kit.pal().trim() : brick);
                kit.set(i.cx() + dx, i.y() - 1, i.cz() + dz, brick);
                // 四隅の上は空ける (頭上の飾りを消す)
                for (int y = i.y() + 1; y <= i.y() + 6; y++) {
                    kit.set(i.cx() + dx, y, i.cz() + dz, BuildKit.AIR);
                }
            }
        }
        for (int d = 0; d < 4; d++) {
            kit.set(i.cx() + Direction.from2DDataValue(d).getStepX(), i.y() - 2, i.cz() + Direction.from2DDataValue(d).getStepZ(), brick);
        }
        for (int y = i.y() - 2; y >= i.y() - 5; y--) {
            kit.set(i.cx(), y, i.cz(), y >= i.y() - 3 ? brick : Blocks.BLACKSTONE.defaultBlockState());
        }
        // 四隅の柱 (骨 2 つ) と灯り
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                int x = i.cx() + dx;
                int z = i.cz() + dz;
                kit.set(x, i.y() + 1, z, kit.pal().pillarAlong(Direction.Axis.Y));
                kit.set(x, i.y() + 2, z, kit.pal().pillarAlong(Direction.Axis.Y));
                kit.set(x, i.y() + 3, z, kit.pal().lantern());
            }
        }
    }

    // 足場を置く。足場の頭上は、天井まで空ける (飾りの鎖・鍾乳石・ランタンが途中で切れて宙に残らない)
    static void placeCourses(BuildKit kit, List<List<int[]>> courses) {
        for (List<int[]> course : courses) {
            for (int[] s : course) {
                for (int y = s[1] + 1; y <= FLOOR + 15; y++) {
                    BlockState b = kit.get(s[0], y, s[2]);
                    if (y <= s[1] + 5 || b.is(Blocks.CHAIN) || b.is(Blocks.POINTED_DRIPSTONE) || b.getBlock() instanceof net.minecraft.world.level.block.LanternBlock) {
                        kit.set(s[0], y, s[2], BuildKit.AIR);
                    }
                }
            }
        }
        for (List<int[]> course : courses) {
            for (int[] s : course) {
                kit.set(s[0], s[1], s[2], s[3] == 1 ? ZonePalette.RIB.glow() : ModBlocks.CRUMBLING_BONE.get().defaultBlockState());
            }
        }
    }

    // 古い足場を取り除く (試練を元に戻すときに、新しい足場に置き換えるため)
    static void clearCourses(BuildKit kit, List<List<int[]>> courses) {
        for (List<int[]> course : courses) {
            for (int[] s : course) {
                kit.set(s[0], s[1], s[2], BuildKit.AIR);
            }
        }
    }

    // 道の灯り: 骨の橋の脇に、天井から鎖で吊るした灯りを 4 つの足場ごとに (橋の真上は頭上を空けてあるので、2 マス脇に)
    private static void pathLights(BuildKit kit) {
        List<List<int[]>> courses = ParkourCourses.forSeed(0);
        int ceiling = FLOOR + 16;
        int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int pit = 0; pit < courses.size(); pit++) {
            int n = 0;
            for (int[] s : courses.get(pit)) {
                if (s[3] != 0 || n++ % 4 != 2) {
                    continue;
                }
                for (int[] o : offsets) {
                    int x = s[0] + o[0];
                    int z = s[2] + o[1];
                    if (freeColumn(x, z) && !kit.get(x, ceiling, z).isAir() && kit.get(x, ceiling - 1, z).isAir()) {
                        kit.hangingLantern(x, z, ceiling, Math.max(1, ceiling - s[1] - 5));
                        break;
                    }
                }
            }
        }
    }

    // その柱 (x, z) の上下に、足場も島もない (隣の柱にも足場がない) か
    private static boolean freeColumn(int x, int z) {
        for (int pit = 0; pit < ParkourCourses.ALL.size(); pit++) {
            for (int[] b : ParkourCourses.allBlocks(pit)) {
                if (Math.abs(b[0] - x) <= 1 && Math.abs(b[2] - z) <= 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void decorate(BuildKit kit) {
        // 休み場
        kit.restPoint(W0);
        kit.restPoint(W1);
        kit.restPoint(W2);
        for (Box rest : List.of(W0, W1, W2)) {
            kit.wallLamps(rest, 3, rest.floorY() + 4);
        }
        kit.pilasters(W0, 3);
        kit.moss(W0, 0.06f);
        // 谷の天井に、肋骨の梁 (谷ごとに。足場の高さより十分上なので頭はぶつからない)
        for (Box pit : MAIN_PITS) {
            kit.ribs(pit, 6, true);
            kit.pilasters(pit, 5);
        }
        // 谷の天井から垂れる鍾乳石と鎖。壁ぎわ 1 マスだけに飾る (足場の上の柱は、足場を置くときに天井まで消える)
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (Box pit : MAIN_PITS) {
            for (int i = 0; i < 30; i++) {
                boolean alongX = kit.random.nextBoolean();
                int x = alongX ? pit.x1() + kit.random.nextInt(pit.x2() - pit.x1() + 1) : (kit.random.nextBoolean() ? pit.x1() : pit.x2());
                int z = alongX ? (kit.random.nextBoolean() ? pit.z1() : pit.z2()) : pit.z1() + kit.random.nextInt(pit.z2() - pit.z1() + 1);
                if (!used.add(((long) x << 32) ^ (z & 0xffffffffL))) {
                    continue;
                }
                if (i % 3 == 0) {
                    kit.hangingLantern(x, z, pit.ceilingY(), 3);
                } else {
                    kit.stalactite(x, pit.ceilingY(), z, 1 + kit.random.nextInt(3));
                }
            }
            // 谷の壁に埋め込んだ灯り (足場が見えるように)
            kit.wallLamps(pit, 5, FLOOR + 7);
        }
        // 岩棚の灯り
        kit.lantern(WA.x2(), FLOOR + 1, -38);
        kit.lantern(-52, W1.floorY() + 1, WA.z1());
        kit.lantern(-49, W1.floorY() + 1, WB.z2());
        kit.lantern(-46, W2.floorY() + 1, WB.z1());
        kit.lantern(-31, W2.floorY() + 1, WC.z1());
        kit.lantern(WC.x2(), WEST_LEDGE_Y + 1, -54);
    }
}
