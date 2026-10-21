package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.back.EmberKit;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// E03 鎖橋の溶けた段 (歩・渡る・★★。docs/design/29 の 3.4)。鎖橋の間 (NW) の坑の上に、一本の鎖の段が西へ渡っている。
//   決まりは一つ: **段は、乗っているあいだ熱くなる。赤くなった段は踏むと焼けて、入口の段へ戻される。離れれば冷える**。
//   熱くなる速さは段ごと (灰色の段は冷えが速い)。坑へ落ちても、焼かれても、最後に安全に立った段へ戻る (熱 +25・6 の傷)。
//   ひねり: ① 最初から赤い段がある (見て避ける)・② 奥の鎖は速く熱くなる (手前で冷やす)・③ 灰色の冷やしの段が途中にある。
//   一人でも: 段をつぎつぎ渡り、乗り続けない。人数が増えると、段は速く熱くなる (1 人増えるごとに 15%。上限 1.6 倍)。
//   渡りきると、向こうの岸 (x ≤ -30) の熾り石を鳴らせる。石の前の光る床で休み場へ戻れる。
// 修飾: 配置 3 (冷やしの段の位置・最初から赤い段・奥が速い)・数字 2 (熱くなるまで 70・56・44 tick / 冷める 60・44・32 tick)・障害 (蛾: 火の粉蛾が坑の上を漂う)・任意の目標 (一度も焼かれず・落ちず)
public final class E03Chain implements TrialCell {
    // 区画の写しで動かせる (決まりは arm・tick・onUse・disarm・overlay の中だけで座標を使う。docs/design/32 の 5 章)
    @Override
    public boolean relocatable() {
        return true;
    }

    // 段の数と並び (段 i は x = -14 - i、z = 10)。段 0 は入口の戸のすぐ内側
    public static final int ROW_Z = 10;
    public static final int PLANKS = 16;
    public static final int BANK_X = -30;

    // 配置ごとの冷やしの段・最初から赤い段・この段から奥 (速く熱くなる)
    static final int[] COOL = {12, 6, 7};
    static final int[][] HOT_AT_START = {{}, {3, 9}, {}};
    static final int[] FAST_FROM = {PLANKS, PLANKS, 8};
    static final double FAST = 1.5;
    static final int[] HOT_TICKS = {70, 56, 44};
    static final int[] COOL_TICKS = {60, 44, 32};
    // 熱の段 (0〜100)。50 から赤く見え、100 で焼ける。冷えて 40 になるまで赤いまま
    static final double WARM = 50;
    static final double BURN_AT = 100;
    static final double HOT_CLEAR = 40;
    static final double DT = 5.0;
    static final float BURN_HEAT = 25f;
    static final float BURN_DAMAGE = 4f;

    public static int plankX(int i) {
        return -14 - i;
    }

    public static BlockPos plank(int i) {
        return new BlockPos(plankX(i), Y, ROW_Z);
    }

    // 床の残る所 (段・岸・入口の柱・休み場・戻りの床)。ほかは坑 (空気。その下は溶岩)
    public static boolean solid(int x, int z) {
        if (z == ROW_Z && x <= -14 && x >= -29) {
            return true;
        }
        if (x >= BANK_X - 2 && x <= BANK_X && z >= 8 && z <= 12) {
            return true;
        }
        if (x == -14 && z >= 8 && z <= 12) {
            return true;
        }
        if (x >= -19 && x <= -15 && z >= 2 && z <= 6) {
            return true;
        }
        return x >= -19 && x <= -17 && z >= 13 && z <= 15;
    }

    public static boolean onBank(double x, double z) {
        return x <= BANK_X + 0.5 && z >= 8 && z <= 12;
    }

    static final String SAFE = "safe";

    private final Def def = TrialCatalog.get("E03");
    private final Ports ports = new Ports(EmberDeck.E03_GATE, false, List.of(), EmberDeck.E03_STONE, EmberDeck.E03_PAD, "k_e03", aabb(),
            List.of(EmberCellKit.rest("k_e03")));

