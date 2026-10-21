package jp.revati.skyabyss.trial.whale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// W04 噴気孔の昇り (歩・拍・★★★。docs/design/29 の 3.1)。鯨の息の通り道の縦の穴。入口の岩棚から底へ飛び降り (ふわりと降りる)、三つの噴気孔の吹き上げに乗って
// 111・115・119 の岩棚へ昇り、南の出口の岩棚へ着く。噴気孔の台は、吹き上げの 1 秒前から白く瞬き、吹き上げのあいだだけ光る。吹き上げは目の高さで止まり、ふわりと降りる。
//   決まりを教える: 底の孔は一つだけ。昇った先の岩棚に次の孔がある。 ひねる: 孔の周期はずれていて、着いたときはちょうど休んでいる。待って、乗る。
//   一人: 吹き上げが長い。 複数: 一人が「栓の台」に立つと、みなの吹き上げが 5 割長くなる (昇る人と栓の人に分かれる)。 落ちても底へふわりと降りるだけ。
// 修飾: 配置 3 (岩棚の並び)・数字 2 (孔の周期・吹き上げの長さ)・障害 (落石)・任意の目標 (一度も底へ戻らずに)
public final class W04Vents implements TrialCell {
    // 区画の写しで動かせる (決まりは arm・tick・onUse・disarm・overlay の中だけで座標を使う。docs/design/32 の 5 章)
    @Override
    public boolean relocatable() {
        return true;
    }

    public static final int X0 = -30;
    public static final int Z0 = 129;
    // 底の床・三つの岩棚の上面のブロックの y
    public static final int BOTTOM = 107;
    public static final int LEDGE1 = 111;
    public static final int LEDGE2 = 115;
    public static final int EXIT = 119;
    // 岩棚の候補 (a 西 u 0〜4 / c 中 u 8〜12 / b 東 u 16〜20)。配置ごとに {一段目, 二段目}
    public static final int[] CAND_U = {0, 8, 16};
    public static final int[][] LAYOUT = {{0, 1}, {1, 2}, {2, 2}};
    // 一段目の岩棚の v 7〜11、二段目の v 13〜17。出口の岩棚は u 6〜14、v 19〜21
    static final int L1_V = 7;
    static final int L2_V = 13;
    public static final int EXIT_U1 = 6;
    public static final int EXIT_U2 = 14;
    static final int EXIT_V1 = 19;
    static final int EXIT_V2 = 21;
    // 周期 (tick)・吹き上げの長さ (tick)
    static final int[] PERIOD = {150, 125, 105};
    static final int[] BURST = {75, 60, 48};
    static final int WARN = 20;
    static final int ROCK_EVERY = 240;

    private final Def def = TrialCatalog.get("W04");
    private final Ports ports = new Ports(WhaleDeck.W04_GATE, false, WhaleDeck.W04_EXIT, WhaleDeck.W04_STONE, WhaleDeck.W04_PAD, WhaleDeck.REST_S1,
            new net.minecraft.world.phys.AABB(X0, BOTTOM + 1, Z0, X0 + 21, 132, 159), WhaleDeck.rests().stream().filter(r -> r.id().equals(WhaleDeck.REST_W04)).toList());

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // ---------------------------------------------------------------- 形
    // 噴気孔 i (0 底・1 一段目の岩棚・2 二段目の岩棚) の台の中心 (x, z)・台の面のブロックの y・昇り先の立つ高さ
    public record Vent(int index, int cx, int cz, int padY, int targetStand) {
        BlockPos center() {
            return new BlockPos(cx, padY, cz);
        }

