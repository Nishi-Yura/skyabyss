package jp.revati.skyabyss.sky;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 次に「何か」が通過する時刻をワールドに保存する
public class SkyEventData extends SavedData {
    private static final String NAME = "skyabyss_sky_events";
    private static final Factory<SkyEventData> FACTORY = new Factory<>(SkyEventData::new, SkyEventData::load);

    private long nextPassTick = -1;
    // これまでに起きた通過の数 (潮殻の巨神の出現の条件。08 の 2.1節)
    private int passCount;
    // 最初の通過が始まった時刻 (まだなら -1)。岩鯨は、これより後でないと現れない (06 の 2.7節)
    private long firstPassTick = -1;

    public static SkyEventData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static SkyEventData load(CompoundTag tag, HolderLookup.Provider registries) {
        SkyEventData data = new SkyEventData();
        data.nextPassTick = tag.getLong("nextPassTick");
        data.passCount = tag.getInt("passCount");
        data.firstPassTick = tag.contains("firstPassTick") ? tag.getLong("firstPassTick") : -1;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("nextPassTick", nextPassTick);
        tag.putInt("passCount", passCount);
        tag.putLong("firstPassTick", firstPassTick);
        return tag;
    }

    public long nextPassTick() {
        return nextPassTick;
    }

    public int passCount() {
        return passCount;
    }

    public long firstPassTick() {
        return firstPassTick;
    }

    // 通過が始まった (now はその時刻)
    public void countPass(long now) {
        if (passCount == 0) {
            firstPassTick = now;
        }
        passCount++;
        setDirty();
    }

    public void schedule(long tick) {
        nextPassTick = tick;
        setDirty();
    }
}
