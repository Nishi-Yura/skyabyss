package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー -> クライアント: 床の印 (decal) を出す。形の数値・色・予告の長さ・いま何 tick 経っているか。
// 見える人 (Telegraph と同じグループの規則) にだけ送る。取り消しは TelegraphCancelPayload。
public record TelegraphStartPayload(int id, int kind, float[] params, float floor, int ageAtSend, int warn, int active, int rgb)
        implements CustomPacketPayload {
    public static final Type<TelegraphStartPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "telegraph_start"));

    // 形の数値の上限 (波の安全な筋の数を含む)。これを超える入力は壊れたものとして断る
    static final int MAX_PARAMS = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, TelegraphStartPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id());
                buf.writeByte(p.kind());
                buf.writeVarInt(p.params().length);
                for (float v : p.params()) {
                    buf.writeFloat(v);
                }
                buf.writeFloat(p.floor());
                buf.writeVarInt(p.ageAtSend());
                buf.writeVarInt(p.warn());
                buf.writeVarInt(p.active());
                buf.writeInt(p.rgb());
            },
            buf -> {
                int id = buf.readVarInt();
                int kind = buf.readByte();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_PARAMS) {
                    throw new IllegalArgumentException("telegraph params too long: " + n);
                }
                float[] params = new float[n];
                for (int i = 0; i < n; i++) {
                    params[i] = buf.readFloat();
                }
                return new TelegraphStartPayload(id, kind, params, buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt());
            });

    public static TelegraphStartPayload of(int id, TelegraphShape shape, double floor, int ageAtSend, int warn, int active, int rgb) {
        double[] d = shape.params();
        float[] f = new float[d.length];
        for (int i = 0; i < d.length; i++) {
            f[i] = (float) d[i];
        }
        return new TelegraphStartPayload(id, shape.kind(), f, (float) floor, ageAtSend, warn, active, rgb);
    }

    // 受け取った側で形を戻す (壊れていれば null)
    public TelegraphShape shape() {
        double[] d = new double[params.length];
        for (int i = 0; i < d.length; i++) {
            d[i] = params[i];
        }
        return TelegraphShape.decode(kind, d);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
