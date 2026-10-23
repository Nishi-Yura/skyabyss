package jp.revati.skyabyss.trial.raid.whale;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

// 闘 WR6「縮む輪」: 広間の床の「安全な輪」が、時間とともに縮む。輪の外は赤く染まり、いると傷を受ける (敵も)。
//  - 輪は最初は広間いっぱい (半径 14)、始まって少しあと (GRACE) から一定の速さで縮み、最後に半径 4.5 の輪になる。縁の床は赤い石の線になる
//  - 輪の中で三つの波を倒すと越える。波は 4・6・8 体ほど (人数で増える)。最後の波に番兵長の影。波は前の波を倒した少しあとに輪の中へ湧く
//  - 倒すのが遅いほど狭い場所で戦うことになる。人数がいると早く倒せる。全員が倒れたら部屋のやり直し (輪も床も最初へ)
public final class WhaleRingRoom extends RaidRoom {
    static final int HX = 15;
    static final int HZ = 15;
    static final int H = 11;
    static final double R0 = 14.0;
    static final double RMIN = 4.5;
    static final int GRACE = 140;
    static final int SHRINK_TICKS = 2700;
    static final int WAVES = 3;
    static final int WAVE_GAP = 100;
    static final float PLAYER_DAMAGE = 2.5f;
    static final float MOB_DAMAGE = 2.0f;
    static final int DAMAGE_EVERY = 10;
    static final double BAND = 1.2;

    private record Cell(int x, int z, double d, BlockState orig) {
    }

