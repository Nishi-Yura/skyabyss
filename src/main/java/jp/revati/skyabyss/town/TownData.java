package jp.revati.skyabyss.town;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 都の町のクエストと裏通りの、ワールドの記録 (docs/design/35 の 3.3・3.5)。
//   cellarOpen : 酒場の地下の奥の壁を開けた (一度開けたら開いたまま。都を建て直したら TownCellar が開け直す)
//   wellTaken  : 古井戸の下の宝を受け取った人 (一人一度)
// クエストの進み自体は人ごとの QuestLog (quest パッケージ) が持つ。ここは人をまたぐものだけ
public final class TownData extends SavedData {
    private static final String NAME = "skyabyss_town";
    private static final Factory<TownData> FACTORY = new Factory<>(TownData::new, TownData::load);

    public boolean cellarOpen;
    public final Set<UUID> wellTaken = new LinkedHashSet<>();

    public static TownData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static TownData load(CompoundTag tag, HolderLookup.Provider registries) {
        TownData d = new TownData();
        d.cellarOpen = tag.getBoolean("CellarOpen");
        for (Tag t : tag.getList("WellTaken", Tag.TAG_INT_ARRAY)) {
            d.wellTaken.add(NbtUtils.loadUUID(t));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("CellarOpen", cellarOpen);
        ListTag list = new ListTag();
        wellTaken.forEach(u -> list.add(NbtUtils.createUUID(u)));
        tag.put("WellTaken", list);
        return tag;
    }
}
