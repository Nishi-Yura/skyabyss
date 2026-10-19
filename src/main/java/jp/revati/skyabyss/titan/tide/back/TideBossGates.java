package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import jp.revati.skyabyss.titan.gate.BossGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// 潮殻の背のボス部屋の大門 (潮招きの干潟の口・殻の底の心臓の間)。珊瑚・真珠・塩・海の青の言葉:
// 暗い海の石の台、方解石 (真珠層) の柱、海のレンガの枠、珊瑚色の縁取り、海のランタンの光。共通の形は titan.gate.BossGate。
// 扉 (封印) のブロックの位置は変えない
public final class TideBossGates {
    private static final BossGate.Palette PALETTE = new BossGate.Palette(
            Blocks.DARK_PRISMARINE.defaultBlockState(),
            Blocks.CALCITE.defaultBlockState(),
            Blocks.PRISMARINE_BRICKS.defaultBlockState(),
            Blocks.PINK_TERRACOTTA.defaultBlockState(),
            Blocks.DARK_PRISMARINE.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
            Blocks.CALCITE.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.PRISMARINE_BRICKS.defaultBlockState(),
            Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());

    private TideBossGates() {
    }

    // 干潟の口 (潮招きの戦い): トンネルの北の端 (z MOUTH_Z)。開口は x -2〜2、床 66 の上 3 段
    public static void flats(ServerLevel level) {
        BossGate.build(level, new BossGate.Spec(new BlockPos(0, TUNNEL_FLOOR, MOUTH_Z), Direction.NORTH).half(MOUTH_HALF).height(3).court(9, 6, 15), PALETTE);
    }

    // 殻の底 (心臓の間) の入口: 前室の北の端 (z -15)。開口は x -1〜1、床 (床のブロックは floorY - 1) の上 3 段。前室から広い前庭へ
    public static void heart(ServerLevel level, int floorY, int gateZ) {
        BossGate.build(level, new BossGate.Spec(new BlockPos(0, floorY - 1, gateZ), Direction.NORTH).height(3).court(4, 4, 15).vault(12, 4), PALETTE);
    }
}
