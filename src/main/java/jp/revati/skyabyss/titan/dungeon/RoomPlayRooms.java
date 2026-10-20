package jp.revati.skyabyss.titan.dungeon;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.RoomPlay;
import jp.revati.skyabyss.combat.RoomPlay.Room;
import jp.revati.skyabyss.combat.RoomPlay.Theme;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import net.minecraft.core.BlockPos;

// 広さの使い方 (RoomPlay) を入れる戦いの部屋の一覧 (設計書 26 の 8.7・29 の 2.8)。巨神ごと。
// 入れる所: 岩鯨の四つの広間 (E1・E2・詰所・番兵長)、試練の階の砦 (W10)・棺の廊下 (W11)・大貝の護送 (T11)、鞴持ちの炉 (E10)、嵐の巣 (S09)。
// 円い闘技場 (W12・T10) と一台ずつの台 (S10) と、巣・水路・炉の口から湧く部屋 (干潟・庭・火床) は、部屋の作りが敵の出どころを決めているので、
// 湧きを入口へ寄せるだけか、そのままにする (各部屋のコードに書いてある)
public final class RoomPlayRooms {
    private RoomPlayRooms() {
    }

    private static List<Room> all;

    public static synchronized List<Room> all() {
        if (all == null) {
            List<Room> out = new ArrayList<>();
            // 岩鯨の背
            out.add(hall("e1", TitanBackLayout.E1, TitanBackLayout.E1_ENTRANCE));
            out.add(hall("ck", TitanBackLayout.CK, TitanBackLayout.CK_ENTRANCE));
            out.add(hall("e3", TitanBackLayout.E3, TitanBackLayout.E3_ENTRANCE));
            // 試練の階 (岩鯨)
            List<BlockPos> slots = new ArrayList<>();
            for (int i = 0; i < jp.revati.skyabyss.trial.whale.W10Fort.SLOTS.length; i++) {
                slots.add(jp.revati.skyabyss.trial.whale.W10Fort.slotSpawn(i));
            }
            Box w10 = WhaleDeck.W10;
            out.add(new Room(Titans.STONE_WHALE, "w10", Theme.BONE, w10.x1(), w10.x2(), w10.z1(), w10.z2(), w10.floorY(), w10.height(),
                    entrance(WhaleDeck.W10_GATE, w10.floorY()), slots));
            Box w11 = WhaleDeck.W11;
            out.add(new Room(Titans.STONE_WHALE, "w11", Theme.BONE, w11.x1(), w11.x2(), w11.z1(), w11.z2(), w11.floorY(), w11.height(),
                    entrance(WhaleDeck.W11_GATE, w11.floorY()), List.of()));
            // 潮殻 (試練の階の下の階。大貝の護送 T11 は棺の廊下と同じ作り。真珠守りの試し T12 の円い広間は、湧きを人のそばに寄せるだけ)
            var t11 = jp.revati.skyabyss.titan.tide.back.TideDeck.T11;
            out.add(new Room(Titans.TIDE_SHELL, "t11", Theme.TIDE, t11.x1(), t11.x2(), t11.z1(), t11.z2(), t11.floor(), t11.height(),
                    entrance(jp.revati.skyabyss.titan.tide.back.TideDeck.T11_GATE, t11.floor()), List.of()));
            // 灰の巨神 (試練の階の炉)
            var b = jp.revati.skyabyss.titan.ember.back.EmberDeck.B;
            List<BlockPos> e10 = new ArrayList<>();
            for (int i = 0; i < jp.revati.skyabyss.trial.ember.E10Furnace.SPAWNS.length; i++) {
                e10.add(jp.revati.skyabyss.trial.ember.E10Furnace.spawnAt(i));
            }
            for (int[] v : jp.revati.skyabyss.trial.ember.E10Furnace.VENTS) {
                e10.add(new BlockPos(v[0], b.minY() + 1, v[1]));
            }
            e10.add(new BlockPos(jp.revati.skyabyss.trial.ember.E10Furnace.FURNACE_X, b.minY() + 1, jp.revati.skyabyss.trial.ember.E10Furnace.FURNACE_Z));
            out.add(new Room(Titans.EMBER, "e10", Theme.EMBER, b.minX(), b.maxX(), b.minZ(), b.maxZ(), b.minY(), 7,
                    entrance(jp.revati.skyabyss.titan.ember.back.EmberDeck.E10_GATE, b.minY()), e10));
            // 雷の翼 (試練の甲板の嵐の巣。円い台の半径 13、中心 (36, 104)。入口は西の渡り)
            int y = jp.revati.skyabyss.titan.storm.back.StormDeck.Y;
            List<BlockPos> rods = new ArrayList<>(jp.revati.skyabyss.titan.storm.back.StormDeck.s09Rods());
            out.add(new Room(Titans.THUNDER_WING, "s09", Theme.STORM, 23, 49, 91, 117, y, 12, new BlockPos(25, y + 1, 99), rods));
            all = List.copyOf(out);
        }
        return all;
    }

    public static List<Room> of(String titan) {
        List<Room> out = new ArrayList<>();
        for (Room r : all()) {
            if (r.titan().equals(titan)) {
                out.add(r);
            }
        }
        return out;
    }

    public static Room byId(String id) {
        for (Room r : all()) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    private static Room hall(String id, Box box, List<BlockPos> entrance) {
        return new Room(Titans.STONE_WHALE, id, Theme.BONE, box.x1(), box.x2(), box.z1(), box.z2(), box.floorY(), box.height(),
                entrance(entrance, box.floorY()), EastWingBuilder.spawnPoints(box));
    }

    // 戸のブロックの、水平の真ん中 (床の一つ上)
    public static BlockPos entrance(List<BlockPos> door, int floorY) {
        long x = 0;
        long z = 0;
        for (BlockPos p : door) {
            x += p.getX();
            z += p.getZ();
        }
        int n = Math.max(1, door.size());
        return new BlockPos((int) Math.round((double) x / n), floorY + 1, (int) Math.round((double) z / n));
    }

    // その広間の入口 (TitanBackTrials が湧かせる場所を寄せるのに使う)
    public static BlockPos entranceOf(String hallId) {
        Room r = byId(hallId);
        return r == null ? BlockPos.ZERO : r.entrance();
    }

}
