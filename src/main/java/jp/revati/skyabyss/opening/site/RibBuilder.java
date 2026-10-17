package jp.revati.skyabyss.opening.site;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 巨神の骨 (17 の 4.4)。丘に半ば埋もれた巨大なあばら骨 (高さ 18、長さ 40)。足もとに石板 (看板 1 枚、一行の碑文)。
// 骨は地面の低い所に合わせて立ち、脚は地面まで延ばす。石板はその場の地面の高さに置く
public final class RibBuilder {
    private RibBuilder() {
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        return new SiteJob(level, data, entry, Blueprint.get("ribs"), "ribs", RibBuilder::finish);
    }

    static void finish(SiteJob job) {
        BlockPos at = job.mark("slab");
        if (at == null) {
            return;
        }
        BlockPos sign = job.placeSmall(Blueprint.get("slab"), at.getX(), at.getZ(), job.rot);
        if (sign != null) {
            SiteKit.writeSign(job.level, sign, SiteKit.SLAB_SIGN);
            job.entry.extra("slab", sign);
        }
    }
}
