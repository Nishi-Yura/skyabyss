package jp.revati.skyabyss.guide;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.story.Chapters;

// 章ごとの経験値の予算 (docs/design/21 の 1.5 の表)。章の小要素は 1 章あたり「そのときの 1 レベル分の 1〜1.5 倍」までに抑え、上限を超えさせない。
//   章 n (一〜四章)   1 レベル分 / 既存の小要素 / 新しい分の上限 / 合計の上限
//   一章               480 / 350 / +400 / 750
//   二章               850 / 500 / +600 / 1,100
//   三章              1,400 / 800 / +900 / 1,700
//   四章              2,000 / 1,450 / +1,200 / 2,650
// 「新しい分」に数えるもの: quest/ch<n>/ のクエスト (既存の LEGACY を除く。繰り返す頼みは数えない) の、段階の経験値と報酬の経験値の合計、
// と、湧き台の場所 (SiteSpawner.Def) のクリアの経験値。読み込みのとき (QuestManager) と自己テスト (`xp budget`) が、この枠を超えていないか検める。
// 超えた章の場所の経験値は、湧き台が渡すとき 0 に切る (allowSite)。品は渡す
public final class ChapterXpBudget {
    public static final int[] LEVEL_XP = {0, 480, 850, 1400, 2000};
    public static final int[] EXISTING = {0, 350, 500, 800, 1450};
    public static final int[] NEW_CAP = {0, 400, 600, 900, 1200};
    public static final int[] TOTAL_CAP = {0, 750, 1100, 1700, 2650};
    // 合計の上限が 1 レベル分の何倍までか (表の一章は 750 / 480)
    public static final double MAX_RATIO = 1.6;

    // 「既存の小要素」に数え済みのクエスト (新しい分に数えない)
    public static final Set<String> LEGACY = Set.of("ch1/day1_traces", "ch1/day1_firewood", "ch1/lost_traveller", "ch2/ebb_road", "ch2/shore_signs",
            "ch3/sub_isana_wait", "ch3/sub_wind_marks", "ch4/fragments");

    private ChapterXpBudget() {
    }

    // クエスト 1 本の経験値の合計 (段階 + 報酬)
    public static int questXp(QuestDef def) {
        int xp = def.reward().xp();
        for (QuestDef.Stage s : def.stages()) {
            xp += s.xp();
        }
        return xp;
    }

    // クエスト ID の章 (quest/ch<n>/…。ch1〜ch4 なら n、違えば 0)
    public static int chapterOf(String questId) {
        if (questId.length() > 4 && questId.startsWith("ch") && questId.charAt(3) == '/' && questId.charAt(2) >= '1' && questId.charAt(2) <= '4') {
            return questId.charAt(2) - '0';
        }
        return 0;
    }

    // その章のクエストの新しい分
    public static int newQuestXp(Collection<QuestDef> defs, int chapter) {
        int sum = 0;
        for (QuestDef def : defs) {
            if (chapterOf(def.id()) == chapter && !LEGACY.contains(def.id()) && !def.repeats()) {
                sum += questXp(def);
            }
        }
        return sum;
    }

    // その章の湧き台の場所のクリアの経験値
    public static int siteXp(int chapter) {
        int sum = 0;
        for (SiteSpawner.Def d : SiteSpawner.defs()) {
            if (d.chapter().number() == chapter) {
                sum += d.xp();
            }
        }
        return sum;
    }

    public static int newXp(Collection<QuestDef> defs, int chapter) {
        return newQuestXp(defs, chapter) + siteXp(chapter);
    }

    // 枠を超えた章の一覧 (空なら守れている)
    public static List<String> overruns(Collection<QuestDef> defs) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int n = 1; n <= 4; n++) {
            out.addAll(overruns(defs, n));
        }
        return out;
    }

    public static List<String> overruns(Collection<QuestDef> defs, int n) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        {
            int fresh = newXp(defs, n);
            if (fresh > NEW_CAP[n]) {
                out.add("chapter " + n + ": new xp " + fresh + " exceeds the budget " + NEW_CAP[n]);
            }
            if (EXISTING[n] + fresh > TOTAL_CAP[n]) {
                out.add("chapter " + n + ": total small-element xp " + (EXISTING[n] + fresh) + " exceeds " + TOTAL_CAP[n]);
            }
        }
        return out;
    }

    // 読み込みの検査 (QuestManager)。超えていれば警告にする (クエストは読み込む)
    public static void validate(Collection<QuestDef> defs, List<String> errors) {
        errors.addAll(overruns(defs));
    }

    // 湧き台がクリアで渡す経験値。その章の枠を超えていれば 0 (品は渡す)
    public static int allowSite(Chapters chapter, int xp) {
        int n = chapter.number();
        if (n < 1 || n > 4) {
            return xp;
        }
        if (!overruns(jp.revati.skyabyss.quest.QuestManager.all(), n).isEmpty()) {
            SkyAbyss.LOGGER.warn("Chapter {} is over its xp budget; the site gives no xp", n);
            return 0;
        }
        return xp;
    }
}
