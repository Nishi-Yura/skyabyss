package jp.revati.skyabyss.titan.tide.gear;

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
import jp.revati.skyabyss.titan.tide.heart.TideLoot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 潮殻の装備の自己テスト (SKYABYSS_SELFTEST=1、SELFTEST tide gear PASSED / FAILED)。03 の 11.8節の表と同じ検査:
//  - 品の登録、図鑑の表 (10 品、確率)、必要 Lv (エピック以下 14・ヒロイック以上 16)、品の数値 (攻撃力・速さ・間合い・防御・強靭)
//  - 強さの曲線: +0 / +10 の 1 秒あたりが 03 の 9.8節の段に入るか、潮殻の心臓・雷翼・余燼の目安のボスとの手ごたえ、守り
//  - 一式の効果、盾、護符、真珠、必要 Lv の打ち消し (左手の盾)
//  - 報酬: 心臓の抽選 (作られた品だけ・特性つき)、道中と潮招きの確率、図鑑に記す、天井、模擬の撃破数
//  - 炉: 潮殻の殻片で強化・分解、交換 (巨神ごと)
public final class TideGearSelfTest {
    private TideGearSelfTest() {
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
            SkyAbyss.LOGGER.error("tide gear selftest crashed", e);
            f.add("crashed: " + e);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST tide gear: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tide gear PASSED (table, levels, stats, curve, set/shield/charm/pearl, loot, forge, exchange)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST tide gear FAILED: {}", x));
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

    // ---------------------------------------------------------------- 表と必要 Lv
    private static void table(List<String> f) {
        check(f, TitanGearSets.get(TideGear.TITAN) == TideGear.SET, "tide set is not registered in TitanGearSets");
        check(f, TitanGearSets.all().get(0) == TitanGearSets.STONE_WHALE, "stone whale should be the first gear set");
        check(f, TideGear.SET.collection().size() == 10 && TideGear.SET.exclusives().size() == 3, "tide collection should be 10 with 3 exclusives");
        check(f, TideLoot.collection().equals(TideGear.SET.collection()), "TideLoot does not read the tide gear table");
        for (String id : TideGear.SET.collection()) {
            check(f, TitanGearSet.registered(id) && TideLoot.registered(id), id + " is not registered");
            Item it = item(id);
            check(f, it instanceof ModGear g && g.dungeon().equals(TideGear.TITAN), id + " is not tide gear");
            int want = TideGear.SET.isExclusive(id) ? 16 : 14;
            check(f, GearLevel.required(it) == want, id + " requires Lv " + GearLevel.required(it) + " (want " + want + ")");
            check(f, TitanGearSets.of(it) == TideGear.SET, id + " does not map to the tide set");
        }
        check(f, TitanGearSet.registered(TideGear.CORE) && GearLevel.required(item(TideGear.CORE)) == 0, "core missing or has a level requirement");
        check(f, Math.abs(TideGear.SET.chance(TideGear.HELMET) - 0.25f) < 1e-6 && Math.abs(TideGear.SET.chance(TideGear.PEARL) - 0.02f) < 1e-6,
                "heart chances do not match 08 6.3");
        // 岩鯨の表は TitanLoot の図鑑と同じ
        List<String> whale = TitanLoot.collection().stream().map(TitanLoot::id).toList();
        check(f, whale.equals(TitanGearSets.STONE_WHALE.collection()), "stone whale gear set differs from TitanLoot.collection()");
        for (Item i : TitanLoot.collection()) {
            check(f, Math.abs(TitanGearSets.STONE_WHALE.chance(TitanLoot.id(i)) - TitanLoot.heartChance(i)) < 1e-6, "stone whale chance differs for " + i);
        }
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

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    private static void numbers(List<String> f) {
        Item trident = item(TideGear.TRIDENT);
        Item claw = item(TideGear.CLAW);
        Item harpoon = item(TideGear.HARPOON);
        check(f, near(GearEvents.weaponAttack(trident), TideGear.TRIDENT_ATTACK) && near(speed(trident), TideGear.TRIDENT_SPEED)
                && near(reach(trident), TideGear.TRIDENT_REACH), "trident stats " + GearEvents.weaponAttack(trident) + " / " + speed(trident));
        check(f, near(GearEvents.weaponAttack(claw), TideGear.CLAW_ATTACK) && near(speed(claw), TideGear.CLAW_SPEED) && near(reach(claw), 0),
                "claw stats " + GearEvents.weaponAttack(claw) + " / " + speed(claw));
        check(f, near(GearEvents.weaponAttack(harpoon), TideGear.HARPOON_ATTACK) && near(speed(harpoon), TideGear.HARPOON_SPEED)
                && near(reach(harpoon), TideGear.HARPOON_REACH), "harpoon stats " + GearEvents.weaponAttack(harpoon) + " / " + speed(harpoon));
        int defense = 0;
        double toughness = 0;
        for (String id : TideGear.ARMOR) {
            ArmorItem a = (ArmorItem) item(id);
            defense += a.getDefense();
            toughness += a.getToughness();
        }
        GearCurve.Stage stage = stage(TideGear.TITAN);
        check(f, defense == stage.armor() && near(toughness + TideGear.SET2_TOUGHNESS, stage.toughness()),
                "armor " + defense + " / " + toughness + " (+set " + TideGear.SET2_TOUGHNESS + ") should match the 9.8 stage " + stage.armor() + " / " + stage.toughness());
        check(f, TideGear.SET4_TAKEN_WET >= stage.setMultiplier() - 1e-9, "set effect goes beyond the x0.8 cap");
        check(f, near(CoralShieldItem.guard(new ItemStack(item(TideGear.SHIELD))), 0.10), "shield guard at +0 should be 10%");
        ItemStack shield10 = new ItemStack(item(TideGear.SHIELD));
        shield10.set(ModDataComponents.ENHANCE.get(), 10);
        check(f, near(CoralShieldItem.guard(shield10), 0.20) && near(CoralShieldItem.thorns(shield10), 4), "shield +10 guard/thorns");
        check(f, AffixRules.slotOf(item(TideGear.SHIELD)) == AffixType.Slot.ARMOR, "coral shield should take armor affixes");
        check(f, AffixRules.slotOf(item(TideGear.CHARM)) == null && AffixRules.slotOf(item(TideGear.PEARL)) == null, "charm and pearl must not take affixes");
        check(f, AffixRules.slotOf(trident) == AffixType.Slot.WEAPON && AffixRules.slotOf(item(TideGear.HELMET)) == AffixType.Slot.ARMOR, "affix slots");
    }

    // ---------------------------------------------------------------- 強さの曲線 (03 の 9章の式)
    static GearCurve.Stage stage(String id) {
        return GearCurve.STAGES.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    // 一撃の攻撃力 (強化込み)
    public static double attack(String id, int enhance) {
        double mult = 1 + GearEvents.ATTACK_RATE_PER_LEVEL * enhance;
        double a = id.equals(TideGear.TRIDENT) ? TideGear.TRIDENT_ATTACK : id.equals(TideGear.CLAW) ? TideGear.CLAW_ATTACK : TideGear.HARPOON_ATTACK;
        return a * mult;
    }

    // 一体への 1 秒あたり (筋力・相手の防具の前、固有効果の上乗せ込み)
    public static double dps(String id, int enhance) {
        double mult = 1 + GearEvents.ATTACK_RATE_PER_LEVEL * enhance;
        if (id.equals(TideGear.TRIDENT)) {
            return TideGear.TRIDENT_ATTACK * mult * TideGear.TRIDENT_SPEED;
        }
        if (id.equals(TideGear.CLAW)) {
            int every = enhance >= ModGear.AWAKEN_LEVEL ? 2 : 3;
            return TideGear.CLAW_ATTACK * mult * TideGear.CLAW_SPEED * (1 + TideGear.CLAW_LINE_RATE / every);
        }
        return TideGear.HARPOON_ATTACK * mult * TideGear.HARPOON_SPEED;
    }

    // 潮殻の鎧で受ける一撃の傷 (enhance: 4 部位の強化、ward: 特性の守り、wet: 水に触れている)
    public static double tideHit(double hit, int enhance, double ward, boolean wet) {
        double toughness = 4 * TideGear.ARMOR_TOUGHNESS + TideGear.SET2_TOUGHNESS + 4 * GearEvents.TOUGHNESS_PER_LEVEL * enhance;
        double taken = GearCurve.armorTaken(hit, 20, toughness) * (wet ? TideGear.SET4_TAKEN_WET : TideGear.SET4_TAKEN) * (1 - ward);
        return hit * taken;
    }

    private static void curve(List<String> f, List<String> notes) {
        GearCurve.Stage whale = stage("stone_whale");
        GearCurve.Stage tide = stage(TideGear.TITAN);
        GearCurve.Stage thunder = stage("thunder_wing");
        GearCurve.Stage ember = stage("ember");
        double netherite = GearCurve.netheriteSharpVDps();
        for (String id : List.of(TideGear.TRIDENT, TideGear.CLAW, TideGear.HARPOON)) {
            double d0 = dps(id, 0);
            double d5 = dps(id, 5);
            double d10 = dps(id, 10);
            notes.add(String.format("%s: per second +0 %.2f, +5 %.2f, +10 %.2f (netherite+V %.1f)", id, d0, d5, d10, netherite));
            // +0 は岩鯨の +0 の 1.1 倍以上で、岩鯨の +10 以下 (乗り換えの手応え。9.9節の決まり)
            check(f, d0 >= whale.dps0() * 1.1 - 1e-6 && d0 <= whale.dps10() + 1e-6, id + " +0 " + d0 + " is out of the tide stage range");
            // +0 は潮殻の目安 13.2 の ±5% 前後
            check(f, d0 >= tide.dps0() * 0.95 && d0 <= tide.dps0() * 1.05, id + " +0 " + d0 + " is far from the stage dps0 " + tide.dps0());
            // +10 はネザライト + 鋭さ V の 1.05 倍まで、雷翼の +10 を超えない、雷翼の +0 の 1.2 倍を超えない
            check(f, d10 <= netherite * 1.05 && d10 < thunder.dps10() && d10 <= thunder.dps0() * 1.2,
                    id + " +10 " + d10 + " is too strong for the next titans");
            // +5 は雷翼の +0 ほど (次の巨神の +0 が前の巨神の +5 ほど)
            check(f, d5 <= thunder.dps0() * 1.08, id + " +5 " + d5 + " goes beyond the thunder +0");
        }
        // 心臓との手ごたえ (9.1節: 手数 6 割、技の間隔 2.5 秒)。体力: 推奨 Lv の想定
        int lv = tide.level();
        double hp = GearCurve.maxHealth(lv);
        double strength = GearCurve.strengthMultiplier(lv);
        double hit0 = tideHit(tide.bossHit(), 0, 0, false);
        double loadFresh = GearCurve.load(dpsOn(dps(TideGear.TRIDENT, 0), attack(TideGear.TRIDENT, 0), strength), tide.bossHp(), 0.6, 2.5, hp, hit0);
        // いちばん楽な形: +10、最良の特性 (ヒロイックの鋭さ 10%・迅さ 12.5%、防具の頑健 1.0 ×4・守り 10%)、筋力に全部 (Lv 18 で 51 点 → ×1.3)
        double best = 1.10 * 1.125;
        double strFull = 1 + 0.006 * Math.min(50, 3 * (lv - 1));
        double hitBest = tideHit(tide.bossHit(), 10, 0.10, true);
        double loadEasy = Double.MAX_VALUE;
        for (String id : List.of(TideGear.TRIDENT, TideGear.CLAW, TideGear.HARPOON)) {
            loadEasy = Math.min(loadEasy, GearCurve.load(dpsOn(dps(id, 10) * best, attack(id, 10) * 1.10, strFull), tide.bossHp(), 0.6, 2.5, hp + 4, hitBest));
        }
        notes.add(String.format("tide heart load: fresh trident+0 %.2f, easiest +10/best affixes/full strength %.2f (hit %.2f / %.2f)",
                loadFresh, loadEasy, hit0, hitBest));
        check(f, loadFresh >= 3.0, "the tide heart is too easy right after getting the gear (load " + loadFresh + ")");
        check(f, loadEasy >= 1.3, "the tide heart becomes a chore with +10 tide gear (load " + loadEasy + ")");
        // 雷翼・余燼の目安のボスを、潮殻の +10 で挑んだとき (推奨 Lv、特性は平均 ×1.05)
        for (GearCurve.Stage next : List.of(thunder, ember)) {
            double h = GearCurve.maxHealth(next.level());
            double s = GearCurve.strengthMultiplier(next.level());
            double hitT = tideHit(next.bossHit(), 10, 0, false);
            double tide10 = Double.MAX_VALUE;
            for (String id : List.of(TideGear.TRIDENT, TideGear.CLAW, TideGear.HARPOON)) {
                tide10 = Math.min(tide10, GearCurve.load(dpsOn(dps(id, 10) * 1.05, attack(id, 10), s), next.bossHp(), 0.6, 2.5, h, hitT));
            }
            // その段の +0 の目安 (武器 dps0、防具 armor / toughness ×一式) と比べる
            double hitN = next.bossHit() * GearCurve.armorTaken(next.bossHit(), next.armor(), next.toughness()) * next.setMultiplier();
            double own0 = GearCurve.load(dpsOn(next.dps0(), next.dps0() / GearCurve.SWORD_SPEED, s), next.bossHp(), 0.6, 2.5, h, hitN);
            // 9.8節の表どおりの潮殻 +10 (武器 dps10、防具 +10 は強靭 +12) で挑んだとき。前の巨神の +10 は次の巨神の +0 より少し強いのが表の作り
            double hitPlan = next.bossHit() * GearCurve.armorTaken(next.bossHit(), tide.armor(), tide.toughness() + 12) * tide.setMultiplier();
            double plan10 = GearCurve.load(dpsOn(tide.dps10() * 1.05, tide.dps10() / GearCurve.SWORD_SPEED, s), next.bossHp(), 0.6, 2.5, h, hitPlan);
            notes.add(String.format("%s boss with tide +10: load %.2f (9.8 plan for tide +10: %.2f, that titan's own +0 gear: %.2f)",
                    next.id(), tide10, plan10, own0));
            // 潮殻の +10 は、表の潮殻 +10 より楽にしない (5% まで)。どの場合も避けて戦う相手のまま (2.0 以上)
            check(f, tide10 >= plan10 * 0.95 && tide10 >= 2.0,
                    next.id() + ": tide +10 makes the boss too easy (" + tide10 + ", plan " + plan10 + ", own +0 " + own0 + ")");
        }
        // 守り: 潮殻の鎧 +0 は、岩鯨の甲殻 +0 (耐性 I) より潮殻の一撃に弱くない。+10 でも雷翼の目安 +0 より硬くない
        double whaleHit = tide.bossHit() * GearCurve.armorTaken(tide.bossHit(), whale.armor(), whale.toughness()) * GearCurve.RESISTANCE_I;
        double thunderOwn = thunder.bossHit() * GearCurve.armorTaken(thunder.bossHit(), thunder.armor(), thunder.toughness()) * thunder.setMultiplier();
        double tideOnThunder = tideHit(thunder.bossHit(), 10, 0, false);
        notes.add(String.format("tide heart hit: tide armor +0 %.2f (wet %.2f) vs stone whale shell +0 %.2f; thunder hit: tide +10 %.2f vs thunder stage +0 %.2f",
                hit0, tideHit(tide.bossHit(), 0, 0, true), whaleHit, tideOnThunder, thunderOwn));
        check(f, hit0 <= whaleHit + 1e-6, "tide armor +0 is weaker than the stone whale shell against the tide heart");
        check(f, tideOnThunder >= thunderOwn * 0.95, "tide armor +10 is harder than the thunder stage +0 armor");
    }

    // 心臓 (防具 8 / 強靭 6。08 の 19.3節) の防具を通した 1 秒あたり。perHit は一撃の攻撃力
    private static double dpsOn(double dps, double perHit, double strength) {
        return dps * strength * GearCurve.armorTaken(perHit * strength, 8, 6);
    }

    // ---------------------------------------------------------------- 効果
    private static FakePlayer player(ServerLevel level, String name, int lv) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        // 世界の出現地点の下 (読み込まれたままのチャンク)。(0, 0) は出現地点が遠い世界では読み込まれず、護符がゾンビを見つけられない
        net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
        p.setPos(spawn.getX() + 0.5, level.getMinBuildHeight() + 2, spawn.getZ() + 0.5);
        Growth.get(p).level = lv;
        return p;
    }

    private static void tick(FakePlayer p, int tickCount) {
        p.tickCount = tickCount;
        TideGearEvents.onPlayerTick(new PlayerTickEvent.Post(p));
    }

    private static void effects(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(level, "tide_gear_test", 20);
        net.minecraft.world.entity.ai.attributes.AttributeInstance tough = p.getAttribute(Attributes.ARMOR_TOUGHNESS);
        // 一式
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 2; i++) {
            p.setItemSlot(slots[i], new ItemStack(item(TideGear.ARMOR.get(i))));
        }
        tick(p, 5);
        AttributeModifier set2 = tough.getModifier(TideGearEvents.SET2);
        check(f, set2 != null && near(set2.amount(), TideGear.SET2_TOUGHNESS), "2-piece toughness bonus is missing");
        check(f, TideGearEvents.setTaken(p) == 1, "2 pieces should not give the shell");
        for (int i = 2; i < 4; i++) {
            p.setItemSlot(slots[i], new ItemStack(item(TideGear.ARMOR.get(i))));
        }
        check(f, near(TideGearEvents.setTaken(p), TideGear.SET4_TAKEN), "4-piece shell should be x0.85 when dry");
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
        check(f, TideGearEvents.setTaken(p) == 1, "the shell must not stack with resistance");
        p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        for (EquipmentSlot s : slots) {
            p.setItemSlot(s, ItemStack.EMPTY);
        }
        tick(p, 5);
        check(f, tough.getModifier(TideGearEvents.SET2) == null, "set toughness was not removed");
        // 必要 Lv: Lv 13 で鎧は着られない、左手の盾の特性は打ち消される
        FakePlayer low = player(level, "tide_gear_low", 13);
        ItemStack helm = new ItemStack(item(TideGear.HELMET));
        check(f, !helm.getItem().canEquip(helm, EquipmentSlot.HEAD, low), "Lv 13 could equip a Lv 14 helm");
        ItemStack shield = new ItemStack(item(TideGear.SHIELD));
        AffixRules.withAffixes(shield, RandomSource.create(5));
        List<jp.revati.skyabyss.item.affix.Affix> fixed = List.of(new jp.revati.skyabyss.item.affix.Affix(AffixType.BULWARK, 1.0));
        shield.set(ModDataComponents.AFFIXES.get(), fixed);
        low.setItemSlot(EquipmentSlot.OFFHAND, shield);
        GearLevel.updateLock(low);
        net.minecraft.world.entity.ai.attributes.AttributeInstance lowTough = low.getAttribute(Attributes.ARMOR_TOUGHNESS);
        double locked = lowTough.getModifiers().stream().filter(m -> m.id().getPath().startsWith("gear_lock/off/")).mapToDouble(AttributeModifier::amount).sum();
        check(f, near(locked, -1.0), "Lv 13 should have the shield's bulwark cancelled (lock " + locked + ")");
        Growth.get(low).level = 14;
        GearLevel.updateLock(low);
        check(f, lowTough.getModifiers().stream().noneMatch(m -> m.id().getPath().startsWith("gear_lock/off/")), "Lv 14 still has the shield lock");
        low.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        GearLevel.updateLock(low);
        // 護符: 近くのゾンビに傷と鈍足、待ち
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z != null) {
            z.setPos(p.getX() + 2, p.getY(), p.getZ());
            level.addFreshEntity(z);
            ItemStack charm = new ItemStack(item(TideGear.CHARM));
            TideChimeCharmItem c = (TideChimeCharmItem) charm.getItem();
            float hp = z.getHealth();
            boolean used = c.activate(p, charm);
            check(f, used && z.getHealth() < hp && z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "charm did not hit the zombie");
            check(f, !c.activate(p, charm) && p.getCooldowns().isOnCooldown(c), "charm should rest after use");
            check(f, c.cooldownTicks() == 20 * 45, "charm cooldown");
            z.discard();
        } else {
            f.add("could not create a zombie");
        }
        // 真珠: 枠に付けていて体力が 30% を切ると、吸収と待ち
        FakePlayer q = player(level, "tide_gear_pearl", 20);
        AccessoryData data = Accessories.get(q);
        data.items.set(AccessoryData.CHARM + 1, new ItemStack(item(TideGear.PEARL)));
        check(f, Accessories.worn(q, item(TideGear.PEARL)), "pearl in the trinket slot is not worn");
        TideGearEvents.pearlShield(q);
        check(f, q.hasEffect(MobEffects.ABSORPTION) && q.getCooldowns().isOnCooldown(item(TideGear.PEARL)), "pearl shield");
    }

