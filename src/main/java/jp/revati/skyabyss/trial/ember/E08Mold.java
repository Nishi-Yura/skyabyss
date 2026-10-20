package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup;
import jp.revati.skyabyss.titan.group.Groups;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// E08 鋳型の間 (智・解く・★★★。docs/design/29 の 3.4)。炉の口 (奥の注ぎ口) が、溶けた銅の色を順に見せる。色は三つ (赤・黄・白)。
//   決まりは一つ: **見せられた色の順を覚え、同じ色の鋳型へ注ぐ**。見返すのは注ぎ口を右クリック (少し待つ)。
//   違う鋳型へ注ぐと、熱が上がって 3 秒止まり、最初の示しから組み直す (熱 +15 は注いだ人)。
//   ひねり: ① 印のついた示しは、同じ色ではなく「次の色」の鋳型へ (配置ごとに一つ)・② 鋳型の並びが配置ごとに変わる (色で覚える)・
//   ③ 湯気 (障害): 鋳型の前の人は、湯気で熱が少しずつ上がる。
//   一人でも: 注ぎ口を見て覚え、鋳型へ戻って注ぐ。人数が四人から、示しが一つ増える。
// 修飾: 配置 3 (鋳型の並び・印の示し)・数字 2 (示しの数 3・4・5 / 色ごとの時間 30・24・18 tick)・障害 (湯気)・任意の目標 (一度で)
public final class E08Mold implements TrialCell {
    public static final int COLORS = 3;
    // 色 (0 赤・1 黄・2 白) の鋳型の x (配置ごと)。鋳型は z = 15、注ぎ口は (29, 8)
    static final int[][] MOLD_X = {{25, 29, 33}, {33, 25, 29}, {29, 33, 25}};
    public static final int MOLD_Z = 15;
    public static final int SPOUT_X = 29;
    public static final int SPOUT_Z = 8;
    static final int MAX_STEPS = 5;
    static final int[] STEPS = {3, 4, 5};
    static final int[] SHOW_TICKS = {30, 24, 18};
    static final int GAP_TICKS = 10;
    static final int LOCK = 60;
    static final int RESHOW_WAIT = 80;
    static final int STEAM_EVERY = 60;
    static final int STEAM_MIN_Z = 12;

    static final net.minecraft.world.level.block.Block[] COLOR_BLOCK = {Blocks.MAGMA_BLOCK, Blocks.OCHRE_FROGLIGHT, Blocks.SEA_LANTERN};

    private final Def def = TrialCatalog.get("E08");
    private final Ports ports = new Ports(EmberDeck.E08_GATE, false, List.of(), EmberDeck.E08_STONE, EmberDeck.E08_PAD, "k_e08", EmberDeck.NE.aabb(),
            List.of(EmberCellKit.rest("k_e08")));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    public static BlockPos moldAt(int layout, int colour) {
        return new BlockPos(MOLD_X[layout][colour], Y + 1, MOLD_Z);
    }

    public static BlockPos spout() {
        return new BlockPos(SPOUT_X, Y + 1, SPOUT_Z);
    }

    public static int steps(Run run, int players) {
        int n = STEPS[run.mods.number(0)] + (EmberCellKit.entrants(players) >= 4 ? 1 : 0);
        return Math.min(MAX_STEPS, n);
    }

    static int showTicks(Run run) {
        return SHOW_TICKS[run.mods.number(1)];
    }

    // 示しの列 (色の番号)。試練が始まったときに種で決める (同じ周・同じ種では同じ列)
    static int[] sequence(Run run, int n) {
        int[] out = new int[n];
        long s = run.seed;
        for (int i = 0; i < n; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            out[i] = (int) Math.floorMod(s >>> 33, (long) COLORS);
        }
        return out;
    }

    // 印のついた示し (配置ごと): 配置 1 は二つめ、配置 2 は最後
    static int markedStep(Run run, int n) {
        return switch (run.mods.layout()) {
            case 1 -> 1;
            case 2 -> n - 1;
            default -> -1;
        };
    }

    // 示しの k 番目で注ぐべき色 (印のついた示しは次の色)
    static int expected(Run run, int[] seq, int k) {
        int c = seq[k];
        return k == markedStep(run, seq.length) ? (c + 1) % COLORS : c;
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("k", 0);
        run.put("lockUntil", 0L);
        run.put("wrong", 0);
        run.put("showAt", back.getGameTime() + 40L);
        run.put("reshowAt", 0L);
        run.put("shown", -2);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
    }

