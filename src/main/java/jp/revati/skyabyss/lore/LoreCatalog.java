package jp.revati.skyabyss.lore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;

// 読み込んだ章と項目の一覧 (変更しない)。サーバーは JSON から作り、参加者には丸ごと送る
public final class LoreCatalog {
    public static final LoreCatalog EMPTY = new LoreCatalog(List.of(), List.of());

    private final List<LoreChapter> chapters;
    private final List<LoreEntry> entries;
    private final Map<String, LoreEntry> byId = new LinkedHashMap<>();

    public LoreCatalog(List<LoreChapter> chapters, List<LoreEntry> entries) {
        List<LoreChapter> c = new ArrayList<>(chapters);
        c.sort(Comparator.comparingInt(LoreChapter::order).thenComparing(LoreChapter::id));
        this.chapters = List.copyOf(c);
        Map<String, Integer> chapterOrder = new LinkedHashMap<>();
        for (LoreChapter chapter : this.chapters) {
            chapterOrder.put(chapter.id(), chapter.order());
        }
        List<LoreEntry> e = new ArrayList<>(entries);
        e.sort(Comparator.<LoreEntry>comparingInt(x -> chapterOrder.getOrDefault(x.chapter(), Integer.MAX_VALUE))
                .thenComparingInt(LoreEntry::order).thenComparing(LoreEntry::id));
        this.entries = List.copyOf(e);
        for (LoreEntry entry : this.entries) {
            byId.put(entry.id(), entry);
        }
    }

    public List<LoreChapter> chapters() {
        return chapters;
    }

    public List<LoreEntry> entries() {
        return entries;
    }

    @Nullable
    public LoreEntry entry(String id) {
        return byId.get(id);
    }

    public List<LoreEntry> inChapter(String chapter) {
        List<LoreEntry> list = new ArrayList<>();
        for (LoreEntry entry : entries) {
            if (entry.chapter().equals(chapter)) {
                list.add(entry);
            }
        }
        return list;
    }

    // 読み込んだ内容の食い違い (章がない、関連先がない、条件がない など)。自己テストと読み込みのログで使う
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (LoreEntry entry : entries) {
            if (chapters.stream().noneMatch(c -> c.id().equals(entry.chapter()))) {
                problems.add(entry.id() + ": unknown chapter " + entry.chapter());
            }
            if (!entry.placeholder() && entry.unlock().isEmpty()) {
                problems.add(entry.id() + ": no unlock condition");
            }
            if (entry.placeholder() && !entry.unlock().isEmpty()) {
                problems.add(entry.id() + ": a placeholder must not have unlock conditions");
            }
            for (String related : entry.related()) {
                if (!byId.containsKey(related)) {
                    problems.add(entry.id() + ": related entry " + related + " does not exist");
                } else if (related.equals(entry.id())) {
                    problems.add(entry.id() + ": related to itself");
                }
            }
            for (LoreTrigger t : entry.unlock()) {
                boolean needsTarget = !t.type().equals("sky_shadow") && !t.type().equals("manual");
                if (needsTarget && t.target().isEmpty()) {
                    problems.add(entry.id() + ": trigger " + t.type() + " needs a target");
                }
            }
        }
        return problems;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(chapters.size());
        chapters.forEach(c -> c.write(buf));
        buf.writeVarInt(entries.size());
        entries.forEach(e -> e.write(buf));
    }

    public static LoreCatalog read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<LoreChapter> chapters = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            chapters.add(LoreChapter.read(buf));
        }
        int m = buf.readVarInt();
        List<LoreEntry> entries = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            entries.add(LoreEntry.read(buf));
        }
        return new LoreCatalog(chapters, entries);
    }
}
