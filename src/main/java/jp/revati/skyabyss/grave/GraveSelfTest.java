package jp.revati.skyabyss.grave;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.accessory.AccessoryData;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.quest.StoryContext;
import jp.revati.skyabyss.record.RecordBuilder;
import jp.revati.skyabyss.record.RecordSnapshot;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 倒れたときの決まりと墓の自己テスト (SKYABYSS_SELFTEST=1。SKYABYSS_SELFTEST_ONLY=grave で単独に)。
// 型ごと (grave / vanilla / keep / ledger / keepInventory) の死、大事な品が残ること、墓の中身がちょうどであること、
// 二つにならないこと、持ち主だけが開けられること、語り部の救い、奈落・溶岩・壁の中の死、時間で消える設定、背中の死が変わらないこと
public final class GraveSelfTest {
    private static int platformIndex;

    private static final int PLATFORMS = 12;

    private GraveSelfTest() {
    }

    // 台の位置 (台ごとに東へ 30 ずつ離す)
    private static BlockPos platformCenter(MinecraftServer server, int index) {
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        return new BlockPos(spawn.getX() + 60 + 30 * index, 150, spawn.getZ() + 60);
    }

    // 台のまわりの区画を読み込んでおく。散らばった品は動いている区画にしか現れず、区画が動くのは次の tick からなので、本体の段の前に別の段で行う
    // (既定の世界では、ほかの検査が残した読み込みに助けられて通っていただけだった)
    public static void load(MinecraftServer server) {
        forceAround(server, true);
    }

