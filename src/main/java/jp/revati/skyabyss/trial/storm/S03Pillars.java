package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormDeck;
import jp.revati.skyabyss.titan.storm.back.StormDeck.Island;
import jp.revati.skyabyss.titan.storm.back.StormFx;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import jp.revati.skyabyss.titan.storm.mob.StaticMoth;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// S03 雷柱の渡り (歩・拍・★★。docs/design/29 の 3.3)。尾の上の空に立つ銅の柱の列 (5x5 の黒い石の天板) を、雷を避けて渡る。
//   決まり (教える): 橙に光った床には、1.5 秒のあと雷が落ちる。橙でない床と、真ん中の金の床は安全。雷は柱を順に走る。
//     前半の三本 (始めの台のあと) は雷が「進む向き」に走る (落ちた柱は安全になるので、雷の後ろについて渡る)。橙は北の半分か南の半分だけ。
//   ひねり: 休み場の台のあと、雷は「向こうから」走ってくる (進むほど危ない柱に出会う)。橙の形が広がる:
//     真ん中の列・手前や奥の半分・縁・斜め・金のほかぜんぶ (金の床に立つしかない)。
//   拍 (凪・嵐) は、凪は雷が遅く予告が長い・嵐は速く予告が短い (どちらでも渡れる)。
// 人数: 橙の形が人数で広がる (二人ごとに一段)、周期が人数で短くなる (一人ごとに 6%、四人分まで)。一人でも金の床と予告で渡れる
// 修飾: 配置 3 (橙の形の並び)・数字 2 (雷の周期 110/90/72・形の段 0/1/2)・障害 2 (放電の線 = 地を這う線は跳ぶ・雷蛾)・任意の目標 (無傷で)
public final class S03Pillars extends StormCell {
    // 橙の形 (5x5 の格子。i は東向きの列、j は南向きの行。真ん中 (2, 2) の金の床はどれにも入らない)
    static final int N = 0, S = 1, C = 2, W = 3, E = 4, B = 5, X = 6, ALL = 7;
    static final long[] MASKS = new long[8];
    // 各段で出る形。教え (前半) は北か南の半分だけ
    static final int[] TEACH = {N, S};
    static final int[][] TIERS = {{N, S, C, W, E}, {N, S, C, W, E, B, X}, {B, X, ALL, W, E, C}};
    static final int[] PERIOD = {110, 90, 70};
    static final int[] WARN = {40, 32, 26};
    // 柱から柱へ雷が走る間隔 (tick)
    static final int D = 10;
    static final float DAMAGE = 7f;
    // 放電の線 (障害): 間隔と予告 (tick)
    static final int ARC_EVERY = 100;
    static final int ARC_WARN = 25;

    static {
        for (int id = 0; id < 8; id++) {
            long m = 0;
            for (int j = 0; j < 5; j++) {
                for (int i = 0; i < 5; i++) {
                    boolean live = switch (id) {
                        case N -> j <= 1;
                        case S -> j >= 3;
                        case C -> j == 2;
                        case W -> i <= 1;
                        case E -> i >= 3;
                        case B -> i == 0 || i == 4 || j == 0 || j == 4;
                        case X -> i == j || i + j == 4;
                        default -> true;
                    };
                    if (live && !(i == 2 && j == 2)) {
                        m |= 1L << (j * 5 + i);
                    }
                }
            }
            MASKS[id] = m;
        }
    }

    private static final BlockState ORANGE = Blocks.ORANGE_CONCRETE.defaultBlockState();

    public S03Pillars() {
        super("S03", new Ports(StormDeck.S03_GATE, false, List.of(), StormDeck.S03_STONE, null, "", StormDeck.S03_AREA, List.of()));
    }

    // ---------------------------------------------------------------- 数字
    // 周期 (tick。5 の倍数)。人数で短く、深淵はさらに短い
    public static int period(Mods m, int players) {
        double scale = 1.0 - 0.06 * Math.min(4, Math.max(0, players - 1));
        int p = (int) Math.round(Abyss.shorter(m, PERIOD[m.number(0)]) * scale / 5.0) * 5;
        return Math.max(60, p);
    }

    public static int warn(Mods m) {
        return WARN[m.number(0)];
    }

    // 形の段 (0〜2)。人数で上がる (二人ごとに一段)
    public static int tier(Mods m, int players) {
        return Math.min(2, m.number(1) + extra(players, 2));
    }

    // 柱 k (0〜6。S03_PILLARS の順) の雷の位相 (周期の中の、橙が出る時刻)。前半 (k < 3) は進む向き、後半は向こうから
    static int offset(int k) {
        int teach = StormDeck.S03_TEACH;
        return k < teach ? k * D : (StormDeck.S03_PILLARS.length - 1 - k) * D;
    }

    // その柱のその周期の橙の形 (形の番号)
    static int shape(Run run, int k, long cycle, int players) {
        Random rng = new Random(run.seed * 31 + cycle * 7919 + k * 104729L + run.mods.layout() * 977L);
        if (k < StormDeck.S03_TEACH) {
            return TEACH[rng.nextInt(TEACH.length)];
        }
        int[] set = TIERS[tier(run.mods, players)];
        return set[rng.nextInt(set.length)];
    }

