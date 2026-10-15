package jp.revati.skyabyss;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

// Modが出す文の見た目をそろえる。用途ごとに色と書体を決めておき、どこからでも同じ調子で出せるようにする
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

    private ModText() {
    }

    public static MutableComponent lore(String key, Object... args) {
        return Component.literal("❖ ").withStyle(s -> s.withColor(LORE))
                .append(Component.translatable(key, args).withStyle(s -> s.withColor(LORE).withItalic(true)));
    }

    public static MutableComponent hint(String key, Object... args) {
        return Component.translatable(key, args).withStyle(s -> s.withColor(HINT));
    }

    public static MutableComponent progress(String key, Object... args) {
        return Component.literal("✦ ").withStyle(s -> s.withColor(PROGRESS))
                .append(Component.translatable(key, args).withStyle(s -> s.withColor(PROGRESS)));
    }

    public static MutableComponent deny(String key, Object... args) {
        return Component.translatable(key, args).withStyle(s -> s.withColor(DENY));
    }

    public static MutableComponent battle(String key, Object... args) {
        return Component.literal("⚔ ").withStyle(s -> s.withColor(BATTLE))
                .append(Component.translatable(key, args).withStyle(s -> s.withColor(BATTLE).withBold(true)));
    }

    public static MutableComponent title(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GOLD);
    }

    public static MutableComponent subtitle(String key) {
        return Component.translatable(key).withStyle(s -> s.withColor(LORE).withItalic(true));
    }
}
