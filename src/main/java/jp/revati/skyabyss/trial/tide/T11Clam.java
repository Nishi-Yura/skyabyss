package jp.revati.skyabyss.trial.tide;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.G;

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
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// T11 大貝の護送 (闘・護る・★★。docs/design/29 の 3.2)。大きな貝 (広間の床の「道」の上) を、岩の祠 (祭壇) まで引いていく。
//   決まりを教える: 貝は、道の先で呼ぶ人 (貝の前、4.5 ブロック以内) がいるあいだだけ進む。群れは貝を狙い、そばに近接の敵がいると貝は動けず、傷む
//   (引く人と守る人に分かれる)。一回目の襲撃は磯のはさみ虫だけ。
//   ひねる: 二回目からは、泡吹き (遠くから貝を泡で責める) か珊瑚の下僕 (硬い) が加わる。 潮が満ちる刻限: 襲撃と襲撃のあいだに時間の限りがあり、
//   間に合わないと潮が満ちて貝は最後の襲撃の場所まで流される (罰はやり直しだけ。群れは消える)。傷みきっても同じ。
//   一人: 貝の丈夫さ・群れの数は入った人数で決まる。手を放した (誰も前にいない) 貝はその場に留まる (進みも傷みも止まる。刻限は止まらない)。
// 修飾: 配置 3 (道の形)・数字 2 (襲撃の回数 2・3・4 / 刻限の長さ 長い・標準・短い)・敵の組 2 (磯の群れ / 泡吹きつき)・障害 (高波)・任意の目標 (貝を無傷で)
public final class T11Clam implements TrialCell {
    // 区画の写しで動かせる (決まりは arm・tick・onUse・disarm・overlay の中だけで座標を使う。docs/design/32 の 5 章)
    @Override
    public boolean relocatable() {
        return true;
    }

    public static final int Y = G;
    // 道 (x, z の折れ線)。どの配置も、始点 (44, 79) から終点 (44, 105) へ
    public static final int[][][] ROUTES = {
            {{44, 79}, {44, 86}, {39, 86}, {39, 94}, {49, 94}, {49, 99}, {44, 99}, {44, 105}},
            {{44, 79}, {44, 84}, {50, 84}, {50, 91}, {38, 91}, {38, 98}, {44, 98}, {44, 105}},
            {{44, 79}, {44, 90}, {50, 90}, {50, 95}, {38, 95}, {38, 100}, {46, 100}, {46, 105}, {44, 105}}};
    static final int[] RAIDS = {2, 3, 4};
    static final double[] SPEED = {0.9, 1.2, 1.6};
    // 刻限 (一つの区間の長さ。tick)
    static final int[] FLOOD = {1500, 1100, 800};
    static final double BASE_HP = 40;
    static final double GNAW = 0.3;
    static final double PUFF = 0.12;
    static final double LEAD = 4.5;
    static final int ROCK_EVERY = 230;

    private final Def def = TrialCatalog.get("T11");
    private final Ports ports = new Ports(TideDeck.T11_GATE, false, TideDeck.T11_EXIT, TideDeck.T11_STONE, TideDeck.T11_PAD, TideDeck.REST_H2,
            new net.minecraft.world.phys.AABB(36, Y + 1, 75, 53, Y + 9, 114), TideDeckRests.of("t11"));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // ---------------------------------------------------------------- 道
    public static double length(int layout) {
        int[][] r = ROUTES[layout];
        double len = 0;
        for (int i = 0; i + 1 < r.length; i++) {
            len += Math.abs(r[i + 1][0] - r[i][0]) + Math.abs(r[i + 1][1] - r[i][1]);
        }
        return len;
    }

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

