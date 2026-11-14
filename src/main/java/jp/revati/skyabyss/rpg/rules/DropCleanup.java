package jp.revati.skyabyss.rpg.rules;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// 地面に落ちた品は早めに消える (2026-11-11 の声「ドロップアイテムずっと残ってると重たくなる」)。
// バニラは 5 分。ここでは 2 分 (LIFESPAN)。世界を読み直したときに残っていた品も、読み直してから 2 分で消える。
// 伝承のページ (lore/PageHoming が持ち主へ運ぶ・名前の札のついた置き物) は今のまま
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DropCleanup {
    public static final int LIFESPAN = 20 * 120;

    private DropCleanup() {
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        if (!item.getTags().isEmpty() || jp.revati.skyabyss.lore.PageHoming.isPage(item.getItem())) {
            return;
        }
        if (event.loadedFromDisk()) {
            // 前から残っていた品: 今から 2 分
            item.lifespan = Math.min(item.lifespan, item.getAge() + LIFESPAN);
        } else {
            item.lifespan = Math.min(item.lifespan, LIFESPAN);
        }
    }
}
