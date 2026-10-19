package jp.revati.skyabyss;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import net.minecraft.server.MinecraftServer;

// 目的の帯の文字数の自己テスト (brushup T-7)。帯 (client/quest/ObjectiveStrip) は幅 230 px で、
// 「◆ 」+ 文を一行に描き、あふれると「…」で切る。サーバー側のテストなのでクライアントの字体は使えず、控えめな幅の表で数える:
//   ASCII は vanilla の標準字体の送り幅 (字の幅 + 1 px)、それ以外 (日本語・記号) はすべて 9 px。
// 対象: メインのクエストの全段の全目的 (+ あれば ".wait" 版) と、序盤の目的 (objective.skyabyss.*)。個数のある目的は「（9/9）」を足す。
//  - 失敗: 一行 (目印の距離なし) の幅 230 px に入らない文
//  - 失敗: 目印が付く目的 (TARGETED: 序盤の 5 つと、章の「巨神を探す・乗る」段。SiteTargets / OpeningDirector.target と合わせる) は、
//    右に距離「230m 南西」(約 52 px) を添えるので、その分を引いた幅に入らない文
//  - 注意 (ログのみ): それ以外で、距離を添えると入らなくなる文 (ふだんは出ない。目印が増えたとき切られる)
public final class ObjectiveBandSelfTest {
    static final int WIDTH = 230; // ObjectiveStrip.WIDTH (クライアント側のクラスなので、ここに写す)
    // 目印の距離「990m 南西」の幅 (+ 文との間の 6 px) の見積もり。南西は英語の画面でも日本語の字
    static final int FAR_RESERVE = width("990m ") + 18 + 6;

    // 目印 (距離と方角) が付く目的のキーの頭。帯の文はこの幅に入る必要がある
    private static final List<String> TARGETED = List.of(
            "objective.skyabyss.examine_camp", "objective.skyabyss.follow_smoke", "objective.skyabyss.ask_storyteller",
            "objective.skyabyss.find_isana", "objective.skyabyss.bone_hill",
            "quest.skyabyss.main_song.board.", "quest.skyabyss.main_tide.see.", "quest.skyabyss.main_tide.board.",
            "quest.skyabyss.main_thunder.see.", "quest.skyabyss.main_thunder.board.", "quest.skyabyss.main_ember.see.");

    private ObjectiveBandSelfTest() {
    }

    // vanilla 標準字体 (ASCII) の送り幅
    static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += glyph(s.charAt(i));
        }
        return w;
    }

    private static int glyph(char c) {
        if (c >= 0x80) {
            return 9;
        }
        if (c == ' ') {
            return 4;
        }
        if ("!,.:;i|".indexOf(c) >= 0) {
            return 2;
        }
        if ("'l`".indexOf(c) >= 0) {
            return 3;
        }
        if ("I[]t\"(){}*".indexOf(c) >= 0) {
            return 4;
        }
        if ("fk<>".indexOf(c) >= 0) {
            return 5;
        }
        if (c == '@' || c == '~') {
            return 7;
        }
        return 6;
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> warn = new ArrayList<>();
        int lines = 0;
        try {
            // 部品の確認: "Hello" = 6+6+3+3+6 = 24、"il." = 2+3+2 = 7、日本語 2 字 = 18
            if (width("Hello") != 24 || width("il.") != 7 || width("目的") != 18) {
                f.add("the width table is wrong: " + width("Hello") + "/" + width("il.") + "/" + width("目的"));
            }
            Map<String, String> ja = LangSelfTest.table("ja_jp");
            Map<String, String> en = LangSelfTest.table("en_us");
            // 対象の (キー -> 個数の有無)
            Map<String, Boolean> keys = new TreeMap<>();
            for (QuestDef def : QuestManager.all()) {
                if (!def.main()) {
                    continue;
                }
                for (QuestDef.Stage st : def.stages()) {
                    for (QuestDef.Objective ob : st.objectives()) {
                        String k = def.objectiveKey(st, ob);
                        keys.put(k, ob.count() > 1);
                        for (String suffix : List.of(".wait", ".wait.prep", ".wait.lamps")) {
                            if (ja.containsKey(k + suffix) || en.containsKey(k + suffix)) {
                                keys.put(k + suffix, ob.count() > 1);
                            }
                        }
                    }
                }
            }
            for (String k : new ArrayList<>(ja.keySet())) {
                if (k.startsWith("objective.skyabyss.")) {
                    keys.put(k, false);
                }
            }
            for (var e : keys.entrySet()) {
                for (var loc : List.of(Map.entry("ja_jp", ja), Map.entry("en_us", en))) {
                    String text = loc.getValue().get(e.getKey());
                    if (text == null) {
                        f.add(loc.getKey() + ": the band text " + e.getKey() + " is missing");
                        continue;
                    }
                    lines++;
                    String shown = "◆ " + text + (e.getValue() ? "（9/9）" : "");
                    int w = width(shown);
                    boolean targeted = TARGETED.stream().anyMatch(t -> e.getKey().equals(t) || (t.endsWith(".") && e.getKey().startsWith(t)));
                    if (w > WIDTH) {
                        f.add(loc.getKey() + ": " + e.getKey() + " is " + w + " px (> " + WIDTH + "): " + text);
                    } else if (targeted && w > WIDTH - FAR_RESERVE) {
                        f.add(loc.getKey() + ": " + e.getKey() + " has a distance label and is " + w + " px (> " + (WIDTH - FAR_RESERVE) + "): " + text);
                    } else if (w > WIDTH - FAR_RESERVE) {
                        warn.add(loc.getKey() + ": " + e.getKey() + " is " + w + " px (> " + (WIDTH - FAR_RESERVE) + " with a distance label): " + text);
                    }
                }
            }
            if (lines < 40) {
                f.add("only " + lines + " band texts were checked (quests not loaded?)");
            }
        } catch (IOException | RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST objective band crashed", e);
            f.add("crashed: " + e);
        }
        warn.forEach(m -> SkyAbyss.LOGGER.debug("SELFTEST objective band NOTE (cut when a distance label is shown): {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST objective band PASSED ({} texts, {} notes)", lines, warn.size());
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST objective band FAILED: {}", m));
        }
    }
}
