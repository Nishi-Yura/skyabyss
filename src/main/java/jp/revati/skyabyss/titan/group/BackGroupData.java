package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 巨神の背中のディメンションごとに一つ。グループ (Groups) ごとの試練の進みの記録。
// 共有の「背中そのもの」の記録 (建てたか・配置の版・欠片を受け取った人) は TitanBackData のまま。
//
// 記録 (Record) の持ち物:
//  - 試練の番号 (epoch): グループがリセットされるたびに一つ増える。休み場の記録に同じ番号を書き、違えば無効にする
//  - 共通の進み: 心臓を倒したか、宝物庫の封印を解いたか、制圧した広間、周回の数
//  - data: 巨神ごとの進み (潮殻の鳴った石・雷翼の受光など) を自由に書く欄 (旗・数・文字列の集まり)
//  - 扉の上書き (GroupGates): 背中に置いた扉ブロックの「開いている/閉じている」を、このグループにだけ変える
public final class BackGroupData extends SavedData {
    private static final String NAME = "skyabyss_back_groups";
    private static final Factory<BackGroupData> FACTORY = new Factory<>(BackGroupData::new, BackGroupData::load);

    // 初回を終えた印 (data の欄。"keep." で始まるのでリセットで残る)
    public static final String FIRST_CLEARED = "keep.first_cleared";

    public static final class Record {
        public final UUID id;
        private int epoch = 1;
        private boolean heartDefeated;
        private boolean gateOpened;
        private int clears;
        private final Set<String> clearedHalls = new HashSet<>();
        private CompoundTag data = new CompoundTag();
        final LongOpenHashSet openOverride = new LongOpenHashSet();
        final LongOpenHashSet closedOverride = new LongOpenHashSet();
        // 最後にこのグループの誰かが背中にいた時刻 (ゲーム時間)
        private long lastSeen;
        private transient BackGroupData owner;

        Record(UUID id, BackGroupData owner) {
            this.id = id;
            this.owner = owner;
        }

        private void dirty() {
            if (owner != null) {
                owner.setDirty();
            }
        }

        public int epoch() {
            return epoch;
        }

        public boolean isHeartDefeated() {
            return heartDefeated;
        }

        public void markHeartDefeated() {
            heartDefeated = true;
            dirty();
        }

        // 心臓をもう一度挑めるようにする (テスト用のコマンド・自己テスト。進みのほかの所と番号はそのまま)
        public void clearHeartDefeated() {
            if (heartDefeated) {
                heartDefeated = false;
                dirty();
            }
        }

        public boolean isGateOpened() {
            return gateOpened;
        }

        public void markGateOpened() {
            gateOpened = true;
            clears++;
            dirty();
        }

        public int clears() {
            return clears;
        }

        public boolean isHallCleared(String id) {
            return clearedHalls.contains(id);
        }

        public void markHallCleared(String id) {
            clearedHalls.add(id);
            dirty();
        }

        // 試験の診断用: 進みと数えている理由
        public String progressWhy() {
            return "gateOpened=" + gateOpened + " heartDefeated=" + heartDefeated + " halls=" + clearedHalls + " data=" + data.getAllKeys()
                    + " open=" + openOverride.size() + " closed=" + closedOverride.size() + " realOverride=" + hasRealOverride();
        }

        // 何かしら試練が進んでいるか (進んでいなければ、元に戻す必要もない)
        public boolean hasProgress() {
            return gateOpened || heartDefeated || !clearedHalls.isEmpty() || hasProgressData()
                    || hasRealOverride();
        }

        // 試練の入口の戸 (引いた三つだけ開ける戸。人が背にいるだけで書かれる) 以外の上書きがあるか
        private boolean hasRealOverride() {
            for (long k : openOverride) {
                if (!IGNORED_OVERRIDE.test(k)) {
                    return true;
                }
            }
            for (long k : closedOverride) {
                if (!IGNORED_OVERRIDE.test(k)) {
                    return true;
                }
            }
            return false;
        }

        // data に進みの欄があるか ("keep." の欄はリセットで残る記録なので、進みには数えない)
        private boolean hasProgressData() {
            for (String key : data.getAllKeys()) {
                if (!key.startsWith("keep.")) {
                    return true;
                }
            }
            return false;
        }

        // 試練を最初に戻す: 番号を進め、進みと扉の上書きを消す (data の "keep." で始まる欄は残す)
        public void resetProgress() {
            epoch++;
            heartDefeated = false;
            gateOpened = false;
            clearedHalls.clear();
            CompoundTag kept = new CompoundTag();
            for (String key : data.getAllKeys()) {
                if (key.startsWith("keep.")) {
                    kept.put(key, data.get(key).copy());
                }
            }
            data = kept;
            openOverride.clear();
            closedOverride.clear();
            dirty();
        }

        public long lastSeen() {
            return lastSeen;
        }

        public void touch(long gameTime) {
            lastSeen = gameTime;
            // 保存は急がない (次の保存のときに書かれる)
        }

        // ---- 初回・周回。"keep." の欄なので、試練のリセットでは消えない
        // そのグループがこの巨神の心臓を一度でも止めたか (FirstRun.onFirstCleared が立てる。パーティの組む・入る・抜けるでは記録ごと写る)
        public boolean isFirstCleared() {
            return data.getBoolean(FIRST_CLEARED);
        }

