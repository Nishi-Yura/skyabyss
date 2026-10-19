package jp.revati.skyabyss.titan.ember.back;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

// 余燼の背の試練の状態 (13 の 7 章)。グループ (パーティ、または一人) ごとに持つ (docs/design/10 の 9 章)。
// 中身はグループの記録 (BackGroupData.Record) の欄に書く。このクラスはその読み書きの窓 (保存はしない)。
//   ember.rung.<試練>・ember.ready.<試練>  石が鳴った・鳴らせる
//   ember.sleeping                         この周に眠る試練
//   ember.gateOpen                         熾の底の門が開いた
//   ember.forgePattern                     鍛えの間の打つ順 (番号 + 1。部屋を最初に使ったときに決める)
//   keep.ember.*                           リセットを越えて残る: 心臓を一度でも倒したか・前に眠った試練・初めての打つ順を使ったか・周回の数・撃破の数
// 試練の番号 (休み場の記録を無効にする番号) と心臓の撃破は、記録そのもの (epoch・isHeartDefeated) を使う。
// リセットの種 (島の並び・打つ順) は、グループの鍵と試練の番号から決まる (グループごとに違い、リセットで変わる)。
// グループのない窓 (none) は、どの試練も鳴っていない・眠っていない「最初の形」で、書いても何も残らない。
public final class EmberBackState {
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

    private static final String RUNG = "ember.rung.";
    private static final String READY = "ember.ready.";
    private static final String SLEEPING = "ember.sleeping";
    private static final String GATE_OPEN = "ember.gateOpen";
    private static final String FORGE_PATTERN = "ember.forgePattern";
    private static final String EVER_DEFEATED = "keep.ember.everDefeated";
    private static final String LAST_SLEEPING = "keep.ember.lastSleeping";
    private static final String FIRST_FORGE = "keep.ember.firstForgeUsed";
    private static final String RESETS = "keep.ember.resets";
    private static final String HEART_DEFEATS = "keep.ember.heartDefeats";

    @Nullable
    private final UUID group;
    @Nullable
    private final BackGroupData.Record record;

    private EmberBackState(@Nullable UUID group, @Nullable BackGroupData.Record record) {
        this.group = group;
        this.record = record;
    }

    // グループの状態 (記録がなければ作る)
    public static EmberBackState of(ServerLevel level, UUID group) {
        return new EmberBackState(group, BackGroupData.get(level).of(group));
    }

    public static EmberBackState of(ServerLevel level, ServerPlayer player) {
        return of(level, Groups.keyOf(player));
    }

    // 記録を作らずに読む (見かけの計算など)。記録がなければ「最初の形」
    public static EmberBackState peek(ServerLevel level, UUID group) {
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        return r == null ? none() : new EmberBackState(group, r);
    }

    // グループが null なら「最初の形」
    public static EmberBackState ofNullable(ServerLevel level, @Nullable UUID group) {
        return group == null ? none() : of(level, group);
    }

    // 試練の部屋の持ち主 (RoomLocks) の状態。持ち主がいなければ「最初の形」
    public static EmberBackState forRoom(ServerLevel level, String room) {
        return ofNullable(level, EmberRooms.owner(room));
    }

    public static EmberBackState none() {
        return new EmberBackState(null, null);
    }

    @Nullable
    public UUID group() {
        return group;
    }

    @Nullable
    public BackGroupData.Record record() {
        return record;
    }

    // ---------------------------------------------------------------- 読む
    public int epoch() {
        return record == null ? 1 : record.epoch();
    }

    public boolean rung(Trial t) {
        return record != null && (record.flag(RUNG + t.id()) || sleeping() == t);
    }

    public int rungCount() {
        int n = 0;
        for (Trial t : Trial.values()) {
            n += rung(t) ? 1 : 0;
        }
        return n;
    }

    public boolean ready(Trial t) {
        return record != null && (rung(t) || record.flag(READY + t.id()));
    }

    @Nullable
    public Trial sleeping() {
        return record == null ? null : Trial.byId(record.getString(SLEEPING));
    }

    public boolean sleeping(Trial t) {
        return sleeping() == t;
    }

    // 部屋の名前で (smith は east と一緒に眠る)
    public boolean roomSleeping(String room) {
        Trial s = sleeping();
        if (s == null) {
            return false;
        }
        return room.equals(s.id()) || room.equals("smith") && s == Trial.EAST;
    }

    public boolean everDefeated() {
        return record != null && record.flag(EVER_DEFEATED);
    }

    public boolean gateOpen() {
        return record != null && record.flag(GATE_OPEN);
    }

