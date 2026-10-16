package jp.revati.skyabyss.item.accessory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 必要レベルと装飾の枠の自己テスト (SKYABYSS_SELFTEST=1)。結果はログに SELFTEST accessory PASSED / FAILED。
//  - 必要 Lv の表 (03 の 10.2節)、防具が着られない、武器の修飾子がちょうど打ち消される (強化込み)
//  - 枠の決まり (種類・重複・レベル)、効くのは枠の品だけ、保存 (NBT) と同期 (StreamCodec) の往復
//  - 前のセーブの移し替え、死んだときに落ちる品、護符をキーで使う (待ち)
public final class AccessorySelfTest {
    private AccessorySelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("accessory selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST accessory PASSED (required levels, armor/weapon lock, slot rules, save, sync, migration, death drops, charm key)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST accessory FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void setLevel(FakePlayer p, int level) {
        Growth.get(p).level = level;
    }

    static void run(MinecraftServer server, List<String> f) {
        // ---- 必要 Lv の表
        Map<Item, Integer> want = Map.of(
                ModItems.WHALEBONE_GREATSWORD.get(), 8, ModItems.STONE_WHALE_HELMET.get(), 8, ModItems.STONE_WHALE_BOOTS.get(), 8,
                ModItems.RESONANCE_CHARM.get(), 8, ModItems.MOSS_TALISMAN.get(), 8,
                ModItems.CAPTAIN_PIKE.get(), 10, ModItems.WATERWAY_FANG.get(), 10, ModItems.STONE_WHALE_PLATING.get(), 10);
        want.forEach((item, lv) -> check(f, GearLevel.required(item) == lv, item + " requires Lv " + GearLevel.required(item) + " (want " + lv + ")"));
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof ModGear && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(SkyAbyss.MODID)) {
                check(f, GearLevel.required(item) > 0, item + " is gear without a level requirement");
            }
        }
        check(f, GearLevel.required(ModItems.CLOUD_JERKY.get()) == 0 && GearLevel.required(ModItems.STONE_WHALE_SHARD.get()) == 0,
                "food and materials should have no level requirement");

        ServerLevel level = server.overworld();
        FakePlayer p = FakePlayerFactory.getMinecraft(level);
        AccessoryData data = Accessories.get(p);
        int oldLevel = Growth.get(p).level;
        try {
            // ---- 防具
            ItemStack helmet = new ItemStack(ModItems.STONE_WHALE_HELMET.get());
            setLevel(p, 7);
            check(f, !helmet.canEquip(EquipmentSlot.HEAD, p), "Lv 7 can wear a Lv 8 helmet");
            setLevel(p, 8);
            check(f, helmet.canEquip(EquipmentSlot.HEAD, p), "Lv 8 cannot wear a Lv 8 helmet");
            setLevel(p, 1);
            p.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
            GearLevel.enforceArmor(p);
            check(f, p.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && p.getInventory().contains(helmet), "worn armor under the level was not moved to the inventory");
            p.getInventory().clearContent();

            // ---- 武器: 品の修飾子 (強化 +10 込み) が打ち消される
            ItemStack pike = new ItemStack(ModItems.CAPTAIN_PIKE.get());
            pike.set(ModDataComponents.ENHANCE.get(), 10);
            p.setItemSlot(EquipmentSlot.MAINHAND, pike);
            for (ItemAttributeModifiers.Entry e : pike.getAttributeModifiers().modifiers()) {
                if (e.slot().test(EquipmentSlot.MAINHAND)) {
                    p.getAttribute(e.attribute()).addOrUpdateTransientModifier(e.modifier());
                }
            }
            double armed = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double reach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            setLevel(p, 9);
            GearLevel.updateLock(p);
            check(f, Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1) < 1e-6, "locked pike attack " + p.getAttributeValue(Attributes.ATTACK_DAMAGE) + " (want 1, bare hand)");
            check(f, Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 3) < 1e-6, "locked pike reach " + p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
            check(f, Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - 4) < 1e-6, "locked pike speed " + p.getAttributeValue(Attributes.ATTACK_SPEED));
            setLevel(p, 10);
            GearLevel.updateLock(p);
            check(f, Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - armed) < 1e-6 && armed > 10,
                    "unlocked pike attack " + p.getAttributeValue(Attributes.ATTACK_DAMAGE) + " (want " + armed + ")");
            check(f, Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - reach) < 1e-6, "unlocked pike reach");
            SkyAbyss.LOGGER.info("[selftest] accessory: pike +10 attack {} at Lv 10, 1.0 at Lv 9", armed);
            for (ItemAttributeModifiers.Entry e : pike.getAttributeModifiers().modifiers()) {
                AttributeInstance inst = p.getAttribute(e.attribute());
                if (inst != null) {
                    inst.removeModifier(e.modifier().id());
                }
            }
            p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

            // ---- 枠の決まり
            for (int i = 0; i < AccessoryData.SIZE; i++) {
                data.items.set(i, ItemStack.EMPTY);
            }
            ItemStack charm = new ItemStack(ModItems.RESONANCE_CHARM.get());
            ItemStack plating = new ItemStack(ModItems.STONE_WHALE_PLATING.get());
            ItemStack moss = new ItemStack(ModItems.MOSS_TALISMAN.get());
            setLevel(p, 8);
            check(f, Accessories.canPlace(p, data, 0, charm), "charm should fit the charm slot at Lv 8");
            check(f, !Accessories.canPlace(p, data, 1, charm), "charm should not fit a trinket slot");
            check(f, !Accessories.canPlace(p, data, 0, moss), "moss talisman should not fit the charm slot");
            check(f, !Accessories.canPlace(p, data, 1, plating), "plating (Lv 10) fits at Lv 8");
            check(f, !Accessories.canPlace(p, data, 1, new ItemStack(ModItems.STONE_WHALE_SHARD.get())), "a material fits a trinket slot");
            setLevel(p, 10);
            data.items.set(1, plating.copy());
            check(f, !Accessories.canPlace(p, data, 2, plating), "a second plating fits the other trinket slot");
            check(f, Accessories.canPlace(p, data, 2, moss), "moss talisman should fit next to the plating");
            data.items.set(2, moss.copy());
            data.items.set(0, charm.copy());
            check(f, Accessories.worn(p, ModItems.STONE_WHALE_PLATING.get()), "slotted plating is not worn at Lv 10");
            setLevel(p, 9);
            check(f, !Accessories.worn(p, ModItems.STONE_WHALE_PLATING.get()) && Accessories.worn(p, ModItems.MOSS_TALISMAN.get()),
                    "plating should stop (and moss keep) working at Lv 9");
            setLevel(p, 10);
            check(f, !p.getInventory().contains(plating) && Accessories.worn(p, ModItems.STONE_WHALE_PLATING.get()), "worn should read the slots");
            p.getInventory().add(new ItemStack(ModItems.STONE_WHALE_PLATING.get()));
            data.items.set(1, ItemStack.EMPTY);
            check(f, !Accessories.worn(p, ModItems.STONE_WHALE_PLATING.get()), "plating in the inventory (not slotted) still works");
            p.getInventory().clearContent();
            data.items.set(1, plating.copy());

            // ---- 保存と同期
            CompoundTag tag = data.serializeNBT(server.registryAccess());
            AccessoryData loaded = new AccessoryData();
            loaded.deserializeNBT(server.registryAccess(), tag);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            AccessoryData.STREAM_CODEC.encode(buf, data);
            AccessoryData synced = AccessoryData.STREAM_CODEC.decode(buf);
            for (int i = 0; i < AccessoryData.SIZE; i++) {
                check(f, ItemStack.matches(loaded.items.get(i), data.items.get(i)), "slot " + i + " changed through NBT");
                check(f, ItemStack.matches(synced.items.get(i), data.items.get(i)), "slot " + i + " changed through sync");
            }
            check(f, loaded.migrated == data.migrated, "migrated flag changed through NBT");

            // ---- 死んだとき (keepInventory でなく、岩鯨の背でもない)
            boolean keep = Accessories.keepsOnDeath(p);
            List<ItemEntity> drops = Accessories.takeDeathDrops(p);
            check(f, drops.size() == 3 && data.isEmpty(), "death drops " + drops.size() + " (want 3) and slots should empty");
            SkyAbyss.LOGGER.info("[selftest] accessory: keepInventory/back keeps={} (overworld), {} drops", keep, drops.size());

            // ---- 前のセーブ: 持ち物の装飾品を一度だけ枠へ (必要 Lv に届かない補強板は残る)
            data.migrated = false;
            setLevel(p, 8);
            p.getInventory().add(charm.copy());
            p.getInventory().add(moss.copy());
            p.getInventory().add(plating.copy());
            int moved = Accessories.migrate(p);
            check(f, moved == 2 && data.items.get(0).is(ModItems.RESONANCE_CHARM.get()) && data.items.get(1).is(ModItems.MOSS_TALISMAN.get())
                    && p.getInventory().contains(plating) && !p.getInventory().contains(charm), "migration moved " + moved + " (want 2: charm, moss)");
            check(f, Accessories.migrate(p) == 0 && data.migrated, "migration should run once");
            p.getInventory().clearContent();

            // ---- 手から枠へ、護符をキーで使う
            data.items.set(0, ItemStack.EMPTY);
            ItemStack held = charm.copy();
            check(f, Accessories.equipFromHand(p, held) && held.isEmpty() && data.items.get(0).is(ModItems.RESONANCE_CHARM.get()), "right-click should slot the charm");
            p.removeAllEffects();
            p.getCooldowns().removeCooldown(ModItems.RESONANCE_CHARM.get());
            Accessories.useCharm(p);
            check(f, p.hasEffect(MobEffects.REGENERATION) && p.getCooldowns().isOnCooldown(ModItems.RESONANCE_CHARM.get()), "charm key did not activate the charm");
            p.removeAllEffects();
            Accessories.useCharm(p);
            check(f, !p.hasEffect(MobEffects.REGENERATION), "charm activated again while resting");
            setLevel(p, 1);
            p.getCooldowns().removeCooldown(ModItems.RESONANCE_CHARM.get());
            Accessories.useCharm(p);
            check(f, !p.hasEffect(MobEffects.REGENERATION), "charm activated under its level");
            check(f, !((ArmorItem) ModItems.STONE_WHALE_HELMET.get()).canEquip(helmet, EquipmentSlot.HEAD, p), "Lv 1 armor check");
        } finally {
            for (int i = 0; i < AccessoryData.SIZE; i++) {
                data.items.set(i, ItemStack.EMPTY);
            }
            p.getInventory().clearContent();
            p.removeAllEffects();
            Growth.get(p).level = oldLevel;
        }
    }
}
