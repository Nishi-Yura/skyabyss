package jp.revati.skyabyss.story.ch6.gear;

import java.util.List;

import jp.revati.skyabyss.item.ChapterBowItem;
import jp.revati.skyabyss.item.ChapterWeaponItem;
import jp.revati.skyabyss.item.GearItem;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 月の装備の品 (docs/design/03 の 12.2)。効果はどれも MoonGearEvents が見る (品の種類と ID で)。
//   Weapon  近接の職の武器 5 本 (鎚・杖・短剣・鈴杖・太刀)。章の武器と同じ SwordItem の作り (強化・特性・持ち方は共通の仕組み)
//   Bow     下弦の弓 (矢 ×2.4)
//   Trinket 満月の環 (装飾の枠)
//   Tear    月の涙 (飲むと 30 秒、次の傷を一度 0 に)
public final class MoonGearItems {
    private MoonGearItems() {
    }

    public static class Weapon extends ChapterWeaponItem {
        public Weapon(Item.Properties properties, double attack, double speed, GearTier tier, String descKey) {
            super(properties, MoonGear.DUNGEON, attack, speed, tier, descKey);
        }

        @Override
        public int requiredLevel() {
            return MoonGear.requiredLevel(tier());
        }
    }

    public static class Bow extends ChapterBowItem {
        public Bow(Item.Properties properties, double multiplier, String descKey) {
            super(properties, MoonGear.DUNGEON, multiplier, descKey);
        }

        @Override
        public int requiredLevel() {
            return MoonGear.requiredLevel(tier());
        }
    }

    public static class Trinket extends GearItem {
        public Trinket(Item.Properties properties, GearTier tier, String descKey, boolean foil) {
            super(properties, tier, descKey, foil);
        }

        @Override
        public String dungeon() {
            return MoonGear.DUNGEON;
        }

        @Override
        public int requiredLevel() {
            return MoonGear.requiredLevel(tier());
        }
    }

    // 月の涙: 飲むと 30 秒、月の光の膜が次の傷を一度だけ受け止める (MoonGearEvents.TEAR)。待ち 60 秒。一つ減る
    public static class Tear extends Item {
        public Tear(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide && level instanceof ServerLevel sl) {
                player.getPersistentData().putLong(MoonGearEvents.TEAR, sl.getGameTime() + MoonGear.TEAR_TICKS);
                player.getCooldowns().addCooldown(this, MoonGear.TEAR_COOLDOWN);
                sl.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.9, 0.5, 0.04);
                sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.4f);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public Component getName(ItemStack stack) {
            return ModGear.name(stack, super.getName(stack), GearTier.RARE);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.skyabyss.moon_tear.desc").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.skyabyss.moon_tear.use").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    // 品が使える (必要 Lv に届く) か
    static boolean usable(Player p, ItemStack stack) {
        return stack.getItem() instanceof ModGear && GearLevel.meets(p, stack);
    }
}
