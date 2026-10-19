package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.back.GaleFeather;
import jp.revati.skyabyss.titan.storm.back.StormBackClock;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormDeck;
import jp.revati.skyabyss.titan.storm.back.StormDeck.Island;
import jp.revati.skyabyss.titan.storm.back.Updraft;
import jp.revati.skyabyss.titan.storm.mob.StaticMoth;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// S02 気嚢の滑空 (歩・拍・★★。docs/design/29 の 3.3)。尾の上の空に浮かぶ気嚢の島を、滑空で渡る。
//   決まり (教える): 気嚢は「膨らんでいる」あいだだけ昇り風を吹く。光る気嚢の真ん中に立つと浮く。浮いたらしゃがみ (Shift) で滑空して、次の気嚢へ。
//     膨らむ前は黄色く点滅する。膨らんでいない気嚢にも立てる (萎んでいるあいだは待つだけ)。始めの台の気嚢だけは、いつも膨らんでいる (見本)。
//   ひねり: 中ほどの台のあと、気嚢は小さく (5x5)・膨らむ窓が短くなる。配置 (気嚢が膨らむ順) は周回で変わる:
//     0 = 追い波 (進む向きに膨らみが走る。飛んだらちょうど次が膨らむ)、1 = 向かい波 (向こうから膨らむ。着いた気嚢は萎んでいて、待つ)、2 = まだら
//   拍 (凪・嵐) は昇り風の高さだけを変える (凪 4・嵐 13。凪でも全部の気嚢は渡れる。嵐は遠くまで滑空できる)。
//   足を踏み外したら、直前の休み場へ戻される。向こうの台に着けば済み (石は台の端)。
// 人数: 膨らんでいる窓が、人が増えるほど長い (一人 +0%・二人 +12%… 五人で +48%)。みなが同じ窓に乗れるように
// 修飾: 配置 3・数字 2 (周期 200/160/130・窓 62%/48%/36%)・障害 2 (横風・雷蛾)・任意の目標 (一度も落ちずに)
public final class S02Sacs extends StormCell implements StormCell.PerTick {
    static final int[] PERIOD = {200, 160, 130};
    static final double[] WINDOW = {0.62, 0.48, 0.36};
    // 膨らむ前の予告 (tick)
    static final int WARN = 30;
    static final int SACS = StormDeck.S02_SACS;
    // 配置 2 (まだら) の膨らみ始めの順 (T / 8 の倍数)。気嚢 1〜5
    static final int[] SCATTER = {0, 5, 2, 7, 3, 6};
    // 横風 (障害): 周期・予告・吹く長さ (tick)。後半 (x -98 より先) の空中で
    static final int GUST_CYCLE = 160;
    static final int GUST_WARN = 40;
    static final int GUST_BLOW = 50;
    static final double GUST_PUSH = 0.06;
    static final double GUST_X = -98;

    private static final BlockState ON = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
    private static final BlockState WARN_BLOCK = Blocks.YELLOW_CONCRETE.defaultBlockState();

    public S02Sacs() {
        super("S02", new Ports(StormDeck.S02_GATE, false, List.of(), StormDeck.S02_STONE, null, "", StormDeck.S02_AREA, List.of()));
    }

    // ---------------------------------------------------------------- 数字
    public static int period(Mods m) {
        return Abyss.shorter(m, PERIOD[m.number(0)]);
    }

    // 膨らんでいる割合 (人数の分を足す)。深淵は狭い
    public static double window(Mods m, int players) {
        double w = Abyss.narrower(m, WINDOW[m.number(1)]);
        w *= 1.0 + 0.12 * Math.min(4, Math.max(0, players - 1));
        return Math.min(0.85, w);
    }

    // 気嚢 i の膨らみ始め (周期の中の位置、tick)
    static int startOf(int layout, int i, int period) {
        int unit = period / 8;
        return switch (layout) {
            case 0 -> i * unit;
            case 1 -> (SACS - i) * unit;
            default -> SCATTER[Math.min(i, SCATTER.length - 1)] * unit;
        };
    }

    // 気嚢 i の膨らんでいる長さ (tick)。中ほどの台の気嚢は長め、後半は短い
    static int onTicks(Mods m, int i, int players) {
        int period = period(m);
        double w = window(m, players) * (i == 3 ? 1.25 : i >= 4 ? 0.8 : 1.0);
        return Math.max(20, Math.min(period - WARN, (int) Math.round(period * Math.min(0.9, w))));
    }

