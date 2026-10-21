package jp.revati.skyabyss.facility;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 訓練場の木偶 (docs/design/35 の 3.2)。設計図の木偶 (柵の脚・わらの胴・南瓜の頭) の柱にかぶせて立つ、見えない的。
//   見た目はブロックの木偶のまま (描かない。client: NoopRenderer)。当たりの箱は柱より少し大きいので、柱を打つとこちらに当たる
//   人の攻撃だけを受け、倒れない (受けたあとに体力を戻す)。押されない・動かない。受けた量は TrainingYard が数えて出す
//   column = かぶせている柱の脚 (柵) の位置。柱がなくなったら消える (TrainingYard の見回り)
public class TrainingDummy extends Mob {
    public static final float MAX_HEALTH = 1000f;
    private BlockPos column = BlockPos.ZERO;

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
        setSilent(false);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ARMOR, 0);
    }

    public BlockPos column() {
        return column;
    }

    public void setColumn(BlockPos column) {
        this.column = column.immutable();
    }

    // 人の攻撃 (矢・技を含む) だけを受ける
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        Entity by = source.getEntity();
        return !(by instanceof Player) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return isAlive();
    }

    @Override
    public void tick() {
        super.tick();
        // 柱の上から動かない
        if (!level().isClientSide && column != BlockPos.ZERO) {
            Vec3 at = Vec3.atBottomCenterOf(column);
            if (position().distanceToSqr(at) > 0.01) {
                setPos(at.x, at.y, at.z);
            }
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GRASS_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GRASS_BREAK;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Column", NbtUtils.writeBlockPos(column));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "Column").ifPresent(this::setColumn);
    }
}
