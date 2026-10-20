package jp.revati.skyabyss.trial.whale;

import static jp.revati.skyabyss.titan.dungeon.WhaleDeck.Y;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRock;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// W11 棺の護送 (闘・護る・★★。docs/design/29 の 3.1)。眠る番兵の棺 (骨の台車) を、広間の床の「道」に沿って祭壇まで押す。
//   棺は、近くに人がいる (押している) あいだだけ進む。骨の群れは棺を狙う。棺のそばに敵がいると、棺は進めない (囲まれて動けない) ので、押す人と守る人に分かれる。
//   棺は傷む。傷みきると、最後に群れが現れた所まで戻る (罰はない。群れは消えて、やり直し)。
//   一人: 棺の傷みにくさ・群れの数は、入った人数で決まる。手を放した棺はその場に留まる (進みも傷みも止まる)。
// 修飾: 配置 3 (道の形)・数字 2 (襲撃の回数 2・3・4 / 棺の速さ)・敵の組 2 (骨の群れ / 射手つき)・障害 (落石)・任意の目標 (棺を無傷で)
public final class W11Coffin implements TrialCell {
    // 道 (x, z の折れ線)。どの配置も、始点 (2, 133) から終点 (2, 163) へ
    public static final int[][][] ROUTES = {
            {{2, 133}, {2, 141}, {-3, 141}, {-3, 152}, {7, 152}, {7, 158}, {2, 158}, {2, 163}},
            {{2, 133}, {2, 138}, {8, 138}, {8, 147}, {-4, 147}, {-4, 157}, {2, 157}, {2, 163}},
            {{2, 133}, {2, 145}, {8, 145}, {8, 150}, {-4, 150}, {-4, 156}, {4, 156}, {4, 163}, {2, 163}}};
    static final int[] RAIDS = {2, 3, 4};
    // 棺の速さ (マス/秒)
    static final double[] SPEED = {0.9, 1.2, 1.6};
    static final double BASE_HP = 36;
    static final double GNAW = 0.3;
    static final int ROCK_EVERY = 230;

    private final Def def = TrialCatalog.get("W11");
    private final Ports ports = new Ports(WhaleDeck.W11_GATE, false, WhaleDeck.W11_EXIT, WhaleDeck.W11_STONE, WhaleDeck.W11_PAD, WhaleDeck.REST_S1,
            new net.minecraft.world.phys.AABB(-6, Y + 1, 129, 11, Y + 9, 173), WhaleDeck.rests().stream().filter(r -> r.id().equals(WhaleDeck.REST_W11)).toList());

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // ---------------------------------------------------------------- 道
    static double length(int layout) {
        int[][] r = ROUTES[layout];
        double len = 0;
        for (int i = 0; i + 1 < r.length; i++) {
            len += Math.abs(r[i + 1][0] - r[i][0]) + Math.abs(r[i + 1][1] - r[i][1]);
        }
        return len;
    }

