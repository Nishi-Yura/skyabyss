package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck2.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.back.StormBackClock;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormDeck2;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import jp.revati.skyabyss.titan.storm.mob.StaticMoth;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// S04 羽根の吊り橋 (歩・渡る・★★★。docs/design/29 の 3.3)。尾の上の空に架かる、手すりのない幅 7 の橋。橋の真ん中に柱が六本。
//   決まり (教える): 突風の前には羽がざわめく (羽毛の筋が風下へ流れ、風の音)。そのとき、柱の風下の青い床 (陰) が白く光る。突風は人を横へ押して橋から落とす。
//     白く光った「陰」に入って待てば、風は当たらない。前半の橋 (柱 3 本) の突風は、いつも北から (南へ押す) ので、柱の南側に入る。
//   ひねり: 中ほどの台のあとの橋は、突風の向きが毎回変わる。羽毛の流れる向きと、光った陰を見て、その側へ回り込む。
//   凪は予告が長く風が弱い・嵐は予告が短く風が強い (どちらでも、陰の中は安全で、陰から陰へは渡れる)。落ちたら直前の休み場へ (傷は軽い)。
// 人数: 突風の周期が一人ごとに 6% 短くなる (四人分まで)。陰はいくらでも入れる。一人でも陰から陰へ渡れる (柱の間は 12)
// 修飾: 配置 3 (後半の突風の向きの並び)・数字 2 (突風の間隔 200/160/130・吹く長さ 20/28/36)・障害 2 (雷蛾・空の雷)・任意の目標 (一度も落ちずに)
public final class S04Bridge extends StormCell implements StormCell.PerTick {
    static final int[] GAP = {200, 160, 130};
    static final int[] BLOW = {20, 28, 36};
    static final int WARN_CALM = 56;
    static final int WARN_STORM = 40;
    static final double PUSH_CALM = 0.055;
    static final double PUSH_STORM = 0.075;
    // 空の雷 (障害): 間隔・予告
    static final int SKY_EVERY = 150;
    static final int SKY_WARN = 24;

    private static final BlockState LIT = Blocks.SEA_LANTERN.defaultBlockState();

    public S04Bridge() {
        super("S04", new Ports(StormDeck2.S04_GATE, false, List.of(), StormDeck2.S04_STONE, null, "", StormDeck2.S04_AREA, List.of()));
    }

    // ---------------------------------------------------------------- 数字
    // 突風の周期 (tick)。人数で短く、深淵はさらに短い (最短 90)
    public static int period(Mods m, int players) {
        double scale = 1.0 - 0.06 * Math.min(4, Math.max(0, players - 1));
        int p = (int) Math.round(Abyss.shorter(m, GAP[m.number(0)]) * scale / 5.0) * 5;
        return Math.max(90, p);
    }

    public static int blow(Mods m) {
        int b = BLOW[m.number(1)];
        return m.abyss() ? (int) Math.round(b * Abyss.NUMBER_STEP) : b;
    }

    public static int warn(boolean stormy) {
        return stormy ? WARN_STORM : WARN_CALM;
    }

    public static double push(boolean stormy) {
        return stormy ? PUSH_STORM : PUSH_CALM;
    }

    // 区画 0 = 教えの橋 (柱 0〜2)、1 = ひねりの橋 (柱 3〜5)。位置がどちらの橋の上か (橋の上でなければ -1)
    public static int zoneOf(double x, double z, double y) {
        if (Math.abs(z - (StormDeck2.S04_CENTER_Z + 0.5)) > 3.6 || y < Y + 0.5 || y > Y + 5) {
            return -1;
        }
        if (x >= StormDeck2.S04_A_X1 && x < StormDeck2.S04_A_X2 + 1) {
            return 0;
        }
        if (x >= StormDeck2.S04_B_X1 && x < StormDeck2.S04_B_X2 + 1) {
            return 1;
        }
        return -1;
    }

