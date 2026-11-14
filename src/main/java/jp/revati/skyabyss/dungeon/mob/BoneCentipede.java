package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 骨掘りの大百足 (docs/design/55 の 1 章、鯨骨の坑の主。形は 00 の 364 = A 骨殻の大百足)。
//   噛む     前の扇 (予告 0.7 秒)。寄った人へ
//   尾で薙ぐ まわりの輪 (予告 0.9 秒)。寄った人へ、ときどき
//   潜る     床へ潜り、主の間の壁の掘り穴 (holes) の一つから出て、穴から人へ向かう長い帯を突進する (予告 1.3 秒)。
//            掘り穴がなければ (卵から出したとき) 人の足もとの輪から突き上げる。離れた人へ、または間を置いて
//   落石     体力が半分を切ると、吠えて天井を揺らし、人のまわりに落石の輪を降らせる (予告 1.5 秒)
//   潜っている間は見えず、傷を受けない (人は掘り穴の帯の予告を見て避ける)
//   骨の礫   少し離れた人へ、口から骨の礫を三本の帯に吐く (予告 0.9 秒)。間を抜ける
//   突進のあとは頭を打って止まる (大きな隙)。後半 (体力半分から) は突進のあとにもう一度だけ潜り、別の穴から続けて突進する。骨の坑夫を 2 体呼ぶ
// 作り込み (2026-11-11 の声「ボスやアニメーション等々の作り込み、この動画くらいに」。頭は四枚の花弁の口と喉の一つ目):
//   三連咬み 寄った人へ、噛む → 向き直って反対から噛む → 高く立って前へ叩きつけ (帯)。打つ瞬間は 0.7・1.3・2.1 秒、どれも予告つき。最後のあとに隙
//   突き上げ 潜ったあと、掘り穴ではなく人の足もとの円から縦に跳び出し (予告 1.2 秒 + 0.15 秒)、立ち上がって吠え、人へ向き直って前の帯へ倒れ込む
//            (予告 1.4 秒)。後半ほど選ぶ。叩きつけのあとに大きな隙
//   後半に入るときは、顔を天へ突き上げて口を全開にする咆哮 (scream) を見せる
public class BoneCentipede extends DungeonBoss {
    static final int BITE = 1;
    static final int SWEEP = 2;
    static final int BURROW = 3;
    static final int CHARGE = 4;
    static final int ROCKS = 5;
    static final int SPIT = 6;
    static final int COMBO = 7;
    static final int ERUPT = 8;
    public static final int SPIT_WARN = 18;
    public static final double SPIT_LENGTH = 15;
    public static final int BITE_WARN = 14;
    public static final double BITE_RANGE = 6.5;
    public static final int SWEEP_WARN = 18;
    public static final double SWEEP_RADIUS = 7;
    public static final int BURROW_DOWN = 20;
    public static final int BURROW_HIDDEN = 24;
    public static final int CHARGE_WARN = 26;
    public static final double CHARGE_LENGTH = 26;
    public static final int ROCKS_WARN = 30;
    // 三連咬み: 一つ目・二つ目・叩きつけの打つ tick (アニメ combo の 0.70・1.30・2.10 秒)
    public static final int COMBO_HIT1 = 14;
    public static final int COMBO_HIT2 = 26;
    public static final int COMBO_HIT3 = 42;
    public static final double SLAM_LENGTH = 11;
    // 突き上げ: 足もとの円は出る瞬間から ERUPT_RISE tick 後に打ち (アニメ erupt の 0.18 秒)、前への叩きつけはその ERUPT_SLAM tick 後 (1.40 秒)
    public static final int ERUPT_RISE = 3;
    public static final int ERUPT_SLAM = 28;
    public static final double ERUPT_RADIUS = 3.8;
    private int lastCombo = -200;
    private boolean erupt;
    private int lastSweep = -400;
    private int lastBurrow = -200;
    private int lastRocks = -400;
    private Vec3 rise;
    private int lastSpit = -200;
    private boolean chained;

