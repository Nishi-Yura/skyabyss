package jp.revati.skyabyss.client.trial;

import javax.annotation.Nullable;

import jp.revati.skyabyss.trial.raid.RaidHudNet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// レイドの部屋の目的の帯 (docs/design/32 の 7 章)。サーバーが 10 tick ごとに送る中身を受けて、目的の帯 (ObjectiveStrip) の一行にする。
// 5 秒送られてこなければ消える (部屋を出た・切断)
public final class RaidHudClient {
    private RaidHudClient() {
    }

    private static final long STALE_TICKS = 100;
    private static volatile RaidHudNet.Hud hud = RaidHudNet.Hud.OFF;
    private static volatile long at;

    public static void onHud(RaidHudNet.Hud h) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            hud = h;
            at = mc.level == null ? 0 : mc.level.getGameTime();
        });
    }

    // 帯に出す一行 (レイドの部屋にいなければ null)
    @Nullable
    public static String line(Minecraft mc) {
        RaidHudNet.Hud h = hud;
        if (h.cellId().isEmpty() || mc.level == null || mc.level.getGameTime() - at > STALE_TICKS) {
            return null;
        }
        String line = text(h);
        if (h.guide() && !h.done() && mc.player != null) {
            line += "  " + arrow(mc.player.getX(), mc.player.getZ(), mc.player.getYRot(), h.gx() + 0.5, h.gz() + 0.5);
        }
        return line;
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    // 行き先の印への矢印と距離 (「↗ 12m」。自分の向きから見て)。近ければ「ここ」
    public static String arrow(double px, double pz, float yaw, double tx, double tz) {
        double dx = tx - px;
        double dz = tz - pz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.5) {
            return Component.translatable("raid.skyabyss.hud.here").getString();
        }
        // 向き: Minecraft の yaw は南が 0、西が 90。行き先の角度を同じ測り方で出し、自分の向きとの差を 8 方位にする
        double to = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = net.minecraft.util.Mth.wrapDegrees(to - yaw);
        int idx = Math.floorMod((int) Math.round(rel / 45.0), 8);
        return ARROWS[idx] + " " + Math.round(dist) + "m";
    }

    public static String text(RaidHudNet.Hud h) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(Component.translatable("trial.skyabyss.cell." + h.cellId()).getString()).append("】 ");
        if (h.done()) {
            sb.append(Component.translatable("raid.skyabyss.hud.cleared").getString());
            return sb.toString();
        }
        sb.append(Component.translatable(h.objKey(), h.args().toArray()).getString());
        if (h.max() > 0) {
            sb.append("  ").append(h.cur()).append('/').append(h.max());
        }
        if (h.secMax() > 0) {
            sb.append("  ").append(Component.translatable(h.secKey()).getString()).append(' ').append(h.sec()).append('/').append(h.secMax());
        }
        if (h.timer() >= 0) {
            int s = (h.timer() + 19) / 20;
            sb.append("  ").append(s / 60).append(':').append(String.format("%02d", s % 60));
        }
        return sb.toString();
    }
}
