package jp.revati.skyabyss.titan.domain;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー -> クライアント: 領域の始まり・進み・終わり (HeartDomain)。画面の側は client/titan/domain/DomainClient。
//  START   : a = 宣言の長さ tick、b = 領域の長さの目安 tick (安全網。画面が通信の途切れを見切る)。部屋の中心・床の高さ・半径つき
//  PROGRESS: a = 解いた割合 (0〜100 %)、b = 解いた仕掛けの数
//  HINT    : key = 案内の翻訳キー (その領域の手順の一行)、a b = 数字 (叩く順の番号・解いた数)、mood = 0 常時の案内 / 1 正解 / 2 失敗 / 3 状況の合図
//  ROUND   : a = いまの巡 (1 始まり)、b = 巡の数
//  END     : a = 1 なら解けた (砕けた)、0 なら安全網・心臓が倒れた
public record DomainPayload(String id, int state, int a, int b, double cx, double cz, double floor, double radius, String key, int mood) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int PROGRESS = 1;
    public static final int END = 2;
    public static final int HINT = 3;
    public static final int ROUND = 4;

    public static final Type<DomainPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "heart_domain"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DomainPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.id);
                buf.writeVarInt(p.state);
                buf.writeVarInt(p.a);
                buf.writeVarInt(p.b);
                buf.writeDouble(p.cx);
                buf.writeDouble(p.cz);
                buf.writeDouble(p.floor);
                buf.writeDouble(p.radius);
                buf.writeUtf(p.key);
                buf.writeVarInt(p.mood);
            },
            buf -> new DomainPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readUtf(), buf.readVarInt()));

    public static DomainPayload start(String id, int declare, int active, double cx, double cz, double floor, double radius) {
        return new DomainPayload(id, START, declare, active, cx, cz, floor, radius, "", 0);
    }

    public static DomainPayload progress(String id, int percent, int remaining) {
        return new DomainPayload(id, PROGRESS, percent, remaining, 0, 0, 0, 0, "", 0);
    }

    public static DomainPayload round(String id, int round, int rounds) {
        return new DomainPayload(id, ROUND, round, rounds, 0, 0, 0, 0, "", 0);
    }

    public static DomainPayload end(String id, boolean broken) {
        return new DomainPayload(id, END, broken ? 1 : 0, 0, 0, 0, 0, 0, "", 0);
    }

    public static DomainPayload hint(String id, String key, int a, int b, int mood) {
        return new DomainPayload(id, HINT, a, b, 0, 0, 0, 0, key, mood);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
