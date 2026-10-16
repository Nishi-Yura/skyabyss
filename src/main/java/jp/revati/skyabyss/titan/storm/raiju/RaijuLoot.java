package jp.revati.skyabyss.titan.storm.raiju;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 雷獣の報酬 (12 の 6.2節)。必ず出る品はない。参加者それぞれに別々に抽選し、持ち物へ直接入れる (受け取れない人の分は預かる)。
// 品は ID で持つ。まだ登録されていない品 (欠片・干し果は敵の担当、雷獣の爪は装備の担当が作る) は抽選から外す。
// 品が登録されれば、この表のまま出る
public final class RaijuLoot {
    public static final int VANILLA_XP = 50;
    // 成長の経験値 (6.6節): 170、初回 +80、推奨 Lv 24 から離れると減る
    public static final int GROWTH_XP = 170;
    public static final int FIRST_BONUS = 80;
    public static final int LEVEL = 24;
    public static final String SIGNAL = "raiju_stilled";

    public static final String SHARD = id("thunder_wing_shard");
    public static final String RESONANCE_SHARD = id("resonance_shard");
    public static final String FRUIT = id("cloudgap_fruit");
    public static final String CLAW = id("raiju_claw");

    public record Entry(String id, float chance, int min, int max) {
    }

    public static final List<Entry> TABLE = List.of(
            new Entry(SHARD, 0.60f, 2, 4),
            new Entry(RESONANCE_SHARD, 0.35f, 1, 2),
            new Entry(FRUIT, 0.20f, 1, 2),
            new Entry(CLAW, 0.05f, 1, 1));

    private RaijuLoot() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    public static Optional<Item> item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
    }

    public static boolean registered(String id) {
        return item(id).isPresent();
    }

    public record Draw(String id, int count) {
    }

    // 一人分の抽選 (available が false の品は引かない)
    public static List<Draw> roll(RandomSource random, Predicate<String> available) {
        List<Draw> out = new ArrayList<>();
        for (Entry e : TABLE) {
            if (!available.test(e.id())) {
                continue;
            }
            if (random.nextFloat() < e.chance()) {
                out.add(new Draw(e.id(), e.min() + random.nextInt(e.max() - e.min() + 1)));
            }
        }
        return out;
    }

    // 撃破の報酬を参加者へ (いない人の分は預かる)。パーティの仲間で居合わせなかった人には成長の経験値の分け前
    public static void onDefeat(ServerLevel level, Collection<UUID> ids) {
        MinecraftServer server = level.getServer();
        List<ServerPlayer> present = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            List<ItemStack> items = new ArrayList<>();
            for (Draw d : roll(level.random, RaijuLoot::registered)) {
                item(d.id()).ifPresent(i -> items.add(new ItemStack(i, d.count())));
            }
            int held = TitanLoot.handOver(server, id, p, items, VANILLA_XP);
            if (p == null) {
                continue;
            }
            present.add(p);
            int amount = (int) Math.round(GROWTH_XP * GrowthRules.levelFalloff(Growth.get(p).level, LEVEL));
            if (Growth.discover(p, "first:" + SkyAbyss.MODID + ":raiju")) {
                amount += FIRST_BONUS;
            }
            Growth.addXp(p, amount, "entity.skyabyss.raiju");
            jp.revati.skyabyss.quest.Quests.signal(p, SIGNAL);
            p.sendSystemMessage(ModText.progress("message.skyabyss.raiju.defeated"));
            if (!items.isEmpty()) {
                p.sendSystemMessage(TitanLoot.rewardLine(items));
            }
            TitanLoot.tellHeld(p, held);
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(server);
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, present)) {
            Growth.addXp(mate, (int) Math.round(GROWTH_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, LEVEL)),
                    "growth.skyabyss.reason.party");
        }
    }
}
