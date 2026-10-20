package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.ember.back.EmberBackSetup;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// E07 三つの鞴 (智・合わせる・★★。docs/design/29 の 3.4)。炉の鞴の踏み板が広間の四隅にある。踏み板に立つと鞴が火を噴き (炎 100)、離れると炎が少しずつ弱る。
//   炉の口を開けるには、**そのときの踏み板すべての炎を同時に燃やしたまま、数秒保つ**。生き物なら何でも踏める (人も、敵も、連れてきた生き物も)。
//   第一の口: 二つの踏み板 (決まりを覚える)。第二の口: 三つ (4 人以上なら四つ)。ひびの入った鞴は炎が 2.5 倍の速さで弱る (踏み板が割れて見える)。
//   一人でも: 重りの石が 2 つ (しゃがんで踏み板を右クリック = 載せる / もう一度で外す)。載せた踏み板は炎が消えない。口が開くと重りは戻る。
//   複数なら、踏み板ごとに一人。声をかけ合う。
// 修飾: 配置 3 (どの踏み板を使うか・ひびの入った鞴)・数字 2 (炎の持ち 14・11・8 秒 / 保つ 2・3・4 秒)・障害 (灰降り: 踏み板が 5 秒、灰に埋まって効かない)・任意の目標 (炎を落とさずに)
public final class E07Bellows implements TrialCell {
    public static final int[] PAD_X = {-30, -18, -30, -18};
    public static final int[] PAD_Z = {74, 74, 88, 88};
    public static final int[][] STAGE1 = {{0, 1}, {2, 3}, {0, 3}};
    public static final int[][] STAGE2 = {{1, 2, 3}, {0, 1, 2}, {0, 1, 2}};
    public static final int[] LEAKY = {2, 0, 1};
    public static final int TOWER = 4;
    // 炉 (広間の中央。3 × 3 × 3)
    public static final int FURNACE_X = -24;
    public static final int FURNACE_Z = 81;
    static final int[] GRACE = {280, 220, 160};
    static final int[] HOLD = {40, 60, 80};
    static final int WEIGHTS = 2;
    static final double LEAK = 2.5;
    static final int ASH_EVERY = 300;
    static final int ASH_LEN = 100;
    static final int ASH_WARN = 20;
    // この以上なら「燃えている」
    public static final int LIT = 25;

    private final Def def = TrialCatalog.get("E07");
    private final Ports ports = new Ports(EmberDeck.E07_GATE, false, EmberDeck.E07_EXIT, EmberDeck.E07_STONE, EmberDeck.E07_PAD, "k0", EmberDeck.D.aabb(),
            List.of(EmberCellKit.rest("k_e07")));

