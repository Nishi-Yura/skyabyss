package jp.revati.skyabyss.abyss;

import java.util.List;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

// 雷淵の弓 (深淵のアーティファクトの弓)。引き方はバニラの弓と同じ。矢の傷は ×2.0 (エピックの雷翼の弓 ×1.75 の 1 割上。
// ×1.8 では満月の矢の傷の切り上げが雷翼の弓と同じ 11 になり、階級の差が出なかった。2026-10-24)。
// 本領は「雲穿ち」の書き換え「天落とし」(Rune.SKY_FALL。主の手に持つあいだだけ)
public class AbyssBowItem extends BowItem implements ModGear {
    public static final double DAMAGE = 2.0;

    // 一体への 1 秒あたり (満月で撃ち続ける。雷翼の弓と同じ式)
    public static double dps(int enhance) {
        return jp.revati.skyabyss.item.storm.ThunderWingBowItem.fullShotDamage(DAMAGE * (1 + jp.revati.skyabyss.item.GearEvents.ATTACK_RATE_PER_LEVEL * enhance))
                / jp.revati.skyabyss.item.storm.StormGear.BOW_CYCLE;
    }

    private final String dungeon;
    private final String descKey;

    public AbyssBowItem(Properties properties, String dungeon, String descKey) {
        super(properties);
        this.dungeon = dungeon;
        this.descKey = descKey;
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        if (arrow.getOwner() instanceof Player player && !GearLevel.meets(player, weaponStack)) {
            return arrow;
        }
        arrow.setBaseDamage(arrow.getBaseDamage() * DAMAGE * (1 + jp.revati.skyabyss.item.GearEvents.ATTACK_RATE_PER_LEVEL * ModGear.enhance(weaponStack)));
        return arrow;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.ECHO_SHARD);
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public GearTier tier() {
        return GearTier.ARTIFACT;
    }

    @Override
    public String dungeon() {
        return dungeon;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), GearTier.ARTIFACT);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, GearTier.ARTIFACT, descKey, true);
    }
}
