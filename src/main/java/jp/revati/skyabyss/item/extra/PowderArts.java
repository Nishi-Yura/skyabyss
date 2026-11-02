package jp.revati.skyabyss.item.extra;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

// 巨神の粉の技と、土地の敵が落とす粉 (docs/design/41)。
//   粉の技 (一つの装備に同じ属性の粉の段の和が Powder.WEIGHT_FOR_ART 以上):
//     武器 (当てたとき)  岩 = 25% でよろけ (鈍足 III 1.5 秒)・風 = 押し飛ばす・潮 = 与えた傷の 10% を癒す・灰 = 3 秒燃やす・骨 = 3 秒弱らせる
//     防具 (受けたとき)  岩 = 20% で耐性 I 3 秒・風 = 速さ II 2 秒・潮 = 25% で再生 I 3 秒・灰 = 相手を 2 秒燃やす・骨 = 相手に 1.5 の棘
//   落ちる粉 (人が倒した敵): 天と淵の大陸の地上は土地の粉 (丘陵 = 岩・浜 = 潮・高地 = 風・荒野 = 灰。都のまわりと外れの谷は無し)、深淵は骨。段は I
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PowderArts {
    static final float STAGGER_CHANCE = 0.25f;
    static final float SHELL_CHANCE = 0.2f;
    static final float TIDE_GUARD_CHANCE = 0.25f;
    static final float TIDE_DRAIN = 0.10f;
    static final float THORN = 1.5f;
    // 土地ごとの落ちる率 (ContinentMap の土地の番号の並び: 都のまわり・外れ・丘陵・浜・高地・荒野)
    static final float[] DROP_CHANCE = {0f, 0f, 0.06f, 0.08f, 0.10f, 0.12f};
    static final Element[] DROP_ELEMENT = {null, null, Element.STONE, Element.TIDE, Element.WIND, Element.ASH};
    static final float ABYSS_CHANCE = 0.10f;

    private PowderArts() {
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel) || event.getNewDamage() <= 0) {
            return;
        }
        // 当てた側 (人の武器)
        if (event.getSource().getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && event.getSource().getDirectEntity() == p) {
            Element e = Powder.art(p.getMainHandItem());
            if (e != null) {
                weaponArt(e, p, target, event.getNewDamage());
            }
        }
        // 受けた側 (人の防具)
        if (target instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
            Element e = armorArt(p);
            if (e != null) {
                armorArtOn(e, p, event.getSource().getEntity() instanceof LivingEntity a ? a : null);
            }
        }
    }

    public static void weaponArt(Element e, ServerPlayer p, LivingEntity target, float dealt) {
        switch (e) {
            case STONE -> {
                if (p.getRandom().nextFloat() < STAGGER_CHANCE) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), p);
                }
            }
            case WIND -> target.knockback(0.6, p.getX() - target.getX(), p.getZ() - target.getZ());
            case TIDE -> p.heal(dealt * TIDE_DRAIN);
            case ASH -> target.igniteForSeconds(3);
            case BONE -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), p);
        }
    }

    // 身に着けた防具のどれかで働く粉の技 (二つ以上なら頭から順に最初のもの)
    public static Element armorArt(ServerPlayer p) {
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack a = p.getItemBySlot(s);
            Element e = a.isEmpty() ? null : Powder.art(a);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    public static void armorArtOn(Element e, ServerPlayer p, LivingEntity attacker) {
        switch (e) {
            case STONE -> {
                if (p.getRandom().nextFloat() < SHELL_CHANCE) {
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0));
                }
            }
            case WIND -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1));
            case TIDE -> {
                if (p.getRandom().nextFloat() < TIDE_GUARD_CHANCE) {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0));
                }
            }
            case ASH -> {
                if (attacker != null) {
                    attacker.igniteForSeconds(2);
                }
            }
            case BONE -> {
                if (attacker != null && attacker != p) {
                    attacker.hurt(p.damageSources().thorns(p), THORN);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 落ちる粉
    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy) || !(dead.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer)) {
            return;
        }
        Element e = null;
        float chance = 0;
        if (level.dimension() == Level.OVERWORLD) {
            int r = jp.revati.skyabyss.world.continent.ContinentMap.get().region(dead.getBlockX(), dead.getBlockZ());
            if (r >= 0 && r < DROP_ELEMENT.length) {
                e = DROP_ELEMENT[r];
                chance = DROP_CHANCE[r];
            }
        } else if (level.dimension() == jp.revati.skyabyss.story.ch5.AbyssWorld.LEVEL) {
            e = Element.BONE;
            chance = ABYSS_CHANCE;
        }
        if (e == null || level.random.nextFloat() >= chance) {
            return;
        }
        ItemStack powder = new ItemStack(jp.revati.skyabyss.registry.ModItems.socketShard(e).get());
        event.getDrops().add(new ItemEntity(level, dead.getX(), dead.getY() + 0.3, dead.getZ(), powder));
    }
}
