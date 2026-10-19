package jp.revati.skyabyss.abyss;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.vow.Vows;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// 深淵の心臓の報酬 (docs/design/31 の B04)。倒した人それぞれに:
//   共鳴の欠片 ×HEART_SHARDS (必ず) と、専用の伝説 (その巨神の 1 点) が LEGEND_CHANCE。LEGEND_PITY 回続けて出なければ次で保証 (天井。巨神ごと)
//   すでに持っているときは、伝説の分解と同じ量 (共鳴の欠片 ×DUPE_SHARDS) に替わる
// 記録は巨神の記憶 (TitanMemoryData) に "abyss:<巨神>" の名前で (通常の天井・図鑑とは別。通常の心臓の報酬は変わらない)。
public final class AbyssLoot {
    public static final int HEART_SHARDS = 2;
    public static final int DUPE_SHARDS = 10;
    public static final String KEY = "abyss:";

    private AbyssLoot() {
    }

    // 抽選の結果。legend が null なら出なかった
    public record Roll(@Nullable String legend, boolean dupe, boolean pity) {
        public boolean dropped() {
            return legend != null;
        }
    }

    public static String titanOf(String theme) {
        return switch (theme) {
            case "tide" -> Titans.TIDE_SHELL;
            case "storm" -> Titans.THUNDER_WING;
            case "ember" -> Titans.EMBER;
            default -> Titans.STONE_WHALE;
        };
    }

    // 記録 r は書き換える (呼ぶ側が保存する)。純粋な数え (ゲームなしで試せる)
    public static Roll roll(TitanMemoryData.Record r, String titan, RandomSource random) {
        r.clears++;
        r.memory++;
        boolean pity = r.memory >= Abyss.LEGEND_PITY;
        if (!pity && random.nextFloat() >= Abyss.LEGEND_CHANCE) {
            return new Roll(null, false, false);
        }
        r.memory = 0;
        String id = ResourceLocation.fromNamespaceAndPath(jp.revati.skyabyss.SkyAbyss.MODID, AbyssSetup.legendOf(titan)).toString();
        boolean dupe = !r.owned.add(id);
        return new Roll(id, dupe, pity);
    }

    // 深淵の心臓が倒れた (HeartBoss の倒れる演出の終わり)。近くの、その心臓が見える人へ
    public static void onDefeat(ServerLevel level, HeartBoss heart) {
        MinecraftServer server = level.getServer();
        String titan = titanOf(heart.abyssThemeId());
        List<ServerPlayer> players = level.getPlayers(p -> !(p instanceof FakePlayer) && !p.isSpectator() && p.distanceToSqr(heart) < 80 * 80
                && GroupOwned.visibleTo(heart, p));
        TitanMemoryData memory = TitanMemoryData.get(server);
        for (ServerPlayer p : players) {
            List<ItemStack> items = new ArrayList<>();
            items.add(new ItemStack(Vows.resonanceShardOf(titan), HEART_SHARDS));
            Roll roll = roll(memory.record(p.getUUID(), KEY + titan), titan, level.random);
            ItemStack legend = ItemStack.EMPTY;
            if (roll.dropped()) {
                if (roll.dupe()) {
                    items.add(new ItemStack(Vows.resonanceShardOf(titan), DUPE_SHARDS));
                } else {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(roll.legend()));
                    legend = new ItemStack(item);
                    items.add(legend);
                }
            }
            TitanLoot.handOver(server, p.getUUID(), p, items, 0);
            Notice.gain(p, TitanLoot.rewardLine(items));
            if (!legend.isEmpty()) {
                Notice.alert(p, "message.skyabyss.abyss.legend_new", TitanLoot.itemLink(legend));
            } else if (roll.dropped()) {
                Notice.hint(p, "message.skyabyss.abyss.legend_dupe");
            }
        }
    }
}
