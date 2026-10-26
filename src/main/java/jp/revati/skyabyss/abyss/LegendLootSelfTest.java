package jp.revati.skyabyss.abyss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 伝説の装備の出どころの検査 (abyss/LegendLoot)。SKYABYSS_SELFTEST_ONLY="legend loot"。
//   伝説の階級の品は一覧に全部入る・巨神の図鑑には無い・まだ持っていない品が先・職の寄り・確率・王の褒美の箱だけ・深い所だけ
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
            if (it instanceof ModGear g && g.tier() == GearTier.ARTIFACT && id.startsWith(SkyAbyss.MODID + ":")) {
                check(f, pool.contains(id), "legendary " + id + " is not in the legend pool");
                for (jp.revati.skyabyss.item.TitanGearSet set : jp.revati.skyabyss.item.TitanGearSets.all()) {
                    check(f, !set.contains(id), "legendary " + id + " is in the " + set.titan + " codex (titans must not drop legends)");
                }
            }
        }
        // まだ持っていない品が先
        Set<String> owned = new HashSet<>(pool.subList(0, pool.size() - 1));
        Random r = new Random(3);
        check(f, LegendLoot.pick(pool, owned, null, r::nextDouble).equals(pool.get(pool.size() - 1)), "an unowned legend was not picked first");
        // 職の寄り: 呪術師なら半分以上が呪術師の伝説
        int mine = 0;
        for (int i = 0; i < 400; i++) {
            if (Job.of(LegendLoot.item(LegendLoot.pick(pool, Set.of(), Job.SHAMAN, r::nextDouble))) == Job.SHAMAN) {
                mine++;
            }
        }
        check(f, mine > 200, "the job lean is too weak: " + mine + " / 400");
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
            SkyAbyss.LOGGER.info("SELFTEST legend loot PASSED ({} legends; none in titan codexes, unowned first, job lean, nightmare rises, deep king chests only)", pool.size());
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST legend loot FAILED: {}", x));
        }
    }
}
