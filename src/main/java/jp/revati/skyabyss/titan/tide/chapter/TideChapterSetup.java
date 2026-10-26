package jp.revati.skyabyss.titan.tide.chapter;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 二章「引く海」(08 の 2.8節) の登録: 潮見の貝、章の音。TideSetup.register から呼ぶ。
// 音は専用の名前空間 skyabyss_ch2 (assets/skyabyss_ch2/sounds.json) に置き、共有の sounds.json は触らない
public final class TideChapterSetup {
    public static final String SOUND_NAMESPACE = "skyabyss_ch2";

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SOUND_NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    // 潮見の貝: 持つと巨神の向き・距離・次の干潮の刻まで。吹くと、その刻は自分のそばへ寄るよう頼める
    public static final DeferredItem<Item> TIDE_WATCHING_SHELL = ITEMS.register("tide_watching_shell",
            () -> new TideConchItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // 遠い貝の音 (予兆の 2 拍目、貝を吹く音)、海が引いていく音、潮が戻る音
    public static final DeferredHolder<SoundEvent, SoundEvent> CONCH_FAR = sound("conch_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> EBB_RECEDE = sound("ebb_recede");
    public static final DeferredHolder<SoundEvent, SoundEvent> EBB_RETURN = sound("ebb_return");

    private TideChapterSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SOUND_NAMESPACE, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(TideChapterSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            event.accept(TIDE_WATCHING_SHELL.get());
        }
    }
}
