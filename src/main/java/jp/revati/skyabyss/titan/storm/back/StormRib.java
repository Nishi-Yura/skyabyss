package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.back.StormKit.Style;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.joml.Vector3f;

// 西の試練 風切りの骨の「肋の間」 (25 の 5章 A「骨を縦に」)。前腕の骨を、縦の空洞の登りにした。
//  - 建てる: 空洞 (RIB_HALL)・梁 (3x3 の銅の格子 = 昇り風の穴) の螺旋・層の岩棚 (5x5 の板。休み場 w2〜w4・灯り)・頂の岩棚 (西の石)
//  - 登り: 凪の昇り風は RIB_HOP (4) 上がるので、隣の梁へ一段ずつ。嵐の昇り風は 13 上がる (横風が吹く。岩棚の板の隅が陰)。
//    嵐に風切りの羽根で滑空すれば、層の岩棚から次の層の岩棚まで一気に渡れる (滑空の輪: くぐると次の岩棚の隅まで輪の流れが運ぶ)。
//    どちらの拍でも頂へ着く (拍で行ける行けないを決めない。25 の 1章)
//  - 降り: 頂の岩棚の東の縁から降りの輪 (3 つ) の流れで、床の東側へ降りる近道 (いつでも)
//  - 見せ方: 層の岩棚に着くと層の灯りが灯る (そのグループにだけ見える)。滑空の輪は一度くぐると金色に光り続ける (そのグループの記録)
//  - 落ちる: 肋の間の中の落下は、傷が 5 まで (風の底があるので、落ちても戻れるが、軽くはない)
final class StormRib {
    // 輪の流れの速さ (1 tick のブロック数)
    static final double CURRENT_UP = 0.5;
    static final double CURRENT_DOWN = 0.36;
    static final double CAPTURE = 1.9;
    static final int CURRENT_MAX_TICKS = 90;
    // 肋の間の中の落下の上限 (距離。傷は距離 − 3)
    static final float FALL_CAP = 8f;

    private static final DustParticleOptions RING_DIM = new DustParticleOptions(new Vector3f(0.55f, 0.75f, 1f), 1.2f);
    private static final DustParticleOptions RING_LIT = new DustParticleOptions(new Vector3f(1f, 0.85f, 0.35f), 1.4f);
    private static final DustParticleOptions RING_DOWN = new DustParticleOptions(new Vector3f(0.7f, 1f, 0.95f), 1.2f);

    // 輪の流れに乗っている人
    private static final class Current {
        final Vec3[] targets;
        final double speed;
        int idx;
        int ticks;

        Current(Vec3[] targets, double speed, int idx) {
            this.targets = targets;
            this.speed = speed;
            this.idx = idx;
        }
    }

    private static final Map<UUID, Current> CURRENTS = new HashMap<>();
    private static final Map<UUID, Long> RING_HINT = new HashMap<>();

    private StormRib() {
    }

    static void clear() {
        CURRENTS.clear();
        RING_HINT.clear();
    }