    // 0 = 萎んでいる、1 = まもなく膨らむ (予告)、2 = 膨らんでいる
    static int stateOf(Mods m, int i, long t, int players) {
        if (i == 0) {
            return 2;
        }
        int period = period(m);
        long ph = Math.floorMod(t - startOf(m.layout(), i, period), (long) period);
        if (ph < onTicks(m, i, players)) {
            return 2;
        }
        return ph >= period - WARN ? 1 : 0;
    }

    // 凪・嵐の昇り風の高さ
    static double lift(ServerLevel back) {
        return StormBackClock.liftHeight(back);
    }

    // ---------------------------------------------------------------- 動かす
    private static int[] states(Run run) {
        return run.get("states", () -> new int[SACS]);
    }

    private static List<UUID> moths(Run run) {
        return run.get("moths", ArrayList::new);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("players", 1);
        int[] s = states(run);
        for (int i = 0; i < SACS; i++) {
            s[i] = stateOf(run.mods, i, 0, 1);
        }
        run.put("mothDone", false);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        discardAll(back, moths(run));
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int n = count(inside);
        run.put("players", n);
        long t = Math.max(0, now - run.startTick);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            bar(inside, ModText.objective("message.skyabyss.trial.s02.start"));
        }
        // 風切りの羽根: 中にいる人に一枚 (出ると消える。StormWest)
        for (ServerPlayer p : inside) {
            if (!GaleFeather.holds(p) && p.isAlive() && !p.isSpectator() && p.getInventory().add(new ItemStack(StormBackSetup.GALE_FEATHER.get()))) {
                Notice.bar(p, ModText.hint("message.skyabyss.trial.s02.feather"));
                back.playSound(null, p.blockPosition(), StormSetup.GALE.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
            }
        }
        // 気嚢の状態。変わったら見かけ・音
        int[] s = states(run);
        boolean visual = false;
        for (int i = 1; i < SACS; i++) {
            int now2 = stateOf(run.mods, i, t, n);
            if (now2 != s[i]) {
                if (now2 == 2) {
                    back.playSound(null, StormDeck.s02Spot(i), StormSetup.GALE.get(), SoundSource.BLOCKS, 0.9f, 1.3f);
                } else if (now2 == 1) {
                    back.playSound(null, StormDeck.s02Spot(i), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.5f);
                }
                s[i] = now2;
                visual = true;
            }
            BlockPos c = StormDeck.s02Spot(i);
            if (s[i] == 2) {
                puff(back, run.group, ParticleTypes.CLOUD, c.getX() + 0.5, c.getY() + 1.2, c.getZ() + 0.5, 3, 0.45, 0.5, 0.45, 0.03);
                puff(back, run.group, ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 1.0 + lift(back), c.getZ() + 0.5, 1, 0.5, 0.0, 0.5, 0.0);
            } else if (s[i] == 1) {
                puff(back, run.group, ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 1.1, c.getZ() + 0.5, 2, 0.8, 0.1, 0.8, 0.01);
            }
        }
        if (visual) {
            TrialRuntime.refresh(back, run);
        }
        // 落ちた: 直前の休み場へ
        for (ServerPlayer p : inside) {
            if (fell(p, Y)) {
                toRest(back, run, p, restByX(p, "s02a", "s02m", -91, false), "message.skyabyss.trial.s02.fell");
                run.put("falls", run.getInt("falls") + 1);
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
            }
        }
        // 雷蛾 (障害): 後半の空に一匹 (三人以上なら二匹)
        if (run.mods.hasHazard(1) && !run.cleared && back.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            int want = n >= 3 ? 2 : 1;
            if (alive(back, moths(run)) < want && !(Boolean) run.data.getOrDefault("mothDone", false) && inside.stream().anyMatch(p -> p.getX() < -60)) {
                Island m = StormDeck.S02_ISLANDS[4];
                StaticMoth moth = spawn(back, StormMobSetup.STATIC_MOTH.get(), run.group, m.cx() + back.random.nextInt(9) - 4, Y + 7, m.cz() + back.random.nextInt(9) - 4);
                if (moth != null) {
                    moths(run).add(moth.getUUID());
                }
                if (moths(run).size() >= want) {
                    run.put("mothDone", true);
                }
            }
        }
        // 向こうの台に着いた
        if (!run.cleared) {
            Island g = StormDeck.S02_ISLANDS[SACS];
            for (ServerPlayer p : inside) {
                if (g.over(p.getX(), p.getZ()) && p.getY() >= Y + 0.9 && p.onGround()) {
                    finish(back, run, inside, now, "message.skyabyss.trial.s02.crossed");
                    break;
                }
            }
        }
    }

