package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck.Y;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormBlocks;
import jp.revati.skyabyss.titan.storm.back.StormDeck;
import jp.revati.skyabyss.titan.storm.back.StormFx;
import jp.revati.skyabyss.titan.storm.back.StormRods;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import jp.revati.skyabyss.titan.storm.mob.FeatherWarden;
import jp.revati.skyabyss.titan.storm.mob.StaticMoth;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.titan.storm.mob.ThunderSupplicant;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

// S09 雷乞いの巣 (戦う・★。docs/design/29 の 3.3。雷獣の巣は東の試練の終わりにあるので、こちらは「雷乞い」の巣)。
// 丸い巣の縁に避雷の柱が 6 本。雷乞いが人の足もとに雷の輪 (1.2 秒の予告) を呼ぶ。柱の 3 ブロック以内の輪は柱が受ける。
//   決まり (教える): 柱のそばに立てば雷の輪は柱へ落ちる。柱は三度受けると、人にも敵にも当たる放電をする (三つ目の電球が灯ると 1.2 秒で放電。半径 3.6)。
//     最初の波は雷乞いだけ。柱のそばで受け、二度目で離れて、雷乞いへ走る。
//   ひねり: 後の波では、雷蛾 (柱に寄って放電を誘う) か羽守 (嵐では突っ込む) が加わる。敵を放電の中へ誘い込むと、敵に大きな傷が入る。
//   波を退けると、東の橋の戸が開いて石の島へ。
// 人数: 雷乞いは二人ごとに一体多い (上限 +3)・敵の組が増える。一人でも柱で受けながら一体ずつ倒せる。部屋の占有 (柱の雷はこの背に一つ)
// 修飾: 配置 3 (敵の湧く向き)・数字 2 (雷乞い 1/2/3・波 1/2/3)・敵の組 2 (雷蛾 / 羽守)・障害 2 (過負荷 = 柱が二度で放電・空の雷 = 無予告ではない輪が余分に降る)・任意の目標 (無傷で)
public final class S09Nest extends StormCell {
    static final int[] CALLERS = {1, 2, 3};
    static final int[] WAVES = {1, 2, 3};
    // 柱の放電: 受ける回数・予告 (tick)・半径・傷 (人・敵)
    static final int OVERLOAD = 3;
    static final int OVERLOAD_WARN = 24;
    static final double BLAST_R = 3.6;
    static final float BLAST_PLAYER = 7f;
    static final float BLAST_MOB = 20f;
    // 空の雷 (障害): 間隔・予告
    static final int SKY_EVERY = 100;
    static final int SKY_WARN = 24;
    // この半径の内に人が入ったら始まる
    static final double START_R = 12;

    // 部屋の占有: 柱の雷は背に一つなので、一度に一つのグループだけ。持ち主
    private static UUID owner;

    private static final List<BlockPos> RODS = StormDeck.s09Rods();

    public S09Nest() {
        super("S09", new Ports(StormDeck.S09_GATE, false, StormDeck.S09_EXIT_GATE, StormDeck.S09_STONE, null, "", StormDeck.S09_AREA, List.of()));
        // 雷が柱へ落ちたら数える (どのグループのものか。招雷の group)
        StormStrike.listen(S09Nest::onStrike);
    }

    // ---------------------------------------------------------------- 数字
    public static int callers(Run run, int players) {
        int c = Abyss.plusOne(run.mods, CALLERS[run.mods.number(0)]) + extra(players, 3);
        return run.mods.quiet() ? Math.max(1, c - 1) : c;
    }

    public static int waves(Run run) {
        return run.mods.quiet() ? 1 : Abyss.plusOne(run.mods, WAVES[run.mods.number(1)]);
    }

    // 柱が放電する回数
    public static int overload(Mods m) {
        return m.hasHazard(0) ? OVERLOAD - 1 : OVERLOAD;
    }

    // ---------------------------------------------------------------- 状態
    private static int[] counts(Run run) {
        return run.get("counts", () -> new int[RODS.size()]);
    }

