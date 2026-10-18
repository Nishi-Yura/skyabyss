package jp.revati.skyabyss.pouch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.grave.DeathEvents;
import jp.revati.skyabyss.grave.DeathRules;
import jp.revati.skyabyss.grave.GraveData;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.pouch.PouchConfig.Category;
import jp.revati.skyabyss.quest.QuestItems;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.record.KeepsakeBonds;
import jp.revati.skyabyss.record.Keepsakes;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.story.StorySetup;
import jp.revati.skyabyss.titan.travel.TravelSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 留め袋の自己テスト (SKYABYSS_SELFTEST=1、SKYABYSS_SELFTEST_ONLY=pouch で単独に)。結果はログに SELFTEST pouch PASSED / FAILED。
// docs/design/22 の 13 章のうち、画面と使う入力 (P4・P6) を除いたもの:
//   登録の表 (区分・上限・入れない品・手に持って使う品)、保存と同期の往復 (登録にない品の保持も)、入れる・出す・払う・上限、
//   部品のある品は入れない、渡し (PouchRouting.give)、拾う入口、掃除 (取り出し済みの印・hold)、取り出しの決まり (ゲームルール・区分)、
//   倒れても残る (型ごと・墓に入らない・体の入れ替え)、控え帳の在りか・天の鍵がそろう数え方、読む側 (炉の費用・振り直し・クエスト)、移し替え
public final class PouchSelfTest {
    private PouchSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        PouchSetupState saved = PouchSetupState.capture(server);
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("pouch selftest crashed", e);
            f.add("crashed: " + e);
        } finally {
            saved.restore(server);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST pouch PASSED (registry, save/sync round trip, add/take/limit, component refusal, give, pickup, sweep, loan mark, withdraw rules, "
                    + "death in every mode, copy to the new body, no grave, record holder, sky key count, forge cost, respec cost, quest count, migration)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST pouch FAILED: {}", x));
        }
    }

    // テストで変えるゲームルールを元へ戻す
    private record PouchSetupState(int withdraw, int deathMode, boolean keepInventory) {
        static PouchSetupState capture(MinecraftServer server) {
            GameRules rules = server.getGameRules();
            return new PouchSetupState(rules.getInt(PouchConfig.WITHDRAW_RULE), rules.getInt(DeathRules.MODE), rules.getBoolean(GameRules.RULE_KEEPINVENTORY));
        }

        void restore(MinecraftServer server) {
            GameRules rules = server.getGameRules();
            rules.getRule(PouchConfig.WITHDRAW_RULE).set(withdraw, server);
            rules.getRule(DeathRules.MODE).set(deathMode, server);
            rules.getRule(GameRules.RULE_KEEPINVENTORY).set(keepInventory, server);
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.getInventory().clearContent();
        return p;
    }

    private static int inv(Player p, Item item) {
        return Pouch.countInventory(p.getInventory(), item);
    }

    static void run(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        Item shard = ModItems.STONE_WHALE_SHARD.get();
        Item crystal = ModItems.RESONANCE_CRYSTAL.get();
        Item core = ModItems.STONE_WHALE_CORE.get();
        Item fragment = ModItems.SKY_KEY_FRAGMENT.get();
        Item key = StorySetup.SKY_KEY.get();
        Item call = TravelSetup.STONE_WHALE_CALL.get();

        // ---- 登録の表 (22 の 2・3 章)
        List<PouchEntries.Entry> all = PouchEntries.all();
        check(f, all.size() >= 20, "pouch entries are too few: " + all.size());
        check(f, all == PouchEntries.all(), "the entry table was rebuilt (the order must be fixed)");
        for (Category c : Category.values()) {
            check(f, all.stream().anyMatch(e -> e.category() == c), "no entry in the category " + c);
        }
        check(f, PouchEntries.entry(shard) != null && PouchEntries.entry(shard).category() == Category.MATERIAL && PouchEntries.entry(shard).limit() == 999,
                "the whale scale is not a titan material with limit 999");
        check(f, PouchEntries.entry(fragment).limit() == 4 && PouchEntries.entry(key).limit() == 1 && PouchEntries.entry(core).limit() == 99,
                "key/fragment/core limits are wrong");
        check(f, PouchEntries.entry(crystal).category() == Category.RESONANCE, "the resonance crystal is not in the resonance category");
        check(f, PouchEntries.any(call) != null && PouchEntries.any(call).needsHand() && PouchEntries.any(call).category() == Category.TOOL,
                "the call whistle is not a hand-used tool entry");
        check(f, PouchConfig.USE_TOOLS_IN_POUCH || PouchEntries.entry(call) == null, "hand-used tools must stay out of the pouch until the use input exists");
        for (Item guarded : Keepsakes.guardedItems()) {
            check(f, PouchEntries.any(guarded) != null, "a guarded keepsake is missing in the pouch entries: " + guarded);
        }
        // 背中の試練の道具・食べ物・装備は入れない (22 の 2.2)
        for (Item out : new Item[]{jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup.FIRE_KEY.get(),
                jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup.FIRE_KEY_HOT.get(), jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup.ASH_URN.get(),
                jp.revati.skyabyss.titan.tide.back.TideBackSetup.TIDE_PEARL.get(), jp.revati.skyabyss.titan.storm.back.StormBackSetup.GALE_FEATHER.get(),
                ModItems.CLOUD_JERKY.get(), ModItems.CAPTAIN_PIKE.get(), ModItems.STONE_WHALE_HELMET.get(), ModItems.RESONANCE_CHARM.get(), Items.BREAD}) {
            check(f, PouchEntries.any(out) == null, "an item that must stay in the inventory is a pouch entry: " + out);
        }
        for (PouchEntries.Entry e : all) {
            check(f, e.withdrawable() == (e.category() == Category.RESONANCE || e.category() == Category.MATERIAL || e.category() == Category.SMALL),
                    "withdraw category wrong for " + e.id());
        }

        // ---- 保存と同期の往復 (22 の 8.1・8.2)
        PouchData sample = new PouchData();
        sample.set(PouchEntries.any(shard).id(), 7);
        sample.set(ResourceLocation.fromNamespaceAndPath("othermod", "gem"), 3);
        sample.markSeen(PouchEntries.any(shard).id());
        sample.migrated = 1;
        sample.hold = true;
        DataResult<Tag> encoded = PouchData.CODEC.encodeStart(NbtOps.INSTANCE, sample);
        Tag tag = encoded.result().orElse(null);
        check(f, tag != null, "the pouch did not encode");
        if (tag != null) {
            PouchData back = PouchData.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
            check(f, back != null && back.count(PouchEntries.any(shard).id()) == 7 && back.migrated == 1 && back.hold && back.hasSeen(PouchEntries.any(shard).id())
                            && back.count(ResourceLocation.fromNamespaceAndPath("othermod", "gem")) == 3 && back.version == PouchData.VERSION,
                    "the pouch changed on a save round trip (an unknown item id must be kept)");
            check(f, tag instanceof CompoundTag ct && ct.getInt("v") == PouchData.VERSION, "the saved pouch has no version");
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        PouchData.STREAM_CODEC.encode(buf, sample);
        PouchData synced = PouchData.STREAM_CODEC.decode(buf);
        check(f, synced.count(PouchEntries.any(shard).id()) == 7 && synced.migrated == 1 && synced.hold, "the pouch changed on a sync round trip");
        check(f, PouchData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).result().isPresent(), "an empty save did not load as an empty pouch");

        // ---- 入れる・上限・取る・払う
        FakePlayer p = player(level, "[pouch_a]");
        check(f, Pouch.data(p).isEmpty(), "a new player has a non-empty pouch");
        ItemStack s20 = new ItemStack(shard, 20);
        check(f, Pouch.add(p, s20) == 20 && s20.isEmpty() && Pouch.count(p, shard) == 20, "adding 20 scales failed");
        ItemStack big = new ItemStack(shard, 64);
        for (int i = 0; i < 15; i++) {
            Pouch.add(p, new ItemStack(shard, 64));
        }
        check(f, Pouch.count(p, shard) == 980, "the scale count after 15 stacks is wrong: " + Pouch.count(p, shard));
        int moved = Pouch.add(p, big);
        check(f, moved == 19 && big.getCount() == 45 && Pouch.count(p, shard) == 999, "the limit 999 did not cap: moved " + moved + " left " + big.getCount());
        check(f, Pouch.take(p, shard, 500) == 500 && Pouch.count(p, shard) == 499 && Pouch.take(p, shard, 9999) == 499 && Pouch.count(p, shard) == 0,
                "take did not stop at what the pouch holds");
        // 部品のある品は入れない (強化・名前)
        ItemStack named = new ItemStack(shard, 2);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("renamed"));
        check(f, Pouch.add(p, named) == 0 && named.getCount() == 2 && Pouch.count(p, shard) == 0, "a renamed scale went into the pouch");
        check(f, Pouch.add(p, new ItemStack(Items.DIAMOND, 3)) == 0, "a vanilla item went into the pouch");
        check(f, Pouch.add(p, new ItemStack(call)) == 0, "a hand-used whistle went into the pouch");

        // ---- 渡し (PouchRouting.give)
        p.getInventory().clearContent();
        ItemStack gift = new ItemStack(shard, 5);
        PouchRouting.give(p, gift);
        check(f, gift.isEmpty() && Pouch.count(p, shard) == 5 && inv(p, shard) == 0, "give did not route the scales to the pouch");
        ItemStack bread = new ItemStack(Items.BREAD, 3);
        PouchRouting.give(p, bread);
        check(f, bread.isEmpty() && inv(p, Items.BREAD) == 3, "give did not keep bread in the inventory");
        Pouch.take(p, shard, 5);
        for (int i = 0; i < 15; i++) {
            Pouch.add(p, new ItemStack(shard, 64));
        }
        ItemStack over = new ItemStack(shard, 60);
        ItemStack left = PouchRouting.give(p, over);
        check(f, Pouch.count(p, shard) == 999 && inv(p, shard) == 60 - 39 && left.isEmpty(), "the overflow of give did not fall back to the inventory: inv " + inv(p, shard));
        p.getInventory().clearContent();
        Pouch.take(p, shard, 999);
        // 受け取る側 (報酬・預かり) の渡しも袋へ
        int held = TitanLoot.handOver(server, p.getUUID(), p, List.of(new ItemStack(shard, 4), new ItemStack(crystal, 2), new ItemStack(Items.APPLE, 1)), 0);
        check(f, held == 0 && Pouch.count(p, shard) == 4 && Pouch.count(p, crystal) == 2 && inv(p, Items.APPLE) == 1, "TitanLoot.handOver did not route to the pouch");
        Quests.give(p, new ItemStack(shard, 3));
        check(f, Pouch.count(p, shard) == 7 && inv(p, shard) == 0, "Quests.give did not route to the pouch");

        // ---- 拾う入口 (ItemEntityPickupEvent.Pre)
        p.getInventory().clearContent();
        Pouch.take(p, shard, 999);
        ItemEntity drop = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(shard, 6));
        drop.setNoPickUpDelay();
        ItemEntityPickupEvent.Pre pre = new ItemEntityPickupEvent.Pre(p, drop);
        NeoForge.EVENT_BUS.post(pre);
        check(f, Pouch.count(p, shard) == 6 && drop.getItem().isEmpty() && drop.isRemoved() && pre.canPickup() == TriState.FALSE && inv(p, shard) == 0,
                "picking up scales did not go to the pouch: count " + Pouch.count(p, shard));
        ItemEntity delayed = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(shard, 2));
        delayed.setPickUpDelay(40);
        NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(p, delayed));
        check(f, Pouch.count(p, shard) == 6 && delayed.getItem().getCount() == 2, "an item with a pickup delay was taken");
        ItemEntity targeted = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(shard, 2));
        targeted.setNoPickUpDelay();
        targeted.setTarget(UUID.randomUUID());
        NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(p, targeted));
        check(f, Pouch.count(p, shard) == 6 && targeted.getItem().getCount() == 2, "an item meant for another player was taken");
        ItemEntity plainDrop = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(Items.DIAMOND, 2));
        plainDrop.setNoPickUpDelay();
        ItemEntityPickupEvent.Pre pre2 = new ItemEntityPickupEvent.Pre(p, plainDrop);
        NeoForge.EVENT_BUS.post(pre2);
        check(f, pre2.canPickup() == TriState.DEFAULT && plainDrop.getItem().getCount() == 2, "a vanilla item pickup was touched");
        // 上限で入りきらない分は持ち物へ
        Pouch.take(p, shard, 999);
        for (int i = 0; i < 15; i++) {
            Pouch.add(p, new ItemStack(shard, 64));
        }
        ItemEntity topUp = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(shard, 50));
        topUp.setNoPickUpDelay();
        NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(p, topUp));
        check(f, Pouch.count(p, shard) == 999 && inv(p, shard) == 11 && topUp.isRemoved(), "the part over the limit did not go to the inventory: inv " + inv(p, shard));
        p.getInventory().clearContent();
        Pouch.take(p, shard, 999);

        // ---- 掃除と取り出し済みの印 (22 の 4.1・6 章)
        Pouch.take(p, crystal, 999);
        p.getInventory().setItem(3, new ItemStack(shard, 9));
        p.getInventory().setItem(30, new ItemStack(crystal, 4));
        p.getInventory().setItem(31, new ItemStack(Items.COBBLESTONE, 10));
        check(f, Pouch.sweep(p) == 13 && Pouch.count(p, shard) == 9 && Pouch.count(p, crystal) == 4 && inv(p, Items.COBBLESTONE) == 10 && inv(p, shard) == 0,
                "the sweep did not move the pouch items");
        check(f, Pouch.withdraw(p, shard, 5, false) == 5 && Pouch.count(p, shard) == 4 && inv(p, shard) == 5, "withdrawing 5 scales failed");
        ItemStack lent = null;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(shard)) {
                lent = s;
            }
        }
        check(f, lent != null && lent.has(PouchSetup.LOAN.get()), "the withdrawn scales have no loan mark");
        check(f, Pouch.sweep(p) == 0 && inv(p, shard) == 5, "the sweep took back withdrawn scales");
        // 拾い直すと袋へ入り、印は外れる
        ItemEntity again = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), lent.copy());
        again.setNoPickUpDelay();
        lent.setCount(0);
        NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(p, again));
        check(f, Pouch.count(p, shard) == 9 && inv(p, shard) == 0, "picking withdrawn scales up again did not return them to the pouch");
        Pouch.data(p).hold = true;
        p.getInventory().setItem(3, new ItemStack(shard, 2));
        check(f, Pouch.sweep(p) == 0 && inv(p, shard) == 2, "hold on did not stop the sweep");
        Pouch.data(p).hold = false;
        check(f, Pouch.sweep(p) == 2 && Pouch.count(p, shard) == 11, "the sweep after hold off did not move the scales");
        // 画面から戻ったカーソルの品
        p.containerMenu.setCarried(new ItemStack(shard, 3));
        check(f, Pouch.sweepAfterClose(p) >= 3 && Pouch.count(p, shard) == 14 && p.containerMenu.getCarried().isEmpty(), "the sweep after closing a screen missed the cursor item");

        // ---- 取り出しの決まり (22 の 6 章。ゲームルール・区分・上限なし)
        Pouch.add(p, new ItemStack(core, 2));
        Pouch.add(p, new ItemStack(fragment, 1));
        check(f, Pouch.withdraw(p, core, 1, false) == 0 && Pouch.withdraw(p, fragment, 1, false) == 0, "a core or key fragment could be withdrawn");
        check(f, Pouch.count(p, core) == 2 && Pouch.count(p, fragment) == 1, "refusing a withdraw changed the pouch");
        server.getGameRules().getRule(PouchConfig.WITHDRAW_RULE).set(0, server);
        check(f, Pouch.withdraw(p, shard, 1, false) == 0 && !PouchConfig.withdrawEnabled(server), "skyabyssPouchWithdraw 0 still allowed a withdraw");
        server.getGameRules().getRule(PouchConfig.WITHDRAW_RULE).set(1, server);
        check(f, Pouch.withdraw(p, shard, 1000, false) == 14 && Pouch.count(p, shard) == 0, "withdrawing everything failed");
        p.getInventory().clearContent();
        // 管理者は決まりを無視して出せる (欠片には今の代の印が付く)
        check(f, Pouch.withdraw(p, fragment, 1, true) == 1 && inv(p, fragment) == 1, "the forced withdraw of a fragment failed");
        ItemStack lentFragment = p.getInventory().items.stream().filter(s -> s.is(fragment)).findFirst().orElse(ItemStack.EMPTY);
        check(f, p.getUUID().equals(KeepsakeBonds.owner(lentFragment)), "a withdrawn fragment was not bound to its owner");
        check(f, Pouch.add(p, lentFragment) == 1 && Pouch.count(p, fragment) == 1, "a bound fragment of its own owner did not go back into the pouch");
        FakePlayer other = player(level, "[pouch_b]");
        ItemStack theirs = new ItemStack(fragment);
        KeepsakeBonds.bindFor(other, theirs);
        check(f, Pouch.add(p, theirs) == 0 && !theirs.isEmpty(), "another player's fragment went into the pouch");
        Pouch.take(p, core, 9);
        Pouch.take(p, fragment, 9);
        p.getInventory().clearContent();

        // ---- 倒れても残る (22 の 5 章)
        for (int mode = 0; mode < DeathRules.Mode.values().length; mode++) {
            server.getGameRules().getRule(DeathRules.MODE).set(mode, server);
            FakePlayer dead = player(level, "[pouch_dead" + mode + "]");
            Pouch.add(dead, new ItemStack(shard, 30));
            Pouch.add(dead, new ItemStack(core, 1));
            Pouch.add(dead, new ItemStack(crystal, 5));
            Pouch.add(dead, new ItemStack(key));
            DeathEvents.handleDeath(dead);
            check(f, Pouch.count(dead, shard) == 30 && Pouch.count(dead, core) == 1 && Pouch.count(dead, crystal) == 5 && Pouch.count(dead, key) == 1,
                    "death in mode " + DeathRules.Mode.values()[mode] + " changed the pouch");
            FakePlayer fresh = player(level, "[pouch_fresh" + mode + "]");
            check(f, Pouch.data(fresh).isEmpty(), "the new body started with a non-empty pouch");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(fresh, dead, true));
            check(f, Pouch.count(fresh, shard) == 30 && Pouch.count(fresh, core) == 1 && Pouch.count(fresh, crystal) == 5 && Pouch.count(fresh, key) == 1,
                    "the pouch did not carry over to the new body in mode " + DeathRules.Mode.values()[mode]);
            for (GraveData.Grave g : GraveData.get(server).owned(dead.getUUID())) {
                for (ItemStack s : g.items) {
                    check(f, PouchEntries.any(s.getItem()) == null, "a pouch item was put in the grave: " + s);
                }
            }
        }
        // 墓の中の昔の品は、渡されるとき袋へ (22 の 4.2)。ここでは墓の取り出し口 (Graves.collect) を直接は呼ばず、渡しの入口で確かめた (上)
        server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        FakePlayer vk = player(level, "[pouch_vk]");
        Pouch.add(vk, new ItemStack(shard, 5));
        check(f, DeathEvents.handleDeath(vk) == null && Pouch.count(vk, shard) == 5, "keepInventory changed the pouch");
        server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);

        // ---- 控え帳の在りか・天の鍵がそろう数え方 (22 の 5・8.3 章)
        FakePlayer k = player(level, "[pouch_key]");
        Pouch.add(k, new ItemStack(fragment, 3));
        Keepsakes.Where where = Keepsakes.locate(k, fragment);
        check(f, where.holders().getOrDefault("pouch", 0) == 3 && where.safe() == 3 && where.total() == 3, "the record location did not count the pouch: " + where.holders());
        k.getInventory().add(new ItemStack(fragment, 1));
        check(f, KeepsakeBonds.where(k, KeepsakeBonds.Kind.FRAGMENT).safe() == 4 && jp.revati.skyabyss.story.SkyKey.fragmentsHeld(k) == 4,
                "the sky key count did not include the pouch: " + jp.revati.skyabyss.story.SkyKey.fragmentsHeld(k));
        check(f, Keepsakes.take(k, fragment, KeepsakeBonds.mine(k), 4) == 4 && Pouch.count(k, fragment) == 0 && jp.revati.skyabyss.story.SkyKey.fragmentsHeld(k) == 0,
                "taking the fragments did not reach into the pouch");
        Pouch.add(k, new ItemStack(core, 1));
        check(f, Keepsakes.locate(k, core).safe() == 1, "a core in the pouch was not at hand");
        Pouch.take(k, core, 1);

        // ---- 読む側 (22 の 8.3 章): 炉の費用・交換・振り直し・クエスト
        FakePlayer r = player(level, "[pouch_read]");
        Pouch.add(r, new ItemStack(shard, 3));
        r.getInventory().add(new ItemStack(shard, 2));
        Pouch.add(r, new ItemStack(crystal, 2));
        check(f, jp.revati.skyabyss.forge.ForgeRules.count(r, shard) == 5 && jp.revati.skyabyss.forge.ForgeRules.count(r.getInventory(), shard) == 2,
                "ForgeRules.count did not add the pouch");
        check(f, jp.revati.skyabyss.forge.ForgeRules.affordable(r, new jp.revati.skyabyss.forge.ForgeRules.Cost(5, 2)), "the forge cost did not read the pouch");
        check(f, !jp.revati.skyabyss.forge.ForgeRules.affordable(r, new jp.revati.skyabyss.forge.ForgeRules.Cost(6, 0)), "the forge cost passed with too few scales");
        check(f, SkillManager.countCrystals(r) == 2 && SkillManager.countCrystals(r.getInventory()) == 0, "the respec crystal count did not read the pouch");
        SkillManager.removeCrystals(r, 1);
        check(f, Pouch.count(r, crystal) == 1, "paying a crystal did not come out of the pouch");
        Pouch.add(r, new ItemStack(ModItems.RESONANCE_SHARD_STONE_WHALE.get(), 40));
        check(f, jp.revati.skyabyss.forge.ForgeRules.exchangeAffordable(r, TitanLoot.STONE_WHALE), "the exchange cost did not read the pouch");
        jp.revati.skyabyss.forge.ForgeRules.payExchange(r, TitanLoot.STONE_WHALE);
        check(f, Pouch.count(r, ModItems.RESONANCE_SHARD_STONE_WHALE.get()) == 10, "the exchange payment did not come out of the pouch");
        Quests.take(r, shard, 4);
        check(f, Pouch.count(r, shard) == 0 && inv(r, shard) == 1, "Quests.take did not pay from the pouch first: pouch " + Pouch.count(r, shard) + " inv " + inv(r, shard));
        Pouch.add(r, new ItemStack(shard, 6));
        check(f, QuestItems.count(r, "skyabyss:stone_whale_shard", null) == 7 && QuestItems.count(r, "skyabyss:stone_whale_shard", "token") == 0,
                "the quest item count did not include the pouch");
        check(f, QuestItems.take(r, "skyabyss:stone_whale_shard", null, 7) && Pouch.count(r, shard) == 0 && inv(r, shard) == 0, "the quest item take failed");

        // ---- 移し替え (22 の 8.4 章): 持ち物・エンダーチェスト・報酬の預かり・預かり (Persisted)・自分の墓
        FakePlayer m = player(level, "[pouch_migrate]");
        m.getInventory().setItem(2, new ItemStack(shard, 8));
        m.getInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 4));
        m.getInventory().setItem(36, new ItemStack(Items.IRON_HELMET));
        m.getEnderChestInventory().setItem(1, new ItemStack(crystal, 3));
        TitanMemoryData.get(server).pending(m.getUUID()).items.add(new ItemStack(fragment, 2));
        CompoundTag persisted = m.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!m.getPersistentData().contains(Player.PERSISTED_NBT_TAG)) {
            m.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        }
        ListTag keepList = new ListTag();
        CompoundTag keepEntry = new CompoundTag();
        keepEntry.putByte("Slot", (byte) 4);
        keepList.add(new ItemStack(ModItems.RESONANCE_SHARD_EMBER.get(), 6).save(level.registryAccess(), keepEntry));
        keepList.add(new ItemStack(Items.DIAMOND, 1).save(level.registryAccess(), new CompoundTag()));
        CompoundTag keep = new CompoundTag();
        keep.put("Inventory", keepList);
        persisted.put("skyabyss_death_keep", keep);
        GraveData.Grave grave = new GraveData.Grave(UUID.randomUUID(), m.getUUID(), "[pouch_migrate]", level.dimension(), new BlockPos(0, -60, 0),
                level.getGameTime(), 0, new ArrayList<>(List.of(new ItemStack(shard, 12), new ItemStack(Items.EMERALD, 2))));
        GraveData.get(server).add(grave);
        check(f, PouchMigration.runOnce(m) == 8 + 3 + 2 + 6 + 12, "the migration moved the wrong number of items");
        check(f, Pouch.count(m, shard) == 20 && Pouch.count(m, crystal) == 3 && Pouch.count(m, fragment) == 2
                        && Pouch.count(m, ModItems.RESONANCE_SHARD_EMBER.get()) == 6, "the migrated counts are wrong");
        check(f, inv(m, shard) == 0 && inv(m, Items.COBBLESTONE) == 4 && m.getInventory().getItem(36).is(Items.IRON_HELMET), "the migration touched items it should leave");
        check(f, m.getEnderChestInventory().getItem(1).isEmpty() && !TitanMemoryData.get(server).pending(m.getUUID()).items.stream().anyMatch(s -> s.is(fragment)),
                "the ender chest or the reward box still holds a pouch item");
        check(f, keepList.size() == 1 && grave.items.size() == 1 && grave.items.get(0).is(Items.EMERALD), "the saved keep list or the grave kept a pouch item");
        check(f, Pouch.data(m).migrated == PouchData.MIGRATION_VERSION && PouchMigration.runOnce(m) == -1, "the migration did not run exactly once");
        GraveData.get(server).remove(grave.id);
        TitanMemoryData.get(server).takePending(m.getUUID());

        // ---- 同期の呼び出しは作り物のプレイヤーでも落ちない
        Pouch.sync(m);
    }
}
