package jp.revati.skyabyss.registry;

import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
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

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
