package jp.revati.skyabyss.abyss.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
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

// 深淵の装備の出どころ (03 の 12.3)。抽選は一人ずつ。記録は巨神の記憶 (TitanMemoryData) の "abyss" の頁 (図鑑・天井・交換)。
//  - 層の主 (AbyssLords.defeat): 錨鉄 3〜5 と、その層の鎧の一部位 (まだ持っていなければ必ず。持っていれば錨鉄 +4)
//  - 心臓の番人 (WardenArena.fallen) と番人の悪夢 (Nightmare.won): 巨神の心臓と同じ形 (図鑑 10 品の確率・天井・心核・共鳴の欠片)
//  - 果てない深みの箱 (DepthLoot.roll): 錨鉄と、宝の間・敵の巣・王の褒美に図鑑の品の機会
//  - 荷運びのロク (会話の取り替え) と作業台 (遺品をなぞる): 品の側は何もしない (data/skyabyss/dialogue/ch5・recipe)
public final class AbyssGearLoot {
    public static final float CRYSTAL_CHANCE = 0.40f;
    // 箱の図鑑の品 (tier: 0 ふつう・1 宝の間・2 敵の巣・3 王の褒美)
    public static final double[] CHEST_GEAR = {0.0, 0.04, 0.06, 0.15};

    private AbyssGearLoot() {
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

    // ---------------------------------------------------------------- 層の主
    // 一人ぶん (自己テストからも呼ぶ)。r: その人の深淵の記録
    public static List<ItemStack> rollLord(TitanMemoryData.Record r, String layer, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        int iron = between(random, 3, 5);
        String piece = AbyssGear.lordPiece(layer);
        if (!r.owned.contains(piece)) {
            addGear(out, piece, random);
            AbyssGear.SET.record(r, List.of(piece));
        } else {
            iron += 4;
        }
        add(out, AbyssGear.SHARD, iron);
        add(out, AbyssGear.RESONANCE_SHARD, 1);
        return out;
    }

    public static void lord(ServerLevel level, String layer, List<ServerPlayer> in) {
        MinecraftServer server = level.getServer();
        TitanMemoryData memory = TitanMemoryData.get(server);
        for (ServerPlayer p : in) {
            TitanMemoryData.Record r = memory.record(p.getUUID(), AbyssGear.DUNGEON);
            List<ItemStack> items = rollLord(r, layer, level.random);
            int held = TitanLoot.handOver(server, p.getUUID(), p, items, 0);
            tell(p, items, "message.skyabyss.abyss_gear.lord_reward");
            TitanLoot.tellHeld(p, held);
        }
        memory.setDirty();
    }

    // ---------------------------------------------------------------- 心臓の番人・悪夢
    public record Roll(List<String> pieces, List<String> fresh, int iron, boolean core, int memory) {
    }

    // 一人ぶんの撃破 (天井を進める)。tier: 悪夢の段 (普通の戦いは 0)。stacks に渡す品を足す
    public static Roll rollWarden(TitanMemoryData.Record r, int tier, RandomSource random, Predicate<String> available, List<ItemStack> stacks) {
        r.memory++;
        r.clears++;
        int memory = r.memory;
        int iron = between(random, 4, 7) + tier / 2;
        add(stacks, AbyssGear.SHARD, iron);
        add(stacks, AbyssGear.RESONANCE_SHARD, 2 + (tier >= 5 ? 1 : 0));
        boolean core = r.clears == 1 || random.nextFloat() < AbyssGear.CORE_CHANCE;
        if (core) {
            add(stacks, AbyssGear.CORE, 1);
        }
        if (random.nextFloat() < CRYSTAL_CHANCE) {
            stacks.add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get()));
        }
        TitanGearSet.Pieces got = AbyssGear.SET.rollPieces(r, memory, random, available);
        if (got.pityShards()) {
            add(stacks, AbyssGear.RESONANCE_SHARD, TitanLoot.PITY_SHARDS);
        }
        for (String piece : got.pieces()) {
            addGear(stacks, piece, random);
        }
        List<String> fresh = AbyssGear.SET.record(r, got.pieces());
        // 交換 (炉) は番人を一度倒してから
        r.firstCleared = true;
        return new Roll(got.pieces(), fresh, iron, core, memory);
    }

    public static void warden(ServerLevel level, List<ServerPlayer> players, int tier) {
        MinecraftServer server = level.getServer();
        TitanMemoryData memory = TitanMemoryData.get(server);
        for (ServerPlayer p : players) {
            TitanMemoryData.Record r = memory.record(p.getUUID(), AbyssGear.DUNGEON);
            List<ItemStack> items = new ArrayList<>();
            rollWarden(r, tier, level.random, TitanGearSet::registered, items);
            int held = TitanLoot.handOver(server, p.getUUID(), p, items, 0);
            tell(p, items, "message.skyabyss.abyss_gear.warden_reward");
            TitanLoot.tellHeld(p, held);
            // 伝説の装備の機会 (abyss/LegendLoot。巨神の心臓からは出ない)
            jp.revati.skyabyss.abyss.LegendLoot.award(p, memory, tier, level.random);
        }
        memory.setDirty();
        SkyAbyss.LOGGER.debug("abyss gear: warden rewards for {} players (tier {})", players.size(), tier);
    }

    // ---------------------------------------------------------------- 果てない深みの箱
    // DepthLoot.roll から。錨鉄 (ふつうの箱は 60%、ほかは必ず) と図鑑の品の機会。王の褒美だけ専用の逸品も出る (5 回に 1 回)
    public static void chest(List<ItemStack> out, int floor, int tier, Random r) {
        jp.revati.skyabyss.abyss.LegendLoot.chest(out, floor, tier, r);
        if (tier > 0 || r.nextDouble() < 0.6) {
            add(out, AbyssGear.SHARD, 1 + r.nextInt(2) + floor / 6 + tier);
        }
        double chance = CHEST_GEAR[Math.max(0, Math.min(CHEST_GEAR.length - 1, tier))];
        if (r.nextDouble() < chance) {
            List<TitanGearSet.Piece> pool = AbyssGear.SET.pieces().stream()
                    .filter(p -> !p.exclusive() || (tier >= 3 && r.nextInt(5) == 0)).toList();
            if (!pool.isEmpty()) {
                String id = pool.get(r.nextInt(pool.size())).id();
                item(id).ifPresent(i -> out.add(AffixRules.withAffixes(new ItemStack(i), RandomSource.create(r.nextLong()))));
            }
        }
    }

    // ---------------------------------------------------------------- 知らせ
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

    @Nullable
    static UUID uuid(@Nullable ServerPlayer p) {
        return p == null ? null : p.getUUID();
    }
}
