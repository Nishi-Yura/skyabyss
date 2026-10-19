package jp.revati.skyabyss.pouch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.guide.RecipeLock;
import jp.revati.skyabyss.pouch.PouchEntries.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

// 作業台 (と持ち物の 2x2) が旅嚢の材料を使えるようにする (docs/design/22 の 17 章)。バニラの作業台・レシピブックは変えず、前後で旅嚢と持ち物をやり取りする:
//   手で置く : 作業台の画面の右の「旅嚢から」の帯 (client/pouch/CraftStrip) から、材料をカーソルへ取る (grid)。サーバーが決める
//   レシピブックの「置く」: ServerPlaceRecipe.recipeClicked の前に、そのレシピの材料になる旅嚢の品を持ち物へ出し (stage)、バニラが枠へ置いたあと、残りを旅嚢へ戻す (unstage)。
//                       旅嚢 + 持ち物の合計は変わらないので、増えない・消えない。持ち物に入りきらない分は最初から出さない
//   閉じる・つながりが切れる: バニラが枠とカーソルの品を持ち物へ戻し、旅嚢の掃除 (PouchRouting) が旅嚢へ戻す (画面を閉じた次の tick と 1 秒ごと)
//   鍵のかかったレシピ (RecipeLock) は旅嚢から出さない。完成品は今までどおり (取り出す側の鍵と返金は RecipeLock)
public final class PouchCraft {
    // 一度に出す数の上限 (一つの材料の枠は、その品のスタックの上限まで。最大 9 枠)
    private static final int STAGE_SLOTS_MAX = 9;

    private PouchCraft() {
    }

    // 作業台の材料に使える旅嚢の品か (結ばれる品は対象外。手に持って使う道具も、レシピの材料なら対象)
    public static boolean craftable(Entry e) {
        return !e.bonded();
    }

    // ---------------------------------------------------------------- 一覧 (クライアントの帯の表示と、サーバーの確かめ)
    private static RecipeManager listedFor;
    private static int listedSize = -1;
    private static List<Entry> listed = List.of();

    // 作業台のレシピの材料に一つでもなる旅嚢の品 (登録の順)。レシピの数が変わらない限り使い回す
    public static synchronized List<Entry> usableInRecipes(RecipeManager manager) {
        List<RecipeHolder<CraftingRecipe>> all = manager.getAllRecipesFor(RecipeType.CRAFTING);
        if (listedFor == manager && listedSize == all.size()) {
            return listed;
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : PouchEntries.enabled()) {
            if (!craftable(e)) {
                continue;
            }
            ItemStack probe = new ItemStack(e.item());
            boolean used = false;
            for (RecipeHolder<CraftingRecipe> holder : all) {
                for (Ingredient ing : holder.value().getIngredients()) {
                    if (!ing.isEmpty() && ing.test(probe)) {
                        used = true;
                        break;
                    }
                }
                if (used) {
                    break;
                }
            }
            if (used) {
                out.add(e);
            }
        }
        listedFor = manager;
        listedSize = all.size();
        listed = List.copyOf(out);
        return listed;
    }

    // ---------------------------------------------------------------- レシピブックの「置く」 (Mixin から)
    // この人が旅嚢の材料を使って作ってよいレシピか (作業台のレシピ・鍵が解けている・旅嚢が入)
    private static boolean eligible(ServerPlayer player, RecipeHolder<?> holder) {
        return PouchConfig.enabled(player.server) && !player.isCreative() && holder.value() instanceof CraftingRecipe
                && RecipeLock.mayCraft(player, holder);
    }

