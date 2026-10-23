package jp.revati.skyabyss.trial.raid.ember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

// 智 ER2「鍛冶の手順」(docs/design/13_ember.md)。北の壁の壁画が、今回の鍛冶の順 (型・火・水・槌の並べ替え) を左から見せる。
// 四つの台を、その順に使う。
//  - 台に立っている (しゃがまない) と 2 秒で「整う」(上に灯りが点く)。整った台はそのまま残る (先に整えておける)
//  - 整った台の上でしゃがむ (押し込み) と「使う」。壁画の次の順なら通り、違えば四つの台は全部まっさらに戻る (傷はなし)
//  - 一人なら先に四つ整えてから順に使う。仲間と手分けして、一人一台に立って整え、順に使うと早い
public final class EmberForgeOrder extends EmberRoom {
    public static final String ID = "ER2";
    public static final int CHARGE_TICKS = 40;
    public static final double PAD_RADIUS = 2.2;
    // 0 型 / 1 火 / 2 水 / 3 槌 (灰・橙・青・黄)
    private static final Block[] COLOR = {Blocks.LIGHT_GRAY_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.YELLOW_CONCRETE};
    private static final Block[] STATION = {Blocks.SMITHING_TABLE, Blocks.BLAST_FURNACE, Blocks.WATER_CAULDRON, Blocks.ANVIL};
    private static final int[][] SPOTS = {{-7, -3}, {7, -3}, {-7, 6}, {7, 6}};
    private static final int[] MURAL_X = {-8, -4, 4, 8};

    private static final int IDLE = 0;
    private static final int READY = 1;
    private static final int USED = 2;

    // 台 (位置の添字) ごとの種類
    private final int[] kindAt = new int[4];
    // 順 (壁画の左から) の種類
    private final int[] order = new int[4];
    private final int[] state = new int[4];
    private final int[] charge = new int[4];
    private final Set<UUID> sneaking = new HashSet<>();
    private int next;
    private boolean done;
    private int wrongs;

