package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

// 潮殻の背の、占有する部屋 (RoomLocks。docs/design/08 の 7.5節)。
// 水・水路・巣・水位・取っ手のように、当たり判定や水が変わる仕掛けの部屋は、一度に一つのグループ (パーティ、または一人) だけが使う。
//  - 珊瑚の庭 (tide_garden): 道の形 (水路の掘り方)・真珠・大貝・庭の敵
//  - 潮見の間 (tide_watch): 三段の溜めの水位・浮き石・取っ手・重石の床・溜めの戸
//  - 群れの干潟 (tide_flats): 巣 (塚とエンティティ)・招き潮の水・潮招き・干潟の口
//  - 殻の底 (tide_heart): 満ち潮の水 (TideHeartBattle が持つ)
// 最初に入ったグループが持ち主になり、部屋をそのグループの種と進み (TideBackState.Group) から作り直す。
// ほかのグループが入ると部屋の入口の外へ戻す。持ち主が 20 秒いなければ手放す (敵を消し、真珠を戻し、戦いを戻す)。
// 解いた試練の部屋に持ち主として入っても、敵は湧かない (石の記録を見る)
public final class TideRooms {
    public static final String GARDEN = "tide_garden";
    public static final String WATCH = "tide_watch";
    public static final String FLATS = "tide_flats";

    private TideRooms() {
    }

    // ---------------------------------------------------------------- 部屋の箱と、追い出す先
    public static AABB gardenBox() {
        return new AABB(TideBackLayout.GARDEN.x1() - 1, GARDEN_FLOOR - 2, TideBackLayout.GARDEN.z1() - 1, TideBackLayout.GARDEN.x2() + 1,
                GARDEN_CEILING + 2, TideBackLayout.GARDEN.z2() + 1);
    }

    public static AABB watchBox() {
        return TideWatch.area();
    }

    public static AABB flatsBox() {
        return TideFlats.flatsBox();
    }

    static final BlockPos GARDEN_EJECT = EJECT_GARDEN;
    static final BlockPos WATCH_EJECT = EJECT_WATCH;
    static final BlockPos FLATS_EJECT = RESTS.get(4).spawn();

    private static final RoomLocks.Listener GARDEN_LISTENER = new RoomLocks.Listener() {
        @Override
        public void acquired(ServerLevel level, UUID group) {
            TideGarden.reset(level, TideBackState.of(level, group));
            TideBackTrials.initGardenPool(level);
        }

        @Override
        public void released(ServerLevel level, UUID formerOwner) {
            TideGarden.release(level, formerOwner);
        }
    };

    private static final RoomLocks.Listener WATCH_LISTENER = new RoomLocks.Listener() {
        @Override
        public void acquired(ServerLevel level, UUID group) {
            TideWatch.reset(level, TideBackState.of(level, group));
        }

        @Override
        public void released(ServerLevel level, UUID formerOwner) {
            TideWatch.release(level, formerOwner);
        }
    };

    private static final RoomLocks.Listener FLATS_LISTENER = new RoomLocks.Listener() {
        @Override
        public void acquired(ServerLevel level, UUID group) {
            TideFlats.reset(level, TideBackState.of(level, group));
        }

        @Override
        public void released(ServerLevel level, UUID formerOwner) {
            TideFlats.release(level, formerOwner);
        }
    };

    private static RoomLocks.Listener listener(String room) {
        return switch (room) {
            case GARDEN -> GARDEN_LISTENER;
            case WATCH -> WATCH_LISTENER;
            case FLATS -> FLATS_LISTENER;
            default -> null;
        };
    }

    // 10 tick ごと (TideBackTrials から)
    static void tick(ServerLevel level) {
        RoomLocks.update(level, GARDEN, gardenBox(), GARDEN_EJECT, RoomLocks.DEFAULT_GRACE, GARDEN_LISTENER);
        RoomLocks.update(level, WATCH, watchBox(), WATCH_EJECT, RoomLocks.DEFAULT_GRACE, WATCH_LISTENER);
        RoomLocks.update(level, FLATS, flatsBox(), FLATS_EJECT, RoomLocks.DEFAULT_GRACE, FLATS_LISTENER);
    }

    @Nullable
    public static UUID owner(String room) {
        return RoomLocks.owner(room);
    }

    // その人が部屋の持ち主のグループか
    public static boolean owns(String room, ServerPlayer player) {
        UUID owner = RoomLocks.owner(room);
        return owner != null && Groups.isMember(player, owner);
    }

    // 部屋をそのグループに持たせ、そのグループの進みから作り直す (自己テストとコマンド。持ち主が違えば前の持ち主の分を片付ける)
    public static void claim(ServerLevel level, String room, UUID group) {
        RoomLocks.Listener l = listener(room);
        UUID before = RoomLocks.claim(level, room, group);
        if (before != null && l != null) {
            l.released(level, before);
        }
        if (l != null) {
            l.acquired(level, group);
        }
    }

    // そのグループが持っている部屋を手放す (グループのリセット)
    static void releaseGroup(ServerLevel level, UUID group) {
        for (String room : new String[] {GARDEN, WATCH, FLATS}) {
            if (group.equals(RoomLocks.owner(room))) {
                RoomLocks.release(room);
                listener(room).released(level, group);
            }
        }
    }

    // すべて手放す (背中に誰もいなくなって久しいとき・建て直し)
    static void releaseAll(ServerLevel level) {
        for (String room : new String[] {GARDEN, WATCH, FLATS}) {
            UUID owner = RoomLocks.owner(room);
            if (owner != null) {
                RoomLocks.release(room);
                listener(room).released(level, owner);
            }
        }
    }

    // 部屋の持ち主を忘れる (サーバーが止まったとき)
    static void forget() {
        for (String room : new String[] {GARDEN, WATCH, FLATS}) {
            RoomLocks.release(room);
        }
    }
}
