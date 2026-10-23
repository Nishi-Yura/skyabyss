package jp.revati.skyabyss.trial.raid.whale;

import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 闘 WR3「鯨の核を守る」: 部屋の中央の核を、三つの波から守る。
//  - 核の体力 (CORE_MAX) は目的の帯に出る。敵が核のそばにいるあいだ減り (剣・盾は近くで一秒に 1.2、射手は離れて 0.6)、敵がいないと少し戻る
//  - 敵は核へ向かう (人を狙える間は人を狙う)。核が壊れたら部屋をやり直す。波は 4・6・8 体 (人数で増える)、最後の波に番兵長の影
//  - 敵の組・出る角度・数は挑戦ごとに少し変わる
//  - 一人のときは核の削れが SOLO_DRAIN 倍 (一人でも越えられる。docs/design/32 の 9 章)
public final class WhaleCoreRoom extends RaidRoom {
    static final int HX = 16;
    static final int HZ = 16;
    static final int H = 11;
    static final int CORE_MAX = 100;
    static final int WAVES = 3;
    static final double MELEE_RANGE = 3.6;
    static final double SHOOT_RANGE = 11;
    static final double MELEE_DPS = 1.2;
    static final double SHOT_DPS = 0.6;
    static final double REGEN_DPS = 0.6;
    static final int WAVE_GAP = 80;
    // 一人のときの核の削れの倍率
    static final double SOLO_DRAIN = 0.55;

