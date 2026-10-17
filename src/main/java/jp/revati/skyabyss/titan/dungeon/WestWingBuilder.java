package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import java.util.List;

import net.minecraft.world.level.block.state.BlockState;

// 西の翼「裂け目の回廊」: 足場を跳び継いで進む、長いアスレチック。
//   西の前室 (休み場) → 肋骨の谷 → 第一の休み場 → 竪穴 → 第二の休み場 → 崩れ橋 → 共鳴石の岩棚
// 谷はどれも床が抜け落ち、はるか下に溶岩が光る。落ちると直前の休み場へ戻される (TitanBackTrials)。
// 一部の足場は乗ると崩れる。最後の岩棚から、鼓動の間へ下りる抜け道が開く
final class WestWingBuilder {
    private WestWingBuilder() {
    }

    static void build(BuildKit kit) {
        kit.room(W0);
        kit.room(WA);
        kit.room(W1);
        kit.room(WB);
        kit.room(W2);
        kit.room(WC);
        // 部屋どうしをつなぐ戸口
        kit.doorway(-23, -23, -23, -21, FLOOR, 4);              // 西の前室 → 肋骨の谷
        kit.doorway(-31, -29, -35, -35, W1.floorY(), 4);         // 肋骨の谷 → 第一の休み場
        kit.doorway(-30, -28, -43, -43, W1.floorY(), 4);         // 第一の休み場 → 竪穴
        kit.doorway(-24, -22, -57, -57, W2.floorY(), 4);         // 竪穴 → 第二の休み場
        kit.room(new Box(-18, -16, -57, -53, W2.floorY(), 4));   // 第二の休み場 → 崩れ橋

        // 谷: 床を抜き、はるか下を溶岩にする
        for (Box pit : PITS) {
            pit(kit, pit);
        }
        // 谷の入口と出口の岩棚
        ledge(kit, -24, -24, -23, -21, FLOOR);
        ledge(kit, -31, -29, -34, -34, W1.floorY());
        ledge(kit, -30, -28, -44, -44, W1.floorY());
        ledge(kit, -24, -22, -56, -56, W2.floorY());
        ledge(kit, -18, -16, -52, -52, W2.floorY());
        ledge(kit, -16, -12, -31, -30, 97);

        decorate(kit);
        ribWindows(kit);
        placeCourses(kit, ParkourCourses.forSeed(TitanBackData.get(kit.level()).courseSeed()));

        // 最後の岩棚から鼓動の間へ下りる抜け道 (石が歌うまで封じられている)
        for (BlockPos p : WEST_RETURN) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
    }

    private static void pit(BuildKit kit, Box pit) {
        for (int x = pit.x1(); x <= pit.x2(); x++) {
            for (int z = pit.z1(); z <= pit.z2(); z++) {
                for (int y = 87; y <= FLOOR; y++) {
                    kit.set(x, y, z, BuildKit.AIR);
                }
                kit.set(x, 86, z, Blocks.LAVA.defaultBlockState());
                kit.set(x, 85, z, Blocks.BLACKSTONE.defaultBlockState());
            }
        }
        // 谷の壁の下のほうは、熱で黒ずんだ石にする
        for (int y = 85; y <= FLOOR; y++) {
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

    // 肋骨の窓: 谷の西の壁 (鯨の左のわき腹) で、外までの殻が薄い所 (4 ブロック以内) を、肋骨の間の裂け目にする。
    // 4 マスごとに骨の柱 (肋骨) を残し、その間は外の面までくり抜き、谷の壁の面にガラスをはめる。谷から雲と空が見える
    // (外へは出られず、窓の奥に立つ所もない)
    static final int WINDOW_Y1 = 96;
    static final int WINDOW_Y2 = 103;
    static final int WINDOW_DEPTH = 4;

    static void ribWindows(BuildKit kit) {
        BlockState rib = Blocks.BONE_BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (Box pit : PITS) {
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
                    boolean isRib = Math.floorMod(z - pit.z1(), 4) == 0 || y == WINDOW_Y1 || y == WINDOW_Y2;
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

    private static void ledge(BuildKit kit, int x1, int x2, int z1, int z2, int y) {
        kit.fill(x1, y, z1, x2, y, z2, BuildKit.POLISHED);
    }

    // 足場を置く。足場はすべて崩れる骨。新しい足場の頭上に飾り (鎖・鍾乳石) が垂れていたら取り除く
    static void placeCourses(BuildKit kit, List<List<int[]>> courses) {
        for (List<int[]> course : courses) {
            for (int[] s : course) {
                kit.set(s[0], s[1], s[2], ModBlocks.CRUMBLING_BONE.get().defaultBlockState());
                for (int y = s[1] + 1; y <= s[1] + 4; y++) {
                    kit.set(s[0], y, s[2], BuildKit.AIR);
                }
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

    private static void decorate(BuildKit kit) {
        // 休み場
        kit.restPoint(W0);
        kit.restPoint(W1);
        kit.restPoint(W2);
        for (Box rest : List.of(W0, W1, W2)) {
            kit.wallLamps(rest, 3, rest.floorY() + 3);
        }
        kit.pilasters(W0, 3);
        kit.moss(W0, 0.06f);
        // 肋骨の谷: 天井に骨の梁 (足場の高さより十分上なので頭はぶつからない)
        kit.ribs(WA, 5, true);
        kit.pilasters(WA, 4);
        // 谷の天井から垂れる鍾乳石と鎖。足場の位置は毎回変わるので、壁ぎわ1マスだけに飾る
        for (Box pit : PITS) {
            for (int i = 0; i < 14; i++) {
                boolean alongX = kit.random.nextBoolean();
                int x = alongX ? pit.x1() + kit.random.nextInt(pit.x2() - pit.x1() + 1) : (kit.random.nextBoolean() ? pit.x1() : pit.x2());
                int z = alongX ? (kit.random.nextBoolean() ? pit.z1() : pit.z2()) : pit.z1() + kit.random.nextInt(pit.z2() - pit.z1() + 1);
                if (i % 3 == 0) {
                    for (int y = pit.ceilingY() - 1; y > pit.ceilingY() - 4; y--) {
                        kit.set(x, y, z, Blocks.CHAIN.defaultBlockState());
                    }
                } else {
                    kit.stalactite(x, pit.ceilingY(), z, 1 + kit.random.nextInt(2));
                }
            }
            // 谷の壁に埋め込んだ灯り (足場が見えるように)
            kit.wallLamps(pit, 5, 99);
        }
        // 岩棚の灯り
        kit.lantern(-24, FLOOR + 1, -23);
        kit.lantern(-31, W1.floorY() + 1, -34);
        kit.lantern(-24, W2.floorY() + 1, -56);
        kit.lantern(-12, 98, -30);
    }

}
