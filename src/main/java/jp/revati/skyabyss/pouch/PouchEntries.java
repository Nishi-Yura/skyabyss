package jp.revati.skyabyss.pouch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.pouch.PouchConfig.Category;
import jp.revati.skyabyss.record.Keepsakes;
import jp.revati.skyabyss.record.TitanPages;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.StorySetup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 袋に入れる品の登録の表 (docs/design/22 の 2・3 章)。品・区分・上限・「手に持たないと使えないか」。
// 並びは登録順で固定 (新しい品は区分の最後に足す)。守る品の一覧 (Keepsakes.guardedItems: 欠片・天の鍵・心核・呼び笛) と同じ登録から作る。
//
// 2.2 に照らして確かめた結果 (実装の確認):
//   手に持たないと使えない (use で右クリック。needsHand): 呼び笛・呼び貝 (TitanCallItem)、潮見の貝 (TideConchItem)、風見鶏 (WeathercockItem、
//     手に持つと帯に状況も出る)、伝承の書 (LoreBookItem)、破れた頁 (LorePageItem。読むと消え、頁ごとに出どころの印を持つ)。
//     袋から使う入力 (P6) ができるまで袋へは入れない (PouchConfig.USE_TOOLS_IN_POUCH)。
//   持たなくてよい: 天の鍵の欠片・天の鍵 (扉も仕掛けも鍵の品を要求しない。手に持つと足もとで脈を打つ演出だけが画面にある)、心核、共鳴の結晶・欠片、素材。
//   袋に入れない (背中の試練の道具。22 の 2.2 のとおり): 火の鍵 (焼き入れ前も後も。鍛え手の扉は手に持った鍵で開け、背中を出ると取り上げられる)、
//     灰の壺、熾、潮の真珠 (潮の庭の試練。22 の 2.1 の「潮の真珠」はこの品ではなく潮干しの貝などの小さな素材の意味に読む)、凪羽 (GaleFeather)。
public final class PouchEntries {
    private PouchEntries() {
    }

    // limit: 袋の中の上限。needsHand: 手に持って使う品。bonded: 持ち主に結ぶ品 (欠片・天の鍵。KeepsakeBonds)
    public record Entry(Item item, ResourceLocation id, Category category, int limit, boolean needsHand, boolean bonded) {
        public boolean enabled() {
            return PouchConfig.ENABLED_CATEGORIES.contains(category) && (!needsHand || PouchConfig.USE_TOOLS_IN_POUCH);
        }

        public boolean withdrawable() {
            return PouchConfig.WITHDRAW_CATEGORIES.contains(category);
        }
    }

    private static volatile List<Entry> all;
    private static volatile Map<Item, Entry> byItem;

    // 登録 (品の登録が済んでから一度だけ作る)
    public static List<Entry> all() {
        List<Entry> list = all;
        if (list == null) {
            synchronized (PouchEntries.class) {
                list = all;
                if (list == null) {
                    list = List.copyOf(build());
                    Map<Item, Entry> map = new LinkedHashMap<>();
                    list.forEach(e -> map.put(e.item(), e));
                    byItem = map;
                    all = list;
                }
            }
        }
        return list;
    }

