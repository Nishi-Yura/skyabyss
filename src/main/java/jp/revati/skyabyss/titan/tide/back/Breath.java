package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.BreathLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 息の間の仕組み (docs/design/24 の案 C)。毎 tick (TideBackTrials から。人が背中にいるときだけ):
//  - 運び: 光る泡 (入口の縁・皿のまんなか・遠い皿への泡・舞台のまんなか) に立つと、次の乗り場へ運ばれる。
//    運びは「折れ線の道 (BreathLayout.Lift.path) に沿って、位置から目標の点へ向かう速度を毎 tick 決める」形。
//    バニラの泡の柱 (上へ押すだけで、向きと高さが人の操作と跳ね方しだい) は使わない: 自己テストで測った着く割合が低かったため。
//    しゃがむと途中でやめられる (その場から落ちる。落ちても痛くない)
//  - 下り: 乗り場でしゃがみ続けると、ひとつ前の乗り場へ降りる。落ちて下へ戻るのもいつでもできる
//  - 安全: 気室の中は落ちても傷を受けない (BreathEvents)。高い所から速く落ちたら、ゆっくり落ちる息 (SLOW_FALLING) が受けとめる
//  - 進み: 立った高さをグループの記録にし、壁の輪 (下・中・上) をそのグループにだけ灯す
//  - 見せ方: 泡の柱の粒、道すじの粒 (近い泡は白、遠い泡は琥珀)、遠い泡が眠っているときの暗い粒
// 潮 (TideClock) は流れの強さだけを変える: 満ちているあいだ (満ち具合 0.75 以上) は遠い泡が働き、皿を一つ飛ばせる。引いているあいだも
// 近い泡で必ず昇れる (潮・時間・天気で道が閉ざされることはない)。
public final class Breath {
    private Breath() {
    }

    public static final List<Lift> LIFTS = BreathLayout.lifts();
    // 乗ってから下りるまで (これより長いと打ち切る)
    static final int MAX_RIDE_TICKS = 160;
    // 運びを終えてから次に乗れるまで
    static final int BOARD_COOLDOWN = 14;
    // しゃがみ続けて下りるまでの tick
    public static final int DESCEND_SNEAK_TICKS = 28;

    static final class Ride {
        final List<Vec3> path;
        final boolean tower;
        final boolean up;
        final int to;
        double s;
        int age;
        boolean sneakReleased;
        boolean cancelled;

        Ride(List<Vec3> path, boolean tower, boolean up, int to) {
            this.path = path;
            this.tower = tower;
            this.up = up;
            this.to = to;
        }
    }

    private static final Map<UUID, Ride> RIDES = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static final Map<UUID, Integer> SNEAK = new HashMap<>();
    private static final Map<UUID, Long> HINT = new HashMap<>();
    private static final Map<UUID, Double> LAST_Y = new HashMap<>();
    // 試験用: 乗った回数
    static int boardings;

    public static AABB activeBox() {
        int e = (int) Math.ceil(R_A) + 3;
        return new AABB(CX - e, POOL_FLOOR, CZ - e, CX + e + 1, OBS_ROOF + 1, CZ + e + 1);
    }

    public static boolean active(Vec3 p) {
        return activeBox().contains(p);
    }

    static void clear() {
        RIDES.clear();
        COOLDOWN.clear();
        SNEAK.clear();
        HINT.clear();
        LAST_Y.clear();
        boardings = 0;
    }

    public static boolean riding(ServerPlayer p) {
        return RIDES.containsKey(p.getUUID());
    }

    static void forget(UUID id) {
        RIDES.remove(id);
        COOLDOWN.remove(id);
        SNEAK.remove(id);
        LAST_Y.remove(id);
    }

