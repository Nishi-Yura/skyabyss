package jp.revati.skyabyss.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 巨神ごとの装備の表の一覧 (TitanGearSet)。共鳴の炉 (欠片・交換)、ツールチップの由来、記録の画面が読む。
// 岩鯨の表はここに書く (品は ModItems)。後の巨神は自分の Setup から register を呼ぶ (潮殻は titan/tide/gear/TideGearSetup)。
// 並びは登録の順 (炉の交換のつまみ・記録の画面の並び)。岩鯨がいつも先頭
public final class TitanGearSets {
    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    // 岩鯨 (02 の 6章、03 の 7.1節)。確率は TitanLoot の定数と同じ (TitanLoot の抽選はこの表を使わず、自分の定数で引く)
    public static final TitanGearSet STONE_WHALE = new TitanGearSet(TitanLoot.STONE_WHALE, id("stone_whale_shard"), id("stone_whale_core"), List.of(
            TitanGearSet.piece(id("stone_whale_helmet"), TitanLoot.ARMOR_CHANCE),
            TitanGearSet.piece(id("stone_whale_chestplate"), TitanLoot.ARMOR_CHANCE),
            TitanGearSet.piece(id("stone_whale_leggings"), TitanLoot.ARMOR_CHANCE),
            TitanGearSet.piece(id("stone_whale_boots"), TitanLoot.ARMOR_CHANCE),
            TitanGearSet.piece(id("whalebone_greatsword"), TitanLoot.GREATSWORD_CHANCE),
            TitanGearSet.piece(id("resonance_charm"), TitanLoot.CHARM_CHANCE),
            TitanGearSet.exclusive(id("captain_pike"), TitanLoot.PIKE_CHANCE),
            TitanGearSet.exclusive(id("waterway_fang"), TitanLoot.FANG_CHANCE),
            TitanGearSet.exclusive(id("stone_whale_plating"), TitanLoot.PLATING_CHANCE)));

    private static final List<TitanGearSet> SETS = new ArrayList<>(List.of(STONE_WHALE));

    private TitanGearSets() {
    }

    // 巨神の表を足す (Mod のコンストラクタから。同じ巨神を二度足すと前のものを置き換える)
    public static synchronized void register(TitanGearSet set) {
        SETS.removeIf(s -> s.titan.equals(set.titan));
        SETS.add(set);
    }

    public static List<TitanGearSet> all() {
        return Collections.unmodifiableList(SETS);
    }

    @Nullable
    public static TitanGearSet get(String titan) {
        for (TitanGearSet s : SETS) {
            if (s.titan.equals(titan)) {
                return s;
            }
        }
        return null;
    }

    public static int indexOf(String titan) {
        for (int i = 0; i < SETS.size(); i++) {
            if (SETS.get(i).titan.equals(titan)) {
                return i;
            }
        }
        return -1;
    }

    // 品の由来の表 (このModの装備でなければ null)
    @Nullable
    public static TitanGearSet of(Item item) {
        return item instanceof ModGear gear ? get(gear.dungeon()) : null;
    }

    // 強化と分解で使う欠片 (由来の巨神の欠片。わからなければ岩鯨の欠片)
    public static Item shardFor(ItemStack stack) {
        TitanGearSet set = of(stack.getItem());
        Optional<Item> shard = set == null ? Optional.empty() : set.shardItem();
        return shard.orElseGet(() -> STONE_WHALE.shardItem().orElseThrow());
    }
}
