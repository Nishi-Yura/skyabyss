package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.util.Players;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

// ボスの登場の画面 (2026-11-11 の声「最初の登場のアニメーションや、画面にボスの名前表示がない」)。
//   RISE: 「〜が坑の底から這い出る」と数え下ろし (ticks = 出てくるまで)。主はまだ地の下
//   NAME: 主の名前の札 (上下の黒い帯、名前が中から広がり、肩書きが上に)。ticks = 札を出す長さ
// 文: gui.skyabyss.boss_intro.rise.<主> (無ければ gui.skyabyss.boss_intro.rise に名前を入れる)、肩書き gui.skyabyss.boss_intro.sub.<主>
public final class BossIntro {
    public static final int RISE = 0;
    public static final int NAME = 1;
    public static final double RANGE = 48;

    private BossIntro() {
    }

    public record Payload(int phase, Component name, String boss, int ticks) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "boss_intro"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Payload::phase, ComponentSerialization.TRUSTED_STREAM_CODEC, Payload::name,
                ByteBufCodecs.STRING_UTF8, Payload::boss, ByteBufCodecs.VAR_INT, Payload::ticks, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 主の近くの、主が見える人へ
    public static void send(Entity boss, int phase, String bossKey, int ticks) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        Payload payload = new Payload(phase, boss.getDisplayName(), bossKey, ticks);
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
                continue;
            }
            if (p.distanceToSqr(boss) < RANGE * RANGE && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(boss, p)) {
                PacketDistributor.sendToPlayer(p, payload);
            }
        }
    }
}
