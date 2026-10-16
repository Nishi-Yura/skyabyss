package jp.revati.skyabyss.titan.tide.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanMemoryData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

// 潮殻の装備の、心臓より前の出どころ (08 の 6.1・6.2節)。抽選は一人ずつで、出た品は図鑑に記す (岩鯨の番兵・番兵長と同じ)。
// 記憶 (天井のカウンタ) は進めない。初めての品なら 0 に戻す (TitanGearSet.record)。
//  - 珊瑚憑き (道中): 潮殻の鎧のどれか一部位 1% (TideMobLoot から)
//  - 潮招き (中ボス): 潮招きの大鋏 5% (TideFlats から)
// 心臓の表は titan/tide/heart/TideLoot (TideGear.SET の確率を使う)
public final class TideGearLoot {
    private TideGearLoot() {
    }

    private static TitanMemoryData.Record record(MinecraftServer server, UUID player) {
        return TitanMemoryData.get(server).record(player, TideGear.TITAN);
    }

    // 出た図鑑の品を作り (特性はここで決まる)、記す
    private static List<ItemStack> give(TitanMemoryData.Record r, List<String> got, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        for (String id : got) {
            TitanGearSet.item(id).ifPresent(item -> out.add(AffixRules.withAffixes(new ItemStack(item), random)));
        }
        if (r != null) {
            TideGear.SET.record(r, got.stream().filter(TitanGearSet::registered).toList());
        }
        return out;
    }

    // 珊瑚憑きを倒した人への、図鑑の品の抽選 (欠片などは TideMobLoot の表)
    public static List<ItemStack> rollThrall(MinecraftServer server, UUID killer, RandomSource random) {
        if (random.nextFloat() >= TideGear.THRALL_ARMOR_CHANCE) {
            return List.of();
        }
        String piece = TideGear.ARMOR.get(random.nextInt(TideGear.ARMOR.size()));
        return give(server == null ? null : record(server, killer), List.of(piece), random);
    }

    // 潮招きを倒した参加者一人への、図鑑の品の抽選
    public static List<ItemStack> rollBeckoner(MinecraftServer server, UUID player, RandomSource random) {
        if (random.nextFloat() >= TideGear.BECKONER_CLAW_CHANCE) {
            return List.of();
        }
        return give(server == null ? null : record(server, player), List.of(TideGear.CLAW), random);
    }
}
