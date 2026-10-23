package jp.revati.skyabyss.trial.raid.ember;

import java.util.Arrays;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

// 智 ER5「炉の圧合わせ」(docs/design/13_ember.md)。北の壁に四つの圧力計。それぞれ七つの目盛りがあり、光る針が一つ。手前の弁を回すと針が動く。
//  - 弁を右クリックで回す (その弁の針が +1、両隣の弁の針は -1)。左クリックで逆に回す (針 -1、両隣 +1)。目盛りは輪になっている (6 の次は 0)
//  - 四つの針を、同じ目盛りへそろえると越える (どの目盛りでもよい。横の印の線で高さを見比べる)。傷も敵もない
//  - 始まりの針は、毎回違う。そろった所から逆に何度か回して作るので、必ず解ける。人数が多いと、四つの弁を手分けして回せる
public final class EmberValves extends EmberRoom {
    public static final String ID = "ER5";
    public static final int HX = 9;
    public static final int HZ = 12;
    public static final int MARKS = 7;
    private static final int[] VX = {-7, -3, 3, 7};
    private static final int VALVE_Z = -HZ + 5;
    // 蝋を引いた銅 (引いていない銅は時間で錆びが進み、部屋の見た目が変わる。docs/design/39 の 6.3)
    private static final Block[] WHEEL = {Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER, Blocks.WAXED_OXIDIZED_COPPER};
    // 七つの炉 (碑「炉は 七つで 息をする」): 東西の壁ぎわに三つずつと入口の戸の上に一つ。そろった針の数だけ大釜が熾で満ちる
    private static final int[][] HEARTHS = {{-HX, -3}, {HX, -3}, {-HX, 2}, {HX, 2}, {-HX, 7}, {HX, 7}, {0, HZ}};

    private final int[] needle = new int[4];
    private final int[] turns = new int[4];
    private boolean done;
    private int moves;
    private int[] startNeedles = new int[4];

