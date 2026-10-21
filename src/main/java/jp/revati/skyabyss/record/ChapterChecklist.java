package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.SiteProgressData;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.guide.WayfarerNotes;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.record.RecordSnapshot.Row;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

// 控え帳の巨神の頁の「この章の進み」(A9、docs/design/07 の 6.8、11 の 3.x)。章 (一〜四章) ごとに、巨神の頁の中に出す:
//   備えの頼み  済み / 3 と、三本の名前に印 (済み ✔・受けた …・まだ ・)
//   寄り道      見つけた数と終えた数 / その章の場所の数 (場所の名前は出さない。輪郭だけ)
//   旅の覚書    受け取り済みか
// 見ていない巨神の頁 (RecordBuilder.titanPage の伏せ字) には出さない。物語の核心は書かない。読むだけで書き換えない
final class ChapterChecklist {
    private ChapterChecklist() {
    }

    // titan の章 (一〜四章)。巨神の章でなければ null
    @Nullable
    static Chapters chapterOf(String titan) {
        for (Chapters c : Chapters.values()) {
            if (titan.equals(c.titan())) {
                return c;
            }
        }
        return null;
    }

    // 頁に足す行 (見出しから)。章がなければ空
    static List<Row> rows(ServerPlayer p, String titan) {
        Chapters chapter = chapterOf(titan);
        List<Row> rows = new ArrayList<>();
        if (chapter == null) {
            return rows;
        }
        int n = chapter.number();
        rows.add(RecordBuilder.header("checklist", RecordBuilder.t("h.checklist")));
        if (PrepQuests.defined(n)) {
            int done = PrepQuests.done(p, n);
            List<Component> detail = new ArrayList<>();
            QuestLog log = QuestLog.of(p);
            for (String id : PrepQuests.ids(n)) {
                String kind = PrepQuests.kindOf(id);
                QuestDef def = QuestManager.quest(id);
                QuestLog.Entry e = log.entry(id);
                String mark = e != null && e.done ? "✔" : e != null ? "…" : "・";
                detail.add(Component.literal(mark + " ").append(def == null ? Component.literal(kind) : Component.translatable(def.titleKey())));
            }
            detail.add(RecordBuilder.t("d.check_prep"));
            rows.add(RecordBuilder.value("check_prep", RecordBuilder.t("r.check_prep"), RecordBuilder.t("v.of", done, PrepQuests.ids(n).size()),
                    done >= PrepQuests.ids(n).size() ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_NONE, detail));
        }
        int total = 0;
        int found = 0;
        int cleared = 0;
        SiteProgressData data = SiteProgressData.get(p.server);
        for (SiteSpawner.Def def : SiteSpawner.defs()) {
            if (def.chapter() != chapter) {
                continue;
            }
            total++;
            if (data.found(p.getUUID(), def.id())) {
                found++;
            }
            if (data.cleared(p.getUUID(), def.id())) {
                cleared++;
            }
        }
        if (total > 0) {
            rows.add(RecordBuilder.value("check_sites", RecordBuilder.t("r.check_sites"), sitesValue(found, cleared, total),
                    cleared >= total ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_NONE,
                    List.of(RecordBuilder.t("d.check_sites", found, cleared, total), RecordBuilder.t("d.check_sites_hint"))));
        }
        boolean notes = Growth.discovered(p, "notes:ch" + n) || WayfarerNotes.has(p, n);
        rows.add(RecordBuilder.value("check_notes", RecordBuilder.t("r.check_notes"), RecordBuilder.t(notes ? "v.check_notes_yes" : "v.check_notes_no"),
                notes ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_LOCKED, List.of(RecordBuilder.t("d.check_notes"))));
        return rows;
    }

    // 「見つけた 2 / 全 4（終えた 1）」(全数だけ。名前は出さない)
    static MutableComponent sitesValue(int found, int cleared, int total) {
        return RecordBuilder.t("v.check_sites", found, total, cleared);
    }
}
