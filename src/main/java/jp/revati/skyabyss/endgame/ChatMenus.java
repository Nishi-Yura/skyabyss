package jp.revati.skyabyss.endgame;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// チャットに押せる一覧を出す機能 (秘伝の枠・塔の受付・見た目替え・旅帳・図鑑) を、係の人の会話から開いたとき、手元でチャットの窓を開く。
// 狭い画面ではチャットの見える行が 1 行まで絞られるので (HudLayout の chatLines)、窓を開いて一覧を全部見せ、押せるようにする。
// 手元は会話の画面が閉じてから開く (endgame/client/ChatMenuClient)
public final class ChatMenus {
    private ChatMenus() {
    }

    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "chat_menu_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void open(ServerPlayer p) {
        if (!(p instanceof FakePlayer) && p.connection != null) {
            PacketDistributor.sendToPlayer(p, new Open());
        }
    }
}
