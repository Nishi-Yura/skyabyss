package jp.revati.skyabyss.item;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 装備の必要レベル (成長の Lv。03 の 10章、2026-10-17)。
//  - 必要 Lv = その巨神の推奨 Lv (GearCurve.STAGES) から、エピック以下は −4、ヒロイック以上は −2 (GearTier.levelMargin)
//  - 足りないとき: 防具は身につけられない (枠に入らず、着ていたら持ち物へ戻す)。装飾は枠に付けられない。
//    武器は手に持てるが、品の修飾子 (攻撃力・速さ・間合い・強化・特性) を打ち消し、固有効果と特性が働かない (素手と同じ)
//  - 拾う・渡す・預ける・強化・再鍛造・交換は、レベルに関わらずできる
//  - 創造モードでは問わない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GearLevel {
    public static final int RARE_MARGIN = GearTier.RARE.levelMargin;
    public static final int LEGENDARY_MARGIN = GearTier.ARTIFACT.levelMargin;

    private static final Map<Player, List<Lock>> LOCKS = new WeakHashMap<>();

    private record Lock(Holder<Attribute> attribute, ResourceLocation id) {
    }

    private GearLevel() {
    }

    // 巨神の推奨 Lv (知らない由来は 0 = 要件なし)
    public static int stageLevel(String dungeon) {
        // 五章 (深淵) は巨神の段の外 (abyss/gear/AbyssGear.STAGE)
        if (jp.revati.skyabyss.abyss.gear.AbyssGear.DUNGEON.equals(dungeon)) {
            return jp.revati.skyabyss.abyss.gear.AbyssGear.STAGE.level();
        }
        // 六章 (月) も巨神の段の外 (story/ch6/gear/MoonGear.STAGE)
        if (jp.revati.skyabyss.story.ch6.gear.MoonGear.DUNGEON.equals(dungeon)) {
            return jp.revati.skyabyss.story.ch6.gear.MoonGear.STAGE.level();
        }
        for (GearCurve.Stage s : GearCurve.STAGES) {
            if (s.id().equals(dungeon)) {
                return s.level();
            }
        }
        return 0;
    }

    public static int defaultRequired(ModGear gear) {
        int base = stageLevel(gear.dungeon());
        if (base <= 0) {
            return 0;
        }
        int margin = gear.tier().levelMargin;
        return Math.max(1, base - margin);
    }

    public static int required(Item item) {
        return item instanceof ModGear gear ? gear.requiredLevel() : 0;
    }

    public static int required(ItemStack stack) {
        return required(stack.getItem());
    }

    // 装備の要求レベルに使うレベル。転生した人は、レベルを 1 から上げ直している間も 50 として扱う
    // (持ち越した装備が使えなくならない。docs/design/31)
    public static int level(Player player) {
        return jp.revati.skyabyss.rebirth.Rebirth.gearLevel(player);
    }

    // この人がこの品の力を使えるか (要件のない品と、プレイヤーでない者はいつでも使える)
    public static boolean meets(Player player, ItemStack stack) {
        int req = required(stack);
        return req <= 0 || player.isCreative() || level(player) >= req;
    }

    public static boolean usable(LivingEntity entity, ItemStack stack) {
        return !(entity instanceof Player player) || meets(player, stack);
    }

    // ---------------------------------------------------------------- 武器: 品の修飾子を打ち消す
    // 手の品の修飾子 (イベントで足された強化・特性を含む) を、同じ量だけ逆向きに足す。
    // 足し算と基礎への掛け算は符号を変え、全体への掛け算は 1/(1+a) − 1 を掛けて、ちょうど品を持たない値に戻す
    public static void updateLock(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        List<Lock> previous = LOCKS.getOrDefault(player, List.of());
        if (previous.isEmpty() && !(hand.getItem() instanceof ModGear) && !(off.getItem() instanceof ModGear)) {
            return;
        }
        // 前に何も打ち消しておらず、両手の品がどちらも使える (レベルが足りる) なら、作る物は何も無い
        if (previous.isEmpty() && (!(hand.getItem() instanceof ModGear) || meets(player, hand)) && (!(off.getItem() instanceof ModGear) || meets(player, off))) {
            return;
        }
        List<Lock> next = new ArrayList<>();
        // 利き手の武器と、左手の盾 (珊瑚の盾など。特性は左手の修飾子になる)
        lock(player, hand, EquipmentSlot.MAINHAND, "gear_lock/", next);
        lock(player, off, EquipmentSlot.OFFHAND, "gear_lock/off/", next);
        for (Lock old : previous) {
            if (!next.contains(old)) {
                AttributeInstance instance = player.getAttribute(old.attribute());
                if (instance != null) {
                    instance.removeModifier(old.id());
                }
            }
        }
        if (next.isEmpty()) {
            LOCKS.remove(player);
        } else {
            LOCKS.put(player, next);
        }
    }

    // 打ち消しの修飾子の名前 (毎 tick 作らないよう、作ったものを覚えておく)
    private static final java.util.Map<String, ResourceLocation[]> LOCK_IDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static ResourceLocation lockId(String prefix, int i) {
        ResourceLocation[] ids = LOCK_IDS.computeIfAbsent(prefix, k -> new ResourceLocation[16]);
        if (i >= ids.length) {
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, prefix + i);
        }
        ResourceLocation id = ids[i];
        if (id == null) {
            id = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, prefix + i);
            ids[i] = id;
        }
        return id;
    }

    private static void lock(ServerPlayer player, ItemStack stack, EquipmentSlot slot, String prefix, List<Lock> next) {
        if (!(stack.getItem() instanceof ModGear) || meets(player, stack)) {
            return;
        }
        int i = 0;
        for (ItemAttributeModifiers.Entry e : stack.getAttributeModifiers().modifiers()) {
            if (!e.slot().test(slot)) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(e.attribute());
            if (instance == null) {
                continue;
            }
            AttributeModifier m = e.modifier();
            double amount = switch (m.operation()) {
                case ADD_VALUE, ADD_MULTIPLIED_BASE -> -m.amount();
                case ADD_MULTIPLIED_TOTAL -> m.amount() > -1 ? 1 / (1 + m.amount()) - 1 : 0;
            };
            ResourceLocation id = lockId(prefix, i++);
            AttributeModifier current = instance.getModifier(id);
            if (current == null || current.amount() != amount || current.operation() != m.operation()) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, m.operation()));
            }
            next.add(new Lock(e.attribute(), id));
        }
    }

    // ---------------------------------------------------------------- 防具: 着ていたら外す (コマンドや発射装置で着せられたとき)
    public static void enforceArmor(ServerPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.getItem() instanceof ModGear && !meets(player, worn)) {
                player.setItemSlot(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(worn) && !worn.isEmpty()) {
                    player.drop(worn, false);
                }
                denyMessage(player, worn);
            }
        }
    }

    public static void denyMessage(Player player, ItemStack stack) {
        Notice.bar(player, ModText.deny("skyabyss_gear.message.level_short", stack.getHoverName(), required(stack), level(player)));
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        updateLock(player);
        if (player.tickCount % 10 == 3) {
            enforceArmor(player);
        }
    }
}
