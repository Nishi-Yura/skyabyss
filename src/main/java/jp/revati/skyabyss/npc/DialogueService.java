package jp.revati.skyabyss.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

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

    record Session(int npcId, String role, String node, List<Integer> shown, List<String> kinds) {
    }

    private static final Map<UUID, Session> SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();

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
        // 見回りは 1 秒おき。段が進んだ直後に話しかけても、その人の今の記録で話題を決める (他の人の見回りには依らない)。
        // 新しい頼みの自動開始は見回りに任せる (会話の入口を、話しかけた瞬間に始まった頼みが横取りしないように)
        Quests.refresh(player, false);
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
        bond.seen.add(node.id());
        NpcBonds.sync(player);
        // ほかの NPC と話している途中なら、そちらは終える (その NPC が話し相手を待ち続けないように)
        Session old = SESSIONS.get(player.getUUID());
        if (old != null && old.npcId() != npc.getId()) {
            NpcEntity previous = npcById(player, old.npcId());
            if (previous != null) {
                previous.stopTalking(player);
            }
        }
        enter(player, npc, dialogue, node);
    }

    // 画面で選んだ。node は画面が映している節 (食い違えば古い画面からの選択なので捨てる)
    public static void choose(ServerPlayer player, String nodeId, int index) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            // 会話はもう終わっている (離れた等): 画面を閉じさせる
            send(player, new NpcNetwork.Close());
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
        if (!s.node().equals(nodeId)) {
            // 古い画面からの選択: 今の節を映し直す (画面が返事を待ったまま止まらないように)
            enter(player, npc, dialogue, node, false);
            return;
        }
        if (index == CONTINUE) {
            go(player, npc, dialogue, ctx, node.next() == null ? Dialogue.END : node.next());
            return;
        }
        if (index == LEAVE) {
            close(player);
            return;
        }
        if (!s.shown().contains(index) || index < 0 || index >= node.options().size()) {
            enter(player, npc, dialogue, node, false);
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
        // 節ごとに「話している」を更新する (長い会話の途中で、NPC が話し相手を忘れないように)
        npc.startTalking(player);
        if (fresh) {
            node.actions().forEach(a -> a.run(ctx));
            Quests.onTalk(player, dialogue.role(), node.id());
        }
        List<Integer> shown = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        List<NpcNetwork.OptionView> views = new ArrayList<>();
        visibleOptions(dialogue, node, ctx, shown, kinds);
        for (int k = 0; k < shown.size(); k++) {
            Dialogue.Option op = node.options().get(shown.get(k));
            views.add(new NpcNetwork.OptionView(shown.get(k), op.textKey(), kinds.get(k)));
        }
        SESSIONS.put(player.getUUID(), new Session(npc.getId(), dialogue.role(), node.id(), List.copyOf(shown), List.copyOf(kinds)));
        send(player, new NpcNetwork.Show(npc.getId(), dialogue.role(), node.id(), node.face(), node.textKey(), views, node.next() != null));
    }


    // 見せる選択肢とその印を決める。「ほかに頼みは」のように、先の節がまた選択肢の束 (ハブ) である選択肢は、
    // 先で実際に選べるものから決める: 先に (立ち去る以外の) 選択肢が一つもなければ隠し、
    // 新しい頼み・渡す品 (kind が quest/give で、その場で終わらない選択肢) がなければ、印は普通の話題にする
    static void visibleOptions(Dialogue dialogue, Dialogue.Node node, StoryContext ctx, List<Integer> shown, List<String> kinds) {
        for (int i = 0; i < node.options().size(); i++) {
            Dialogue.Option op = node.options().get(i);
            if (!op.condition().test(ctx)) {
                continue;
            }
            String kind = op.kind();
            Dialogue.Node target = dialogue.nodes().get(op.target());
            if (target != null && !target.options().isEmpty() && !op.kind().equals("leave")) {
                int available = 0;
                int fresh = 0;
                for (Dialogue.Option t : target.options()) {
                    if (!t.condition().test(ctx) || t.kind().equals("leave")) {
                        continue;
                    }
                    available++;
                    if ((t.kind().equals("quest") || t.kind().equals("give")) && !t.target().equals(Dialogue.END)
                            && !t.target().equals(Dialogue.ENTRY) && offers(dialogue, t, ctx)) {
                        fresh++;
                    }
                }
                if (available == 0) {
                    continue;
                }
                if (fresh == 0 && (kind.equals("quest") || kind.equals("give"))) {
                    kind = "talk";
                }
            }
            shown.add(i);
            kinds.add(kind);
        }
    }

    // この選択肢の先 (続き・選択肢をたどって) に、実際にクエストを始める・品を渡す・品を納める結果があるか。
    // 先がただの話 (頼みのように見えて何も起きない) なら、金の印の理由にしない (「頼み事はあるかい」がいつも金色になっていた)
    static boolean offers(Dialogue dialogue, Dialogue.Option option, StoryContext ctx) {
        if (hasOfferAction(option.actions())) {
            return true;
        }
        return reaches(dialogue, option.target(), ctx, new java.util.HashSet<>(), 0);
    }

    private static boolean hasOfferAction(List<jp.revati.skyabyss.quest.Action> actions) {
        for (jp.revati.skyabyss.quest.Action a : actions) {
            String type = a.type();
            if (type.equals("quest_start") || type.equals("give") || type.equals("take") || type.equals("open_board")) {
                return true;
            }
        }
        return false;
    }

    private static boolean reaches(Dialogue dialogue, String id, StoryContext ctx, java.util.Set<String> seen, int depth) {
        Dialogue.Node node = dialogue.nodes().get(id);
        if (node == null || depth > 6 || !seen.add(id)) {
            return false;
        }
        if (hasOfferAction(node.actions())) {
            return true;
        }
        if (node.next() != null && reaches(dialogue, node.next(), ctx, seen, depth + 1)) {
            return true;
        }
        for (Dialogue.Option o : node.options()) {
            if (!o.condition().test(ctx) || o.kind().equals("leave") || o.target().equals(Dialogue.ENTRY)) {
                continue;
            }
            if (hasOfferAction(o.actions()) || reaches(dialogue, o.target(), ctx, seen, depth + 1)) {
                return true;
            }
        }
        return false;
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

    // 自己テスト用: 今見せている選択肢の印 (shownOptions と同じ並び)
    @Nullable
    public static List<String> shownKinds(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.kinds();
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
            return;
        }
        if (s != null) {
            refresh(player, s);
        }
    }

    // 話している間に状態が変わった (空の影を見た・頁を読んだ・品を得た、など) ら、今の節の選択肢を映し直す。
    // 変わる前の選択肢のまま話を続けて、新しく開いた話題 (影を見たあとの「あの影のこと」など) を取りこぼさないように
    static boolean refresh(ServerPlayer player, Session s) {
        NpcEntity npc = npc(player, s);
        Dialogue dialogue = DialogueManager.dialogue(s.role());
        Dialogue.Node node = dialogue == null ? null : dialogue.nodes().get(s.node());
        if (npc == null || node == null || node.options().isEmpty()) {
            return false;
        }
        StoryContext ctx = new StoryContext(player, s.role(), npc);
        List<Integer> now = new ArrayList<>();
        List<String> nowKinds = new ArrayList<>();
        visibleOptions(dialogue, node, ctx, now, nowKinds);
        if (now.equals(s.shown()) && nowKinds.equals(s.kinds())) {
            return false;
        }
        enter(player, npc, dialogue, node, false);
        return true;
    }

    // 自己テスト用: いま (時間を進めずに) 見回りを一度行う
    public static boolean refreshForTest(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && refresh(player, s);
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
