package jp.revati.skyabyss.story.ch6.mob;

import java.util.ArrayDeque;

import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 下弦の淵の敵 (docs/design/46 の 4.6「残像」)。倒れると 3 秒前にいた所から残像として一度立ち上がる (RiftRemnants が立たせる)。
//   いた所は 5 tick ごとに覚える (13 個 = 3 秒ぶん)。残像 (echo) は体力が元の 40%、半ば透けて描かれ、倒れても残像を残さず、
//   落とし物と経験値を出さない
public abstract class RiftMob extends MoonMob {
    public static final int ECHO_BACK = 60;
    public static final double ECHO_HP = 0.4;
    private static final EntityDataAccessor<Boolean> ECHO = SynchedEntityData.defineId(RiftMob.class, EntityDataSerializers.BOOLEAN);
    private final ArrayDeque<Vec3> past = new ArrayDeque<>();

    protected RiftMob(EntityType<? extends Monster> type, Level level, String prefix, String... actions) {
        super(type, level, prefix, actions);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ECHO, false);
    }

    public boolean echo() {
        return entityData.get(ECHO);
    }

    // 残像にする (湧かせる側が tuneMoon のあとに呼ぶ)
    public void makeEcho() {
        entityData.set(ECHO, true);
        addTag(GrowthRewards.NO_REWARD);
        double max = getMaxHealth() * ECHO_HP;
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(max);
        setHealth((float) max);
    }

    // 3 秒前にいた所 (覚えが足りなければ一番古い所)
    public Vec3 pastPosition() {
        return past.isEmpty() ? position() : past.peekFirst();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 5 == 0) {
            past.addLast(position());
            while (past.size() > ECHO_BACK / 5 + 1) {
                past.pollFirst();
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        if (!echo()) {
            super.dropCustomDeathLoot(level, source, recentlyHit);
        }
    }

    @Override
    protected boolean shouldDropLoot() {
        return !echo() && super.shouldDropLoot();
    }

    @Override
    public boolean shouldDropExperience() {
        return !echo() && super.shouldDropExperience();
    }
}
