package jp.revati.skyabyss.fishing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 釣りの品 (docs/design/54 の 6.2.4.1): 魚 31 種 (Fish.ALL。食べ物。大きさ FISH_SIZE を持つ) と釣り竿 (バニラの釣り竿の動き。壊れない)
public final class FishingSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SkyAbyss.MODID);

    // 魚の大きさ (mm)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FISH_SIZE = COMPONENTS.registerComponentType("fish_size",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredItem<Item> ROD = ITEMS.register("fishing_rod", () -> new FishingRodItem(new Item.Properties().stacksTo(1)) {
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("item.skyabyss.fishing_rod.desc").withStyle(ChatFormatting.GRAY));
        }
    });

    public static final Map<String, DeferredItem<Item>> FISH = new LinkedHashMap<>();

    static {
        for (Fish.Species s : Fish.ALL) {
            FISH.put(s.id(), ITEMS.register(s.itemId(), () -> new FishItem(s, new Item.Properties()
                    .food(new FoodProperties.Builder().nutrition(s.rarity() == 'C' ? 3 : 4).saturationModifier(0.4f).build()))));
        }
    }

    private FishingSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
    }

    public static ItemStack stack(Fish.Species s, int sizeMm) {
        ItemStack st = new ItemStack(FISH.get(s.id()).get());
        st.set(FISH_SIZE.get(), sizeMm);
        return st;
    }

    // 魚の品 (説明に大きさ・土地・まれさと一行)
    public static class FishItem extends Item {
        public final Fish.Species species;

        public FishItem(Fish.Species species, Properties properties) {
            super(properties);
            this.species = species;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            Integer mm = stack.get(FISH_SIZE.get());
            if (mm != null) {
                lines.add(Component.translatable("item.skyabyss.fish.size", String.format("%.1f", mm / 10.0)).withStyle(ChatFormatting.AQUA));
            }
            Component land = Component.translatable(species.region() == Fish.ANY ? "item.skyabyss.fish.land.any" : "item.skyabyss.fish.land." + species.region());
            lines.add(Component.translatable("item.skyabyss.fish.where", land, Component.translatable("item.skyabyss.fish.rarity." + species.rarity()))
                    .withStyle(species.rarity() == 'L' ? ChatFormatting.GOLD : species.rarity() == 'R' ? ChatFormatting.BLUE
                            : species.rarity() == 'U' ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
