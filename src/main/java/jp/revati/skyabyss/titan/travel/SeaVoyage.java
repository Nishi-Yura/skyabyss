package jp.revati.skyabyss.titan.travel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.TideOcean;
import jp.revati.skyabyss.titan.tide.TideSetup;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// 潮殻の巨神での移動 (09 の 16 章)。岩鯨の旅 (Voyage) と同じ段階 (迎え・乗る・決める・運ぶ・降ろす) を、海を歩く巨神の形でやる。
//
//   COMING   沖から歩いてきて、迎えの水際の沖で甲羅の縁を水の上に出して止まる (潮門を呼んだ人の側へ向ける)
//   WAITING  水際に泡の柱を立てる。柱に入ると、潮に持ち上げられて潮門の前 → 甲羅の肩 → 頂の座席へ運ばれる
//   BOARDING 行き先が決まった。少し待って柱をたたむ
//   ASCENT   (潮殻では) 沈んで歩く深さになり、行き先の海へ向き直る。甲羅の頂だけが海から出た島に乗っている形
//   CRUISE   海と浅瀬を歩く (18 ブロック/秒)。前に陸が見えれば向きを変えて避ける。浅瀬では体が持ち上がる
//   VEIL     (潮殻では「潮の深み」) 海の向こうへ続く旅は、深い潮の流れに乗って行き先の沖へ移る (画面が深い青に包まれる)
//   APPROACH 行き先にいちばん近い海の浜を探し、その沖へ歩いて止まる
//   LOWERING 浜へ泡の柱を立て、潮が一人ずつ浜へ運ぶ。陸の奥の行き先なら「〇〇まで、ここから北へ およそ N ブロック」
final class SeaVoyage extends Voyage {
    // ---- 数値 (09 の 16.5 節)
    // 海を歩く速さ (18 ブロック/秒)。岩鯨 (28) より遅く、海の上を低く行く
    static final double SEA_SPEED = 0.9;
    // 旅で歩く深さ: 底が海面の 24 下 (甲羅の頂が海から 10 ブロック出る)。浅瀬では海面の 12 下まで持ち上がる
    static final double TRAVEL_DRAFT = 24;
    static final double SHALLOW_DRAFT = 12;
    // 海だけを通って 700 ブロックまでの旅は、本当に歩いていく。それより遠い・陸を越える旅は潮の深みで縮める
    static final double DIRECT_LIMIT = 700;
    // 潮の深みから出る所: 行き先の海の手前 140 ブロック
    static final double CURRENT_EXIT = 140;
    // 潮の深みへ入るまで歩く時間 (tick)
    static final int CURRENT_AFTER_MIN = 160;
    static final int CURRENT_AFTER_MAX = 320;
    // 呼んだ人のまわりで、寄れる浅瀬を探す範囲。迎えの泡の柱を立てる水際を探す範囲
    static final double PARK_RANGE = 80;
    static final int SHORE_RANGE = 10;
    // 潮が人を運ぶ速さ (ブロック/tick)
    static final double CARRY_SPEED = 0.75;
    // 行き先の近くの海を探す範囲 (ブロック) と、刻み
    static final int SEA_SEARCH = 1024;
    static final int SEA_STEP = 32;
    // これより遠い浜で降ろしたら、行き先までの方角と距離を知らせる
    static final double FAR_LANDING = 48;

    // 止まる所 (中心と向き)
    record Park(Vec3 center, float yaw) {
    }

    @Nullable
    Park park;
    boolean relocatedOnce;
    // 行き先にいちばん近い海の点 (行き先を決めたときに決める)
    @Nullable
    BlockPos seaTarget;
    // 海だけを通る短い旅か
    boolean direct;
    // 陸を避けている向きと残り
    float avoidYaw;
    int avoidTicks;
    int blockedChecks;
    // 潮が人を運ぶ道すじ
    final Map<UUID, List<Vec3>> carry = new HashMap<>();

    SeaVoyage(TitanKind kind, TravelTitan titan, ServerPlayer leader, BlockPos pickup) {
        super(kind, titan, leader, pickup);
    }

    // ---------------------------------------------------------------- 呼ぶ前に (Voyages.call から)

