package jp.revati.skyabyss.registry;

import java.util.List;

import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.affix.Affix;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 道具に持たせる、このMod独自のデータ
public final class ModDataComponents {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(SkyAbyss.MODID);

    // 共鳴の炉で鍛えた段階 (+1〜+10)。保存され、参加者にも同期される
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENHANCE = COMPONENTS.registerComponentType("enhance",
            builder -> builder.persistent(Codec.intRange(0, 10)).networkSynchronized(ByteBufCodecs.VAR_INT));

    // ランダムな特性 (Affix)。付いていない = まだ抽選していない (第1段階のセーブにある装備)。
    // 手にしたとき (AffixEvents) か、報酬として出たとき (TitanLoot) に決まる
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Affix>>> AFFIXES = COMPONENTS.registerComponentType("affixes",
            builder -> builder.persistent(Affix.LIST_CODEC).networkSynchronized(Affix.LIST_STREAM_CODEC).cacheEncoding());

    // 再鍛造の途中の候補 (最大 3 組)。装備に持たせるので、炉を閉じても払った分は消えない
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<List<Affix>>>> REFORGE_OFFERS = COMPONENTS.registerComponentType("reforge_offers",
            builder -> builder.persistent(Affix.OFFERS_CODEC).networkSynchronized(Affix.OFFERS_STREAM_CODEC).cacheEncoding());

    // 特性の外側の刻み: 穴・差したもの・アスペクト・世代 (docs/design/31 の A02・A06・A07・B15)。付いていない装備は手にしたときに抽選する
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<jp.revati.skyabyss.item.extra.GearExtras>> GEAR_EXTRAS = COMPONENTS.registerComponentType("gear_extras",
            builder -> builder.persistent(jp.revati.skyabyss.item.extra.GearExtras.CODEC).networkSynchronized(jp.revati.skyabyss.item.extra.GearExtras.STREAM_CODEC).cacheEncoding());

    // アスペクトの芯 (品) が持つ、逸品の書き換え (Rune の id)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ASPECT = COMPONENTS.registerComponentType("aspect",
            builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // 技の修飾ルーン (品) の中身
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<jp.revati.skyabyss.item.extra.RuneData>> SKILL_RUNE = COMPONENTS.registerComponentType("skill_rune",
            builder -> builder.persistent(jp.revati.skyabyss.item.extra.RuneData.CODEC).networkSynchronized(jp.revati.skyabyss.item.extra.RuneData.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