    private double hp = CORE_MAX;
    private int wave;
    private boolean waveOut;
    private boolean cleared;
    private long nextWave;
    private long lastHurtFx;
    private int stage = 2;
    private double angleOffset;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.BONE));
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        // 核の台座 (5×5 の低い段) と核 (3×3×3 の骨の柱の上に光る球)
        RaidKit.fill(level, c.origin().offset(-2, 1, -2), c.origin().offset(2, 1, 2), Blocks.POLISHED_ANDESITE.defaultBlockState());
        RaidKit.fill(level, c.origin().offset(-1, 2, -1), c.origin().offset(1, 4, 1), bone);
        RaidKit.fill(level, c.origin().offset(0, 5, 0), c.origin().offset(0, 5, 0), Blocks.SEA_LANTERN.defaultBlockState());
        RaidKit.fill(level, c.origin().offset(-1, 5, 0), c.origin().offset(1, 5, 0), Blocks.SEA_LANTERN.defaultBlockState());
        RaidKit.fill(level, c.origin().offset(0, 5, -1), c.origin().offset(0, 5, 1), Blocks.SEA_LANTERN.defaultBlockState());
        // まわりの低い骨の柱 (飾り・敵の足場にならない高さ)
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            int x = (int) Math.round(Math.cos(a) * 7);
            int z = (int) Math.round(Math.sin(a) * 7);
            RaidKit.fill(level, c.origin().offset(x, 1, z), c.origin().offset(x, 2, z), bone);
        }
        angleOffset = ctx.rng().nextDouble() * Math.PI / 4;
    }

    @Override
    public void start(RaidCtx ctx) {
        nextWave = ctx.now() + 60;
        wave = 0;
    }

    @Override
    public void tick(RaidCtx ctx) {
        long now = ctx.now();
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
        Vec3 core = corePos(ctx);
        double dmg = 0;
        for (Mob m : ctx.aliveMobs()) {
            double dx = m.getX() - core.x;
            double dz = m.getZ() - core.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            boolean ranged = m.getType() == jp.revati.skyabyss.registry.ModEntities.RIB_ARCHER.get();
            if (!ranged && d <= MELEE_RANGE) {
                dmg += MELEE_DPS;
            } else if (ranged && d <= SHOOT_RANGE) {
                dmg += SHOT_DPS;
            }
            // 狙う人がいない (遠い) 敵は核へ向かう
            if (now % 20 == 0 && (!ranged || d > SHOOT_RANGE - 2)) {
                var tgt = m.getTarget();
                if (tgt == null || !tgt.isAlive() || tgt.distanceToSqr(m) > 12 * 12) {
                    m.getNavigation().moveTo(core.x, ctx.origin().getY() + 1, core.z, 1.1);
                }
            }
        }
        if (dmg > 0) {
            // 一人のときは、核の削れを遅くする (一人では四方から来る敵を同時に止められない。二人からは今のまま)
            hp -= dmg * (ctx.scale() == 1 ? SOLO_DRAIN : 1.0) / 20.0;
            if (now - lastHurtFx >= 10) {
                lastHurtFx = now;
                ServerLevel level = ctx.level();
                level.sendParticles(ParticleTypes.CRIT, core.x, core.y + 1.5, core.z, 8, 0.8, 0.8, 0.8, 0.1);
                level.playSound(null, BlockPos.containing(core), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
            }
        } else if (hp > 0 && hp < CORE_MAX && waveOut) {
            hp = Math.min(CORE_MAX, hp + REGEN_DPS / 20.0);
        }
        setStage(ctx);
        if (hp <= 0) {
            hp = 0;
            ctx.fail("message.skyabyss.raid.WR3.broken");
        }
    }

    private Vec3 corePos(RaidCtx ctx) {
        return new Vec3(ctx.origin().getX() + 0.5, ctx.origin().getY() + 2, ctx.origin().getZ() + 0.5);
    }

    // 核の光は体力で暗くなる
    private void setStage(RaidCtx ctx) {
        int s = hp > CORE_MAX * 0.5 ? 2 : hp > CORE_MAX * 0.25 ? 1 : 0;
        if (s == stage) {
            return;
        }
        stage = s;
        BlockState st = s == 2 ? Blocks.SEA_LANTERN.defaultBlockState() : s == 1 ? Blocks.PRISMARINE.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState();
        RaidKit.fill(ctx.level(), ctx.origin().offset(0, 5, 0), ctx.origin().offset(0, 5, 0), st);
        RaidKit.fill(ctx.level(), ctx.origin().offset(-1, 5, 0), ctx.origin().offset(1, 5, 0), st);
        RaidKit.fill(ctx.level(), ctx.origin().offset(0, 5, -1), ctx.origin().offset(0, 5, 1), st);
    }

    private void spawnWave(RaidCtx ctx, int w) {
        int scale = ctx.scale();
        int base = 2 + 2 * w;
        int n = base + (int) Math.round((scale - 1) * (1.0 + 0.5 * w));
        n += ctx.rng().nextInt(2);
        int r = HX - 3;
        for (int i = 0; i < n; i++) {
            double a = angleOffset + Math.PI * 2 * i / n + ctx.rng().nextDouble() * 0.3;
            int x = (int) Math.round(Math.cos(a) * r);
            int z = (int) Math.round(Math.sin(a) * r);
            BlockPos at = ctx.cell().floor(Math.max(-HX + 2, Math.min(HX - 2, x)), Math.max(-HZ + 2, Math.min(HZ - 2, z)));
            String kind = w == 1 ? "sentinel" : (i % 3 == 0 ? "archer" : i % 3 == 1 ? "crawler" : "sentinel");
            if (w == 3 && i % 4 == 0) {
                kind = "archer";
            }
            ctx.spawnGuard(kind, at);
        }
        if (w == WAVES) {
            Mob shadow = ctx.spawnGuard("captain", ctx.cell().floor(0, -HZ + 3));
            if (shadow instanceof WhaleboneCaptain) {
                var health = shadow.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(Math.min(1024, health.getBaseValue() * 0.6 * StoneWhaleHeart.scaleFor(scale)));
                    shadow.setHealth(shadow.getMaxHealth());
                }
            }
        }
        for (ServerPlayer p : ctx.players()) {
            jp.revati.skyabyss.notice.Notice.bar(p, net.minecraft.network.chat.Component.translatable("message.skyabyss.raid.WR3.wave", w));
        }
    }

    @Override
    public boolean isComplete() {
        return cleared;
    }

    @Override
    public void reset(RaidCtx ctx) {
        hp = CORE_MAX;
        wave = 0;
        waveOut = false;
        cleared = false;
        stage = 1;
        setStage(ctx);
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.WR3.defend";
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
        return "raid.skyabyss.WR3.core";
    }

    @Override
    public int secondary() {
        return (int) Math.ceil(hp);
    }

    @Override
    public int secondaryMax() {
        return CORE_MAX;
    }

    // 試験用
    public int wave() {
        return wave;
    }

    public double coreHp() {
        return hp;
    }

    public void damageCore(double amount) {
        hp = Math.max(0, hp - amount);
    }
}
