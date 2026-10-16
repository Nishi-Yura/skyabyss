package jp.revati.skyabyss.item.storm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 雷翼の装備の自己テスト (SKYABYSS_SELFTEST=1、SELFTEST storm gear PASSED / FAILED)。03 の 12.8節の表と同じ検査:
//  - 表: 図鑑 10 品 (確率は 12 の 6.3節)、品と素材の登録、ID (12 の 11.1節と報酬の担当の表)、必要 Lv (レア 20・伝説 22)、炉の欠片
//  - 数値: 攻撃力・速さ・間合い、羽衣の防御 21・強靭 14 (03 の 9.8節の雷翼の段)、一式が ×0.8 の上限内、特性の種類
//  - 強さの曲線: GearCurve.STAGES の雷翼の段の目安 (+0 / +5 / +10、潮殻・余燼との並び)、心臓 (650、一撃 15.5) との手ごたえ、守り
//  - 効果: 落下の傷、嵐の衣、空駆けの判定 (西の試練では効かない)、弓の矢の倍率、護符、羽根のそらし、干し果
//  - 報酬: 図鑑の抽選・天井・模擬の撃破数、羽守 1%・雷獣 5%、図鑑に記す
//  - 炉: 雷翼の欠片で強化 (弓も)、分解、交換
public final class StormGearSelfTest {
    // 雷翼の心臓 (12 の 5.3節): 防御 9 / 靭性 6
    private static final double HEART_ARMOR = 9;
    private static final double HEART_TOUGHNESS = 6;

    private StormGearSelfTest() {
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
            SkyAbyss.LOGGER.error("storm gear selftest crashed", e);
            f.add("crashed: " + e);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST storm gear: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST storm gear PASSED (table, ids, levels, stats, curve, set/dash/bow/charm/feather/fruit, loot, forge, exchange)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST storm gear FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    private static Item item(String id) {
        return TitanGearSet.item(id).orElseThrow(() -> new IllegalStateException("not registered: " + id));
    }

    // ---------------------------------------------------------------- 表・ID・必要 Lv
    private static void table(List<String> f) {
        check(f, TitanGearSets.get(StormGear.TITAN) == StormGear.SET, "storm set is not registered in TitanGearSets");
        check(f, TitanGearSets.all().get(0) == TitanGearSets.STONE_WHALE, "stone whale should be the first gear set");
        check(f, StormGear.SET.collection().size() == 10 && StormGear.SET.exclusives().size() == 3, "storm collection should be 10 with 3 exclusives");
        for (String id : StormGear.SET.collection()) {
            check(f, TitanGearSet.registered(id), id + " is not registered");
            Item it = item(id);
            check(f, it instanceof ModGear g && g.dungeon().equals(StormGear.TITAN), id + " is not storm gear");
            int want = StormGear.SET.isExclusive(id) ? 22 : 20;
            check(f, GearLevel.required(it) == want, id + " requires Lv " + GearLevel.required(it) + " (want " + want + ")");
            check(f, TitanGearSets.of(it) == StormGear.SET, id + " does not map to the storm set");
        }
        // 素材 (12 の 11.1節) と、ほかの担当の報酬の表が引く ID
        for (String id : List.of(StormGear.SHARD, StormGear.CORE, StormGear.FRUIT, StormGear.DUST)) {
            check(f, TitanGearSet.registered(id) && GearLevel.required(item(id)) == 0, id + " missing or has a level requirement");
        }
        for (String id : List.of("skyabyss:thunder_wing_shard", "skyabyss:cloudgap_fruit", "skyabyss:moth_dust", "skyabyss:raiju_claw",
                "skyabyss:thunder_wing_helmet", "skyabyss:thunder_wing_chestplate", "skyabyss:thunder_wing_leggings", "skyabyss:thunder_wing_boots")) {
            check(f, TitanGearSet.registered(id), "the mob / raiju loot id " + id + " is not registered");
        }
        check(f, BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse(StormGear.STATIC_WARD)), "static ward effect is missing");
        check(f, near(StormGear.SET.chance(StormGear.HELMET), 0.25) && near(StormGear.SET.chance(StormGear.BOW), 0.20)
                && near(StormGear.SET.chance(StormGear.ANKLET), 0.20) && near(StormGear.SET.chance(StormGear.CHARM), 0.20)
                && near(StormGear.SET.chance(StormGear.CLAW), 0.04) && near(StormGear.SET.chance(StormGear.GLAIVE), 0.04)
                && near(StormGear.SET.chance(StormGear.EYE_FEATHER), 0.02), "heart chances do not match 12 6.3");
        check(f, TitanGearSets.shardFor(new ItemStack(item(StormGear.BOW))) == StormGearSetup.SHARD.get(), "the bow should use thunder wing shards");
        // 雷翼の段 (03 の 9.8節)
        GearCurve.Stage s = stage(StormGear.TITAN);
        check(f, s.level() == 24 && near(s.dps0(), 15.2) && near(s.dps10(), 19.8) && near(s.armor(), 21) && near(s.toughness(), 14)
                && near(s.bossHit(), 15.5) && s.bossHp() == 650, "the thunder wing stage differs from 03 9.8");
    }

