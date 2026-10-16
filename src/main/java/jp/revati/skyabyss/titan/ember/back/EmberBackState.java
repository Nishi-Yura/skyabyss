package jp.revati.skyabyss.titan.ember.back;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

// 余燼の背の試練の状態 (ember_back のディメンションに保存。13 の 7 章)。
// 試練の番号 (休み場の記録を無効にする番号)・心臓の撃破・建てた版は、岩鯨・潮殻と共通の TitanBackData に置く。ここには余燼だけのものを置く。
//
// ほかの担当が読むもの: rung(t)・ready(t)・sleeping(t)・seed()・gateOpen()。書くのは EmberBackTrials の markReady・ring だけ。
public class EmberBackState extends SavedData {
    private static final String NAME = "skyabyss_ember_back";
    private static final Factory<EmberBackState> FACTORY = new Factory<>(EmberBackState::new, EmberBackState::load);

    public enum Trial {
        WEST, EAST, SOUTH;

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

    // 三つの熾り石が鳴っているか
    private final boolean[] rung = new boolean[3];
    // 石を鳴らせる (西は奥の岸に着いた人がいる、東は鍛え手を倒した、南は火床が 100 になった)
    private final boolean[] ready = new boolean[3];
    // 眠っている試練 (なければ null) と、前回眠った試練
    @Nullable
    private Trial sleeping;
    @Nullable
    private Trial lastSleeping;
    // ワールドで余燼の心臓が一度でも倒されたか (2 周目から試練が一つ眠る)
    private boolean everDefeated;
    // 熾の底の門が開いたか (三つの石がそろい、炉の火が落ちた)
    private boolean gateOpen;
    // リセットの種 (灰の道の島の並び 3 通り、鍛えの間の打つ順 4 通りなどを、各担当がここから決める)
    private int seed;
    // 鍛えの間で、いちばんやさしい打つ順をもう使ったか (ワールドで初めての挑戦だけ「赤 → 橙 → 黄」。C2 が読む)
    private boolean firstForgeUsed;
    private int resets;

    public static EmberBackState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static EmberBackState load(CompoundTag tag, HolderLookup.Provider registries) {
        EmberBackState s = new EmberBackState();
        for (int i = 0; i < 3; i++) {
            s.rung[i] = tag.getBoolean("Rung" + i);
            s.ready[i] = tag.getBoolean("Ready" + i);
        }
        s.sleeping = Trial.byId(tag.getString("Sleeping"));
        s.lastSleeping = Trial.byId(tag.getString("LastSleeping"));
        s.everDefeated = tag.getBoolean("EverDefeated");
        s.gateOpen = tag.getBoolean("GateOpen");
        s.seed = tag.getInt("Seed");
        s.firstForgeUsed = tag.getBoolean("FirstForgeUsed");
        s.resets = tag.getInt("Resets");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < 3; i++) {
            tag.putBoolean("Rung" + i, rung[i]);
            tag.putBoolean("Ready" + i, ready[i]);
        }
        tag.putString("Sleeping", sleeping == null ? "" : sleeping.id());
        tag.putString("LastSleeping", lastSleeping == null ? "" : lastSleeping.id());
        tag.putBoolean("EverDefeated", everDefeated);
        tag.putBoolean("GateOpen", gateOpen);
        tag.putInt("Seed", seed);
        tag.putBoolean("FirstForgeUsed", firstForgeUsed);
        tag.putInt("Resets", resets);
        return tag;
    }

    // ---------------------------------------------------------------- 読む
    public boolean rung(Trial t) {
        return rung[t.ordinal()];
    }

    public int rungCount() {
        int n = 0;
        for (boolean b : rung) {
            n += b ? 1 : 0;
        }
        return n;
    }

    public boolean ready(Trial t) {
        return ready[t.ordinal()];
    }

    @Nullable
    public Trial sleeping() {
        return sleeping;
    }

    public boolean sleeping(Trial t) {
        return sleeping == t;
    }

    // 部屋の名前で (smith は east と一緒に眠る)
    public boolean roomSleeping(String room) {
        if (sleeping == null) {
            return false;
        }
        return room.equals(sleeping.id()) || room.equals("smith") && sleeping == Trial.EAST;
    }

    public boolean everDefeated() {
        return everDefeated;
    }

    public boolean gateOpen() {
        return gateOpen;
    }

    public int seed() {
        return seed;
    }

    public boolean firstForgeUsed() {
        return firstForgeUsed;
    }

    public int resets() {
        return resets;
    }

    // ---------------------------------------------------------------- 書く (EmberBackTrials から)
    void ring(Trial t) {
        rung[t.ordinal()] = true;
        ready[t.ordinal()] = true;
        setDirty();
    }

    void markReady(Trial t) {
        ready[t.ordinal()] = true;
        setDirty();
    }

    void markGateOpen() {
        gateOpen = true;
        setDirty();
    }

    public void markEverDefeated() {
        everDefeated = true;
        setDirty();
    }

    // C2 が、ワールドで初めての挑戦の打つ順を使ったら呼ぶ
    public void markFirstForgeUsed() {
        firstForgeUsed = true;
        setDirty();
    }

    // 試練を最初に戻す (7.1節)。新しい種を引き、2 周目からは試練を一つ眠らせる (同じ試練は二回続けて眠らない)
    void reset(RandomSource random, @Nullable Trial forceSleep, boolean noSleep) {
        java.util.Arrays.fill(rung, false);
        java.util.Arrays.fill(ready, false);
        gateOpen = false;
        seed = random.nextInt();
        Trial next = null;
        if (forceSleep != null) {
            next = forceSleep;
        } else if (everDefeated && !noSleep) {
            List<Trial> pool = new ArrayList<>(List.of(Trial.values()));
            pool.remove(lastSleeping);
            next = pool.get(random.nextInt(pool.size()));
        }
        sleeping = next;
        if (next != null) {
            lastSleeping = next;
            rung[next.ordinal()] = true;
            ready[next.ordinal()] = true;
        }
        resets++;
        setDirty();
    }

    // 眠る試練をコマンドで決める (今の周に効かせる。null で眠らせない)
    void setSleeping(@Nullable Trial t) {
        if (sleeping != null && sleeping != t) {
            rung[sleeping.ordinal()] = false;
            ready[sleeping.ordinal()] = false;
        }
        sleeping = t;
        if (t != null) {
            rung[t.ordinal()] = true;
            ready[t.ordinal()] = true;
            lastSleeping = t;
        }
        setDirty();
    }
}
