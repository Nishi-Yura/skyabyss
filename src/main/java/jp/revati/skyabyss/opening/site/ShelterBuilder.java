package jp.revati.skyabyss.opening.site;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// 鐘の宿り (17 の 4.2、語り部の家)。小屋 1 つ、柵の庭、バニラの鐘 (POI "meeting" になるので、NpcSpawner が語り部を立たせる)、
// わら束の上の煙の出る焚き火 (遠くからの目印)。庭の入口 (南向きの部品) は野営地の方へ向く
public final class ShelterBuilder {
    private ShelterBuilder() {
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        return new SiteJob(level, data, entry, Blueprint.get("shelter"), "shelter", ShelterBuilder::finish);
    }

    static void finish(SiteJob job) {
        BlockPos bell = job.mark("bell");
        if (bell == null || !job.level.getBlockState(bell).is(Blocks.BELL)) {
            SkyAbyss.LOGGER.error("Opening shelter: no bell at {}", bell);
        } else {
            job.entry.extra("bell", bell);
        }
        BlockPos smoke = job.mark("smoke");
        if (smoke != null) {
            job.entry.extra("smoke", smoke);
        }
        BlockPos stand = job.mark("stand");
        if (stand != null) {
            job.entry.extra("stand", stand);
        }
        BlockPos door = job.mark("door");
        if (door != null) {
            job.entry.extra("door", door);
        }
    }
}
