package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.effect.BleedingEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Mod独自の状態効果
public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SkyAbyss.MODID);

    // 出血: 骨の棘や牙で裂かれた傷から、少しずつ体力が流れ出る
    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
