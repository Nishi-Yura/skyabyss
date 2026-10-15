package jp.revati.skyabyss.forge;

import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.registry.ModMenus;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// 共鳴の炉の画面: 装備を置く枠が一つと、持ち物。ボタンで強化・分解する (費用は持ち物から払う)
public class ResonanceForgeMenu extends AbstractContainerMenu {
    public static final int BUTTON_ENHANCE = 0;
    public static final int BUTTON_DISMANTLE = 1;
    public static final int GEAR_X = 44;
    public static final int GEAR_Y = 35;

    private final Container gear = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;

    public ResonanceForgeMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public ResonanceForgeMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.RESONANCE_FORGE.get(), id);
        this.access = access;
        addSlot(new Slot(gear, 0, GEAR_X, GEAR_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ForgeRules.accepts(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    public ItemStack gear() {
        return gear.getItem(0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack stack = gear();
        if (stack.isEmpty()) {
            return false;
        }
        if (id == BUTTON_ENHANCE) {
            if (!ForgeRules.enhance(player, stack)) {
                return false;
            }
            gear.setChanged();
            effects(player, true, ModGear.enhance(stack));
            return true;
        }
        if (id == BUTTON_DISMANTLE) {
            int[] yield = ForgeRules.dismantleYield(stack);
            gear.setItem(0, ItemStack.EMPTY);
            if (yield[0] > 0) {
                player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.RESONANCE_SHARD.get(), yield[0]));
            }
            if (yield[1] > 0) {
                player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), yield[1]));
            }
            effects(player, false, 0);
            return true;
        }
        return false;
    }

    private void effects(Player player, boolean enhanced, int level) {
        access.execute((world, pos) -> {
            if (enhanced) {
                float pitch = 0.7f + level * 0.08f;
                world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, pitch);
                world.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.1f);
            } else {
                world.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1f, 0.8f);
                world.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1f, 0.9f);
            }
            if (world instanceof ServerLevel server) {
                server.sendParticles(enhanced ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, enhanced ? 16 : 24, 0.25, 0.3, 0.25, 0.03);
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 37, true)) {
                return ItemStack.EMPTY;
            }
        } else if (ForgeRules.accepts(stack) && !slots.get(0).hasItem()) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < 28) {
            if (!moveItemStackTo(stack, 28, 37, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 1, 28, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.RESONANCE_FORGE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((world, pos) -> clearContainer(player, gear));
    }
}
