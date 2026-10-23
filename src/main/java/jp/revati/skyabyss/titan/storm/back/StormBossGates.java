package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import jp.revati.skyabyss.titan.gate.BossGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// 雷翼の背のボス部屋の大門 (雷獣の巣の口・竜骨の間の入口)。銅・鋼・白い羽根・稲妻の言葉:
// 鉄の台、白い羽根の柱、銅の枠、銅の彫り込みの縁取り、深層岩のタイルの破風、稲妻色 (青白い) の光。共通の形は titan.gate.BossGate。
// 扉 (封印) のブロックの位置は変えない。鳥を建てたあと (StormBackBuilder.finish) に呼ぶ
public final class StormBossGates {
    private static final BossGate.Palette PALETTE = new BossGate.Palette(
            Blocks.IRON_BLOCK.defaultBlockState(),
            Blocks.WHITE_CONCRETE.defaultBlockState(),
            Blocks.WAXED_CUT_COPPER.defaultBlockState(),
            Blocks.WAXED_CHISELED_COPPER.defaultBlockState(),
            Blocks.DEEPSLATE_TILES.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.WAXED_COPPER_BLOCK.defaultBlockState(),
            Blocks.IRON_BLOCK.defaultBlockState(),
            Blocks.SMOOTH_STONE.defaultBlockState(),
            Blocks.POLISHED_ANDESITE.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState());

    private StormBossGates() {
    }

    // 竜骨の間: 前室の南の端。南へ進む。開口は前室の床の上 3 段。前室の天井の上、竜骨の間の天井 130 まで掘り上げる
    // (体の外の封じた竜骨の間。StormBirdBuilder.buildHeartInstance から)
    public static void heart(ServerLevel level) {
        BossGate.build(level, new BossGate.Spec(new BlockPos(HEART_ROOM.getX(), KEEL_ANTE.floor(), (int) HEART_DOOR.minZ), Direction.SOUTH)
                .height(3).court(2, 3, 12).vault(5, 3).limit(12), PALETTE);
    }

    public static void build(ServerLevel level) {
        // 雷獣の巣: 雷の骨の回廊の突き当たり (最後の戸と前室のあと)。東へ進む。回廊 (幅 23・高さ 13) を狭めて、幅 7 の開口にする
        int gateX = (int) RAIJU_NEST_MOUTH.minX;
        BossGate.build(level, new BossGate.Spec(new BlockPos(gateX - 1, BONE_FLOOR, WZ), Direction.EAST)
                .half(3).height(5).smallEmblem().noPylons().limit(EAST_HEIGHT), PALETTE);
    }
}
