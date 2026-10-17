package jp.revati.skyabyss.titan.storm.raiju;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 雷獣の巣の記録 (オーバーワールドのデータに一つ): いまの周で倒されたか、ワールドで一度でも倒されたか、巣を支度した版。
// 「倒された」は背中の記録 (StormBackState.raijuDefeated) と合わせる。背中のリセットがそちらを戻すと、RaijuBattle がこちらも戻す
public class RaijuState extends SavedData {
    private static final String NAME = "skyabyss_raiju";
    private static final Factory<RaijuState> FACTORY = new Factory<>(RaijuState::new, RaijuState::load);

    private boolean defeated;
    private boolean everDefeated;
    private int built;

    public static RaijuState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static RaijuState load(CompoundTag tag, HolderLookup.Provider registries) {
        RaijuState s = new RaijuState();
        s.defeated = tag.getBoolean("defeated");
        s.everDefeated = tag.getBoolean("ever_defeated");
        s.built = tag.getInt("built_real");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("defeated", defeated);
        tag.putBoolean("ever_defeated", everDefeated);
        tag.putInt("built_real", built);
        return tag;
    }

    public boolean defeated() {
        return defeated;
    }

    public boolean everDefeated() {
        return everDefeated;
    }

    public void setDefeated(boolean value) {
        defeated = value;
        if (value) {
            everDefeated = true;
        }
        setDirty();
    }

    public int built() {
        return built;
    }

    public void markBuilt(int version) {
        built = version;
        setDirty();
    }
}
