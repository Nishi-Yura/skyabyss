package jp.revati.skyabyss.facility;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 預かり所の箱を開いたまま頁を替える (2026-11-09 の声「チェスト開いてる所からすぐに 2 ページ目行けるようにして」)。
// 箱を開くと、サーバーが今の頁と開けた頁の数を送る (Tabs)。箱の右に頁の札が出て (client/facility/StashTabs)、押すと Turn が来て、その頁の箱に開き直す。
// 手に品を持っている (カーソルに載せている) 間は替えない (開き直すと持っている品が持ち物へ戻り、いっぱいなら落ちるため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StashPages {
    private StashPages() {
    }

    // サーバー → 人: 箱 (containerId) は pages 頁のうちの page 頁目
    public record Tabs(int containerId, int page, int pages) implements CustomPacketPayload {
        public static final Type<Tabs> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "stash_tabs"));
        public static final StreamCodec<FriendlyByteBuf, Tabs> STREAM_CODEC = StreamCodec.of((buf, t) -> {
            buf.writeVarInt(t.containerId);
            buf.writeVarInt(t.page);
            buf.writeVarInt(t.pages);
        }, buf -> new Tabs(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 人 → サーバー: 開いている預かり所の箱を page 頁目に替える
    public record Turn(int page) implements CustomPacketPayload {
        public static final Type<Turn> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "stash_turn"));
        public static final StreamCodec<FriendlyByteBuf, Turn> STREAM_CODEC = StreamCodec.of((buf, t) -> buf.writeVarInt(t.page), buf -> new Turn(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("stash-pages-1")
                .playToClient(Tabs.TYPE, Tabs.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                        () -> jp.revati.skyabyss.client.facility.StashTabs.receive(payload)))
                .playToServer(Turn.TYPE, Turn.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer p) {
                        turn(p, payload.page());
                    }
                }));
    }

    // 開いた箱の頁を知らせる (StashMenu.open から)
    static void tell(ServerPlayer p, int page, int pages) {
        if (p.connection != null && p.containerMenu instanceof StashMenu) {
            PacketDistributor.sendToPlayer(p, new Tabs(p.containerMenu.containerId, page, pages));
        }
    }

    // 頁を替える。預かり所の箱を開いていない・手に品を持っている・まだ開けていない頁なら何もしない
    static boolean turn(ServerPlayer p, int page) {
        if (!(p.containerMenu instanceof StashMenu menu) || !menu.getCarried().isEmpty() || !menu.stillValid(p)) {
            return false;
        }
        StashData data = p.getData(FacilitySetup.STASH);
        if (page < 1 || page > data.pages() || page == menu.page()) {
            return false;
        }
        StashMenu.open(p, menu.keeper(), page);
        return true;
    }
}
