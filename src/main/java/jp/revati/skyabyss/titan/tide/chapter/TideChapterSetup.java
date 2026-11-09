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

    // 潮見の貝 (巨神の向きを示し、干潮の刻に呼べる貝) は 2026-11-08 の声「潮見の貝いらなくね」で外した。巨神は決まった場所にいて、地図に出る

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

    }

}
