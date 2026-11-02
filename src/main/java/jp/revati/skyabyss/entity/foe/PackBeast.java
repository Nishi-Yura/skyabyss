package jp.revati.skyabyss.entity.foe;

import java.util.function.Supplier;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 群れの獣 (野犬・雷狼) の共通: 近ければ跳びかかって噛む (予告 0.4 秒、前の帯)。群れの仲間がいれば、ときどき一頭が天を仰いで吠える。
// 吠えたときに何が起きるかは種ごと (野犬 = 仲間が一斉に跳びかかる、雷狼 = 狙った人の足もとに雷)。吠えた一頭のまわりの仲間はしばらく吠えない
public abstract class PackBeast extends ContinentFoe {
    public static final int BITE_WARN = 8;
    public static final double BITE_LENGTH = 3.2;
    public static final double BITE_HALF = 0.6;
    public static final double BITE_START = 2.8;
    public static final int BITE_END = BITE_WARN + 14;
    public static final int HOWL_END = 30;
    public static final double PACK_RADIUS = 12;
    public static final int HOWL_EVERY = 220;
    private final Supplier<SoundEvent> biteSound;
    protected boolean howling;
    protected int lastHowl;
    protected LivingEntity howlTarget;

    protected PackBeast(EntityType<? extends Monster> type, Level level, String animPrefix, Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt,
            Supplier<SoundEvent> death, Supplier<SoundEvent> bite) {
        super(type, level, animPrefix, ambient, hurt, death, "bite", "howl", "hurt", "death");
        this.biteSound = bite;
        this.lastHowl = -level.random.nextInt(HOWL_EVERY);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.25, 1.6));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (tickCount - lastHowl > HOWL_EVERY && distance < PACK_RADIUS && distance > BITE_START && packmates() > 0) {
            howling = true;
            howlTarget = target;
            for (PackBeast mate : level().getEntitiesOfClass(PackBeast.class, getBoundingBox().inflate(PACK_RADIUS), e -> e.getType() == getType())) {
                mate.lastHowl = tickCount;
            }
            begin(target, "howl");
            howlStarted(target);
            return;
        }
        if (distance <= BITE_START) {
            bite(target, BITE_LENGTH);
        }
    }

    protected int packmates() {
        return level().getEntitiesOfClass(PackBeast.class, getBoundingBox().inflate(PACK_RADIUS), e -> e != this && e.isAlive() && e.getType() == getType()).size();
    }

    // 跳びかかって噛む: 床の帯 (長さ length) が満ちたら、帯の向きへ跳ぶ
    public void bite(LivingEntity target, double length) {
        howling = false;
        begin(target, "bite");
        Vec3 f = forward();
        double leap = 0.35 + length * 0.08;
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), length, BITE_HALF))
                .floor(getY()).warn(BITE_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> {
                    setDeltaMovement(f.x * leap, 0.3, f.z * leap);
                    hasImpulse = true;
                    playSound(biteSound.get(), 0.9f, 0.9f + random.nextFloat() * 0.2f);
                })
                .onHit((server, player) -> player.hurt(attackSource(), damage()))
                .start();
    }

    // 吠えに応えて跳びかかる (群れの仲間から呼ばれる)。技の最中・遠すぎるときは応えない
    public void answer(LivingEntity target) {
        if (act != 0 || !isAlive() || target == null || !target.isAlive() || distanceTo(target) > 8 || !hasLineOfSight(target)) {
            return;
        }
        setTarget(target);
        bite(target, Math.min(7.5, distanceTo(target) + 1.2));
    }

    // 吠え始め (予告を出すなら、ここで)
    protected void howlStarted(LivingEntity target) {
    }

    // 吠えの山 (HOWL_AT tick)
    protected abstract void howlPeak(LivingEntity target);

    protected int howlAt() {
        return 20;
    }

    @Override
    protected void during(int tick) {
        if (howling && tick == howlAt() && howlTarget != null) {
            howlPeak(howlTarget);
        }
    }

    @Override
    protected int length() {
        return howling ? HOWL_END : BITE_END;
    }

    @Override
    protected void finish() {
        if (howling) {
            cooldown = 10;
        } else {
            retreat = 12;
            cooldown = 24 + random.nextInt(20);
        }
        howling = false;
        howlTarget = null;
    }
}
