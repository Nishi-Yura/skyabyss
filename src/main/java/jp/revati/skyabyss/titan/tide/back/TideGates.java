package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.SealedGateBlock;
import jp.revati.skyabyss.titan.group.GroupGates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

// 潮殻の背の扉 (封印の扉 SealedGateBlock) の座標と置き方。扉はいつもブロックとして置いてあり、
// ワールドの既定 (OPEN) とグループごとの上書き (GroupGates) で開け閉めする (docs/design/08 の 7.5節)。
//  - 干潟の門: ワールドのブロックはいつも閉じている (開いた扉は水を通し、満ちた広間の水がトンネルへ漏れるため)。背中にいるグループにはいつも開けてある
//    (グループの上書き。潮に関わらず。満潮は水の中を泳いでくぐる)。潮招きと戦っているグループにだけ閉じる
//  - 潮の底の門: 既定は閉じている。三つの石を鳴らしたグループにだけ、潮に関わらず開く
//  - 干潟の口: 既定は開いている。潮招きと戦っているグループにだけ閉じる
//  - 潮見の間の溜めの戸 (と岩棚への戸): 部屋の占有の中の仕掛けなので、既定 (水位) だけで動かす
//  - くぐり道の封印: ピースフルのあいだだけ既定で閉じる
//  - 殻の底の入口: TideHeartArena.gate()。戦いのあいだ既定で閉じる (部屋の占有の中。満ち潮の水を止める)
public final class TideGates {
    private TideGates() {
    }

    public static List<BlockPos> flats() {
        List<BlockPos> list = new ArrayList<>();
        for (int x = -FLATS_GATE_HALF; x <= FLATS_GATE_HALF; x++) {
            for (int y = HUB_FLOOR + 1; y <= HUB_FLOOR + 3; y++) {
                list.add(new BlockPos(x, y, FLATS_GATE_Z));
            }
        }
        return list;
    }

    // 潮の底の門の沈む格子 (西の列の 3 マス)
    public static List<BlockPos> bottom() {
        List<BlockPos> list = new ArrayList<>();
        for (int z = -1; z <= 1; z++) {
            list.add(new BlockPos(-1, HUB_FLOOR, z));
        }
        return list;
    }

    // 潮の底の門の、沈まない格子 (真ん中の灯りのまわり)
    static List<BlockPos> bottomFixed() {
        List<BlockPos> list = new ArrayList<>();
        for (int x = 0; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    list.add(new BlockPos(x, HUB_FLOOR, z));
                }
            }
        }
        return list;
    }

    public static List<BlockPos> mouth() {
        List<BlockPos> list = new ArrayList<>();
        for (int x = -MOUTH_HALF; x <= MOUTH_HALF; x++) {
            for (int y = TUNNEL_FLOOR + 1; y <= TUNNEL_FLOOR + 3; y++) {
                list.add(new BlockPos(x, y, MOUTH_Z));
            }
        }
        return list;
    }

    // 溜めの戸 (k = 0〜2)。上の溜めの戸は、岩棚への戸もいっしょに開け閉めする
    public static List<BlockPos> door(int k) {
        List<BlockPos> list = new ArrayList<>(List.of(BASIN_DOOR[k], BASIN_DOOR[k].above()));
        if (k == 2) {
            list.add(T_EXIT_DOOR);
            list.add(T_EXIT_DOOR.above());
        }
        return list;
    }

    public static List<BlockPos> cave() {
        List<BlockPos> list = new ArrayList<>();
        int f = TideIslandBuilder.caveFloor(CAVE_SEAL_Z);
        for (int x = CAVE_X1; x <= CAVE_X2; x++) {
            for (int y = f + 1; y <= f + 6; y++) {
                list.add(new BlockPos(x, y, CAVE_SEAL_Z));
            }
        }
        return list;
    }

    // 扉のブロックがなければ置く (いまの既定は変えない)。建てたあと・起動のあとに呼ぶ (何度呼んでもよい)
    static void ensure(ServerLevel level, Collection<BlockPos> blocks, boolean defaultOpen) {
        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos p : blocks) {
            if (!GroupGates.isGate(level, p)) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            GroupGates.place(level, missing, defaultOpen);
        }
    }

    // 島の扉をすべて置く (建てたあと・起動のあと)
    static void ensureAll(ServerLevel level) {
        ensure(level, flats(), false);
        ensure(level, bottom(), false);
        ensure(level, bottomFixed(), false);
        ensure(level, mouth(), true);
        for (int k = 0; k < 3; k++) {
            ensure(level, door(k), false);
        }
        ensure(level, cave(), true);
        TideFins.placeGates(level, false);
        ensure(level, jp.revati.skyabyss.titan.tide.heart.TideHeartArena.gate(), true);
    }

    // ワールドの既定で開いているか (扉でなければ開いているとみなす)
    public static boolean defaultOpen(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.is(ModBlocks.SEALED_GATE.get()) || s.getValue(SealedGateBlock.OPEN);
    }
}
