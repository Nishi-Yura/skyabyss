package jp.revati.skyabyss.town;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 町のクエストの開発用コマンド (OP)。
//   /skyabyss town list                 町のクエストの一覧 (自分の進み・受けられるか) と、限定の人が立つ印
//   /skyabyss town rumor                宿の女将の噂を聞く (町のクエストが始まる)
//   /skyabyss town start <クエスト>     町のクエストを始める (章を問わない)
//   /skyabyss town place                限定の人の見回りを今する (立たせる・消す)
//   /skyabyss town bell                 大鐘楼の頂の鐘を鳴らしたことにする (塔に登れないうちの試し)
//   /skyabyss town well [reset]         古井戸の下の宝を受け取る (reset で受け取っていないことに)
//   /skyabyss town cellar open|close    酒場の地下の奥の壁を開ける / 閉じたことにする
//   /skyabyss town mapmaker [draw]      地図屋の噂 (draw で描き足し)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TownCommands {
    private TownCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("town")
                        .then(Commands.literal("list").executes(c -> list(c.getSource(), c.getSource().getPlayerOrException())))
                        .then(Commands.literal("rumor").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Notice.narrate(p, "message.skyabyss.inn.rumor_head", jp.revati.skyabyss.facility.FacilityActions.rumorFor(p));
                            return 1;
                        }))
                        .then(Commands.literal("start").then(Commands.argument("quest", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(TownQuests.ALL.stream().map(TownQuests.TownQuest::id), b))
                                .executes(c -> Quests.start(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "quest")) ? 1 : 0)))
                        .then(Commands.literal("place").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            BlockPos cap = TownGuests.capital(p.server);
                            int n = cap == null ? 0 : TownGuests.keep(p.serverLevel(), cap, TownGuests.inTown(p.serverLevel(), cap));
                            c.getSource().sendSuccess(() -> Component.literal("town guests: " + n), false);
                            return n;
                        }))
                        .then(Commands.literal("bell").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Quests.signalWithParty(p.serverLevel(), java.util.List.of(p), TownQuests.SIGNAL_TOWER_BELL);
                            return 1;
                        }))
                        .then(Commands.literal("well").executes(c -> TownQuests.claimWell(c.getSource().getPlayerOrException()) ? 1 : 0)
                                .then(Commands.literal("reset").executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    TownData d = TownData.get(p.server);
                                    d.wellTaken.remove(p.getUUID());
                                    d.setDirty();
                                    return 1;
                                })))
                        .then(Commands.literal("cellar")
                                .then(Commands.literal("open").executes(c -> TownCellar.open(c.getSource().getServer()) ? 1 : 0))
                                .then(Commands.literal("close").executes(c -> {
                                    TownCellar.close(c.getSource().getServer());
                                    return 1;
                                })))
                        .then(Commands.literal("mapmaker").executes(c -> {
                            TownActions.mapmakerRumor(c.getSource().getPlayerOrException());
                            return 1;
                        }).then(Commands.literal("draw").executes(c -> TownActions.mapmakerDraw(c.getSource().getPlayerOrException()) ? 1 : 0)))));
    }

    static int list(CommandSourceStack source, ServerPlayer p) {
        int ch = Coins.chapter(p);
        QuestLog log = QuestLog.of(p);
        source.sendSuccess(() -> Component.literal("town quests (chapter " + ch + ")").withStyle(ChatFormatting.AQUA), false);
        for (TownQuests.TownQuest q : TownQuests.ALL) {
            QuestLog.Entry e = log.entry(q.id());
            String state = e == null ? (TownQuests.available(q, ch, log) ? "available" : "-") : e.done ? "done" : "stage " + TownGuests.stageOf(p, q.id());
            TownGuests.Guest g = TownGuests.byQuest(q.id());
            BlockPos at = g == null ? null : TownGuests.mark(p.server, g.mark());
            String where = g == null ? "" : "  guest " + g.id() + " @ " + g.mark() + (at == null ? " (no mark)" : " " + at.toShortString());
            source.sendSuccess(() -> Component.literal("  " + q.id() + " [ch" + q.chapter() + "] " + state + where), false);
        }
        source.sendSuccess(() -> Component.literal("  well taken: " + TownData.get(p.server).wellTaken.contains(p.getUUID())
                + ", cellar open: " + TownCellar.isOpen(p.server)), false);
        return 1;
    }
}
