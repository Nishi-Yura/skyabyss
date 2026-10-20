package jp.revati.skyabyss.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.quest.ChapterLabel;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// クエストの見え方 (docs/design/18 の 3.5 節): 今の章の札 (左上の目的の帯の上)、クエストの更新の音。
// NPC の頭の印は npc/NpcMarkers。章はサーバーが人ごとに導き (Chapters.of)、変わったときだけその人に送る。
//   音: tools/gen_quest_ux_sounds.py で合成。メインの更新 = 小さな鐘が二つ (五度上がる)、サブ = ひとつだけもっと柔らかい
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class QuestUx {
    private static final String PROTOCOL_VERSION = "questux-1";
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAIN_SOUND = sound("quest_main");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIDE_SOUND = sound("quest_side");

    // 人ごとに最後に送った章の番号 (ログアウトで消す。ログイン後の最初の見回りで必ず送る)
    private static final Map<UUID, Integer> SENT = new HashMap<>();

    private QuestUx() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

    @SubscribeEvent
    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(ChapterPayload.TYPE, ChapterPayload.STREAM_CODEC,
                (payload, context) -> ChapterLabel.receive(payload.chapter()));
    }

    // サーバー → その人だけ: 今の章の番号 (Chapters.number()。-1 = 出さない)
    public record ChapterPayload(int chapter) implements CustomPacketPayload {
        public static final Type<ChapterPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "chapter_label"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChapterPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.chapter + 1), buf -> new ChapterPayload(buf.readVarInt() - 1));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.connection == null
                || player.tickCount % 20 != 7) {
            return;
        }
        // 札 (題字) を出すまでは前の章のまま: 章が終わりきる前に次の章の表示を出さない
        Chapters cur = Chapters.of(player);
        int now = cur.number() > 0 && !jp.revati.skyabyss.story.arc.ArcData.of(player).titleShown(cur) ? cur.number() - 1 : cur.number();
        Integer before = SENT.put(player.getUUID(), now);
        if (before == null || before != now) {
            PacketDistributor.sendToPlayer(player, new ChapterPayload(now));
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SENT.clear();
    }
}
