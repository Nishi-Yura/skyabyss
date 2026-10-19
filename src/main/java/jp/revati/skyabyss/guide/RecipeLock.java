package jp.revati.skyabyss.guide;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 旅の覚書・職人が解くまで作れないレシピ (docs/design/21 の 1.2・1.4、P5)。
// ゲームルール doLimitedCrafting はワールド全体の設定でほかのレシピまで縛るので使わず、「作業台の完成品の枠」だけを見る:
//   - 対象 = WayfarerNotes に登録された章のレシピの ID (旅装・砥石・糧・薬。登録した時点で自動で鍵がかかる)
//   - 解く = 旅の覚書を開く / 職人に教わる (どちらも WayfarerNotes.unlockRecipes = レシピブックに入る)。人ごと
//   - 作業台と持ち物の 2x2 の完成品の枠に、鍵のかかったレシピの品が出たら、毎 tick その枠を空にし、一行 (5 秒に一度) 知らせる。
//     取り出す手前の競り合い (同じ tick の続けざまの操作) は、作られた直後の品を消して取りこぼしを防ぐ (材料は戻らない。通常の操作では起きない)
//   - 創造モードは問わない。バニラのレシピ・ほかの Mod のレシピには触れない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RecipeLock {
    private RecipeLock() {
    }

    public static boolean locked(ResourceLocation recipeId) {
        for (int chapter = 0; chapter <= WayfarerNotes.MAX_CHAPTER; chapter++) {
            if (WayfarerNotes.recipes(chapter).contains(recipeId)) {
                return true;
            }
        }
        return false;
    }

    // この人がこのレシピを作ってよいか
    public static boolean mayCraft(ServerPlayer player, RecipeHolder<?> holder) {
        return player.isCreative() || !locked(holder.id()) || player.getRecipeBook().contains(holder);
    }

    // いま開いている作業台 (持ち物の 2x2 を含む) の完成品の枠に出ているレシピ (なければ null)
    private static RecipeHolder<?> shown(AbstractContainerMenu menu) {
        if (!(menu instanceof CraftingMenu) && !(menu instanceof InventoryMenu)) {
            return null;
        }
        Slot result = menu.slots.get(0);
        if (result.container instanceof ResultContainer rc && result.hasItem()) {
            return rc.getRecipeUsed();
        }
        return null;
    }

    // 鍵のかかった完成品を空にした (サーバー tick と同じ処理を一度。自己テストからも呼ぶ)。空にしたら true
    public static boolean sweep(ServerPlayer player) {
        RecipeHolder<?> holder = shown(player.containerMenu);
        if (holder == null || mayCraft(player, holder)) {
            return false;
        }
        player.containerMenu.slots.get(0).set(ItemStack.EMPTY);
        player.containerMenu.broadcastChanges();
        Integer last = WARNED.get(player.getUUID());
        if (last == null || player.tickCount < last || player.tickCount - last >= 100) {
            WARNED.put(player.getUUID(), player.tickCount);
            Notice.hint(player, "message.skyabyss.recipe.locked");
        }
        return true;
    }

    // 人ごとの最後に知らせた tick (5 秒に一度)
    private static final java.util.Map<java.util.UUID, Integer> WARNED = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu != null && !player.isCreative()) {
            sweep(player);
        }
    }

    // 取りこぼしの備え: 作られた直後の品が鍵のかかったレシピのものなら消す
    @SubscribeEvent
    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        RecipeHolder<?> holder = recipeUsed(player.containerMenu);
        if (holder != null && !mayCraft(player, holder)) {
            event.getCrafting().setCount(0);
        }
    }

    private static RecipeHolder<?> recipeUsed(AbstractContainerMenu menu) {
        if ((menu instanceof CraftingMenu || menu instanceof InventoryMenu) && menu.slots.get(0).container instanceof ResultContainer rc) {
            return rc.getRecipeUsed();
        }
        return null;
    }
}
