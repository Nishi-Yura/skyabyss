package jp.revati.skyabyss.story.ch5.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// 深淵の区切りと戦いの一言 (サーバーの story/ch5/layers/AbyssSections から)。章の札 (ChapterLabel) と目的の帯 (ObjectiveStrip) が読む
public final class AbyssSectionClient {
    static final String[] KEYS = {"bone", "salt", "storm", "ash", "heart"};
    private static volatile int section = -1;
    private static volatile String objective = "";

    private AbyssSectionClient() {
    }

    public static void set(int s, String obj) {
        section = s;
        objective = obj == null ? "" : obj;
    }

    // 深淵の中で、今の区切りの札 (「五章の一　骨の層」)。外なら null
    public static Component label() {
        int s = section;
        Minecraft mc = Minecraft.getInstance();
        if (s < 0 || s >= KEYS.length || mc.level == null || !mc.level.dimension().location().getPath().equals("abyss")) {
            return null;
        }
        return Component.translatable("chapter.skyabyss.abyss_v2." + KEYS[s] + ".tab").append("　")
                .append(Component.translatable("chapter.skyabyss.abyss_v2." + KEYS[s] + ".title"));
    }

    // 戦いの一言 (無ければ null)
    public static String objective() {
        String o = objective;
        Minecraft mc = Minecraft.getInstance();
        if (o.isEmpty() || section < 0 || mc.level == null || !mc.level.dimension().location().getPath().equals("abyss")) {
            return null;
        }
        return Component.translatable(o).getString();
    }
}
