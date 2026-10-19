package jp.revati.skyabyss.trial.tide;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.F;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// T06 貝の合唱 (智・解く・★。docs/design/29 の 3.2)。壁画の絵の色の順に、七つの貝を叩く。三つの合唱で、読み方が変わる:
//   一つ目: 上の段を、左から右へ読む (決まりを教える)。 二つ目: 下の段を、右から左へ (鏡)。 三つ目: 上と下を、左から交互に (上・下・上・下…)。
//   貝の色はこの組だけのもの (見かけの上書きなので、別の組の同じ部屋とは混ざらない)。叩いた数は壁画の下の灯りの列に点く。
//   違う貝を叩くと海が濁り (暗くなり)、その合唱は最初から (罰はそれだけ)。
//   一人: 壁画をしばらく見返せる (書見台。冷めるのが早い)。 三人から: 合唱が長い。 「うねり」の障害は、ときどき二つの貝が色を取り替える (予告つき)。
// 修飾: 数字 2 (合唱の長さ 3・4・5 / 壁画の見え方: 出しっぱなし・25 秒・12 秒で消える)・障害 (うねり)・任意の目標 (一度も濁らせずに)
public final class T06Choir implements TrialCell {
    public static final int PEDS = 7;
    public static final int PED_X0 = 56;
    public static final int PED_Z = 57;
    public static final int MURAL_X = 69;
    public static final int MURAL_Z0 = 51;
    public static final BlockPos LECTERN = new BlockPos(68, F + 1, 49);
    static final int[] LENGTH = {3, 4, 5};
    static final int[] SHOWN = {-1, 500, 240};
    static final int SHOW_AGAIN = 240;
    static final int SWAP_EVERY = 480;
    static final int SWAP_WARN = 30;
    static final BlockState[] COLORS = {Blocks.YELLOW_CONCRETE.defaultBlockState(), Blocks.LIME_CONCRETE.defaultBlockState(), Blocks.MAGENTA_CONCRETE.defaultBlockState(),
            Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), Blocks.RED_CONCRETE.defaultBlockState(), Blocks.WHITE_CONCRETE.defaultBlockState(),
            Blocks.ORANGE_CONCRETE.defaultBlockState()};

    private final Def def = TrialCatalog.get("T06");
    private final Ports ports = new Ports(TideDeck.T06_GATE, false, TideDeck.T06_EXIT, TideDeck.T06_STONE, TideDeck.T06_PAD, TideDeck.REST_H,
            new net.minecraft.world.phys.AABB(56, F + 1, 49, 69, F + 11, 72), TideDeckRests.of("t06"));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    public static BlockPos pedestal(int i) {
        return new BlockPos(PED_X0 + 2 * i, F + 1, PED_Z);
    }

    // 壁画の i 番目の列の z。段: 0 = 上 (y F+3)・1 = 下 (y F+2)・2 = 進みの灯り (y F+1)
    public static BlockPos mural(int row, int i) {
        return new BlockPos(MURAL_X, F + 3 - row, MURAL_Z0 + 2 * i);
    }

    @Override
    public Set<BlockPos> interactive() {
        Set<BlockPos> set = new HashSet<>();
        for (int i = 0; i < PEDS; i++) {
            set.add(pedestal(i));
        }
        set.add(LECTERN);
        return set;
    }

    // ---------------------------------------------------------------- 合唱の組み立て
    // 長さ: 一つ目・二つ目は base、三つ目は上下 m ずつ (m = base / 2 + 1)
    static int base(Mods m, int players) {
        int b = LENGTH[m.number(0)] + (players >= 3 ? 1 : 0) + (players >= 5 ? 1 : 0);
        return Math.min(5, Abyss.plusOne(m, b));
    }

    // 合唱 r (0〜2) の、期待する色の並び (打つ順) と、壁画の見かけ (上・下の段の色。-1 = 空)
    public record Chorus(int[] order, int[][] rows) {
    }

    public static Chorus chorus(Random rng, int round, int base) {
        int[] palette = new int[COLORS.length];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = i;
        }
        shuffle(rng, palette);
        int[][] rows = {fill(), fill()};
        if (round == 0) {
            int[] order = java.util.Arrays.copyOf(palette, base);
            System.arraycopy(order, 0, rows[0], 0, base);
            return new Chorus(order, rows);
        }
        if (round == 1) {
            int[] order = java.util.Arrays.copyOf(palette, base);
            // 上の段は飾り (違う色)。下の段は、右から左へ読んで order になる並び
            for (int i = 0; i < base; i++) {
                rows[0][i] = palette[(base + i) % palette.length];
                rows[1][base - 1 - i] = order[i];
            }
            return new Chorus(order, rows);
        }
        int m = base / 2 + 1;
        int[] order = new int[2 * m];
        for (int i = 0; i < m; i++) {
            rows[0][i] = palette[i];
            rows[1][i] = palette[(m + i) % palette.length];
            order[2 * i] = rows[0][i];
            order[2 * i + 1] = rows[1][i];
        }
        return new Chorus(order, rows);
    }

    private static int[] fill() {
        int[] a = new int[PEDS];
        java.util.Arrays.fill(a, -1);
        return a;
    }

    private static void shuffle(Random rng, int[] a) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    // いま、色 c の貝はどれか
    public static int pedestalOf(int[] colors, int c) {
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == c) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void arm(ServerLevel back, Run run) {
        Random rng = new Random(run.seed);
        int[] colors = new int[PEDS];
        for (int i = 0; i < PEDS; i++) {
            colors[i] = i;
        }
        shuffle(rng, colors);
        run.put("colors", colors);
        run.put("round", -1);
        run.put("progress", 0);
        run.put("mistakes", 0);
        run.put("rng", rng);
        run.put("visibleUntil", Long.MAX_VALUE);
        run.put("recallCool", 0L);
        run.put("view", -1L);
        run.put("nextSwap", back.getGameTime() + 400L);
        if (run.cleared) {
            GroupGates.open(back, run.group, TideDeck.T06_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        if (!run.cleared) {
            GroupGates.release(back, run.group, TideDeck.T06_EXIT);
        }
    }

    private void startRound(ServerLevel back, Run run, int round, long now, int players) {
        Chorus ch = chorus((Random) run.data.get("rng"), round, base(run.mods, players));
        run.put("round", round);
        run.put("chorus", ch);
        run.put("progress", 0);
        int shown = SHOWN[run.mods.number(1)];
        run.put("visibleUntil", shown < 0 ? Long.MAX_VALUE : now + (run.mods.abyss() ? Math.round(shown / Abyss.NUMBER_STEP) : shown));
        TrialRuntime.refresh(back, run);
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        int n = Math.max(1, inside.size());
        if (run.getInt("round") < 0 && !run.cleared) {
            startRound(back, run, 0, now, n);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.t06.start"));
            TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.t06.rule0"));
        }
        if (run.cleared) {
            return;
        }
        int[] colors = (int[]) run.data.get("colors");
        // うねり (障害): 二つの貝が色を取り替える。予告の粒 → 取り替え
        if (m.hasHazard(0)) {
            long next = run.getLong("nextSwap");
            Random r = new Random(run.seed ^ next);
            int a = r.nextInt(PEDS);
            int b = (a + 1 + r.nextInt(PEDS - 1)) % PEDS;
            if (now >= next - SWAP_WARN && now < next) {
                run.put("swapA", a + 1);
                run.put("swapB", b + 1);
                for (int k : new int[] {a, b}) {
                    BlockPos p = pedestal(k);
                    TrialFx.particles(back, run.group, ParticleTypes.GLOW, p.getX() + 0.5, p.getY() + 1.4, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
                }
            } else if (now >= next) {
                int t = colors[a];
                colors[a] = colors[b];
                colors[b] = t;
                run.put("swapA", 0);
                run.put("swapB", 0);
                run.put("nextSwap", now + (n >= 3 ? SWAP_EVERY * 3 / 4 : SWAP_EVERY));
                TrialFx.sound(back, run.group, pedestal(a), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
                TrialRuntime.refresh(back, run);
            }
        }
        long view = (now < run.getLong("visibleUntil") ? 1L : 0L) | (long) run.getInt("progress") << 4 | (long) run.getInt("round") << 12
                | (long) (run.getInt("swapA") + 8 * run.getInt("swapB")) << 20;
        if (view != run.getLong("view")) {
            run.put("view", view);
            TrialRuntime.refresh(back, run);
        }
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        if (run.cleared) {
            if (!pos.equals(LECTERN)) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t06.done"));
            }
            return true;
        }
        if (pos.equals(LECTERN)) {
            long cool = run.getLong("recallCool");
            if (now < run.getLong("visibleUntil") && run.getLong("visibleUntil") != Long.MAX_VALUE) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t06.recall_showing"));
            } else if (run.getLong("visibleUntil") == Long.MAX_VALUE) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t06.recall_always"));
            } else if (now < cool) {
                Notice.bar(player, ModText.deny("message.skyabyss.trial.t06.recall_cooling", (int) Math.ceil((cool - now) / 20.0)));
            } else {
                run.put("visibleUntil", now + SHOW_AGAIN);
                int players = Groups.inBox(back, run.group, ports.area()).size();
                run.put("recallCool", now + (players <= 1 ? 60L : 200L));
                TrialFx.sound(back, run.group, LECTERN, SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.9f);
                Notice.bar(player, ModText.progress("message.skyabyss.trial.t06.recalled"));
                TrialRuntime.refresh(back, run);
            }
            return true;
        }
        int idx = -1;
        for (int i = 0; i < PEDS; i++) {
            if (pos.equals(pedestal(i))) {
                idx = i;
            }
        }
        if (idx < 0 || run.getInt("round") < 0) {
            return false;
        }
        Chorus ch = (Chorus) run.data.get("chorus");
        int[] colors = (int[]) run.data.get("colors");
        int progress = run.getInt("progress");
        int want = pedestalOf(colors, ch.order()[progress]);
        if (idx == want) {
            progress++;
            run.put("progress", progress);
            TrialFx.sound(back, run.group, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, 0.6f + 0.12f * progress);
            TrialFx.particles(back, run.group, ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.5);
            Notice.bar(player, ModText.progress("message.skyabyss.trial.t06.step", progress, ch.order().length));
            if (progress >= ch.order().length) {
                int round = run.getInt("round") + 1;
                int players = Math.max(1, Groups.inBox(back, run.group, ports.area()).size());
                TrialFx.sound(back, run.group, pos, SoundEvents.BELL_BLOCK, 1.6f, 0.8f + 0.2f * round);
                if (round >= 3) {
                    run.cleared = true;
                    run.clearedAt = now;
                    GroupGates.open(back, run.group, TideDeck.T06_EXIT);
                    TrialFx.sound(back, run.group, TideDeck.T06_STONE, SoundEvents.BELL_RESONATE, 1.5f, 1.0f);
                    for (ServerPlayer p : Groups.inBox(back, run.group, ports.area())) {
                        Notice.bar(p, ModText.progress("message.skyabyss.trial.t06.solved"));
                    }
                } else {
                    startRound(back, run, round, now, players);
                    for (ServerPlayer p : Groups.inBox(back, run.group, ports.area())) {
                        Notice.bar(p, ModText.hint("message.skyabyss.trial.t06.rule" + round));
                    }
                }
            }
            TrialRuntime.refresh(back, run);
            return true;
        }
        // 違う貝: 海が濁る (暗くなり、その合唱は最初から)
        run.put("progress", 0);
        run.put("mistakes", run.getInt("mistakes") + 1);
        if (run.mods.goal() == 0) {
            run.goalOk = false;
        }
        TrialFx.sound(back, run.group, pos, SoundEvents.AMETHYST_BLOCK_BREAK, 1.5f, 0.5f);
        TrialFx.particles(back, run.group, ParticleTypes.SQUID_INK, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 20, 0.6, 0.4, 0.6, 0.02);
        for (ServerPlayer p : Groups.inBox(back, run.group, ports.area())) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
            Notice.bar(p, ModText.deny("message.skyabyss.trial.t06.murk"));
        }
        TrialRuntime.refresh(back, run);
        return true;
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int[] colors = run.data.get("colors") instanceof int[] c ? c : null;
        if (colors == null) {
            return;
        }
        long now = back.getGameTime();
        int swapA = run.getInt("swapA") - 1;
        int swapB = run.getInt("swapB") - 1;
        BlockState warn = Blocks.CALCITE.defaultBlockState();
        for (int i = 0; i < PEDS; i++) {
            out.accept(pedestal(i), i == swapA || i == swapB ? warn : COLORS[colors[i]]);
        }
        if (!(run.data.get("chorus") instanceof Chorus ch)) {
            return;
        }
        boolean visible = now < run.getLong("visibleUntil");
        BlockState hidden = Blocks.BLACKSTONE.defaultBlockState();
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < PEDS; i++) {
                int c = ch.rows()[row][i];
                if (c < 0) {
                    out.accept(mural(row, i), Blocks.GRAY_CONCRETE.defaultBlockState());
                } else {
                    out.accept(mural(row, i), visible ? COLORS[c] : hidden);
                }
            }
        }
        int progress = run.getInt("progress");
        for (int i = 0; i < ch.order().length && i < PEDS; i++) {
            out.accept(mural(2, i), i < progress ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < PEDS; i++) {
            BlockPos p = pedestal(i);
            if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).isAir()) {
                bad.add("T06 shell pedestal " + i + " is missing or covered at " + p);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int i = 0; i < PEDS; i++) {
                if (back.getBlockState(mural(row, i)).isAir()) {
                    bad.add("T06 mural cell missing at " + mural(row, i));
                    row = 3;
                    break;
                }
            }
        }
        if (!back.getBlockState(LECTERN).is(Blocks.LECTERN)) {
            bad.add("T06 lectern missing");
        }
        // 合唱は、どの長さ・どの種でも、貝のどれかを指す (色の並びは重ならない)
        for (int len : LENGTH) {
            for (int round = 0; round < 3; round++) {
                for (long seed = 0; seed < 20; seed++) {
                    Chorus ch = chorus(new Random(seed), round, Math.min(5, len + 2));
                    Set<Integer> seen = new HashSet<>();
                    for (int c : ch.order()) {
                        if (c < 0 || c >= COLORS.length || !seen.add(c)) {
                            bad.add("T06 chorus (round " + round + ", length " + len + ") repeats or misses a colour");
                            return bad;
                        }
                    }
                }
            }
        }
        if (!back.getBlockState(TideDeck.T06_STONE).is(jp.revati.skyabyss.titan.tide.back.TideBackSetup.CHIME_STONE.get())) {
            bad.add("T06 stone missing");
        }
        if (!back.getBlockState(TideDeck.T06_EXIT.get(0)).is(ModBlocks.SEALED_GATE.get())) {
            // 戸は installGates が置く
            bad.add("T06 exit gate is not placed");
        }
        return bad;
    }
}
