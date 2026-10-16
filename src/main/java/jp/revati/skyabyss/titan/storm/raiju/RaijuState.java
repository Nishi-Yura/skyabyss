package jp.revati.skyabyss.titan.storm.raiju;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 雷獣の巣の記録 (オーバーワールドのデータに一つ): いまの周で倒されたか、ワールドで一度でも倒されたか、巣を建てた版 (本番・仮)。
// 「倒された」は背中のリセット (担当 1 の TitanBackReset 相当) が RaijuBattle.reawaken で戻す
public class RaijuState extends SavedData {
    private static final String NAME = "skyabyss_raiju";
    private static final Factory<RaijuState> FACTORY = new Factory<>(RaijuState::new, RaijuState::load);

    private boolean defeated;
    private boolean everDefeated;
    private int builtReal;
    private int builtTemp;

    public static RaijuState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static RaijuState load(CompoundTag tag, HolderLookup.Provider registries) {
        RaijuState s = new RaijuState();
        s.defeated = tag.getBoolean("defeated");
        s.everDefeated = tag.getBoolean("ever_defeated");
        s.builtReal = tag.getInt("built_real");
        s.builtTemp = tag.getInt("built_temp");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("defeated", defeated);
        tag.putBoolean("ever_defeated", everDefeated);
        tag.putInt("built_real", builtReal);
        tag.putInt("built_temp", builtTemp);
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

    public int built(boolean temporary) {
        return temporary ? builtTemp : builtReal;
    }

    public void markBuilt(boolean temporary, int version) {
        if (temporary) {
            builtTemp = version;
        } else {
            builtReal = version;
        }
        setDirty();
    }
}