    private final List<Cell> cells = new ArrayList<>();
    private int bandN;
    private int brickN;
    private long startAt = Long.MAX_VALUE;
    private long nextDamage;
    private long nextWave;
    private int wave;
    private boolean waveOut;
    private boolean cleared;
    private boolean active;
    private double radius = R0;
    private long lastNow;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.BONE));
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        // 中央の歌う石 (骨の輪) と、縁の肋骨の柱
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double d = Math.hypot(x, z);
                if (d > 1.6 && d <= 2.6) {
                    RaidKit.set(level, c.origin().offset(x, 0, z), bone);
                }
            }
        }
        RaidKit.fill(level, c.origin(), c.origin(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        for (int i = 0; i < 12; i++) {
            double a = Math.PI / 6 * i;
            int x = (int) Math.round(Math.cos(a) * 13.5);
            int z = (int) Math.round(Math.sin(a) * 13.5);
            RaidKit.fill(level, c.origin().offset(x, 1, z), c.origin().offset(x, 3, z), bone);
            RaidKit.set(level, c.origin().offset(x, 4, z), Blocks.LANTERN.defaultBlockState());
        }
        for (int z : new int[] {-8, 0, 8}) {
            RaidKit.fill(level, c.origin().offset(-HX, H, z), c.origin().offset(HX, H, z), bone);
            RaidKit.fill(level, c.origin().offset(-HX, H - 3, z), c.origin().offset(-HX, H, z), bone);
            RaidKit.fill(level, c.origin().offset(HX, H - 3, z), c.origin().offset(HX, H, z), bone);
        }
        // 床の元の姿を覚える (遠い順)
        for (int x = -HX; x <= HX; x++) {
            for (int z = -HZ; z <= HZ; z++) {
                cells.add(new Cell(x, z, Math.hypot(x, z), level.getBlockState(c.origin().offset(x, 0, z))));
            }
        }
        cells.sort(Comparator.comparingDouble(Cell::d).reversed());
        radius = R0;
        paint(ctx);
    }

    // 半径に合わせて床を染める (縁の帯 = 赤い石の線、その外 = 赤いレンガ)。縮むだけなので、先へ進む印だけ持てばよい
    private void paint(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        BlockState band = Blocks.REDSTONE_BLOCK.defaultBlockState();
        BlockState brick = Blocks.RED_NETHER_BRICKS.defaultBlockState();
        while (bandN < cells.size() && cells.get(bandN).d() > radius) {
            Cell c = cells.get(bandN++);
            RaidKit.set(level, ctx.origin().offset(c.x(), 0, c.z()), band);
        }
        while (brickN < cells.size() && cells.get(brickN).d() > radius + BAND) {
            Cell c = cells.get(brickN++);
            RaidKit.set(level, ctx.origin().offset(c.x(), 0, c.z()), brick);
        }
    }

    @Override
    public void start(RaidCtx ctx) {
        startAt = ctx.now() + GRACE;
        nextWave = ctx.now() + 80;
        nextDamage = 0;
        active = true;
    }

    public double radiusAt(long now) {
        if (!active || now < startAt) {
            return R0;
        }
        return Math.max(RMIN, R0 - (R0 - RMIN) * (now - startAt) / SHRINK_TICKS);
    }

    private double dist(RaidCtx ctx, double x, double z) {
        return Math.hypot(x - ctx.origin().getX() - 0.5, z - ctx.origin().getZ() - 0.5);
    }

    @Override
    public void tick(RaidCtx ctx) {
        long now = ctx.now();
        lastNow = now;
        ServerLevel level = ctx.level();
        radius = radiusAt(now);
        paint(ctx);
        edgeFx(ctx, now);
        // 輪の外の傷
        if (now >= nextDamage) {
            nextDamage = now + DAMAGE_EVERY;
            for (ServerPlayer p : ctx.standing()) {
                if (!p.isCreative() && !p.isSpectator() && ctx.cell().inner().contains(p.position()) && dist(ctx, p.getX(), p.getZ()) > radius) {
                    p.invulnerableTime = 0;
                    p.hurt(level.damageSources().magic(), PLAYER_DAMAGE);
                }
            }
            for (Mob m : ctx.aliveMobs()) {
                if (dist(ctx, m.getX(), m.getZ()) > radius + 0.5) {
                    m.invulnerableTime = 0;
                    m.hurt(level.damageSources().magic(), MOB_DAMAGE);
                }
            }
        }
        // 波
        if (cleared) {
            return;
        }
        if (!waveOut) {
            if (now >= nextWave) {
                wave++;
                spawnWave(ctx, wave);
                waveOut = true;
            }
        } else if (ctx.aliveMobs().isEmpty()) {
            waveOut = false;
            if (wave >= WAVES) {
                cleared = true;
                return;
            }
            nextWave = now + WAVE_GAP;
        }
    }

    private void edgeFx(RaidCtx ctx, long now) {
        if (now % 2 != 0) {
            return;
        }
        ServerLevel level = ctx.level();
        DustParticleOptions red = new DustParticleOptions(new Vector3f(0.85f, 0.05f, 0.05f), 1.7f);
        int n = (int) Math.max(24, Math.round(radius * 5));
        double cx = ctx.origin().getX() + 0.5;
        double cz = ctx.origin().getZ() + 0.5;
        double y = ctx.origin().getY() + 1.15;
        double phase = (now % 200) / 200.0 * Math.PI * 2 / n;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + phase;
            double x = cx + Math.cos(a) * (radius + 0.3);
            double z = cz + Math.sin(a) * (radius + 0.3);
            level.sendParticles(red, x, y, z, 1, 0, 0, 0, 0);
            if (i % 3 == 0) {
                level.sendParticles(red, x, y + 0.9, z, 1, 0, 0.1, 0, 0);
            }
        }
        if (now % 20 == 0 && active && now >= startAt && radius > RMIN) {
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, cx, y + 0.5, cz, 6, radius * 0.5, 0.3, radius * 0.5, 0);
        }
    }

    private void spawnWave(RaidCtx ctx, int w) {
        int scale = ctx.scale();
        int base = 2 + 2 * w;
        int n = base + (int) Math.round((scale - 1) * (1.0 + 0.5 * w));
        n += ctx.rng().nextInt(2);
        double r = Math.max(2.0, Math.min(radius - 2.5, 10.0));
        double off = ctx.rng().nextDouble() * Math.PI;
        for (int i = 0; i < n; i++) {
            double a = off + Math.PI * 2 * i / n + ctx.rng().nextDouble() * 0.3;
            int x = (int) Math.round(Math.cos(a) * r);
            int z = (int) Math.round(Math.sin(a) * r);
            String kind = w == 1 ? (i % 3 == 0 ? "archer" : "sentinel") : (i % 3 == 0 ? "archer" : i % 3 == 1 ? "crawler" : "sentinel");
            ctx.spawnGuard(kind, ctx.cell().floor(x, z));
        }
        if (w == WAVES) {
            Mob shadow = ctx.spawnGuard("captain", ctx.cell().floor(0, 0));
            if (shadow instanceof WhaleboneCaptain) {
                var health = shadow.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(Math.min(1024, health.getBaseValue() * 0.6 * StoneWhaleHeart.scaleFor(scale)));
                    shadow.setHealth(shadow.getMaxHealth());
                }
            }
        }
        for (ServerPlayer p : ctx.players()) {
            Notice.bar(p, Component.translatable("message.skyabyss.raid.WR6.wave", w));
        }
    }

    @Override
    public boolean isComplete() {
        return cleared;
    }

    @Override
    public void reset(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        for (int i = 0; i < bandN; i++) {
            Cell c = cells.get(i);
            RaidKit.set(level, ctx.origin().offset(c.x(), 0, c.z()), c.orig());
        }
        bandN = 0;
        brickN = 0;
        active = false;
        startAt = Long.MAX_VALUE;
        radius = R0;
        wave = 0;
        waveOut = false;
        cleared = false;
        paint(ctx);
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.WR6.fight";
    }

    @Override
    public int counter() {
        return Math.max(1, wave);
    }

    @Override
    public int counterMax() {
        return WAVES;
    }

    @Override
    public String secondaryKey() {
        return "raid.skyabyss.WR6.ring";
    }

    @Override
    public int secondary() {
        return (int) Math.round(radius);
    }

    @Override
    public int secondaryMax() {
        return (int) Math.round(R0);
    }

    @Override
    public int timerTicks() {
        if (!active || startAt == Long.MAX_VALUE) {
            return -1;
        }
        long end = startAt + SHRINK_TICKS;
        return lastNow >= end ? -1 : (int) Math.max(0, end - lastNow);
    }

    // ---- 試験用
    public double radius() {
        return radius;
    }

    public int wave() {
        return wave;
    }

    public long shrinkStart() {
        return startAt;
    }

    public boolean outside(RaidCtx ctx, double x, double z) {
        return dist(ctx, x, z) > radius;
    }
}