    public BoneCentipede(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_centipede", () -> SoundEvents.SILVERFISH_AMBIENT, () -> SoundEvents.SILVERFISH_HURT, () -> SoundEvents.RAVAGER_DEATH,
                "bite", "combo", "sweep", "spit", "burrow", "emerge", "erupt", "roar", "scream", "intro", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 480)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected boolean holdsAction(String action) {
        return action.equals("burrow");
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (hp() < 0.55f && tickCount - lastRocks > 280) {
            rocks(target);
            return;
        }
        if (distance > 6 && distance <= SPIT_LENGTH && tickCount - lastSpit > 140 && random.nextFloat() < 0.6f) {
            spit(target);
            return;
        }
        if (distance > 9 || tickCount - lastBurrow > 360) {
            burrow(target);
            return;
        }
        if (distance <= BITE_RANGE && tickCount - lastCombo > (phase2 ? 150 : 220) && random.nextFloat() < 0.55f) {
            combo(target);
            return;
        }
        if (distance < SWEEP_RADIUS - 0.5 && tickCount - lastSweep > 160) {
            move = SWEEP;
            lastSweep = tickCount;
            begin(target, "sweep");
            cue("bone_centipede", "sweep", SWEEP_WARN);
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), SWEEP_RADIUS))
                    .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1, getZ(), 12, SWEEP_RADIUS * 0.4, 0.3, SWEEP_RADIUS * 0.4, 0))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.85f);
                        Vec3 away = player.position().subtract(position()).normalize();
                        player.knockback(1.2, -away.x, -away.z);
                    })
                    .start();
            return;
        }
        if (distance <= BITE_RANGE) {
            move = BITE;
            begin(target, "bite");
            cue("bone_centipede", "bite", BITE_WARN);
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BITE_RANGE, Math.toRadians(60)))
                    .floor(getY()).warn(BITE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 1.1f);
                        player.knockback(0.9, -f.x, -f.z);
                    })
                    .start();
        }
    }

    // 骨の礫: 人へ向けた三本の帯 (間は通れる)
    void spit(LivingEntity target) {
        move = SPIT;
        lastSpit = tickCount;
        begin(target, "spit");
        cue("bone_centipede", "spit", SPIT_WARN);
        double base = Telegraph.angleOf(target.position().subtract(position()));
        for (int k = -1; k <= 1; k++) {
            double a = base + k * 0.42;
            Vec3 mouth = position().add(forward().scale(3.0)).add(0, 3.4, 0);
            jp.revati.skyabyss.combat.prop.AttackProps.toss(server(), "bone_shard", this, mouth, position().add(Math.cos(a) * SPIT_LENGTH, 0.4, Math.sin(a) * SPIT_LENGTH),
                    SPIT_WARN + Math.abs(k) * 4, 10, 1.5f, 1.4f, 8);
            Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), a, SPIT_LENGTH, 0.9))
                    .floor(getY()).warn(SPIT_WARN + Math.abs(k) * 4).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                            getX() + Math.cos(a) * SPIT_LENGTH * 0.6, getY() + 0.6, getZ() + Math.sin(a) * SPIT_LENGTH * 0.6, 30, 2.5, 0.3, 2.5, 0.1))
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 0.8f))
                    .start();
        }
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.6f, 0.5f);
    }

    // 潜る (床へ潜り、掘り穴か人の足もとから出て突進する)
    void burrow(LivingEntity target) {
        move = BURROW;
        lastBurrow = tickCount;
        // 掘り穴からの突進か、人の足もとからの突き上げか (後半ほど突き上げ)
        erupt = holes.isEmpty() || random.nextFloat() < (phase2 ? 0.55f : 0.35f);
        begin(target, "burrow");
        cue("bone_centipede", "burrow", BURROW_DOWN + BURROW_HIDDEN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
    }

    // つっかえたら潜って出直す (地を蹴って移る代わり)
    @Override
    protected void unstick(LivingEntity target) {
        if (onGround()) {
            burrow(target);
        } else {
            super.unstick(target);
        }
    }

    // 吠えて天井から落石: 人ごとに輪を一つ、ほかに二つ
    void rocks(LivingEntity target) {
        move = ROCKS;
        lastRocks = tickCount;
        begin(target, "roar");
        cue("bone_centipede", "rocks", ROCKS_WARN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.6f);
        java.util.List<Vec3> spots = new java.util.ArrayList<>();
        for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(28), this::canHit)) {
            spots.add(p.position());
        }
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            spots.add(position().add(Math.cos(a) * (6 + random.nextDouble() * 8), 0, Math.sin(a) * (6 + random.nextDouble() * 8)));
        }
        for (Vec3 s : spots) {
            jp.revati.skyabyss.combat.prop.AttackProps.fall(server(), "rock", this, new Vec3(s.x, getY(), s.z), ROCKS_WARN, 14, 14f, 1.4f, 16);
            Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, 2.6))
                    .floor(getY()).warn(ROCKS_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), s.x, getY() + 5, s.z, 40, 1.0, 1.5, 1.0, 0.1);
                        server.playSound(null, s.x, getY(), s.z, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 0.9f))
                    .start();
        }
    }

    // 三連咬み: 一つ目の扇はここで、二つ目と叩きつけは during で人へ向き直ってから出す
    void combo(LivingEntity target) {
        move = COMBO;
        lastCombo = tickCount;
        begin(target, "combo");
        cue("bone_centipede", "combo", COMBO_HIT3);
        biteCone(target, COMBO_HIT1, 0.9f);
    }

    private void biteCone(LivingEntity target, int warn, float dmg) {
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BITE_RANGE, Math.toRadians(55)))
                .floor(getY()).warn(warn).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.6f, 0.6f))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * dmg);
                    player.knockback(0.7, -f.x, -f.z);
                })
                .start();
    }

    // 前への叩きつけ (三連の締め・突き上げの締め): 前の太い帯。当たると浮く
    private void slamLine(LivingEntity target, int warn, double length, float dmg) {
        Vec3 f = forward();
        double a = Telegraph.angleOf(f);
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), a, length, 2.2))
                .floor(getY()).warn(warn).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> {
                    for (double d = 2; d <= length; d += 1.5) {
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                                getX() + Math.cos(a) * d, getY() + 0.3, getZ() + Math.sin(a) * d, 10, 1.0, 0.2, 1.0, 0.12);
                    }
                    server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.3f, 0.55f);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * dmg);
                    player.setDeltaMovement(player.getDeltaMovement().add(0, 0.7, 0));
                    player.hurtMarked = true;
                })
                .start();
    }

    // 後半に入る: 顔を天へ突き上げて口を全開にする咆哮を見せる
    @Override
    protected void enterPhase2(LivingEntity target) {
        playAction("scream");
        super.enterPhase2(target);
    }

    @Override
    protected void during(int tick) {
        super.during(tick);
        if (move == COMBO) {
            LivingEntity t = getTarget();
            if (t != null && t.isAlive() && (tick == COMBO_HIT1 || tick == COMBO_HIT2)) {
                lockYawToward(t);
                if (tick == COMBO_HIT1) {
                    biteCone(t, COMBO_HIT2 - COMBO_HIT1, 0.9f);
                } else {
                    slamLine(t, COMBO_HIT3 - COMBO_HIT2, SLAM_LENGTH, 1.3f);
                }
            }
            return;
        }
        if (move != BURROW) {
            return;
        }
        LivingEntity target = getTarget();
        if (tick == BURROW_DOWN) {
            // 床の下へ: 見えず、傷を受けない。掘り穴 (なければ人の足もと) へ移る
            setInvisible(true);
            setInvulnerable(true);
            server().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()), getX(), getY() + 0.5, getZ(), 60, 1.5, 0.4, 1.5, 0.1);
            if (!erupt && !holes.isEmpty() && target != null) {
                // 人から遠すぎず近すぎない穴
                Vec3 best = holes.get(random.nextInt(holes.size()));
                for (Vec3 h : holes) {
                    double d = h.distanceTo(target.position());
                    if (d > 9 && d < best.distanceTo(target.position()) + random.nextDouble() * 6) {
                        best = h;
                    }
                }
                rise = best;
            } else {
                rise = target != null ? target.position() : position();
            }
            teleportTo(rise.x, rise.y, rise.z);
            if (erupt && target != null) {
                // 突き上げ: 人の足もとの円 (出る瞬間から ERUPT_RISE tick 後、体が縦に伸びきるときに打つ)
                Telegraph.in(server(), TelegraphShape.circle(rise.x, rise.z, ERUPT_RADIUS))
                        .floor(rise.y).warn(BURROW_HIDDEN + ERUPT_RISE).danger().owner(this).target(target)
                        .victims(this::canHit)
                        .onHit((server, player) -> {
                            player.hurt(attackSource(), damage() * 1.2f);
                            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.9, 0));
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        if (tick == BURROW_DOWN + BURROW_HIDDEN) {
            setInvisible(false);
            setInvulnerable(false);
            server().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 1, getZ(), 50, 1.4, 0.8, 1.4, 0.15);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.4f, 1.3f);
            // 床を割って出る: まわりに骨の棘が突き出る
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + 0.2;
                jp.revati.skyabyss.combat.prop.AttackProps.rise(server(), "bone_spike", this, position().add(Math.cos(a) * 3.2, 0, Math.sin(a) * 3.2), (float) Math.toDegrees(a), 0.8f, 18);
            }
            if (erupt) {
                // 縦に跳び出して吠え、人へ向き直って前へ倒れ込む
                move = ERUPT;
                playAction("erupt");
                cue("bone_centipede", "erupt", ERUPT_SLAM);
                server().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.2f, 0.5f);
                if (target != null && target.isAlive()) {
                    lockYawToward(target);
                    slamLine(target, ERUPT_SLAM, 13, 1.4f);
                }
            } else if (!holes.isEmpty() && target != null && target.isAlive()) {
                // 掘り穴から人へ突進 (RegionBoss.charge: 帯の予告のあと走る)
                cue("bone_centipede", "charge", CHARGE_WARN);
                charge(target, CHARGE, "emerge", CHARGE_WARN, CHARGE_LENGTH, 1.6, 0.9, () -> SoundEvents.RAVAGER_ROAR);
            } else {
                playAction("emerge");
            }
        }
    }

    // 登場: 口先が床を割って覗き、もがきながら頭・首・体と段を踏んで這い出て、抜けきると口を全開に吠え、辺りを睨んで構える (アニメ intro 5.5 秒)
    @Override
    protected String introAnim() {
        return "intro";
    }

    @Override
    protected int introLength() {
        return 110;
    }

    @Override
    protected int introCardAt() {
        return 55;
    }

    @Override
    protected String bossId() {
        return "bone_centipede";
    }

    @Override
    protected String minionId() {
        return "skyabyss:pit_digger";
    }

    @Override
    protected int length() {
        return switch (move) {
            case BITE -> BITE_WARN + 12;
            case SWEEP -> SWEEP_WARN + 12;
            case BURROW -> BURROW_DOWN + BURROW_HIDDEN + 20;
            case CHARGE -> CHARGE_WARN + (int) Math.ceil(CHARGE_LENGTH / 0.9) + 12;
            case SPIT -> SPIT_WARN + 16;
            case COMBO -> COMBO_HIT3 + 14;
            case ERUPT -> BURROW_DOWN + BURROW_HIDDEN + ERUPT_SLAM + 16;
            default -> ROCKS_WARN + 14;
        };
    }

    @Override
    protected void finish() {
        if (isInvisible()) {
            setInvisible(false);
            setInvulnerable(false);
        }
        if (move == COMBO || move == ERUPT) {
            // 叩きつけのあとは頭が床に刺さって止まる (大きな隙)
            opening();
        }
        if (move == CHARGE) {
            // 頭を打って止まる (大きな隙)。後半は一度だけ続けて潜る
            if (phase2 && !chained) {
                chained = true;
                lastBurrow = tickCount - 1000;
                cooldown = 4;
                super.finish();
                return;
            }
            chained = false;
            opening();
        }
        cooldown = switch (move) {
            case CHARGE, ROCKS, ERUPT -> 36;
            case COMBO -> 30;
            case BURROW -> 6;
            default -> 16 + random.nextInt(12);
        };
        super.finish();
    }
}
