package jp.revati.skyabyss.story.ch5.layers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 層の遊びの知らせ (行動バーに一言)。文言は tools/abyss/gen_abyss_layer_texts_a.py が作る
final class LayerNotes {
    private LayerNotes() {
    }

    // 印の近く (水平の距離 reach 以内) の人へ、行動バーに一言
    static void near(ServerLevel level, BlockPos at, double reach, String key) {
        if (at == null) {
            return;
        }
        for (ServerPlayer p : java.util.List.copyOf(level.players())) {
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ();
            if (p.isAlive() && dx * dx + dz * dz <= reach * reach) {
                p.displayClientMessage(Component.translatable(key), true);
            }
        }
    }
}
