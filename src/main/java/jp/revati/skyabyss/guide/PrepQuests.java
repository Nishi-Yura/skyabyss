package jp.revati.skyabyss.guide;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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

// 支度の段・備えの頼み (docs/design/21 の 1.4、34 の 3 章)。各章 (一〜四章) に 3 本。章の頭に、クエストログへ自動で出る。
//
// ---------------------------------------------------------------- 章ごとの 3 本
//   一章     支度の段 (34 の 4.2。メインの段 = category main): ch1/trail_iron (鉄の跡)・ch1/trail_blade (折れた剣)・ch1/trail_shield (盾と糧)。
//            tools/gen/ch1/gen_ch1_trails.py が書く。旧い備えの頼み ch1/prep_* は、この 3 本に吸収した (置くと読み込みの警告)
//   二章     支度の段 2 本 (34 の 4.3): ch2/salt_road (塩の道)・ch2/ebb_boat (船と貝)。tools/gen/ch2/gen_ch2_ground.py。旅装は任意のサブ ch2/wader_gear
//   三章     支度の段: ch3/lightning_pillars (避雷の柱)・ch3/scorched_watch (焼けた見張り塔)・ch3/sky_stone (空の石)。tools/gen/ch3/gen_ch3_ground.py
//   四章     支度の段 2 本 (34 の 4.5): ch4/fire_draught (火除けの薬)・ch4/forge_ruins (炉跡)。tools/gen/ch4/gen_ch4_ground.py
//   既定の形 (今はどの章も使っていない。仕組みと自己テストのために残す): data/skyabyss/quest/ch<n>/prep_gear.json (身ごしらえ) / prep_blade.json (刃を研ぐ) /
//            prep_pulse.json (鼓動を聞く)。ID は ch<n>/prep_<kind>、文のキーは quest.skyabyss.ch<n>.prep_<kind>.title など (QuestDef.keyBase)。
//            章の担当が 34 の段の名前に替えるときは、下の CHAPTER_IDS に一行足す
//   必須     chapter = その章の ID (一章なら titan_whale)、start_when (章の頭に自動で出る)、repeat と board は付けない。
//            category は支度の段 (CHAPTER_IDS) なら main、既定の備えの頼みなら sub。守っていなければ読み込みの警告 (QuestManager.loadErrors。validate)。
//   順番     支度の段の章 (一〜三章) は、一本ずつ順に出る (前の段を終えると次が出る。start_when に前の段の done。2026-10-22 の試遊:
//            メインが並ぶと迷う)。親のメイン (main_song・main_tide・main_thunder) には「支度」の段 prepare (目的 condition の prep) があり、
//            目的の帯 (client/quest/ObjectiveStrip.current) は支度の段のほうを一つだけ出す。四章の既定の形はふさがない (ログの並びは order)。
//   合図     三本が終わったとき (どの順でも)、その人に合図 `ch<n>_ready` (Quests.signal) を一度だけ送る。以降はいつでも `ready(player, n)` が真。
//            クエストの条件では {"type":"prep","chapter":n,"min":k} (k 本以上。省くと全部) で読める。一章は、三本そろうまで岩鯨が初めて
//            現れない (物語の運び。story.ch1.Ch1Trails.callReady・titan.TitanEvents)。見たあとの降下は今までどおり (21 の 3.2、Ch1Descent)。
//            三本ぜんぶの定義がそろっていない章は、合図は出ない (まだ中身がない章)。
//            章ごとの仕上げ (一章の記念品「鯨追いの印」) は onReady で足す (合図のあと、人ごとに一度)
//   覚書     その章の 3 本のどれかを受けたとき、その章の旅の覚書 (WayfarerNotes) を人ごとに一度渡す。
//   マルチ   ready / atLeast は人ごと。「グループの誰かが満たせば」は groupAtLeast (オンラインの仲間まで)。
public final class PrepQuests {
    public static final List<String> KINDS = List.of("gear", "blade", "pulse");
    private static final Pattern ID = Pattern.compile("ch([1-4])/prep_([a-z_]+)");
    // 既定の形 (ch<n>/prep_<kind>) でない章の 3 本 (章 → ID の並び。kind は ID の最後の「_」より後ろ)
    private static final Map<Integer, List<String>> CHAPTER_IDS = new ConcurrentHashMap<>(Map.of(
            1, List.of("ch1/trail_iron", "ch1/trail_blade", "ch1/trail_shield"),
            // 二章は 2 本 (34 の 4.3。塩の道・船と貝。tools/gen/ch2/gen_ch2_ground.py)
            2, List.of("ch2/salt_road", "ch2/ebb_boat"),
            // 三章の支度の段 (34 の 4.4。避雷の柱・焼けた見張り塔・空の石。tools/gen/ch3/gen_ch3_ground.py)
            3, List.of("ch3/lightning_pillars", "ch3/scorched_watch", "ch3/sky_stone"),
            // 四章の支度の段 (34 の 4.5。火除けの薬・炉跡。tools/gen/ch4/gen_ch4_ground.py)
            4, List.of("ch4/fire_draught", "ch4/forge_ruins")));
    // 三本そろったときの章ごとの仕上げ
    private static final Map<Integer, Consumer<ServerPlayer>> ON_READY = new ConcurrentHashMap<>();

