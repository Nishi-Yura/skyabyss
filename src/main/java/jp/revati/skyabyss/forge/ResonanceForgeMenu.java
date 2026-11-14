package jp.revati.skyabyss.forge;

import java.util.List;

import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.loot.GearExchange;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.registry.ModMenus;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// 共鳴の炉の画面: 装備を置く枠が一つと、材料の枠と、持ち物。ボタンで強化・分解・粉・再鍛造・交換する (費用は持ち物から払う)
// 交換に要る「持っている専用の逸品」と「交換できるか」はサーバーの記録にしかないので、数の枠 (DataSlot) で送る。
// 町の鍛冶師に話すと、同じ画面を「粉だけ」で開く (openPowder。2026-11-05 の粉と炉の頁 1-4)。そのときは粉の操作だけ通し、鍛冶師のそばにいるあいだ開いている
@net.neoforged.fml.common.EventBusSubscriber(modid = jp.revati.skyabyss.SkyAbyss.MODID)
public class ResonanceForgeMenu extends AbstractContainerMenu {
    public static final int BUTTON_ENHANCE = 0;
    public static final int BUTTON_DISMANTLE = 1;
    public static final int BUTTON_TRIAL = 2;
    // 確定: 今のまま (3)、候補 0〜2 (4〜6)
    public static final int BUTTON_KEEP = 3;
    public static final int BUTTON_PICK = 4;
    // 装飾を重ねる (持ち物の同じ品一つ + 結晶)
    public static final int BUTTON_MERGE = 9;
    // 交換: 専用の逸品 0〜2 (10〜12)
    public static final int BUTTON_EXCHANGE = 10;
    // 交換のボタン: BUTTON_EXCHANGE + 巨神の番号 (TitanGearSets の並び) × EXCHANGE_STRIDE + 専用の逸品の番号
    public static final int EXCHANGE_STRIDE = 8;
    // 刻み (アスペクト・ソケット・ルーン): 100 以上。交換の番号とぶつからない
    public static final int BUTTON_EXTRACT_DESTROY = 100;
    public static final int BUTTON_EXTRACT_KEEP = 101;
    public static final int BUTTON_IMPRINT = 102;
    public static final int BUTTON_INSERT = 103;
    // 外す: 穴 0〜2 (104〜106)
    public static final int BUTTON_REMOVE = 104;
    // 粉を合わせる (材料の枠の同じ段の粉三つ → 一つ上の段一つ)。外す (BUTTON_REMOVE + 穴の番号 0〜2) と重ならないように
    public static final int BUTTON_COMBINE = 108;
    // 旅袋の細工の枠 i (0〜35) の品を材料の枠へ (200 + i。2026-11-10 の選び p_parts)
    public static final int BUTTON_PART = 200;
    // 町の鍛冶師のそばで開いた「粉だけ」の画面で、鍛冶師から離れてよい距離
    public static final double ANCHOR_RANGE = 8;
    public static final int GEAR_X = 44;
    public static final int GEAR_Y = 35;
    // 材料の枠 (欠片・ルーン・アスペクトの芯)
    public static final int MATERIAL_X = 14;
    public static final int MATERIAL_Y = 35;

    private final Container gear = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final Container material = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    // 粉だけの画面を開いた鍛冶師 (炉で開いたときは null)
    @org.jetbrains.annotations.Nullable
    private final net.minecraft.world.entity.Entity anchor;
    // 1 = 粉だけ (町の鍛冶師)。画面へは数の枠で送る
    private final net.minecraft.world.inventory.DataSlot mode = net.minecraft.world.inventory.DataSlot.standalone();
    // 巨神ごとに二つ (TitanGearSets の並び): [2t] 持っている専用の逸品の印、[2t+1] 交換の状態 (ForgeRules.ExchangeState の順番)
    private final ContainerData exchange;

