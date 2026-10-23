package jp.revati.skyabyss.trial.raid.ember;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.CinderRoller;
import jp.revati.skyabyss.titan.ember.mob.EmberMoth;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

// 闘 ER3「炉を守る」(docs/design/13_ember.md)。部屋の真ん中の大炉 (体力 100) を、火を消しに来る灰の敵から三つの波で守る。
//  - 敵 (灰被り・蛾・転がり) は、近くに人がいなければ大炉へ向かい、炉の近くで毎秒 (灰被り 2・蛾 1・転がり 3) 炉の体力を削る
//  - 四隅の火鉢が灯っている間、その近く (7 ブロック) の敵は遅くなる。敵が近づくと火鉢は消える。人が側に立つと (1.2 秒) 灯し直せる
//  - 炉の体力が尽きたら、部屋は最初からやり直し (ctx.fail)。三つの波をすべて倒せば越える
public final class EmberForgeDefense extends EmberRoom {
    public static final String ID = "ER3";
    public static final int FORGE_MAX = 100;
    public static final int WAVES = 3;
    public static final int FIRST_DELAY = 60;
    public static final int WAVE_GAP = 80;
    public static final double DOUSE_RANGE = 3.8;
    public static final double BRAZIER_RANGE = 7.0;
    public static final double BRAZIER_DOUSE = 2.5;
    public static final int RELIGHT_TICKS = 25;
    // 一人のとき、炉の火を削る間隔 (tick。仲間がいれば 20)
    public static final int SOLO_DOUSE_EVERY = 40;
    private static final int[][] BRAZIERS = {{-9, -9}, {9, -9}, {-9, 9}, {9, 9}};
    private static final int[][] SPAWNS = {{-11, -3}, {11, -3}, {-11, 3}, {11, 3}, {-4, -11}, {4, -11}, {-6, 11}, {6, 11}};

    private BlockPos forge;
    private int hp;
    private int wavesSpawned;
    private int wavesCleared;
    private long t;
    private long nextWave;
    private boolean done;
    private final boolean[] lit = new boolean[4];
    private final int[] relight = new int[4];

    @Override
    public void build(RaidCtx ctx) {
        RaidKit.buildCell(ctx, new RaidKit.Spec(13, 13, 9, RaidKit.EMBER));
        var lv = ctx.level();
        EmberRaidDecor deco = EmberRaidDecor.of(ctx);
        deco.shell(true);
        // 入口から大炉への金の筋と、大炉のまわりの輪 (半径 5)
        deco.goldLine(0, 4, 12);
        deco.floorRing(0, 0, 5, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        deco.floorRing(0, 0, 9.5, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        forge = ctx.local(0, 0, 0);
        // 大炉: 5x5 の台、3x3 の炉身、上の口、四つの火口 (マグマ)
        RaidKit.fill(lv, ctx.local(-2, 0, -2), ctx.local(2, 0, 2), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        RaidKit.fill(lv, ctx.local(-1, 1, -1), ctx.local(1, 4, 1), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        RaidKit.fill(lv, ctx.local(-1, 5, -1), ctx.local(1, 5, 1), b(Blocks.BLACKSTONE));
        set(ctx, ctx.local(0, 6, 0), b(Blocks.SHROOMLIGHT));
        for (int[] d : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            set(ctx, ctx.local(d[0], 2, d[1]), b(Blocks.MAGMA_BLOCK));
        }
        for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            RaidKit.fill(lv, ctx.local(d[0], 1, d[1]), ctx.local(d[0], 2, d[1]), b(Blocks.BASALT));
        }
        // 火鉢の柱
        for (int[] c : BRAZIERS) {
            RaidKit.fill(lv, ctx.local(c[0] - 1, 0, c[1] - 1), ctx.local(c[0] + 1, 0, c[1] + 1), b(Blocks.SMOOTH_BASALT));
            RaidKit.fill(lv, ctx.local(c[0], 1, c[1]), ctx.local(c[0], 2, c[1]), b(Blocks.POLISHED_BASALT));
        }
        decorate(ctx, deco);
        resetState(ctx);
    }

    // 飾り (docs/design/39 の 4.4 の「守る」): 大炉の上に天井から下りる煙突の覆い (鎖つき)、火鉢の上の吊り鎖、
    // 敵の湧く八つの所の壁に暗い灰の口、壁は玄武岩の柱と熾の溝。大炉のまわり (半径 3)・火鉢・湧く所・入口からの道は空ける
    private void decorate(RaidCtx ctx, EmberRaidDecor d) {
        d.keepAround(0, 0, 3, 7);
        for (int[] c : BRAZIERS) {
            d.keepAround(c[0], c[1], 2, 4);
        }
        for (int[] s : SPAWNS) {
            d.keepAround(s[0], s[1], 2, 4);
        }
        d.keep(-1, 0, 3, 1, 3, 13);
        // 煙突の覆い: dy 9 (天井の下) に 5x5、dy 8 に口の輪 (真ん中は空けて炉の火を見せる)、四隅に短い鎖
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                d.place(x, 9, z, Math.abs(x) == 2 || Math.abs(z) == 2 ? EmberRaidDecor.BRICKS : EmberRaidDecor.CHISELED);
                if (Math.max(Math.abs(x), Math.abs(z)) == 1) {
                    d.place(x, 8, z, EmberRaidDecor.BRICKS);
                }
            }
        }
        for (int[] k : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            d.place(k[0], 8, k[1], EmberRaidDecor.CHAIN);
        }
        d.put(0, 7, 0, EmberRaidDecor.light(10));
        // 火鉢の上の吊り鎖 (天井から火鉢の頭の二つ上まで)
        for (int[] c : BRAZIERS) {
            for (int y = 6; y <= 9; y++) {
                d.place(c[0], y, c[1], EmberRaidDecor.CHAIN);
            }
            d.place(c[0], 5, c[1], EmberRaidDecor.IRON_BARS);
        }
        // 灰の口: 湧く所の後ろの壁 (殻) を、煤けた深層岩の暗い枠に
        for (int[] s : SPAWNS) {
            boolean xWall = Math.abs(s[0]) == 11;
            for (int k = -1; k <= 1; k++) {
                for (int dy = 1; dy <= 3; dy++) {
                    int x = xWall ? Integer.signum(s[0]) * 14 : s[0] + k;
                    int z = xWall ? s[1] + k : Integer.signum(s[1]) * 14;
                    boolean rim = Math.abs(k) == 1 || dy == 3;
                    d.repaint(x, dy, z, rim ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.COBBLED_DEEPSLATE));
                }
            }
        }
        // 大炉の脇の床の明るさ (見えない灯り)
        for (int[] k : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            if (ctx.level().getBlockState(ctx.local(k[0], 3, k[1])).isAir()) {
                d.put(k[0], 3, k[1], EmberRaidDecor.light(10));
            }
        }
        d.exitFrame();
        d.relief();
        d.veins(3);
        d.chains(6, 5, 2, 0, 0, 6);
        d.entry(EmberRaidDecor.Scene.BRAZIER);
        d.exitRoom();
        d.lights(4, 6, 9);
        finishDecor(ctx, d, d.defaultExitLamps());
    }

