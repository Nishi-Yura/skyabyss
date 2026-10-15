package jp.revati.skyabyss.titan.dungeon;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 岩鯨の背の状態をワールドに保存する。
// 試練の進み具合 (制圧した広間、封印を解いたか) は、誰もいなくなってしばらくすると元に戻り、何度でも挑める。
// 天の鍵の欠片を受け取った人の一覧だけは消さない (欠片は一人一つ)
// 岩鯨の心臓を倒したかどうかは、試練のリセットまで保つ (リセットで挑み直せる)
public class TitanBackData extends SavedData {
    private static final String NAME = "skyabyss_titan_back";
    private static final Factory<TitanBackData> FACTORY = new Factory<>(TitanBackData::new, TitanBackData::load);

    private boolean built;
    private boolean gateOpened;
    private boolean heartDefeated;
    private final Set<String> clearedHalls = new HashSet<>();
    private final Set<UUID> fragmentClaimed = new HashSet<>();
    private int clears;
    // アスレチックと迷路の形を決める種。試練が元に戻るたびに新しくなる
    private long courseSeed;
    private long mazeSeed;
    // 試練が元に戻るたびに一つ増える番号 (1 から)。休み場の記録にも同じ番号を書き、違えば記録を無効にする
    private int epoch = 1;

    public static TitanBackData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static TitanBackData load(CompoundTag tag, HolderLookup.Provider registries) {
        TitanBackData data = new TitanBackData();
        data.built = tag.getBoolean("Built");
        data.gateOpened = tag.getBoolean("GateOpened");
        data.heartDefeated = tag.getBoolean("HeartDefeated");
        data.clears = tag.getInt("Clears");
        data.courseSeed = tag.getLong("CourseSeed");
        data.mazeSeed = tag.getLong("MazeSeed");
        data.epoch = tag.contains("Epoch") ? Math.max(1, tag.getInt("Epoch")) : 1;
        for (Tag t : tag.getList("ClearedHalls", Tag.TAG_STRING)) {
            data.clearedHalls.add(t.getAsString());
        }
        for (Tag t : tag.getList("FragmentClaimed", Tag.TAG_INT_ARRAY)) {
            data.fragmentClaimed.add(NbtUtils.loadUUID(t));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Built", built);
        tag.putBoolean("GateOpened", gateOpened);
        tag.putBoolean("HeartDefeated", heartDefeated);
        tag.putInt("Clears", clears);
        tag.putLong("CourseSeed", courseSeed);
        tag.putLong("MazeSeed", mazeSeed);
        tag.putInt("Epoch", epoch);
        ListTag halls = new ListTag();
        clearedHalls.forEach(h -> halls.add(StringTag.valueOf(h)));
        tag.put("ClearedHalls", halls);
        ListTag claimed = new ListTag();
        fragmentClaimed.forEach(id -> claimed.add(NbtUtils.createUUID(id)));
        tag.put("FragmentClaimed", claimed);
        return tag;
    }

    public boolean isBuilt() {
        return built;
    }

    public void markBuilt() {
        built = true;
        setDirty();
    }

    public boolean isGateOpened() {
        return gateOpened;
    }

    public void markGateOpened() {
        gateOpened = true;
        clears++;
        setDirty();
    }

    public boolean isHeartDefeated() {
        return heartDefeated;
    }

    public void markHeartDefeated() {
        heartDefeated = true;
        setDirty();
    }

    public boolean isHallCleared(String id) {
        return clearedHalls.contains(id);
    }

    public void markHallCleared(String id) {
        clearedHalls.add(id);
        setDirty();
    }

    // 何かしら試練が進んでいるか (進んでいなければ、元に戻す必要もない)
    public boolean hasProgress() {
        return gateOpened || heartDefeated || !clearedHalls.isEmpty();
    }

    public void resetProgress() {
        epoch++;
        gateOpened = false;
        heartDefeated = false;
        clearedHalls.clear();
        setDirty();
    }

    // 天の鍵の欠片を受け取る。すでに受け取っていれば false
    public boolean claimFragment(UUID player) {
        boolean added = fragmentClaimed.add(player);
        if (added) {
            setDirty();
        }
        return added;
    }

    // 天の鍵の欠片をもう受け取ったか
    public boolean hasClaimedFragment(UUID player) {
        return fragmentClaimed.contains(player);
    }

    public long courseSeed() {
        return courseSeed;
    }

    public long mazeSeed() {
        return mazeSeed;
    }

    // 新しい種を引く (アスレチックと迷路の形が変わる)
    public void reseed(net.minecraft.util.RandomSource random) {
        courseSeed = random.nextLong();
        mazeSeed = random.nextLong();
        setDirty();
    }

    // いまの試練の番号。リセットのたびに増える (休み場の記録とログイン位置の検査に使う)
    public int epoch() {
        return epoch;
    }

    public int clears() {
        return clears;
    }
}
