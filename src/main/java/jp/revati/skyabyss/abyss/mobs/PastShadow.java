package jp.revati.skyabyss.abyss.mobs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import jp.revati.skyabyss.SkyAbyss;

// 前の私の影 (深み・docs/design/36 の 4・4.1 C 濡れた影)。深みにだけ出る。
//   人の歩いた道を 3 秒遅れてなぞる (その人の三秒前の位置へ寄る)。人が殴ったあと近くにいると、鏡のように殴り返す (予告 0.5 秒)。
//   殴った印は AttackEntityEvent で人ごとに覚える (サーバーだけ。ATTACKED はこの係の中にだけある)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public class PastShadow extends AbyssMobB {
    public static final String ID = "past_shadow";
    public static final double HP = 60;
    public static final double DMG = 7;
    public static final int ECHO_TICKS = 60;
    public static final double MIRROR_RANGE = 3.5;
    private static final int MIRROR_TELL = 10;
    private static final int MIRROR_COOLDOWN = 30;
    private static final Map<UUID, Long> ATTACKED = new HashMap<>();
    private final Deque<Vec3> trail = new ArrayDeque<>();
    private int mirrorWind = -1;
    private int mirrorCooldown;

    public PastShadow(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "mirror", "hurt", "death");
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        if (mirrorCooldown > 0) {
            mirrorCooldown--;
        }
        Player t = getTarget() instanceof Player p && p.isAlive() ? p : null;
        if (t == null) {
            trail.clear();
            return;
        }
        // 人の道を覚える (一秒に二つ。三秒ぶん)
        if (tickCount % 2 == 0) {
            trail.addLast(t.position());
            while (trail.size() > ECHO_TICKS / 2) {
                trail.removeFirst();
            }
        }
        // 三秒遅れの位置へ寄る (遠いときだけ歩く)
        if (trail.size() >= ECHO_TICKS / 2 && tickCount % 5 == 0 && distanceTo(t) > 2.0) {
            Vec3 to = trail.peekFirst();
            getNavigation().moveTo(to.x, to.y, to.z, 1.0);
        }
        if (tickCount % 80 == 0 && random.nextInt(2) == 0) {
            playSound(AbyssMobsBSetup.PAST_SHADOW_ECHO.get(), 0.7f, 0.9f + random.nextFloat() * 0.2f);
        }
        // 鏡: 人が殴った直後 (2 tick 以内) に近くにいたら、予告のあとに殴り返す
        if (mirrorWind >= 0) {
            getNavigation().stop();
            if (--mirrorWind < 0 && distanceTo(t) <= MIRROR_RANGE + 1) {
                strike(t, dmg());
                sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 6, 0.3, 0.4, 0.3, 0.02);
                mirrorCooldown = MIRROR_COOLDOWN;
            }
            return;
        }
        Long hit = ATTACKED.get(t.getUUID());
        if (hit != null && sl.getGameTime() - hit <= 2 && mirrorCooldown == 0 && distanceTo(t) <= MIRROR_RANGE) {
            ATTACKED.remove(t.getUUID());
            playAction("mirror");
            playSound(AbyssMobsBSetup.PAST_SHADOW_MIRROR.get(), 0.8f, 1.0f);
            mirrorWind = MIRROR_TELL;
        }
    }

    // 人が殴った印 (サーバー。鏡の予告の材料)
    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        ATTACKED.put(p.getUUID(), p.serverLevel().getGameTime());
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, AbyssMobsBSetup.ECHO_SHARD.get(), 0.5, 1, 1);
    }
}
