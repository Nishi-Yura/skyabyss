package jp.revati.skyabyss.titan.young;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.WhaleKeeper;
import jp.revati.skyabyss.titan.ember.EmberKeeper;
import jp.revati.skyabyss.titan.end.TitanDormancy;
import jp.revati.skyabyss.titan.solid.TitanSolids;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.travel.LandingFinder;
import jp.revati.skyabyss.titan.travel.TitanCalls;
import jp.revati.skyabyss.titan.travel.TravelDest;
import jp.revati.skyabyss.titan.travel.TravelNetwork;
import jp.revati.skyabyss.titan.travel.TravelState;
import jp.revati.skyabyss.titan.travel.TravelWorldData;
import jp.revati.skyabyss.titan.travel.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// 子の行き先 (海図の一覧) と、巨神ごとの得意による「届くか」の判定 (docs/design/28 の 4 章)。
// 行き先: はじまりの地・覚えた碑・出会った巨神 (今いる場所)。眠っている巨神と、まだ姿を見せていない巨神は選べない (理由を添える)。
// 届くか (判定は生地形の biome だけで行い、チャンクを作らない):
//   WHALE: どこへでも
//   STORM: 陸の行き先だけ。海の上は渡らない (160 ブロック以上続く海の上は飛べない)
//   EMBER: 陸の道が続く行き先だけ (深い水・海は渡らない。川は渡る)
//   TIDE : 海のある所の行き先 (行き先から 1024 ブロック以内に海がある)。陸は始めと終わりを短く転がるだけ
public final class YoungReach {
    // これより近い行き先へは運ばない
    public static final double MIN_DISTANCE = 64;
    // 海の上を飛べない長さ (雷翼の雛)
    private static final double STORM_SEA_LIMIT = 160;
    // 水の上を渡れない長さ (余燼の仔)
    private static final double EMBER_WATER_LIMIT = 64;
    private static final int SEA_SEARCH = 1024;
    private static final int SEA_STEP = 32;