    // 坑を含むセルの床の範囲 (落ちた人も中に入る。坑は床の下 1 ブロック)
    static net.minecraft.world.phys.AABB aabb() {
        EmberBackLayout.Box b = EmberDeck.NW;
        return new net.minecraft.world.phys.AABB(b.minX(), Y - 2, b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    static double heatRate(Run run, int players) {
        return Math.min(1.6, 1.0 + 0.15 * (EmberCellKit.entrants(players) - 1));
    }

    static int hotTicks(Run run) {
        return HOT_TICKS[run.mods.number(0)];
    }

    static int coolTicks(Run run) {
        return COOL_TICKS[run.mods.number(1)];
    }

    public static int coolIndex(Run run) {
        return COOL[run.mods.layout()];
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        double[] h = new double[PLANKS];
        boolean[] hot = new boolean[PLANKS];
        for (int i : HOT_AT_START[run.mods.layout()]) {
            h[i] = BURN_AT;
            hot[i] = true;
        }
        run.put("h", h);
        run.put("hot", hot);
        run.put("warm", new int[PLANKS]);
        run.put("mothsAt", 0L);
        run.put("bursts", 0);
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

    @SuppressWarnings("unchecked")
    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Integer> safe(Run run) {
        return run.get(SAFE, HashMap::new);
    }

    // p が立っている段 (なければ -1)
    static int onPlank(ServerPlayer p) {
        if (!p.onGround() || p.getY() < Y + 0.9) {
            return -1;
        }
        BlockPos below = p.blockPosition().below();
        if (below.getY() != Y || below.getZ() != ROW_Z) {
            return -1;
        }
        int i = -14 - below.getX();
        return i >= 0 && i < PLANKS ? i : -1;
    }

    // 坑へ落ちた・赤い段を踏んだ: 熱 +25・傷 4 のあと、最後に安全に立った段へ
    private void fall(ServerLevel back, Run run, ServerPlayer p) {
        if (p.isCreative()) {
            return;
        }
        EmberHeat.add(p, BURN_HEAT, EmberHeat.Reason.VENT);
        p.invulnerableTime = 0;
        p.hurt(jp.revati.skyabyss.titan.ember.back.trial.EmberTrials.damage(back, EmberIds.HEAT_BURST, null), BURN_DAMAGE);
        int i = safe(run).getOrDefault(p.getUUID(), 0);
        BlockPos at = plank(i);
        p.teleportTo(back, at.getX() + 0.5, Y + 1, at.getZ() + 0.5, 270f, 0f);
        p.setDeltaMovement(0, 0, 0);
        p.hurtMarked = true;
        run.put("burns", run.getInt("burns") + 1);
        if (run.mods.goal() == 0) {
            run.goalOk = false;
        }
        TrialFx.bar(List.of(p), ModText.deny("message.skyabyss.trial.e03.fell"));
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e03.start"));
            if (run.mods.hasHazard(0)) {
                spawnMoths(back, run, inside.size());
            }
        }
        if (run.cleared) {
            return;
        }
        double[] h = run.get("h", () -> new double[PLANKS]);
        boolean[] hot = run.get("hot", () -> new boolean[PLANKS]);
        int[] warm = run.get("warm", () -> new int[PLANKS]);
        boolean[] occupied = new boolean[PLANKS];
        // 人の動き: 落ちた・赤い段を踏んだら戻す。乗っている段は熱くなる。渡りきったら終わり
        for (ServerPlayer p : inside) {
            if (!p.isAlive()) {
                continue;
            }
            if (p.getY() < Y + 0.5) {
                fall(back, run, p);
                continue;
            }
            int i = onPlank(p);
            if (i >= 0) {
                if (hot[i]) {
                    fall(back, run, p);
                    continue;
                }
                occupied[i] = true;
                safe(run).put(p.getUUID(), i);
            }
            if (onBank(p.getX(), p.getZ()) && p.onGround()) {
                run.cleared = true;
                run.clearedAt = now;
                TrialFx.sound(back, run.group, EmberDeck.E03_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.e03.crossed"));
                TrialRuntime.refresh(back, run);
                return;
            }
        }
        // 熱の段の動き (5 tick ごと)
        int cool = coolIndex(run);
        double rise = BURN_AT * DT / hotTicks(run) * heatRate(run, inside.size());
        double fall = BURN_AT * DT / coolTicks(run);
        boolean changed = false;
        for (int i = 0; i < PLANKS; i++) {
            double r = occupied[i] ? rise * (i >= FAST_FROM[run.mods.layout()] ? FAST : 1.0) : -fall * (i == cool ? 3.0 : 1.0);
            h[i] = Math.max(0.0, Math.min(BURN_AT, h[i] + r));
            boolean wasHot = hot[i];
            hot[i] = wasHot ? h[i] > HOT_CLEAR : h[i] >= BURN_AT;
            int w = hot[i] ? 2 : h[i] >= WARM ? 1 : 0;
            if (hot[i] != wasHot || w != warm[i]) {
                changed = true;
            }
            warm[i] = w;
        }
        if (changed) {
            TrialRuntime.refresh(back, run);
        }
        // 蛾 (障害): 一定の間隔で坑の上を漂う数を保つ (死んだら次の周期で補う)
        if (run.mods.hasHazard(0) && now >= run.getLong("mothsAt")) {
            run.put("mothsAt", now + 200L);
            if (alive(back, run) < 2) {
                spawnMoths(back, run, inside.size());
            }
        }
        if (run.mods.goal() == 0 && run.getInt("burns") > 0) {
            run.goalOk = false;
        }
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

    private void spawnMoths(ServerLevel back, Run run, int players) {
        int count = 2 + (players >= 3 ? 1 : 0);
        for (int j = 0; j < count; j++) {
            Mob m = EmberCellKit.spawn(back, run.group, EmberMobSetup.EMBER_MOTH.get(), -20.5 + j * 4, Y + 3, ROW_Z + 4.5);
            if (m != null) {
                mobs(run).add(m.getUUID());
            }
        }
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int cool = coolIndex(run);
        int[] warm = run.get("warm", () -> new int[PLANKS]);
        for (int i = 0; i < PLANKS; i++) {
            BlockPos p = plank(i);
            if (warm[i] == 2) {
                out.accept(p, Blocks.MAGMA_BLOCK.defaultBlockState());
            } else if (warm[i] == 1) {
                out.accept(p, Blocks.RED_NETHER_BRICKS.defaultBlockState());
            } else if (i == cool) {
                out.accept(p, EmberKit.ASH);
            }
        }
        EmberCellKit.padGlow(ports.pad(), run.cleared, out);
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < PLANKS; i++) {
            if (back.getBlockState(plank(i)).isAir()) {
                bad.add("E03 plank " + i + " is missing");
                break;
            }
        }
        // 坑の床 (段のわき) は空気、その下は溶岩
        BlockPos side = new BlockPos(plankX(4), Y, ROW_Z + 2);
        if (!back.getBlockState(side).isAir() || !back.getBlockState(side.below()).is(Blocks.MAGMA_BLOCK)) {
            bad.add("E03 pit is not open over magma at " + side);
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(EmberDeck.E03_STONE))) {
            bad.add("E03 stone missing");
        }
        return bad;
    }
}
