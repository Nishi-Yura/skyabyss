package jp.revati.skyabyss.dev.play;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.playtest.Playtest;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.server.level.ServerPlayer;

// 試遊の章の用意・道具一式・ボス。どれも既存の試遊の部品 (Playtest) と、クエストの公開 API だけを使う。
// 章は「その章に入る直前の状態」を作る。0 は何も持たない初めての状態。docs/design/37 の 2 節
public final class PlayChapters {
    private PlayChapters() {
    }

    // 章の主線 (章 n は MAIN[n-1] を今の目的にする)。5 は深淵 (main_abyss) で、主線 main_key を済ませてから入る
    public static final String[] MAIN = {"main_song", "main_tide", "main_thunder", "main_ember", "main_key", "main_abyss"};
    public static final int CHAPTERS = 6;

    // 道具一式の名前 → Playtest.gear の tier (0 = 鉄、1 = 岩鯨の装備 … 4 = 余燼まで終えた)
    public static final List<String> KITS = List.of("iron", "whale", "tide", "storm", "ember");

    public static int kitTier(String name) {
        return KITS.indexOf(name);
    }

    // ボス名 (boss コマンドの候補)
    public static final List<String> BOSSES = List.of("whale", "tide", "storm", "ember", "raiju", "smith", "warden", "nightmare");

    // 章の用意。null なら成功、そうでなければ翻訳キー (失敗の理由)。stage を渡すと、その章の主線をその段階にする
    @Nullable
    public static String chapter(ServerPlayer p, int n, @Nullable String stage) {
        if (n < 0 || n >= CHAPTERS) {
            return "commands.skyabyss_play.bad_chapter";
        }
        Playtest.reset(p);
        if (n == 0) {
            return null;
        }
        if (n <= 4) {
            // 岩鯨 (1) から四つの巨神の章。Playtest.chapter が序盤・前の心臓・道具・主線の開始・巨神の呼び出しをする
            Playtest.chapter(p, n);
        } else {
            // 深淵 (5): 四つの巨神を止めたあと、main_key を済ませて main_abyss に入る。戻すのは地上
            Playtest.skipOpening(p);
            Playtest.grantPrior(p, 4);
            Playtest.gear(p, 4);
            for (int i = 1; i <= 3; i++) {
                Quests.start(p, MAIN[i]);
                finish(p, MAIN[i]);
            }
            finish(p, "main_key");
            Quests.start(p, "main_abyss");
            Playtest.toOverworld(p);
        }
        if (stage != null) {
            String quest = MAIN[n - 1];
            if (!Quests.setStage(p, quest, stage)) {
                return "commands.skyabyss_play.bad_stage";
            }
        }
        return null;
    }

    // 主線を最後まで済ませる (段階を順に済ませる。上限つき)。始まっていなければ始める
    public static void finish(ServerPlayer p, String quest) {
        if (QuestLog.of(p).entry(quest) == null && !Quests.start(p, quest)) {
            return;
        }
        for (int i = 0; i < 60; i++) {
            QuestLog.Entry e = QuestLog.of(p).entry(quest);
            if (e == null || e.done || !Quests.forceStage(p, quest)) {
                return;
            }
        }
    }

    // 道具一式: 装備・武器・矢・回復の品 (Playtest.gear。体力と成長の目安もそろえる)
    public static void kit(ServerPlayer p, int tier) {
        Playtest.gear(p, tier);
    }

    // 巨神の心臓が止まっているか (状態の表示用)
    public static boolean stilled(ServerPlayer p, String titan) {
        return Progression.stopped(p.server, p.getUUID(), titan);
    }

    // ボスを用意して呼ぶ。tier を渡すと、その道具で行く。null なら成功、翻訳キーなら失敗
    @Nullable
    public static String boss(ServerPlayer p, String name, @Nullable Integer tier) {
        if (!BOSSES.contains(name)) {
            return "commands.skyabyss_play.unknown_boss";
        }
        if (tier != null) {
            if (tier < 0 || tier > 4) {
                return "commands.skyabyss_play.bad_tier";
            }
            kit(p, tier);
        }
        switch (name) {
            case "whale" -> {
                // 岩鯨の心臓の部屋へ。戦いは部屋に入ると始まる (専用の開始コマンドはない)
                return PlayPlaces.go(p, "whale-heart");
            }
            case "tide", "storm", "ember" -> {
                // 背に乗せて心臓の部屋へ、着いてから開始 (乗るのは呼び出しを待つので、続きは callback で)
                int idx = Playtest.index(name);
                Playtest.board(p, idx, pp -> {
                    Playtest.gotoSpot(pp, idx, "heart");
                    Playtest.run(pp, "skyabyss " + name + "heart start");
                });
                return null;
            }
            case "raiju", "smith" -> {
                // 中ボス: 背に乗せて、順路の中ボスの間 (trial/raid/mid) へ運ぶ。戦いは間に着くと始まる
                int idx = name.equals("raiju") ? 2 : 3;
                Playtest.board(p, idx, pp -> {
                    String miss = PlayRaid.enterMid(pp, idx);
                    if (miss != null) {
                        pp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(miss, name));
                    }
                });
                return null;
            }
            case "warden" -> {
                String miss = PlayPlaces.go(p, "abyss");
                if (miss != null) {
                    return miss;
                }
                Playtest.run(p, "skyabyss abyss warden wake");
                return null;
            }
            default -> {
                // nightmare: 深淵に入ってから。次の段階は記録で決まる (今回は段階を選ばない)
                String miss = PlayPlaces.go(p, "abyss");
                if (miss != null) {
                    return miss;
                }
                Playtest.run(p, "skyabyss_nightmare enter");
                return null;
            }
        }
    }
}
