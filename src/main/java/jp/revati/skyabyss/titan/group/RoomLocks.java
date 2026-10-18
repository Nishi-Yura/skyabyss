package jp.revati.skyabyss.titan.group;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

// 部屋の占有 (docs/design/10 の 9 章)。
// 水・溶岩・足場・水路のように、当たり判定や水の動きが変わる仕掛けは、見る人ごとに変えられない。
// そういう部屋 (庭・潮見の間・干潟・灰の道・鍛えの間・火守りなど) は「一度に一つのグループだけ」が入れる。
//  - 最初に部屋へ入ったグループが持ち主になる。ほかのグループが入ると、入口の外へ戻して理由を出す
//  - 持ち主の誰かが中にいるあいだは持ち主のまま。持ち主が全員いなくなって grace tick たつと手放す (onRelease で部屋を最初に戻す)
//  - 次に入ったグループが持ち主になるとき (onAcquire)、部屋はそのグループの記録から作り直す
// 部屋の進み (解けたか) はグループの記録にあるので、解いたグループは部屋に戻る必要がない
// (試練の部屋は行き止まりの枝で、解いたあとは石を鳴らした記録だけが要る)。
// 状態はメモリにだけある (サーバーを止めれば全部手放す。部屋はその時点でリセットされる)。tick は 10 tick ごとに呼ぶ
public final class RoomLocks {
    public static final int DEFAULT_GRACE = 20 * 20;

    private record Lock(UUID owner, long lastInside) {
    }

    private static final Map<String, Lock> LOCKS = new HashMap<>();

    private RoomLocks() {
    }

    public interface Listener {
        // 新しい持ち主になった (部屋をそのグループの進みから作り直す)
        void acquired(ServerLevel level, UUID group);

        // 手放した (誰もいない。部屋を最初に戻す)
        void released(ServerLevel level, UUID formerOwner);
    }

    @Nullable
    public static UUID owner(String room) {
        Lock lock = LOCKS.get(room);
        return lock == null ? null : lock.owner;
    }

    // いまの持ち主を決め直す。部屋の中にいる人 (生きていて観戦者でない) の箱と、追い出し先を渡す。持ち主 (いなければ null) を返す
    @Nullable
    public static UUID update(ServerLevel level, String room, AABB box, BlockPos eject, int grace, Listener listener) {
        List<ServerPlayer> inside = level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator());
        return update(level, room, inside, grace, listener, p -> {
            p.fallDistance = 0;
            p.teleportTo(level, eject.getX() + 0.5, eject.getY(), eject.getZ() + 0.5, p.getYRot(), 0f);
            Notice.bar(p, ModText.deny("message.skyabyss.room_busy"));
        });
    }

    // 部屋の中にいる人を渡す形 (自己テスト用にも使う)。持ち主でないグループの人は ejector で外へ出す
    @Nullable
    public static UUID update(ServerLevel level, String room, List<ServerPlayer> inside, int grace, Listener listener,
            java.util.function.Consumer<ServerPlayer> ejector) {
        long now = level.getGameTime();
        Lock lock = LOCKS.get(room);
        if (lock != null) {
            boolean ownerInside = false;
            for (ServerPlayer p : inside) {
                if (Groups.isMember(p, lock.owner)) {
                    ownerInside = true;
                    break;
                }
            }
            if (ownerInside) {
                lock = new Lock(lock.owner, now);
                LOCKS.put(room, lock);
            } else if (now - lock.lastInside >= grace) {
                LOCKS.remove(room);
                if (listener != null) {
                    listener.released(level, lock.owner);
                }
                lock = null;
            }
        }
        if (lock == null && !inside.isEmpty()) {
            UUID first = Groups.keyOf(inside.get(0));
            lock = new Lock(first, now);
            LOCKS.put(room, lock);
            if (listener != null) {
                listener.acquired(level, first);
            }
        }
        if (lock == null) {
            return null;
        }
        for (ServerPlayer p : inside) {
            if (!Groups.isMember(p, lock.owner)) {
                ejector.accept(p);
            }
        }
        return lock.owner;
    }

    // 持ち主をその場で手放させる (部屋を終えた・リセットしたとき)
    public static void release(String room) {
        LOCKS.remove(room);
    }

    public static void releaseAll() {
        LOCKS.clear();
    }

    // グループが持っている部屋の名前
    public static List<String> heldBy(UUID group) {
        return LOCKS.entrySet().stream().filter(e -> e.getValue().owner.equals(group)).map(Map.Entry::getKey).toList();
    }
}