    public static BlockPos tower(int pad) {
        return new BlockPos(PAD_X[pad], Y + 1, PAD_Z[pad] - 3);
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    @Override
    public Set<BlockPos> interactive() {
        Set<BlockPos> set = new HashSet<>();
        for (int k = 0; k < 4; k++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set.add(new BlockPos(PAD_X[k] + dx, Y, PAD_Z[k] + dz));
                }
            }
        }
        return set;
    }

    public static int graceOf(Mods m) {
        return jp.revati.skyabyss.abyss.Abyss.shorter(m, GRACE[m.number(0)]);
    }

    public static int holdOf(Mods m) {
        return jp.revati.skyabyss.abyss.Abyss.longer(m, HOLD[m.number(1)]);
    }

    // いまの口で使う踏み板 (人数で増える)
    public static int[] padsOf(Run run) {
        int stage = run.getInt("stage");
        int layout = run.mods.layout();
        if (stage <= 1) {
            return STAGE1[layout];
        }
        return run.getInt("entrants") >= 4 ? new int[] {0, 1, 2, 3} : STAGE2[layout];
    }

    static boolean used(Run run, int pad) {
        for (int p : padsOf(run)) {
            if (p == pad) {
                return true;
            }
        }
        return false;
    }

    static boolean leaky(Run run, int pad) {
        return run.getInt("stage") >= 2 && pad == LEAKY[run.mods.layout()];
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("lvl", new int[4]);
        run.put("weight", new boolean[4]);
        run.put("ashUntil", new long[4]);
        run.put("weightsLeft", WEIGHTS);
        run.put("stage", 1);
        run.put("entrants", 1);
        run.put("view", -1);
        run.put("holdSince", 0L);
        run.put("nextAsh", back.getGameTime() + 240L);
        if (run.cleared) {
            GroupGates.open(back, run.group, EmberDeck.E07_EXIT);
            run.put("stage", 3);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        if (!run.cleared) {
            GroupGates.release(back, run.group, EmberDeck.E07_EXIT);
        }
    }

    private boolean occupied(ServerLevel back, Run run, List<ServerPlayer> inside, int pad) {
        double cx = PAD_X[pad] + 0.5;
        double cz = PAD_Z[pad] + 0.5;
        for (ServerPlayer p : inside) {
            if (p.isAlive() && Math.abs(p.getX() - cx) <= 1.6 && Math.abs(p.getZ() - cz) <= 1.6 && p.getY() >= Y + 0.9 && p.getY() < Y + 2.5) {
                return true;
            }
        }
        AABB box = new AABB(cx - 1.6, Y + 1, cz - 1.6, cx + 1.6, Y + 2.5, cz + 1.6);
        for (LivingEntity e : back.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !x.isSpectator() && !(x instanceof ServerPlayer))) {
            if (!(e instanceof GroupOwned o) || o.groupId() == null || o.groupId().equals(run.group)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int n = EmberCellKit.entrants(inside.size());
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e07.start", WEIGHTS));
        }
        if (run.cleared) {
            return;
        }
        if (n > run.getInt("entrants")) {
            run.put("entrants", n);
        }
        int[] lvl = run.get("lvl", () -> new int[4]);
        boolean[] weight = run.get("weight", () -> new boolean[4]);
        long[] ashUntil = run.get("ashUntil", () -> new long[4]);
        int grace = graceOf(run.mods);
        int hold = holdOf(run.mods);
        int[] pads = padsOf(run);
        // 灰降り (障害): 使っている踏み板の一つが、5 秒、灰に埋まる。予告の粒が 1 秒前から
        if (run.mods.hasHazard(0)) {
            long next = run.getLong("nextAsh");
            Random rng = new Random(run.seed ^ next);
            int target = pads[rng.nextInt(pads.length)];
            if (now >= next - ASH_WARN && now < next) {
                TrialFx.particles(back, run.group, ParticleTypes.ASH, PAD_X[target] + 0.5, Y + 1.5, PAD_Z[target] + 0.5, 12, 1.0, 0.4, 1.0, 0.02);
            } else if (now >= next) {
                ashUntil[target] = now + ASH_LEN;
                run.put("nextAsh", now + ASH_LEN + ASH_EVERY);
                TrialFx.sound(back, run.group, new BlockPos(PAD_X[target], Y, PAD_Z[target]), SoundEvents.SAND_BREAK, 1.5f, 0.6f);
            }
        }
        boolean allLit = true;
        int mask = run.getInt("stage") << 28;
        for (int k = 0; k < 4; k++) {
            boolean active = used(run, k);
            boolean ashed = now < ashUntil[k];
            if (!active) {
                lvl[k] = 0;
                continue;
            }
            if (ashed) {
                lvl[k] = 0;
            } else if (weight[k] || occupied(back, run, inside, k)) {
                lvl[k] = 100;
            } else {
                double rate = 100.0 * 5.0 / grace * (leaky(run, k) ? LEAK : 1.0);
                lvl[k] = Math.max(0, (int) Math.round(lvl[k] - rate));
            }
            if (lvl[k] < LIT) {
                allLit = false;
            }
            mask |= (lvl[k] / 25) << (k * 3) | (weight[k] ? 1 << (12 + k) : 0) | (ashed ? 1 << (16 + k) : 0);
        }
        if (allLit) {
            long since = run.getLong("holdSince");
            if (since == 0) {
                since = now;
                run.put("holdSince", now);
                TrialFx.sound(back, run.group, new BlockPos(FURNACE_X, Y + 1, FURNACE_Z), SoundEvents.BLASTFURNACE_FIRE_CRACKLE, 1.2f, 0.8f);
            }
            long held = now - since;
            if (now % 10 == 0) {
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.e07.hold", (int) Math.min(100, held * 100 / hold)));
            }
            mask |= 1 << 20;
            if (held >= hold) {
                advance(back, run, inside, now);
            }
        } else {
            if (run.getLong("holdSince") != 0) {
                run.put("holdSince", 0L);
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
                run.put("lapses", run.getInt("lapses") + 1);
                TrialFx.sound(back, run.group, new BlockPos(FURNACE_X, Y + 1, FURNACE_Z), SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.8f);
            }
        }
        if (run.cleared) {
            mask |= 1 << 21;
        }
        if (mask != run.getInt("view")) {
            run.put("view", mask);
            TrialRuntime.refresh(back, run);
        }
    }

    private void advance(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int stage = run.getInt("stage");
        run.put("holdSince", 0L);
        run.put("lvl", new int[4]);
        run.put("weight", new boolean[4]);
        run.put("weightsLeft", WEIGHTS);
        if (stage <= 1) {
            run.put("stage", 2);
            TrialFx.sound(back, run.group, new BlockPos(FURNACE_X, Y + 1, FURNACE_Z), SoundEvents.BLAZE_SHOOT, 1.2f, 0.7f);
            TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.e07.stage2", run.getInt("entrants") >= 4 ? 4 : 3));
        } else {
            run.put("stage", 3);
            run.cleared = true;
            run.clearedAt = now;
            GroupGates.open(back, run.group, EmberDeck.E07_EXIT);
            TrialFx.sound(back, run.group, EmberDeck.E07_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.8f);
            TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.e07.done"));
        }
        TrialRuntime.refresh(back, run);
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        int pad = -1;
        for (int k = 0; k < 4; k++) {
            if (Math.abs(pos.getX() - PAD_X[k]) <= 1 && Math.abs(pos.getZ() - PAD_Z[k]) <= 1) {
                pad = k;
            }
        }
        if (pad < 0 || run.cleared) {
            return false;
        }
        if (!used(run, pad)) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial.e07.dormant"));
            return true;
        }
        if (!sneaking) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial.e07.step", WEIGHTS));
            return true;
        }
        long[] ashUntil = run.get("ashUntil", () -> new long[4]);
        if (now < ashUntil[pad]) {
            Notice.bar(player, ModText.deny("message.skyabyss.trial.e07.ashed"));
            return true;
        }
        boolean[] weight = run.get("weight", () -> new boolean[4]);
        int left = run.getInt("weightsLeft");
        if (weight[pad]) {
            weight[pad] = false;
            run.put("weightsLeft", left + 1);
            Notice.bar(player, ModText.hint("message.skyabyss.trial.e07.weight_off", left + 1));
        } else if (left > 0) {
            weight[pad] = true;
            run.put("weightsLeft", left - 1);
            Notice.bar(player, ModText.hint("message.skyabyss.trial.e07.weight_on", left - 1));
        } else {
            Notice.bar(player, ModText.deny("message.skyabyss.trial.e07.no_weight"));
            return true;
        }
        TrialFx.sound(back, run.group, new BlockPos(PAD_X[pad], Y, PAD_Z[pad]), SoundEvents.ANVIL_PLACE, 0.8f, 0.9f);
        return true;
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int[] lvl = run.get("lvl", () -> new int[4]);
        boolean[] weight = run.get("weight", () -> new boolean[4]);
        long[] ashUntil = run.get("ashUntil", () -> new long[4]);
        long now = back.getGameTime();
        BlockState lit = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
        BlockState plain = jp.revati.skyabyss.titan.ember.back.EmberKit.RUIN;
        for (int k = 0; k < 4; k++) {
            boolean active = used(run, k) && !run.cleared;
            int cx = PAD_X[k];
            int cz = PAD_Z[k];
            if (!active) {
                // 使わない踏み板は、ふつうの床に見える。塔も暗い
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        out.accept(new BlockPos(cx + dx, Y, cz + dz), jp.revati.skyabyss.titan.ember.back.EmberKit.ruinFloor(cx + dx, cz + dz));
                    }
                }
                continue;
            }
            boolean ashed = now < ashUntil[k];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = new BlockPos(cx + dx, Y, cz + dz);
                    boolean center = dx == 0 && dz == 0;
                    if (ashed) {
                        out.accept(p, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState());
                    } else if (center && weight[k]) {
                        out.accept(p, Blocks.GILDED_BLACKSTONE.defaultBlockState());
                    } else if (!center && lvl[k] >= LIT) {
                        out.accept(p, lit);
                    } else if (!center && leaky(run, k)) {
                        out.accept(p, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    }
                }
            }
            int levels = (lvl[k] + 24) / 25;
            BlockPos t = tower(k);
            for (int i = 0; i < TOWER; i++) {
                out.accept(t.above(i), i < levels ? lit : plain);
            }
        }
        EmberCellKit.padGlow(ports.pad(), run.cleared, out);
        // 炉: 第一の口が開くと下の段が、全部開くと全体が光る
        int stage = run.getInt("stage");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < 3; dy++) {
                    BlockPos p = new BlockPos(FURNACE_X + dx, Y + 1 + dy, FURNACE_Z + dz);
                    boolean glow = stage >= 3 || (stage == 2 && dy == 0) || (run.getLong("holdSince") != 0 && dy == 0);
                    if (glow) {
                        out.accept(p, stage >= 3 ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState());
                    }
                }
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = new BlockPos(PAD_X[k] + dx, Y, PAD_Z[k] + dz);
                    if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).isAir()) {
                        bad.add("E07 pad " + k + " is broken at " + p);
                        return bad;
                    }
                }
            }
            if (!EmberDeck.D.contains(PAD_X[k] - 1, Y + 1, PAD_Z[k] - 4) || !EmberDeck.D.contains(PAD_X[k] + 1, Y + 1, PAD_Z[k] + 1)) {
                bad.add("E07 pad " + k + " is outside the hall");
            }
        }
        for (int l = 0; l < STAGE1.length; l++) {
            boolean leakyInStage2 = false;
            for (int p : STAGE2[l]) {
                leakyInStage2 |= p == LEAKY[l];
            }
            if (!leakyInStage2) {
                bad.add("E07 layout " + l + ": the leaky bellows is not in stage 2");
            }
        }
        // 踏み板どうしは 12 ブロック以上離れている (一人で同時に二つは踏めない)
        for (int a = 0; a < 4; a++) {
            for (int b = a + 1; b < 4; b++) {
                if (Math.hypot(PAD_X[a] - PAD_X[b], PAD_Z[a] - PAD_Z[b]) < 12) {
                    bad.add("E07 pads " + a + " and " + b + " are too close");
                }
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(EmberDeck.E07_STONE))) {
            bad.add("E07 stone missing");
        }
        return bad;
    }
}
