package jp.revati.skyabyss.client.hud;

import java.util.List;

import jp.revati.skyabyss.client.notice.NoticeClient;
import jp.revati.skyabyss.client.pouch.QuickCard;
import jp.revati.skyabyss.client.quest.ChapterLabel;
import jp.revati.skyabyss.client.quest.ObjectiveStrip;
import jp.revati.skyabyss.client.quest.QuestHud;
import jp.revati.skyabyss.client.titan.BackGaugeHud;
import jp.revati.skyabyss.client.titan.BossAttackCue;
import jp.revati.skyabyss.client.tutorial.TutorialClient;
import jp.revati.skyabyss.client.vow.VowHud;
import jp.revati.skyabyss.hud.HudLayout.Request;
import jp.revati.skyabyss.party.client.DownedClient;
import jp.revati.skyabyss.party.client.PartyClient;
import jp.revati.skyabyss.rpg.client.GrowthHud;
import jp.revati.skyabyss.rpg.client.skill.SkillHud;
import net.minecraft.client.Minecraft;

// 各描画層が「いま出したい部品」の要求を出す口の一覧 (HudManager が毎フレーム呼ぶ)。
// 新しい HUD の部品は、ここに一行足して、自分の描画では HudManager.rect(Id) をもらった長方形の中にだけ描く
final class HudRequests {
    private HudRequests() {
    }

    static void collect(Minecraft mc, int w, int h, List<Request> out) {
        // 左上の帯 (上から): 成長 → 目的 → 追跡中の依頼
        GrowthHud.requests(mc, w, h, out);
        SkillHud.requests(mc, out);
        ChapterLabel.requests(mc, out);
        ObjectiveStrip.requests(mc, out);
        jp.revati.skyabyss.client.titan.LampBandClient.requests(mc, out);
        QuestHud.requests(mc, out);
        // ホットバーの左右
        QuickCard.requests(mc, out);
        // 右上の板 (上から): 誓い → 背中の目盛りと目的の板 → 仲間の一覧
        jp.revati.skyabyss.story.ch5.client.AbyssHud.requests(mc, out);
        VowHud.requests(mc, out);
        BackGaugeHud.requests(mc, out);
        PartyClient.requests(mc, w, h, out);
        jp.revati.skyabyss.abyss.arts.client.ArtClient.requests(mc, w, h, out);
        // 下中央: 倒れた表示 → 手がかり → 字幕。中央: 起こす輪
        DownedClient.requests(mc, out);
        TutorialClient.requests(mc, out);
        // 上中央 (ボスバーの下): 技の名前 → 上の帯 → レベルアップ → 中央の一行。字幕は上中央と手がかりのあいだ
        BossAttackCue.requests(mc, out);
        jp.revati.skyabyss.client.titan.domain.DomainClient.requests(mc, out);
        NoticeClient.requests(mc, w, h, out);
    }
}
