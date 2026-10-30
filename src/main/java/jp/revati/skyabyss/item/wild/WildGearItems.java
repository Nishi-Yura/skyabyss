package jp.revati.skyabyss.item.wild;

import java.util.List;

import jp.revati.skyabyss.item.ChapterBowItem;
import jp.revati.skyabyss.item.ChapterWeaponItem;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 野の装備の武器 (docs/design/51)。近接と杖と鈴杖は章の武器と同じ SwordItem の作り (強化・特性・持ち方は共通の仕組み)、弓は章の弓。
// 手に持つ間の数値の能力は品の修飾子 (WildGearSetup.addPassives)、ほかの能力は WildGearEvents
public final class WildGearItems {
    private WildGearItems() {
    }

    public static class Weapon extends ChapterWeaponItem implements WildGear {
        final WildGearTypes.Weapon row;

        public Weapon(Item.Properties properties, WildGearTypes.Weapon row) {
            super(properties, WildGearTypes.forgeOrigin(row.region()), row.attack(), row.speed(), row.tier(), "item.skyabyss." + row.id() + ".desc",
                    b -> WildGearSetup.addPassives(b, row.fx()));
            this.row = row;
        }

        @Override
        public String region() {
            return row.region();
        }

        @Override
        public int wildLevel() {
            return row.level();
        }

        @Override
        public String dungeon() {
            return WildGear.super.dungeon();
        }

        @Override
        public int requiredLevel() {
            return row.level();
        }
    }

    public static class Bow extends ChapterBowItem implements WildGear {
        final WildGearTypes.Weapon row;

        public Bow(Item.Properties properties, WildGearTypes.Weapon row) {
            super(properties, WildGearTypes.forgeOrigin(row.region()), row.bow(), row.tier(), "item.skyabyss." + row.id() + ".desc");
            this.row = row;
        }

        @Override
        public String region() {
            return row.region();
        }

        @Override
        public int wildLevel() {
            return row.level();
        }

        @Override
        public String dungeon() {
            return WildGear.super.dungeon();
        }

        @Override
        public int requiredLevel() {
            return row.level();
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss." + row.id() + ".desc", true);
        }
    }
}
