package jp.revati.skyabyss.titan.ember.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.GearEvents;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.item.accessory.AccessoryData;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.affix.AffixType;
import jp.revati.skyabyss.loot.GearExchange;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.tide.gear.TideGear;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 余燼の装備の自己テスト (SKYABYSS_SELFTEST=1、SELFTEST ember gear PASSED / FAILED)。03 の 13.8節の表と同じ検査:
//  - 品の登録、図鑑の表 (10 品・EmberIds.CODEX の並び・確率)、必要 Lv (レア 26・伝説 28)、品の数値
//  - 強さの曲線: GearCurve.STAGES の余燼の段 (03 の 9.8節) に +0 / +10 が入るか、潮殻・雷翼の目安との比べ、余燼の目安のボスとの手ごたえ、守り
//  - 効果: 一式 (強靭・火に触れて筋力・熱のたまり方)、戦斧のくすぶり (火に強い相手にも) と +10%、大鎚の亀裂、火掻きの灰、外套、護符、灯、角杯
//  - 報酬: 道中 1%・鍛え手 5%、図鑑に記す、天井、模擬の撃破数 (13 の 6.4節)
//  - 炉: 余燼の欠片で強化・分解、交換 (巨神ごと)
public final class EmberGearSelfTest {
    private EmberGearSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            table(f);
            numbers(f);
            curve(f, notes);
            effects(server, f);
            loot(server, f, notes);
            forge(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("ember gear selftest crashed", e);
            f.add("crashed: " + e);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST ember gear: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember gear PASSED (table, levels, stats, curve, set/axe/hammer/poker/mantle/charm/lantern/horn, loot, forge, exchange)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST ember gear FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static Item item(String id) {
        return TitanGearSet.item(id).orElseThrow(() -> new IllegalStateException("not registered: " + id));
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    // ---------------------------------------------------------------- 表と必要 Lv
    private static void table(List<String> f) {
        check(f, TitanGearSets.get(EmberGear.TITAN) == EmberGear.SET, "ember set is not registered in TitanGearSets");
        check(f, TitanGearSets.all().get(0) == TitanGearSets.STONE_WHALE, "stone whale should be the first gear set");
        List<String> codex = EmberIds.CODEX.stream().map(p -> SkyAbyss.MODID + ":" + p).toList();
        check(f, EmberGear.SET.collection().equals(codex), "ember collection should follow EmberIds.CODEX: " + EmberGear.SET.collection());
        List<String> choices = EmberIds.FIRST_CLEAR_CHOICES.stream().map(p -> SkyAbyss.MODID + ":" + p).toList();
        check(f, EmberGear.SET.exclusives().equals(choices), "ember exclusives should be the first-clear choices");
        for (String id : EmberGear.SET.collection()) {
            check(f, TitanGearSet.registered(id), id + " is not registered");
            Item it = item(id);
            check(f, it instanceof ModGear g && g.dungeon().equals(EmberGear.TITAN), id + " is not ember gear");
            int want = EmberGear.SET.isExclusive(id) ? 28 : 26;
            check(f, GearLevel.required(it) == want, id + " requires Lv " + GearLevel.required(it) + " (want " + want + ")");
            check(f, TitanGearSets.of(it) == EmberGear.SET, id + " does not map to the ember set");
            check(f, new ItemStack(it).has(DataComponents.FIRE_RESISTANT), id + " should not burn in lava (it comes from the ember titan)");
        }
        for (String id : List.of(EmberGear.CORE, EmberGear.SHARD)) {
            check(f, TitanGearSet.registered(id) && GearLevel.required(item(id)) == 0, id + " missing or has a level requirement");
        }
        check(f, EmberGear.SET.shardItem().orElse(null) == EmberGearSetup.SHARD.get(), "ember shard is not the set's shard");
        check(f, near(EmberGear.SET.chance(EmberGear.HELMET), 0.25) && near(EmberGear.SET.chance(EmberGear.WAR_AXE), 0.20)
                && near(EmberGear.SET.chance(EmberGear.HAMMER), 0.04) && near(EmberGear.SET.chance(EmberGear.LANTERN), 0.02),
                "heart chances do not match 13 6.3");
        check(f, GearLevel.required(EmberGearSetup.HORN.get()) == 0, "the horn is food and must not need a level");
    }

    // ---------------------------------------------------------------- 品の数値
    private static double speed(Item item) {
        double s = 4;
        for (ItemAttributeModifiers.Entry e : item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().equals(Attributes.ATTACK_SPEED) && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                s += e.modifier().amount();
            }
        }
        return s;
    }

    private static double reach(Item item) {
        double r = 0;
        for (ItemAttributeModifiers.Entry e : item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE)) {
                r += e.modifier().amount();
            }
        }
        return r;
    }

    static GearCurve.Stage stage(String id) {
        return GearCurve.STAGES.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private static void numbers(List<String> f) {
        Item axe = item(EmberGear.WAR_AXE);
        Item hammer = item(EmberGear.HAMMER);
        Item poker = item(EmberGear.POKER);
        check(f, near(GearEvents.weaponAttack(axe), EmberGear.WAR_AXE_ATTACK) && near(speed(axe), EmberGear.WAR_AXE_SPEED) && near(reach(axe), 0),
                "war axe stats " + GearEvents.weaponAttack(axe) + " / " + speed(axe));
        check(f, near(GearEvents.weaponAttack(hammer), EmberGear.HAMMER_ATTACK) && near(speed(hammer), EmberGear.HAMMER_SPEED) && near(reach(hammer), 0),
                "hammer stats " + GearEvents.weaponAttack(hammer) + " / " + speed(hammer));
        check(f, near(GearEvents.weaponAttack(poker), EmberGear.POKER_ATTACK) && near(speed(poker), EmberGear.POKER_SPEED)
                && near(reach(poker), EmberGear.POKER_REACH), "poker stats " + GearEvents.weaponAttack(poker) + " / " + speed(poker));
        int defense = 0;
        double toughness = 0;
        for (String id : EmberGear.ARMOR) {
            ArmorItem a = (ArmorItem) item(id);
            defense += a.getDefense();
            toughness += a.getToughness();
        }
        GearCurve.Stage ember = stage(EmberGear.TITAN);
        check(f, ember.level() == 30 && near(ember.dps0(), 17.5) && near(ember.dps10(), 22.8) && near(ember.armor(), 22) && near(ember.toughness(), 16),
                "GearCurve ember stage differs from 03 9.8: " + ember);
        check(f, defense == ember.armor() && near(toughness + EmberGear.SET2_TOUGHNESS, ember.toughness()),
                "armor " + defense + " / " + toughness + " (+set " + EmberGear.SET2_TOUGHNESS + ") should match the 9.8 stage " + ember.armor() + " / " + ember.toughness());
        check(f, AffixRules.slotOf(axe) == AffixType.Slot.WEAPON && AffixRules.slotOf(hammer) == AffixType.Slot.WEAPON
                && AffixRules.slotOf(poker) == AffixType.Slot.WEAPON && AffixRules.slotOf(item(EmberGear.HELMET)) == AffixType.Slot.ARMOR, "affix slots");
        check(f, AffixRules.slotOf(item(EmberGear.CHARM)) == null && AffixRules.slotOf(item(EmberGear.MANTLE)) == null
                && AffixRules.slotOf(item(EmberGear.LANTERN)) == null, "charm and trinkets must not take affixes");
    }

    // ---------------------------------------------------------------- 強さの曲線 (03 の 9章の式)
    public static double attack(String id, int enhance) {
        double mult = 1 + GearEvents.ATTACK_RATE_PER_LEVEL * enhance;
        double a = id.equals(EmberGear.WAR_AXE) ? EmberGear.WAR_AXE_ATTACK : id.equals(EmberGear.HAMMER) ? EmberGear.HAMMER_ATTACK : EmberGear.POKER_ATTACK;
        return a * mult;
    }

    // 一体への 1 秒あたり (筋力・相手の防具の前、固有効果の上乗せ込み)。戦斧のくすぶりは 2 打目から切れないので常に入るとみる
    public static double dps(String id, int enhance) {
        double a = attack(id, enhance);
        if (id.equals(EmberGear.WAR_AXE)) {
            return a * EmberGear.WAR_AXE_SPEED * (1 + EmberGear.WAR_AXE_SMOLDER_BONUS);
        }
        if (id.equals(EmberGear.HAMMER)) {
            int every = enhance >= ModGear.AWAKEN_LEVEL ? 2 : 3;
            return a * EmberGear.HAMMER_SPEED * (1 + EmberGear.HAMMER_CRACK_RATE / every);
        }
        return a * EmberGear.POKER_SPEED;
    }

    // 余燼の鎧で受ける一撃の傷 (enhance: 4 部位の強化、ward: 特性の守り)。一式に傷を減らす効果はない (熱の効果だけ)
    public static double emberHit(double hit, int enhance, double ward) {
        double toughness = 4 * EmberGear.ARMOR_TOUGHNESS + EmberGear.SET2_TOUGHNESS + 4 * GearEvents.TOUGHNESS_PER_LEVEL * enhance;
        return hit * GearCurve.armorTaken(hit, 22, toughness) * (1 - ward);
    }

    // 心臓の防具 (潮殻と同じ 8 / 6 とみる。F が決めたら合わせる) を通した 1 秒あたり
    private static double dpsOn(double dps, double perHit, double strength) {
        return dps * strength * GearCurve.armorTaken(perHit * strength, 8, 6);
    }

    private static void curve(List<String> f, List<String> notes) {
        GearCurve.Stage tide = stage(TideGear.TITAN);
        GearCurve.Stage thunder = stage("thunder_wing");
        GearCurve.Stage ember = stage(EmberGear.TITAN);
        for (String id : List.of(EmberGear.WAR_AXE, EmberGear.HAMMER, EmberGear.POKER)) {
            double d0 = dps(id, 0);
            double d5 = dps(id, 5);
            double d10 = dps(id, 10);
            notes.add(String.format("%s: per second +0 %.2f, +5 %.2f, +10 %.2f (stage %.1f / %.1f)", id, d0, d5, d10, ember.dps0(), ember.dps10()));
            // +0 は雷翼の +0 の 1.1 倍以上で雷翼の +10 以下 (乗り換えの手応え)、余燼の目安 17.5 の ±5%
            check(f, d0 >= thunder.dps0() * 1.1 - 1e-6 && d0 <= thunder.dps10() + 1e-6, id + " +0 " + d0 + " is out of the ember stage range");
            check(f, d0 >= ember.dps0() * 0.95 && d0 <= ember.dps0() * 1.05, id + " +0 " + d0 + " is far from the stage dps0 " + ember.dps0());
            // +10 は余燼の目安 22.8 の ±5% (1 章の最後の巨神。次の巨神の目安はまだない)
            check(f, d10 >= ember.dps10() * 0.95 && d10 <= ember.dps10() * 1.05, id + " +10 " + d10 + " is far from the stage dps10 " + ember.dps10());
            // +0 は潮殻の +10 (17.2) の 0.95 倍以上 (次の巨神の +0 は前の巨神の +5 より上、+10 の手前)
            check(f, d0 >= tide.dps10() * 0.95, id + " +0 is far below the tide +10");
        }
        // 余燼の目安のボス (790、一撃 18.5) との手ごたえ (9.1節: 手数 6 割、技の間隔 2.5 秒。推奨 Lv 30)
        int lv = ember.level();
        double hp = GearCurve.maxHealth(lv);
        double strength = GearCurve.strengthMultiplier(lv);
        double hit0 = emberHit(ember.bossHit(), 0, 0);
        double loadFresh = Double.MAX_VALUE;
        for (String id : List.of(EmberGear.WAR_AXE, EmberGear.HAMMER, EmberGear.POKER)) {
            loadFresh = Math.min(loadFresh, GearCurve.load(dpsOn(dps(id, 0), attack(id, 0), strength), ember.bossHp(), 0.6, 2.5, hp, hit0));
        }
        double best = 1.10 * 1.125;
        double strFull = 1 + 0.006 * Math.min(50, 3 * (lv - 1));
        double hitBest = emberHit(ember.bossHit(), 10, 0.10);
        double loadEasy = Double.MAX_VALUE;
        for (String id : List.of(EmberGear.WAR_AXE, EmberGear.HAMMER, EmberGear.POKER)) {
            // 4 部位の火照りの攻撃 +10% も入れた、いちばん楽な形
            double d = dps(id, 10) * best * (1 + EmberGear.SET4_FLUSHED_ATTACK);
            loadEasy = Math.min(loadEasy, GearCurve.load(dpsOn(d, attack(id, 10) * 1.10, strFull), ember.bossHp(), 0.6, 2.5, hp + 4, hitBest));
        }
        notes.add(String.format("ember heart load: fresh +0 %.2f, easiest +10/best affixes/full strength/flushed %.2f (hit %.2f / %.2f)",
                loadFresh, loadEasy, hit0, hitBest));
        check(f, loadFresh >= 3.0 && loadFresh <= 6.0, "ember heart load right after getting the gear is " + loadFresh + " (want 3.0 to 6.0)");
        check(f, loadEasy >= 1.3, "the ember heart becomes a chore with +10 ember gear (load " + loadEasy + ")");
        // 守り: 余燼の鎧 +0 は、潮殻の鎧 +10 (濡れていない) より余燼の一撃に弱くない。一式は ×0.8 の上限を超えない (傷を減らす効果がない)
        double tide10 = ember.bossHit() * GearCurve.armorTaken(ember.bossHit(), 20, 4 * TideGear.ARMOR_TOUGHNESS + TideGear.SET2_TOUGHNESS + 12)
                * TideGear.SET4_TAKEN;
        notes.add(String.format("ember heart hit: ember armor +0 %.2f vs tide armor +10 %.2f", hit0, tide10));
        check(f, hit0 <= tide10 + 1e-6, "ember armor +0 is weaker than tide armor +10 against the ember heart");
    }

    // ---------------------------------------------------------------- 効果
    private static FakePlayer player(ServerLevel level, String name, int lv) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        // 世界の出生地点 (出生地点の周りのチャンクは読み込まれている)。(0, 0) は世界によって読み込まれておらず、
        // 敵を足せない (大鎚の亀裂・護符が当たらない) ことがあった
        net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
        p.setPos(spawn.getX() + 0.5, level.getMinBuildHeight() + 2, spawn.getZ() + 0.5);
        Growth.get(p).level = lv;
        return p;
    }

    private static void effects(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(level, "ember_gear_test", 30);
        var tough = p.getAttribute(Attributes.ARMOR_TOUGHNESS);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        // 一式
        for (int i = 0; i < 2; i++) {
            p.setItemSlot(slots[i], new ItemStack(item(EmberGear.ARMOR.get(i))));
        }
        EmberGearEvents.tickForTest(p);
        AttributeModifier set2 = tough.getModifier(EmberGearEvents.SET2);
        check(f, set2 != null && near(set2.amount(), EmberGear.SET2_TOUGHNESS), "2-piece toughness bonus is missing");
        check(f, !EmberGear.armorSet(p) && near(EmberGear.heatGain(p), 1), "2 pieces should not change heat gain");
        for (int i = 2; i < 4; i++) {
            p.setItemSlot(slots[i], new ItemStack(item(EmberGear.ARMOR.get(i))));
        }
        check(f, EmberGear.armorSet(p) && near(EmberGear.heatGain(p), 0.7), "4-piece heat gain should be x0.7, is " + EmberGear.heatGain(p));
        check(f, near(EmberGearEvents.flushedAttack(p), 1), "flushed attack must not apply off the ember back");
        // 火に触れると筋力 I
        p.igniteForTicks(40);
        EmberGearEvents.tickForTest(p);
        check(f, p.hasEffect(MobEffects.DAMAGE_BOOST), "4 pieces + fire should give strength I");
        p.clearFire();
        p.removeEffect(MobEffects.DAMAGE_BOOST);
        // 外套・角杯・火炎耐性の重ね方 (13 の 3.4節)
        AccessoryData acc = Accessories.get(p);
        acc.items.set(AccessoryData.CHARM + 1, new ItemStack(item(EmberGear.MANTLE)));
        check(f, EmberGear.mantleWorn(p) && near(EmberGear.heatGain(p), 0.7 * 0.75), "armor + mantle heat gain " + EmberGear.heatGain(p));
        EmberHornItem.drinkOnBack(p);
        check(f, EmberGear.hornActive(p) && near(EmberGear.heatGain(p), EmberGear.HEAT_GAIN_FLOOR), "armor + mantle + horn should floor at 0.4");
        EmberHornItem.clear(p);
        for (EquipmentSlot s : slots) {
            p.setItemSlot(s, ItemStack.EMPTY);
        }
        acc.items.set(AccessoryData.CHARM + 1, ItemStack.EMPTY);
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100));
        EmberHornItem.drinkOnBack(p);
        check(f, near(EmberGear.heatGain(p), 0.5), "fire resistance and horn should not stack (stronger only)");
        EmberHornItem.clear(p);
        p.removeEffect(MobEffects.FIRE_RESISTANCE);
        EmberGearEvents.tickForTest(p);
        check(f, tough.getModifier(EmberGearEvents.SET2) == null, "set toughness was not removed");
        // 外套は火の傷だけを減らす (余燼の熱の傷は減らさない)
        acc.items.set(AccessoryData.CHARM + 1, new ItemStack(item(EmberGear.MANTLE)));
        check(f, near(EmberGearEvents.mantleTaken(p, p.damageSources().inFire()), EmberGear.MANTLE_FIRE_TAKEN)
                && near(EmberGearEvents.mantleTaken(p, p.damageSources().lava()), EmberGear.MANTLE_FIRE_TAKEN)
                && near(EmberGearEvents.mantleTaken(p, p.damageSources().source(EmberIds.HEAT)), 1)
                && near(EmberGearEvents.mantleTaken(p, p.damageSources().generic()), 1), "mantle should only soften fire and lava");
        acc.items.set(AccessoryData.CHARM + 1, ItemStack.EMPTY);
        // 必要 Lv: Lv 25 で鎧は着られない、26 で着られる
        FakePlayer low = player(level, "ember_gear_low", 25);
        ItemStack helm = new ItemStack(item(EmberGear.HELMET));
        check(f, !helm.getItem().canEquip(helm, EquipmentSlot.HEAD, low), "Lv 25 could equip a Lv 26 helm");
        Growth.get(low).level = 26;
        check(f, helm.getItem().canEquip(helm, EquipmentSlot.HEAD, low), "Lv 26 cannot equip the ember helm");

        // 戦斧: 火に強い相手にもくすぶりの印、くすぶる相手へ +10%
        Blaze blaze = EntityType.BLAZE.create(level);
        if (blaze != null) {
            EmberGearEvents.smolder(blaze, 80);
            check(f, !blaze.isOnFire() && EmberGearEvents.smoldering(blaze), "a fire-immune blaze should smolder without burning");
        }
        Zombie plain = zombie(level, p, 2);
        Zombie smolder = zombie(level, p, -2);
        if (plain != null && smolder != null) {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(item(EmberGear.WAR_AXE)));
            EmberGearEvents.smolder(smolder, 80);
            float h1 = plain.getHealth();
            float h2 = smolder.getHealth();
            plain.hurt(p.damageSources().playerAttack(p), 10);
            smolder.hurt(p.damageSources().playerAttack(p), 10);
            double lost1 = h1 - plain.getHealth();
            double lost2 = h2 - smolder.getHealth();
            check(f, lost1 > 0 && Math.abs(lost2 / lost1 - 1.1) < 0.02, "war axe smolder bonus: " + lost1 + " vs " + lost2);
            // 大鎚の亀裂: 線の上のゾンビに当たる
            plain.setHealth(plain.getMaxHealth());
            plain.setPos(p.getX() + 3, p.getY(), p.getZ());
            int crackHits = EmberGearEvents.erupt(level, p, plain, p.position(), new Vec3(1, 0, 0), EmberGear.HAMMER_CRACK_LENGTH, 6f);
            check(f, crackHits >= 1 && plain.getHealth() < plain.getMaxHealth(), "hammer crack did not hit the zombie on its line");
            ItemStack hammer = new ItemStack(item(EmberGear.HAMMER));
            check(f, SmithHammerItem.every(hammer) == 3 && SmithHammerItem.crackLength(hammer) == EmberGear.HAMMER_CRACK_LENGTH, "hammer +0 rhythm");
            hammer.set(ModDataComponents.ENHANCE.get(), 10);
            check(f, SmithHammerItem.every(hammer) == 2 && SmithHammerItem.crackLength(hammer) == EmberGear.HAMMER_CRACK_LENGTH_MAX, "hammer +10 rhythm");
            int before = EmberGearEvents.pendingCracks();
            EmberGearEvents.crack(level, p, plain, new Vec3(1, 0, 0), 5, 1f);
            check(f, EmberGearEvents.pendingCracks() == before + 1, "a crack should wait before it erupts");
            // 火掻きの灰: 5 つ目で固まり、鈍足と防御 −4
            ItemStack poker = new ItemStack(item(EmberGear.POKER));
            int n = ThrallPokerItem.stacksNeeded(poker);
            boolean early = false;
            for (int i = 1; i < n; i++) {
                early |= ThrallPokerItem.addAsh(smolder, n);
            }
            boolean set = ThrallPokerItem.addAsh(smolder, n);
            check(f, n == 5 && !early && set, "poker ash should set on the 5th stack");
            EmberGearEvents.brittle(smolder, p);
            var armor = smolder.getAttribute(Attributes.ARMOR);
            check(f, smolder.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && armor != null && armor.getModifier(EmberGearEvents.BRITTLE) != null,
                    "brittle should slow and lower armor");
            // 護符: 近くの敵に傷、自分の熱を 0 に、待ち 50 秒
            ItemStack charm = new ItemStack(item(EmberGear.CHARM));
            KindlingCharmItem c = (KindlingCharmItem) charm.getItem();
            plain.setHealth(plain.getMaxHealth());
            plain.invulnerableTime = 0;
            plain.getPersistentData().remove(SkyAbyss.MODID + ":ember_smolder_until");
            plain.clearFire();
            plain.setPos(p.getX() + 2, p.getY(), p.getZ());
            boolean used = c.activate(p, charm);
            check(f, used && plain.getHealth() < plain.getMaxHealth() && EmberGearEvents.smoldering(plain), "charm did not burn the zombie");
            check(f, !c.activate(p, charm) && c.cooldownTicks() == 20 * 50, "charm should rest 50 seconds after use");
            plain.discard();
            smolder.discard();
        } else {
            f.add("could not create zombies");
        }

        // 灯: 倒れる傷を一度こらえる。待ちの間とトーテムを通さない傷では働かない
        FakePlayer q = player(level, "ember_gear_lantern", 30);
        Accessories.get(q).items.set(AccessoryData.CHARM + 1, new ItemStack(item(EmberGear.LANTERN)));
        LivingDeathEvent death = new LivingDeathEvent(q, q.damageSources().generic());
        EmberGearEvents.onDeath(death);
        check(f, death.isCanceled() && near(q.getHealth(), EmberGear.LANTERN_HEALTH) && q.getCooldowns().isOnCooldown(item(EmberGear.LANTERN)),
                "lantern should hold the player at 4 health");
        LivingDeathEvent again = new LivingDeathEvent(q, q.damageSources().generic());
        EmberGearEvents.onDeath(again);
        check(f, !again.isCanceled(), "lantern should sleep after it burns");
        FakePlayer r = player(level, "ember_gear_lantern2", 30);
        Accessories.get(r).items.set(AccessoryData.CHARM + 1, new ItemStack(item(EmberGear.LANTERN)));
        LivingDeathEvent kill = new LivingDeathEvent(r, r.damageSources().genericKill());
        EmberGearEvents.onDeath(kill);
        check(f, !kill.isCanceled(), "lantern must not save from /kill");

        // 角杯: 地上では火炎耐性 30 秒
        FakePlayer d = player(level, "ember_gear_horn", 30);
        ItemStack horn = new ItemStack(EmberGearSetup.HORN.get());
        horn.getItem().finishUsingItem(horn, level, d);
        check(f, d.hasEffect(MobEffects.FIRE_RESISTANCE) && !EmberGear.hornActive(d), "horn on the surface should give fire resistance only");
    }

    private static Zombie zombie(ServerLevel level, FakePlayer p, double dz) {
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z == null) {
            return null;
        }
        z.setPos(p.getX() + 2, p.getY(), p.getZ() + dz);
        level.addFreshEntity(z);
        return z;
    }

    // ---------------------------------------------------------------- 報酬
    private static void loot(MinecraftServer server, List<String> f, List<String> notes) {
        RandomSource random = RandomSource.create(23);
        int thrall = 0;
        int smith = 0;
        for (int i = 0; i < 20000; i++) {
            thrall += EmberGearLoot.rollThrall(null, null, random).size();
            smith += EmberGearLoot.rollSmith(null, null, random).size();
        }
        check(f, thrall > 140 && thrall < 270, "thrall armor drops " + thrall + " / 20000 (want about 200)");
        check(f, smith > 850 && smith < 1150, "smith hammer drops " + smith + " / 20000 (want about 1000)");
        UUID who = UUID.randomUUID();
        TitanMemoryData memory = TitanMemoryData.get(server);
        TitanMemoryData.Record rec = memory.record(who, EmberGear.TITAN);
        rec.memory = 3;
        List<ItemStack> hammer = List.of();
        for (int i = 0; i < 500 && hammer.isEmpty(); i++) {
            hammer = EmberGearLoot.rollSmith(server, who, random);
        }
        check(f, !hammer.isEmpty() && hammer.get(0).has(ModDataComponents.AFFIXES.get()), "smith hammer has no affixes");
        check(f, rec.owned.contains(EmberGear.HAMMER) && rec.memory == 0, "smith hammer was not recorded / memory not reset");
        check(f, memory.peek(who, TitanLoot.STONE_WHALE) == null && memory.peek(who, TideGear.TITAN) == null, "ember loot wrote to another titan's record");
        TitanMemoryData.Record hard = new TitanMemoryData.Record();
        TitanGearSet.Pieces pity = EmberGear.SET.rollPieces(hard, TitanLoot.HARD_PITY, random, TitanGearSet::registered);
        check(f, pity.guaranteed() != null && pity.pieces().contains(pity.guaranteed()), "hard pity did not guarantee a piece");
        // 模擬 (13 の 6.4節): 毎回鍛え手と心臓を倒し、初回は灯を選ぶ。図鑑 10 品がそろうまでの平均の撃破数
        double mean = meanClears(4000, random);
        notes.add(String.format("collection in %.1f clears on average (13 6.4 says 13.8)", mean));
        check(f, mean > 11 && mean < 17, "mean clears to complete the ember collection " + mean);
    }

    static double meanClears(int players, RandomSource random) {
        long total = 0;
        for (int n = 0; n < players; n++) {
            TitanMemoryData.Record r = new TitanMemoryData.Record();
            int clears = 0;
            while (r.owned.size() < EmberGear.SET.collection().size() && clears < 200) {
                clears++;
                List<String> got = new ArrayList<>();
                if (random.nextFloat() < EmberGear.SMITH_HAMMER_CHANCE) {
                    got.add(EmberGear.HAMMER);
                }
                EmberGear.SET.record(r, got);
                int mem = r.memory + 1;
                r.memory = mem;
                TitanGearSet.Pieces roll = EmberGear.SET.rollPieces(r, mem, random, TitanGearSet::registered);
                List<String> heart = new ArrayList<>(roll.pieces());
                if (clears == 1) {
                    heart.add(EmberGear.LANTERN);
                }
                EmberGear.SET.record(r, heart);
            }
            total += clears;
        }
        return (double) total / players;
    }

    // ---------------------------------------------------------------- 炉
    private static void forge(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        ItemStack axe = new ItemStack(item(EmberGear.WAR_AXE));
        check(f, ForgeRules.shardItem(axe) == EmberGearSetup.SHARD.get(), "war axe should be enhanced with ember shards");
        FakePlayer p = player(level, "ember_gear_forge", 30);
        p.getInventory().add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 64));
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 8));
        check(f, !ForgeRules.enhance(p, axe), "war axe was enhanced with stone whale shards");
        p.getInventory().add(new ItemStack(EmberGearSetup.SHARD.get(), 10));
        check(f, ForgeRules.enhance(p, axe) && ModGear.enhance(axe) == 1, "war axe was not enhanced with ember shards");
        check(f, ForgeRules.count(p.getInventory(), EmberGearSetup.SHARD.get()) == 6, "enhance should take 4 ember shards");
        check(f, !ForgeRules.canEnhance(new ItemStack(item(EmberGear.CHARM))) && !ForgeRules.canEnhance(new ItemStack(item(EmberGear.LANTERN))),
                "charm and lantern must not be enhanced");
        int[] y = ForgeRules.dismantleYield(new ItemStack(item(EmberGear.HAMMER)));
        check(f, y[0] == 10 && y[2] == 2, "hammer dismantle yield");
        TitanMemoryData.Record r = TitanMemoryData.get(server).record(p.getUUID(), EmberGear.TITAN);
        check(f, GearExchange.state(EmberGear.SET, r) == ForgeRules.ExchangeState.NO_CLEAR, "exchange before an ember heart clear");
        r.clears = 1;
        check(f, GearExchange.state(EmberGear.SET, r) == ForgeRules.ExchangeState.READY, "exchange should be ready after a clear");
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_SHARD.get(), 30));
        check(f, !GearExchange.exchange(p, EmberGear.SET, EmberGear.WAR_AXE), "a non-exclusive was traded");
        check(f, GearExchange.exchange(p, EmberGear.SET, EmberGear.LANTERN), "lantern exchange failed");
        check(f, r.owned.contains(EmberGear.LANTERN) && GearExchange.state(EmberGear.SET, r) == ForgeRules.ExchangeState.USED, "exchange state after trading");
        check(f, GearExchange.ownedMask(EmberGear.SET, r) == 0b100, "owned mask " + GearExchange.ownedMask(EmberGear.SET, r));
    }
}
