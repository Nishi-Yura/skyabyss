package jp.revati.skyabyss.world.continent;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 入った世界が「天と淵の大陸」かを手元へ知らせる (旅の地図が描いた大陸の絵を敷くため)。入るたびに送る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentInfo {
    // 手元: いまの世界が大陸か (入ったときに届く。抜けたら次に入るまで前の値のまま)
    public static volatile boolean clientContinent;

    public record Payload(boolean continent) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "continent_info"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Payload::continent, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ContinentInfo() {
    }

    public static boolean isContinent(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getChunkSource().getGenerator() instanceof ContinentChunkGenerator;
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("continent-1").playToClient(Payload.TYPE, Payload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> clientContinent = payload.continent()));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new Payload(isContinent(player.server)));
        }
    }
}