    static Island pillar(int k) {
        return StormDeck.S03_ISLANDS[StormDeck.S03_PILLARS[k]];
    }

    // 柱 k の、格子 (i, j) の床のブロック
    static BlockPos tile(int k, int i, int j) {
        Island p = pillar(k);
        return new BlockPos(p.cx() - 2 + i, p.y(), p.cz() - 2 + j);
    }

    // ---------------------------------------------------------------- 動かす
    private static long[] lastStrike(Run run) {
        return run.get("last", () -> {
            long[] a = new long[StormDeck.S03_PILLARS.length];
            java.util.Arrays.fill(a, -1);
            return a;
        });
    }

    // いま橙の柱ごとの形 (0 = なし。ビット)
    private static long[] live(Run run) {
        return run.get("live", () -> new long[StormDeck.S03_PILLARS.length]);
    }

    private static List<UUID> moths(Run run) {
        return run.get("moths", ArrayList::new);
    }

    // 放電の線: {柱, 列, 終わる tick}
    private static List<long[]> arcs(Run run) {
        return run.get("arcs", ArrayList::new);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("players", 1);
        run.put("nextArc", 0L);
        run.put("mothDone", false);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        discardAll(back, moths(run));
        arcs(run).clear();
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int n = count(inside);
        run.put("players", n);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            bar(inside, ModText.objective("message.skyabyss.trial.s03.start"));
        }
        long t = Math.max(0, now - run.startTick);
        int period = period(run.mods, n);
        int warn = warn(run.mods);
        long cycle = Math.floorDiv(t, (long) period);
        int ph = (int) Math.floorMod(t, (long) period);
        long[] live = live(run);
        long[] last = lastStrike(run);
        boolean visual = false;
        for (int k = 0; k < live.length; k++) {
            int off = offset(k);
            long mask = 0;
            if (ph >= off && ph < off + warn) {
                mask = MASKS[shape(run, k, cycle, n)];
            }
            if (mask != live[k]) {
                live[k] = mask;
                visual = true;
                if (mask != 0) {
                    BlockPos c = tile(k, 2, 2);
                    back.playSound(null, c, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.7f);
                }
            }
            if (mask != 0) {
                sparks(back, run, k, mask, ph - off >= warn - 10);
            }
            // 落ちる: 位相が (off + warn) の tick (一度だけ)
            if (ph >= off + warn && ph < off + warn + 5 && last[k] != cycle) {
                last[k] = cycle;
                strike(back, run, inside, k, MASKS[shape(run, k, cycle, n)]);
            }
        }
        if (visual) {
            TrialRuntime.refresh(back, run);
        }
        // 放電の線 (障害)
        if (run.mods.hasHazard(0) && !run.cleared) {
            arcs(back, run, inside, now);
        }
        // 雷蛾 (障害)
        if (run.mods.hasHazard(1) && !run.cleared && back.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            int want = n >= 3 ? 2 : 1;
            if (alive(back, moths(run)) < want && !(Boolean) run.data.getOrDefault("mothDone", false) && inside.stream().anyMatch(p -> p.getX() > 60)) {
                Island m = StormDeck.S03_ISLANDS[6];
                StaticMoth moth = spawn(back, StormMobSetup.STATIC_MOTH.get(), run.group, m.cx() + back.random.nextInt(7) - 3, Y + 11, m.cz() + back.random.nextInt(7) - 3);
                if (moth != null) {
                    moths(run).add(moth.getUUID());
                }
                if (moths(run).size() >= want) {
                    run.put("mothDone", true);
                }
            }
        }
        // 落ちた: 直前の休み場へ
        for (ServerPlayer p : inside) {
            if (fell(p, Y)) {
                toRest(back, run, p, p.getX() >= 66 ? "s03m" : "s03a", "message.skyabyss.trial.s03.fell");
                run.put("falls", run.getInt("falls") + 1);
            }
        }
        // 向こうの台に着いた
        if (!run.cleared) {
            Island g = StormDeck.S03_ISLANDS[StormDeck.S03_ISLANDS.length - 1];
            for (ServerPlayer p : inside) {
                if (g.over(p.getX(), p.getZ()) && p.getY() >= g.y() + 0.9 && p.onGround()) {
                    finish(back, run, inside, now, "message.skyabyss.trial.s03.crossed");
                    break;
                }
            }
        }
        if (run.mods.goal() == 0 && run.getInt("hits") > 0) {
            run.goalOk = false;
        }
    }

    // 橙の床の予告の火花 (落ちる 10 tick 前からは強く)
    private static void sparks(ServerLevel back, Run run, int k, long mask, boolean late) {
        if (back.getGameTime() % 5 != 0) {
            return;
        }
        for (int b = 0; b < 25; b += late ? 1 : 3) {
            if ((mask >> b & 1L) != 0) {
                BlockPos p = tile(k, b % 5, b / 5);
                puff(back, run.group, late ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMALL_FLAME, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 1, 0.3, 0.05, 0.3,
                        0.02);
            }
        }
    }

    // 雷が落ちる: 橙の床に立つ人へ
    private void strike(ServerLevel back, Run run, List<ServerPlayer> inside, int k, long mask) {
        Island p = pillar(k);
        Vec3 top = new Vec3(p.cx() + 0.5, p.y() + 1.0, p.cz() + 0.5);
        StormFx.to(run.group, () -> StormStrike.bolt(back, top.add(0, 18, 0), top));
        for (ServerPlayer pl : inside) {
            if (pl.isCreative() || pl.isSpectator() || !p.over(pl.getX(), pl.getZ()) || pl.getY() < p.y() - 0.2 || pl.getY() > p.y() + 2.6) {
                continue;
            }
            int i = (int) Math.floor(pl.getX()) - (p.cx() - 2);
            int j = (int) Math.floor(pl.getZ()) - (p.cz() - 2);
            if (i < 0 || i > 4 || j < 0 || j > 4 || (mask >> (j * 5 + i) & 1L) == 0) {
                continue;
            }
            pl.hurt(back.damageSources().lightningBolt(), DAMAGE);
            pl.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false, true));
        }
    }

    // 放電の線 (障害): 地を這う線が柱の上を横切る。予告 25 tick (青白い線)、跳んでいれば当たらない
    private void arcs(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        List<long[]> list = arcs(run);
        if (now >= run.getLong("nextArc") && !inside.isEmpty()) {
            run.put("nextArc", now + ARC_EVERY);
            ServerPlayer target = inside.get(new Random(run.seed ^ now).nextInt(inside.size()));
            for (int k = 0; k < StormDeck.S03_PILLARS.length; k++) {
                if (pillar(k).over(target.getX(), target.getZ())) {
                    list.add(new long[] {k, new Random(run.seed + now).nextInt(5), now + ARC_WARN});
                    back.playSound(null, target.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.6f, 1.9f);
                    break;
                }
            }
        }
        for (java.util.Iterator<long[]> it = list.iterator(); it.hasNext();) {
            long[] a = it.next();
            Island p = pillar((int) a[0]);
            double x = p.cx() - 2 + a[1] + 0.5;
            Vec3 from = new Vec3(x, p.y() + 1.3, p.cz() - 2.5);
            Vec3 to = new Vec3(x, p.y() + 1.3, p.cz() + 2.5);
            if (now < a[2]) {
                if (now % 5 == 0) {
                    StormFx.to(run.group, () -> StormFx.line(back, ParticleTypes.ELECTRIC_SPARK, from, to, 0.7));
                }
                continue;
            }
            it.remove();
            StormFx.to(run.group, () -> StormFx.line(back, ParticleTypes.END_ROD, from, to, 0.4));
            back.playSound(null, BlockPos.containing(x, p.y() + 1, p.cz()), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.8f, 1.8f);
            for (ServerPlayer pl : inside) {
                if (pl.isCreative() || !p.over(pl.getX(), pl.getZ()) || Math.abs(pl.getX() - x) > 0.8) {
                    continue;
                }
                // 跳んでいれば当たらない (足が床から 0.6 より上)
                if (pl.getY() < p.y() + 1.0 + 0.6 && pl.getY() >= p.y() - 0.2) {
                    pl.hurt(back.damageSources().lightningBolt(), DAMAGE);
                }
            }
        }
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        long[] live = live(run);
        for (int k = 0; k < live.length; k++) {
            if (live[k] == 0) {
                continue;
            }
            for (int b = 0; b < 25; b++) {
                if ((live[k] >> b & 1L) != 0) {
                    out.accept(tile(k, b % 5, b / 5), ORANGE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 検査
    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        Island[] is = StormDeck.S03_ISLANDS;
        for (int i = 0; i < is.length; i++) {
            for (BlockPos p : is[i].floor()) {
                if (back.getBlockState(p).isAir()) {
                    bad.add("S03 island " + i + " has a bad tile at " + p);
                    break;
                }
            }
            if (i + 1 < is.length) {
                // 跳べる: 縁と縁のあいだが空気 2 以下 (BackReach の跳躍 3.2)、高さの差が 1 以下
                int gap = is[i + 1].x1() - is[i].x2() - 1;
                int dy = is[i + 1].y() - is[i].y();
                if (gap > 2 || dy > 1) {
                    bad.add("S03 jump " + i + " is too hard (gap " + gap + ", rise " + dy + ")");
                }
            }
        }
        for (int k = 0; k < StormDeck.S03_PILLARS.length; k++) {
            if (!back.getBlockState(tile(k, 2, 2)).is(Blocks.GOLD_BLOCK)) {
                bad.add("S03 pillar " + k + " has no gold tile");
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(StormDeck.S03_STONE))) {
            bad.add("S03 stone missing");
        }
        // 形: どの形も金の床を含まず、どの段にも安全な床が半分以上ある (ALL は金の一枚だけ)
        for (int id = 0; id < 8; id++) {
            if ((MASKS[id] >> 12 & 1L) != 0) {
                bad.add("S03 shape " + id + " covers the gold tile");
            }
        }
        return bad;
    }
}
