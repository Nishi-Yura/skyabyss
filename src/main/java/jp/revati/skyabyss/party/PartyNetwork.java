package jp.revati.skyabyss.party;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.client.PartyClient;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// パーティと物語の進みのパケット。ほかの担当とぶつからないように、別の版で登録する。
// - サーバー → 人: 仲間 (名前・レベル・体力)、届いている招待、物語の段階とレベルの上限 (パーティにいる人は 0.5 秒ごと、ほかは 5 秒ごと、変わったときはすぐ)
// - 人 → サーバー: 画面の操作 (招待・受ける・断る・抜ける・外す・リーダーを渡す)。中身は Parties がもう一度検める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PartyNetwork {
    private static final String PROTOCOL_VERSION = "party-1";
    private static final Set<UUID> SOON = new HashSet<>();

    private PartyNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Sync.TYPE, Sync.STREAM_CODEC, (payload, context) -> PartyClient.receive(payload))
                .playToServer(Action.TYPE, Action.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handle(player, payload);
                    }
                });
    }

    // 人ごとに最後に送った内容 (同じなら送り直さない)
    private static final java.util.Map<UUID, Sync> LAST_SENT = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    static void onLogoutForget(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    public static void syncSoon(ServerPlayer player) {
        SOON.add(player.getUUID());
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 10 != 0) {
            return;
        }
        PartyData data = PartyData.get(server);
        for (ServerPlayer player : Players.snapshot(server.getPlayerList().getPlayers())) {
            boolean inParty = data.of(player.getUUID()) != null;
            boolean soon = SOON.remove(player.getUUID());
            if (soon || tick % 100 == 0) {
                send(player);
            } else if (inParty && player.connection != null) {
                // 仲間がいる間は 0.5 秒ごとに見るが、前に送った中身と同じなら送らない (5 秒に一度は必ず送る)
                Sync now = build(player);
                if (!now.equals(LAST_SENT.put(player.getUUID(), now))) {
                    PacketDistributor.sendToPlayer(player, now);
                }
            }
        }
        SOON.clear();
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncSoon(player);
            PartyData.Party party = Parties.of(player);
            if (party != null) {
                // 名前が変わっていれば控えを直す
                party.names.put(player.getUUID(), player.getGameProfile().getName());
                Parties.onlineMembers(player.server, party).forEach(PartyNetwork::syncSoon);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SOON.clear();
        LAST_SENT.clear();
    }

    public static Sync build(ServerPlayer player) {
        MinecraftServer server = player.server;
        List<Member> members = new ArrayList<>();
        PartyData.Party party = Parties.of(player);
        if (party != null) {
            for (UUID id : party.members) {
                ServerPlayer m = server.getPlayerList().getPlayer(id);
                String name = party.names.getOrDefault(id, "?");
                if (m == null) {
                    members.add(new Member(id.toString(), name, 0, 0, 0, false, false, party.isLeader(id), jp.revati.skyabyss.rebirth.Rebirths.offlineCount(server, id)));
                } else {
                    members.add(new Member(id.toString(), name, Growth.get(m).level, m.getHealth(), m.getMaxHealth(), true,
                            m.level() == player.level(), party.isLeader(id), jp.revati.skyabyss.rebirth.RebirthView.count(m)));
                }
            }
        }
        Parties.Invite inv = Parties.inviteFor(player);
        String next = Progression.nextTitan(player);
        return new Sync(Progression.stage(player), Progression.levelCap(player), Progression.worldStage(server), next == null ? "" : next,
                (int) Math.round(Parties.shareRate(server) * 100), members, inv == null ? "" : inv.fromName());
    }

    static void send(ServerPlayer player) {
        if (player.connection != null) {
            Sync now = build(player);
            LAST_SENT.put(player.getUUID(), now);
            PacketDistributor.sendToPlayer(player, now);
        }
    }

    static void handle(ServerPlayer player, Action action) {
        MinecraftServer server = player.server;
        switch (action.op()) {
            case Action.INVITE -> {
                ServerPlayer to = server.getPlayerList().getPlayerByName(action.name());
                if (to != null) {
                    Parties.invite(player, to);
                }
            }
            case Action.ACCEPT -> Parties.accept(player);
            case Action.DECLINE -> Parties.decline(player);
            case Action.LEAVE -> Parties.leave(player);
            case Action.KICK, Action.PROMOTE -> {
                PartyData.Party party = Parties.of(player);
                UUID target = party == null ? null : Parties.memberByName(party, action.name());
                if (target != null) {
                    if (action.op() == Action.KICK) {
                        Parties.kick(player, target, action.name());
                    } else {
                        Parties.promote(player, target, action.name());
                    }
                }
            }
            default -> {
            }
        }
        syncSoon(player);
    }

    // rebirths: 転生の回数 (名前の枠の色と記号に使う。docs/design/31 の C15)
    public record Member(String id, String name, int level, float health, float maxHealth, boolean online, boolean sameDim, boolean leader, int rebirths) {
        public Member(String id, String name, int level, float health, float maxHealth, boolean online, boolean sameDim, boolean leader) {
            this(id, name, level, health, maxHealth, online, sameDim, leader, 0);
        }

        static void write(FriendlyByteBuf buf, Member m) {
            buf.writeUtf(m.id, 64);
            buf.writeUtf(m.name, 32);
            buf.writeVarInt(m.level);
            buf.writeFloat(m.health);
            buf.writeFloat(m.maxHealth);
            buf.writeBoolean(m.online);
            buf.writeBoolean(m.sameDim);
            buf.writeBoolean(m.leader);
            buf.writeVarInt(m.rebirths);
        }

        static Member read(FriendlyByteBuf buf) {
            return new Member(buf.readUtf(64), buf.readUtf(32), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }
    }

    public record Sync(int stage, int cap, int worldStage, String nextTitan, int sharePercent, List<Member> members, String inviteFrom)
            implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeVarInt(s.stage);
                    buf.writeVarInt(s.cap);
                    buf.writeVarInt(s.worldStage);
                    buf.writeUtf(s.nextTitan, 64);
                    buf.writeVarInt(s.sharePercent);
                    buf.writeVarInt(s.members.size());
                    s.members.forEach(m -> Member.write(buf, m));
                    buf.writeUtf(s.inviteFrom, 32);
                },
                buf -> {
                    int stage = buf.readVarInt();
                    int cap = buf.readVarInt();
                    int world = buf.readVarInt();
                    String next = buf.readUtf(64);
                    int share = buf.readVarInt();
                    int n = Math.min(buf.readVarInt(), 16);
                    List<Member> members = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        members.add(Member.read(buf));
                    }
                    return new Sync(stage, cap, world, next, share, members, buf.readUtf(32));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Action(int op, String name) implements CustomPacketPayload {
        public static final int INVITE = 0;
        public static final int ACCEPT = 1;
        public static final int DECLINE = 2;
        public static final int LEAVE = 3;
        public static final int KICK = 4;
        public static final int PROMOTE = 5;
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of(
                (buf, a) -> {
                    buf.writeVarInt(a.op);
                    buf.writeUtf(a.name, 32);
                },
                buf -> new Action(buf.readVarInt(), buf.readUtf(32)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
