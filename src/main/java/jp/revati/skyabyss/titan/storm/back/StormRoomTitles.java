package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

// 部屋の入口の一行 (26 の 3.3・25 の 3章): 入った部屋の名前と、その部屋の決まり (教える一つの決まり、ひねり) を、入ったときに一度だけ一行で言う。
// 同じ人は、背中を出るまで同じ部屋では繰り返さない。lang は message.skyabyss.storm.room.<id>。道を読むための帯であって、閉じる扉ではない
final class StormRoomTitles {
    // 部屋 (id と箱)。入る順の目安。箱は床の 1 上から天井まで
    record Room(String id, AABB box) {
    }

    private static AABB wingBox(int x1, int x2) {
        return new AABB(x1, BONE_FLOOR, BONE_Z1, x2 + 1, BONE_FLOOR + BONE_HEIGHT + 1, BONE_Z2 + 1);
    }

    static final List<Room> ROOMS = buildRooms();

    private static List<Room> buildRooms() {
        java.util.ArrayList<Room> r = new java.util.ArrayList<>();
        r.add(new Room("plaza", TAIL_PLAZA.aabb()));
        r.add(new Room("hub", HUB.aabb()));
        r.add(new Room("w0", W0.aabb()));
        r.add(new Room("valley1", wingBox(WEST_GAPS[0][0] - 2, WEST_UPPER_X2)));
        r.add(new Room("island", WEST_ISLAND.aabb()));
        r.add(new Room("valley2", wingBox(WEST_UPPER_X1, WEST_GAPS[1][1] + 2)));
        r.add(new Room("elbow", new AABB(WEST_ELBOW.getX() - ELBOW_R, BONE_FLOOR, WZ - ELBOW_R, WEST_ELBOW.getX() + ELBOW_R + 1, BONE_FLOOR + 12, WZ + ELBOW_R + 1)));
        r.add(new Room("rib", RIB_HALL.aabb()));
        r.add(new Room("vault_w", WEST_VAULT.aabb()));
        r.add(new Room("e0", E0.aabb()));
        int from = EAST_X1;
        for (int i = 0; i < EAST_DOORS.length; i++) {
            r.add(new Room("circuit" + (i + 1), new AABB(from, BONE_FLOOR, BONE_Z1, EAST_DOORS[i], BONE_FLOOR + EAST_HEIGHT + 1, BONE_Z2 + 1)));
            from = EAST_DOORS[i] + 1;
        }
        r.add(new Room("east_ante", new AABB(from, BONE_FLOOR, BONE_Z1, EAST_X2 + 1, BONE_FLOOR + EAST_HEIGHT + 1, BONE_Z2 + 1)));
        r.add(new Room("vault_e", EAST_VAULT.aabb()));
        r.add(new Room("crown", F0.aabb()));
        r.add(new Room("eye", EYE_HALL.aabb()));
        r.add(new Room("beak", BEAK_ROOM.aabb()));
        r.add(new Room("heart_ante", KEEL_ANTE.aabb()));
        // 任意の翼 (StormExtra。外の台は除く)
        for (StormExtra.Zone z : StormExtra.titledZones()) {
            r.add(new Room(z.id(), z.area().aabb()));
        }
        // 試練の甲板 (StormDeck)
        if (StormDeck.placed()) {
            for (StormDeck.Zone z : StormDeck.zones()) {
                r.add(new Room(z.id(), z.box()));
            }
        }
        return List.copyOf(r);
    }

    private static final Map<UUID, Set<String>> SHOWN = new HashMap<>();

    private StormRoomTitles() {
    }

    static void clear() {
        SHOWN.clear();
    }

    // StormGuide.tick から (10 tick ごと)
    static void tick(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) {
                continue;
            }
            Set<String> seen = SHOWN.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
            for (Room r : ROOMS) {
                if (r.box().contains(p.position()) && seen.add(r.id())) {
                    Notice.bar(p, ModText.hint("message.skyabyss.storm.room." + r.id()));
                    break;
                }
            }
        }
        SHOWN.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }
}
