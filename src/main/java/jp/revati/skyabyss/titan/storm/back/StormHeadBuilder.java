package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.storm.back.StormKit.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 雷翼の頭 (26 の 3.3): 首の回廊 → 嵐の冠の間 (7 本の銅の避雷針の冠。休み場 f0・舵・戻しの鏡) → 冠の道 → 眼の間 (鏡の台 5x5) → 嘴の付け根の小部屋
final class StormHeadBuilder {
    private StormHeadBuilder() {
    }

    static void front(StormKit kit) {
        // 首: 広間の上の回廊から北へ。灯りの柱と、行き先の線 (白)
        kit.room(NECK, Style.CROWN, 6);
        for (int z = NECK.z2() - 2; z >= NECK.z1() + 2; z -= 4) {
            kit.put(0, NECK.floor(), z, z % 8 == 0 ? StormDeco.FRONT_PIP : StormDeco.FRONT_LINE);
        }
        for (int z = NECK.z2() - 4; z >= NECK.z1() + 4; z -= 8) {
            StormDeco.lampPost(kit, NECK.x1(), NECK.floor(), z);
            StormDeco.lampPost(kit, NECK.x2(), NECK.floor(), z);
        }
        crown(kit);
        crownWalk(kit);
        eye(kit);
    }

    // 嵐の冠の間 (21x21): 真ん中に銅の菱形の床。7 本の銅の避雷針が輪になって立つ (頭の上の冠と同じ数)
    private static void crown(StormKit kit) {
        Area a = F0;
        kit.room(a, Style.CROWN, 5);
        int cx = 0;
        int cz = (a.z1() + a.z2()) / 2;
        // 床: 銅の輪と白い羽の菱形
        for (int x = a.x1(); x <= a.x2(); x++) {
            for (int z = a.z1(); z <= a.z2(); z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d >= 6.5 && d <= 7.5) {
                    kit.put(x, a.floor(), z, StormKit.COPPER);
                } else if (Math.abs(x - cx) + Math.abs(z - cz) <= 4) {
                    kit.put(x, a.floor(), z, StormKit.FEATHER_WHITE);
                }
            }
        }
        // 冠の 7 本 (半径 7 に 7 等分。南 (首の口) と北 (冠の道の口) の真ん中は避ける)
        for (int i = 0; i < 7; i++) {
            double ang = Math.PI * 2 * i / 7 + 0.2;
            int px = cx + (int) Math.round(Math.cos(ang) * 7);
            int pz = cz + (int) Math.round(Math.sin(ang) * 7);
            for (int y = a.floor() + 1; y <= a.floor() + 6; y++) {
                kit.put(px, y, pz, y == a.floor() + 1 ? StormKit.COPPER_BLOCK : StormKit.COPPER);
            }
            kit.put(px, a.floor() + 7, pz, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            kit.put(px, a.floor() + 8, pz, Blocks.LIGHTNING_ROD.defaultBlockState());
        }
        // 真ん中の台 (戻しの鏡の取っ手は別。ここは光る台と、ほのめかしの浮き彫り)
        for (int dy = 1; dy <= 2; dy++) {
            kit.put(cx, a.floor() + dy, cz, dy == 1 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : StormKit.LAMP);
        }
        // 戻しの鏡の台 (冠の間の北の端。取っ手)
        kit.put(MIRROR_RESET.getX(), MIRROR_RESET.getY() - 1, MIRROR_RESET.getZ(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        kit.put(MIRROR_RESET.getX(), MIRROR_RESET.getY(), MIRROR_RESET.getZ(), StormBackSetup.RETURN_MIRROR.get().defaultBlockState()
                .setValue(StormBlocks.ReturnMirror.FACING, Direction.EAST));
        // 腰掛け (東と西の壁ぎわ。待つ人のため)
        StormDeco.bench(kit, a.x1(), a.x1(), cz - 2, cz + 2, a.floor() + 1, Direction.EAST);
        StormDeco.bench(kit, a.x2(), a.x2(), cz - 2, cz + 2, a.floor() + 1, Direction.WEST);
        // 壁の灯りの柱
        for (int sz : new int[] {a.z1() + 1, a.z2() - 1}) {
            for (int sx : new int[] {a.x1() + 1, a.x2() - 1}) {
                StormDeco.lampPost(kit, sx, a.floor(), sz);
            }
        }
    }

    // 冠の道 (冠の間 → 眼の間。幅 7)。行き先の白い線と矢じるし
    private static void crownWalk(StormKit kit) {
        int fy = UPPER_GALLERY_Y;
        for (int z = F0.z1() - 1; z >= EYE_HALL.z2() + 1; z--) {
            int floor = Math.max(EYE_HALL.floor(), fy);
            kit.room(-3, 3, z, z, floor, 6, Style.EYE, 0);
            if (z == F0.z1() - 1 && floor > fy) {
                // 冠の間の床 (147) から道の床 (148) へ、半段の階段
                for (int x = -3; x <= 3; x++) {
                    kit.stair(x, floor, z, Direction.NORTH, Blocks.QUARTZ_STAIRS.defaultBlockState());
                }
            }
            if (z % 3 == 0) {
                kit.solid(4, floor + 3, z, StormKit.LAMP);
                kit.solid(-4, floor + 3, z, StormKit.LAMP);
            }
            if (z % 4 == 0) {
                kit.put(0, floor, z, StormDeco.FRONT_PIP);
            }
        }
        StormDeco.chevron(kit, 0, F0.z1() - 4, Direction.NORTH, StormKit.COPPER, zz -> Math.max(EYE_HALL.floor(), fy));
        StormDeco.chevron(kit, 0, F0.z1() - 9, Direction.NORTH, StormKit.COPPER, zz -> Math.max(EYE_HALL.floor(), fy));
    }

    // 眼の間 (23x23): 鏡の台 5x5 (間隔 4)。左の眼の窓。嘴の付け根の小部屋と戸
    private static void eye(StormKit kit) {
        StormBackLayout.Area e = EYE_HALL;
        kit.room(e, Style.EYE, 0);
        // 天井の灯り (格子の上)
        for (int c = 0; c < 5; c += 2) {
            for (int r = 0; r < 5; r += 2) {
                BlockPos g = gridCell(c, r);
                kit.solid(g.getX(), e.floor() + e.height() + 1, g.getZ(), StormKit.LAMP);
            }
        }
        // 壁の灯り (南の入口の左右・北の壁)
        for (int x = e.x1() + 2; x <= e.x2() - 2; x += 5) {
            kit.solid(x, e.floor() + 4, e.z1() - 1, StormKit.LAMP);
        }
        // 鏡の台 (25 台。鏡は試練の側が置く)
        for (int c = 0; c < 5; c++) {
            for (int r = 0; r < 5; r++) {
                BlockPos g = gridCell(c, r);
                kit.put(g.getX(), BEAM_Y - 1, g.getZ(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            }
        }
        // 左の眼の窓 (西の壁の光の帯。光の入る行はガラスが光る)。壁の外の殻まで抜く (外が空気のところまで)
        for (int r = 0; r < 5; r++) {
            BlockPos g = gridCell(0, r);
            for (int x = e.x1() - 8; x <= e.x1() - 1; x++) {
                if (!kit.get(x, BEAM_Y, g.getZ()).isAir() || x >= e.x1() - 3) {
                    kit.put(x, BEAM_Y, g.getZ(), StormKit.LENS);
                }
            }
        }
        // 嘴の付け根の小部屋 (前の石) と、戸
        kit.room(BEAK_ROOM, Style.EYE, 0);
        kit.solid(0, BEAK_ROOM.floor() + BEAK_ROOM.height() + 1, (BEAK_ROOM.z1() + BEAK_ROOM.z2()) / 2, StormKit.LAMP);
        for (int z = BEAK_DOOR_Z1; z <= BEAK_DOOR_Z2; z++) {
            for (int x = -1; x <= 1; x++) {
                kit.put(x, e.floor(), z, StormKit.floor(Style.EYE, x, z));
                for (int y = e.floor() + 1; y <= e.floor() + 3; y++) {
                    kit.put(x, y, z, ModBlocks.SEALED_GATE.get().defaultBlockState());
                }
            }
        }
        // もう一つの戻しの鏡 (眼の間の入口の東の隅。鏡の台と同じ高さ)
        kit.put(MIRROR_RESET_HALL.getX(), MIRROR_RESET_HALL.getY() - 1, MIRROR_RESET_HALL.getZ(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        kit.put(MIRROR_RESET_HALL.getX(), MIRROR_RESET_HALL.getY(), MIRROR_RESET_HALL.getZ(), StormBackSetup.RETURN_MIRROR.get().defaultBlockState()
                .setValue(StormBlocks.ReturnMirror.FACING, Direction.WEST));
    }
}
