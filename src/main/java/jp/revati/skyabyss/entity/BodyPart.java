package jp.revati.skyabyss.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

// 体の長い主の、当たりの部品 (エンダードラゴンの部品と同じ作り。2026-11-15 の声「大きいモブの当たり判定。杖や弓で狙うときに重要」)。
// 本体の当たりの箱は四角 (幅 × 幅) なので、長い体の頭や尾は箱の外に出る。部品は体に沿って並ぶ見えない箱で、
// 叩かれる・矢が当たる・杖の弾が当たると、その傷を本体へ渡す。体の向きに合わせて LongBody が毎 tick 置き直す。保存しない
public class BodyPart extends PartEntity<Mob> {
    private final EntityDimensions size;
    // 本体の足もとからの置き場 (前 = 体の前へ、横 = 体の右へ。ブロック)
    final double forward;
    final double side;

    public BodyPart(Mob parent, double forward, double side, float width, float height) {
        super(parent);
        this.size = EntityDimensions.scalable(width, height);
        this.forward = forward;
        this.side = side;
        this.noPhysics = true;
        refreshDimensions();
    }

    public double forward() {
        return forward;
    }

    public double side() {
        return side;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return getParent().isAlive() && !getParent().isSpectator();
    }

    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().isAlive() && getParent().hurt(source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
