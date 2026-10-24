package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 与えた傷の数字 (2026-10-25 の試遊: 当てた手ごたえがない)。人が敵に傷を入れたら、その人にだけ「どの体に、いくつ」を送る。
// 数字を描くのは手元 (client/combat/DamageNumbersClient)。技の傷は色を変える (技の名の色)、とどめは大きく
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DamageNumbers {
    private static final String PROTOCOL_VERSION = "damage-numbers-1";

    // 種類: 0 = ふつうの一撃, 1 = 技, 2 = とどめ
    public static final int NORMAL = 0;
    public static final int SKILL = 1;
    public static final int KILL = 2;

    private DamageNumbers() {
    }

    public record Payload(int entity, double x, double y, double z, float amount, int kind) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "damage_number"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entity);
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.amount);
                    buf.writeByte(p.kind);
                },
                buf -> new Payload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(Payload.TYPE, Payload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.client.combat.DamageNumbersClient.receive(payload)));
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        float amount = event.getNewDamage();
        if (amount <= 0 || victim instanceof Player || victim.level().isClientSide()
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker instanceof FakePlayer || attacker.connection == null) {
            return;
        }
        int kind = victim.isDeadOrDying() ? KILL : jp.revati.skyabyss.rpg.skill.SkillActions.dealing() != null ? SKILL : NORMAL;
        PacketDistributor.sendToPlayer(attacker, new Payload(victim.getId(), victim.getX(), victim.getY() + victim.getBbHeight(), victim.getZ(), amount, kind));
    }
}
