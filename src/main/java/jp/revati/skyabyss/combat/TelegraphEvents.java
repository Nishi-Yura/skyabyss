package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Telegraph を毎 tick 進める入口 (サーバーの tick の終わりに、全ディメンションを一度ずつ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TelegraphEvents {
    private TelegraphEvents() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Telegraph.tickLevel(level);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        Telegraph.clearAll();
    }
}