    public boolean heartDefeated() {
        return record != null && record.isHeartDefeated();
    }

    public int heartDefeats() {
        return record == null ? 0 : record.getInt(HEART_DEFEATS);
    }

    // リセットの種: グループの鍵と試練の番号から (グループごとに違い、リセットで変わる)
    public int seed() {
        if (group == null) {
            return 0;
        }
        long h = group.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ group.getLeastSignificantBits() * 0xC2B2AE3D27D4EB4FL;
        h ^= epoch() * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (int) h;
    }

    public boolean firstForgeUsed() {
        return record != null && record.flag(FIRST_FORGE);
    }

    public int resets() {
        return record == null ? 0 : record.getInt(RESETS);
    }

    // 何か試練が進んでいるか (眠っている試練の石は数えない)。放置のリセットはこれが真のときだけ
    public boolean hasProgress() {
        if (record == null) {
            return false;
        }
        if (gateOpen() || heartDefeated()) {
            return true;
        }
        for (Trial t : Trial.values()) {
            if (!sleeping(t) && (record.flag(RUNG + t.id()) || record.flag(READY + t.id()))) {
                return true;
            }
        }
        return false;
    }

    // 鍛えの間の打つ順の番号。この周で初めて聞かれたときに決めて覚える (打つ途中で「初めての挑戦」が済んでも、順は変わらない)
    public int forgePattern(int patterns) {
        if (record == null) {
            return 0;
        }
        int stored = record.getInt(FORGE_PATTERN);
        if (stored > 0) {
            return stored - 1;
        }
        int idx = firstForgeUsed() ? Math.floorMod(seed(), patterns) : 0;
        record.putInt(FORGE_PATTERN, idx + 1);
        return idx;
    }

    // ---------------------------------------------------------------- 書く (EmberBackTrials・試練の担当から)
    public void ring(Trial t) {
        if (record != null) {
            record.setFlag(RUNG + t.id(), true);
            record.setFlag(READY + t.id(), true);
        }
    }

    void markReady(Trial t) {
        if (record != null) {
            record.setFlag(READY + t.id(), true);
        }
    }

    public void markGateOpen() {
        if (record != null) {
            record.setFlag(GATE_OPEN, true);
        }
    }

    public void markEverDefeated() {
        if (record != null) {
            record.setFlag(EVER_DEFEATED, true);
        }
    }

    // 心臓を倒した (この周の撃破と、数)
    public void markHeartDefeated() {
        if (record != null) {
            record.markHeartDefeated();
            record.putInt(HEART_DEFEATS, record.getInt(HEART_DEFEATS) + 1);
            record.setFlag(EVER_DEFEATED, true);
        }
    }

    // 自己テストが、試したあとに元へ戻す
    public void setEverDefeated(boolean value) {
        if (record != null) {
            record.setFlag(EVER_DEFEATED, value);
        }
    }

    // 鍛えの間で初めての打つ順を使った
    public void markFirstForgeUsed() {
        if (record != null) {
            record.setFlag(FIRST_FORGE, true);
        }
    }

    // 記録を最初に戻した (BackGroupData.Record.resetProgress) あとに呼ぶ。2 周目からは試練を一つ眠らせる (同じ試練は二回続けて眠らない)
    void afterReset(RandomSource random, @Nullable Trial forceSleep, boolean noSleep) {
        if (record == null) {
            return;
        }
        Trial next = null;
        if (forceSleep != null) {
            next = forceSleep;
        } else if (everDefeated() && !noSleep) {
            List<Trial> pool = new ArrayList<>(List.of(Trial.values()));
            pool.remove(Trial.byId(record.getString(LAST_SLEEPING)));
            next = pool.get(random.nextInt(pool.size()));
        }
        setSleepingRaw(next);
        record.putInt(RESETS, record.getInt(RESETS) + 1);
    }

    // 眠る試練をコマンドで決める (今の周に効かせる。null で眠らせない)
    void setSleeping(@Nullable Trial t) {
        if (record == null) {
            return;
        }
        Trial was = sleeping();
        if (was != null && was != t) {
            record.setFlag(RUNG + was.id(), false);
            record.setFlag(READY + was.id(), false);
        }
        setSleepingRaw(t);
    }

    private void setSleepingRaw(@Nullable Trial t) {
        if (t == null) {
            if (record.has(SLEEPING)) {
                record.remove(SLEEPING);
            }
        } else {
            record.putString(SLEEPING, t.id());
            record.putString(LAST_SLEEPING, t.id());
        }
    }
}
