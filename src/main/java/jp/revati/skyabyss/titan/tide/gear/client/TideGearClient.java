package jp.revati.skyabyss.titan.tide.gear.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.gear.TideGearSetup;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// 潮殻の装備の画面側: 珊瑚の盾の「構えている」(バニラの blocking の印)。
// models/item/coral_shield.json が minecraft:blocking = 1 で coral_shield_blocking へ替わる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TideGearClient {
    private TideGearClient() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(TideGearSetup.SHIELD.get(), ResourceLocation.withDefaultNamespace("blocking"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f));
    }
}
