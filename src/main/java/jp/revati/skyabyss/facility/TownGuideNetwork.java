package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 都の案内のパケット (TownGuide)。ほかとぶつからないよう別の版 (town-guide-1) で登録する
//   Menu (→ クライアント): 施設の一覧 (ID・種類・戸の位置)。TownGuideScreen を開く
//   Pick (→ サーバー)    : 選んだ施設 (空 = やめる)
//   Path (→ クライアント): 選んだ施設までの道 (間引いた点の列)。id が空 = 消す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TownGuideNetwork {
    private static final String PROTOCOL_VERSION = "town-guide-1";

    private TownGuideNetwork() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name));
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Menu.TYPE, Menu.STREAM_CODEC, (p, c) -> jp.revati.skyabyss.client.guide.TownGuideClient.menu(p))
                .playToClient(Path.TYPE, Path.STREAM_CODEC, (p, c) -> jp.revati.skyabyss.client.guide.TownGuideClient.path(p))
                .playToServer(Pick.TYPE, Pick.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        TownGuide.pick(player, p.id());
                    }
                });
    }

    public record Entry(String id, int group, BlockPos door) {
    }

    public record Menu(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Menu> TYPE = typeOf("town_guide_menu");
        public static final StreamCodec<RegistryFriendlyByteBuf, Menu> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entries.size());
                    for (Entry e : p.entries) {
                        buf.writeUtf(e.id());
                        buf.writeVarInt(e.group());
                        buf.writeBlockPos(e.door());
                    }
                },
                buf -> {
                    int n = Math.min(buf.readVarInt(), 64);
                    List<Entry> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(new Entry(buf.readUtf(64), buf.readVarInt(), buf.readBlockPos()));
                    }
                    return new Menu(list);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Pick(String id) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = typeOf("town_guide_pick");
        public static final StreamCodec<RegistryFriendlyByteBuf, Pick> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.id, 64), buf -> new Pick(buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // reached = 戸まで着く道 (false なら近づけた所まで)
    public record Path(String id, int group, BlockPos door, boolean reached, List<BlockPos> nodes) implements CustomPacketPayload {
        public static final Path CLEAR = new Path("", 0, BlockPos.ZERO, false, List.of());
        public static final Type<Path> TYPE = typeOf("town_guide_path");
        public static final StreamCodec<RegistryFriendlyByteBuf, Path> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.id, 64);
                    buf.writeVarInt(p.group);
                    buf.writeBlockPos(p.door);
                    buf.writeBoolean(p.reached);
                    buf.writeVarInt(p.nodes.size());
                    for (BlockPos b : p.nodes) {
                        buf.writeBlockPos(b);
                    }
                },
                buf -> {
                    String id = buf.readUtf(64);
                    int group = buf.readVarInt();
                    BlockPos door = buf.readBlockPos();
                    boolean reached = buf.readBoolean();
                    int n = Math.min(buf.readVarInt(), 4096);
                    List<BlockPos> nodes = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        nodes.add(buf.readBlockPos());
                    }
                    return new Path(id, group, door, reached, nodes);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
