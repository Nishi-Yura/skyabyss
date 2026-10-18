package jp.revati.skyabyss.grave;

import jp.revati.skyabyss.notice.Notice;
import net.minecraft.server.level.ServerPlayer;

// 語り部の救い。取りに行けない墓を、割合 (skyabyssGraveRescue、既定 50%) だけ手もとへ呼び戻す。
// 会話の結果 grave_choose は墓の一覧の画面を開き (GraveChooser)、選んだ墓を一つ救う。grave_rescue はいちばん古い墓を救う (画面なし)。
// 墓は消え、残りは土に還る。入りきらない分は報酬の預かりへ (控え帳から受け取れる)
public final class GraveRescue {
    private GraveRescue() {
    }

    public static void run(ServerPlayer player) {
        report(player, Graves.rescue(player));
    }

    // 救った結果を知らせる (救えなければ「呼び戻せる墓はない」)
    static void report(ServerPlayer player, Graves.Rescued r) {
        if (r == null) {
            Notice.hint(player, "skyabyss_grave.rescue_none");
            return;
        }
        Notice.unlock(player, "skyabyss_grave.rescued", r.items(), r.xp(), DeathRules.rescuePercent(player.server));
        if (r.toPending() > 0) {
            Notice.warn(player, "skyabyss_grave.rescued_pending", r.toPending());
        }
    }
}
