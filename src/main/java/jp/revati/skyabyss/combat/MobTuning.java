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

// mod の敵の体力と攻撃を一律に整える。2026-10-23 の試遊で雑魚を下げた (体力 8 割・攻撃 9 割) が、2026-11-09 の声
// 「ソロでの敵が弱かったらパーティー組む意味ない」「攻撃力低いかも」で上げた: 雑魚は体力 1.1 倍・攻撃 1.2 倍、主 (ボスの帯・大物) は攻撃 1.3 倍
// (入る人のレベルで固くした主は、さらにその 0.6 乗: LevelScale.attack)。
// 防具 (鎧の値) で傷を減らすのもやめ、その分を体力に掛ける (「耐えはこのゲームから消していい。hp 増やすのだけで敵を固くする」。combat/Hardness の armor)。
// 傷は人が受けるときだけ 1 + ATTACK 倍。ボスの帯を持つ敵と、大物 (最大体力が ShieldRules.BIG_HEALTH 以上。心臓・中ボス・主・王) は下げない。数値は各敵の作りのまま、ここで掛けるだけ
public final class MobTuning {
    public static final double HEALTH = 0.1;
    public static final double ATTACK = 0.2;
    public static final double BOSS_ATTACK = 0.3;
    static final ResourceLocation ARMOR_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "no_armor");
    // 防具を体力に直すときの、ふつうの一撃の大きさ
    static final double TYPICAL_HIT = 12;
    public static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "trash_health");

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

    // 人が受ける傷の倍率 (雑魚は 1 + ATTACK、主は 1 + BOSS_ATTACK とレベルの倍率)。自己テストも使う
    public static double attackFactor(LivingEntity attacker) {
        // 外れの帯の敵は、その上に土地の上限 +3 の Lv の分 (world/continent/FrontierBand)
        double far = jp.revati.skyabyss.world.continent.FrontierBand.attack(attacker);
        if (attacker.getTags().contains(UNTUNED)) {
            return 1;
        }
        if (!ours(attacker)) {
            return far;
        }
        return (applies(attacker) ? 1 + ATTACK : (1 + BOSS_ATTACK) * LevelScale.attack(attacker)) * far;
    }

    // mod の敵か (雑魚も主も)
    public static boolean ours(LivingEntity e) {
        return e instanceof Enemy && SkyAbyss.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }

    // 防具 (鎧の値・鎧の強さ) を 0 にし、ふつうの一撃 (TYPICAL_HIT) で減っていた分を体力に掛ける (バニラの防具の式)
    public static void armorToHealth(LivingEntity e) {
        AttributeInstance armor = e.getAttribute(Attributes.ARMOR);
        AttributeInstance tough = e.getAttribute(Attributes.ARMOR_TOUGHNESS);
        // 丸まると防具が上がる敵 (燠虫の転がり。丸まった玉は硬い、が技) は防具のまま
        if (armor == null || armor.hasModifier(ARMOR_ID) || e.getTags().contains(UNTUNED) || e instanceof jp.revati.skyabyss.titan.ember.mob.CinderRoller) {
            return;
        }
        double a = armor.getValue();
        double t = tough == null ? 0 : tough.getValue();
        if (a <= 0) {
            return;
        }
        double eff = Math.max(a / 5.0, Math.min(20.0, a - TYPICAL_HIT / (2.0 + t / 4.0)));
        double factor = 1.0 / Math.max(0.2, 1.0 - eff / 25.0);
        armor.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (tough != null) {
            tough.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        Hardness.set(e, "armor", factor);
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
    public static final String UNTUNED = "skyabyss_untuned";

    public static void untune(LivingEntity e) {
        // 印を付けると、このあとも攻撃の倍率・体力の上乗せ・防具の置き換えを掛けない
        e.addTag(UNTUNED);
        AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.hasModifier(HEALTH_ID)) {
            hp.removeModifier(HEALTH_ID);
            e.setHealth(e.getMaxHealth());
        }
        // 防具を体力に直した分も戻す (防具の値・殻の分け方を数で確かめる試し)
        AttributeInstance armor = e.getAttribute(Attributes.ARMOR);
        if (armor != null && armor.hasModifier(ARMOR_ID)) {
            armor.removeModifier(ARMOR_ID);
            AttributeInstance tough = e.getAttribute(Attributes.ARMOR_TOUGHNESS);
            if (tough != null) {
                tough.removeModifier(ARMOR_ID);
            }
            Hardness.set(e, "armor", 1);
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
                // 防具は作りの値 (属性) なので、湧いたその場で体力に直す (同じ tick に殴られても同じ)
                if (ours(e)) {
                    armorToHealth(e);
                }
                PENDING.add(e);
            }
        }

        @SubscribeEvent
        static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
            if (PENDING.isEmpty()) {
                return;
            }
            for (LivingEntity e : java.util.List.copyOf(PENDING)) {
                if (e.isAlive() && applies(e) && !e.getTags().contains(UNTUNED)) {
                    AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
                    boolean fresh = !hp.hasModifier(HEALTH_ID);
                    boolean full = e.getHealth() >= e.getMaxHealth() - 0.01f;
                    hp.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_ID, HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    if (fresh && (full || e.getHealth() > e.getMaxHealth())) {
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
            if (!(by instanceof LivingEntity attacker) || !(event.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
                return;
            }
            double k = attackFactor(attacker);
            if (k == 1) {
                return;
            }
            event.setAmount((float) (event.getAmount() * k));
        }
    }
}
