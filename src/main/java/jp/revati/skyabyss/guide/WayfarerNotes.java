package jp.revati.skyabyss.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// 旅の覚書 (docs/design/21 の 1.4。`wayfarer_notes`)。章ごとに 1 冊の薄い手帳。
//  - 渡る: 備えの頼み 1 (ch<n>/prep_gear) を受けたとき、人ごとに一度 (PrepQuests.onStarted)。会話の結果 give_notes でも渡せる (なくした人に職人が渡し直す。
//    渡し直しは give(…, true))
//  - 開く: 右クリック。そのとき、その章のレシピを解く (registerRecipes で足した分。新しい画面は作らない。レシピブックの解放)。
//    手帳の中身 (敵の見分け方・備えの一行ずつ) は、文のキー wayfarer_notes.ch<n>.line.<i> (i = 1, 2, …。途切れるまで) を画面に並べる
//  - 序章 (0) は野営地の帳面のそば (21 の 2 章、P23)。レシピの解放はなし
// レシピを実際に「解くまで作れない」ようにするのは、旅装・砥石の担当 (P5) が、ゲームルール doLimitedCrafting か自前の検査でする
// (ここは解く口だけを持つ)
public final class WayfarerNotes {
    public static final int MAX_CHAPTER = 4;

    private static final Map<Integer, List<ResourceLocation>> RECIPES = new ConcurrentHashMap<>();
    private static final List<ResourceLocation> EMPTY = List.of();

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

    // ---------------------------------------------------------------- レシピ
    public static void registerRecipes(int chapter, ResourceLocation... ids) {
        List<ResourceLocation> list = RECIPES.computeIfAbsent(chapter, k -> new CopyOnWriteArrayList<>());
        for (ResourceLocation id : ids) {
            if (!list.contains(id)) {
                list.add(id);
            }
        }
    }

    public static List<ResourceLocation> recipes(int chapter) {
        return RECIPES.getOrDefault(chapter, EMPTY);
    }

    // 自己テスト用: 足したレシピを消す
    public static void clearRecipes(int chapter) {
        RECIPES.remove(chapter);
    }

    // その章のレシピを解く。新しく解けた数を返す (存在しないレシピは数えない)
    public static int unlockRecipes(ServerPlayer player, int chapter) {
        List<ResourceLocation> fresh = new ArrayList<>();
        var manager = player.server.getRecipeManager();
        for (ResourceLocation id : recipes(chapter)) {
            var holder = manager.byKey(id);
            if (holder.isPresent() && !player.getRecipeBook().contains(holder.get())) {
                fresh.add(id);
            }
        }
        if (!fresh.isEmpty()) {
            player.awardRecipesByKey(fresh);
        }
        return fresh.size();
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

    // 開く (サーバー側)。レシピを解き、解けた数があれば一行知らせる
    public static void onOpen(ServerPlayer player, int chapter) {
        int n = unlockRecipes(player, chapter);
        if (n > 0) {
            Notice.hint(player, "message.skyabyss.notes.recipes", n);
        }
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
