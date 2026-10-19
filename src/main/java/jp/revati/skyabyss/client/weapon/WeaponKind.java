package jp.revati.skyabyss.client.weapon;

import static jp.revati.skyabyss.client.weapon.SwingMove.BY;
import static jp.revati.skyabyss.client.weapon.SwingMove.LEAN;
import static jp.revati.skyabyss.client.weapon.SwingMove.MX;
import static jp.revati.skyabyss.client.weapon.SwingMove.MY;
import static jp.revati.skyabyss.client.weapon.SwingMove.MZ;
import static jp.revati.skyabyss.client.weapon.SwingMove.OX;
import static jp.revati.skyabyss.client.weapon.SwingMove.OY;
import static jp.revati.skyabyss.client.weapon.SwingMove.OZ;
import static jp.revati.skyabyss.client.weapon.SwingMove.PZ;
import static jp.revati.skyabyss.client.weapon.SwingMove.RX;
import static jp.revati.skyabyss.client.weapon.SwingMove.RY;
import static jp.revati.skyabyss.client.weapon.SwingMove.RZ;
import static jp.revati.skyabyss.client.weapon.SwingMove.TX;
import static jp.revati.skyabyss.client.weapon.SwingMove.TY;
import static jp.revati.skyabyss.client.weapon.SwingMove.TZ;

