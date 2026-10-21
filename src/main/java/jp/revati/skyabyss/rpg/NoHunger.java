package jp.revati.skyabyss.rpg;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// おなかは空かない。食べ物は、食べたその場で少し戻り、そのあと少しずつ決まった量まで戻る (2026-10-22 の二人の試遊: 回復が多すぎた)。docs/design/17 の 4 章
//   満腹度はいつも FOOD (17): 18 に届かないので自然の回復は起きず、6 より上なので走れる、20 でないのでいつでも食べられる。満腹度の隠れた値と疲れは 0。
//   戻る量 (半ハート単位) = 栄養 + 隠れた満腹度 × 0.3 (パン 6.8、焼いた牛肉 11.8、金のにんじん 10.3)。その半分をすぐ、残りを 1 秒に 1 ずつ。
//   空腹の効き目のある食べ物 (腐った肉・生の鶏肉・クモの目など): 戻る量は半分、空腹の効き目は弱体 (20 秒) と吐き気 (4 秒) に替える (空腹はもう意味がないので)。
//   毒の食べ物 (毒じゃがいも・フグ) の毒は、そのまま。続けて食べると、残りの量は足していく (上限 20)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NoHunger {
    public static final int FOOD = 17;
    static final float SATURATION_SHARE = 0.3f;
    static final float PENDING_MAX = 20f;
    // 少しずつ戻る残り (人ごと)
    private static final Map<UUID, Float> PENDING = new ConcurrentHashMap<>();

    private NoHunger() {
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        hold(p.getFoodData());
        if (p.tickCount % 20 == 0) {
            Float left = PENDING.get(p.getUUID());
            if (left != null) {
                float step = Math.min(1f, left);
                if (p.isAlive() && p.getHealth() < p.getMaxHealth()) {
                    p.heal(step);
                }
                float rest = left - step;
                if (rest <= 0.001f || !p.isAlive()) {
                    PENDING.remove(p.getUUID());
                } else {
                    PENDING.put(p.getUUID(), rest);
                }
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    public static void hold(FoodData fd) {
        if (fd.getFoodLevel() != FOOD) {
            fd.setFoodLevel(FOOD);
        }
        if (fd.getSaturationLevel() != 0f) {
            fd.setSaturation(0f);
        }
        if (fd.getExhaustionLevel() != 0f) {
            fd.setExhaustion(0f);
        }
    }

    // 食べ物の空腹の効き目があるか (腐った肉など)
    public static boolean spoiled(FoodProperties food) {
        for (FoodProperties.PossibleEffect e : food.effects()) {
            if (e.effect().getEffect().is(MobEffects.HUNGER)) {
                return true;
            }
        }
        return false;
    }

    // 食べたときに戻す体力の合計 (半ハート単位)
    public static float healOf(FoodProperties food) {
        float total = food.nutrition() + food.saturation() * SATURATION_SHARE;
        return spoiled(food) ? total * 0.5f : total;
    }

    // すぐ戻す分 (合計の半分)
    public static float instantOf(FoodProperties food) {
        return healOf(food) * 0.5f;
    }

    @SubscribeEvent
    static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player p) || p.level().isClientSide) {
            return;
        }
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food == null) {
            return;
        }
        float total = healOf(food);
        float now = instantOf(food);
        p.heal(now);
        PENDING.merge(p.getUUID(), total - now, (a, b) -> Math.min(PENDING_MAX, a + b));
        if (spoiled(food)) {
            p.removeEffect(MobEffects.HUNGER);
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 20, 0));
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 4 * 20, 0));
        }
        hold(p.getFoodData());
    }

    // 自己テスト用: 少しずつ戻る残り
    public static float pending(Player p) {
        return PENDING.getOrDefault(p.getUUID(), 0f);
    }
}
