package jp.revati.skyabyss.trial.raid.whale;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

// 歩 WR4「胃の縦穴の上り」(2026-11-08 の夜に作り直し。声「胃の縦穴、部屋構成やギミックを見直した方がいいかも」。
// 前は骨の突起を叩くと階段が生え、歩いて上るだけだった)。縦穴の壁ぞいを、五つの踊り場を伝って頂 (喉) へ跳んで上る。
//  - ひだの棚: 踊り場と踊り場のあいだは、壁から出た一マス幅の棚が三つ (一マスあけて一段ずつ高い)。跳び移って上る
//  - 蠕動: 胃が縮むたびに、登りごとに棚が壁へ引っ込む (CLOSED の間)。引っ込む前の WARN の間は棚が赤い肉に変わり、ぐちゃりと鳴る。
//    となりの登りは半周ずれて縮むので、踊り場で待ってから渡る。上の登りほど縮む間隔が短い (CYCLE)。乗ったまま引っ込むと落ちる
//  - ひだ押さえ: 踊り場 (と床) の隅の骨のこぶを叩くと、次の登りの棚が HOLD の間は引っ込まない。叩いたこぶは COOLDOWN の間は暗い。
//    一人なら自分で、人数がいれば先に上った人が下の人のために押さえる
//  - 胃液: 縦穴の底から一定の速さで上がる (赤い霧の層。層の下にいると傷)。頂より 3 上まで満ちたら部屋のやり直し
//  - 胃液の噴き上げ: 棚や踊り場にいる人の足もとに赤い輪 (予告)。輪の中にいると傷と、縦穴の真ん中へ押し出される (棚から落ちる)
//  - 頂 (五つ目の踊り場) に立った人が一人いれば越える
public final class WhaleShaftRoom extends RaidRoom {
    static final int HX = 7;
    static final int HZ = 7;
    static final int H = 27;
    // 踊り場の数 (= 登りの数。床から踊り場 1 へが登り 0)
    public static final int FLIGHTS = 5;
    // 踊り場どうしの高さ
    public static final int RISE = 4;
    public static final int TOP = FLIGHTS * RISE;
    // 一つの登りの棚の数 (高さ +1・+2・+3。次の踊り場が +4)
    public static final int LEDGES = 3;
    // 蠕動: 登りごとの一周 (上ほど短い)・引っ込んでいる間・引っ込む前の知らせ
    static final int[] CYCLE = {84, 78, 72, 66, 60};
    public static final int CLOSED = 26;
    public static final int WARN = 16;
    // ひだ押さえ: 押さえている間とこぶの休み
    public static final int HOLD = 70;
    public static final int COOLDOWN = 300;
    // 胃液: 最初の猶予と一ブロック上がる間
    static final int GRACE = 100;
    static final int RISE_TICKS = 44;
    static final float DAMAGE = 5f;
    static final int DAMAGE_EVERY = 10;
    static final String FAIL_KEY = "message.skyabyss.raid.WR4.filled";
    // 胃液の噴き上げ: 間隔・予兆・輪の半径・傷・押し出す強さ
    static final int GEYSER_EVERY = 70;
    static final int GEYSER_WARN = 24;
    static final double GEYSER_R = 1.6;
    static final float GEYSER_DAMAGE = 6f;
    static final double GEYSER_PUSH = 1.0;

    // 隅 (x, z の向き)。踊り場 n は隅 CORNER[n % 4] (床の登り口は隅 0)
    private static final int[][] CORNER = {{-1, 1}, {-1, -1}, {1, -1}, {1, 1}};

    private static final BlockState LEDGE = Blocks.BONE_BLOCK.defaultBlockState();
    private static final BlockState LEDGE_WARN = Blocks.NETHER_WART_BLOCK.defaultBlockState();
    private static final BlockState LANDING = Blocks.POLISHED_ANDESITE.defaultBlockState();

    private final java.util.List<double[]> geysers = new java.util.ArrayList<>();
    private long nextGeyser;
    // 登りごとの棚の見え方 (0 = 出ている・1 = 知らせ・2 = 引っ込んでいる)
    private final int[] shown = new int[FLIGHTS];
    private final long[] holdUntil = new long[FLIGHTS];
    private final long[] knobReady = new long[FLIGHTS];
    private long startAt = Long.MAX_VALUE;
    private long nextDamage;
    private boolean active;
    private boolean cleared;
    private int tier;
    private double lastLevel;
    private long lastNow;

