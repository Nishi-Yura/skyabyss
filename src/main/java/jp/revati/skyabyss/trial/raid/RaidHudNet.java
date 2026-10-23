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

    private static final String PROTOCOL_VERSION = "raid_hud-2";

    // guide = 行き先の印があるか (gx, gy, gz はその位置。帯に矢印と距離を出す。RaidRoom.guideTarget)
    public record Hud(String cellId, String objKey, List<String> args, int cur, int max, String secKey, int sec, int secMax, int timer, boolean done,
            boolean guide, int gx, int gy, int gz) implements CustomPacketPayload {
        public Hud(String cellId, String objKey, List<String> args, int cur, int max, String secKey, int sec, int secMax, int timer, boolean done) {
            this(cellId, objKey, args, cur, max, secKey, sec, secMax, timer, done, false, 0, 0, 0);
        }

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
                    buf.writeBoolean(t.guide);
                    if (t.guide) {
                        buf.writeInt(t.gx);
                        buf.writeInt(t.gy);
                        buf.writeInt(t.gz);
                    }
                }, buf -> {
                    String cell = buf.readUtf(16);
                    String obj = buf.readUtf(96);
                    int n = buf.readVarInt();
                    List<String> args = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        args.add(buf.readUtf(64));
                    }
                    int cur = buf.readVarInt();
                    int max = buf.readVarInt();
                    String sec = buf.readUtf(96);
                    int secV = buf.readVarInt();
                    int secMax = buf.readVarInt();
                    int timer = buf.readInt();
                    boolean done = buf.readBoolean();
                    boolean guide = buf.readBoolean();
                    int gx = guide ? buf.readInt() : 0;
                    int gy = guide ? buf.readInt() : 0;
                    int gz = guide ? buf.readInt() : 0;
                    return new Hud(cell, obj, List.copyOf(args), cur, max, sec, secV, secMax, timer, done, guide, gx, gy, gz);
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
