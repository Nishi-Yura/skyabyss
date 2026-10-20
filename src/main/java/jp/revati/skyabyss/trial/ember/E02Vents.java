package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

// E02 噴気の渡り (歩・拍・★★。docs/design/29 の 3.4)。谷を横切る四本の「噴気の帯」(幅 21 を 7 本の筋に分けた、割れた床) を越えて向こうの岸へ渡る。
//   決まりは一つ: 割れ目は、**光ったあと噴く** (光る 1 秒ちょっと → 噴く 1.2 秒 → しずか)。噴いている割れ目に立っていると、熱 +25 と 6 の傷で突き上げられる。
//   帯のあいだの岸には灰溜まりがあり、立つと熱が冷める。落ちても、焼かれても、休み場へ戻るだけ (熱は戻される)。
//   帯ごとに噴き方が違う: ① 全部いっしょ (間をぬけて走る) ② 一筋おき (噴いていない筋へ) ③ 波が走る (波と同じ向きに走る) ④ 半分ずつ。配置 (3 通り) で帯の並びが変わる。
//   一人でも: 割れ目は避けるだけで、仲間は要らない (灰溜まりで熱を冷ませる)。複数なら、先に行った人の「光った」を見て後ろの人が間合いを読める。
//   人数が増えると周期が 5% ずつ縮む (下限 75%。光る長さは変えない)。
// 修飾: 配置 3 (帯の並び)・数字 2 (周期 130・104・84 tick / 予告 28・24・20 tick。予告の最短 16 tick は割らない)・障害 2 (突風・火の粉蛾)・任意の目標 (一度も焼かれずに)
public final class E02Vents implements TrialCell {
    public static final int LANES = 7;
    public static final int BANDS = 4;
    // 帯の北端の z (帯は 3 深さ)。筋 i は x = 14 + 3i 〜 16 + 3i
    public static final int[] BAND_Z = {31, 39, 47, 55};
    public static final int LANE_X0 = 14;
    // 帯ごとの噴き方 (0 いっしょ / 1 一筋おき / 2 波 (右へ) / 3 波 (左へ) / 4 半分ずつ) を、配置ごとに
    public static final int[][] PATTERN = {{0, 0, 1, 2}, {0, 1, 3, 2}, {1, 0, 2, 4}};
    static final int[] PERIOD = {130, 104, 84};
    static final int[] WARN = {28, 24, 20};
    // 始めの静けさ (tick)
    static final int LEAD = 60;
    // 突風
    static final int GUST_EVERY = 150;
    static final int GUST_WARN = 20;

    private final Def def = TrialCatalog.get("E02");
    private final Ports ports = new Ports(EmberDeck.E02_GATE, false, List.of(), EmberDeck.E02_STONE, EmberDeck.E02_PAD, "k0", EmberDeck.A.aabb(),
            List.of(EmberCellKit.rest("k_e02a"), EmberCellKit.rest("k_e02b")));

    public static int cx(int lane) {
        return LANE_X0 + 3 * lane + 1;
    }

