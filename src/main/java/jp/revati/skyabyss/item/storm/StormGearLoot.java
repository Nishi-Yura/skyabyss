package jp.revati.skyabyss.item.storm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanMemoryData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

// 雷翼の装備の、心臓より前の出どころ (12 の 6.1・6.2節)。潮殻の TideGearLoot と同じ形: 抽選は一人ずつで、出た品は特性を付けて図鑑に記す。
// 記憶 (天井のカウンタ) は進めない。初めての品なら 0 に戻す (TitanGearSet.record)。
//  - 羽守 (道中): 雷翼の羽衣のどれか一部位 1% (敵の担当の StormMobLoot から 1 行で呼ぶ: 今は ID で羽衣を落とすので、図鑑に記されない)
//  - 雷獣 (中ボス): 雷獣の爪 5% (中ボスの担当の RaijuLoot から 1 行で呼ぶ: 同上)
// 心臓の表は心臓の担当の StormLoot (StormGear.SET の collection / chance / rollPieces を使う)
public final class StormGearLoot {
    private StormGearLoot() {
    }

    private static TitanMemoryData.Record record(MinecraftServer server, UUID player) {
        return TitanMemoryData.get(server).record(player, StormGear.TITAN);
    }

    // 出た図鑑の品を作り (特性はここで決まる)、記す
    public static List<ItemStack> give(TitanMemoryData.Record r, List<String> got, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        for (String id : got) {
            TitanGearSet.item(id).ifPresent(item -> out.add(AffixRules.withAffixes(new ItemStack(item), random)));
        }
        if (r != null) {
            StormGear.SET.record(r, got.stream().filter(TitanGearSet::registered).toList());
        }
        return out;
    }

    // 羽守を倒した人への、図鑑の品の抽選
    public static List<ItemStack> rollWarden(MinecraftServer server, UUID killer, RandomSource random) {
        if (random.nextFloat() >= StormGear.WARDEN_ARMOR_CHANCE) {
            return List.of();
        }
        String piece = StormGear.ARMOR.get(random.nextInt(StormGear.ARMOR.size()));
        return give(server == null ? null : record(server, killer), List.of(piece), random);
    }

    // 雷獣を倒した参加者一人への、図鑑の品の抽選
    public static List<ItemStack> rollRaiju(MinecraftServer server, UUID player, RandomSource random) {
        if (random.nextFloat() >= StormGear.RAIJU_CLAW_CHANCE) {
            return List.of();
        }
        return give(server == null ? null : record(server, player), List.of(StormGear.CLAW), random);
    }
}
