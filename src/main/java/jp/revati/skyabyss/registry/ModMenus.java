package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ResonanceForgeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Mod独自の画面 (メニュー)
public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SkyAbyss.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ResonanceForgeMenu>> RESONANCE_FORGE = MENUS.register("resonance_forge",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new ResonanceForgeMenu(id, inventory)));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
