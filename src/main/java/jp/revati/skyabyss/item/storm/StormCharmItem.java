package jp.revati.skyabyss.item.storm;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.item.accessory.Accessory;
import jp.revati.skyabyss.item.accessory.AccessoryKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 嵐の護符 (レア): 銅の避雷針の小さな束に、稲妻を閉じこめた護符。護符の枠に付けて H キーで使うと、
// 次の 3 回の近接・射撃に雷を足す (一回 5、まわりへは届かない。StormGearEvents)。使ってから 30 秒で消え、待ちも 30 秒
public class StormCharmItem extends Item implements ModGear, Accessory {
    // 残りの回数と、消える時刻 (ゲームの時刻)
    public static final String CHARGES = SkyAbyss.MODID + ":storm_charm_charges";
    public static final String UNTIL = SkyAbyss.MODID + ":storm_charm_until";

    public StormCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp && !Accessories.equipFromHand(sp, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public AccessoryKind accessoryKind() {
        return AccessoryKind.CHARM;
    }

    @Override
    public int cooldownTicks() {
        return StormGear.CHARM_COOLDOWN;
    }

    // 今残っている雷の回数 (消える時刻を過ぎていれば 0)
    public static int charges(Player player) {
        if (player.level().getGameTime() > player.getPersistentData().getLong(UNTIL)) {
            return 0;
        }
        return player.getPersistentData().getInt(CHARGES);
    }

    public static void spend(Player player) {
        player.getPersistentData().putInt(CHARGES, Math.max(0, charges(player) - 1));
    }

    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        player.getPersistentData().putInt(CHARGES, StormGear.CHARM_CHARGES);
        player.getPersistentData().putLong(UNTIL, level.getGameTime() + StormGear.CHARM_COOLDOWN);
        // 腕に稲妻がまとわりつく: 体のまわりを上る火花の螺旋と、白い閃き
        for (int i = 0; i < 36; i++) {
            double a = i * 0.55;
            double y = player.getY() + 0.1 + i * 0.055;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + Math.cos(a) * 0.7, y, player.getZ() + Math.sin(a) * 0.7, 2, 0.03, 0.03, 0.03, 0.05);
        }
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 12, 0.4, 0.6, 0.4, 0.03);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35f, 1.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.7f, 1.4f);
        player.getCooldowns().addCooldown(this, StormGear.CHARM_COOLDOWN);
        player.displayClientMessage(Component.translatable("message.skyabyss.storm_charm.armed", StormGear.CHARM_CHARGES)
                .withStyle(s -> s.withColor(0xBFE6FF)), true);
        return true;
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
    public boolean enhanceable() {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.storm_charm.desc", false);
    }
}