    // いま示している色 (-1 = 示していない) と、印の有無
    static int showing(Run run, long now, int n) {
        long t = now - run.getLong("showAt");
        if (t < 0) {
            return -1;
        }
        int period = showTicks(run) + GAP_TICKS;
        int slot = (int) (t / period);
        if (slot >= n || t % period >= showTicks(run)) {
            return -1;
        }
        return slot;
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e08.start"));
        }
        if (run.cleared) {
            return;
        }
        int n = steps(run, inside.size());
        int[] seq = run.get("seq", () -> sequence(run, n));
        if (seq.length != n) {
            seq = sequence(run, n);
            run.put("seq", seq);
        }
        // 示しの見え方が変わったら、画面を組み直す
        int slot = showing(run, now, n);
        int shown = slot < 0 ? -1 : seq[slot];
        int mark = slot >= 0 && slot == markedStep(run, n) ? 1 : 0;
        int key = shown * 4 + mark + 100;
        if (key != run.getInt("shown")) {
            run.put("shown", key);
            TrialRuntime.refresh(back, run);
            if (shown >= 0) {
                TrialFx.sound(back, run.group, spout(), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 0.6f + 0.2f * shown);
            }
        }
        // 湯気 (障害): 鋳型の前の人は、少しずつ熱くなる
        if (run.mods.hasHazard(0) && now % STEAM_EVERY == 0) {
            for (ServerPlayer p : inside) {
                if (p.getZ() >= STEAM_MIN_Z && p.isAlive()) {
                    EmberHeat.add(p, 3f, EmberHeat.Reason.OTHER);
                    TrialFx.particles(back, run.group, ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 4, 0.4, 0.3, 0.4, 0.02);
                }
            }
        }
    }

    // 右クリック: 注ぎ口 = もう一度見る。鋳型 = 注ぐ
    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        if (run.cleared) {
            return false;
        }
        if (pos.equals(spout())) {
            if (now < run.getLong("reshowAt")) {
                TrialFx.bar(List.of(player), ModText.hint("message.skyabyss.trial.e08.wait"));
                return true;
            }
            run.put("reshowAt", now + RESHOW_WAIT);
            run.put("showAt", now + 10L);
            run.put("k", 0);
            TrialRuntime.refresh(back, run);
            TrialFx.bar(List.of(player), ModText.hint("message.skyabyss.trial.e08.again"));
            return true;
        }
        int colour = -1;
        for (int c = 0; c < COLORS; c++) {
            if (pos.equals(moldAt(run.mods.layout(), c))) {
                colour = c;
            }
        }
        if (colour < 0) {
            return false;
        }
        if (now < run.getLong("lockUntil")) {
            TrialFx.bar(List.of(player), ModText.deny("message.skyabyss.trial.e08.locked", (int) Math.ceil((run.getLong("lockUntil") - now) / 20.0)));
            return true;
        }
        int[] seq = run.get("seq", () -> sequence(run, steps(run, 1)));
        int k = run.getInt("k");
        if (k >= seq.length) {
            return true;
        }
        if (showing(run, now, seq.length) >= 0) {
            TrialFx.bar(List.of(player), ModText.hint("message.skyabyss.trial.e08.wait_show"));
            return true;
        }
        if (colour == expected(run, seq, k)) {
            run.put("k", k + 1);
            if (k + 1 >= seq.length) {
                run.cleared = true;
                run.clearedAt = now;
                TrialFx.sound(back, run.group, EmberDeck.E08_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
                TrialFx.bar(Groups.online(back, run.group), ModText.progress("message.skyabyss.trial.e08.cleared"));
                TrialRuntime.refresh(back, run);
            } else {
                TrialFx.bar(List.of(player), ModText.progress("message.skyabyss.trial.e08.poured", k + 1, seq.length));
                TrialRuntime.refresh(back, run);
            }
        } else {
            // 違う鋳型: 熱 +15 (注いだ人)・3 秒止まる・示しを最初から組み直す
            EmberHeat.add(player, 15f, EmberHeat.Reason.OTHER);
            run.put("lockUntil", now + LOCK);
            run.put("k", 0);
            run.put("showAt", now + LOCK);
            run.put("wrong", run.getInt("wrong") + 1);
            if (run.mods.goal() == 0) {
                run.goalOk = false;
            }
            TrialFx.sound(back, run.group, spout(), EmberTrialSetup.VENT_HISS.get(), 1.0f, 0.8f);
            TrialFx.bar(List.of(player), ModText.deny("message.skyabyss.trial.e08.wrong"));
            TrialRuntime.refresh(back, run);
        }
        return true;
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int l = run.mods.layout();
        for (int c = 0; c < COLORS; c++) {
            out.accept(moldAt(l, c), COLOR_BLOCK[c].defaultBlockState());
        }
        int shown = run.getInt("shown") - 100;
        if (shown >= 0) {
            int color = shown / 4;
            int mark = shown % 4;
            out.accept(spout(), COLOR_BLOCK[color].defaultBlockState());
            if (mark == 1) {
                out.accept(new BlockPos(SPOUT_X, Y + 2, SPOUT_Z), Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        EmberCellKit.padGlow(ports.pad(), run.cleared, out);
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < MOLD_X.length; l++) {
            boolean[] seen = new boolean[NUM_SLOTS];
            for (int x : MOLD_X[l]) {
                if (x < EmberDeck.NE.minX() || x > EmberDeck.NE.maxX()) {
                    bad.add("E08 mold outside the room at layout " + l);
                }
                seen[(x - 25) / 4] = true;
            }
            for (boolean b : seen) {
                if (!b) {
                    bad.add("E08 molds do not fill the three places at layout " + l);
                    break;
                }
            }
        }
        for (int c = 0; c < COLORS; c++) {
            if (back.getBlockState(moldAt(0, c)).isAir()) {
                bad.add("E08 mold " + c + " is missing");
            }
        }
        if (!back.getBlockState(EmberDeck.E08_STONE).is(jp.revati.skyabyss.titan.ember.back.EmberBackSetup.KINDLE_STONE.get())) {
            bad.add("E08 stone missing");
        }
        return bad;
    }

    static final int NUM_SLOTS = 3;
}
