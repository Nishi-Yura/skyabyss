package jp.revati.skyabyss.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

// 終えたクエストの頁 (クエストログ。client/quest/QuestLogScreen が描く) の中身を、画面に依らずに決める (自己テスト npc が見る)。
//   やったこと  段ごとの目的を順に (目的の文は待ち版でなく元の鍵。数は「n/n」)。どれか一つの段は、二つ目から「または」でつなぐ
//   書き付け    終わりの文 (<クエスト>.done)。無ければ最後の段の書き付け
//   日          受けた日と終えた日 (ゲームの総 tick から。1 日目から数える)。終えた時刻の記録が無ければ出さない
public final class QuestDoneView {
    private QuestDoneView() {
    }

    // やったことの一行。key = 目的の文の鍵、count = 「n/n」(数のない目的は空)、alternative = どれか一つの段の二つ目から
    public record Line(String key, String count, boolean alternative) {
    }

    public static List<Line> lines(QuestDef def) {
        List<Line> out = new ArrayList<>();
        for (QuestDef.Stage stage : def.stages()) {
            for (int i = 0; i < stage.objectives().size(); i++) {
                QuestDef.Objective ob = stage.objectives().get(i);
                out.add(new Line(def.objectiveKey(stage, ob), ob.count() > 1 ? ob.count() + "/" + ob.count() : "", stage.any() && i > 0));
            }
        }
        return out;
    }

    // 書き付けの鍵 (has = その鍵の文があるか)
    public static String journalKey(QuestDef def, Predicate<String> has) {
        if (has.test(def.doneKey()) || def.stages().isEmpty()) {
            return def.doneKey();
        }
        return def.journalKey(def.stages().get(def.stages().size() - 1));
    }

    // {受けた日, 終えた日} (終えた時刻が無ければ null)
    @Nullable
    public static long[] days(QuestLog.Entry entry) {
        if (!entry.done || entry.finished <= 0) {
            return null;
        }
        return new long[] {Math.max(0, entry.started) / 24000L + 1, entry.finished / 24000L + 1};
    }
}
