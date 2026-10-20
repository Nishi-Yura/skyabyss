package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.CinderRoller;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// E12 大燠虫の巣 (闘・戦う・★★★。docs/design/29 の 3.4)。地下の巣の三つの穴から、大燠虫が丸まって転がり出る。
//   決まりは一つ: **大燠虫は丸まっているあいだ硬い。転がって壁にぶつかると目を回し、背が開く。目を回したあいだに叩く**。
//   丸まりは予告 (穴が光る)。転がるあいだは、道の上を離れる。噴気の割れ目 (障害) の上では焼かれる。
//   ひねり: ① 穴の場所が配置ごとに変わる・② 波が増える (数字)・③ 噴気が巣の中に開く (障害)。
//   一人でも: 丸まりを見て穴の線から外れ、目を回したところを叩く。人数が増えると、大燠虫が一匹ずつ増える (3 人から +1)。
//   灰被りの群れ (敵の組 2) が一緒に来るときは、大燠虫の影に隠れる。
// 修飾: 配置 3 (穴の場所)・数字 2 (一波の大燠虫 2・3・4 / 波の数 1・2・3)・敵の組 2 (大燠虫だけ / 灰被りつき)・障害 (噴気)・任意の目標 (噴気を踏まずに)
public final class E12Nest implements TrialCell {
    // 穴 (大燠虫が出てくる所)。配置ごと
    public static final int[][][] HOLES = {
            {{-6, 113}, {6, 113}, {0, 117}},
            {{-8, 116}, {8, 116}, {0, 112}},
            {{-10, 115}, {10, 113}, {0, 114}},
    };
    // 噴気の割れ目 (障害。3 × 3 の床。炉の割れ目と同じ見え方)
    public static final int[][] VENTS = {{-8, 111}, {2, 111}, {-3, 117}, {9, 111}};
    static final int VENT_PERIOD = 110;
    public static final int VENT_WARN = 24;
    static final int[] ROLLERS = {2, 3, 4};
    static final int[] WAVES = {1, 2, 3};
    static final int WAVE_GAP = 80;
    static final int FIRST_WAVE = 60;
    static final int LIGHT_AHEAD = 40;

    private final Def def = TrialCatalog.get("E12");
    private final Ports ports = new Ports(EmberDeck.E12_GATE, false, List.of(), EmberDeck.E12_STONE, EmberDeck.E12_PAD, "k_e12", EmberDeck.SC.aabb(),
            List.of(EmberCellKit.rest("k_e12")));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    static int rollers(Run run, int players) {
        int n = ROLLERS[run.mods.number(0)] + (EmberCellKit.entrants(players) >= 3 ? 1 : 0);
        return run.mods.quiet() ? Math.max(1, n - 1) : n;
    }

    static int waves(Run run) {
        return run.mods.quiet() ? 1 : WAVES[run.mods.number(1)];
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    static int ventPhase(Run run, int i, long now) {
        if (!run.mods.hasHazard(0) || run.getLong("ventBase") == 0) {
            return 0;
        }
        return EmberCellKit.phase(now, run.getLong("ventBase"), VENT_PERIOD, i * 27L, VENT_WARN);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("wave", 0);
        run.put("started", false);
        run.put("inProgress", false);
        run.put("lit", false);
        run.put("view", -1);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        for (UUID id : mobs(run)) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        mobs(run).clear();
    }

    private static int alive(ServerLevel back, Run run) {
        int n = 0;
        List<UUID> list = mobs(run);
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(list.get(i));
            if (e == null || !e.isAlive()) {
                list.remove(i);
            } else {
                n++;
            }
        }
        return n;
    }

