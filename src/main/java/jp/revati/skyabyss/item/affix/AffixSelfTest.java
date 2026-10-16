package jp.revati.skyabyss.item.affix;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

// 特性・再鍛造・交換の自己テスト。SKYABYSS_SELFTEST=1 で専用サーバーを起動したときだけ動く (止めるのは TitanBackSelfTest)。
//  - 抽選の分布 (数・種類・値の幅・重なりなし)、保存 (NBT の往復)、古い装備の扱い
//  - 再鍛造 (3 組まで、確定・今のまま・範囲外)、交換の決まり (心臓ごとに一度、未所持だけ、欠片 30)
//  - 修飾子 (部位ごとの ID、値)、守りの上限、冴え
//  - 模擬: 欠片と結晶の集まり方、特性込みで岩鯨の心臓が簡単になりすぎないか
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AffixSelfTest {
    private AffixSelfTest() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") == null) {
            return;
        }
        List<String> failures = new ArrayList<>();
        try {
            run(event.getServer(), failures);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("affix selftest crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST affix PASSED (distribution, counts, values, save, legacy, reforge, exchange, modifiers, ward cap, keen, economy, heart balance)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST affix FAILED: {}", f));
        }
    }

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) {
            failures.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        RandomSource random = RandomSource.create(20261016);
        distribution(f, random);
        save(server, f, random);
        reforge(f, random);
        exchange(f);
        modifiers(server, f, random);
        economy(f, random);
        balance(f, random);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    // ---------------------------------------------------------------- 抽選の分布
    private static void distribution(List<String> f, RandomSource random) {
        int n = 40000;
        for (GearTier tier : new GearTier[]{GearTier.RARE, GearTier.LEGENDARY}) {
            for (AffixType.Slot slot : AffixType.Slot.values()) {
                ItemStack sample = new ItemStack(slot == AffixType.Slot.WEAPON
                        ? (tier == GearTier.LEGENDARY ? ModItems.CAPTAIN_PIKE.get() : ModItems.WHALEBONE_GREATSWORD.get())
                        : ModItems.STONE_WHALE_HELMET.get());
                if (((ModGear) sample.getItem()).tier() != tier) {
                    // 伝説の防具はまだない。数と値の検めだけ、表の上で回す
                    sample = null;
                }
                int[] counts = new int[5];
                Map<AffixType, Integer> seen = new EnumMap<>(AffixType.class);
                Map<AffixType, double[]> range = new EnumMap<>(AffixType.class);
                for (int i = 0; i < n; i++) {
                    List<Affix> list = AffixRules.roll(slot, tier, random);
                    counts[list.size()]++;
                    Set<String> types = new HashSet<>();
                    for (Affix a : list) {
                        AffixType t = a.kind();
                        check(f, t != null && t.slot == slot, "rolled an affix of the wrong slot: " + a.type());
                        if (!types.add(a.type())) {
                            f.add("duplicate affix on one piece: " + a.type());
                        }
                        seen.merge(t, 1, Integer::sum);
                        double[] r = range.computeIfAbsent(t, k -> new double[]{Double.MAX_VALUE, -Double.MAX_VALUE, 0});
                        r[0] = Math.min(r[0], a.value());
                        r[1] = Math.max(r[1], a.value());
                        r[2] += a.value();
                    }
                    if (sample != null && i < 2000) {
                        check(f, AffixRules.valid(sample, list), "a rolled list is not valid: " + list);
                    }
                }
                int min = AffixRules.minCount(tier);
                int max = AffixRules.maxCount(tier);
                double fewer = counts[min] / (double) n;
                check(f, counts[min] + counts[max] == n, tier + " " + slot + " rolled a count outside " + min + "-" + max);
                check(f, Math.abs(fewer - AffixRules.FEWER_CHANCE) < 0.02, tier + " " + slot + " fewer-count share is " + fmt(fewer));
                // 種類の出かた: 重みどおりか (重なりなしの抽選なので、一つ目だけを数える代わりに、全体の順位を見る)
                List<AffixType> pool = AffixRules.pool(slot);
                StringBuilder share = new StringBuilder();
                for (AffixType t : pool) {
                    int c = seen.getOrDefault(t, 0);
                    double[] r = range.get(t);
                    share.append(t.id).append(' ').append(fmt(c * 100.0 / n)).append("% ");
                    check(f, c > 0, tier + " " + slot + " never rolled " + t.id);
                    if (r != null) {
                        boolean legendary = tier == GearTier.LEGENDARY;
                        check(f, Math.abs(r[0] - t.min(legendary)) < 1e-9 && Math.abs(r[1] - t.max(legendary)) < 1e-9,
                                t.id + " values span " + r[0] + ".." + r[1] + ", expected " + t.min(legendary) + ".." + t.max(legendary));
                        double mean = r[2] / c;
                        double mid = (t.min(legendary) + t.max(legendary)) / 2;
                        check(f, Math.abs(mean - mid) < (t.max(legendary) - t.min(legendary)) * 0.05, t.id + " mean " + mean + " is not centred on " + mid);
                    }
                }
                SkyAbyss.LOGGER.info("[selftest] affix {} {}: {} affix {}%, {} affixes {}%; share {}", tier.key, slot,
                        min, fmt(fewer * 100), max, fmt(100 - fewer * 100), share.toString().trim());
            }
        }
        // 重みが大きいほど多く出る (武器: 鋭さ 30 > 渇き 15)
        Map<AffixType, Integer> first = new EnumMap<>(AffixType.class);
        for (int i = 0; i < n; i++) {
            first.merge(AffixRules.roll(AffixType.Slot.WEAPON, GearTier.RARE, random).get(0).kind(), 1, Integer::sum);
        }
        double sharp = first.getOrDefault(AffixType.SHARP, 0) / (double) n;
        double expected = AffixType.SHARP.weight / (double) AffixRules.pool(AffixType.Slot.WEAPON).stream().mapToInt(t -> t.weight).sum();
        check(f, Math.abs(sharp - expected) < 0.015, "first affix share of sharp " + fmt(sharp) + " vs weight " + fmt(expected));
        // 付かない品
        check(f, !AffixRules.eligible(new ItemStack(ModItems.RESONANCE_CHARM.get())), "the charm should not take affixes");
        check(f, !AffixRules.eligible(new ItemStack(ModItems.STONE_WHALE_PLATING.get())), "the plating should not take affixes");
        check(f, !AffixRules.eligible(new ItemStack(ModItems.MOSS_TALISMAN.get())), "the talisman should not take affixes");
        check(f, AffixRules.eligible(new ItemStack(ModItems.WATERWAY_FANG.get())), "the fang should take affixes");
    }

    // ---------------------------------------------------------------- 保存と古い装備
    private static void save(MinecraftServer server, List<String> f, RandomSource random) {
        ItemStack stack = AffixRules.withAffixes(new ItemStack(ModItems.CAPTAIN_PIKE.get()), random);
        stack.set(ModDataComponents.ENHANCE.get(), 4);
        AffixRules.addTrial(stack, random);
        AffixRules.addTrial(stack, random);
        Tag tag = stack.save(server.registryAccess());
        ItemStack back = ItemStack.parse(server.registryAccess(), tag).orElse(ItemStack.EMPTY);
        check(f, ItemStack.isSameItemSameComponents(stack, back), "affixes or offers did not survive an NBT round trip");
        check(f, AffixRules.affixes(back).size() >= 2 && AffixRules.offers(back).size() == 2, "round trip lost data: " + AffixRules.affixes(back));

        // 第1段階の装備 (特性の項目がない) は、持ち物に入ると一度だけ決まる
        ItemStack legacy = new ItemStack(ModItems.STONE_WHALE_CHESTPLATE.get());
        legacy.set(ModDataComponents.ENHANCE.get(), 3);
        check(f, !legacy.has(ModDataComponents.AFFIXES.get()), "a fresh stack already had affixes");
        check(f, AffixRules.ensureRolled(legacy, random) && AffixRules.valid(legacy, AffixRules.affixes(legacy)), "legacy gear was not rolled");
        List<Affix> once = AffixRules.affixes(legacy);
        check(f, !AffixRules.ensureRolled(legacy, random) && once.equals(AffixRules.affixes(legacy)), "rolled affixes were rolled again");
        check(f, ModGear.enhance(legacy) == 3, "rolling affixes changed the enhance level");
        // 付かない品は何も変わらない
        ItemStack charm = new ItemStack(ModItems.RESONANCE_CHARM.get());
        check(f, !AffixRules.ensureRolled(charm, random) && !charm.has(ModDataComponents.AFFIXES.get()), "the charm got affixes");

        // 知らない種類 (将来消したもの) は読み込めて、効かないだけ
        ItemStack odd = new ItemStack(ModItems.WHALEBONE_GREATSWORD.get());
        odd.set(ModDataComponents.AFFIXES.get(), List.of(new Affix("removed_kind", 3)));
        ItemStack oddBack = ItemStack.parse(server.registryAccess(), odd.save(server.registryAccess())).orElse(ItemStack.EMPTY);
        check(f, AffixRules.affixes(oddBack).size() == 1 && AffixRules.value(oddBack, AffixType.SHARP) == 0, "an unknown affix broke loading");

        // 巨神の記憶の交換の項目: 古い記録 (項目なし) は 0、往復で戻る
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        r.clears = 4;
        r.lastExchangeClears = 3;
        r.exchanges = 2;
        TitanMemoryData.Record rb = TitanMemoryData.Record.load(r.save());
        check(f, rb.lastExchangeClears == 3 && rb.exchanges == 2, "exchange fields did not survive a save");
        CompoundTag old = r.save();
        old.remove("lastExchangeClears");
        old.remove("exchanges");
        TitanMemoryData.Record ro = TitanMemoryData.Record.load(old);
        check(f, ro.lastExchangeClears == 0 && ro.exchanges == 0, "an old record did not default its exchange fields");

        // 報酬として出た装備には、はじめから特性が付いている
        TitanMemoryData.Record fresh = new TitanMemoryData.Record();
        int gear = 0;
        for (int i = 0; i < 300; i++) {
            for (ItemStack s : TitanLoot.rollHeart(fresh, random).items()) {
                if (AffixRules.eligible(s)) {
                    gear++;
                    check(f, s.has(ModDataComponents.AFFIXES.get()) && AffixRules.valid(s, AffixRules.affixes(s)), "a dropped piece had no valid affixes");
                }
            }
        }
        check(f, gear > 100, "too few dropped pieces to check affixes: " + gear);
    }

    // ---------------------------------------------------------------- 再鍛造
    private static void reforge(List<String> f, RandomSource random) {
        ItemStack stack = AffixRules.withAffixes(new ItemStack(ModItems.WATERWAY_FANG.get()), random);
        List<Affix> before = AffixRules.affixes(stack);
        check(f, AffixRules.canTrial(stack) && !AffixRules.confirm(stack, -1), "confirm worked without offers");
        List<List<Affix>> made = new ArrayList<>();
        for (int i = 0; i < AffixRules.MAX_TRIALS; i++) {
            made.add(AffixRules.addTrial(stack, random));
        }
        check(f, !AffixRules.canTrial(stack), "a fourth trial was allowed");
        check(f, AffixRules.offers(stack).equals(made), "offers were not kept in order");
        check(f, AffixRules.affixes(stack).equals(before), "trials changed the current affixes before confirming");
        made.forEach(o -> check(f, AffixRules.valid(stack, o), "an offer is not valid: " + o));
        check(f, !AffixRules.confirm(stack, 3) && !AffixRules.confirm(stack, -2), "an out of range choice was accepted");
        check(f, AffixRules.confirm(stack, 1) && AffixRules.affixes(stack).equals(made.get(1)) && AffixRules.offers(stack).isEmpty(),
                "confirming offer 1 did not apply it and clear the offers");
        // 今のまま
        List<Affix> kept = AffixRules.affixes(stack);
        AffixRules.addTrial(stack, random);
        check(f, AffixRules.confirm(stack, -1) && AffixRules.affixes(stack).equals(kept) && !stack.has(ModDataComponents.REFORGE_OFFERS.get()),
                "keeping the current affixes changed them");
        // 確定したら、また 3 組まで試せる
        check(f, AffixRules.canTrial(stack), "trials did not reset after confirming");
        // 付かない品は試せない
        check(f, !AffixRules.canTrial(new ItemStack(ModItems.RESONANCE_CHARM.get())), "the charm could be reforged");
        // 分解で結晶が戻る (重なった装備が再鍛造の燃料になる)
        check(f, ForgeRules.dismantleYield(new ItemStack(ModItems.STONE_WHALE_BOOTS.get()))[2] == ForgeRules.DISMANTLE_CRYSTALS_RARE
                && ForgeRules.dismantleYield(new ItemStack(ModItems.CAPTAIN_PIKE.get()))[2] == ForgeRules.DISMANTLE_CRYSTALS_LEGENDARY
                && ForgeRules.dismantleYield(new ItemStack(ModItems.RESONANCE_CHARM.get()))[2] == 0, "dismantle crystal yield is wrong");
    }

    // ---------------------------------------------------------------- 交換
    private static void exchange(List<String> f) {
        List<Item> ex = TitanLoot.exclusives();
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        check(f, TitanLoot.exchangeState(null) == ForgeRules.ExchangeState.NO_CLEAR, "no record should mean no clear");
        check(f, TitanLoot.exchangeState(r) == ForgeRules.ExchangeState.NO_CLEAR, "exchange allowed before the first heart");
        check(f, "message.skyabyss.exchange.no_clear".equals(TitanLoot.exchangeDenial(r, ex.get(0), true)), "denial before a clear is wrong");
        r.clears = 1;
        r.owned.add(TitanLoot.id(ex.get(0)));
        check(f, TitanLoot.exchangeState(r) == ForgeRules.ExchangeState.READY, "exchange not ready after a clear");
        check(f, "message.skyabyss.exchange.owned".equals(TitanLoot.exchangeDenial(r, ex.get(0), true)), "an owned exclusive could be exchanged");
        check(f, "message.skyabyss.exchange.owned".equals(TitanLoot.exchangeDenial(r, ModItems.STONE_WHALE_HELMET.get(), true)),
                "a non-exclusive could be exchanged");
        check(f, "message.skyabyss.exchange.no_shards".equals(TitanLoot.exchangeDenial(r, ex.get(1), false)), "exchange without shards allowed");
        check(f, TitanLoot.exchangeDenial(r, ex.get(1), true) == null, "a valid exchange was refused");
        // 交換した (TitanLoot.exchange と同じ書き換え)
        r.owned.add(TitanLoot.id(ex.get(1)));
        r.lastExchangeClears = r.clears;
        check(f, TitanLoot.exchangeState(r) == ForgeRules.ExchangeState.USED, "a second exchange in the same run was allowed");
        check(f, "message.skyabyss.exchange.used".equals(TitanLoot.exchangeDenial(r, ex.get(2), true)), "used denial is wrong");
        r.clears++;
        check(f, TitanLoot.exchangeState(r) == ForgeRules.ExchangeState.READY, "exchange did not come back after the next heart");
        // 天井の進みは交換で変わらない (判断の記録どおり)
        r.owned.add(TitanLoot.id(ex.get(2)));
        check(f, TitanLoot.exchangeState(r) == ForgeRules.ExchangeState.ALL_OWNED, "all exclusives owned is not reported");
        check(f, TitanLoot.ownedExclusiveMask(r) == 0b111, "owned mask is wrong: " + TitanLoot.ownedExclusiveMask(r));
        // 何回心臓を倒しても、交換は撃破の回数を超えない
        TitanMemoryData.Record g = new TitanMemoryData.Record();
        int done = 0;
        for (int clear = 1; clear <= 10; clear++) {
            g.clears = clear;
            for (int tries = 0; tries < 3; tries++) {
                if (TitanLoot.exchangeState(g) == ForgeRules.ExchangeState.READY) {
                    g.lastExchangeClears = g.clears;
                    done++;
                }
            }
        }
        check(f, done == 10, "exchange count over 10 clears was " + done + " (want one per clear)");
    }

    // ---------------------------------------------------------------- 修飾子
    private static void modifiers(MinecraftServer server, List<String> f, RandomSource random) {
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "affix_test_a"));
        Item[] armor = {ModItems.STONE_WHALE_HELMET.get(), ModItems.STONE_WHALE_CHESTPLATE.get(), ModItems.STONE_WHALE_LEGGINGS.get(),
                ModItems.STONE_WHALE_BOOTS.get()};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        double health = p.getAttributeValue(Attributes.MAX_HEALTH);
        double tough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        // 同じ属性に同じ ID が二つあると上書きし合う (属性が違えば同じ ID でよい。バニラの防具もそう)
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            ItemStack s = new ItemStack(armor[i]);
            s.set(ModDataComponents.AFFIXES.get(), List.of(new Affix(AffixType.VIGOR, 1.0), new Affix(AffixType.WARD, 0.03)));
            s.set(ModDataComponents.ENHANCE.get(), 2);
            p.setItemSlot(slots[i], s);
            EquipmentSlot slot = slots[i];
            s.forEachModifier(slot, (attribute, modifier) -> {
                if (!ids.add(attribute.getRegisteredName() + "|" + modifier.id())) {
                    f.add("modifier id " + modifier.id() + " is shared between armor pieces");
                }
                AttributeInstance inst = p.getAttribute(attribute);
                if (inst != null) {
                    inst.addTransientModifier(modifier);
                }
            });
        }
        check(f, Math.abs(p.getAttributeValue(Attributes.MAX_HEALTH) - (health + 4)) < 1e-6,
                "four vigor 1.0 pieces gave max health " + p.getAttributeValue(Attributes.MAX_HEALTH));
        double expectTough = tough + 4 * 2.5 + 4 * 2 * jp.revati.skyabyss.item.GearEvents.TOUGHNESS_PER_LEVEL;
        check(f, Math.abs(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - expectTough) < 1e-6,
                "enhance toughness of four pieces did not add up: " + p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) + " vs " + expectTough);
        check(f, Math.abs(AffixEvents.ward(p) - AffixRules.WARD_CAP) < 1e-9, "ward 4 x 3% was not capped at 10%: " + AffixEvents.ward(p));

        // 武器: 鋭さは攻撃力に掛ける。冴えで技の待ちが縮む
        ItemStack sword = new ItemStack(ModItems.WHALEBONE_GREATSWORD.get());
        sword.set(ModDataComponents.AFFIXES.get(), List.of(new Affix(AffixType.SHARP, 0.05), new Affix(AffixType.KEEN, 0.10)));
        p.setItemSlot(EquipmentSlot.MAINHAND, sword);
        double base = p.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        sword.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            AttributeInstance inst = p.getAttribute(attribute);
            if (inst != null) {
                inst.addOrUpdateTransientModifier(modifier);
            }
        });
        double expectDamage = (base + 9) * 1.05;
        check(f, Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - expectDamage) < 1e-6,
                "sharp 5% gave attack " + p.getAttributeValue(Attributes.ATTACK_DAMAGE) + " vs " + expectDamage);
        check(f, AffixEvents.scaleCooldown(p, 160) == 144, "keen 10% gave cooldown " + AffixEvents.scaleCooldown(p, 160));
        p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        check(f, AffixEvents.scaleCooldown(p, 160) == 160, "keen worked without the weapon in hand");
    }

    // ---------------------------------------------------------------- 模擬: 欠片と結晶
    private static void economy(List<String> f, RandomSource random) {
        int players = 4000;
        long runsTo30 = 0;
        long crystalsBy10 = 0;
        long noExchangeAll = 0;
        long withExchangeAll = 0;
        List<Item> ex = TitanLoot.exclusives();
        for (int p = 0; p < players; p++) {
            // 欠片だけを貯めるとき、30 に届く周回 (心臓と番兵長。道中の番兵の 5% は数えない)
            TitanMemoryData.Record r = new TitanMemoryData.Record();
            int shards = 0;
            int crystals = 0;
            int run = 0;
            int reach = 0;
            Set<Item> kept = new HashSet<>();
            while (run < 60 && (reach == 0 || run < 10)) {
                run++;
                List<ItemStack> got = new ArrayList<>(TitanLoot.rollCaptain(r, random).items());
                got.addAll(TitanLoot.rollHeart(r, random).items());
                for (ItemStack s : got) {
                    if (s.is(ModItems.RESONANCE_SHARD.get())) {
                        shards += s.getCount();
                    } else if (s.is(ModItems.RESONANCE_CRYSTAL.get())) {
                        crystals += s.getCount();
                    } else if (s.getItem() instanceof ModGear && !kept.add(s.getItem())) {
                        // 重なった装備は分解する
                        int[] y = ForgeRules.dismantleYield(s);
                        shards += y[0];
                        crystals += y[2];
                    }
                }
                if (reach == 0 && shards >= ForgeRules.EXCHANGE_SHARDS) {
                    reach = run;
                }
                if (run == 10) {
                    crystalsBy10 += crystals;
                }
            }
            runsTo30 += reach == 0 ? 60 : reach;

            // 専用伝説 3 つ: 天井と初回の選択だけ / 交換もする
            noExchangeAll += exclusivesRuns(random, false, ex);
            withExchangeAll += exclusivesRuns(random, true, ex);
        }
        double avgReach = runsTo30 / (double) players;
        double avgCrystals = crystalsBy10 / (double) players;
        double without = noExchangeAll / (double) players;
        double with = withExchangeAll / (double) players;
        SkyAbyss.LOGGER.info("[selftest] affix economy: 30 resonance shards after {} clears on average; {} crystals in the first 10 clears (dismantling duplicates); "
                + "all 3 exclusives in {} clears without exchange, {} with exchange", fmt(avgReach), fmt(avgCrystals), fmt(without), fmt(with));
        check(f, avgReach >= 4 && avgReach <= 12, "30 shards takes " + fmt(avgReach) + " clears (want 4-12)");
        check(f, avgCrystals >= 6, "only " + fmt(avgCrystals) + " crystals in 10 clears (reforging would starve)");
        check(f, with < without, "exchange did not shorten the exclusive hunt (" + fmt(with) + " vs " + fmt(without) + ")");
        check(f, with >= 3, "exchange made exclusives too fast (" + fmt(with) + " clears)");
    }

    // 専用伝説 3 つがそろうまでの心臓の撃破数 (一人)
    private static int exclusivesRuns(RandomSource random, boolean exchange, List<Item> ex) {
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        int shards = 0;
        Set<Item> kept = new HashSet<>();
        for (int run = 1; run <= 200; run++) {
            List<ItemStack> got = new ArrayList<>(TitanLoot.rollCaptain(r, random).items());
            got.addAll(TitanLoot.rollHeart(r, random).items());
            if (run == 1) {
                r.owned.add(TitanLoot.id(ex.get(random.nextInt(ex.size()))));
            }
            for (ItemStack s : got) {
                if (s.is(ModItems.RESONANCE_SHARD.get())) {
                    shards += s.getCount();
                } else if (s.getItem() instanceof ModGear && !kept.add(s.getItem())) {
                    shards += ForgeRules.dismantleYield(s)[0];
                }
            }
            if (exchange && shards >= ForgeRules.EXCHANGE_SHARDS && TitanLoot.exchangeState(r) == ForgeRules.ExchangeState.READY) {
                shards -= ForgeRules.EXCHANGE_SHARDS;
                r.owned.add(TitanLoot.id(TitanLoot.missingExclusives(r).get(0)));
                r.lastExchangeClears = r.clears;
            }
            if (TitanLoot.missingExclusives(r).isEmpty()) {
                return run;
            }
        }
        return 200;
    }

    // ---------------------------------------------------------------- 模擬: 岩鯨の心臓が簡単になりすぎないか
    // 一人、初回クリアのころ (Lv 9、筋力 24 → 攻撃 ×1.144)、武器 +5、甲殻 4 部位 +0、心臓の一撃 12 (02 の 5.3節)。
    //  攻め: 1 秒あたりの傷 = 攻撃力 × 攻撃速度 (毎回振りきる)
    //  守り: 一撃の傷 (防具と強靭、甲殻の耐性 I、守り) と最大体力から「倒れるまでに耐える回数」
    private static void balance(List<String> f, RandomSource random) {
        double str = 1.144;
        double[][] weapons = {{9, 1.0}, {8, 1.1}, {5, 2.2}}; // 武器の上乗せ (素の 1 を足すと 10・9・6)。大剣、鉾、顎牙
        String[] names = {"greatsword", "pike", "fang"};
        GearTier[] tiers = {GearTier.RARE, GearTier.LEGENDARY, GearTier.LEGENDARY};
        double worstAvg = 0;
        double worstMax = 0;
        for (int w = 0; w < weapons.length; w++) {
            double dmg = weapons[w][0] + 5 * jp.revati.skyabyss.item.GearEvents.ATTACK_PER_LEVEL;
            double plain = dps(dmg, weapons[w][1], str, 0, 0);
            double sum = 0;
            int n = 20000;
            for (int i = 0; i < n; i++) {
                List<Affix> list = AffixRules.roll(AffixType.Slot.WEAPON, tiers[w], random);
                sum += dps(dmg, weapons[w][1], str, find(list, AffixType.SHARP), find(list, AffixType.SWIFT)) / plain;
            }
            boolean legendary = tiers[w] == GearTier.LEGENDARY;
            double best = dps(dmg, weapons[w][1], str, AffixType.SHARP.max(legendary), AffixType.SWIFT.max(legendary)) / plain;
            double avg = sum / n;
            worstAvg = Math.max(worstAvg, avg);
            worstMax = Math.max(worstMax, best);
            SkyAbyss.LOGGER.info("[selftest] affix balance {}: heart 450 hp in {}s plain, {}s with average affixes (x{}), {}s at best (x{})",
                    names[w], fmt(450 / plain), fmt(450 / (plain * avg)), fmt(avg), fmt(450 / (plain * best)), fmt(best));
        }
        // 守り: 防具 20、強靭 10、甲殻の耐性 I (×0.8)、体力 20
        double plainHits = 20 / hit(12, 20, 10, 0);
        double sumHits = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            double hp = 20;
            double tough = 10;
            double ward = 0;
            for (int piece = 0; piece < 4; piece++) {
                List<Affix> list = AffixRules.roll(AffixType.Slot.ARMOR, GearTier.RARE, random);
                hp += find(list, AffixType.VIGOR);
                tough += find(list, AffixType.BULWARK);
                ward += find(list, AffixType.WARD);
            }
            sumHits += hp / hit(12, 20, tough, Math.min(AffixRules.WARD_CAP, ward));
        }
        double avgDef = sumHits / n / plainHits;
        // いちばん守りの強い組: どの部位も 頑健 1.0 と 守り 3%
        double bestDef = (20 + 4) / hit(12, 20, 10, AffixRules.WARD_CAP) / plainHits;
        SkyAbyss.LOGGER.info("[selftest] affix balance armor: survives {} heart hits plain, x{} with average affixes, x{} at best (vigor+ward on all 4)",
                fmt(plainHits), fmt(avgDef), fmt(bestDef));
        // 難しさ = (倒すのにかかる時間) ÷ (耐えられる回数)。特性で 1/(攻め×守り) になる
        double avgEase = 1 / (worstAvg * avgDef);
        double bestEase = 1 / (worstMax * bestDef);
        SkyAbyss.LOGGER.info("[selftest] affix balance heart difficulty: x{} with average affixes, x{} at the very best (1.00 = no affixes)",
                fmt(avgEase), fmt(bestEase));
        check(f, worstAvg <= 1.12, "average weapon affixes raise heart dps by x" + fmt(worstAvg) + " (want <= 1.12)");
        check(f, worstMax <= 1.30, "best weapon affixes raise heart dps by x" + fmt(worstMax) + " (want <= 1.30)");
        check(f, avgDef <= 1.15, "average armor affixes raise survival by x" + fmt(avgDef) + " (want <= 1.15)");
        check(f, bestDef <= 1.40, "best armor affixes raise survival by x" + fmt(bestDef) + " (want <= 1.40)");
        check(f, avgEase >= 0.80, "average affixes make the heart x" + fmt(avgEase) + " as hard (want >= 0.80)");
        check(f, bestEase >= 0.60, "perfect affixes make the heart x" + fmt(bestEase) + " as hard (want >= 0.60)");
    }

    private static double find(List<Affix> list, AffixType type) {
        for (Affix a : list) {
            if (a.kind() == type) {
                return a.value();
            }
        }
        return 0;
    }

    private static double dps(double weaponDamage, double speed, double str, double sharp, double swift) {
        // 攻撃力: (素の 1 + 武器) × (1 + 鋭さ) × 筋力。速さ: 武器の速さ × (1 + 迅さ)
        return (1 + weaponDamage) * (1 + sharp) * str * speed * (1 + swift);
    }

    // バニラの防具の式 (CombatRules) に、甲殻の耐性 I と守りを掛けた一撃の傷
    private static double hit(double damage, double armor, double toughness, double ward) {
        double f = 2 + toughness / 4;
        double eff = Math.max(armor * 0.2, Math.min(20, armor - damage / f));
        return damage * (1 - eff / 25) * 0.8 * (1 - ward);
    }
}
