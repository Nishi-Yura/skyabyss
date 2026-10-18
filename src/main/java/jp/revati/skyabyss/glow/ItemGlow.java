package jp.revati.skyabyss.glow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.glow.ItemGlowClient;
import jp.revati.skyabyss.record.Keepsakes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 自分だけが拾える落とし物 (落ちてくる頁・伝承の書・章の頁・守りの欠片や鍵・倒した人に確保された戦利品) を、その持ち主の画面でだけ光らせる。
// バニラの発光 (共有の印) は全員に同期されるので使わない。サーバーが持ち主ごとに「光らせる実体の id と種類」を送り (10 tick ごと、近く 64 マス)、
// クライアントが自分のコピーにだけ発光の印と色つきのチームを付ける (ItemGlowClient)。他の人の画面では普通の落とし物のまま。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ItemGlow {
    private static final String PROTOCOL_VERSION = "glow-1";
    public static final byte PAGE = 0; // 頁・書・戦利品: 淡い金
    public static final byte KEEPSAKE = 1; // 守りの品 (欠片・鍵): 水色
    private static final int PERIOD = 10;
    private static final double RANGE = 64;
    // 直前に「光らせるものがあった」人 (空になったとき一度だけ空の一覧を送るため)
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private ItemGlow() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(Sync.TYPE, Sync.STREAM_CODEC, (payload, context) -> ItemGlowClient.receive(payload));
    }

    // この落とし物を拾える (確保された) 人。いなければ null
    public static UUID ownerOf(ItemEntity item) {
        if (item.isRemoved() || item.getItem().isEmpty()) {
            return null;
        }
        if (Keepsakes.guarded(item.getItem())) {
            return Keepsakes.owner(item);
        }
        return item.getTarget();
    }

    public static byte kindOf(ItemEntity item) {
        return Keepsakes.guarded(item.getItem()) ? KEEPSAKE : PAGE;
    }

    // この人のために光らせる実体 (id, 種類) を集める
    public static List<int[]> collect(ServerLevel level, UUID owner, double x, double y, double z) {
        List<int[]> list = new ArrayList<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(x, y, z, x, y, z).inflate(RANGE))) {
            if (owner.equals(ownerOf(item))) {
                list.add(new int[] {item.getId(), kindOf(item)});
            }
        }
        return list;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % PERIOD != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            List<int[]> list = collect(player.serverLevel(), player.getUUID(), player.getX(), player.getY(), player.getZ());
            if (list.isEmpty() && !ACTIVE.contains(player.getUUID())) {
                continue;
            }
            if (list.isEmpty()) {
                ACTIVE.remove(player.getUUID());
            } else {
                ACTIVE.add(player.getUUID());
            }
            int[] ids = new int[list.size()];
            byte[] kinds = new byte[list.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = list.get(i)[0];
                kinds[i] = (byte) list.get(i)[1];
            }
            PacketDistributor.sendToPlayer(player, new Sync(ids, kinds));
        }
    }

    // サーバー → 持ち主: 自分に光らせる実体の一覧 (空なら全部消す)
    public record Sync(int[] ids, byte[] kinds) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "item_glow"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.ids.length);
                    for (int i = 0; i < p.ids.length; i++) {
                        buf.writeVarInt(p.ids[i]);
                        buf.writeByte(p.kinds[i]);
                    }
                },
                buf -> {
                    int n = buf.readVarInt();
                    int[] ids = new int[n];
                    byte[] kinds = new byte[n];
                    for (int i = 0; i < n; i++) {
                        ids[i] = buf.readVarInt();
                        kinds[i] = buf.readByte();
                    }
                    return new Sync(ids, kinds);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
