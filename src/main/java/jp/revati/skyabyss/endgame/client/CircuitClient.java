package jp.revati.skyabyss.endgame.client;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.endgame.CircuitPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

// 巡り路 (endgame/Circuits) の手元の表示: 目的の帯 (client/quest/ObjectiveStrip) に「巡り路 12:34・印 3：〜」と行き先を出す。
// 秒はサーバーから毎秒届く。届かなくなったら (3 秒) 消す
public final class CircuitClient {
    private static volatile CircuitPayload last;
    private static volatile long at;

    private CircuitClient() {
    }

    public static void receive(CircuitPayload payload) {
        last = payload.active() ? payload : null;
        at = System.currentTimeMillis();
    }

    @Nullable
    static CircuitPayload now() {
        CircuitPayload p = last;
        if (p == null || System.currentTimeMillis() - at > 3000 || Minecraft.getInstance().player == null) {
            return null;
        }
        return p;
    }

    @Nullable
    public static String objective() {
        CircuitPayload p = now();
        if (p == null) {
            return null;
        }
        String time = String.format("%d:%02d", p.seconds() / 60, p.seconds() % 60);
        return Component.translatable("circuit.skyabyss.line", time, p.marks(), p.task()).getString();
    }

    @Nullable
    public static BlockPos target() {
        CircuitPayload p = now();
        return p == null || !p.hasTarget() ? null : p.target();
    }
}