    // ---------------------------------------------------------------- 報酬
    private static void loot(MinecraftServer server, List<String> f, List<String> notes) {
        RandomSource random = RandomSource.create(17);
        // 心臓: 作られた品が出る (図鑑の品が全部登録されている今は、available = registered で模擬)
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        int gear = 0;
        for (int i = 0; i < 400; i++) {
            TideLoot.Roll roll = TideLoot.rollHeart(r, random, TideLoot::registered);
            gear += (int) roll.draws().stream().filter(TideLoot.Draw::gear).count();
        }
        check(f, gear > 400 && r.owned.size() == 10, "heart rolls gave " + gear + " gear and recorded " + r.owned.size());
        double mean = TideLoot.meanClearsToComplete(4000, 11, TideLoot::registered);
        notes.add(String.format("heart: collection in %.1f clears on average (08 6.4 says 15.9)", mean));
        check(f, mean > 13 && mean < 20, "mean clears " + mean);
        // 道中 1%、潮招き 5%
        int thrall = 0;
        int beckoner = 0;
        for (int i = 0; i < 20000; i++) {
            thrall += TideGearLoot.rollThrall(null, null, random).size();
            beckoner += TideGearLoot.rollBeckoner(null, null, random).size();
        }
        check(f, thrall > 140 && thrall < 270, "thrall armor drops " + thrall + " / 20000 (want about 200)");
        check(f, beckoner > 850 && beckoner < 1150, "beckoner claw drops " + beckoner + " / 20000 (want about 1000)");
        // 図鑑に記す・特性・記憶
        UUID who = UUID.randomUUID();
        TitanMemoryData memory = TitanMemoryData.get(server);
        TitanMemoryData.Record rec = memory.record(who, TideGear.TITAN);
        rec.memory = 3;
        List<ItemStack> claw = List.of();
        for (int i = 0; i < 500 && claw.isEmpty(); i++) {
            claw = TideGearLoot.rollBeckoner(server, who, random);
        }
        check(f, !claw.isEmpty() && claw.get(0).has(ModDataComponents.AFFIXES.get()), "beckoner claw has no affixes");
        check(f, rec.owned.contains(TideGear.CLAW) && rec.memory == 0, "beckoner claw was not recorded / memory not reset");
        check(f, memory.peek(who, TitanLoot.STONE_WHALE) == null, "tide loot wrote to the stone whale record");
        // 天井 (共通の表の抽選)
        TitanMemoryData.Record hard = new TitanMemoryData.Record();
        TitanGearSet.Pieces pity = TideGear.SET.rollPieces(hard, TitanLoot.HARD_PITY, random, TitanGearSet::registered);
        check(f, pity.guaranteed() != null && pity.pieces().contains(pity.guaranteed()), "hard pity did not guarantee a piece");
    }

