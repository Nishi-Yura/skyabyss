package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Gimmick;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 区画の仕掛け (docs/design/36 の 9)。区画に人がいるときだけ動く (いなければ何もしない)。
//   灯りが消える    壁の灯りが四分の一ずつ、二十秒ごとに消えて回る
//   下りの重さ      落ちる速さが増す (上りは変わらない)。毎 tick (PlayerTickEvent)
//   拍で消える岩棚  脇の岩棚が一秒半ごとに交互に消える (道の足場は消えない)
//   崩れる岩棚      脇の岩棚に二秒立つと崩れ、十秒で戻る
//   写しの敵        湧く敵の種 (DepthMobs が見る)
//   霧が敵を隠す    灯り (灯籠・灯り) から六ブロック以上離れた敵は姿が消える (DepthMobs の湧きと同じ区画の敵を見る)
//   下から広がる闇  底から上へ、十二秒に一ブロックずつ闇が上がる。闇より下にいると闇にたまり、一秒ごとに痛い。灯りの近くは守られる
//   上からの追手    二十秒ごとに一人の真上へ石が落ちる (四十 tick 前に輪の予告)
//   修飾 (DepthMobs と DepthRun が見る): 敵が多い・傷が深い・落とし物が多い・灯りが少ない (LAMP は DepthLayout が半分にする)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DepthGimmicks {
    static final int STEP = 5;
    static final long LIGHT_TURN = 400;     // 灯りの回り (二十秒)
    static final long BEAT = 30;            // 拍 (一秒半)
    static final long CRUMBLE_STAND = 40;   // 立ってから崩れるまで (二秒)
    static final long CRUMBLE_BACK = 200;   // 崩れてから戻るまで (十秒)
    static final long FRONT_STEP = 240;     // 闇が一ブロック上がる時間 (十二秒)
    static final long CHASE_EVERY = 400;    // 追手の間隔 (二十秒)
    static final long CHASE_WARN = 40;      // 予告 (二秒)
    static final int FALL_TOP = 10;         // 石を落とす高さ (人の上)
    static final double HEAVY = 0.06;       // 下りの重さ (毎 tick に足す下向きの力)
    static final double HEAVY_MAX = -3.0;

    // 区画ごとに、いつ闇が立ち上がったか・崩れた岩棚・次の追手 (実行の間だけ。ワールドを閉じると消える)
    private static final Map<Integer, Long> FRONT_START = new HashMap<>();
    private static final Map<Long, Long> STAND = new HashMap<>();
    private static final Map<Long, Long> CRUMBLED = new HashMap<>();
    private static final Map<Integer, long[]> CHASE = new HashMap<>();
    private static final Map<Integer, Long> CHASE_NEXT = new HashMap<>();

    private DepthGimmicks() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % STEP != 0) {
            return;
        }
        tick(server);
    }

    static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(jp.revati.skyabyss.story.ch5.AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        Map<Integer, List<ServerPlayer>> by = new HashMap<>();
        for (ServerPlayer p : level.players()) {
            int sec = DepthRun.sectionOf(p);
            if (sec > 0 && p.isAlive() && !p.isSpectator()) {
                by.computeIfAbsent(sec, k -> new ArrayList<>()).add(p);
            }
        }
        long t = level.getGameTime();
        for (Map.Entry<Integer, List<ServerPlayer>> e : by.entrySet()) {
            int sec = e.getKey();
            if (!DepthBuilder.built(server, sec)) {
                continue;
            }
            DepthLayout.Result g = DepthBuilder.geometry(server, sec);
            FRONT_START.putIfAbsent(sec, t);
            if (g.section().has(Gimmick.LIGHTS_OUT)) {
                lightsOut(level, g, t);
            }
            if (g.section().has(Gimmick.BEAT_LEDGES)) {
                beatLedges(level, g, t);
            }
            if (g.section().has(Gimmick.CRUMBLE_LEDGES)) {
                crumble(level, g, e.getValue(), t);
            }
            if (g.section().has(Gimmick.FOG_HIDE)) {
                DepthMobs.fog(level, g);
            }
            if (g.section().has(Gimmick.DARK_FRONT)) {
                darkFront(level, g, e.getValue(), t);
            }
            if (g.section().has(Gimmick.CHASE_FALL)) {
                chase(level, g, e.getValue(), t);
            }
        }
    }

    // 下りの重さ (毎 tick。落ちている人だけ)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator() || p.getAbilities().flying || p.onGround() || p.isInWater()) {
            return;
        }
        if (p.getDeltaMovement().y >= 0 || !p.level().dimension().equals(jp.revati.skyabyss.story.ch5.AbyssWorld.LEVEL)) {
            return;
        }
        int sec = DepthRun.sectionOf(p);
        if (sec == 0 || !DepthBuilder.geometry(p.server, sec).section().has(Gimmick.HEAVY_DESCENT)) {
            return;
        }
        var dm = p.getDeltaMovement();
        p.setDeltaMovement(dm.x, Math.max(HEAVY_MAX, dm.y - HEAVY), dm.z);
        p.hurtMarked = true;
    }

    // 灯りが消える: 灯りの四つに一つずつ、二十秒ごとに消えて回る
    static void lightsOut(ServerLevel level, DepthLayout.Result g, long t) {
        int turn = (int) ((t / LIGHT_TURN) % 4);
        BlockState lit = g.palette().lamp();
        BlockState dark = g.palette().dark();
        int i = 0;
        for (BlockPos b : g.lamps()) {
            boolean off = (i * 7 + g.section().index()) % 4 == turn;
            BlockState want = off ? dark : lit;
            if (level.getBlockState(b) != want && (level.getBlockState(b).getBlock() == lit.getBlock() || level.getBlockState(b).getBlock() == dark.getBlock())) {
                level.setBlock(b, want, 3);
            }
            i++;
        }
    }

    // 拍で消える岩棚: 岩棚 k は 拍 (BEAT) ごとに、偶数と奇数で交互に出る
    static void beatLedges(ServerLevel level, DepthLayout.Result g, long t) {
        int k = 0;
        for (Platform p : g.ledges()) {
            boolean on = ((t / BEAT) + k) % 2 == 0;
            for (BlockPos b : cells(g, p)) {
                BlockState cur = level.getBlockState(b);
                if (on && cur.isAir()) {
                    level.setBlock(b, g.palette().ledge(), 3);
                } else if (!on && cur.getBlock() == g.palette().ledge().getBlock()) {
                    level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            k++;
        }
    }

    // 崩れる岩棚: 立つと CRUMBLE_STAND で崩れ、CRUMBLE_BACK で戻る
    static void crumble(ServerLevel level, DepthLayout.Result g, List<ServerPlayer> players, long t) {
        int sec = g.section().index();
        int k = 0;
        for (Platform p : g.ledges()) {
            long key = sec * 100_000L + k++;
            BlockPos top = ledgeTop(g, p);
            Long back = CRUMBLED.get(key);
            if (back != null) {
                if (t >= back) {
                    CRUMBLED.remove(key);
                    for (BlockPos b : cells(g, p)) {
                        level.setBlock(b, g.palette().ledge(), 3);
                    }
                }
                continue;
            }
            boolean standing = false;
            for (ServerPlayer pl : players) {
                double dx = pl.getX() - (top.getX() + 0.5);
                double dz = pl.getZ() - (top.getZ() + 0.5);
                if (dx * dx + dz * dz <= 2.25 && Math.abs(pl.getY() - (top.getY() + 1)) < 0.6) {
                    standing = true;
                    break;
                }
            }
            if (!standing) {
                STAND.remove(key);
                continue;
            }
            long since = STAND.computeIfAbsent(key, x -> t);
            if (t - since >= CRUMBLE_STAND) {
                STAND.remove(key);
                CRUMBLED.put(key, t + CRUMBLE_BACK);
                for (BlockPos b : cells(g, p)) {
                    level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
                }
                level.playSound(null, top, net.minecraft.sounds.SoundEvents.DEEPSLATE_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.6f);
            }
        }
    }

    // 下から広がる闇: 底から一ブロックずつ上がる。闇より下にいる人は暗くなり、痛い。灯りの近く (五ブロック) は守られる
    static void darkFront(ServerLevel level, DepthLayout.Result g, List<ServerPlayer> players, long t) {
        int sec = g.section().index();
        long start = FRONT_START.getOrDefault(sec, t);
        int rise = (int) ((t - start) / FRONT_STEP);
        int frontLy = DepthPlan.HEIGHT - 1 - rise;
        for (ServerPlayer p : players) {
            int ly = DepthPlan.lyOf(p.blockPosition().getY());
            if (ly <= frontLy || nearLight(level, g, p.blockPosition(), 5)) {
                continue;
            }
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false, false));
            if (t % 20 == 0) {
                p.hurt(level.damageSources().magic(), 1.0f);
            }
        }
    }

    // 上からの追手: 人の真上へ石 (予告の輪のあと)
    static void chase(ServerLevel level, DepthLayout.Result g, List<ServerPlayer> players, long t) {
        int sec = g.section().index();
        long[] pending = CHASE.get(sec);
        if (pending != null) {
            if (t >= pending[3]) {
                CHASE.remove(sec);
                BlockPos at = new BlockPos((int) pending[0], (int) pending[1], (int) pending[2]).above(FALL_TOP);
                if (level.getBlockState(at).isAir()) {
                    FallingBlockEntity.fall(level, at, g.palette().wall());
                }
            } else if (t % 10 == 0) {
                level.sendParticles(ParticleTypes.SOUL, pending[0] + 0.5, pending[1] + 0.2, pending[2] + 0.5, 6, 0.9, 0.05, 0.9, 0.01);
            }
            return;
        }
        if (t < CHASE_NEXT.getOrDefault(sec, 0L) || players.isEmpty()) {
            return;
        }
        ServerPlayer target = players.get(level.random.nextInt(players.size()));
        BlockPos b = target.blockPosition();
        CHASE.put(sec, new long[] {b.getX(), b.getY(), b.getZ(), t + CHASE_WARN});
        CHASE_NEXT.put(sec, t + CHASE_EVERY);
    }

    // 灯りの近く (灯籠の火・灯り) か
    static boolean nearLight(ServerLevel level, DepthLayout.Result g, BlockPos at, int r) {
        for (BlockPos b : g.lamps()) {
            if (b.distSqr(at) <= r * r && level.getBlockState(b).getBlock() == g.palette().lamp().getBlock()) {
                return true;
            }
        }
        BlockPos lantern = g.lantern();
        return lantern.distSqr(at) <= r * r && level.getBlockState(lantern).getBlock() == Blocks.CANDLE
                && level.getBlockState(lantern).getValue(CandleBlock.LIT);
    }

    // 岩棚の上の段 (人の立つ面の一つ下の板)
    static BlockPos ledgeTop(DepthLayout.Result g, Platform p) {
        return new BlockPos(g.section().centerX() + (int) Math.round(p.dx()), DepthPlan.TOP_Y - p.ly(), (int) Math.round(p.dz()));
    }

    // 岩棚の板の場所 (中心と、その四つの横)
    static List<BlockPos> cells(DepthLayout.Result g, Platform p) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = ledgeTop(g, p);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i * i + j * j <= 1) {
                    out.add(c.offset(i, 0, j));
                }
            }
        }
        return out;
    }
}
