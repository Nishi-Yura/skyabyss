package jp.revati.skyabyss.record;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 控え帳を見せる・最新にする・受け取る。誰が誰のを見てよいかは、ここで毎回検める (サーバー権威)。
//  - 自分の控え帳: 誰でも
//  - ほかの人の控え帳: 管理者 (権限 2) だけ。権限を外されたら、開いている画面にも次から送らない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RecordService {
    // 画面からの「最新にして」は、この間隔 (tick) より詰めて受け付けない
    static final int REQUEST_INTERVAL = 10;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final Map<UUID, Long> LAST_CLAIM = new HashMap<>();

    private RecordService() {
    }

    public static boolean mayView(ServerPlayer viewer, ServerPlayer target) {
        return viewer.getUUID().equals(target.getUUID()) || viewer.hasPermissions(2);
    }

    // 画面を開く (コマンドから)。見てよくなければ false
    public static boolean open(ServerPlayer viewer, ServerPlayer target, String tab) {
        if (!mayView(viewer, target)) {
            viewer.sendSystemMessage(ModText.deny("skyabyss_record.deny.inspect"));
            return false;
        }
        send(viewer, target, true, tab == null ? "" : tab);
        return true;
    }

    static void send(ServerPlayer viewer, ServerPlayer target, boolean open, String tab) {
        if (viewer instanceof FakePlayer || viewer.connection == null) {
            return;
        }
        RecordSnapshot snapshot = RecordBuilder.build(target, viewer.getUUID().equals(target.getUUID()));
        PacketDistributor.sendToPlayer(viewer, new RecordNetwork.Show(snapshot, open, tab));
    }

    private static boolean throttled(Map<UUID, Long> map, ServerPlayer player) {
        long now = player.server.getTickCount();
        Long last = map.get(player.getUUID());
        if (last != null && now - last < REQUEST_INTERVAL && now >= last) {
            return true;
        }
        map.put(player.getUUID(), now);
        return false;
    }

    static void request(ServerPlayer viewer, UUID targetId) {
        if (throttled(LAST_REQUEST, viewer)) {
            return;
        }
        ServerPlayer target = viewer.server.getPlayerList().getPlayer(targetId);
        if (target == null || !mayView(viewer, target)) {
            // いなくなった・見てはいけない: 何も送らない (画面は最後の中身のまま)
            return;
        }
        send(viewer, target, false, "");
    }

    static void claim(ServerPlayer player) {
        if (throttled(LAST_CLAIM, player)) {
            return;
        }
        TitanLoot.deliver(player, false);
        send(player, player, false, "");
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_REQUEST.remove(event.getEntity().getUUID());
        LAST_CLAIM.remove(event.getEntity().getUUID());
    }
}
