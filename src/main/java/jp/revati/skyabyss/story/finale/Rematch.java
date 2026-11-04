package jp.revati.skyabyss.story.finale;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch6.MoonTrial;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 主との再戦の暦 (2026-11-05 の削る・まとめる頁 ct_rematch = hub)。大鐘楼の門の行き先に［試し］を足し、
// 位相の挑戦 (月の主 5。段 1〜5)・巡礼の試し (巡礼者。段 1〜5)・初代の空見を一つの暦から選ぶ。選ぶとその戦いの場へ直に渡る
// (月は主の間の入口・雲海は巡礼者の座の雲の暦のそば・鐘の内)。月の暦と雲の暦はそのまま残す。
// 押しどころは /skyabyss_gate trial …。門のそばでだけ (FinaleGate.nearGate)
public final class Rematch {
    private Rematch() {
    }

    // 暦に何か出るか
    public static boolean available(ServerPlayer p) {
        return MoonTrial.open(p) || PilgrimTrial.open(p) || jp.revati.skyabyss.story.finale.founder.Founder.available(p);
    }

    // 門の選びの［試し］
    public static Component gateOption(String command) {
        return Component.translatable("message.skyabyss.gate.choose.trial")
                .withStyle(Style.EMPTY.withColor(0xC9A0E8).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command + " trial")));
    }

    // 暦: 挑めるものを一行ずつ
    public static void offer(ServerPlayer p, String command) {
        Notice.narrate(p, "message.skyabyss.rematch.head");
        if (MoonTrial.open(p)) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.rematch.moon").withStyle(st -> st.withColor(0x9CB4F0)));
            for (String lord : MoonTrial.LORDS) {
                int t = MoonTrial.next(p, lord);
                p.sendSystemMessage(Component.literal("  ").append(Component.translatable("message.skyabyss.rematch.pick", Component.translatable("moon_trial.skyabyss." + lord), t)
                        .withStyle(Style.EMPTY.withColor(0x9CB4F0).withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command + " trial moon " + lord))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.skyabyss.rematch.best", MoonTrial.best(p, lord)))))));
            }
        }
        if (PilgrimTrial.open(p)) {
            int t = PilgrimTrial.next(p);
            p.sendSystemMessage(Component.translatable("message.skyabyss.rematch.pilgrim").withStyle(s -> s.withColor(0xF2D78A)).append(" ")
                    .append(Component.translatable("message.skyabyss.rematch.pick", Component.translatable("message.skyabyss.rematch.pilgrim_name"), t)
                            .withStyle(Style.EMPTY.withColor(0xF2D78A).withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command + " trial pilgrim"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.skyabyss.rematch.best", PilgrimTrial.cleared(p)))))));
        }
        if (jp.revati.skyabyss.story.finale.founder.Founder.available(p)) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.rematch.founder").withStyle(s -> s.withColor(0xD8C08A)).append(" ")
                    .append(jp.revati.skyabyss.story.finale.founder.Founder.gateOption(command)));
        }
        if (!available(p)) {
            Notice.hint(p, "message.skyabyss.rematch.none");
        }
    }

    // 月の主へ (主の間の入口。段は次の段)
    public static boolean moon(ServerPlayer p, String lord) {
        if (!MoonTrial.open(p) || !MoonTrial.LORDS.contains(lord)) {
            return false;
        }
        MoonWorld.enter(p);
        return MoonTrial.start(p, lord, MoonTrial.next(p, lord)) != null;
    }

    // 巡礼者へ (雲海の座の雲の暦のそばへ渡り、次の段の試しを始める)
    public static boolean pilgrim(ServerPlayer p) {
        if (!PilgrimTrial.open(p) || !FinaleWorld.enter(p)) {
            return false;
        }
        ServerLevel level = p.serverLevel();
        BlockPos c = PilgrimSeat.mark("calendar");
        level.getChunk(c);
        p.teleportTo(level, c.getX() + 0.5, c.getY() + 1, c.getZ() + 2.5, 180f, 0f);
        return PilgrimTrial.begin(p);
    }
}
