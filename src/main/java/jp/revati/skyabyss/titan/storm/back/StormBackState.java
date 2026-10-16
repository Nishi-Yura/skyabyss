package jp.revati.skyabyss.titan.storm.back;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 雷翼の背の試練の状態 (storm_back のディメンションに保存)。
// 試練の番号 (休み場の記録を無効にする番号) と心臓の撃破は、岩鯨・潮殻と共通の TitanBackData に置く。ここには雷翼だけのものを置く
public class StormBackState extends SavedData {
    private static final String NAME = "skyabyss_storm_back";
    private static final Factory<StormBackState> FACTORY = new Factory<>(StormBackState::new, StormBackState::load);

    public enum Trial {
        WEST, EAST, FRONT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        public static Trial byId(String id) {
            for (Trial t : values()) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
            return null;
        }
    }

    // 嵐の拍 (一周 1800 tick の中の位置)
    int clock;
    // 三つの風鳴り石が鳴っているか
    private final boolean[] rung = new boolean[3];
    @Nullable
    private Trial sleeping;
    @Nullable
    private Trial lastSleeping;
    // ワールドで雷翼の心臓が一度でも倒されたか (2 周目から試練が一つ眠る)
    private boolean everDefeated;
    // 眼の間で、いちばんやさしい配置をもう使ったか
    private boolean mirrorsFirstUsed;
    // リセットの種で決めたもの: 風切りの骨の道の形 (0〜2)、眼の間の配置 (0〜3)
    private int westRoute;
    private int mirrorConfig;
    // 東の骨の戸 (ビット)、雷獣を倒したか、竜骨の門が開いたか
    private int eastDoors;
    private boolean raijuDefeated;
    private boolean keelOpen;
    // 眼の間の受け羽 (ビット) と、鏡の向き (台ごとに 2 ビット)
    private int receivers;
    private long mirrorFacing;
    private int resets;

    public static StormBackState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static StormBackState load(CompoundTag tag, HolderLookup.Provider registries) {
        StormBackState s = new StormBackState();
        s.clock = tag.getInt("Clock");
        for (int i = 0; i < 3; i++) {
            s.rung[i] = tag.getBoolean("Rung" + i);
        }
        s.sleeping = Trial.byId(tag.getString("Sleeping"));
        s.lastSleeping = Trial.byId(tag.getString("LastSleeping"));
        s.everDefeated = tag.getBoolean("EverDefeated");
        s.mirrorsFirstUsed = tag.getBoolean("MirrorsFirstUsed");
        s.westRoute = tag.getInt("WestRoute");
        s.mirrorConfig = tag.getInt("MirrorConfig");
        s.eastDoors = tag.getInt("EastDoors");
        s.raijuDefeated = tag.getBoolean("RaijuDefeated");
        s.keelOpen = tag.getBoolean("KeelOpen");
        s.receivers = tag.getInt("Receivers");
        s.mirrorFacing = tag.getLong("MirrorFacing");
        s.resets = tag.getInt("Resets");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Clock", clock);
        for (int i = 0; i < 3; i++) {
            tag.putBoolean("Rung" + i, rung[i]);
        }
        tag.putString("Sleeping", sleeping == null ? "" : sleeping.id());
        tag.putString("LastSleeping", lastSleeping == null ? "" : lastSleeping.id());
        tag.putBoolean("EverDefeated", everDefeated);
        tag.putBoolean("MirrorsFirstUsed", mirrorsFirstUsed);
        tag.putInt("WestRoute", westRoute);
        tag.putInt("MirrorConfig", mirrorConfig);
        tag.putInt("EastDoors", eastDoors);
        tag.putBoolean("RaijuDefeated", raijuDefeated);
        tag.putBoolean("KeelOpen", keelOpen);
        tag.putInt("Receivers", receivers);
        tag.putLong("MirrorFacing", mirrorFacing);
        tag.putInt("Resets", resets);
        return tag;
    }

    public int clock() {
        return clock;
    }

    public void setClock(int clock) {
        this.clock = clock;
        setDirty();
    }

    public boolean rung(Trial t) {
        return rung[t.ordinal()];
    }

    public void setRung(Trial t, boolean v) {
        rung[t.ordinal()] = v;
        setDirty();
    }

    public int rungCount() {
        int n = 0;
        for (boolean b : rung) {
            n += b ? 1 : 0;
        }
        return n;
    }

    // 眠っている試練の石は鳴ったものとして数える
    public boolean allRung() {
        for (Trial t : Trial.values()) {
            if (!rung(t) && t != sleeping) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public Trial sleeping() {
        return sleeping;
    }

    @Nullable
    public Trial lastSleeping() {
        return lastSleeping;
    }

    public void setSleeping(@Nullable Trial t) {
        if (t != null) {
            lastSleeping = t;
        }
        sleeping = t;
        setDirty();
    }

    public boolean everDefeated() {
        return everDefeated;
    }

    public void markDefeated() {
        everDefeated = true;
        setDirty();
    }

    public boolean mirrorsFirstUsed() {
        return mirrorsFirstUsed;
    }

    public void markMirrorsFirstUsed() {
        mirrorsFirstUsed = true;
        setDirty();
    }

    public int westRoute() {
        return westRoute;
    }

    public int mirrorConfig() {
        return mirrorConfig;
    }

    public void setRoutes(int westRoute, int mirrorConfig) {
        this.westRoute = westRoute;
        this.mirrorConfig = mirrorConfig;
        setDirty();
    }

    public boolean eastDoorOpen(int i) {
        return (eastDoors & (1 << i)) != 0;
    }

    public void openEastDoor(int i) {
        eastDoors |= 1 << i;
        setDirty();
    }

    public boolean raijuDefeated() {
        return raijuDefeated;
    }

    public void setRaijuDefeated(boolean v) {
        raijuDefeated = v;
        setDirty();
    }

    public boolean keelOpen() {
        return keelOpen;
    }

    public void setKeelOpen(boolean v) {
        keelOpen = v;
        setDirty();
    }

    public int receivers() {
        return receivers;
    }

    public void setReceivers(int bits) {
        receivers = bits;
        setDirty();
    }

    public int mirrorFacing(int i) {
        return (int) ((mirrorFacing >> (i * 2)) & 3);
    }

    public void setMirrorFacing(int i, int facing) {
        mirrorFacing = (mirrorFacing & ~(3L << (i * 2))) | ((long) (facing & 3) << (i * 2));
        setDirty();
    }

    public int resets() {
        return resets;
    }

    // 試練を戻す (拍は凪の始まりへ)
    void clearTrials() {
        java.util.Arrays.fill(rung, false);
        eastDoors = 0;
        raijuDefeated = false;
        keelOpen = false;
        receivers = 0;
        mirrorFacing = 0;
        clock = 0;
        resets++;
        setDirty();
    }
}
