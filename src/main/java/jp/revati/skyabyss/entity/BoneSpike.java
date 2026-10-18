package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.registry.ModEntities;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

// 骨棘の射手が放つ骨の棘。拾えない。刺さった相手は少しのあいだ動きが鈍る
public class BoneSpike extends AbstractArrow {
    public BoneSpike(EntityType<? extends BoneSpike> type, Level level) {
        super(type, level);
    }

    public BoneSpike(Level level, LivingEntity shooter) {
        super(ModEntities.BONE_SPIKE.get(), shooter, level, new ItemStack(Items.BONE), null);
        this.pickup = Pickup.DISALLOWED;
        setBaseDamage(4.0);
    }

    // 射った敵と別のグループの人には、棘を送らない (見えない)
    @Override
    public boolean broadcastToPlayer(net.minecraft.server.level.ServerPlayer player) {
        return super.broadcastToPlayer(player)
                && (getOwner() == null || jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(getOwner(), player));
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), getEffectSource());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.BONE);
    }

    @Override
    public void tick() {
        super.tick();
        // 刺さった棘は、しばらくすると崩れて消える
        if (inGround && inGroundTime > 60) {
            discard();
        }
    }
}
