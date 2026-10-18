package jp.revati.skyabyss.grave;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;


// 語り部の救いで、どの墓を戻すかを選ぶ (会話の結果 grave_choose が開く一覧の画面。クライアントは client/grave/GraveScreen)。
// サーバーが一覧を送り、選んだ墓の id が返る。会話から開いたときだけ有効 (OFFER_TICKS のあいだ) で、自分の墓以外・救いが 0% のときは断る。
// 救うたびに残りの一覧を送り直す (なくなれば空を送って画面を閉じる)
public final class GraveChooser {
    // 一覧を開いてから、選べる時間 (tick。5 分)
    static final long OFFER_TICKS = 6000L;

    private static final Map<UUID, Long> OFFERS = new HashMap<>();

    private GraveChooser() {
    }

    // 救える墓の一覧 (古い順)
    public static List<GraveNetwork.Entry> entries(ServerPlayer player) {
        long now = Graves.now(player.server);
        List<GraveNetwork.Entry> list = new ArrayList<>();
        for (GraveData.Grave g : GraveData.get(player.server).owned(player.getUUID())) {
            list.add(new GraveNetwork.Entry(g.id, g.dimension.location().toString(), g.pos.getX(), g.pos.getY(), g.pos.getZ(), g.itemCount(), g.xp,
                    Math.max(0L, now - g.created)));
        }
        return list;
    }

    // 一覧の画面を開く。救える墓がなければ「呼び戻せる墓はない」
    public static void open(ServerPlayer player) {
        if (!Graves.rescueAvailable(player)) {
            GraveRescue.report(player, null);
            return;
        }
        OFFERS.put(player.getUUID(), Graves.now(player.server) + OFFER_TICKS);
        send(player);
    }

    // 画面が開いているか (選べるか)
    static boolean offered(ServerPlayer player) {
        Long until = OFFERS.get(player.getUUID());
        return until != null && Graves.now(player.server) <= until;
    }

    // 画面で墓が選ばれた。救えれば結果を返す (自己テストからも呼ぶ)
    @Nullable
    public static Graves.Rescued pick(ServerPlayer player, UUID id) {
        if (!offered(player)) {
            OFFERS.remove(player.getUUID());
            return null;
        }
        GraveData.Grave grave = GraveData.get(player.server).get(id);
        Graves.Rescued r = grave == null ? null : Graves.rescue(player, grave);
        if (r == null) {
            // 断った (もうない・自分の墓でない・救い 0%): 最新の一覧を送り直す
            if (!Graves.rescueAvailable(player)) {
                OFFERS.remove(player.getUUID());
            }
            send(player);
            return null;
        }
        GraveRescue.report(player, r);
        if (Graves.rescueAvailable(player)) {
            OFFERS.put(player.getUUID(), Graves.now(player.server) + OFFER_TICKS);
        } else {
            OFFERS.remove(player.getUUID());
        }
        send(player);
        return r;
    }

    private static void send(ServerPlayer player) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new GraveNetwork.Open(DeathRules.rescuePercent(player.server), entries(player)));
    }

    // 自己テスト用: 開いた状態か
    static boolean isOffered(ServerPlayer player) {
        return offered(player);
    }

    static void forget(ServerPlayer player) {
        OFFERS.remove(player.getUUID());
    }
}
