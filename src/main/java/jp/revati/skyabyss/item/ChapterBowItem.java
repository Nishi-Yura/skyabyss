package jp.revati.skyabyss.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 職の章の弓 (ChapterGear) と祠の弓。引き方はバニラの弓と同じ。矢の傷 × 倍率 (強化で +3%/段。雷翼の弓と同じ伸び)。
// 必要 Lv に届かないときは、ふつうの弓と同じ矢になる
public class ChapterBowItem extends BowItem implements ModGear {
    // 満月で撃ち続けたときの一射の間 (秒。雷翼の弓と同じ)
    public static final double CYCLE = 1.1;

    private final String dungeon;
    private final double multiplier;
    private final GearTier gearTier;
    private final String descKey;

    public ChapterBowItem(Properties properties, String dungeon, double multiplier, String descKey) {
        this(properties, dungeon, multiplier, GearTier.EPIC, descKey);
    }

    public ChapterBowItem(Properties properties, String dungeon, double multiplier, GearTier tier, String descKey) {
        super(properties);
        this.dungeon = dungeon;
        this.multiplier = multiplier;
        this.gearTier = tier;
        this.descKey = descKey;
    }

    public double multiplier(int enhance) {
        return multiplier * (1 + GearEvents.ATTACK_RATE_PER_LEVEL * enhance);
    }

    // 一体への 1 秒あたり (満月で撃ち続ける。AbstractArrow の式: ceil(速さ 3 × 基礎 2 × 倍率) + 会心の平均)
    public double dps(int enhance) {
        int i = Mth.ceil(3.0 * 2.0 * multiplier(enhance));
        return (i + (i / 2 + 1) / 2.0) / CYCLE;
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        if (arrow.getOwner() instanceof Player player && !GearLevel.meets(player, weaponStack)) {
            return arrow;
        }
        arrow.setBaseDamage(arrow.getBaseDamage() * multiplier(ModGear.enhance(weaponStack)));
        return arrow;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public GearTier tier() {
        return gearTier;
    }

    @Override
    public String dungeon() {
        return dungeon;
    }

    @Override
    public int requiredLevel() {
        return gearTier == GearTier.COMMON ? 1 : ModGear.super.requiredLevel();
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), gearTier);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, gearTier, descKey, true);
    }
}