    // 迎えの場所: 寄れる浅瀬が近くにあるときの、呼んだ人のいちばん近くの水際 (海面の高さ)。海辺でなければ null
    @Nullable
    static BlockPos findPickup(ServerLevel level, ServerPlayer player) {
        if (parkNear(level, player.position()) == null) {
            return null;
        }
        return shoreWater(level, player.blockPosition(), SHORE_RANGE);
    }

    // spot のまわり range の、海面に水のあるいちばん近い所 (泡の柱を立てる)。なければ spot の足もと
    static BlockPos shoreWater(ServerLevel level, BlockPos spot, int range) {
        int sea = level.getSeaLevel();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                double d = dx * dx + dz * dz;
                if (d >= bestD || d > range * range) {
                    continue;
                }
                BlockPos p = new BlockPos(spot.getX() + dx, sea - 1, spot.getZ() + dz);
                if (level.isLoaded(p) && level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p.above()).isAir()) {
                    best = p.above();
                    bestD = d;
                }
            }
        }
        return best != null ? best : spot;
    }

    // spot に潮門を向けて止まれる浅瀬 (読み込まれている所だけ)。まず干潮の休み場所の決まり (深さ 6〜14、岸から 40)、なければ深さ 5 以上の海
    @Nullable
    static Park parkNear(ServerLevel level, Vec3 spot) {
        Vec3 rest = TideOcean.findRestSpot(level, spot.x, spot.z);
        if (rest != null) {
            return new Park(new Vec3(rest.x, level.getSeaLevel() - TideShellEntity.REST_DRAFT, rest.z), (float) rest.y);
        }
        for (double r = 24; r <= PARK_RANGE; r += 8) {
            for (int a = 0; a < 16; a++) {
                double angle = Math.PI * 2 * a / 16;
                double x = spot.x + Math.cos(angle) * r;
                double z = spot.z + Math.sin(angle) * r;
                if (TideOcean.walkable(level, x, z, 5)) {
                    // 巨神の左 (潮門の側) を spot へ向ける
                    float yaw = (float) (Mth.atan2(spot.z - z, spot.x - x) * Mth.RAD_TO_DEG);
                    return new Park(new Vec3(x, level.getSeaLevel() - TideShellEntity.REST_DRAFT, z), yaw);
                }
            }
        }
        return null;
    }

    // 遠い・読み込まれていない潮殻を呼び寄せる所: 迎えの場所の沖 140〜220 の海 (なければ、前にいた方角の 160 先)
    static Vec3 entryPoint(ServerLevel level, BlockPos pickup, double angle) {
        Vec3 sea = TideOcean.findOceanPoint(level, level.random, pickup.getX(), pickup.getZ(), 140, 220, 8, 24);
        double y = level.getSeaLevel() - TRAVEL_DRAFT;
        if (sea != null) {
            return new Vec3(sea.x, y, sea.z);
        }
        return new Vec3(pickup.getX() + Math.cos(angle) * 160, y, pickup.getZ() + Math.sin(angle) * 160);
    }

    // ---------------------------------------------------------------- 行き先

    // 行き先にいちばん近い海 (地形を作らず、バイオームの元の数式だけで探す)。dest が海ならそこ。見つからなければ null
    @Nullable
    static BlockPos seaNear(ServerLevel level, BlockPos dest) {
        int y = QuartPos.fromBlock(level.getSeaLevel() - 4);
        for (int r = 0; r <= SEA_SEARCH; r += SEA_STEP) {
            int steps = r == 0 ? 1 : Math.max(8, (int) Math.round(2 * Math.PI * r / SEA_STEP));
            for (int i = 0; i < steps; i++) {
                double angle = Math.PI * 2 * i / steps;
                int x = dest.getX() + (int) Math.round(Math.cos(angle) * r);
                int z = dest.getZ() + (int) Math.round(Math.sin(angle) * r);
                Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), y, QuartPos.fromBlock(z));
                if (biome.is(BiomeTags.IS_OCEAN)) {
                    return new BlockPos(x, level.getSeaLevel(), z);
                }
            }
        }
        return null;
    }

    // 二点のあいだが海 (海・浜・川のバイオーム) だけか (32 ブロックごとに見る)
    static boolean seaRoute(ServerLevel level, BlockPos from, BlockPos to) {
        double d = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        int n = Math.max(1, (int) (d / 32));
        int y = QuartPos.fromBlock(level.getSeaLevel() - 4);
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            int x = (int) Mth.lerp(t, from.getX(), to.getX());
            int z = (int) Mth.lerp(t, from.getZ(), to.getZ());
            Holder<Biome> b = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), y, QuartPos.fromBlock(z));
            if (!b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_BEACH) && !b.is(BiomeTags.IS_RIVER)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Nullable
    String accept(TravelNetwork.Dest d) {
        if (test && seaTarget != null) {
            return null;
        }
        BlockPos destPos = new BlockPos(d.x(), d.y(), d.z());
        BlockPos sea = seaNear(level(), destPos);
        if (sea == null) {
            return "message.skyabyss.travel.tide_shell.no_sea_dest";
        }
        seaTarget = sea;
        return null;
    }

    @Override
    void chosen(TravelNetwork.Dest d) {
        super.chosen(d);
        if (seaTarget == null) {
            BlockPos s = seaNear(level(), destPos());
            seaTarget = s != null ? s : destPos();
        }
        double distance = horizontal(Vec3.atCenterOf(pickup), Vec3.atCenterOf(seaTarget));
        direct = distance <= DIRECT_LIMIT && seaRoute(level(), pickup, seaTarget);
        SkyAbyss.LOGGER.info("Tide voyage: destination {} -> nearest sea {} ({} blocks, {})", destPos(), seaTarget, (int) distance,
                direct ? "walking all the way" : "through the deep current");
    }

    // 見積もり: 出発の待ち 8 秒、沈む 6 秒、歩く (または歩いて潮の深みへ)、浜の沖へ寄る 20 秒、降ろす 8 秒
    @Override
    int estimate(double distance) {
        double seconds = TravelRules.BOARDING_COUNTDOWN / 20.0 + 2 + 6;
        if (distance <= DIRECT_LIMIT) {
            seconds += distance / (SEA_SPEED * 20) * 1.25;
        } else {
            seconds += CURRENT_AFTER_MIN / 20.0 + 3 + CURRENT_EXIT / (SEA_SPEED * 20) * 1.3;
        }
        return (int) Math.round(seconds + 20 + 8);
    }

    // ---------------------------------------------------------------- 歩き方

    // 旅で歩く高さ (底の y): 前方の海底に合わせ、深い海では海面の 24 下、浅瀬では 12 下まで持ち上がる
    double walkY(ServerLevel level, Entity shell) {
        double sea = level.getSeaLevel();
        Vec3 ahead = shell.position().add(TideShellEntity.offset(shell.getYRot(), 0, 0, 30));
        double floor = TideOcean.floorY(level, ahead.x, ahead.z);
        if (Double.isNaN(floor)) {
            return sea - TRAVEL_DRAFT;
        }
        return Mth.clamp(floor - 1, sea - TRAVEL_DRAFT, sea - SHALLOW_DRAFT);
    }

    // 止まる所へ歩いていき、向きを合わせる。着いたら true
    boolean walkTo(ServerLevel level, Entity shell, Park target) {
        Vec3 c = target.center();
        double dist = horizontal(shell.position(), c);
        double restY = level.getSeaLevel() - TideShellEntity.REST_DRAFT;
        double ty = dist > 60 ? walkY(level, shell) : restY;
        double vy = Mth.clamp((ty - shell.getY()) * 0.04, -0.2, 0.2);
        float yaw = shell.getYRot();
        if (dist > 8) {
            yaw = Mth.approachDegrees(yaw, yawTowards(shell.position(), c), dist > 40 ? 1.2f : 2.0f);
            double want = Mth.clamp(dist * 0.02, 0.1, SEA_SPEED);
            speed = speed < want ? Math.min(want, speed + 0.012) : want;
            move(shell, yaw, speed, vy);
        } else {
            // 近くでは、決めた向きへ合わせながらまっすぐ寄せる
            yaw = Mth.approachDegrees(yaw, target.yaw(), 2.0f);
            shell.setYRot(yaw);
            speed = Math.min(speed, 0.2);
            Vec3 d = new Vec3(c.x - shell.getX(), 0, c.z - shell.getZ());
            Vec3 h = d.lengthSqr() > 1e-6 ? d.normalize().scale(Math.min(Math.max(0.05, speed), d.length())) : Vec3.ZERO;
            Vec3 motion = new Vec3(h.x, vy, h.z);
            shell.setDeltaMovement(motion);
            shell.move(MoverType.SELF, motion);
        }
        return dist < 1.0 && Math.abs(shell.getY() - ty) < 0.6 && Math.abs(Mth.degreesDifference(yaw, target.yaw())) < 2f;
    }

    private void snapTo(Entity shell, Park target) {
        snaps++;
        shell.setYRot(target.yaw());
        shell.teleportTo(target.center().x, target.center().y, target.center().z);
    }

    // ---------------------------------------------------------------- 段階

    @Override
    void tickComing(ServerLevel level, Entity shell) {
        if (relocateTo != null) {
            // 沖の入口が読み込まれたら移す (岩鯨と同じ)
            super.tickComing(level, shell);
            return;
        }
        if (park == null) {
            park = parkNear(level, Vec3.atBottomCenterOf(pickup));
            if (park == null) {
                // 寄れる浅瀬が消えた (潮が引いた、地形が変わった): 迎えの場所の沖
                float yaw = yawTowards(shell.position(), Vec3.atCenterOf(pickup)) + 90f;
                park = new Park(new Vec3(pickup.getX() + 0.5, level.getSeaLevel() - TideShellEntity.REST_DRAFT, pickup.getZ() + 0.5), yaw);
            }
            if (!relocatedOnce && horizontal(shell.position(), park.center()) > 260) {
                // 遠い: 潮の流れに乗って沖の入口へ (呼び寄せ)
                relocatedOnce = true;
                Vec3 entry = entryPoint(level, pickup, Math.atan2(shell.getZ() - pickup.getZ(), shell.getX() - pickup.getX()));
                relocateTo = entry;
                return;
            }
        }
        boolean arrived = walkTo(level, shell, park);
        if (arrived || phaseTicks > TravelRules.COMING_LIMIT) {
            if (!arrived) {
                snapTo(shell, park);
            }
            shell.setDeltaMovement(Vec3.ZERO);
            speed = 0;
            titan.setVoyageChain(pickup.getY());
            setPhase(Phase.WAITING);
            level.playSound(null, shell.getX(), level.getSeaLevel(), shell.getZ(), TideSetup.SURF.get(), SoundSource.AMBIENT, 10f, 0.9f);
            level.playSound(null, shell.getX(), level.getSeaLevel(), shell.getZ(), TideSetup.SONG.get(), SoundSource.AMBIENT, 16f, 0.9f);
            ServerPlayer lead = player(leader);
            if (lead != null) {
                lead.sendSystemMessage(ModText.lore(kind.text("message.skyabyss.travel.arrived_pickup")));
            }
        }
    }

    @Override
    void tickWaiting(ServerLevel level, Entity shell) {
        super.tickWaiting(level, shell);
        tickColumn(level, pickup, true);
    }

    @Override
    void tickBoarding(ServerLevel level, Entity shell) {
        if (phaseTicks < TravelRules.BOARDING_COUNTDOWN) {
            tickColumn(level, pickup, true);
        }
        super.tickBoarding(level, shell);
    }

    // 泡の柱 (乗り場・降ろす所): 泡と飛沫、潮門の前までの白い筋。柱に入った人を乗せる
    void tickColumn(ServerLevel level, BlockPos spot, boolean board) {
        double sea = level.getSeaLevel();
        if (totalTicks % 3 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, spot.getX() + 0.5, sea - 0.8, spot.getZ() + 0.5, 8, 0.35, 0.4, 0.35, 0.05);
            level.sendParticles(ParticleTypes.SPLASH, spot.getX() + 0.5, sea + 0.4, spot.getZ() + 0.5, 6, 0.5, 0.2, 0.5, 0.1);
        }
        if (totalTicks % 10 == 0) {
            Vec3 dock = titan.boardingPath().get(0);
            Vec3 from = new Vec3(spot.getX() + 0.5, sea + 0.1, spot.getZ() + 0.5);
            int n = (int) Math.min(40, from.distanceTo(dock) / 2);
            for (int i = 0; i <= n; i++) {
                Vec3 p = from.lerp(dock, n == 0 ? 0 : (double) i / n);
                level.sendParticles(ParticleTypes.BUBBLE_POP, p.x, sea + 0.1, p.z, 1, 0.3, 0, 0.3, 0.0);
            }
        }
        if (!board) {
            return;
        }
        if (totalTicks % 40 == 0) {
            for (ServerPlayer p : nearbyPlayers(level, Vec3.atCenterOf(spot), 24)) {
                if (!involves(p.getUUID())) {
                    p.displayClientMessage(ModText.hint(kind.text("message.skyabyss.travel.chain_hint")), true);
                }
            }
        }
        for (ServerPlayer p : level.players()) {
            if (involves(p.getUUID()) || p.isPassenger() || p.isSpectator() || !p.isAlive()) {
                continue;
            }
            if (Math.hypot(p.getX() - (spot.getX() + 0.5), p.getZ() - (spot.getZ() + 0.5)) < 1.8 && Math.abs(p.getY() - spot.getY()) < 3.0) {
                grab(p);
            }
        }
    }

    @Override
    void tickAscent(ServerLevel level, Entity shell) {
        // 沈んで歩く深さになりながら、行き先の海へ向き直る
        BlockPos target = seaTarget != null ? seaTarget : destFeet();
        holdTicket(level, new ChunkPos(target));
        float yaw = Mth.approachDegrees(shell.getYRot(), yawTowards(shell.position(), Vec3.atCenterOf(target)), 1.6f);
        speed = Math.min(0.45, speed + 0.01);
        double ty = walkY(level, shell);
        move(shell, yaw, speed, Mth.clamp((ty - shell.getY()) * 0.05, -0.25, 0.25));
        if (Math.abs(shell.getY() - ty) < 1 || phaseTicks > 200) {
            double distance = horizontal(shell.position(), Vec3.atCenterOf(target));
            cruiseFor = direct ? Integer.MAX_VALUE : (int) Mth.clamp(CURRENT_AFTER_MIN + distance / 40, CURRENT_AFTER_MIN, CURRENT_AFTER_MAX);
            setPhase(Phase.CRUISE);
        }
    }

    @Override
    void tickCruise(ServerLevel level, Entity shell) {
        BlockPos target = seaTarget != null ? seaTarget : destFeet();
        holdTicket(level, new ChunkPos(target));
        Vec3 goal = Vec3.atCenterOf(target);
        double distance = horizontal(shell.position(), goal);
        float desired = yawTowards(shell.position(), goal);
        if (totalTicks % 10 == 0) {
            steerAroundLand(level, shell, desired);
        }
        if (avoidTicks > 0) {
            avoidTicks--;
            desired = avoidYaw;
        }
        float yaw = Mth.approachDegrees(shell.getYRot(), desired, 1.2f);
        speed = Math.min(SEA_SPEED, speed + 0.012);
        move(shell, yaw, speed, Mth.clamp((walkY(level, shell) - shell.getY()) * 0.04, -0.2, 0.2));
        if (totalTicks % 20 == 0) {
            // 甲羅の縁で波が割れ、飛沫がかかる
            for (ServerPlayer rider : players(riders)) {
                level.sendParticles(rider, ParticleTypes.SPLASH, true, rider.getX(), level.getSeaLevel() + 1, rider.getZ(), 30, 8, 0.5, 8, 0.2);
                level.sendParticles(rider, ParticleTypes.BUBBLE_POP, true, rider.getX(), level.getSeaLevel() + 0.2, rider.getZ(), 20, 10, 0.1, 10, 0.0);
            }
        }
        if (totalTicks % 120 == 0) {
            level.playSound(null, shell.getX(), level.getSeaLevel(), shell.getZ(), TideSetup.SURF.get(), SoundSource.AMBIENT, 6f, 0.9f + level.random.nextFloat() * 0.2f);
        }
        boolean stuck = blockedChecks >= 4 && !direct;
        if ((phaseTicks >= cruiseFor || stuck) && distance > CURRENT_EXIT + 120) {
            setPhase(Phase.VEIL);
            veiled = true;
            veilMovedAt = -1;
            for (ServerPlayer rider : players(riders)) {
                PacketDistributor.sendToPlayer(rider, new TravelNetwork.Veil(true, kind.style().ordinal()));
                rider.playNotifySound(kind.veilSound().get(), SoundSource.AMBIENT, 1f, 1f);
                rider.sendSystemMessage(ModText.lore(kind.text("message.skyabyss.travel.veil")));
            }
            return;
        }
        if (distance < 150) {
            setPhase(Phase.APPROACH);
        }
    }

    // 前方 40・80 ブロックが陸 (歩けない) なら、左右に向きを振って海のある方を探す
    void steerAroundLand(ServerLevel level, Entity shell, float desired) {
        if (clear(level, shell.position(), shell.getYRot())) {
            blockedChecks = 0;
            return;
        }
        for (float off : new float[] {25f, -25f, 50f, -50f, 80f, -80f, 115f, -115f}) {
            if (clear(level, shell.position(), desired + off)) {
                avoidYaw = desired + off;
                avoidTicks = 60;
                blockedChecks = 0;
                return;
            }
        }
        blockedChecks++;
    }

    private static boolean clear(ServerLevel level, Vec3 at, float yaw) {
        for (double d : new double[] {40, 80}) {
            Vec3 p = at.add(TideShellEntity.offset(yaw, 0, 0, d));
            if (TideOcean.loaded(level, p.x, p.z) && !TideOcean.walkable(level, p.x, p.z, 4)) {
                return false;
            }
        }
        return true;
    }

    // 潮の深み: 深い青に包まれ、行き先の海の手前 140 へ移る
    @Override
    void tickVeil(ServerLevel level, Entity shell) {
        BlockPos target = seaTarget != null ? seaTarget : destFeet();
        holdTicket(level, new ChunkPos(target));
        Vec3 goal = Vec3.atCenterOf(target);
        if (veilMovedAt < 0) {
            if (veilExit == null) {
                Vec3 from = shell.position();
                Vec3 dir = new Vec3(goal.x - from.x, 0, goal.z - from.z).normalize();
                veilExit = new Vec3(goal.x - dir.x * CURRENT_EXIT, 0, goal.z - dir.z * CURRENT_EXIT);
            }
            holdMoveTicket(level, new ChunkPos(BlockPos.containing(veilExit)));
            speed = Math.max(0.3, speed - 0.03);
            move(shell, shell.getYRot(), speed, -0.05);
            boolean ready = loaded(level, veilExit) && loaded(level, goal);
            if (phaseTicks >= TravelRules.VEIL_FADE + 10 && (ready || phaseTicks > TravelRules.VEIL_WAIT_LIMIT)) {
                Vec3 exit = veilExit;
                if (ready && !TideOcean.walkable(level, exit.x, exit.z, 4) && TideOcean.walkable(level, goal.x, goal.z, 4)) {
                    // 出口が陸だった: 行き先の海そのものへ
                    exit = goal;
                }
                shell.teleportTo(exit.x, level.getSeaLevel() - TRAVEL_DRAFT, exit.z);
                shell.setYRot(yawTowards(shell.position(), goal));
                veilMovedAt = phaseTicks;
                veilExit = null;
                SkyAbyss.LOGGER.info("Tide voyage: moved {} through the deep current to {}, {}", kind.id(), (int) exit.x, (int) exit.z);
            }
            return;
        }
        move(shell, shell.getYRot(), speed, 0);
        if (phaseTicks - veilMovedAt >= 25) {
            for (ServerPlayer rider : players(riders)) {
                PacketDistributor.sendToPlayer(rider, new TravelNetwork.Veil(false, kind.style().ordinal()));
            }
            veiled = false;
            setPhase(Phase.APPROACH);
        }
    }

    // 行き先にいちばん近い浜の沖へ
    @Override
    void tickApproach(ServerLevel level, Entity shell) {
        BlockPos target = seaTarget != null ? seaTarget : destFeet();
        holdTicket(level, new ChunkPos(target));
        if (landing == null) {
            if (!level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4) && phaseTicks < 200) {
                move(shell, shell.getYRot(), Math.max(0.2, speed *= 0.98), 0);
                return;
            }
            chooseLanding(level, target);
        }
        if (park == null || landingChanged) {
            landingChanged = false;
            park = parkNear(level, Vec3.atBottomCenterOf(landing));
            if (park == null) {
                float yaw = yawTowards(shell.position(), Vec3.atCenterOf(landing)) + 90f;
                park = new Park(new Vec3(target.getX() + 0.5, level.getSeaLevel() - TideShellEntity.REST_DRAFT, target.getZ() + 0.5), yaw);
            }
        }
        boolean arrived = walkTo(level, shell, park);
        if (arrived || phaseTicks > TravelRules.COMING_LIMIT) {
            if (!arrived) {
                snapTo(shell, park);
            }
            shell.setDeltaMovement(Vec3.ZERO);
            speed = 0;
            setPhase(Phase.LOWERING);
            level.playSound(null, shell.getX(), level.getSeaLevel(), shell.getZ(), TideSetup.SURF.get(), SoundSource.AMBIENT, 10f, 0.8f);
            double far = horizontal(Vec3.atCenterOf(landing), Vec3.atCenterOf(destPos()));
            for (ServerPlayer rider : players(riders)) {
                showTitle(rider, Component.literal(Voyages.destName(dest).getString()),
                        Component.translatable(kind.text("title.skyabyss.travel.arrive.sub")));
                if (far > FAR_LANDING) {
                    rider.sendSystemMessage(ModText.hint("message.skyabyss.travel.tide_shell.far_landing", Voyages.destName(dest),
                            Component.translatable("message.skyabyss.travel.compass." + compass(landing, destPos())), (int) Math.round(far)));
                }
            }
        }
    }

    boolean landingChanged;

    // 降ろす浜: 行き先の海から行き先へ向かって、最初の陸の立てる所。行き先まで陸がなければ (行き先が水辺) 行き先そのもの
    void chooseLanding(ServerLevel level, BlockPos sea) {
        BlockPos destination = destPos();
        Vec3 from = Vec3.atCenterOf(sea);
        Vec3 to = Vec3.atCenterOf(destination);
        double d = horizontal(from, to);
        Vec3 dir = d < 1 ? Vec3.ZERO : new Vec3(to.x - from.x, 0, to.z - from.z).normalize();
        BlockPos found = null;
        for (double s = 0; s <= Math.min(d, 480) && found == null; s += 6) {
            double x = from.x + dir.x * s;
            double z = from.z + dir.z * s;
            // 浜を探すときだけ、道すじのチャンクを読み込む (画面は潮の深みや、浜の沖への歩きの最中)
            level.getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
            if (TideOcean.land(level, x, z)) {
                Vec3 shore = TideOcean.standableShore(level, x, z);
                if (shore != null) {
                    found = BlockPos.containing(shore);
                }
            }
        }
        // 行き先まで陸がない (行き先が水の上・海の中): 行き先のまわりのいちばん近い浜。水の上に降ろさない。
        // 読み込まれている所だけを見る (行き先のまわりは札で読み込まれている)
        for (int r = 6; r <= 72 && found == null; r += 6) {
            for (int i = 0; i < 24 && found == null; i++) {
                double angle = Math.PI * 2 * i / 24;
                double x = to.x + Math.cos(angle) * r;
                double z = to.z + Math.sin(angle) * r;
                if (TideOcean.land(level, x, z)) {
                    Vec3 shore = TideOcean.standableShore(level, x, z);
                    if (shore != null) {
                        found = BlockPos.containing(shore);
                    }
                }
            }
        }
        if (found == null) {
            found = LandingFinder.find(level, dest != null && dest.kind() == 1 ? destination.above() : destination, 1, 10);
        }
        landing = found;
        landingChanged = true;
        SkyAbyss.LOGGER.info("Tide voyage: landing on the beach at {} ({} blocks from the destination)", landing,
                (int) horizontal(Vec3.atCenterOf(landing), to));
    }

    // 8 方位 (北が -z)
    static String compass(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return names[Math.floorMod((int) Math.round(angle / 45.0), 8)];
    }

    @Override
    void tickLowering(ServerLevel level, Entity shell) {
        BlockPos spot = landing != null ? landing : destFeet();
        tickColumn(level, spot, false);
        super.tickLowering(level, shell);
    }

    // ---------------------------------------------------------------- 潮が人を運ぶ (鎖の代わり)

    @Override
    void tickClimbs(ServerLevel level) {
        for (UUID id : new ArrayList<>(climbs.keySet())) {
            ServerPlayer p = player(id);
            Climb c = climbs.get(id);
            if (p == null || !p.isAlive() || p.level() != level) {
                climbs.remove(id);
                carry.remove(id);
                if (p != null) {
                    p.setNoGravity(false);
                }
                continue;
            }
            c.ticks++;
            List<Vec3> route = carry.computeIfAbsent(id, k -> {
                List<Vec3> r = new ArrayList<>();
                r.add(p.position().add(0, 2.5, 0));
                r.addAll(arc(p.position().add(0, 2.5, 0), titan.boardingPath().get(0)));
                r.addAll(titan.boardingPath());
                return r;
            });
            if (step(level, p, c, route) || c.ticks > TravelRules.CLIMB_LIMIT * 2) {
                climbs.remove(id);
                carry.remove(id);
                p.setNoGravity(false);
                p.setDeltaMovement(Vec3.ZERO);
                seat(p);
            }
        }
    }

    @Override
    void tickLowers(ServerLevel level) {
        BlockPos spot = phase == Phase.LOWERING ? (landing != null ? landing : destFeet()) : pickup;
        for (UUID id : new ArrayList<>(lowers.keySet())) {
            ServerPlayer p = player(id);
            Climb c = lowers.get(id);
            if (p == null || !p.isAlive() || p.level() != level) {
                lowers.remove(id);
                carry.remove(id);
                if (p != null) {
                    p.setNoGravity(false);
                }
                continue;
            }
            c.ticks++;
            List<Vec3> route = carry.computeIfAbsent(id, k -> {
                List<Vec3> path = new ArrayList<>(titan.boardingPath());
                java.util.Collections.reverse(path);
                List<Vec3> r = new ArrayList<>(path);
                Vec3 end = new Vec3(spot.getX() + 0.5, Math.max(spot.getY(), level.getSeaLevel()) + 0.6, spot.getZ() + 0.5);
                r.addAll(arc(path.get(path.size() - 1), end));
                r.add(end);
                return r;
            });
            if (step(level, p, c, route)) {
                lowers.remove(id);
                carry.remove(id);
                releaseLower(p, false);
            } else if (c.ticks > TravelRules.LOWER_LIMIT) {
                lowers.remove(id);
                carry.remove(id);
                // 届かなかった: 浜の上へ移してから放す (海に落とさない)
                p.teleportTo(spot.getX() + 0.5, Math.max(spot.getY(), level.getSeaLevel()) + 0.2, spot.getZ() + 0.5);
                releaseLower(p, true);
            }
        }
    }

    // 道すじの次の点へ運ぶ。終わりまで来たら true
    private boolean step(ServerLevel level, ServerPlayer p, Climb c, List<Vec3> route) {
        p.fallDistance = 0;
        if (c.stage >= route.size()) {
            return true;
        }
        Vec3 point = route.get(c.stage);
        Vec3 velocity = towards(p.position(), point, CARRY_SPEED);
        if (p.position().distanceTo(point) < 1.0) {
            c.stage++;
        }
        if (c.ticks % 4 == 0) {
            level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.2, p.getZ(), 6, 0.4, 0.2, 0.4, 0.1);
            level.sendParticles(ParticleTypes.BUBBLE_POP, p.getX(), p.getY() + 0.5, p.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
        }
        if (c.ticks % 30 == 1) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), TideSetup.SURF.get(), SoundSource.PLAYERS, 0.8f, 1.3f);
            p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, false, false, false));
        }
        p.setDeltaMovement(velocity);
        p.hurtMarked = true;
        return false;
    }

    // 波に乗って運ばれるような弧 (a から b へ、まん中が 4 ブロック高い)。端は含まない
    static List<Vec3> arc(Vec3 a, Vec3 b) {
        List<Vec3> out = new ArrayList<>();
        int n = (int) Math.max(2, Math.min(16, a.distanceTo(b) / 4));
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            Vec3 p = a.lerp(b, t);
            out.add(p.add(0, Math.sin(t * Math.PI) * 4, 0));
        }
        return out;
    }

    @Override
    void tellAboard(ServerLevel level, String key) {
        // 潮殻の背 (tide_back) の人には知らせない (背中の島は、潮殻の体とは別の場所として遊ぶ)
    }

    // 降ろした場所の地面の高さ (自己テスト用)
    static int surface(ServerLevel level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
    }
}
