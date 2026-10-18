package jp.revati.skyabyss.story;

import java.util.List;

import jp.revati.skyabyss.opening.OpeningBeat;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.opening.OpeningState;
import jp.revati.skyabyss.progress.Progression;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// 章 (序章／一〜四章／五章・六章・終章) と、その人の進み (段階) からの写像。仕様は docs/design/10 の 3 章、計画は docs/review/chapter_per_titan_plan.md。
//
// ---------------------------------------------------------------- 口 (P2〜P4 はここだけを見る。変えるときは P1 の担当に聞く)
//   章                  enum Chapters  PROLOGUE, WHALE, TIDE, STORM, EMBER, ABYSS, MOON, FINALE (この順。ordinal = 番号 0〜7)
//   写像 (純粋)         Chapters.of(int stage, boolean prologueDone)                       → 鍵がそろっていない前提
//                       Chapters.of(int stage, boolean prologueDone, boolean keyAssembled) → 段階 4 + 鍵がそろった = ABYSS
//   写像 (人)           Chapters.of(ServerPlayer)    Progression.stage + 序盤の拍子 + SkyKey.assembled から毎回導く。保存は増やさない
//   序章を終えたか      Chapters.prologueDone(OpeningState)   legacy、または B7 (イサナ) 以降の拍子が済んでいる
//   番号・名前           number()  0=序章 1〜4=一〜四章 5=五章 6=六章 7=終章
//                       id()      内部名 (= 伝承の章 ID。lore/chapter/<id>.json、項目の "chapter" 欄)。byId で引ける
//                       tabKey()  「一章」(lore.skyabyss.chapter.<id>.tab)   nameKey() 「岩鯨」(.name)   titleKey() 題字「空を泳ぐもの」(.title)
//                       label()   「一章　岩鯨」(tab + 全角空白 + name) の Component
//   巨神・段階          titan()   その章の巨神 (ProgressRules.TITAN_ORDER の名前。序章と五章以降は null)
//                       stage()   その章を遊ぶ段階 (序章と一章 0、二章 1、三章 2、四章 3、五章 4。六章・終章は -1)
//   レベル              levelFrom() 推奨レベルの始まり / levelTo() その章を終えるころ / levelCap() 着いたときの上限 (ProgressRules.LEVEL_CAP と同じ)
//                       序章 1/4/15、一章 4/12/15、二章 12/18/21、三章 18/24/27、四章 24/30/33 (止めたあとは 40)、五章 40/50/50。六章・終章は -1 (未定)
//   区分                reserved() 五章以降 (名前だけで中身は未着手) / titanChapter() 一〜四章 (巨神編。旧「1 章」) / written() 中身のある章
//   次の章              next()  最後は null
//
// 内部の ID (伝承 chapter1/…、クエスト、段階 0〜4、上限、背のディメンション) は変えていない。伝承の旧「chapter1」章は
// 一〜四章に分けた (項目の ID は同じで、"chapter" 欄だけ新しい章を指す)。元の「chapter2」「chapter3」は五章・六章の ID として残してある。
public enum Chapters {
    PROLOGUE("prologue", null, 0, 1, 4, 15),
    WHALE("titan_whale", "stone_whale", 0, 4, 12, 15),
    TIDE("titan_tide", "tide_shell", 1, 12, 18, 21),
    STORM("titan_storm", "thunder_wing", 2, 18, 24, 27),
    EMBER("titan_ember", "ember", 3, 24, 30, 33),
    ABYSS("chapter2", null, 4, 40, 50, 50),
    MOON("chapter3", null, -1, -1, -1, -1),
    FINALE("finale", null, -1, -1, -1, -1);

    private final String id;
    private final String titan;
    private final int stage;
    private final int levelFrom;
    private final int levelTo;
    private final int levelCap;

    Chapters(String id, String titan, int stage, int levelFrom, int levelTo, int levelCap) {
        this.id = id;
        this.titan = titan;
        this.stage = stage;
        this.levelFrom = levelFrom;
        this.levelTo = levelTo;
        this.levelCap = levelCap;
    }

    public String id() {
        return id;
    }

    public int number() {
        return ordinal();
    }

    public String titan() {
        return titan;
    }

    public int stage() {
        return stage;
    }

    public int levelFrom() {
        return levelFrom;
    }

    public int levelTo() {
        return levelTo;
    }

    public int levelCap() {
        return levelCap;
    }

    public boolean reserved() {
        return ordinal() >= ABYSS.ordinal();
    }

    // 巨神編 (一〜四章。旧「1 章」、内部名 chapter1)
    public boolean titanChapter() {
        return ordinal() >= WHALE.ordinal() && ordinal() <= EMBER.ordinal();
    }

    public Chapters next() {
        Chapters[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
    }

    public String tabKey() {
        return "lore.skyabyss.chapter." + id + ".tab";
    }

    public String nameKey() {
        return "lore.skyabyss.chapter." + id + ".name";
    }

    public String titleKey() {
        return "lore.skyabyss.chapter." + id + ".title";
    }

    public Component label() {
        return Component.translatable(tabKey()).append("　").append(Component.translatable(nameKey()));
    }

    public static Chapters byId(String id) {
        for (Chapters c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    public static List<Chapters> written() {
        return List.of(PROLOGUE, WHALE, TIDE, STORM, EMBER);
    }

    // 段階 (順番どおりに止めた巨神の数 0〜4) と序章を終えたかから章を導く
    public static Chapters of(int stage, boolean prologueDone) {
        return of(stage, prologueDone, false);
    }

    public static Chapters of(int stage, boolean prologueDone, boolean keyAssembled) {
        int s = Math.max(0, stage);
        if (s >= 4) {
            return keyAssembled ? ABYSS : EMBER;
        }
        if (s == 0 && !prologueDone) {
            return PROLOGUE;
        }
        return switch (s) {
            case 0 -> WHALE;
            case 1 -> TIDE;
            case 2 -> STORM;
            default -> EMBER;
        };
    }

    // 序章 (B0〜B6) を終えたか。すでに遊んでいた人 (legacy) と、イサナ (B7) 以降の拍子が済んだ人
    public static boolean prologueDone(OpeningState st) {
        if (st.legacy()) {
            return true;
        }
        for (OpeningBeat beat : OpeningBeat.values()) {
            if (beat.number() >= OpeningBeat.ISANA.number() && st.done(beat)) {
                return true;
            }
        }
        return false;
    }

    public static Chapters of(ServerPlayer player) {
        return of(Progression.stage(player), prologueDone(OpeningDirector.state(player)), SkyKey.assembled(player));
    }
}
