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
//  - 鳴った風鳴り石 (ビット)、眠っている試練、東の骨の戸 (ビット)、雷獣を倒したか、竜骨の門が開いたか
//  - 眼の間: 配置の番号、鏡の向き (無ければ配置の最初の向き)、光った受け羽 (ビット)
//  - 心臓を倒したか・試練の番号 (休み場の記録の番号) は Record の共通の欄 (isHeartDefeated・epoch)
//  - リセットをまたいで残すもの ("keep." で始まる欄): 心臓を一度でも倒したか (2 周目から一つ眠る)、前の周に眠った試練
// 試練を最初に戻す (reset) と、記録の番号が一つ進み、上のものは消える (扉の上書きも消える。呼ぶ側が syncGates で戻す)
public final class StormProgress {
    static final String RUNG = "storm.rung";
    static final String SLEEPING = "storm.sleeping";
    static final String DOORS = "storm.doors";
    static final String RAIJU = "storm.raiju";
    static final String KEEL = "storm.keel";
    static final String MIRROR_CONFIG = "storm.mirror_config";
    static final String MIRROR_ROTS = "storm.mirror_rots";
    static final String RECEIVERS = "storm.receivers";
    // 周回の長い眼の間 (32 の 4 章、R5): 解いた配置の数 (0 = まだ。長い周は 2 つ解くと嘴の戸が開く)
    static final String MIRROR_STAGE = "storm.mirror_stage";
    static final String RIB = "storm.rib";
    static final String RIB_RINGS = "storm.rib_rings";
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

    // 三つとも鳴ったか (眠っている試練の石は鳴っている)
    public boolean allRung() {
        return rungCount() >= 3;
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

    // ---------------------------------------------------------------- 西の肋の間 (25 の 5章 A)
    // 着いた層の岩棚の最高 (0 = 床だけ。RIB_LAYERS = 頂)
    public int ribLayer() {
        return record.getInt(RIB);
    }

    public void setRibLayer(int layer) {
        record.putInt(RIB, Math.max(ribLayer(), layer));
    }

    // くぐった滑空の輪 (ビット)
    public boolean ribRingLit(int k) {
        return (record.getInt(RIB_RINGS) & (1 << k)) != 0;
    }

    public void setRibRingLit(int k) {
        record.putInt(RIB_RINGS, record.getInt(RIB_RINGS) | (1 << k));
    }

    public int ribRingCount() {
        return Integer.bitCount(record.getInt(RIB_RINGS));
    }

    // ---------------------------------------------------------------- 東の試練・雷獣・竜骨の門
    public boolean eastDoorOpen(int i) {
        return (record.getInt(DOORS) & (1 << i)) != 0;
    }

    public void openEastDoor(int i) {
        record.putInt(DOORS, record.getInt(DOORS) | (1 << i));
    }

    public int openDoorCount() {
        return Integer.bitCount(record.getInt(DOORS));
    }

    // 東の戸がこのグループにとって開いているか (開けた戸、または東が眠っている周)
    public boolean eastDoorPassable(int i) {
        return eastDoorOpen(i) || sleeping() == Trial.EAST;
    }

    public boolean raijuDefeated() {
        return record.flag(RAIJU);
    }

    public void setRaijuDefeated(boolean v) {
        record.setFlag(RAIJU, v);
    }

    // 雷獣の巣の真ん中の昇り風が立っているか
    public boolean raijuVent() {
        return raijuDefeated() || sleeping() == Trial.EAST || rung(Trial.EAST);
    }

    public boolean keelOpen() {
        return record.flag(KEEL);
    }

    public void setKeelOpen(boolean v) {
        record.setFlag(KEEL, v);
    }

    // ---------------------------------------------------------------- 眼の間
    public int mirrorConfig() {
        return Math.floorMod(record.getInt(MIRROR_CONFIG), EyeMirrors.CONFIGS.size());
    }

    public void setMirrorConfig(int c) {
        record.putInt(MIRROR_CONFIG, Math.floorMod(c, EyeMirrors.CONFIGS.size()));
        resetMirrorRots();
    }

    public EyeMirrors.Config config() {
        return EyeMirrors.CONFIGS.get(mirrorConfig());
    }

    // 鏡の向き (配置の鏡の順)。回していなければ配置の最初の向き
    public int[] mirrorRots() {
        EyeMirrors.Config cfg = config();
        int[] saved = record.data().getIntArray(MIRROR_ROTS);
        if (saved.length != cfg.mirrors().size()) {
            return cfg.init().clone();
        }
        return saved.clone();
    }

    public void setMirrorRot(int i, int rot) {
        int[] rots = mirrorRots();
        rots[i] = rot & 3;
        record.data().putIntArray(MIRROR_ROTS, rots);
        record.changed();
    }

    public void resetMirrorRots() {
        if (record.has(MIRROR_ROTS)) {
            record.remove(MIRROR_ROTS);
        }
    }

    public int receivers() {
        return record.getInt(RECEIVERS);
    }

    public int mirrorStage() {
        return record.getInt(MIRROR_STAGE);
    }

    public void setMirrorStage(int v) {
        record.putInt(MIRROR_STAGE, v);
    }

    // 嘴の戸を開けるまでに解く配置の数 (周回のグループは 2)
    public int mirrorStagesNeeded() {
        return jp.revati.skyabyss.trial.RouteLength.extended(record) ? EyeMirrors.STAGES_LONG : 1;
    }

    public void setReceivers(int bits) {
        record.putInt(RECEIVERS, bits);
    }

    // 嘴の小部屋の戸がこのグループにとって開いているか
    public boolean beakOpen() {
        return receivers() == 7 || rung(Trial.FRONT);
    }

    // ---------------------------------------------------------------- リセット
    // 戻す意味のある進みがあるか (眠っている試練の石と、リセットをまたいで残す欄だけなら無い)
    public boolean needsReset() {
        Trial s = sleeping();
        return rungCount() > (s != null ? 1 : 0) || record.isHeartDefeated() || receivers() != 0 || raijuDefeated() || record.getInt(DOORS) != 0
                || keelOpen() || record.has(MIRROR_ROTS) || ribLayer() > 0 || record.getInt(RIB_RINGS) != 0;
    }

    // 試練を最初に戻す (12 の 7.1節)。番号が進み (古い休み場の記録は無効)、心臓も眠り直す。
    // 眼の間は新しい配置を引く (このグループが初めて挑むときだけ、いちばんやさしい 0)。
    // 試練はどれも、いつでも入れて終えられる (25 の 2章): 周回で試練が眠って飛ばされることはない。
    // forceSleep は開発用のコマンドだけ (試練を眠らせた状態の確かめ)。noSleep は残してあるが、自動で眠らせないので効果はない
    public void reset(RandomSource random, @Nullable Trial forceSleep, boolean noSleep) {
        boolean ever = everDefeated();
        record.resetProgress();
        if (ever) {
            markEverDefeated();
        }
        record.putInt(MIRROR_CONFIG, 1 + random.nextInt(EyeMirrors.CONFIGS.size() - 1));
        if (forceSleep != null) {
            setSleeping(forceSleep);
        }
    }
}
