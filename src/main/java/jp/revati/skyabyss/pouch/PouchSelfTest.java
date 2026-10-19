package jp.revati.skyabyss.pouch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

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
import net.minecraft.core.registries.BuiltInRegistries;
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
// docs/design/22 の 13 章・16.6 章のとおり (画面そのものの描画・マウスは窓がないと確かめられないので、メニューの決まり・輪の入口・作り直し・既定をサーバー側で):
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
                    + "death in every mode, copy to the new body, no grave, record holder, sky key count, forge cost, respec cost, quest count, migration, screen menu clicks and layout, wheel/short-press use, quick card pick and pin, rebuild and repair, defaults flip)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST pouch FAILED: {}", x));
        }
    }

    // テストで変えるゲームルールを元へ戻す
    private record PouchSetupState(boolean master, int withdraw, int deathMode, boolean keepInventory) {
        static PouchSetupState capture(MinecraftServer server) {
            GameRules rules = server.getGameRules();
            boolean on = rules.getBoolean(PouchConfig.MASTER_RULE);
            rules.getRule(PouchConfig.MASTER_RULE).set(true, server);
            // 既定は取り出し不可 (0)。取り出しの決まりを確かめる段は、ここで可 (1) にして、段ごとに 0 へ切り替える
            int withdraw = rules.getInt(PouchConfig.WITHDRAW_RULE);
            rules.getRule(PouchConfig.WITHDRAW_RULE).set(1, server);
            return new PouchSetupState(on, withdraw, rules.getInt(DeathRules.MODE), rules.getBoolean(GameRules.RULE_KEEPINVENTORY));
        }

        void restore(MinecraftServer server) {
            GameRules rules = server.getGameRules();
            rules.getRule(PouchConfig.MASTER_RULE).set(master, server);
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

    private static int ForgeRulesCount(Player p, Item item) {
        return jp.revati.skyabyss.forge.ForgeRules.count(p, item);
    }

    // 一枚札の選び方 (QuickPick。純粋な関数): 場面の自動、前回の道具、置いた札 (pin) の優先
    private static void quickCard(List<String> f) {
        var whale = net.minecraft.resources.ResourceLocation.parse("skyabyss:whale_call");
        var shell = net.minecraft.resources.ResourceLocation.parse("skyabyss:shell_call");
        var conch = net.minecraft.resources.ResourceLocation.parse("skyabyss:conch");
        var cock = net.minecraft.resources.ResourceLocation.parse("skyabyss:cock");
        var book = net.minecraft.resources.ResourceLocation.parse("skyabyss:book");
        var all = List.of(new QuickPick.Cand(whale, QuickPick.Role.CALL, "whale"), new QuickPick.Cand(shell, QuickPick.Role.CALL, "shell"),
                new QuickPick.Cand(conch, QuickPick.Role.SEA, null), new QuickPick.Cand(cock, QuickPick.Role.STORM, null),
                new QuickPick.Cand(book, QuickPick.Role.OTHER, null));
        var none = QuickPick.Ctx.NONE;
        check(f, QuickPick.pick(List.of(), none, null, null) == null, "quick card: an empty pouch must give no card");
        check(f, book.equals(QuickPick.pick(List.of(all.get(4)), none, null, null)), "quick card: a single tool must be the card");
        check(f, whale.equals(QuickPick.pick(all, none, null, null)), "quick card: no context and no last must give the first tool");
        check(f, conch.equals(QuickPick.pick(all, none, null, conch)), "quick card: nothing relevant must give the last tool");
        check(f, shell.equals(QuickPick.pick(all, new QuickPick.Ctx("shell", false, false, false), null, conch)), "quick card: near a titan must give its own call");
        check(f, whale.equals(QuickPick.pick(all, new QuickPick.Ctx("whale", true, true, true), null, conch)), "quick card: a titan must beat the sea and the storm");
        check(f, shell.equals(QuickPick.pick(all, new QuickPick.Ctx(null, true, false, false), null, shell)), "quick card: a waymark must give the last call tool");
        check(f, whale.equals(QuickPick.pick(all, new QuickPick.Ctx(null, true, false, false), null, conch)), "quick card: a waymark without a last call tool must give the first call");
        check(f, conch.equals(QuickPick.pick(all, new QuickPick.Ctx(null, false, true, true), null, whale)), "quick card: the sea must give the conch before the storm");
        check(f, cock.equals(QuickPick.pick(all, new QuickPick.Ctx(null, false, false, true), null, whale)), "quick card: a storm must give the weathercock");
        check(f, whale.equals(QuickPick.pick(List.of(all.get(0), all.get(4)), new QuickPick.Ctx(null, false, true, true), null, whale)),
                "quick card: a context without its tool must fall back to the last tool");
        check(f, book.equals(QuickPick.pick(all, new QuickPick.Ctx("shell", true, true, true), book, whale)), "quick card: a pinned tool must beat every context");
        check(f, conch.equals(QuickPick.pick(List.of(all.get(1), all.get(2)), none, whale, conch)),
                "quick card: a pin that is not in the pouch must be ignored (fall to last)");
    }

    static void run(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        Item shard = ModItems.STONE_WHALE_SHARD.get();
        Item crystal = ModItems.RESONANCE_CRYSTAL.get();
        Item core = ModItems.STONE_WHALE_CORE.get();
        Item fragment = ModItems.SKY_KEY_FRAGMENT.get();
        Item key = StorySetup.SKY_KEY.get();
        Item call = TravelSetup.STONE_WHALE_CALL.get();

        quickCard(f);

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
        check(f, PouchConfig.USE_TOOLS_IN_POUCH && PouchEntries.entry(call) != null, "hand-used tools must be pouch entries now that the wheel exists");
        check(f, PouchConfig.ENABLED_DEFAULT && PouchConfig.WITHDRAW_DEFAULT == 0, "the pouch must default to on with withdraw off");
        for (Item usable : new Item[]{call, jp.revati.skyabyss.titan.tide.chapter.TideChapterSetup.TIDE_WATCHING_SHELL.get(),
                jp.revati.skyabyss.titan.storm.StormSetup.WEATHERCOCK.get(), jp.revati.skyabyss.lore.LoreSetup.LORE_BOOK.get(),
                jp.revati.skyabyss.lore.LoreSetup.LORE_PAGE.get()}) {
            check(f, usable instanceof PouchUsable && PouchEntries.entry(usable) != null, "a hand-used item is not a usable pouch entry: " + usable);
        }
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
        // ---- "NEW" の印 (22 の 9.4): 初めて入った品に付く・見たら外れる・同じ品を足しても付き直さない・人ごと・保存と同期・倒れても残る
        ResourceLocation shardId = PouchEntries.any(shard).id();
        check(f, Pouch.data(p).isFresh(shardId) && Pouch.data(p).anyFresh(), "a first-time pouch item has no NEW flag");
        Pouch.add(p, new ItemStack(shard, 1));
        check(f, Pouch.data(p).isFresh(shardId), "adding more of an item cleared the NEW flag");
        FakePlayer freshOther = player(level, "[pouch_new_other]");
        check(f, !Pouch.data(freshOther).anyFresh(), "another player got the NEW flag");
        PouchData flagged = Pouch.data(p);
        Tag freshTag = PouchData.CODEC.encodeStart(NbtOps.INSTANCE, flagged).result().orElse(null);
        PouchData freshBack = freshTag == null ? null : PouchData.CODEC.parse(NbtOps.INSTANCE, freshTag).result().orElse(null);
        check(f, freshBack != null && freshBack.isFresh(shardId), "the NEW flag was lost on a save round trip");
        RegistryFriendlyByteBuf freshBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        PouchData.STREAM_CODEC.encode(freshBuf, flagged);
        check(f, PouchData.STREAM_CODEC.decode(freshBuf).isFresh(shardId), "the NEW flag was lost on a sync round trip");
        check(f, flagged.copy().isFresh(shardId), "the NEW flag was lost on the death copy");
        check(f, !PouchNet.viewed(freshOther, shardId) && Pouch.data(p).isFresh(shardId), "viewing from another player cleared the flag");
        check(f, PouchNet.viewed(p, shardId) && !Pouch.data(p).isFresh(shardId) && !Pouch.data(p).anyFresh(), "viewing an item did not clear its NEW flag");
        check(f, !PouchNet.viewed(p, shardId), "viewing an unflagged item reported a change");
        Pouch.take(p, shard, 21);
        Pouch.add(p, new ItemStack(shard, 20));
        check(f, !Pouch.data(p).isFresh(shardId), "re-adding an item that was seen before flagged it NEW again");
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
        check(f, Pouch.add(p, new ItemStack(call)) == 1 && Pouch.count(p, call) == 1 && Pouch.take(p, call, 1) == 1, "a whistle did not go into the pouch");

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

        // ---- 親スイッチ skyabyssPouch (切): 袋行きをしない・中身は失われない・取り出せる
        FakePlayer o = player(level, "[pouch_off]");
        Pouch.add(o, new ItemStack(shard, 5));
        server.getGameRules().getRule(PouchConfig.MASTER_RULE).set(false, server);
        ItemStack off = new ItemStack(shard, 4);
        PouchRouting.give(o, off);
        check(f, !PouchConfig.enabled() && off.isEmpty() && inv(o, shard) == 4 && Pouch.count(o, shard) == 5, "give with the switch off did not use the old path");
        check(f, Pouch.sweep(o) == 0 && PouchMigration.runOnce(o) == -1 && Pouch.data(o).migrated == 0, "sweep or migration ran with the switch off");
        ItemEntity offDrop = new ItemEntity(level, o.getX(), o.getY(), o.getZ(), new ItemStack(shard, 2));
        offDrop.setNoPickUpDelay();
        NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(o, offDrop));
        check(f, offDrop.getItem().getCount() == 2 && Pouch.count(o, shard) == 5, "pickup routing ran with the switch off");
        check(f, ForgeRulesCount(o, shard) == 9, "readers stopped counting the pouch with the switch off");
        check(f, Pouch.withdraw(o, shard, 5, true) == 5 && Pouch.count(o, shard) == 0, "the pouch contents could not be taken with the switch off");
        server.getGameRules().getRule(PouchConfig.MASTER_RULE).set(true, server);

        // ---- 同期の呼び出しは作り物のプレイヤーでも落ちない
        Pouch.sync(m);

        runTitanMaterials(server, f, level);
        runScreen(server, f, level);
        runUse(server, f, level);
        runRebuild(server, f, level);
        runCraftTable(server, f, level);
        runDefaults(server, f);
    }

    // ---------------------------------------------------------------- 四巨神の欠片・素材・鍵・心核 (どの入口でも袋へ入る)
    private static List<Item> titanPouchItems() {
        List<Item> out = new ArrayList<>();
        out.add(ModItems.SKY_KEY_FRAGMENT.get());
        out.add(StorySetup.SKY_KEY.get());
        out.add(ModItems.RESONANCE_CRYSTAL.get());
        out.add(ModItems.RESONANCE_SHARD.get());
        for (String titan : jp.revati.skyabyss.progress.Titans.all()) {
            out.add(ModItems.resonanceShard(titan));
        }
        out.addAll(Keepsakes.coreItems());
        out.add(ModItems.STONE_WHALE_SHARD.get());
        out.add(ModItems.STONE_WHALE_CORE.get());
        out.add(jp.revati.skyabyss.titan.tide.mob.TideMobSetup.TIDE_SHELL_SHARD.get());
        out.add(jp.revati.skyabyss.item.storm.StormGearSetup.SHARD.get());
        out.add(jp.revati.skyabyss.item.storm.StormGearSetup.CORE.get());
        out.add(jp.revati.skyabyss.titan.ember.gear.EmberGearSetup.SHARD.get());
        out.add(jp.revati.skyabyss.item.storm.StormGearSetup.DUST.get());
        out.add(jp.revati.skyabyss.titan.tide.mob.TideMobSetup.BUBBLE_SAC.get());
        out.add(jp.revati.skyabyss.titan.tide.mob.TideMobSetup.TIDE_DRIED_SHELL.get());
        out.add(jp.revati.skyabyss.titan.ember.gear.EmberGearSetup.SPARK_DUST.get());
        out.add(jp.revati.skyabyss.titan.ember.gear.EmberGearSetup.CINDER_HUSK.get());
        return out;
    }

    private static void runTitanMaterials(MinecraftServer server, List<String> f, ServerLevel level) {
        for (Item item : titanPouchItems()) {
            String name = BuiltInRegistries.ITEM.getKey(item).toString();
            check(f, PouchEntries.entry(item) != null, "a titan pouch item has no pouch entry: " + name);
            check(f, new ItemStack(item).getComponentsPatch().isEmpty(), "a fresh titan item carries data components: " + name);
            // 入口ごとに新しい人で (鍵・欠片は上限が小さいので積み重ねない)
            FakePlayer p = player(level, "[pouch_titan_give]");
            ItemStack a = new ItemStack(item, 1);
            PouchRouting.give(p, a);
            check(f, a.isEmpty() && Pouch.count(p, item) == 1 && inv(p, item) == 0, "give did not route " + name);
            p = player(level, "[pouch_titan_quest]");
            Quests.give(p, new ItemStack(item, 1));
            check(f, Pouch.count(p, item) == 1 && inv(p, item) == 0, "Quests.give did not route " + name);
            p = player(level, "[pouch_titan_loot]");
            TitanLoot.handOver(server, p.getUUID(), p, List.of(new ItemStack(item, 1)), 0);
            check(f, Pouch.count(p, item) == 1 && inv(p, item) == 0, "TitanLoot.handOver did not route " + name);
            p = player(level, "[pouch_titan_pick]");
            ItemEntity drop = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(item, 1));
            drop.setNoPickUpDelay();
            drop.setTarget(p.getUUID());
            NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(p, drop));
            check(f, Pouch.count(p, item) == 1 && inv(p, item) == 0 && drop.isRemoved(), "pickup did not route " + name);
            p = player(level, "[pouch_titan_sweep]");
            p.getInventory().setItem(7, new ItemStack(item, 1));
            Pouch.sweep(p);
            check(f, Pouch.count(p, item) == 1 && inv(p, item) == 0, "sweep did not route " + name);
            FakePlayer m = player(level, "[pouch_titan_m]");
            m.getInventory().setItem(2, new ItemStack(item, 1));
            PouchMigration.run(m);
            check(f, Pouch.count(m, item) == 1 && inv(m, item) == 0, "migration did not route " + name);
        }
        // 持ち主の印の付いた欠片・鍵: 自分の今の代の品は入り、取り出すと印が付く
        FakePlayer b = player(level, "[pouch_titan_b]");
        for (Item item : new Item[]{ModItems.SKY_KEY_FRAGMENT.get(), StorySetup.SKY_KEY.get()}) {
            ItemStack bound = new ItemStack(item);
            KeepsakeBonds.bindFor(b, bound);
            check(f, !bound.getComponentsPatch().isEmpty(), "bindFor did not stamp " + item);
            PouchRouting.give(b, bound);
            check(f, bound.isEmpty() && Pouch.count(b, item) == 1 && inv(b, item) == 0, "an owner-bound item did not go into the pouch: " + item);
            ItemStack bound2 = new ItemStack(item);
            KeepsakeBonds.bindFor(b, bound2);
            ItemEntity dropB = new ItemEntity(level, b.getX(), b.getY(), b.getZ(), bound2);
            dropB.setNoPickUpDelay();
            NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(b, dropB));
            check(f, Pouch.count(b, item) == (item == StorySetup.SKY_KEY.get() ? 1 : 2) && inv(b, item) == 0,
                    "an owner-bound item pickup was not routed right: " + item);
        }
    }

    // ---------------------------------------------------------------- 画面 (メニューの決まり。サーバー側の論理)
    private static int slotOf(PouchMenu menu, Item item) {
        for (int i = 0; i < menu.pouchSlotCount(); i++) {
            if (menu.entryAt(i).item() == item) {
                return i;
            }
        }
        return -1;
    }

    private static void runScreen(MinecraftServer server, List<String> f, ServerLevel level) {
        Item shard = ModItems.STONE_WHALE_SHARD.get();
        Item crystal = ModItems.RESONANCE_CRYSTAL.get();
        Item core = ModItems.STONE_WHALE_CORE.get();
        FakePlayer p = player(level, "[pouch_screen]");
        PouchMenu menu = new PouchMenu(7, p.getInventory());
        p.containerMenu = menu;
        // 枠の並び: 登録の品ごとに 1 枠、重ならず、画面の中、説明の線より上
        PouchLayout.Result layout = PouchLayout.compute(PouchEntries.enabled());
        check(f, menu.pouchSlotCount() == PouchEntries.enabled().size() && menu.slots.size() == menu.pouchSlotCount(), "the menu does not have one slot per entry");
        check(f, layout.bottom() <= PouchLayout.SEPARATOR_Y - 2, "the pouch slots run into the info area: " + layout.bottom());
        boolean clean = true;
        for (int i = 0; i < layout.spots().size(); i++) {
            PouchLayout.Spot a = layout.spots().get(i);
            clean &= a.x() >= 4 && a.x() + 16 <= PouchLayout.W - 4 && a.y() >= 0 && a.entry() == menu.entryAt(i)
                    && menu.getSlot(i).x == a.x() && menu.getSlot(i).y == a.y();
            for (int j = i + 1; j < layout.spots().size(); j++) {
                PouchLayout.Spot b = layout.spots().get(j);
                clean &= Math.abs(a.x() - b.x()) >= 18 || Math.abs(a.y() - b.y()) >= 18;
            }
        }
        check(f, clean, "the pouch slot positions overlap or leave the panel");
        check(f, layout.labels().size() == Category.values().length, "a category has no label");
        int sh = slotOf(menu, shard);
        int cr = slotOf(menu, crystal);
        int co = slotOf(menu, core);
        check(f, sh >= 0 && cr >= 0 && co >= 0, "the slots for scale/crystal/core are missing");
        // 枠は見かけの品を映す (個数 0 は空、999 まで)
        check(f, menu.getSlot(sh).getItem().isEmpty(), "an empty pouch slot is not empty");
        // 手でも置ける: カーソルの品を左クリックで全部、右クリックで一つ (その枠の品だけ)
        menu.setCarried(new ItemStack(shard, 12));
        menu.clicked(sh, 0, ClickType.PICKUP, p);
        check(f, Pouch.count(p, shard) == 12 && menu.getCarried().isEmpty() && menu.getSlot(sh).getItem().getCount() == 12, "a left click did not put the whole cursor stack in");
        menu.setCarried(new ItemStack(shard, 5));
        menu.clicked(sh, 1, ClickType.PICKUP, p);
        check(f, Pouch.count(p, shard) == 13 && menu.getCarried().getCount() == 4, "a right click did not put exactly one in");
        menu.clicked(cr, 0, ClickType.PICKUP, p);
        check(f, Pouch.count(p, crystal) == 0 && menu.getCarried().getCount() == 4, "a scale was put in the crystal slot");
        menu.setCarried(ItemStack.EMPTY);
        // 999 まで
        Pouch.add(p, new ItemStack(shard, 986));
        check(f, Pouch.count(p, shard) == 999, "could not fill a slot to 999");
        menu.setCarried(new ItemStack(shard, 20));
        menu.clicked(sh, 0, ClickType.PICKUP, p);
        check(f, Pouch.count(p, shard) == 999 && menu.getCarried().getCount() == 20, "the slot took more than 999");
        menu.setCarried(ItemStack.EMPTY);
        check(f, menu.getSlot(sh).getItem().getCount() == 999, "the slot does not show 999");
        // 取り出し (ゲームルール skyabyssPouchWithdraw)。既定は 0 = 取り出せない
        server.getGameRules().getRule(PouchConfig.WITHDRAW_RULE).set(0, server);
        menu.broadcastChanges();
        menu.clicked(sh, 0, ClickType.PICKUP, p);
        menu.clicked(sh, 0, ClickType.QUICK_MOVE, p);
        check(f, menu.getCarried().isEmpty() && Pouch.count(p, shard) == 999 && inv(p, shard) == 0 && !menu.withdrawAllowed(),
                "withdraw 0 still let an item out of the slot");
        check(f, Pouch.withdraw(p, shard, 3, false) == 0 && Pouch.takeOut(p, shard, 3, false).isEmpty(), "withdraw 0 still let the API take an item out");
        // 管理者 (force) は取り出せる
        check(f, Pouch.withdraw(p, shard, 3, true) == 3 && inv(p, shard) == 3 && Pouch.count(p, shard) == 996, "the admin withdraw did not work with withdraw 0");
        p.getInventory().clearContent();
        // 取り出し可 (1): 左クリックで一スタック、右クリックで半分、シフトで持ち物へ。鍵・心核は区分で不可
        server.getGameRules().getRule(PouchConfig.WITHDRAW_RULE).set(1, server);
        menu.broadcastChanges();
        check(f, menu.withdrawAllowed(), "the withdraw flag did not follow the game rule");
        int stack = shard.getDefaultMaxStackSize();
        menu.clicked(sh, 0, ClickType.PICKUP, p);
        ItemStack out = menu.getCarried();
        check(f, out.is(shard) && out.getCount() == stack && out.has(PouchSetup.LOAN.get()) && Pouch.count(p, shard) == 996 - stack,
                "a left click did not take one stack out with the loan mark");
        // 取り出した品を枠へ置き戻す: 印が外れて袋へ戻る
        menu.clicked(sh, 0, ClickType.PICKUP, p);
        check(f, menu.getCarried().isEmpty() && Pouch.count(p, shard) == 996, "putting a withdrawn stack back did not return it to the pouch");
        menu.clicked(sh, 1, ClickType.PICKUP, p);
        check(f, menu.getCarried().getCount() == stack / 2, "a right click did not take half a stack: " + menu.getCarried().getCount());
        menu.setCarried(ItemStack.EMPTY);
        Pouch.add(p, new ItemStack(shard, stack / 2));
        int before = Pouch.count(p, shard);
        menu.clicked(sh, 0, ClickType.QUICK_MOVE, p);
        check(f, inv(p, shard) == stack && Pouch.count(p, shard) == before - stack, "shift-click did not move a stack to the inventory");
        p.getInventory().clearContent();
        Pouch.add(p, new ItemStack(core, 2));
        menu.clicked(co, 0, ClickType.PICKUP, p);
        menu.clicked(co, 0, ClickType.QUICK_MOVE, p);
        check(f, menu.getCarried().isEmpty() && Pouch.count(p, core) == 2 && inv(p, core) == 0, "a core came out of the pouch screen");
        // 枠の外・決まりのない動き (投げる・入れ替え) は何も起こさない
        int now = Pouch.count(p, shard);
        menu.clicked(-999, 0, ClickType.PICKUP, p);
        menu.clicked(sh, 0, ClickType.THROW, p);
        menu.clicked(sh, 0, ClickType.SWAP, p);
        check(f, Pouch.count(p, shard) == now && inv(p, shard) == 0, "an unsupported click changed the pouch");
        // 枠は標準の動きを断る
        check(f, !menu.getSlot(sh).mayPlace(new ItemStack(shard)) && !menu.getSlot(sh).mayPickup(p), "the pouch slot accepts the standard moves");
        check(f, menu.quickMoveStack(p, sh).isEmpty(), "quickMoveStack moved something");
        // 作り直しの一覧を見ている間は、クライアントの画面だけ枠を隠す
        menu.rebuildView = true;
        check(f, !menu.getSlot(sh).isActive(), "the slots stay active in the rebuild view");
        menu.rebuildView = false;
        Pouch.clear(p);
        p.getInventory().clearContent();
    }

    // ---------------------------------------------------------------- 使う入力 (輪・短押し・PouchUse)
    private static void runUse(MinecraftServer server, List<String> f, ServerLevel level) {
        Item cock = jp.revati.skyabyss.titan.storm.StormSetup.WEATHERCOCK.get();
        Item conch = jp.revati.skyabyss.titan.tide.chapter.TideChapterSetup.TIDE_WATCHING_SHELL.get();
        Item page = jp.revati.skyabyss.lore.LoreSetup.LORE_PAGE.get();
        Item shard = ModItems.STONE_WHALE_SHARD.get();
        FakePlayer p = player(level, "[pouch_use]");
        check(f, PouchUsable.usable(p).isEmpty() && PouchUsable.usePouch(p, cock) == PouchUsable.Result.NONE, "an empty pouch has a usable tool");
        check(f, PouchNet.use(p, Optional.empty()) == PouchUsable.Result.NONE, "a short press with no tool did something");
        Pouch.add(p, new ItemStack(cock));
        Pouch.add(p, new ItemStack(conch));
        Pouch.add(p, new ItemStack(shard, 3));
        List<PouchEntries.Entry> usable = PouchUsable.usable(p);
        check(f, usable.size() == 2, "the wheel list is wrong: " + usable.size());
        check(f, usable.stream().noneMatch(e -> e.item() == shard), "a material is in the wheel list");
        // 使う: 右クリックと同じ中身 (条件を満たさなければ断られ、待ちが付く。作り物のプレイヤーは空の洞の中なので断られる)
        PouchUsable.Result first = PouchNet.use(p, Optional.of(PouchEntries.any(cock).id()));
        check(f, first == PouchUsable.Result.REFUSED || first == PouchUsable.Result.OK, "using the weathercock from the pouch did not reach the item: " + first);
        check(f, PouchEntries.any(cock).id().equals(PouchNet.last(p)), "the last tool was not remembered");
        check(f, p.getCooldowns().isOnCooldown(cock), "using from the pouch did not start the item's cooldown (same as right click)");
        check(f, PouchNet.use(p, Optional.empty()) == PouchUsable.Result.COOLDOWN, "a short press did not reuse the last tool (it should be on cooldown)");
        check(f, PouchNet.use(p, Optional.of(PouchEntries.any(shard).id())) == PouchUsable.Result.NONE, "a material was used from the wheel");
        // 袋にない道具・袋が切のとき
        Pouch.take(p, conch, 1);
        check(f, PouchUsable.usePouch(p, conch) == PouchUsable.Result.NONE, "a tool that is not in the pouch was used");
        p.getCooldowns().removeCooldown(cock);
        server.getGameRules().getRule(PouchConfig.MASTER_RULE).set(false, server);
        check(f, PouchUsable.usePouch(p, cock) == PouchUsable.Result.NONE, "the wheel worked with the switch off");
        server.getGameRules().getRule(PouchConfig.MASTER_RULE).set(true, server);
        // 破れた頁: 使えれば袋の数が一つ減り、使えなければ残る
        Pouch.add(p, new ItemStack(page, 2));
        PouchUsable.Result pr = PouchUsable.usePouch(p, page);
        check(f, (pr == PouchUsable.Result.OK && Pouch.count(p, page) == 1) || (pr == PouchUsable.Result.REFUSED && Pouch.count(p, page) == 2),
                "using a page did not consume the right amount: " + pr);
        // 出どころの印のある頁は袋に入らない (持ち物に残り、右クリックで読む)
        ItemStack tagged = jp.revati.skyabyss.lore.LorePageItem.of("village");
        check(f, Pouch.add(p, tagged) == 0 && !tagged.isEmpty(), "a page with its source mark went into the pouch");
        // パケットの往復
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        PouchNet.Use.STREAM_CODEC.encode(buf, new PouchNet.Use(Optional.of(PouchEntries.any(cock).id())));
        PouchNet.Use.STREAM_CODEC.encode(buf, new PouchNet.Use(Optional.empty()));
        PouchNet.Use a = PouchNet.Use.STREAM_CODEC.decode(buf);
        PouchNet.Use b = PouchNet.Use.STREAM_CODEC.decode(buf);
        check(f, a.item().isPresent() && a.item().get().equals(PouchEntries.any(cock).id()) && b.item().isEmpty(), "the PouchUse packet changed on a round trip");
        Pouch.clear(p);
        p.getCooldowns().removeCooldown(cock);
    }

    // ---------------------------------------------------------------- 作り直し・修理
    private static void runRebuild(MinecraftServer server, List<String> f, ServerLevel level) {
        Item core = ModItems.STONE_WHALE_CORE.get();
        Item shard = ModItems.STONE_WHALE_SHARD.get();
        Item call = TravelSetup.STONE_WHALE_CALL.get();
        var all = PouchRebuild.recipes(server.getRecipeManager(), server.registryAccess());
        ResourceLocation callId = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "stone_whale_call");
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(callId)) {
                index = i;
            }
        }
        check(f, index >= 0, "the whistle recipe is not in the rebuild list (" + all.size() + " recipes)");
        // 材料に袋の品を使う作業台のレシピは、全部ここに並ぶ (袋の品を一つも使わないレシピは並ばない)
        for (String name : new String[]{"stone_whale_call", "tide_shell_call", "titan_waymark", "resonance_forge", "cinder_lamp", "firework_rocket_from_spark_dust"}) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name);
            check(f, all.stream().anyMatch(h -> h.id().equals(id)), "a recipe that needs pouch items is missing from the rebuild list: " + name);
        }
        check(f, all.stream().noneMatch(h -> h.id().equals(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "message_board"))),
                "a recipe without pouch items is in the rebuild list");
        List<String> ids = all.stream().map(h -> h.id().toString()).toList();
        check(f, ids.equals(ids.stream().sorted().toList()), "the rebuild list is not in id order (client and server must agree)");
        if (index < 0) {
            return;
        }
        FakePlayer p = player(level, "[pouch_rebuild]");
        var recipe = all.get(index).value();
        // まだ手に入れていない品の作り直しは見せない・作れない
        check(f, !PouchRebuild.visible(p, all.get(index)) && !PouchRebuild.rebuild(p, index), "a recipe for items never obtained was visible or built");
        Pouch.add(p, new ItemStack(core));
        Pouch.add(p, new ItemStack(shard, 5));
        check(f, PouchRebuild.visible(p, all.get(index)), "the recipe stayed hidden after obtaining the items");
        // 骨 (持ち物の品) が足りない
        check(f, PouchRebuild.plan(p, recipe) == null && !PouchRebuild.rebuild(p, index) && Pouch.count(p, core) == 1 && Pouch.count(p, shard) == 5,
                "a rebuild without the bone consumed something");
        p.getInventory().add(new ItemStack(Items.BONE, 2));
        PouchMenu menu = new PouchMenu(9, p.getInventory());
        p.containerMenu = menu;
        check(f, menu.clickMenuButton(p, PouchRebuild.BUTTON_RECIPE_BASE + index), "the menu button did not accept the rebuild");
        check(f, Pouch.count(p, call) == 1 && Pouch.count(p, core) == 0 && Pouch.count(p, shard) == 4 && inv(p, Items.BONE) == 1 && inv(p, call) == 0,
                "the rebuilt whistle or the payment is wrong: call " + Pouch.count(p, call) + " core " + Pouch.count(p, core) + " shard " + Pouch.count(p, shard)
                        + " bone " + inv(p, Items.BONE));
        check(f, !PouchRebuild.rebuild(p, index) && !PouchRebuild.rebuild(p, -1) && !PouchRebuild.rebuild(p, 9999), "a rebuild with no core or a bad index went through");
        Pouch.clear(p);
        // 修理: 手に持った壊れた品を、袋の素材で直す
        ItemStack tool = ItemStack.EMPTY;
        PouchEntries.Entry material = null;
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            ItemStack candidate = new ItemStack(item);
            if (!SkyAbyss.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace()) || !candidate.isDamageableItem()) {
                continue;
            }
            for (PouchEntries.Entry e : PouchEntries.enabled()) {
                if (e.category() == Category.MATERIAL && item.isValidRepairItem(candidate, new ItemStack(e.item()))) {
                    tool = candidate;
                    material = e;
                }
            }
            if (!tool.isEmpty()) {
                break;
            }
        }
        check(f, !tool.isEmpty(), "no mod item can be repaired with a pouch material");
        if (!tool.isEmpty()) {
            tool.setDamageValue(tool.getMaxDamage() / 2);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
            int damaged = tool.getDamageValue();
            check(f, PouchRebuild.repair(p) == 0 && tool.getDamageValue() == damaged, "a repair with no material changed the item");
            Pouch.add(p, new ItemStack(material.item(), 10));
            int repaired = PouchRebuild.repair(p);
            check(f, repaired > 0 && tool.getDamageValue() == damaged - repaired && Pouch.count(p, material.item()) < 10, "the repair did not use the pouch material");
            tool.setDamageValue(0);
            check(f, PouchRebuild.repair(p) == 0, "an undamaged item was repaired");
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        Pouch.clear(p);
        p.getInventory().clearContent();
    }

    // ---------------------------------------------------------------- 作業台が旅嚢の材料を使う (22 の 17 章)
    private static int gridCount(net.minecraft.world.inventory.CraftingMenu menu, Item item) {
        int n = 0;
        for (int i = 1; i <= 9; i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> holder(MinecraftServer server, String name) {
        return (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>)
                server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)).orElseThrow();
    }

    private static net.minecraft.world.inventory.CraftingMenu table(ServerLevel level, FakePlayer p) {
        var menu = new net.minecraft.world.inventory.CraftingMenu(31, p.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(level, BlockPos.ZERO));
        p.containerMenu = menu;
        return menu;
    }

    // 閉じる: バニラの閉じる処理 (枠とカーソルの品を持ち物へ) と、旅嚢の掃除
    private static void closeTable(FakePlayer p, net.minecraft.world.inventory.CraftingMenu menu) {
        menu.removed(p);
        p.containerMenu = p.inventoryMenu;
        Pouch.sweepAfterClose(p);
    }

    private static void unlockAll(FakePlayer p) {
        for (int chapter = 0; chapter <= jp.revati.skyabyss.guide.WayfarerNotes.MAX_CHAPTER; chapter++) {
            jp.revati.skyabyss.guide.WayfarerNotes.unlockRecipes(p, chapter);
        }
    }

    private static void runCraftTable(MinecraftServer server, List<String> f, ServerLevel level) {
        Item scrap = jp.revati.skyabyss.guide.GuideSetup.ROCK_SHELL_SCRAP.get();
        Item thread = jp.revati.skyabyss.guide.GuideSetup.ROCK_SHELL_THREAD.get();
        Item chip = jp.revati.skyabyss.guide.GuideSetup.BONE_CHIP.get();
        Item quill = jp.revati.skyabyss.guide.GuideSetup.HARE_QUILL.get();
        for (Item item : new Item[]{scrap, thread, chip, quill}) {
            check(f, PouchEntries.entry(item) != null && PouchEntries.entry(item).withdrawable(), "a chapter material is not a pouch entry: " + item);
        }
        FakePlayer p = player(level, "[pouch_craft_a]");
        unlockAll(p);
        var helmet = holder(server, "whale_road_helmet");
        var whetstone = holder(server, "rock_whetstone");
        var hardtack = holder(server, "road_hardtack");
        var salve = holder(server, "moss_salve");
        Item helmetItem = jp.revati.skyabyss.guide.TravelGear.HELMET.get();
        // 旅装 (岩殻片 3 を旅嚢に、鉄は持ち物に): レシピブックの置くで枠へ。旅嚢からだけ減り、持ち物には岩殻片が残らない
        Pouch.add(p, new ItemStack(scrap, 20));
        Pouch.add(p, new ItemStack(chip, 5));
        p.getInventory().add(new ItemStack(Items.IRON_INGOT, 4));
        var menu = table(level, p);
        menu.handlePlacement(false, helmet, p);
        check(f, gridCount(menu, scrap) == 3 && gridCount(menu, Items.IRON_INGOT) == 2 && Pouch.count(p, scrap) == 17 && inv(p, scrap) == 0,
                "the helmet was not filled from the pouch: grid scrap " + gridCount(menu, scrap) + " pouch " + Pouch.count(p, scrap) + " inv " + inv(p, scrap));
        check(f, menu.getSlot(0).getItem().is(helmetItem), "the helmet did not show in the result slot");
        menu.quickMoveStack(p, 0);
        check(f, inv(p, helmetItem) == 1 && gridCount(menu, scrap) == 0 && Pouch.count(p, scrap) == 17, "crafting the helmet from the grid went wrong");
        closeTable(p, menu);
        check(f, Pouch.count(p, scrap) == 17 && inv(p, scrap) == 0 && inv(p, Items.IRON_INGOT) == 2, "leftovers after the helmet are wrong");
        // 砥石 (岩殻片 2 + 骨片 + 火打石)。シフト (全部) で置くと、できる数だけの材料が枠に出て、残りは旅嚢へ戻る
        p.getInventory().add(new ItemStack(Items.FLINT, 3));
        menu = table(level, p);
        menu.handlePlacement(true, whetstone, p);
        int k = gridCount(menu, chip);
        check(f, k == 3 && gridCount(menu, scrap) == 6 && Pouch.count(p, chip) == 2 && Pouch.count(p, scrap) == 11 && inv(p, chip) == 0 && inv(p, scrap) == 0,
                "the shift fill of the whetstone is wrong: chip in grid " + k + " pouch chip " + Pouch.count(p, chip) + " scrap " + Pouch.count(p, scrap));
        menu.quickMoveStack(p, 0);
        check(f, inv(p, jp.revati.skyabyss.guide.TravelGear.ROCK_WHETSTONE.get()) > 0, "no whetstone came out of the table");
        closeTable(p, menu);
        check(f, Pouch.count(p, chip) == 5 - inv(p, jp.revati.skyabyss.guide.TravelGear.ROCK_WHETSTONE.get()) / 2 && inv(p, chip) == 0, "bone chips were duplicated or lost after the whetstone: pouch " + Pouch.count(p, chip) + " inv " + inv(p, chip) + " whet " + inv(p, jp.revati.skyabyss.guide.TravelGear.ROCK_WHETSTONE.get()));
        // 閉じるときに枠に残った旅嚢の品は旅嚢へ戻る (増えない)
        p.getInventory().clearContent();
        Pouch.clear(p);
        Pouch.add(p, new ItemStack(chip, 5));
        p.getInventory().add(new ItemStack(Items.WHEAT, 9));
        menu = table(level, p);
        menu.handlePlacement(false, hardtack, p);
        check(f, gridCount(menu, chip) == 1 && gridCount(menu, Items.WHEAT) == 3 && Pouch.count(p, chip) == 4, "the hardtack was not filled from the pouch");
        closeTable(p, menu);
        check(f, Pouch.count(p, chip) == 5 && inv(p, chip) == 0 && inv(p, Items.WHEAT) == 9, "closing the table with pouch items in the grid lost or duplicated items");
        // 軟膏 (苔・骨片・粘土玉)
        p.getInventory().add(new ItemStack(Items.MOSS_BLOCK, 2));
        p.getInventory().add(new ItemStack(Items.CLAY_BALL, 2));
        menu = table(level, p);
        menu.handlePlacement(false, salve, p);
        check(f, menu.getSlot(0).getItem().is(jp.revati.skyabyss.guide.TravelGear.MOSS_SALVE.get()) && gridCount(menu, chip) == 1, "the salve was not filled from the pouch");
        menu.quickMoveStack(p, 0);
        check(f, inv(p, jp.revati.skyabyss.guide.TravelGear.MOSS_SALVE.get()) == 2, "the salve did not come out");
        closeTable(p, menu);
        check(f, Pouch.count(p, chip) == 4 && inv(p, chip) == 0, "the salve took the wrong number of chips");
        // 持ち物がいっぱいでも、出しすぎない (旅嚢 + 持ち物 + 枠の合計が変わらない)
        p.getInventory().clearContent();
        Pouch.clear(p);
        Pouch.add(p, new ItemStack(scrap, 30));
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        }
        menu = table(level, p);
        menu.handlePlacement(false, holder(server, "whale_road_chestplate"), p);
        int total = Pouch.count(p, scrap) + inv(p, scrap) + gridCount(menu, scrap);
        check(f, total == 30, "a full inventory changed the total of scraps: " + total);
        closeTable(p, menu);
        check(f, Pouch.count(p, scrap) + inv(p, scrap) == 30, "a full inventory lost scraps on close");
        p.getInventory().clearContent();
        // 鍵のかかったレシピは旅嚢から出さない
        FakePlayer locked = player(level, "[pouch_craft_locked]");
        Pouch.add(locked, new ItemStack(scrap, 5));
        locked.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
        var lockedMenu = table(level, locked);
        lockedMenu.handlePlacement(false, helmet, locked);
        check(f, Pouch.count(locked, scrap) == 5 && gridCount(lockedMenu, scrap) == 0 && inv(locked, scrap) == 0, "a locked recipe pulled from the pouch");
        closeTable(locked, lockedMenu);
        // 作り直しの一覧にも、鍵のかかったレシピは出ない (解いた人には出る)
        check(f, !PouchRebuild.visible(locked, helmet) && PouchRebuild.visible(p, helmet), "the rebuild list ignored the recipe lock");
        // 手で置く: 帯のクリック (PouchCraft.grid)。左 = 一束、右 = 一つ、同じ品を持っていれば戻す
        Pouch.clear(p);
        Pouch.add(p, new ItemStack(scrap, 100));
        menu = table(level, p);
        ResourceLocation scrapId = PouchEntries.entry(scrap).id();
        check(f, PouchCraft.grid(p, scrapId, false) && menu.getCarried().is(scrap) && menu.getCarried().getCount() == 64 && Pouch.count(p, scrap) == 36, "the strip left click did not take a stack");
        check(f, PouchCraft.grid(p, scrapId, true) && menu.getCarried().getCount() == 63 && Pouch.count(p, scrap) == 37, "the strip right click did not put one back");
        check(f, !PouchCraft.grid(p, PouchEntries.entry(chip).id(), false) && menu.getCarried().getCount() == 63, "the strip took a different item onto a held cursor");
        menu.getSlot(1).set(menu.getCarried().copyWithCount(10));
        menu.setCarried(menu.getCarried().copyWithCount(53));
        closeTable(p, menu);
        check(f, Pouch.count(p, scrap) == 100 && inv(p, scrap) == 0, "the cursor and the grid did not return to the pouch on close: " + Pouch.count(p, scrap) + " inv " + inv(p, scrap));
        // 持ち物の 2x2 でも
        p.containerMenu = p.inventoryMenu;
        check(f, PouchCraft.grid(p, scrapId, false) && p.inventoryMenu.getCarried().getCount() == 64, "the strip did not work with the 2x2 grid");
        check(f, PouchCraft.grid(p, scrapId, false) && Pouch.count(p, scrap) == 100, "putting the whole held stack back failed");
        // 旅嚢の画面・結ばれる品・登録にない品は、帯では動かない
        p.containerMenu = new PouchMenu(33, p.getInventory());
        check(f, !PouchCraft.grid(p, scrapId, false), "the strip worked on the pouch screen");
        p.containerMenu = p.inventoryMenu;
        check(f, !PouchCraft.grid(p, PouchEntries.entry(ModItems.SKY_KEY_FRAGMENT.get()).id(), false), "the strip handled a bonded item");
        check(f, !PouchCraft.grid(p, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "nope"), false), "the strip handled an unknown id");
        // サーバー側では、持ち物の数にだけ頼る (旅嚢を二重に数えない)
        net.minecraft.world.entity.player.StackedContents contents = new net.minecraft.world.entity.player.StackedContents();
        p.getInventory().fillStackedContents(contents);
        check(f, !contents.canCraft(helmet.value(), null), "the server counted the pouch for the recipe book");
        // 二人 (別々の旅嚢・別々の作業台)
        FakePlayer a = player(level, "[pouch_craft_two_a]");
        FakePlayer b = player(level, "[pouch_craft_two_b]");
        for (FakePlayer x : new FakePlayer[]{a, b}) {
            unlockAll(x);
            x.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
        }
        Pouch.add(a, new ItemStack(scrap, 10));
        Pouch.add(b, new ItemStack(scrap, 4));
        var ma = table(level, a);
        var mb = table(level, b);
        ma.handlePlacement(false, helmet, a);
        mb.handlePlacement(false, helmet, b);
        check(f, gridCount(ma, scrap) == 3 && gridCount(mb, scrap) == 3 && Pouch.count(a, scrap) == 7 && Pouch.count(b, scrap) == 1, "two players' tables mixed up their pouches");
        closeTable(a, ma);
        check(f, Pouch.count(a, scrap) == 10 && Pouch.count(b, scrap) == 1 && gridCount(mb, scrap) == 3, "closing one table touched the other player");
        closeTable(b, mb);
        check(f, Pouch.count(b, scrap) == 4 && inv(b, scrap) == 0, "the second player's leftovers are wrong");
        p.getInventory().clearContent();
        Pouch.clear(p);
    }

    // ---------------------------------------------------------------- 既定 (入・取り出し不可)・親スイッチの一度きりの書き換え
    private static void runDefaults(MinecraftServer server, List<String> f) {
        PouchSetup.WorldState state = PouchSetup.WorldState.get(server);
        boolean was = state.ruleApplied;
        GameRules rules = server.getGameRules();
        // 古いワールド (ゲームルールが切で保存されている) は、最初の起動で一度だけ既定 (入) になる
        rules.getRule(PouchConfig.MASTER_RULE).set(false, server);
        rules.getRule(PouchConfig.WITHDRAW_RULE).set(1, server);
        state.ruleApplied = false;
        PouchSetup.onServerStarted(new ServerStartedEvent(server));
        check(f, rules.getBoolean(PouchConfig.MASTER_RULE) && rules.getInt(PouchConfig.WITHDRAW_RULE) == 0 && state.ruleApplied,
                "the first start did not switch the pouch to the new defaults");
        // 管理者が切にしたものは、次の起動で入に戻さない
        rules.getRule(PouchConfig.MASTER_RULE).set(false, server);
        PouchSetup.onServerStarted(new ServerStartedEvent(server));
        check(f, !rules.getBoolean(PouchConfig.MASTER_RULE), "a later start switched the pouch back on");
        rules.getRule(PouchConfig.MASTER_RULE).set(true, server);
        rules.getRule(PouchConfig.WITHDRAW_RULE).set(1, server);
        state.ruleApplied = was;
    }

}