    // 置く前: レシピの材料に当たる旅嚢の品を持ち物へ出す。入りきらない分は旅嚢に残す (出した数だけ旅嚢から減らす)
    public static void stage(ServerPlayer player, RecipeHolder<?> holder) {
        if (!eligible(player, holder)) {
            return;
        }
        // 旅嚢の品ごとの「置く枠の数」。一つの材料の枠は最大 9
        Map<Entry, Integer> slots = new LinkedHashMap<>();
        for (Ingredient ing : ((Recipe<?>) holder.value()).getIngredients()) {
            if (ing.isEmpty()) {
                continue;
            }
            for (Entry e : PouchEntries.enabled()) {
                if (craftable(e) && ing.test(new ItemStack(e.item()))) {
                    slots.merge(e, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<Entry, Integer> s : slots.entrySet()) {
            Item item = s.getKey().item();
            int want = Math.min(Pouch.count(player, item), Math.max(1, item.getDefaultMaxStackSize()) * Math.min(STAGE_SLOTS_MAX, s.getValue()));
            if (want <= 0) {
                continue;
            }
            int taken = Pouch.take(player, item, want);
            ItemStack out = new ItemStack(item, taken);
            player.getInventory().add(out);
            if (!out.isEmpty()) {
                // 持ち物に入らなかった分は旅嚢へ戻す (戻せないときだけ足もと)
                Pouch.add(player, out);
                if (!out.isEmpty()) {
                    PouchRouting.giveOrDrop(player, out);
                }
            }
        }
        player.getInventory().setChanged();
    }

    // 置いたあと: 枠へ置かれなかった旅嚢の品 (出した分の残り・枠から戻った分) を旅嚢へ戻す
    public static void unstage(ServerPlayer player) {
        if (PouchConfig.enabled(player.server)) {
            Pouch.sweep(player);
        }
    }

    // ---------------------------------------------------------------- クライアントのレシピブックの「作れる」
    // 旅嚢の材料を数に足す (クライアントだけ。サーバーは置くときに持ち物へ出すので、持ち物の数がそのまま使われる)
    public static void fillClient(Player player, StackedContents contents) {
        if (player == null || !player.level().isClientSide || !PouchConfig.enabled()) {
            return;
        }
        for (Entry e : PouchEntries.enabled()) {
            if (!craftable(e)) {
                continue;
            }
            int n = Pouch.count(player, e.item());
            if (n > 0) {
                contents.accountStack(new ItemStack(e.item(), n), n);
            }
        }
    }

    // ---------------------------------------------------------------- 帯のクリック (PouchNet.Grid)
    // 帯の品を左クリック (right = false) = 一スタックをカーソルへ取る、右クリック = 一つ取る。
    // カーソルに同じ品を持っているとき = 左クリックで全部、右クリックで一つを旅嚢へ戻す。ほかの品を持っているときは何も起きない。
    // 作業台 (と持ち物の 2x2) が開いているときだけ。取った品には印を付けない (普通の品として枠へ置ける)。戻すのは閉じたあとも掃除が見ている
    public static boolean grid(ServerPlayer player, ResourceLocation id, boolean right) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!PouchConfig.enabled(player.server) || !(menu instanceof CraftingMenu || menu instanceof InventoryMenu)) {
            return false;
        }
        Entry e = PouchEntries.byId(id);
        if (e == null || !e.enabled() || !craftable(e) || !usableInRecipes(player.server.getRecipeManager()).contains(e)) {
            return false;
        }
        ItemStack carried = menu.getCarried();
        boolean changed = false;
        if (carried.isEmpty()) {
            int have = Pouch.count(player, e.item());
            int n = Math.min(have, right ? 1 : Math.max(1, e.item().getDefaultMaxStackSize()));
            if (n > 0 && Pouch.take(player, e.item(), n) == n) {
                menu.setCarried(new ItemStack(e.item(), n));
                changed = true;
            }
        } else if (carried.is(e.item()) && carried.getComponentsPatch().isEmpty()) {
            ItemStack back = right ? carried.copyWithCount(1) : carried.copy();
            int moved = Pouch.add(player, back);
            if (moved > 0) {
                carried.shrink(moved);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                changed = true;
            }
        }
        if (changed) {
            menu.broadcastChanges();
        }
        return changed;
    }

    @Nullable
    public static Entry entry(ResourceLocation id) {
        return PouchEntries.byId(id);
    }
}
