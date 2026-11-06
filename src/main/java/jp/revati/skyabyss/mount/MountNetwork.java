package jp.revati.skyabyss.mount;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 乗り物の店 (厩舎) の通信 (docs/design/38 の 5 章)。サーバー → 画面: 持ち物の全体 (View)・閉じろ (Close)。画面 → サーバー: 押したこと (Act)・閉じた (Closed)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MountNetwork {
    private static final String PROTOCOL_VERSION = "mount-1";

    public static final int BUY_LOOK = 0;
    public static final int CHOOSE_LOOK = 1;
    public static final int BUY_TIER = 2;
    public static final int WHISTLE = 3;

    private MountNetwork() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name));
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(View.TYPE, View.STREAM_CODEC, (p, c) -> jp.revati.skyabyss.client.mount.MountClient.view(p))
                .playToClient(Close.TYPE, Close.STREAM_CODEC, (p, c) -> jp.revati.skyabyss.client.mount.MountClient.close())
                .playToServer(Act.TYPE, Act.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        MountService.act(player, p.action(), p.arg());
                    }
                })
                .playToServer(Closed.TYPE, Closed.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        MountService.closed(player);
                    }
                })
                .playToServer(Rename.TYPE, Rename.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        MountService.rename(player, p.name());
                    }
                });
    }

    public static View view(ServerPlayer player) {
        MountData d = MountService.data(player);
        return new View(Coins.count(player), MountService.chapter(player), d.ownedBits(), d.chosen().ordinal(),
                d.tier(MountStats.SPEED), d.tier(MountStats.JUMP), d.tier(MountStats.SEATS), MountService.hasWhistle(player), d.bond(), (int) d.ridden(), d.name());
    }

    // owned = 買った見た目の bit、chosen = 今の見た目の ordinal、speed / jump / seats = 性能の段 (tier(行) は店の行の順)
    // bond = 絆の段、ridden = 乗った距離 (ブロック)、name = 付けた名前
    public record View(int coins, int chapter, int owned, int chosen, int speed, int jump, int seats, boolean whistle, int bond, int ridden,
            String name) implements CustomPacketPayload {
        public static final Type<View> TYPE = typeOf("mount_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.coins);
                    buf.writeVarInt(p.chapter);
                    buf.writeVarInt(p.owned);
                    buf.writeVarInt(p.chosen);
                    buf.writeVarInt(p.speed);
                    buf.writeVarInt(p.jump);
                    buf.writeVarInt(p.seats);
                    buf.writeBoolean(p.whistle);
                    buf.writeVarInt(p.bond);
                    buf.writeVarInt(p.ridden);
                    buf.writeUtf(p.name);
                },
                buf -> new View(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));

        public boolean owns(MountLook look) {
            return (owned & (1 << look.ordinal())) != 0;
        }

        public int tier(int kind) {
            return kind == 0 ? speed : kind == 1 ? jump : seats;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Close() implements CustomPacketPayload {
        public static final Type<Close> TYPE = typeOf("mount_close");
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> STREAM_CODEC = StreamCodec.unit(new Close());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Act(int action, int arg) implements CustomPacketPayload {
        public static final Type<Act> TYPE = typeOf("mount_act");
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.action);
                    buf.writeVarInt(p.arg);
                },
                buf -> new Act(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Rename(String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = typeOf("mount_rename");
        public static final StreamCodec<RegistryFriendlyByteBuf, Rename> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.name, 64),
                buf -> new Rename(buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Closed() implements CustomPacketPayload {
        public static final Type<Closed> TYPE = typeOf("mount_closed");
        public static final StreamCodec<RegistryFriendlyByteBuf, Closed> STREAM_CODEC = StreamCodec.unit(new Closed());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
