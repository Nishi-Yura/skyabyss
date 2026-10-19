package jp.revati.skyabyss.titan.travel;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

// 海図の行き先の名前 (クライアントとサーバーの両方から使う。サーバーのクラスに触れない)
public final class TravelDest {
    private TravelDest() {
    }

    public static Component destName(@Nullable TravelNetwork.Dest dest) {
        if (dest == null) {
            return Component.empty();
        }
        if (dest.kind() == 0) {
            return Component.translatable("gui.skyabyss.travel.spawn");
        }
        if (dest.kind() == 2) {
            return Component.translatable("progress.skyabyss.titan." + dest.name());
        }
        if (!dest.name().isEmpty()) {
            return Component.literal(dest.name());
        }
        return waymarkName(dest.biome());
    }

    public static Component waymarkName(String biome) {
        if (biome.isEmpty()) {
            return Component.translatable("gui.skyabyss.travel.waymark_unnamed");
        }
        return Component.translatable("gui.skyabyss.travel.waymark_of", Component.translatable("biome." + biome.replace(':', '.')));
    }

    // 巨神ごとの目印の色 (BoardGlow.colour と同じ)
    public static int titanColor(String titan) {
        return switch (titan) {
            case "tide_shell" -> 0x9FE8E0;
            case "thunder_wing" -> 0xFFF2A0;
            case "ember" -> 0xFF9A3C;
            default -> 0x7FF4FF;
        };
    }
}
