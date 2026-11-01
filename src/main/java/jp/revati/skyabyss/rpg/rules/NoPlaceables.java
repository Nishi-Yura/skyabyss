package jp.revati.skyabyss.rpg.rules;

import java.util.HashSet;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 置く品は持たせない (2026-11-02 の声「置く系のアイテムは手に入らないようにして。松明とか」「アイテム壊れたときのドロップなくしていい。たまにドロップアイテム落ちてる」)。
// 冒険の決まり (RpgRules.ADVENTURE) ではブロックを置けないので、松明・丸太などのブロックの品や、舟・額縁などの置く品は持っても使えない。
//   渡さない : 頼みの褒美・会話・宝箱 (Quests.give)、人ごとの宝箱 (loot/SharedChests)、跡地の宝 (guide/SiteTreasure) は、置く品を飛ばす
//   拾えない : 地面の置く品は拾えず、世界にも出ない (出たら消す)
//   落とさない: ブロックが壊れても品を落とさない (葉の枯れ・支えを失った松明や花・建てるときの置き換え)
//   外す     : 2 秒ごとに持ち物を見て、置く品を外す (前の版で手に入れた分)
// 例外 (持ってよい): 頼みで集める・渡す品 (collect / turn_in の的) と店の品 (頼みの材料のランタン・蝋燭・避雷針など)、
//   作物の種のようにブロックにもなる品 (ItemNameBlockItem)、使い方のある Mod の品 (月の錘)、バケツ (採集の水汲み)。
// クリエイティブ・観戦者と、決まりが切れた世界では何もしない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NoPlaceables {
    private NoPlaceables() {
    }

    // 頼みと店が使う品 (起動ごとに一度作る)
    private static volatile Set<Item> allowed;

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        allowed = null;
    }

    static Set<Item> allowed() {
        Set<Item> a = allowed;
        if (a == null) {
            a = new HashSet<>();
            for (jp.revati.skyabyss.quest.QuestDef q : jp.revati.skyabyss.quest.QuestManager.all()) {
                for (jp.revati.skyabyss.quest.QuestDef.Stage st : q.stages()) {
                    for (jp.revati.skyabyss.quest.QuestDef.Objective o : st.objectives()) {
                        if (o.type().equals("collect") || o.type().equals("turn_in")) {
                            for (String t : o.targets()) {
                                ResourceLocation id = ResourceLocation.tryParse(t);
                                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                                    a.add(BuiltInRegistries.ITEM.get(id));
                                }
                            }
                        }
                    }
                }
            }
            for (jp.revati.skyabyss.facility.ShopDef shop : jp.revati.skyabyss.facility.ShopManager.all().values()) {
                a.addAll(shop.allItems());
            }
            allowed = a;
        }
        return a;
    }

    // 置く品か (持たせない品)
    public static boolean placeable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem) {
            return !(item instanceof ItemNameBlockItem) && !(item instanceof jp.revati.skyabyss.story.ch6.cliff.MoonWeightItem) && !allowed().contains(item);
        }
        boolean vanilla = BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft");
        return vanilla && (item instanceof BoatItem || item instanceof MinecartItem || item instanceof ArmorStandItem || item instanceof HangingEntityItem
                || item instanceof EndCrystalItem || item instanceof FlintAndSteelItem || item instanceof FireChargeItem || item instanceof SpawnEggItem)
                && !allowed().contains(item);
    }

    // 自己テストのあいだは止める (ほかの段が松明などを手に持たせて仕掛けを試すため)。この仕組みの段だけ入れる
    static boolean testing;

    // 決まりが入っていて、遊ぶ人 (クリエイティブ・観戦者でない) か
    public static boolean applies(ServerPlayer p) {
        GameType mode = p.gameMode.getGameModeForPlayer();
        return !(p instanceof FakePlayer) && mode != GameType.CREATIVE && mode != GameType.SPECTATOR && RpgRules.adventure(p.serverLevel())
                && (testing || !jp.revati.skyabyss.SelfTestRunner.enabled());
    }

    // 渡す前に見る: 置く品なら渡さない (true = 渡さない)
    public static boolean blockGive(ServerPlayer p, ItemStack stack) {
        return applies(p) && placeable(stack);
    }

    // ---------------------------------------------------------------- 拾えない
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer p && applies(p) && placeable(event.getItemEntity().getItem())) {
            event.setCanPickup(TriState.FALSE);
            event.getItemEntity().discard();
        }
    }

    // 世界に出る置く品は消す (壊れたブロック・額縁・舟などの落とし物。人が投げた物も)
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && event.getLevel() instanceof ServerLevel level && RpgRules.adventure(level)
                && (testing || !jp.revati.skyabyss.SelfTestRunner.enabled()) && placeable(item.getItem())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 落とさない
    // ブロックが壊れても何も落とさない (冒険の決まりでは人は壊せないので、落ちるのは葉の枯れ・支えの消えた飾り・仕掛けの置き換えだけ)
    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        if (RpgRules.adventure(event.getLevel()) && (testing || !jp.revati.skyabyss.SelfTestRunner.enabled())
                && !(event.getBreaker() instanceof ServerPlayer p && p.isCreative())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 外す (2 秒ごと)
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 40 == 27 && applies(p)) {
            sweep(p);
        }
    }

    // 持ち物 (手・袋の枠・左手) とエンダーチェストから置く品を外す。外した数
    public static int sweep(ServerPlayer p) {
        int n = sweep(p.getInventory().items) + sweep(p.getInventory().offhand);
        n += sweep(p.getEnderChestInventory());
        if (n > 0) {
            p.getInventory().setChanged();
        }
        return n;
    }

    private static int sweep(java.util.List<ItemStack> list) {
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            if (placeable(list.get(i))) {
                n += list.get(i).getCount();
                list.set(i, ItemStack.EMPTY);
            }
        }
        return n;
    }

    private static int sweep(Container box) {
        int n = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack s = box.getItem(i);
            if (placeable(s)) {
                n += s.getCount();
                box.setItem(i, ItemStack.EMPTY);
            }
        }
        return n;
    }
}
