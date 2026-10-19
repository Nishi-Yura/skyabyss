package jp.revati.skyabyss;

import jp.revati.skyabyss.notice.Notice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

// Modが出す文の見た目をそろえる。用途ごとに色と書体を決めておき、どこからでも同じ調子で出せるようにする。
// 返す文には知らせの種類の印が付く (notice/Notice)。プレイヤーへは Notice で送ると種類ごとの見せ方になり、
// コマンドの返事 (sendSuccess) やツールチップにそのまま使っても見た目は変わらない
public final class ModText {
    // 世界のささやき (語り・伝承): 青緑の斜体
    private static final TextColor LORE = TextColor.fromRgb(0x7FD6D0);
    // 操作の手がかり: 淡い灰色
    private static final TextColor HINT = TextColor.fromRgb(0xB8B8C8);
    // 前進した知らせ (封印が解けた、石が歌った): 淡い青
    private static final TextColor PROGRESS = TextColor.fromRgb(0x9FE8FF);
    // 拒まれた・危険: くすんだ赤紫
    private static final TextColor DENY = TextColor.fromRgb(0xD9779A);
    // 戦いの知らせ: 金色
    private static final TextColor BATTLE = TextColor.fromRgb(0xF2C14E);

    // 目的の文: 目的の帯の文字色 (ObjectiveStrip)
    private static final TextColor OBJECTIVE = TextColor.fromRgb(0xE9E3D4);

    private ModText() {
    }

    public static MutableComponent lore(String key, Object... args) {
        return Notice.tag(Notice.Kind.NARRATE, Component.literal("❖ ").withStyle(s -> s.withColor(LORE)),
                Component.translatable(key, args).withStyle(s -> s.withColor(LORE).withItalic(true)));
    }

    public static MutableComponent hint(String key, Object... args) {
        return Notice.tag(Notice.Kind.HINT, Component.translatable(key, args).withStyle(s -> s.withColor(HINT)));
    }

    public static MutableComponent progress(String key, Object... args) {
        return Notice.tag(Notice.Kind.UNLOCK, Component.literal("✦ ").withStyle(s -> s.withColor(PROGRESS)),
                Component.translatable(key, args).withStyle(s -> s.withColor(PROGRESS)));
    }

    public static MutableComponent deny(String key, Object... args) {
        return Notice.tag(Notice.Kind.WARN, Component.translatable(key, args).withStyle(s -> s.withColor(DENY)));
    }

    public static MutableComponent battle(String key, Object... args) {
        return Notice.tag(Notice.Kind.ALERT, Component.literal("⚔ ").withStyle(s -> s.withColor(BATTLE)),
                Component.translatable(key, args).withStyle(s -> s.withColor(BATTLE).withBold(true)));
    }

    // 目的の知らせ: 目的の帯と同じ型 (金の「◆」と、明るい文。太字にしない)。字幕の列に流れる (手がかりの扱い)
    public static MutableComponent objective(String key, Object... args) {
        // 画面には本文 (Notice.body = 最後の部品) だけが出るので、「◆」も同じ部品の中に入れる
        return Notice.tag(Notice.Kind.HINT, Component.literal("◆ ").withStyle(s -> s.withColor(BATTLE))
                .append(Component.translatable(key, args).withStyle(s -> s.withColor(OBJECTIVE))));
    }

    public static MutableComponent title(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GOLD);
    }

    public static MutableComponent subtitle(String key) {
        return Component.translatable(key).withStyle(s -> s.withColor(LORE).withItalic(true));
    }
}