    private void showBrazier(RaidCtx ctx, int i) {
        set(ctx, ctx.local(BRAZIERS[i][0], 3, BRAZIERS[i][1]), lit[i] ? b(Blocks.SHROOMLIGHT) : b(Blocks.BLACKSTONE));
    }

    private void resetState(RaidCtx ctx) {
        hp = FORGE_MAX;
        wavesSpawned = 0;
        wavesCleared = 0;
        t = 0;
        nextWave = FIRST_DELAY;
        done = false;
        for (int i = 0; i < 4; i++) {
            lit[i] = true;
            relight[i] = 0;
            showBrazier(ctx, i);
        }
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
        resetState(ctx);
    }

    private BlockPos spawnPoint(RaidCtx ctx) {
        int[] s = SPAWNS[ctx.rng().nextInt(SPAWNS.length)];
        return ctx.local(s[0], 1, s[1]);
    }

    private void spawnWave(RaidCtx ctx) {
        int s = scale(ctx);
        int moths;
        int thralls;
        int rollers;
        switch (wavesSpawned) {
            case 0 -> {
                moths = 3 + (s - 1);
                thralls = 0;
                rollers = 0;
            }
            case 1 -> {
                moths = 0;
                thralls = 2 + (s - 1);
                rollers = 1;
            }
            default -> {
                moths = 3 + (s - 1);
                thralls = 2 + (s - 1);
                rollers = 2;
            }
        }
        for (int i = 0; i < moths; i++) {
            spawn(ctx, EmberMobSetup.EMBER_MOTH.get(), spawnPoint(ctx).above(2));
        }
        for (int i = 0; i < thralls; i++) {
            spawn(ctx, EmberMobSetup.ASH_THRALL.get(), spawnPoint(ctx));
        }
        for (int i = 0; i < rollers; i++) {
            spawn(ctx, EmberMobSetup.CINDER_ROLLER.get(), spawnPoint(ctx));
        }
        wavesSpawned++;
        ctx.level().playSound(null, forge, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2f, 0.5f);
        bar(ctx, Component.translatable("raid.skyabyss.ER3.wave", wavesSpawned, WAVES));
    }

