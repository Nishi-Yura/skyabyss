package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// 控え帳と大事な品の守りの自己テスト (SKYABYSS_SELFTEST=1 の専用サーバー、起動直後に一度。止めるのは TitanBackSelfTest)。
// 中身の符号化、在りかの数え方 (持ち物・袋・エンダーチェスト・預かり・背中の預かり)、変わった値の見つけ方、
// 落ちた品の守り (消えない・燃えない・奈落から戻る・控え)、投げ捨ての拒み、見てよい人の決まりを確かめる
public final class RecordSelfTest {
    private RecordSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        try {
            run(server, failures);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("record selftest crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST record PASSED (snapshot codec, locations, diff, guarded drops, void rescue, toss, give without duplicate, holders, permissions, four titan pages, fragment bonds: home, chest, recall, heart restore, one per person, key)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST record FAILED: {}", f));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        ServerLevel overworld = server.overworld();
        FakePlayer a = FakePlayerFactory.get(overworld, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000b001"), "[record_test_a]"));
        FakePlayer b = FakePlayerFactory.get(overworld, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000b002"), "[record_test_b]"));
        wipe(a);
        a.getEnderChestInventory().clearContent();
        TitanMemoryData memory = TitanMemoryData.get(server);
        memory.takePending(a.getUUID());

        // ---------------------------------------------------------------- 中身と符号化
        RecordSnapshot snap = RecordBuilder.build(a, true);
        int pages = RecordSnapshot.TAB_IDS.size() + jp.revati.skyabyss.progress.Titans.all().size() + DeedPages.GROUPS.size();
        check(f, snap.tabs().size() == pages, "snapshot should have " + pages + " tabs (5, a page per titan and a page per deed group), got " + snap.tabs().size());
        for (String id : RecordSnapshot.TAB_IDS) {
            RecordSnapshot.Tab t = snap.tab(id);
            check(f, t != null && !t.rows().isEmpty(), "tab " + id + " is empty");
            if (t != null) {
                java.util.Set<String> keys = new java.util.HashSet<>();
                for (RecordSnapshot.Row r : t.rows()) {
                    check(f, keys.add(r.key()), "duplicate row key " + id + "/" + r.key());
                }
            }
        }
        // 縁と頁の伝承の行: 序章と一〜四章 (巨神ごと) が章ごとに並び、中身のない五章以降と旧 chapter1 は出ない (05 の 2 章、11 の 3 章)
        RecordSnapshot.Tab bonds = snap.tab(RecordSnapshot.BONDS);
        if (bonds != null) {
            for (jp.revati.skyabyss.story.Chapters c : jp.revati.skyabyss.story.Chapters.values()) {
                check(f, (bonds.row("lore." + c.id()) != null) == !c.reserved(), "lore row for chapter " + c.id() + " should " + (c.reserved() ? "not " : "") + "exist");
            }
            check(f, bonds.row("lore.chapter1") == null, "the old chapter1 lore row must be gone");
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        RecordSnapshot.STREAM_CODEC.encode(buf, snap);
        int bytes = buf.readableBytes();
        RecordSnapshot back = RecordSnapshot.STREAM_CODEC.decode(buf);
        buf.release();
        check(f, back.target().equals(snap.target()) && back.self() == snap.self(), "snapshot header did not survive the codec");
        check(f, RecordSnapshot.diff(snap, back).isEmpty(), "snapshot rows changed through the codec: " + RecordSnapshot.diff(snap, back).size());
        check(f, bytes < 64 * 1024, "snapshot is too large: " + bytes + " bytes");
        SkyAbyss.LOGGER.info("[selftest] record snapshot {} rows, {} bytes", snap.flat().size(), bytes);

        // 見てよい人: 本人は見られる。権限のない人はほかの人を見られない
        check(f, RecordService.mayView(a, a), "a player should see their own ledger");
        check(f, !RecordService.mayView(b, a), "a non-operator could view someone else's ledger");

        // ---------------------------------------------------------------- 在りか
        var fragment = ModItems.SKY_KEY_FRAGMENT.get();
        check(f, Keepsakes.locate(a, fragment).total() == 0 && !Keepsakes.fragmentMissing(a), "an empty player should have no fragment (and TitanLoot never restores by itself)");
        a.getInventory().add(new ItemStack(fragment));
        a.getEnderChestInventory().setItem(3, new ItemStack(fragment, 2));
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(fragment, 3))));
        a.getInventory().add(box);
        memory.pending(a.getUUID()).items.add(new ItemStack(fragment, 4));
        CompoundTag persisted = a.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag kept = new CompoundTag();
        ListTag inv = new ListTag();
        inv.add(new ItemStack(fragment, 5).save(server.registryAccess()));
        kept.put("Inventory", inv);
        persisted.put(Keepsakes.KEPT_TAG, kept);
        a.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        Keepsakes.Where w = Keepsakes.locate(a, fragment);
        check(f, w.inventory() == 1 && w.ender() == 2 && w.bags() == 3 && w.pending() == 4 && w.kept() == 5,
                "locate counted " + w);
        check(f, Keepsakes.keptStacks(a) == 1, "kept stacks should be 1");
        RecordSnapshot.Row row = RecordBuilder.build(a, true).tab(RecordSnapshot.KEEPSAKES).row("fragments");
        check(f, row != null && row.mark() != RecordSnapshot.MARK_LOST && row.mark() != RecordSnapshot.MARK_LOCKED,
                "fragment row should not be lost or locked when fragments are known");
        RecordSnapshot.Row pendingRow = RecordBuilder.build(a, true).tab(RecordSnapshot.KEEPSAKES).row("pending");
        check(f, pendingRow != null && pendingRow.action() == RecordSnapshot.ACTION_CLAIM, "own pending row should offer the claim button");
        RecordSnapshot.Row inspected = RecordBuilder.build(a, false).tab(RecordSnapshot.KEEPSAKES).row("pending");
        check(f, inspected != null && inspected.action() == RecordSnapshot.ACTION_NONE, "an inspected ledger must not offer the claim button");
        persisted.remove(Keepsakes.KEPT_TAG);
        memory.takePending(a.getUUID());
        wipe(a);
        a.getEnderChestInventory().clearContent();

