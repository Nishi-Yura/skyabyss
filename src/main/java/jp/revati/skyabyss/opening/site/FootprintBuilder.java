package jp.revati.skyabyss.opening.site;

import net.minecraft.server.level.ServerLevel;

// 巨大な足跡 (17 の 4.3)。足の形のくぼみ (指 5 本、深さ 1〜3)、底は粘土と石、雨水がたまる。右足と左足を交互に (index が奇数なら左右反転)。
// 進む向きは SiteFinder が決める (部品は北向き)
public final class FootprintBuilder {
    private FootprintBuilder() {
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        return new SiteJob(level, data, entry, Blueprint.get("footprint"), "footprint " + entry.index, job -> {
        });
    }
}
