package jp.revati.skyabyss.story.finale.founder;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.finale.FinaleEnding;
import jp.revati.skyabyss.story.finale.FinaleWorld;
import jp.revati.skyabyss.story.finale.mob.FounderBoss;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 裏ボス「初代の空見」の進みと入口と報酬 (docs/design/53)。
//   頼み    side_founder (寄り道・終章)。三つの結末を見終えると始まる (条件 endings_seen 3)。
//           段: verse (語り部にやっつめを聞く) → enter (鐘の内へ入る。合図 founder_enter) → founder (始祖を退ける。合図 founder_down) → tell (語り部に話す)
//   入口    都の大鐘楼の門 (月への門) に触れると、行き先の選びに [鐘の内へ] が出る (FinaleGate)。/skyabyss_gate bell。門のそば 8 にいる組の人も一緒に入る
//   報酬    初めて退けた人に、称号「やっつめを聞いた者」(Rebirths の称号)・空見の肩掛け・始祖の小鐘・伝承の頁 2 枚 (finale/founder・finale/eighth_verse)。
//           挑み直すたびに最速の時間 (人のデータ BEST) を残す。強さは増やさない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Founder {
    public static final String QUEST = "side_founder";
    public static final String ENTER_SIGNAL = "founder_enter";
    public static final String DOWN_SIGNAL = "founder_down";
    public static final String KEY = "skyabyss_founder";
    public static final String BEST = "skyabyss_founder_best";
    public static final String LORE_FOUNDER = "finale/founder";
    public static final String LORE_VERSE = "finale/eighth_verse";
    static final double PARTY_NEAR = 8;

    private Founder() {
    }

    // 鐘の内へ入れるか (頼みの段 enter 以降・済み)
    public static boolean available(ServerPlayer p) {
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
            if (def.stages().get(i).id().equals("enter")) {
                return e.stage >= i;
            }
        }
        return false;
    }

    public static boolean beaten(ServerPlayer p) {
        return p.getPersistentData().getBoolean(KEY);
    }

    // 最速 (tick。まだなら -1)
    public static long best(ServerPlayer p) {
        return p.getPersistentData().contains(BEST) ? p.getPersistentData().getLong(BEST) : -1;
    }

    // 門の選びの [鐘の内へ] (入れる人にだけ)
    public static Component gateOption(String command) {
        return Component.translatable("message.skyabyss.gate.choose.bell")
                .withStyle(Style.EMPTY.withColor(0xD8C08A).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command + " bell")));
    }

    // 門から鐘の内へ。門のそばにいる同じ組の人 (入れる人) も一緒に
    public static void enterFromGate(ServerPlayer p, BlockPos gate) {
        go(p);
        PartyData.Party party = Parties.of(p);
        if (party == null) {
            return;
        }
        for (java.util.UUID id : party.members) {
            ServerPlayer m = p.server.getPlayerList().getPlayer(id);
            if (m != null && m != p && m.isAlive() && m.level().dimension() == p.serverLevel().dimension() && gate != null
                    && m.distanceToSqr(gate.getX() + 0.5, gate.getY() + 0.5, gate.getZ() + 0.5) <= PARTY_NEAR * PARTY_NEAR && available(m)) {
                go(m);
            }
        }
    }

    public static boolean go(ServerPlayer p) {
        ServerLevel level = p.server.getLevel(FinaleWorld.LEVEL);
        if (level == null || !p.isAlive() || p.hasDisconnected()) {
            return false;
        }
        FinaleWorld.build(level);
        BlockPos at = FounderArena.mark("arrive");
        level.getChunk(at);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
        p.resetFallDistance();
        Quests.signal(p, ENTER_SIGNAL);
        FounderArena.arrive(level, p);
        return true;
    }

    // 始祖がやっつめを歌い終えた (FounderArena.defeated)
    static void onDefeat(ServerLevel level, List<ServerPlayer> here, long ticks) {
        Quests.signalWithParty(level, here, DOWN_SIGNAL);
        for (ServerPlayer p : here) {
            if (!beaten(p)) {
                p.getPersistentData().putBoolean(KEY, true);
                Quests.give(p, new ItemStack(FounderItems.SHAWL_ITEM.get()));
                Quests.give(p, new ItemStack(FounderItems.HANDBELL.get()));
                Lore.unlock(p, LORE_FOUNDER, true);
                Lore.unlock(p, LORE_VERSE, true);
                Notice.place(p, Component.translatable("message.skyabyss.founder.won.title"), Component.translatable("message.skyabyss.founder.won.sub"));
                jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
            } else {
                Notice.place(p, Component.translatable("message.skyabyss.founder.again.title"), Component.empty());
            }
            if (ticks > 0) {
                long old = best(p);
                if (old < 0 || ticks < old) {
                    p.getPersistentData().putLong(BEST, ticks);
                    Notice.gain(p, "message.skyabyss.founder.best", time(ticks));
                } else {
                    Notice.gain(p, "message.skyabyss.founder.time", time(ticks), time(old));
                }
            }
        }
    }

    public static String time(long ticks) {
        long s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // 開発用: /skyabyss founder enter | summon | phase <1..3> | verse <1..7> | win | seen | wear | reset (権限 2)
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("founder")
                        .then(Commands.literal("enter").executes(c -> go(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("summon").executes(c -> FounderArena.summon(c.getSource().getServer().getLevel(FinaleWorld.LEVEL)) != null ? 1 : 0))
                        .then(Commands.literal("phase").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(c -> {
                            FounderBoss b = FounderArena.real(c.getSource().getServer().getLevel(FinaleWorld.LEVEL));
                            if (b != null) {
                                b.devPhase(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n"));
                            }
                            return b != null ? 1 : 0;
                        })))
                        .then(Commands.literal("verse").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 7)).executes(c -> {
                            FounderBoss b = FounderArena.real(c.getSource().getServer().getLevel(FinaleWorld.LEVEL));
                            if (b != null) {
                                b.devVerse(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n"));
                            }
                            return b != null ? 1 : 0;
                        })))
                        .then(Commands.literal("win").executes(c -> {
                            ServerLevel l = c.getSource().getServer().getLevel(FinaleWorld.LEVEL);
                            FounderBoss b = FounderArena.real(l);
                            if (b == null) {
                                return 0;
                            }
                            b.devFinish();
                            return 1;
                        }))
                        .then(Commands.literal("seen").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            p.getPersistentData().putString(FinaleEnding.SEEN, String.join(",", FinaleEnding.ENDINGS));
                            return 1;
                        }))
                        .then(Commands.literal("wear").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            FounderItems.setWearing(p, !FounderItems.wearing(p));
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            p.getPersistentData().remove(KEY);
                            p.getPersistentData().remove(BEST);
                            FounderItems.setWearing(p, false);
                            return 1;
                        }))));
    }
}
