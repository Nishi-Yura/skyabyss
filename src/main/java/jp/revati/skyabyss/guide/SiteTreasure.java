package jp.revati.skyabyss.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.titan.group.FirstRun;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 地上の場所の宝。宝箱の中身は置かず、人ごとの一度きりの抽選で、倒した人・踏破した人の持ち物へ直接渡す
// (マルチで取り合いにならない。03 の 2.3 と同じ考え方)。
//   登録   SiteTreasure.register(id, Table)。Table = 必ず渡す品 (guaranteed) + 重みつきの抽選 (rolls 回、entries から)
//   受取   SiteTreasure.claim(player, id): その人が初めてなら抽選して渡し、渡した品を返す。受け取り済み・未登録は空。記録は SiteProgressData (人ごと)
//   周回   SiteTreasure.give(player, table): 受け取り記録を使わず何度でも渡す (周回の素材。場所の最後の部屋が戻ったあと)
// 巨神の装備 (ModGear)・武器・防具は入れない (追憶の宝と同じ。FirstRun.keepsakeAllowed)。備えの品・素材・小さな装飾だけ。品の ID は登録の時点で
// 引かず、渡すときに引く (あとから登録される品でも書ける。引けない品は渡さず記録に残す)
public final class SiteTreasure {
    public record Entry(String item, int min, int max, int weight) {
        public Entry(String item, int count) {
            this(item, count, count, 1);
        }
    }

    public record Table(List<Entry> guaranteed, int rollsMin, int rollsMax, List<Entry> entries) {
        public static Table of(List<Entry> guaranteed) {
            return new Table(guaranteed, 0, 0, List.of());
        }

        public static Table of(List<Entry> guaranteed, int rollsMin, int rollsMax, List<Entry> entries) {
            return new Table(guaranteed, rollsMin, rollsMax, entries);
        }
    }

    private static final Map<String, Table> TABLES = new ConcurrentHashMap<>();

    private SiteTreasure() {
    }

    public static void register(String id, Table table) {
        TABLES.put(id, table);
    }

    public static void unregister(String id) {
        TABLES.remove(id);
    }

    @Nullable
    public static Table table(String id) {
        return TABLES.get(id);
    }

    public static boolean claimed(ServerPlayer player, String id) {
        return SiteProgressData.get(player.server).hasTreasure(player.getUUID(), id);
    }

    // 抽選して渡す (一度きり)。渡した品を返す
    public static List<ItemStack> claim(ServerPlayer player, String id) {
        Table table = TABLES.get(id);
        if (table == null || !SiteProgressData.get(player.server).markTreasure(player.getUUID(), id)) {
            return List.of();
        }
        return give(player, table);
    }

    // 受け取り記録を見ずに渡す (周回の素材)
    public static List<ItemStack> give(ServerPlayer player, Table table) {
        List<ItemStack> stacks = roll(table, player.getRandom());
        for (ItemStack s : stacks) {
            Quests.give(player, s.copy());
        }
        return stacks;
    }

    // 抽選だけ (純粋。装備が混ざっていれば外す)
    public static List<ItemStack> roll(Table table, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        for (Entry e : table.guaranteed()) {
            add(out, e, random);
        }
        int total = table.entries().stream().mapToInt(e -> Math.max(0, e.weight())).sum();
        if (total > 0) {
            int rolls = table.rollsMin() + (table.rollsMax() > table.rollsMin() ? random.nextInt(table.rollsMax() - table.rollsMin() + 1) : 0);
            for (int i = 0; i < rolls; i++) {
                int pick = random.nextInt(total);
                for (Entry e : table.entries()) {
                    pick -= Math.max(0, e.weight());
                    if (pick < 0) {
                        add(out, e, random);
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static void add(List<ItemStack> out, Entry e, RandomSource random) {
        ResourceLocation key = ResourceLocation.tryParse(e.item());
        Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item == null) {
            return;
        }
        int count = e.min() + (e.max() > e.min() ? random.nextInt(e.max() - e.min() + 1) : 0);
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (!FirstRun.keepsakeAllowed(stack)) {
            SkyAbyss.LOGGER.error("Site treasure refused {}: gear is not allowed in a site treasure", e.item());
            return;
        }
        out.add(stack);
    }
}
