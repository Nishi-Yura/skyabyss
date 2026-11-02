package jp.revati.skyabyss.facility;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.INBTSerializable;

// 施設で一度だけのことの印 (人ごと。死んでも残る)。
//   brew_lesson : 薬師に醸造を教わった (四章。手ほどきの品は一度だけ)
public final class FacilityMarks implements INBTSerializable<CompoundTag> {
    public static final String BREW_LESSON = "brew_lesson";

    final Set<String> marks = new LinkedHashSet<>();

    public static boolean has(ServerPlayer player, String mark) {
        return player.getData(FacilitySetup.MARKS).marks.contains(mark);
    }

    // 付けた (はじめて) なら true
    public static boolean add(ServerPlayer player, String mark) {
        return player.getData(FacilitySetup.MARKS).marks.add(mark);
    }

    public static boolean remove(ServerPlayer player, String mark) {
        return player.getData(FacilitySetup.MARKS).marks.remove(mark);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        marks.forEach(m -> list.add(StringTag.valueOf(m)));
        tag.put("marks", list);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        marks.clear();
        for (Tag t : tag.getList("marks", Tag.TAG_STRING)) {
            marks.add(t.getAsString());
        }
    }
}
