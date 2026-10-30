package jp.revati.skyabyss.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

// クリエイティブの欄の分け方 (2026-10-27 の声「クリエでの mod のアイテム欄を整理して」)。
// 品ごとに入れる担当が 30 あって登録の順のままだったのを、ここで全部の品を分けて並べる:
//   武器      職 (戦士・鍛冶師・魔法使い・弓使い・暗殺者・呪術師) → 由来 (岩鯨・潮殻・雷翼・余燼・深淵) → 階級 の順
//   防具と飾り 由来 → 階級 → 部位 (頭・胴・脚・足、飾り)
//   敵の卵     スポーンエッグ
//   ブロック   置ける品
//   素材と品   残り (登録の順。同じ章の品が並ぶ)
public final class CreativeTabs {
    static final List<String> DUNGEONS = List.of("stone_whale", "tide_shell", "thunder_wing", "ember", "abyss");

    private CreativeTabs() {
    }

    public enum Kind {
        WEAPON, GEAR, EGG, BLOCK, MISC
    }

    public static Kind kind(Item item) {
        ItemStack stack = new ItemStack(item);
        if (item instanceof SpawnEggItem) {
            return Kind.EGG;
        }
        if (Job.of(stack) != null) {
            return Kind.WEAPON;
        }
        if (item instanceof ArmorItem || item instanceof ModGear) {
            return Kind.GEAR;
        }
        if (item instanceof BlockItem) {
            return Kind.BLOCK;
        }
        return Kind.MISC;
    }

    static List<Item> mine() {
        List<Item> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(SkyAbyss.MODID)) {
                out.add(item);
            }
        }
        return out;
    }

    static int dungeon(Item item) {
        // 野の装備 (docs/design/51) は物語の品のあと、必要 Lv の順
        if (item instanceof jp.revati.skyabyss.item.wild.WildGear w) {
            return DUNGEONS.size() + 1 + w.wildLevel();
        }
        int i = item instanceof ModGear g ? DUNGEONS.indexOf(g.dungeon()) : -1;
        return i < 0 ? DUNGEONS.size() : i;
    }

    static int tier(Item item) {
        return item instanceof ModGear g ? g.tier().ordinal() : GearTier.values().length;
    }

    static int slot(Item item) {
        return item instanceof ArmorItem a ? a.getEquipmentSlot().ordinal() * -1 + 10 : 20;
    }

    public static void fill(Kind kind, CreativeModeTab.Output output) {
        List<Item> items = new ArrayList<>(mine());
        items.removeIf(i -> kind(i) != kind);
        switch (kind) {
            case WEAPON -> items.sort(Comparator.<Item>comparingInt(i -> Job.of(new ItemStack(i)).ordinal())
                    .thenComparingInt(CreativeTabs::dungeon).thenComparingInt(CreativeTabs::tier));
            case GEAR -> items.sort(Comparator.<Item>comparingInt(CreativeTabs::dungeon).thenComparingInt(CreativeTabs::tier)
                    .thenComparingInt(CreativeTabs::slot));
            default -> {
            }
        }
        for (Item item : items) {
            output.accept(item);
        }
    }
}
