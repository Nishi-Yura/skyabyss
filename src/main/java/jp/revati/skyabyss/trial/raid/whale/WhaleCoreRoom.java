package jp.revati.skyabyss.trial.raid.whale;

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
//  - 核の体力 (CORE_MAX) は目的の帯に出る。敵が核を叩く・射る一撃ごとに減り (MELEE_HIT・SHOT_HIT)、敵がいないと少し戻る
//  - 敵は核へ向かう (人には向かわない。殴られた敵だけ RETALIATE のあいだ殴った人を相手にする)。核が壊れたら部屋をやり直す。波は 3・4 体 (人数で増える) と、三つ目は老番兵一体だけ
//    (2026-10-25 の試遊: 骨守りが難しすぎた・三つ目に番兵長が出て、次の段の中ボスとかぶっていた)
//  - 敵の組・出る角度・数は挑戦ごとに少し変わる
//  - 一人のときは核の削れが SOLO_DRAIN 倍 (一人でも越えられる。docs/design/32 の 9 章)
//  - 闘の試練では (bossFollows) 二つの波のあと、番兵長が核を狙って現れる (32 の 9.5)。番兵長は人が近くにいないと核へ歩き、
//    核のそばでは一秒に CAPTAIN_DPS 削る。核は中ボスの段では戻らない。核が壊れたら部屋をやり直す
public final class WhaleCoreRoom extends RaidRoom {
    static final int HX = 16;
    static final int HZ = 16;
    static final int H = 11;
    static final int CORE_MAX = 100;
    static final int WAVES = 3;
    static final double MELEE_RANGE = 3.6;
    static final double SHOOT_RANGE = 11;
    // 核への一撃 (2026-11-10 の声「もう少し削れるように」「近くにいたら減るのではなく、ちゃんと攻撃したら」):
    // 剣・盾・這う者は核のそばで STRIKE_EVERY tick ごとに MELEE_HIT、射手は射程の中で SHOT_EVERY tick ごとに SHOT_HIT。敵がいないと少し戻る
    static final double MELEE_HIT = 3.0;
    static final int STRIKE_EVERY = 20;
    static final double SHOT_HIT = 2.0;
    static final int SHOT_EVERY = 30;
    static final double REGEN_DPS = 0.6;
    // 人に殴られた敵が、その人を相手にする長さ (tick)
    static final int RETALIATE = 80;
    static final int WAVE_GAP = 80;
    // 一人のときの核の削れの倍率
    static final double SOLO_DRAIN = 0.7;
    // 番兵長が核を削る速さ (一秒)・人が近いと核より人を相手にする距離
    static final double CAPTAIN_DPS = 3.5;
    static final double CAPTAIN_ENGAGE = 6;

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
        // 飾り (docs/design/39 の 4.1): 核を肋骨の籠の晶洞に (核のまわり半径 3 は空ける)
        WhaleDecor.wr3(ctx);
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
            if (wave >= waves()) {
                cleared = true;
                if (!bossFollows) {
                    WhaleDecor.exitLamps(ctx, true);
                }
                return;
            }
            nextWave = now + WAVE_GAP;
        }
        pressCore(ctx, now, null);
    }

    // 闘の試練では二つ (三つ目の老番兵の代わりに番兵長)
    int waves() {
        return bossFollows ? WAVES - 1 : WAVES;
    }

    @Override
    public void bossBegin(RaidCtx ctx, Mob boss) {
        ctx.say(net.minecraft.network.chat.Component.translatable("message.skyabyss.raid.WR3.captain"));
    }

    @Override
    public void bossTick(RaidCtx ctx, Mob boss) {
        pressCore(ctx, ctx.now(), boss);
    }

    @Override
    public void bossDone(RaidCtx ctx) {
        WhaleDecor.exitLamps(ctx, true);
    }

    @Override
    public boolean bossKeepsSecondary() {
        return true;
    }

    // 敵が核を削る (captain = 中ボスの段の番兵長。null なら波の段)
    private void pressCore(RaidCtx ctx, long now, @javax.annotation.Nullable Mob captain) {
        Vec3 core = corePos(ctx);
        double dmg = 0;
        double captainDps = 0;
        for (Mob m : ctx.aliveMobs()) {
            if (m == captain) {
                double d = Math.hypot(m.getX() - core.x, m.getZ() - core.z);
                if (d <= MELEE_RANGE + 0.8) {
                    captainDps += CAPTAIN_DPS;
                    if (now % 20 == 0) {
                        m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    }
                }
                // 人が近くにいなければ核へ (人が寄れば人を相手にする)
                if (now % 20 == 0) {
                    boolean near = false;
                    for (ServerPlayer p : ctx.standing()) {
                        if (p.distanceTo(m) < CAPTAIN_ENGAGE) {
                            near = true;
                        }
                    }
                    if (!near && d > MELEE_RANGE) {
                        m.setTarget(null);
                        m.getNavigation().moveTo(core.x, ctx.origin().getY() + 1, core.z, 1.0);
                    }
                }
                continue;
            }
            double dx = m.getX() - core.x;
            double dz = m.getZ() - core.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            boolean ranged = m.getType() == jp.revati.skyabyss.registry.ModEntities.RIB_ARCHER.get();
            // 核を目指す。人に殴られた敵だけ、RETALIATE のあいだその人を相手にする (2026-11-10 の声「核じゃなくてプレイヤーにまっすぐいくので、全然壊れない」)
            boolean provoked = m.getLastHurtByMob() instanceof ServerPlayer && m.tickCount - m.getLastHurtByMobTimestamp() < RETALIATE;
            if (!provoked && now % 5 == 0) {
                if (m.getTarget() instanceof ServerPlayer) {
                    m.setTarget(null);
                }
                double reach = ranged ? SHOOT_RANGE - 2 : MELEE_RANGE - 0.8;
                if (d > reach) {
                    m.getNavigation().moveTo(core.x, ctx.origin().getY() + 1, core.z, 1.1);
                } else {
                    m.getNavigation().stop();
                    m.getLookControl().setLookAt(core.x, core.y + 1, core.z);
                }
            }
            if (provoked) {
                continue;
            }
            // 核を叩く・射る一撃ごとに削れる (近くにいるだけでは削れない。2026-11-10 の声「ちゃんと攻撃したらにして」)
            int phase = Math.floorMod(m.getId() * 7, ranged ? SHOT_EVERY : STRIKE_EVERY);
            if (!ranged && d <= MELEE_RANGE && now % STRIKE_EVERY == phase) {
                m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                strike(ctx, m.position().add(0, 1, 0), core, false);
                dmg += MELEE_HIT;
            } else if (ranged && d <= SHOOT_RANGE && now % SHOT_EVERY == phase) {
                m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                strike(ctx, m.getEyePosition(), core, true);
                dmg += SHOT_HIT;
            }
        }
        if (dmg > 0 || captainDps > 0) {
            // 一人のときは、核の削れを遅くする (一人では四方から来る敵を同時に止められない。二人からは今のまま)
            // 波の敵は一撃の量、番兵長は一秒あたりの削り
            hp -= (dmg + captainDps / 20.0) * (ctx.scale() == 1 ? SOLO_DRAIN : 1.0);
            if (now - lastHurtFx >= 10) {
                lastHurtFx = now;
                ServerLevel level = ctx.level();
                level.sendParticles(ParticleTypes.CRIT, core.x, core.y + 1.5, core.z, 8, 0.8, 0.8, 0.8, 0.1);
                level.playSound(null, BlockPos.containing(core), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
            }
        } else if (hp > 0 && hp < CORE_MAX && waveOut && captain == null) {
            hp = Math.min(CORE_MAX, hp + REGEN_DPS / 20.0);
        }
        setStage(ctx);
        if (hp <= 0) {
            hp = 0;
            ctx.fail("message.skyabyss.raid.WR3.broken");
        }
    }

    // 核への一撃の見た目: 近くは骨の欠片と打つ音、射手は核まで矢の筋
    private static void strike(RaidCtx ctx, Vec3 from, Vec3 core, boolean shot) {
        ServerLevel level = ctx.level();
        Vec3 hit = core.add(0, 1.5, 0);
        if (shot) {
            Vec3 dir = hit.subtract(from);
            int n = (int) Math.max(4, dir.length() * 2);
            for (int i = 0; i <= n; i++) {
                Vec3 p = from.add(dir.scale(i / (double) n));
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 0.8f, 0.9f);
        } else {
            level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.7f, 1.2f);
        }
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                hit.x, hit.y, hit.z, 10, 0.5, 0.5, 0.5, 0.1);
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
        if (w == WAVES && !bossFollows) {
            // 三つ目: 老番兵 (盾の突進・足踏み・笛で射手を呼ぶ) 一体だけ。人数で体力が増える
            Mob elder = ctx.spawnGuard("elder", ctx.cell().floor(0, -HZ + 3));
            if (elder != null) {
                var health = elder.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(Math.min(1024, health.getBaseValue() * (1 + 0.5 * (scale - 1))));
                    elder.setHealth(elder.getMaxHealth());
                }
            }
        } else {
            int base = 2 + w;
            int n = base + (int) Math.round((scale - 1) * (1.0 + 0.5 * w));
            n += ctx.rng().nextInt(2);
            int r = HX - 3;
            for (int i = 0; i < n; i++) {
                double a = angleOffset + Math.PI * 2 * i / n + ctx.rng().nextDouble() * 0.3;
                int x = (int) Math.round(Math.cos(a) * r);
                int z = (int) Math.round(Math.sin(a) * r);
                BlockPos at = ctx.cell().floor(Math.max(-HX + 2, Math.min(HX - 2, x)), Math.max(-HZ + 2, Math.min(HZ - 2, z)));
                String kind = w == 1 ? "sentinel" : (i % 3 == 0 ? "archer" : i % 3 == 1 ? "crawler" : "sentinel");
                ctx.spawnGuard(kind, at);
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
        WhaleDecor.exitLamps(ctx, false);
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
        return waves();
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
