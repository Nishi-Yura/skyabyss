package jp.revati.skyabyss.opening.site;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

// 構造物の仕上げの道具
public final class SiteKit {
    // 看板の文のキー (lang は assets/skyabyss_opening/lang)。4 行
    public static final String SLAB_SIGN = "sign.skyabyss.opening_slab";

    private SiteKit() {
    }

    // 看板に 4 行の文を書く。古い碑文らしく、淡い青の光る文字 (BuildKit と同じ)
    static void writeSign(ServerLevel level, BlockPos pos, String key) {
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText().setColor(DyeColor.LIGHT_BLUE).setHasGlowingText(true);
            for (int i = 0; i < 4; i++) {
                text = text.setMessage(i, Component.translatable(key + "." + (i + 1)));
            }
            sign.setText(text, true);
            sign.setWaxed(true);
        }
    }
}
