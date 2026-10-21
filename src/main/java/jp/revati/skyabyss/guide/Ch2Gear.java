package jp.revati.skyabyss.guide;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.ch1.Tasks;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 二章「引く海」の備えの品 (docs/design/21 の 1.2・4.1、P5 の二章の分)。一章の TravelGear と同じ型。
//  - 素材: 小真珠・塩結晶・真珠の護り糸 (鼓動守 潮殻の材料)・貝の欠け。落とす敵は二章の地上の敵 (別の担当が ID の文字列で参照する)
//  - 潮渡りの旅装 (4 部位): 防御 3/7/5/3 = 18、強靭 2.25 × 4 = 9、水の中の動きの鈍りを軽くする (水中移動効率 +0.1 × 4 = 0.4)、
//    アンコモン、炉で鍛えない、必要 Lv 13 (GearCurve.TRAVEL[1])。修理は塩結晶。一式で合図 SIGNAL_GEAR_MADE
//  - 塩の砥石: 「塩の刃」(攻撃 +1.8、20 分)。水の中 (自分か相手が水に浸かっている) で当てると塩が散って +1 (SALT_WET_BONUS)。使うと合図 SIGNAL_WHETSTONE_USED
//  - 潮の塩漬け (水中呼吸 60 秒 + 満腹)・真珠の粉薬 (即時に 3❤ 回復、浮遊と鈍足を治す)
//  - 潮の刻みの砂時計 (消耗しない。次の干潮・満潮までの秒数を画面の帯に一度。クールダウン 30 秒)
// レシピは旅の覚書 (二章) か職人 (unlock_recipes chapter 2) に教わるまで作れない (RecipeLock)。生成は tools/gen_ch2_gear.py
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch2Gear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SkyAbyss.MODID);

    public static final int CHAPTER = 2;
    public static final String TITAN = "tide_shell";
    public static final GearCurve.Travel ROW = GearCurve.TRAVEL.get(CHAPTER - 1);

    // ---------------------------------------------------------------- 合図 (クエストの目的 signal が読む)
    // 潮渡りの旅装を一式そろえた (作ったとき、と、着ている分も数える)
    public static final String SIGNAL_GEAR_MADE = "ch2_travel_gear_made";
    // 塩の砥石を使った
    public static final String SIGNAL_WHETSTONE_USED = "ch2_whetstone_used";
    // 塩の刃 (塩の砥石の効き目) が効いている間に、名づき・地上の敵などを倒した (倒した敵の ID は問わない。番人を倒す目的は kill と組み合わせる)
    public static final String SIGNAL_SALT_KILL = "ch2_salt_blade_kill";
    // 塩の刃が効いている間に潮の番人 (shore_warden) を倒した (備えの頼み「刃を研ぐ」の締め)
    public static final String SIGNAL_WARDEN_SALTED = "ch2_warden_salted";
    public static final String SHORE_WARDEN = "skyabyss:shore_warden";
    // 砂時計で潮の刻みを読んだ
    public static final String SIGNAL_TIDE_READ = "ch2_tide_read";

    // 旅の覚書 (二章) が解くレシピ (data/skyabyss/recipe)
    public static final List<String> RECIPES = List.of("tide_wader_helmet", "tide_wader_chestplate", "tide_wader_leggings", "tide_wader_boots",
            "salt_whetstone", "tide_pickle", "pearl_powder", "tide_hourglass");

    // ---------------------------------------------------------------- 数値
    public static final int[] ARMOR_DEFENSE = {3, 7, 5, 3};
    public static final float ARMOR_TOUGHNESS = 2.25f;
    // 水中移動効率 (バニラの属性。水渡りのエンチャント III で 1.0)。1 部位 0.1、一式 0.4
    public static final double WATER_EFFICIENCY = 0.1;
    public static final double WHETSTONE_ATTACK = ROW.whetstone();
    public static final int WHETSTONE_TICKS = 20 * 60 * 20;
    // 水の中で塩の刃を当てたときの上乗せ
    public static final float SALT_WET_BONUS = 1.0f;
    public static final int PICKLE_TICKS = 20 * 60;
    // 潮の塩漬けの備えの糧の点 (水中呼吸は回復ではないが、潮の背で溺れない分を小さく数える。1❤)
    public static final double PICKLE_POINTS = 2;
    // 真珠の粉薬: 即時に 3❤ (6 点)
    public static final float POWDER_HEAL = 6;
    public static final int HOURGLASS_COOLDOWN = 20 * 30;

    public static final DeferredHolder<MobEffect, MobEffect> SALT_WHETTED = EFFECTS.register("salt_whetted", SaltWhettedEffect::new);

    public static final Holder<ArmorMaterial> MATERIAL = MATERIALS.register("tide_wader", () -> new ArmorMaterial(
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, ARMOR_DEFENSE[0],
                    ArmorItem.Type.CHESTPLATE, ARMOR_DEFENSE[1],
                    ArmorItem.Type.LEGGINGS, ARMOR_DEFENSE[2],
                    ArmorItem.Type.BOOTS, ARMOR_DEFENSE[3],
                    ArmorItem.Type.BODY, 7)),
            9,
            SoundEvents.ARMOR_EQUIP_TURTLE,
            () -> Ingredient.of(Ch2Gear.SALT_CRYSTAL.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_wader"))),
            ARMOR_TOUGHNESS,
            0f));

    // ---------------------------------------------------------------- 素材
    public static final DeferredItem<Item> SMALL_PEARL = material("small_pearl");
    public static final DeferredItem<Item> SALT_CRYSTAL = material("salt_crystal");
    // 真珠の護り糸 (鼓動守 潮殻の材料。潮の番人の落とし物)
    public static final DeferredItem<Item> PEARL_WARD_THREAD = material("pearl_ward_thread");
    public static final DeferredItem<Item> SHELL_CHIP = material("shell_chip");

    // ---------------------------------------------------------------- 潮渡りの旅装 (4 部位)
    public static final DeferredItem<Item> HELMET = armor("tide_wader_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<Item> CHESTPLATE = armor("tide_wader_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<Item> LEGGINGS = armor("tide_wader_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<Item> BOOTS = armor("tide_wader_boots", ArmorItem.Type.BOOTS);

    // ---------------------------------------------------------------- 砥石・糧・薬・道具
    public static final DeferredItem<Item> SALT_WHETSTONE = ITEMS.register("salt_whetstone",
            () -> new SaltWhetstoneItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> TIDE_PICKLE = ITEMS.register("tide_pickle",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.9f)
                    .effect(() -> new MobEffectInstance(MobEffects.WATER_BREATHING, PICKLE_TICKS, 0), 1.0f).build())));
    public static final DeferredItem<Item> PEARL_POWDER = ITEMS.register("pearl_powder",
            () -> new PearlPowderItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(0).saturationModifier(0f)
                    .alwaysEdible().fast().build())));
    public static final DeferredItem<Item> TIDE_HOURGLASS = ITEMS.register("tide_hourglass",
            () -> new TideHourglassItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    static {
        // 備えの三灯へ: 塩の砥石は効いているか持ち歩いているとき +1.8 (一章の岩の砥石と重ねず、大きい方を数える = TravelGear.whetstoneBonus)
        TravelGear.addWhetstoneKind(player -> player.hasEffect(SALT_WHETTED) || TravelGear.carries(player, SALT_WHETSTONE.get()) ? WHETSTONE_ATTACK : 0);
        // 糧: 粉薬は即時の 3❤ (食べ物の効果ではないので点を足す)、塩漬けは小さく 1❤
        Readiness.registerProvision(stack -> stack.is(PEARL_POWDER.get()) ? POWDER_HEAL : stack.is(TIDE_PICKLE.get()) ? PICKLE_POINTS : 0);
        // 手段の名前 (潮殻の備えのときだけ)
        Readiness.registerRemedy(Readiness.Lamp.BODY, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.tide_wader"));
        Readiness.registerRemedy(Readiness.Lamp.BLADE, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.salt_whetstone"));
        Readiness.registerRemedy(Readiness.Lamp.PROVISIONS, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.tide_pickle"));
        Readiness.registerRemedy(Readiness.Lamp.PROVISIONS, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.pearl_powder"));
        // 旅の覚書 (二章) のレシピ
        WayfarerNotes.registerRecipes(CHAPTER, RECIPES.stream().map(n -> ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, n)).toArray(ResourceLocation[]::new));
    }

    private Ch2Gear() {
    }

    // 素材 (説明の一行 item.skyabyss.<id>.desc をツールチップに出すだけ)
    private static DeferredItem<Item> material(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                tooltip.add(Component.translatable("item.skyabyss." + id + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
            }
        });
    }

    private static DeferredItem<Item> armor(String id, ArmorItem.Type type) {
        return ITEMS.register(id, () -> new TideWaderArmorItem(MATERIAL, type, new Item.Properties().rarity(Rarity.UNCOMMON)));
    }

    static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        EFFECTS.register(modEventBus);
        modEventBus.addListener(Ch2Gear::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (DeferredItem<Item> item : List.of(SMALL_PEARL, SALT_CRYSTAL, PEARL_WARD_THREAD, SHELL_CHIP, HELMET, CHESTPLATE, LEGGINGS, BOOTS,
                    SALT_WHETSTONE, TIDE_PICKLE, PEARL_POWDER, TIDE_HOURGLASS)) {
                event.accept(item.get());
            }
        }
    }

    // ---------------------------------------------------------------- 旅装
    public static boolean isTideWader(ItemStack stack) {
        return stack.is(HELMET.get()) || stack.is(CHESTPLATE.get()) || stack.is(LEGGINGS.get()) || stack.is(BOOTS.get());
    }

    // 4 部位がそろっているか (着ているか持ち物にあるか)
    public static boolean hasFullSet(Player player) {
        for (DeferredItem<Item> piece : List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS)) {
            if (player.getInventory().countItem(piece.get()) > 0) {
                continue;
            }
            boolean worn = false;
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                worn |= player.getItemBySlot(slot).is(piece.get());
            }
            if (!worn) {
                return false;
            }
        }
        return true;
    }

    public static boolean signalIfComplete(ServerPlayer player) {
        if (!hasFullSet(player)) {
            return false;
        }
        Quests.signal(player, SIGNAL_GEAR_MADE);
        return true;
    }

    @SubscribeEvent
    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isTideWader(event.getCrafting())) {
            Tasks.later(2, () -> signalIfComplete(player));
        }
    }

    // ---------------------------------------------------------------- 塩の砥石
    static void addAttackModifier(MobEffect effect) {
        effect.addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "effect.salt_whetted"), WHETSTONE_ATTACK,
                AttributeModifier.Operation.ADD_VALUE);
    }

    // 塩の砥石を使った。岩の砥石の研ぎは塩の研ぎに替わる (重ねない)
    public static boolean applyWhetstone(ServerPlayer player) {
        player.removeEffect(TravelGear.WHETTED);
        player.addEffect(new MobEffectInstance(SALT_WHETTED, WHETSTONE_TICKS, 0, false, true, true));
        Notice.hint(player, "message.skyabyss.salt_whetstone.used");
        Quests.signal(player, SIGNAL_WHETSTONE_USED);
        return true;
    }

    // 水の中の一撃か (打つ人か相手のどちらかが水に浸かっている)
    public static boolean wetHit(LivingEntity attacker, LivingEntity target) {
        return attacker.isInWaterOrBubble() || target.isInWaterOrBubble();
    }

    // 塩の刃の上乗せ (効いていて、水の中の近接の一撃なら SALT_WET_BONUS)。純粋な部分 (自己テストが呼ぶ)
    public static float wetBonus(boolean salted, boolean melee, boolean wet) {
        return salted && melee && wet ? SALT_WET_BONUS : 0f;
    }

    // 水の中でも切れる: 塩の刃で水の中の相手を打つと、白い塩が散って +1
    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player) {
            return;
        }
        LivingEntity target = event.getEntity();
        float bonus = wetBonus(player.hasEffect(SALT_WHETTED), true, wetHit(player, target));
        if (bonus <= 0) {
            return;
        }
        event.setAmount(event.getAmount() + bonus);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WHITE_ASH, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.BUBBLE_POP, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.25, 0.25, 0.25, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
    }

    // 塩の刃が効いている間に倒した
    @SubscribeEvent
    static void onKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && player.hasEffect(SALT_WHETTED) && !(event.getEntity() instanceof Player)) {
            Quests.signal(player, SIGNAL_SALT_KILL);
            if (SHORE_WARDEN.equals(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType())))) {
                Quests.signal(player, SIGNAL_WARDEN_SALTED);
            }
        }
    }

    // ---------------------------------------------------------------- 粉薬
    // 食べ終えたとき: 3❤ 回復、浮遊と鈍足を治す
    static void applyPowder(LivingEntity entity) {
        entity.heal(POWDER_HEAL);
        entity.removeEffect(MobEffects.LEVITATION);
        entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }
}
