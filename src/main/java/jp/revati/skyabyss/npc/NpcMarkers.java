package jp.revati.skyabyss.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.npc.NpcMarkerClient;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestItems;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// NPC の頭の上の印 (docs/design/18 の 3.5 節、07 の 11 章)。話しかけると何かが起きる NPC にだけ、その人にだけ見える印を出す。
// 計算は人ごと (サーバー): 会話の「入口の節」と、ハブの選択肢の印 (DialogueService.visibleOptions) と、そのクエストの今の段の目的から決める。
// 話しても何も起きない (入口が定型のあいさつだけ) なら印なし。クライアントは受け取った印を頭の上に描くだけ。
//   NEW_MAIN   金の「!」  メインの次の段が、この人に話すこと / この人からの新しい頼み (ハブで金の印になるもの)
//   NEW_SIDE   青緑の「!」 サブの次の段が、この人に話すこと / この人からの新しい頼み・渡す品
//   READY_MAIN 金の「?」  メインの最後の段 (報告) が、この人に話すこと / 納める品がそろっている
//   READY_SIDE 青緑の「?」 サブの同じ
//   CHAT       小さな点   まだ聞いていない話がある (入口が定型でなく、まだその節に入ったことがない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NpcMarkers {
    private static final String PROTOCOL_VERSION = "npcmark-1";
    // これより遠い NPC には送らない (クライアントの描画の上限は 64 ブロック)
    static final double RANGE = 64.0;

    public enum Marker {
        NONE,
        NEW_MAIN,
        NEW_SIDE,
        READY_MAIN,
        READY_SIDE,
        CHAT;

        static Marker byId(int id) {
            Marker[] all = values();
            return id >= 0 && id < all.length ? all[id] : NONE;
        }

        public boolean main() {
            return this == NEW_MAIN || this == READY_MAIN;
        }

        public boolean ready() {
            return this == READY_MAIN || this == READY_SIDE;
        }
    }

    // 人ごとに最後に送った印 (NPC の entity id → 印)。変わったときだけ送る
    private static final Map<UUID, Map<Integer, Integer>> SENT = new HashMap<>();

    private NpcMarkers() {
    }

    @SubscribeEvent
    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(Update.TYPE, Update.STREAM_CODEC,
                (payload, context) -> NpcMarkerClient.receive(payload.ids(), payload.markers()));
    }

    // サーバー → その人だけ: 近くの NPC の印の全部 (印のない NPC は載せない)
    public record Update(int[] ids, int[] markers) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "npc_markers"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.ids.length);
                    for (int i = 0; i < p.ids.length; i++) {
                        buf.writeVarInt(p.ids[i]);
                        buf.writeByte(p.markers[i]);
                    }
                },
                buf -> {
                    int n = buf.readVarInt();
                    int[] ids = new int[n];
                    int[] markers = new int[n];
                    for (int i = 0; i < n; i++) {
                        ids[i] = buf.readVarInt();
                        markers[i] = buf.readByte();
                    }
                    return new Update(ids, markers);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- 印の決め方
    public static Marker of(ServerPlayer player, NpcEntity npc) {
        String role = npc.role();
        Dialogue dialogue = DialogueManager.dialogue(role);
        return dialogue == null ? Marker.NONE : of(dialogue, new StoryContext(player, role, npc));
    }

    // 優先: 報告 (?) > 次の段 (!) > 新しい頼み (!) > 新しい雑談 (点)。話しても何も起きないなら NONE
    public static Marker of(Dialogue dialogue, StoryContext ctx) {
        int entryIndex = -1;
        for (int i = 0; i < dialogue.entries().size(); i++) {
            if (dialogue.entries().get(i).condition().test(ctx)) {
                entryIndex = i;
                break;
            }
        }
        if (entryIndex < 0) {
            return Marker.NONE;
        }
        Dialogue.Node node = dialogue.nodes().get(dialogue.entries().get(entryIndex).node());
        if (node == null) {
            return Marker.NONE;
        }
        String role = dialogue.role();
        ServerPlayer player = ctx.player();
        Marker step = questStep(player, role);
        // ハブの印 (会話画面と同じ決め方)。新しい頼み・渡す品があれば「!」
        List<Integer> shown = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        DialogueService.visibleOptions(dialogue, node, ctx, shown, kinds);
        boolean request = false;
        for (String kind : kinds) {
            if (kind.equals("quest") || kind.equals("give")) {
                request = true;
                break;
            }
        }
        if (step != Marker.NONE) {
            return step;
        }
        if (request) {
            // 頼みがメインの段に関わるなら金、そうでなければ青緑
            return mainRequest(player, role) ? Marker.NEW_MAIN : Marker.NEW_SIDE;
        }
        // 新しい雑談: 入口が定型 (最後の、条件のない入口) でなく、その節にまだ入ったことがない
        boolean fallback = entryIndex == dialogue.entries().size() - 1;
        if (!fallback && !NpcBonds.of(player).seen(role, node.id())) {
            return Marker.CHAT;
        }
        return Marker.NONE;
    }

    // いま進めているクエストの今の段が、この人に話す / この人に品を納めることか。
    // 最後の段 (報告) か、納める品がそろっていれば「?」、それ以外 (次の段) は「!」。メインがあればメインを優先
    static Marker questStep(ServerPlayer player, String role) {
        QuestLog log = QuestLog.of(player);
        Marker best = Marker.NONE;
        for (Map.Entry<String, QuestLog.Entry> e : log.quests().entrySet()) {
            QuestDef def = QuestManager.quest(e.getKey());
            QuestLog.Entry entry = e.getValue();
            if (def == null || entry.done || entry.stage >= def.stages().size()) {
                continue;
            }
            QuestDef.Stage stage = def.stages().get(entry.stage);
            if (Quests.satisfied(stage, entry)) {
                continue;
            }
            boolean last = entry.stage == def.stages().size() - 1;
            for (int i = 0; i < stage.objectives().size(); i++) {
                QuestDef.Objective ob = stage.objectives().get(i);
                if (entry.progress(i) >= ob.count()) {
                    continue;
                }
                Marker m = Marker.NONE;
                if (ob.type().equals("talk") && ob.targets().contains(role)
                        && !(ob.topic() != null && log.heard(def.id(), role, ob.topic()))) {
                    m = last ? (def.main() ? Marker.READY_MAIN : Marker.READY_SIDE) : (def.main() ? Marker.NEW_MAIN : Marker.NEW_SIDE);
                } else if (ob.type().equals("turn_in") && role.equals(def.giver())
                        && QuestItems.count(player, ob.targets().get(0), ob.token()) >= ob.count()) {
                    m = def.main() ? Marker.READY_MAIN : Marker.READY_SIDE;
                }
                if (rank(m) > rank(best)) {
                    best = m;
                }
            }
        }
        return best;
    }

    private static int rank(Marker m) {
        return switch (m) {
            case READY_MAIN -> 5;
            case READY_SIDE -> 4;
            case NEW_MAIN -> 3;
            case NEW_SIDE -> 2;
            default -> 0;
        };
    }

    // この人が依頼主のメインのクエストが、まだ始まっていない / この人に話す段にあるか (頼みの印を金にする)
    private static boolean mainRequest(ServerPlayer player, String role) {
        QuestLog log = QuestLog.of(player);
        for (QuestDef def : QuestManager.all()) {
            if (def.main() && role.equals(def.giver()) && log.entry(def.id()) == null) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 送る (1 秒ごと)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.connection == null
                || player.tickCount % 20 != 13) {
            return;
        }
        List<NpcEntity> near = player.level().getEntitiesOfClass(NpcEntity.class,
                new AABB(player.blockPosition()).inflate(RANGE));
        Map<Integer, Integer> now = new HashMap<>();
        for (NpcEntity npc : near) {
            Marker m = of(player, npc);
            if (m != Marker.NONE) {
                now.put(npc.getId(), m.ordinal());
            }
        }
        Map<Integer, Integer> before = SENT.put(player.getUUID(), now);
        if (before == null ? !now.isEmpty() : !before.equals(now)) {
            send(player, now);
        }
    }

    private static void send(ServerPlayer player, Map<Integer, Integer> now) {
        int[] ids = new int[now.size()];
        int[] markers = new int[now.size()];
        int k = 0;
        for (Map.Entry<Integer, Integer> e : now.entrySet()) {
            ids[k] = e.getKey();
            markers[k++] = e.getValue();
        }
        PacketDistributor.sendToPlayer(player, new Update(ids, markers));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SENT.clear();
    }

    // 自己テスト用: ディメンションを移った人の記録を捨て、次の見回りで必ず送り直す
    static void forgetForTest(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }
}
