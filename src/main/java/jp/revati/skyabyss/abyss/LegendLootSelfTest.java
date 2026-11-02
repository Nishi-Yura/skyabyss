package jp.revati.skyabyss.abyss;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 伝説の装備の出どころの検査 (abyss/LegendLoot)。SKYABYSS_SELFTEST_ONLY="legend loot"。
//   伝説の階級の品は一覧に全部入る・巨神の図鑑には無い・かぶりありの同じ割合・確率・王の褒美の箱だけ・深い所だけ
public final class LegendLootSelfTest {
    private LegendLootSelfTest() {
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> pool = LegendLoot.pool();
        for (Item it : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(it).toString();
            // 終章の神器は巡礼者の報酬 (story/finale/gear)。伝説の抽選には入れない
            if (it instanceof ModGear g && g.tier() == GearTier.ARTIFACT && id.startsWith(SkyAbyss.MODID + ":")
                    && !jp.revati.skyabyss.story.finale.gear.DivineGear.RELICS.contains(id)) {
                check(f, pool.contains(id), "legendary " + id + " is not in the legend pool");
                for (jp.revati.skyabyss.item.TitanGearSet set : jp.revati.skyabyss.item.TitanGearSets.all()) {
                    check(f, !set.contains(id), "legendary " + id + " is in the " + set.titan + " codex (titans must not drop legends)");
                }
            }
        }
        // かぶりあり・職に寄せない: どの品もおよそ同じ割合で出る
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        Random r = new Random(3);
        int n = 400 * pool.size();
        for (int i = 0; i < n; i++) {
            seen.merge(LegendLoot.pick(pool, r::nextDouble), 1, Integer::sum);
        }
        for (String id : pool) {
            int c = seen.getOrDefault(id, 0);
            check(f, c > 300 && c < 500, id + " came " + c + " times in " + n + " (want about 400)");
        }
        // 確率と箱
        check(f, LegendLoot.chance(0) == LegendLoot.WARDEN_CHANCE && LegendLoot.chance(3) > LegendLoot.chance(1), "nightmare tiers should raise the chance");
        List<ItemStack> chest = new ArrayList<>();
        Random cr = new Random(9);
        for (int i = 0; i < 2000; i++) {
            LegendLoot.chest(chest, 10, 3, cr);
            LegendLoot.chest(chest, 60, 2, cr);
        }
        check(f, chest.isEmpty(), "legends came from a shallow or non-king chest: " + chest.size());
        for (int i = 0; i < 2000; i++) {
            LegendLoot.chest(chest, 60, 3, cr);
        }
        check(f, chest.size() > 120 && chest.size() < 360, "deep king chests gave " + chest.size() + " legends in 2000 (want about 240)");
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST legend loot PASSED ({} legends; none in titan codexes, even odds with duplicates, nightmare rises, deep king chests only)", pool.size());
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST legend loot FAILED: {}", x));
        }
    }
}
