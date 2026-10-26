package jp.revati.skyabyss.abyss;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 伝説の装備の出どころ (2026-10-26 の声「伝説は巨神では出ずに、深淵以降やエンドコンテンツで」)。巨神の心臓 (深淵の心臓も) からは出ない。
//  - 心臓の番人 (五章の本戦): WARDEN_CHANCE。悪夢の段: 段が上がるほど (NIGHTMARE_BASE + 段 × NIGHTMARE_STEP)。PITY 回続けて出なければ次で必ず
//  - 果てない深みの王の褒美の箱: 深いほど (CHEST_PER_FLOOR × 階、CHEST_MAX まで)
// どの伝説が出るか: まだ持っていない品を先に。半分は今の職 (持った武器・祠で選んだ職) の伝説から。持っている品だけなら共鳴の欠片に替わる
public final class LegendLoot {
    public static final String KEY = "legend";
    public static final float WARDEN_CHANCE = 0.10f;
    public static final float NIGHTMARE_BASE = 0.12f;
    public static final float NIGHTMARE_STEP = 0.06f;
    public static final int PITY = 6;
    public static final double CHEST_PER_FLOOR = 0.002;
    public static final double CHEST_MAX = 0.12;
    public static final int CHEST_FLOOR = 20;
    public static final int DUPE_SHARDS = 10;

    private LegendLoot() {
    }

    // 伝説の装備の一覧 (品の id)。深淵の専用伝説 (AbyssSetup) のほか、増えた品もここへ
    public static List<String> pool() {
        List<String> out = new ArrayList<>();
        for (String id : AbyssSetup.legends()) {
            out.add(SkyAbyss.MODID + ":" + id);
        }
        return out;
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    // 番人・悪夢の 1 回の分の確率 (tier 0 = 本戦)
    public static float chance(int tier) {
        return tier <= 0 ? WARDEN_CHANCE : Math.min(0.5f, NIGHTMARE_BASE + tier * NIGHTMARE_STEP);
    }

    // どの伝説か (まだ持っていない品を先に。job があれば半分はその職の品から)。純粋な部分 (自己テストが呼ぶ)
    public static String pick(List<String> pool, java.util.Set<String> owned, Job job, java.util.function.DoubleSupplier rnd) {
        List<String> fresh = pool.stream().filter(id -> !owned.contains(id)).toList();
        List<String> from = fresh.isEmpty() ? pool : fresh;
        if (job != null && rnd.getAsDouble() < 0.5) {
            List<String> mine = from.stream().filter(id -> Job.of(item(id)) == job).toList();
            if (!mine.isEmpty()) {
                from = mine;
            }
        }
        return from.get((int) Math.min(from.size() - 1, Math.floor(rnd.getAsDouble() * from.size())));
    }

    // 番人・悪夢を倒した一人へ。出たら手渡して知らせる
    public static void award(ServerPlayer p, TitanMemoryData memory, int tier, RandomSource random) {
        TitanMemoryData.Record r = memory.record(p.getUUID(), KEY);
        r.clears++;
        r.memory++;
        boolean pity = r.memory >= PITY;
        if (!pity && random.nextFloat() >= chance(tier)) {
            return;
        }
        r.memory = 0;
        Job job = Job.held(p) != null ? Job.held(p) : jp.revati.skyabyss.rpg.skill.JobChoice.chosen(p);
        String id = pick(pool(), r.owned, job, random::nextDouble);
        List<ItemStack> items = new ArrayList<>();
        boolean dupe = !r.owned.add(id);
        ItemStack legend = ItemStack.EMPTY;
        if (dupe) {
            items.add(new ItemStack(jp.revati.skyabyss.abyss.gear.AbyssGearSetup.RESONANCE_SHARD.get(), DUPE_SHARDS));
        } else {
            legend = AffixRules.withAffixes(new ItemStack(item(id)), random);
            items.add(legend);
        }
        TitanLoot.handOver(p.getServer(), p.getUUID(), p, items, 0);
        if (!legend.isEmpty()) {
            Notice.alert(p, "message.skyabyss.abyss.legend_new", TitanLoot.itemLink(legend));
        } else {
            Notice.hint(p, "message.skyabyss.abyss.legend_dupe");
        }
    }

    // 果てない深みの王の褒美の箱 (tier 3)。深いほど出やすい。箱の中なので誰のものでもない (職の寄りは無し)
    public static void chest(List<ItemStack> out, int floor, int tier, Random r) {
        if (tier < 3 || floor < CHEST_FLOOR) {
            return;
        }
        double c = Math.min(CHEST_MAX, CHEST_PER_FLOOR * floor);
        if (r.nextDouble() < c) {
            List<String> pool = pool();
            out.add(AffixRules.withAffixes(new ItemStack(item(pool.get(r.nextInt(pool.size())))), RandomSource.create(r.nextLong())));
        }
    }
}
