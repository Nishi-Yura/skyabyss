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
//   岩鯨を呼ぶ  三本そろうと、少しあとに岩鯨が初めて現れる (titan.TitanEvents。callReady)。それまで岩鯨は出ない (34 の 4.2、2026-10-22)
public final class Ch1Trails {
    private Ch1Trails() {
    }

    // 岩鯨を呼んでよい時刻 (人の UUID → ゲームの総 tick)。三本そろった合図から CALL_DELAY あと (最後の会話と札を読む間)。
    // 保存しない: 入り直した人・再起動のあとは、もう呼んでよいものとする
    static final long CALL_DELAY = 600L;
    private static final java.util.Map<java.util.UUID, Long> CALL_AT = new java.util.concurrent.ConcurrentHashMap<>();
    // 自己テストが差し替える口 (本番はパーティの誰かが三本を済ませたか)
    static java.util.function.Predicate<ServerPlayer> prepared = p -> PrepQuests.groupAtLeast(p, 1, 3);

    // 岩鯨の初登場を、この人のもとへ呼んでよいか (34 の 4.2: 一章の支度を全部済ませたあと)。
    // 一章を過ぎた人・もう岩鯨を見た人・序盤の前から遊んでいた人 (legacy)・支度の段の定義がないデータは、支度を問わない
    public static boolean callReady(ServerPlayer player) {
        if (jp.revati.skyabyss.story.Chapters.of(player).ordinal() > jp.revati.skyabyss.story.Chapters.WHALE.ordinal() || Lore.state(player).has(Ch1Descent.SEEN)
                || !PrepQuests.defined(1) || jp.revati.skyabyss.opening.OpeningDirector.state(player).legacy()) {
            return true;
        }
        if (!prepared.test(player)) {
            return false;
        }
        // 仲間のために呼ぶときも、最後に支度を終えた人の間を待つ (岩鯨はワールドに一体)
        long now = player.level().getGameTime();
        for (long at : CALL_AT.values()) {
            if (now < at) {
                return false;
            }
        }
        return true;
    }

    static void onReady(ServerPlayer player) {
        giveMark(player);
        CALL_AT.put(player.getUUID(), player.level().getGameTime() + CALL_DELAY);
        // 支度の山場: 遠い空の歌 (何が来るかは言わない)
        Tasks.later(200, () -> Notice.narrate(player, "message.skyabyss.ch1.trail.call"));
    }

    static void register() {
        PrepQuests.onReady(1, Ch1Trails::onReady);
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
