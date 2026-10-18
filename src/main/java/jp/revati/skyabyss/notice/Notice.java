package jp.revati.skyabyss.notice;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 画面に出す知らせの共通の入口 (docs/design/18_notices.md)。チャットを流れる文をやめ、種類ごとの見せ方に振り分ける。
//   narrate : 世界の声・囁き      → 画面の下中央の字幕 (ゆっくり出て消える)
//   hint    : 操作の手がかり      → 同じ字幕 (灰色。混んだら先に捨てる)
//   levelUp : レベル・技の点      → 画面中央の短い演出
//   gain    : 獲得・解放          → 右上の札 (伝承・クエストの札と同じ列)
//   place   : 場所の名前          → 画面中央上のタイトル
//   alert   : 戦いの始まり・出現  → 同じ上の帯 (小さめ)
//   chapter / aftermath : 章の題字・余韻 → 同じ上の帯の列 (順番待ち。大きめ・長め。story/arc/ChapterCards が送る)
//   warn    : 失敗・入れない      → アクションバー
// チャットに残すのは、他の人に伝えるべきこと (誰が心臓を倒した等) とコマンドの結果だけ
public final class Notice {
    public enum Channel {
        CAPTION, CENTER, BANNER, TOAST, ACTIONBAR
    }

    public enum Kind {
        NARRATE(Channel.CAPTION), HINT(Channel.CAPTION), LEVEL_UP(Channel.CENTER), GAIN(Channel.TOAST), UNLOCK(Channel.TOAST),
        PLACE(Channel.BANNER), ALERT(Channel.BANNER), WARN(Channel.ACTIONBAR), BAR(Channel.ACTIONBAR),
        // 章の題字の札 (text = 「一章　岩鯨」、extra = 題字) と余韻の札 (text = 一言、extra = 見出し)。上の帯と同じ順番待ち。
        // 追加した種類は必ず末尾に (パケットは順番の番号で送る)
        CHAPTER(Channel.BANNER), AFTERMATH(Channel.BANNER);

        private final Channel channel;

        Kind(Channel channel) {
            this.channel = channel;
        }

        public Channel channel() {
            return channel;
        }
    }

    private static final String TAG = "skyabyss:notice/";

    private Notice() {
    }

    // ---------------------------------------------------------------- 文への印 (ModText が付ける)
    // 先頭の空の部品の insertion に種類を入れる。中身 (印のあとの飾りと本文) は子として並べる
    public static MutableComponent tag(Kind kind, Component... parts) {
        MutableComponent root = Component.empty().withStyle(s -> s.withInsertion(TAG + kind.name().toLowerCase(Locale.ROOT)));
        for (Component part : parts) {
            root.append(part);
        }
        return root;
    }

    @Nullable
    public static Kind kindOf(Component text) {
        String ins = text.getStyle().getInsertion();
        if (ins == null || !ins.startsWith(TAG) || !(text.getContents() == PlainTextContents.EMPTY)) {
            return null;
        }
        try {
            return Kind.valueOf(ins.substring(TAG.length()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 画面に出す本文 (飾りの記号を除く)。印がなければそのまま
    public static Component body(Component text) {
        if (kindOf(text) == null) {
            return text;
        }
        List<Component> parts = text.getSiblings();
        return parts.isEmpty() ? text : parts.get(parts.size() - 1);
    }

    // チャットにも残すときの文 (飾りつき・印なし)
    public static Component plain(Component text) {
        if (kindOf(text) == null) {
            return text;
        }
        MutableComponent out = Component.empty();
        text.getSiblings().forEach(out::append);
        return out;
    }

    // ---------------------------------------------------------------- サーバーから送る
    public static void narrate(Player p, String key, Object... args) {
        send(p, Kind.NARRATE, ModText.lore(key, args));
    }

    public static void hint(Player p, String key, Object... args) {
        send(p, Kind.HINT, ModText.hint(key, args));
    }

    public static void levelUp(Player p, String key, Object... args) {
        send(p, Kind.LEVEL_UP, ModText.progress(key, args));
    }

    public static void gain(Player p, String key, Object... args) {
        send(p, Kind.GAIN, ModText.progress(key, args));
    }

    public static void gain(Player p, Component text) {
        send(p, Kind.GAIN, text);
    }

    public static void unlock(Player p, String key, Object... args) {
        send(p, Kind.UNLOCK, ModText.progress(key, args));
    }

    public static void alert(Player p, String key, Object... args) {
        send(p, Kind.ALERT, ModText.battle(key, args));
    }

    public static void warn(Player p, String key, Object... args) {
        send(p, Kind.WARN, ModText.deny(key, args));
    }

    // 場所の名前: title は大きく、subtitle (空でもよい) はその下に
    public static void place(Player p, Component title, @Nullable Component subtitle) {
        deliver(p, new NoticePayload(Kind.PLACE, title, subtitle == null ? Component.empty() : subtitle));
    }

    // 章の題字の札: title は「一章　岩鯨」、subtitle は題字「空を泳ぐもの」。story/arc/ChapterCards が人ごとに一度だけ送る
    public static void chapter(Player p, Component title, Component subtitle) {
        deliver(p, new NoticePayload(Kind.CHAPTER, title, subtitle));
    }

    // 章の余韻の札: line は一言 (碑文など)、heading は「一章　岩鯨　終」のような見出し
    public static void aftermath(Player p, Component heading, Component line) {
        deliver(p, new NoticePayload(Kind.AFTERMATH, line, heading));
    }

    // 種類を文の印から決める (印がなければチャット)。既存の呼び出しの置き換え先
    public static void send(Player p, Component text) {
        Kind kind = kindOf(text);
        if (kind == null || !(p instanceof ServerPlayer)) {
            p.sendSystemMessage(text);
        } else {
            send(p, kind, text);
        }
    }

    // 種類を決めて送る (飾りつきのまま送り、画面には本文だけ、チャットに残す設定のときは飾りつきを出す)
    public static void send(Player p, Kind kind, Component text) {
        deliver(p, new NoticePayload(kind, text, Component.empty()));
    }

    // アクションバー (ホットバーの上): displayClientMessage(…, true) の置き換え先。警告の印のある文は警告として
    public static void bar(Player p, Component text) {
        if (!(p instanceof ServerPlayer)) {
            p.displayClientMessage(text, true);
            return;
        }
        deliver(p, new NoticePayload(kindOf(text) == Kind.WARN ? Kind.WARN : Kind.BAR, text, Component.empty()));
    }

    // 同じ文を何人かへ
    public static void send(Iterable<? extends Player> players, Component text) {
        for (Player p : players) {
            send(p, text);
        }
    }

    private static void deliver(Player p, NoticePayload payload) {
        if (!(p instanceof ServerPlayer sp) || p instanceof FakePlayer || sp.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(sp, payload);
    }
}
