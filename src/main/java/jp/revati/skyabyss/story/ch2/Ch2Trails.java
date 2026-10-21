package jp.revati.skyabyss.story.ch2;

import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.server.level.ServerPlayer;

// 二章の支度の段 (docs/design/34 の 4.3) の、クエストのデータの外にある仕上げ。
//   二本 (塩の道・船と貝) がそろったとき (PrepQuests.onReady。人ごとに一度)、イサナの言葉を一行 (記念品はない)。合図 ch2_ready と進捗 tide_trail は PrepQuests が送る
public final class Ch2Trails {
    private Ch2Trails() {
    }

    static void register() {
        PrepQuests.onReady(2, Ch2Trails::ready);
    }

    static void ready(ServerPlayer player) {
        Notice.narrate(player, "message.skyabyss.ch2.trail.ready");
    }
}