    private static int douseRate(Mob m) {
        if (m instanceof AshThrall) {
            return 2;
        }
        if (m instanceof CinderRoller) {
            return 3;
        }
        return 1;
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (done) {
            return;
        }
        t++;
        List<ServerPlayer> ps = who(ctx);
        List<Mob> mobs = new ArrayList<>(ctx.aliveMobs());
        double fx = forge.getX() + 0.5;
        double fz = forge.getZ() + 0.5;
        // 波
        if (wavesSpawned < WAVES && mobs.isEmpty() && t >= nextWave) {
            spawnWave(ctx);
            mobs = new ArrayList<>(ctx.aliveMobs());
        }
        // 敵: 炉へ向かう・火を消す
        for (Mob m : mobs) {
            boolean playerNear = false;
            for (ServerPlayer p : ps) {
                if (p.distanceToSqr(m) < 36) {
                    playerNear = true;
                    break;
                }
            }
            if (t % 10 == m.getId() % 10 && !playerNear) {
                m.setTarget(null);
                m.getNavigation().moveTo(fx, forge.getY() + 1, fz, 1.0);
                if (m instanceof EmberMoth) {
                    m.getMoveControl().setWantedPosition(fx, forge.getY() + 4, fz, 1.0);
                }
            }
            // 一人のときは守る手が一つなので、炉の消え方を半分にする (2 秒に一度。仲間がいれば毎秒)
            int every = scale(ctx) <= 1 ? SOLO_DOUSE_EVERY : 20;
            if (t % every == m.getId() % every && flat(m.position(), fx, fz) <= DOUSE_RANGE && Math.abs(m.getY() - forge.getY()) < 7) {
                hp = Math.max(0, hp - douseRate(m));
                particles(ctx, ParticleTypes.SMOKE, fx, forge.getY() + 4, fz, 10, 0.8, 0.03);
                ctx.level().playSound(null, forge, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.7f);
            }
            for (int i = 0; i < 4; i++) {
                double bx = ctx.origin().getX() + BRAZIERS[i][0] + 0.5;
                double bz = ctx.origin().getZ() + BRAZIERS[i][1] + 0.5;
                if (!lit[i]) {
                    continue;
                }
                double d = flat(m.position(), bx, bz);
                if (d <= BRAZIER_DOUSE) {
                    lit[i] = false;
                    relight[i] = 0;
                    showBrazier(ctx, i);
                    ctx.level().playSound(null, ctx.local(BRAZIERS[i][0], 3, BRAZIERS[i][1]), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 1f);
                } else if (d <= BRAZIER_RANGE && t % 10 == 0) {
                    m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 2, false, true));
                }
            }
        }
        // 火鉢を灯し直す
        for (int i = 0; i < 4; i++) {
            BlockPos c = ctx.local(BRAZIERS[i][0], 0, BRAZIERS[i][1]);
            if (lit[i]) {
                if (t % 6 == 0) {
                    particles(ctx, ParticleTypes.FLAME, c.getX() + 0.5, c.getY() + 4.2, c.getZ() + 0.5, 2, 0.15, 0.01);
                }
                continue;
            }
            boolean any = false;
            for (ServerPlayer p : ps) {
                if (near(p, c, 2.4)) {
                    any = true;
                    break;
                }
            }
            if (any) {
                relight[i]++;
                if (relight[i] >= RELIGHT_TICKS) {
                    lit[i] = true;
                    showBrazier(ctx, i);
                    ctx.level().playSound(null, c, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1f, 1.2f);
                }
            } else {
                relight[i] = Math.max(0, relight[i] - 1);
            }
        }
        // 結果
        if (hp <= 0) {
            fail(ctx, "raid.skyabyss.ER3.lost");
            return;
        }
        if (wavesSpawned > wavesCleared && mobs.isEmpty()) {
            wavesCleared = wavesSpawned;
            nextWave = t + WAVE_GAP;
            if (wavesCleared >= WAVES) {
                done = true;
                opened(ctx);
                particles(ctx, ParticleTypes.LAVA, fx, forge.getY() + 5, fz, 30, 1.0, 0.2);
                ctx.level().playSound(null, forge, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.5f, 0.6f);
            }
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.ER3.defend";
    }

    @Override
    public int counter() {
        return wavesCleared;
    }

    @Override
    public int counterMax() {
        return WAVES;
    }

    @Override
    public String secondaryKey() {
        return "raid.skyabyss.ER3.forge";
    }

    @Override
    public int secondary() {
        return hp;
    }

    @Override
    public int secondaryMax() {
        return FORGE_MAX;
    }

    // 自己テスト用の窓
    int forgeHp() {
        return hp;
    }

    void setForgeHp(int v) {
        hp = v;
    }

    int wavesSpawned() {
        return wavesSpawned;
    }

    boolean brazierLit(int i) {
        return lit[i];
    }

    BlockPos brazierPos(RaidCtx ctx, int i) {
        return ctx.local(BRAZIERS[i][0], 0, BRAZIERS[i][1]);
    }

    BlockPos forgePos() {
        return forge;
    }
}
