package jp.revati.skyabyss.story.arc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.server.level.ServerPlayer;

// 予兆 (foreshadowing) の枠: 章ごとに 3 つの拍 (痕跡 → 間接 → 一部) を、人ごとに、条件が合った順に一度ずつ起こす。
// 仕様は docs/design/01 の 4.1 (予兆 3 拍)、17 の 1 章、計画は docs/review/chapter_per_titan_plan.md の 2.2。
//
// ---------------------------------------------------------------- 使い方 (P4 の担当はここだけを見る。共有のファイルは編集しなくてよい)
//   1. 拍を作る:      Beat.of(Chapters.TIDE, 1, "salt_cairns", (player, beat) -> { ...起こす...; return true; })
//                         .time(Beat.Time.DAWN)                             // 時刻 (ANY/DAY/DAWN/DUSK/NIGHT)。既定 ANY
//                         .distance(p -> baseOf(p), 20, 150)                // anchor からの距離。anchor が null の間は見送り。既定 なし
//                         .cooldown(6000)                                   // 直前の拍 (どの章でも) から空ける tick。既定 5 分 (DEFAULT_COOLDOWN)
//                         .when(p -> p.level().isRaining())                 // 自由な条件。既定 常に真
//                         .dimension(null)                                  // 既定はオーバーワールドだけ。null で次元を問わない
//   2. 登録する (どちらでも。同じ拍を二重に登録すると IllegalArgumentException):
//        a. 自分の巨神の Setup クラスの register / 静的初期化から   Foreshadow.register(beat)
//        b. 共有のファイルを触らず、モッドバスの購読で
//             @EventBusSubscriber(modid = SkyAbyss.MODID, bus = EventBusSubscriber.Bus.MOD)
//             static void onForeshadow(ForeshadowRegisterEvent event) { event.register(beat); }
//           (b は共通の準備 FMLCommonSetupEvent のあとに呼ばれる。a は SkyAbyss のコンストラクタ経由で ModBus の登録時に呼ぶ)
//   3. 起きたかを調べる/やり直す:  Foreshadow.done(player, beat.id()) / Foreshadow.count(player, chapter) / ArcData.of(player).resetBeats()
//
//   action の中身: true を返すと「起きた」と記録する (記録は ArcData。人ごとに保存、死んでも引き継ぐ)。false を返すか例外なら見送る
//                  (例外は記録して以後二度と起こさない。同じ失敗の繰り返しを防ぐため)。起こすのは「跡・音・小さな異変」だけ
//                  (敵とジャンプスケアは足さない。01 の 7)。字幕は Notice.narrate / hint、頁は Lore.unlock など既存の口を使う
//
// ---------------------------------------------------------------- 決まり
//   - 冪等: 一度起きた拍 (id) は二度と起きない。ログアウト・死亡・再起動をまたぐ。
//   - 章: その拍の章 (Chapters.of(player)) を遊んでいる人にだけ。先の章にいる人には出さない。前の章の人にも出さない。
//         (次の章の 1 拍目は前の章の「引き」なので、前の章の余韻のあとに章が変わった時点で起きる)
//   - 順番に寛容: 毎回、拍を order 順に調べ、条件が合った最初の一つだけを起こす。前の拍が条件に合わず (夜だけの拍が昼のまま、など) 済んでいなくても、
//         後の拍は止まらない。見送られた拍は条件が整えば後からでも起きる。
//   - 間隔: 一度の調べで起こすのは一つまで。ほかの拍からも cooldown だけ空ける (連続で起きない)。
//   - 場面 (シネマティック) の間、死んでいる・観戦中の間は調べない。調べる頻度は 2 秒に一度 (Foreshadow.CHECK_TICKS)。
public final class Foreshadow {
    public static final int BEATS_PER_CHAPTER = 3;
    public static final long DEFAULT_COOLDOWN = 6000;
    public static final int CHECK_TICKS = 40;
    // ログイン直後・リスポーン直後に起こさない (画面が落ち着くまで)
    static final int MIN_TICK_COUNT = 200;