    private void spawnWave(ServerLevel back, Run run, int players, int wave) {
        int[][] holes = HOLES[run.mods.layout()];
        int count = rollers(run, players);
        for (int j = 0; j < count; j++) {
            int[] h = holes[j % holes.length];
            CinderRoller r = EmberCellKit.spawn(back, run.group, EmberMobSetup.CINDER_ROLLER.get(), h[0] + 0.5, Y + 1, h[1] + 0.5);
            if (r != null) {
                mobs(run).add(r.getUUID());
            }
        }
        if (run.mods.enemy() == 1) {
            int thralls = Math.max(1, count / 2);
            for (int j = 0; j < thralls; j++) {
                int[] h = holes[(j + 1) % holes.length];
                AshThrall t = EmberCellKit.spawn(back, run.group, EmberMobSetup.ASH_THRALL.get(), h[0] + 0.5, Y + 1, h[1] + 0.5 + 1);
                if (t != null) {
                    mobs(run).add(t.getUUID());
                }
            }
        }
        TrialFx.sound(back, run.group, new BlockPos(holes[0][0], Y + 1, holes[0][1]), jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup.BELLOWS.get(), 1.4f, 0.6f);
        TrialFx.bar(jp.revati.skyabyss.titan.group.Groups.online(back, run.group), ModText.battle("message.skyabyss.trial.e12.wave", wave, waves(run), count));
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e12.start"));
        }
        if (run.mods.goal() == 0 && run.getInt("burns") > 0) {
            run.goalOk = false;
        }
        if (run.cleared) {
            return;
        }
        boolean started = Boolean.TRUE.equals(run.data.get("started"));
        if (!started) {
            run.put("started", true);
            run.put("nextWave", now + FIRST_WAVE);
            run.put("ventBase", now + 100L);
            started = true;
        }
        int wave = run.getInt("wave");
        int left = alive(back, run);
        boolean inProgress = Boolean.TRUE.equals(run.data.get("inProgress"));
        if (inProgress && left == 0) {
            run.put("inProgress", false);
            if (wave >= waves(run)) {
                run.cleared = true;
                run.clearedAt = now;
                TrialFx.sound(back, run.group, EmberDeck.E12_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.7f);
                TrialFx.bar(jp.revati.skyabyss.titan.group.Groups.online(back, run.group), ModText.progress("message.skyabyss.trial.e12.cleared"));
                TrialRuntime.refresh(back, run);
                return;
            }
            run.put("nextWave", now + WAVE_GAP);
        } else if (!inProgress && now >= run.getLong("nextWave")) {
            run.put("inProgress", true);
            run.put("wave", wave + 1);
            spawnWave(back, run, EmberCellKit.entrants(inside.size()), wave + 1);
        }
        // 次の波の予告: 穴が光る
        boolean lit = !Boolean.TRUE.equals(run.data.get("inProgress")) && now >= run.getLong("nextWave") - LIGHT_AHEAD && !run.cleared;
        if (lit != Boolean.TRUE.equals(run.data.get("lit"))) {
            run.put("lit", lit);
            TrialRuntime.refresh(back, run);
        }
        if (lit && now % 10 == 0) {
            for (int[] h : HOLES[run.mods.layout()]) {
                TrialFx.particles(back, run.group, ParticleTypes.SMOKE, h[0] + 0.5, Y + 1.2, h[1] + 0.5, 2, 0.3, 0.1, 0.3, 0.01);
            }
        }
        // 噴気 (障害)
        if (run.mods.hasHazard(0)) {
            int[] states = run.get("vents", () -> new int[VENTS.length]);
            Set<Integer> hit = run.get("ventHit", HashSet::new);
            boolean changed = false;
            for (int i = 0; i < VENTS.length; i++) {
                int s = ventPhase(run, i, now);
                if (s != states[i]) {
                    states[i] = s;
                    changed = true;
                    if (s > 0) {
                        EmberCellKit.ventFx(back, run.group, VENTS[i][0], Y, VENTS[i][1], s);
                    }
                }
                if (s == 2) {
                    EmberCellKit.ventColumn(back, run.group, VENTS[i][0], Y, VENTS[i][1]);
                    int idx = (int) EmberCellKit.burstIndex(now, run.getLong("ventBase"), VENT_PERIOD, i * 27L);
                    for (ServerPlayer p : inside) {
                        if (p.isAlive() && EmberCellKit.over(p, VENTS[i][0], Y, VENTS[i][1]) && hit.add((idx * 4 + i) * 31 + p.getId())) {
                            EmberCellKit.burn(back, run, p, VENTS[i][0], VENTS[i][1]);
                        }
                    }
                }
            }
            if (hit.size() > 300) {
                hit.clear();
            }
            if (changed) {
                TrialRuntime.refresh(back, run);
            }
        }
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        if (run.mods.hasHazard(0)) {
            int[] states = run.get("vents", () -> new int[VENTS.length]);
            for (int i = 0; i < VENTS.length; i++) {
                EmberCellKit.patchOverlay(VENTS[i][0], Y, VENTS[i][1], states[i], out);
            }
        } else {
            for (int[] v : VENTS) {
                EmberCellKit.patchPlain(v[0], Y, v[1], out);
            }
        }
        if (Boolean.TRUE.equals(run.data.get("lit"))) {
            for (int[] h : HOLES[run.mods.layout()]) {
                out.accept(new BlockPos(h[0], Y, h[1]), Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
        EmberCellKit.padGlow(ports.pad(), run.cleared, out);
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < HOLES.length; l++) {
            for (int[] h : HOLES[l]) {
                if (!EmberDeck.SC.contains(h[0], Y + 1, h[1])) {
                    bad.add("E12 hole " + h[0] + "," + h[1] + " is outside the nest (layout " + l + ")");
                }
            }
        }
        for (int[] v : VENTS) {
            if (!back.getBlockState(new BlockPos(v[0], Y, v[1])).is(jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup.HEAT_VENT.get())) {
                bad.add("E12 vent block missing at " + v[0] + "," + v[1]);
                break;
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(EmberDeck.E12_STONE))) {
            bad.add("E12 stone missing");
        }
        return bad;
    }
}