    // ---------------------------------------------------------------- 毎 tick
    public static void tick(ServerLevel level, boolean peaceful) {
        double fullness = TideClock.fullness(level);
        boolean far = farActive(fullness);
        for (ServerPlayer p : Players.snapshot(level.players())) {
            UUID id = p.getUUID();
            if (p.isSpectator() || !p.isAlive() || !active(p.position())) {
                if (RIDES.containsKey(id) || COOLDOWN.containsKey(id)) {
                    forget(id);
                }
                continue;
            }
            Ride ride = RIDES.get(id);
            if (ride != null) {
                tickRide(level, p, ride);
                continue;
            }
            COOLDOWN.computeIfPresent(id, (k, v) -> v > 1 ? v - 1 : null);
            softLanding(p);
            if (!COOLDOWN.containsKey(id)) {
                board(level, p, far, peaceful);
            }
            if (!RIDES.containsKey(id)) {
                descend(level, p);
            }
            progress(level, p);
        }
        if (level.getGameTime() % 2 == 0) {
            effects(level, far, peaceful);
        }
    }

    // ---------------------------------------------------------------- 乗る
    static boolean standing(ServerPlayer p) {
        return p.onGround() || p.isInWater() || p.isInWaterOrBubble();
    }

    static void board(ServerLevel level, ServerPlayer p, boolean far, boolean peaceful) {
        if (!standing(p) || p.isPassenger() || p.isSleeping() || p.getAbilities().flying) {
            return;
        }
        Vec3 pos = p.position();
        for (Lift l : LIFTS) {
            double[] pad = l.pad();
            if (Math.hypot(pos.x - pad[0], pos.z - pad[1]) > l.padRadius() || Math.abs(pos.y - l.padY()) > 1.3) {
                continue;
            }
            if (l.far() && !far) {
                hint(level, p, "message.skyabyss.tide.breath_far_shallow");
                return;
            }
            if (l.to() == 9 && BreathSummit.busyFor(level, Groups.keyOf(p))) {
                hint(level, p, "message.skyabyss.tide.breath_arena_busy");
                return;
            }
            if (l.to() == 10 && !deckOpen(level, p, peaceful)) {
                hint(level, p, "message.skyabyss.tide.breath_deck_shut");
                return;
            }
            start(level, p, l.path(), l.to() == 10, true, l.to());
            return;
        }
    }

    // 展望の間への風の柱が、このグループに働くか (真珠守りを退けた・周回の既知の道・ピースフル)
    public static boolean deckOpen(ServerLevel level, ServerPlayer p, boolean peaceful) {
        UUID group = Groups.keyOf(p);
        return peaceful || TideBackState.of(level, group).keeperDefeated() || !FirstRun.isFirstRun(level, group);
    }

