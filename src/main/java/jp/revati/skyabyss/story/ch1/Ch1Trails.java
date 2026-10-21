package jp.revati.skyabyss.story.ch1;

import java.util.List;

import jp.revati.skyabyss.guide.GuideSetup;
import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

// 一章の支度の段 (docs/design/34 の 4.2) の、クエストのデータの外にある仕上げ。
//   折れた剣   割れ殻の洞をクリアすると (初回も周回も)、その場の人に頁 ch1/trail_blade (奥の台座の折れた鉄の剣) と伝承 ch1/cleft_cave。
//              trail_blade の最初の段は頁で数えるので、先に洞を抜けた人もさかのぼって済む
//   鯨追いの印  三本そろったとき (PrepQuests.onReady。人ごとに一度)、記念品を一つ (見た目だけ。説明つき)
public final class Ch1Trails {
    private Ch1Trails() {
    }

    static void register() {
        PrepQuests.onReady(1, Ch1Trails::giveMark);
        SiteSpawner.onCleared(Ch1Prep.CLEFT_CAVE, (p, first) -> {
            boolean fresh = !Lore.state(p).has(Ch1Prep.PAGE_BLADE);
            Lore.unlock(p, Ch1Prep.CLEFT_CAVE_LORE, false);
            if (fresh) {
                Notice.narrate(p, "message.skyabyss.ch1.cave.sword");
                Tasks.later(40, () -> Lore.unlock(p, Ch1Prep.PAGE_BLADE, true));
            }
        });
    }

    public static ItemStack mark() {
        ItemStack stack = new ItemStack(GuideSetup.WHALE_CHASER_MARK.get());
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.skyabyss.whale_chaser_mark.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))));
        return stack;
    }

    static void giveMark(ServerPlayer player) {
        ItemStack stack = mark();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        Notice.gain(player, "message.skyabyss.ch1.trail.mark");
    }
}
