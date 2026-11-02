package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 雷狼 (docs/design/44): 嵐の高地の夜に群れで出る。近ければ素早く跳びかかって噛む。群れの一頭が吠える (予告 1.2 秒) と、
// 狙った人の足もとに円が出て、満ちると雷が落ちる (雷は見た目だけ。傷は円の中の人だけ、火も付かない)
public class ThunderWolf extends PackBeast {
    public static final int STRIKE_WARN = 24;
    public static final double STRIKE_RADIUS = 2.2;

    public ThunderWolf(EntityType<? extends Monster> type, Level level) {
        super(type, level, "thunder_wolf", MobSounds.THUNDER_WOLF_AMBIENT, MobSounds.THUNDER_WOLF_HURT, MobSounds.THUNDER_WOLF_DEATH, MobSounds.FERAL_DOG_BITE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 22)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override
    protected void howlStarted(LivingEntity target) {
        server().playSound(null, getX(), getY(), getZ(), MobSounds.THUNDER_WOLF_HOWL.get(), SoundSource.HOSTILE, 1.6f, 1f);
        double x = target.getX();
        double z = target.getZ();
        double y = target.getY();
        Telegraph.in(server(), TelegraphShape.circle(x, z, STRIKE_RADIUS))
                .floor(y).warn(STRIKE_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
                    if (bolt != null) {
                        bolt.moveTo(x, y, z);
                        bolt.setVisualOnly(true);
                        server.addFreshEntity(bolt);
                    }
                    server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.3, z, 30, STRIKE_RADIUS * 0.4, 0.2, STRIKE_RADIUS * 0.4, 0.2);
                })
                .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.3f))
                .start();
    }

    @Override
    protected void howlPeak(LivingEntity target) {
        server().sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 12, 0.4, 0.4, 0.4, 0.1);
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }
}
