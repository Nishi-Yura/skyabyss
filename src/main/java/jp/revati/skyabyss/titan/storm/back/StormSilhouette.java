package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.Set;
import java.util.function.IntBinaryOperator;

import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Area;
import jp.revati.skyabyss.titan.storm.back.StormKit.Style;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 箱に見える広間に影をつける (26 の 9.5)。部屋の大きさ・床・戸・戦いの範囲は変えない。変えるのは天井と壁の面だけ:
//  - 肋のある丸天井 (広間・肩の部屋・眼の間): 肋 (銅の帯) の間の天井を 1〜5 マス掘り上げる。縁は低いまま。高さの違う天井
//  - 羽の壁 (広間・尾の間): 白い羽軸 (石英の柱) と、斜めに走る羽枝の白い線
//  - 銅の筋 (雷の側: 東の肩の部屋と東の通路): 壁を這って枝分かれする銅の筋と、ところどころ光る石
// 掘り上げる所は、真上に鳥の体が 2 マス以上残る所だけ (外へ抜けない)。ランプ・ガラス・光る石は掘らない。戸・柱・灯りの壁は塗り替えない (壁の石だけ)
final class StormSilhouette {
    private static final Set<Block> PLAIN = Set.of(Blocks.TUFF, Blocks.CALCITE, Blocks.POLISHED_DIORITE, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE,
            Blocks.BONE_BLOCK, Blocks.LIGHT_GRAY_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_QUARTZ,
            Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.GRAY_CONCRETE, Blocks.BASALT, Blocks.BLACKSTONE);

    private StormSilhouette() {
    }

    static void build(StormKit kit) {
        hubVault(kit);
        tailRibs(kit);
        bays(kit, W0, Style.BONE, 6, 3);
        bays(kit, E0, Style.THUNDER, 6, 3);
        eyeDome(kit);
        featherWall(kit, HUB.x1() - 1, HUB.z1(), HUB.z2(), HUB.floor(), HUB.height(), true);
        featherWall(kit, HUB.x2() + 1, HUB.z1(), HUB.z2(), HUB.floor(), HUB.height(), true);
        featherWall(kit, HUB.z1() - 1, HUB.x1(), HUB.x2(), HUB.floor(), HUB.height(), false);
        featherWall(kit, TAIL_HALL.x1() - 1, TAIL_HALL.z1(), TAIL_HALL.z2(), TAIL_HALL.floor(), TAIL_HALL.height(), true);
        featherWall(kit, TAIL_HALL.x2() + 1, TAIL_HALL.z1(), TAIL_HALL.z2(), TAIL_HALL.floor(), TAIL_HALL.height(), true);
        // 銅の筋: 東の肩の部屋と東の肩の階段、東の通路の入口の前
        veinWall(kit, E0.z1() - 1, E0.x1(), E0.x2(), E0.floor(), E0.height(), false, 3);
        veinWall(kit, E0.z2() + 1, E0.x1(), E0.x2(), E0.floor(), E0.height(), false, 5);
        veinWall(kit, EAST_PASS.z1() - 1, EAST_PASS.x1() + 4, EAST_PASS.x2(), EAST_PASS.floor() + 14, EAST_PASS.height(), false, 7);
        veinWall(kit, EAST_PASS.z2() + 1, EAST_PASS.x1() + 4, EAST_PASS.x2(), EAST_PASS.floor() + 14, EAST_PASS.height(), false, 9);
    }

    // ---------------------------------------------------------------- 肋のある丸天井
    // 掘ってよい石か: バニラの光らない固い石 (ランプ・ガラス・銅・光る石・この MOD の仕掛けのブロックは除く)
    private static boolean plain(StormKit kit, int x, int y, int z) {
        BlockState s = kit.get(x, y, z);
        if (s.isAir() || s.getLightEmission() > 0 || !s.isSolidRender(kit.level(), new net.minecraft.core.BlockPos(x, y, z))) {
            return false;
        }
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return key.getNamespace().equals("minecraft") && !s.is(StormKit.COPPER.getBlock()) && !s.is(StormKit.COPPER_BLOCK.getBlock());
    }

    // 天井の層 ceilY から raise マス掘り上げ、蓋を石にする。上の体が 2 マス以上残るところまで、途中に掘れない石があればその手前まで。
    // 掘れた蓋の y を返す (掘らなければ -1)
    private static int lift(StormKit kit, int x, int z, int ceilY, int raise, Style style) {
        int top = StormBirdBuilder.top(x, z);
        int cap = Math.min(ceilY + raise, top - 2);
        for (int y = ceilY; y <= cap; y++) {
            if (!plain(kit, x, y, z)) {
                cap = y - 1;
                break;
            }
        }
        if (cap <= ceilY) {
            return -1;
        }
        for (int y = ceilY; y < cap; y++) {
            kit.air(x, y, z);
        }
        kit.put(x, cap, z, StormKit.wall(style, x, cap, z));
        return cap;
    }

    private static void rib(StormKit kit, int x, int y, int z) {
        if (plain(kit, x, y, z)) {
            kit.put(x, y, z, StormKit.COPPER);
        }
    }

    private static void hubVault(StormKit kit) {
        Area a = HUB;
        int ceil = a.floor() + a.height() + 1;
        for (int x = a.x1() + 3; x <= a.x2() - 3; x++) {
            for (int z = a.z1() + 3; z <= a.z2() - 3; z++) {
                if (x * x + (z + 12) * (z + 12) <= (SKYLIGHT_R + 3) * (SKYLIGHT_R + 3)) {
                    continue;
                }
                int zr = z - a.z1();
                if (zr % 9 <= 1) {
                    rib(kit, x, ceil, z);
                    continue;
                }
                int raise = Math.abs(x) <= 9 ? 3 : Math.abs(x) <= 15 ? 2 : 1;
                int cap = lift(kit, x, z, ceil, raise, Style.FEATHER);
                if (cap > 0 && zr % 9 == 5 && Math.floorMod(x, 8) == 0) {
                    kit.put(x, cap, z, StormKit.LAMP);
                }
            }
        }
    }