    public static int cz(int band) {
        return BAND_Z[band] + 1;
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // 筋 lane のずれ (tick)。period = 周期
    static long offset(int pattern, int lane, int period) {
        return switch (pattern) {
            case 0 -> 0;
            case 1 -> (lane % 2) * (long) period / 2;
            case 2 -> lane * (long) period / LANES;
            case 3 -> (LANES - 1 - lane) * (long) period / LANES;
            default -> lane < 3 ? 0 : lane == 3 ? period / 4 : period / 2;
        };
    }

    public static int periodOf(Mods m, int entrants) {
        int base = jp.revati.skyabyss.abyss.Abyss.shorter(m, PERIOD[m.number(0)]);
        double scale = Math.max(0.75, 1.0 - 0.05 * (entrants - 1));
        return Math.max(70, (int) Math.round(base * scale));
    }

    public static int warnOf(Mods m) {
        int w = WARN[m.number(1)];
        return Math.max(EmberCellKit.WARN_MIN, m.abyss() ? (int) Math.round(w / jp.revati.skyabyss.abyss.Abyss.NUMBER_STEP) : w);
    }

    public static int phaseAt(Run run, int band, int lane, long now) {
        int pattern = PATTERN[run.mods.layout()][band];
        int period = run.getInt("period");
        return EmberCellKit.phase(now, run.getLong("base"), period, offset(pattern, lane, period), warnOf(run.mods));
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("entrants", 1);
        run.put("period", periodOf(run.mods, 1));
        run.put("base", back.getGameTime() + LEAD);
        run.put("states", new int[BANDS * LANES]);
        run.put("hit", new HashSet<String>());
        run.put("gustAt", back.getGameTime() + 160L);
        run.put("gustDir", 1);
        run.put("mobs", new ArrayList<UUID>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void disarm(ServerLevel back, Run run) {
        List<UUID> mobs = run.get("mobs", () -> new ArrayList<UUID>());
        for (UUID id : mobs) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        mobs.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e02.start"));
            spawnMoths(back, run, inside.size());
        }
        // 人数が増えたら周期を縮める (組み直しのあいだ 2 秒しずか)
        int n = EmberCellKit.entrants(inside.size());
        if (n > run.getInt("entrants")) {
            run.put("entrants", n);
            run.put("period", periodOf(run.mods, n));
            run.put("base", now + 40L);
        }
        int[] states = run.get("states", () -> new int[BANDS * LANES]);
        Set<String> hit = run.get("hit", HashSet::new);
        boolean changed = false;
        Set<String> sounded = new HashSet<>();
        for (int b = 0; b < BANDS; b++) {
            for (int l = 0; l < LANES; l++) {
                int s = phaseAt(run, b, l, now);
                int i = b * LANES + l;
                if (s != states[i]) {
                    states[i] = s;
                    changed = true;
                    // 同じ帯の同じずれは音を一度だけ
                    int period = run.getInt("period");
                    String key = b + ":" + offset(PATTERN[run.mods.layout()][b], l, period) + ":" + s;
                    if (s > 0 && sounded.add(key)) {
                        EmberCellKit.ventFx(back, run.group, cx(l), Y, cz(b), s);
                    }
                }
                if (s == 2) {
                    EmberCellKit.ventColumn(back, run.group, cx(l), Y, cz(b));
                    int period = run.getInt("period");
                    long idx = EmberCellKit.burstIndex(now, run.getLong("base"), period, offset(PATTERN[run.mods.layout()][b], l, period));
                    for (ServerPlayer p : inside) {
                        if (!p.isAlive() || !EmberCellKit.over(p, cx(l), Y, cz(b))) {
                            continue;
                        }
                        if (hit.add(p.getUUID() + ":" + i + ":" + idx)) {
                            EmberCellKit.burn(back, run, p, cx(l), cz(b));
                            if (run.mods.goal() == 0) {
                                run.goalOk = false;
                            }
                            Notice.bar(p, ModText.deny("message.skyabyss.trial.e02.burned"));
                        }
                    }
                }
            }
        }
        if (hit.size() > 400) {
            hit.clear();
        }
        if (changed) {
            TrialRuntime.refresh(back, run);
        }
        // 突風 (障害): 予告の粒 → 帯のあたりの人を横へ押す
        if (run.mods.hasHazard(0) && !run.cleared) {
            long gustAt = run.getLong("gustAt");
            int dir = run.getInt("gustDir");
            if (now >= gustAt - GUST_WARN && now < gustAt) {
                for (ServerPlayer p : inside) {
                    if (inBands(p)) {
                        TrialFx.particles(back, run.group, ParticleTypes.CLOUD, p.getX() - dir * 1.4, p.getY() + 1, p.getZ(), 3, 0.1, 0.4, 0.4, 0.05 * dir);
                    }
                }
            } else if (now >= gustAt) {
                for (ServerPlayer p : inside) {
                    if (inBands(p)) {
                        p.setDeltaMovement(p.getDeltaMovement().add(0.5 * dir, 0.1, 0));
                        p.hurtMarked = true;
                    }
                }
                run.put("gustAt", now + GUST_EVERY + new Random(run.seed ^ now).nextInt(40));
                run.put("gustDir", -dir);
            }
        }
        if (run.mods.goal() == 0 && run.getInt("burns") > 0) {
            run.goalOk = false;
        }
        // 向こうの岸に着いた
        if (!run.cleared) {
            for (ServerPlayer p : inside) {
                if (p.getZ() >= BAND_Z[BANDS - 1] + 4 && p.getY() >= Y + 0.9 && p.onGround()) {
                    run.cleared = true;
                    run.clearedAt = now;
                    TrialFx.sound(back, run.group, EmberDeck.E02_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
                    TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.e02.crossed"));
                    TrialRuntime.refresh(back, run);
                    break;
                }
            }
        }
    }

    private static boolean inBands(ServerPlayer p) {
        return p.getZ() >= BAND_Z[0] - 2 && p.getZ() < BAND_Z[BANDS - 1] + 4 && p.getY() > Y;
    }

    // 火の粉蛾 (障害): 帯のあいだの岸を漂う。触れると熱 +8 (EmberMoth のふるまい)
    @SuppressWarnings("unchecked")
    private void spawnMoths(ServerLevel back, Run run, int players) {
        if (!run.mods.hasHazard(1)) {
            return;
        }
        List<UUID> mobs = run.get("mobs", () -> new ArrayList<UUID>());
        int count = 2 + (players >= 4 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            Mob m = EmberCellKit.spawn(back, run.group, EmberMobSetup.EMBER_MOTH.get(), 20.5 + i * 4, Y + 3, BAND_Z[1 + i % 2] - 2.5);
            if (m != null) {
                mobs.add(m.getUUID());
            }
        }
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int[] states = run.get("states", () -> new int[BANDS * LANES]);
        for (int b = 0; b < BANDS; b++) {
            for (int l = 0; l < LANES; l++) {
                EmberCellKit.patchOverlay(cx(l), Y, cz(b), states[b * LANES + l], out);
            }
        }
        EmberCellKit.padGlow(ports.pad(), run.cleared, out);
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int b = 0; b < BANDS; b++) {
            for (int l = 0; l < LANES; l++) {
                BlockPos c = new BlockPos(cx(l), Y, cz(b));
                if (!back.getBlockState(c).is(EmberTrialSetup.HEAT_VENT.get())) {
                    bad.add("E02 vent block missing at " + c);
                    return bad;
                }
                if (!EmberDeck.A.contains(c.getX() - 1, Y + 1, c.getZ() - 1) || !EmberDeck.A.contains(c.getX() + 1, Y + 1, c.getZ() + 1)) {
                    bad.add("E02 patch outside the cell at " + c);
                }
            }
        }
        // 渡れる間 (周期 − 光る − 噴く) は、どの段・深淵・6 人でも 24 tick (1.2 秒) 以上
        for (int level = 0; level < 3; level++) {
            for (boolean abyss : new boolean[] {false, true}) {
                Mods m = new Mods(0, new int[] {level, level}, -1, new int[0], -1, jp.revati.skyabyss.trial.Trial.Extreme.NONE, false,
                        abyss ? jp.revati.skyabyss.trial.Trial.Rarity.ABYSS : jp.revati.skyabyss.trial.Trial.Rarity.COMMON);
                int window = periodOf(m, 6) - warnOf(m) - EmberCellKit.ERUPT;
                if (window < 24 || warnOf(m) < EmberCellKit.WARN_MIN) {
                    bad.add("E02 level " + level + (abyss ? " abyss" : "") + ": the safe window is " + window + " ticks (or the warning is under 0.8 s)");
                }
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(EmberDeck.E02_STONE))) {
            bad.add("E02 stone missing");
        }
        return bad;
    }
}
