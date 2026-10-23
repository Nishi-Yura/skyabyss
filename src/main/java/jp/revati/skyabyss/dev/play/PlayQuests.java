package jp.revati.skyabyss.dev.play;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.server.level.ServerPlayer;

// /skyabyss play quest ...: クエスト (メイン・サブ・サイド) を進める・決める・済ませる。
// 進めるのは本物の道 (Quests.forceStage = 段の完了 → 報酬・シグナル → 次の段の計測 → 同期) を通すので、
// 日誌・目的の帯・NPC の話題・パーティの進みは本物と同じに更新される。docs/design/37
public final class PlayQuests {
    private PlayQuests() {
    }

    // 一人の「次へ」: id 省略なら追跡中のクエスト、なければ進行中のメイン、なければ次の未着手のメイン。
    // 結果の一行 (日本語。前 → 後)。進められなければ null と、理由を入れた message を返す
    public record Result(boolean ok, String line) {
    }

    public static Result next(ServerPlayer p, @Nullable String id) {
        String q = id != null ? id : pick(p);
        if (q == null) {
            return new Result(false, "進められるクエストがありません");
        }
        QuestDef def = QuestManager.quest(q);
        if (def == null) {
            return new Result(false, "そんなクエストはありません: " + q);
        }
        String before = line(p, q);
        QuestLog.Entry e = QuestLog.of(p).entry(q);
        if (e != null && e.done) {
            return new Result(false, before + " (もう完了しています)");
        }
        boolean moved = e == null ? Quests.start(p, q) : Quests.forceStage(p, q);
        if (!moved) {
            return new Result(false, before + " (始められません)");
        }
        return new Result(true, before + " → " + line(p, q));
    }

    // 「次へ」の対象: 追跡中 → 進行中のメイン (定義順) → 未着手の最初のメイン
    @Nullable
    private static String pick(ServerPlayer p) {
        QuestLog log = QuestLog.of(p);
        if (!log.tracked().isEmpty()) {
            QuestLog.Entry t = log.entry(log.tracked());
            if (t != null && !t.done && QuestManager.quest(log.tracked()) != null) {
                return log.tracked();
            }
        }
        for (QuestDef d : QuestManager.all()) {
            QuestLog.Entry e = log.entry(d.id());
            if (d.main() && e != null && !e.done) {
                return d.id();
            }
        }
        for (String m : PlayChapters.MAIN) {
            if (log.entry(m) == null && QuestManager.quest(m) != null) {
                return m;
            }
        }
        return null;
    }

    // 一つのクエストの状態を一行に (種類・未着手・完了・段階名と番号)
    public static String line(ServerPlayer p, String id) {
        QuestDef def = QuestManager.quest(id);
        String kind = def != null && def.main() ? "メイン" : "サブ";
        QuestLog.Entry e = QuestLog.of(p).entry(id);
        if (e == null) {
            return kind + " " + id + ": 未着手";
        }
        if (e.done) {
            return kind + " " + id + ": 完了";
        }
        String stage = def == null || e.stage >= def.stages().size() ? "?" : def.stages().get(e.stage).id();
        int total = def == null ? 0 : def.stages().size();
        return kind + " " + id + ": " + stage + " (" + (e.stage + 1) + "/" + total + ")";
    }

    // 進行中のクエストの一覧 (メインが先)。進行中がなければ空
    public static List<String> list(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        QuestLog log = QuestLog.of(p);
        for (int pass = 0; pass < 2; pass++) {
            for (QuestDef d : QuestManager.all()) {
                QuestLog.Entry e = log.entry(d.id());
                if (e != null && !e.done && d.main() == (pass == 0)) {
                    out.add(line(p, d.id()));
                }
            }
        }
        return out;
    }
}
