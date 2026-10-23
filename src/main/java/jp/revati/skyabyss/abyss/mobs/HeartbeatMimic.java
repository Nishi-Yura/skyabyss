package jp.revati.skyabyss.abyss.mobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import jp.revati.skyabyss.story.ch4.Ch4Mobs;

// 鼓動もどき (深淵だけ・docs/design/36 の 4・4.1 B 膜の袋)。溜まりの間の鼓動の音をまねて、人を呼ぶ。動かない。
//   近く (16 ブロック内) の人に、鼓動の音を 2 秒ごとに出す (遠いほど小さい)。近づいて 2.4 ブロック以内に入ると、噛む (予告 0.4 秒)。
//   血は出ない: 膜が縮んで、顎が閉じるだけ。
public class HeartbeatMimic extends AbyssMobB {
    public static final String ID = "heartbeat_mimic";
    public static final double HP = 50;
    public static final double DMG = 6;
    public static final int LURE_EVERY = 40;
    public static final double LURE_RANGE = 16;

    public HeartbeatMimic(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "bite", "lure", "hurt", "death");
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 16)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Tell(this, 0.0, 2.4, 8, 30, "bite", 1.0));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 呼ぶ (2 秒ごと) は飾りの動作なので、被弾の動きを止めない
    @Override
    protected boolean guardsAgainstHurt(String action) {
        return !action.equals("lure");
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        // 呼ぶ: 近くの人に、鼓動の音と膜の脈 (2 秒ごと)
        if (tickCount % LURE_EVERY == 0) {
            Player near = sl.getNearestPlayer(this, LURE_RANGE);
            if (near != null) {
                playAction("lure");
                playSound(AbyssMobsBSetup.HEARTBEAT_LURE.get(), 0.9f, 1.0f);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, AbyssMobsBSetup.PULSE_MEMBRANE.get(), 0.6, 1, 1);
    }
}
