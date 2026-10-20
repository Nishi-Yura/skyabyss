package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.back.EmberBackSetup;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// E10 鞴持ちの炉 (闘・戦う・★★。docs/design/29 の 3.4)。広間の真ん中の炉を、鞴持ち (炉番の灰被り) が煽り続ける。**鞴持ちが生きているあいだ、広間の熱が上がり続ける**。
//   広間の熱が 100 になると炉が噴いて、広間の人みなの熱 +30 と 4 の傷 (そのあと熱は 55 に戻る)。鞴持ちを倒せば熱は下がる。
//   壁ぎわの灰溜まり (灰色の羊毛) で熱を冷ませる。鞴持ちを狙う人と、灰溜まりへ人を送る・敵を引きつける人に分かれると楽だが、一人でもできる (数は人数で決まる)。
//   波ごとに鞴持ち 1〜3 体 (人数で 3 人から +1・5 人から +2)。敵の組 B は火の粉蛾も連れる。静 (敵が少ない) のときは 1 波・一体少ない。
//   敵はそのグループのもの。倒れて戻ったら休み場 (入口の脇) から。全員いなくなって 5 秒で最初に戻る。波を片付けると石の前の光る床が使える。
// 修飾: 配置 3 (鞴持ちの湧く場所の順)・数字 2 (一波の鞴持ち 1・2・3 / 波の数 1・2・3)・敵の組 2 (鞴持ちだけ / 蛾つき)・障害 (噴気: 炉のまわりの 4 か所の割れ目)・任意の目標 (熱を焦げに上げずに)
public final class E10Furnace implements TrialCell {
    // 炉 (3 × 3 × 3) の中心。土台は 5 × 5
    public static final int FURNACE_X = 24;
    public static final int FURNACE_Z = 88;
    // 噴気の割れ目 (障害)。炉のまわりの 4 か所 (中心)
    public static final int[][] VENTS = {{18, 85}, {30, 85}, {18, 93}, {30, 93}};
    // 鞴持ちの湧く場所 (広間の東寄り・奥)。配置ごとに並べ替える
    public static final int[][] SPAWNS = {{31, 96}, {17, 96}, {31, 79}, {17, 99}, {24, 96}, {24, 78}};
    public static final int[][] ORDER = {{0, 1, 2, 3, 4, 5}, {2, 0, 5, 1, 3, 4}, {4, 3, 1, 5, 0, 2}};
    static final int[] BEARERS = {1, 2, 3};
    static final int[] WAVES = {1, 2, 3};
    // この x が過ぎたら始まる (入口の休み場を出たら)
    static final int START_X = 21;
    // 熱の上がり (1 秒に、鞴持ち 1 体あたり)・下がり (鞴持ちがいないとき 1 秒に)
    static final double RISE = 3.0;
    static final double FALL = 5.0;
    static final double BURST_AT = 100.0;
    static final double BURST_TO = 55.0;
    static final float BURST_HEAT = 30f;
    static final float BURST_DAMAGE = 4f;
    // 噴気 (障害)
    static final int VENT_PERIOD = 100;
    public static final int VENT_WARN = 24;

    public static final String BEARER_TAG = "skyabyss_furnace_bearer";

    private final Def def = TrialCatalog.get("E10");
    private final Ports ports = new Ports(EmberDeck.E10_GATE, false, List.of(), EmberDeck.E10_STONE, EmberDeck.E10_PAD, "k0", EmberDeck.B.aabb(),
            List.of(EmberCellKit.rest("k_e10")));

