package jp.revati.skyabyss.entity.foe;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 熾の山犬 (灰の荒野。2026-11-05 の頁 s_ember_foe = A。docs/design/44): 痩せた灰色の山犬。毛の割れ目から熾が透ける。三、四頭の群れで出る。
// 近ければ跳びかかって噛む (PackBeast の帯)。群れの一頭が座って吠える (1.0 秒) と、まわりの仲間が火の粉を引いて跳び、
// 狙った人の背中側 (向いている向きの反対。左右に散る) へ回り込む。着いてから 0.6 秒おいて、それぞれ床の帯を出して噛みつく。
// 吠え声が合図。振り向けば帯が見えるので、帯の外へ出れば当たらない
public class EmberJackal extends PackBeast {
    public static final double FLANK_DISTANCE = 2.8;
    public static final int FLANK_WAIT = 12;
    public static final double FLANK_SPREAD = 50;
    private int flankWait;
    private LivingEntity flankTarget;

    public EmberJackal(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ember_jackal", MobSounds.EMBER_JACKAL_AMBIENT, MobSounds.EMBER_JACKAL_HURT, MobSounds.EMBER_JACKAL_DEATH, MobSounds.FERAL_DOG_BITE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 22)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override
    protected void howlStarted(LivingEntity target) {
        server().playSound(null, getX(), getY(), getZ(), MobSounds.EMBER_JACKAL_HOWL.get(), SoundSource.HOSTILE, 1.5f, 1f);
    }

    @Override
    protected void howlPeak(LivingEntity target) {
        server().sendParticles(ParticleTypes.LAVA, getX(), getY() + 1.0, getZ(), 6, 0.3, 0.2, 0.3, 0.0);
        List<EmberJackal> mates = new ArrayList<>(level().getEntitiesOfClass(EmberJackal.class, getBoundingBox().inflate(PACK_RADIUS),
                e -> e != this && e.isAlive() && e.act == 0 && e.flankWait == 0));
        // 背中側: 人の向きの反対。左右へ交互に散らす (0, +50, -50, +100 ...)
        float back = target.getYRot() + 180f;
        for (int i = 0; i < mates.size(); i++) {
            int side = (i + 1) / 2;
            float yaw = back + (i % 2 == 1 ? 1 : -1) * side * (float) FLANK_SPREAD;
            double rad = Math.toRadians(yaw);
            Vec3 spot = new Vec3(target.getX() - Math.sin(rad) * FLANK_DISTANCE, target.getY(), target.getZ() + Math.cos(rad) * FLANK_DISTANCE);
            mates.get(i).flank(spot, target);
        }
    }

    // 回り込む: 狙った所へ弧を描いて跳ぶ (空気の抵抗で 10 tick ほどで着く)。着いたら FLANK_WAIT tick おいて噛む
    public void flank(Vec3 spot, LivingEntity target) {
        Vec3 d = spot.subtract(position());
        double k = Mth.clamp(d.horizontalDistance() * 0.15, 0.0, 1.3) / Math.max(0.01, d.horizontalDistance());
        setDeltaMovement(d.x * k, 0.42, d.z * k);
        hasImpulse = true;
        setYRot((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f);
        setTarget(target);
        flankTarget = target;
        flankWait = FLANK_WAIT + 10;
        server().sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.6, getZ(), 8, 0.3, 0.2, 0.3, 0.02);
    }

    public boolean flanking() {
        return flankWait > 0;
    }

    @Override
    protected void customServerAiStep() {
        if (flankWait > 0) {
            if (tickCount % 2 == 0) {
                server().sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.5, getZ(), 1, 0.15, 0.1, 0.15, 0.0);
            }
            getNavigation().stop();
            if (--flankWait == 0) {
                LivingEntity t = flankTarget;
                flankTarget = null;
                if (t != null && t.isAlive()) {
                    answer(t);    // 噛む帯は begin が狙った人へ向き直してから出す
                }
            }
            return;
        }
        super.customServerAiStep();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }
}
