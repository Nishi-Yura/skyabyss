package jp.revati.skyabyss.titan.shrine;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// 巨神の祠 (docs/design/32 の 3.1)。周回の順路の入口
public final class TitanShrine {
    private TitanShrine() {
    }

    // 祠の中央 (まだ建っていなければ null)
    @Nullable
    public static BlockPos posOf(MinecraftServer server, String titan) {
        return null;
    }

    // 祠の輪へ送る。送れたか
    public static boolean sendTo(ServerPlayer p, String titan) {
        return false;
    }
}
