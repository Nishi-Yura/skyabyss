package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.util.Players;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

// ボスの技の名前を、見える人へ送る (画面の側は client/titan/BossAttackCue。設定 bossAttackNames に従う)。
// 名前の鍵は gui.skyabyss.attack.<ボス>.<技>。四つの心臓・中ボス・番兵が共通で使う。
// 雷翼の心臓の技の名前は従来どおり StormHeartNetwork.Warn (印つき) でも送る (ここは追加の技 = 回り続けへの技・領域用)。
public final class BossCue {
    // 見える距離
    public static final double RANGE = 64;

    private BossCue() {
    }

    public record Payload(String boss, String attack, int ticks) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "boss_cue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Payload::boss, ByteBufCodecs.STRING_UTF8, Payload::attack, ByteBufCodecs.VAR_INT, Payload::ticks, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 試験用: 最後に送った名前と、送った数
    private static String lastSent = "";
    private static int sentCount;

    // 技の名前を出す。ticks は予告の長さ (名前の出ている長さの目安)。ボスの近くの、そのボスが見える人へ
    public static void send(Entity boss, String bossKey, String attackKey, int ticks) {
        lastSent = bossKey + "." + attackKey;
        sentCount++;
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        Payload payload = new Payload(bossKey, attackKey, ticks);
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
                continue;
            }
            if (p.distanceToSqr(boss) < RANGE * RANGE && GroupOwned.visibleTo(boss, p)) {
                PacketDistributor.sendToPlayer(p, payload);
            }
        }
    }

    public static String lastSent() {
        return lastSent;
    }

    public static int sentCount() {
        return sentCount;
    }
}
