package jp.revati.skyabyss.trial;

import net.minecraft.network.chat.Component;

// 修飾の札の文 (祠の画面と段の札で共通。サーバーでもクライアントでも組める)
public final class TrialChips {
    private TrialChips() {
    }

    public static Component text(TrialBoardNet.Chip c) {
        if (c.hidden()) {
            return Component.translatable("trial.skyabyss.hidden");
        }
        return switch (c.type()) {
            case "layout" -> Component.translatable("trial.skyabyss.chip.layout", c.code());
            case "number" -> {
                String[] p = c.code().split(":");
                yield Component.translatable("trial.skyabyss.num." + p[0]).append(": ").append(Component.translatable("trial.skyabyss.level." + p[1]));
            }
            case "enemy" -> {
                String[] p = c.code().split(":");
                yield Component.translatable("trial.skyabyss.enemy." + p[0] + "." + p[1]);
            }
            case "hazard" -> Component.translatable("trial.skyabyss.hazard." + c.code());
            case "goal" -> Component.translatable("trial.skyabyss.goal." + c.code());
            case "extreme" -> Component.translatable("trial.skyabyss.extreme." + c.code());
            case "abyss" -> Component.translatable("trial.skyabyss.abyss");
            case "quiet" -> Component.translatable("trial.skyabyss.quiet");
            default -> Component.literal(c.code());
        };
    }

    public static String stars(int tier) {
        return "★".repeat(Math.max(1, tier)) + "☆".repeat(Math.max(0, 3 - tier));
    }
}
