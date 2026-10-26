package jp.revati.skyabyss.rpg.skill;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

// 職 (docs/design/43。6 職)。持った武器で職が決まる (FFXIV の形)。職ごとに技の木が一本 (SkillTree)、技の点も職ごと。
// 名前はわかる名前 (job.skyabyss.<id>) に、世界の呼び名 (木の名前 skilltree.skyabyss.<木>) を副題で
public enum Job {
    WARRIOR("warrior", SkillTree.ROCK),
    SMITH("smith", SkillTree.ASH),
    MAGE("mage", SkillTree.STAR),
    ARCHER("archer", SkillTree.BONE),
    ASSASSIN("assassin", SkillTree.WIND),
    SHAMAN("shaman", SkillTree.TIDE);

    public final String id;
    public final SkillTree tree;

    Job(String id, SkillTree tree) {
        this.id = id;
        this.tree = tree;
    }

    public String nameKey() {
        return "job.skyabyss." + id;
    }

    public String roleKey() {
        return "job.skyabyss." + id + ".role";
    }

    @Nullable
    public static Job byId(String id) {
        for (Job j : values()) {
            if (j.id.equals(id)) {
                return j;
            }
        }
        return null;
    }

    // この木の職 (職の木でなければ null。淵・前世の技はどの職でもない)
    @Nullable
    public static Job ofTree(SkillTree tree) {
        for (Job j : values()) {
            if (j.tree == tree) {
                return j;
            }
        }
        return null;
    }

    // 手に持っている武器の職 (主の手。弓はどちらの手でも)。職の武器でなければ null
    @Nullable
    public static Job held(Player player) {
        Job main = of(player.getMainHandItem());
        if (main != null) {
            return main;
        }
        Job off = of(player.getOffhandItem());
        return off == ARCHER ? off : null;
    }

    @Nullable
    public static Job of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : of(stack.getItem());
    }

    // 品の職。品の名前の語で見分ける (この作品の武器は名前に種類が入っている)。バニラの品は種類で
    @Nullable
    public static Job of(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        // 呪術師の鈴の杖・祭具 (鈴だけ・お守り (charm) は武器ではない: 鐘銭・遺物の鈴・装身具)
        if (word(path, "rite", "totem", "censer") || word(path, "bell") && word(path, "staff", "wand")) {
            return SHAMAN;
        }
        if (word(path, "staff", "wand", "rod", "scepter")) {
            return path.contains("lightning_rod") || path.contains("fishing_rod") || path.contains("blaze_rod") || path.contains("breeze_rod") ? null : MAGE;
        }
        if (word(path, "bow", "crossbow", "longbow")) {
            return ARCHER;
        }
        if (word(path, "fang", "dagger", "claw", "shears", "pincer", "knife")) {
            return ASSASSIN;
        }
        // 鍛冶師: 鎚・斧・火掻き・錨 (重く打ち下ろす)・大鎌 (余燼の灰の刈り手)
        if (word(path, "hammer", "warhammer", "axe", "waraxe", "battleaxe", "greataxe", "poker", "mace", "maul", "anchor", "scythe")) {
            return SMITH;
        }
        if (word(path, "sword", "greatsword", "blade", "pike", "spear", "lance", "glaive", "halberd", "harpoon", "trident", "shield")) {
            return WARRIOR;
        }
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return ARCHER;
        }
        if (item instanceof AxeItem || item instanceof MaceItem) {
            return SMITH;
        }
        if (item instanceof SwordItem || item instanceof TridentItem || item instanceof ShieldItem) {
            return WARRIOR;
        }
        return null;
    }

    // 名前を _ で区切った語のどれかが words の語と同じか (spike が pike に、rod が lightning_rod に当たらないよう語ごとに)
    private static boolean word(String path, String... words) {
        for (String token : path.split("_")) {
            for (String w : words) {
                if (token.equals(w) || token.equals(w + "s")) {
                    return true;
                }
            }
        }
        return false;
    }
}
