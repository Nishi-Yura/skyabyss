package jp.revati.skyabyss.guide;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.server.level.ServerPlayer;

// 備えの頼み (docs/design/21 の 1.4、P3)。各章 (一〜四章) に 3 本のサブクエスト。章の頭に、クエストログへ自動で出る。
//
// ---------------------------------------------------------------- 追加ファイルの規則 (章の担当 P6・P11・P15・P19 が守る)
//   置き場   data/skyabyss/quest/ch<n>/prep_gear.json (身ごしらえ) / prep_blade.json (刃を研ぐ) / prep_pulse.json (鼓動を聞く)。n = 1〜4。
//            ID は ch<n>/prep_<kind>、文のキーは quest.skyabyss.ch<n>.prep_<kind>.title など (QuestDef.keyBase)。
//   必須     category = sub、chapter = その章の ID (一章なら titan_whale)、start_when (章の頭に自動で出る。たとえば章が開いた = stage の条件)、
//            repeat と board は付けない (一度きりの頼みで、板には出さない)。giver は旅の職人の役 (P4。それまでは語り部でもよい)。
//            守っていなければ読み込みの警告 (QuestManager.loadErrors) になる (PrepQuests.validate)。
//   順番     ふさがない。どれから受けても終えてもよい (ログの並びは order で 1→2→3)。
//   合図     三本が終わったとき (どの順でも)、その人に合図 `ch<n>_ready` (Quests.signal) を一度だけ送る。以降はいつでも `ready(player, n)` が真。
//            クエストの条件では {"type":"prep","chapter":n,"min":k} (k 本以上。省くと全部) で読める。一章の岩鯨の降下は「2 本終えたとき」(3.2)。
//            三本ぜんぶの定義がそろっていない章は、合図は出ない (まだ中身がない章)。
//   覚書     ch<n>/prep_gear を受けたとき、その章の旅の覚書 (WayfarerNotes) を人ごとに一度渡す。
//   マルチ   ready / atLeast は人ごと。「グループの誰かが満たせば」は groupAtLeast (オンラインの仲間まで)。
public final class PrepQuests {
    public static final List<String> KINDS = List.of("gear", "blade", "pulse");
    private static final Pattern ID = Pattern.compile("ch([1-4])/prep_([a-z_]+)");

    private PrepQuests() {
    }

    public static String id(int chapter, String kind) {
        return "ch" + chapter + "/prep_" + kind;
    }

    public static String readySignal(int chapter) {
        return "ch" + chapter + "_ready";
    }

    // 備えの頼みの ID なら章 (1〜4)、違えば 0
    public static int chapterOf(String questId) {
        Matcher m = ID.matcher(questId);
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    public static String kindOf(String questId) {
        Matcher m = ID.matcher(questId);
        return m.matches() ? m.group(2) : "";
    }

    // その章の備えの頼みが 3 本ともデータにそろっているか
    public static boolean defined(int chapter) {
        for (String kind : KINDS) {
            if (QuestManager.quest(id(chapter, kind)) == null) {
                return false;
            }
        }
        return true;
    }

    // 終えた本数 (0〜3)
    public static int done(ServerPlayer player, int chapter) {
        QuestLog log = QuestLog.of(player);
        int n = 0;
        for (String kind : KINDS) {
            QuestLog.Entry e = log.entry(id(chapter, kind));
            if (e != null && e.done) {
                n++;
            }
        }
        return n;
    }

    public static boolean ready(ServerPlayer player, int chapter) {
        return defined(chapter) && done(player, chapter) >= KINDS.size();
    }

    // n 本以上 (n が 3 以上なら全部)
    public static boolean atLeast(ServerPlayer player, int chapter, int n) {
        return n >= KINDS.size() ? ready(player, chapter) : done(player, chapter) >= Math.max(1, n);
    }

    // グループ (パーティ、または一人) のオンラインの誰かが満たしているか
    public static boolean groupAtLeast(ServerPlayer player, int chapter, int n) {
        if (atLeast(player, chapter, n)) {
            return true;
        }
        PartyData.Party party = Parties.of(player);
        if (party == null) {
            return false;
        }
        for (ServerPlayer mate : Parties.onlineMembers(player.server, party)) {
            if (atLeast(mate, chapter, n)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- クエストからの呼び出し (Quests.start / finish)
    public static void onStarted(ServerPlayer player, QuestDef def) {
        int chapter = chapterOf(def.id());
        if (chapter > 0 && kindOf(def.id()).equals("gear")) {
            WayfarerNotes.give(player, chapter);
        }
    }

    public static void onFinished(ServerPlayer player, QuestDef def) {
        int chapter = chapterOf(def.id());
        if (chapter == 0 || !KINDS.contains(kindOf(def.id())) || !ready(player, chapter)) {
            return;
        }
        // 合図は人ごとに一度 (経験値と同じ鍵の流儀。やり直しで二度鳴らさない)
        if (Growth.discover(player, "prep_ready:" + chapter)) {
            Notice.alert(player, "message.skyabyss.prep.ready", WayfarerNotes.chapterName(chapter));
            Quests.signal(player, readySignal(chapter));
        }
    }

    // ---------------------------------------------------------------- 読み込みの検査
    public static void validate(Map<String, QuestDef> loaded, List<String> errors) {
        for (QuestDef def : loaded.values()) {
            int chapter = chapterOf(def.id());
            if (chapter == 0) {
                if (def.id().matches("ch\\d+/prep_.*")) {
                    errors.add("quest " + def.id() + ": a prep quest must be ch1〜ch4/prep_<gear|blade|pulse>");
                }
                continue;
            }
            String where = "prep quest " + def.id();
            if (!KINDS.contains(kindOf(def.id()))) {
                errors.add(where + ": the kind must be one of " + KINDS);
            }
            if (def.main()) {
                errors.add(where + ": must be a sub quest");
            }
            if (!def.chapter().equals(Chapters.values()[chapter].id())) {
                errors.add(where + ": chapter must be " + Chapters.values()[chapter].id());
            }
            if (def.startWhen() == null) {
                errors.add(where + ": needs start_when (it appears in the log at the head of the chapter)");
            }
            if (def.repeats() || def.board()) {
                errors.add(where + ": must not repeat or go on the board");
            }
        }
    }
}
