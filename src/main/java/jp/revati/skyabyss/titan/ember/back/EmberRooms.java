package jp.revati.skyabyss.titan.ember.back;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.group.RoomLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

// 余燼の背の、一度に一つのグループだけが入れる部屋 (部屋の占有 RoomLocks。docs/design/10 の 9 章、13 の 7.6節)。
// 溶岩の湖と冷え殻・炉の熱と扉の灯り・火床と噴気・熾の底の沈む床は、ブロックそのもの (当たり判定・溶岩) が動くので、見る人ごとに変えられない。
//   west   灰の道 (WEST)
//   east   鍛えの間と鍛え手の間 (EAST + SMITH。炉の扉の向こうの戦いまで一続き)
//   south  火守りの谷 (SOUTH)
//   heart  熾の底 (HEART。前室は含まない)
// 最初に入ったグループが持ち主。ほかのグループの人は広間 (心臓は前室) へ戻され「別のパーティーが挑んでいる」と出る。
// 持ち主が全員いなくなって grace たつと手放し、部屋を最初の形に戻す。新しい持ち主が入ると、部屋をそのグループの記録 (種・眠り) から作り直す。
// 部屋の中の試練の仕掛け (AshPath・Forge・Hearth・EmberHeartArena の static) は、いまの持ち主のためにだけ動く (EmberBackState.forRoom)
public final class EmberRooms {
    // 試練の部屋を手放すまで (持ち主が全員いなくなってから)。倒れて休み場の外で起きた人が戻ってくる間は待つ
    public static final int TRIAL_GRACE = 20 * 30;

    public record Room(String id, String lock, EmberBackLayout.Box box, BlockPos eject, List<String> parts, int grace) {
        public boolean contains(Vec3 p) {
            return box.contains(p);
        }
    }

    public static final Room WEST = new Room("west", "ember.west", EmberBackLayout.WEST, EmberBackLayout.EJECT_WEST, List.of("west"), TRIAL_GRACE);
    public static final Room EAST = new Room("east", "ember.east",
            new EmberBackLayout.Box(EmberBackLayout.EAST.minX(), EmberBackLayout.EAST.minY(), EmberBackLayout.EAST.minZ(), EmberBackLayout.SMITH.maxX(),
                    Math.max(EmberBackLayout.EAST.maxY(), EmberBackLayout.SMITH.maxY()), EmberBackLayout.SMITH.maxZ()),
            EmberBackLayout.EJECT_EAST, List.of("east", "smith"), TRIAL_GRACE);
    public static final Room SOUTH = new Room("south", "ember.south", EmberBackLayout.SOUTH, EmberBackLayout.EJECT_SOUTH, List.of("south"),
            TRIAL_GRACE);
    public static final Room HEART = new Room("heart", "ember.heart", EmberBackLayout.HEART, EmberBackLayout.rest("heart").floor().above(),
            List.of("heart"), RoomLocks.DEFAULT_GRACE);
    public static final List<Room> ALL = List.of(WEST, EAST, SOUTH, HEART);

    private EmberRooms() {
    }

    // 部屋の名前 (west・east・smith・south・heart) から、占有の部屋
    @Nullable
    public static Room of(String part) {
        for (Room r : ALL) {
            if (r.parts().contains(part)) {
                return r;
            }
        }
        return null;
    }

    // 部屋の名前の持ち主 (いなければ null)
    @Nullable
    public static UUID owner(String part) {
        Room r = of(part);
        return r == null ? null : RoomLocks.owner(r.lock());
    }

    @Nullable
    public static UUID owner(Room room) {
        return RoomLocks.owner(room.lock());
    }

    // その位置を含む占有の部屋 (なければ null)
    @Nullable
    public static Room at(Vec3 pos) {
        for (Room r : ALL) {
            if (r.contains(pos)) {
                return r;
            }
        }
        return null;
    }

    // その位置を含む部屋の持ち主 (部屋の外・持ち主なしなら null)
    @Nullable
    public static UUID ownerAt(Vec3 pos) {
        Room r = at(pos);
        return r == null ? null : owner(r);
    }

    // 10 tick ごと (EmberBackTrials から)。持ち主を決め直し、ほかのグループの人を外へ出す
    static void tick(ServerLevel level) {
        for (Room r : ALL) {
            RoomLocks.update(level, r.lock(), r.box().aabb(), r.eject(), r.grace(), new RoomLocks.Listener() {
                @Override
                public void acquired(ServerLevel l, UUID group) {
                    EmberBackRooms.resetRoom(l, r);
                }

                @Override
                public void released(ServerLevel l, UUID formerOwner) {
                    EmberBackRooms.resetRoom(l, r);
                }
            });
        }
    }

    // グループが持っている部屋を、その場で手放させて最初に戻す (グループのリセット)
    static void releaseHeldBy(ServerLevel level, UUID group) {
        for (Room r : ALL) {
            if (group.equals(owner(r))) {
                RoomLocks.release(r.lock());
                EmberBackRooms.resetRoom(level, r);
            }
        }
    }

    // すべての部屋を手放して最初に戻す (建てたあと・全部のリセット)
    static void releaseAll(ServerLevel level) {
        for (Room r : ALL) {
            RoomLocks.release(r.lock());
            EmberBackRooms.resetRoom(level, r);
        }
    }

}