    // ---- 形
    // 踊り場 n (1〜FLIGHTS) の 3×3 の隅の範囲: x0, x1, z0, z1 (原点からの相対)
    private static int[] landing(int n) {
        int[] c = CORNER[n % 4];
        int x0 = c[0] < 0 ? -6 : 4;
        int z0 = c[1] < 0 ? -6 : 4;
        return new int[] {x0, x0 + 2, z0, z0 + 2};
    }

    // 登り f の k 番目 (0〜LEDGES-1) の棚のマス (壁ぞいの一マス。下の登りほど奥行き 2、上の三つは 1)
    public List<BlockPos> ledgeCells(RaidCtx ctx, int f, int k) {
        int[] a = CORNER[f % 4];
        int[] b = CORNER[(f + 1) % 4];
        int y = RISE * f + 1 + k;
        // 二つの隅が共有する壁: x が同じなら x の壁、z が同じなら z の壁
        List<BlockPos> out = new ArrayList<>();
        int along = (2 - 2 * k);
        int depth = f < 2 ? 2 : 1;
        for (int d = 0; d < depth; d++) {
            if (a[0] == b[0]) {
                int x = a[0] * (6 - d);
                int z = a[1] * along;
                out.add(ctx.origin().offset(x, y, z));
            } else {
                int z = a[1] * (6 - d);
                int x = a[0] * along;
                out.add(ctx.origin().offset(x, y, z));
            }
        }
        return out;
    }

    // ひだ押さえのこぶ (登り f を押さえる。床か踊り場 f の外の隅): 柱の足もと
    public BlockPos knob(RaidCtx ctx, int f) {
        int[] c = CORNER[f % 4];
        return ctx.origin().offset(c[0] * 6, RISE * f + 1, c[1] * 6);
    }

