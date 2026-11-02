package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// mod の雑魚敵の体力と攻撃を一律に下げる (2026-10-23 の試遊: 体力は今の 8 割、攻撃も少し下げる)。
// 傷は人が受けるときだけ 1 + ATTACK 倍。ボスの帯を持つ敵と、大物 (最大体力が ShieldRules.BIG_HEALTH 以上。心臓・中ボス・主・王) は下げない。数値は各敵の作りのまま、ここで掛けるだけ
public final class MobTuning {
    public static final double HEALTH = -0.2;
    public static final double ATTACK = -0.1;
    static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "trash_health");

    private MobTuning() {
    }

    // 下げる対象か (mod の敵で、大物でない)
    public static boolean applies(LivingEntity e) {
        if (!(e instanceof Enemy)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (!SkyAbyss.MODID.equals(id.getNamespace())) {
            return false;
        }
        AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
        return hp != null && hp.getBaseValue() < ShieldRules.BIG_HEALTH && !hasBossBar(e.getClass());
    }

    // 画面上のボスの帯を持つ敵 (中ボス・番人など) は雑魚ではない。帯 (ServerBossEvent) を持つ型かを型ごとに一度だけ調べる
    private static final java.util.Map<Class<?>, Boolean> BOSS = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean hasBossBar(Class<?> type) {
        return BOSS.computeIfAbsent(type, t -> {
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (net.minecraft.world.BossEvent.class.isAssignableFrom(f.getType())) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    // 下げた分を外す (自己テストで、敵の作りの数そのものを確かめるとき)
    public static void untune(LivingEntity e) {
        AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.hasModifier(HEALTH_ID)) {
            hp.removeModifier(HEALTH_ID);
            e.setHealth(e.getMaxHealth());
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Events {
        // 湧いた tick には体力を決めていない敵がある (深みの王・層の主は湧いたあとで体力を入れる) ので、一 tick 待ってから見分ける
        private static final java.util.List<LivingEntity> PENDING = new java.util.ArrayList<>();

        @SubscribeEvent
        static void onJoin(EntityJoinLevelEvent event) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity e && e instanceof Enemy) {
                PENDING.add(e);
            }
        }

        @SubscribeEvent
        static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
            if (PENDING.isEmpty()) {
                return;
            }
            for (LivingEntity e : java.util.List.copyOf(PENDING)) {
                if (e.isAlive() && applies(e)) {
                    AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
                    boolean fresh = !hp.hasModifier(HEALTH_ID);
                    hp.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_ID, HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    if (fresh && e.getHealth() > e.getMaxHealth()) {
                        e.setHealth(e.getMaxHealth());
                    }
                }
            }
            PENDING.clear();
        }

        // 攻撃は傷の側で下げる (敵ごとに技の傷を数で持っているものが多く、攻撃力の属性だけでは効かないため)
        @SubscribeEvent
        static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
            net.minecraft.world.entity.Entity by = event.getSource().getEntity();
            if (by instanceof LivingEntity attacker && event.getEntity() instanceof net.minecraft.world.entity.player.Player && applies(attacker)) {
                event.setAmount((float) (event.getAmount() * (1 + ATTACK)));
            }
        }
    }
}
