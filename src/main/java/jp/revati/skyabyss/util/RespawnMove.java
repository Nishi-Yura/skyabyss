package jp.revati.skyabyss.util;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 復活 (PlayerRespawnEvent) の中で人を別の次元へ移すときの口。
// 2026-11-07 の二人の試遊: 潮殻の背で倒れた仲間 (ホストでない人) が、復活の中で背中へ移されたあと「体力 0 のまま何もできない」になった
// (サーバーの記録に「moved too quickly」。仲間の画面では古い倒れた体が残っていた)。復活の途中で次元を移すと、遠くの人の画面が新しい体に追いつかない。
// 別の次元へは、復活が済んでから (DELAY tick 後) 移す。同じ次元なら今すぐ。移す前に抜けた・倒れた人は動かさない
public final class RespawnMove {
    public static final int DELAY = 2;

    private RespawnMove() {
    }

    // move は人を to へ移す処理 (休み場の記録・守りなど、移したあとの事も中で)。今すぐ動かしたら true
    public static boolean after(ServerPlayer player, ServerLevel to, Runnable move) {
        if (player.level() == to) {
            move.run();
            return true;
        }
        jp.revati.skyabyss.story.ch1.Tasks.later(DELAY, () -> {
            ServerPlayer now = player.server.getPlayerList().getPlayer(player.getUUID());
            if (now != player || !player.isAlive() || player.hasDisconnected()) {
                SkyAbyss.LOGGER.info("Respawn move of {} skipped (left or fell again before it)", player.getGameProfile().getName());
                return;
            }
            move.run();
        });
        return false;
    }
}