    // 尾の間: 真ん中の持ち上がった天井に、銅の肋 (4 おき)。灯りは残す
    private static void tailRibs(StormKit kit) {
        Area a = TAIL_HALL;
        int cap = a.floor() + a.height() + 3;
        for (int x = -8; x <= 8; x += 4) {
            for (int z = a.z1() + 2; z <= a.z2() - 2; z++) {
                BlockState s = kit.get(x, cap, z);
                if (!s.is(StormKit.LAMP.getBlock()) && PLAIN.contains(s.getBlock())) {
                    kit.put(x, cap, z, StormKit.COPPER);
                }
            }
        }
    }

    // 部屋 a の天井を、x 方向の肋 (rib おき) の間で掘り上げる (肩の部屋)。縁 2 マスは低いまま
    private static void bays(StormKit kit, Area a, Style style, int ribEvery, int raise) {
        int ceil = a.floor() + a.height() + 1;
        for (int x = a.x1() + 2; x <= a.x2() - 2; x++) {
            for (int z = a.z1() + 2; z <= a.z2() - 2; z++) {
                int xr = x - a.x1();
                if (xr % ribEvery <= 1) {
                    rib(kit, x, ceil, z);
                    continue;
                }
                int cap = lift(kit, x, z, ceil, raise, style);
                if (cap > 0 && xr % ribEvery == 3 && Math.floorMod(z - a.z1(), 6) == 3) {
                    kit.put(x, cap, z, StormKit.LAMP);
                }
            }
        }
    }

    // 眼の間: 真ん中が高い円い天井 (鏡の台のまわりが円蓋になる)
    private static void eyeDome(StormKit kit) {
        Area a = EYE_HALL;
        int ceil = a.floor() + a.height() + 1;
        int cz = (a.z1() + a.z2()) / 2;
        for (int x = a.x1() + 1; x <= a.x2() - 1; x++) {
            for (int z = a.z1() + 1; z <= a.z2() - 1; z++) {
                double d = Math.sqrt(x * x + (z - cz) * (z - cz));
                int raise = d <= 3.5 ? 5 : d <= 6.5 ? 4 : d <= 8.5 ? 3 : d <= 10 ? 2 : 0;
                if (raise > 0) {
                    lift(kit, x, z, ceil, raise, Style.EYE);
                }
            }
        }
        // 円蓋の縁に銅の輪
        for (int x = a.x1() + 1; x <= a.x2() - 1; x++) {
            for (int z = a.z1() + 1; z <= a.z2() - 1; z++) {
                double d = Math.sqrt(x * x + (z - cz) * (z - cz));
                if (d > 10 && d <= 11 && plain(kit, x, ceil, z)) {
                    kit.put(x, ceil, z, StormKit.COPPER);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 羽の壁
    // 壁の面 (axisX = true なら x = fixed の壁で s は z、false なら z = fixed の壁で s は x)。壁の石だけ塗り替える
    private static void featherWall(StormKit kit, int fixed, int s1, int s2, int floor, int height, boolean axisX) {
        for (int s = s1; s <= s2; s++) {
            int p = Math.floorMod(s, 8);
            for (int y = floor + 1; y <= floor + height; y++) {
                int x = axisX ? fixed : s;
                int z = axisX ? s : fixed;
                if (!plain(kit, x, y, z)) {
                    continue;
                }
                int q = Math.floorMod(y - floor, 8);
                BlockState cur = kit.get(x, y, z);
                if (p == 4) {
                    kit.put(x, y, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
                } else if (q > 0 && (Math.abs(p - 4) + q) % 4 == 0 && !cur.is(Blocks.SMOOTH_QUARTZ)) {
                    kit.put(x, y, z, StormKit.ANDESITE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 銅の筋
    // z = fixed の壁の面 (x の範囲 x1〜x2、床 floor の上 height まで) に、下から上へ枝分かれして伸びる銅の筋。深い石の壁の石だけ
    private static void veinWall(StormKit kit, int fixed, int x1, int x2, int floor, int height, boolean unused, int seed) {
        for (int start = x1 + 2 + seed % 3; start <= x2 - 2; start += 7) {
            int x = start;
            for (int y = floor + 1; y <= floor + height; y++) {
                int h = Math.floorMod(x * 73856093 ^ y * 19349663 ^ seed * 83492791, 97);
                x = Math.max(x1 + 1, Math.min(x2 - 1, x + (h % 3) - 1));
                vein(kit, x, y, fixed, h);
                if (y % 5 == 0) {
                    // 枝
                    int dir = h % 2 == 0 ? 1 : -1;
                    for (int k = 1; k <= 2 + h % 3; k++) {
                        vein(kit, Math.max(x1 + 1, Math.min(x2 - 1, x + dir * k)), y + (k + 1) / 2, fixed, h + k);
                    }
                }
            }
        }
    }

    private static void vein(StormKit kit, int x, int y, int z, int h) {
        BlockState s = kit.get(x, y, z);
        if (!PLAIN.contains(s.getBlock())) {
            return;
        }
        kit.put(x, y, z, h % 13 == 0 ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : StormKit.COPPER);
    }

    // ほかから使う (塔の中の筋など)
    static void veins(StormKit kit, int fixed, int x1, int x2, int floor, int height, int seed) {
        veinWall(kit, fixed, x1, x2, floor, height, false, seed);
    }

    static IntBinaryOperator never() {
        return (x, z) -> 0;
    }
}
