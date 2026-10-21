package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.ember.back.EmberBackSetup;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.group.GroupGates;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// E06 煙突の風読み (智・解く・★。docs/design/29 の 3.4)。広間に八本の煙突が立つ。煙の傾きを読んで、「抜け口」の煙突の蓋を右クリックで開ける。
//   決まりは一つ: **煙は抜け口へ傾き、抜け口の煙はまっすぐ昇る**。第 1 の間は一列の四本 (まっすぐな一本が抜け口)。
//   ひねり ① 第 2 の間から、よどんだ煙突も まっすぐ昇る。まっすぐな煙突が二つ以上あるときは、みなが傾く先が抜け口。
//   ひねり ② 逆風の間 (第 3 の間から。蓋のそばの合図と帯の文で知らせる): 煙は抜け口から**逃げる**。みなが傾く向きの反対が抜け口。
//   冷えた煙突 (煙の出ない煙突) は数に入れない。間違えた蓋は熱を噴き (熱 +20)、3 秒は次の蓋が開かない。間違えても進める (任意の目標は崩れる)。
//   間を重ねて、最後の抜け口を開けると炉の扉が開く。一人でも蓋は一つずつ開けるだけで、仲間は要らない。人数が増えると煙突が増える (3 人で +1・5 人で +2。上限 8)。
// 修飾: 配置 3 (煙突の位置の入れ替え)・数字 2 (煙突の数 5・6・8 / 間の数 2・3・4)・障害 (突風: 煙が 3 秒乱れる)・任意の目標 (間違えずに)
public final class E06Flues implements TrialCell {
    // 区画の写しで動かせる (決まりは arm・tick・onUse・disarm・overlay の中だけで座標を使う。docs/design/32 の 5 章)
    @Override
    public boolean relocatable() {
        return true;
    }

    public static final int SITES = 8;
    // 煙突の位置: 手前の列 (z 33) と奥の列 (z 43)。八本とも常に立っている (配置は、第 1 の間にどの四本が煙を出すか)
    public static final int[] SITE_X = {-31, -26, -22, -17};
    public static final int[] ROW_Z = {33, 43};
    // 配置ごとの第 1 の間の四本 (0 手前の列 / 1 奥の列 / 2 真ん中の四角)
    static final int[][] FIRST = {{0, 1, 2, 3}, {4, 5, 6, 7}, {1, 2, 5, 6}};
    // 柱 y 120〜123 + 蓋 y 124
    public static final int PILLAR = 4;
    static final int[] FLUES = {5, 6, 8};
    static final int[] ROUNDS = {2, 3, 4};
    static final int WRONG_HEAT = 20;
    static final int LOCK = 60;
    static final int GUST_EVERY = 260;
    static final int GUST_LEN = 60;
    static final int GUST_WARN = 20;

    private final Def def = TrialCatalog.get("E06");
    private final Ports ports = new Ports(EmberDeck.E06_GATE, false, EmberDeck.E06_EXIT, EmberDeck.E06_STONE, EmberDeck.E06_PAD, "k0", EmberDeck.C.aabb(),
            List.of(EmberCellKit.rest("k_e06")));

    public static int siteX(int layout, int site) {
        return SITE_X[site % 4];
    }

    public static int siteZ(int site) {
        return ROW_Z[site / 4];
    }

    public static BlockPos lid(int layout, int site) {
        return new BlockPos(siteX(layout, site), Y + PILLAR + 1, siteZ(site));
    }

