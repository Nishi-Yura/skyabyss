package jp.revati.skyabyss.story.finale;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch6.MoonGate;
import jp.revati.skyabyss.story.reel.StoryReels;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 都の大鐘楼の門から雲海へ (docs/design/50 の 4.1)。MoonGate が門を触られたときに先に聞く (route)。
//   メイン「雲の上へ」(main_finale_ascend) の段 ascend 以降の人だけ。
//   初めて: 紙芝居「巨神の昇り」(finale_ascend) を見て雲海の着く所へ (合図 cloudsea_arrive)
//   二度目から: 月にも行ける人にはチャットの二択 ([月へ] / [雲海へ]。/skyabyss_gate moon|cloud。門のそばでだけ)。
//   裏ボスの頼み (founder/Founder) の段 enter 以降の人には [鐘の内へ] も (/skyabyss_gate bell)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FinaleGate {
    public static final String QUEST = "main_finale_ascend";
    public static final String REEL = "finale_ascend";
    public static final String ARRIVE_SIGNAL = "cloudsea_arrive";
    public static final String ASCENDED = "skyabyss_finale_ascended";
    static final String COMMAND = "skyabyss_gate";
    static final double NEAR = 6;

    private FinaleGate() {
    }

    // 雲海へ行けるか (「雲の上へ」の段 ascend 以降・済み)
    public static boolean open(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        if (e == null) {
            return false;
        }
        if (e.done) {
            return true;
        }
        QuestDef def = QuestManager.quest(QUEST);
        if (def == null) {
            return false;
        }
        for (int i = 0; i < def.stages().size(); i++) {
            if (def.stages().get(i).id().equals("ascend")) {
                return e.stage >= i;
            }
        }
        return false;
    }

    // 門を触られた。雲海の側で扱ったら true (MoonGate は何もしない)
    public static boolean route(ServerPlayer p, BlockPos gate) {
        if (!open(p)) {
            return false;
        }
        if (!p.getPersistentData().getBoolean(ASCENDED)) {
            p.getPersistentData().putBoolean(ASCENDED, true);
            // 紙芝居を見終える (飛ばす) と着く所へ (StoryReels.onDone)
            StoryReels.play(p, REEL);
            return true;
        }
        if (MoonGate.refusal(p) == null) {
            Notice.narrate(p, "message.skyabyss.gate.choose");
            Component moon = Component.translatable("message.skyabyss.gate.choose.moon")
                    .withStyle(Style.EMPTY.withColor(0x9CB4F0).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + " moon")));
            Component cloud = Component.translatable("message.skyabyss.gate.choose.cloud")
                    .withStyle(Style.EMPTY.withColor(0xF2D78A).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + " cloud")));
            Component line = Component.empty().append(moon).append("  ").append(cloud);
            if (jp.revati.skyabyss.story.finale.founder.Founder.available(p)) {
                line = line.copy().append("  ").append(jp.revati.skyabyss.story.finale.founder.Founder.gateOption(COMMAND));
            }
            p.sendSystemMessage(line);
            return true;
        }
        go(p);
        return true;
    }

    // 雲海の着く所へ (札と合図)
    public static void go(ServerPlayer p) {
        if (p.isAlive() && !p.hasDisconnected() && FinaleWorld.enter(p)) {
            Quests.signal(p, ARRIVE_SIGNAL);
            Notice.place(p, Component.translatable("message.skyabyss.cloudsea.arrive.title"), Component.translatable("message.skyabyss.cloudsea.arrive.sub"));
        }
    }

    static {
        StoryReels.onDone(REEL, FinaleGate::go);
    }

    static boolean nearGate(ServerPlayer p) {
        BlockPos g = MoonGate.gatePos(p.server);
        return g != null && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                && p.distanceToSqr(g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5) <= NEAR * NEAR;
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND)
                .then(Commands.literal("moon").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (nearGate(p) && MoonGate.refusal(p) == null) {
                        MoonGate.open(p, MoonGate.gatePos(p.server));
                    }
                    return 1;
                }))
                .then(Commands.literal("bell").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (nearGate(p) && jp.revati.skyabyss.story.finale.founder.Founder.available(p)) {
                        jp.revati.skyabyss.story.finale.founder.Founder.enterFromGate(p, MoonGate.gatePos(p.server));
                    }
                    return 1;
                }))
                .then(Commands.literal("cloud").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (nearGate(p) && open(p)) {
                        go(p);
                    }
                    return 1;
                })));
    }
}
