package jp.revati.skyabyss.titan.storm.back;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

// 雷翼の背の「背中そのもの」の状態 (storm_back のディメンションに保存。全員で一つ)。
//  - 嵐の拍 (clock): 景色と昇り風・横風の強さ。全員に同じ
//  - 風切りの骨の道の形 (westRoute): 割れた床の梁の位置はブロックに彫るので、見る人ごとに変えられない。全員で一つの道
//    (背中にだれもいなくなって 3 分たったときに、引き直して彫り直す)
// 試練の進み (鳴った石・骨の戸・受け羽・雷獣・竜骨の門・眠る試練・眼の間の配置) はグループごと: StormProgress (BackGroupData の記録)
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
    // 風切りの骨の道の形 (0〜2)
    private int westRoute;

    public static StormBackState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static StormBackState load(CompoundTag tag, HolderLookup.Provider registries) {
        StormBackState s = new StormBackState();
        s.clock = tag.getInt("Clock");
        s.westRoute = tag.getInt("WestRoute");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Clock", clock);
        tag.putInt("WestRoute", westRoute);
        return tag;
    }

    public int clock() {
        return clock;
    }

    public void setClock(int clock) {
        this.clock = clock;
        setDirty();
    }

    public int westRoute() {
        return westRoute;
    }

    public void setWestRoute(int route) {
        westRoute = Math.floorMod(route, 3);
        setDirty();
    }

    // 道の形を引き直し、拍を凪の始まりへ (背中にだれもいなくなったとき)
    public void reroll(RandomSource random) {
        westRoute = random.nextInt(3);
        clock = 0;
        setDirty();
    }
}
