package jp.revati.skyabyss.dungeon.mob;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 背晶の大蜥蜴 (docs/design/55 の 1 章、雷晶の空洞の主。形は 00 の 366 = A 背晶の大蜥蜴)。
//   噛む      前の扇 (予告 0.7 秒)
//   尾で薙ぐ  後ろの扇 (予告 0.8 秒)。後ろに人がいるとき
//   突進      前の長い帯 (予告 1.2 秒)。離れた人へ
//   溜まり    技を一つ出すたびに背の結晶が溜まる (CHARGE_MAX)。満ちると後ろ脚で立って雷を放つ:
//             足もとの輪 (半径 6) と、人ごとの雷の輪 (予告 1.6 秒)。放つと溜まりは 0 に戻る
//   雷の走り  背晶から床へ十字の四本の雷の帯 (予告 1.1 秒)。後半は斜めの十字が続く
//   放電のあとは力が抜けて大きな隙。後半 (体力半分から) は突進が二度続く。晶の目玉を 2 体呼ぶ
//   主の間の結晶 (印 crystal_*) に人が技を当てて満たすと、大蜥蜴の溜まりが抜ける (Mechanics から drain)
// 作り込み (2026-11-11 の声「他のボスたちもこの方向性で」。骨掘りの大百足と同じ方向):
//   雷の息    のけぞって喉を光らせ (予告 1.0 秒)、首を左から右へ振って前の二枚の扇を 0.75 秒焼く。寄った人へ
//   跳びかかり 深く沈んで跳び上がり、人のいた所の円へ落ちる (予告 1.2 秒)、続けて外への輪の衝撃。離れた人へ
//   登場は床を割って爪が掛かり、頭が這い出て辺りを嗅ぎ、体を引き抜いて土を払い、立ち上がって吠える (intro)。後半に入るときも吠える (scream)
public class CrystalLizard extends DungeonBoss {
    static final int BITE = 1;
    static final int TAIL = 2;
    static final int DASH = 3;
    static final int CHARGE_UP = 4;
    static final int BOLTS = 5;
    static final int BREATH = 6;
    static final int POUNCE = 7;
    public static final int BOLTS_WARN = 22;
    public static final double BOLTS_LENGTH = 15;
    public static final int BITE_WARN = 14;
    public static final double BITE_RANGE = 6;
    public static final int TAIL_WARN = 16;
    public static final double TAIL_RANGE = 7;
    public static final int DASH_WARN = 24;
    public static final double DASH_LENGTH = 16;
    public static final int CHARGE_WARN = 32;
    public static final int CHARGE_MAX = 5;
    public static final int BREATH_WARN = 20;
    public static final int BREATH_ACTIVE = 15;
    public static final double BREATH_RANGE = 9;
    public static final int POUNCE_WARN = 24;
    private int lastBreath = -200;
    private int lastPounce = -300;
    private int charge;
    private int lastDash = -200;
    private int lastBolts = -200;
    private boolean dashedTwice;

