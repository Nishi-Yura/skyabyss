package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import jp.revati.skyabyss.titan.gate.BossGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

// 岩鯨の背のボス部屋の大門 (番兵長の広間・心臓の間)。石・骨・苔・琥珀の言葉: 深層岩のレンガの枠、骨の柱、苔の絨毯、琥珀色の光 (キノコ光・黄土のカエル光)。
// 共通の形は titan.gate.BossGate。扉 (封印) のブロックの位置は変えない。部屋・通路を全部建てたあと (buildRest の終わり近く) に呼ぶ。
// 版 13 で入口が幅 7・高さ 6 になったので、開口の半幅 3・高さ 6 で建てる
final class BossGates {
    private static final BossGate.Palette PALETTE = new BossGate.Palette(
            Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            Blocks.BONE_BLOCK.defaultBlockState(),
            Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
            Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
            Blocks.DEEPSLATE_TILES.defaultBlockState(),
            Blocks.SHROOMLIGHT.defaultBlockState(),
            Blocks.OCHRE_FROGLIGHT.defaultBlockState(),
            Blocks.MOSS_BLOCK.defaultBlockState(),
            Blocks.BONE_BLOCK.defaultBlockState(),
            Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
            Blocks.OCHRE_FROGLIGHT.defaultBlockState());

    private BossGates() {
    }

    static void build(BuildKit kit) {
        // 心臓の間: 回廊の終わり (z HEART_GATE_Z) の大門。回廊の先を広い前庭にする (休み場の北の壁 z -101 の手前まで)
        BossGate.build(kit.level(), new BossGate.Spec(new BlockPos(0, FLOOR, HEART_GATE_Z), Direction.NORTH).half(3).height(6)
                .court(7, 8, 20).vault(7, 4), PALETTE);
        // 番兵長の広間: 控えの間からの通路の終わり (x 33) の大門。通路を広い前室にする
        int z = (E3_ENTRANCE.get(0).getZ() + E3_ENTRANCE.get(E3_ENTRANCE.size() - 1).getZ()) / 2;
        BossGate.build(kit.level(), new BossGate.Spec(new BlockPos(E3_ENTRANCE.get(0).getX(), FLOOR, z), Direction.WEST).half(2).height(6)
                .court(5, 4, 20), PALETTE);
        // 心臓の間の入口の一言は、右の柱の内側の面に掛ける
        kit.wallSign(HEART_GATE_SIGN.pos(), HEART_GATE_SIGN.facing(), HEART_GATE_SIGN.key());
    }
}
