package jp.revati.skyabyss.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

// プレイヤーごとのクエストの記録 (Data Attachment skyabyss:quest_log)。死んでも引き継ぎ、持ち主にだけ同期する。
//   quests  : クエストの ID → 進み具合 (始めた順)
//   tracked : 画面の左に小さく出す (追跡する) クエスト。空なら出さない
public final class QuestLog {
    // 一つのクエストの進み具合。stage は今の段階の番号 (終えたら段階の数)、progress は今の段階の目的ごとの数
    public static final class Entry {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("stage", 0).forGetter(e -> e.stage),
                Codec.INT.listOf().optionalFieldOf("progress", List.of()).forGetter(e -> e.progressList()),
                Codec.BOOL.optionalFieldOf("done", false).forGetter(e -> e.done),
                Codec.LONG.optionalFieldOf("started", 0L).forGetter(e -> e.started),
                Codec.LONG.optionalFieldOf("finished", 0L).forGetter(e -> e.finished),
                Codec.LONG.optionalFieldOf("cycle", -1L).forGetter(e -> e.cycle),
                Codec.LONG.optionalFieldOf("origin", Long.MIN_VALUE).forGetter(e -> e.origin),
                Codec.STRING.optionalFieldOf("origin_dim", "").forGetter(e -> e.originDim)).apply(i, Entry::new));
        public static final long NO_ORIGIN = Long.MIN_VALUE;

        public int stage;
        public int[] progress;
        public boolean done;
        public long started;
        public long finished;
        // 繰り返すクエストの周期 (受けた日・週。Repeats)。一度きりは -1
        public long cycle = -1;
        // 受けた場所 (言伝ての板か、代わりに見せた NPC の足もと。BlockPos.asLong)。羊を連れ戻す目的 (herd) が使う
        public long origin = NO_ORIGIN;
        public String originDim = "";

        public Entry(int objectives, long started) {
            this.progress = new int[objectives];
            this.started = started;
        }

        private Entry(int stage, List<Integer> progress, boolean done, long started, long finished, long cycle, long origin, String originDim) {
            this.stage = stage;
            this.progress = progress.stream().mapToInt(Integer::intValue).toArray();
            this.done = done;
            this.started = started;
            this.finished = finished;
            this.cycle = cycle;
            this.origin = origin;
            this.originDim = originDim;
        }

        List<Integer> progressList() {
            List<Integer> list = new ArrayList<>(progress.length);
            for (int p : progress) {
                list.add(p);
            }
            return list;
        }

        public int progress(int objective) {
            return objective < progress.length ? progress[objective] : 0;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Entry e && e.stage == stage && java.util.Arrays.equals(e.progress, progress) && e.done == done
                    && e.started == started && e.finished == finished && e.cycle == cycle && e.origin == origin && e.originDim.equals(originDim);
        }

        @Override
        public int hashCode() {
            return (stage * 31 + java.util.Arrays.hashCode(progress)) * 31 + (done ? 1 : 0) + (int) cycle * 7;
        }

        @Override
        public String toString() {
            return (done ? "done" : "stage " + stage) + java.util.Arrays.toString(progress);
        }
    }

    public static final Codec<QuestLog> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Entry.CODEC).optionalFieldOf("quests", Map.of()).forGetter(l -> l.quests),
            Codec.STRING.optionalFieldOf("tracked", "").forGetter(l -> l.tracked),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("claimed", Map.of()).forGetter(l -> l.claimed),
            Codec.STRING.listOf().optionalFieldOf("heard", List.of()).forGetter(l -> List.copyOf(l.heard))).apply(i, QuestLog::new));
    // 同期は NBT を通す (中身が小さいので、手書きの符号化は要らない)
    public static final StreamCodec<ByteBuf, QuestLog> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    final Map<String, Entry> quests = new LinkedHashMap<>();
    String tracked = "";
    // 繰り返すクエストの、報酬を渡した最後の周期 (同じ日・週に二度は渡さない。記録を消して受け直しても)
    final Map<String, Long> claimed = new java.util.HashMap<>();
    // 聞いた話題 ("クエスト|役/節"。そのクエストを始めたあとに聞いたものだけ)。「〜に尋ねよう」の目的は、順番や途中で閉じたかに依らず、聞いた時点で済む (Quests.onTalk が足し、measure が数える)
    final java.util.Set<String> heard = new java.util.LinkedHashSet<>();

    public QuestLog() {
    }

    private QuestLog(Map<String, Entry> quests, String tracked, Map<String, Long> claimed, List<String> heard) {
        this.claimed.putAll(claimed);
        this.heard.addAll(heard);
        // 保存した順を保つため、始めた時刻で並べ直す (unboundedMap は順を保たない)
        quests.entrySet().stream().sorted(java.util.Comparator.comparingLong(e -> e.getValue().started))
                .forEach(e -> this.quests.put(e.getKey(), e.getValue()));
        this.tracked = tracked;
    }

    public static QuestLog of(Player player) {
        return player.getData(QuestSetup.LOG);
    }

    @Nullable
    public Entry entry(String quest) {
        return quests.get(quest);
    }

    public Map<String, Entry> quests() {
        return java.util.Collections.unmodifiableMap(quests);
    }

    public String tracked() {
        return tracked;
    }

    public boolean heard(String quest, String role, String node) {
        return heard.contains(quest + "|" + role + "/" + node);
    }

    public long claimed(String quest) {
        return claimed.getOrDefault(quest, Long.MIN_VALUE);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof QuestLog l && l.quests.equals(quests) && l.tracked.equals(tracked) && l.claimed.equals(claimed) && l.heard.equals(heard);
    }

    @Override
    public int hashCode() {
        return quests.hashCode();
    }

    @Override
    public String toString() {
        return "QuestLog" + quests + " tracked=" + tracked;
    }
}
