package jp.revati.skyabyss.trial.tide;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.G;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
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

// T08 珊瑚の標本棚 (智・解く・★★★。docs/design/29 の 3.2)。棚に散らばる珊瑚片 (色と丈をもつ) を、札の決まりで鉢へ分ける。三つの組で、決まりが変わる:
//   一つ目: 札は「色」だけ (決まりを教える)。 二つ目: 札は「丈」だけ (ひねる。色は見ない)。
//   三つ目: 札は「色と丈」。両方が合う鉢へ入れる。どの鉢にも合わなければ、四つ目の「割れ鉢」へ (札が黒い)。
//   間違った鉢へ入れると、珊瑚片は棚へ戻り、海が少し濁る (罰はそれだけ)。持てるのは一人一つ。
//   一人: 一つずつ運び、間違いは戻せる。 三人から: 片が増える (拾う人・運ぶ人・札を読む人に分かれる)。 札は書見台でしばらく見返せる (冷めるのが早い)。
// 修飾: 配置 3 (三つ目の札の型: 色も丈も別 / 色がかぶる / 丈がかぶる)・数字 2 (片の数 10・12・14 / 札の見え方 出しっぱなし・25 秒・12 秒)・
//       障害 (潮替え: ときどき二つの鉢の札が入れ替わる。予告つき)・任意の目標 (戻さずに = 一度も間違えずに)
public final class T08Shelf implements TrialCell {
    public static final int SLOTS = 14;
    public static final int COLORS = 5;
    // 棚 (東の壁ぎわの二列。x 71・69、z 78〜90 の 2 おき)
    public static final int SLOT_X0 = 71;
    public static final int SLOT_Z0 = 78;
    // 鉢 (西の壁ぎわ。x 58、z 78・82・86・90)。札の台は x 56
    public static final int BOWL_X = 58;
    public static final int LABEL_X = 56;
    public static final int BOWL_Z0 = 78;
    public static final BlockPos LECTERN = new BlockPos(60, G + 1, 76);
    // 片の数 (三つの組。やさしい・標準・きびしい)
    static final int[][] COUNTS = {{3, 3, 4}, {3, 4, 5}, {4, 4, 6}};
    static final int[] SHOWN = {-1, 500, 240};
    static final int SHOW_AGAIN = 240;
    static final int SWAP_EVERY = 700;
    static final int SWAP_WARN = 36;
    static final int MURK = 50;
    static final BlockState[] COLOR_BLOCKS = {Blocks.TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.HORN_CORAL_BLOCK.defaultBlockState()};

    private final Def def = TrialCatalog.get("T08");
    private final Ports ports = new Ports(TideDeck.T08_GATE, false, TideDeck.T08_EXIT, TideDeck.T08_STONE, TideDeck.T08_PAD, TideDeck.REST_H2,
            new net.minecraft.world.phys.AABB(56, G + 1, 75, 73, G + 11, 100), TideDeckRests.of("t08"));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // ---------------------------------------------------------------- 場所
    public static BlockPos slot(int s) {
        return new BlockPos(SLOT_X0 - 2 * (s / 7), G + 1, SLOT_Z0 + 2 * (s % 7));
    }

    public static BlockPos bowl(int b) {
        return new BlockPos(BOWL_X, G + 1, BOWL_Z0 + 4 * b);
    }

    public static BlockPos label(int b) {
        return new BlockPos(LABEL_X, G + 1, BOWL_Z0 + 4 * b);
    }

    @Override
    public Set<BlockPos> interactive() {
        Set<BlockPos> set = new HashSet<>();
        for (int s = 0; s < SLOTS; s++) {
            for (int dy = 0; dy < 4; dy++) {
                set.add(slot(s).above(dy));
            }
        }
        for (int b = 0; b < 4; b++) {
            for (int dy = 0; dy < 4; dy++) {
                set.add(bowl(b).above(dy));
            }
        }
        set.add(LECTERN);
        return set;
    }

    // ---------------------------------------------------------------- 決まりの組み立て (純粋な計算。自己テストが見る)
    public record Piece(int color, int height) {
    }