        public void markFirstCleared() {
            if (!data.getBoolean(FIRST_CLEARED)) {
                data.putBoolean(FIRST_CLEARED, true);
                dirty();
            }
        }

        // ---- 自由な欄 (巨神ごとの進み)
        public boolean flag(String key) {
            return data.getBoolean(key);
        }

        public void setFlag(String key, boolean value) {
            if (data.getBoolean(key) != value || !data.contains(key)) {
                if (value) {
                    data.putBoolean(key, true);
                } else {
                    data.remove(key);
                }
                dirty();
            }
        }

        public int getInt(String key) {
            return data.getInt(key);
        }

        public void putInt(String key, int value) {
            if (value == 0) {
                data.remove(key);
            } else {
                data.putInt(key, value);
            }
            dirty();
        }

        public long getLong(String key) {
            return data.getLong(key);
        }

        public void putLong(String key, long value) {
            data.putLong(key, value);
            dirty();
        }

        public boolean has(String key) {
            return data.contains(key);
        }

        public String getString(String key) {
            return data.getString(key);
        }

        public void putString(String key, String value) {
            data.putString(key, value);
            dirty();
        }

        public void remove(String key) {
            data.remove(key);
            dirty();
        }

        // 生の欄 (書き換えたら changed() を呼ぶ)
        public CompoundTag data() {
            return data;
        }

        public void changed() {
            dirty();
        }

        public Set<String> strings(String key) {
            Set<String> out = new HashSet<>();
            for (Tag t : data.getList(key, Tag.TAG_STRING)) {
                out.add(t.getAsString());
            }
            return out;
        }

        // 扉の上書きの読み書き (GroupGates から)
        public boolean hasOverride() {
            return !openOverride.isEmpty() || !closedOverride.isEmpty();
        }

        // 別の記録の内容を写す (パーティを組む・抜けるとき)
        void copyFrom(Record other) {
            epoch = other.epoch;
            heartDefeated = other.heartDefeated;
            gateOpened = other.gateOpened;
            clears = other.clears;
            clearedHalls.clear();
            clearedHalls.addAll(other.clearedHalls);
            data = other.data.copy();
            openOverride.clear();
            openOverride.addAll(other.openOverride);
            closedOverride.clear();
            closedOverride.addAll(other.closedOverride);
            dirty();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putInt("Epoch", epoch);
            tag.putBoolean("HeartDefeated", heartDefeated);
            tag.putBoolean("GateOpened", gateOpened);
            tag.putInt("Clears", clears);
            tag.putLong("LastSeen", lastSeen);
            ListTag halls = new ListTag();
            clearedHalls.forEach(h -> halls.add(StringTag.valueOf(h)));
            tag.put("ClearedHalls", halls);
            tag.put("Data", data.copy());
            tag.put("Open", new LongArrayTag(openOverride.toLongArray()));
            tag.put("Closed", new LongArrayTag(closedOverride.toLongArray()));
            return tag;
        }

        static Record load(CompoundTag tag, BackGroupData owner) {
            Record r = new Record(tag.getUUID("Id"), owner);
            r.epoch = Math.max(1, tag.getInt("Epoch"));
            r.heartDefeated = tag.getBoolean("HeartDefeated");
            r.gateOpened = tag.getBoolean("GateOpened");
            r.clears = tag.getInt("Clears");
            r.lastSeen = tag.getLong("LastSeen");
            for (Tag t : tag.getList("ClearedHalls", Tag.TAG_STRING)) {
                r.clearedHalls.add(t.getAsString());
            }
            r.data = tag.getCompound("Data");
            for (long v : tag.getLongArray("Open")) {
                r.openOverride.add(v);
            }
            for (long v : tag.getLongArray("Closed")) {
                r.closedOverride.add(v);
            }
            return r;
        }
    }

    private final Map<UUID, Record> records = new LinkedHashMap<>();

    // 進みに数えない戸の位置 (試練のプールの入口の戸。TrialRuntime が設定する)
    public static volatile java.util.function.LongPredicate IGNORED_OVERRIDE = k -> false;

    public static BackGroupData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static BackGroupData load(CompoundTag tag, HolderLookup.Provider registries) {
        BackGroupData data = new BackGroupData();
        for (Tag t : tag.getList("Records", Tag.TAG_COMPOUND)) {
            Record r = Record.load((CompoundTag) t, data);
            data.records.put(r.id, r);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Record r : records.values()) {
            list.add(r.save());
        }
        tag.put("Records", list);
        return tag;
    }

    // グループの記録 (なければ作る)
    public Record of(UUID group) {
        Record r = records.get(group);
        if (r == null) {
            r = new Record(group, this);
            records.put(group, r);
            setDirty();
        }
        return r;
    }

    @Nullable
    public Record peek(UUID group) {
        return records.get(group);
    }

    public List<UUID> ids() {
        return new ArrayList<>(records.keySet());
    }

    public void remove(UUID group) {
        if (records.remove(group) != null) {
            setDirty();
        }
    }

    // from の進みを to に写す (to の元の中身は捨てる)
    public Record copy(UUID from, UUID to) {
        Record src = records.get(from);
        Record dst = of(to);
        if (src != null && src != dst) {
            dst.copyFrom(src);
        }
        return dst;
    }

    public int size() {
        return records.size();
    }
}
