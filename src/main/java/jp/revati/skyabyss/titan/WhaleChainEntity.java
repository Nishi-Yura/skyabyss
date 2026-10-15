package jp.revati.skyabyss.titan;

import jp.revati.skyabyss.ModText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// 岩鯨の背から地上へ垂れた鎖の、つかめる部分 (地面近くの数ブロック)。
// 見た目は岩鯨の描画側で描き、これは右クリックを受け取るための見えない当たり判定。保存はしない
public class WhaleChainEntity extends Entity {
    public WhaleChainEntity(EntityType<? extends WhaleChainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    // 岩鯨が呼び直された・読み込みが外れたなどで、垂れている鎖がなくなった当たり判定は消す
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 40 == 0 && getVehicleWhale() == null) {
            discard();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server && getVehicleWhale() instanceof StoneWhaleEntity whale) {
            TitanTravel.startLift(server, whale);
        } else {
            player.displayClientMessage(ModText.deny("message.skyabyss.chain_slack"), true);
        }
        return InteractionResult.SUCCESS;
    }

    private Entity getVehicleWhale() {
        for (StoneWhaleEntity whale : level().getEntitiesOfClass(StoneWhaleEntity.class, getBoundingBox().inflate(80, 120, 80))) {
            if (whale.isChainDown()) {
                return whale;
            }
        }
        return null;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
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
}
