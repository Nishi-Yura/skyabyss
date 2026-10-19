package jp.revati.skyabyss.titan.ember.back;

import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import jp.revati.skyabyss.titan.gate.BossGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// 余燼の背のボス部屋の大門 (灰の鍛え手の扉・熾の底の入口)。煤・鉄・光る継ぎ目の言葉:
// 黒石の台と枠、磨いた黒石レンガの柱、鉄の帯、金の斑の入った黒石の縁取り、熾りのように光るキノコ光 (灯りは黄土のカエル光)。
// 共通の形は titan.gate.BossGate。扉 (封印) のブロックと、取っておく場所 (EmberBackLayout.reserved) は変えない。
// 熱は足もとのマグマで増えるので、床にはマグマを使わない。部屋を建て、休み場・碑文を置いたあとに呼ぶ
public final class EmberBossGates {
    private static final BossGate.Palette PALETTE = new BossGate.Palette(
            Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            Blocks.IRON_BLOCK.defaultBlockState(),
            Blocks.GILDED_BLACKSTONE.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState(),
            Blocks.SHROOMLIGHT.defaultBlockState(),
            Blocks.OCHRE_FROGLIGHT.defaultBlockState(),
            Blocks.RED_NETHER_BRICKS.defaultBlockState(),
            Blocks.IRON_BLOCK.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            Blocks.SHROOMLIGHT.defaultBlockState());

    private EmberBossGates() {
    }

    public static void build(ServerLevel level) {
        // 熾の底: 前室の南の端 (z -22)。南へ進む。開口は x -1〜1、床 50 の上 4 段。前室の天井 (56) の上へ、扉の前だけ高く掘り上げる。
        // 前室の北の休み場 (z -29〜-25) には触らない
        BossGate.build(level, new BossGate.Spec(new BlockPos(0, FLOOR_HEART, HEART_DOOR.minZ()), Direction.SOUTH)
                .depth(2).run(2).noPylons(), PALETTE);
        // 灰の鍛え手: 鍛えの間の奥の壁 (z 29)。南へ進む。大炉 (z 22〜26) の後ろ、前の取っておく場所 (z 27〜28) を空けたまま、壁の面に立てる。大炉の脇から扉の前へ回る道 (z 27) を柱でふさがない
        BossGate.build(level, new BossGate.Spec(new BlockPos(26, FLOOR_EAST, SMITH_DOOR.minZ()), Direction.SOUTH)
                .depth(2).lowDepth(1).run(0).noPylons().limit(14), PALETTE);
    }
}
