package jp.revati.skyabyss.opening;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// プレイヤーごとの序盤の拍子の状態 (Data Attachment skyabyss:opening)。プレイヤーのデータに保存され、死んでも引き継ぐ (17 の 6 章 A)。
// マルチでは人ごとに持つので、あとから入った人にも B0〜B4 が起きる。時刻はどれもゲーム時間 (getGameTime)。ない値は -1。
//   done        : 済んだ拍子
//   awakeAt     : 目覚め (B0) の時刻。-1 ならまだ初参加の判定をしていない
//   awakeDay    : 目覚めた日 (一日の長さで割った日数。時刻の操作に左右されない dayTime から数える)
//   legacy      : すでに遊んでいた人 (拍子は全部済んだものとして扱い、何も起こさない)
//   campLoreAt  : 野営地の頁 (prologue/camp) を記す時刻
//   bellAt      : 最後に鐘の宿りの鐘を鳴らした時刻
//   stepsNight / stepsAt / stepsLoreAt / stepsDoneNight : 足音 (B6) の予定の夜・時刻、頁を記す時刻、起きた夜
//   songNight / songAt / songLoreAt / songDoneNight : 歌 (B9) の同じもの
//   pageAt / pageReadAt : 影のあとの破れた頁を落とす時刻、落としたあと読まれなければ自動で読む時刻
//   cueUntil    : 画面の演出 (足音・歌・目覚め) が続いている間 (同時に二つ起こさない)
public final class OpeningState {
    public static final Codec<OpeningState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(s -> s.doneIds()),
            Codec.LONG.optionalFieldOf("awake_at", -1L).forGetter(s -> s.awakeAt),
            Codec.LONG.optionalFieldOf("awake_day", -1L).forGetter(s -> s.awakeDay),
            Codec.BOOL.optionalFieldOf("legacy", false).forGetter(s -> s.legacy),
            Codec.LONG.optionalFieldOf("camp_lore_at", -1L).forGetter(s -> s.campLoreAt),
            Codec.LONG.optionalFieldOf("bell_at", -1L).forGetter(s -> s.bellAt),
            Codec.LONG.listOf().optionalFieldOf("steps", List.of()).forGetter(s -> List.of(s.stepsNight, s.stepsAt, s.stepsLoreAt, s.stepsDoneNight)),
            Codec.LONG.listOf().optionalFieldOf("song", List.of()).forGetter(s -> List.of(s.songNight, s.songAt, s.songLoreAt, s.songDoneNight)),
            Codec.LONG.listOf().optionalFieldOf("page", List.of()).forGetter(s -> List.of(s.pageAt, s.pageReadAt)),
            Codec.LONG.optionalFieldOf("cue_until", -1L).forGetter(s -> s.cueUntil)).apply(i, OpeningState::new));

    private final Set<OpeningBeat> done = EnumSet.noneOf(OpeningBeat.class);
    long awakeAt = -1;
    long awakeDay = -1;
    boolean legacy;
    long campLoreAt = -1;
    long bellAt = -1;
    long stepsNight = -1;
    long stepsAt = -1;
    long stepsLoreAt = -1;
    long stepsDoneNight = -1;
    long songNight = -1;
    long songAt = -1;
    long songLoreAt = -1;
    long songDoneNight = -1;
    long pageAt = -1;
    long pageReadAt = -1;
    long cueUntil = -1;
    // 画面の目的の帯に最後に送った目的 (保存しない。参加し直したら送り直す)
    transient String sentObjective = null;
    transient long sentAt = -1;
    // 最後に送った目印の位置 (BlockPos.asLong。なしは Long.MIN_VALUE)。動く目印 (鯨追いの焚き火) が決まったら送り直す
    transient long sentTarget = Long.MIN_VALUE;
    // 骨の丘の前触れをどこまで出したか (0 なし / 1 においの字幕 / 2 遠い白 / 3 足もとの欠片 / 4 風が止む。保存しない: 参加し直したら近さに応じて出し直す)
    transient int boneCue = 0;
    // コマンドで全部なかったことにした直後 (すでに遊んでいた人の判定をせず、B0 から)
    transient boolean fresh;

    public OpeningState() {
    }

    private OpeningState(List<String> done, long awakeAt, long awakeDay, boolean legacy, long campLoreAt, long bellAt, List<Long> steps,
            List<Long> song, List<Long> page, long cueUntil) {
        for (String id : done) {
            OpeningBeat beat = OpeningBeat.byId(id);
            if (beat != null) {
                this.done.add(beat);
            }
        }
        this.awakeAt = awakeAt;
        this.awakeDay = awakeDay;
        this.legacy = legacy;
        this.campLoreAt = campLoreAt;
        this.bellAt = bellAt;
        if (steps.size() == 4) {
            stepsNight = steps.get(0);
            stepsAt = steps.get(1);
            stepsLoreAt = steps.get(2);
            stepsDoneNight = steps.get(3);
        }
        if (song.size() == 4) {
            songNight = song.get(0);
            songAt = song.get(1);
            songLoreAt = song.get(2);
            songDoneNight = song.get(3);
        }
        if (page.size() == 2) {
            pageAt = page.get(0);
            pageReadAt = page.get(1);
        }
        this.cueUntil = cueUntil;
    }

    List<String> doneIds() {
        List<String> out = new ArrayList<>();
        for (OpeningBeat beat : OpeningBeat.values()) {
            if (done.contains(beat)) {
                out.add(beat.id);
            }
        }
        return out;
    }

    public boolean done(OpeningBeat beat) {
        return done.contains(beat);
    }

    public Set<OpeningBeat> doneBeats() {
        return java.util.Collections.unmodifiableSet(done);
    }

    void markDone(OpeningBeat beat) {
        done.add(beat);
    }

    void unmark(OpeningBeat beat) {
        done.remove(beat);
    }

    public boolean legacy() {
        return legacy;
    }

    public long awakeAt() {
        return awakeAt;
    }

    public long awakeDay() {
        return awakeDay;
    }

    // 済んでいる拍子のうち、一番先まで進んだもの (なければ null)
    public OpeningBeat latest() {
        OpeningBeat best = null;
        for (OpeningBeat beat : OpeningBeat.values()) {
            if (done.contains(beat)) {
                best = beat;
            }
        }
        return best;
    }

    // 全部なかったことにする (コマンド用。次の見回りで、また初参加として B0 から)
    void reset() {
        done.clear();
        awakeAt = -1;
        awakeDay = -1;
        legacy = false;
        campLoreAt = -1;
        bellAt = -1;
        stepsNight = stepsAt = stepsLoreAt = stepsDoneNight = -1;
        songNight = songAt = songLoreAt = songDoneNight = -1;
        pageAt = pageReadAt = -1;
        cueUntil = -1;
        sentObjective = null;
        fresh = true;
    }

    // この拍子と、それより後の拍子を「まだ」に戻す (コマンド用。予定も消す)
    void rewindTo(OpeningBeat beat) {
        for (OpeningBeat b : OpeningBeat.values()) {
            if (b.ordinal() >= beat.ordinal()) {
                done.remove(b);
            }
        }
        if (beat.ordinal() <= OpeningBeat.CAMP.ordinal()) {
            campLoreAt = -1;
        }
        if (beat.ordinal() <= OpeningBeat.TRAIL.ordinal()) {
            bellAt = -1;
        }
        if (beat.ordinal() <= OpeningBeat.SHADOW.ordinal()) {
            pageAt = pageReadAt = -1;
        }
        if (beat.ordinal() <= OpeningBeat.FOOTSTEPS.ordinal()) {
            stepsNight = stepsAt = stepsLoreAt = stepsDoneNight = -1;
        }
        if (beat.ordinal() <= OpeningBeat.SONG.ordinal()) {
            songNight = songAt = songLoreAt = songDoneNight = -1;
        }
        cueUntil = -1;
        legacy = false;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OpeningState s && s.done.equals(done) && s.awakeAt == awakeAt && s.awakeDay == awakeDay && s.legacy == legacy
                && s.campLoreAt == campLoreAt && s.bellAt == bellAt && s.stepsNight == stepsNight && s.stepsAt == stepsAt
                && s.stepsLoreAt == stepsLoreAt && s.stepsDoneNight == stepsDoneNight && s.songNight == songNight && s.songAt == songAt
                && s.songLoreAt == songLoreAt && s.songDoneNight == songDoneNight && s.pageAt == pageAt && s.pageReadAt == pageReadAt
                && s.cueUntil == cueUntil;
    }

    @Override
    public int hashCode() {
        return done.hashCode() * 31 + Long.hashCode(awakeAt);
    }

    @Override
    public String toString() {
        return "OpeningState" + doneIds() + " awakeAt=" + awakeAt + (legacy ? " legacy" : "");
    }
}
