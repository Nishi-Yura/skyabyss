package jp.revati.skyabyss.story.arc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// 章の題字の札と余韻の札 (docs/design/18 の 3・9 章、01 の 4.1)。表示は Notice (上の帯の順番待ち)。
//
// ---------------------------------------------------------------- 題字の札 (自動。章が替わると一度だけ)
//   人の章 (Chapters.of) がまだ札を出していない章なら、替わってから TITLE_DELAY tick (5 秒) 空けて出す。空けるのは、前の章の余韻の札などが先に出るため。
//   場面 (シネマティック) の間・死んでいる間は出さず、終わってから出す。出すのは一〜四章だけ。次は何も出さずに「出した」ことにする:
//     - 序章 (開幕の題字は OpeningOverlay が出す)、五章以降 (中身がまだない)
//     - すでに遊んでいた人 (OpeningState.legacy)
//     - すでに先の章にいたとき、その前の章 (今の章の札だけを出す)
//   「出した」の記録は ArcData (人ごと、保存、死んでも引き継ぐ)。再ログイン・死亡・再起動でも二度出ない。
//   文: 見出し = Chapters.label() 「一章　岩鯨」、副題 = Chapters.titleKey() 「空を泳ぐもの」
//
// ---------------------------------------------------------------- 余韻の札 (章の終わり。各章の担当が呼ぶ)
//   ChapterCards.aftermath(player, Chapters.WHALE, Component 一言)   // 見出し = 「一章　岩鯨　終」
//   ChapterCards.aftermath(player, Chapters.WHALE)                    // 一言 = 翻訳キー chapter.skyabyss.aftermath.<章の ID> (その章の担当が lang に足す)
//   人ごとに章ごと一度だけ (記録は ArcData)。場面の間は預かり、終わってから出す (ログアウトで預かりは消える。その場合は記録されないので、もう一度呼んでよい)。
//   語り部の一言を足したいときは、この札のあとに Notice.narrate を続ける (上の帯と字幕は別の列なので重ならない)。
public final class ChapterCards {
    // 章が替わってから題字を出すまで
    public static final long TITLE_DELAY = 100;
    public static final String AFTERMATH_HEAD_KEY = "chapter.skyabyss.aftermath.head";

    private record Pending(Chapters chapter, Component line) {
    }

    // 題字を出す待ち (章が替わったのを見た時刻。保存しない。ログインのたびに数え直す)
    private static final Map<UUID, Long> TITLE_SINCE = new HashMap<>();
    private static final Map<UUID, Chapters> TITLE_FOR = new HashMap<>();
    // 預かっている余韻
    private static final Map<UUID, List<Pending>> HELD = new HashMap<>();

    private ChapterCards() {
    }

    public static Component titleLine(Chapters chapter) {
        return Component.translatable(chapter.titleKey());
    }

    public static Component aftermathHead(Chapters chapter) {
        return Component.translatable(AFTERMATH_HEAD_KEY, chapter.label());
    }

    public static Component aftermathDefaultLine(Chapters chapter) {
        return Component.translatable("chapter.skyabyss.aftermath." + chapter.id());
    }

    // ---------------------------------------------------------------- 余韻 (API)
    public static boolean aftermath(ServerPlayer player, Chapters chapter) {
        return aftermath(player, chapter, aftermathDefaultLine(chapter));
    }

    // 受け付けたら true (出した、または預かった)。すでに出している・預かっているなら false
    public static boolean aftermath(ServerPlayer player, Chapters chapter, Component line) {
        if (ArcData.of(player).aftermathShown(chapter)) {
            return false;
        }
        List<Pending> held = HELD.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        for (Pending p : held) {
            if (p.chapter() == chapter) {
                return false;
            }
        }
        if (busy(player)) {
            held.add(new Pending(chapter, line));
            return true;
        }
        deliverAftermath(player, chapter, line);
        return true;
    }

    private static void deliverAftermath(ServerPlayer player, Chapters chapter, Component line) {
        ArcData.of(player).markAftermath(chapter);
        Notice.aftermath(player, aftermathHead(chapter), line);
    }

    // ---------------------------------------------------------------- 題字 (自動)
    // 本番の入口 (ArcSetup が 20 tick ごとに呼ぶ)
    static void tick(ServerPlayer player) {
        boolean legacy = OpeningDirector.state(player).legacy();
        tick(player, Chapters.of(player), legacy, player.level().getGameTime());
    }

    // 試せる形: chapter = 人の今の章、legacy = すでに遊んでいた人、now = ゲーム時間。札を出した (送った) なら true
    static boolean tick(ServerPlayer player, Chapters chapter, boolean legacy, long now) {
        UUID id = player.getUUID();
        boolean sent = false;
        ArcData data = ArcData.of(player);
        // 預かった余韻は、場面が終わったら出す
        List<Pending> held = HELD.get(id);
        if (held != null && !held.isEmpty() && !busy(player)) {
            Iterator<Pending> it = held.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                it.remove();
                if (!data.aftermathShown(p.chapter())) {
                    deliverAftermath(player, p.chapter(), p.line());
                    sent = true;
                }
            }
        }
        if (data.titleShown(chapter)) {
            TITLE_SINCE.remove(id);
            TITLE_FOR.remove(id);
            return sent;
        }
        // 出さずに済ませる: 序章・五章以降・すでに遊んでいた人。先の章で始めた人は前の章を済ませる
        if (!chapter.titanChapter() || legacy) {
            data.markTitlesUpTo(chapter);
            return sent;
        }
        for (Chapters c : Chapters.values()) {
            if (c.number() < chapter.number()) {
                data.markTitle(c);
            }
        }
        Long since = TITLE_SINCE.get(id);
        if (since == null || TITLE_FOR.get(id) != chapter) {
            TITLE_SINCE.put(id, now);
            TITLE_FOR.put(id, chapter);
            since = now;
        }
        if (now - since < TITLE_DELAY || busy(player)) {
            return sent;
        }
        data.markTitle(chapter);
        TITLE_SINCE.remove(id);
        TITLE_FOR.remove(id);
        Notice.chapter(player, chapter.label(), titleLine(chapter));
        return true;
    }

    // 札を出せない間: 場面の最中、死んでいる、観戦中
    private static boolean busy(ServerPlayer player) {
        if (busyForTest != null) {
            return busyForTest;
        }
        return Cinematics.active(player) || !player.isAlive() || player.isSpectator();
    }

    // 自己テスト用: 場面の最中を装う (null で本物の判定)
    private static Boolean busyForTest;

    static void setBusyForTest(Boolean busy) {
        busyForTest = busy;
    }

    static void forget(UUID id) {
        TITLE_SINCE.remove(id);
        TITLE_FOR.remove(id);
        HELD.remove(id);
    }

    // 自己テスト用
    static int held(UUID id) {
        List<Pending> h = HELD.get(id);
        return h == null ? 0 : h.size();
    }
}
