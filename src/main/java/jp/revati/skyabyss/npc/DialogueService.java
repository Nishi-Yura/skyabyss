package jp.revati.skyabyss.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 会話の進行はすべてサーバーが決める。参加者の画面は今の節を映し、選んだ番号を返すだけ。
// 一人に一つの会話 (Session)。同じ NPC と二人が同時に話してもよい (それぞれの記録で分岐する)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DialogueService {
    // これより離れたら会話を閉じる
    public static final double MAX_DISTANCE = 8.0;
    // 選択肢の番号の特別な値: 続き (next) / 立ち去る (選択肢も続きもない節)
    public static final int CONTINUE = -1;
    public static final int LEAVE = -2;

    record Session(int npcId, String role, String node, List<Integer> shown) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DialogueService() {
    }

    // 話しかけた
    public static void open(ServerPlayer player, NpcEntity npc) {
        String role = npc.role();
        Dialogue dialogue = DialogueManager.dialogue(role);
        if (dialogue == null) {
            SkyAbyss.LOGGER.warn("[dialogue] no dialogue for {}", role);
            return;
        }
        StoryContext ctx = new StoryContext(player, role, npc);
        Dialogue.Node node = dialogue.entryNode(ctx);
        if (node == null) {
            return;
        }
        // 話した記録: 一日の最初の会話で好感度が少し上がる (二回目に会う日から)。「会った」の印はこの節を選んだあとで立てる
        NpcBonds.Bond bond = NpcBonds.of(player).bond(role);
        long day = player.server.overworld().getGameTime() / 24000L;
        if (bond.lastDay != day) {
            if (bond.talks > 0) {
                bond.affinity = Math.min(Affinity.MAX, bond.affinity + Affinity.DAILY_TALK);
            }
            bond.lastDay = day;
        }
        bond.talks++;
        bond.flags.add("met");
        NpcBonds.sync(player);
        npc.startTalking(player);
        enter(player, npc, dialogue, node);
    }

    // 画面で選んだ。node は画面が映している節 (食い違えば古い画面からの選択なので捨てる)
    public static void choose(ServerPlayer player, String nodeId, int index) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !s.node().equals(nodeId)) {
            return;
        }
        NpcEntity npc = npc(player, s);
        Dialogue dialogue = DialogueManager.dialogue(s.role());
        if (npc == null || dialogue == null) {
            close(player);
            return;
        }
        Dialogue.Node node = dialogue.nodes().get(s.node());
        if (node == null) {
            close(player);
            return;
        }
        StoryContext ctx = new StoryContext(player, s.role(), npc);
        if (index == CONTINUE) {
            go(player, npc, dialogue, ctx, node.next() == null ? Dialogue.END : node.next());
            return;
        }
        if (index == LEAVE) {
            close(player);
            return;
        }
        if (!s.shown().contains(index) || index < 0 || index >= node.options().size()) {
            return;
        }
        Dialogue.Option option = node.options().get(index);
        // 画面を開いたあとで状態が変わった (持ち物を捨てた等) なら、選べない
        if (!option.condition().test(ctx)) {
            enter(player, npc, dialogue, node, false);
            return;
        }
        option.actions().forEach(a -> a.run(ctx));
        go(player, npc, dialogue, ctx, option.target());
    }

    private static void go(ServerPlayer player, NpcEntity npc, Dialogue dialogue, StoryContext ctx, String target) {
        if (target.equals(Dialogue.END)) {
            close(player);
            return;
        }
        Dialogue.Node next = target.equals(Dialogue.ENTRY) ? dialogue.entryNode(ctx) : dialogue.nodes().get(target);
        if (next == null) {
            close(player);
            return;
        }
        enter(player, npc, dialogue, next);
    }

    static void enter(ServerPlayer player, NpcEntity npc, Dialogue dialogue, Dialogue.Node node) {
        enter(player, npc, dialogue, node, true);
    }

    // 節に入る: 結果を起こし、「話した」をクエストへ知らせ、見せる選択肢を選んで画面へ送る
    private static void enter(ServerPlayer player, NpcEntity npc, Dialogue dialogue, Dialogue.Node node, boolean fresh) {
        StoryContext ctx = new StoryContext(player, dialogue.role(), npc);
        if (fresh) {
            node.actions().forEach(a -> a.run(ctx));
            Quests.onTalk(player, dialogue.role(), node.id());
        }
        List<Integer> shown = new ArrayList<>();
        List<NpcNetwork.OptionView> views = new ArrayList<>();
        for (int i = 0; i < node.options().size(); i++) {
            Dialogue.Option op = node.options().get(i);
            if (op.condition().test(ctx)) {
                shown.add(i);
                views.add(new NpcNetwork.OptionView(i, op.textKey(), op.kind()));
            }
        }
        SESSIONS.put(player.getUUID(), new Session(npc.getId(), dialogue.role(), node.id(), List.copyOf(shown)));
        send(player, new NpcNetwork.Show(npc.getId(), dialogue.role(), node.id(), node.face(), node.textKey(), views, node.next() != null));
    }

    public static void close(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) {
            NpcEntity npc = npcById(player, s.npcId());
            if (npc != null) {
                npc.stopTalking(player);
            }
            send(player, new NpcNetwork.Close());
        }
    }

    // 参加者が画面を閉じた (Esc)
    static void onClientClosed(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) {
            NpcEntity npc = npcById(player, s.npcId());
            if (npc != null) {
                npc.stopTalking(player);
            }
        }
    }

    // 自己テスト用: 今の会話の節
    @Nullable
    public static String currentNode(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.node();
    }

    @Nullable
    public static List<Integer> shownOptions(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.shown();
    }

    @Nullable
    private static NpcEntity npc(ServerPlayer player, Session s) {
        NpcEntity npc = npcById(player, s.npcId());
        if (npc == null || !npc.isAlive() || npc.distanceToSqr(player) > MAX_DISTANCE * MAX_DISTANCE) {
            return null;
        }
        return npc;
    }

    @Nullable
    private static NpcEntity npcById(ServerPlayer player, int id) {
        Entity e = player.level().getEntity(id);
        return e instanceof NpcEntity n ? n : null;
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }

    // 離れた・相手がいなくなった・倒れた・ディメンションを移った: 閉じる
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s != null && (!player.isAlive() || npc(player, s) == null)) {
            close(player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            onClientClosed(player);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    static void forgetForTest(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }
}
