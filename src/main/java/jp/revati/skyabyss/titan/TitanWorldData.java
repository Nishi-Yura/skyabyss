package jp.revati.skyabyss.titan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

// 巨神の記録 (オーバーワールドに保存)。
// 巨神は種類ごとにワールドに一体だけ。どの個体が「本物」かを UUID で覚え、最後に見た位置と状態も残す。
// 本物が読み込まれていない間に呼び直したときは、この状態を新しい個体へ引き継ぐ。
// 巨神ごとの記録は Slot。岩鯨の記録は前からのキー (StoneWhaleSpawned, WhaleId, LastX...) のまま一番上に置き、
// ほかの巨神は "Titans" の下に名前 (TitanKeeper の slotId) ごとに置く
public final class TitanWorldData extends SavedData {
    private static final Factory<TitanWorldData> FACTORY = new Factory<>(TitanWorldData::new, TitanWorldData::load);
    public static final String STONE_WHALE = "stone_whale";

    // 巨神一体ぶんの記録
    public static final class Slot {
        // 一度でも現れたか (自然の出現は一度だけ。以後は見守りが呼び直す)
        public boolean spawned;
        @Nullable
        public UUID id;
        public boolean hasLast;
        public double lastX;
        public double lastY;
        public double lastZ;
        public float lastYaw;
        public CompoundTag state = new CompoundTag();

        void read(CompoundTag tag, String spawnedKey, String idKey) {
            spawned = tag.getBoolean(spawnedKey);
            id = tag.hasUUID(idKey) ? tag.getUUID(idKey) : null;
            hasLast = tag.contains("LastX");
            lastX = tag.getDouble("LastX");
            lastY = tag.getDouble("LastY");
            lastZ = tag.getDouble("LastZ");
            lastYaw = tag.getFloat("LastYaw");
            state = tag.getCompound("State");
        }

        void write(CompoundTag tag, String spawnedKey, String idKey) {
            tag.putBoolean(spawnedKey, spawned);
            if (id != null) {
                tag.putUUID(idKey, id);
            }
            if (hasLast) {
                tag.putDouble("LastX", lastX);
                tag.putDouble("LastY", lastY);
                tag.putDouble("LastZ", lastZ);
                tag.putFloat("LastYaw", lastYaw);
            }
            tag.put("State", state);
        }

        void copyFrom(Slot o) {
            spawned = o.spawned;
            id = o.id;
            hasLast = o.hasLast;
            lastX = o.lastX;
            lastY = o.lastY;
            lastZ = o.lastZ;
            lastYaw = o.lastYaw;
            state = o.state.copy();
        }

        @Nullable
        public net.minecraft.world.phys.Vec3 lastPosition() {
            return hasLast ? new net.minecraft.world.phys.Vec3(lastX, lastY, lastZ) : null;
        }
    }

    private final Slot whale = new Slot();
    private final Map<String, Slot> others = new LinkedHashMap<>();

    public static TitanWorldData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_titans");
    }

    // 巨神ごとの記録。なければ作る
    public Slot slot(String id) {
        return STONE_WHALE.equals(id) ? whale : others.computeIfAbsent(id, k -> new Slot());
    }

    Slot whale() {
        return whale;
    }

    @Nullable
    public UUID whaleId() {
        return whale.id;
    }

    // 巨神での移動 (titan.travel) が、読み込まれていない岩鯨を呼び寄せるときに使う
    public boolean hasSpawned() {
        return whale.spawned;
    }

    public CompoundTag lastState() {
        return whale.state.copy();
    }

    @Nullable
    public net.minecraft.world.phys.Vec3 lastPosition() {
        return whale.lastPosition();
    }

    // 自己テスト用: 今の記録を写し取り、あとで元に戻す
    public CompoundTag snapshot(HolderLookup.Provider registries) {
        return save(new CompoundTag(), registries);
    }

    public void restore(CompoundTag tag, HolderLookup.Provider registries) {
        TitanWorldData r = load(tag, registries);
        whale.copyFrom(r.whale);
        others.clear();
        r.others.forEach((k, v) -> slot(k).copyFrom(v));
        setDirty();
    }

    // 一つの巨神の記録だけを元に戻す (自己テスト用)
    public void restoreSlot(String id, CompoundTag tag, HolderLookup.Provider registries) {
        slot(id).copyFrom(load(tag, registries).slot(id));
        setDirty();
    }

    void register(Slot slot, Entity titan, CompoundTag state) {
        slot.spawned = true;
        slot.id = titan.getUUID();
        remember(slot, titan, state);
    }

    void remember(Slot slot, Entity titan, CompoundTag state) {
        slot.hasLast = true;
        slot.lastX = titan.getX();
        slot.lastY = titan.getY();
        slot.lastZ = titan.getZ();
        slot.lastYaw = titan.getYRot();
        slot.state = state;
        setDirty();
    }

    static TitanWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        TitanWorldData data = new TitanWorldData();
        data.whale.read(tag, "StoneWhaleSpawned", "WhaleId");
        CompoundTag titans = tag.getCompound("Titans");
        for (String key : titans.getAllKeys()) {
            data.slot(key).read(titans.getCompound(key), "Spawned", "Id");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        whale.write(tag, "StoneWhaleSpawned", "WhaleId");
        if (!others.isEmpty()) {
            CompoundTag titans = new CompoundTag();
            others.forEach((k, v) -> {
                CompoundTag t = new CompoundTag();
                v.write(t, "Spawned", "Id");
                titans.put(k, t);
            });
            tag.put("Titans", titans);
        }
        return tag;
    }
}
