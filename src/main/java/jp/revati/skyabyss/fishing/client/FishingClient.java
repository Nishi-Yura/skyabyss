package jp.revati.skyabyss.fishing.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.fishing.FishingSetup;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// 釣り竿の「投げている」(バニラの cast の印)。models/item/fishing_rod.json が cast = 1 で fishing_rod_cast (糸と浮きの無い竿) へ替わる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class FishingClient {
    private FishingClient() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(FishingSetup.ROD.get(), ResourceLocation.withDefaultNamespace("cast"), (stack, level, entity, seed) -> {
            if (!(entity instanceof Player p) || p.fishing == null) {
                return 0f;
            }
            return p.getMainHandItem() == stack || p.getOffhandItem() == stack ? 1f : 0f;
        }));
    }
}
