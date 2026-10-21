package jp.revati.skyabyss.quest.free;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 自由なモードのサブクエストのワールドの記録 (オーバーワールドに保存、skyabyss_free_quests)。
//   boards  : 言伝ての板を立てた (か、もう立っていた) 村の鐘。壊されても立て直さない (プレイヤーが片付けたものを戻さない)
//   seen    : 人ごとに、最後に数えた空の影の通過 (始まりの時刻) と干潮の刻 (番号)。同じ通過・同じ刻を二度数えない
public class FreeQuestData extends SavedData {
    private static final String NAME = "skyabyss_free_quests";
    private static final Factory<FreeQuestData> FACTORY = new Factory<>(FreeQuestData::new, FreeQuestData::load);

    final Set<Long> boards = new HashSet<>();

    // この鐘のそばには板を立てない (都では組合の中に設計図が板を置く)
    public void markBoard(long bell) {
        if (boards.add(bell)) {
            setDirty();
        }
    }
    final Map<UUID, Long> lastPass = new HashMap<>();
    final Map<UUID, Long> lastTide = new HashMap<>();

    public static FreeQuestData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // その人が初めてこの値を見たなら true (覚える)
    boolean firstPass(UUID player, long passStart) {
        Long old = lastPass.get(player);
        if (old != null && old == passStart) {
            return false;
        }
        lastPass.put(player, passStart);
        setDirty();
        return true;
    }

    boolean firstTide(UUID player, long tide) {
        Long old = lastTide.get(player);
        if (old != null && old == tide) {
            return false;
        }
        lastTide.put(player, tide);
        setDirty();
        return true;
    }

    static FreeQuestData load(CompoundTag tag, HolderLookup.Provider registries) {
        FreeQuestData data = new FreeQuestData();
        for (Tag t : tag.getList("boards", Tag.TAG_LONG)) {
            data.boards.add(((LongTag) t).getAsLong());
        }
        readMap(tag.getCompound("last_pass"), data.lastPass);
        readMap(tag.getCompound("last_tide"), data.lastTide);
        return data;
    }

    private static void readMap(CompoundTag tag, Map<UUID, Long> into) {
        for (String key : tag.getAllKeys()) {
            try {
                into.put(UUID.fromString(key), tag.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // 壊れた鍵は捨てる
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        boards.forEach(b -> list.add(LongTag.valueOf(b)));
        tag.put("boards", list);
        CompoundTag pass = new CompoundTag();
        lastPass.forEach((k, v) -> pass.putLong(k.toString(), v));
        tag.put("last_pass", pass);
        CompoundTag tide = new CompoundTag();
        lastTide.forEach((k, v) -> tide.putLong(k.toString(), v));
        tag.put("last_tide", tide);
        return tag;
    }
}
