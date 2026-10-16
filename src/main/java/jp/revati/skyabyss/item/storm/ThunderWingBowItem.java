package jp.revati.skyabyss.item.storm;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearEvents;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 雷翼の弓 (レア): 雷翼の風切羽の軸を弓幹にした弓。引き方はバニラの弓と同じ (1 秒で満月)。
//  - 矢の傷 ×1.75 (強化で +3%/段。剣の強化と同じ伸び)。1 秒あたり +0 で 12.7、+10 で 16.4 (近接の目安 15.2 / 19.8 の 0.85 倍。離れて撃てる分)
//  - 命中で感電: 当たった敵が 3 秒光り、まわり 2.5 ブロックの別の敵一体に 1.5 (+5 から 2.5 を二体へ)。StormGearEvents
// 必要 Lv に届かないときは、ふつうの弓と同じ矢になる (倍率と感電なし)。ランダムな特性は付かない (判断: 弓の特性は剣の修飾子で効かないため)
public class ThunderWingBowItem extends BowItem implements ModGear {
    // 矢に付ける印 (感電と、護符の雷が「この弓の矢」を見分ける)。値は強化の段
    public static final String ARROW_TAG = SkyAbyss.MODID + ":storm_bow";

    public ThunderWingBowItem(Properties properties) {
        super(properties);
    }

    // 矢の傷の倍率 (強化込み)
    public static double damageMultiplier(int enhance) {
        return StormGear.BOW_DAMAGE * (1 + GearEvents.ATTACK_RATE_PER_LEVEL * enhance);
    }

    // 満月で放った矢の、会心込みの平均の傷 (AbstractArrow の式: ceil(速さ 3 × 基礎 2 × 倍率) + 会心 nextInt(i/2+2))
    public static double fullShotDamage(double multiplier) {
        int i = Mth.ceil(3.0 * 2.0 * multiplier);
        return i + (i / 2 + 1) / 2.0;
    }

    // 一体への 1 秒あたり (満月で撃ち続ける)
    public static double dps(int enhance) {
        return fullShotDamage(damageMultiplier(enhance)) / StormGear.BOW_CYCLE;
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        if (arrow.getOwner() instanceof Player player && !GearLevel.meets(player, weaponStack)) {
            return arrow;
        }
        int enhance = ModGear.enhance(weaponStack);
        arrow.setBaseDamage(arrow.getBaseDamage() * damageMultiplier(enhance));
        arrow.getPersistentData().putInt(ARROW_TAG, enhance + 1);
        return arrow;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(StormGearSetup.SHARD.get());
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public GearTier tier() {
        return GearTier.RARE;
    }

    @Override
    public String dungeon() {
        return StormGear.TITAN;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.thunder_wing_bow.desc", true);
        int enhance = ModGear.enhance(stack);
        tooltip.add(Component.translatable("item.skyabyss.thunder_wing_bow.power",
                String.format(java.util.Locale.ROOT, "%.2f", damageMultiplier(enhance))).withStyle(s -> s.withColor(0x9FE8FF)));
    }
}