    public static BlockPos spawnAt(int slot) {
        return new BlockPos(SPAWNS[slot][0], Y + 1, SPAWNS[slot][1]);
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    public static int bearers(Run run, int players) {
        int b = jp.revati.skyabyss.abyss.Abyss.plusOne(run.mods, BEARERS[run.mods.number(0)] + (players >= 3 ? 1 : 0) + (players >= 5 ? 1 : 0));
        return run.mods.quiet() ? Math.max(1, b - 1) : b;
    }

    static int waves(Run run) {
        return run.mods.quiet() ? 1 : jp.revati.skyabyss.abyss.Abyss.plusOne(run.mods, WAVES[run.mods.number(1)]);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("wave", 0);
        run.put("started", false);
        run.put("heat", 0.0);
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

    private static int alive(ServerLevel back, Run run, boolean bearersOnly) {
        int n = 0;
        List<UUID> list = mobs(run);
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(list.get(i));
            if (e == null || !e.isAlive()) {
                list.remove(i);
            } else if (!bearersOnly || e.getTags().contains(BEARER_TAG)) {
                n++;
            }
        }
        return n;
    }

    private void spawnWave(ServerLevel back, Run run, int players, int wave) {
        // 設計書 26 の 8.7 の B: 一人・二人は、入口に近い口から先に使う (4 人から、もとの順)
        java.util.List<BlockPos> spotPos = new java.util.ArrayList<>();
        for (int i = 0; i < SPAWNS.length; i++) {
            spotPos.add(spawnAt(i));
        }
        BlockPos entrance = jp.revati.skyabyss.titan.dungeon.RoomPlayRooms.entranceOf("e10");
        int[] order = jp.revati.skyabyss.combat.RoomPlay.orderNear(ORDER[run.mods.layout()], spotPos, entrance, players);
        int count = bearers(run, players);
        for (int j = 0; j < count; j++) {
            BlockPos at = spawnAt(order[j % order.length]);
            AshThrall t = EmberCellKit.spawn(back, run.group, EmberMobSetup.ASH_THRALL.get(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            if (t != null) {
                t.addTag(BEARER_TAG);
                t.setCustomName(Component.translatable("trial.skyabyss.e10.bearer"));
                t.setCustomNameVisible(true);
                mobs(run).add(t.getUUID());
            }
        }
        if (run.mods.enemy() == 1 && !run.mods.quiet()) {
            int moths = 2 + (players >= 4 ? 1 : 0);
            for (int j = 0; j < moths; j++) {
                BlockPos at = spawnAt(order[(j + 2) % order.length]);
                Mob m = EmberCellKit.spawn(back, run.group, EmberMobSetup.EMBER_MOTH.get(), at.getX() + 0.5, Y + 4, at.getZ() + 0.5);
                if (m != null) {
                    mobs(run).add(m.getUUID());
                }
            }
        }
        TrialFx.sound(back, run.group, new BlockPos(FURNACE_X, Y + 1, FURNACE_Z), EmberTrialSetup.BELLOWS.get(), 1.4f, 0.7f);
        TrialFx.bar(jp.revati.skyabyss.titan.group.Groups.online(back, run.group), ModText.battle("message.skyabyss.trial.e10.wave", wave, waves(run), count));
    }

    // 噴気の割れ目 i の、いまの段階 (障害があるときだけ。周期ごとに 25 tick ずつずれて順に噴く)
    public static int ventPhase(Run run, int i, long now) {
        if (!run.mods.hasHazard(0) || run.getLong("ventBase") == 0) {
            return 0;
        }
        return EmberCellKit.phase(now, run.getLong("ventBase"), VENT_PERIOD, i * 25L, VENT_WARN);
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e10.start"));
        }
        if (run.mods.goal() == 0) {
            for (ServerPlayer p : inside) {
                if (EmberHeat.get(p) >= EmberHeat.SCORCHING) {
                    run.goalOk = false;
                }
            }
        }
        if (run.cleared) {
            return;
        }
        boolean started = (Boolean) run.data.getOrDefault("started", false);
        if (!started) {
            for (ServerPlayer p : inside) {
                if (p.getX() >= START_X && p.isAlive()) {
                    run.put("started", true);
                    run.put("nextWave", now + 60L);
                    run.put("ventBase", now + 100L);
                    started = true;
                    break;
                }
            }
            if (!started) {
                return;
            }
        }
        int wave = run.getInt("wave");
        int bearersAlive = alive(back, run, true);
        int alive = alive(back, run, false);
        boolean inProgress = (Boolean) run.data.getOrDefault("inProgress", false);
        double heat = (Double) run.data.getOrDefault("heat", 0.0);
        // 広間の熱 (5 tick ごと): 鞴持ちが生きていれば上がり、いなければ下がる
        double dt = 5.0 / 20.0;
        if (inProgress && bearersAlive > 0) {
            double scale = Math.max(0.6, 1.0 - 0.06 * (EmberCellKit.entrants(inside.size()) - 1));
            heat = Math.min(BURST_AT, heat + RISE * bearersAlive * scale * dt);
        } else {
            heat = Math.max(0.0, heat - FALL * dt);
        }
        if (heat >= BURST_AT) {
            heat = BURST_TO;
            for (ServerPlayer p : inside) {
                EmberHeat.add(p, BURST_HEAT, EmberHeat.Reason.OTHER);
                p.invulnerableTime = 0;
                p.hurt(jp.revati.skyabyss.titan.ember.back.trial.EmberTrials.damage(back, jp.revati.skyabyss.titan.ember.EmberIds.HEAT_BURST, null), BURST_DAMAGE);
            }
            TrialFx.sound(back, run.group, new BlockPos(FURNACE_X, Y + 1, FURNACE_Z), EmberTrialSetup.VENT_BURST.get(), 2.0f, 0.6f);
            TrialFx.particles(back, run.group, ParticleTypes.LAVA, FURNACE_X + 0.5, Y + 4, FURNACE_Z + 0.5, 24, 1.5, 0.6, 1.5, 0.1);
            TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.e10.burst"));
            run.put("bursts", run.getInt("bursts") + 1);
        }
        run.put("heat", heat);
        if (now % 20 == 0 && inProgress) {
            TrialFx.bar(inside, ModText.battle("message.skyabyss.trial.e10.gauge", (int) Math.round(heat), bearersAlive));
        }
        if (inProgress && bearersAlive > 0 && now % 20 == 0) {
            TrialFx.particles(back, run.group, ParticleTypes.FLAME, FURNACE_X + 0.5, Y + 5.2, FURNACE_Z + 0.5, 6, 0.8, 0.4, 0.8, 0.02);
        }
        // 波
        if (inProgress && alive == 0) {
            run.put("inProgress", false);
            if (wave >= waves(run)) {
                run.cleared = true;
                run.clearedAt = now;
                for (ServerPlayer p : inside) {
                    EmberHeat.cool(p, 100f);
                }
                TrialFx.sound(back, run.group, EmberDeck.E10_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.7f);
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.e10.cleared"));
                TrialRuntime.refresh(back, run);
                return;
            }
            run.put("nextWave", now + 80L);
        } else if (!inProgress && now >= run.getLong("nextWave")) {
            run.put("inProgress", true);
            run.put("wave", wave + 1);
            spawnWave(back, run, EmberCellKit.entrants(inside.size()), wave + 1);
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
                    int idx = (int) EmberCellKit.burstIndex(now, run.getLong("ventBase"), VENT_PERIOD, i * 25L);
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
        // 炉の光り方 (熱の段)
        int level = heat < 34 ? 0 : heat < 67 ? 1 : 2;
        if (level != run.getInt("view")) {
            run.put("view", level);
            TrialRuntime.refresh(back, run);
        }
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        // 噴気の割れ目は、障害のあるときだけ割れて見える (無ければふつうの床)
        if (!run.mods.hasHazard(0)) {
            for (int[] v : VENTS) {
                EmberCellKit.patchPlain(v[0], Y, v[1], out);
            }
        } else {
            int[] states = run.get("vents", () -> new int[VENTS.length]);
            for (int i = 0; i < VENTS.length; i++) {
                EmberCellKit.patchOverlay(VENTS[i][0], Y, VENTS[i][1], states[i], out);
            }
        }
        EmberCellKit.padGlow(ports.pad(), run.cleared, out);
        double heat = (Double) run.data.getOrDefault("heat", 0.0);
        int level = heat < 34 ? 0 : heat < 67 ? 1 : 2;
        if (level == 0 || run.cleared) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < 3; dy++) {
                    if (dy < level + (level == 2 ? 1 : 0)) {
                        out.accept(new BlockPos(FURNACE_X + dx, Y + 2 + dy, FURNACE_Z + dz), level == 2 ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState());
                    }
                }
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < ORDER.length; l++) {
            boolean[] seen = new boolean[SPAWNS.length];
            for (int s : ORDER[l]) {
                seen[s] = true;
            }
            for (boolean b : seen) {
                if (!b) {
                    bad.add("E10 layout " + l + ": the order must list every spawn");
                    break;
                }
            }
        }
        for (int s = 0; s < SPAWNS.length; s++) {
            BlockPos at = spawnAt(s);
            if (back.getBlockState(at.below()).isAir() || !back.getBlockState(at).isAir() || !back.getBlockState(at.above()).isAir()) {
                bad.add("E10 spawn " + s + " has no standing place");
            }
            if (!EmberDeck.B.contains(at)) {
                bad.add("E10 spawn " + s + " is outside the hall");
            }
        }
        for (int[] v : VENTS) {
            if (!back.getBlockState(new BlockPos(v[0], Y, v[1])).is(EmberTrialSetup.HEAT_VENT.get())) {
                bad.add("E10 vent block missing at " + v[0] + "," + v[1]);
                break;
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(EmberDeck.E10_STONE))) {
            bad.add("E10 stone missing");
        }
        // 熱の収支: 鞴持ち 1 体で 100 まで 33 秒以上、6 人でも (鞴持ち 5 体・人数で 0.7) 噴くまで 5 秒以上
        if (BURST_AT / (RISE * 1) < 30 || BURST_AT / (RISE * 5 * 1.0) < 6) {
            bad.add("E10 heat balance: the hall burns too fast");
        }
        return bad;
    }
}
