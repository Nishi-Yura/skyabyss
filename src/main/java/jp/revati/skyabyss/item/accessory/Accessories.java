package jp.revati.skyabyss.item.accessory;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 装飾の枠の入口。ほかの担当 (装備の効果、巨神の装備) もここを通す。
//  - worn: 枠に付けていて、必要レベルに届いている品だけが効く (持ち物にあるだけでは効かない)
//  - 護符は B キー (UseCharmPayload) で使う。右クリックで手から枠へ付ける (装飾も同じ)
//  - 死んだとき: 持ち物と同じに落とす。keepInventory と、岩鯨の背 (持ち物を預かる仕組みが落とす前に止める) では残る
//  - 枠を作る前のセーブ: 持ち物にある装飾品を、一度だけ空いた枠へ移す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Accessories {
    private Accessories() {
    }

    public static AccessoryData get(Player player) {
        return player.getData(AccessorySetup.DATA);
    }

    public static void sync(Player player) {
        if (player instanceof ServerPlayer sp && sp.connection != null) {
            sp.syncData(AccessorySetup.DATA);
        }
    }

    public static ItemStack stack(Player player, int index) {
        return get(player).items.get(index);
    }

    // この品を枠に付けていて、力が出ているか
    public static boolean worn(Player player, Item item) {
        for (ItemStack s : get(player).items) {
            if (s.is(item) && GearLevel.meets(player, s)) {
                return true;
            }
        }
        return false;
    }

    // 枠 index にこの品を置けるか: 種類が合う、もう一つの枠に同じ品がない、必要レベルに届いている
    public static boolean canPlace(Player player, AccessoryData data, int index, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (!(stack.getItem() instanceof Accessory a) || a.accessoryKind() != AccessoryData.kindOf(index)) {
            return false;
        }
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            if (i != index && data.items.get(i).is(stack.getItem())) {
                return false;
            }
        }
        return GearLevel.meets(player, stack);
    }

    // 品の種類に合う枠 (空いている所を先に。なければ同じ種類の最初の枠)。合う枠がなければ -1
    public static int slotFor(Player player, AccessoryData data, ItemStack stack) {
        if (!(stack.getItem() instanceof Accessory a)) {
            return -1;
        }
        int fallback = -1;
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            if (AccessoryData.kindOf(i) != a.accessoryKind()) {
                continue;
            }
            if (data.items.get(i).is(stack.getItem())) {
                // 同じ品が付いている: その枠と入れ替える
                return i;
            }
            if (data.items.get(i).isEmpty()) {
                return i;
            }
            if (fallback < 0) {
                fallback = i;
            }
        }
        return fallback;
    }

    // ---------------------------------------------------------------- 手から枠へ (右クリック)
    // 合う枠へ付け、前に付いていた品を手に戻す。付けられなければ理由を知らせて false
    public static boolean equipFromHand(ServerPlayer player, ItemStack held) {
        AccessoryData data = get(player);
        if (!GearLevel.meets(player, held)) {
            GearLevel.denyMessage(player, held);
            return false;
        }
        int slot = slotFor(player, data, held);
        if (slot < 0) {
            return false;
        }
        ItemStack one = held.copyWithCount(1);
        ItemStack old = data.items.get(slot);
        data.items.set(slot, ItemStack.EMPTY);
        if (!canPlace(player, data, slot, one)) {
            data.items.set(slot, old);
            return false;
        }
        data.items.set(slot, one);
        held.shrink(1);
        if (!old.isEmpty() && !player.getInventory().add(old)) {
            player.drop(old, false);
        }
        sync(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        AccessoryKind kind = AccessoryData.kindOf(slot);
        player.displayClientMessage(ModText.progress("skyabyss_gear.message.equipped." + kind.key, one.getHoverName()), true);
        return true;
    }

    // ---------------------------------------------------------------- 護符を使う
    public static void useCharm(ServerPlayer player) {
        ItemStack charm = stack(player, AccessoryData.CHARM);
        if (charm.isEmpty() || !(charm.getItem() instanceof Accessory a)) {
            player.displayClientMessage(ModText.deny("skyabyss_gear.message.no_charm"), true);
            return;
        }
        if (!GearLevel.meets(player, charm)) {
            GearLevel.denyMessage(player, charm);
            return;
        }
        if (player.getCooldowns().isOnCooldown(charm.getItem())) {
            int seconds = Math.max(1, Math.round(player.getCooldowns().getCooldownPercent(charm.getItem(), 0) * cooldownTicks(charm) / 20f));
            player.displayClientMessage(ModText.deny("skyabyss_gear.message.charm_resting", charm.getHoverName(), seconds), true);
            return;
        }
        a.activate(player, charm);
    }

    private static int cooldownTicks(ItemStack charm) {
        return charm.getItem() instanceof jp.revati.skyabyss.item.ResonanceCharmItem ? jp.revati.skyabyss.item.ResonanceCharmItem.COOLDOWN : 20 * 60;
    }

    public static void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AccessoryMenu(id, inventory),
                Component.translatable("skyabyss_gear.screen.title")));
    }

    // ---------------------------------------------------------------- 前のセーブ: 持ち物の装飾品を一度だけ枠へ
    // 移した数を返す。必要レベルに届かない品と、枠が埋まっている品は持ち物に残す
    public static int migrate(ServerPlayer player) {
        AccessoryData data = get(player);
        if (data.migrated) {
            return 0;
        }
        data.migrated = true;
        Inventory inventory = player.getInventory();
        int moved = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!(s.getItem() instanceof Accessory)) {
                continue;
            }
            int slot = slotFor(player, data, s);
            if (slot < 0 || !data.items.get(slot).isEmpty() || !canPlace(player, data, slot, s)) {
                continue;
            }
            data.items.set(slot, s.split(1));
            inventory.setItem(i, s);
            moved++;
        }
        if (moved > 0) {
            inventory.setChanged();
            player.sendSystemMessage(ModText.hint("skyabyss_gear.message.migrated", moved));
        }
        sync(player);
        return moved;
    }

    // ---------------------------------------------------------------- 死んだとき
    // 落とすかどうか: keepInventory なら落とさない。岩鯨の背では、預かりの仕組み (TitanBackTrials) が落とす前に止める
    // (止められた LivingDropsEvent はここに届かないので、枠の中身は copyOnDeath で残る)
    public static boolean keepsOnDeath(ServerPlayer player) {
        return player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)
                || player.level().dimension() == TitanTravel.TITAN_BACK;
    }

    // 枠の中身を足もとへ落とす品にして返し、枠を空にする (消滅の呪いの品は消える)
    public static List<ItemEntity> takeDeathDrops(ServerPlayer player) {
        AccessoryData data = get(player);
        List<ItemEntity> drops = new ArrayList<>();
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            ItemStack s = data.items.get(i);
            data.items.set(i, ItemStack.EMPTY);
            if (s.isEmpty() || EnchantmentHelper.has(s, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                continue;
            }
            ItemEntity e = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), s);
            e.setDefaultPickUpDelay();
            float f = player.getRandom().nextFloat() * 0.5f;
            float r = player.getRandom().nextFloat() * ((float) Math.PI * 2f);
            e.setDeltaMovement(-Math.sin(r) * f, 0.2, Math.cos(r) * f);
            drops.add(e);
        }
        sync(player);
        return drops;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !keepsOnDeath(player) && !get(player).isEmpty()) {
            event.getDrops().addAll(takeDeathDrops(player));
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 11 && !get(player).migrated) {
            migrate(player);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }
}
