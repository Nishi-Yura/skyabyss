package jp.revati.skyabyss.guide;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.titan.storm.chapter.StormVane;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Vector3f;

// 三章「風に逆らう嵐」の備えの品 (docs/design/21 の 1.2・5.1)。一章の TravelGear・二章の Ch2Gear と同じ型。
//  - 素材: 焦げ羽・帯電綿・銅線・雷の護り糸 (鼓動守 雷翼の材料)。落とすのは三章の地上の敵 (titan/storm/chapter/Ch3Mobs が ID で引く)
//  - 峰越えの旅装 (4 部位): 防御 3/7/6/3 = 19、強靭 2.75 × 4 = 11、感電の傷 −6.25% × 着ている部位 (一式 −25%)、
//    アンコモン、炉で鍛えない、必要 Lv 19 (GearCurve.TRAVEL[2])。修理は銅線。一式で合図 SIGNAL_GEAR_MADE
//  - 雷の砥石: 「雷の刃」(攻撃 +2.2、20 分)。近接で当てると小さな火花が散る (見た目と音だけ)。岩・塩の砥石とは重ねない (替わる)
//  - 風干しの団子 (吸収 II 60 秒 + 落下速度低下 20 秒)・避雷の油 (飲むと 90 秒「避雷」= 感電と雷の傷 −50%)
//  - 風読みの小旗 (消耗しない): 使うと、そこに 30 秒、風下へ細い光の筋を立て、次の嵐までの目安を画面の帯に出す。クールダウン 30 秒
// 感電の傷 (isShock): 雷 (lightning_bolt の傷) と、雷を使う敵 (SHOCK_MOBS) の傷。レシピは旅の覚書 (三章) か職人 (unlock_recipes chapter 3) に教わるまで作れない。
// 生成は tools/gen/ch3/gen_ch3_gear.py (レシピと文)、絵は tools/install/install_ch3_gear_assets.py
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch3Gear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SkyAbyss.MODID);

    public static final int CHAPTER = 3;
    public static final String TITAN = "thunder_wing";
    public static final GearCurve.Travel ROW = GearCurve.TRAVEL.get(CHAPTER - 1);

    // ---------------------------------------------------------------- 合図
    public static final String SIGNAL_GEAR_MADE = "ch3_travel_gear_made";
    public static final String SIGNAL_WHETSTONE_USED = "ch3_whetstone_used";
    // 雷の刃が効いている間に倒した / 焼けた羽守を倒した (備えの頼み「刃を研ぐ」の締めに使える)
    public static final String SIGNAL_THUNDER_KILL = "ch3_thunder_blade_kill";
    public static final String SIGNAL_WARDEN_HONED = "ch3_warden_honed";
    public static final String SCORCHED_WARDEN = "skyabyss:scorched_warden";
    public static final String SIGNAL_OIL_USED = "ch3_oil_used";
    public static final String SIGNAL_WIND_READ = "ch3_wind_read";

    public static final List<String> RECIPES = List.of("peak_crossing_helmet", "peak_crossing_chestplate", "peak_crossing_leggings", "peak_crossing_boots",
            "thunder_whetstone", "wind_dumpling", "lightning_oil", "wind_streamer", "copper_wire");

    // ---------------------------------------------------------------- 数値
    public static final int[] ARMOR_DEFENSE = {3, 7, 6, 3};
    public static final float ARMOR_TOUGHNESS = 2.75f;
    // 感電の傷の軽さ: 1 部位 6.25% (一式 25%)
    public static final double SHOCK_PER_PIECE = 0.0625;
    // 避雷の油: 90 秒、感電と雷の傷を半分に
    public static final int OIL_TICKS = 20 * 90;
    public static final double OIL_FACTOR = 0.5;
    // 避雷の油の備えの糧の点 (回復ではないので小さく。1❤)
    public static final double OIL_POINTS = 2;
    public static final double WHETSTONE_ATTACK = ROW.whetstone();
    public static final int WHETSTONE_TICKS = 20 * 60 * 20;
    public static final int DUMPLING_ABSORB_TICKS = 20 * 60;
    public static final int DUMPLING_FALL_TICKS = 20 * 20;
    public static final int STREAMER_TICKS = 20 * 30;
    public static final int STREAMER_COOLDOWN = 20 * 30;
    public static final double STREAMER_LENGTH = 4.0;

    // 雷を使う敵 (この敵の傷は感電の傷)。雷翼の背の雷蛾・雷乞い・雷獣・心臓、三章の地上の帯電毛玉
    public static final Set<String> SHOCK_MOBS = Set.of("skyabyss:static_moth", "skyabyss:static_puff", "skyabyss:thunder_supplicant", "skyabyss:raiju",
            "skyabyss:thunder_wing_heart", "skyabyss:storm_cub");

    public static final DeferredHolder<MobEffect, MobEffect> THUNDER_WHETTED = EFFECTS.register("thunder_whetted", ThunderWhettedEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> GROUNDED = EFFECTS.register("grounded", GroundedEffect::new);

    public static final Holder<ArmorMaterial> MATERIAL = MATERIALS.register("peak_crossing", () -> new ArmorMaterial(
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, ARMOR_DEFENSE[0],
                    ArmorItem.Type.CHESTPLATE, ARMOR_DEFENSE[1],
                    ArmorItem.Type.LEGGINGS, ARMOR_DEFENSE[2],
                    ArmorItem.Type.BOOTS, ARMOR_DEFENSE[3],
                    ArmorItem.Type.BODY, 7)),
            9,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            () -> Ingredient.of(Ch3Gear.COPPER_WIRE.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "peak_crossing"))),
            ARMOR_TOUGHNESS,
            0f));

    // ---------------------------------------------------------------- 素材
    public static final DeferredItem<Item> SCORCHED_FEATHER = material("scorched_feather");
    public static final DeferredItem<Item> STATIC_COTTON = material("static_cotton");
    public static final DeferredItem<Item> COPPER_WIRE = material("copper_wire");
    // 雷の護り糸 (鼓動守 雷翼の材料。焼けた羽守の落とし物)
    public static final DeferredItem<Item> THUNDER_WARD_THREAD = material("thunder_ward_thread");

    // ---------------------------------------------------------------- 峰越えの旅装 (4 部位)
    public static final DeferredItem<Item> HELMET = armor("peak_crossing_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<Item> CHESTPLATE = armor("peak_crossing_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<Item> LEGGINGS = armor("peak_crossing_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<Item> BOOTS = armor("peak_crossing_boots", ArmorItem.Type.BOOTS);

    // ---------------------------------------------------------------- 砥石・糧・薬・道具
    public static final DeferredItem<Item> THUNDER_WHETSTONE = ITEMS.register("thunder_whetstone",
            () -> new ThunderWhetstoneItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> WIND_DUMPLING = ITEMS.register("wind_dumpling",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.8f)
                    .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, DUMPLING_ABSORB_TICKS, 1), 1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.SLOW_FALLING, DUMPLING_FALL_TICKS, 0), 1.0f).build())) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("item.skyabyss.wind_dumpling.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            });
    public static final DeferredItem<Item> LIGHTNING_OIL = ITEMS.register("lightning_oil",
            () -> new LightningOilItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(0).saturationModifier(0f)
                    .alwaysEdible().fast().build())));
    public static final DeferredItem<Item> WIND_STREAMER = ITEMS.register("wind_streamer",
            () -> new WindStreamerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    static {
        // 備えの刃: 雷の砥石は効いているか持ち歩いているとき +2.2 (ほかの砥石と重ねず、大きい方 = TravelGear.whetstoneBonus)
        TravelGear.addWhetstoneKind(player -> player.hasEffect(THUNDER_WHETTED) || TravelGear.carries(player, THUNDER_WHETSTONE.get()) ? WHETSTONE_ATTACK : 0);
        // 糧: 団子は吸収 II (食べ物の効果で自然に入る)。油は回復ではないので小さく 1❤
        Readiness.registerProvision(stack -> stack.is(LIGHTNING_OIL.get()) ? OIL_POINTS : 0);
        Readiness.registerRemedy(Readiness.Lamp.BODY, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.peak_crossing"));
        Readiness.registerRemedy(Readiness.Lamp.BLADE, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.thunder_whetstone"));
        Readiness.registerRemedy(Readiness.Lamp.PROVISIONS, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.wind_dumpling"));
        Readiness.registerRemedy(Readiness.Lamp.PROVISIONS, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.lightning_oil"));
        WayfarerNotes.registerRecipes(CHAPTER, RECIPES.stream().map(n -> ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, n)).toArray(ResourceLocation[]::new));
    }

    private Ch3Gear() {
    }

    private static DeferredItem<Item> material(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable("item.skyabyss." + id + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        });
    }

    private static DeferredItem<Item> armor(String id, ArmorItem.Type type) {
        return ITEMS.register(id, () -> new PeakCrossingArmorItem(MATERIAL, type, new Item.Properties().rarity(Rarity.UNCOMMON)));
    }

    static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        EFFECTS.register(modEventBus);
        modEventBus.addListener(Ch3Gear::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (DeferredItem<Item> item : List.of(SCORCHED_FEATHER, STATIC_COTTON, COPPER_WIRE, THUNDER_WARD_THREAD, HELMET, CHESTPLATE, LEGGINGS, BOOTS,
                    THUNDER_WHETSTONE, WIND_DUMPLING, LIGHTNING_OIL, WIND_STREAMER)) {
                event.accept(item.get());
            }
        }
    }

    // ---------------------------------------------------------------- 旅装
    public static boolean isPeakCrossing(ItemStack stack) {
        return stack.is(HELMET.get()) || stack.is(CHESTPLATE.get()) || stack.is(LEGGINGS.get()) || stack.is(BOOTS.get());
    }

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
        if (event.getEntity() instanceof ServerPlayer player && isPeakCrossing(event.getCrafting())) {
            Tasks.later(2, () -> signalIfComplete(player));
        }
    }

    // 着ている峰越えの旅装の部位の数
    public static int worn(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (isPeakCrossing(e.getItemBySlot(slot))) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 感電の傷
    // 感電の傷か: 雷 (lightning_bolt の傷)、または雷を使う敵の傷
    public static boolean isShock(DamageSource source) {
        if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            return true;
        }
        Entity e = source.getEntity();
        return e != null && SHOCK_MOBS.contains(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())));
    }

    // 感電の傷に掛ける倍率 (純粋な部分。自己テストが呼ぶ): 旅装の部位 × 6.25% 軽く、避雷の油が効いていれば半分
    public static double shockFactor(int pieces, boolean grounded) {
        double f = 1.0 - SHOCK_PER_PIECE * Math.max(0, Math.min(4, pieces));
        return grounded ? f * OIL_FACTOR : f;
    }

    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide) {
            return;
        }
        // 感電の傷を軽く (旅装・避雷の油)
        if (target instanceof Player && isShock(source) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            double f = shockFactor(worn(target), target.hasEffect(GROUNDED));
            if (f < 1.0) {
                event.setAmount((float) (event.getAmount() * f));
            }
        }
        // 雷の刃: 近接で当てると小さな火花 (見た目と音だけ)
        if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && player.hasEffect(THUNDER_WHETTED)
                && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 10, 0.25, 0.3, 0.25, 0.15);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 0.4f, 1.8f);
        }
    }

    // ---------------------------------------------------------------- 雷の砥石
    static void addAttackModifier(MobEffect effect) {
        effect.addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "effect.thunder_whetted"), WHETSTONE_ATTACK,
                AttributeModifier.Operation.ADD_VALUE);
    }

    // 雷の砥石を使った。岩・塩の砥石の研ぎは雷の研ぎに替わる (重ねない)
    public static boolean applyWhetstone(ServerPlayer player) {
        player.removeEffect(TravelGear.WHETTED);
        player.removeEffect(Ch2Gear.SALT_WHETTED);
        player.addEffect(new MobEffectInstance(THUNDER_WHETTED, WHETSTONE_TICKS, 0, false, true, true));
        Notice.hint(player, "message.skyabyss.thunder_whetstone.used");
        Quests.signal(player, SIGNAL_WHETSTONE_USED);
        return true;
    }

    @SubscribeEvent
    static void onKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && player.hasEffect(THUNDER_WHETTED) && !(event.getEntity() instanceof Player)) {
            Quests.signal(player, SIGNAL_THUNDER_KILL);
            if (SCORCHED_WARDEN.equals(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType())))) {
                Quests.signal(player, SIGNAL_WARDEN_HONED);
            }
        }
    }

    // ---------------------------------------------------------------- 避雷の油
    static void applyOil(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(GROUNDED, OIL_TICKS, 0, false, true, true));
        if (entity instanceof ServerPlayer sp) {
            Notice.hint(sp, "message.skyabyss.lightning_oil.used");
            Quests.signal(sp, SIGNAL_OIL_USED);
        }
    }

    // ---------------------------------------------------------------- 風読みの小旗
    // 立てた小旗 (保存しない。30 秒で消える)
    record Streamer(ServerLevel level, Vec3 at, long until) {
    }

    private static final List<Streamer> STREAMERS = new ArrayList<>();
    private static final DustParticleOptions WIND_LIGHT = new DustParticleOptions(new Vector3f(0.82f, 0.92f, 1.0f), 0.7f);

    // 風下の向き (x, z の単位ベクトル。x が東、z が南)。StormVane の日の風
    public static Vec3 downwind(long day) {
        double a = StormVane.windAngle(day);
        return new Vec3(Math.sin(a), 0, -Math.cos(a));
    }

    // 次の嵐 (雷雨) までの tick の目安 (今が雷雨なら 0)。晴れの刻が残っていればその後
    public static long ticksToStorm(ServerLevel level) {
        if (level.isThundering()) {
            return 0;
        }
        if (level.getLevelData() instanceof ServerLevelData data) {
            long t = Math.max(0, data.getThunderTime());
            return Math.max(t, data.getClearWeatherTime());
        }
        return -1;
    }

    public static Component streamerMessage(Vec3 wind, long toStorm) {
        String dir = compass(wind);
        Component d = Component.translatable("message.skyabyss.wind_streamer.dir." + dir);
        if (toStorm == 0) {
            return Component.translatable("message.skyabyss.wind_streamer.now", d);
        }
        if (toStorm < 0) {
            return Component.translatable("message.skyabyss.wind_streamer.still", d);
        }
        return Component.translatable("message.skyabyss.wind_streamer.read", d, Math.max(1, (int) Math.round(toStorm / 1200.0)));
    }

    // 八方位 (n, ne, e, se, s, sw, w, nw)
    public static String compass(Vec3 v) {
        double deg = Math.toDegrees(Math.atan2(v.x, -v.z));
        int k = Math.floorMod(Math.round((float) (deg / 45.0)), 8);
        return new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[k];
    }

    public static void plantStreamer(ServerPlayer player, Vec3 at) {
        ServerLevel level = player.serverLevel();
        synchronized (STREAMERS) {
            STREAMERS.add(new Streamer(level, at, level.getGameTime() + STREAMER_TICKS));
        }
        long day = level.getDayTime() / 24000L;
        Notice.bar(player, streamerMessage(downwind(day), ticksToStorm(level)));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8f, 1.2f);
        Quests.signal(player, SIGNAL_WIND_READ);
    }

    public static int activeStreamers() {
        synchronized (STREAMERS) {
            return STREAMERS.size();
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 5 != 0) {
            return;
        }
        synchronized (STREAMERS) {
            if (STREAMERS.isEmpty()) {
                return;
            }
            STREAMERS.removeIf(s -> s.level().getGameTime() >= s.until());
            for (Streamer s : STREAMERS) {
                Vec3 w = downwind(s.level().getDayTime() / 24000L);
                // 柱 (小旗の竿) と、風下へ伸びる細い光の筋
                for (double y = 0; y <= 1.4; y += 0.35) {
                    s.level().sendParticles(WIND_LIGHT, s.at().x, s.at().y + y, s.at().z, 1, 0, 0, 0, 0);
                }
                for (double d = 0.3; d <= STREAMER_LENGTH; d += 0.4) {
                    double wave = Math.sin((s.level().getGameTime() * 0.3) + d * 2) * 0.08;
                    s.level().sendParticles(WIND_LIGHT, s.at().x + w.x * d, s.at().y + 1.4 + wave, s.at().z + w.z * d, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        synchronized (STREAMERS) {
            STREAMERS.clear();
        }
    }

    // ================================================================ 品の型
    // 雷の砥石: 右クリックで一つ使い、雷の刃 20 分
    public static class ThunderWhetstoneItem extends Item {
        public ThunderWhetstoneItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) {
                return InteractionResultHolder.sidedSuccess(stack, true);
            }
            if (player instanceof ServerPlayer sp) {
                applyWhetstone(sp);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8f, 1.7f);
                ((ServerLevel) level).sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                player.getCooldowns().addCooldown(this, 40);
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.skyabyss.thunder_whetstone.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    // 避雷の油: 飲むと 90 秒の「避雷」
    public static class LightningOilItem extends Item {
        public LightningOilItem(Properties properties) {
            super(properties);
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.DRINK;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide) {
                applyOil(entity);
            }
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.skyabyss.lightning_oil.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    // 風読みの小旗: ブロックの上に使えばそこに、宙で使えば足もとに立てる (消耗しない)
    public static class WindStreamerItem extends Item {
        public WindStreamerItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            if (ctx.getPlayer() == null) {
                return InteractionResult.PASS;
            }
            BlockPos above = ctx.getClickedPos().relative(ctx.getClickedFace());
            return plant(ctx.getLevel(), ctx.getPlayer(), Vec3.atBottomCenterOf(above)) ? InteractionResult.sidedSuccess(ctx.getLevel().isClientSide) : InteractionResult.PASS;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            return plant(level, player, player.position()) ? InteractionResultHolder.sidedSuccess(stack, level.isClientSide) : InteractionResultHolder.pass(stack);
        }

        private boolean plant(Level level, Player player, Vec3 at) {
            if (player.getCooldowns().isOnCooldown(this)) {
                return false;
            }
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                plantStreamer(sp, at);
                player.getCooldowns().addCooldown(this, STREAMER_COOLDOWN);
            }
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.skyabyss.wind_streamer.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    // 雷の刃 (雷の砥石を使って 20 分)。攻撃 +2.2
    public static class ThunderWhettedEffect extends MobEffect {
        public ThunderWhettedEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xAAD8FF);
            addAttackModifier(this);
        }
    }

    // 避雷 (避雷の油を飲んで 90 秒)。感電と雷の傷が半分 (onIncoming)
    public static class GroundedEffect extends MobEffect {
        public GroundedEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xC47A3A);
        }
    }
}
