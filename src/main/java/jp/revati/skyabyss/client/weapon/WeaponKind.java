package jp.revati.skyabyss.client.weapon;

import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// プレイヤーが持つ mod 武器の「型」(三人称の腕の構えの登録名 SKYABYSS_*)。
// 動きそのもの (構え・振り・連撃・当たる瞬間・ヒットストップ) は、品ごとに weapon/MoveSet (assets の weapon_moves/<id>.json。tools/gen_weapon_moves.py が作る) が持つ。
// 弓 (雷翼の弓・深淵の雷弓) と盾 (珊瑚の盾) は、バニラの引き・構えの動きに MoveSet の一人称の動きを足す (WeaponClient)。
// 槍・長柄・火かき棒は、穂先が腕の向きに前へ伸びる持ち方 (tools/weapon_display.py の forward) で、構えも腕を前へ出す
public enum WeaponKind {
    GREATSWORD,
    PIKE,
    GLAIVE,
    FANG,
    CLAW,
    AXE,
    HAMMER,
    POKER;

    // 武器の id (パス) → 型
    private static final Map<String, WeaponKind> BY_ITEM = Map.ofEntries(
            Map.entry("whalebone_greatsword", GREATSWORD),
            Map.entry("captain_pike", PIKE),
            Map.entry("tide_shell_trident", PIKE),
            Map.entry("coral_harpoon", PIKE),
            Map.entry("warden_glaive", GLAIVE),
            Map.entry("waterway_fang", FANG),
            Map.entry("raiju_claw", CLAW),
            Map.entry("tidecaller_claw", CLAW),
            Map.entry("ember_war_axe", AXE),
            Map.entry("smith_hammer", HAMMER),
            Map.entry("thrall_poker", POKER),
            // 深淵の専用伝説 (剣型の 3 点。弓は WeaponClient が BowExtension で登録する)
            Map.entry("abyss_greatsword", GREATSWORD),
            Map.entry("abyss_bell_staff", POKER),
            Map.entry("abyss_ash_scythe", AXE));

    public static Iterable<Map.Entry<String, WeaponKind>> items() {
        return BY_ITEM.entrySet();
    }

    public static WeaponKind of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "skyabyss".equals(id.getNamespace()) ? BY_ITEM.get(id.getPath()) : null;
    }
}
