package jp.revati.skyabyss.titan.tide.back;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

// 潮殻の背の試練の状態 (tide_back のディメンションに保存)。
// 試練の番号 (休み場の記録を無効にする番号) と心臓の撃破は、岩鯨と共通の TitanBackData に置く。ここには潮殻だけのものを置く
public class TideBackState extends SavedData {
    private static final String NAME = "skyabyss_tide_back";
    private static final Factory<TideBackState> FACTORY = new Factory<>(TideBackState::new, TideBackState::load);

    public enum Trial {
        WEST, EAST, FRONT;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Trial byId(String id) {
            for (Trial t : values()) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
            return null;
        }
    }

    // 潮の時計 (一周 2400 tick の中の位置)
    int clock;
    // 三つの潮鳴り石が鳴っているか
    private final boolean[] rung = new boolean[3];
    // 眠っている試練 (なければ null) と、前回眠った試練
    private Trial sleeping;
    private Trial lastSleeping;
    // ワールドで潮殻の心臓が一度でも倒されたか (2 周目から試練が一つ眠る)
    private boolean everDefeated;
    // 潮見の間で、いちばんやさしい配置をもう使ったか
    private boolean eastFirstUsed;
    // リセットの種で決めたもの: 庭の道の形 (0〜2)、潮見の間の配置 (0〜3)、巣の場所 (6 か所の中の 3 つ。ビット)
    private int gardenRoute;
    private int eastConfig;
    private int nestSites = 0b000111;
    // 壊した巣 (ビット)、潮招きを倒したか
    private int nestsBroken;
    private boolean beckonerDefeated;
    // 潮の底の門が開いたか (三つの石がそろい、水盤が引ききった)
    private boolean bottomOpen;
    private int resets;

    public static TideBackState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static TideBackState load(CompoundTag tag, HolderLookup.Provider registries) {
        TideBackState s = new TideBackState();
        s.clock = tag.getInt("Clock");
        for (int i = 0; i < 3; i++) {
            s.rung[i] = tag.getBoolean("Rung" + i);
        }
        s.sleeping = Trial.byId(tag.getString("Sleeping"));
        s.lastSleeping = Trial.byId(tag.getString("LastSleeping"));
        s.everDefeated = tag.getBoolean("EverDefeated");
        s.eastFirstUsed = tag.getBoolean("EastFirstUsed");
        s.gardenRoute = tag.getInt("GardenRoute");
        s.eastConfig = tag.getInt("EastConfig");
        s.nestSites = tag.contains("NestSites") ? tag.getInt("NestSites") : 0b000111;
        s.nestsBroken = tag.getInt("NestsBroken");
        s.beckonerDefeated = tag.getBoolean("BeckonerDefeated");
        s.bottomOpen = tag.getBoolean("BottomOpen");
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
        tag.putBoolean("EastFirstUsed", eastFirstUsed);
        tag.putInt("GardenRoute", gardenRoute);
        tag.putInt("EastConfig", eastConfig);
        tag.putInt("NestSites", nestSites);
        tag.putInt("NestsBroken", nestsBroken);
        tag.putBoolean("BeckonerDefeated", beckonerDefeated);
        tag.putBoolean("BottomOpen", bottomOpen);
        tag.putInt("Resets", resets);
        return tag;
    }

    public int clock() {
        return clock;
    }

    void setClock(int value) {
        clock = value;
        setDirty();
    }

    public boolean rung(Trial t) {
        return rung[t.ordinal()];
    }

    public void ring(Trial t) {
        rung[t.ordinal()] = true;
        setDirty();
    }

    public int rungCount() {
        int n = 0;
        for (boolean b : rung) {
            n += b ? 1 : 0;
        }
        return n;
    }

    public Trial sleeping() {
        return sleeping;
    }

    public boolean everDefeated() {
        return everDefeated;
    }

    public void markEverDefeated() {
        everDefeated = true;
        setDirty();
    }

    public int gardenRoute() {
        return gardenRoute;
    }

    public int eastConfig() {
        return eastConfig;
    }

    public int nestSites() {
        return nestSites;
    }

    public boolean nestBroken(int site) {
        return (nestsBroken & (1 << site)) != 0;
    }

    public void breakNest(int site) {
        nestsBroken |= 1 << site;
        setDirty();
    }

    public boolean nestsBrokenAny() {
        return (nestsBroken & nestSites) != 0;
    }

    // 選ばれた巣がすべて壊れたか
    public boolean allNestsBroken() {
        return (nestsBroken & nestSites) == nestSites;
    }

    public boolean beckonerDefeated() {
        return beckonerDefeated;
    }

    public void markBeckonerDefeated() {
        beckonerDefeated = true;
        setDirty();
    }

    public boolean bottomOpen() {
        return bottomOpen;
    }

    public void markBottomOpen() {
        bottomOpen = true;
        setDirty();
    }

    public int resets() {
        return resets;
    }

    // 試練を最初に戻す (07 の 7.1節)。新しい種を引き、2 周目からは試練を一つ眠らせる (同じ試練は二回続けて眠らない)
    public void reset(RandomSource random, Trial forceSleep, boolean noSleep) {
        java.util.Arrays.fill(rung, false);
        nestsBroken = 0;
        beckonerDefeated = false;
        bottomOpen = false;
        clock = 0;
        gardenRoute = random.nextInt(3);
        if (!eastFirstUsed) {
            eastConfig = 0;
            eastFirstUsed = true;
        } else {
            eastConfig = 1 + random.nextInt(3);
        }
        // 6 か所から 3 つ
        int sites = 0;
        while (Integer.bitCount(sites) < 3) {
            sites |= 1 << random.nextInt(6);
        }
        nestSites = sites;
        Trial next = null;
        if (forceSleep != null) {
            next = forceSleep;
        } else if (everDefeated && !noSleep) {
            java.util.List<Trial> pool = new java.util.ArrayList<>(java.util.List.of(Trial.values()));
            pool.remove(lastSleeping);
            next = pool.get(random.nextInt(pool.size()));
        }
        sleeping = next;
        if (next != null) {
            lastSleeping = next;
            rung[next.ordinal()] = true;
        }
        resets++;
        setDirty();
    }

    // 眠る試練をコマンドで決める (今の周に効かせる)
    public void setSleeping(Trial t) {
        if (sleeping != null && sleeping != t) {
            rung[sleeping.ordinal()] = false;
        }
        sleeping = t;
        if (t != null) {
            rung[t.ordinal()] = true;
            lastSleeping = t;
        }
        setDirty();
    }

    public boolean eastFirstUsed() {
        return eastFirstUsed;
    }
}
