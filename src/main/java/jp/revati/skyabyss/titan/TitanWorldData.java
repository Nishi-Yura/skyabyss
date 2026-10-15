package jp.revati.skyabyss.titan;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 岩鯨の記録 (オーバーワールドに保存)。
// 岩鯨はワールドに一体だけ。どの個体が「本物」かを UUID で覚え、最後に見た位置と状態も残す。
// 本物が読み込まれていない間に呼び直したときは、この状態を新しい個体へ引き継ぐ
public final class TitanWorldData extends SavedData {
    private static final Factory<TitanWorldData> FACTORY = new Factory<>(TitanWorldData::new, TitanWorldData::load);

    // 一度でも現れたか (自然の出現は一度だけ。以後は見守りが呼び直す)
    boolean spawned;
    @Nullable
    UUID whaleId;
    boolean hasLast;
    double lastX;
    double lastY;
    double lastZ;
    float lastYaw;
    CompoundTag state = new CompoundTag();

    public static TitanWorldData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_titans");
    }

    @Nullable
    public UUID whaleId() {
        return whaleId;
    }

    void register(StoneWhaleEntity whale) {
        spawned = true;
        whaleId = whale.getUUID();
        remember(whale);
    }

    void remember(StoneWhaleEntity whale) {
        hasLast = true;
        lastX = whale.getX();
        lastY = whale.getY();
        lastZ = whale.getZ();
        lastYaw = whale.getYRot();
        state = whale.saveState();
        setDirty();
    }

    static TitanWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        TitanWorldData data = new TitanWorldData();
        data.spawned = tag.getBoolean("StoneWhaleSpawned");
        if (tag.hasUUID("WhaleId")) {
            data.whaleId = tag.getUUID("WhaleId");
        }
        data.hasLast = tag.contains("LastX");
        data.lastX = tag.getDouble("LastX");
        data.lastY = tag.getDouble("LastY");
        data.lastZ = tag.getDouble("LastZ");
        data.lastYaw = tag.getFloat("LastYaw");
        data.state = tag.getCompound("State");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("StoneWhaleSpawned", spawned);
        if (whaleId != null) {
            tag.putUUID("WhaleId", whaleId);
        }
        if (hasLast) {
            tag.putDouble("LastX", lastX);
            tag.putDouble("LastY", lastY);
            tag.putDouble("LastZ", lastZ);
            tag.putFloat("LastYaw", lastYaw);
        }
        tag.put("State", state);
        return tag;
    }
}