    static int[] pillarsOf(int zone) {
        int t = StormDeck2.S04_TEACH;
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = StormDeck2.S04_PILLARS[zone == 0 ? i : t + i];
        }
        return out;
    }

    // その周期の風の向き (+1 = 北から吹いて南 (+z) へ押す、-1 = 南から)。教えの橋はいつも +1。ひねりの橋は配置ごと
    public static int dirOf(Run run, int zone, long cycle) {
        if (zone == 0) {
            return 1;
        }
        return switch (run.mods.layout()) {
            case 0 -> (cycle & 1) == 0 ? -1 : 1;
            case 1 -> new Random(run.seed * 131 + cycle * 7919).nextBoolean() ? 1 : -1;
            default -> ((cycle >> 1) & 1) == 0 ? -1 : 1;
        };
    }

    // 陰: 風向き dir で、柱 px の風下の床にいるか (x は柱の前後 2 マス、z は柱の風下の二列)
    public static boolean inLee(double x, double z, int px, int dir) {
        if (x < px - 2 || x >= px + 3) {
            return false;
        }
        return dir > 0 ? z >= StormDeck2.S04_CENTER_Z + 2 : z < StormDeck2.S04_CENTER_Z - 1;
    }

    public static boolean sheltered(double x, double z, int zone, int dir) {
        for (int px : pillarsOf(zone)) {
            if (inLee(x, z, px, dir)) {
                return true;
            }
        }
        return false;
    }

    // 周期の中の段階 (0 しずか / 1 予告 / 2 吹く)。区画 1 は半周期ずれる (二つの橋の風が同時に来ない)
    static int phase(long t, int zone, int period, int warn, int blow) {
        long tt = t + (zone == 1 ? period / 2 : 0);
        int ph = (int) Math.floorMod(tt, (long) period);
        return ph < warn ? 1 : ph < warn + blow ? 2 : 0;
    }

    static long cycleOf(long t, int zone, int period) {
        return Math.floorDiv(t + (zone == 1 ? period / 2 : 0), (long) period);
    }

    static boolean stormy(ServerLevel back) {
        return StormBackClock.strength(back) >= 0.5;
    }

    private static List<UUID> moths(Run run) {
        return run.get("moths", ArrayList::new);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("players", 1);
        run.put("nextSky", 0L);
        run.put("mothDone", false);
        run.put("view", 0);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        discardAll(back, moths(run));
    }

    // ---------------------------------------------------------------- 動かす
    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int n = count(inside);
        run.put("players", n);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            bar(inside, ModText.objective("message.skyabyss.trial.s04.start"));
        }
        // 落ちた: 直前の休み場へ
        for (ServerPlayer p : inside) {
            if (fell(p, Y)) {
                toRest(back, run, p, restByX(p, "s04a", "s04m", -86.5, false), "message.skyabyss.trial.s04.fell");
                run.put("falls", run.getInt("falls") + 1);
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
            }
        }
        // 向こうの台に着いた
        if (!run.cleared) {
            for (ServerPlayer p : inside) {
                if (StormDeck2.S04_END.over(p.getX(), p.getZ()) && p.getY() >= Y + 0.9 && p.onGround()) {
                    run.put("view", 0);
                    finish(back, run, inside, now, "message.skyabyss.trial.s04.crossed");
                    break;
                }
            }
        }
        if (run.cleared) {
            return;
        }
        // 見かけ: 予告・吹いているあいだ、風下の陰が白く光る (段階か向きが変わったら送り直す)
        long t = Math.max(0, now - run.startTick);
        int period = period(run.mods, n);
        int warn = warn(stormy(back));
        int blow = blow(run.mods);
        int view = 0;
        for (int zone = 0; zone < 2; zone++) {
            int ph = phase(t, zone, period, warn, blow);
            if (ph != 0) {
                view |= (1 << zone) | (dirOf(run, zone, cycleOf(t, zone, period)) > 0 ? 4 << zone : 0);
            }
            // 予告の始め: 風の音
            if (ph == 1 && phase(t - 5, zone, period, warn, blow) == 0) {
                BlockPos at = new BlockPos(pillarsOf(zone)[1], Y + 1, StormDeck2.S04_CENTER_Z);
                back.playSound(null, at, StormSetup.CROSSWIND.get(), SoundSource.AMBIENT, 1.4f, 1f);
                if (ph == 1 && zone == 1 && !run.data.containsKey("twisted")) {
                    run.put("twisted", true);
                    bar(where(inside, p -> p.getX() < StormDeck2.S04_B_X2 + 12), ModText.hint("message.skyabyss.trial.s04.twist"));
                }
            }
        }
        if (view != run.getInt("view")) {
            run.put("view", view);
            TrialRuntime.refresh(back, run);
        }
        // 予告の羽毛 (風下へ流れる筋)
        if (now % 3 == 0) {
            for (int zone = 0; zone < 2; zone++) {
                int ph = phase(t, zone, period, warn, blow);
                if (ph == 0 || !anyIn(inside, zone)) {
                    continue;
                }
                int dir = dirOf(run, zone, cycleOf(t, zone, period));
                int[] px = pillarsOf(zone);
                for (int k = 0; k < 3; k++) {
                    double x = px[k] - 5 + back.random.nextInt(11);
                    double z = StormDeck2.S04_CENTER_Z + 0.5 - dir * 4.5;
                    puff(back, run.group, ph == 2 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_ASH, x, Y + 1.4 + back.random.nextDouble() * 1.5, z, 0, 0, 0, dir,
                            ph == 2 ? 0.9 : 0.5);
                }
            }
        }
        // 雷蛾 (障害): ひねりの橋に着いたころ
        if (run.mods.hasHazard(0) && back.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            int want = n >= 3 ? 2 : 1;
            if (alive(back, moths(run)) < want && !(Boolean) run.data.getOrDefault("mothDone", false) && inside.stream().anyMatch(p -> p.getX() < StormDeck2.S04_MID.cx())) {
                StaticMoth moth = spawn(back, StormMobSetup.STATIC_MOTH.get(), run.group, -120 + back.random.nextInt(9) - 4, Y + 8, StormDeck2.S04_CENTER_Z + back.random.nextInt(5) - 2);
                if (moth != null) {
                    moths(run).add(moth.getUUID());
                }
                if (moths(run).size() >= want) {
                    run.put("mothDone", true);
                }
            }
        }
        // 空の雷 (障害): 橋の上の人に 1.2 秒の予告の輪
        if (run.mods.hasHazard(1) && now >= run.getLong("nextSky") && back.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            List<ServerPlayer> onBridge = where(inside, p -> zoneOf(p.getX(), p.getZ(), p.getY()) >= 0);
            if (!onBridge.isEmpty()) {
                ServerPlayer target = onBridge.get(back.random.nextInt(onBridge.size()));
                run.put("nextSky", now + SKY_EVERY);
                StormStrike.call(back, target.position(), SKY_WARN, 8f, null, run.group);
            }
        }
        if (run.mods.goal() == 0 && run.getInt("falls") > 0) {
            run.goalOk = false;
        }
    }

    private static boolean anyIn(List<ServerPlayer> inside, int zone) {
        for (ServerPlayer p : inside) {
            double x = p.getX();
            double lo = zone == 0 ? StormDeck2.S04_A_X1 - 12 : StormDeck2.S04_B_X1 - 12;
            double hi = zone == 0 ? StormDeck2.S04_A_X2 + 12 : StormDeck2.S04_B_X2 + 12;
            if (x >= lo && x <= hi) {
                return true;
            }
        }
        return false;
    }

    // 毎 tick: 吹いているあいだ、陰の外の人を横へ押す
    @Override
    public void perTick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (run.cleared) {
            return;
        }
        int n = count(inside);
        long t = Math.max(0, now - run.startTick);
        int period = period(run.mods, n);
        int warn = warn(stormy(back));
        int blow = blow(run.mods);
        double push = push(stormy(back));
        for (ServerPlayer p : inside) {
            if (p.isSpectator() || p.getAbilities().flying || !p.isAlive()) {
                continue;
            }
            int zone = zoneOf(p.getX(), p.getZ(), p.getY());
            if (zone < 0 || phase(t, zone, period, warn, blow) != 2) {
                continue;
            }
            int dir = dirOf(run, zone, cycleOf(t, zone, period));
            if (sheltered(p.getX(), p.getZ(), zone, dir)) {
                continue;
            }
            Vec3 d = p.getDeltaMovement();
            p.setDeltaMovement(d.x, d.y, d.z + dir * push);
            p.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int view = run.getInt("view");
        for (int zone = 0; zone < 2; zone++) {
            if ((view >> zone & 1) == 0) {
                continue;
            }
            int dir = (view >> (2 + zone) & 1) == 1 ? 1 : -1;
            for (int px : pillarsOf(zone)) {
                for (int x = px - 2; x <= px + 2; x++) {
                    for (int dz = 0; dz < 2; dz++) {
                        int z = dir > 0 ? StormDeck2.S04_CENTER_Z + 2 + dz : StormDeck2.S04_CENTER_Z - 3 + dz;
                        out.accept(new BlockPos(x, Y, z), LIT);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 検査
    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (var is : List.of(StormDeck2.S04_START, StormDeck2.S04_MID, StormDeck2.S04_END)) {
            for (BlockPos p : is.floor()) {
                if (back.getBlockState(p).isAir()) {
                    bad.add("S04 island has a hole at " + p);
                    break;
                }
            }
        }
        int[][] spans = {{StormDeck2.S04_A_X1, StormDeck2.S04_A_X2}, {StormDeck2.S04_B_X1, StormDeck2.S04_B_X2}};
        for (int[] sp : spans) {
            for (int x = sp[0]; x <= sp[1]; x++) {
                for (int z = StormDeck2.S04_BRIDGE_Z1; z <= StormDeck2.S04_BRIDGE_Z2; z++) {
                    BlockPos p = new BlockPos(x, Y, z);
                    if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).isAir() && !(Math.abs(z - StormDeck2.S04_CENTER_Z) <= 1 && isPillarX(x))) {
                        bad.add("S04 bridge is broken at " + p);
                        x = sp[1];
                        break;
                    }
                }
            }
        }
        for (int px : StormDeck2.S04_PILLARS) {
            if (!back.getBlockState(new BlockPos(px, Y + 3, StormDeck2.S04_CENTER_Z)).is(Blocks.POLISHED_BLACKSTONE_BRICKS)) {
                bad.add("S04 pillar missing at " + px);
            }
            if (!back.getBlockState(new BlockPos(px, Y, StormDeck2.S04_CENTER_Z + 3)).is(Blocks.LIGHT_BLUE_TERRACOTTA)
                    || !back.getBlockState(new BlockPos(px, Y, StormDeck2.S04_CENTER_Z - 3)).is(Blocks.LIGHT_BLUE_TERRACOTTA)) {
                bad.add("S04 lee tiles missing at " + px);
            }
        }
        // 柱どうし・島との間は 12 以上 (陰から陰へ、予告のうちに歩いて渡れる)
        for (int i = 0; i + 1 < StormDeck2.S04_PILLARS.length; i++) {
            if (Math.abs(StormDeck2.S04_PILLARS[i] - StormDeck2.S04_PILLARS[i + 1]) > 14 && i != StormDeck2.S04_TEACH - 1) {
                bad.add("S04 pillars " + i + " and " + (i + 1) + " are too far apart");
            }
        }
        if (!back.getBlockState(StormDeck2.S04_STONE).is(StormBackSetup.CHIME_STONE.get())) {
            bad.add("S04 stone missing");
        }
        return bad;
    }

    private static boolean isPillarX(int x) {
        for (int px : StormDeck2.S04_PILLARS) {
            if (Math.abs(x - px) <= 1) {
                return true;
            }
        }
        return false;
    }
}