    // 放電の時刻 (予告中。なければ 0)
    private static long[] due(Run run) {
        return run.get("due", () -> new long[RODS.size()]);
    }

    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    // 空の雷の予告 (落ちる時刻と場所)
    @Nullable
    private static Vec3 skyAt(Run run) {
        return (Vec3) run.data.get("skyAt");
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("wave", 0);
        run.put("started", false);
        run.put("blocked", false);
        if (owner == null || owner.equals(run.group)) {
            owner = run.group;
        } else {
            run.put("blocked", true);
        }
        for (BlockPos base : RODS) {
            StormRods.register(back, base);
        }
        if (run.cleared) {
            GroupGates.open(back, run.group, StormDeck.S09_EXIT_GATE);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        discardAll(back, mobs(run));
        if (!run.cleared) {
            GroupGates.release(back, run.group, StormDeck.S09_EXIT_GATE);
        }
        if (run.group.equals(owner)) {
            owner = null;
        }
    }

    // 招雷が柱に落ちた: そのグループの巣の柱の数を増やす
    private static void onStrike(StormStrike.Strike s) {
        if (s.group == null || s.rod == null) {
            return;
        }
        Run run = TrialRuntime.runOf(s.group, "S09");
        if (run == null || run.cleared) {
            return;
        }
        int idx = RODS.indexOf(s.rod);
        if (idx < 0) {
            return;
        }
        int[] counts = counts(run);
        long[] due = due(run);
        if (due[idx] != 0) {
            return;
        }
        counts[idx]++;
        if (counts[idx] >= overload(run.mods)) {
            due[idx] = s.level.getGameTime() + OVERLOAD_WARN;
            for (ServerPlayer p : jp.revati.skyabyss.titan.group.Groups.online(s.level, s.group)) {
                if (p.distanceToSqr(Vec3.atBottomCenterOf(s.rod)) < 24 * 24) {
                    Notice.bar(p, ModText.deny("message.skyabyss.trial.s09.overload"));
                }
            }
        }
        TrialRuntime.refresh(s.level, run);
    }

    // ---------------------------------------------------------------- 動かす
    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if ((Boolean) run.data.getOrDefault("blocked", false)) {
            if (owner == null) {
                run.put("blocked", false);
                owner = run.group;
            } else {
                for (ServerPlayer p : inside) {
                    if (p.getZ() > 86) {
                        jp.revati.skyabyss.titan.storm.back.StormBackTrials.returnToRest(p, "yard");
                        Notice.bar(p, ModText.deny("message.skyabyss.trial.s09.occupied"));
                    }
                }
                return;
            }
        }
        int n = count(inside);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            bar(inside, ModText.objective("message.skyabyss.trial.s09.start"));
        }
        // 落ちた: 控えへ
        for (ServerPlayer p : inside) {
            if (fell(p, Y)) {
                toRest(back, run, p, "s09a", "message.skyabyss.trial.s09.fell");
            }
        }
        if (run.mods.goal() == 0 && run.getInt("hits") > 0) {
            run.goalOk = false;
        }
        tickRods(back, run, inside, now);
        if (run.cleared) {
            return;
        }
        if (back.getDifficulty() == Difficulty.PEACEFUL) {
            if (now % 100 == 0) {
                bar(inside, ModText.hint("message.skyabyss.trial.s09.peaceful"));
            }
            return;
        }
        boolean started = (Boolean) run.data.getOrDefault("started", false);
        if (!started) {
            for (ServerPlayer p : inside) {
                if (Math.hypot(p.getX() - (StormDeck.S09_ARENA.cx() + 0.5), p.getZ() - (StormDeck.S09_ARENA.cz() + 0.5)) < START_R && p.getY() >= Y + 0.5) {
                    run.put("started", true);
                    run.put("nextWave", now + 60L);
                    run.put("nextSky", now + 200L);
                    started = true;
                    break;
                }
            }
            if (!started) {
                return;
            }
        }
        int wave = run.getInt("wave");
        int alive = alive(back, mobs(run));
        boolean inProgress = (Boolean) run.data.getOrDefault("inProgress", false);
        if (inProgress && alive == 0) {
            run.put("inProgress", false);
            if (wave >= waves(run)) {
                run.cleared = true;
                run.clearedAt = now;
                GroupGates.open(back, run.group, StormDeck.S09_EXIT_GATE);
                TrialFx.sound(back, run.group, StormDeck.S09_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.7f);
                bar(inside, ModText.progress("message.skyabyss.trial.s09.cleared"));
                TrialRuntime.refresh(back, run);
                return;
            }
            run.put("nextWave", now + 100L);
            bar(inside, ModText.progress("message.skyabyss.trial.s09.wave_done"));
        } else if (!inProgress && now >= run.getLong("nextWave")) {
            run.put("inProgress", true);
            run.put("wave", wave + 1);
            spawnWave(back, run, n, wave + 1);
        }
        leash(back, run);
        if (inProgress && run.mods.hasHazard(1) && now >= run.getLong("nextSky") && skyAt(run) == null && !inside.isEmpty()) {
            ServerPlayer target = inside.get(back.random.nextInt(inside.size()));
            Vec3 at = target.position();
            run.put("skyAt", at);
            run.put("skyDue", now + SKY_WARN);
            run.put("nextSky", now + SKY_EVERY);
            StormStrike.call(back, at, SKY_WARN, 8f, null, run.group);
        }
        if (skyAt(run) != null && now >= run.getLong("skyDue")) {
            run.data.remove("skyAt");
        }
    }

    // 敵を湧かせる (巣の縁。向きは配置で回る)
    private void spawnWave(ServerLevel back, Run run, int players, int wave) {
        int callers = callers(run, players);
        boolean quiet = run.mods.quiet();
        double base = Math.toRadians(20 * run.mods.layout() + 37 * wave);
        int c = StormDeck.S09_ARENA.cx();
        int z0 = StormDeck.S09_ARENA.cz();
        // 設計書 26 の 8.7 の B: 一人・二人は、入口 (西の渡り) の側の縁に寄せる。3 人からは配置で回る輪のまま
        double bearing = jp.revati.skyabyss.combat.RoomPlay.bearingFor(c, z0, jp.revati.skyabyss.titan.dungeon.RoomPlayRooms.entranceOf("s09"));
        for (int i = 0; i < callers; i++) {
            double a = jp.revati.skyabyss.combat.RoomPlay.angleNear(base + Math.PI * 2 * i / callers, i, callers, bearing, players);
            ThunderSupplicant s = spawn(back, StormMobSetup.THUNDER_SUPPLICANT.get(), run.group, c + 0.5 + Math.cos(a) * 10, Y + 1, z0 + 0.5 + Math.sin(a) * 10);
            if (s != null) {
                mobs(run).add(s.getUUID());
            }
        }
        if (!quiet && wave >= 2) {
            int support = 1 + (players >= 3 ? 1 : 0) + (wave >= 3 ? 1 : 0);
            for (int i = 0; i < support; i++) {
                double a = jp.revati.skyabyss.combat.RoomPlay.angleNear(base + Math.PI * (0.4 + 2.0 * i / Math.max(1, support)), i, support,
                        bearing + 0.35, players);
                Mob m;
                if (run.mods.enemy() == 1) {
                    m = spawn(back, StormMobSetup.FEATHER_WARDEN.get(), run.group, c + 0.5 + Math.cos(a) * 8, Y + 1, z0 + 0.5 + Math.sin(a) * 8);
                } else {
                    m = spawn(back, StormMobSetup.STATIC_MOTH.get(), run.group, c + 0.5 + Math.cos(a) * 5, Y + 4, z0 + 0.5 + Math.sin(a) * 5);
                }
                if (m != null) {
                    mobs(run).add(m.getUUID());
                }
            }
        }
        TrialFx.sound(back, run.group, StormDeck.S09_ARENA.center(), SoundEvents.RAVAGER_ROAR, 0.8f, 1.2f);
        List<ServerPlayer> all = jp.revati.skyabyss.titan.group.Groups.online(back, run.group);
        TrialFx.bar(all, ModText.battle("message.skyabyss.trial.s09.wave", wave, waves(run)));
    }

    // 巣から出た敵を戻す・落ちた敵は数えから外す
    private void leash(ServerLevel back, Run run) {
        Vec3 c = new Vec3(StormDeck.S09_ARENA.cx() + 0.5, Y + 1, StormDeck.S09_ARENA.cz() + 0.5);
        for (UUID id : new ArrayList<>(mobs(run))) {
            Entity e = back.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) {
                continue;
            }
            if (m.getY() < Y - 4 || m.position().distanceTo(c) > StormDeck.S09_ARENA_R + 4) {
                double a = back.random.nextDouble() * Math.PI * 2;
                m.teleportTo(c.x + Math.cos(a) * 6, m instanceof StaticMoth ? Y + 4 : Y + 1, c.z + Math.sin(a) * 6);
                m.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    // 柱: 放電の予告・放電。電球は overlay
    private void tickRods(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        long[] due = due(run);
        int[] counts = counts(run);
        for (int i = 0; i < RODS.size(); i++) {
            if (due[i] == 0) {
                continue;
            }
            BlockPos base = RODS.get(i);
            Vec3 at = Vec3.atBottomCenterOf(base);
            if (now < due[i]) {
                StormFx.to(run.group, () -> StormFx.ring(back, ParticleTypes.ELECTRIC_SPARK, at.add(0, 0.2, 0), BLAST_R, 14));
                if (now % 10 == 0) {
                    back.playSound(null, base, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9f, 1.8f);
                }
                continue;
            }
            // 放電
            due[i] = 0;
            counts[i] = 0;
            Vec3 tip = new Vec3(base.getX() + 0.5, base.getY() + StormRods.HEIGHT, base.getZ() + 0.5);
            StormFx.to(run.group, () -> {
                StormStrike.bolt(back, tip.add(0, 14, 0), tip);
                StormFx.ring(back, ParticleTypes.END_ROD, at.add(0, 0.3, 0), BLAST_R, 24);
            });
            for (ServerPlayer p : inside) {
                if (inBlast(p, at) && !p.isCreative()) {
                    p.hurt(back.damageSources().lightningBolt(), BLAST_PLAYER);
                    p.setDeltaMovement(p.getDeltaMovement().add(0, 0.35, 0));
                    p.hurtMarked = true;
                }
            }
            for (UUID id : new ArrayList<>(mobs(run))) {
                Entity e = back.getEntity(id);
                if (e instanceof LivingEntity le && le.isAlive() && inBlast(le, at)) {
                    le.hurt(back.damageSources().lightningBolt(), BLAST_MOB);
                }
            }
            TrialRuntime.refresh(back, run);
        }
    }

    private static boolean inBlast(LivingEntity e, Vec3 at) {
        return Math.hypot(e.getX() - at.x, e.getZ() - at.z) <= BLAST_R && Math.abs(e.getY() - at.y) <= 4.5;
    }

    // 電球: 受けた回数だけ灯る (放電の予告中は三つとも灯る)
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int[] counts = counts(run);
        long[] due = due(run);
        for (int i = 0; i < RODS.size(); i++) {
            int lit = due[i] != 0 ? 3 : Math.min(3, counts[i] * 3 / Math.max(1, overload(run.mods)));
            BlockPos base = RODS.get(i);
            for (int k = 0; k < 3; k++) {
                out.accept(base.above(1 + k), StormBackSetup.ROD_BULB.get().defaultBlockState().setValue(StormBlocks.RodBulb.LIT, lit > k));
            }
        }
    }

    // ---------------------------------------------------------------- 検査
    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (BlockPos p : StormDeck.S09_ARENA.floor()) {
            if (back.getBlockState(p).isAir()) {
                bad.add("S09 arena has a hole at " + p);
                break;
            }
        }
        for (BlockPos base : RODS) {
            if (!back.getBlockState(base.above(5)).is(StormBackSetup.ROD_TIP.get())) {
                bad.add("S09 rod missing at " + base);
            }
            if (Math.hypot(base.getX() - StormDeck.S09_ARENA.cx(), base.getZ() - StormDeck.S09_ARENA.cz()) > StormDeck.S09_ARENA_R - 3) {
                bad.add("S09 rod too near the rim at " + base);
            }
        }
        if (!back.getBlockState(StormDeck.S09_STONE).is(StormBackSetup.CHIME_STONE.get())) {
            bad.add("S09 stone missing");
        }
        // 柱どうしの間は 6 以上 (放電が重ならない) 、6 本のどの二本も 3 ブロック以内の柱を共有しない
        for (int i = 0; i < RODS.size(); i++) {
            for (int j = i + 1; j < RODS.size(); j++) {
                if (Math.hypot(RODS.get(i).getX() - RODS.get(j).getX(), RODS.get(i).getZ() - RODS.get(j).getZ()) < 6) {
                    bad.add("S09 rods " + i + " and " + j + " are too close");
                }
            }
        }
        return bad;
    }

    // 試験用: 巣の持ち主を消す
    static void clearOwner() {
        owner = null;
    }

    static Map<String, Object> debug(Run run) {
        Map<String, Object> m = new HashMap<>();
        m.put("counts", java.util.Arrays.toString(counts(run)));
        m.put("wave", run.getInt("wave"));
        return m;
    }
}
