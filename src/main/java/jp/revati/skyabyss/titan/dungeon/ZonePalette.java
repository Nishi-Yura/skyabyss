package jp.revati.skyabyss.titan.dungeon;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

// 岩鯨の背の「ゾーンごとの材質」(設計書 26 の 8.6)。体の中がどこも同じ暗いレンガと梁にならないよう、ゾーンごとに壁・床・天井・柱・梁・灯りの材質を替える。
// BuildKit が今の材質 (kit.palette) を持ち、部屋・柱・梁・灯り・休み場の部品はそれを使う
record ZonePalette(String name, BlockState wall, BlockState wallAlt, BlockState floor, BlockState floorAlt, BlockState ceiling,
        BlockState pillar, BlockState cap, BlockState trim, BlockState beam, BlockState lamp, BlockState glow, boolean warm) {

    private static BlockState b(Block block) {
        return block.defaultBlockState();
    }

    private static BlockState log(Block block) {
        return block.defaultBlockState();
    }

    // 梁・柱の向き (回せるブロックだけ)
    BlockState beamAlong(net.minecraft.core.Direction.Axis axis) {
        return beam.hasProperty(RotatedPillarBlock.AXIS) ? beam.setValue(RotatedPillarBlock.AXIS, axis) : beam;
    }

    BlockState pillarAlong(net.minecraft.core.Direction.Axis axis) {
        return pillar.hasProperty(RotatedPillarBlock.AXIS) ? pillar.setValue(RotatedPillarBlock.AXIS, axis) : pillar;
    }

    // 灯り (置く向きなしの標準の台つきランタン / 吊りランタン)
    BlockState lantern() {
        return warm ? b(Blocks.LANTERN) : b(Blocks.SOUL_LANTERN);
    }

    BlockState hangingLantern() {
        return lantern().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
    }

    // 深層岩 (これまでの材質。鼓動の間・入口の通路・祠)
    static final ZonePalette DEEP = new ZonePalette("deep", b(Blocks.DEEPSLATE_BRICKS), b(Blocks.CRACKED_DEEPSLATE_BRICKS), b(Blocks.POLISHED_DEEPSLATE),
            b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_TILES), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.CHISELED_DEEPSLATE), b(Blocks.CHISELED_DEEPSLATE),
            b(Blocks.BONE_BLOCK), b(Blocks.SEA_LANTERN), b(Blocks.SEA_LANTERN), false);

    // 西の翼 (肋骨の谷): 熱で黒ずんだ石と骨。谷は肋骨の籠
    static final ZonePalette RIB = new ZonePalette("rib", b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
            b(Blocks.POLISHED_BLACKSTONE), b(Blocks.POLISHED_BASALT), b(Blocks.BLACKSTONE), b(Blocks.BONE_BLOCK), b(Blocks.CHISELED_POLISHED_BLACKSTONE),
            b(Blocks.CHISELED_POLISHED_BLACKSTONE), b(Blocks.BONE_BLOCK), b(Blocks.SEA_LANTERN), b(Blocks.PEARLESCENT_FROGLIGHT), false);

    // 第二の広間 (射手の回廊): 磨いた安山岩と雲母の灰色、トウヒの梁
    static final ZonePalette GALLERY = new ZonePalette("gallery", b(Blocks.POLISHED_ANDESITE), b(Blocks.ANDESITE), b(Blocks.SMOOTH_STONE),
            b(Blocks.STONE), b(Blocks.SPRUCE_PLANKS), b(Blocks.STRIPPED_SPRUCE_LOG), b(Blocks.CHISELED_STONE_BRICKS), b(Blocks.CHISELED_STONE_BRICKS),
            b(Blocks.SPRUCE_LOG), b(Blocks.SHROOMLIGHT), b(Blocks.SHROOMLIGHT), true);

    // 番兵長の広間: 黒い磨き石と金の差し色、赤い絨毯
    static final ZonePalette CAPTAIN = new ZonePalette("captain", b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
            b(Blocks.POLISHED_BLACKSTONE), b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.BLACKSTONE), b(Blocks.GILDED_BLACKSTONE), b(Blocks.GILDED_BLACKSTONE),
            b(Blocks.CHISELED_POLISHED_BLACKSTONE), b(Blocks.DARK_OAK_LOG), b(Blocks.SHROOMLIGHT), b(Blocks.SHROOMLIGHT), true);

}
