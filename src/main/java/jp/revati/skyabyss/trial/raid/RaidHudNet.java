package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// レイドの部屋の目的の帯の中身 (docs/design/32 の 7 章)。サーバー → 部屋にいる人。cellId が空なら帯から消す。
// 部屋の名前 (trial.skyabyss.cell.<cellId>)・目的の文 (objKey + args)・数 cur/max・二つ目の数 (secKey sec/secMax)・残り tick (timer。負なら出さない)・越えた (done)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RaidHudNet {
    private RaidHudNet() {
    }

    private static final String PROTOCOL_VERSION = "raid_hud-1";

    public record Hud(String cellId, String objKey, List<String> args, int cur, int max, String secKey, int sec, int secMax, int timer, boolean done)
            implements CustomPacketPayload {
        public static final Type<Hud> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "raid_hud"));
        public static final Hud OFF = new Hud("", "", List.of(), 0, 0, "", 0, 0, -1, false);
        public static final StreamCodec<RegistryFriendlyByteBuf, Hud> STREAM_CODEC = StreamCodec.of(
                (buf, t) -> {
                    buf.writeUtf(t.cellId, 16);
                    buf.writeUtf(t.objKey, 96);
                    buf.writeVarInt(t.args.size());
                    for (String a : t.args) {
                        buf.writeUtf(a, 64);
                    }
                    buf.writeVarInt(t.cur);
                    buf.writeVarInt(t.max);
                    buf.writeUtf(t.secKey, 96);
                    buf.writeVarInt(t.sec);
                    buf.writeVarInt(t.secMax);
                    buf.writeInt(t.timer);
                    buf.writeBoolean(t.done);
                }, buf -> {
                    String cell = buf.readUtf(16);
                    String obj = buf.readUtf(96);
                    int n = buf.readVarInt();
                    List<String> args = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        args.add(buf.readUtf(64));
                    }
                    return new Hud(cell, obj, List.copyOf(args), buf.readVarInt(), buf.readVarInt(), buf.readUtf(96), buf.readVarInt(), buf.readVarInt(),
                            buf.readInt(), buf.readBoolean());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION).playToClient(Hud.TYPE, Hud.STREAM_CODEC, (p, ctx) -> jp.revati.skyabyss.client.trial.RaidHudClient.onHud(p));
    }
}
