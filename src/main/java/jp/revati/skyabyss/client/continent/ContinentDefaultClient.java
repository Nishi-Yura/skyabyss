package jp.revati.skyabyss.client.continent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

// 新しい世界を作る画面を開いたとき、ワールドの種類を「天と淵の大陸」にしておく (docs/design/40 の 3 章)。
// 一つの画面で一度だけ (そのあと人が選び直したものは変えない)。専用サーバーは server.properties の level-type=skyabyss:continent
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ContinentDefaultClient {
    static final ResourceKey<WorldPreset> CONTINENT = ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "continent"));
    private static final Set<CreateWorldScreen> DONE = Collections.newSetFromMap(new WeakHashMap<>());

    private ContinentDefaultClient() {
    }

    @SubscribeEvent
    static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen) || !DONE.add(screen)) {
            return;
        }
        WorldCreationUiState ui = screen.getUiState();
        for (WorldCreationUiState.WorldTypeEntry e : ui.getNormalPresetList()) {
            if (e.preset() != null && e.preset().is(CONTINENT)) {
                ui.setWorldType(e);
                return;
            }
        }
    }
}
