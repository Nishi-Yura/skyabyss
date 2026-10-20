package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck2.Y;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.titan.storm.back.StormBackClock;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormDeck2;
import jp.revati.skyabyss.titan.storm.back.StormFx;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// S11 避雷の護送 (闘・護る・★★。docs/design/29 の 3.3)。避雷の籠 (銅の格子の籠に避雷針) を、大きな甲板の床の道に沿って、南東の塔まで運ぶ。
//   決まり (教える): 籠は、近く (7 マス以内) に人がいるあいだ、道に沿って自分で進む (押さなくてよい)。雷は籠に落ちる: 籠のまわり 2.5 マスに、予告の輪のあと雷が落ちる。
//     輪の中にいる人は傷を受ける。籠から 3 マス以上離れて、でも 7 マス以内で守ろう。道のはじめの三分の一は雷だけ。
//   ひねり: 道の途中から襲撃が来る (雷蛾・羽守)。敵が籠のそば (3.6 マス以内) にいると籠は進まず、傷む。敵は籠に落ちる雷に当たる (敵に大きな傷)。
//     敵を籠のそばへ誘い込んで、雷に当てよう。籠は傷みきると、最後の襲撃の場所へ戻る (罰はない。敵は消える)。
//   凪は雷の間隔が長く (120)・嵐は短い (90)。手を放した (7 マス以内に誰もいない) 籠はその場に留まり、進みも傷みも止まる。
// 人数: 襲撃の敵が増え、籠が丈夫になる。一人でも、籠を連れて歩き、敵を雷へ誘って倒せる
// 修飾: 配置 3 (道の形)・数字 2 (襲撃 2・3・4 回 / 籠の速さ)・敵の組 2 (雷蛾 / 羽守と雷蛾)・障害 2 (二連の雷・空の雷)・任意の目標 (籠を無傷で)
public final class S11Cage extends StormCell {
    static final int[] RAIDS = {2, 3, 4};
    static final double[] SPEED = {0.9, 1.2, 1.6};
    static final double BASE_HP = 36;
    static final double GNAW = 0.3;
    static final double FOLLOW_R = 7.5;
    static final double DANGER_R = 2.6;
    static final double PIN_R = 3.6;
    static final int EVERY_CALM = 120;
    static final int EVERY_STORM = 90;
    static final int STRIKE_WARN = 24;
    static final float STRIKE_PLAYER = 9f;
    static final float STRIKE_MOB = 22f;
    static final int SKY_EVERY = 150;

    public S11Cage() {
        super("S11", new Ports(StormDeck2.S11_GATE, false, List.of(), StormDeck2.S11_STONE, null, "", StormDeck2.S11_AREA, List.of()));
    }

    // ---------------------------------------------------------------- 道
    public static double length(int layout) {
        int[][] r = StormDeck2.S11_ROUTES[layout];
        double len = 0;
        for (int i = 0; i + 1 < r.length; i++) {
            len += Math.abs(r[i + 1][0] - r[i][0]) + Math.abs(r[i + 1][1] - r[i][1]);
        }
        return len;
    }

    // 道のりの s (マス) の位置 {x, z}
    public static double[] at(int layout, double s) {
        int[][] r = StormDeck2.S11_ROUTES[layout];
        double left = Math.max(0, s);
        for (int i = 0; i + 1 < r.length; i++) {
            double seg = Math.abs(r[i + 1][0] - r[i][0]) + Math.abs(r[i + 1][1] - r[i][1]);
            if (left <= seg) {
                double f = seg == 0 ? 0 : left / seg;
                return new double[] {r[i][0] + (r[i + 1][0] - r[i][0]) * f, r[i][1] + (r[i + 1][1] - r[i][1]) * f};
            }
            left -= seg;
        }
        int[] e = r[r.length - 1];
        return new double[] {e[0], e[1]};
    }

    public static List<BlockPos> cells(int layout) {
        Set<BlockPos> set = new LinkedHashSet<>();
        int[][] r = StormDeck2.S11_ROUTES[layout];
        for (int i = 0; i + 1 < r.length; i++) {
            int x = r[i][0];
            int z = r[i][1];
            int dx = Integer.signum(r[i + 1][0] - x);
            int dz = Integer.signum(r[i + 1][1] - z);
            while (x != r[i + 1][0] || z != r[i + 1][1]) {
                set.add(new BlockPos(x, Y, z));
                x += dx;
                z += dz;
            }
        }
        int[] e = r[r.length - 1];
        set.add(new BlockPos(e[0], Y, e[1]));
        return List.copyOf(set);
    }

    // ---------------------------------------------------------------- 数字
    static int raids(Run run) {
        return run.mods.quiet() ? 2 : Abyss.plusOne(run.mods, RAIDS[run.mods.number(0)]);
    }

