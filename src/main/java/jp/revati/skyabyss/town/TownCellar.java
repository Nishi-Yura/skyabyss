package jp.revati.skyabyss.town;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

// 酒場「深い杯」の地下の奥の壁 (docs/design/35 の 6.8)。建てたときは閉じていて、酔いどれの三つ目の頼み (town/cellar_3) で開く。
//   開ける所: 印 cellar_gap (開ける壁の下の段) とその上の 1 段の 2 マスを空気に
//   一度開けたらワールドで開いたまま (TownData.cellarOpen)。都を建て直して壁が戻っていたら、人が近くに来たとき開け直す
public final class TownCellar {
    public static final String GAP = "cellar_gap";

    private TownCellar() {
    }

    // 壁を開ける (記録して、今読み込まれていれば崩す)。印がなければ記録だけ (設計図に印が入ったら keepOpen が開ける)
    public static boolean open(MinecraftServer server) {
        TownData data = TownData.get(server);
        if (!data.cellarOpen) {
            data.cellarOpen = true;
            data.setDirty();
        }
        BlockPos gap = TownGuests.mark(server, GAP);
        if (gap == null) {
            SkyAbyss.LOGGER.warn("[town] cellar opened, but the blueprint has no {} mark yet", GAP);
            return false;
        }
        return carve(server.overworld(), gap, true);
    }

    // 閉じ直す (開発用)
    public static void close(MinecraftServer server) {
        TownData data = TownData.get(server);
        data.cellarOpen = false;
        data.setDirty();
    }

    public static boolean isOpen(MinecraftServer server) {
        return TownData.get(server).cellarOpen;
    }

    // 開いているはずの壁が戻っていれば崩す (1 秒ごと。都に人がいるとき)
    static void keepOpen(MinecraftServer server) {
        if (!TownData.get(server).cellarOpen) {
            return;
        }
        BlockPos gap = TownGuests.mark(server, GAP);
        ServerLevel level = server.overworld();
        if (gap != null && level.isLoaded(gap) && !wallGone(level, gap)) {
            carve(level, gap, false);
        }
    }

    public static boolean wallGone(ServerLevel level, BlockPos gap) {
        return level.getBlockState(gap).isAir() && level.getBlockState(gap.above()).isAir();
    }

    private static boolean carve(ServerLevel level, @Nullable BlockPos gap, boolean loud) {
        if (gap == null || !level.isLoaded(gap)) {
            return false;
        }
        for (BlockPos p : new BlockPos[]{gap, gap.above()}) {
            if (!level.getBlockState(p).isAir()) {
                level.destroyBlock(p, false);
                level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), 3);
            }
        }
        if (loud) {
            level.playSound(null, gap, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1f, 0.6f);
            level.playSound(null, gap, SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 1f, 0.5f);
        }
        return true;
    }
}
