package jp.revati.skyabyss.titan.ember.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanMemoryData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

// 余燼の装備の、心臓より前の出どころ (13 の 6.1・6.2節)。抽選は一人ずつで、出た品は図鑑に記す (潮殻の TideGearLoot と同じ)。
// 記憶 (天井のカウンタ) は進めない。初めての品なら 0 に戻す (TitanGearSet.record)。
//  - 灰被り (道中、D の EmberMobLoot から 1 行): 余燼の鎧のどれか一部位 1%
//  - 灰の鍛え手 (中ボス、E から参加者ごとに 1 行): 鍛え手の大鎚 5%
// 心臓の表は F の EmberLoot が EmberGear.SET (確率・図鑑・専用の逸品) を読む
public final class EmberGearLoot {
    private EmberGearLoot() {
    }

    @Nullable
    private static TitanMemoryData.Record record(@Nullable MinecraftServer server, @Nullable UUID player) {
        return server == null || player == null ? null : TitanMemoryData.get(server).record(player, EmberGear.TITAN);
    }

    // 出た図鑑の品を作り (特性はここで決まる)、記す
    private static List<ItemStack> give(@Nullable TitanMemoryData.Record r, List<String> got, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        for (String id : got) {
            TitanGearSet.item(id).ifPresent(item -> out.add(AffixRules.withAffixes(new ItemStack(item), random)));
        }
        if (r != null) {
            EmberGear.SET.record(r, got.stream().filter(TitanGearSet::registered).toList());
        }
        return out;
    }

    // 灰被りを倒した人への、図鑑の品の抽選 (欠片・角杯・頁は D の表)
    public static List<ItemStack> rollThrall(@Nullable MinecraftServer server, @Nullable UUID killer, RandomSource random) {
        if (random.nextFloat() >= EmberGear.THRALL_ARMOR_CHANCE) {
            return List.of();
        }
        String piece = EmberGear.ARMOR.get(random.nextInt(EmberGear.ARMOR.size()));
        return give(record(server, killer), List.of(piece), random);
    }

    // 灰の鍛え手を倒した参加者一人への、図鑑の品の抽選
    public static List<ItemStack> rollSmith(@Nullable MinecraftServer server, @Nullable UUID player, RandomSource random) {
        if (random.nextFloat() >= EmberGear.SMITH_HAMMER_CHANCE) {
            return List.of();
        }
        return give(record(server, player), List.of(EmberGear.HAMMER), random);
    }
}