    private static final Map<String, Beat> BEATS = new LinkedHashMap<>();

    private Foreshadow() {
    }

    // ---------------------------------------------------------------- 登録
    public static synchronized void register(Beat beat) {
        if (BEATS.containsKey(beat.id())) {
            throw new IllegalArgumentException("duplicate foreshadowing beat " + beat.id());
        }
        for (Beat other : BEATS.values()) {
            if (other.chapter() == beat.chapter() && other.order() == beat.order()) {
                throw new IllegalArgumentException(beat.id() + " has the same order as " + other.id());
            }
        }
        BEATS.put(beat.id(), beat);
    }

    // その章の拍 (order 順)
    public static synchronized List<Beat> beats(Chapters chapter) {
        List<Beat> out = new ArrayList<>();
        for (Beat b : BEATS.values()) {
            if (b.chapter() == chapter) {
                out.add(b);
            }
        }
        out.sort(Comparator.comparingInt(Beat::order));
        return out;
    }

    @Nullable
    public static synchronized Beat beat(String id) {
        return BEATS.get(id);
    }

    // 自己テスト用: 登録を写し取る・空にする・戻す (テストの拍が本物の拍と混ざらないように)
    static synchronized Map<String, Beat> snapshot() {
        return new LinkedHashMap<>(BEATS);
    }

    static synchronized void clearAll() {
        BEATS.clear();
    }

    static synchronized void restore(Map<String, Beat> saved) {
        BEATS.clear();
        BEATS.putAll(saved);
    }

    // ---------------------------------------------------------------- 記録
    public static boolean done(ServerPlayer player, String beatId) {
        return ArcData.of(player).beatDone(beatId);
    }

    public static int count(ServerPlayer player, Chapters chapter) {
        return ArcData.of(player).beatCount(chapter);
    }

    // ---------------------------------------------------------------- 調べて起こす
    // 条件が合う次の拍 (なければ null)。起こさず、記録も変えない。current = その人の今の章
    @Nullable
    public static Beat next(ServerPlayer player, Chapters current, long gameTime, long dayTime) {
        ArcData data = ArcData.of(player);
        for (Beat b : beats(current)) {
            if (!data.beatDone(b.id()) && b.ready(player, gameTime, dayTime, data.lastBeatAt())) {
                return b;
            }
        }
        return null;
    }

    // 起こす: 条件が合う次の拍を一つだけ起こす。起きたらその拍、なければ null
    @Nullable
    public static Beat tryFire(ServerPlayer player, Chapters current, long gameTime, long dayTime) {
        ArcData data = ArcData.of(player);
        for (Beat b : beats(current)) {
            if (data.beatDone(b.id()) || !b.ready(player, gameTime, dayTime, data.lastBeatAt())) {
                continue;
            }
            boolean fired;
            try {
                fired = b.action().fire(player, b);
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("[arc] beat {} failed; it will not run again for {}", b.id(), player.getGameProfile().getName(), e);
                fired = true;
            }
            if (fired) {
                data.markBeat(b.id(), gameTime);
                return b;
            }
            // 見送られた拍 (action が false): 記録せず、後ろの拍を試す
        }
        return null;
    }

    // 本番の毎 tick の入口 (ArcSetup が CHECK_TICKS ごとに呼ぶ)。場面・死亡・観戦・ログイン直後は見送る
    static void tick(ServerPlayer player) {
        if (player.tickCount < MIN_TICK_COUNT || !player.isAlive() || player.isSpectator() || Cinematics.active(player)) {
            return;
        }
        Chapters current = Chapters.of(player);
        if (!current.titanChapter()) {
            return;
        }
        tryFire(player, current, player.level().getGameTime(), player.level().getDayTime());
    }
}
