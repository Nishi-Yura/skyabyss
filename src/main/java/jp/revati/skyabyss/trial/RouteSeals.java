package jp.revati.skyabyss.trial;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 順路の部屋を分ける封じ (docs/design/32 の 2.5。2026-10-22 の二人の試遊: Wynncraft のレイドのように、試練は一つずつ別の部屋)。
// 背の中は歩いてつながらない。入口の箱 (集まる所) から先へは歩けず、試練・中ボス・心臓へは運ばれて入る。背を建てたあとに置く:
//   1. 入口の箱の先: 到着の側から箱を通らずに届く空気のほかの、箱に接する空気 (= 箱の先へ続く口) を、封じた戸で埋める
//   2. 試練どうしをつなぐ広間 (鼓動の間・潮の広間・風の広間・炉心の広間と、岩鯨の入口の通路・雷翼の尾の間): 箱の外周 (壁・床・天井) の
//      抜け (内と外が空気でつながる所) を、封じた戸で埋める。広間そのものは使わない (誰も入らない)
// 封じは閉じた封印の扉 (SealedGateBlock の OPEN = false)。どのグループにも開けない。背の監査 (BackReach) は戸を開いたものとして歩くので形の検査は変わらない。
// 分かれているかは自己テスト "route rooms" (RouteRoomsSelfTest) が、封じを壁として歩いて確かめる。置いた所は背ごとの記録 (RouteSeals.Data)
public final class RouteSeals {
    private RouteSeals() {
    }

    // 入口の箱のまわりで、到着の側を探す広さ (箱の中心から)
    static final int FLOOD_RADIUS = 28;

    // 広間の箱 (内側。x1..x2, y1..y2 (床の上〜天井の下), z1..z2)
    public record Room(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
    }