    static double speed(Run run) {
        double s = SPEED[run.mods.number(1)];
        return run.mods.abyss() ? s * Abyss.NUMBER_STEP : s;
    }

    static double hpMax(int players) {
        return BASE_HP * (1 + 0.3 * (Math.max(1, players) - 1));
    }

    // 雷の間隔 (凪 120・嵐 90。深淵はさらに短い)
    public static int every(Mods m, boolean stormy) {
        return Abyss.shorter(m, stormy ? EVERY_STORM : EVERY_CALM);
    }

    // 一回の襲撃の敵の数 (k = 1 から)
    public static int foes(Run run, int players, int k) {
        int n = 2 + k + (int) Math.ceil((Math.max(1, players) - 1) * 0.8);
        n = Abyss.plusOne(run.mods, n);
        return run.mods.quiet() ? Math.max(1, n - 2) : n;
    }

    // 襲撃が始まる道のり (最初の三分の一は雷だけ)
    public static double raidAt(Run run, int layout, int k) {
        double len = length(layout);
        int total = raids(run);
        return len * (0.30 + 0.65 * (k - 1) / Math.max(1, total));
    }

    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("s", 0.0);
        run.put("trig", 0);
        run.put("chkS", 0.0);
        run.put("chkTrig", 0);
        run.put("hp", hpMax(1));
        run.put("hpMax", hpMax(1));
        run.put("cell", -1L);
        run.put("strikeAt", 0L);
        run.put("nextStrike", back.getGameTime() + 100L);
        run.put("nextSky", back.getGameTime() + 300L);
        run.put("secondAt", 0L);
        if (run.cleared) {
            run.put("s", length(run.mods.layout()));
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        discardAll(back, mobs(run));
    }

