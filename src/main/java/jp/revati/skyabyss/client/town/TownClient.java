package jp.revati.skyabyss.client.town;

import java.util.Map;
import java.util.function.Function;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.opening.SiteTargets;
import jp.revati.skyabyss.client.quest.ObjectiveStrip;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// 町のクエストの目的の帯の行き先 (サーバーの town.TownNetwork が送る「クエスト/段」→ 場所)。
// 帯の行き先の口 (ObjectiveStrip.targets) に、ほかの口の前に差し込む。位置は地上のものなので、地上にいるときだけ返す
public final class TownClient {
    private static volatile Map<String, BlockPos> spots = Map.of();

    private TownClient() {
    }

    public static void receive(Map<String, BlockPos> map) {
        spots = Map.copyOf(map);
    }

    public static BlockPos spot(String key) {
        return SiteTargets.inOverworld() ? spots.get(key) : null;
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    static final class Install {
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                Function<String, BlockPos> previous = ObjectiveStrip.targets;
                ObjectiveStrip.targets = id -> {
                    BlockPos p = id.startsWith("town/") ? spot(id) : null;
                    return p != null || previous == null ? p : previous.apply(id);
                };
            });
        }
    }
}
