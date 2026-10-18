package jp.revati.skyabyss.opening.site;

import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 構造物の仕上げの道具
public final class SiteKit {
    // 碑の文のキー (lang は assets/skyabyss_opening/lang)。4 行
    public static final String SLAB_SIGN = "sign.skyabyss.opening_slab";

    private SiteKit() {
    }

    // 碑 (骨の石板) に文のキーを刻む。文は lang の "<key>.1"〜"<key>.4"、見ると字幕に出る
    static void writeSign(ServerLevel level, BlockPos pos, String key) {
        Tablets.write(level, pos, key);
    }
}