    // 巨神の体積の名前 (TitanVolume)
    private static String volumeName(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> "tide_shell";
            case Titans.THUNDER_WING -> "thunder_wing";
            case Titans.EMBER -> "ember_titan";
            default -> "stone_whale";
        };
    }

    private YoungReach() {
    }

    // ---------------------------------------------------------------- 巨神の今の場所

    @Nullable
    private static Entity adultEntity(ServerLevel level, String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> WhaleKeeper.current(level);
            case Titans.TIDE_SHELL -> TideKeeper.KEEPER.current(level);
            case Titans.THUNDER_WING -> StormKeeper.KEEPER.current(level);
            case Titans.EMBER -> EmberKeeper.KEEPER.current(level);
            default -> null;
        };
    }

    // 巨神の今の場所 (読み込まれていなければ最後に見た場所)。まだ姿を見せていなければ null
    @Nullable
    public static Vec3 adultPos(ServerLevel level, String titan) {
        Entity e = adultEntity(level, titan);
        if (e != null && e.isAlive()) {
            return e.position();
        }
        TitanWorldData.Slot slot = TitanWorldData.get(level).slot(titan);
        return slot.spawned ? slot.lastPosition() : null;
    }

    private static float adultYaw(ServerLevel level, String titan) {
        Entity e = adultEntity(level, titan);
        if (e != null && e.isAlive()) {
            return e.getYRot();
        }
        return TitanWorldData.get(level).slot(titan).lastYaw;
    }

    // ---------------------------------------------------------------- 一覧

    public static List<TravelNetwork.Dest> list(ServerPlayer player, YoungKind kind, Vec3 origin) {
        ServerLevel level = player.server.overworld();
        List<TravelNetwork.Dest> list = new ArrayList<>();
        // 転生の絆 (docs/design/31 の B14): 持ち主の運ぶ速さの倍率 (所要時間の見積りに入れる)
        double bond = jp.revati.skyabyss.rebirth.Rebirth.youngSpeed(player.server, player.getUUID());
        list.add(build(level, kind, origin, "spawn", "", "", level.getSharedSpawnPos(), 0, true, bond));
        // 町 (都)。建っていれば、巨神の子は町にも運ぶ
        jp.revati.skyabyss.opening.OpeningSites.shelter(player.server).ifPresent(town -> list.add(build(level, kind, origin, "town", "", "", town, 4, true, bond)));
        TravelWorldData world = TravelWorldData.get(player.server);
        TravelState state = Voyages.state(player);
        for (BlockPos pos : state.waymarks) {
            TravelWorldData.Waymark mark = world.waymark(pos);
            list.add(build(level, kind, origin, "w" + pos.asLong(), mark == null ? "" : mark.name(), mark == null ? "" : mark.biome(), pos, 1, mark != null, bond));
        }
        for (String titan : Titans.all()) {
            if (!TitanCalls.known(titan) || !TitanCalls.met(player, titan)) {
                continue;
            }
            Vec3 at = adultPos(level, titan);
            String lock = "";
            if (TitanDormancy.isResting(player.server, titan)) {
                lock = "sleeping";
            } else if (at == null) {
                lock = "absent";
            }
            BlockPos pos = at == null ? level.getSharedSpawnPos() : BlockPos.containing(at);
            TravelNetwork.Dest d = build(level, kind, origin, "t:" + titan, titan, "", pos, 2, lock.isEmpty(), bond);
            if (!lock.isEmpty()) {
                d = new TravelNetwork.Dest(d.id(), d.name(), d.biome(), d.x(), d.y(), d.z(), d.kind(), false, d.seconds(), lock);
            }
            list.add(d);
        }
        // 巨神の祠 (docs/design/32 の 3.1。心臓を止めた巨神の祠だけ。祠は時間・天気・眠りで閉じない)
        for (String titan : Titans.all()) {
            BlockPos shrine = jp.revati.skyabyss.titan.shrine.TitanShrine.posOf(player.server, titan);
            if (shrine != null && jp.revati.skyabyss.progress.Progression.stopped(player.server, player.getUUID(), titan)) {
                list.add(build(level, kind, origin, "s:" + titan, titan, "", shrine.south(3), 3, true, bond));
            }
        }
        return list;
    }

    private static TravelNetwork.Dest build(ServerLevel level, YoungKind kind, Vec3 origin, String id, String name, String biome, BlockPos pos, int dkind,
                                            boolean valid, double bond) {
        double distance = Math.hypot(pos.getX() + 0.5 - origin.x, pos.getZ() + 0.5 - origin.z);
        String lock = valid && distance >= MIN_DISTANCE ? refuse(level, kind, origin, pos) : "";
        return new TravelNetwork.Dest(id, name, biome, pos.getX(), pos.getY(), pos.getZ(), dkind, valid && lock.isEmpty(),
                estimateSeconds(kind, distance, bond), lock);
    }

    // 運ぶのにかかるおおよその秒数 (画面に出す)
    public static int estimateSeconds(YoungKind kind, double distance) {
        return estimateSeconds(kind, distance, 1.0);
    }

    public static int estimateSeconds(YoungKind kind, double distance, double bond) {
        double speed = kind.speed * bond;
        double ticks = 160 /* 出発の待ち */ + 40 /* 舞い上がる */ + 120 /* 降りる */;
        if (distance > YoungTrip.VEIL_DISTANCE) {
            ticks += YoungTrip.CRUISE_BEFORE_VEIL + YoungTrip.VEIL_FADE * 2 + YoungTrip.VEIL_EXIT / speed * 1.2;
        } else {
            ticks += distance / speed * 1.2;
        }
        return (int) Math.round(ticks / 20.0);
    }

    public static Component destName(@Nullable TravelNetwork.Dest dest) {
        return TravelDest.destName(dest);
    }

    public static Component waymarkName(String biome) {
        return TravelDest.waymarkName(biome);
    }

    // ---------------------------------------------------------------- 届くか

    // 届かない理由 (lang の鍵の末尾。message.skyabyss.young.reach.<理由>)。届くなら空
    public static String refuse(ServerLevel level, YoungKind kind, Vec3 from, BlockPos dest) {
        double distance = Math.hypot(dest.getX() + 0.5 - from.x, dest.getZ() + 0.5 - from.z);
        if (distance > kind.maxRange) {
            return "too_far";
        }
        switch (kind.gait) {
            case SKY -> {
                return "";
            }
            case RIDGE -> {
                if (sea(level, dest.getX(), dest.getZ())) {
                    return "no_land";
                }
                return seaRun(level, from, dest, STORM_SEA_LIMIT) ? "open_sea" : "";
            }
            case GROUND -> {
                if (sea(level, dest.getX(), dest.getZ()) || seaRun(level, from, dest, EMBER_WATER_LIMIT)) {
                    return "water";
                }
                return "";
            }
            case SEA -> {
                return seaNear(level, dest) == null ? "no_sea" : "";
            }
        }
        return "";
    }

    private static boolean sea(ServerLevel level, double x, double z) {
        Holder<Biome> b = level.getUncachedNoiseBiome(QuartPos.fromBlock(Mth.floor(x)), QuartPos.fromBlock(level.getSeaLevel() - 4), QuartPos.fromBlock(Mth.floor(z)));
        return b.is(BiomeTags.IS_OCEAN);
    }

    // 二点のあいだに、limit ブロック以上続く海があるか (32 ブロックごとの biome)
    private static boolean seaRun(ServerLevel level, Vec3 from, BlockPos to, double limit) {
        double d = Math.hypot(to.getX() - from.x, to.getZ() - from.z);
        int n = Math.max(1, (int) (d / 32));
        int run = 0;
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            if (sea(level, Mth.lerp(t, from.x, to.getX()), Mth.lerp(t, from.z, to.getZ()))) {
                run++;
                if (run * 32.0 >= limit) {
                    return true;
                }
            } else {
                run = 0;
            }
        }
        return false;
    }

    // 行き先にいちばん近い海 (地形を作らない)。dest が海ならそこ。見つからなければ null
    @Nullable
    public static BlockPos seaNear(ServerLevel level, BlockPos dest) {
        for (int r = 0; r <= SEA_SEARCH; r += SEA_STEP) {
            int steps = r == 0 ? 1 : Math.max(8, (int) Math.round(2 * Math.PI * r / SEA_STEP));
            for (int i = 0; i < steps; i++) {
                double angle = Math.PI * 2 * i / steps;
                int x = dest.getX() + (int) Math.round(Math.cos(angle) * r);
                int z = dest.getZ() + (int) Math.round(Math.sin(angle) * r);
                if (sea(level, x, z)) {
                    return new BlockPos(x, level.getSeaLevel(), z);
                }
            }
        }
        return null;
    }

    // 二点のあいだが海だけか (潮殻の稚貝が、陸を避けずに滑っていけるか)
    public static boolean seaOnly(ServerLevel level, Vec3 from, BlockPos to) {
        double d = Math.hypot(to.getX() - from.x, to.getZ() - from.z);
        int n = Math.max(1, (int) (d / 32));
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            if (!sea(level, Mth.lerp(t, from.x, to.getX()), Mth.lerp(t, from.z, to.getZ()))) {
                return false;
            }
        }
        return true;
    }

    // 笛を吹く場所のそばに水があるか (潮殻の稚貝が滑り出せる)。読み込まれている所だけ見る
    @Nullable
    public static BlockPos waterNear(ServerLevel level, BlockPos spot, int range) {
        int sea = level.getSeaLevel();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -range; dx <= range; dx += 2) {
            for (int dz = -range; dz <= range; dz += 2) {
                double d = dx * dx + dz * dz;
                if (d >= bestD || d > range * range) {
                    continue;
                }
                BlockPos p = new BlockPos(spot.getX() + dx, sea - 1, spot.getZ() + dz);
                if (level.isLoaded(p) && level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER) && level.getBlockState(p.above()).isAir()) {
                    best = p.above();
                    bestD = d;
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 降りる場所 (成体の体の外)

    // 子が止まる所 (pos = 足もと)。water = 水の上
    public record Pad(Vec3 pos, boolean water) {
    }

    // dest に着いたとき止まる所。成体なら、成体の体の外 (立入禁止の外) で from の側。碑・はじまりの地ならその近く。
    // チャンクを同期で読み込む (近づいてから 1 回だけ呼ぶ)
    public static Pad padFor(ServerLevel level, YoungKind kind, String destId, BlockPos destPos, Vec3 from) {
        Vec3 center = Vec3.atBottomCenterOf(destPos);
        double r0 = 0;
        if (destId.startsWith("t:")) {
            String titan = destId.substring(2);
            Vec3 at = adultPos(level, titan);
            if (at != null) {
                center = at;
                r0 = outsideBody(level, titan, at, from);
            }
        }
        double ang = Math.atan2(from.z - center.z, from.x - center.x);
        if (center.distanceToSqr(from) < 1) {
            ang = 0;
        }
        double x = center.x + Math.cos(ang) * r0;
        double z = center.z + Math.sin(ang) * r0;
        return settle(level, kind, x, z);
    }

    // 成体の体 (立入禁止) の外へ出るのに要る、中心からの距離 (from の側。いちばん近い 8 ブロック刻み)
    private static double outsideBody(ServerLevel level, String titan, Vec3 at, Vec3 from) {
        TitanVolume volume = TitanVolume.get(volumeName(titan));
        float yaw = adultYaw(level, titan);
        double ang = Math.atan2(from.z - at.z, from.x - at.x);
        for (double r = 40; r <= 400; r += 8) {
            double x = at.x + Math.cos(ang) * r;
            double z = at.z + Math.sin(ang) * r;
            double ground = groundY(level, x, z, at.y);
            boolean clear = true;
            for (double y : new double[] {0, ground - at.y, ground - at.y + 8}) {
                Vec3 local = TitanSolids.toLocal(at.x, at.y, at.z, yaw, new Vec3(x, at.y + y, z));
                if (volume.inKeepOut(local.x, local.y, local.z)) {
                    clear = false;
                }
            }
            if (clear) {
                return r + 16;
            }
        }
        return 220;
    }

    private static double groundY(ServerLevel level, double x, double z, double fallback) {
        if (!level.getChunkSource().hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4)) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    // (x, z) のそばの、子が止まれる所を探す (チャンクを読み込む)
    public static Pad settle(ServerLevel level, YoungKind kind, double x, double z) {
        int cx = Mth.floor(x) >> 4;
        int cz = Mth.floor(z) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk(cx + dx, cz + dz);
            }
        }
        BlockPos center = BlockPos.containing(x, level.getSeaLevel(), z);
        if (kind.gait == YoungKind.Gait.SEA) {
            BlockPos water = waterNear(level, center, 24);
            if (water != null) {
                return new Pad(new Vec3(water.getX() + 0.5, level.getSeaLevel() - 0.3, water.getZ() + 0.5), true);
            }
        }
        BlockPos land = LandingFinder.find(level, center, 0, kind.gait == YoungKind.Gait.GROUND ? 20 : 10);
        boolean water = level.getFluidState(land.below()).is(net.minecraft.tags.FluidTags.WATER);
        double y = water ? level.getSeaLevel() - 0.3 : land.getY();
        return new Pad(new Vec3(land.getX() + 0.5, y, land.getZ() + 0.5), water);
    }
}