    // ---------------------------------------------------------------- 建てる
    static void build(StormKit kit) {
        kit.room(RIB_HALL, Style.BONE, 0);
        // 壁の灯り (長い壁に 6 ブロックおき、高さ 6 おき)
        for (int y = BONE_FLOOR + 3; y <= RIB_TOP_Y + 2; y += 6) {
            for (int x = RIB_X1 + 2; x <= RIB_X2 - 2; x += 6) {
                kit.solid(x, y, BONE_Z1 - 1, StormKit.LAMP);
                kit.solid(x, y, BONE_Z2 + 1, StormKit.LAMP);
            }
        }
        // 肋 (北と南の壁ぞいの縦の骨。景色と陰)
        for (int x = RIB_X1 + 4; x <= RIB_X2 - 2; x += 5) {
            for (int y = BONE_FLOOR + 1; y <= RIB_TOP_Y + 4; y++) {
                kit.put(x, y, BONE_Z1, y % 4 == 0 ? StormKit.CALCITE : StormKit.BONE);
                kit.put(x, y, BONE_Z2, y % 4 == 0 ? StormKit.CALCITE : StormKit.BONE);
            }
        }
        // 層の岩棚の板 (層 1〜頂)。中心の 3x3 は昇り風の穴 (頂は穴なし)
        for (int layer = 1; layer <= RIB_LAYERS; layer++) {
            Area slab = ribSlab(layer);
            for (int x = slab.x1(); x <= slab.x2(); x++) {
                for (int z = slab.z1(); z <= slab.z2(); z++) {
                    kit.put(x, slab.floor(), z, StormKit.floor(Style.BONE, x, z));
                    for (int y = slab.floor() + 1; y <= slab.floor() + 3; y++) {
                        kit.air(x, y, z);
                    }
                }
            }
            for (BlockPos p : fin(layer)) {
                kit.put(p.getX(), p.getY(), p.getZ(), StormKit.CALCITE);
            }
            BlockPos light = layerLight(layer);
            kit.put(light.getX(), light.getY(), light.getZ(), StormKit.SLATE);
        }
        // 梁 (昇り風の穴)。0 段目は床の上、頂の手前まで
        for (int i = 0; i < RIB_LAYERS * RIB_HOPS; i++) {
            BlockPos c = ribLedge(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    kit.put(c.getX() + dx, c.getY(), c.getZ() + dz, StormKit.GRATE);
                    if (i > 0) {
                        for (int y = c.getY() + 1; y <= c.getY() + 3; y++) {
                            kit.air(c.getX() + dx, y, c.getZ() + dz);
                        }
                    }
                }
            }
            kit.put(c.getX(), c.getY() - 1, c.getZ(), StormKit.LAMP);
        }
        // 降りの輪の着き場 (床の東側。銅の輪の模様)
        BlockPos land = BlockPos.containing(RIB_DOWN_LANDING);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                    kit.put(land.getX() + dx, BONE_FLOOR, land.getZ() + dz, StormKit.COPPER);
                }
            }
        }
    }

    // 層の岩棚の陰の柱 (風上の西の側。層 1 は壁に接しているので要らない)。横風は同じ行 (z) の風上 3 ブロック以内に固いブロックがあれば避けられるので、
    // 入る道 (梁からの着き場) の行はあけて、隅の行に立てる
    static List<BlockPos> fin(int layer) {
        Area s = ribSlab(layer);
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        if (s.x1() <= RIB_X1) {
            return out;
        }
        for (int z = s.z1(); z <= s.z2(); z++) {
            boolean corner = z == s.z1() || z == s.z2();
            if (layer == RIB_LAYERS || corner) {
                for (int y = s.floor() + 1; y <= s.floor() + 2; y++) {
                    out.add(new BlockPos(s.x1(), y, z));
                }
            }
        }
        return out;
    }

    // 層の灯りの場所 (岩棚の西の縁の上)。まだ着いていないグループには消えた石、着いたグループには灯り
    static BlockPos layerLight(int layer) {
        Area s = ribSlab(layer);
        return new BlockPos(s.x1(), s.floor() + 3, (s.z1() + s.z2()) / 2);
    }

    // グループごとの見かけ: 着いた層の灯り
    static void overlay(ServerLevel level, UUID group, BiConsumer<BlockPos, BlockState> out) {
        StormProgress p = StormProgress.peek(level, group);
        if (p == null) {
            return;
        }
        for (int layer = 1; layer <= RIB_LAYERS; layer++) {
            if (p.ribLayer() >= layer) {
                out.accept(layerLight(layer), StormKit.LAMP);
            }
        }
    }

    // ---------------------------------------------------------------- 場所の判定
    static boolean inHall(Vec3 p) {
        return p.x > RIB_X1 - 0.5 && p.x < RIB_X2 + 1.5 && p.z > BONE_Z1 - 0.5 && p.z < BONE_Z2 + 1.5 && p.y > BONE_FLOOR - 0.5 && p.y < BONE_FLOOR + RIB_HEIGHT + 1;
    }

    static AABB hallBox() {
        return RIB_HALL.aabb().inflate(1.5, 0, 1.5);
    }

    // 立っている層 (岩棚の板の上。なければ -1)
    static int layerAt(Vec3 p) {
        for (int layer = RIB_LAYERS; layer >= 1; layer--) {
            Area s = ribSlab(layer);
            if (p.x >= s.x1() && p.x <= s.x2() + 1 && p.z >= s.z1() && p.z <= s.z2() + 1 && p.y >= s.floor() + 0.9 && p.y <= s.floor() + 3) {
                return layer;
            }
        }
        return -1;
    }

    // 滑空の輪 k の中心 (層 k → k+1)
    static Vec3 upRing(int k) {
        return ribRing(k);
    }

    // 輪の流れの行き先: 次の層の岩棚の隅 (休み場の spawn の真上)
    static Vec3 landingTarget(int layer) {
        BlockPos spawn = rest("w" + (layer + 1));
        return new Vec3(spawn.getX() + 0.5, spawn.getY() + 1.4, spawn.getZ() + 0.5);
    }

    // ---------------------------------------------------------------- 毎 tick (肋の間に人がいるときだけ)
    static void tick(ServerLevel level) {
        long gt = level.getGameTime();
        boolean particles = gt % 6 == 0;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isSpectator() || !p.isAlive()) {
                CURRENTS.remove(p.getUUID());
                continue;
            }
            Vec3 pos = p.position();
            if (!inHall(pos)) {
                CURRENTS.remove(p.getUUID());
                continue;
            }
            StormProgress pr = StormProgress.of(level, p);
            // 輪の流れ
            Current cur = CURRENTS.get(p.getUUID());
            if (cur != null) {
                stepCurrent(level, p, cur);
            } else {
                capture(level, p, pr);
            }
            // 層の岩棚に着いた
            int layer = layerAt(pos);
            if (layer > pr.ribLayer() && p.onGround()) {
                reach(level, p, pr, layer);
            }
            if (particles) {
                drawRings(level, p, pr);
            }
        }
    }

    private static void capture(ServerLevel level, ServerPlayer p, StormProgress pr) {
        Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
        for (int k = 0; k < RIB_LAYERS; k++) {
            if (c.distanceToSqr(upRing(k)) <= CAPTURE * CAPTURE) {
                boolean first = !pr.ribRingLit(k);
                if (first) {
                    pr.setRibRingLit(k);
                }
                Vec3 target = landingTarget(k + 1);
                CURRENTS.put(p.getUUID(), new Current(new Vec3[] {target}, CURRENT_UP, 0));
                ringSound(level, p, upRing(k), first ? 1.4f : 1.0f);
                if (first) {
                    Notice.bar(p, ModText.progress("message.skyabyss.storm.rib_ring", pr.ribRingCount(), RIB_LAYERS));
                }
                return;
            }
        }
        for (int j = 0; j < RIB_DOWN_RINGS.length; j++) {
            if (c.distanceToSqr(RIB_DOWN_RINGS[j]) <= CAPTURE * CAPTURE) {
                Vec3[] t = new Vec3[RIB_DOWN_RINGS.length - j];
                for (int i = j + 1; i < RIB_DOWN_RINGS.length; i++) {
                    t[i - j - 1] = RIB_DOWN_RINGS[i];
                }
                t[t.length - 1] = RIB_DOWN_LANDING;
                CURRENTS.put(p.getUUID(), new Current(t, CURRENT_DOWN, 0));
                ringSound(level, p, RIB_DOWN_RINGS[j], 0.8f);
                if (j == 0) {
                    Notice.bar(p, ModText.hint("message.skyabyss.storm.rib_down"));
                }
                return;
            }
        }
    }

    private static void stepCurrent(ServerLevel level, ServerPlayer p, Current cur) {
        cur.ticks++;
        Vec3 target = cur.targets[cur.idx];
        Vec3 here = p.position().add(0, 0.4, 0);
        Vec3 d = target.subtract(here);
        double dist = d.length();
        if (dist < 1.1) {
            cur.idx++;
            if (cur.idx >= cur.targets.length) {
                p.setDeltaMovement(p.getDeltaMovement().scale(0.2));
                p.hurtMarked = true;
                p.fallDistance = 0;
                CURRENTS.remove(p.getUUID());
                return;
            }
            target = cur.targets[cur.idx];
            d = target.subtract(here);
            dist = d.length();
        }
        if (cur.ticks > CURRENT_MAX_TICKS || p.isShiftKeyDown() && cur.speed == CURRENT_UP && cur.ticks > 6) {
            // 時間切れ・しゃがんで抜けた
            CURRENTS.remove(p.getUUID());
            return;
        }
        Vec3 v = d.scale(cur.speed / Math.max(dist, 1e-3));
        Vec3 old = p.getDeltaMovement();
        p.setDeltaMovement(old.x + (v.x - old.x) * 0.5, old.y + (v.y - old.y) * 0.5, old.z + (v.z - old.z) * 0.5);
        p.fallDistance = 0;
        p.hurtMarked = true;
        if (cur.ticks % 3 == 0) {
            StormFx.forced(level, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private static void ringSound(ServerLevel level, ServerPlayer p, Vec3 at, float pitch) {
        level.playSound(null, at.x, at.y, at.z, StormSetup.GALE.get(), SoundSource.PLAYERS, 0.9f, pitch);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, pitch + 0.3f);
        StormFx.burst(level, ParticleTypes.END_ROD, at, 12, 0.5);
    }

    // 層の岩棚に着いた (そのグループの記録に。その人にだけ手応え)
    private static void reach(ServerLevel level, ServerPlayer p, StormProgress pr, int layer) {
        pr.setRibLayer(layer);
        GroupOverlays.refresh(level, pr.group);
        for (ServerPlayer mate : Groups.online(level, pr.group)) {
            if (inHall(mate.position())) {
                Notice.bar(mate, layer >= RIB_LAYERS ? ModText.progress("message.skyabyss.storm.rib_top") : ModText.progress("message.skyabyss.storm.rib_layer", layer, RIB_LAYERS));
            }
        }
        StormGuide.successTo(level, p, 1.0f + 0.15f * layer);
        BlockPos light = layerLight(layer);
        level.playSound(null, light, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.2f + 0.1f * layer);
    }

    // 輪の絵 (見える範囲の人にだけ。滑空の輪は光ったら金)
    private static void drawRings(ServerLevel level, ServerPlayer p, StormProgress pr) {
        for (int k = 0; k < RIB_LAYERS; k++) {
            Vec3 c = upRing(k);
            if (p.distanceToSqr(c) < 40 * 40) {
                ring(level, p, c, ringAxis(k), pr.ribRingLit(k) ? RING_LIT : RING_DIM);
            }
        }
        for (Vec3 c : RIB_DOWN_RINGS) {
            if (p.distanceToSqr(c) < 40 * 40) {
                ring(level, p, c, new Vec3(0, 1, 0), RING_DOWN);
            }
        }
    }

    // 輪は次の岩棚へ向かう向きに立てる (軸 = 進む向き)
    private static Vec3 ringAxis(int k) {
        BlockPos a = ribLanding(k);
        BlockPos b = ribLanding(k + 1);
        Vec3 d = new Vec3(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
        return d.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : d.normalize();
    }

    private static void ring(ServerLevel level, ServerPlayer p, Vec3 center, Vec3 axis, DustParticleOptions dust) {
        Vec3 up = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = axis.cross(up).normalize();
        Vec3 w = axis.cross(u).normalize();
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 q = center.add(u.scale(Math.cos(a) * 1.5)).add(w.scale(Math.sin(a) * 1.5));
            level.sendParticles(p, dust, true, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    // 肋の間の入口の近くで、登り方の手がかり (一度ずつ、30 秒に一度まで)
    static void hint(ServerLevel level, ServerPlayer p) {
        long now = level.getGameTime();
        if (now - RING_HINT.getOrDefault(p.getUUID(), -10000L) < 600) {
            return;
        }
        Vec3 v = p.position();
        if (inHall(v) && v.y < BONE_FLOOR + 3 && v.x > RIB_X2 - 8) {
            RING_HINT.put(p.getUUID(), now);
            Notice.bar(p, ModText.hint("message.skyabyss.storm.rib_enter"));
        }
    }

    // 輪の流れの途中か (自己テスト)
    static boolean riding(UUID id) {
        return CURRENTS.containsKey(id);
    }

    // 自己テスト用: 人を輪の流れに乗せる
    static void startForTest(ServerPlayer p, Vec3[] targets, double speed) {
        CURRENTS.put(p.getUUID(), new Current(targets, speed, 0));
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Events {
        private Events() {
        }

        // 肋の間の落下は軽くする (風の底。傷は 5 まで)
        @SubscribeEvent
        static void onFall(LivingFallEvent event) {
            if (event.getEntity() instanceof ServerPlayer p && p.level().dimension() == jp.revati.skyabyss.titan.storm.StormTravel.STORM_BACK
                    && inHall(p.position()) && event.getDistance() > FALL_CAP) {
                event.setDistance(FALL_CAP);
            }
        }
    }
}
