package jp.revati.skyabyss.util;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 人 (UUID) ごとの小さな覚え書き (最後に踏んだ時刻など) を持つ static の表の掃除の口。
// `PlayerMaps.track(new HashMap<>())` で包んだ表は、その人がログアウトしたときに行が消える。
// 遊びの状態 (試練の途中・倒れ・乗り物など) は各係が自分で消す。ここは「消し忘れても困らないが残り続ける」表のため
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PlayerMaps {
    private static final List<Map<UUID, ?>> TRACKED = new CopyOnWriteArrayList<>();

    private PlayerMaps() {
    }

    public static <M extends Map<UUID, ?>> M track(M map) {
        TRACKED.add(map);
        return map;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        for (Map<UUID, ?> m : TRACKED) {
            m.remove(id);
        }
    }

    // 自己テスト用: 追いかけている表の数
    public static int trackedCount() {
        return TRACKED.size();
    }
}
