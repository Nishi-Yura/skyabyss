package jp.revati.skyabyss.story.finale;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

// 終章の「初めての一言」を人ごとに一度だけ (人のデータ KEY に記す)。サーバーで一度きりの印だと、組の二人目や別のワールドの人に出ない
public final class FinaleHints {
    static final String KEY = "skyabyss_finale_hints";

    private FinaleHints() {
    }

    // その人に初めてなら true を返して記す
    public static boolean once(ServerPlayer p, String hint) {
        CompoundTag root = p.getPersistentData();
        CompoundTag tag = root.getCompound(KEY);
        if (tag.getBoolean(hint)) {
            return false;
        }
        tag.putBoolean(hint, true);
        root.put(KEY, tag);
        return true;
    }
}