    private static void forceAround(MinecraftServer server, boolean on) {
        ServerLevel level = server.overworld();
        for (int i = 0; i < PLATFORMS; i++) {
            BlockPos c = platformCenter(server, i);
            for (int cx = (c.getX() - 12) >> 4; cx <= (c.getX() + 12) >> 4; cx++) {
                for (int cz = (c.getZ() - 12) >> 4; cz <= (c.getZ() + 12) >> 4; cz++) {
                    level.setChunkForced(cx, cz, on);
                }
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        DeathRules.Mode mode = DeathRules.mode(server);
        boolean ruleKeep = DeathRules.vanillaKeep(server);
        int[] saved = {server.getGameRules().getInt(DeathRules.MODE), server.getGameRules().getInt(DeathRules.EXPIRE),
                server.getGameRules().getInt(DeathRules.RESCUE), server.getGameRules().getInt(DeathRules.DURABILITY)};
        boolean[] savedBool = {server.getGameRules().getBoolean(DeathRules.KEEP_GEAR), server.getGameRules().getBoolean(DeathRules.OWNER_ONLY)};
        try {
            run(server, failures);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("grave selftest crashed", e);
            failures.add("crashed: " + e);
        } finally {
            forceAround(server, false);
            GameRules rules = server.getGameRules();
            rules.getRule(DeathRules.MODE).set(saved[0], server);
            rules.getRule(DeathRules.EXPIRE).set(saved[1], server);
            rules.getRule(DeathRules.RESCUE).set(saved[2], server);
            rules.getRule(DeathRules.DURABILITY).set(saved[3], server);
            rules.getRule(DeathRules.KEEP_GEAR).set(savedBool[0], server);
            rules.getRule(DeathRules.OWNER_ONLY).set(savedBool[1], server);
            rules.getRule(GameRules.RULE_KEEPINVENTORY).set(ruleKeep, server);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST grave PASSED (rules, grave/vanilla/keep/ledger/keepInventory deaths, important items kept, exact contents, no duplication, owner-only, partial pickup, rescue, void/lava/wall deaths, expire, break spill, titan back unchanged, storyteller dialogue, record row, lang)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST grave FAILED: {}", f));
        }
        SkyAbyss.LOGGER.info("[selftest] grave: started from mode {}", mode);
    }

    // 語り部の救いで、墓を選べる (一覧は古い順。選んだ墓だけ救われる。会話から開いていなければ・人の墓・なくなった墓は断る)
    private static void chooser(MinecraftServer server, ServerLevel level, GraveData data, List<String> f) {
        BlockPos site = platform(level);
        FakePlayer cr = player(level, "[grave_choose]");
        FakePlayer other = player(level, "[grave_choose_other]");
        List<GraveData.Grave> made = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            place(cr, site.offset(i * 4 - 4, 0, 0));
            fill(cr);
            DeathEvents.Outcome o = DeathEvents.handleDeath(cr);
            if (o != null && o.grave() != null) {
                made.add(o.grave());
            }
            DeathEvents.persisted(cr).remove(DeathEvents.KEEP_TAG);
            cr.getInventory().clearContent();
        }
        check(f, made.size() == 3, "the chooser test needs three graves, got " + made.size());
        if (made.size() < 3) {
            return;
        }
        place(other, site.offset(0, 0, 8));
        fill(other);
        DeathEvents.Outcome oo = DeathEvents.handleDeath(other);
        // 会話から開いていなければ断る
        GraveChooser.forget(cr);
        check(f, GraveChooser.pick(cr, made.get(2).id) == null && data.get(made.get(2).id) != null, "picking without the chooser being opened should be refused");
        GraveChooser.open(cr);
        List<GraveNetwork.Entry> entries = GraveChooser.entries(cr);
        check(f, GraveChooser.isOffered(cr) && entries.size() == 3 && entries.get(0).id().equals(made.get(0).id) && entries.get(2).id().equals(made.get(2).id)
                && entries.get(0).items() == made.get(0).itemCount() && entries.get(0).xp() == made.get(0).xp
                && entries.get(0).x() == made.get(0).pos.getX() && entries.get(0).age() >= 0, "the chooser list should show the owner's graves oldest first");
        // 人の墓は選べない
        if (oo != null && oo.grave() != null) {
            check(f, GraveChooser.pick(cr, oo.grave().id) == null && data.get(oo.grave().id) != null, "another player's grave must not be chosen");
        }
        // 真ん中を選ぶ: 古い墓も新しい墓も残る
        Graves.Rescued r = GraveChooser.pick(cr, made.get(1).id);
        check(f, r != null && data.get(made.get(1).id) == null && data.get(made.get(0).id) != null && data.get(made.get(2).id) != null
                && data.owned(cr.getUUID()).size() == 2, "choosing the middle grave should rescue only that one");
        check(f, GraveChooser.isOffered(cr), "the chooser should stay open while graves remain");
        // もうない墓は断る
        check(f, GraveChooser.pick(cr, made.get(1).id) == null && data.owned(cr.getUUID()).size() == 2, "a grave that is already gone must be refused");
        // 救いが 0% なら断る (墓は残る)
        rule(server, DeathRules.RESCUE, 0);
        check(f, GraveChooser.pick(cr, made.get(0).id) == null && data.get(made.get(0).id) != null, "a rescue at 0% must be refused");
        rule(server, DeathRules.RESCUE, 50);
        GraveChooser.open(cr);
        check(f, GraveChooser.pick(cr, made.get(2).id) != null && GraveChooser.pick(cr, made.get(0).id) != null && data.owned(cr.getUUID()).isEmpty(),
                "choosing the rest should empty the list");
        check(f, !GraveChooser.isOffered(cr), "the chooser should close when the list is empty");
        if (oo != null && oo.grave() != null) {
            data.remove(oo.grave().id);
        }
        cr.getInventory().clearContent();
        TitanMemoryData.get(server).takePending(cr.getUUID());
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    // ---------------------------------------------------------------- 道具
    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.getInventory().clearContent();
        return p;
    }

    private static void rule(MinecraftServer server, GameRules.Key<GameRules.IntegerValue> key, int value) {
        server.getGameRules().getRule(key).set(value, server);
    }

    private static void rule(MinecraftServer server, GameRules.Key<GameRules.BooleanValue> key, boolean value) {
        server.getGameRules().getRule(key).set(value, server);
    }

    // 平らな石の台 (15x15、床は y=149) と、その上の空き。台ごとに離す
    private static BlockPos platform(ServerLevel level) {
        BlockPos c = platformCenter(level.getServer(), platformIndex++);
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                for (int y = 140; y <= 158; y++) {
                    level.setBlock(c.offset(dx, y - 150, dz), y < 149 ? Blocks.STONE.defaultBlockState()
                            : y == 149 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return c;
    }

    private static void place(FakePlayer p, BlockPos pos) {
        p.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    // 持ち物: ホットバー 2 (剣・丸石)、主な持ち物 3 (ダイヤ・金のリンゴ・パン)、大事な品 3 (欠片・呼び笛・心核。主な持ち物に)、
    // 防具 (兜)、左手 (盾)、装飾品 (護符)
    private static void fill(FakePlayer p) {
        Inventory inv = p.getInventory();
        inv.clearContent();
        inv.setItem(0, new ItemStack(Items.IRON_SWORD));
        inv.setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        inv.setItem(9, new ItemStack(Items.DIAMOND, 5));
        inv.setItem(10, new ItemStack(Items.GOLDEN_APPLE, 3));
        inv.setItem(20, new ItemStack(Items.BREAD, 16));
        inv.setItem(15, new ItemStack(ModItems.SKY_KEY_FRAGMENT.get()));
        inv.setItem(16, new ItemStack(jp.revati.skyabyss.titan.travel.TravelSetup.STONE_WHALE_CALL.get()));
        inv.setItem(17, new ItemStack(ModItems.STONE_WHALE_CORE.get()));
        inv.setItem(36, new ItemStack(Items.IRON_HELMET));
        inv.setItem(40, new ItemStack(Items.SHIELD));
        AccessoryData data = Accessories.get(p);
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            data.items.set(i, ItemStack.EMPTY);
        }
        data.items.set(0, new ItemStack(ModItems.RESONANCE_CHARM.get()));
        p.setExperienceLevels(0);
        p.setExperienceLevels(10);
    }

    private static Map<String, Integer> tally(Inventory inv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            add(m, inv.getItem(i));
        }
        return m;
    }

    private static void add(Map<String, Integer> m, ItemStack s) {
        if (!s.isEmpty()) {
            m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
    }

    private static Map<String, Integer> tally(List<ItemStack> list) {
        Map<String, Integer> m = new LinkedHashMap<>();
        list.forEach(s -> add(m, s));
        return m;
    }

    private static Map<String, Integer> plus(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> m = new LinkedHashMap<>(a);
        b.forEach((k, v) -> m.merge(k, v, Integer::sum));
        return m;
    }

    private static List<ItemEntity> dropsAround(ServerLevel level, FakePlayer p) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(24));
    }

    private static void clearDrops(ServerLevel level, FakePlayer p) {
        dropsAround(level, p).forEach(e -> e.discard());
    }

    private static boolean noXp(FakePlayer p) {
        LivingExperienceDropEvent drop = new LivingExperienceDropEvent(p, null, 70);
        NeoForge.EVENT_BUS.post(drop);
        return drop.isCanceled();
    }

    private static void respawn(FakePlayer dead, FakePlayer fresh) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(fresh, dead, true));
    }

    private static int count(Inventory inv, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 本体
    static void run(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        GraveData data = GraveData.get(server);

        // ---- ゲームルールの既定
        check(f, DeathRules.MODE != null && DeathRules.KEEP_GEAR != null && DeathRules.OWNER_ONLY != null && DeathRules.EXPIRE != null
                && DeathRules.RESCUE != null && DeathRules.DURABILITY != null, "death game rules were not registered");
        rule(server, DeathRules.MODE, 0);
        rule(server, DeathRules.KEEP_GEAR, true);
        rule(server, DeathRules.OWNER_ONLY, true);
        rule(server, DeathRules.EXPIRE, 0);
        rule(server, DeathRules.RESCUE, 50);
        rule(server, DeathRules.DURABILITY, 10);
        rule(server, GameRules.RULE_KEEPINVENTORY, false);
        check(f, DeathRules.mode(server) == DeathRules.Mode.GRAVE && DeathRules.keepGear(server) && DeathRules.ownerOnly(server)
                && DeathRules.expireMinutes(server) == 0 && DeathRules.rescuePercent(server) == 50 && DeathRules.durabilityPercent(server) == 10,
                "game rule readers do not match the settings");
        rule(server, DeathRules.MODE, 9);
        check(f, DeathRules.mode(server) == DeathRules.Mode.GRAVE, "an out-of-range mode should fall back to grave");
        rule(server, DeathRules.MODE, 0);

        // ---------------------------------------------------------------- grave (既定): 墓の中身がちょうど、残る物が残る
        BlockPos site = platform(level);
        FakePlayer a = player(level, "[grave_a]");
        place(a, site);
        fill(a);
        Map<String, Integer> before = tally(a.getInventory());
        int accessoriesBefore = 1;
        // 本物の死に近い順 (倒れた瞬間 → 経験値の玉 → 散らかし → 新しい体)。結果は預かりと控えから確かめる
        DeathEvents.Outcome oc = DeathEvents.handleDeath(a);
        check(f, oc != null && oc.mode() == DeathRules.Mode.GRAVE, "grave mode death returned " + oc);
        if (oc == null) {
            return;
        }
        GraveData.Grave grave = oc.grave();
        check(f, grave != null, "no grave was made");
        if (grave == null) {
            return;
        }
        check(f, oc.keptStacks() == 7 && oc.graveStacks() == 3 && oc.graveItems() == 24 && oc.xp() == 70,
                "grave split: kept " + oc.keptStacks() + " (want 7: sword, cobblestone, helmet, shield, fragment, call, core), grave " + oc.graveStacks()
                        + " (want 3) items " + oc.graveItems() + " (want 24) xp " + oc.xp() + " (want 70)");
        Map<String, Integer> inGrave = tally(grave.items);
        check(f, inGrave.equals(Map.of("minecraft:diamond", 5, "minecraft:golden_apple", 3, "minecraft:bread", 16)), "grave contents " + inGrave);
        check(f, grave.xp == 70 && noXp(a), "grave xp " + grave.xp + " and the orbs must not drop (cancelled=" + noXp(a) + ")");
        check(f, a.getInventory().isEmpty(), "the dying player's inventory should be empty after the split (nothing for vanilla to scatter)");
        check(f, level.getBlockState(grave.pos).is(ModBlocks.GRAVE.get()), "no grave block at " + grave.pos);
        check(f, Graves.isSafeSpot(level, grave.pos) || level.getBlockState(grave.pos).is(ModBlocks.GRAVE.get()), "grave spot is not safe");
        check(f, data.at(level.dimension(), grave.pos) == grave && data.owned(a.getUUID()).contains(grave), "the ledger does not hold the grave");
        check(f, !Accessories.get(a).items.get(0).isEmpty() && Accessories.keepsOnDeath(a), "accessories should stay with the player in grave mode with keep-gear");
        a.getInventory().dropAll();
        check(f, dropsAround(level, a).isEmpty(), "vanilla scattered " + dropsAround(level, a).size() + " items after the split");
        FakePlayer a2 = player(level, "[grave_a2]");
        respawn(a, a2);
        Map<String, Integer> restored = tally(a2.getInventory());
        check(f, plus(restored, inGrave).equals(before), "no duplication / loss: restored " + restored + " + grave " + inGrave + " != before " + before);
        check(f, count(a2.getInventory(), ModItems.SKY_KEY_FRAGMENT.get()) == 1 && count(a2.getInventory(), jp.revati.skyabyss.titan.travel.TravelSetup.STONE_WHALE_CALL.get()) == 1
                && count(a2.getInventory(), ModItems.STONE_WHALE_CORE.get()) == 1, "important items did not come back to the inventory");
        check(f, a2.getInventory().getItem(0).is(Items.IRON_SWORD) && a2.getInventory().getItem(36).is(Items.IRON_HELMET) && a2.getInventory().getItem(40).is(Items.SHIELD)
                && a2.getInventory().getItem(1).is(Items.COBBLESTONE), "hotbar / armor / offhand slots were not restored in place");
        check(f, a2.getInventory().getItem(0).getDamageValue() == 25 && a2.getInventory().getItem(36).getDamageValue() == 16,
                "durability loss: sword " + a2.getInventory().getItem(0).getDamageValue() + " (want 25), helmet " + a2.getInventory().getItem(36).getDamageValue() + " (want 16)");
        check(f, DeathEvents.persisted(a).getCompound(DeathEvents.KEEP_TAG).isEmpty() && DeathEvents.persisted(a2).getCompound(DeathEvents.KEEP_TAG).isEmpty(),
                "the kept record was not cleared after respawn");

        // ---- 墓を開ける: 持ち主以外は開けられない (既定)。持ち主は全部受け取り、墓は消える
        FakePlayer stranger = player(level, "[grave_stranger]");
        Graves.interact(stranger, level, grave.pos);
        check(f, data.at(level.dimension(), grave.pos) != null && grave.items.size() == 3 && stranger.getInventory().isEmpty(),
                "a stranger opened an owner-only grave");
        check(f, Graves.canOpen(a, grave) && !Graves.canOpen(stranger, grave), "canOpen mismatch");
        FakePlayer owner = a;
        owner.getInventory().clearContent();
        // 持ち物がいっぱい: 入る分だけ受け取り、残りは墓に残る (失わない)
        for (int i = 0; i < 36; i++) {
            owner.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        owner.getInventory().setItem(11, ItemStack.EMPTY);
        Graves.Collected part = Graves.collect(owner, grave);
        check(f, part.items() > 0 && part.leftStacks() > 0 && data.get(grave.id) != null && grave.xp == 0,
                "a full pack should take what fits and leave the rest in the grave: " + part + " (xp is given at once)");
        Map<String, Integer> afterPart = tally(owner.getInventory());
        afterPart.merge("minecraft:dirt", -64 * 35, Integer::sum);
        afterPart.remove("minecraft:dirt");
        check(f, plus(afterPart, tally(grave.items)).equals(inGrave), "partial pickup lost or duplicated items: got " + afterPart + " grave " + tally(grave.items));
        owner.getInventory().clearContent();
        Map<String, Integer> remaining = tally(grave.items);
        Graves.Collected all = Graves.collect(owner, grave);
        check(f, all.leftStacks() == 0 && data.get(grave.id) == null && tally(owner.getInventory()).equals(remaining), "owner pickup: " + all + " " + tally(owner.getInventory()));
        check(f, level.getBlockState(grave.pos).isAir(), "an emptied grave block should be removed");

        // ---- ゲームルールで誰でも開けられる
        rule(server, DeathRules.OWNER_ONLY, false);
        BlockPos site2 = platform(level);
        FakePlayer c = player(level, "[grave_c]");
        place(c, site2);
        fill(c);
        DeathEvents.Outcome oc2 = DeathEvents.handleDeath(c);
        check(f, oc2 != null && oc2.grave() != null, "second grave was not made");
        if (oc2 != null && oc2.grave() != null) {
            check(f, Graves.canOpen(stranger, oc2.grave()), "an open grave should open for anyone");
            stranger.getInventory().clearContent();
            Map<String, Integer> expect = tally(oc2.grave().items);
            Graves.interact(stranger, level, oc2.grave().pos);
            check(f, data.get(oc2.grave().id) == null && tally(stranger.getInventory()).equals(expect), "a stranger could not open an open grave");
        }
        rule(server, DeathRules.OWNER_ONLY, true);
        c.getInventory().dropAll();

        // ---------------------------------------------------------------- 複数の墓と、語り部の救い
        BlockPos site3 = platform(level);
        FakePlayer r = player(level, "[grave_r]");
        List<GraveData.Grave> made = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            place(r, site3.offset(i * 4 - 4, 0, 0));
            fill(r);
            DeathEvents.Outcome o = DeathEvents.handleDeath(r);
            check(f, o != null && o.grave() != null, "grave " + i + " was not made");
            if (o != null && o.grave() != null) {
                made.add(o.grave());
            }
            DeathEvents.persisted(r).remove(DeathEvents.KEEP_TAG);
            r.getInventory().clearContent();
        }
        check(f, data.owned(r.getUUID()).size() == 3, "multiple graves should all stay, got " + data.owned(r.getUUID()).size());
        check(f, made.stream().map(g -> g.pos).distinct().count() == 3, "graves should not share a spot");
        check(f, Graves.rescueAvailable(r), "rescue should be available with graves");
        chooser(server, level, data, f);
        GraveData.Grave oldest = made.get(0);
        int oldestItems = oldest.itemCount();
        Map<String, Integer> oldestTally = tally(oldest.items);
        List<ItemStack> expectShare = Graves.share(oldest.items, 50);
        int shareItems = tally(expectShare).values().stream().mapToInt(Integer::intValue).sum();
        check(f, shareItems == oldestItems * 50 / 100, "share(50%) of " + oldestItems + " gave " + shareItems);
        check(f, Graves.share(oldest.items, 100).stream().mapToInt(ItemStack::getCount).sum() == oldestItems
                && Graves.share(oldest.items, 0).isEmpty(), "share at 100% / 0% is wrong");
        Graves.Rescued rescued = Graves.rescue(r);
        check(f, rescued != null && rescued.items() == shareItems && rescued.xp() == 35 && rescued.lostItems() == oldestItems - shareItems,
                "rescue result " + rescued + " (want " + shareItems + " items, 35 xp, " + (oldestItems - shareItems) + " lost)");
        check(f, data.get(oldest.id) == null && data.owned(r.getUUID()).size() == 2 && level.getBlockState(oldest.pos).isAir(),
                "the rescued grave (the oldest) should be consumed, the others kept");
        Map<String, Integer> got = tally(r.getInventory());
        check(f, tally(expectShare).equals(got), "rescued items " + got + " vs expected " + tally(expectShare));
        for (Map.Entry<String, Integer> e : got.entrySet()) {
            check(f, e.getValue() <= oldestTally.getOrDefault(e.getKey(), 0), "rescue returned more " + e.getKey() + " than the grave held");
        }
        // 持ち物がいっぱい: 戻せない分は報酬の預かりへ (消さない)
        for (int i = 0; i < 36; i++) {
            r.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        TitanMemoryData memory = TitanMemoryData.get(server);
        memory.takePending(r.getUUID());
        GraveData.Grave second = data.owned(r.getUUID()).get(0);
        int secondShare = tally(Graves.share(second.items, 50)).values().stream().mapToInt(Integer::intValue).sum();
        Graves.Rescued full = Graves.rescue(r);
        TitanMemoryData.Pending pending = memory.pending(r.getUUID());
        check(f, full != null && full.toPending() == secondShare && pending.items.stream().mapToInt(ItemStack::getCount).sum() == secondShare,
                "a full pack should send the rescue to pending: " + full + " pending " + pending.items.size());
        memory.takePending(r.getUUID());
        // 救いの割合 0 では使えない (墓は残る)
        rule(server, DeathRules.RESCUE, 0);
        check(f, !Graves.rescueAvailable(r) && Graves.rescue(r) == null && data.owned(r.getUUID()).size() == 1, "rescue at 0% should be off and keep the grave");
        rule(server, DeathRules.RESCUE, 50);
        // 語り部の会話から (条件・結果)
        Dialogue st = DialogueManager.dialogue("skyabyss:storyteller");
        check(f, st != null && st.nodes().containsKey("grave_ask"), "storyteller has no grave node");
        if (st != null) {
            Dialogue.Node menu = st.nodes().get("menu");
            Dialogue.Option menuGrave = menu == null ? null : menu.options().stream().filter(o -> o.id().equals("grave")).findFirst().orElse(null);
            check(f, menuGrave != null && menu.options().size() <= 13, "storyteller menu: grave option " + (menuGrave != null) + ", options "
                    + (menu == null ? -1 : menu.options().size()) + " (max 13)");
            check(f, DialogueManager.loadErrors().stream().noneMatch(e -> e.contains("grave")), "dialogue load errors: " + DialogueManager.loadErrors());
            r.getInventory().clearContent();
            StoryContext ctx = StoryContext.of(r);
            Dialogue.Node ask = st.nodes().get("grave_ask");
            Dialogue.Option yes = ask.options().stream().filter(o -> o.id().equals("yes")).findFirst().orElse(null);
            check(f, menuGrave != null && menuGrave.condition().test(ctx) && yes != null && yes.condition().test(ctx), "the grave option should show while a grave exists");
            if (yes != null) {
                // 「お願いする」は墓の一覧の画面を開いて会話を終える (FakePlayer には送らないが、選べる状態になる)。選ぶと救われる
                yes.actions().forEach(act -> act.run(ctx));
                check(f, yes.target().equals(Dialogue.END) && GraveChooser.isOffered(r) && data.owned(r.getUUID()).size() == 1,
                        "the dialogue should open the chooser, end the talk and keep the grave until one is picked");
                GraveData.Grave last = data.owned(r.getUUID()).get(0);
                check(f, GraveChooser.pick(r, last.id) != null && data.owned(r.getUUID()).isEmpty(), "picking in the chooser did not consume the grave");
                check(f, !GraveChooser.isOffered(r), "the offer should close once no grave is left");
                check(f, !yes.condition().test(ctx) && (menuGrave == null || !menuGrave.condition().test(ctx)), "the grave option should hide once no grave is left");
            }
        }
        // 控え帳の「墓」の行
        FakePlayer rec = player(level, "[grave_rec]");
        RecordSnapshot.Row none = RecordBuilder.build(rec, true).tab(RecordSnapshot.KEEPSAKES).row("grave");
        check(f, none != null && none.mark() == RecordSnapshot.MARK_NONE, "record grave row with no graves: " + none);
        place(rec, site3);
        fill(rec);
        DeathEvents.handleDeath(rec);
        RecordSnapshot.Row row = RecordBuilder.build(rec, true).tab(RecordSnapshot.KEEPSAKES).row("grave");
        check(f, row != null && row.mark() == RecordSnapshot.MARK_ATTENTION && row.detail().size() >= 4, "record grave row with a grave: " + (row == null ? null : row.detail().size()));

        // ---------------------------------------------------------------- 大事な品は、どの型でも手もとに残る
        for (DeathRules.Mode m : DeathRules.Mode.values()) {
            rule(server, DeathRules.MODE, m.ordinal());
            BlockPos s = platform(level);
            FakePlayer p = player(level, "[grave_" + m.name().toLowerCase() + "]");
            place(p, s);
            fill(p);
            Map<String, Integer> was = tally(p.getInventory());
            DeathEvents.Outcome o = DeathEvents.handleDeath(p);
            check(f, o != null && o.mode() == m, m + " death outcome " + o);
            if (o == null) {
                continue;
            }
            boolean cancelsXp = noXp(p);
            p.getInventory().dropAll();
            List<ItemEntity> scattered = dropsAround(level, p);
            Map<String, Integer> scatteredTally = new LinkedHashMap<>();
            scattered.forEach(e -> add(scatteredTally, e.getItem()));
            FakePlayer fresh = player(level, "[grave_fresh_" + m.name().toLowerCase() + "]");
            respawn(p, fresh);
            Map<String, Integer> back = tally(fresh.getInventory());
            Map<String, Integer> inside = o.grave() == null ? Map.of() : tally(o.grave().items);
            // 持ち物の総数 (耐久は変わるが数は変わらない): 残った + 墓 + 散らばった = 倒れる前
            check(f, plus(plus(back, inside), scatteredTally).equals(was), m + ": back " + back + " + grave " + inside + " + scattered " + scatteredTally + " != " + was);
            check(f, back.getOrDefault("skyabyss:sky_key_fragment", 0) == 1 && back.getOrDefault("skyabyss:stone_whale_core", 0) == 1
                    && back.getOrDefault("skyabyss:stone_whale_call", 0) == 1 && !scatteredTally.containsKey("skyabyss:sky_key_fragment")
                    && !inside.containsKey("skyabyss:sky_key_fragment"), m + ": important items were not all kept in hand");
            switch (m) {
                case GRAVE -> check(f, o.grave() != null && inside.size() == 3 && scatteredTally.isEmpty() && cancelsXp, "grave: " + inside + " scattered " + scatteredTally);
                case VANILLA -> check(f, o.grave() == null && back.size() == 3 && scatteredTally.size() == 7 && !cancelsXp && fresh.getInventory().getItem(0).isEmpty(),
                        "vanilla: back " + back + " scattered " + scatteredTally + " xpCancelled " + cancelsXp);
                case KEEP, LEDGER -> check(f, o.grave() == null && scatteredTally.isEmpty() && back.equals(was) && cancelsXp
                        && fresh.experienceLevel == 10 && fresh.getInventory().getItem(0).getDamageValue() == 25,
                        m + ": back " + back + " scattered " + scatteredTally + " level " + fresh.experienceLevel + " sword wear " + fresh.getInventory().getItem(0).getDamageValue());
            }
            if (m == DeathRules.Mode.VANILLA) {
                check(f, !Accessories.keepsOnDeath(p), "vanilla mode should drop accessories");
            } else {
                check(f, Accessories.keepsOnDeath(p), m + " should keep accessories");
            }
            clearDrops(level, p);
        }
        // 耐久の減りは skyabyssDeathDurability で (0 なら減らない。壊れる手前で止まる)
        rule(server, DeathRules.MODE, 2);
        rule(server, DeathRules.DURABILITY, 0);
        FakePlayer w = player(level, "[grave_wear0]");
        place(w, site);
        fill(w);
        DeathEvents.handleDeath(w);
        FakePlayer w2 = player(level, "[grave_wear0b]");
        respawn(w, w2);
        check(f, w2.getInventory().getItem(0).getDamageValue() == 0, "durability rule 0 should not wear the sword");
        rule(server, DeathRules.DURABILITY, 100);
        FakePlayer w3 = player(level, "[grave_wear100]");
        place(w3, site);
        fill(w3);
        DeathEvents.handleDeath(w3);
        FakePlayer w4 = player(level, "[grave_wear100b]");
        respawn(w3, w4);
        check(f, w4.getInventory().getItem(0).getDamageValue() == 249 && w4.getInventory().getItem(0).getCount() == 1, "100% wear must stop before breaking, got "
                + w4.getInventory().getItem(0).getDamageValue());
        rule(server, DeathRules.DURABILITY, 10);

        // ---- 本物の死の流れ: 倒れた瞬間のイベントだけで分かれる (ServerPlayer.die は FakePlayer では何もしないので、イベントを流す)。
        // そのあとバニラの散らかしと経験値の玉、新しい体への引き継ぎ
        rule(server, DeathRules.MODE, 0);
        FakePlayer real = player(level, "[grave_real]");
        place(real, site);
        fill(real);
        Map<String, Integer> realBefore = tally(real.getInventory());
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(real, real.damageSources().genericKill()));
        real.getInventory().dropAll();
        List<GraveData.Grave> realGraves = data.owned(real.getUUID());
        check(f, realGraves.size() == 1 && realGraves.get(0).xp == 70 && noXp(real), "a death event should make one grave with the orbs' xp and cancel the orbs");
        check(f, dropsAround(level, real).isEmpty(), "a death event scattered items");
        FakePlayer realFresh = player(level, "[grave_real_b]");
        respawn(real, realFresh);
        check(f, plus(tally(realFresh.getInventory()), realGraves.isEmpty() ? Map.of() : tally(realGraves.get(0).items)).equals(realBefore),
                "the death event path lost or duplicated items");

        // ---- 装備を残さない設定 (grave): 防具・左手・ホットバー・装飾品も墓へ。大事な品だけ残る
        rule(server, DeathRules.MODE, 0);
        rule(server, DeathRules.KEEP_GEAR, false);
        FakePlayer ng = player(level, "[grave_nogear]");
        place(ng, site);
        fill(ng);
        Map<String, Integer> ngBefore = tally(ng.getInventory());
        DeathEvents.Outcome ngo = DeathEvents.handleDeath(ng);
        check(f, ngo != null && ngo.grave() != null && ngo.keptStacks() == 3 && ngo.accessoryStacks() == 1 && !Accessories.keepsOnDeath(ng)
                && Accessories.get(ng).isEmpty(), "no-gear grave: " + ngo);
        if (ngo != null && ngo.grave() != null) {
            Map<String, Integer> ngGrave = tally(ngo.grave().items);
            FakePlayer ngFresh = player(level, "[grave_nogear_b]");
            respawn(ng, ngFresh);
            ngGrave.merge("skyabyss:resonance_charm", -1, Integer::sum);
            ngGrave.remove("skyabyss:resonance_charm");
            check(f, plus(tally(ngFresh.getInventory()), ngGrave).equals(ngBefore) && ngo.grave().items.stream().anyMatch(s -> s.is(ModItems.RESONANCE_CHARM.get())),
                    "no-gear grave lost or duplicated items");
        }
        rule(server, DeathRules.KEEP_GEAR, true);

        // ---- バニラの keepInventory はバニラのとおり (何もしない)
        rule(server, GameRules.RULE_KEEPINVENTORY, true);
        FakePlayer vk = player(level, "[grave_vkeep]");
        place(vk, site);
        fill(vk);
        Map<String, Integer> vkBefore = tally(vk.getInventory());
        check(f, DeathEvents.handleDeath(vk) == null && tally(vk.getInventory()).equals(vkBefore) && !noXp(vk) && Accessories.keepsOnDeath(vk),
                "keepInventory should leave everything to vanilla");
        rule(server, GameRules.RULE_KEEPINVENTORY, false);

        // ---------------------------------------------------------------- 奈落・溶岩・壁の中
        rule(server, DeathRules.MODE, 0);
        BlockPos vs = platform(level);
        FakePlayer v = player(level, "[grave_void]");
        place(v, vs.offset(2, 0, 3));
        fill(v);
        CompoundTagHolder.setLastSafe(v, level, vs.offset(2, 0, 3));
        v.setPos(vs.getX() + 2.5, level.getMinBuildHeight() - 40, vs.getZ() + 3.5);
        DeathEvents.Outcome vo = DeathEvents.handleDeath(v);
        check(f, vo != null && vo.grave() != null && Math.abs(vo.grave().pos.getY() - vs.getY()) <= 1 && vo.grave().pos.distManhattan(vs.offset(2, 0, 3)) <= 4
                && level.getBlockState(vo.grave().pos).is(ModBlocks.GRAVE.get()) && level.getBlockState(vo.grave().pos.below()).is(Blocks.STONE),
                "a void death should put the grave at the last safe spot: " + (vo == null || vo.grave() == null ? null : vo.grave().pos) + " near " + vs.offset(2, 0, 3));
        // 最後に安全だった場所の記録がなくても、その柱の地表へ
        FakePlayer v2 = player(level, "[grave_void2]");
        v2.setPos(vs.getX() - 3.5, level.getMinBuildHeight() - 40, vs.getZ() - 2.5);
        fill(v2);
        DeathEvents.Outcome vo2 = DeathEvents.handleDeath(v2);
        check(f, vo2 != null && vo2.grave() != null && vo2.grave().pos.getY() >= vs.getY() && vo2.grave().dimension.equals(level.dimension())
                && level.getBlockState(vo2.grave().pos.below()).isSolid(), "a void death without a record should use the surface of the column: "
                + (vo2 == null || vo2.grave() == null ? null : vo2.grave().pos));
        // 溶岩の中
        BlockPos ls = platform(level);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(ls.offset(dx, -1, dz), Blocks.LAVA.defaultBlockState(), 2);
                level.setBlock(ls.offset(dx, -2, dz), Blocks.LAVA.defaultBlockState(), 2);
            }
        }
        FakePlayer lava = player(level, "[grave_lava]");
        place(lava, ls.offset(0, -1, 0));
        fill(lava);
        DeathEvents.Outcome lo = DeathEvents.handleDeath(lava);
        boolean lavaOk = lo != null && lo.grave() != null;
        if (lavaOk) {
            BlockPos gp = lo.grave().pos;
            boolean nearLava = false;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                nearLava |= level.getFluidState(gp.relative(d)).is(net.minecraft.tags.FluidTags.LAVA);
            }
            lavaOk = level.getBlockState(gp).is(ModBlocks.GRAVE.get()) && !nearLava && level.getBlockState(gp.below()).isFaceSturdy(level, gp.below(), net.minecraft.core.Direction.UP);
        }
        check(f, lavaOk, "a lava death should put the grave on solid ground away from lava: " + (lo == null || lo.grave() == null ? null : lo.grave().pos));
        // 壁 (石) の中
        FakePlayer wall = player(level, "[grave_wall]");
        place(wall, vs.offset(0, -3, 0));
        fill(wall);
        DeathEvents.Outcome wo = DeathEvents.handleDeath(wall);
        check(f, wo != null && wo.grave() != null && level.getBlockState(wo.grave().pos).is(ModBlocks.GRAVE.get()) && wo.grave().pos.getY() >= vs.getY(),
                "a death inside stone should put the grave above the surface: " + (wo == null || wo.grave() == null ? null : wo.grave().pos));

        // ---------------------------------------------------------------- 壊れる・時間・控えのない墓
        BlockPos es = platform(level);
        FakePlayer e1 = player(level, "[grave_expire]");
        // 時間で消えない (既定)。古くても控えのまま
        BlockPos old = es.offset(0, 0, 0);
        GraveData.Grave ancient = new GraveData.Grave(UUID.randomUUID(), e1.getUUID(), "x", level.dimension(), old, Graves.now(server) - 100_000_000L, 5,
                new ArrayList<>(List.of(new ItemStack(Items.EMERALD, 7))));
        data.add(ancient);
        level.setBlock(old, ModBlocks.GRAVE.get().defaultBlockState(), 3);
        Graves.patrol(level, old, ModBlocks.GRAVE.get());
        check(f, data.get(ancient.id) != null && level.getBlockState(old).is(ModBlocks.GRAVE.get()), "a grave must not expire when the rule is 0");
        // 時間の設定 (1 分): 来たら中身が足もとに落ち、墓は消える
        rule(server, DeathRules.EXPIRE, 1);
        Graves.patrol(level, old, ModBlocks.GRAVE.get());
        check(f, data.get(ancient.id) == null && level.getBlockState(old).isAir(), "an expired grave should break");
        List<ItemEntity> spilled = level.getEntitiesOfClass(ItemEntity.class, new AABB(old).inflate(3));
        check(f, spilled.size() == 1 && spilled.get(0).getItem().is(Items.EMERALD) && spilled.get(0).getItem().getCount() == 7, "expired grave spill: " + spilled.size());
        spilled.forEach(ItemEntity::discard);
        rule(server, DeathRules.EXPIRE, 0);
        // 壊された (クリエイティブ・コマンド): 中身は足もとに落ちる (失わない)
        BlockPos br = es.offset(4, 0, 0);
        GraveData.Grave breakable = new GraveData.Grave(UUID.randomUUID(), e1.getUUID(), "x", level.dimension(), br, Graves.now(server), 0,
                new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT, 3))));
        data.add(breakable);
        level.setBlock(br, ModBlocks.GRAVE.get().defaultBlockState(), 3);
        level.setBlock(br, Blocks.AIR.defaultBlockState(), 3);
        List<ItemEntity> broken = level.getEntitiesOfClass(ItemEntity.class, new AABB(br).inflate(3));
        check(f, data.get(breakable.id) == null && broken.size() == 1 && broken.get(0).getItem().getCount() == 3, "a broken grave should spill its items: " + broken.size());
        broken.forEach(ItemEntity::discard);
        // 控えのない墓 (語り部の救いのあとにチャンクが読み込まれていなかった墓): 見回りで消える
        BlockPos orphan = es.offset(-4, 0, 0);
        level.setBlock(orphan, ModBlocks.GRAVE.get().defaultBlockState(), 3);
        Graves.patrol(level, orphan, ModBlocks.GRAVE.get());
        check(f, level.getBlockState(orphan).isAir() && level.getEntitiesOfClass(ItemEntity.class, new AABB(orphan).inflate(3)).isEmpty(), "an orphan grave block should vanish");
        // 墓の上を壊す・流体: 墓は爆発や液体で消えない (耐性と形)
        check(f, ModBlocks.GRAVE.get().defaultBlockState().getDestroySpeed(level, orphan) < 0 && ModBlocks.GRAVE.get().getExplosionResistance() > 1000,
                "the grave must be unbreakable and explosion-proof");
        check(f, ModBlocks.GRAVE.get().defaultBlockState().getLightEmission() > 0, "the grave should glow faintly");

        // ---------------------------------------------------------------- 背中の死は今のまま
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null) {
            FakePlayer bp = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "[grave_back]"));
            bp.getInventory().clearContent();
            fill(bp);
            Map<String, Integer> bBefore = tally(bp.getInventory());
            int gravesBefore = data.all().size();
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(bp, bp.damageSources().genericKill()));
            check(f, DeathEvents.handleDeath(bp) == null && data.all().size() == gravesBefore && !DeathEvents.persisted(bp).contains(DeathEvents.KEEP_TAG),
                    "a death on the titan back must not use graves");
            check(f, DeathEvents.persisted(bp).contains("skyabyss_kept") && tally(bp.getInventory()).equals(bBefore) && noXp(bp),
                    "a titan back death should still hold everything for the rest point (inventory untouched, orbs cancelled)");
            check(f, Accessories.keepsOnDeath(bp), "accessories are kept on the titan back");
        } else {
            SkyAbyss.LOGGER.warn("[selftest] grave: titan back level missing, back death check skipped");
        }

        // ---------------------------------------------------------------- 文 (日英が同じキー)
        Set<String> ja = langKeys("ja_jp");
        Set<String> en = langKeys("en_us");
        check(f, !ja.isEmpty() && ja.equals(en), "grave lang keys differ: ja-only " + diff(ja, en) + " en-only " + diff(en, ja));
        for (String key : List.of("block.skyabyss.grave", "skyabyss_grave.made", "skyabyss_grave.rescued", "skyabyss_record.r.grave", "skyabyss_record.d.grave_item",
                "dialogue.skyabyss.storyteller.menu.grave", "dialogue.skyabyss.storyteller.grave_ask.yes")) {
            check(f, ja.contains(key), "missing lang key " + key);
        }
        // 後片付け: ここで作った墓を消す
        for (GraveData.Grave g : new ArrayList<>(data.all())) {
            if (g.dimension.equals(level.dimension()) && g.ownerName.startsWith("[grave_")) {
                data.remove(g.id);
            }
        }
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> d = new HashSet<>(a);
        d.removeAll(b);
        return d;
    }

    private static Set<String> langKeys(String name) {
        try (InputStream in = GraveSelfTest.class.getResourceAsStream("/assets/skyabyss_grave/lang/" + name + ".json")) {
            if (in == null) {
                return Set.of();
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new HashSet<>(o.keySet());
        } catch (Exception e) {
            return Set.of();
        }
    }

    // 最後に安全だった場所を書く (本番では 1 秒ごとの見回りが書く)
    private static final class CompoundTagHolder {
        static void setLastSafe(FakePlayer p, ServerLevel level, BlockPos pos) {
            net.minecraft.nbt.CompoundTag data = p.getPersistentData();
            if (!data.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) {
                data.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
            }
            net.minecraft.nbt.CompoundTag safe = new net.minecraft.nbt.CompoundTag();
            safe.putString("dim", level.dimension().location().toString());
            safe.putLong("pos", pos.asLong());
            data.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).put(DeathEvents.SAFE_TAG, safe);
        }
    }
}
