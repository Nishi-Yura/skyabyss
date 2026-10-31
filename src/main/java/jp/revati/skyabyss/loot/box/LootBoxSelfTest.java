package jp.revati.skyabyss.loot.box;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.LegendGear;
import jp.revati.skyabyss.abyss.gear.AbyssGear;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.HeroicGear;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.wild.WildGearSetup;
import jp.revati.skyabyss.item.wild.WildGearTable;
import jp.revati.skyabyss.item.wild.WildGearTypes;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.pouch.PouchConfig;
import jp.revati.skyabyss.pouch.PouchEntries;
import jp.revati.skyabyss.pouch.PouchRouting;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

// 封の箱の自己テスト (SKYABYSS_SELFTEST=1、段 "loot boxes")。docs/design/52
//  - 品: 19 種が登録され、16 まで重なり、旅嚢の「封の箱」の区分 (袋の枠には並ばない)。絵・モデル・形のメッシュ
//  - どの箱か: 野の装備 → 階級と帯の箱、巨神の図鑑・章・ヒロイックの武器 → その巨神の箱、伝説・アーティファクト → 金の箱、
//    深淵の装備 → 階級の箱。神器・コモン・素材は箱にならない
//  - 表: 19 種とも出うる品があり、どれも同じ箱に戻る。巨神の箱の階級の割合 (45/45/10)、階級の箱の種類の割合 (60/25/15)、職への寄せ
//  - 封の列: 包むと箱になり、重なり、古い順に出る。列が空なら表から引く。渡し口 (TitanLoot.handOver) が包み、direct と keepDirect は包まない
public final class LootBoxSelfTest {
    private LootBoxSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            items(f);
            kinds(f);
            pools(f, notes);
            queue(server, f);
            buyback(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("loot boxes selftest crashed", e);
            f.add("crashed: " + e);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST loot boxes: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST loot boxes PASSED (items, kinds, pools, odds, queue, hand-over, buyback and the coin cap)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST loot boxes FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    // 封切り師の買い取り (facility/Buyback) と鐘銭の上限 (2026-11-01)
    static void buyback(MinecraftServer server, List<String> f) {
        FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "[buyback]"));
        net.minecraft.world.item.Item heroic = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "wild_lavajaw_greatsword"));
        net.minecraft.world.item.Item divine = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "divine_sky_sword"));
        ItemStack gear = new ItemStack(heroic);
        int price = jp.revati.skyabyss.facility.Buyback.price(gear);
        int lv = jp.revati.skyabyss.item.GearLevel.required(gear);
        check(f, price == Math.round(60 * (1 + lv / 10.0)), "heroic gear price " + price + " at Lv " + lv);
        check(f, jp.revati.skyabyss.facility.Buyback.price(new ItemStack(divine)) == 0, "a divine relic can be sold");
        check(f, jp.revati.skyabyss.facility.Buyback.price(new ItemStack(net.minecraft.world.item.Items.DIAMOND)) == 0, "a vanilla item can be sold to the box opener");
        p.getInventory().setItem(3, gear);
        p.getInventory().setItem(4, new ItemStack(divine));
        var view = jp.revati.skyabyss.facility.Buyback.view(p);
        check(f, view.sells().size() == 1 && view.sells().get(0).index() == 3 && view.sells().get(0).coins() == price, "buyback lists only the sellable gear");
        int before = jp.revati.skyabyss.facility.Coins.count(p);
        check(f, jp.revati.skyabyss.facility.Buyback.sell(p, 3, 1) == 1 && p.getInventory().getItem(3).isEmpty()
                && jp.revati.skyabyss.facility.Coins.count(p) == before + price, "selling paid the price and took the item");
        check(f, jp.revati.skyabyss.facility.Buyback.sell(p, 4, 1) == 0 && !p.getInventory().getItem(4).isEmpty(), "the divine relic was sold");
        // 大きな額: 旅嚢へまとめて入る (上限 999,999,999)
        jp.revati.skyabyss.facility.Coins.give(p, 50_000_000);
        check(f, jp.revati.skyabyss.facility.Coins.count(p) == before + price + 50_000_000, "a large coin gift did not land in the pouch: "
                + jp.revati.skyabyss.facility.Coins.count(p));
        check(f, jp.revati.skyabyss.facility.Coins.MAX == 999_999_999, "coin cap");
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        p.getInventory().clearContent();
    }

    static void items(List<String> f) {
        check(f, BoxKind.ALL.size() == 20, "box kinds " + BoxKind.ALL.size() + " != 20");
        String a = "/assets/skyabyss/";
        for (BoxKind k : BoxKind.ALL) {
            Item item = LootBoxSetup.item(k);
            check(f, item instanceof SealedBoxItem s && s.kind == k, "not registered: " + k.id());
            if (item == null) {
                continue;
            }
            check(f, new ItemStack(item).getMaxStackSize() == LootBoxSetup.STACK, k.id() + " does not stack to " + LootBoxSetup.STACK);
            PouchEntries.Entry e = PouchEntries.entry(item);
            check(f, e != null && e.category() == PouchConfig.Category.BOX && e.limit() >= 999 && !e.withdrawable(), k.id() + " pouch entry");
            check(f, exists(a + "models/item/" + k.id() + ".json") && exists(a + "textures/item/" + k.id() + ".png"), k.id() + " model/icon missing");
            check(f, exists(a + "lowpoly/items/" + k.look() + "/common.json") && exists(a + "models/item_flat/" + k.look() + ".json"), k.look() + " mesh missing");
        }
        check(f, !jp.revati.skyabyss.pouch.PouchLayout.onGrid(PouchConfig.Category.BOX), "boxes must not take pouch grid slots");
    }

    static void kinds(List<String> f) {
        // 野の装備: 帯と階級
        for (WildGearTypes.Weapon w : WildGearTable.WEAPONS) {
            BoxKind k = LootBoxes.kindFor(new ItemStack(WildGearSetup.item(w.id())));
            check(f, k != null && k.type() == BoxKind.Type.RARITY && k.tier() == w.tier() && k.band() == w.band(), w.id() + " -> " + (k == null ? null : k.id()));
        }
        for (WildGearTypes.Accessory x : WildGearTable.ACCESSORIES) {
            BoxKind k = LootBoxes.kindFor(new ItemStack(WildGearSetup.item(x.id())));
            check(f, k != null && k.type() == BoxKind.Type.RARITY && k.tier() == x.tier(), x.id() + " -> " + (k == null ? null : k.id()));
        }
        // 巨神の図鑑・ヒロイック武器
        for (TitanGearSet set : TitanGearSets.all()) {
            if (!BoxKind.TITANS.contains(set.titan)) {
                continue;
            }
            for (String id : set.collection()) {
                TitanGearSet.item(id).ifPresent(i -> {
                    BoxKind k = LootBoxes.kindFor(new ItemStack(i));
                    boolean art = i instanceof ModGear g && g.tier() == GearTier.ARTIFACT;
                    check(f, k != null && (art ? k.type() == BoxKind.Type.ARTIFACT : k.id().equals("box_" + set.titan)), id + " -> " + (k == null ? null : k.id()));
                });
            }
        }
        TitanGearSet.item(HeroicGear.TIDE_STAFF).ifPresent(i -> check(f, kindOf(i) == BoxKind.titan("tide_shell"), "heroic staff is not a tide box"));
        for (String id : LegendGear.legendIds()) {
            TitanGearSet.item(id).ifPresent(i -> check(f, kindOf(i) == BoxKind.byId("box_artifact"), id + " is not in the gold box"));
        }
        for (String id : AbyssGear.SET.collection()) {
            TitanGearSet.item(id).ifPresent(i -> {
                BoxKind k = kindOf(i);
                check(f, k != null && k.type() != BoxKind.Type.TITAN, id + " (abyss) -> " + (k == null ? null : k.id()));
            });
        }
        check(f, LootBoxes.kindFor(new ItemStack(Items.BREAD)) == null, "bread became a box");
        check(f, LootBoxes.kindFor(new ItemStack(jp.revati.skyabyss.registry.ModItems.STONE_WHALE_SHARD.get())) == null, "a material became a box");
        for (Item i : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            // 神器 (雲海のアーティファクト)。雲海の土地の野の装備 (由来の素材が雲海) は階級の箱に入る
            if (i instanceof ModGear g && g.tier() == GearTier.ARTIFACT && jp.revati.skyabyss.story.finale.gear.DivineGear.DUNGEON.equals(g.dungeon())) {
                check(f, kindOf(i) == null, "divine gear became a box: " + i);
            }
            if (i instanceof ModGear g && g.tier() == GearTier.COMMON) {
                check(f, kindOf(i) == null, "common gear became a box: " + i);
            }
        }
    }

    private static BoxKind kindOf(Item i) {
        return LootBoxes.kindFor(new ItemStack(i));
    }

    static void pools(List<String> f, List<String> notes) {
        for (BoxKind k : BoxKind.ALL) {
            List<Item> pool = LootBoxes.pool(k);
            check(f, !pool.isEmpty(), k.id() + " has nothing inside");
            for (Item i : pool) {
                check(f, kindOf(i) == k, k.id() + " holds " + i + " that maps elsewhere");
            }
            notes.add(k.id() + " " + pool.size() + " items");
        }
        RandomSource r = RandomSource.create(52L);
        // 巨神の箱: 階級の割合
        int n = 4000;
        Map<GearTier, Integer> tiers = new EnumMap<>(GearTier.class);
        for (int i = 0; i < n; i++) {
            ItemStack s = LootBoxes.roll(BoxKind.titan("stone_whale"), Job.WARRIOR, r);
            tiers.merge(((ModGear) s.getItem()).tier(), 1, Integer::sum);
        }
        double rare = tiers.getOrDefault(GearTier.RARE, 0) / (double) n, epic = tiers.getOrDefault(GearTier.EPIC, 0) / (double) n,
                heroic = tiers.getOrDefault(GearTier.HEROIC, 0) / (double) n;
        notes.add(String.format("whale box tiers rare %.2f epic %.2f heroic %.2f", rare, epic, heroic));
        check(f, Math.abs(rare - 0.45) < 0.05 && Math.abs(epic - 0.45) < 0.05 && Math.abs(heroic - 0.10) < 0.03, "whale box tier odds " + tiers);
        // 階級の箱: 種類の割合と職への寄せ
        Map<LootBoxes.Category, Integer> cats = new EnumMap<>(LootBoxes.Category.class);
        int mine = 0, weapons = 0;
        for (int i = 0; i < n; i++) {
            ItemStack s = LootBoxes.roll(BoxKind.byId("box_rare_3"), Job.MAGE, r);
            LootBoxes.Category c = LootBoxes.category(s.getItem());
            cats.merge(c, 1, Integer::sum);
            if (c == LootBoxes.Category.WEAPON) {
                weapons++;
                if (Job.of(s.getItem()) == Job.MAGE) {
                    mine++;
                }
            }
        }
        double w = cats.getOrDefault(LootBoxes.Category.WEAPON, 0) / (double) n, ar = cats.getOrDefault(LootBoxes.Category.ARMOR, 0) / (double) n,
                ac = cats.getOrDefault(LootBoxes.Category.ACCESSORY, 0) / (double) n;
        notes.add(String.format("rare III weapon %.2f armor %.2f accessory %.2f, mage weapons %.2f", w, ar, ac, mine / (double) Math.max(1, weapons)));
        check(f, Math.abs(w - 0.60) < 0.05 && Math.abs(ar - 0.25) < 0.05 && Math.abs(ac - 0.15) < 0.04, "rare III category odds " + cats);
        check(f, mine / (double) Math.max(1, weapons) > 0.6, "job bias too weak: " + mine + "/" + weapons);
    }

    static void queue(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[loot_box]"));
        p.getInventory().clearContent();
        LootBoxData data = LootBoxData.get(server);
        RandomSource r = RandomSource.create(7L);
        Item first = WildGearSetup.item(WildGearTable.WEAPONS.stream().filter(w -> w.tier() == GearTier.RARE && w.band() == 2).findFirst().orElseThrow().id());
        BoxKind kind = LootBoxes.kindFor(new ItemStack(first));
        Item box = LootBoxSetup.item(kind);
        ItemStack g1 = AffixRules.withAffixes(new ItemStack(first), r);
        ItemStack g2 = AffixRules.withAffixes(new ItemStack(first), r);
        ItemStack b1 = LootBoxes.wrap(server, p.getUUID(), g1);
        check(f, b1.is(box) && b1.getCount() == 1 && data.size(p.getUUID(), kind.id()) == 1, "wrap did not make one box and queue the gear");
        PouchRouting.give(p, b1);
        PouchRouting.give(p, LootBoxes.wrap(server, p.getUUID(), g2));
        check(f, Pouch.count(p, box) == 2 && LootBoxes.count(p, kind) == 2, "two boxes of one kind did not stack in the pouch");
        List<LootBoxes.Opened> o = LootBoxes.open(p, kind, 1);
        check(f, o.size() == 1 && ItemStack.isSameItemSameComponents(o.get(0).gear(), g1) && LootBoxes.count(p, kind) == 1, "the oldest gear did not come out first");
        check(f, o.size() == 1 && o.get(0).reel().size() == LootBoxes.REEL && ItemStack.isSameItemSameComponents(o.get(0).reel().get(LootBoxes.WIN), g1),
                "the reel does not land on the gear");
        check(f, Pouch.countInventory(p.getInventory(), first) == 1, "the opened gear was not handed over");
        LootBoxes.open(p, kind, 5);
        check(f, LootBoxes.count(p, kind) == 0 && data.size(p.getUUID(), kind.id()) == 0, "open all left boxes or queue");
        // 列が空の箱 (渡された箱) は表から引く
        PouchRouting.give(p, new ItemStack(box, 3));
        List<LootBoxes.Opened> fresh = LootBoxes.open(p, kind, 3);
        check(f, fresh.size() == 3 && fresh.stream().allMatch(x -> LootBoxes.kindFor(x.gear()) == kind && AffixRules.affixes(x.gear()) != null),
                "an empty-queue box did not roll from its pool");
        // 渡し口: 包む / direct / keepDirect
        p.getInventory().clearContent();
        Item whale = TitanGearSet.item(HeroicGear.WHALE_STAFF).orElseThrow();
        List<ItemStack> items = new ArrayList<>(List.of(new ItemStack(whale), new ItemStack(jp.revati.skyabyss.registry.ModItems.STONE_WHALE_SHARD.get(), 2)));
        TitanLoot.handOver(server, p.getUUID(), p, items, 0);
        Item whaleBox = LootBoxSetup.item(BoxKind.titan("stone_whale"));
        check(f, LootBoxes.count(p, BoxKind.titan("stone_whale")) == 1 && Pouch.countInventory(p.getInventory(), whale) == 0 && items.get(0).is(whaleBox),
                "handOver did not box the titan gear (or the caller's list still names the gear)");
        LootBoxes.direct(() -> TitanLoot.handOver(server, p.getUUID(), p, List.of(new ItemStack(whale)), 0));
        check(f, Pouch.countInventory(p.getInventory(), whale) == 1, "direct hand-over was boxed");
        ItemStack kept = LootBoxes.keepDirect(new ItemStack(whale));
        TitanLoot.handOver(server, p.getUUID(), p, new ArrayList<>(List.of(kept)), 0);
        check(f, Pouch.countInventory(p.getInventory(), whale) == 2, "a keepDirect piece was boxed");
        data.clear(p.getUUID());
        jp.revati.skyabyss.loot.TitanMemoryData.get(server).setDirty();
    }

    private static boolean exists(String path) {
        try (InputStream in = LootBoxSelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }
}