    // 道の向き (単位ベクトル {dx, dz})
    public static double[] heading(int layout, double s) {
        double[] a = at(layout, s);
        double[] b = at(layout, s + 0.5);
        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        double l = Math.hypot(dx, dz);
        if (l < 1e-6) {
            b = at(layout, s - 0.5);
            dx = a[0] - b[0];
            dz = a[1] - b[1];
            l = Math.hypot(dx, dz);
        }
        return l < 1e-6 ? new double[] {0, 1} : new double[] {dx / l, dz / l};
    }

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
        double s = SPEED[1];
        return run.mods.abyss() ? s * Abyss.NUMBER_STEP : s;
    }

    // 区間ごとの刻限 (tick)。人数でわずかに変わる (一人は長い)
    public static int flood(Mods m, int players) {
        double t = FLOOD[m.number(1)] * (players <= 1 ? 1.2 : players >= 4 ? 0.9 : 1.0);
        return Abyss.shorter(m, (int) Math.round(t));
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

    // k 番目の襲撃の j 番目の敵の種類。一回目ははさみ虫だけ (決まりを教える)。二回目から、組に応じて加わる
    public static String kind(Run run, int k, int j) {
        if (k <= 1) {
            return "scuttler";
        }
        boolean puffers = run.mods.enemy() == 1;
        return puffers ? (j % 3 == 0 ? "scuttler" : "puffer") : (j % 3 == 2 ? "thrall" : "scuttler");
    }

    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    private static Map<UUID, String> kinds(Run run) {
        return run.get("kinds", () -> new HashMap<UUID, String>());
    }

    private static EntityType<? extends Mob> type(String kind) {
        return switch (kind) {
            case "puffer" -> TideMobSetup.BUBBLE_PUFFER.get();
            case "thrall" -> TideMobSetup.CORAL_THRALL.get();
            default -> TideMobSetup.REEF_SCUTTLER.get();
        };
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void arm(ServerLevel back, Run run) {
        long now = back.getGameTime();
        run.put("s", 0.0);
        run.put("trig", 0);
        run.put("chkS", 0.0);
        run.put("chkTrig", 0);
        run.put("hp", hpMax(1));
        run.put("hpMax", hpMax(1));
        run.put("cell", -1L);
        run.put("nextRock", now + 300L);
        run.put("legEnd", now + flood(run.mods, 1) + 200L);
        run.put("floodWarned", 0);
        if (run.cleared) {
            GroupGates.open(back, run.group, TideDeck.T11_EXIT);
            run.put("s", length(run.mods.layout()));
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        clearMobs(back, run);
        if (!run.cleared) {
            GroupGates.release(back, run.group, TideDeck.T11_EXIT);
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
        kinds(run).clear();
    }

    private static double dist(double x1, double z1, double x2, double z2) {
        return Math.hypot(x1 - x2, z1 - z2);
    }

    private void spawnRaid(ServerLevel back, Run run, int players, int k, double[] c) {
        int total = foes(run, players, k);
        Random rng = new Random(run.seed ^ (k * 31L));
        for (int j = 0; j < total; j++) {
            boolean west = j % 2 == 0;
            // 貝のそば (道の脇の 5〜8 ブロック) に湧く
            double zz = Math.max(76, Math.min(104, c[1] + (west ? -2 : 3) + rng.nextInt(4)));
            BlockPos at = new BlockPos(west ? 37 : 51, Y + 1, (int) zz);
            String kind = kind(run, k, j);
            if (kind.equals("puffer")) {
                at = at.above(2);
            }
            Mob m = TideSpawns.spawn(back, type(kind), at, run.group);
            if (m != null) {
                mobs(run).add(m.getUUID());
                kinds(run).put(m.getUUID(), kind);
            }
        }
        TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.DROWNED_AMBIENT_WATER, 1.5f, 0.6f);
        TrialFx.bar(Groups.online(back, run.group), ModText.battle("message.skyabyss.trial.t11.raid" + (k <= 1 ? "1" : "2"), k, raids(run)));
    }

    // 潮が満ちた: 最後の襲撃の場所まで流される
    private void flooded(ServerLevel back, Run run, List<ServerPlayer> inside, double[] c, long now, int players) {
        clearMobs(back, run);
        run.put("s", run.get("chkS", () -> 0.0) - 0.01);
        run.put("trig", run.getInt("chkTrig"));
        run.put("hp", hpMax(players));
        run.put("legEnd", now + flood(run.mods, players));
        run.put("floodWarned", 0);
        TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.6f, 0.6f);
        TrialFx.particles(back, run.group, ParticleTypes.SPLASH, c[0] + 0.5, Y + 1.2, c[1] + 0.5, 60, 2.0, 0.6, 2.0, 0.3);
        TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.t11.flooded"));
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        int layout = m.layout();
        int players = Math.max(1, inside.size());
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.t11.start"));
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
        double[] dir = heading(layout, s);
        // 襲撃
        int trig = run.getInt("trig");
        int k = raids(run);
        if (trig < k && s >= len * (trig + 1) / (k + 1)) {
            trig++;
            run.put("trig", trig);
            run.put("chkS", s);
            run.put("chkTrig", trig - 1);
            run.put("legEnd", now + flood(m, players));
            run.put("floodWarned", 0);
            spawnRaid(back, run, players, trig, c);
        }
        // 敵: 貝へ向かう (近くに人がいなければ)。近接の敵は貝をかじり、泡吹きは遠くから責める
        int gnawing = 0;
        int puffing = 0;
        List<UUID> list = mobs(run);
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(list.get(i));
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                kinds(run).remove(list.get(i));
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
            String kind = kinds(run).getOrDefault(mob.getUUID(), "scuttler");
            if (!nearPlayer && d > 1.8) {
                mob.getNavigation().moveTo(c[0] + 0.5, Y + 1, c[1] + 0.5, 1.15);
            }
            if (kind.equals("puffer")) {
                if (d < 9.0) {
                    puffing++;
                }
            } else if (d < 2.6) {
                gnawing++;
            }
        }
        // 呼ぶ人 (貝の前、道の向きの先)
        int leaders = 0;
        for (ServerPlayer p : inside) {
            double rx = p.getX() - (c[0] + 0.5);
            double rz = p.getZ() - (c[1] + 0.5);
            double ahead = rx * dir[0] + rz * dir[1];
            if (ahead > 0.4 && Math.hypot(rx, rz) < LEAD && Math.abs(p.getY() - (Y + 1)) < 3) {
                leaders++;
            }
        }
        if (gnawing > 0 || puffing > 0) {
            hp -= gnawing * GNAW + puffing * PUFF;
            if (run.getInt("scratched") == 0) {
                run.put("scratched", 1);
                if (m.goal() == 0) {
                    run.goalOk = false;
                }
            }
            TrialFx.particles(back, run.group, ParticleTypes.BUBBLE_POP, c[0] + 0.5, Y + 2, c[1] + 0.5, 4, 0.5, 0.3, 0.5, 0.1);
            if (now % 20 == 0) {
                TrialFx.bar(inside, ModText.deny(gnawing > 0 ? "message.skyabyss.trial.t11.pinned" : "message.skyabyss.trial.t11.pelted",
                        (int) Math.max(0, Math.round(100 * hp / hpMax))));
            }
        }
        if (gnawing == 0 && leaders > 0) {
            s += speed(run) * (0.7 + 0.3 * Math.min(3, leaders)) * 0.25;
            if (now % 8 == 0) {
                TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.TURTLE_SHAMBLE, 0.9f, 0.7f);
            }
        }
        // 刻限
        long legEnd = run.getLong("legEnd");
        long left = legEnd - now;
        if (left <= 200 && run.getInt("floodWarned") == 0) {
            run.put("floodWarned", 1);
            TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.t11.rising"));
            TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1.2f, 0.6f);
        }
        if (hp <= 0) {
            hp = hpMax;
            clearMobs(back, run);
            s = (Double) run.data.get("chkS") - 0.01;
            run.put("trig", run.getInt("chkTrig"));
            run.put("legEnd", now + flood(m, players));
            run.put("floodWarned", 0);
            TrialFx.sound(back, run.group, new BlockPos((int) c[0], Y + 1, (int) c[1]), SoundEvents.TURTLE_EGG_CRACK, 1.5f, 0.6f);
            TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.t11.broken"));
        } else if (left <= 0 && s < len - 0.3) {
            hp = hpMax;
            s = (Double) run.data.get("chkS") - 0.01;
            flooded(back, run, inside, c, now, players);
        }
        run.put("hp", Math.min(hp, hpMax));
        run.put("s", Math.min(s, len));
        // 高波
        if (m.hasHazard(0)) {
            TrialRock.tick(back, run, inside, now, ROCK_EVERY, 4f, ParticleTypes.BUBBLE, ParticleTypes.SPLASH, SoundEvents.PLAYER_SPLASH_HIGH_SPEED);
        }
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
            GroupGates.open(back, run.group, TideDeck.T11_EXIT);
            TrialFx.sound(back, run.group, TideDeck.T11_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
            TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.t11.arrived"));
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
        BlockState ahead = Blocks.DARK_PRISMARINE.defaultBlockState();
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
        // 大貝: 白い殻の二つの塊に、真珠 (傷むと暗くなる)
        BlockPos next = cells.get(Math.min(cells.size() - 1, idx + 1));
        BlockPos here = new BlockPos(cx, Y + 1, cz);
        BlockPos second = new BlockPos(next.getX(), Y + 1, next.getZ());
        out.accept(here, Blocks.CALCITE.defaultBlockState());
        out.accept(second, Blocks.CALCITE.defaultBlockState());
        out.accept(here.above(), hp < hpMax * 0.5 ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.SEA_LANTERN.defaultBlockState());
        out.accept(second.above(), Blocks.PRISMARINE_BRICK_SLAB.defaultBlockState());
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < ROUTES.length; l++) {
            int[][] r = ROUTES[l];
            if (r[0][0] != 44 || r[0][1] != 79 || r[r.length - 1][0] != 44 || r[r.length - 1][1] != 105) {
                bad.add("T11 route " + l + " does not run from (44,79) to (44,105)");
            }
            for (int[] pt : r) {
                if (pt[0] < 38 || pt[0] > 50 || pt[1] < 76 || pt[1] > 106) {
                    bad.add("T11 route " + l + " leaves the hall at " + pt[0] + "," + pt[1]);
                }
            }
            for (int i = 0; i + 1 < r.length; i++) {
                if (r[i][0] != r[i + 1][0] && r[i][1] != r[i + 1][1]) {
                    bad.add("T11 route " + l + " has a diagonal leg at " + i);
                }
            }
            // 刻限は、いちばん短くても、一区間を押すのに足りる (人数 4 以上・深淵・きびしい)
            double leg = length(l) / (RAIDS[0] + 1);
            double need = leg / (SPEED[1] * 4.0) * 1.0;
            double budget = flood(new Mods(0, new int[] {1, 2}, 0, new int[0], -1, jp.revati.skyabyss.trial.Trial.Extreme.NONE, false, jp.revati.skyabyss.trial.Trial.Rarity.COMMON), 4) / 20.0;
            if (budget < need * 3) {
                bad.add("T11 route " + l + " flood budget " + budget + " s is short for a leg of " + leg + " blocks");
            }
            for (BlockPos p : cells(l)) {
                if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).isAir() || !back.getBlockState(p.above(2)).isAir()
                        || !back.getBlockState(p.above(3)).isAir()) {
                    bad.add("T11 route " + l + " is blocked at " + p);
                    break;
                }
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(TideDeck.T11_STONE))) {
            bad.add("T11 stone missing");
        }
        return bad;
    }
}
