package jp.revati.skyabyss.rpg.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// 成長の画面まわりの登録 (Mod のイベントバス)。キーは J (バニラでは使っていない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GrowthClientSetup {
    public static final KeyMapping STATUS_KEY = new KeyMapping("key.skyabyss.status", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.STATUS, "key.categories.skyabyss");

    private GrowthClientSetup() {
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(STATUS_KEY);
    }

    @SubscribeEvent
    static void onLayers(RegisterGuiLayersEvent event) {
        // チャットより下に描く (チャットが重なったらチャットを優先)
        event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "growth_hud"), GrowthHud::render);
    }
}
