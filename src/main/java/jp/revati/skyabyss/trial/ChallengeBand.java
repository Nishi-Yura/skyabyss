package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.dungeon.DungeonRun;
import jp.revati.skyabyss.dungeon.DungeonWorld;
import jp.revati.skyabyss.dungeon.Dungeons;
import jp.revati.skyabyss.util.Players;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
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

// 挑んでいる所の名前と、いま効いている変化の帯 (2026-11-09 の声「どこかに挑んでるとき、画面のどこかに出してもいいかもね。
// そのダンジョンとかレイドの名前を。強い版とか深淵とか。色々変更があると、そこに書いてる」)。
// 1 秒ごとに人ごとに作り、変わったときだけ送る (Band)。手もとは右上の板の一番上に出す (client/hud/ChallengeHud)
//   ダンジョン: 名前・今の層、札 = 深く潜る・今日の様子・人数で硬い
//   巨神のルート (背中のレイドの部屋): 巨神の名前のルート・今の部屋、札 = 中ボスの部屋・崩れた回数
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChallengeBand {
    private static final Map<UUID, Band> LAST = new ConcurrentHashMap<>();

    private ChallengeBand() {
    }

    // 一枚の札 (文と色)
    public record Tag(Component text, int rgb) {
        static final StreamCodec<RegistryFriendlyByteBuf, Tag> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Tag::text, ByteBufCodecs.VAR_INT, Tag::rgb, Tag::new);
    }

    // title が空なら帯を消す
    public record Band(Component title, Component sub, List<Tag> tags) implements CustomPacketPayload {
        public static final Type<Band> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "challenge_band"));
        public static final Band OFF = new Band(Component.empty(), Component.empty(), List.of());
        public static final StreamCodec<RegistryFriendlyByteBuf, Band> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Band::title, ComponentSerialization.TRUSTED_STREAM_CODEC, Band::sub,
                Tag.STREAM_CODEC.apply(ByteBufCodecs.list(8)), Band::tags, Band::new);

        public boolean off() {
            return title.getString().isEmpty();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("challenge-band-1").playToClient(Band.TYPE, Band.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.client.hud.ChallengeHud.receive(payload)));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    // その人の今の帯 (自己テストからも)
    public static Band of(ServerPlayer p) {
        if (DungeonWorld.in(p)) {
            DungeonRun run = Dungeons.runOf(p);
            if (run == null) {
                return Band.OFF;
            }
            int f = Math.max(0, run.floorOf(p));
            List<Tag> tags = new ArrayList<>();
            if (run.deep) {
                tags.add(new Tag(Component.translatable("hud.skyabyss.challenge.deep"), 0xB48AE6));
            }
            if (run.mood != null) {
                tags.add(new Tag(Component.translatable(run.mood.nameKey()), 0x7FD6D0));
            }
            if (run.members.size() > 1) {
                tags.add(new Tag(Component.translatable("hud.skyabyss.challenge.party", run.members.size()), 0xC8D2DC));
            }
            return new Band(Component.translatable(run.dungeon.nameKey()), Component.translatable(run.dungeon.nameKey() + ".floor" + f), tags);
        }
        java.util.UUID group = jp.revati.skyabyss.titan.group.Groups.keyOf(p);
        jp.revati.skyabyss.trial.raid.RaidRun.Run raid = group == null ? null : jp.revati.skyabyss.trial.raid.RaidRun.of(group);
        if (raid != null && raid.ctx != null && raid.ctx.level() == p.level()) {
            List<Tag> tags = new ArrayList<>();
            if (raid.mid) {
                tags.add(new Tag(Component.translatable("hud.skyabyss.challenge.mid"), 0xF2C14E));
            }
            if (raid.wipes > 0) {
                tags.add(new Tag(Component.translatable("hud.skyabyss.challenge.wipes", raid.wipes), 0xD9779A));
            }
            Component sub = raid.lastHud == null || raid.lastHud.cellId().isEmpty() ? Component.empty()
                    : Component.translatable("trial.skyabyss.cell." + raid.lastHud.cellId());
            return new Band(Component.translatable("hud.skyabyss.challenge.route", jp.revati.skyabyss.progress.Progression.titanName(raid.ctx.titan())), sub, tags);
        }
        return Band.OFF;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 13) {
            return;
        }
        for (ServerPlayer p : Players.snapshot(server.getPlayerList().getPlayers())) {
            if (p instanceof net.neoforged.neoforge.common.util.FakePlayer || p.connection == null) {
                continue;
            }
            Band now = of(p);
            Band before = LAST.get(p.getUUID());
            if (before != null && before.equals(now)) {
                continue;
            }
            LAST.put(p.getUUID(), now);
            PacketDistributor.sendToPlayer(p, now);
        }
    }
}
