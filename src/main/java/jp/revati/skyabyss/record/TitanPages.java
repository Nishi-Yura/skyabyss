package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.tide.heart.TideLoot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

// 控え帳の「巨神」のつまみに出す、巨神ごとの頁の中身 (図鑑・心核・炉の交換)。11 の 3 章。
// 巨神の一覧そのものは progress.Titans (ProgressRules.TITAN_ORDER) で、つまみの頁は一覧の順に自動で並ぶ。
// ここに登録のない巨神 (本体がまだない雷翼・余燼) は、頁の枠と「まだ見ぬ巨神」の伏せ字だけを出す。
//
// 後の巨神の担当へ: 図鑑を作ったら、起動時 (FMLCommonSetupEvent の enqueueWork など) に一行
//     TitanPages.register(new TitanPages.Page(Titans.THUNDER_WING, () -> 図鑑の品, 品 -> 専用伝説か, 品 -> 心臓で出る率, () -> 心核, false));
// を呼ぶ。巨神の記憶 (TitanMemoryData) の記録の名前は巨神の名前と同じにする (天井・撃破数・図鑑・欠片はそこから読む)
public final class TitanPages {
    // collection: 図鑑の品 (まだ作られていない品は入れない)。exclusive: 正体を伏せる品 (専用伝説)
    // chance: 心臓の一回で出る率 (天井の倍率をかける前)。core: 心核 (なければ null)。exchange: 共鳴の炉で交換できるか
    public record Page(String titan, Supplier<List<Item>> collection, Predicate<Item> exclusive, ToDoubleFunction<Item> chance,
                       Supplier<Item> core, boolean exchange) {
        @Nullable
        public Item coreItem() {
            return core.get();
        }
    }

    private static final Map<String, Page> PAGES = new LinkedHashMap<>();

    static {
        register(new Page(TitanLoot.STONE_WHALE, TitanLoot::collection, TitanLoot::isExclusive, TitanLoot::heartChance,
                () -> ModItems.STONE_WHALE_CORE.get(), true));
        // 潮殻の図鑑は品の名前で決まっている (装備の担当が並行で作っている)。作られた品だけを並べる
        register(new Page(TideLoot.TITAN, () -> items(TideLoot.collection()), item -> TideLoot.EXCLUSIVES.contains(TitanLoot.id(item)),
                item -> TideLoot.chance(TitanLoot.id(item)), () -> item(TideLoot.CORE).orElse(null), false));
    }

    private TitanPages() {
    }

    public static synchronized void register(Page page) {
        PAGES.put(page.titan(), page);
    }

    @Nullable
    public static synchronized Page page(String titan) {
        return PAGES.get(titan);
    }

    static Optional<Item> item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
    }

    static List<Item> items(List<String> ids) {
        List<Item> out = new ArrayList<>();
        for (String id : ids) {
            item(id).ifPresent(out::add);
        }
        return out;
    }
}