    // 八本の煙突の足もと (建てるとき)
    public static List<BlockPos> union() {
        List<BlockPos> out = new ArrayList<>();
        for (int s = 0; s < SITES; s++) {
            out.add(new BlockPos(siteX(0, s), Y, siteZ(s)));
        }
        return List.copyOf(out);
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // 蓋と柱を右クリックできる (配置によらず全部。その配置で使わない位置は「冷えている」)
    @Override
    public Set<BlockPos> interactive() {
        Set<BlockPos> set = new HashSet<>();
        for (BlockPos base : union()) {
            for (int y = Y + 1; y <= Y + PILLAR + 1; y++) {
                set.add(new BlockPos(base.getX(), y, base.getZ()));
            }
        }
        return set;
    }

    public static int rounds(Run run) {
        int r = jp.revati.skyabyss.abyss.Abyss.plusOne(run.mods, ROUNDS[run.mods.number(1)]);
        return run.mods.quiet() ? Math.max(2, r - 1) : r;
    }

    // 第 round 間の向き (0 傾く / 1 逃げる)。2 間 = 順・逆、3 間 = 順・順・逆、4 間以上 = 順・順・逆・逆
    static int modeOf(int round, int rounds) {
        if (rounds <= 2) {
            return round >= 2 ? 1 : 0;
        }
        return round >= 3 ? 1 : 0;
    }

    static int decoysOf(int round, int rounds) {
        if (round <= 1) {
            return 0;
        }
        return round >= rounds && rounds >= 3 ? 2 : 1;
    }

    static int countOf(Run run, int round, int entrants) {
        if (round <= 1) {
            return 4;
        }
        int extra = (entrants >= 3 ? 1 : 0) + (entrants >= 5 ? 1 : 0);
        return Math.min(SITES, FLUES[run.mods.number(0)] + extra);
    }

    // 第 round 間を作る (どの煙突が生きているか・抜け口・よどんだ煙突)。同じ種なら同じ
    static void setup(Run run, int round, int entrants) {
        Random rng = new Random(run.seed * 31 + round * 0x9E3779B97F4A7C15L);
        boolean[] active = new boolean[SITES];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < SITES; i++) {
            order.add(i);
        }
        int count = countOf(run, round, entrants);
        if (round <= 1) {
            for (int i : FIRST[run.mods.layout()]) {
                active[i] = true;
            }
        } else {
            Collections.shuffle(order, rng);
            for (int i = 0; i < count; i++) {
                active[order.get(i)] = true;
            }
        }
        List<Integer> live = new ArrayList<>();
        for (int i = 0; i < SITES; i++) {
            if (active[i]) {
                live.add(i);
            }
        }
        int prev = run.getInt("flue") - 1;
        int flue;
        do {
            flue = live.get(rng.nextInt(live.size()));
        } while (flue == prev && live.size() > 1);
        boolean[] decoy = new boolean[SITES];
        int wantDecoys = Math.min(decoysOf(round, rounds(run)), live.size() - 4);
        List<Integer> rest = new ArrayList<>(live);
        rest.remove(Integer.valueOf(flue));
        Collections.shuffle(rest, rng);
        for (int i = 0; i < wantDecoys; i++) {
            decoy[rest.get(i)] = true;
        }
        run.put("active", active);
        run.put("decoy", decoy);
        run.put("flue", flue + 1);
        run.put("round", round);
        run.put("mode", modeOf(round, rounds(run)));
        run.put("opened", 0);
        run.put("wrongSite", 0);
        run.put("wrongUntil", 0L);
        run.put("pauseUntil", 0L);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("entrants", 1);
        setup(run, 1, 1);
        run.put("view", -1);
        run.put("nextGust", back.getGameTime() + 200L);
        run.put("gustUntil", 0L);
        run.put("lockUntil", 0L);
        if (run.cleared) {
            GroupGates.open(back, run.group, EmberDeck.E06_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        if (!run.cleared) {
            GroupGates.release(back, run.group, EmberDeck.E06_EXIT);
        }
    }

    // 煙の傾く向き (単位ベクトルの x, z)。抜け口・よどんだ煙突はまっすぐ (0, 0)。突風のあいだはでたらめ
    public static double[] lean(Run run, int site, long now) {
        boolean[] decoy = run.get("decoy", () -> new boolean[SITES]);
        int flue = run.getInt("flue") - 1;
        if (site == flue || decoy[site]) {
            return new double[] {0, 0};
        }
        int layout = run.mods.layout();
        if (now < run.getLong("gustUntil")) {
            double a = new Random(run.seed + site * 977L + now / 10).nextDouble() * Math.PI * 2;
            return new double[] {Math.cos(a), Math.sin(a)};
        }
        double dx = siteX(layout, flue) - siteX(layout, site);
        double dz = siteZ(flue) - siteZ(site);
        double len = Math.max(1e-6, Math.hypot(dx, dz));
        double sign = run.getInt("mode") == 0 ? 1 : -1;
        return new double[] {sign * dx / len, sign * dz / len};
    }

    private static String ruleOf(Run run) {
        int round = run.getInt("round");
        int mode = run.getInt("mode");
        if (mode == 1) {
            return "away";
        }
        return round <= 1 ? "first" : "decoy";
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int n = EmberCellKit.entrants(inside.size());
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            run.put("entrants", n);
            setup(run, 1, n);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e06.rule.first"));
        }
        if (n > run.getInt("entrants")) {
            run.put("entrants", n);
        }
        if (run.cleared && run.getLong("pauseUntil") == 0) {
            smoke(back, run, now);
            return;
        }
        // 間の切り替え: 正しい蓋を開けて少しのち
        long pauseUntil = run.getLong("pauseUntil");
        if (pauseUntil > 0 && now >= pauseUntil) {
            int round = run.getInt("round");
            if (round >= rounds(run)) {
                run.put("pauseUntil", 0L);
                run.cleared = true;
                run.clearedAt = now;
                GroupGates.open(back, run.group, EmberDeck.E06_EXIT);
                TrialFx.sound(back, run.group, EmberDeck.E06_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.8f);
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.e06.done"));
            } else {
                setup(run, round + 1, run.getInt("entrants"));
                TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e06.rule." + ruleOf(run)));
                TrialFx.sound(back, run.group, EmberDeck.E06_STONE, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.2f);
            }
        }
        // 突風 (障害): 予告 → 3 秒、煙が乱れる
        if (run.mods.hasHazard(0) && !run.cleared && run.getLong("pauseUntil") == 0) {
            long next = run.getLong("nextGust");
            if (now >= next - GUST_WARN && now < next) {
                TrialFx.particles(back, run.group, ParticleTypes.CLOUD, -24, Y + 4, siteZ(0) + 5, 8, 8, 0.5, 4, 0.03);
                if (now >= next - GUST_WARN && now < next - GUST_WARN + 5) {
                    TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.e06.gust_warn"));
                }
            } else if (now >= next) {
                run.put("gustUntil", now + GUST_LEN);
                run.put("nextGust", now + GUST_LEN + GUST_EVERY);
                TrialFx.sound(back, run.group, EmberDeck.E06_STONE.offset(0, 0, -30), SoundEvents.PHANTOM_FLAP, 1.0f, 0.7f);
            }
        }
        smoke(back, run, now);
        // 見かけ (蓋) が変わったら送る
        int view = (run.getInt("round") << 16) ^ (run.getInt("opened") << 8) ^ (run.getInt("wrongSite") << 4) ^ (now < run.getLong("wrongUntil") ? 1 : 0)
                ^ (run.cleared ? 1 << 20 : 0) ^ (run.getInt("flue") << 24);
        if (view != run.getInt("view")) {
            run.put("view", view);
            TrialRuntime.refresh(back, run);
        }
        if (run.getInt("wrongSite") > 0 && now >= run.getLong("wrongUntil")) {
            run.put("wrongSite", 0);
        }
    }

    // 煙: 生きている煙突から、傾いた柱のように粒を出す
    private void smoke(ServerLevel back, Run run, long now) {
        if (now % 10 != 0 && now % 10 != 5) {
            return;
        }
        boolean[] active = run.get("active", () -> new boolean[SITES]);
        int layout = run.mods.layout();
        int opened = run.getInt("opened") - 1;
        for (int s = 0; s < SITES; s++) {
            if (!active[s] || s == opened) {
                continue;
            }
            double[] d = lean(run, s, now);
            double x = siteX(layout, s) + 0.5;
            double z = siteZ(s) + 0.5;
            double y0 = Y + PILLAR + 2.1;
            for (int k = 0; k < 4; k++) {
                double h = k * 0.95;
                double off = 0.55 * k * (k + 1) / 2.0;
                TrialFx.particles(back, run.group, k % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x + d[0] * off, y0 + h, z + d[1] * off, 1, 0.05, 0.02,
                        0.05, 0.0);
            }
        }
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        int layout = run.mods.layout();
        int site = -1;
        for (int s = 0; s < SITES; s++) {
            if (siteX(layout, s) == pos.getX() && siteZ(s) == pos.getZ()) {
                site = s;
                break;
            }
        }
        if (site < 0) {
            // その配置では使わない位置の煙突
            Notice.bar(player, ModText.hint("message.skyabyss.trial.e06.cold"));
            return true;
        }
        boolean[] active = run.get("active", () -> new boolean[SITES]);
        if (run.cleared || run.getLong("pauseUntil") > 0) {
            return true;
        }
        if (!active[site]) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial.e06.cold"));
            return true;
        }
        if (now < run.getLong("gustUntil")) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial.e06.gust_now"));
            return true;
        }
        if (now < run.getLong("lockUntil")) {
            Notice.bar(player, ModText.deny("message.skyabyss.trial.e06.locked", (int) Math.ceil((run.getLong("lockUntil") - now) / 20.0)));
            return true;
        }
        int flue = run.getInt("flue") - 1;
        boolean[] decoy = run.get("decoy", () -> new boolean[SITES]);
        if (site == flue) {
            run.put("opened", site + 1);
            run.put("pauseUntil", now + 50L);
            TrialFx.sound(back, run.group, lid(layout, site), SoundEvents.BELL_BLOCK, 1.5f, 1.1f);
            TrialFx.particles(back, run.group, ParticleTypes.FLAME, siteX(layout, site) + 0.5, Y + PILLAR + 2.2, siteZ(site) + 0.5, 12, 0.3, 0.5, 0.3, 0.04);
            int round = run.getInt("round");
            for (ServerPlayer p : Groups_online(back, run)) {
                Notice.bar(p, round >= rounds(run) ? ModText.progress("message.skyabyss.trial.e06.last") : ModText.progress("message.skyabyss.trial.e06.right", round, rounds(run)));
            }
            return true;
        }
        run.put("mistakes", run.getInt("mistakes") + 1);
        if (run.mods.goal() == 0) {
            run.goalOk = false;
        }
        run.put("lockUntil", now + LOCK);
        run.put("wrongSite", site + 1);
        run.put("wrongUntil", now + LOCK);
        EmberHeat.add(player, WRONG_HEAT, EmberHeat.Reason.OTHER);
        TrialFx.sound(back, run.group, lid(layout, site), SoundEvents.FIRECHARGE_USE, 1.2f, 0.6f);
        TrialFx.particles(back, run.group, ParticleTypes.LAVA, siteX(layout, site) + 0.5, Y + PILLAR + 2, siteZ(site) + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
        Notice.bar(player, decoy[site] ? ModText.deny("message.skyabyss.trial.e06.decoy") : ModText.deny("message.skyabyss.trial.e06.wrong"));
        TrialRuntime.refresh(back, run);
        return true;
    }

