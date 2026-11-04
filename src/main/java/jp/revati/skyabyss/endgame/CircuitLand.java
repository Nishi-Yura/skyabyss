package jp.revati.skyabyss.endgame;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 巡り路の土地ごとの課題 (docs/design/54 の 3.1.1。2026-11-05 のダンジョンの頁 3-1)。その土地の巡り路にだけ出る (Circuits.landKind)
//   掘り出す DIG    (岩鯨の丘陵) 柱のまわりの光る所 3 つに立ってしゃがみ続け (2 秒)、埋まった骨を掘り出す。骨の兵が寄せてくる
//   潮待ち   TIDE   (潮の浜)    潮が満ち引きする (はじめ満ち 10 秒、引き 20 秒・満ち 12 秒をくり返す)。引いたあいだに沖の杭まで渡って印を取り、
//                               柱のそばへ戻る。満ちているあいだ柱から離れていると波に呑まれ (傷・押し戻し)、印を持っていれば落とす
//   雷を集める CHARGE (嵐の高地) 柱のまわりの結晶 3 つに 3 回ずつ当てて満たす (殴る・矢)。帯電した敵が寄せてくる
//   火を守る FIRE   (灰の荒野)  柱の焚き火のそば (5) に誰かがいるあいだ進む (40 秒。離れると戻る)。6 秒ごとに灰の風 (予告の帯) が火を横切る
// 置いた結晶と焚き火は、課題が終わると片づける (CircuitRun.placed。元は空気の所にだけ置く)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CircuitLand {
    public static final int DIG_POINTS = 3;
    public static final int DIG_STEPS = 4;
    static final double DIG_REACH = 1.6;
    static final int WAVE = 300;
    static final int TIDE_FIRST = 200;
    static final int TIDE_EBB = 400;
    static final int TIDE_FLOOD = 240;
    static final double TIDE_SAFE = 8;
    static final double TIDE_TOUCH = 2.2;
    static final double TIDE_HOME = 4;
    public static final int CRYSTALS = 3;
    public static final int CRYSTAL_HITS = 3;
    public static final int FIRE_NEED = 800;
    static final double FIRE_R = 5;
    static final int WIND_EVERY = 120;
    static final int WIND_WARN = 24;

    private CircuitLand() {
    }

    // ---------------------------------------------------------------- 掘り出す
    static void stepDig(ServerLevel level, CircuitRun run, List<ServerPlayer> ps, long now) {
        if (run.phase == 0) {
            ServerPlayer near = Circuits.nearest(ps, run.spot, Circuits.ARRIVE);
            if (near == null) {
                return;
            }
            if (run.points.isEmpty()) {
                pickPoints(level, run, DIG_POINTS, 4, 9);
                run.need = run.points.size();
            }
            run.mobs.clear();
            Circuits.spawnAround(level, run, run.spot, (int) Math.round((2 + ps.size()) * Circuits.harder(run)), 6, 11, near);
            run.phase = 1;
            run.nextWave = now + WAVE;
            for (ServerPlayer p : ps) {
                Notice.hint(p, "message.skyabyss.circuit.dig_start");
            }
            return;
        }
        for (int i = 0; i < run.points.size(); i++) {
            int got = run.pointProgress.get(i);
            if (got >= DIG_STEPS) {
                continue;
            }
            BlockPos at = run.points.get(i);
            level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.95f, 0.88f, 0.62f), 1.2f), at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0);
            level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5, 1, 0.1, 0.3, 0.1, 0.01);
            for (ServerPlayer p : ps) {
                if (p.isShiftKeyDown() && Circuits.flat(at, p.getX(), p.getZ()) < DIG_REACH) {
                    got++;
                    run.pointProgress.set(i, got);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()), at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0);
                    level.playSound(null, at, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
                    if (got >= DIG_STEPS) {
                        run.progress++;
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
                        level.playSound(null, at, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 0.9f);
                        for (ServerPlayer q : ps) {
                            Notice.gain(q, "message.skyabyss.circuit.dug", run.progress, run.need);
                        }
                    }
                    break;
                }
            }
        }
        waves(level, run, ps, now);
        if (run.progress >= run.need) {
            Circuits.complete(level, run);
        }
    }

    // ---------------------------------------------------------------- 潮待ち
    // 潮の今: true = 引いている
    static boolean ebb(CircuitRun run, long now) {
        long t = now - run.cycleStart;
        if (t < TIDE_FIRST) {
            return false;
        }
        return (t - TIDE_FIRST) % (TIDE_EBB + TIDE_FLOOD) < TIDE_EBB;
    }

    // 次に潮が変わるまで (tick)
    static long toTurn(CircuitRun run, long now) {
        long t = now - run.cycleStart;
        if (t < TIDE_FIRST) {
            return TIDE_FIRST - t;
        }
        long c = (t - TIDE_FIRST) % (TIDE_EBB + TIDE_FLOOD);
        return c < TIDE_EBB ? TIDE_EBB - c : TIDE_EBB + TIDE_FLOOD - c;
    }

    static void stepTide(ServerLevel level, CircuitRun run, List<ServerPlayer> ps, long now) {
        if (run.phase == 0) {
            ServerPlayer near = Circuits.nearest(ps, run.spot, Circuits.ARRIVE);
            if (near == null) {
                return;
            }
            if (run.points.isEmpty()) {
                pickPoints(level, run, 1, 22, 30);
            }
            run.cycleStart = now;
            run.mobs.clear();
            BlockPos stake = run.points.get(0);
            Circuits.spawnAround(level, run, stake, (int) Math.round((1 + ps.size()) * Circuits.harder(run)), 3, 7, near);
            run.phase = 1;
            for (ServerPlayer p : ps) {
                Notice.hint(p, "message.skyabyss.circuit.tide_start");
            }
            return;
        }
        BlockPos stake = run.points.get(0);
        boolean ebb = ebb(run, now);
        long turn = toTurn(run, now);
        if (turn <= 60 && turn > 50) {
            for (ServerPlayer p : ps) {
                Notice.hint(p, ebb ? "message.skyabyss.circuit.tide_flood_soon" : "message.skyabyss.circuit.tide_ebb_soon");
            }
        }
        // 沖の杭の目印
        level.sendParticles(ebb ? ParticleTypes.BUBBLE_POP : ParticleTypes.SPLASH, stake.getX() + 0.5, stake.getY() + 0.5, stake.getZ() + 0.5, 6, 0.3, 0.6, 0.3, 0.05);
        level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.37f, 0.78f, 0.94f), 1.4f), stake.getX() + 0.5, stake.getY() + 1.6, stake.getZ() + 0.5, 3, 0.1, 0.4, 0.1, 0);
        if (!ebb) {
            // 満ちている: 柱から離れた人は波に呑まれる
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI / 10;
                level.sendParticles(ParticleTypes.SPLASH, run.spot.getX() + 0.5 + Math.cos(a) * TIDE_SAFE, run.spot.getY() + 0.3, run.spot.getZ() + 0.5 + Math.sin(a) * TIDE_SAFE, 2, 0.2, 0.1, 0.2, 0.05);
            }
            for (ServerPlayer p : ps) {
                double d = Circuits.flat(run.spot, p.getX(), p.getZ());
                if (d > TIDE_SAFE && d < 48) {
                    p.hurt(level.damageSources().drown(), 1.5f);
                    Vec3 back = new Vec3(run.spot.getX() + 0.5 - p.getX(), 0, run.spot.getZ() + 0.5 - p.getZ()).normalize().scale(0.35);
                    p.push(back.x, 0.05, back.z);
                    p.hurtMarked = true;
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                    level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.4, 0.3, 0.4, 0.1);
                    if (run.phase == 2 && p.getUUID().equals(run.holder)) {
                        run.phase = 1;
                        run.holder = null;
                        for (ServerPlayer q : ps) {
                            Notice.warn(q, "message.skyabyss.circuit.tide_swept", p.getDisplayName());
                        }
                    }
                }
            }
        }
        if (run.phase == 1) {
            if (ebb) {
                for (ServerPlayer p : ps) {
                    if (Circuits.flat(stake, p.getX(), p.getZ()) < TIDE_TOUCH) {
                        run.phase = 2;
                        run.holder = p.getUUID();
                        level.playSound(null, stake, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
                        for (ServerPlayer q : ps) {
                            Notice.gain(q, "message.skyabyss.circuit.tide_mark", p.getDisplayName());
                        }
                        break;
                    }
                }
            }
            return;
        }
        // 印を持った人が柱のそばへ戻れば越える
        for (ServerPlayer p : ps) {
            if (p.getUUID().equals(run.holder)) {
                level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.37f, 0.78f, 0.94f), 1.0f), p.getX(), p.getY() + 2.1, p.getZ(), 2, 0.1, 0.1, 0.1, 0);
                if (Circuits.flat(run.spot, p.getX(), p.getZ()) < TIDE_HOME) {
                    Circuits.complete(level, run);
                }
                return;
            }
        }
        // 印を持った人がいない (倒れた・抜けた): 取り直し
        run.phase = 1;
        run.holder = null;
    }

    // ---------------------------------------------------------------- 雷を集める
    static void stepCharge(ServerLevel level, CircuitRun run, List<ServerPlayer> ps, long now) {
        if (run.phase == 0) {
            ServerPlayer near = Circuits.nearest(ps, run.spot, Circuits.ARRIVE);
            if (near == null) {
                return;
            }
            clear(level, run);
            pickPoints(level, run, CRYSTALS, 4, 8);
            for (BlockPos at : run.points) {
                place(level, run, at, Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
            run.need = run.points.size();
            run.mobs.clear();
            Circuits.spawnAround(level, run, run.spot, (int) Math.round((2 + ps.size()) * Circuits.harder(run)), 6, 11, near);
            run.phase = 1;
            run.nextWave = now + WAVE;
            for (ServerPlayer p : ps) {
                Notice.hint(p, "message.skyabyss.circuit.charge_start", CRYSTAL_HITS);
            }
            return;
        }
        for (int i = 0; i < run.points.size(); i++) {
            BlockPos at = run.points.get(i);
            if (run.pointProgress.get(i) < CRYSTAL_HITS) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 3, 0.25, 0.3, 0.25, 0.05);
            }
        }
        waves(level, run, ps, now);
        if (run.progress >= run.need) {
            Circuits.complete(level, run);
        }
    }

    // 結晶に当たった (殴る・矢)。満ちたら数える
    static void strike(ServerLevel level, CircuitRun run, BlockPos hit) {
        if (run.kind != CircuitRun.Kind.CHARGE || run.phase != 1) {
            return;
        }
        for (int i = 0; i < run.points.size(); i++) {
            BlockPos at = run.points.get(i);
            int n = run.pointProgress.get(i);
            if (at.distManhattan(hit) > 1 || n >= CRYSTAL_HITS) {
                continue;
            }
            run.pointProgress.set(i, n + 1);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 16 + 8 * n, 0.4, 0.5, 0.4, 0.2);
            level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.3f, 0.8f + 0.3f * n);
            if (n + 1 >= CRYSTAL_HITS) {
                level.setBlock(at, Blocks.SEA_LANTERN.defaultBlockState(), 3);
                level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.5f, 1.7f);
                run.progress++;
                for (ServerPlayer p : Circuits.online(level, run)) {
                    Notice.gain(p, "message.skyabyss.circuit.charged", run.progress, run.need);
                }
            }
            return;
        }
    }

    @SubscribeEvent
    static void onHitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        CircuitRun run = Circuits.of(p);
        if (run != null) {
            strike(p.serverLevel(), run, event.getPos());
        }
    }

    @SubscribeEvent
    static void onProjectile(ProjectileImpactEvent event) {
        if (!(event.getProjectile().level() instanceof ServerLevel level) || !(event.getRayTraceResult() instanceof BlockHitResult hit)
                || !(event.getProjectile().getOwner() instanceof ServerPlayer p)) {
            return;
        }
        CircuitRun run = Circuits.of(p);
        if (run != null) {
            strike(level, run, hit.getBlockPos());
        }
    }

    // ---------------------------------------------------------------- 火を守る
    static void stepFire(ServerLevel level, CircuitRun run, List<ServerPlayer> ps, long now) {
        if (run.phase == 0) {
            ServerPlayer near = Circuits.nearest(ps, run.spot, Circuits.ARRIVE);
            if (near == null) {
                return;
            }
            clear(level, run);
            BlockPos g = Circuits.ground(level, run.spot.getX(), run.spot.getZ());
            for (int i = 0; g == null && i < 16; i++) {
                g = Circuits.ground(level, run.spot.getX() + level.random.nextInt(7) - 3, run.spot.getZ() + level.random.nextInt(7) - 3);
            }
            if (g == null) {
                // 置ける地面がない: 越えたことにする (詰まらせない)
                Circuits.complete(level, run);
                return;
            }
            place(level, run, g, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            run.points.clear();
            run.points.add(g);
            run.pointProgress.add(0);
            run.need = (int) Math.round(FIRE_NEED * Circuits.harder(run));
            run.phase = 1;
            run.nextWave = now + WIND_EVERY;
            for (ServerPlayer p : ps) {
                Notice.hint(p, "message.skyabyss.circuit.fire_start");
            }
            return;
        }
        BlockPos fire = run.points.get(0);
        boolean near = Circuits.nearest(ps, fire, FIRE_R) != null;
        run.progress = near ? run.progress + 10 : Math.max(0, run.progress - 20);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, fire.getX() + 0.5, fire.getY() + 0.8, fire.getZ() + 0.5, 1, 0.1, 0.2, 0.1, 0.01);
        if (!near) {
            level.sendParticles(ParticleTypes.SMOKE, fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
        }
        if (now >= run.nextWave) {
            run.nextWave = now + WIND_EVERY;
            wind(level, run, fire);
        }
        if (run.progress >= run.need) {
            Circuits.complete(level, run);
        }
    }

    // 灰の風: 火を横切る帯 (向きはそのたび変わる)。当たると傷と押し
    static void wind(ServerLevel level, CircuitRun run, BlockPos fire) {
        double angle = level.random.nextDouble() * Math.PI * 2;
        double len = 20;
        double sx = fire.getX() + 0.5 - Math.cos(angle) * len / 2;
        double sz = fire.getZ() + 0.5 - Math.sin(angle) * len / 2;
        float dmg = (float) Circuits.stakeDamage(run);
        Vec3 push = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(0.9);
        Telegraph.in(level, TelegraphShape.line(sx, sz, angle, len, 1.8)).floor(fire.getY()).warn(WIND_WARN).danger()
                .startSound(SoundEvents.ELYTRA_FLYING, 0.5f, 0.6f)
                .victims(p -> run.members.contains(p.getUUID()))
                .onImpact(server -> {
                    for (int i = 0; i <= 20; i++) {
                        double t = i / 20.0 * len;
                        server.sendParticles(ParticleTypes.ASH, sx + Math.cos(angle) * t, fire.getY() + 0.8, sz + Math.sin(angle) * t, 6, 0.4, 0.4, 0.4, 0.02);
                        server.sendParticles(ParticleTypes.LARGE_SMOKE, sx + Math.cos(angle) * t, fire.getY() + 0.6, sz + Math.sin(angle) * t, 1, 0.3, 0.2, 0.3, 0.01);
                    }
                    server.playSound(null, fire, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.6f, 0.5f);
                })
                .onHit((server, player) -> {
                    player.hurt(level.damageSources().generic(), dmg);
                    player.push(push.x, 0.25, push.z);
                    player.hurtMarked = true;
                })
                .start();
    }

    // ---------------------------------------------------------------- 共通
    // 敵の寄せ (WAVE ごと。生きている敵が多ければ待つ)
    static void waves(ServerLevel level, CircuitRun run, List<ServerPlayer> ps, long now) {
        if (now < run.nextWave) {
            return;
        }
        run.nextWave = now + WAVE;
        ServerPlayer near = Circuits.nearest(ps, run.spot, 24);
        if (near != null && Circuits.alive(level, run) < Circuits.BREAK_CAP) {
            Circuits.spawnAround(level, run, run.spot, 1 + ps.size() / 2, 6, 11, near);
        }
    }

    // 柱のまわり (min〜max) の地面を n か所 (互いに 3 以上離す)。見つからなければ柱の所
    static void pickPoints(ServerLevel level, CircuitRun run, int n, int min, int max) {
        run.points.clear();
        run.pointProgress.clear();
        for (int tries = 0; run.points.size() < n && tries < n * 30; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = min + level.random.nextDouble() * (max - min);
            BlockPos g = Circuits.ground(level, (int) Math.floor(run.spot.getX() + Math.cos(a) * d), (int) Math.floor(run.spot.getZ() + Math.sin(a) * d));
            if (g == null || run.points.stream().anyMatch(q -> q.distSqr(g) < 9)) {
                continue;
            }
            run.points.add(g);
            run.pointProgress.add(0);
        }
        while (run.points.size() < n) {
            run.points.add(run.spot.offset(run.points.size(), 0, 0));
            run.pointProgress.add(0);
        }
    }

    // 空気の所にだけ置き、片づけのために覚える
    static void place(ServerLevel level, CircuitRun run, BlockPos at, net.minecraft.world.level.block.state.BlockState state) {
        if (!level.getBlockState(at).isAir()) {
            return;
        }
        level.setBlock(at, state, 3);
        run.placed.add(at.immutable());
    }

    // 置いた結晶と焚き火を片づける (空気に戻す)
    static void clear(ServerLevel level, CircuitRun run) {
        for (BlockPos at : run.placed) {
            var s = level.getBlockState(at);
            if (s.is(Blocks.AMETHYST_BLOCK) || s.is(Blocks.SEA_LANTERN) || s.is(Blocks.CAMPFIRE)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        run.placed.clear();
    }

    // 目的の帯の一行 (土地の課題)
    @Nullable
    static Component taskLine(CircuitRun run, long now) {
        return switch (run.kind) {
            case DIG -> run.phase == 0 ? Component.translatable("circuit.skyabyss.task.dig_go", Circuits.place(run.place))
                    : Component.translatable("circuit.skyabyss.task.dig", run.progress, run.need);
            case TIDE -> run.phase == 0 ? Component.translatable("circuit.skyabyss.task.tide_go", Circuits.place(run.place))
                    : Component.translatable(run.phase == 1 ? (ebb(run, now) ? "circuit.skyabyss.task.tide_ebb" : "circuit.skyabyss.task.tide_wait")
                            : "circuit.skyabyss.task.tide_back", toTurn(run, now) / 20);
            case CHARGE -> run.phase == 0 ? Component.translatable("circuit.skyabyss.task.charge_go", Circuits.place(run.place))
                    : Component.translatable("circuit.skyabyss.task.charge", run.progress, run.need);
            case FIRE -> run.phase == 0 ? Component.translatable("circuit.skyabyss.task.fire_go", Circuits.place(run.place))
                    : Component.translatable("circuit.skyabyss.task.fire", Math.max(0, (run.need - run.progress) / 20));
            default -> null;
        };
    }

    // 目的の帯の行き先 (潮待ちの印を取りに行くあいだは沖の杭)
    @Nullable
    static BlockPos target(CircuitRun run) {
        if (run.kind == CircuitRun.Kind.TIDE && run.phase == 1 && !run.points.isEmpty()) {
            return run.points.get(0);
        }
        return null;
    }
}