    static void start(ServerLevel level, ServerPlayer p, List<Vec3> path, boolean tower, boolean up, int to) {
        Ride r = new Ride(path, tower, up, to);
        r.sneakReleased = !p.isShiftKeyDown();
        RIDES.put(p.getUUID(), r);
        boardings++;
        p.fallDistance = 0;
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.4f, up ? 1.0f : 0.8f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, up ? 1.5f : 1.1f);
        level.sendParticles(ParticleTypes.BUBBLE_POP, p.getX(), p.getY() + 0.3, p.getZ(), 14, 0.4, 0.2, 0.4, 0.08);
    }

    static void tickRide(ServerLevel level, ServerPlayer p, Ride r) {
        r.age++;
        Vec3 pos = p.position();
        if (!p.isShiftKeyDown()) {
            r.sneakReleased = true;
        }
        boolean sneak = r.sneakReleased && p.isShiftKeyDown() && r.age > 12;
        BreathLayout.Step st = BreathLayout.step(r.path, pos, r.s, r.tower);
        Vec3 near = BreathLayout.pointAt(r.path, BreathLayout.progress(r.path, pos));
        boolean lost = pos.distanceTo(near) > 3.4;
        if (st.done() || sneak || lost || r.age > MAX_RIDE_TICKS) {
            end(level, p, r, st.done());
            return;
        }
        r.s = st.s();
        p.setDeltaMovement(st.velocity());
        p.hurtMarked = true;
        p.fallDistance = 0;
        if (r.age % 2 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 0.4, p.getZ(), 3, 0.25, 0.3, 0.25, 0.01);
        }
    }

    static void end(ServerLevel level, ServerPlayer p, Ride r, boolean arrived) {
        RIDES.remove(p.getUUID());
        COOLDOWN.put(p.getUUID(), BOARD_COOLDOWN);
        p.fallDistance = 0;
        if (arrived) {
            Vec3 last = r.path.get(r.path.size() - 1);
            p.setDeltaMovement(0, 0, 0);
            p.hurtMarked = true;
            level.playSound(null, last.x, last.y, last.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.3f);
            level.sendParticles(ParticleTypes.BUBBLE_POP, last.x, last.y + 0.3, last.z, 10, 0.4, 0.2, 0.4, 0.05);
        } else {
            // やめたとき: ゆっくり落ちる息で受けとめる (落ちても痛くない)
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
        }
    }

    // ---------------------------------------------------------------- 下り (しゃがみ続ける)
    @Nullable
    static Node nodeAt(Vec3 p) {
        for (Dish d : dishes()) {
            if (Math.hypot(p.x - d.wx(), p.z - d.wz()) <= RING_R + 0.3 && p.y >= d.floorY() - 0.3 && p.y <= d.floorY() + 2.6) {
                return dishNode(d.index());
            }
        }
        if (Math.hypot(p.x - WX, p.z - WZ) <= ARENA_R + 0.3 && p.y >= ARENA_FLOOR + 0.7 && p.y <= ARENA_FLOOR + 3.6) {
            return arena();
        }
        if (deckBox().contains(p) && p.y >= DECK_FLOOR + 0.7) {
            return deck();
        }
        return null;
    }

    static void descend(ServerLevel level, ServerPlayer p) {
        UUID id = p.getUUID();
        if (!p.isShiftKeyDown() || !standing(p) || p.isPassenger()) {
            SNEAK.remove(id);
            return;
        }
        Node n = nodeAt(p.position());
        if (n == null) {
            SNEAK.remove(id);
            return;
        }
        int t = SNEAK.merge(id, 1, Integer::sum);
        if (t == 1) {
            hint(level, p, "message.skyabyss.tide.breath_descend_hint");
        }
        if (t >= DESCEND_SNEAK_TICKS) {
            SNEAK.remove(id);
            start(level, p, BreathLayout.descent(n.id(), p.position()), n.kind() == Kind.DECK, false, n.id() - 1);
        }
    }

    // 高い所から速く落ちたら、ゆっくり落ちる息が受けとめる
    static void softLanding(ServerPlayer p) {
        UUID id = p.getUUID();
        Double last = LAST_Y.put(id, p.getY());
        if (last != null && last - p.getY() > 0.8 && p.getY() > LIP + 6 && !p.hasEffect(MobEffects.SLOW_FALLING)) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, true));
            if (p.level() instanceof ServerLevel level) {
                hint(level, p, "message.skyabyss.tide.breath_caught");
            }
        }
    }

    static void hint(ServerLevel level, ServerPlayer p, String key) {
        long now = level.getGameTime();
        if (now - HINT.getOrDefault(p.getUUID(), -1000L) < 120) {
            return;
        }
        HINT.put(p.getUUID(), now);
        Notice.bar(p, ModText.hint(key));
    }

    // ---------------------------------------------------------------- 進み (壁の輪)
    static int ringsLit(int best) {
        int n = 0;
        for (int y : RING_Y) {
            if (best >= y - (LIP + 1)) {
                n++;
            }
        }
        return n;
    }

    static void progress(ServerLevel level, ServerPlayer p) {
        if (!p.onGround() || p.getY() < LIP + 0.5) {
            return;
        }
        int h = heightOf(p.getY());
        UUID group = Groups.keyOf(p);
        TideBackState.Group st = TideBackState.of(level, group);
        int best = st.upBest();
        if (h <= best) {
            return;
        }
        st.setUpBest(h);
        int before = ringsLit(best);
        int after = ringsLit(h);
        if (after > before) {
            GroupOverlays.refresh(level, group);
            for (ServerPlayer m : Groups.online(level, group)) {
                if (active(m.position())) {
                    Notice.bar(m, ModText.progress("message.skyabyss.tide.breath_ring", after));
                    level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4f, 0.9f + 0.2f * after);
                }
            }
        }
        TideBackTrials.sendGaugeNow(level);
    }

    // 壁の輪の見かけ (そのグループが着いた高さまでの輪が灯る)
    static void overlay(ServerLevel level, UUID group, java.util.function.BiConsumer<BlockPos, BlockState> out) {
        if (level.dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        int best = TideBackState.of(level, group).upBest();
        int lit = ringsLit(best);
        for (int i = 0; i < lit; i++) {
            for (BlockPos q : ringCells(i)) {
                if (level.getBlockState(q).is(TideKit.DARK.getBlock())) {
                    out.accept(q, TideKit.LAMP);
                }
            }
        }
    }

    static List<BlockPos> ringCells(int ring) {
        List<BlockPos> list = new ArrayList<>();
        int r = (int) Math.ceil(R_A) + 3;
        for (int x = CX - r; x <= CX + r; x++) {
            for (int z = CZ - r; z <= CZ + r; z++) {
                double d = dist(x, z);
                if (d > R_A && d <= R_A + 1.2) {
                    list.add(new BlockPos(x, RING_BAND[ring], z));
                }
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- 見せ方 (2 tick に一度。近くに人がいる乗り場だけ)
    static void effects(ServerLevel level, boolean far, boolean peaceful) {
        long t = level.getGameTime();
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && active(p.position())) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            return;
        }
        for (Lift l : LIFTS) {
            double[] pad = l.pad();
            double py = l.padY();
            boolean near = false;
            for (ServerPlayer p : players) {
                if (p.distanceToSqr(pad[0], py, pad[1]) < 40 * 40) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue;
            }
            boolean on = !l.far() || far;
            if (l.to() == 10 && !players.isEmpty()) {
                // 展望の間への柱は、働くとき (そのグループに) だけ明るい。ここでは全員に薄く見せる (働かない人にも柱があることは見える)
                on = anyOpen(level, players, peaceful);
            }
            if (l.to() == 9 && BreathSummit.busy(level)) {
                on = false;
            }
            // 泡の柱
            if (t % 4 == 0) {
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, pad[0], py + 0.2, pad[1], on ? 4 : 1, 0.25, 0.1, 0.25, 0.02);
                level.sendParticles(on ? ParticleTypes.END_ROD : ParticleTypes.SMOKE, pad[0], py + 0.9, pad[1], 1, 0.15, 0.2, 0.15, 0.0);
            }
            // 道すじ (行き先まで動く光。遠い泡は琥珀)
            List<Vec3> path = l.path();
            double len = length(path);
            for (int k = 0; k < 3; k++) {
                double s = (t * 0.55 + k * len / 3.0) % len;
                Vec3 q = pointAt(path, s);
                level.sendParticles(on ? (l.far() ? ParticleTypes.WAX_ON : ParticleTypes.END_ROD) : ParticleTypes.ASH, q.x, q.y + 0.3, q.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }
        // 気室の息: 中心の柱を泡がゆっくり昇る (6 秒の拍。気室の真ん中が一つの気道に見える)
        if (t % 6 == 0) {
            double phase = (t % 120) / 120.0;
            double y = LIP + 2 + phase * (DECK_FLOOR - LIP - 4);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, WX, y, WZ, 3, 0.6, 1.5, 0.6, 0.0);
        }
        if (t % 100 == 0) {
            for (ServerPlayer p : players) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.AMBIENT, 0.6f, 0.7f);
            }
        }
    }

    private static boolean anyOpen(ServerLevel level, List<ServerPlayer> players, boolean peaceful) {
        for (ServerPlayer p : players) {
            if (deckOpen(level, p, peaceful)) {
                return true;
            }
        }
        return false;
    }
}
