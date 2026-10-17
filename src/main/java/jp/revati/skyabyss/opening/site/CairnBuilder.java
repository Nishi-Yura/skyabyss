package jp.revati.skyabyss.opening.site;

import net.minecraft.server.level.ServerLevel;

// 積み石 (17 の 4.2、野営地から宿りへの道に 40 ブロックおき)。小さな石の塔。地面は整えない
public final class CairnBuilder {
    private CairnBuilder() {
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        return new SiteJob(level, data, entry, Blueprint.get("cairn"), "cairn " + entry.index, job -> {
        });
    }
}