    // ---------------------------------------------------------------- 品の数値
    private static double speed(Item item) {
        double v = 4;
        for (ItemAttributeModifiers.Entry e : item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().equals(Attributes.ATTACK_SPEED) && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                v += e.modifier().amount();
            }
        }
        return v;
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

    private static void numbers(List<String> f) {
        Item claw = item(StormGear.CLAW);
        Item glaive = item(StormGear.GLAIVE);
        check(f, near(GearEvents.weaponAttack(claw), StormGear.CLAW_ATTACK) && near(speed(claw), StormGear.CLAW_SPEED) && near(reach(claw), 0),
                "claw stats " + GearEvents.weaponAttack(claw) + " / " + speed(claw));
        check(f, near(GearEvents.weaponAttack(glaive), StormGear.GLAIVE_ATTACK) && near(speed(glaive), StormGear.GLAIVE_SPEED)
                && near(reach(glaive), StormGear.GLAIVE_REACH), "glaive stats " + GearEvents.weaponAttack(glaive) + " / " + speed(glaive));
        int defense = 0;
        double toughness = 0;
        double knock = 0;
        for (String id : StormGear.ARMOR) {
            ArmorItem a = (ArmorItem) item(id);
            defense += a.getDefense();
            toughness += a.getToughness();
            knock += a.getMaterial().value().knockbackResistance();
        }
        GearCurve.Stage stage = stage(StormGear.TITAN);
        check(f, defense == stage.armor() && near(toughness, stage.toughness()),
                "armor " + defense + " / " + toughness + " should match the 9.8 stage " + stage.armor() + " / " + stage.toughness());
        check(f, near(knock, 0.2), "knockback resistance per piece should be 0.05");
        check(f, StormGear.SET4_TAKEN_AIR >= stage.setMultiplier() - 1e-9, "set effect goes beyond the x0.8 cap");
        check(f, AffixRules.slotOf(claw) == AffixType.Slot.WEAPON && AffixRules.slotOf(glaive) == AffixType.Slot.WEAPON
                && AffixRules.slotOf(item(StormGear.HELMET)) == AffixType.Slot.ARMOR, "affix slots");
        check(f, AffixRules.slotOf(item(StormGear.BOW)) == null && AffixRules.slotOf(item(StormGear.CHARM)) == null
                && AffixRules.slotOf(item(StormGear.ANKLET)) == null && AffixRules.slotOf(item(StormGear.EYE_FEATHER)) == null,
                "bow, charm and trinkets must not take affixes");
        check(f, ((ModGear) item(StormGear.BOW)).enhanceable() && !((ModGear) item(StormGear.CHARM)).enhanceable(), "enhanceable flags");
    }