        public List<BlockPos> cells() {
            List<BlockPos> out = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.add(new BlockPos(cx + dx, padY, cz + dz));
                }
            }
            return out;
        }
    }

    public static Vent vent(int layout, int i) {
        return switch (i) {
            case 0 -> new Vent(0, X0 + 10, Z0 + 4, BOTTOM, LEDGE1 + 1);
            case 1 -> new Vent(1, X0 + CAND_U[LAYOUT[layout][0]] + 2, Z0 + L1_V + 1, LEDGE1, LEDGE2 + 1);
            default -> new Vent(2, X0 + CAND_U[LAYOUT[layout][1]] + 2, Z0 + L2_V + 1, LEDGE2, EXIT + 1);
        };
    }

    // 噴気孔 i に乗って昇った先に立つ位置 {x, y, z} (次の岩棚の中ほど)。BackReach の「吹き上げの道」に使う
    public static int[] landing(int layout, int i) {
        return switch (i) {
            case 0 -> new int[] {X0 + CAND_U[LAYOUT[layout][0]] + 2, LEDGE1 + 1, Z0 + L1_V + 2};
            case 1 -> new int[] {X0 + CAND_U[LAYOUT[layout][1]] + 2, LEDGE2 + 1, Z0 + L2_V + 2};
            default -> new int[] {X0 + 10, EXIT + 1, Z0 + EXIT_V1 + 1};
        };
    }

    // 栓の台 (底の東。3×3)
    public static final int PLUG_CX = X0 + 18;
    public static final int PLUG_CZ = Z0 + 4;

    public static List<BlockPos> plugCells() {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.add(new BlockPos(PLUG_CX + dx, BOTTOM, PLUG_CZ + dz));
            }
        }
        return out;
    }

    // 岩棚の上面 (5×5。封印の扉のブロック。グループごとに残る・消える)
    public static List<BlockPos> ledge(int level, int cand) {
        List<BlockPos> out = new ArrayList<>();
        int u0 = CAND_U[cand];
        int v0 = level == 1 ? L1_V : L2_V;
        int y = level == 1 ? LEDGE1 : LEDGE2;
        for (int u = u0; u < u0 + 5; u++) {
            for (int v = v0; v < v0 + 5; v++) {
                out.add(new BlockPos(X0 + u, y, Z0 + v));
            }
        }
        return out;
    }

    public static List<BlockPos> union() {
        Set<BlockPos> set = new LinkedHashSet<>();
        for (int level = 1; level <= 2; level++) {
            for (int c = 0; c < CAND_U.length; c++) {
                set.addAll(ledge(level, c));
            }
        }
        return List.copyOf(set);
    }

    static List<BlockPos> inactive(int layout) {
        Set<BlockPos> act = new LinkedHashSet<>(ledge(1, LAYOUT[layout][0]));
        act.addAll(ledge(2, LAYOUT[layout][1]));
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!act.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 数字
    static int period(Mods m) {
        return Abyss.shorter(m, PERIOD[m.number(0)]);
    }

    static int burstBase(Mods m) {
        int b = BURST[m.number(1)];
        return m.abyss() ? Math.max(36, (int) Math.round(b / Abyss.NUMBER_STEP)) : b;
    }

    // 吹き上げの長さ (tick)。一人は長く、五人からは短い。栓の台に立つ人がいれば 5 割増し
    public static int burst(Mods m, int players, boolean plugged) {
        double f = players <= 1 ? 1.25 : players == 2 ? 1.1 : players <= 4 ? 1.0 : 0.85;
        double b = burstBase(m) * f * (plugged ? 1.5 : 1.0);
        return (int) Math.round(Math.min(b, period(m) - WARN - 10));
    }

    static long phase(Mods m, int vent, long t) {
        int p = period(m);
        return Math.floorMod(t + (long) vent * p / 3, p);
    }

    // 0 休み / 1 まもなく / 2 吹き上げ
    public static int state(Mods m, int vent, long t, int players, boolean plugged) {
        long ph = phase(m, vent, t);
        if (ph < burst(m, players, plugged)) {
            return 2;
        }
        return ph >= period(m) - WARN ? 1 : 0;
    }

    private static boolean onPlug(ServerPlayer p) {
        return Math.abs(p.getX() - (PLUG_CX + 0.5)) < 1.6 && Math.abs(p.getZ() - (PLUG_CZ + 0.5)) < 1.6 && p.getY() >= BOTTOM + 0.7 && p.getY() < BOTTOM + 2.5;
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void arm(ServerLevel back, Run run) {
        GroupGates.open(back, run.group, inactive(run.mods.layout()));
        run.put("view", -1);
        run.put("high", new HashMap<UUID, Double>());
        run.put("nextRock", back.getGameTime() + 260L);
        if (run.cleared) {
            GroupGates.open(back, run.group, WhaleDeck.W04_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        GroupGates.release(back, run.group, union());
        if (!run.cleared) {
            GroupGates.release(back, run.group, WhaleDeck.W04_EXIT);
        }
        for (ServerPlayer p : jp.revati.skyabyss.titan.group.Groups.online(back, run.group)) {
            p.removeEffect(MobEffects.LEVITATION);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        int layout = m.layout();
        long t = now - run.startTick;
        int n = Math.max(1, inside.size());
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.w04.start"));
        }
        boolean plugged = false;
        for (ServerPlayer p : inside) {
            plugged |= onPlug(p);
        }
        if (plugged != (Boolean) run.data.getOrDefault("plugged", false)) {
            run.put("plugged", plugged);
            TrialFx.sound(back, run.group, new BlockPos(PLUG_CX, BOTTOM + 1, PLUG_CZ), plugged ? SoundEvents.ANVIL_PLACE : SoundEvents.PISTON_CONTRACT, 0.9f, 0.9f);
            if (plugged) {
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.w04.plugged"));
            }
        }
        int mask = plugged ? 1 << 8 : 0;
        int[] st = new int[3];
        for (int i = 0; i < 3; i++) {
            st[i] = state(m, i, t, n, plugged);
            mask |= st[i] << (i * 2);
        }
        if (mask != run.getInt("view")) {
            run.put("view", mask);
            TrialRuntime.refresh(back, run);
        }
        Map<UUID, Double> high = (Map<UUID, Double>) run.data.get("high");
        for (int i = 0; i < 3; i++) {
            Vent v = vent(layout, i);
            if (st[i] == 2) {
                TrialFx.particles(back, run.group, ParticleTypes.CLOUD, v.cx() + 0.5, v.padY() + 1.2, v.cz() + 0.5, 0, 0, 1, 0, 0.35);
                TrialFx.particles(back, run.group, ParticleTypes.BUBBLE_POP, v.cx() + 0.5, v.padY() + 2.5, v.cz() + 0.5, 3, 0.6, 1.2, 0.6, 0.02);
            } else if (st[i] == 1) {
                TrialFx.particles(back, run.group, ParticleTypes.SMOKE, v.cx() + 0.5, v.padY() + 1.1, v.cz() + 0.5, 3, 0.6, 0.1, 0.6, 0.01);
            }
        }
        for (ServerPlayer p : inside) {
            boolean inShaft = p.getX() >= X0 && p.getX() < X0 + 21 && p.getZ() >= Z0 && p.getZ() < Z0 + 22 && p.getY() > BOTTOM && p.getY() < 132;
            if (inShaft) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
            }
            boolean lifted = false;
            for (int i = 0; i < 3; i++) {
                Vent v = vent(layout, i);
                if (Math.abs(p.getX() - (v.cx() + 0.5)) < 1.6 && Math.abs(p.getZ() - (v.cz() + 0.5)) < 1.6 && p.getY() >= v.padY() + 0.7 && p.getY() <= v.targetStand() + 3.2) {
                    if (st[i] == 2) {
                        if (p.getY() < v.targetStand() + 1.6) {
                            p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 8, 4, false, false, false));
                            lifted = true;
                        }
                    }
                    break;
                }
            }
            if (!lifted) {
                p.removeEffect(MobEffects.LEVITATION);
            }
            // 底へ戻った (昇ったあとに)
            double hi = high.getOrDefault(p.getUUID(), 0.0);
            if (p.getY() > hi) {
                high.put(p.getUUID(), p.getY());
            } else if (hi >= LEDGE1 + 1.5 && p.getY() <= BOTTOM + 1.6 && inShaft) {
                high.put(p.getUUID(), 0.0);
                run.put("falls", run.getInt("falls") + 1);
                if (m.goal() == 0) {
                    run.goalOk = false;
                }
                Notice.bar(p, ModText.hint("message.skyabyss.trial.w04.back_down"));
            }
        }
        if (m.hasHazard(0) && !run.cleared) {
            TrialRock.tick(back, run, inside, now, ROCK_EVERY, 4f);
        }
        if (!run.cleared) {
            for (ServerPlayer p : inside) {
                if (p.getZ() >= Z0 + EXIT_V1 && p.getZ() < Z0 + 22 && p.getY() >= EXIT + 0.9 && p.onGround()) {
                    run.cleared = true;
                    run.clearedAt = now;
                    GroupGates.open(back, run.group, WhaleDeck.W04_EXIT);
                    TrialFx.sound(back, run.group, WhaleDeck.W04_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
                    TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.w04.reached"));
                    TrialRuntime.refresh(back, run);
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int layout = run.mods.layout();
        int mask = Math.max(0, run.getInt("view"));
        BlockState rock = Blocks.BONE_BLOCK.defaultBlockState();
        for (int level = 1; level <= 2; level++) {
            for (BlockPos p : ledge(level, LAYOUT[layout][level - 1])) {
                out.accept(p, rock);
            }
        }
        BlockState idle = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState warn = Blocks.CALCITE.defaultBlockState();
        BlockState on = Blocks.SEA_LANTERN.defaultBlockState();
        for (int i = 0; i < 3; i++) {
            int s = (mask >> (i * 2)) & 3;
            for (BlockPos p : vent(layout, i).cells()) {
                out.accept(p, s == 2 ? on : s == 1 ? warn : idle);
            }
        }
        BlockState plug = (mask & (1 << 8)) != 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
        for (BlockPos p : plugCells()) {
            out.accept(p, plug);
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!back.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                bad.add("W04 ledge block missing at " + p);
                break;
            }
        }
        for (int l = 0; l < LAYOUT.length; l++) {
            // 昇り先の岩棚は、孔の台の縁から横 8 以内・前へ 1〜6 に届く
            for (int i = 0; i < 3; i++) {
                Vent v = vent(l, i);
                int u0;
                int u1;
                int z;
                if (i == 0) {
                    u0 = CAND_U[LAYOUT[l][0]];
                    u1 = u0 + 4;
                    z = Z0 + L1_V;
                } else if (i == 1) {
                    u0 = CAND_U[LAYOUT[l][1]];
                    u1 = u0 + 4;
                    z = Z0 + L2_V;
                } else {
                    u0 = EXIT_U1;
                    u1 = EXIT_U2;
                    z = Z0 + EXIT_V1;
                }
                if (u0 < 0 || u1 > 20) {
                    bad.add("W04 layout " + l + ": ledge outside the shaft");
                }
                int vu = v.cx() - X0;
                int gap = Math.max(0, Math.max(u0 - (vu + 1), (vu - 1) - u1));
                int dv = z - (v.cz() + 1);
                if (gap > 8 || dv > 6 || dv < 1) {
                    bad.add("W04 layout " + l + ": vent " + i + " cannot reach its next ledge (gap " + gap + ", forward " + dv + ")");
                }
            }
        }
        // 底の台は固い・栓の台も
        for (BlockPos p : plugCells()) {
            if (back.getBlockState(p).isAir()) {
                bad.add("W04 plug pad has no floor at " + p);
                break;
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(WhaleDeck.W04_STONE))) {
            bad.add("W04 stone missing");
        }
        return bad;
    }
}
