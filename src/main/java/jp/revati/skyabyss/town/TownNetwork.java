package jp.revati.skyabyss.town;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.town.TownClient;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 町のクエストの目的の帯の行き先 (「クエスト/段」→ 場所) を参加者へ送る。帯 (client/quest/ObjectiveStrip) が距離と方角を出す。
// 場所は段の目的から決める: 限定の人と話す → その人の立つ印、係の人と話す → npc_<役>、town_at:<印> → その印、
// 頂の鐘 → 印 bell、子どもを宿へ → npc_innkeeper、town_hand:<相手> → 相手の立つ印。集める・倒す段は出さない。
// ほかの担当とぶつからないよう、別の版 (town-1) で登録する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TownNetwork {
    private static final String PROTOCOL_VERSION = "town-1";
    private static Map<String, BlockPos> lastSent = Map.of();

    private TownNetwork() {
    }

    public record Spots(Map<String, BlockPos> spots) implements CustomPacketPayload {
        public static final Type<Spots> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "town_spots"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Spots> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.spots.size());
                    p.spots.forEach((k, v) -> {
                        buf.writeUtf(k);
                        buf.writeBlockPos(v);
                    });
                },
                buf -> {
                    int n = buf.readVarInt();
                    Map<String, BlockPos> m = new LinkedHashMap<>();
                    for (int i = 0; i < n; i++) {
                        m.put(buf.readUtf(), buf.readBlockPos());
                    }
                    return new Spots(m);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Spots.TYPE, Spots.STREAM_CODEC, (p, c) -> TownClient.receive(p.spots()));
    }

    // ---------------------------------------------------------------- 場所を決める
    public static Map<String, BlockPos> spots(MinecraftServer server) {
        Map<String, BlockPos> marks = TownGuests.marks(server);
        Map<String, BlockPos> out = new LinkedHashMap<>();
        for (TownQuests.TownQuest q : TownQuests.ALL) {
            QuestDef def = QuestManager.quest(q.id());
            if (def == null) {
                continue;
            }
            for (QuestDef.Stage st : def.stages()) {
                BlockPos at = stageSpot(st, marks);
                if (at != null) {
                    out.put(q.id() + "/" + st.id(), at);
                }
            }
        }
        return out;
    }

    @Nullable
    static BlockPos stageSpot(QuestDef.Stage st, Map<String, BlockPos> marks) {
        for (QuestDef.Objective ob : st.objectives()) {
            for (String t : ob.targets()) {
                String mark = switch (ob.type()) {
                    case "talk" -> markOfRole(t, st.id());
                    case "signal" -> markOfSignal(t, st.id());
                    default -> null;
                };
                if (mark != null && marks.get(mark) != null) {
                    return marks.get(mark);
                }
            }
        }
        return null;
    }

    // 役 (skyabyss:guest_<id> / skyabyss:attendant_<役> / guest_<id> / attendant_<役>) の立つ印
    @Nullable
    static String markOfRole(String role, String stage) {
        String r = role.startsWith(SkyAbyss.MODID + ":") ? role.substring(SkyAbyss.MODID.length() + 1) : role;
        if (r.startsWith("guest_")) {
            TownGuests.Guest g = TownGuests.byId(r.substring("guest_".length()));
            return g == null ? null : g.markFor(stage);
        }
        if (r.startsWith("attendant_")) {
            return "npc_" + r.substring("attendant_".length());
        }
        return null;
    }

    @Nullable
    static String markOfSignal(String signal, String stage) {
        if (signal.startsWith(TownQuests.SIGNAL_AT)) {
            return signal.substring(TownQuests.SIGNAL_AT.length());
        }
        if (signal.equals(TownQuests.SIGNAL_TOWER_BELL)) {
            return "bell";
        }
        if (signal.startsWith("town_hand:")) {
            return markOfRole(signal.substring("town_hand:".length()), stage);
        }
        for (TownGuests.Guest g : TownGuests.ALL) {
            if (signal.equals(g.escortSignal())) {
                return g.escortTo();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 送る
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayer(p, new Spots(spots(p.server)));
        }
    }

    // 印が変わった (都が建った・建て直した) ら、みなに送り直す (5 秒ごとに比べる)
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 100 != 51 || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        Map<String, BlockPos> now = spots(server);
        if (!now.equals(lastSent)) {
            lastSent = Map.copyOf(now);
            PacketDistributor.sendToAllPlayers(new Spots(now));
        }
    }
}
