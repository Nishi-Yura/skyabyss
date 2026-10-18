package jp.revati.skyabyss.titan.ember;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 四章 (余燼) の世界の記録 (オーバーワールドに保存、skyabyss_ember_chapter)。今は「初めて浮かび上がった夜」を済ませたかだけ。
// 中盤の出来事 (EmberRising) はワールドで一度きりなので、ここに残す。人ごとの記録は ArcData と各人の永続データにある
public final class EmberChapterData extends SavedData {
    private static final String NAME = "skyabyss_ember_chapter";

    private boolean risen;
    private long risenDay = -1;

    public static EmberChapterData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(EmberChapterData::new, EmberChapterData::load), NAME);
    }

    public boolean risen() {
        return risen;
    }

    public long risenDay() {
        return risenDay;
    }

    // 初めての浮上を記録する。初めてなら true (二度目からは false)
    public boolean markRisen(long day) {
        if (risen) {
            return false;
        }
        risen = true;
        risenDay = day;
        setDirty();
        return true;
    }

    // 自己テスト用: 写し取った記録へ戻す
    public CompoundTag snapshot(HolderLookup.Provider registries) {
        return save(new CompoundTag(), registries);
    }

    public void restore(CompoundTag tag, HolderLookup.Provider registries) {
        EmberChapterData r = load(tag, registries);
        risen = r.risen;
        risenDay = r.risenDay;
        setDirty();
    }

    private static EmberChapterData load(CompoundTag tag, HolderLookup.Provider registries) {
        EmberChapterData d = new EmberChapterData();
        d.risen = tag.getBoolean("risen");
        d.risenDay = tag.contains("risen_day") ? tag.getLong("risen_day") : -1;
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("risen", risen);
        tag.putLong("risen_day", risenDay);
        return tag;
    }
}