    // 毎 tick: 膨らんでいる気嚢の昇り風と、横風
    @Override
    public void perTick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int[] s = states(run);
        double h = lift(back);
        for (ServerPlayer p : inside) {
            if (p.isSpectator() || p.getAbilities().flying || !p.isAlive()) {
                continue;
            }
            for (int i = 0; i < SACS; i++) {
                if (s[i] != 2) {
                    continue;
                }
                BlockPos c = StormDeck.s02Spot(i);
                double base = c.getY() + 1;
                double dx = p.getX() - (c.getX() + 0.5);
                double dz = p.getZ() - (c.getZ() + 0.5);
                if (dx * dx + dz * dz <= StormDeck.VENT_R * StormDeck.VENT_R && p.getY() >= base - 0.2 && p.getY() <= base + h + 1.5) {
                    Updraft.lift(p, base + h);
                    break;
                }
            }
        }
        if (run.mods.hasHazard(0)) {
            gust(back, run, inside, now);
        }
    }

    // 横風 (障害): 後半の空中。予告のあいだ水平の雨の筋と笛、吹くあいだは滑空している人を横 (南北) へ押す
    private void gust(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        long t = Math.max(0, now - run.startTick);
        int ph = (int) Math.floorMod(t, (long) GUST_CYCLE);
        int dir = ((t / GUST_CYCLE) & 1) == 0 ? 1 : -1;
        for (ServerPlayer p : inside) {
            if (p.getX() > GUST_X || p.onGround() || p.getY() < Y - 1) {
                continue;
            }
            if (ph < GUST_WARN) {
                if (now % 6 == 0) {
                    puff(back, run.group, ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ() - dir * 6, 4, 0.4, 0.8, 1.0, 0.0);
                }
                if (ph == 0) {
                    back.playSound(null, p.blockPosition(), StormSetup.CROSSWIND.get(), SoundSource.AMBIENT, 1.2f, 1f);
                }
            } else if (ph < GUST_WARN + GUST_BLOW && GaleFeather.gliding(p)) {
                Vec3 d = Updraft.actualMotion(p);
                p.setDeltaMovement(d.x, d.y, d.z + GUST_PUSH * dir);
                p.hurtMarked = true;
            }
        }
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int[] s = states(run);
        for (int i = 1; i < SACS; i++) {
            if (s[i] == 0) {
                continue;
            }
            BlockPos c = StormDeck.s02Spot(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean mid = dx == 0 && dz == 0;
                    out.accept(c.offset(dx, 0, dz), s[i] == 2 ? (mid ? Blocks.SEA_LANTERN.defaultBlockState() : ON) : WARN_BLOCK);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 検査 (建てた形と距離)
    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < StormDeck.S02_ISLANDS.length; i++) {
            Island is = StormDeck.S02_ISLANDS[i];
            for (BlockPos p : is.floor()) {
                if (back.getBlockState(p).isAir()) {
                    bad.add("S02 island " + i + " has a bad tile at " + p);
                    break;
                }
            }
            if (i < SACS && back.getBlockState(StormDeck.s02Spot(i)).isAir()) {
                bad.add("S02 sac " + i + " has no vent spot");
            }
        }
        // 滑空の余裕: 凪の昇り風 (4) から次の島の手前の縁まで。0.7 以下
        for (int i = 0; i < SACS; i++) {
            double margin = StormDeck.glideMargin(StormDeck.s02Spot(i), StormDeck.S02_ISLANDS[i + 1], StormBackClock.CALM_LIFT);
            if (margin > 0.72) {
                bad.add("S02 hop " + i + " is too long for the calm updraft (margin " + String.format("%.2f", margin) + ")");
            }
        }
        if (!back.getBlockState(StormDeck.S02_STONE).is(StormBackSetup.CHIME_STONE.get())) {
            bad.add("S02 stone missing");
        }
        return bad;
    }

    static boolean isId(String id) {
        return TrialCatalog.get(id) != null;
    }
}