    private PrepQuests() {
    }

    // 自己テスト用: その章を既定の形 (ch<n>/prep_*) に戻す。戻すための Runnable を返す (prep chain の既定の形の検査)
    public static Runnable testDefaultForm(int chapter) {
        List<String> was = CHAPTER_IDS.remove(chapter);
        return () -> {
            if (was != null) {
                CHAPTER_IDS.put(chapter, was);
            }
        };
    }

    public static String id(int chapter, String kind) {
        return "ch" + chapter + "/prep_" + kind;
    }

    // その章の 3 本の ID
    public static List<String> ids(int chapter) {
        List<String> fixed = CHAPTER_IDS.get(chapter);
        return fixed != null ? fixed : KINDS.stream().map(k -> id(chapter, k)).toList();
    }

    // 支度の段の章か (3 本がメインの段)
    public static boolean isTrail(int chapter) {
        return CHAPTER_IDS.containsKey(chapter);
    }

    public static void onReady(int chapter, Consumer<ServerPlayer> action) {
        ON_READY.put(chapter, action);
    }

    public static String readySignal(int chapter) {
        return "ch" + chapter + "_ready";
    }

    // 支度の段・備えの頼みの ID なら章 (1〜4)、違えば 0。支度の段の章の ch<n>/prep_* は 0 (もう使わない)
    public static int chapterOf(String questId) {
        for (Map.Entry<Integer, List<String>> e : CHAPTER_IDS.entrySet()) {
            if (e.getValue().contains(questId)) {
                return e.getKey();
            }
        }
        Matcher m = ID.matcher(questId);
        if (!m.matches()) {
            return 0;
        }
        int chapter = Integer.parseInt(m.group(1));
        return CHAPTER_IDS.containsKey(chapter) ? 0 : chapter;
    }

    public static String kindOf(String questId) {
        int chapter = chapterOf(questId);
        if (chapter > 0 && CHAPTER_IDS.containsKey(chapter)) {
            return questId.substring(questId.lastIndexOf('_') + 1);
        }
        Matcher m = ID.matcher(questId);
        return m.matches() ? m.group(2) : "";
    }

    // その章の 3 本がデータにそろっているか
    public static boolean defined(int chapter) {
        for (String id : ids(chapter)) {
            if (QuestManager.quest(id) == null) {
                return false;
            }
        }
        return true;
    }

    // 終えた本数 (0〜3)
    public static int done(ServerPlayer player, int chapter) {
        QuestLog log = QuestLog.of(player);
        int n = 0;
        for (String id : ids(chapter)) {
            QuestLog.Entry e = log.entry(id);
            if (e != null && e.done) {
                n++;
            }
        }
        return n;
    }

    public static boolean ready(ServerPlayer player, int chapter) {
        return defined(chapter) && done(player, chapter) >= ids(chapter).size();
    }

    // n 本以上 (n が 3 以上なら全部)
    public static boolean atLeast(ServerPlayer player, int chapter, int n) {
        return n >= ids(chapter).size() ? ready(player, chapter) : done(player, chapter) >= Math.max(1, n);
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
        if (chapter > 0) {
            WayfarerNotes.give(player, chapter);
        }
    }

    public static void onFinished(ServerPlayer player, QuestDef def) {
        int chapter = chapterOf(def.id());
        if (chapter == 0 || !ready(player, chapter)) {
            return;
        }
        // 合図は人ごとに一度 (経験値と同じ鍵の流儀。やり直しで二度鳴らさない)
        if (Growth.discover(player, "prep_ready:" + chapter)) {
            Notice.alert(player, "message.skyabyss.prep.ready", WayfarerNotes.chapterName(chapter));
            Quests.signal(player, readySignal(chapter));
            Consumer<ServerPlayer> extra = ON_READY.get(chapter);
            if (extra != null) {
                extra.accept(player);
            }
        }
    }

    // ---------------------------------------------------------------- 読み込みの検査
    public static void validate(Map<String, QuestDef> loaded, List<String> errors) {
        for (QuestDef def : loaded.values()) {
            int chapter = chapterOf(def.id());
            if (chapter == 0) {
                if (def.id().matches("ch\\d+/prep_.*")) {
                    errors.add("quest " + def.id() + ": a prep quest must be ch2〜ch4/prep_<gear|blade|pulse> (chapter 1 uses " + ids(1) + ")");
                }
                continue;
            }
            String where = "prep quest " + def.id();
            boolean trail = CHAPTER_IDS.containsKey(chapter);
            if (!trail && !KINDS.contains(kindOf(def.id()))) {
                errors.add(where + ": the kind must be one of " + KINDS);
            }
            if (!trail && def.main()) {
                errors.add(where + ": must be a sub quest");
            }
            if (trail && !def.main()) {
                errors.add(where + ": a trail step of the chapter must be a main quest");
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
