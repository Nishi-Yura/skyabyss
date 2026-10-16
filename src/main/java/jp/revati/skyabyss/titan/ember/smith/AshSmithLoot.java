package jp.revati.skyabyss.titan.ember.smith;

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
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.gear.EmberGear;
import jp.revati.skyabyss.titan.ember.gear.EmberGearLoot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 灰の鍛え手の報酬 (13 の 6.2節)。必ず出る品はない。参加者それぞれに別々に抽選し、持ち物へ直接入れる (受け取れない人の分は預かる)。
//   経験値 50 (バニラ。必ず) / 余燼の欠片 60% 2〜4 / 共鳴の欠片 35% 1〜2 / 熾火の角杯 20% 1〜2 / 鍛え手の大鎚 5% (専用伝説。EmberGearLoot で特性を付け、図鑑に記す)
// 品は ID で持つ (EmberGear の ID)。まだ登録されていない品は抽選から外す。成長の経験値は 220 (初回 +100、推奨 Lv 30)
public final class AshSmithLoot {
    public static final int VANILLA_XP = 50;
    public static final String SIGNAL = "ash_smith_stilled";

    public static final String SHARD = EmberGear.SHARD;
    public static final String RESONANCE_SHARD = SkyAbyss.MODID + ":resonance_shard";
    public static final String HORN = SkyAbyss.MODID + ":" + EmberIds.EMBER_HORN;
    public static final String HAMMER = EmberGear.HAMMER;
    public static final float HAMMER_CHANCE = EmberGear.SMITH_HAMMER_CHANCE;

    public record Entry(String id, float chance, int min, int max) {
    }

    public static final List<Entry> TABLE = List.of(
            new Entry(SHARD, 0.60f, 2, 4),
            new Entry(RESONANCE_SHARD, 0.35f, 1, 2),
            new Entry(HORN, 0.20f, 1, 2));

    private AshSmithLoot() {
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
            for (Draw d : roll(level.random, AshSmithLoot::registered)) {
                item(d.id()).ifPresent(i -> items.add(new ItemStack(i, d.count())));
            }
            items.addAll(EmberGearLoot.rollSmith(server, id, level.random));
            int held = TitanLoot.handOver(server, id, p, items, VANILLA_XP);
            if (p == null) {
                continue;
            }
            present.add(p);
            int amount = (int) Math.round(EmberSmithSetup.GROWTH_XP * GrowthRules.levelFalloff(Growth.get(p).level, EmberSmithSetup.CONTENT_LEVEL));
            if (Growth.discover(p, "first:" + SkyAbyss.MODID + ":" + EmberIds.ASH_SMITH)) {
                amount += EmberSmithSetup.FIRST_BONUS;
            }
            Growth.addXp(p, amount, "entity.skyabyss.ash_smith");
            jp.revati.skyabyss.quest.Quests.signal(p, SIGNAL);
            p.sendSystemMessage(ModText.progress("message.skyabyss.ember.smith.defeated"));
            if (!items.isEmpty()) {
                p.sendSystemMessage(TitanLoot.rewardLine(items));
            }
            TitanLoot.tellHeld(p, held);
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(server);
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, present)) {
            Growth.addXp(mate, (int) Math.round(EmberSmithSetup.GROWTH_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level,
                    EmberSmithSetup.CONTENT_LEVEL)), "growth.skyabyss.reason.party");
        }
    }
}
