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

    public static void build(ServerLevel level) {
        // 竜骨の間: 前室の北の端 (z -30)。南へ進む。開口は x -1〜1、床 121 の上 3 段。前室の天井は 126、竜骨の間の天井 133 まで掘り上げる
        BossGate.build(level, new BossGate.Spec(new BlockPos(0, KEEL_ANTE.floor(), (int) HEART_DOOR.minZ), Direction.SOUTH)
                .height(3).court(2, 3, 12).limit(12), PALETTE);
        // 雷獣の巣: 雷の骨の回廊の突き当たり (x 114 の扉の列の手前)。東へ進む。回廊 (幅 15・高さ 10) を狭めて、幅 7 の開口にする
        int gateX = (int) RAIJU_NEST_MOUTH.minX;
        BossGate.build(level, new BossGate.Spec(new BlockPos(gateX - 1, BONE_FLOOR, -17), Direction.EAST)
                .half(3).height(4).smallEmblem().noPylons().limit(11), PALETTE);
    }
}