    // labels[b] = {色, 丈} (-1 = 見ない)。b = 3 は割れ鉢 (三つ目の組だけ)。round: 0 色 / 1 丈 / 2 色と丈
    public record Round(int round, int[][] labels, List<Piece> pieces) {
    }

    static boolean matches(int[] label, Piece p) {
        return (label[0] < 0 || label[0] == p.color()) && (label[1] < 0 || label[1] == p.height());
    }

    // 札 labels のもとで、片 p の入る鉢 (どれにも合わなければ 3 = 割れ鉢)
    public static int bowlFor(int round, int[][] labels, Piece p) {
        for (int b = 0; b < 3; b++) {
            if (matches(labels[b], p)) {
                return b;
            }
        }
        return round == 2 ? 3 : -1;
    }

    static int[] perm(Random rng, int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    public static Round makeRound(Random rng, int round, int count, int layout) {
        int[][] labels = new int[4][];
        for (int b = 0; b < 4; b++) {
            labels[b] = new int[] {-1, -1};
        }
        List<Piece> pieces = new ArrayList<>();
        if (round == 0) {
            int[] c = perm(rng, COLORS);
            for (int b = 0; b < 3; b++) {
                labels[b] = new int[] {c[b], -1};
            }
            for (int i = 0; i < count; i++) {
                pieces.add(new Piece(i < 3 ? c[i] : c[rng.nextInt(3)], 1 + rng.nextInt(3)));
            }
        } else if (round == 1) {
            int[] h = perm(rng, 3);
            for (int b = 0; b < 3; b++) {
                labels[b] = new int[] {-1, h[b] + 1};
            }
            for (int i = 0; i < count; i++) {
                pieces.add(new Piece(rng.nextInt(COLORS), i < 3 ? h[i] + 1 : 1 + rng.nextInt(3)));
            }
        } else {
            int[] c = perm(rng, COLORS);
            int[] h = perm(rng, 3);
            if (layout == 1) {
                // 色がかぶる: 0 と 1 が同じ色
                c[1] = c[0];
            } else if (layout == 2) {
                // 丈がかぶる: 0 と 1 が同じ丈 (色は別)
                h[1] = h[0];
            }
            for (int b = 0; b < 3; b++) {
                labels[b] = new int[] {c[b], h[b] + 1};
            }
            labels[3] = new int[] {-2, -2};
            int decoys = Math.max(1, count / 3);
            int exact = Math.max(3, count - decoys);
            for (int i = 0; i < exact; i++) {
                int b = i < 3 ? i : rng.nextInt(3);
                pieces.add(new Piece(labels[b][0], labels[b][1]));
            }
            for (int i = 0; i < count - exact; i++) {
                pieces.add(decoy(rng, labels));
            }
        }
        java.util.Collections.shuffle(pieces, rng);
        return new Round(round, labels, List.copyOf(pieces));
    }

    // どの札とも (色, 丈) の両方は合わない片。たいていは、色か丈の片方だけ合う (紛らわしい)
    static Piece decoy(Random rng, int[][] labels) {
        for (int tries = 0; tries < 60; tries++) {
            int[] near = labels[rng.nextInt(3)];
            Piece p;
            if (tries < 40 && rng.nextBoolean()) {
                p = new Piece(near[0], 1 + rng.nextInt(3));
            } else if (tries < 40) {
                p = new Piece(rng.nextInt(COLORS), near[1]);
            } else {
                p = new Piece(rng.nextInt(COLORS), 1 + rng.nextInt(3));
            }
            boolean hit = false;
            for (int b = 0; b < 3; b++) {
                hit |= matches(labels[b], p);
            }
            if (!hit) {
                return p;
            }
        }
        // 十分に探しても見つからなければ (あり得ない組): 札にない色の片
        int used = 0;
        for (int b = 0; b < 3; b++) {
            used |= 1 << labels[b][0];
        }
        for (int c = 0; c < COLORS; c++) {
            if ((used & (1 << c)) == 0) {
                return new Piece(c, 1);
            }
        }
        return new Piece(0, 1);
    }

    // 片の数: 人数で増える (三人から +1、五人から +2。三つ目の組だけ。棚の穴の数まで)
    public static int count(Mods m, int round, int players) {
        int n = COUNTS[m.number(0)][round];
        if (round == 2) {
            n += (players >= 3 ? 1 : 0) + (players >= 5 ? 1 : 0);
            n = Abyss.plusOne(m, n);
        }
        return Math.min(n, SLOTS);
    }

    static int shownTicks(Mods m) {
        int s = SHOWN[m.number(1)];
        return s < 0 ? -1 : Abyss.shorter(m, s);
    }

    // ---------------------------------------------------------------- 状態
    static final class S {
        int round = -1;
        Round r;
        int[] slotOf = new int[0];
        int[] state = new int[0];
        final Map<UUID, Integer> carry = new HashMap<>();
        final int[] bowlCount = new int[4];
        long visibleUntil = Long.MAX_VALUE;
        long recallCool;
        long nextSwap;
        int swapA = -1;
        int swapB = -1;
        int mistakes;
        long view = -1;
        Random rng;
        long lastCarryNote;
    }

    private static S st(Run run) {
        return (S) run.data.get("s");
    }

    // 自己テスト用
    public static Round round(Run run) {
        return run.data.get("s") instanceof S s ? s.r : null;
    }

    public static int roundIndex(Run run) {
        return run.data.get("s") instanceof S s ? s.round : -1;
    }

    // 片 i の棚の穴 (置いたあと・持っているときは -1)
    public static int slotOf(Run run, int i) {
        return run.data.get("s") instanceof S s && i < s.state.length && s.state[i] == 0 ? s.slotOf[i] : -1;
    }

    public static int placedCount(Run run) {
        return run.data.get("s") instanceof S s ? placed(s) : 0;
    }

    public static int mistakes(Run run) {
        return run.data.get("s") instanceof S s ? s.mistakes : 0;
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        S s = new S();
        s.rng = new Random(run.seed);
        s.nextSwap = back.getGameTime() + SWAP_EVERY;
        run.put("s", s);
        if (run.cleared) {
            GroupGates.open(back, run.group, TideDeck.T08_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        if (!run.cleared) {
            GroupGates.release(back, run.group, TideDeck.T08_EXIT);
        }
    }

    private void startRound(ServerLevel back, Run run, S s, int round, long now, int players) {
        Mods m = run.mods;
        s.round = round;
        s.r = makeRound(s.rng, round, count(m, round, players), m.layout());
        int n = s.r.pieces().size();
        int[] order = perm(s.rng, SLOTS);
        s.slotOf = new int[n];
        s.state = new int[n];
        for (int i = 0; i < n; i++) {
            s.slotOf[i] = order[i];
        }
        s.carry.clear();
        java.util.Arrays.fill(s.bowlCount, 0);
        int shown = shownTicks(m);
        s.visibleUntil = shown < 0 ? Long.MAX_VALUE : now + shown;
        s.view = -1;
        TrialRuntime.refresh(back, run);
    }

    private int pieceAtSlot(S s, int slot) {
        for (int i = 0; i < s.slotOf.length; i++) {
            if (s.state[i] == 0 && s.slotOf[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static int slotIndex(BlockPos pos) {
        for (int s = 0; s < SLOTS; s++) {
            BlockPos p = slot(s);
            if (pos.getX() == p.getX() && pos.getZ() == p.getZ() && pos.getY() >= p.getY() && pos.getY() <= p.getY() + 3) {
                return s;
            }
        }
        return -1;
    }

    private static int bowlIndex(BlockPos pos) {
        for (int b = 0; b < 4; b++) {
            BlockPos p = bowl(b);
            if (pos.getX() == p.getX() && pos.getZ() == p.getZ() && pos.getY() >= p.getY() && pos.getY() <= p.getY() + 3) {
                return b;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        S s = st(run);
        int n = Math.max(1, inside.size());
        if (s.round < 0 && !run.cleared) {
            startRound(back, run, s, 0, now, n);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.t08.start"));
            TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.t08.rule0"));
        }
        if (run.cleared) {
            return;
        }
        // 持ったまま部屋を出た人の片は、棚へ戻る
        Set<UUID> here = new HashSet<>();
        for (ServerPlayer p : inside) {
            here.add(p.getUUID());
        }
        boolean dropped = false;
        for (UUID id : new ArrayList<>(s.carry.keySet())) {
            if (!here.contains(id)) {
                s.state[s.carry.remove(id)] = 0;
                dropped = true;
            }
        }
        // 潮替え (障害): 二つの鉢の札が入れ替わる。予告 (札がまたたく) のあとで
        if (run.mods.hasHazard(0) && s.round >= 0) {
            if (now >= s.nextSwap - SWAP_WARN && now < s.nextSwap && s.swapA < 0) {
                int[] pr = perm(new Random(run.seed ^ s.nextSwap), 3);
                s.swapA = pr[0];
                s.swapB = pr[1];
                TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.t08.swap_warn"));
                for (int b : new int[] {s.swapA, s.swapB}) {
                    BlockPos p = label(b);
                    TrialFx.particles(back, run.group, ParticleTypes.GLOW, p.getX() + 0.5, p.getY() + 2.0, p.getZ() + 0.5, 6, 0.3, 0.5, 0.3, 0.02);
                }
                dropped = true;
            } else if (now >= s.nextSwap && s.swapA >= 0) {
                int[] t = s.r.labels()[s.swapA];
                s.r.labels()[s.swapA] = s.r.labels()[s.swapB];
                s.r.labels()[s.swapB] = t;
                s.swapA = -1;
                s.swapB = -1;
                s.nextSwap = now + (n >= 3 ? SWAP_EVERY * 3 / 4 : SWAP_EVERY);
                TrialFx.sound(back, run.group, bowl(0), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
                TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.t08.swapped"));
                dropped = true;
            }
        }
        // 持っている片の知らせ (4 秒ごと)
        if (now - s.lastCarryNote >= 80) {
            s.lastCarryNote = now;
            for (ServerPlayer p : inside) {
                Integer idx = s.carry.get(p.getUUID());
                if (idx != null) {
                    Piece pc = s.r.pieces().get(idx);
                    Notice.bar(p, ModText.hint("message.skyabyss.trial.t08.holding", colorName(pc.color()), pc.height()));
                }
            }
        }
        long vis = now < s.visibleUntil ? 1L : 0L;
        long view = vis | (long) (s.swapA + 1) << 4 | (long) (s.swapB + 1) << 8 | (long) s.round << 12;
        if (dropped || view != s.view) {
            s.view = view;
            TrialRuntime.refresh(back, run);
        }
    }

    // 色の名前 (lang の trial.skyabyss.t08.color.<n>)
    static net.minecraft.network.chat.Component colorName(int c) {
        return net.minecraft.network.chat.Component.translatable("trial.skyabyss.t08.color." + c);
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        S s = st(run);
        if (run.cleared) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial.t08.done"));
            return true;
        }
        if (pos.equals(LECTERN)) {
            if (s.round < 0) {
                return true;
            }
            if (s.visibleUntil == Long.MAX_VALUE) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t08.recall_always"));
            } else if (now < s.visibleUntil) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t08.recall_showing"));
            } else if (now < s.recallCool) {
                Notice.bar(player, ModText.deny("message.skyabyss.trial.t08.recall_cooling", (int) Math.ceil((s.recallCool - now) / 20.0)));
            } else {
                s.visibleUntil = now + SHOW_AGAIN;
                int players = Math.max(1, Groups.inBox(back, run.group, ports.area()).size());
                s.recallCool = now + (players <= 1 ? 60L : 200L);
                TrialFx.sound(back, run.group, LECTERN, SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.9f);
                Notice.bar(player, ModText.progress("message.skyabyss.trial.t08.recalled"));
                TrialRuntime.refresh(back, run);
            }
            Notice.bar(player, ModText.hint("message.skyabyss.trial.t08.rule" + s.round));
            return true;
        }
        if (s.round < 0) {
            return false;
        }
        int slot = slotIndex(pos);
        if (slot >= 0) {
            Integer held = s.carry.get(player.getUUID());
            int onShelf = pieceAtSlot(s, slot);
            if (held == null && onShelf >= 0) {
                s.carry.put(player.getUUID(), onShelf);
                s.state[onShelf] = 1;
                Piece pc = s.r.pieces().get(onShelf);
                TrialFx.sound(back, run.group, pos, SoundEvents.CORAL_BLOCK_PLACE, 1.2f, 1.3f);
                Notice.bar(player, ModText.progress("message.skyabyss.trial.t08.picked", colorName(pc.color()), pc.height()));
                TrialRuntime.refresh(back, run);
            } else if (held != null && onShelf < 0) {
                // 空いた棚の穴へ戻す
                s.slotOf[held] = slot;
                s.state[held] = 0;
                s.carry.remove(player.getUUID());
                TrialFx.sound(back, run.group, pos, SoundEvents.CORAL_BLOCK_PLACE, 1.2f, 0.9f);
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t08.returned"));
                TrialRuntime.refresh(back, run);
            } else if (held != null) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t08.hands_full"));
            }
            return true;
        }
        int bowl = bowlIndex(pos);
        if (bowl >= 0) {
            Integer held = s.carry.get(player.getUUID());
            if (held == null) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t08.empty_hands"));
                return true;
            }
            if (bowl == 3 && s.round != 2) {
                Notice.bar(player, ModText.hint("message.skyabyss.trial.t08.no_cracked"));
                return true;
            }
            Piece pc = s.r.pieces().get(held);
            int want = bowlFor(s.round, s.r.labels(), pc);
            s.carry.remove(player.getUUID());
            if (want == bowl) {
                s.state[held] = 2;
                s.bowlCount[bowl]++;
                TrialFx.sound(back, run.group, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.7f + 0.12f * placed(s));
                TrialFx.particles(back, run.group, ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.5);
                Notice.bar(player, ModText.progress("message.skyabyss.trial.t08.placed", placed(s), s.r.pieces().size()));
                if (placed(s) >= s.r.pieces().size()) {
                    int next = s.round + 1;
                    int players = Math.max(1, Groups.inBox(back, run.group, ports.area()).size());
                    TrialFx.sound(back, run.group, pos, SoundEvents.BELL_BLOCK, 1.6f, 0.8f + 0.2f * next);
                    if (next >= 3) {
                        run.cleared = true;
                        run.clearedAt = now;
                        GroupGates.open(back, run.group, TideDeck.T08_EXIT);
                        TrialFx.sound(back, run.group, TideDeck.T08_STONE, SoundEvents.BELL_RESONATE, 1.5f, 1.0f);
                        for (ServerPlayer p : Groups.inBox(back, run.group, ports.area())) {
                            Notice.bar(p, ModText.progress("message.skyabyss.trial.t08.solved"));
                        }
                    } else {
                        startRound(back, run, s, next, now, players);
                        for (ServerPlayer p : Groups.inBox(back, run.group, ports.area())) {
                            Notice.bar(p, ModText.hint("message.skyabyss.trial.t08.rule" + next));
                        }
                    }
                }
            } else {
                // 違う鉢: 片は棚の元の穴へ戻り、海が少し濁る
                s.state[held] = 0;
                s.mistakes++;
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
                TrialFx.sound(back, run.group, pos, SoundEvents.AMETHYST_BLOCK_BREAK, 1.5f, 0.5f);
                TrialFx.particles(back, run.group, ParticleTypes.SQUID_INK, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 16, 0.5, 0.4, 0.5, 0.02);
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, MURK, 0, false, false, false));
                Notice.bar(player, ModText.deny("message.skyabyss.trial.t08.wrong"));
            }
            TrialRuntime.refresh(back, run);
            return true;
        }
        return false;
    }

    static int placed(S s) {
        int n = 0;
        for (int st : s.state) {
            if (st == 2) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        if (!(run.data.get("s") instanceof S s) || s.r == null) {
            return;
        }
        long now = back.getGameTime();
        // 棚: 穴ごとに、片の色の丈の分の珊瑚の塊
        for (int slot = 0; slot < SLOTS; slot++) {
            int i = pieceAtSlot(s, slot);
            BlockPos base = slot(slot);
            for (int dy = 1; dy <= 3; dy++) {
                BlockState st = Blocks.AIR.defaultBlockState();
                if (i >= 0 && dy <= s.r.pieces().get(i).height()) {
                    st = COLOR_BLOCKS[s.r.pieces().get(i).color()];
                }
                out.accept(base.above(dy), st);
            }
        }
        // 鉢と札
        boolean visible = now < s.visibleUntil;
        BlockState hidden = Blocks.BLACKSTONE.defaultBlockState();
        for (int b = 0; b < 4; b++) {
            boolean active = b < 3 || s.round == 2;
            BlockPos bp = bowl(b);
            out.accept(bp, active ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
            for (int i = 1; i <= 3; i++) {
                out.accept(bp.above(i), i <= Math.min(3, s.bowlCount[b]) ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            BlockPos lp = label(b);
            boolean swapping = b == s.swapA || b == s.swapB;
            for (int dy = 1; dy <= 3; dy++) {
                BlockState st = Blocks.AIR.defaultBlockState();
                int[] lab = s.r.labels()[b];
                if (!active) {
                    st = Blocks.AIR.defaultBlockState();
                } else if (b == 3) {
                    st = dy == 1 ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                } else if (!visible) {
                    st = dy == 1 ? hidden : Blocks.AIR.defaultBlockState();
                } else if (swapping) {
                    st = dy == 1 ? Blocks.CALCITE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                } else {
                    int h = lab[1] < 0 ? 1 : lab[1];
                    if (dy <= h) {
                        st = lab[0] < 0 ? Blocks.WHITE_CONCRETE.defaultBlockState() : COLOR_BLOCKS[lab[0]];
                    }
                }
                out.accept(lp.above(dy), st);
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int s = 0; s < SLOTS; s++) {
            BlockPos p = slot(s);
            if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).isAir() || !back.getBlockState(p.above(3)).isAir()) {
                bad.add("T08 shelf slot " + s + " is missing or covered at " + p);
            }
        }
        for (int b = 0; b < 4; b++) {
            BlockPos p = bowl(b);
            if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).isAir() || back.getBlockState(label(b)).isAir() || !back.getBlockState(label(b).above()).isAir()) {
                bad.add("T08 bowl or label " + b + " is missing or covered");
            }
        }
        if (!back.getBlockState(LECTERN).is(Blocks.LECTERN)) {
            bad.add("T08 lectern missing");
        }
        // どの組・配置・種でも、札は重ならず、片は必ず一つの鉢を指す (三つ目は割れ鉢も出る)
        for (int layout = 0; layout < 3; layout++) {
            for (int round = 0; round < 3; round++) {
                for (long seed = 0; seed < 30; seed++) {
                    int pieces = COUNTS[(int) (seed % 3)][round] + (seed % 7 == 0 ? 2 : 0);
                    Round r = makeRound(new Random(seed), round, pieces, layout);
                    Set<String> seen = new HashSet<>();
                    boolean cracked = false;
                    for (int b = 0; b < 3; b++) {
                        if (!seen.add(r.labels()[b][0] + ":" + r.labels()[b][1])) {
                            bad.add("T08 labels repeat (round " + round + ", layout " + layout + ")");
                            return bad;
                        }
                    }
                    int[] hits = new int[4];
                    for (Piece p : r.pieces()) {
                        int b = bowlFor(round, r.labels(), p);
                        if (b < 0 || b > 3 || b == 3 && round != 2) {
                            bad.add("T08 a piece has no bowl (round " + round + ", layout " + layout + ")");
                            return bad;
                        }
                        hits[b]++;
                        cracked |= b == 3;
                    }
                    if (round == 2 && (!cracked || hits[0] == 0 || hits[1] == 0 || hits[2] == 0)) {
                        bad.add("T08 round 3 does not use every bowl (layout " + layout + ", seed " + seed + ")");
                        return bad;
                    }
                    if (round < 2 && (hits[0] == 0 || hits[1] == 0 || hits[2] == 0)) {
                        bad.add("T08 round " + round + " leaves a bowl empty (seed " + seed + ")");
                        return bad;
                    }
                }
            }
        }
        if (!back.getBlockState(TideDeck.T08_STONE).is(jp.revati.skyabyss.titan.tide.back.TideBackSetup.CHIME_STONE.get())) {
            bad.add("T08 stone missing");
        }
        return bad;
    }
}