    public BlockPos landingSpot(RaidCtx ctx, int n) {
        int[] l = landing(n);
        int[] c = CORNER[n % 4];
        // 隅のこぶの斜め内側 (こぶの柱を避ける)
        return ctx.origin().offset(c[0] < 0 ? l[0] + 1 : l[1] - 1, RISE * n + 1, c[1] < 0 ? l[2] + 1 : l[3] - 1);
    }

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.BONE));
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        for (int n = 1; n <= FLIGHTS; n++) {
            int y = RISE * n;
            int[] l = landing(n);
            RaidKit.fill(level, o(c, l[0], y, l[2]), o(c, l[1], y, l[3]), LANDING);
            // 内側の二辺を骨で縁どる
            int[] cn = CORNER[n % 4];
            int ex = cn[0] < 0 ? l[1] : l[0];
            int ez = cn[1] < 0 ? l[3] : l[2];
            for (int x = l[0]; x <= l[1]; x++) {
                RaidKit.set(level, o(c, x, y, ez), bone);
            }
            for (int z = l[2]; z <= l[3]; z++) {
                RaidKit.set(level, o(c, ex, y, z), bone);
            }
            // 踊り場の下の支え (肋骨の柱。壁の隅)
            RaidKit.fill(level, o(c, cn[0] * 6, y - 2, cn[1] * 6), o(c, cn[0] * 6, y - 1, cn[1] * 6), bone);
        }
        // 天井の肋骨の弧
        for (int z : new int[] {-4, 0, 4}) {
            RaidKit.fill(level, o(c, -HX, H, z), o(c, HX, H, z), bone);
            RaidKit.fill(level, o(c, -HX, H - 3, z), o(c, -HX, H, z), bone);
            RaidKit.fill(level, o(c, HX, H - 3, z), o(c, HX, H, z), bone);
        }
        // 頂の踊り場の灯り (外の隅。立つ所をふさがない)
        int[] tc = CORNER[FLIGHTS % 4];
        RaidKit.set(level, o(c, tc[0] * 6, TOP + 1, tc[1] * 6), Blocks.LANTERN.defaultBlockState());
        WhaleDecor.wr4(ctx, this);
        RaidKit.brightenFloor(ctx, 3);
        for (int f = 0; f < FLIGHTS; f++) {
            placeKnob(ctx, f, false);
            setLedges(ctx, f, 0);
        }
    }

    private static BlockPos o(RaidKit.Cell c, int x, int y, int z) {
        return c.origin().offset(x, y, z);
    }

    // 隅のこぶ: 骨の柱 + 頭 (押さえている間は海のランタン、休みは暗い石、使えるときは模様の石)
    private void placeKnob(RaidCtx ctx, int f, boolean lit) {
        BlockPos p = knob(ctx, f);
        RaidKit.set(ctx.level(), p, Blocks.BONE_BLOCK.defaultBlockState());
        boolean resting = !lit && ctx.now() < knobReady[f];
        RaidKit.set(ctx.level(), p.above(), lit ? Blocks.SEA_LANTERN.defaultBlockState()
                : resting ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    }

    private void setLedges(RaidCtx ctx, int f, int state) {
        shown[f] = state;
        BlockState st = state == 0 ? LEDGE : state == 1 ? LEDGE_WARN : Blocks.AIR.defaultBlockState();
        for (int k = 0; k < LEDGES; k++) {
            for (BlockPos p : ledgeCells(ctx, f, k)) {
                RaidKit.set(ctx.level(), p, st);
            }
        }
    }

    // 蠕動の今の状態 (0 = 出ている・1 = 知らせ・2 = 引っ込んでいる)。始まる前・越えたあと・押さえている間は出ている
    public int phase(RaidCtx ctx, int f) {
        long now = ctx.now();
        if (!active || cleared || now < startAt || now < holdUntil[f]) {
            return 0;
        }
        int cyc = CYCLE[f];
        // 隣の登りは半周ずれる。始まりは出ている側から
        long t = Math.floorMod(now - startAt + (f % 2 == 1 ? cyc / 2 : 0), (long) cyc);
        long open = cyc - CLOSED;
        if (t < open - WARN) {
            return 0;
        }
        return t < open ? 1 : 2;
    }

    public boolean ledgesOut(int f) {
        return shown[f] != 2;
    }

    // ---- 始まり・毎 tick
    @Override
    public void start(RaidCtx ctx) {
        startAt = ctx.now() + GRACE;
        nextGeyser = startAt + GEYSER_EVERY;
        geysers.clear();
        nextDamage = 0;
        active = true;
    }

    @Override
    public void tick(RaidCtx ctx) {
        long now = ctx.now();
        ServerLevel level = ctx.level();
        for (int f = 0; f < FLIGHTS; f++) {
            int ph = phase(ctx, f);
            if (ph != shown[f]) {
                BlockPos mid = ledgeCells(ctx, f, 1).get(0);
                if (ph == 1) {
                    level.playSound(null, mid, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1.4f, 0.5f);
                } else if (ph == 2) {
                    level.playSound(null, mid, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.BLOCKS, 1.3f, 0.5f);
                } else {
                    level.playSound(null, mid, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
                }
                setLedges(ctx, f, ph);
            }
            if (ph == 2 && now % 6 == f % 6) {
                // 引っ込んでいる棚の口のしるし
                for (int k = 0; k < LEDGES; k++) {
                    BlockPos p = ledgeCells(ctx, f, k).get(0);
                    level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.0);
                }
            }
            // こぶ: 押さえが終わった・休みが明けた
            if (holdUntil[f] != 0 && now >= holdUntil[f]) {
                holdUntil[f] = 0;
                placeKnob(ctx, f, false);
            }
            if (knobReady[f] != 0 && now >= knobReady[f]) {
                knobReady[f] = 0;
                placeKnob(ctx, f, false);
                level.playSound(null, knob(ctx, f), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7f, 1.2f);
            }
        }
        // 胃液の層
        double lv = fluidLevel(ctx);
        lastLevel = lv;
        lastNow = now;
        double surface = ctx.origin().getY() + 1 + lv;
        fluidFx(ctx, surface, now);
        if (now >= startAt && now >= nextDamage) {
            nextDamage = now + DAMAGE_EVERY;
            for (ServerPlayer p : ctx.standing()) {
                if (!p.isCreative() && !p.isSpectator() && p.getY() < surface && ctx.cell().inner().contains(p.position())) {
                    p.invulnerableTime = 0;
                    p.hurt(level.damageSources().magic(), DAMAGE);
                }
            }
        }
        geysers(ctx, now, surface);
        // 上った高さ・頂
        int t = 0;
        for (ServerPlayer p : ctx.standing()) {
            double rel = p.getY() - (ctx.origin().getY() + 1);
            if (ctx.cell().inner().contains(p.position())) {
                t = Math.max(t, Math.min(FLIGHTS, (int) Math.floor((rel + 0.25) / RISE)));
            }
        }
        tier = Math.max(0, t);
        if (!cleared && tier >= FLIGHTS) {
            cleared = true;
            for (int f = 0; f < FLIGHTS; f++) {
                setLedges(ctx, f, 0);
            }
            WhaleDecor.exitLamps(ctx, true);
        }
        if (!cleared && lv >= TOP + 3) {
            ctx.fail(FAIL_KEY);
        }
    }

    // 胃液の噴き上げ: 上っている人 (胃液の面より 2 上) の足もとを狙う
    private void geysers(RaidCtx ctx, long now, double surface) {
        ServerLevel level = ctx.level();
        if (cleared || now < startAt) {
            return;
        }
        if (now >= nextGeyser) {
            nextGeyser = now + GEYSER_EVERY;
            java.util.List<ServerPlayer> climbers = new java.util.ArrayList<>();
            for (ServerPlayer p : ctx.standing()) {
                if (!p.isSpectator() && ctx.cell().inner().contains(p.position()) && p.getY() > surface + 2) {
                    climbers.add(p);
                }
            }
            java.util.Collections.shuffle(climbers, new java.util.Random(ctx.rng().nextLong()));
            int shots = climbers.size() >= 2 ? 2 : climbers.size();
            for (int i = 0; i < shots; i++) {
                ServerPlayer p = climbers.get(i);
                geysers.add(new double[] {p.getX(), Math.floor(p.getY()), p.getZ(), now + GEYSER_WARN});
                level.playSound(null, p.blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.HOSTILE, 1.4f, 0.6f);
            }
        }
        DustParticleOptions warn = new DustParticleOptions(new Vector3f(0.9f, 0.1f, 0.08f), 1.1f);
        for (java.util.Iterator<double[]> it = geysers.iterator(); it.hasNext(); ) {
            double[] g = it.next();
            if (now < g[3]) {
                if (now % 3 == 0) {
                    for (int k = 0; k < 12; k++) {
                        double a = Math.PI * 2 * k / 12;
                        level.sendParticles(warn, g[0] + Math.cos(a) * GEYSER_R, g[1] + 0.1, g[2] + Math.sin(a) * GEYSER_R, 1, 0, 0, 0, 0);
                    }
                }
                continue;
            }
            it.remove();
            level.playSound(null, BlockPos.containing(g[0], g[1], g[2]), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.HOSTILE, 1.3f, 0.5f);
            level.sendParticles(ParticleTypes.FALLING_LAVA, g[0], g[1] + 0.5, g[2], 30, 0.5, 1.5, 0.5, 0.1);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.7f, 0.05f, 0.05f), 2.0f), g[0], g[1] + 1.5, g[2], 40, 0.6, 1.4, 0.6, 0.02);
            BlockPos o = ctx.origin();
            for (ServerPlayer p : ctx.standing()) {
                if (p.isCreative() || p.isSpectator()) {
                    continue;
                }
                double dx = p.getX() - g[0];
                double dz = p.getZ() - g[2];
                if (dx * dx + dz * dz <= GEYSER_R * GEYSER_R && Math.abs(p.getY() - g[1]) <= 3) {
                    p.invulnerableTime = 0;
                    p.hurt(level.damageSources().magic(), GEYSER_DAMAGE);
                    double cx = o.getX() + 0.5 - p.getX();
                    double cz = o.getZ() + 0.5 - p.getZ();
                    double len = Math.max(0.01, Math.hypot(cx, cz));
                    p.push(cx / len * GEYSER_PUSH, 0.45, cz / len * GEYSER_PUSH);
                    p.hurtMarked = true;
                }
            }
        }
    }

    private void fluidFx(RaidCtx ctx, double surface, long now) {
        ServerLevel level = ctx.level();
        BlockPos o = ctx.origin();
        DustParticleOptions red = new DustParticleOptions(new Vector3f(0.78f, 0.06f, 0.06f), 1.6f);
        DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.45f, 0.02f, 0.05f), 1.2f);
        if (now % 2 == 0) {
            for (int k = -HX; k <= HX; k++) {
                level.sendParticles(red, o.getX() + k + 0.5, surface + 0.1, o.getZ() - HZ + 0.1, 1, 0, 0, 0, 0);
                level.sendParticles(red, o.getX() + k + 0.5, surface + 0.1, o.getZ() + HZ + 0.9, 1, 0, 0, 0, 0);
                level.sendParticles(red, o.getX() - HX + 0.1, surface + 0.1, o.getZ() + k + 0.5, 1, 0, 0, 0, 0);
                level.sendParticles(red, o.getX() + HX + 0.9, surface + 0.1, o.getZ() + k + 0.5, 1, 0, 0, 0, 0);
            }
        }
        for (int n = 0; n < 6; n++) {
            double x = o.getX() - HX + ctx.rng().nextDouble() * (2 * HX + 1);
            double z = o.getZ() - HZ + ctx.rng().nextDouble() * (2 * HZ + 1);
            level.sendParticles(dark, x, surface + 0.05, z, 1, 0.3, 0.05, 0.3, 0);
        }
        if (now % 6 == 0) {
            double x = o.getX() - HX + ctx.rng().nextDouble() * (2 * HX + 1);
            double z = o.getZ() - HZ + ctx.rng().nextDouble() * (2 * HZ + 1);
            level.sendParticles(ParticleTypes.DRIPPING_LAVA, x, surface - 0.2, z, 1, 0, 0, 0, 0);
        }
    }

    // ---- 入力: 隅のこぶを叩くと、その登りを押さえる
    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        for (int f = 0; f < FLIGHTS; f++) {
            BlockPos k = knob(ctx, f);
            if (pos.equals(k) || pos.equals(k.above())) {
                hold(ctx, f);
                return true;
            }
        }
        return false;
    }

    // 登り f を押さえる。休みのあいだは鈍い音だけ。押さえたか
    public boolean hold(RaidCtx ctx, int f) {
        long now = ctx.now();
        BlockPos k = knob(ctx, f);
        if (now < knobReady[f]) {
            ctx.level().playSound(null, k, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
            return false;
        }
        holdUntil[f] = now + HOLD;
        knobReady[f] = now + COOLDOWN;
        placeKnob(ctx, f, true);
        ctx.level().sendParticles(ParticleTypes.CRIT, k.getX() + 0.5, k.getY() + 1.5, k.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        ctx.level().playSound(null, k, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 1.0f, 0.5f + 0.12f * f);
        return true;
    }

    public boolean holding(RaidCtx ctx, int f) {
        return ctx.now() < holdUntil[f];
    }

    // 胃液の層の高さ (床の上のブロック数)
    public double fluidLevel(RaidCtx ctx) {
        if (!active || ctx.now() < startAt) {
            return 0;
        }
        return (ctx.now() - startAt) / (double) RISE_TICKS;
    }

    @Override
    public boolean isComplete() {
        return cleared;
    }

    @Override
    public void reset(RaidCtx ctx) {
        WhaleDecor.exitLamps(ctx, false);
        active = false;
        startAt = Long.MAX_VALUE;
        cleared = false;
        tier = 0;
        geysers.clear();
        for (int f = 0; f < FLIGHTS; f++) {
            holdUntil[f] = 0;
            knobReady[f] = 0;
            placeKnob(ctx, f, false);
            setLedges(ctx, f, 0);
        }
    }

    @Override
    public void end(RaidCtx ctx) {
        active = false;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.WR4.climb";
    }

    @Override
    public int counter() {
        return tier;
    }

    @Override
    public int counterMax() {
        return FLIGHTS;
    }

    @Override
    public String secondaryKey() {
        return "raid.skyabyss.WR4.fluid";
    }

    @Override
    public int secondary() {
        return (int) Math.min(TOP, Math.floor(lastLevel));
    }

    @Override
    public int secondaryMax() {
        return TOP;
    }

    @Override
    public int timerTicks() {
        return active && startAt != Long.MAX_VALUE ? (int) Math.max(0, startAt + (long) (TOP + 1) * RISE_TICKS - lastNow) : -1;
    }

    // ---- 試験用
    public int tier() {
        return tier;
    }

    // 登り f の上る向き (床の隅から次の隅へ)
    public Direction ascend(int f) {
        int[] a = CORNER[f % 4];
        int[] b = CORNER[(f + 1) % 4];
        return Direction.fromDelta(Integer.signum(b[0] - a[0]), 0, Integer.signum(b[1] - a[1]));
    }
}
