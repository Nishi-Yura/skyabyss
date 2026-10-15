package jp.revati.skyabyss.lore;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// プレイヤーごとの伝承の書の記録 (Data Attachment)。プレイヤーのデータに保存され、死んでも引き継ぐ。
// 月のループでも消さない (巻き戻るのは物語だけで、留まる者の記憶は残る)。
//   unlocked  : 記された項目 (記された順)
//   seen      : 書で開いて読んだ項目 (新しい項目の印を消すため)
//   bookGiven : 伝承の書を一度でも受け取ったか
//   bookDropAt: 空の影を初めて見たあと、書が空から落ちてくる時刻 (ゲーム時間)。予定がなければ -1
public final class LoreState {
    public static final Codec<LoreState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(s -> List.copyOf(s.unlocked)),
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(s -> List.copyOf(s.seen)),
            Codec.BOOL.optionalFieldOf("book_given", false).forGetter(s -> s.bookGiven),
            Codec.LONG.optionalFieldOf("book_drop_at", -1L).forGetter(s -> s.bookDropAt)).apply(i, LoreState::new));

    final Set<String> unlocked = new LinkedHashSet<>();
    final Set<String> seen = new LinkedHashSet<>();
    boolean bookGiven;
    long bookDropAt = -1;

    public LoreState() {
    }

    private LoreState(List<String> unlocked, List<String> seen, boolean bookGiven, long bookDropAt) {
        this.unlocked.addAll(unlocked);
        this.seen.addAll(seen);
        this.bookGiven = bookGiven;
        this.bookDropAt = bookDropAt;
    }

    public boolean has(String id) {
        return unlocked.contains(id);
    }

    public Set<String> unlocked() {
        return java.util.Collections.unmodifiableSet(unlocked);
    }

    public Set<String> seen() {
        return java.util.Collections.unmodifiableSet(seen);
    }

    public boolean bookGiven() {
        return bookGiven;
    }

    public long bookDropAt() {
        return bookDropAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LoreState s && s.unlocked.equals(unlocked) && List.copyOf(s.unlocked).equals(List.copyOf(unlocked))
                && s.seen.equals(seen) && s.bookGiven == bookGiven && s.bookDropAt == bookDropAt;
    }

    @Override
    public int hashCode() {
        return unlocked.hashCode() * 31 + seen.hashCode();
    }

    @Override
    public String toString() {
        return "LoreState" + unlocked + " seen=" + seen + " book=" + bookGiven + " drop=" + bookDropAt;
    }
}