    // 道のりの s (マス) の位置 {x, z}。折れ線の上
    public static double[] at(int layout, double s) {
        int[][] r = ROUTES[layout];
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

    // 道の床のマス (重ならず順に)
    public static List<BlockPos> cells(int layout) {
        Set<BlockPos> set = new LinkedHashSet<>();
        int[][] r = ROUTES[layout];
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

    // 一回の襲撃の敵の数 (k = 1 から)
    public static int foes(Run run, int players, int k) {
        int n = 2 + k + (int) Math.ceil((Math.max(1, players) - 1) * 0.8);
        n = Abyss.plusOne(run.mods, n);
        return run.mods.quiet() ? Math.max(1, n - 2) : n;
    }

    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    private static Map<UUID, Boolean> melee(Run run) {
        return run.get("melee", () -> new HashMap<UUID, Boolean>());
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
        run.put("nextRock", back.getGameTime() + 300L);
        if (run.cleared) {
            GroupGates.open(back, run.group, WhaleDeck.W11_EXIT);
            run.put("s", length(run.mods.layout()));
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        clearMobs(back, run);
        if (!run.cleared) {
            GroupGates.release(back, run.group, WhaleDeck.W11_EXIT);
        }
    }

    private static void clearMobs(ServerLevel back, Run run) {
        for (UUID id : mobs(run)) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        mobs(run).clear();
        melee(run).clear();
    }

    private static double dist(double x1, double z1, double x2, double z2) {
        return Math.hypot(x1 - x2, z1 - z2);
    }

    private void spawnRaid(ServerLevel back, Run run, int players, int k, double[] c) {
        int total = foes(run, players, k);
        Random rng = new Random(run.seed ^ (k * 31L));
        boolean archers = run.mods.enemy() == 1;
        for (int j = 0; j < total; j++) {
            boolean west = j % 2 == 0;
            double zz = Math.max(131, Math.min(163, c[1] + (west ? -2 : 3) + rng.nextInt(4)));
            BlockPos at = new BlockPos(west ? -5 : 9, Y + 1, (int) zz);
            String kind = archers ? (j % 3 == 0 ? "crawler" : "archer") : (j % 3 == 2 ? "sentinel" : "crawler");
            Mob m = TitanBackTrials.spawnGuard(back, kind, at, run.group);
            if (m != null) {
                mobs(run).add(m.getUUID());
                melee(run).put(m.getUUID(), !kind.equals("archer"));
            }
        }
        TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.SKELETON_AMBIENT, 1.5f, 0.5f);
        TrialFx.bar(jp.revati.skyabyss.titan.group.Groups.online(back, run.group), ModText.battle("message.skyabyss.trial.w11.raid", k, raids(run)));
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        int layout = m.layout();
        int players = Math.max(1, inside.size());
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.w11.start"));
        }
        if (run.cleared) {
            return;
        }
        double len = length(layout);
        double s = (Double) run.data.get("s");
        double hp = (Double) run.data.get("hp");
        double hpMax = hpMax(players);
        if (players > run.getInt("maxPlayers")) {
            // 人が増えたぶん、棺は丈夫になる (傷みの割合は保つ)
            double ratio = hp / (Double) run.data.get("hpMax");
            run.put("maxPlayers", players);
            run.put("hpMax", hpMax);
            hp = hpMax * ratio;
        }
        double[] c = at(layout, s);
        // 襲撃
        int trig = run.getInt("trig");
        int k = raids(run);
        if (trig < k && s >= len * (trig + 1) / (k + 1)) {
            trig++;
            run.put("trig", trig);
            run.put("chkS", s);
            run.put("chkTrig", trig - 1);
            spawnRaid(back, run, players, trig, c);
        }
        // 敵: 棺へ向かう (近くに人がいなければ)。棺のそばの近接の敵は棺をかじる
        int gnawing = 0;
        List<UUID> list = mobs(run);
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(list.get(i));
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                melee(run).remove(list.get(i));
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
                mob.getNavigation().moveTo(c[0] + 0.5, Y + 1, c[1] + 0.5, 1.15);
            }
            if (d < 2.6 && Boolean.TRUE.equals(melee(run).get(mob.getUUID()))) {
                gnawing++;
            }
        }
        // 押す人
        int pushers = 0;
        for (ServerPlayer p : inside) {
            if (dist(p.getX(), p.getZ(), c[0] + 0.5, c[1] + 0.5) < 3.2 && Math.abs(p.getY() - (Y + 1)) < 3) {
                pushers++;
            }
        }
        if (gnawing > 0) {
            hp -= gnawing * GNAW;
            if (run.getInt("scratched") == 0) {
                run.put("scratched", 1);
                if (m.goal() == 0) {
                    run.goalOk = false;
                }
            }
            TrialFx.particles(back, run.group, ParticleTypes.CRIT, c[0] + 0.5, Y + 2, c[1] + 0.5, 4, 0.5, 0.3, 0.5, 0.1);
            if (now % 20 == 0) {
                TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.w11.pinned", (int) Math.max(0, Math.round(100 * hp / hpMax))));
            }
        } else if (pushers > 0) {
            s += speed(run) * (0.7 + 0.3 * Math.min(3, pushers)) * 0.25;
            if (now % 8 == 0) {
                TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.BONE_BLOCK_STEP, 0.7f, 0.8f);
            }
        }
        if (hp <= 0) {
            // 傷みきった: 最後の襲撃の場所へ戻る
            clearMobs(back, run);
            s = (Double) run.data.get("chkS") - 0.01;
            run.put("trig", run.getInt("chkTrig"));
            hp = hpMax;
            TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.SKELETON_DEATH, 1.5f, 0.6f);
            TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.w11.broken"));
        }
        run.put("hp", Math.min(hp, hpMax));
        run.put("s", Math.min(s, len));
        // 落石
        if (m.hasHazard(0)) {
            TrialRock.tick(back, run, inside, now, ROCK_EVERY, 4f);
        }
        // 見かけ (棺のいるマスか、傷みの段が変わったら送り直す)
        double[] c2 = at(layout, Math.min(s, len));
        long cell = (long) Math.floor(c2[0]) * 1000L + (long) Math.floor(c2[1]) + (hp < hpMax * 0.5 ? 1_000_000L : 0L);
        if (cell != run.getLong("cell")) {
            run.put("cell", cell);
            TrialRuntime.refresh(back, run);
        }
        if (s >= len - 0.3) {
            run.cleared = true;
            run.clearedAt = now;
            clearMobs(back, run);
            GroupGates.open(back, run.group, WhaleDeck.W11_EXIT);
            TrialFx.sound(back, run.group, WhaleDeck.W11_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.7f);
            TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.w11.arrived"));
            TrialRuntime.refresh(back, run);
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
        BlockState ahead = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState passed = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
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
            BlockPos p = cells.get(i);
            out.accept(p, i % 4 == 0 ? lamp : i < idx ? passed : ahead);
        }
        // 棺: 骨の台に、骸骨の頭
        BlockPos next = cells.get(Math.min(cells.size() - 1, idx + 1));
        BlockPos here = new BlockPos(cx, Y + 1, cz);
        BlockPos second = new BlockPos(next.getX(), Y + 1, next.getZ());
        out.accept(here, Blocks.BONE_BLOCK.defaultBlockState());
        out.accept(second, Blocks.BONE_BLOCK.defaultBlockState());
        BlockState skull = hp < hpMax * 0.5 ? Blocks.WITHER_SKELETON_SKULL.defaultBlockState() : Blocks.SKELETON_SKULL.defaultBlockState();
        out.accept(here.above(), skull);
        out.accept(second.above(), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < ROUTES.length; l++) {
            int[][] r = ROUTES[l];
            if (r[0][0] != 2 || r[0][1] != 133 || r[r.length - 1][0] != 2 || r[r.length - 1][1] != 163) {
                bad.add("W11 route " + l + " does not run from (2,133) to (2,163)");
            }
            for (int[] pt : r) {
                if (pt[0] < -5 || pt[0] > 9 || pt[1] < 130 || pt[1] > 164) {
                    bad.add("W11 route " + l + " leaves the hall at " + pt[0] + "," + pt[1]);
                }
            }
            for (int i = 0; i + 1 < r.length; i++) {
                if (r[i][0] != r[i + 1][0] && r[i][1] != r[i + 1][1]) {
                    bad.add("W11 route " + l + " has a diagonal leg at " + i);
                }
            }
            for (BlockPos p : cells(l)) {
                if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).isAir() || !back.getBlockState(p.above(2)).isAir()
                        || !back.getBlockState(p.above(3)).isAir()) {
                    bad.add("W11 route " + l + " is blocked at " + p);
                    break;
                }
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(WhaleDeck.W11_STONE))) {
            bad.add("W11 stone missing");
        }
        return bad;
    }
}