    // ---------------------------------------------------------------- 強さの曲線
    static GearCurve.Stage stage(String id) {
        return GearCurve.STAGES.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    public static double attack(String id, int enhance) {
        double a = id.equals(StormGear.CLAW) ? StormGear.CLAW_ATTACK : StormGear.GLAIVE_ATTACK;
        return a * (1 + GearEvents.ATTACK_RATE_PER_LEVEL * enhance);
    }

    // 一体への 1 秒あたり (筋力・相手の防具の前。爪の跳ぶ雷・鉤槍の空中の上乗せは別の相手か場面しだいなので数えない)
    public static double dps(String id, int enhance) {
        if (id.equals(StormGear.BOW)) {
            return ThunderWingBowItem.dps(enhance);
        }
        double speed = id.equals(StormGear.CLAW) ? StormGear.CLAW_SPEED : StormGear.GLAIVE_SPEED;
        return attack(id, enhance) * speed;
    }

    // 雷翼の羽衣で受ける一撃の傷 (enhance: 4 部位の強化、ward: 特性の守り、air: 空中)
    public static double stormHit(double hit, int enhance, double ward, boolean air) {
        double toughness = 4 * StormGear.ARMOR_TOUGHNESS + 4 * GearEvents.TOUGHNESS_PER_LEVEL * enhance;
        double armor = 0;
        for (int d : StormGear.ARMOR_DEFENSE) {
            armor += d;
        }
        return hit * GearCurve.armorTaken(hit, armor, toughness) * (air ? StormGear.SET4_TAKEN_AIR : StormGear.SET4_TAKEN) * (1 - ward);
    }

    private static double dpsOn(double dps, double perHit, double strength) {
        return dps * strength * GearCurve.armorTaken(perHit * strength, HEART_ARMOR, HEART_TOUGHNESS);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void curve(List<String> f, List<String> notes) {
        GearCurve.Stage tide = stage("tide_shell");
        GearCurve.Stage thunder = stage(StormGear.TITAN);
        GearCurve.Stage ember = stage("ember");
        double netherite = GearCurve.netheriteSharpVDps();
        for (String id : List.of(StormGear.CLAW, StormGear.GLAIVE)) {
            double d0 = dps(id, 0);
            double d5 = dps(id, 5);
            double d10 = dps(id, 10);
            notes.add(String.format(Locale.ROOT, "%s: per second +0 %.2f, +5 %.2f, +10 %.2f (netherite+V %.1f)", id, d0, d5, d10, netherite));
            // +0 は潮殻の +0 の 1.1 倍以上・潮殻の +10 以下 (乗り換えの手応え)、雷翼の目安 15.2 の ±5%
            check(f, d0 >= tide.dps0() * 1.1 - 1e-6 && d0 <= tide.dps10() + 1e-6, id + " +0 " + d0 + " is out of the thunder stage range");
            check(f, d0 >= thunder.dps0() * 0.95 && d0 <= thunder.dps0() * 1.05, id + " +0 " + d0 + " is far from the stage dps0 " + thunder.dps0());
            // +10 は雷翼の目安 19.8 を超えない (5% まで)、余燼の +10 を超えない、余燼の +0 の 1.2 倍まで
            check(f, d10 <= thunder.dps10() * 1.05 && d10 < ember.dps10() && d10 <= ember.dps0() * 1.2, id + " +10 " + d10 + " is too strong for ember");
            // +5 は余燼の +0 ほど
            check(f, d5 <= ember.dps0() * 1.08, id + " +5 " + d5 + " goes beyond the ember +0");
        }
        // 弓: 離れて撃てるので近接の目安の 0.8〜0.9 倍。+10 は雷翼の目安 +10 の 0.9 倍まで
        double b0 = dps(StormGear.BOW, 0);
        double b10 = dps(StormGear.BOW, 10);
        notes.add(String.format(Locale.ROOT, "bow: per second +0 %.2f, +10 %.2f (vanilla bow %.2f)", b0, b10, ThunderWingBowItem.fullShotDamage(1) / StormGear.BOW_CYCLE));
        check(f, b0 >= thunder.dps0() * 0.8 && b0 <= thunder.dps0() * 0.9, "bow +0 " + b0 + " should be 0.8-0.9 of the stage dps0");
        check(f, b10 <= thunder.dps10() * 0.9 && b10 > b0 * 1.2, "bow +10 " + b10);

        // 心臓 (650、一撃 15.5、防御 9 / 靭性 6) との手ごたえ (手数 6 割、技の間隔 2.5 秒、推奨 Lv 24)
        int lv = thunder.level();
        double hp = GearCurve.maxHealth(lv);
        double strength = GearCurve.strengthMultiplier(lv);
        double hit0 = stormHit(thunder.bossHit(), 0, 0, false);
        double loadFresh = Double.MAX_VALUE;
        for (String id : List.of(StormGear.CLAW, StormGear.GLAIVE)) {
            loadFresh = Math.min(loadFresh, GearCurve.load(dpsOn(dps(id, 0), attack(id, 0), strength), thunder.bossHp(), 0.6, 2.5, hp, hit0));
        }
        double best = 1.10 * 1.125;
        double strFull = 1 + 0.006 * Math.min(50, 3 * (lv - 1));
        double hitBest = stormHit(thunder.bossHit(), 10, 0.10, true);
        double loadEasy = Double.MAX_VALUE;
        for (String id : List.of(StormGear.CLAW, StormGear.GLAIVE)) {
            // 鉤槍は空駆けや段階 2 の空中戦で空中の上乗せが付く (いちばん楽な形には入れる)
            double proc = id.equals(StormGear.GLAIVE) ? StormGear.GLAIVE_AERIAL_AWAKENED : 1;
            loadEasy = Math.min(loadEasy, GearCurve.load(dpsOn(dps(id, 10) * best * proc, attack(id, 10) * 1.10 * proc, strFull),
                    thunder.bossHp(), 0.6, 2.5, hp + 4, hitBest));
        }
        notes.add("thunder heart load: fresh +0 " + fmt(loadFresh) + ", easiest +10/best affixes/full strength/aerial " + fmt(loadEasy)
                + " (hit " + fmt(hit0) + " / " + fmt(hitBest) + ")");
        check(f, loadFresh >= 3.0, "the thunder heart is too easy right after getting the gear (load " + loadFresh + ")");
        check(f, loadEasy >= 1.3, "the thunder heart becomes a chore with +10 storm gear (load " + loadEasy + ")");
        // 余燼の目安のボスを、雷翼の +10 で: 表どおりの +10 より楽にしない、避けて戦う相手のまま (2.0 以上)
        double h = GearCurve.maxHealth(ember.level());
        double s = GearCurve.strengthMultiplier(ember.level());
        double hitE = stormHit(ember.bossHit(), 10, 0, false);
        double storm10 = Double.MAX_VALUE;
        for (String id : List.of(StormGear.CLAW, StormGear.GLAIVE)) {
            storm10 = Math.min(storm10, GearCurve.load(dpsOn(dps(id, 10) * 1.05, attack(id, 10), s), ember.bossHp(), 0.6, 2.5, h, hitE));
        }
        double hitPlan = ember.bossHit() * GearCurve.armorTaken(ember.bossHit(), thunder.armor(), thunder.toughness() + 12) * thunder.setMultiplier();
        double plan10 = GearCurve.load(dpsOn(thunder.dps10() * 1.05, thunder.dps10() / GearCurve.SWORD_SPEED, s), ember.bossHp(), 0.6, 2.5, h, hitPlan);
        notes.add("ember boss with storm +10: load " + fmt(storm10) + " (9.8 plan " + fmt(plan10) + ")");
        check(f, storm10 >= plan10 * 0.95 && storm10 >= 2.0, "storm +10 makes the ember boss too easy (" + storm10 + ", plan " + plan10 + ")");
        // 守り: 羽衣 +0 は潮殻の鎧 +10 より雷翼の一撃に弱くない (乗り換えで守りが少し上がる)。+10 でも余燼の目安 +0 より硬くない
        double tideOnThunder = jp.revati.skyabyss.titan.tide.gear.TideGearSelfTest.tideHit(thunder.bossHit(), 10, 0, false);
        double tide0OnThunder = jp.revati.skyabyss.titan.tide.gear.TideGearSelfTest.tideHit(thunder.bossHit(), 0, 0, false);
        double emberOwn = ember.bossHit() * GearCurve.armorTaken(ember.bossHit(), ember.armor(), ember.toughness()) * ember.setMultiplier();
        double stormOnEmber = stormHit(ember.bossHit(), 10, 0, true);
        notes.add("thunder heart hit: storm +0 " + fmt(hit0) + " (air " + fmt(stormHit(thunder.bossHit(), 0, 0, true)) + ") vs tide +0 "
                + fmt(tide0OnThunder) + " / tide +10 " + fmt(tideOnThunder) + "; ember hit: storm +10 air " + fmt(stormOnEmber) + " vs ember stage +0 " + fmt(emberOwn));
        check(f, hit0 <= tideOnThunder + 1e-6, "storm armor +0 is weaker than tide armor +10 against the thunder heart");
        check(f, stormOnEmber >= emberOwn * 0.95, "storm armor +10 is harder than the ember stage +0 armor");
    }

    // ---------------------------------------------------------------- 効果
    private static FakePlayer player(ServerLevel level, String name, int lv) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.setPos(0.5, level.getMinBuildHeight() + 2, 0.5);
        Growth.get(p).level = lv;
        return p;
    }

    private static void effects(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(level, "storm_gear_test", 24);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        check(f, near(StormGearEvents.fallMultiplier(p), 1), "fall multiplier without gear");
        for (int i = 0; i < 2; i++) {
            p.setItemSlot(slots[i], new ItemStack(item(StormGear.ARMOR.get(i))));
        }
        check(f, near(StormGearEvents.fallMultiplier(p), 0.5), "2 pieces should halve fall damage");
        check(f, StormGearEvents.setTaken(p) == 1, "2 pieces should not give the storm cloak");
        for (int i = 2; i < 4; i++) {
            p.setItemSlot(slots[i], new ItemStack(item(StormGear.ARMOR.get(i))));
        }
        p.setOnGround(true);
        check(f, near(StormGearEvents.setTaken(p), StormGear.SET4_TAKEN), "4-piece cloak should be x0.85 on the ground");
        p.setOnGround(false);
        check(f, near(StormGearEvents.setTaken(p), StormGear.SET4_TAKEN_AIR), "4-piece cloak should be x0.8 in the air");
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
        check(f, StormGearEvents.setTaken(p) == 1, "the cloak must not stack with resistance");
        p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        // 空駆け: 4 部位・空中・待ちなしで跳べる。地面・待ちの最中・西の試練では跳べない
        check(f, StormGearEvents.canDash(p), "a 4-piece player in the air should be able to dash");
        Vec3 v = StormGearEvents.dashVelocity(p);
        check(f, near(Math.hypot(v.x, v.z), StormGear.DASH_FORWARD) && near(v.y, StormGear.DASH_LIFT), "dash velocity " + v);
        p.setOnGround(true);
        check(f, !StormGearEvents.canDash(p), "dash on the ground");
        p.setOnGround(false);
        p.getCooldowns().addCooldown(StormGearSetup.CHESTPLATE.get(), 10);
        check(f, !StormGearEvents.canDash(p), "dash while resting");
        p.getCooldowns().removeCooldown(StormGearSetup.CHESTPLATE.get());
        p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        check(f, !StormGearEvents.canDash(p), "dash with 3 pieces");
        check(f, StormGear.inWestTrial(serverLevel(server, StormGear.STORM_BACK, level), new Vec3(-70, 145, -18)) == (server.getLevel(StormGear.STORM_BACK) != null),
                "west trial box");
        check(f, !StormGear.inWestTrial(level, new Vec3(-70, 145, -18)), "the west trial box must only be on the storm back");
        for (EquipmentSlot sl : slots) {
            p.setItemSlot(sl, ItemStack.EMPTY);
        }
        // 必要 Lv: Lv 19 で羽衣を着られない、Lv 21 で爪の力が出ない
        FakePlayer low = player(level, "storm_gear_low", 19);
        ItemStack helm = new ItemStack(item(StormGear.HELMET));
        check(f, !helm.getItem().canEquip(helm, EquipmentSlot.HEAD, low), "Lv 19 could equip a Lv 20 helm");
        Growth.get(low).level = 21;
        check(f, !GearLevel.meets(low, new ItemStack(item(StormGear.CLAW))) && GearLevel.meets(low, new ItemStack(item(StormGear.BOW))),
                "Lv 21: claw (22) should be locked, bow (20) usable");
        // 弓の矢: 倍率と印。Lv が足りなければふつうの矢
        ItemStack bow = new ItemStack(item(StormGear.BOW));
        ThunderWingBowItem bowItem = (ThunderWingBowItem) bow.getItem();
        Arrow arrow = new Arrow(level, p, new ItemStack(Items.ARROW), bow);
        double base = arrow.getBaseDamage();
        bowItem.customArrow(arrow, new ItemStack(Items.ARROW), bow);
        check(f, near(arrow.getBaseDamage(), base * StormGear.BOW_DAMAGE) && arrow.getPersistentData().getInt(ThunderWingBowItem.ARROW_TAG) == 1,
                "bow arrow damage " + arrow.getBaseDamage());
        bow.set(ModDataComponents.ENHANCE.get(), 10);
        Arrow arrow10 = new Arrow(level, p, new ItemStack(Items.ARROW), bow);
        bowItem.customArrow(arrow10, new ItemStack(Items.ARROW), bow);
        check(f, near(arrow10.getBaseDamage(), base * StormGear.BOW_DAMAGE * 1.3), "bow +10 arrow damage " + arrow10.getBaseDamage());
        FakePlayer weak = player(level, "storm_gear_weak", 10);
        Arrow plain = new Arrow(level, weak, new ItemStack(Items.ARROW), bow);
        bowItem.customArrow(plain, new ItemStack(Items.ARROW), bow);
        check(f, near(plain.getBaseDamage(), base) && !plain.getPersistentData().contains(ThunderWingBowItem.ARROW_TAG), "Lv 10 bow arrow should be plain");
        // 護符・感電・羽根
        Zombie z = EntityType.ZOMBIE.create(level);
        Zombie z2 = EntityType.ZOMBIE.create(level);
        if (z != null && z2 != null) {
            z.setPos(p.getX() + 2, p.getY(), p.getZ());
            z2.setPos(p.getX() + 3.5, p.getY(), p.getZ());
            level.addFreshEntity(z);
            level.addFreshEntity(z2);
            ItemStack charm = new ItemStack(item(StormGear.CHARM));
            StormCharmItem c = (StormCharmItem) charm.getItem();
            boolean used = c.activate(p, charm);
            check(f, used && StormCharmItem.charges(p) == 3, "charm should arm 3 charges");
            check(f, !c.activate(p, charm) && p.getCooldowns().isOnCooldown(c) && c.cooldownTicks() == 20 * 30, "charm should rest 30 s");
            float hp = z.getHealth();
            StormGearEvents.strike(level, z, p, StormGear.CHARM_DAMAGE);
            check(f, z.getHealth() <= hp - 4.9f, "charm bolt did not hit for 5 (" + (hp - z.getHealth()) + ")");
            StormCharmItem.spend(p);
            StormCharmItem.spend(p);
            StormCharmItem.spend(p);
            StormCharmItem.spend(p);
            check(f, StormCharmItem.charges(p) == 0, "charges should not go below 0");
            float hp2 = z2.getHealth();
            StormGearEvents.shock(level, z, p, false);
            check(f, z.hasEffect(MobEffects.GLOWING) && z2.getHealth() < hp2, "bow shock should glow the target and arc to the next zombie");
            z.discard();
            z2.discard();
        } else {
            f.add("could not create zombies");
        }
        FakePlayer q = player(level, "storm_gear_feather", 24);
        AccessoryData data = Accessories.get(q);
        data.items.set(AccessoryData.CHARM + 1, new ItemStack(item(StormGear.EYE_FEATHER)));
        check(f, Accessories.worn(q, item(StormGear.EYE_FEATHER)), "eye feather in the trinket slot is not worn");
        Arrow incoming = new Arrow(EntityType.ARROW, level);
        incoming.setPos(q.getX() + 4, q.getY() + 1, q.getZ());
        incoming.setDeltaMovement(-1.5, 0, 0);
        level.addFreshEntity(incoming);
        boolean deflected = StormGearEvents.deflect(q);
        check(f, deflected && incoming.getDeltaMovement().x > 0 && q.getCooldowns().isOnCooldown(item(StormGear.EYE_FEATHER)), "eye feather did not deflect the arrow");
        incoming.discard();
        data.items.set(AccessoryData.CHARM + 2, new ItemStack(item(StormGear.ANKLET)));
        check(f, near(StormGearEvents.fallMultiplier(q), StormGear.ANKLET_FALL), "anklet fall multiplier");
        // 干し果: 食べ物と静電避け
        ItemStack fruit = new ItemStack(item(StormGear.FRUIT));
        var food = fruit.get(DataComponents.FOOD);
        check(f, food != null && food.nutrition() == 5 && Math.abs(food.saturation() - 5 * 0.6f * 2) < 1e-3
                && food.effects().stream().anyMatch(e -> e.effect().is(StormGearSetup.STATIC_WARD)), "cloudgap fruit food");
    }

    private static ServerLevel serverLevel(MinecraftServer server, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key, ServerLevel fallback) {
        ServerLevel l = server.getLevel(key);
        return l == null ? fallback : l;
    }

    // ---------------------------------------------------------------- 報酬
    // 何回の心臓で図鑑 10 品が揃うか (12 の 6.4節の前提: 初回は嵐の目の羽根を選ぶ、雷獣も毎回倒す)
    public static double meanClearsToComplete(int runs, long seed) {
        RandomSource random = RandomSource.create(seed);
        long total = 0;
        for (int i = 0; i < runs; i++) {
            TitanMemoryData.Record r = new TitanMemoryData.Record();
            r.owned.add(StormGear.EYE_FEATHER);
            int n = 0;
            while (StormGear.SET.collected(r) < 10 && n < 200) {
                n++;
                if (random.nextFloat() < StormGear.RAIJU_CLAW_CHANCE) {
                    StormGear.SET.record(r, List.of(StormGear.CLAW));
                }
                r.memory++;
                TitanGearSet.Pieces got = StormGear.SET.rollPieces(r, r.memory, random, TitanGearSet::registered);
                StormGear.SET.record(r, got.pieces());
            }
            total += n;
        }
        return total / (double) runs;
    }

    private static void loot(MinecraftServer server, List<String> f, List<String> notes) {
        RandomSource random = RandomSource.create(23);
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        int gear = 0;
        for (int i = 0; i < 400; i++) {
            r.memory++;
            TitanGearSet.Pieces got = StormGear.SET.rollPieces(r, r.memory, random, TitanGearSet::registered);
            gear += got.pieces().size();
            StormGear.SET.record(r, got.pieces());
        }
        check(f, gear > 400 && r.owned.size() == 10, "heart rolls gave " + gear + " gear and recorded " + r.owned.size());
        double mean = meanClearsToComplete(4000, 11);
        notes.add(String.format(Locale.ROOT, "heart: collection in %.1f clears on average (12 6.4 says 13.9)", mean));
        check(f, mean > 10 && mean < 18, "mean clears " + mean);
        int warden = 0;
        int raiju = 0;
        for (int i = 0; i < 20000; i++) {
            warden += StormGearLoot.rollWarden(null, null, random).size();
            raiju += StormGearLoot.rollRaiju(null, null, random).size();
        }
        check(f, warden > 140 && warden < 270, "warden armor drops " + warden + " / 20000 (want about 200)");
        check(f, raiju > 850 && raiju < 1150, "raiju claw drops " + raiju + " / 20000 (want about 1000)");
        UUID who = UUID.randomUUID();
        TitanMemoryData memory = TitanMemoryData.get(server);
        TitanMemoryData.Record rec = memory.record(who, StormGear.TITAN);
        rec.memory = 3;
        List<ItemStack> claw = List.of();
        for (int i = 0; i < 500 && claw.isEmpty(); i++) {
            claw = StormGearLoot.rollRaiju(server, who, random);
        }
        check(f, !claw.isEmpty() && claw.get(0).has(ModDataComponents.AFFIXES.get()), "raiju claw has no affixes");
        check(f, rec.owned.contains(StormGear.CLAW) && rec.memory == 0, "raiju claw was not recorded / memory not reset");
        check(f, memory.peek(who, TitanLoot.STONE_WHALE) == null && memory.peek(who, "tide_shell") == null, "storm loot wrote to another titan's record");
        TitanMemoryData.Record hard = new TitanMemoryData.Record();
        TitanGearSet.Pieces pity = StormGear.SET.rollPieces(hard, TitanLoot.HARD_PITY, random, TitanGearSet::registered);
        check(f, pity.guaranteed() != null && pity.pieces().contains(pity.guaranteed()), "hard pity did not guarantee a piece");
    }

    // ---------------------------------------------------------------- 炉
    private static void forge(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        Item shard = StormGearSetup.SHARD.get();
        ItemStack glaive = new ItemStack(item(StormGear.GLAIVE));
        ItemStack bow = new ItemStack(item(StormGear.BOW));
        check(f, ForgeRules.shardItem(glaive) == shard && ForgeRules.shardItem(bow) == shard, "storm gear should be enhanced with thunder wing shards");
        FakePlayer p = player(level, "storm_gear_forge", 24);
        p.getInventory().add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 64));
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 8));
        check(f, !ForgeRules.enhance(p, bow), "bow was enhanced with stone whale shards");
        p.getInventory().add(new ItemStack(shard, 10));
        check(f, ForgeRules.accepts(bow) && ForgeRules.enhance(p, bow) && ModGear.enhance(bow) == 1, "bow was not enhanced with thunder wing shards");
        check(f, ForgeRules.count(p.getInventory(), shard) == 6, "enhance should take 4 thunder wing shards");
        check(f, ForgeRules.enhance(p, glaive) && ModGear.enhance(glaive) == 1, "glaive enhance");
        int[] y = ForgeRules.dismantleYield(new ItemStack(item(StormGear.CLAW)));
        check(f, y[0] == 10 && y[2] == 2, "claw dismantle yield");
        int[] yb = ForgeRules.dismantleYield(new ItemStack(item(StormGear.BOW)));
        check(f, yb[1] > 0 && yb[2] == 0, "bow dismantle yield " + yb[1] + "/" + yb[2]);
        TitanMemoryData.Record r = TitanMemoryData.get(server).record(p.getUUID(), StormGear.TITAN);
        check(f, GearExchange.state(StormGear.SET, r) == ForgeRules.ExchangeState.NO_CLEAR, "exchange before a thunder heart clear");
        r.clears = 1;
        check(f, GearExchange.state(StormGear.SET, r) == ForgeRules.ExchangeState.READY, "exchange should be ready after a clear");
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_SHARD.get(), 30));
        check(f, !GearExchange.exchange(p, StormGear.SET, StormGear.BOW), "a non-exclusive was traded");
        check(f, GearExchange.exchange(p, StormGear.SET, StormGear.GLAIVE), "glaive exchange failed");
        check(f, r.owned.contains(StormGear.GLAIVE) && r.exchanges == 1 && GearExchange.state(StormGear.SET, r) == ForgeRules.ExchangeState.USED,
                "exchange state after trading");
        check(f, ForgeRules.count(p.getInventory(), item(StormGear.GLAIVE)) == 1, "glaive not handed over");
        check(f, TitanGearSets.indexOf(StormGear.TITAN) > TitanGearSets.indexOf("tide_shell") || TitanGearSets.indexOf("tide_shell") < 0,
                "the storm set should come after the tide set");
    }
}
