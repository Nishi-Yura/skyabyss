package jp.revati.skyabyss.titan.tide.back;

import java.util.UUID;

import jp.revati.skyabyss.titan.group.BackGroupData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

// 潮殻の背の試練の状態 (tide_back のディメンションに保存)。
//  - 島で一つのもの (ここに保存): 潮の時計の位置。潮は景色なので、全員が同じ潮を見る
//  - グループ (パーティ、または一人) ごとのもの: Group (BackGroupData の記録の data 欄に "tide." で書く)。
//    鳴った石・眠る試練・種 (庭の道の形・潮見の間の配置・巣の場所)・壊した巣・潮招き・潮の底の門。
//    試練の番号 (休み場の記録を無効にする番号) と心臓の撃破も、そのグループの記録 (BackGroupData.Record) が持つ。
//    "keep." で始まる欄 (前に眠った試練・心臓を一度でも倒したか・やさしい配置を使ったか・リセットの数) はリセットでも残る
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

    public static TideBackState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static TideBackState load(CompoundTag tag, HolderLookup.Provider registries) {
        TideBackState s = new TideBackState();
        s.clock = tag.getInt("Clock");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Clock", clock);
        return tag;
    }

    public int clock() {
        return clock;
    }

    void setClock(int value) {
        clock = value;
        setDirty();
    }

    // そのグループの進み (記録がなければ作り、種がなければ引く)
    public static Group of(ServerLevel level, UUID group) {
        Group g = new Group(group, BackGroupData.get(level).of(group));
        g.ensureSeeded(level.random);
        return g;
    }

    // ---------------------------------------------------------------- グループごとの進み
    public static final class Group {
        private static final String RUNG = "tide.rung";
        private static final String SLEEPING = "tide.sleeping";
        private static final String SEEDED = "tide.seeded";
        private static final String ROUTE = "tide.route";
        private static final String EAST = "tide.east";
        private static final String NEST_SITES = "tide.nest_sites";
        private static final String NESTS_BROKEN = "tide.nests_broken";
        private static final String BECKONER = "tide.beckoner";
        private static final String BOTTOM = "tide.bottom";
        private static final String UP_BEST = "tide.up_best";
        private static final String KEEPER = "tide.keeper";
        private static final String KEEPER_EVER = "keep.tide.keeper_ever";
        private static final String LAST_SLEEPING = "keep.tide.last_sleeping";
        private static final String EVER_DEFEATED = "keep.tide.ever_defeated";
        private static final String EAST_FIRST_USED = "keep.tide.east_first_used";
        private static final String RESETS = "keep.tide.resets";

        public final UUID id;
        public final BackGroupData.Record record;

        Group(UUID id, BackGroupData.Record record) {
            this.id = id;
            this.record = record;
        }

        // 種がまだなければ引く (初めて来たグループ。リセットのときは reset が引き直す)
        void ensureSeeded(RandomSource random) {
            if (!record.flag(SEEDED)) {
                seed(random);
            }
        }

        private void seed(RandomSource random) {
            record.putInt(ROUTE, random.nextInt(3));
            if (!record.flag(EAST_FIRST_USED)) {
                // そのグループの初めての周は、いちばんやさしい配置
                record.putInt(EAST, 0);
                record.setFlag(EAST_FIRST_USED, true);
            } else {
                record.putInt(EAST, 1 + random.nextInt(3));
            }
            // 6 か所から 3 つ
            int sites = 0;
            while (Integer.bitCount(sites) < 3) {
                sites |= 1 << random.nextInt(6);
            }
            record.putInt(NEST_SITES, sites);
            record.setFlag(SEEDED, true);
        }

        public int epoch() {
            return record.epoch();
        }

        public boolean heartDefeated() {
            return record.isHeartDefeated();
        }

        public boolean rung(Trial t) {
            return (record.getInt(RUNG) & (1 << t.ordinal())) != 0;
        }

        public void ring(Trial t) {
            record.putInt(RUNG, record.getInt(RUNG) | (1 << t.ordinal()));
        }

        public int rungCount() {
            return Integer.bitCount(record.getInt(RUNG) & 0b111);
        }

        public Trial sleeping() {
            return Trial.byId(record.getString(SLEEPING));
        }

        public boolean everDefeated() {
            return record.flag(EVER_DEFEATED);
        }

        public void markEverDefeated() {
            record.setFlag(EVER_DEFEATED, true);
        }

        public int gardenRoute() {
            return record.getInt(ROUTE);
        }

        public int eastConfig() {
            return record.getInt(EAST);
        }

        public boolean eastFirstUsed() {
            return record.flag(EAST_FIRST_USED);
        }

        public int nestSites() {
            int s = record.getInt(NEST_SITES);
            return s == 0 ? 0b000111 : s;
        }

        public boolean nestBroken(int site) {
            return (record.getInt(NESTS_BROKEN) & (1 << site)) != 0;
        }

        public void breakNest(int site) {
            record.putInt(NESTS_BROKEN, record.getInt(NESTS_BROKEN) | (1 << site));
        }

        public boolean nestsBrokenAny() {
            return (record.getInt(NESTS_BROKEN) & nestSites()) != 0;
        }

        // 選ばれた巣がすべて壊れたか
        public boolean allNestsBroken() {
            return (record.getInt(NESTS_BROKEN) & nestSites()) == nestSites();
        }

        public boolean beckonerDefeated() {
            return record.flag(BECKONER);
        }

        public void markBeckonerDefeated() {
            record.setFlag(BECKONER, true);
        }

        public boolean bottomOpen() {
            return record.flag(BOTTOM);
        }

        public void markBottomOpen() {
            record.setFlag(BOTTOM, true);
        }

        // 息の間: これまでに着いた足もとの高さ (縁を 0 とした「息の目盛り」)。壁の輪が灯る
        public int upBest() {
            return record.getInt(UP_BEST);
        }

        public void setUpBest(int height) {
            record.putInt(UP_BEST, height);
        }

        // 息の間の頂の真珠守りを、この周で倒したか。倒すと展望の間への風の柱が働く (周回のグループ・ピースフルは倒さなくても働く)
        public boolean keeperDefeated() {
            return record.flag(KEEPER);
        }

        public void markKeeperDefeated() {
            record.setFlag(KEEPER, true);
            record.setFlag(KEEPER_EVER, true);
        }

        public boolean keeperEver() {
            return record.flag(KEEPER_EVER);
        }

        public int resets() {
            return record.getInt(RESETS);
        }

        // 戻す値打ちのある進みがあるか (眠っている試練の石は数えない。種だけでは戻さない)
        public boolean hasTrialProgress() {
            return rungCount() > (sleeping() != null ? 1 : 0) || heartDefeated() || nestsBrokenAny() || beckonerDefeated() || bottomOpen();
        }

        // 試練を最初に戻す (08 の 7.1節)。試練の番号を進め (記録の進みと扉の上書きも消える)、新しい種を引く。
        // 心臓を一度でも倒したグループは、試練を一つ眠らせる (同じ試練は二回続けて眠らない)
        public void reset(RandomSource random, Trial forceSleep, boolean noSleep) {
            if (heartDefeated()) {
                markEverDefeated();
            }
            Trial last = Trial.byId(record.getString(LAST_SLEEPING));
            record.resetProgress();
            seed(random);
            Trial next = null;
            if (forceSleep != null) {
                next = forceSleep;
            } else if (everDefeated() && !noSleep) {
                java.util.List<Trial> pool = new java.util.ArrayList<>(java.util.List.of(Trial.values()));
                pool.remove(last);
                next = pool.get(random.nextInt(pool.size()));
            }
            if (next != null) {
                record.putString(SLEEPING, next.id());
                record.putString(LAST_SLEEPING, next.id());
                ring(next);
            }
            record.putInt(RESETS, resets() + 1);
        }

        // 眠る試練をコマンドで決める (今の周に効かせる)
        public void setSleeping(Trial t) {
            Trial was = sleeping();
            if (was != null && was != t) {
                record.putInt(RUNG, record.getInt(RUNG) & ~(1 << was.ordinal()));
            }
            if (t == null) {
                record.remove(SLEEPING);
            } else {
                record.putString(SLEEPING, t.id());
                record.putString(LAST_SLEEPING, t.id());
                ring(t);
            }
        }
    }
}