    @Override
    public void build(RaidCtx ctx) {
        RaidKit.buildCell(ctx, new RaidKit.Spec(11, 13, 8, RaidKit.EMBER));
        var lv = ctx.level();
        EmberRaidDecor deco = EmberRaidDecor.of(ctx);
        deco.shell(true);
        // 入口から壁画への金の筋
        deco.goldLine(0, -10, 12);
        List<Integer> kinds = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(kinds, new java.util.Random(ctx.rng().nextLong()));
        for (int i = 0; i < 4; i++) {
            kindAt[i] = kinds.get(i);
        }
        Collections.shuffle(kinds, new java.util.Random(ctx.rng().nextLong()));
        for (int i = 0; i < 4; i++) {
            order[i] = kinds.get(i);
        }
        // 台 (3x3 の色の床、真ん中に道具)
        for (int i = 0; i < 4; i++) {
            BlockPos c = ctx.local(SPOTS[i][0], 0, SPOTS[i][1]);
            RaidKit.fill(lv, c.offset(-1, 0, -1), c.offset(1, 0, 1), b(COLOR[kindAt[i]]));
            set(ctx, c, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
            set(ctx, c.above(), b(STATION[kindAt[i]]));
            for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                RaidKit.fill(lv, c.offset(d[0], 1, d[1]), c.offset(d[0], 2, d[1]), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
                // 台の四隅の柱の頭は燭台 (明るさ 12 以上の灯りは部屋に 40 まで。docs/design/39 の 6.2)
                set(ctx, c.offset(d[0], 3, d[1]), EmberRaidDecor.candles(3));
            }
        }
        // 壁画 (北の壁ぎわ。左から順に 3x3 の色の板と、真ん中の道具、上に順の数の灯り、下に済みの灯り)
        int z = -13;
        for (int s = 0; s < 4; s++) {
            int cx = MURAL_X[s];
            int k = order[s];
            RaidKit.fill(lv, ctx.local(cx - 1, 2, z), ctx.local(cx + 1, 4, z), b(COLOR[k]));
            set(ctx, ctx.local(cx, 3, z), b(STATION[k]));
            RaidKit.fill(lv, ctx.local(cx - 2, 1, z), ctx.local(cx + 2, 5, z), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
            RaidKit.fill(lv, ctx.local(cx - 1, 2, z), ctx.local(cx + 1, 4, z), b(COLOR[k]));
            set(ctx, ctx.local(cx, 3, z), b(STATION[k]));
            for (int j = 0; j <= s; j++) {
                set(ctx, ctx.local(cx - s / 2 + j - (s % 2 == 0 ? 0 : 0), 6, z), b(Blocks.SHROOMLIGHT));
            }
        }
        decorate(ctx, deco);
        resetState(ctx);
    }

    // 飾り (docs/design/39 の 4.4 の「解く」): 北の壁画の上に梁と鎖、南東の入口の脇に鞴の見渡し台 (二段。階段で上る)、
    // 壁は玄武岩の柱と熾の溝、南西に遠くの炉の窓。台 (色の床と四隅の柱) と壁画の前は空ける
    private void decorate(RaidCtx ctx, EmberRaidDecor d) {
        for (int[] s : SPOTS) {
            d.keepAround(s[0], s[1], 3, 5);
        }
        d.keep(-11, 0, -13, 11, 6, -11);
        d.keep(-1, 0, -13, 1, 3, 13);
        // 壁画の上の梁 (dy 7)。金の鋲を四つの絵の真上に
        for (int x = -10; x <= 10; x++) {
            boolean stud = false;
            for (int mx : MURAL_X) {
                stud |= x == mx;
            }
            d.place(x, 7, -13, stud ? EmberRaidDecor.GILD : EmberRaidDecor.axis(EmberRaidDecor.P_BASALT, net.minecraft.core.Direction.Axis.X));
        }
        for (int mx : MURAL_X) {
            d.chainAt(mx + (mx < 0 ? -2 : 2), -12, 7, null);
        }
        // 見渡し台 (鞴の上): x 7..10, z 10..12。胴は茶の羊毛 (鞴の革)、上は唐檜の板。西から階段二段
        for (int x = 7; x <= 10; x++) {
            for (int z = 10; z <= 12; z++) {
                boolean rim = x == 7 || x == 10 || z == 10 || z == 12;
                d.put(x, 1, z, rim ? b(Blocks.BROWN_WOOL) : b(Blocks.POLISHED_BLACKSTONE_BRICKS));
                d.put(x, 2, z, b(Blocks.SPRUCE_PLANKS));
            }
        }
        for (int z = 10; z <= 12; z++) {
            // 西から東へ上る。段の向きは低い側 (西)。北の端 (z = 12) は壁に向くので段にせず、角の段の高さだけ揃える
            if (z == 12) {
                d.put(5, 1, z, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
                d.put(6, 1, z, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
                d.put(6, 2, z, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
                continue;
            }
            d.put(5, 1, z, b(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS).setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.WEST));
            d.put(6, 1, z, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
            d.put(6, 2, z, b(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS).setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.WEST));
        }
        // 鞴の口 (北へ向いた鉄の筒) と、台の隅の金床・燭台
        d.put(8, 1, 9, b(Blocks.IRON_BARS));
        d.put(10, 3, 12, EmberRaidDecor.anvil(net.minecraft.core.Direction.NORTH));
        d.put(10, 3, 10, EmberRaidDecor.candles(2));
        d.exitFrame();
        d.vistaSouth(-9, -5, 1);
        d.relief();
        d.veins(2);
        d.chains(6, 4, 2, 0, 0, 0);
        d.entry(EmberRaidDecor.Scene.MOLD);
        d.exitRoom();
        d.lights(3, 5, 10);
        // 出口の灯り: 壁画の切れ目 (x ±1) から見える殻の上の段
        finishDecor(ctx, d, List.of(ctx.local(-1, 4, -14), ctx.local(1, 4, -14)));
    }

    private void resetState(RaidCtx ctx) {
        next = 0;
        done = false;
        for (int i = 0; i < 4; i++) {
            state[i] = IDLE;
            charge[i] = 0;
            show(ctx, i);
            set(ctx, ctx.local(MURAL_X[i], 0, -12), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
            set(ctx, ctx.local(MURAL_X[i], 1, -12), b(Blocks.COAL_BLOCK));
        }
        sneaking.clear();
    }

    private void show(RaidCtx ctx, int i) {
        BlockPos c = ctx.local(SPOTS[i][0], 2, SPOTS[i][1]);
        set(ctx, c.below(), b(STATION[kindAt[i]]));
        set(ctx, c, state[i] == READY ? b(Blocks.SHROOMLIGHT) : state[i] == USED ? b(Blocks.MAGMA_BLOCK) : AIR);
    }

    private void wipeStations(RaidCtx ctx) {
        for (int i = 0; i < 4; i++) {
            state[i] = IDLE;
            charge[i] = 0;
            show(ctx, i);
        }
        for (int s = 0; s < 4; s++) {
            set(ctx, ctx.local(MURAL_X[s], 1, -12), b(Blocks.COAL_BLOCK));
        }
        next = 0;
    }

    @Override
    public void start(RaidCtx ctx) {
        resetState(ctx);
    }

    @Override
    public void reset(RaidCtx ctx) {
        generation++;
        closed(ctx);
        failRequested = false;
        wrongs = 0;
        resetState(ctx);
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (done) {
            return;
        }
        List<ServerPlayer> ps = who(ctx);
        boolean[] occupied = new boolean[4];
        Set<UUID> nowSneak = new HashSet<>();
        for (ServerPlayer p : ps) {
            int at = -1;
            for (int i = 0; i < 4; i++) {
                BlockPos c = ctx.local(SPOTS[i][0], 0, SPOTS[i][1]);
                if (near(p, c, PAD_RADIUS) && Math.abs(p.getY() - (c.getY() + 1)) < 1.6) {
                    at = i;
                    break;
                }
            }
            boolean sn = p.isShiftKeyDown();
            if (sn) {
                nowSneak.add(p.getUUID());
            }
            if (at < 0) {
                continue;
            }
            if (!sn && state[at] == IDLE) {
                occupied[at] = true;
            } else if (sn && !sneaking.contains(p.getUUID()) && state[at] == READY) {
                use(ctx, at);
                if (done) {
                    break;
                }
            }
        }
        sneaking.clear();
        sneaking.addAll(nowSneak);
        if (done) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (state[i] != IDLE) {
                continue;
            }
            if (occupied[i]) {
                charge[i]++;
                BlockPos c = ctx.local(SPOTS[i][0], 2, SPOTS[i][1]);
                if (charge[i] % 5 == 0) {
                    particles(ctx, ParticleTypes.FLAME, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 3, 0.3, 0.02);
                }
                if (charge[i] >= CHARGE_TICKS) {
                    state[i] = READY;
                    show(ctx, i);
                    ctx.level().playSound(null, c, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1f, 1.1f);
                }
            } else if (charge[i] > 0) {
                charge[i] = Math.max(0, charge[i] - 2);
            }
        }
    }

    private void use(RaidCtx ctx, int station) {
        BlockPos c = ctx.local(SPOTS[station][0], 2, SPOTS[station][1]);
        if (kindAt[station] == order[next]) {
            state[station] = USED;
            show(ctx, station);
            set(ctx, ctx.local(MURAL_X[next], 1, -12), b(Blocks.SHROOMLIGHT));
            next++;
            ctx.level().playSound(null, c, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 0.8f + 0.1f * next);
            particles(ctx, ParticleTypes.LAVA, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 8, 0.4, 0.1);
            if (next >= 4) {
                done = true;
                opened(ctx);
            }
        } else {
            wrongs++;
            ctx.level().playSound(null, c, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.2f, 0.6f);
            for (int i = 0; i < 4; i++) {
                BlockPos p = ctx.local(SPOTS[i][0], 3, SPOTS[i][1]);
                particles(ctx, ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.4, 0.03);
            }
            wipeStations(ctx);
            bar(ctx, Component.translatable("raid.skyabyss.ER2.wrong"));
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        int ready = 0;
        for (int s : state) {
            if (s == READY) {
                ready++;
            }
        }
        return ready + next >= 4 ? "raid.skyabyss.ER2.use" : "raid.skyabyss.ER2.charge";
    }

    @Override
    public int counter() {
        return next;
    }

    @Override
    public int counterMax() {
        return 4;
    }

    // 自己テスト用の窓
    BlockPos padOf(RaidCtx ctx, int kind) {
        for (int i = 0; i < 4; i++) {
            if (kindAt[i] == kind) {
                return ctx.local(SPOTS[i][0], 0, SPOTS[i][1]);
            }
        }
        return ctx.origin();
    }

    int[] order() {
        return order.clone();
    }

    int readyCount() {
        int n = 0;
        for (int s : state) {
            if (s == READY) {
                n++;
            }
        }
        return n;
    }

    int next() {
        return next;
    }

    int wrongs() {
        return wrongs;
    }
}
