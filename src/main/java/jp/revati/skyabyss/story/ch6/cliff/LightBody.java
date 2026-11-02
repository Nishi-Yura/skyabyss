package jp.revati.skyabyss.story.ch6.cliff;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 星術「軽身」(上弦の崖の月の溜まりで覚える。docs/design/46 の 4.2・4.4.1)。覚えたあとは、月の上で空中にいるとき跳ぶ鍵をもう一度押すと、
// もう一段跳ぶ (着くまで一度)。道具も技の枠もいらない。動きは画面の側で決まるので、跳ぶのは画面の側 (client/LightBodyClient)、
// サーバーは印 (覚えたか) を持ち、跳んだ知らせで落ちた高さを戻して音と粒を出す。印は倒れても残す (util/LifeKeep)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LightBody {
    public static final String TAG = "skyabyss_moon_light_body";

    // 覚えたか (サーバー → 画面)
    public record Sync(boolean learned) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "light_body_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Sync::learned, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 跳んだ (画面 → サーバー)
    public record Leap() implements CustomPacketPayload {
        public static final Type<Leap> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "light_body_leap"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Leap> STREAM_CODEC = StreamCodec.unit(new Leap());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private LightBody() {
    }

    public static boolean learned(ServerPlayer p) {
        return p.getPersistentData().getBoolean(TAG);
    }

    public static void learn(ServerPlayer p) {
        p.getPersistentData().putBoolean(TAG, true);
        sync(p);
    }

    // 開発・自己テスト
    public static void forget(ServerPlayer p) {
        p.getPersistentData().remove(TAG);
        sync(p);
    }

    static void sync(ServerPlayer p) {
        if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            PacketDistributor.sendToPlayer(p, new Sync(learned(p)));
        }
    }

    // 跳んだ知らせ: 月の上で覚えた人だけ。落ちた高さを戻し、足もとに星の粒の輪と音
    static void leap(ServerPlayer p) {
        if (!learned(p) || p.level().dimension() != MoonWorld.LEVEL) {
            return;
        }
        p.fallDistance = 0;
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), CliffBlocks.LIGHT_BODY.get(), SoundSource.PLAYERS, 0.7f, 1.3f);
        for (int i = 0; i < 12; i++) {
            double a = i / 12.0 * Math.PI * 2;
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * 0.6, p.getY(), p.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0.01);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p);
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Net {
        private Net() {
        }

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1")
                    .playToClient(Sync.TYPE, Sync.STREAM_CODEC, (payload, context) -> jp.revati.skyabyss.story.ch6.client.LightBodyClient.receive(payload.learned()))
                    .playToServer(Leap.TYPE, Leap.STREAM_CODEC, (payload, context) -> {
                        if (context.player() instanceof ServerPlayer p) {
                            leap(p);
                        }
                    });
        }
    }
}