import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// プレイヤーが持つ mod 武器の種類と、種類ごとの振りの動き (docs/design/15 の「武器を持った姿と攻撃の動き」)。
// 弓 (雷翼の弓) と盾 (珊瑚の盾) は、バニラの引き・構えの動きをそのまま使う。
// 槍・長柄・火かき棒は、穂先が腕の向きに前へ伸びる持ち方 (tools/weapon_display.py の forward) で、構えも腕を前へ出す
public enum WeaponKind {
    // 鯨骨の大剣: 両手で振りかぶり、斜めに断ち下ろす
    GREATSWORD(SwingMove.at(0f, 0.28f, 0.5f, 0.75f, 1f)
            .set(MX, -0.45f, -2.8f, -0.3f, 0.0f, -0.45f)
            .set(MY, 0f, 0.2f, -0.45f, -0.4f, 0f)
            .set(MZ, 0f, 0.35f, -0.1f, 0f, 0f)
            .set(OX, -0.3f, -2.6f, -0.5f, -0.2f, -0.3f)
            .set(OY, 0f, -0.2f, 0.5f, 0.4f, 0f)
            .set(OZ, 0f, -0.4f, 0f, 0f, 0f)
            .set(BY, 0f, 0.45f, -0.5f, -0.3f, 0f)
            .set(LEAN, 0f, -6f, 14f, 6f, 0f)
            .set(TX, 0f, 0.05f, -0.1f, -0.05f, 0f)
            .set(TY, 0f, 0.4f, -0.08f, -0.04f, 0f)
            .set(TZ, 0f, 0.05f, -0.5f, -0.15f, 0f)
            .set(RX, 0f, 5f, -70f, -28f, 0f)
            .set(RY, 0f, 0f, -12f, -5f, 0f)
            .set(RZ, 0f, 10f, 15f, 6f, 0f)),
    // 長柄 (番兵長の鉾・三叉槍・銛): 肩ごと引いて、まっすぐ突く
    PIKE(SwingMove.at(0f, 0.3f, 0.5f, 0.75f, 1f)
            .idle(-1.15f, -0.1f).idleOff(-1.2f, 0.55f)
            .set(MX, -0.9f, -1.1f, -1.5f, -1.2f, -0.9f)
            .set(MY, -0.1f, 0.2f, -0.25f, -0.15f, -0.1f)
            .set(OX, -1.0f, -1.2f, -1.5f, -1.25f, -1.0f)
            .set(OY, 0.55f, 0.9f, 0.7f, 0.6f, 0.55f)
            .set(PZ, 0f, 3f, -4.5f, -1.5f, 0f)
            .set(BY, 0f, 0.3f, -0.35f, -0.15f, 0f)
            .set(LEAN, 0f, -3f, 8f, 3f, 0f)
            .set(TY, 0f, 0.05f, -0.05f, 0f, 0f)
            .set(TZ, 0f, 0.25f, -0.6f, -0.2f, 0f)
            .set(RX, 0f, 6f, -4f, -2f, 0f)),
    // 鉤槍: 両手で水平に大きく薙ぐ
    GLAIVE(SwingMove.at(0f, 0.25f, 0.5f, 0.75f, 1f)
            .idle(-1.15f, -0.1f).idleOff(-1.2f, 0.55f)
            .set(MX, -1.0f, -1.2f, -1.2f, -1.1f, -1.0f)
            .set(MY, 0f, 1.0f, -1.1f, -0.7f, 0f)
            .set(OX, -1.0f, -1.2f, -1.2f, -1.1f, -1.0f)
            .set(OY, 0f, 1.6f, -0.5f, 0f, 0f)
            .set(BY, 0f, 0.6f, -0.7f, -0.3f, 0f)
            .set(LEAN, 0f, -4f, 6f, 0f, 0f)
            .set(TX, 0f, 0.3f, -0.35f, -0.1f, 0f)
            .set(TZ, 0f, 0.1f, -0.25f, -0.1f, 0f)
            .set(RX, 0f, 0f, 0f, 0f, 0f)
            .set(RY, 0f, -30f, 40f, 15f, 0f)
            .set(RZ, 0f, 0f, 0f, 0f, 0f)),
    // 牙・短剣: 素早い一閃
    FANG(SwingMove.at(0f, 0.2f, 0.45f, 0.7f, 1f)
            .set(MX, -0.35f, -1.4f, -1.0f, -0.6f, -0.35f)
            .set(MY, 0f, 0.7f, -0.8f, -0.3f, 0f)
            .set(MZ, 0f, 0.2f, -0.2f, 0f, 0f)
            .set(BY, 0f, 0.25f, -0.35f, -0.1f, 0f)
            .set(TX, 0f, 0.1f, -0.28f, -0.1f, 0f)
            .set(TY, 0f, 0.3f, -0.1f, -0.04f, 0f)
            .set(TZ, 0f, 0f, -0.25f, -0.1f, 0f)
            .set(RX, 0f, 5f, -40f, -15f, 0f)
            .set(RY, 0f, 0f, 30f, 10f, 0f)
            .set(RZ, 0f, -10f, 35f, 10f, 0f)),
    // 爪: 振り上げて、斜めに引き裂く
    CLAW(SwingMove.at(0f, 0.25f, 0.45f, 0.7f, 1f)
            .set(MX, -0.35f, -2.2f, -0.5f, -0.4f, -0.35f)
            .set(MY, 0f, 0.5f, -0.4f, -0.2f, 0f)
            .set(MZ, 0f, 0.3f, -0.3f, -0.1f, 0f)
            .set(BY, 0f, 0.3f, -0.4f, -0.15f, 0f)
            .set(LEAN, 0f, -3f, 8f, 3f, 0f)
            .set(TY, 0f, 0.35f, -0.3f, -0.1f, 0f)
            .set(TZ, 0f, 0f, -0.3f, -0.1f, 0f)
            .set(RX, 0f, 5f, -50f, -20f, 0f)
            .set(RY, 0f, 0f, 15f, 5f, 0f)
            .set(RZ, 0f, -8f, 25f, 8f, 0f)),
    // 戦斧: 片手で振りかぶって打ち下ろす
    AXE(SwingMove.at(0f, 0.25f, 0.45f, 0.7f, 1f)
            .set(MX, -0.4f, -2.5f, -0.1f, 0.1f, -0.4f)
            .set(MY, 0f, 0.1f, -0.2f, -0.1f, 0f)
            .set(MZ, 0f, 0.2f, -0.05f, 0f, 0f)
            .set(BY, 0f, 0.35f, -0.45f, -0.2f, 0f)
            .set(LEAN, 0f, -5f, 12f, 4f, 0f)
            .set(TY, 0f, 0.45f, -0.32f, -0.1f, 0f)
            .set(TZ, 0f, 0f, -0.3f, -0.1f, 0f)
            .set(RX, 0f, 5f, -65f, -22f, 0f)
            .set(RZ, 0f, 6f, 12f, 4f, 0f)),
    // 鍛え手の大鎚: 大きく振りかぶって、体ごと叩きつけ、少し止まる
    HAMMER(SwingMove.at(0f, 0.35f, 0.5f, 0.65f, 1f)
            .set(MX, -0.45f, -3.0f, -0.05f, 0.0f, -0.45f)
            .set(MY, 0f, 0.1f, -0.3f, -0.3f, 0f)
            .set(MZ, 0f, 0.3f, -0.05f, -0.05f, 0f)
            .set(OX, -0.3f, -2.8f, -0.1f, -0.1f, -0.3f)
            .set(OY, 0f, -0.2f, 0.5f, 0.5f, 0f)
            .set(OZ, 0f, -0.3f, 0f, 0f, 0f)
            .set(BY, 0f, 0.2f, -0.2f, -0.1f, 0f)
            .set(LEAN, 0f, -10f, 20f, 18f, 0f)
            .set(TY, 0f, 0.4f, -0.12f, -0.1f, 0f)
            .set(TZ, 0f, 0f, -0.5f, -0.45f, 0f)
            .set(RX, 0f, 5f, -65f, -50f, 0f)),
    // 火かき棒: 短く鋭い突き
    POKER(SwingMove.at(0f, 0.3f, 0.5f, 0.75f, 1f)
            .idle(-1.0f, -0.1f)
            .set(MX, -0.9f, -1.1f, -1.5f, -1.2f, -0.9f)
            .set(MY, -0.1f, 0.15f, -0.2f, -0.1f, -0.1f)
            .set(PZ, 0f, 2.5f, -4f, -1.5f, 0f)
            .set(BY, 0f, 0.25f, -0.3f, -0.1f, 0f)
            .set(TZ, 0f, 0.12f, -0.5f, -0.1f, 0f)
            .set(RX, 0f, 6f, -3f, 0f, 0f));

    final SwingMove move;

    WeaponKind(SwingMove move) {
        this.move = move;
    }

    // 武器の id (パス) → 種類
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
