package jp.revati.skyabyss.guide;


import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// 旅の覚書 (`wayfarer_notes`)。章ごとに 1 冊の薄い手帳。
//  - 渡る: 備えの頼み 1 (ch<n>/prep_gear) を受けたとき、人ごとに一度 (PrepQuests.onStarted)。会話の結果 give_notes でも渡せる (なくした人に職人が渡し直す。
//    渡し直しは give(…, true))
//  - 開く: 右クリック。手帳の中身 (敵の見分け方・備えの一行ずつ) は、文のキー wayfarer_notes.ch<n>.line.<i> (i = 1, 2, …。途切れるまで) を画面に並べる
//  - 序章 (0) は野営地の帳面のそば (P23)
public final class WayfarerNotes {
    public static final int MAX_CHAPTER = 4;

    private WayfarerNotes() {
    }

    // ---------------------------------------------------------------- 品
    public static ItemStack make(int chapter) {
        ItemStack stack = new ItemStack(GuideSetup.WAYFARER_NOTES.get());
        stack.set(GuideSetup.NOTES_CHAPTER.get(), Math.max(0, Math.min(MAX_CHAPTER, chapter)));
        return stack;
    }

    public static int chapterOf(ItemStack stack) {
        return stack.getOrDefault(GuideSetup.NOTES_CHAPTER.get(), 0);
    }

    // ---------------------------------------------------------------- 渡す・開く
    // 人ごとに一度だけ渡す。渡したら true (すでに受け取った人には何もしない)
    public static boolean give(ServerPlayer player, int chapter) {
        return give(player, chapter, false);
    }

    // again = true は、なくした人への渡し直し (受け取り済みでも渡す。すでに持っていれば渡さない)
    public static boolean give(ServerPlayer player, int chapter, boolean again) {
        boolean first = Growth.discover(player, "notes:ch" + chapter);
        if (!first && (!again || has(player, chapter))) {
            return false;
        }
        Quests.give(player, make(chapter));
        Notice.hint(player, "message.skyabyss.notes.given", chapterName(chapter));
        return true;
    }

    // 持ち物にその章の覚書があるか
    public static boolean has(ServerPlayer player, int chapter) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(GuideSetup.WAYFARER_NOTES.get()) && chapterOf(s) == chapter) {
                return true;
            }
        }
        return false;
    }

    public static Component chapterName(int chapter) {
        Chapters c = Chapters.values()[Math.max(0, Math.min(MAX_CHAPTER, chapter))];
        return Component.translatable(c.tabKey());
    }

    // 手帳の行の翻訳キー (1 始まり)
    public static String lineKey(int chapter, int index) {
        return "wayfarer_notes.ch" + chapter + ".line." + index;
    }
}