    // ---------------------------------------------------------------- 炉
    private static void forge(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        ItemStack trident = new ItemStack(item(TideGear.TRIDENT));
        check(f, ForgeRules.shardItem(trident) == TideGearSetup.shard(), "trident should be enhanced with tide shell flakes");
        check(f, ForgeRules.shardItem(new ItemStack(ModItems.WHALEBONE_GREATSWORD.get())) == ModItems.STONE_WHALE_SHARD.get(), "greatsword shard");
        FakePlayer p = player(level, "tide_gear_forge", 20);
        p.getInventory().add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 64));
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 8));
        check(f, !ForgeRules.enhance(p, trident), "trident was enhanced with stone whale scales");
        p.getInventory().add(new ItemStack(TideGearSetup.shard(), 10));
        check(f, ForgeRules.enhance(p, trident) && ModGear.enhance(trident) == 1, "trident was not enhanced with tide shell flakes");
        check(f, ForgeRules.count(p.getInventory(), TideGearSetup.shard()) == 6, "enhance should take 4 tide shell flakes");
        ItemStack shield = new ItemStack(item(TideGear.SHIELD));
        check(f, ForgeRules.canEnhance(shield) && ForgeRules.accepts(shield), "the coral shield should be enhanceable");
        int[] y = ForgeRules.dismantleYield(new ItemStack(item(TideGear.CLAW)));
        check(f, y[0] == 10 && y[2] == 2, "claw dismantle yield");
        // 交換 (巨神ごと)
        TitanMemoryData.Record r = TitanMemoryData.get(server).record(p.getUUID(), TideGear.TITAN);
        check(f, GearExchange.state(TideGear.SET, r) == ForgeRules.ExchangeState.NO_CLEAR, "exchange before a tide heart clear");
        r.clears = 1;
        check(f, GearExchange.state(TideGear.SET, r) == ForgeRules.ExchangeState.READY, "exchange should be ready after a clear");
        // 共鳴の欠片は巨神ごと: 岩鯨の欠片 30 では潮殻の専用の逸品は買えない (払われもしない)
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_SHARD_STONE_WHALE.get(), 30));
        check(f, !GearExchange.exchange(p, TideGear.SET, TideGear.PEARL) && ForgeRules.count(p.getInventory(), ModItems.RESONANCE_SHARD_STONE_WHALE.get()) == 30
                && !r.owned.contains(TideGear.PEARL), "stone whale resonance fragments paid for a tide exclusive");
        p.getInventory().clearContent();
        // 潮殻の欠片 20 と古い共通の欠片 10 で買える (古い欠片は、どの巨神の分にも数える。潮殻の欠片から先に払う)
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_SHARD_TIDE_SHELL.get(), 20));
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_SHARD.get(), 10));
        check(f, ForgeRules.resonanceHeld(p.getInventory(), TideGear.TITAN) == 30 && ForgeRules.resonanceHeld(p.getInventory(), TitanLoot.STONE_WHALE) == 10,
                "the old generic fragments should count for every titan, the tide ones only for tide");
        check(f, !GearExchange.exchange(p, TideGear.SET, TideGear.TRIDENT), "a non-exclusive was traded");
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_SHARD_TIDE_SHELL.get(), 10));
        check(f, GearExchange.exchange(p, TideGear.SET, TideGear.PEARL), "pearl exchange failed");
        check(f, r.owned.contains(TideGear.PEARL) && r.exchanges == 1 && GearExchange.state(TideGear.SET, r) == ForgeRules.ExchangeState.USED,
                "exchange state after trading");
        check(f, ForgeRules.count(p.getInventory(), item(TideGear.PEARL)) == 1, "pearl not handed over");
        // 払ったのは潮殻の欠片 30 (40 のうち)。古い共通の欠片は手つかずで残る
        check(f, ForgeRules.count(p.getInventory(), ModItems.RESONANCE_SHARD_TIDE_SHELL.get()) == 0 && ForgeRules.count(p.getInventory(), ModItems.RESONANCE_SHARD.get()) == 10,
                "the exchange should take the tide fragments first: tide " + ForgeRules.count(p.getInventory(), ModItems.RESONANCE_SHARD_TIDE_SHELL.get())
                        + " old " + ForgeRules.count(p.getInventory(), ModItems.RESONANCE_SHARD.get()));
        TitanMemoryData.Record whale = TitanMemoryData.get(server).peek(p.getUUID(), TitanLoot.STONE_WHALE);
        check(f, whale == null || whale.exchanges == 0, "tide exchange touched the stone whale record");
        check(f, GearExchange.ownedMask(TideGear.SET, r) == 0b100, "owned mask " + GearExchange.ownedMask(TideGear.SET, r));
    }
}