    public CrystalLizard(EntityType<? extends Monster> type, Level level) {
        super(type, level, "crystal_lizard", () -> SoundEvents.HOGLIN_AMBIENT, () -> SoundEvents.RAVAGER_HURT, () -> SoundEvents.RAVAGER_DEATH,
                "bite", "tail", "dash", "charge_up", "discharge", "breath", "pounce", "intro", "scream", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800)
                .add(Attributes.ATTACK_DAMAGE, 18)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected boolean holdsAction(String action) {
        return action.equals("charge_up");
    }

    // 主の間の結晶が満ちた: 溜まりが抜ける (Mechanics.strike から)
    public void drain() {
        charge = 0;
        if (!level().isClientSide) {
            server().sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2, getZ(), 60, 1.6, 1.0, 1.6, 0.3);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2f, 0.7f);
        }
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (charge >= CHARGE_MAX) {
            discharge(target);
            return;
        }
        Vec3 f = forward();
        if (tickCount - lastBolts > 200 && distance < BOLTS_LENGTH) {
            bolts(target);
            charge++;
            return;
        }
        if (distance > 8 && distance < 22 && tickCount - lastPounce > 240) {
            move = POUNCE;
            lastPounce = tickCount;
            leapAt(target, "pounce", POUNCE_WARN, 3.6, 1.3f);
            charge++;
            return;
        }
        if (distance <= BREATH_RANGE - 1 && tickCount - lastBreath > 220 && random.nextFloat() < 0.5f) {
            move = BREATH;
            lastBreath = tickCount;
            spray(target, "breath", BREATH_WARN, BREATH_RANGE, 50, BREATH_ACTIVE, 0.9f, ParticleTypes.ELECTRIC_SPARK);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 0.5f);
            charge++;
            return;
        }
        Vec3 to = target.position().subtract(position());
        boolean behind = to.x * f.x + to.z * f.z < 0;
        if (behind && distance <= TAIL_RANGE) {
            move = TAIL;
            begin(target, "tail");
            cue("crystal_lizard", "tail", TAIL_WARN);
            // 尾は後ろの扇 (向きは人の側へ固定せず、体の後ろ)
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f.scale(-1)), TAIL_RANGE, Math.toRadians(110)))
                    .floor(getY()).warn(TAIL_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.9f);
                        player.knockback(1.5, f.x, f.z);
                    })
                    .start();
            charge++;
            return;
        }
        if (distance > 7 && distance <= DASH_LENGTH && tickCount - lastDash > 160) {
            lastDash = tickCount;
            cue("crystal_lizard", "dash", DASH_WARN);
            charge(target, DASH, "dash", DASH_WARN, DASH_LENGTH, 1.5, 0.85, () -> SoundEvents.RAVAGER_ROAR);
            charge++;
            return;
        }
        if (distance <= BITE_RANGE) {
            move = BITE;
            begin(target, "bite");
            cue("crystal_lizard", "bite", BITE_WARN);
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BITE_RANGE, Math.toRadians(70)))
                    .floor(getY()).warn(BITE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.1f))
                    .start();
            charge++;
        }
    }

    // 雷の走り: 十字の四本 (後半は斜めの十字が続く)
    void bolts(LivingEntity target) {
        move = BOLTS;
        lastBolts = tickCount;
        begin(target, "discharge");
        cue("crystal_lizard", "bolts", BOLTS_WARN);
        double base = Telegraph.angleOf(forward());
        for (int set = 0; set < (phase2 ? 2 : 1); set++) {
            for (int k = 0; k < 4; k++) {
                double a = base + k * Math.PI / 2 + set * Math.PI / 4;
                Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), a, BOLTS_LENGTH, 1.0))
                        .floor(getY()).warn(BOLTS_WARN + set * 14).danger().owner(this).target(target)
                        .victims(this::canHit)
                        .onImpact(server -> {
                            for (int i = 2; i < BOLTS_LENGTH; i += 3) {
                                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(a) * i, getY() + 0.4, getZ() + Math.sin(a) * i, 6, 0.3, 0.3, 0.3, 0.2);
                            }
                        })
                        .onHit((server, player) -> player.hurt(attackSource(), damage() * 0.85f))
                        .start();
            }
        }
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 1.4f, 1.4f);
    }

    void discharge(LivingEntity target) {
        move = CHARGE_UP;
        charge = 0;
        begin(target, "charge_up");
        cue("crystal_lizard", "discharge", CHARGE_WARN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 0.6f);
        List<Vec3> spots = new ArrayList<>();
        spots.add(position());
        for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(30), this::canHit)) {
            spots.add(p.position());
        }
        for (int i = 0; i < spots.size(); i++) {
            Vec3 s = spots.get(i);
            double r = i == 0 ? 6.0 : 3.0;
            Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, r))
                    .floor(getY()).warn(CHARGE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.x, getY() + 1, s.z, 80, r * 0.4, 1.2, r * 0.4, 0.4);
                        server.playSound(null, s.x, getY(), s.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.4f, 1.2f);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.0f))
                    .start();
        }
    }

    @Override
    protected void during(int tick) {
        super.during(tick);
        if (move == CHARGE_UP) {
            if (tick % 4 == 0) {
                server().sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.6, getZ(), 6, 1.2, 0.5, 1.6, 0.1);
            }
            if (tick == CHARGE_WARN) {
                playAction("discharge");
            }
        }
    }

    // 登場: 爪が掛かり、頭が這い出て、体を引き抜き、立ち上がって吠える (アニメ intro 5.0 秒)
    @Override
    protected String introAnim() {
        return "intro";
    }

    @Override
    protected int introLength() {
        return 100;
    }

    @Override
    protected int introCardAt() {
        return 68;
    }

    @Override
    protected void enterPhase2(LivingEntity target) {
        playAction("scream");
        super.enterPhase2(target);
    }

    @Override
    protected String bossId() {
        return "crystal_lizard";
    }

    @Override
    protected String minionId() {
        return "skyabyss:crystal_eye";
    }

    @Override
    protected int length() {
        return switch (move) {
            case BITE -> BITE_WARN + 10;
            case TAIL -> TAIL_WARN + 12;
            case DASH -> DASH_WARN + (int) Math.ceil(DASH_LENGTH / 0.85) + 12;
            case BOLTS -> BOLTS_WARN + (phase2 ? 14 : 0) + 12;
            case BREATH -> BREATH_WARN + 10 + BREATH_ACTIVE + 12;
            case POUNCE -> POUNCE_WARN + 22;
            default -> CHARGE_WARN + 20;
        };
    }

    @Override
    protected void finish() {
        if (move == CHARGE_UP) {
            // 放電のあとは力が抜ける (大きな隙)
            opening();
        }
        if (move == DASH && phase2 && !dashedTwice) {
            // 後半: 突進がもう一度続く
            dashedTwice = true;
            lastDash = tickCount - 1000;
            cooldown = 4;
            super.finish();
            return;
        }
        if (move == DASH) {
            dashedTwice = false;
        }
        cooldown = move == CHARGE_UP || move == DASH ? 30 : 14 + random.nextInt(10);
        super.finish();
    }
}
