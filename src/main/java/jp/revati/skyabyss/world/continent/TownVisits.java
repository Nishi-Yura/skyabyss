package jp.revati.skyabyss.world.continent;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 土地の町に入ったことを進捗へ伝える (Beats の town:visit:<土地>)。2 秒ごとに、町の中にいる人の土地を見る。都 (草原) は都めぐり (town:tour) のほうで数える。
// 一度伝えた町は人ごとに覚えておき、二度目からは何もしない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TownVisits {
    static final String KEY = "skyabyss_towns_visited";

    private TownVisits() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 17) {
            return;
        }
        ServerLevel level = server.overworld();
        for (ServerPlayer p : level.players()) {
            visit(level, p);
        }
    }

    // その人が今いる土地の町に入っていれば、初めてのときだけ伝える。伝えたら true
    public static boolean visit(ServerLevel level, ServerPlayer p) {
        if (!p.isAlive() || !jp.revati.skyabyss.opening.site.SiteGuard.inTown(level, p.blockPosition())) {
            return false;
        }
        int region = ContinentMap.get().region(p.getBlockX(), p.getBlockZ());
        if (region == ContinentMap.MEADOW || region == ContinentMap.OUTSKIRTS || region < 0 || region >= RegionSpawner.REGIONS.length) {
            return false;
        }
        int seen = p.getPersistentData().getInt(KEY);
        if ((seen & (1 << region)) != 0) {
            return false;
        }
        p.getPersistentData().putInt(KEY, seen | (1 << region));
        jp.revati.skyabyss.advancement.Beats.fire(p, "town:visit:" + RegionSpawner.REGIONS[region]);
        return true;
    }
}