    private static List<ServerPlayer> Groups_online(ServerLevel back, Run run) {
        return jp.revati.skyabyss.titan.group.Groups.online(back, run.group);
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        boolean[] active = run.get("active", () -> new boolean[SITES]);
        int layout = run.mods.layout();
        int opened = run.getInt("opened") - 1;
        int wrong = run.getInt("wrongSite") - 1;
        for (int s = 0; s < SITES; s++) {
            BlockPos lid = lid(layout, s);
            if (s == opened) {
                out.accept(lid, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            } else if (s == wrong) {
                out.accept(lid, Blocks.MAGMA_BLOCK.defaultBlockState());
            } else if (!active[s]) {
                // 冷えた煙突: 蓋が石の壁になる (煙が出ない)
                out.accept(lid, Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState());
            }
        }
        EmberCellKit.padGlow(ports.pad(), run.cleared, out);
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (BlockPos base : union()) {
            for (int y = Y + 1; y <= Y + PILLAR + 1; y++) {
                if (back.getBlockState(new BlockPos(base.getX(), y, base.getZ())).isAir()) {
                    bad.add("E06 chimney block missing at " + base.getX() + "," + y + "," + base.getZ());
                    return bad;
                }
            }
            if (!EmberDeck.C.contains(base.getX(), Y + 1, base.getZ())) {
                bad.add("E06 chimney outside the hall at " + base);
            }
        }
        // 煙突どうしが 4 ブロック以上離れている (煙が読める)
        for (int a = 0; a < SITES; a++) {
            for (int b = a + 1; b < SITES; b++) {
                if (Math.hypot(siteX(0, a) - siteX(0, b), siteZ(a) - siteZ(b)) < 4) {
                    bad.add("E06 chimneys " + a + " and " + b + " are closer than 4");
                }
            }
        }
        for (int[] first : FIRST) {
            if (first.length != 4) {
                bad.add("E06 the first round must have four chimneys");
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(EmberDeck.E06_STONE))) {
            bad.add("E06 stone missing");
        }
        return bad;
    }
}
