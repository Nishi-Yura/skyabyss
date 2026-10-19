package jp.revati.skyabyss.rpg.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;

// 武器の型 (重・長・速・遠・守)。武器 13 種を 5 つにまとめる (docs/design/27 の 3.9)。
// 縛りは作らない: どの武器でも技は使える。合う型を持つと、その技にちょっとしたおまけが付く (上限 +15%)。
// 品の種類は名前で見分ける (まだ作られていない武器も、名前が合えば効く)。バニラの品も近いものへ
public enum WeaponType {
    HEAVY("heavy"),
    LONG("long"),
    FAST("fast"),
    RANGED("ranged"),
    GUARD("guard");

    // 型のおまけの合計の上限
    public static final double AFFINITY_CAP = 0.15;

    public final String id;

    WeaponType(String id) {
        this.id = id;
    }

    public String nameKey() {
        return "weapon_type.skyabyss." + id;
    }

    // 品の型 (どれでもなければ null)
    public static WeaponType of(Item item) {
        if (item == null) {
            return null;
        }
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (contains(path, "greatsword", "hammer", "warhammer", "waraxe", "battleaxe", "greataxe")) {
            return HEAVY;
        }
        if (contains(path, "pike", "trident", "harpoon", "halberd", "poker", "spear", "lance")) {
            return LONG;
        }
        if (contains(path, "fang", "dagger", "claw", "shears", "pincer")) {
            return FAST;
        }
        if (contains(path, "bow", "crossbow", "longbow")) {
            return RANGED;
        }
        if (contains(path, "shield")) {
            return GUARD;
        }
        if (item instanceof AxeItem) {
            return HEAVY;
        }
        if (item instanceof TridentItem) {
            return LONG;
        }
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return RANGED;
        }
        if (item instanceof ShieldItem) {
            return GUARD;
        }
        return null;
    }

    // 名前を _ で区切った語のどれかが、words の語で始まるか (spike が pike に当たらないように、語の頭で比べる)
    private static boolean contains(String path, String... words) {
        for (String token : path.split("_")) {
            for (String w : words) {
                if (token.startsWith(w)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 手に持っている品の型 (主の手。遠い武器は両手のどちらでも)
    public static WeaponType held(Player player) {
        WeaponType main = of(player.getMainHandItem().getItem());
        if (main != null) {
            return main;
        }
        return of(player.getOffhandItem().getItem()) == RANGED ? RANGED : null;
    }

    // 持っているものが、この型を満たすか。守 = 左手の盾でもよい
    public static boolean has(Player player, WeaponType type) {
        if (type == GUARD) {
            return of(player.getOffhandItem().getItem()) == GUARD || of(player.getMainHandItem().getItem()) == GUARD;
        }
        if (type == RANGED) {
            return of(player.getMainHandItem().getItem()) == RANGED || of(player.getOffhandItem().getItem()) == RANGED;
        }
        return of(player.getMainHandItem().getItem()) == type;
    }

    public static boolean isHeld(ItemStack stack, WeaponType type) {
        return !stack.isEmpty() && of(stack.getItem()) == type;
    }
}
