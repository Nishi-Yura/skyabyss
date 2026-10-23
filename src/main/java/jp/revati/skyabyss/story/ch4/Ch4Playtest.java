package jp.revati.skyabyss.story.ch4;

import javax.annotation.Nullable;

import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

// 試遊を速く進める /skyabyss playtest now (いま) の四章の分 (playtest/Playtest.nowInner から)。四章の支度の段を、本物の仕組みで一歩ずつ進める:
//   イサナの噂         メイン「灰の昇る山」の噂の段なら、聞いた扱い (支度の段が出る)
//   火除けの薬 (醸造)   耐火のポーションを 2 つ渡す                  → 職人に見せる段では、見せた扱い (会話の節 ch4g_draught_show の効き目)
//   炉跡 (坑)          消えた炉の坑の口へ運ぶ (next なら頁を記す)    → 語り部に見せる段では、見せた扱い
// 二本とも済んだ (か、心臓を先に止めた) ら null を返し、いつもの巨神の手順 (呼んで背中へ) に任せる
public final class Ch4Playtest {
    private Ch4Playtest() {
    }

    @Nullable
    public static String now(ServerPlayer p, boolean next) {
        // 支度の段はイサナの噂のあとに出る (一本ずつ)。噂の段にいれば、聞いた扱いにして支度を出す
        if ("rumor".equals(DeadForgePit.stageOf(p, "main_ember"))) {
            Quests.onTalk(p, "skyabyss:whale_chaser", "ms_c_ember2");
            Quests.poll(p, true);
            return "commands.skyabyss.playtest.now.ch4_rumor";
        }
        String draught = DeadForgePit.stageOf(p, Ch4Ground.Q_DRAUGHT);
        if ("brew".equals(draught)) {
            for (int i = 0; i < 2; i++) {
                Quests.give(p, PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE));
            }
            Quests.poll(p, true);
            return "commands.skyabyss.playtest.now.ch4_draught";
        }
        if ("show".equals(draught)) {
            Lore.unlock(p, Ch4Ground.PAGE_DRAUGHT, true);
            Quests.onTalk(p, "skyabyss:artisan", "ch4g_draught_show");
            // 炉跡は火除けの薬を終えたあとに出る (一本ずつ)。次の呼び出しで炉跡へ進めるよう、ここで出しておく
            Quests.poll(p, true);
            return "commands.skyabyss.playtest.now.ch4_draught_show";
        }
        String forge = DeadForgePit.stageOf(p, Ch4Ground.Q_FORGE);
        if ("pit".equals(forge)) {
            if (next) {
                Lore.unlock(p, Ch4Ground.PAGE_FORGE, true);
                Quests.poll(p, true);
                return "commands.skyabyss.playtest.now.ch4_forge_page";
            }
            BlockPos at = Ch4Ground.first(p.server, Ch4Ground.PIT, null);
            if (at != null) {
                p.teleportTo(p.server.overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 10f);
                return "commands.skyabyss.playtest.now.ch4_pit";
            }
            Lore.unlock(p, Ch4Ground.PAGE_FORGE, true);
            Quests.poll(p, true);
            return "commands.skyabyss.playtest.now.ch4_forge_page";
        }
        if ("tell".equals(forge)) {
            Quests.onTalk(p, "skyabyss:storyteller", "ch4g_forge_read2");
            return "commands.skyabyss.playtest.now.ch4_forge_tell";
        }
        return null;
    }
}
