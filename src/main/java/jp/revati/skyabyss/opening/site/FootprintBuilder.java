package jp.revati.skyabyss.opening.site;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 巨大な足跡 (17 の 4.3、v2)。長さ 50・幅 45・深さ 4〜8 の足の形 (かかと・土踏まず・母指球・指 5 本と爪の溝)。
// 地面の高さに沿って掘る (起伏のある所でも、地面に押し込まれた形になる)。縁は盛り上がった土、割れた地面、はね飛ばされた岩、押しつぶされた木。
// 底に雨水がところどころ (池にならない 1 ブロックのたまり)。右足と左足を交互に (index が奇数なら左右反転)。
// 並びのいちばん先 (index が最大) だけ別の部品 (footprint_end): 足跡の先が深い火口になり、その先へ土のうねりが続く (余燼が地へ潜った跡、17-3)。
// 進む向きは SiteFinder が決める (部品は北向き)
public final class FootprintBuilder {
    private FootprintBuilder() {
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        return new SiteJob(level, data, entry, Blueprint.get(blueprintName(data, entry)), "footprint " + entry.index, FootprintBuilder::finish);
    }

    // 並びの最後の足跡か
    static boolean isLast(SiteData data, SiteData.Entry entry) {
        int max = -1;
        for (SiteData.Entry e : data.entries("footprint")) {
            max = Math.max(max, e.index);
        }
        return entry.index == max && max >= 1;
    }

    public static String blueprintName(SiteData data, SiteData.Entry entry) {
        // はじまりの島 (決まった置き場のある大陸の世界) は、古く草に覆われた浅い足跡 (2026-10-26)
        if (!jp.revati.skyabyss.world.continent.ContinentLayout.get().islandPrints().isEmpty()
                && jp.revati.skyabyss.world.continent.ContinentLayout.get().island("gate") != null && near(entry)) {
            return "footprint_island";
        }
        return isLast(data, entry) ? "footprint_end" : "footprint";
    }

    // 足跡が島の決まった置き場のそばか (大陸の世界の足跡)
    static boolean near(SiteData.Entry entry) {
        for (BlockPos p : jp.revati.skyabyss.world.continent.ContinentLayout.get().islandPrints()) {
            if (Math.abs(p.getX() - entry.pos.getX()) <= 4 && Math.abs(p.getZ() - entry.pos.getZ()) <= 4) {
                return true;
            }
        }
        return false;
    }

    // 底 (floor) と縁の外 (rim) の立つ場所を書く (構造の検査: 落ちても登って出られるか)
    static void finish(SiteJob job) {
        for (String name : new String[] {"floor", "rim"}) {
            int[] m = job.bp.marks.get(name);
            if (m == null) {
                continue;
            }
            BlockPos w = Blueprint.transform(m[0], 0, m[2], job.rot, job.mirror, job.origin);
            job.entry.extra(name, job.standAt(w.getX(), w.getZ(), job.origin.getY() + 30));
        }
    }
}