        // ---------------------------------------------------------------- 変わった値
        RecordSnapshot before = RecordBuilder.build(a, true);
        int level = Growth.get(a).level;
        Growth.setLevel(a, level >= 12 ? 3 : 12);
        a.getInventory().add(new ItemStack(fragment));
        List<RecordSnapshot.Change> changes = RecordSnapshot.diff(before, RecordBuilder.build(a, true));
        check(f, changes.stream().anyMatch(c -> c.key().equals("growth/level")), "level change was not found");
        check(f, changes.stream().anyMatch(c -> c.key().equals("keepsakes/fragments")), "fragment change was not found");
        check(f, RecordSnapshot.isVolatile("growth/mana") && !RecordSnapshot.isVolatile("growth/level"), "volatile rows are wrong");
        Growth.setLevel(a, level);
        wipe(a);

        // ---------------------------------------------------------------- 落ちた品の守り
        BlockPos spawn = overworld.getSharedSpawnPos();
        ItemEntity dropped = new ItemEntity(overworld, spawn.getX() + 0.5, overworld.getMaxBuildHeight() - 2, spawn.getZ() + 0.5, new ItemStack(fragment));
        dropped.getPersistentData().putUUID(Keepsakes.OWNER_TAG, a.getUUID());
        overworld.addFreshEntity(dropped);
        check(f, dropped.isInvulnerable() && dropped.getAge() == -32768, "a guarded drop should be invulnerable and never despawn");
        check(f, !dropped.hurt(overworld.damageSources().lava(), 100) && !dropped.hurt(overworld.damageSources().explosion(null, null), 100)
                && !dropped.hurt(overworld.damageSources().cactus(), 100), "a guarded drop took damage");
        check(f, dropped.isAlive(), "a guarded drop died");
        Map<UUID, DropLedger.Drop> ledger = DropLedger.get(server).all();
        check(f, ledger.containsKey(dropped.getUUID()), "the drop was not written in the ledger");
        Keepsakes.Where ground = Keepsakes.locate(a, fragment);
        check(f, ground.groundCount() == 1 && ground.ground().get(0).loaded(), "the ledger should show the drop on the ground: " + ground);
        // 奈落へ
        dropped.setPos(dropped.getX(), overworld.getMinBuildHeight() - 20, dropped.getZ());
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(dropped));
        check(f, dropped.isRemoved(), "a guarded drop in the void was not rescued");
        check(f, !DropLedger.get(server).all().containsKey(dropped.getUUID()), "a rescued drop stayed in the ledger");
        check(f, memory.hasPending(a.getUUID()) && memory.pending(a.getUUID()).items.stream().anyMatch(s -> s.is(fragment)),
                "an absent owner's rescued fragment should be held");
        memory.takePending(a.getUUID());
        // ふつうの品は守らない
        ItemEntity plain = new ItemEntity(overworld, spawn.getX() + 0.5, overworld.getMaxBuildHeight() - 2, spawn.getZ() + 0.5, new ItemStack(Items.DIRT));
        overworld.addFreshEntity(plain);
        check(f, !plain.isInvulnerable() && !DropLedger.get(server).all().containsKey(plain.getUUID()), "a plain item was guarded");
        plain.discard();

        // ---------------------------------------------------------------- 投げ捨て
        wipe(a);
        ItemEntity toss = new ItemEntity(overworld, a.getX(), a.getY(), a.getZ(), new ItemStack(fragment));
        toss.setThrower(a);
        ItemTossEvent tossEvent = new ItemTossEvent(toss, a);
        NeoForge.EVENT_BUS.post(tossEvent);
        check(f, tossEvent.isCanceled() && Keepsakes.locate(a, fragment).safe() == 1, "tossing a fragment should be refused and returned");
        // /give は持ち物に入れたあと、見せかけの品を投げた人なしで落とす。拒んで戻すと 1 つ増えてしまう
        wipe(a);
        a.getInventory().add(new ItemStack(fragment));
        ItemEntity giveShow = new ItemEntity(overworld, a.getX(), a.getY(), a.getZ(), new ItemStack(fragment));
        ItemTossEvent giveEvent = new ItemTossEvent(giveShow, a);
        NeoForge.EVENT_BUS.post(giveEvent);
        check(f, !giveEvent.isCanceled() && Keepsakes.locate(a, fragment).inventory() == 1,
                "the /give display drop should pass untouched (no duplicate), got " + Keepsakes.locate(a, fragment).inventory());
        ItemEntity tossCore = new ItemEntity(overworld, a.getX(), a.getY(), a.getZ(), new ItemStack(ModItems.STONE_WHALE_CORE.get()));
        ItemTossEvent coreEvent = new ItemTossEvent(tossCore, a);
        NeoForge.EVENT_BUS.post(coreEvent);
        check(f, !coreEvent.isCanceled(), "the /give display drop of a core should pass untouched");
        // 心核と呼び笛も、自分で投げれば拒んで戻す
        for (net.minecraft.world.item.Item guardedItem : List.of(ModItems.STONE_WHALE_CORE.get(), jp.revati.skyabyss.titan.travel.TravelSetup.STONE_WHALE_CALL.get())) {
            wipe(a);
            ItemEntity own = new ItemEntity(overworld, a.getX(), a.getY(), a.getZ(), new ItemStack(guardedItem));
            own.setThrower(a);
            ItemTossEvent ownEvent = new ItemTossEvent(own, a);
            NeoForge.EVENT_BUS.post(ownEvent);
            check(f, ownEvent.isCanceled() && Keepsakes.locate(a, guardedItem).safe() == 1, "tossing " + guardedItem + " should be refused and returned");
        }
        wipe(a);

        // ---------------------------------------------------------------- ほかの仕組みの枠 (装飾品のスロット用)
        Keepsakes.registerHolder("accessory", (player, item) -> player == a && item == fragment ? 1 : 0);
        try {
            Keepsakes.Where held = Keepsakes.locate(a, fragment);
            check(f, held.holders().getOrDefault("accessory", 0) == 1 && held.safe() == 1 && held.total() == 1,
                    "a registered holder should count as at hand, got " + held);
            check(f, Keepsakes.locate(b, fragment).holders().isEmpty(), "a holder with nothing should not be listed");
        } finally {
            Keepsakes.unregisterHolder("accessory");
        }
        memory.takePending(a.getUUID());

        titanPages(f, a);
        bonds(server, f, a, b);
    }

    // ---------------------------------------------------------------- 巨神のつまみ: 巨神の一覧 (4 体) の頁が自動で並ぶ
    static void titanPages(List<String> f, FakePlayer a) {
        RecordSnapshot snap = RecordBuilder.build(a, true);
        List<RecordSnapshot.Tab> chips = snap.subTabs(RecordSnapshot.TITANS);
        List<String> titans = jp.revati.skyabyss.progress.Titans.all();
        check(f, chips.size() == titans.size() + 1, "titan tab should have an overview and " + titans.size() + " pages, got " + chips.size());
        for (int i = 0; i < titans.size() && i + 1 < chips.size(); i++) {
            RecordSnapshot.Tab page = chips.get(i + 1);
            check(f, page.id().equals(RecordSnapshot.TITANS + RecordSnapshot.SUB + titans.get(i)), "titan page " + i + " is " + page.id());
            check(f, !page.rows().isEmpty(), "titan page " + page.id() + " is empty");
        }
        RecordSnapshot.Tab overview = snap.tab(RecordSnapshot.TITANS);
        for (String titan : titans) {
            check(f, overview != null && overview.row("titan." + titan) != null, "overview misses " + titan);
            TitanPages.Page page = TitanPages.page(titan);
            check(f, page != null && page.coreItem() != null && !page.collection().get().isEmpty(), "the record page of " + titan + " has no core or collection");
        }
        // 段階 0 の人には、潮殻より先は伏せ字 (名前の翻訳キーが出ない)
        if (jp.revati.skyabyss.progress.Progression.stage(a) == 0) {
            RecordSnapshot.Tab ember = snap.tab(RecordSnapshot.TITANS + RecordSnapshot.SUB + "ember");
            check(f, ember != null && ember.mark() == RecordSnapshot.MARK_LOCKED && ember.row("unknown") != null, "an unreached titan should be hidden");
            check(f, ember != null && !ember.title().toString().contains("short.ember"), "an unreached titan's name leaked into its chip");
        }
        // 4 体ぶんの欠片の行 (一人一つ)
        RecordSnapshot.Tab keep = snap.tab(RecordSnapshot.KEEPSAKES);
        for (String titan : titans) {
            check(f, keep.row("fragment." + titan) != null, "keepsakes misses the fragment row for " + titan);
        }
        check(f, keep.row("sky_key") != null && keep.row("call.stone_whale") != null, "keepsakes misses the key or the whale call");
        SkyAbyss.LOGGER.info("[selftest] record titan pages: {}", chips.stream().map(RecordSnapshot.Tab::id).toList());
    }

    private static int count(FakePlayer p, net.minecraft.world.item.Item item, MinecraftServer server) {
        // 手もとの数: 持ち物 (前の代を除く) + 留め袋
        int n = jp.revati.skyabyss.pouch.Pouch.count(p, item);
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item) && !KeepsakeBonds.stale(server, s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 欠片の結び・戻り方・一人一つ
    static void bonds(MinecraftServer server, List<String> f, FakePlayer a, FakePlayer b) {
        var fragment = ModItems.SKY_KEY_FRAGMENT.get();
        TitanMemoryData memory = TitanMemoryData.get(server);
        BondData bond = BondData.get(server);
        bond.forget(a.getUUID());
        bond.forget(b.getUUID());
        wipe(a);
        wipe(b);
        a.getEnderChestInventory().clearContent();
        memory.takePending(a.getUUID());
        memory.takePending(b.getUUID());
        // a は岩鯨と潮殻の殻片を受け取った (撃破の記録 1)
        TitanMemoryData.Record whale = memory.record(a.getUUID(), "stone_whale");
        TitanMemoryData.Record tide = memory.record(a.getUUID(), "tide_shell");
        boolean[] saved = {whale.fragment, tide.fragment};
        int[] clears = {whale.clears, tide.clears};
        whale.fragment = true;
        tide.fragment = true;
        whale.clears = Math.max(1, whale.clears);
        tide.clears = Math.max(1, tide.clears);
        var level = server.overworld();
        BlockPos chestPos = new BlockPos(level.getSharedSpawnPos().getX(), level.getMaxBuildHeight() - 6, level.getSharedSpawnPos().getZ());
        boolean assembledBefore = jp.revati.skyabyss.story.SkyKey.assembled(a);
        try {
            check(f, KeepsakeBonds.expected(a, KeepsakeBonds.Kind.FRAGMENT) == 2, "a should expect 2 fragments");
            a.getInventory().add(new ItemStack(fragment, 2));
            KeepsakeBonds.sweep(a);
            boolean bound = false;
            for (ItemStack s : a.getInventory().items) {
                if (s.is(fragment) && a.getUUID().equals(KeepsakeBonds.owner(s))) {
                    bound = true;
                }
            }
            check(f, bound, "a's fragments were not bound to a");
            check(f, KeepsakeBonds.missing(a, KeepsakeBonds.Kind.FRAGMENT) == 0, "nothing should be missing with 2 in hand");
            // ほかの人に渡す (箱やホッパー経由): b の見回りで a のもとへ帰る (a は名簿にいないので預かりへ)
            ItemStack one = ItemStack.EMPTY;
            for (ItemStack s : a.getInventory().items) {
                if (s.is(fragment)) {
                    one = s.split(1);
                    break;
                }
            }
            b.getInventory().add(one);
            KeepsakeBonds.sweep(b);
            check(f, b.getInventory().countItem(fragment) == 0, "b kept a's fragment");
            check(f, memory.hasPending(a.getUUID()) && memory.pending(a.getUUID()).items.stream().anyMatch(s -> s.is(fragment)),
                    "a's fragment did not go home to a's held rewards");
            check(f, KeepsakeBonds.missing(a, KeepsakeBonds.Kind.FRAGMENT) == 0, "a fragment sent home should still be counted");
            memory.takePending(a.getUUID());
            // 箱にしまって忘れた: 箱の控えがあれば、なくしたことにはならない
            level.setBlockAndUpdate(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
            ItemStack stash = new ItemStack(fragment);
            KeepsakeBonds.bind(stash, a.getUUID(), bond.gen(a.getUUID(), "fragment"));
            chest.setItem(0, stash);
            var menu = net.minecraft.world.inventory.ChestMenu.threeRows(1, a.getInventory(), chest);
            KeepsakeBonds.noteStash(a, menu);
            Keepsakes.Where w = KeepsakeBonds.where(a, KeepsakeBonds.Kind.FRAGMENT);
            check(f, w.stashCount() == 1 && w.total() == 2, "the chest should be noted as the fragment's place, got " + w);
            check(f, KeepsakeBonds.missing(a, KeepsakeBonds.Kind.FRAGMENT) == 0, "a fragment in a noted chest is not lost");
            // コマンドで消えた (/clear): 手もとの一つを消す → 1 つ足りない
            wipe(a);
            check(f, KeepsakeBonds.missing(a, KeepsakeBonds.Kind.FRAGMENT) == 1, "one cleared fragment should be missing, got "
                    + KeepsakeBonds.missing(a, KeepsakeBonds.Kind.FRAGMENT));
            // 呼び戻す: 手もとにない分 (箱の分も) が戻り、箱の中の前の代は崩れる。合わせて 2 (一人一つ)
            KeepsakeBonds.RecallResult rr = KeepsakeBonds.recall(a, KeepsakeBonds.Kind.FRAGMENT);
            check(f, rr == KeepsakeBonds.RecallResult.RECALLED, "recall was refused");
            check(f, KeepsakeBonds.stale(server, chest.getItem(0)), "the old fragment in the chest should be stale after a recall");
            KeepsakeBonds.noteStash(a, menu);
            check(f, chest.getItem(0).isEmpty(), "the stale fragment in the chest did not crumble when the chest was opened");
            check(f, count(a, fragment, server) == 2, "recall should leave exactly 2 fragments in hand, got " + count(a, fragment, server));
            check(f, KeepsakeBonds.recall(a, KeepsakeBonds.Kind.FRAGMENT) == KeepsakeBonds.RecallResult.NOTHING_MISSING,
                    "a second recall should be refused when nothing is missing");
            // 地面に出た前の代の品は、その場で消える
            ItemStack old = new ItemStack(fragment);
            KeepsakeBonds.bind(old, a.getUUID(), 0);
            ItemEntity oldDrop = new ItemEntity(level, chestPos.getX() + 0.5, chestPos.getY() + 1, chestPos.getZ() + 0.5, old);
            check(f, !level.addFreshEntity(oldDrop), "a stale fragment entered the world");
            // 心臓をもう一度倒した (撃破の記録が増えた): なくしていれば戻る。なくしていなければ何もしない
            KeepsakeBonds.checkHearts(a);
            whale.clears++;
            KeepsakeBonds.checkHearts(a);
            check(f, count(a, fragment, server) == 2, "a re-clear with nothing missing should not add a fragment");
            // 手もとの欠片を一つ消す (袋にあれば袋から。なければ持ち物から)
            if (jp.revati.skyabyss.pouch.Pouch.take(a, fragment, 1) == 0) {
                for (ItemStack s : a.getInventory().items) {
                    if (s.is(fragment)) {
                        s.shrink(1);
                        break;
                    }
                }
            }
            KeepsakeBonds.sweep(a);
            check(f, KeepsakeBonds.missing(a, KeepsakeBonds.Kind.FRAGMENT) == 1, "one fragment should be missing before the re-clear");
            tide.clears++;
            KeepsakeBonds.checkHearts(a);
            check(f, count(a, fragment, server) == 2, "re-stilling a heart should restore the lost fragment to 2, got " + count(a, fragment, server));
            // 天の鍵: そろえた人だけが呼び戻せる。鍵は一つ
            wipe(a);
            jp.revati.skyabyss.story.StoryData.get(server).mark(a.getUUID(), "[record_test_a]", 0);
            check(f, KeepsakeBonds.expected(a, KeepsakeBonds.Kind.FRAGMENT) == 0, "fragments are not expected after the key is made");
            check(f, KeepsakeBonds.missing(a, KeepsakeBonds.Kind.KEY) == 1, "a missing key should be noticed");
            // 語りの見直し: 見た語りだけ、巨神の一覧の頁に「見直す」の行が出る
            String reelKey = RecordSnapshot.REEL_ROW + jp.revati.skyabyss.story.reel.StoryReels.OPENING;
            jp.revati.skyabyss.story.reel.StoryReels.markSeen(a, jp.revati.skyabyss.story.reel.StoryReels.OPENING);
            RecordSnapshot.Row reelRow = RecordBuilder.build(a, true).tab(RecordSnapshot.TITANS).row(reelKey);
            check(f, reelRow != null && reelRow.action() == RecordSnapshot.ACTION_REPLAY, "a seen tale should offer a replay row");
            RecordSnapshot.Row keyRow = RecordBuilder.build(a, true).tab(RecordSnapshot.KEEPSAKES).row("sky_key");
            check(f, keyRow != null && keyRow.action() == RecordSnapshot.ACTION_RECALL_KEY, "the key row should offer a recall");
            KeepsakeBonds.recall(a, KeepsakeBonds.Kind.KEY);
            var key = jp.revati.skyabyss.story.StorySetup.SKY_KEY.get();
            check(f, count(a, key, server) == 1, "recalling the key should give exactly one");
            check(f, KeepsakeBonds.recall(a, KeepsakeBonds.Kind.KEY) == KeepsakeBonds.RecallResult.NOTHING_MISSING && count(a, key, server) == 1,
                    "a second key recall should not add a key");
        } finally {
            if (!assembledBefore) {
                jp.revati.skyabyss.story.StoryData.get(server).clear(a.getUUID());
            }
            if (level.getBlockState(chestPos).is(net.minecraft.world.level.block.Blocks.CHEST)) {
                var be = level.getBlockEntity(chestPos);
                if (be instanceof net.minecraft.world.Container c) {
                    c.clearContent();
                }
                level.removeBlock(chestPos, false);
            }
            whale.fragment = saved[0];
            tide.fragment = saved[1];
            whale.clears = clears[0];
            tide.clears = clears[1];
            wipe(a);
            wipe(b);
            memory.takePending(a.getUUID());
            memory.takePending(b.getUUID());
            bond.forget(a.getUUID());
            bond.forget(b.getUUID());
        }
    }

    // 持ち物と留め袋を空にする (品がなくなったことにしたいとき。袋は持ち物とは別の入れ物なので、袋も空にする)
    private static void wipe(net.minecraft.world.entity.player.Player p) {
        p.getInventory().clearContent();
        jp.revati.skyabyss.pouch.Pouch.clear(p);
    }
}
