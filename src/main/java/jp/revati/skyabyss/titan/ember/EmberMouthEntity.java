package jp.revati.skyabyss.titan.ember;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

// 熱の口 (13 の 2.4節): 眠る巨神の舌の上の、見えない入口の判定。巨神が眠っているあいだだけ巨神が出し、保存しない。
// 吸うと止めるのあいだ (10 秒) だけ、入った人を背中へ渡す。右クリックは要らない。吐くあいだは入れない (巨神の息が外へ押す)。
// 乗る資格・待ち・乗り物から降ろすは共通の TitanGate (EmberTravel.PASSAGE)。見た目は巨神の描画が受け持つ
public class EmberMouthEntity extends Entity implements jp.revati.skyabyss.titan.TitanVisibility.Hidden {
    @Override
    public String visibilityTitan() {
        return jp.revati.skyabyss.progress.Titans.EMBER;
    }

    // 巨神の見え方 (docs/design/32 の 3.3): 章を終えた人 (とそのパーティでない人) には実体を送らない
    @Override
    public boolean broadcastToPlayer(net.minecraft.server.level.ServerPlayer player) {
        return super.broadcastToPlayer(player) && jp.revati.skyabyss.titan.TitanVisibility.sees(player, jp.revati.skyabyss.progress.Titans.EMBER);
    }

    @Nullable
    private UUID titanId;
    // 口に立って渡らなかった人をログに書いた時刻 (書きすぎない)
    private static final java.util.Map<UUID, Long> LOGGED = new java.util.HashMap<>();

    public EmberMouthEntity(EntityType<? extends EmberMouthEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    void bind(UUID titan) {
        this.titanId = titan;
    }

    @Nullable
    public EmberTitanEntity titan() {
        if (titanId == null || !(level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(titanId) instanceof EmberTitanEntity t && !t.isRemoved() ? t : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        EmberTitanEntity titan = titan();
        // 巨神がいない・眠っていない (呼び直された、沈んだ、読み込みが外れた) なら消える
        if (titan == null || titan.mode() != EmberTitanEntity.Mode.SLEEP) {
            discard();
            return;
        }
        if (tickCount % 2 != 0) {
            return;
        }
        AABB around = getBoundingBox().inflate(12.0);
        for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, around)) {
            if (player.isSpectator() || !titan.inMouth(player.getX(), player.getY(), player.getZ())) {
                continue;
            }
            boolean sees = jp.revati.skyabyss.titan.TitanVisibility.sees(player, jp.revati.skyabyss.progress.Titans.EMBER);
            boolean took = sees && titan.mouthOpen() && EmberTravel.boardFromMouth(player, titan);
            if (!took) {
                // 口の中に立っても渡らなかった理由を残す (試遊のログで分かるように。人ごとに 10 秒に一度)
                Long last = LOGGED.get(player.getUUID());
                long now = level().getGameTime();
                if (last == null || now - last >= 200) {
                    LOGGED.put(player.getUUID(), now);
                    jp.revati.skyabyss.SkyAbyss.LOGGER.info("Ember mouth: {} stands in the mouth but was not taken in (sees {}, open {}, cooling {}, may board {})",
                            player.getGameProfile().getName(), sees, titan.mouthOpen(), EmberTravel.PASSAGE.coolingDown(player),
                            jp.revati.skyabyss.progress.Progression.canBoard(player, jp.revati.skyabyss.progress.Titans.EMBER));
                }
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
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
