package jp.revati.skyabyss.rpg;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// おなかは空かない。食べ物は、食べたその場で体力を戻す (2026-10-21 の試遊)。docs/design/17 の 4 章
//   満腹度はいつも FOOD (17): 18 に届かないので自然の回復は起きず、6 より上なので走れる、20 でないのでいつでも食べられる。満腹度の隠れた値と疲れは 0。
//   食べ終えたとき: 体力を 栄養 + 隠れた満腹度 × 0.5 だけ戻す (パン 8、焼いた牛肉 14、金のにんじん 13)。食べ物の効き目 (金のりんごなど) はそのまま
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NoHunger {
    public static final int FOOD = 17;

    private NoHunger() {
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        hold(p.getFoodData());
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

    // 食べたときに戻す体力 (半ハート単位)
    public static float healOf(FoodProperties food) {
        return food.nutrition() + food.saturation() * 0.5f;
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
        p.heal(healOf(food));
        hold(p.getFoodData());
    }
}
