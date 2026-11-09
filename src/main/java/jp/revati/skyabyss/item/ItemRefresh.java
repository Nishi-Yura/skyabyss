package jp.revati.skyabyss.item;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.item.affix.Affix;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.affix.AffixType;
import jp.revati.skyabyss.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

// 古いセーブの品を今の決まりにそろえる (2026-11-09 の声「いらない昔のデータ入ってる？ 昔のデータになっているものはすべて最新に変更して
// (例えば、装飾とか)。最新の状態になってるか確認して」)。どれも何度流しても同じ結果になる:
//   野の装飾: 個体差 (70〜130%) をまだ振っていなければ振る (ダンジョンの装飾品は一品ものなので振らない。item/wild/AccessoryRolls)
//   特性: 今の表にない種類・別の部位の種類・重なり・二つ目の印を外し、幅の外の値は幅の端へ寄せ、数を階級の数にそろえる
//         (足りなければ今の表から足し、多ければ後ろを外す。持ち主が鍛え直した値はできるだけ残す)
//   穴: 階級の数にそろえる (item/extra/GearFreedom.ensure)
// 流す所: 入ったとき一度 (upgrade/PlayerUpgrade の段 3。持ち物・エンダーチェスト・倉庫) と、持ち物は 1 秒ごと (item/affix/AffixEvents)
public final class ItemRefresh {
    private ItemRefresh() {
    }

    // 一つの品をそろえる。変えたら true
    public static boolean refresh(ItemStack stack, RandomSource random) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean changed = false;
        if (stack.getItem() instanceof jp.revati.skyabyss.item.wild.WildAccessoryItem
                && !jp.revati.skyabyss.dungeon.DungeonGear.isTrinket(stack.getItem())) {
            changed |= jp.revati.skyabyss.item.wild.AccessoryRolls.ensure(stack, random);
        }
        changed |= AffixRules.ensureRolled(stack, random);
        changed |= normalizeAffixes(stack, random);
        changed |= jp.revati.skyabyss.item.extra.GearFreedom.ensure(stack, random);
        return changed;
    }

    // 特性を今の決まりへ (AffixRules.valid が通る形)。変えたら true
    public static boolean normalizeAffixes(ItemStack stack, RandomSource random) {
        if (!AffixRules.eligible(stack) || !stack.has(ModDataComponents.AFFIXES.get())) {
            return false;
        }
        List<Affix> have = AffixRules.affixes(stack);
        if (AffixRules.valid(stack, have)) {
            return false;
        }
        AffixType.Slot slot = AffixRules.slotOf(stack.getItem());
        GearTier tier = ((ModGear) stack.getItem()).tier();
        boolean legendary = tier.wideAffixes;
        List<Affix> out = new ArrayList<>();
        List<AffixType> seen = new ArrayList<>();
        boolean mark = false;
        for (Affix a : have) {
            AffixType t = a.kind();
            if (t == null || t.slot != slot || seen.contains(t) || t.isMark() && mark) {
                continue;
            }
            double v = Math.max(t.min(legendary), Math.min(t.max(legendary), a.value()));
            out.add(new Affix(a.type(), t.round(v)));
            seen.add(t);
            mark |= t.isMark();
        }
        int max = AffixRules.maxCount(tier);
        while (out.size() > max) {
            out.remove(out.size() - 1);
        }
        int min = AffixRules.minCount(tier);
        // 足りない分は今の表から (印は一つまで)
        for (int tries = 0; out.size() < min && tries < 8; tries++) {
            for (Affix a : AffixRules.roll(stack, random)) {
                AffixType t = a.kind();
                if (out.size() >= min || t == null || seen.contains(t) || t.isMark() && mark) {
                    continue;
                }
                out.add(a);
                seen.add(t);
                mark |= t.isMark();
            }
        }
        if (!AffixRules.valid(stack, out)) {
            // それでもそろわなければ (表が大きく変わった品)、今の表で振り直す
            out = AffixRules.roll(stack, random);
        }
        stack.set(ModDataComponents.AFFIXES.get(), List.copyOf(out));
        stack.remove(ModDataComponents.REFORGE_OFFERS.get());
        return true;
    }

    // 入れ物の中を全部そろえる。変えた数
    public static int refresh(Container c, RandomSource random) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (refresh(c.getItem(i), random)) {
                n++;
            }
        }
        if (n > 0) {
            c.setChanged();
        }
        return n;
    }

    // 人の持ち物・エンダーチェスト・倉庫。変えた数
    public static int refreshAll(ServerPlayer p) {
        RandomSource r = p.getRandom();
        int n = refresh(p.getInventory(), r) + refresh(p.getEnderChestInventory(), r);
        jp.revati.skyabyss.facility.StashData stash = p.getData(jp.revati.skyabyss.facility.FacilitySetup.STASH);
        for (ItemStack s : stash.items()) {
            if (refresh(s, r)) {
                n++;
            }
        }
        return n;
    }
}
