package jp.revati.skyabyss.titan.storm.back;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.back.StormBackState.Trial;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

// 雷翼の背の、グループ (Groups: パーティ、または一人) ごとの試練の進み (docs/design/12 の 7 章・10 の 9 章)。
// 中身は storm_back の BackGroupData の記録 (Record) の data に書く。このクラスはその読み書きの窓で、自分では何も持たない。
//  - 鳴った風鳴り石 (ビット)、眠っている試練
//  - 心臓を倒したか・試練の番号 (休み場の記録の番号) は Record の共通の欄 (isHeartDefeated・epoch)
//  - リセットをまたいで残すもの ("keep." で始まる欄): 心臓を一度でも倒したか (2 周目から一つ眠る)、前の周に眠った試練
// 試練を最初に戻す (reset) と、記録の番号が一つ進み、上のものは消える (扉の上書きも消える。呼ぶ側が syncGates で戻す)
public final class StormProgress {
    static final String RUNG = "storm.rung";
    static final String SLEEPING = "storm.sleeping";
    static final String EVER_DEFEATED = "keep.storm.ever_defeated";
    static final String LAST_SLEEPING = "keep.storm.last_sleeping";

    public final UUID group;
    public final BackGroupData.Record record;

    private StormProgress(UUID group, BackGroupData.Record record) {
        this.group = group;
        this.record = record;
    }

    public static StormProgress of(ServerLevel level, UUID group) {
        return new StormProgress(group, BackGroupData.get(level).of(group));
    }

    public static StormProgress of(ServerLevel level, ServerPlayer player) {
        return of(level, Groups.keyOf(player));
    }

    // 記録が無ければ null (作らない。見かけの計算など、読むだけのとき)
    @Nullable
    public static StormProgress peek(ServerLevel level, UUID group) {
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        return r == null ? null : new StormProgress(group, r);
    }

    public int epoch() {
        return record.epoch();
    }

    // ---------------------------------------------------------------- 風鳴り石と眠る試練
    public boolean rung(Trial t) {
        return (record.getInt(RUNG) & (1 << t.ordinal())) != 0;
    }

    public void setRung(Trial t, boolean v) {
        int bits = record.getInt(RUNG);
        record.putInt(RUNG, v ? bits | (1 << t.ordinal()) : bits & ~(1 << t.ordinal()));
    }

    public int rungCount() {
        return Integer.bitCount(record.getInt(RUNG) & 7);
    }

    @Nullable
    public Trial sleeping() {
        return Trial.byId(record.getString(SLEEPING));
    }

    @Nullable
    public Trial lastSleeping() {
        return Trial.byId(record.getString(LAST_SLEEPING));
    }

    // 眠る試練をコマンドで決める (今の周に効かせる)
    public void setSleeping(@Nullable Trial t) {
        Trial was = sleeping();
        if (was != null && was != t) {
            setRung(was, false);
        }
        if (t == null) {
            record.remove(SLEEPING);
            return;
        }
        record.putString(SLEEPING, t.id());
        record.putString(LAST_SLEEPING, t.id());
        setRung(t, true);
    }

    // このグループが心臓を一度でも倒したか (2 周目から試練が一つ眠る)
    public boolean everDefeated() {
        return record.flag(EVER_DEFEATED) || record.isHeartDefeated();
    }

    public void markEverDefeated() {
        record.setFlag(EVER_DEFEATED, true);
    }

    public boolean heartDefeated() {
        return record.isHeartDefeated();
    }

    // ---------------------------------------------------------------- リセット
    // 戻す意味のある進みがあるか (眠っている試練の石と、リセットをまたいで残す欄だけなら無い)
    public boolean needsReset() {
        Trial s = sleeping();
        return rungCount() > (s != null ? 1 : 0) || record.isHeartDefeated();
    }

    // 試練を最初に戻す (12 の 7.1節)。番号が進み (古い休み場の記録は無効)、心臓も眠り直す。
    // 試練はどれも、いつでも入れて終えられる (25 の 2章): 周回で試練が眠って飛ばされることはない。
    // forceSleep は開発用のコマンドだけ (試練を眠らせた状態の確かめ)。noSleep は残してあるが、自動で眠らせないので効果はない
    public void reset(RandomSource random, @Nullable Trial forceSleep, boolean noSleep) {
        boolean ever = everDefeated();
        record.resetProgress();
        if (ever) {
            markEverDefeated();
        }
        if (forceSleep != null) {
            setSleeping(forceSleep);
        }
    }
}
