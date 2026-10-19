package jp.revati.skyabyss.pouch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.guide.RecipeLock;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

// 旅嚢の「作り直し」(docs/design/22 の 14 章 205)。心核・素材は袋の中にあって作業台には並べられないので、
// 袋の品を材料に使う作業台のレシピを、袋の画面から作る。材料は袋の中から先に (足りなければ持ち物から) 払い、できた品は袋 → 持ち物 → 足もとへ渡す。
//   一覧   : 作業台のレシピのうち、材料に袋の品が一つでもあるもの (呼び笛・呼び貝・標・共鳴の炉・温石・燠の灯・花火…)。
//            その品を一度も手に入れていない (袋の「いちど入れた品」に無い) 人には出さない (まだ知らない品を明かさない)。
//            一覧の並びはレシピ ID の順で、クライアントとサーバーで同じ (メニューのボタン番号 = この並びの何番目か)
//   修理   : 手に持った壊れた品を、袋の素材 (その品の修理の素材) で直す。金床は袋の品を並べられないので、ここで済ませる
public final class PouchRebuild {
    // メニューのボタン番号: 修理 = 0、作り直し = RECIPE_BASE + 一覧の番号
    public static final int BUTTON_REPAIR = 0;
    public static final int BUTTON_RECIPE_BASE = 100;

    private PouchRebuild() {
    }

    // 一つの材料が袋の品のどれに当たるか (当たる袋の品。なければ空)
    private static List<PouchEntries.Entry> pouchMatches(Ingredient ingredient) {
        List<PouchEntries.Entry> out = new ArrayList<>();
        if (ingredient.isEmpty()) {
            return out;
        }
        for (PouchEntries.Entry e : PouchEntries.enabled()) {
            if (ingredient.test(new ItemStack(e.item()))) {
                out.add(e);
            }
        }
        return out;
    }

    // 材料に袋の品を含む作業台のレシピ (ID の順。クライアントでもサーバーでも同じ並び)
    public static List<RecipeHolder<CraftingRecipe>> recipes(RecipeManager manager, RegistryAccess access) {
        List<RecipeHolder<CraftingRecipe>> out = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe r = holder.value();
            if (r.isSpecial() || r.getResultItem(access).isEmpty()) {
                continue;
            }
            for (Ingredient ing : r.getIngredients()) {
                if (!pouchMatches(ing).isEmpty()) {
                    out.add(holder);
                    break;
                }
            }
        }
        out.sort(Comparator.comparing(h -> h.id().toString()));
        return out;
    }

    // この人に見せてよいか: 鍵のかかったレシピ (旅の覚書・職人が解くまで作れない。RecipeLock) は解けていること、材料の袋の品を、どれも一度は手に入れていること
    public static boolean visible(Player player, RecipeHolder<CraftingRecipe> holder) {
        // 鍵の判定はサーバーだけ (クライアントの画面は同じ判定を自分のレシピブックで足す)
        if (player instanceof ServerPlayer sp && !RecipeLock.mayCraft(sp, holder)) {
            return false;
        }
        CraftingRecipe recipe = holder.value();
        PouchData data = Pouch.data(player);
        for (Ingredient ing : recipe.getIngredients()) {
            List<PouchEntries.Entry> matches = pouchMatches(ing);
            if (!matches.isEmpty() && matches.stream().noneMatch(e -> data.hasSeen(e.id()) || data.count(e.id()) > 0)) {
                return false;
            }
        }
        return true;
    }

    // 材料の割り当て: 袋から払う数 (品 → 個数) と、持ち物から払う数 (枠の番号 → 個数)
    public record Plan(Map<Item, Integer> fromPouch, Map<Integer, Integer> fromInventory) {
    }

    // 払える割り当てを作る。足りなければ null。材料は 袋 → 持ち物 の順で探す (袋の品に当たる材料は袋を先に)
    public static Plan plan(Player player, CraftingRecipe recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        Map<Item, Integer> pouch = new HashMap<>();
        Map<Integer, Integer> inv = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) {
                continue;
            }
            boolean ok = false;
            for (PouchEntries.Entry e : pouchMatches(ing)) {
                int have = Pouch.count(player, e.item()) - pouch.getOrDefault(e.item(), 0);
                if (have > 0) {
                    pouch.merge(e.item(), 1, Integer::sum);
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                for (int i = 0; i < inventory.items.size(); i++) {
                    ItemStack s = inventory.items.get(i);
                    if (!s.isEmpty() && ing.test(s) && s.getCount() - inv.getOrDefault(i, 0) > 0) {
                        inv.merge(i, 1, Integer::sum);
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok) {
                return null;
            }
        }
        return new Plan(pouch, inv);
    }

    // 作る。材料を払い、できた品を渡す。作れたら true。足りない・見せてよくない・番号が違うときは false (足りないときは帯に出す)
    public static boolean rebuild(ServerPlayer player, int index) {
        if (!PouchConfig.enabled(player.server)) {
            return false;
        }
        List<RecipeHolder<CraftingRecipe>> list = recipes(player.server.getRecipeManager(), player.registryAccess());
        if (index < 0 || index >= list.size()) {
            return false;
        }
        CraftingRecipe recipe = list.get(index).value();
        if (!visible(player, list.get(index))) {
            return false;
        }
        Plan plan = plan(player, recipe);
        if (plan == null) {
            Notice.bar(player, Component.translatable("skyabyss_pouch.rebuild.short"));
            return false;
        }
        plan.fromPouch().forEach((item, n) -> Pouch.take(player, item, n));
        Inventory inventory = player.getInventory();
        plan.fromInventory().forEach((slot, n) -> inventory.items.get(slot).shrink(n));
        inventory.setChanged();
        ItemStack result = recipe.getResultItem(player.registryAccess()).copy();
        Component name = result.getHoverName();
        PouchRouting.giveOrDrop(player, result);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.7f, 1.2f);
        Notice.bar(player, Component.translatable("skyabyss_pouch.rebuild.done", name));
        return true;
    }

    // 修理する: 手に持った壊れた品を、袋の素材で直す。一つの素材で最大耐久の 1/4 (金床と同じ)。直した耐久を返す (0 = できない)
    public static int repair(ServerPlayer player) {
        if (!PouchConfig.enabled(player.server)) {
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !held.isDamaged()) {
            Notice.bar(player, Component.translatable("skyabyss_pouch.rebuild.repair_none"));
            return 0;
        }
        int unit = Math.max(1, held.getMaxDamage() / 4);
        for (PouchEntries.Entry e : PouchEntries.enabled()) {
            int have = Pouch.count(player, e.item());
            if (have <= 0 || !held.getItem().isValidRepairItem(held, new ItemStack(e.item()))) {
                continue;
            }
            int need = Math.min(have, (held.getDamageValue() + unit - 1) / unit);
            int repaired = Math.min(held.getDamageValue(), need * unit);
            Pouch.take(player, e.item(), need);
            held.setDamageValue(held.getDamageValue() - repaired);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.3f);
            Notice.bar(player, Component.translatable("skyabyss_pouch.rebuild.repaired", held.getHoverName(), need, e.item().getDescription()));
            return repaired;
        }
        Notice.bar(player, Component.translatable("skyabyss_pouch.rebuild.repair_short"));
        return 0;
    }
}
