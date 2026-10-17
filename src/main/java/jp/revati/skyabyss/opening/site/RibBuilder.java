package jp.revati.skyabyss.opening.site;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 巨神の骨 (17 の 4.4、v2)。丘に半ば埋もれた巨大な骨: 背骨 (椎骨が並ぶ) と、そこから弓なりに立つ 6 本の肋骨 (高さ 25〜38、長さ 100 ほどの
// 骨の全体)、東の端に頭蓋の殻 (高さ 22、横に半径 6 の眼窩、前に口吻)。苔と草に覆われる。足もとに石板 (看板 1 枚、一行の碑文)。
// 骨は地面の低い所に合わせて立ち、脚は地面まで延ばす。石板はその場の地面の高さに置く
public final class RibBuilder {
    private RibBuilder() {
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        return new SiteJob(level, data, entry, Blueprint.get("ribs"), "ribs", RibBuilder::finish);
    }

    static void finish(SiteJob job) {
        // 骨の下 (under) と骨の列の外 (far) の立つ場所 (構造の検査: 骨の下を歩けるか)
        for (String name : new String[] {"under", "far"}) {
            int[] m = job.bp.marks.get(name);
            if (m != null) {
                BlockPos w = Blueprint.transform(m[0], 0, m[2], job.rot, job.mirror, job.origin);
                job.entry.extra(name, job.standAt(w.getX(), w.getZ(), job.origin.getY() + 12));
            }
        }
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
