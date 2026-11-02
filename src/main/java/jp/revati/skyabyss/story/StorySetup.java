package jp.revati.skyabyss.story;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 1 章の終わり (天の鍵) の登録。SkyAbyss のコンストラクタから register を呼ぶ。共有の ModItems には手を入れない。
// 文は assets/skyabyss_story/lang、音は assets/skyabyss_story/sounds.json (画面だけで鳴らすので登録しない)
public final class StorySetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    // 天の鍵。四つの欠片が一つになったもの (SkyKey.assemble でだけ手に入る)。一つだけ、火に強く、捨てられない (Keepsakes)
    public static final DeferredItem<Item> SKY_KEY = ITEMS.register("sky_key",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("item.skyabyss.sky_key.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }

                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }
            });

    private StorySetup() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(StorySetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            event.accept(SKY_KEY.get());
        }
    }
}
