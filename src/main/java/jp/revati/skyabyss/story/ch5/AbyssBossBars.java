package jp.revati.skyabyss.story.ch5;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;

// 深淵の主たちの段のあるボスバー (2026-10-23 の試遊「5章の中ボス系全然作りこめてない。攻撃やボスバーも」)。
// 段と咆哮はバニラのボスバーの色と刻みで送る (パケットを足さない): 色 RED = 一段目、PURPLE = 二段目、WHITE = 三段目。刻み NOTCHED_20 = 咆哮中 (段の変わり目・名乗り)。
// 描くのは story/ch5/client/AbyssBossBarClient (PhasedBossBar の枠と顔の印。名前の翻訳キーで型を決める)
public final class AbyssBossBars {
    static final BossEvent.BossBarColor[] COLORS = {BossEvent.BossBarColor.RED, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarColor.WHITE};

    private AbyssBossBars() {
    }

    public static void phase(ServerBossEvent bar, int phase) {
        BossEvent.BossBarColor c = COLORS[Math.max(0, Math.min(COLORS.length - 1, phase))];
        if (bar.getColor() != c) {
            bar.setColor(c);
        }
    }

    public static void roar(ServerBossEvent bar, boolean on) {
        BossEvent.BossBarOverlay o = on ? BossEvent.BossBarOverlay.NOTCHED_20 : BossEvent.BossBarOverlay.NOTCHED_10;
        if (bar.getOverlay() != o) {
            bar.setOverlay(o);
        }
    }

    public static int phaseOf(BossEvent bar) {
        return switch (bar.getColor()) {
            case PURPLE -> 1;
            case WHITE -> 2;
            default -> 0;
        };
    }

    public static boolean roaring(BossEvent bar) {
        return bar.getOverlay() == BossEvent.BossBarOverlay.NOTCHED_20;
    }
}
