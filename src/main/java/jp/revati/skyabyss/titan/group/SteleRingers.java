package jp.revati.skyabyss.titan.group;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 試練の石を「誰が鳴らしたか」の控え (rpg_brushup A4、docs/design/10 の 10.9)。表示のためだけのもので、石の共有の仕組み
// (鳴ったか = グループの記録、踏破の数え方) には触らない。グループ → 石の合図 (TrialSteles.signal) → 鳴らした人。
// 石が鳴っていない (グループが試練を戻した) ときは TrialSteles.who が「まだ」を返すので、古い控えが残っていても見えない
public final class SteleRingers extends SavedData {
    private static final String NAME = "skyabyss_stele_ringers";

    public record Ringer(UUID id, String name) {
    }

    private final Map<UUID, Map<String, Ringer>> byGroup = new HashMap<>();

    public static SteleRingers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SteleRingers::new, SteleRingers::load), NAME);
    }

    public void put(UUID group, String signal, UUID player, String name) {
        byGroup.computeIfAbsent(group, k -> new HashMap<>()).put(signal, new Ringer(player, name));
        setDirty();
    }

    @Nullable
    public Ringer get(UUID group, String signal) {
        Map<String, Ringer> m = byGroup.get(group);
        return m == null ? null : m.get(signal);
    }

    // 自己テスト用: そのグループの控えを消す
    public void forget(UUID group) {
        if (byGroup.remove(group) != null) {
            setDirty();
        }
    }

    private static SteleRingers load(CompoundTag tag, HolderLookup.Provider registries) {
        SteleRingers data = new SteleRingers();
        CompoundTag groups = tag.getCompound("groups");
        for (String g : groups.getAllKeys()) {
            try {
                UUID group = UUID.fromString(g);
                CompoundTag signals = groups.getCompound(g);
                for (String signal : signals.getAllKeys()) {
                    CompoundTag r = signals.getCompound(signal);
                    if (r.hasUUID("id")) {
                        data.byGroup.computeIfAbsent(group, k -> new HashMap<>()).put(signal, new Ringer(r.getUUID("id"), r.getString("name")));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // 壊れた控えは捨てる
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag groups = new CompoundTag();
        byGroup.forEach((group, signals) -> {
            CompoundTag s = new CompoundTag();
            signals.forEach((signal, r) -> {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", r.id());
                c.putString("name", r.name());
                s.put(signal, c);
            });
            groups.put(group.toString(), s);
        });
        tag.put("groups", groups);
        return tag;
    }
}
