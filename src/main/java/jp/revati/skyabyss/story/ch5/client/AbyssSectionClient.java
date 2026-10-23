package jp.revati.skyabyss.story.ch5.client;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.story.ch5.AbyssNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

// 深淵の区切り・戦いの一言・道案内 (サーバーの story/ch5/layers/AbyssSections と AbyssGuide から)。
// 章の札 (ChapterLabel) と目的の帯 (ObjectiveStrip) が読む。道案内は深淵の中ではいつも出る (戦いの一言があればそちら)
public final class AbyssSectionClient {
    static final String[] KEYS = {"bone", "salt", "storm", "ash", "heart"};
    private static volatile int section = -1;
    private static volatile String objective = "";
    private static volatile String guide = "";
    private static volatile List<String> args = List.of();
    @Nullable
    private static volatile BlockPos target;

    private AbyssSectionClient() {
    }

    public static void set(AbyssNetwork.Section s) {
        section = s.section();
        objective = s.objective() == null ? "" : s.objective();
        guide = s.guide() == null ? "" : s.guide();
        args = s.args() == null ? List.of() : s.args();
        target = s.has() ? new BlockPos(s.x(), s.y(), s.z()) : null;
    }

    public static void set(int s, String obj) {
        set(AbyssNetwork.Section.of(s, obj));
    }

    static boolean inAbyss() {
        Minecraft mc = Minecraft.getInstance();
        return section >= 0 && mc.level != null && mc.level.dimension().location().getPath().equals("abyss");
    }

    // 深淵の中で、今の区切りの札 (「五章の一　骨の層」)。外なら null
    public static Component label() {
        int s = section;
        if (s >= KEYS.length || !inAbyss()) {
            return null;
        }
        return Component.translatable("chapter.skyabyss.abyss_v2." + KEYS[s] + ".tab").append("　")
                .append(Component.translatable("chapter.skyabyss.abyss_v2." + KEYS[s] + ".title"));
    }

    // 戦いの一言 (無ければ null)
    public static String objective() {
        String o = objective;
        if (o.isEmpty() || !inAbyss()) {
            return null;
        }
        return Component.translatable(o).getString();
    }

    // 道案内の一言 (無ければ null)
    @Nullable
    public static String guide() {
        String g = guide;
        if (g.isEmpty() || !inAbyss()) {
            return null;
        }
        Object[] a = new Object[args.size()];
        for (int i = 0; i < a.length; i++) {
            String s = args.get(i);
            a[i] = s.startsWith("#") ? s.substring(1) : Component.translatable(s);
        }
        return Component.translatable(g, a).getString();
    }

    // 道案内の行き先 (戦いの間も主のいる間の真ん中などを指す)。無ければ null
    @Nullable
    public static BlockPos target() {
        return inAbyss() ? target : null;
    }
}
