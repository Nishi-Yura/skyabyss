package jp.revati.skyabyss.story.arc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import jp.revati.skyabyss.story.Chapters;
import net.minecraft.world.entity.player.Player;

// 章の演出の人ごとの記録 (Data Attachment skyabyss:chapter_arc。プレイヤーのデータに保存し、死んでも引き継ぐ)。
//   titleShown     : 題字の札を出した章 (章の番号を 1 ビットずつ)
//   aftermathShown : 余韻の札を出した章 (同上)
//   beats          : 起きた予兆の ID (Beat.id。"<章の ID>/<名前>")
//   lastBeatAt     : 最後に予兆が起きたゲーム時間 (なければ -1)
public final class ArcData {
    public static final Codec<ArcData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("title_shown", 0).forGetter(d -> d.titleShown),
            Codec.INT.optionalFieldOf("aftermath_shown", 0).forGetter(d -> d.aftermathShown),
            Codec.STRING.listOf().optionalFieldOf("beats", List.of()).forGetter(d -> List.copyOf(d.beats)),
            Codec.LONG.optionalFieldOf("last_beat_at", -1L).forGetter(d -> d.lastBeatAt)).apply(i, ArcData::new));

    private int titleShown;
    private int aftermathShown;
    private final Set<String> beats = new LinkedHashSet<>();
    private long lastBeatAt = -1;

    public ArcData() {
    }

    private ArcData(int titleShown, int aftermathShown, List<String> beats, long lastBeatAt) {
        this.titleShown = titleShown;
        this.aftermathShown = aftermathShown;
        this.beats.addAll(beats);
        this.lastBeatAt = lastBeatAt;
    }

    public static ArcData of(Player player) {
        return player.getData(ArcSetup.STATE);
    }

    private static int bit(Chapters chapter) {
        return 1 << chapter.number();
    }

    public boolean titleShown(Chapters chapter) {
        return (titleShown & bit(chapter)) != 0;
    }

    public void markTitle(Chapters chapter) {
        titleShown |= bit(chapter);
    }

    // この章まで (この章を含む) の題字を「出した」ことにする (出さずに済ませる: すでに先の章にいた人、序章、旧セーブ)
    public void markTitlesUpTo(Chapters chapter) {
        for (Chapters c : Chapters.values()) {
            if (c.number() <= chapter.number()) {
                markTitle(c);
            }
        }
    }

    public boolean aftermathShown(Chapters chapter) {
        return (aftermathShown & bit(chapter)) != 0;
    }

    public void markAftermath(Chapters chapter) {
        aftermathShown |= bit(chapter);
    }

    public boolean beatDone(String id) {
        return beats.contains(id);
    }

    // 起きたと記す。初めてなら true (二度目以降は何も変えない)
    public boolean markBeat(String id, long gameTime) {
        if (!beats.add(id)) {
            return false;
        }
        lastBeatAt = gameTime;
        return true;
    }

    public long lastBeatAt() {
        return lastBeatAt;
    }

    public Set<String> beats() {
        return Set.copyOf(beats);
    }

    // 章 (ID) の予兆がいくつ起きたか
    public int beatCount(Chapters chapter) {
        String prefix = chapter.id() + "/";
        int n = 0;
        for (String b : beats) {
            if (b.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }

    public void reset() {
        titleShown = 0;
        aftermathShown = 0;
        beats.clear();
        lastBeatAt = -1;
    }

    // 予兆の記録だけ消す (開発用・自己テスト用)
    public void resetBeats() {
        beats.clear();
        lastBeatAt = -1;
    }
}