    public static List<Room> junctions(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> {
                var hub = jp.revati.skyabyss.titan.dungeon.TitanBackLayout.HUB;
                var entry = jp.revati.skyabyss.titan.dungeon.TitanBackLayout.ENTRY;
                yield List.of(new Room("hub", hub.x1(), hub.floorY() + 1, hub.z1(), hub.x2(), hub.ceilingY() - 1, hub.z2()),
                        new Room("entry", entry.x1(), entry.floorY() + 1, entry.z1(), entry.x2(), entry.ceilingY() - 1, entry.z2()));
            }
            case Titans.TIDE_SHELL -> {
                var h = jp.revati.skyabyss.titan.tide.back.TideBackLayout.HUB;
                yield List.of(new Room("hub", h.x1(), h.floor() + 1, h.z1(), h.x2(), h.floor() + h.height(), h.z2()));
            }
            case Titans.THUNDER_WING -> {
                var h = jp.revati.skyabyss.titan.storm.back.StormBackLayout.HUB;
                var t = jp.revati.skyabyss.titan.storm.back.StormBackLayout.TAIL_HALL;
                yield List.of(new Room("hub", h.x1(), h.floor() + 1, h.z1(), h.x2(), h.floor() + h.height(), h.z2()),
                        new Room("tail hall", t.x1(), t.floor() + 1, t.z1(), t.x2(), t.floor() + t.height(), t.z2()));
            }
            case Titans.EMBER -> {
                var h = jp.revati.skyabyss.titan.ember.back.EmberBackLayout.HUB;
                yield List.of(new Room("hub", h.minX(), h.minY() + 1, h.minZ(), h.maxX(), h.maxY() - 1, h.maxZ()));
            }
            default -> List.of();
        };
    }

    // 封じた広間の中か (内側だけ。壁の灯りは隣の部屋も照らすので残す)。titan は巨神の名 (Titans) か、灯りの表・建築の監査の短い名 (whale・tide・storm・ember)。
    // 誰も入らないので、灯り・飾りは置かない (2026-10-22「誰も見ない豪華な建築は省いて」)。建築の監査の暗さも数えない
    public static boolean inHall(String titan, BlockPos p) {
        String t = switch (titan) {
            case "whale" -> Titans.STONE_WHALE;
            case "tide" -> Titans.TIDE_SHELL;
            case "storm" -> Titans.THUNDER_WING;
            case "ember" -> Titans.EMBER;
            default -> titan;
        };
        for (Room r : junctions(t)) {
            if (p.getX() >= r.x1() && p.getX() <= r.x2() && p.getY() >= r.y1() && p.getY() <= r.y2() && p.getZ() >= r.z1() && p.getZ() <= r.z2()) {
                return true;
            }
        }
        return false;
    }

    // 封じたあと、背の中で人を戻す所 (到着の台。入口の箱の手前で、広間・試練には歩いて入れない)。足の位置。
    // 広間 (鼓動の間など) へ戻していた所 (グループが変わった・リセット・心臓のあとの光・笛の光・休み場の記録が無い) はここへ
    public static BlockPos safeFeet(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.back.TideBackLayout.ARRIVAL;
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackLayout.ARRIVAL;
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBackLayout.ARRIVAL.above();
            default -> jp.revati.skyabyss.titan.dungeon.TitanBackLayout.arrival();
        };
    }

    // ---------------------------------------------------------------- 置く
    // 背を建てた最後に呼ぶ (何度呼んでも同じ)。置いた数
    public static int place(ServerLevel back, String titan) {
        long t0 = System.nanoTime();
        Data data = Data.get(back);
        LongOpenHashSet seals = new LongOpenHashSet();
        AABB box = TrialRoute.entryBox(titan);
        BlockPos arrival = jp.revati.skyabyss.titan.end.ReturnBeacons.arrivalOf(titan);
        if (box != null && arrival != null) {
            gather(back, box, arrival, seals);
        }
        for (Room r : junctions(titan)) {
            ring(back, r, seals);
        }
        BlockState gate = ModBlocks.SEALED_GATE.get().defaultBlockState();
        int n = 0;
        for (long k : seals) {
            BlockPos p = BlockPos.of(k);
            if (!back.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                back.setBlock(p, gate, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            n++;
        }
        data.set(seals);
        SkyAbyss.LOGGER.info("Route seals on the {}: {} blocks in {} ms", titan, n, (System.nanoTime() - t0) / 1_000_000L);
        return n;
    }

    // 通れる (当たりがなく、溶岩でない)。封じた所は通れない
    static boolean open(ServerLevel back, BlockPos p, LongOpenHashSet seals) {
        if (seals.contains(p.asLong())) {
            return false;
        }
        BlockState s = back.getBlockState(p);
        if (s.is(ModBlocks.SEALED_GATE.get())) {
            return true;
        }
        return s.getCollisionShape(back, p).isEmpty() && !s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }

    private static boolean inBox(AABB box, BlockPos p) {
        return p.getX() >= Math.floor(box.minX) && p.getX() < Math.ceil(box.maxX) && p.getY() >= Math.floor(box.minY) && p.getY() < Math.ceil(box.maxY)
                && p.getZ() >= Math.floor(box.minZ) && p.getZ() < Math.ceil(box.maxZ);
    }

    // 1. 入口の箱の先を封じる
    static void gather(ServerLevel back, AABB box, BlockPos arrival, LongOpenHashSet seals) {
        Vec3 c = box.getCenter();
        // 到着の側の、箱のすぐ外の空気 (箱のまんなかから到着の向きへ)
        Vec3 dir = new Vec3(arrival.getX() + 0.5 - c.x, 0, arrival.getZ() + 0.5 - c.z).normalize();
        BlockPos start = null;
        for (double d = 0; d < FLOOD_RADIUS && start == null; d += 0.5) {
            for (int dy = 0; dy < (int) Math.ceil(box.maxY - box.minY) && start == null; dy++) {
                BlockPos p = BlockPos.containing(c.x + dir.x * d, Math.floor(box.minY) + dy, c.z + dir.z * d);
                if (!inBox(box, p) && open(back, p, seals)) {
                    start = p;
                }
            }
        }
        if (start == null) {
            SkyAbyss.LOGGER.warn("Route seals: no air on the arrival side of the entry box {}", box);
            return;
        }
        LongOpenHashSet side = new LongOpenHashSet();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        side.add(start.asLong());
        q.add(start);
        double r2 = (double) FLOOD_RADIUS * FLOOD_RADIUS;
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (inBox(box, n) || side.contains(n.asLong()) || n.distToCenterSqr(c) > r2 || !open(back, n, seals)) {
                    continue;
                }
                side.add(n.asLong());
                q.add(n);
            }
        }
        // 箱に接する空気のうち、到着の側でないもの
        int x0 = (int) Math.floor(box.minX) - 1;
        int x1 = (int) Math.ceil(box.maxX);
        int y0 = (int) Math.floor(box.minY) - 1;
        int y1 = (int) Math.ceil(box.maxY);
        int z0 = (int) Math.floor(box.minZ) - 1;
        int z1 = (int) Math.ceil(box.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (inBox(box, p) || side.contains(p.asLong()) || !open(back, p, seals)) {
                        continue;
                    }
                    boolean touches = false;
                    for (Direction d : Direction.values()) {
                        touches |= inBox(box, p.relative(d));
                    }
                    if (touches) {
                        seals.add(p.asLong());
                    }
                }
            }
        }
    }

    // 2. 広間の外周の抜けを封じる (内側の空気と外側の空気に接する、外周の空気)
    static void ring(ServerLevel back, Room r, LongOpenHashSet seals) {
        for (int x = r.x1() - 1; x <= r.x2() + 1; x++) {
            for (int y = r.y1() - 1; y <= r.y2() + 1; y++) {
                for (int z = r.z1() - 1; z <= r.z2() + 1; z++) {
                    boolean inside = x >= r.x1() && x <= r.x2() && y >= r.y1() && y <= r.y2() && z >= r.z1() && z <= r.z2();
                    if (inside) {
                        continue;
                    }
                    BlockPos p = new BlockPos(x, y, z);
                    if (!open(back, p, seals)) {
                        continue;
                    }
                    boolean in = false;
                    boolean out = false;
                    for (Direction d : Direction.values()) {
                        BlockPos n = p.relative(d);
                        boolean nIn = n.getX() >= r.x1() && n.getX() <= r.x2() && n.getY() >= r.y1() && n.getY() <= r.y2() && n.getZ() >= r.z1() && n.getZ() <= r.z2();
                        if (!open(back, n, seals)) {
                            continue;
                        }
                        if (nIn) {
                            in = true;
                        } else if (!(n.getX() >= r.x1() - 1 && n.getX() <= r.x2() + 1 && n.getY() >= r.y1() - 1 && n.getY() <= r.y2() + 1
                                && n.getZ() >= r.z1() - 1 && n.getZ() <= r.z2() + 1)) {
                            out = true;
                        }
                    }
                    if (in && out) {
                        seals.add(p.asLong());
                    }
                }
            }
        }
    }

    public static int count(ServerLevel back) {
        return Data.get(back).seals.size();
    }

    // 背ごとの記録 (置いた封じの座標)
    public static final class Data extends SavedData {
        private static final String NAME = "skyabyss_route_seals";
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        final LongOpenHashSet seals = new LongOpenHashSet();

        public static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (long k : tag.getLongArray("seals")) {
                d.seals.add(k);
            }
            return d;
        }

        void set(LongOpenHashSet s) {
            seals.clear();
            seals.addAll(s);
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLongArray("seals", seals.toLongArray());
            return tag;
        }
    }

    @Nullable
    static List<BlockPos> list(ServerLevel back) {
        List<BlockPos> out = new ArrayList<>();
        for (long k : Data.get(back).seals) {
            out.add(BlockPos.of(k));
        }
        return out;
    }
}
