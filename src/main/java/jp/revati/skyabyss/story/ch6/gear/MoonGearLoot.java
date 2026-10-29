package jp.revati.skyabyss.story.ch6.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 月の装備の出どころ (docs/design/03 の 12.2)。抽選は一人ずつ。記録は巨神の記憶 (TitanMemoryData) の "moon" の頁 (図鑑・天井・交換)。
//  - 区画の主 (谷・崖・野・淵の主の間の onDeath): 月の欠片 3〜5 と共鳴の欠片 1、その主の鎧の一部位 (まだ持っていなければ必ず。持っていれば欠片 +4)
//  - 月の核 (CoreArena.onDeath) と位相の挑戦 (MoonTrial): 欠片 4〜7 (+段)、共鳴の欠片 2 (+段 ÷ 2)、心核 (初回必ず・以後 25%)、図鑑の品・月の涙 30%
//    位相の挑戦の区画の主は、主の分 (rollLord) に段の欠片を足し、図鑑の品を一回 (確率 × (1 + 0.1 × 段))
public final class MoonGearLoot {
    public static final float CRYSTAL_CHANCE = 0.40f;

    private MoonGearLoot() {
    }

    private static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static Optional<Item> item(String id) {
        return TitanGearSet.item(id);
    }

    private static void add(List<ItemStack> out, String id, int count) {
        item(id).ifPresent(i -> out.add(new ItemStack(i, count)));
    }

    private static void addGear(List<ItemStack> out, String id, RandomSource random) {
        item(id).ifPresent(i -> out.add(AffixRules.withAffixes(new ItemStack(i), random)));
    }

    // ---------------------------------------------------------------- 区画の主
    public static List<ItemStack> rollLord(TitanMemoryData.Record r, String lord, int tier, RandomSource random, Predicate<String> available) {
        List<ItemStack> out = new ArrayList<>();
        int fragments = between(random, 3, 5) + tier;
        String piece = MoonGear.lordPiece(lord);
        if (!r.owned.contains(piece)) {
            addGear(out, piece, random);
            MoonGear.SET.record(r, List.of(piece));
        } else {
            fragments += 4;
        }
        add(out, MoonGear.SHARD, fragments);
        add(out, MoonGear.RESONANCE_SHARD, 1 + tier / 2);
        if (tier > 0) {
            r.memory++;
            TitanGearSet.Pieces got = MoonGear.SET.rollPieces(r, r.memory, random, available, 1 + 0.1 * tier);
            for (String p : got.pieces()) {
                addGear(out, p, random);
            }
            MoonGear.SET.record(r, got.pieces());
        }
        return out;
    }

    public static void lord(ServerLevel level, String lord, int tier, List<ServerPlayer> in) {
        MinecraftServer server = level.getServer();
        TitanMemoryData memory = TitanMemoryData.get(server);
        for (ServerPlayer p : in) {
            TitanMemoryData.Record r = memory.record(p.getUUID(), MoonGear.DUNGEON);
            List<ItemStack> items = rollLord(r, lord, tier, level.random, TitanGearSet::registered);
            int held = TitanLoot.handOver(server, p.getUUID(), p, items, 0);
            tell(p, items, "message.skyabyss.moon_gear.lord_reward");
            TitanLoot.tellHeld(p, held);
        }
        memory.setDirty();
    }

    // ---------------------------------------------------------------- 月の核 (と核の挑戦)
    public record Roll(List<String> pieces, int fragments, boolean core, boolean tear, List<String> heroics) {
    }

    // 別枠のヒロイック 5 本: 一本ずつ HEROIC_CHANCE × (1 + 0.1 × 段)。図鑑の周回の数には入らない (巨神のヒロイック武器と同じ)
    public static List<String> heroicDrops(int tier, RandomSource random, Predicate<String> available) {
        List<String> out = new ArrayList<>();
        for (String id : MoonGear.HEROICS) {
            if (available.test(id) && random.nextFloat() < MoonGear.HEROIC_CHANCE * (1 + 0.1 * tier)) {
                out.add(id);
            }
        }
        return out;
    }

    public static Roll rollCore(TitanMemoryData.Record r, int tier, RandomSource random, Predicate<String> available, List<ItemStack> stacks) {
        r.memory++;
        r.clears++;
        int fragments = between(random, 4, 7) + tier;
        add(stacks, MoonGear.SHARD, fragments);
        add(stacks, MoonGear.RESONANCE_SHARD, 2 + tier / 2);
        boolean core = r.clears == 1 || random.nextFloat() < MoonGear.CORE_CHANCE;
        if (core) {
            add(stacks, MoonGear.CORE, 1);
        }
        boolean tear = random.nextFloat() < MoonGear.TEAR_CHANCE;
        if (tear) {
            add(stacks, MoonGear.TEAR, 1);
        }
        if (random.nextFloat() < CRYSTAL_CHANCE) {
            stacks.add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get()));
        }
        TitanGearSet.Pieces got = MoonGear.SET.rollPieces(r, r.memory, random, available, 1 + 0.1 * tier);
        if (got.pityShards()) {
            add(stacks, MoonGear.RESONANCE_SHARD, TitanLoot.PITY_SHARDS);
        }
        for (String piece : got.pieces()) {
            addGear(stacks, piece, random);
        }
        MoonGear.SET.record(r, got.pieces());
        r.firstCleared = true;
        List<String> heroics = new ArrayList<>();
        for (String id : heroicDrops(tier, random, available)) {
            addGear(stacks, id, random);
            heroics.add(id);
        }
        return new Roll(got.pieces(), fragments, core, tear, heroics);
    }

    public static void core(ServerLevel level, List<ServerPlayer> players, int tier) {
        MinecraftServer server = level.getServer();
        TitanMemoryData memory = TitanMemoryData.get(server);
        for (ServerPlayer p : players) {
            TitanMemoryData.Record r = memory.record(p.getUUID(), MoonGear.DUNGEON);
            List<ItemStack> items = new ArrayList<>();
            rollCore(r, tier, level.random, TitanGearSet::registered, items);
            int held = TitanLoot.handOver(server, p.getUUID(), p, items, 0);
            tell(p, items, "message.skyabyss.moon_gear.core_reward");
            TitanLoot.tellHeld(p, held);
        }
        memory.setDirty();
    }

    private static void tell(ServerPlayer p, List<ItemStack> items, String key) {
        List<Component> names = new ArrayList<>();
        for (ItemStack s : items) {
            if (s.getItem() instanceof jp.revati.skyabyss.item.ModGear) {
                names.add(s.getHoverName());
            }
        }
        if (!names.isEmpty()) {
            Component list = net.minecraft.network.chat.ComponentUtils.formatList(names, Component.literal("、"));
            Notice.alert(p, key, list);
        }
    }
}