    public ResonanceForgeMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public ResonanceForgeMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        this(id, inventory, access, null);
    }

    public ResonanceForgeMenu(int id, Inventory inventory, ContainerLevelAccess access, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity anchor) {
        this(id, inventory, access, anchor, anchor != null);
    }

    // anchor: 開いている間そばにいる相手 (鍛冶師。炉で開いたときは null)。powderOnly: 粉の操作だけ
    public ResonanceForgeMenu(int id, Inventory inventory, ContainerLevelAccess access, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity anchor,
            boolean powderOnly) {
        super(ModMenus.RESONANCE_FORGE.get(), id);
        this.access = access;
        this.anchor = anchor;
        mode.set(powderOnly ? 1 : 0);
        addDataSlot(mode);
        int sets = TitanGearSets.exchangeable().size();
        if (inventory.player instanceof ServerPlayer server) {
            exchange = new ContainerData() {
                @Override
                public int get(int index) {
                    TitanGearSet set = TitanGearSets.exchangeable().get(index / 2);
                    TitanMemoryData.Record r = TitanMemoryData.get(server.server).peek(server.getUUID(), set.titan);
                    return index % 2 == 0 ? GearExchange.ownedMask(set, r) : GearExchange.state(set, r).ordinal();
                }

                @Override
                public void set(int index, int value) {
                }

                @Override
                public int getCount() {
                    return sets * 2;
                }
            };
        } else {
            exchange = new SimpleContainerData(sets * 2);
        }
        addDataSlots(exchange);
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
        addSlot(new Slot(material, 0, MATERIAL_X, MATERIAL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptsMaterial(stack);
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

    // 町の鍛冶師の「粉だけ」の画面か
    public boolean powderOnly() {
        return mode.get() == 1;
    }

    // 町の鍛冶師に話して、粉だけの画面を開く (facility/FacilityActions の "powder")
    public static void openPowder(ServerPlayer player, net.minecraft.world.entity.Entity smith) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new ResonanceForgeMenu(id, inv, ContainerLevelAccess.create(player.level(), smith.blockPosition()), smith),
                net.minecraft.network.chat.Component.translatable("gui.skyabyss.forge.powder_title")));
    }

    // 開発・撮影: /skyabyss_forge open|powder : 炉 (か粉だけ) の画面を開き、手の品を装備の枠へ、左手の品を材料の枠へ移す
    @net.neoforged.bus.api.SubscribeEvent
    static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("skyabyss_forge").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("open").executes(c -> devOpen(c.getSource().getPlayerOrException(), false)))
                .then(net.minecraft.commands.Commands.literal("powder").executes(c -> devOpen(c.getSource().getPlayerOrException(), true))));
    }

    private static int devOpen(ServerPlayer player, boolean powder) {
        ItemStack hand = player.getMainHandItem().copy();
        ItemStack off = player.getOffhandItem().copy();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> {
            ResonanceForgeMenu m = new ResonanceForgeMenu(id, inv, ContainerLevelAccess.create(player.level(), player.blockPosition()), player, powder);
            m.gear.setItem(0, hand);
            m.material.setItem(0, off);
            return m;
        }, net.minecraft.network.chat.Component.translatable(powder ? "gui.skyabyss.forge.powder_title" : "block.skyabyss.resonance_forge")));
        return 1;
    }

    static boolean powderButton(int id) {
        return id == BUTTON_INSERT || id == BUTTON_COMBINE || id >= BUTTON_REMOVE && id < BUTTON_REMOVE + jp.revati.skyabyss.item.extra.GearExtras.MAX_SOCKETS;
    }

    public ItemStack material() {
        return material.getItem(0);
    }

    // 材料の枠に置けるもの: 属性の欠片・技の修飾ルーン・アスペクトの芯
    public static boolean acceptsMaterial(ItemStack stack) {
        return stack.getItem() instanceof jp.revati.skyabyss.item.extra.GearFreedomItems.ElementShard
                || stack.is(ModItems.SKILL_RUNE.get()) || stack.is(ModItems.ASPECT_CORE.get());
    }

    public boolean ownsExclusive(int titan, int index) {
        return (exchange.get(titan * 2) & (1 << index)) != 0;
    }

    public ForgeRules.ExchangeState exchangeState(int titan) {
        ForgeRules.ExchangeState[] all = ForgeRules.ExchangeState.values();
        int i = exchange.get(titan * 2 + 1);
        return i >= 0 && i < all.length ? all[i] : ForgeRules.ExchangeState.NO_CLEAR;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= BUTTON_PART && id < BUTTON_PART + jp.revati.skyabyss.pouch.PouchData.PARTS) {
            return partToMaterial(player, id - BUTTON_PART);
        }
        if (powderOnly() && !powderButton(id)) {
            return false;
        }
        // 粉を合わせるのは材料の枠だけを使う
        if (id == BUTTON_COMBINE) {
            ItemStack out = ForgeRules.combinePowder(material());
            if (out.isEmpty()) {
                return false;
            }
            material.setChanged();
            placeBack(player, out);
            inscribeEffects(true);
            fireForge(player, "powder_combine");
            return true;
        }
        // 交換は装備の枠を使わない
        if (id >= BUTTON_EXCHANGE && id < BUTTON_EXTRACT_DESTROY) {
            int t = (id - BUTTON_EXCHANGE) / EXCHANGE_STRIDE;
            int i = (id - BUTTON_EXCHANGE) % EXCHANGE_STRIDE;
            List<TitanGearSet> sets = TitanGearSets.exchangeable();
            if (t >= sets.size() || i >= sets.get(t).exclusives().size() || !(player instanceof ServerPlayer server)
                    || !GearExchange.exchange(server, sets.get(t), sets.get(t).exclusives().get(i))) {
                return false;
            }
            exchangeEffects();
            return true;
        }
        ItemStack stack = gear();
        if (stack.isEmpty()) {
            return false;
        }
        if (id >= BUTTON_EXTRACT_DESTROY) {
            return clickInscribe(player, stack, id);
        }
        if (id == BUTTON_TRIAL) {
            if (!ForgeRules.reforgeTrial(player, stack)) {
                return false;
            }
            gear.setChanged();
            reforgeEffects(false);
            fireForge(player, "reforge");
            return true;
        }
        if (id >= BUTTON_KEEP && id < BUTTON_PICK + AffixRules.MAX_TRIALS) {
            if (!AffixRules.confirm(stack, id - BUTTON_PICK)) {
                return false;
            }
            gear.setChanged();
            reforgeEffects(true);
            return true;
        }
        if (id == BUTTON_MERGE) {
            if (!ForgeRules.merge(player, stack)) {
                return false;
            }
            gear.setChanged();
            effects(player, true, jp.revati.skyabyss.item.wild.AccessoryRolls.stage(stack) * 2);
            fireForge(player, "merge");
            return true;
        }
        if (id == BUTTON_ENHANCE) {
            if (!ForgeRules.enhance(player, stack)) {
                return false;
            }
            gear.setChanged();
            effects(player, true, ModGear.enhance(stack));
            fireForge(player, "enhance");
            return true;
        }
        if (id == BUTTON_DISMANTLE) {
            int[] yield = ForgeRules.dismantleYield(stack);
            stack = stack.copy();
            gear.setItem(0, ItemStack.EMPTY);
            // 差していた欠片・ルーンと、刻んだアスペクトは壊さず品に戻す
            jp.revati.skyabyss.item.extra.GearExtras extras = jp.revati.skyabyss.item.extra.GearFreedom.extras(stack);
            for (jp.revati.skyabyss.item.extra.Gem g : extras.gems()) {
                placeBack(player, ForgeRules.gemItem(g));
            }
            jp.revati.skyabyss.rpg.skill.Rune aspect = extras.aspect().isEmpty() ? null : jp.revati.skyabyss.rpg.skill.Rune.byId(extras.aspect());
            if (aspect != null) {
                placeBack(player, jp.revati.skyabyss.item.extra.GearFreedom.core(aspect));
            }
            if (yield[0] > 0) {
                placeBack(player, new ItemStack(ForgeRules.resonanceItem(stack), yield[0]));
            }
            if (yield[1] > 0) {
                placeBack(player, new ItemStack(ForgeRules.shardItem(stack), yield[1]));
            }
            if (yield[2] > 0) {
                placeBack(player, new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), yield[2]));
            }
            effects(player, false, 0);
            fireForge(player, "dismantle");
            return true;
        }
        return false;
    }

    // 刻みの操作。装備の穴・アスペクトはサーバーが毎回検める (画面は頼むだけ)
    private boolean clickInscribe(Player player, ItemStack stack, int id) {
        jp.revati.skyabyss.item.extra.GearFreedom.ensure(stack, player.getRandom());
        ItemStack mat = material();
        switch (id) {
            case BUTTON_EXTRACT_DESTROY, BUTTON_EXTRACT_KEEP -> {
                boolean destroy = id == BUTTON_EXTRACT_DESTROY;
                ItemStack core = ForgeRules.extract(player, stack, destroy);
                if (core.isEmpty()) {
                    return false;
                }
                if (destroy) {
                    gear.setItem(0, ItemStack.EMPTY);
                } else {
                    gear.setChanged();
                }
                placeBack(player, core);
                inscribeEffects(true);
                fireForge(player, "extract");
                return true;
            }
            case BUTTON_IMPRINT -> {
                if (!ForgeRules.imprint(player, stack, mat)) {
                    return false;
                }
                gear.setChanged();
                material.setChanged();
                inscribeEffects(true);
                fireForge(player, "imprint");
                return true;
            }
            case BUTTON_INSERT -> {
                if (!ForgeRules.insertGem(stack, mat)) {
                    return false;
                }
                gear.setChanged();
                material.setChanged();
                inscribeEffects(false);
                fireForge(player, "socket");
                return true;
            }
            default -> {
                int index = id - BUTTON_REMOVE;
                if (index < 0 || index >= jp.revati.skyabyss.item.extra.GearExtras.MAX_SOCKETS) {
                    return false;
                }
                ItemStack back = ForgeRules.removeGem(player, stack, index);
                if (back.isEmpty()) {
                    return false;
                }
                gear.setChanged();
                placeBack(player, back);
                inscribeEffects(false);
                return true;
            }
        }
    }

    private void inscribeEffects(boolean big) {
        access.execute((world, pos) -> {
            world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, big ? 0.8f : 1.5f);
            world.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.2f);
            if (world instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, big ? 24 : 12, 0.25, 0.3, 0.25, 0.5);
            }
        });
    }

    // 分解で戻る素材は、留め袋に入る品なら袋へ (22 の 8.3)
    // 細工の枠の品を材料の枠へ。材料の枠にあった品は旅袋 (入らなければ持ち物) へ戻す。
    // 材料の枠の品は「取り出し済み」の印を付けない (画面を閉じて持ち物へ戻ったら、掃除が旅袋へ戻す)
    private boolean partToMaterial(Player player, int index) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        ItemStack part = jp.revati.skyabyss.pouch.Pouch.data(sp).part(index);
        if (part.isEmpty()) {
            return false;
        }
        ItemStack old = material.getItem(0);
        if (!old.isEmpty()) {
            jp.revati.skyabyss.pouch.Pouch.addPart(sp, old);
            if (!old.isEmpty()) {
                sp.getInventory().placeItemBackInInventory(old);
            }
            material.setItem(0, ItemStack.EMPTY);
        }
        ItemStack out = jp.revati.skyabyss.pouch.Pouch.takePart(sp, index, part.getCount());
        out.remove(jp.revati.skyabyss.pouch.PouchSetup.LOAN.get());
        material.setItem(0, out);
        material.setChanged();
        sp.playNotifySound(net.minecraft.sounds.SoundEvents.BUNDLE_REMOVE_ONE, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.0f);
        return true;
    }

    private static void placeBack(Player player, ItemStack stack) {
        if (player instanceof ServerPlayer server) {
            jp.revati.skyabyss.pouch.PouchRouting.placeBack(server, stack);
        } else {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    // 炉で何かしたことを進捗へ (一行)
    private static void fireForge(Player player, String what) {
        if (player instanceof ServerPlayer server) {
            jp.revati.skyabyss.advancement.Beats.fire(server, "forge:" + what);
        }
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

    private void reforgeEffects(boolean confirmed) {
        access.execute((world, pos) -> {
            if (confirmed) {
                world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 0.9f);
                world.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6f, 1.3f);
            } else {
                world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.4f);
            }
            if (world instanceof ServerLevel server) {
                server.sendParticles(confirmed ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, confirmed ? 12 : 20, 0.25, 0.3, 0.25, 0.4);
            }
        });
    }

    private void exchangeEffects() {
        access.execute((world, pos) -> {
            world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4f, 0.6f);
            world.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.2f);
            if (world instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.05);
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
        // 枠: 0 装備、1 材料、2〜28 持ち物、29〜37 手の列
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, 38, true)) {
                return ItemStack.EMPTY;
            }
        } else if (acceptsMaterial(stack) && !slots.get(1).hasItem()) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else if (ForgeRules.accepts(stack) && !slots.get(0).hasItem()) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < 29) {
            if (!moveItemStackTo(stack, 29, 38, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 2, 29, false)) {
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
        if (anchor != null) {
            return anchor.isAlive() && !anchor.isRemoved() && player.distanceToSqr(anchor) <= ANCHOR_RANGE * ANCHOR_RANGE;
        }
        return stillValid(access, player, ModBlocks.RESONANCE_FORGE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((world, pos) -> {
            clearContainer(player, gear);
            clearContainer(player, material);
        });
    }
}