    private void spawnRaid(ServerLevel back, Run run, int players, int k, double[] c) {
        int total = foes(run, players, k);
        Random rng = new Random(run.seed ^ (k * 31L));
        boolean wardens = run.mods.enemy() == 1;
        int nWardens = wardens ? (int) Math.ceil(total / 3.0) : 0;
        var d = StormDeck2.S11_DECK;
        for (int j = 0; j < total; j++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = 11 + rng.nextDouble() * 5;
            double x = Math.max(d.x1() + 2, Math.min(d.x2() - 2, c[0] + 0.5 + Math.cos(a) * r));
            double z = Math.max(d.z1() + 2, Math.min(d.z2() - 2, c[1] + 0.5 + Math.sin(a) * r));
            Mob m = j < nWardens ? spawn(back, StormMobSetup.FEATHER_WARDEN.get(), run.group, x, Y + 1, z)
                    : spawn(back, StormMobSetup.STATIC_MOTH.get(), run.group, x, Y + 4, z);
            if (m != null) {
                mobs(run).add(m.getUUID());
            }
        }
        TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.RAVAGER_ROAR, 0.9f, 1.2f);
        TrialFx.bar(jp.revati.skyabyss.titan.group.Groups.online(back, run.group), ModText.battle("message.skyabyss.trial.s11.raid", k, raids(run)));
    }

    private static double dist(double x1, double z1, double x2, double z2) {
        return Math.hypot(x1 - x2, z1 - z2);
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int layout = run.mods.layout();
        int players = count(inside);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            bar(inside, ModText.objective("message.skyabyss.trial.s11.start"));
        }
        for (ServerPlayer p : inside) {
            if (fell(p, Y)) {
                toRest(back, run, p, "s11a", "message.skyabyss.trial.s11.fell");
            }
        }
        if (run.cleared) {
            return;
        }
        double len = length(layout);
        double s = (Double) run.data.get("s");
        double hp = (Double) run.data.get("hp");
        double hpMax = hpMax(players);
        if (players > run.getInt("maxPlayers")) {
            double ratio = hp / (Double) run.data.get("hpMax");
            run.put("maxPlayers", players);
            run.put("hpMax", hpMax);
            hp = hpMax * ratio;
        }
        double[] c = at(layout, s);
        boolean peaceful = back.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
        // 襲撃
        int trig = run.getInt("trig");
        int k = raids(run);
        if (trig < k && s >= raidAt(run, layout, trig + 1) && !peaceful) {
            trig++;
            run.put("trig", trig);
            run.put("chkS", s);
            run.put("chkTrig", trig - 1);
            spawnRaid(back, run, players, trig, c);
        }
        // 敵: 籠へ向かう (近くに人がいなければ)。籠のそばの敵は籠をかじる
        int gnawing = 0;
        List<UUID> list = mobs(run);
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(list.get(i));
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                list.remove(i);
                continue;
            }
            boolean nearPlayer = false;
            for (ServerPlayer p : inside) {
                if (p.distanceToSqr(mob) < 49) {
                    nearPlayer = true;
                    break;
                }
            }
            double d = dist(mob.getX(), mob.getZ(), c[0] + 0.5, c[1] + 0.5);
            if (!nearPlayer && d > 1.8) {
                mob.getNavigation().moveTo(c[0] + 0.5, mob.getY(), c[1] + 0.5, 1.15);
            }
            if (d < PIN_R) {
                gnawing++;
            }
        }
        // 籠に付き添う人
        int followers = 0;
        for (ServerPlayer p : inside) {
            if (dist(p.getX(), p.getZ(), c[0] + 0.5, c[1] + 0.5) < FOLLOW_R && Math.abs(p.getY() - (Y + 1)) < 4) {
                followers++;
            }
        }
        if (gnawing > 0) {
            hp -= gnawing * GNAW;
            if (run.getInt("scratched") == 0) {
                run.put("scratched", 1);
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
            }
            TrialFx.particles(back, run.group, ParticleTypes.CRIT, c[0] + 0.5, Y + 2, c[1] + 0.5, 4, 0.5, 0.3, 0.5, 0.1);
            if (now % 20 == 0) {
                TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.s11.pinned", (int) Math.max(0, Math.round(100 * hp / hpMax))));
            }
        } else if (followers > 0) {
            s += speed(run) * (0.8 + 0.2 * Math.min(3, followers)) * 0.25;
            if (now % 10 == 0) {
                TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.COPPER_STEP, 0.8f, 0.9f);
            }
        }
        if (hp <= 0) {
            discardAll(back, mobs(run));
            s = (Double) run.data.get("chkS") - 0.01;
            run.put("trig", run.getInt("chkTrig"));
            hp = hpMax;
            TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.COPPER_BREAK, 1.5f, 0.6f);
            TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.s11.broken"));
        }
        run.put("hp", Math.min(hp, hpMax));
        run.put("s", Math.min(s, len));
        // 雷
        strikes(back, run, inside, now, c);
        // 見かけ (籠のいるマスか、傷みの段が変わったら送り直す)
        double[] c2 = at(layout, Math.min(s, len));
        long cell = (long) Math.floor(c2[0]) * 1000L + (long) Math.floor(c2[1]) + (hp < hpMax * 0.5 ? 1_000_000L : 0L);
        if (cell != run.getLong("cell")) {
            run.put("cell", cell);
            TrialRuntime.refresh(back, run);
        }
        if (s >= len - 0.3) {
            run.cleared = true;
            run.clearedAt = now;
            discardAll(back, mobs(run));
            TrialFx.sound(back, run.group, StormDeck2.S11_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.7f);
            TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.s11.arrived"));
            TrialRuntime.refresh(back, run);
        }
    }

    // 雷: 予告の輪 (籠のまわり) → 落ちる。二連 (障害) は 1 秒後にもう一度。空の雷 (障害) は人の足もとへ
    private void strikes(ServerLevel back, Run run, List<ServerPlayer> inside, long now, double[] c) {
        Vec3 cage = new Vec3(c[0] + 0.5, Y + 1.0, c[1] + 0.5);
        long at = run.getLong("strikeAt");
        if (at == 0 && now >= run.getLong("nextStrike")) {
            at = now + STRIKE_WARN;
            run.put("strikeAt", at);
            run.put("strikeTarget", cage);
            back.playSound(null, BlockPos.containing(cage), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0f, 1.7f);
            if (!run.data.containsKey("warned")) {
                run.put("warned", true);
                bar(inside, ModText.hint("message.skyabyss.trial.s11.rule"));
            }
        }
        if (at != 0) {
            Vec3 target = (Vec3) run.data.get("strikeTarget");
            if (now < at) {
                if (now % 5 == 0) {
                    StormFx.to(run.group, () -> StormFx.ring(back, ParticleTypes.ELECTRIC_SPARK, target.add(0, 0.15, 0), DANGER_R, 16));
                }
            } else {
                run.put("strikeAt", 0L);
                boolean stormy = StormBackClock.strength(back) >= 0.5;
                run.put("nextStrike", now + every(run.mods, stormy));
                bolt(back, run, inside, target);
                if (run.mods.hasHazard(0)) {
                    run.put("secondAt", now + 20);
                }
            }
        }
        long second = run.getLong("secondAt");
        if (second != 0 && now >= second) {
            run.put("secondAt", 0L);
            bolt(back, run, inside, cage);
        }
        // 空の雷 (障害)
        if (run.mods.hasHazard(1) && now >= run.getLong("nextSky") && !inside.isEmpty() && back.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            ServerPlayer target = inside.get(back.random.nextInt(inside.size()));
            run.put("nextSky", now + SKY_EVERY);
            StormStrike.call(back, target.position(), 24, 8f, null, run.group);
        }
        // 危ない輪の目印 (薄く、いつも)
        if (now % 10 == 0) {
            StormFx.to(run.group, () -> StormFx.ring(back, ParticleTypes.GLOW, cage.add(0, 0.1, 0), DANGER_R, 10));
        }
    }

    private void bolt(ServerLevel back, Run run, List<ServerPlayer> inside, Vec3 at) {
        StormFx.to(run.group, () -> StormStrike.bolt(back, at.add(0, 16, 0), at.add(0, 1.3, 0)));
        for (ServerPlayer p : inside) {
            if (!p.isCreative() && dist(p.getX(), p.getZ(), at.x, at.z) <= DANGER_R && Math.abs(p.getY() - at.y) < 3.5) {
                p.hurt(back.damageSources().lightningBolt(), STRIKE_PLAYER);
                p.setDeltaMovement(p.getDeltaMovement().add(0, 0.35, 0));
                p.hurtMarked = true;
            }
        }
        for (UUID id : new ArrayList<>(mobs(run))) {
            Entity e = back.getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive() && dist(le.getX(), le.getZ(), at.x, at.z) <= DANGER_R + 0.6 && Math.abs(le.getY() - at.y) < 6) {
                le.hurt(back.damageSources().lightningBolt(), STRIKE_MOB);
            }
        }
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int layout = run.mods.layout();
        double s = run.data.get("s") instanceof Double d ? d : 0.0;
        double hp = run.data.get("hp") instanceof Double d ? d : hpMax(1);
        double hpMax = run.data.get("hpMax") instanceof Double d ? d : hpMax(1);
        List<BlockPos> cells = cells(layout);
        BlockState ahead = Blocks.YELLOW_CONCRETE.defaultBlockState();
        BlockState passed = Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
        BlockState lamp = Blocks.GLOWSTONE.defaultBlockState();
        double[] c = at(layout, s);
        int cx = (int) Math.floor(c[0]);
        int cz = (int) Math.floor(c[1]);
        int idx = 0;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).getX() == cx && cells.get(i).getZ() == cz) {
                idx = i;
            }
        }
        for (int i = 0; i < cells.size(); i++) {
            out.accept(cells.get(i), i % 4 == 0 ? lamp : i < idx ? passed : ahead);
        }
        // 籠: 銅の格子に避雷針 (傷むと錆びた色)
        BlockPos here = new BlockPos(cx, Y + 1, cz);
        out.accept(here, hp < hpMax * 0.5 ? Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState() : Blocks.WAXED_COPPER_GRATE.defaultBlockState());
        out.accept(here.above(), Blocks.LIGHTNING_ROD.defaultBlockState());
        out.accept(new BlockPos(cx, Y, cz), Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
    }

    // ---------------------------------------------------------------- 検査
    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        var d = StormDeck2.S11_DECK;
        for (BlockPos p : d.floor()) {
            if (back.getBlockState(p).isAir()) {
                bad.add("S11 deck has a hole at " + p);
                break;
            }
        }
        for (int l = 0; l < StormDeck2.S11_ROUTES.length; l++) {
            int[][] r = StormDeck2.S11_ROUTES[l];
            if (r[0][0] != 20 || r[0][1] != 201) {
                bad.add("S11 route " + l + " does not start at (20,201)");
            }
            int[] e = r[r.length - 1];
            if (e[0] != 62 || e[1] != 244) {
                bad.add("S11 route " + l + " does not end at (62,244)");
            }
            for (int[] pt : r) {
                if (pt[0] < d.x1() + 2 || pt[0] > d.x2() - 2 || pt[1] < d.z1() + 2 || pt[1] > d.z2() - 2) {
                    bad.add("S11 route " + l + " leaves the deck at " + pt[0] + "," + pt[1]);
                }
            }
            for (int i = 0; i + 1 < r.length; i++) {
                if (r[i][0] != r[i + 1][0] && r[i][1] != r[i + 1][1]) {
                    bad.add("S11 route " + l + " has a diagonal leg at " + i);
                }
            }
            for (BlockPos p : cells(l)) {
                if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).isAir() || !back.getBlockState(p.above(2)).isAir()
                        || !back.getBlockState(p.above(3)).isAir()) {
                    bad.add("S11 route " + l + " is blocked at " + p);
                    break;
                }
            }
        }
        if (!back.getBlockState(StormDeck2.S11_STONE).is(StormBackSetup.CHIME_STONE.get())) {
            bad.add("S11 stone missing");
        }
        return bad;
    }
}
