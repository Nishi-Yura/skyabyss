package jp.revati.skyabyss.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// パーティの記録 (オーバーワールドに保存。サーバーを止めても組んだまま)。
// 一人は一つのパーティにだけ入る。先頭がリーダー。招待は保存しない (Parties が覚える)
public final class PartyData extends SavedData {
    private static final String NAME = "skyabyss_parties";

    public static final class Party {
        public final UUID id;
        // 先頭がリーダー
        public final List<UUID> members = new ArrayList<>();
        // 名前の控え (オフラインの仲間も画面に出すため)
        public final Map<UUID, String> names = new HashMap<>();

        Party(UUID id) {
            this.id = id;
        }

        public UUID leader() {
            return members.get(0);
        }

        public boolean isLeader(UUID player) {
            return !members.isEmpty() && members.get(0).equals(player);
        }
    }

    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, Party> byMember = new HashMap<>();

    public static PartyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PartyData::new, PartyData::load), NAME);
    }

    @Nullable
    public Party of(UUID player) {
        return byMember.get(player);
    }

    public Iterable<Party> all() {
        return parties.values();
    }

    public Party create(UUID leader, String name) {
        Party p = new Party(UUID.randomUUID());
        parties.put(p.id, p);
        add(p, leader, name);
        return p;
    }

    public void add(Party p, UUID player, String name) {
        p.members.add(player);
        p.names.put(player, name);
        byMember.put(player, p);
        setDirty();
    }

    // 抜ける。残りが一人になったら解散する。抜けたのがリーダーなら次の人がリーダー
    public void remove(UUID player) {
        Party p = byMember.remove(player);
        if (p == null) {
            return;
        }
        p.members.remove(player);
        p.names.remove(player);
        if (p.members.size() <= 1) {
            for (UUID m : p.members) {
                byMember.remove(m);
            }
            parties.remove(p.id);
        }
        setDirty();
    }

    public void disband(Party p) {
        for (UUID m : p.members) {
            byMember.remove(m);
        }
        parties.remove(p.id);
        setDirty();
    }

    public void promote(Party p, UUID player) {
        if (p.members.remove(player)) {
            p.members.add(0, player);
            setDirty();
        }
    }

    private static PartyData load(CompoundTag tag, HolderLookup.Provider registries) {
        PartyData data = new PartyData();
        for (Tag t : tag.getList("parties", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Party p = new Party(c.getUUID("id"));
            for (Tag m : c.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag mc = (CompoundTag) m;
                UUID id = mc.getUUID("id");
                if (data.byMember.containsKey(id)) {
                    continue;
                }
                p.members.add(id);
                p.names.put(id, mc.getString("name"));
                data.byMember.put(id, p);
            }
            if (p.members.size() >= 2) {
                data.parties.put(p.id, p);
            } else {
                p.members.forEach(data.byMember::remove);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Party p : parties.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", p.id);
            ListTag members = new ListTag();
            for (UUID m : p.members) {
                CompoundTag mc = new CompoundTag();
                mc.putUUID("id", m);
                mc.putString("name", p.names.getOrDefault(m, "?"));
                members.add(mc);
            }
            c.put("members", members);
            list.add(c);
        }
        tag.put("parties", list);
        return tag;
    }

    // 自己テスト用: 写し取りと戻し
    public CompoundTag snapshot(HolderLookup.Provider registries) {
        return save(new CompoundTag(), registries);
    }

    public void clearAll() {
        parties.clear();
        byMember.clear();
        setDirty();
    }

    public void restore(CompoundTag tag, HolderLookup.Provider registries) {
        PartyData r = load(tag, registries);
        parties.clear();
        byMember.clear();
        parties.putAll(r.parties);
        byMember.putAll(r.byMember);
        setDirty();
    }

}