    // 今の設定で入る品 (袋の画面の枠はこの並び)
    public static List<Entry> enabled() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) {
            if (e.enabled()) {
                out.add(e);
            }
        }
        return out;
    }

    // 品の登録。入らない品・今の設定で外れている品は null
    @Nullable
    public static Entry entry(Item item) {
        all();
        Entry e = byItem.get(item);
        return e != null && e.enabled() ? e : null;
    }

    @Nullable
    public static Entry entry(ItemStack stack) {
        return stack.isEmpty() ? null : entry(stack.getItem());
    }

    // 設定に関わらず登録にある品 (袋の画面の「？」の枠や、保存の読み替えで使う)
    @Nullable
    public static Entry any(Item item) {
        all();
        return byItem.get(item);
    }

    @Nullable
    public static Entry byId(ResourceLocation id) {
        for (Entry e : all()) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private static List<Entry> build() {
        List<Entry> list = new ArrayList<>();
        Item fragment = ModItems.SKY_KEY_FRAGMENT.get();
        Item key = StorySetup.SKY_KEY.get();
        // 使う道具: 呼び笛・呼び貝 (守る品の一覧から)、潮見の貝、風見鶏
        for (Item item : Keepsakes.guardedItems()) {
            if (item != fragment && item != key && !isCore(item)) {
                add(list, item, Category.TOOL, 1, true, false);
            }
        }
        add(list, jp.revati.skyabyss.titan.tide.chapter.TideChapterSetup.TIDE_WATCHING_SHELL.get(), Category.TOOL, 1, true, false);
        add(list, jp.revati.skyabyss.titan.storm.StormSetup.WEATHERCOCK.get(), Category.TOOL, 1, true, false);
        // 鍵と心核
        add(list, fragment, Category.KEY, 4, false, true);
        add(list, key, Category.KEY, 1, false, true);
        for (Item item : Keepsakes.guardedItems()) {
            if (isCore(item)) {
                add(list, item, Category.KEY, 99, false, false);
            }
        }
        // 共鳴
        add(list, ModItems.RESONANCE_CRYSTAL.get(), Category.RESONANCE, 999, false, false);
        add(list, ModItems.RESONANCE_SHARD.get(), Category.RESONANCE, 999, false, false);
        for (String titan : jp.revati.skyabyss.progress.Titans.all()) {
            add(list, ModItems.resonanceShard(titan), Category.RESONANCE, 999, false, false);
        }
        // 書
        add(list, jp.revati.skyabyss.lore.LoreSetup.LORE_BOOK.get(), Category.BOOK, 1, true, false);
        add(list, jp.revati.skyabyss.lore.LoreSetup.LORE_PAGE.get(), Category.BOOK, 99, true, false);
        // 巨神の素材
        add(list, ModItems.STONE_WHALE_SHARD.get(), Category.MATERIAL, 999, false, false);
        add(list, jp.revati.skyabyss.titan.tide.mob.TideMobSetup.TIDE_SHELL_SHARD.get(), Category.MATERIAL, 999, false, false);
        add(list, jp.revati.skyabyss.item.storm.StormGearSetup.SHARD.get(), Category.MATERIAL, 999, false, false);
        add(list, jp.revati.skyabyss.titan.ember.gear.EmberGearSetup.SHARD.get(), Category.MATERIAL, 999, false, false);
        // 序盤の背中の素材 (岩殻片・護り糸)。作業台も旅嚢の中から使える (22 の 17 章)。登録の末尾に足して既存の枠の位置を動かさない
        add(list, jp.revati.skyabyss.guide.GuideSetup.ROCK_SHELL_SCRAP.get(), Category.MATERIAL, 999, false, false);
        add(list, jp.revati.skyabyss.guide.GuideSetup.ROCK_SHELL_THREAD.get(), Category.MATERIAL, 999, false, false);
        // 小さな素材
        add(list, jp.revati.skyabyss.item.storm.StormGearSetup.DUST.get(), Category.SMALL, 999, false, false);
        add(list, jp.revati.skyabyss.titan.tide.mob.TideMobSetup.BUBBLE_SAC.get(), Category.SMALL, 999, false, false);
        add(list, jp.revati.skyabyss.titan.tide.mob.TideMobSetup.TIDE_DRIED_SHELL.get(), Category.SMALL, 999, false, false);
        add(list, jp.revati.skyabyss.titan.ember.gear.EmberGearSetup.SPARK_DUST.get(), Category.SMALL, 999, false, false);
        add(list, jp.revati.skyabyss.titan.ember.gear.EmberGearSetup.CINDER_HUSK.get(), Category.SMALL, 999, false, false);
        // 序盤の小さな素材 (骨片・棘針)
        add(list, jp.revati.skyabyss.guide.GuideSetup.BONE_CHIP.get(), Category.SMALL, 999, false, false);
        add(list, jp.revati.skyabyss.guide.GuideSetup.HARE_QUILL.get(), Category.SMALL, 999, false, false);
        return list;
    }

    private static boolean isCore(Item item) {
        if (item == ModItems.STONE_WHALE_CORE.get()) {
            return true;
        }
        for (String titan : jp.revati.skyabyss.progress.Titans.all()) {
            TitanPages.Page page = TitanPages.page(titan);
            if (page != null && page.coreItem() == item) {
                return true;
            }
        }
        return false;
    }

    private static void add(List<Entry> list, Item item, Category category, int limit, boolean needsHand, boolean bonded) {
        for (Entry e : list) {
            if (e.item() == item) {
                return;
            }
        }
        list.add(new Entry(item, BuiltInRegistries.ITEM.getKey(item), category, limit, needsHand, bonded));
    }
}
