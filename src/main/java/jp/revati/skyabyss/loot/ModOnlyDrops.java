package jp.revati.skyabyss.loot;

import java.util.HashSet;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.ShopDef;
import jp.revati.skyabyss.facility.ShopManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

// 敵の落とし物は、この Mod で使う物だけ (2026-10-27 の声「敵が落とすものは、お金や素材等必ずこの mod で使う物に。いらないものはドロップしないように」)。
// いちばん最後に (ほかの落とし物の足しのあと)、プレイヤーでない者の落とし物から、使わないバニラの品を外す。
// 残すバニラの品: 店で売り買いしている品 (ShopManager の goods・buys)・矢 (弓の矢)・パン
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ModOnlyDrops {
    private ModOnlyDrops() {
    }

    static boolean used(Item item) {
        if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
            return true;
        }
        if (item == Items.ARROW || item == Items.BREAD) {
            return true;
        }
        return shopItems().contains(item);
    }

    private static Set<Item> cached;
    private static int cachedFor = -1;

    // 店の品 (ShopManager が読み直されたら作り直す)
    static Set<Item> shopItems() {
        var all = ShopManager.all();
        if (cached == null || cachedFor != System.identityHashCode(all)) {
            Set<Item> s = new HashSet<>();
            for (ShopDef d : all.values()) {
                d.goods().forEach(g -> s.add(g.item()));
                d.pool().forEach(g -> s.add(g.item()));
                d.buys().forEach(b -> s.add(b.item()));
            }
            cached = s;
            cachedFor = System.identityHashCode(all);
        }
        return cached;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        event.getDrops().removeIf(e -> !used(e.getItem().getItem()));
    }
}