    @Override
    public void build(RaidCtx ctx) {
        RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, 8, RaidKit.EMBER));
        var lv = ctx.level();
        EmberRaidDecor deco = EmberRaidDecor.of(ctx);
        deco.shell(true);
        deco.goldLine(0, -11, 11);
        // 北の壁の盤: 四つの圧力計の枠と、高さの印 (全部の目盛りの高さに横の印)
        RaidKit.fill(lv, ctx.local(-HX, 1, -HZ), ctx.local(HX, MARKS + 1, -HZ), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        RaidKit.fill(lv, ctx.local(-1, 1, -HZ), ctx.local(1, 3, -HZ), AIR);
        for (int j = 0; j < MARKS; j++) {
            for (int x = -HX + 1; x < HX; x++) {
                if (Math.abs(x) <= 1) {
                    continue;
                }
                boolean gauge = false;
                for (int v : VX) {
                    gauge |= x == v;
                }
                if (!gauge) {
                    set(ctx, ctx.local(x, j + 1, -HZ), b((j == 0 || j == MARKS - 1) ? Blocks.GILDED_BLACKSTONE : Blocks.CHISELED_POLISHED_BLACKSTONE));
                }
            }
        }
        // 弁 (手前): 3x3 の台、柱、銅の輪
        for (int i = 0; i < 4; i++) {
            RaidKit.fill(lv, ctx.local(VX[i] - 1, 0, VALVE_Z - 1), ctx.local(VX[i] + 1, 0, VALVE_Z + 1), b(Blocks.POLISHED_BASALT));
            RaidKit.fill(lv, ctx.local(VX[i], 1, VALVE_Z), ctx.local(VX[i], 2, VALVE_Z), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
            // 圧力計の下の「脚」(針の当たらない根)
            set(ctx, ctx.local(VX[i], 0, -HZ + 1), b(Blocks.POLISHED_BASALT));
        }
        for (int[] d : new int[][] {{-9, -4}, {9, -4}, {-9, 6}, {9, 6}}) {
            RaidKit.fill(lv, ctx.local(d[0], 1, d[1]), ctx.local(d[0], 5, d[1]), b(Blocks.BASALT));
            set(ctx, ctx.local(d[0], 6, d[1]), b(Blocks.SHROOMLIGHT));
        }
        decorate(ctx, deco);
        shuffle(ctx);
        draw(ctx);
    }

    // 飾り (docs/design/39 の 4.4 の「解く (圧合わせ)」): 七つの炉 (台・熾の大釜・間・覆い)、弁から圧力計への床の配管、
    // 壁は玄武岩の柱と熾の溝、天井の鎖。北の盤とその前 (弁の台まで) は空ける
    private void decorate(RaidCtx ctx, EmberRaidDecor d) {
        d.keep(-HX, 0, -HZ, HX, 8, VALVE_Z + 2);
        for (int[] hth : HEARTHS) {
            boolean south = hth[1] == HZ;
            int base = south ? 5 : 1;
            d.put(hth[0], base, hth[1], EmberRaidDecor.BRICKS);
            d.put(hth[0], base + 1, hth[1], b(Blocks.CAULDRON));
            d.put(hth[0], base + 3, hth[1], EmberRaidDecor.BRICKS);
            if (base + 4 <= 8) {
                d.put(hth[0], base + 4, hth[1], EmberRaidDecor.CHISELED);
            }
            d.keepAround(hth[0], hth[1], 1, 8);
        }
        // 弁の台から盤の脚へ、床に埋めた配管 (磨いた玄武岩を横に)
        for (int v : VX) {
            for (int z = -HZ + 2; z <= VALVE_Z - 2; z++) {
                d.repaint(v, 0, z, EmberRaidDecor.axis(EmberRaidDecor.P_BASALT, net.minecraft.core.Direction.Axis.Z));
            }
        }
        d.exitFrame();
        d.relief();
        d.veins(2);
        d.chains(5, 4, 2, 0, 0, 0);
        d.entry(EmberRaidDecor.Scene.BELLOWS);
        d.exitRoom();
        d.lights(3, 5, 10);
        // 出口の灯り: 盤の真ん中の切れ目 (戸) の両肩
        finishDecor(ctx, d, java.util.List.of(ctx.local(-1, 4, -HZ), ctx.local(1, 4, -HZ)));
    }

    // 七つの炉の大釜: そろっている針の数 (counter) に合わせて満ちる (越えたら全部)
    private void hearths(RaidCtx ctx) {
        int full = done ? HEARTHS.length : counter() * HEARTHS.length / 4;
        for (int i = 0; i < HEARTHS.length; i++) {
            int base = HEARTHS[i][1] == HZ ? 5 : 1;
            BlockPos p = ctx.local(HEARTHS[i][0], base + 1, HEARTHS[i][1]);
            if (ctx.level().getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.AbstractCauldronBlock) {
                ctx.level().setBlock(p, i < full ? b(Blocks.LAVA_CAULDRON) : b(Blocks.CAULDRON), 2);
            }
        }
    }

    private void turn(int i, int dir) {
        needle[i] = Math.floorMod(needle[i] + dir, MARKS);
        if (i > 0) {
            needle[i - 1] = Math.floorMod(needle[i - 1] - dir, MARKS);
        }
        if (i < 3) {
            needle[i + 1] = Math.floorMod(needle[i + 1] - dir, MARKS);
        }
    }

    private boolean equal() {
        return needle[0] == needle[1] && needle[1] == needle[2] && needle[2] == needle[3];
    }

    // 解けた形 (どれも同じ目盛り) から、毎回違う回数・順で逆に回して、始まりの針を作る (必ず解ける。最初からそろってはいない)
    private void shuffle(RaidCtx ctx) {
        int base = ctx.rng().nextInt(MARKS);
        do {
            Arrays.fill(needle, base);
            int n = 9 + ctx.rng().nextInt(5);
            for (int k = 0; k < n; k++) {
                turn(ctx.rng().nextInt(4), ctx.rng().nextBoolean() ? 1 : -1);
            }
        } while (equal());
        startNeedles = needle.clone();
    }

    private void draw(RaidCtx ctx) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < MARKS; j++) {
                set(ctx, ctx.local(VX[i], j + 1, -HZ), j == needle[i] ? b(Blocks.SHROOMLIGHT) : b(Blocks.BLACKSTONE));
            }
            set(ctx, ctx.local(VX[i], 3, VALVE_Z), b(WHEEL[Math.floorMod(turns[i], 4)]));
        }
        hearths(ctx);
    }

    @Override
    public void start(RaidCtx ctx) {
        done = false;
        moves = 0;
        Arrays.fill(turns, 0);
        needle[0] = startNeedles[0];
        needle[1] = startNeedles[1];
        needle[2] = startNeedles[2];
        needle[3] = startNeedles[3];
        draw(ctx);
    }

    @Override
    public void reset(RaidCtx ctx) {
        generation++;
        closed(ctx);
        failRequested = false;
        done = false;
        moves = 0;
        Arrays.fill(turns, 0);
        System.arraycopy(startNeedles, 0, needle, 0, 4);
        draw(ctx);
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (done) {
            return;
        }
        if (ctx.now() % 8 == 0) {
            for (int i = 0; i < 4; i++) {
                particles(ctx, ParticleTypes.SMALL_FLAME, ctx.origin().getX() + VX[i] + 0.5, ctx.origin().getY() + needle[i] + 1.5, ctx.origin().getZ() - HZ + 1.1, 1, 0.1, 0.0);
            }
        }
    }

    // 弁を回す: 弁の台のまわり (3x3、高さ 0〜3) を叩く・使う
    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        if (done) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            BlockPos c = ctx.local(VX[i], 0, VALVE_Z);
            if (Math.abs(pos.getX() - c.getX()) <= 1 && Math.abs(pos.getZ() - c.getZ()) <= 1 && pos.getY() >= c.getY() && pos.getY() <= c.getY() + 3) {
                turnValve(ctx, i, attack ? -1 : 1);
                return true;
            }
        }
        return false;
    }

    void turnValve(RaidCtx ctx, int i, int dir) {
        turn(i, dir);
        turns[i] += dir;
        moves++;
        draw(ctx);
        BlockPos w = ctx.local(VX[i], 3, VALVE_Z);
        ctx.level().playSound(null, w, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.6f + 0.1f * needle[i]);
        ctx.level().playSound(null, w, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.6f, 0.8f);
        particles(ctx, ParticleTypes.SMOKE, w.getX() + 0.5, w.getY() + 1, w.getZ() + 0.5, 6, 0.3, 0.02);
        if (equal()) {
            done = true;
            opened(ctx);
            for (int k = 0; k < 4; k++) {
                particles(ctx, ParticleTypes.LAVA, ctx.origin().getX() + VX[k] + 0.5, ctx.origin().getY() + needle[k] + 1.5, ctx.origin().getZ() - HZ + 1.5, 8, 0.4, 0.1);
            }
            ctx.level().playSound(null, ctx.local(0, 2, -HZ), SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.5f, 0.7f);
            bar(ctx, Component.translatable("raid.skyabyss.ER5.done"));
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.ER5.align";
    }

    // 針の一番多い目盛りにそろっている数 (四つ中)
    @Override
    public int counter() {
        int best = 0;
        for (int m = 0; m < MARKS; m++) {
            int n = 0;
            for (int v : needle) {
                if (v == m) {
                    n++;
                }
            }
            best = Math.max(best, n);
        }
        return best;
    }

    @Override
    public int counterMax() {
        return 4;
    }

    // 自己テスト用の窓
    int[] needles() {
        return needle.clone();
    }

    int moves() {
        return moves;
    }

    int[] start() {
        return startNeedles.clone();
    }

    int turnsOf(int i) {
        return turns[i];
    }

    BlockPos valvePos(RaidCtx ctx, int i) {
        return ctx.local(VX[i], 3, VALVE_Z);
    }

    // 針が見えている位置 (圧力計 i の目盛り m)
    BlockPos needleBlock(RaidCtx ctx, int i, int m) {
        return ctx.local(VX[i], m + 1, -HZ);
    }

    // 解き方の探索 (幅優先。回した順の長さを返す。なければ -1)
    int[] solve() {
        record S(int[] n, int[] path) {
        }
        java.util.ArrayDeque<S> q = new java.util.ArrayDeque<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        q.add(new S(needle.clone(), new int[0]));
        seen.add(Arrays.toString(needle));
        while (!q.isEmpty()) {
            S s = q.poll();
            if (s.n()[0] == s.n()[1] && s.n()[1] == s.n()[2] && s.n()[2] == s.n()[3]) {
                return s.path();
            }
            for (int i = 0; i < 4; i++) {
                int[] n = s.n().clone();
                n[i] = Math.floorMod(n[i] + 1, MARKS);
                if (i > 0) {
                    n[i - 1] = Math.floorMod(n[i - 1] - 1, MARKS);
                }
                if (i < 3) {
                    n[i + 1] = Math.floorMod(n[i + 1] - 1, MARKS);
                }
                if (seen.add(Arrays.toString(n))) {
                    int[] p = Arrays.copyOf(s.path(), s.path().length + 1);
                    p[p.length - 1] = i;
                    q.add(new S(n, p));
                }
            }
        }
        return null;
    }
}
