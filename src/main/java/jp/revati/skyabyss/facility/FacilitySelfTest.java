package jp.revati.skyabyss.facility;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.guild.GuildSelfTest;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.quest.Action;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.StoryContext;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;

// 都の施設 (docs/design/35 の F2・F3) の自己テスト。段: "facility" (係の人・鐘銭・店・預かり所・宿・修理)、"facility guild" (GuildSelfTest)、
// "facility extras" (章で開く施設と訓練場。FacilityExtrasSelfTest)、"facility trade" (取引所。trade/TradeSelfTest)。
//   絵: 3 案の形・骨だけの geo・役ごとの 15 枚の絵・対話の顔・施設の印・鐘銭の絵と形
//   会話: 15 人の会話がそろい、初めては一言の説明から、二度目からはできることの選択肢。施設の結果 (facility) の名前を読み込みで調べる
//   立たせる: 印 npc_<役> に立ち、二人目は立たせず、離れたら戻す。役と見た目の案を保存する。読む印の名前 (ours)
//   鐘銭: 渡す・数える・払う、敵の落とし物の割合 (章で増える)、言伝ての板の報酬
//   店: 品ぞろえの読み込み (エメラルドなし・道具屋と薬師の基本の品)、買う (銭・素材・章・日の限り)、売る、闇市の日替わり (日で決まる 4 品)
//   預かり所: 預けられない品 (鍵・笛・書)、保存と読み直し
//   宿: 寝台の印がなければ女将の足もと。修理の値段と払い
public final class FacilitySelfTest {
    private FacilitySelfTest() {
    }

    static BlockPos testBase(ServerLevel level) {
        // 世界の真ん中の側へ 512 (大陸の世界は出現地点が南西の島なので、外へ向けると世界の縁の外に出る)
        BlockPos spawn = level.getSharedSpawnPos();
        return spawn.offset(spawn.getX() > 0 ? -512 : 512, 0, spawn.getZ() > 0 ? -512 : 512);
    }

    static void load(net.minecraft.server.MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos base = testBase(level);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced((base.getX() >> 4) + dx, (base.getZ() >> 4) + dz, true);
            }
        }
    }

    public static List<SelfTestRunner.Step> steps() {
        // 先に試しの場所 (出現地点から 512 離れた所。都の本物の係の人と混ざらない) のチャンクを読み込み、生き物が動くまで待つ
        // (大陸の世界は試しの場所が海の上の新しいチャンクで、作るのに時間がかかる。生き物が動くまで待つ。30 秒で諦めて進む)
        int[] waited = {0};
        return List.of(SelfTestRunner.Step.once("facility (load)", FacilitySelfTest::load),
                SelfTestRunner.Step.until("facility (wait)", server -> server.overworld().isPositionEntityTicking(testBase(server.overworld())) || ++waited[0] > 600),
                SelfTestRunner.Step.once("facility", 20, FacilitySelfTest::run),
                SelfTestRunner.Step.once("facility guild", GuildSelfTest::run),
                SelfTestRunner.Step.once("facility extras", FacilityExtrasSelfTest::run),
                SelfTestRunner.Step.once("facility trade", jp.revati.skyabyss.facility.trade.TradeSelfTest::run));
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "facility_p"));
        try {
            assets(f);
            dialogues(f, level, p);
            attendants(f, level);
            coins(f, p);
            shops(f, p);
            stash(f, server, p);
            inn(f, level, p);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST facility crashed", e);
            f.add("crashed: " + e);
        } finally {
            p.getInventory().clearContent();
            jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST facility PASSED (assets 3 looks x 16 roles, dialogues, attendant spawn/keep/save, coins, shops buy/sell/rotation, stash, inn, repair)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST facility FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 絵
    private static boolean resource(String path) {
        try (InputStream in = FacilitySelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void assets(List<String> f) {
        String a = "/assets/skyabyss/";
        for (String look : AttendantLook.LOOKS) {
            check(f, resource(a + "lowpoly/attendant_" + look + ".json"), "mesh attendant_" + look);
            check(f, resource(a + "geo/entity/attendant_" + look + "_lowpoly.geo.json"), "geo attendant_" + look);
            for (AttendantRole r : AttendantRole.values()) {
                check(f, resource(a + "textures/lowpoly/" + AttendantLook.texture(look.charAt(0) - 'a' + 1, r.id) + ".png"), "texture " + look + " " + r.id);
            }
        }
        for (AttendantRole r : AttendantRole.values()) {
            for (String face : Dialogue.FACES) {
                check(f, resource(a + "textures/gui/npc/face/attendant_" + r.id + "_" + face + ".png"), "face " + r.id + " " + face);
            }
            check(f, resource(a + "textures/gui/npc_marker/fac_" + r.id + ".png"), "facility icon " + r.id);
        }
        try (InputStream in = FacilitySelfTest.class.getResourceAsStream(a + "animations/entity/attendant.animation.json")) {
            String json = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var anims = in == null ? null : JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("animations");
            for (String n : List.of("idle", "walk", "talk")) {
                check(f, anims != null && anims.has("animation.attendant." + n), "animation.attendant." + n);
            }
        } catch (java.io.IOException | RuntimeException e) {
            f.add("animation unreadable " + e);
        }
        check(f, resource(a + "textures/item/bell_coin.png"), "bell coin texture");
        check(f, resource(a + "models/item/bell_coin.json"), "bell coin model");
        check(f, AttendantLook.look(0).equals(AttendantLook.DEFAULT) && AttendantLook.look(2).equals(AttendantLook.DEFAULT), "look by variant");
    }

    // ---------------------------------------------------------------- 会話
    private static void dialogues(List<String> f, ServerLevel level, FakePlayer p) {
        for (String e : DialogueManager.loadErrors()) {
            if (e.contains("attendant")) {
                f.add("dialogue load error: " + e);
            }
        }
        for (AttendantRole r : AttendantRole.values()) {
            Dialogue d = DialogueManager.dialogue(r.dialogueRole());
            if (d == null) {
                f.add("no dialogue " + r.dialogueRole());
                continue;
            }
            check(f, AttendantRole.byDialogueRole(r.dialogueRole()) == r, "role by dialogue " + r.id);
            NpcBonds.of(p).bond(r.dialogueRole()).flags().remove("intro");
            StoryContext ctx = new StoryContext(p, r.dialogueRole(), null);
            Dialogue.Node first = d.entryNode(ctx);
            // 都の外 (土地の町) では intro_town (都にしかない物の名を出さない。45 の 5 章)
            check(f, first != null && (first.id().equals("intro") || first.id().equals("intro_town")), r.id + ": first talk opens intro");
            NpcBonds.of(p).bond(r.dialogueRole()).flags().add("intro");
            Dialogue.Node menu = d.entryNode(ctx);
            check(f, menu != null && menu.id().equals("menu") && menu.options().size() >= 3, r.id + ": then the menu");
            // 店・預かり所の役は、その働きの選択肢を持つ
            String need = r.shop != null ? "shop" : switch (r) {
                case STORAGE_KEEPER -> "stash";
                case INNKEEPER -> "inn_rest";
                case PERCH_KEEPER -> "perch";
                case GUILD_CLERK -> "guild";
                case SMITH -> "repair";
                case TRADE_KEEPER -> "trade";
                case STABLE_KEEPER -> "mount_shop";
                case BOX_OPENER -> "boxes";
                default -> null;
            };
            if (need != null && menu != null) {
                boolean has = menu.options().stream().anyMatch(o -> o.actions().stream().anyMatch(a -> a.type().equals("facility") && a.str("open").equals(need)));
                check(f, has, r.id + ": menu opens " + need);
            }
            NpcBonds.of(p).bond(r.dialogueRole()).flags().remove("intro");
        }
        List<String> errors = new ArrayList<>();
        com.google.gson.JsonObject bad = new com.google.gson.JsonObject();
        bad.addProperty("type", "facility");
        bad.addProperty("open", "nope");
        Action.parseList(bad, errors, "test");
        check(f, !errors.isEmpty(), "unknown facility is a load error");
    }

    // ---------------------------------------------------------------- 立たせる
    private static void attendants(List<String> f, ServerLevel level) {
        check(f, Attendants.ours("npc_smith") && Attendants.ours("inn_bed") && Attendants.ours("inn_bed_2"), "reads npc_<role> and inn_bed marks");
        check(f, !Attendants.ours("npc_nobody") && !Attendants.ours("villager_1"), "ignores other marks");
        check(f, AttendantRole.byMark("npc_tool_merchant") == AttendantRole.TOOL_MERCHANT, "role by mark");
        // 出現地点のまわり (始めから読み込まれている) の空の上
        // 都 (世界の出発点にある) の本物の係の人と混ざらないよう、512 離れた所のチャンクを読み込んで使う
        BlockPos base = testBase(level);
        BlockPos spot = new BlockPos(base.getX() + 3, level.getMaxBuildHeight() - 20, base.getZ() + 3);
        AttendantEntity a = Attendants.ensure(level, AttendantRole.SMITH, spot);
        if (a == null) {
            f.add("ensure did not place");
            return;
        }
        try {
            check(f, a.role().equals(AttendantRole.SMITH.dialogueRole()), "attendant role id " + a.role());
            check(f, a.getCustomName() != null, "attendant is named");
            AttendantEntity again = Attendants.ensure(level, AttendantRole.SMITH, spot);
            check(f, again == a, "second ensure keeps the same attendant");
            a.teleportTo(spot.getX() + 10.5, spot.getY(), spot.getZ() + 0.5);
            Attendants.ensure(level, AttendantRole.SMITH, spot);
            check(f, a.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) < 2, "drifted attendant is brought back");
            AttendantEntity other = Attendants.ensure(level, AttendantRole.APOTHECARY, spot.offset(2, 0, 0));
            check(f, other != null && other != a, "another role gets its own attendant");
            if (other != null) {
                other.discard();
            }
            // 保存と読み直し (役と見た目)
            a.setVariant(3);
            CompoundTag tag = new CompoundTag();
            a.saveWithoutId(tag);
            AttendantEntity copy = FacilitySetup.ATTENDANT.get().create(level);
            copy.load(tag);
            check(f, copy.attendantRole() == AttendantRole.SMITH && copy.variant() == 3, "role and look survive save");
            // 印のない役は飛ばす (ensureAll は知らない名前を数えない)
            Map<String, BlockPos> marks = new HashMap<>();
            marks.put("npc_nobody", spot);
            check(f, Attendants.ensureAll(level, marks) == 0, "unknown marks are skipped");
        } finally {
            a.discard();
        }
    }

    // ---------------------------------------------------------------- 鐘銭
    private static void coins(List<String> f, FakePlayer p) {
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        Coins.give(p, 150);
        check(f, Coins.count(p) == 150, "give/count coins " + Coins.count(p));
        check(f, Coins.take(p, 40) && Coins.count(p) == 110, "take coins");
        check(f, !Coins.take(p, 500) && Coins.count(p) == 110, "cannot take more than held");
        check(f, Coins.dropChance(1) > 0 && Coins.dropChance(5) > Coins.dropChance(1) && Coins.dropChance(5) <= 0.2f, "drop chance grows by chapter");
        for (int i = 0; i < 20; i++) {
            int n = Coins.dropCount(3, p.getRandom());
            check(f, n >= 1 && n <= 3, "drop count within chapter " + n);
        }
        QuestDef board = null;
        QuestDef notBoard = null;
        for (QuestDef d : QuestManager.all()) {
            if (d.board() && board == null) {
                board = d;
            }
            if (!d.board() && notBoard == null) {
                notBoard = d;
            }
        }
        check(f, board != null && Coins.errandReward(board, 1) > 0, "board errands pay coins");
        check(f, board == null || Coins.errandReward(board, 4) > Coins.errandReward(board, 1), "errand coins grow by chapter");
        check(f, notBoard == null || Coins.errandReward(notBoard, 1) == 0, "story quests do not pay coins automatically");
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
    }

    // ---------------------------------------------------------------- 店
    private static void shops(List<String> f, FakePlayer p) {
        for (String e : ShopManager.loadErrors()) {
            f.add("shop load error: " + e);
        }
        for (String id : List.of("tool_shop", "food_shop", "outfit_shop", "material_shop", "apothecary", "black_market")) {
            ShopDef s = ShopManager.shop(id);
            if (s == null) {
                f.add("no shop " + id);
                return;
            }
            for (Item item : s.allItems()) {
                check(f, item != Items.EMERALD, id + " uses emeralds");
            }
        }
        // 市の四人 (2026-10-27): 道具屋 = 灯りと寝袋、食料屋 = パン、武具屋 = 矢、素材屋 = 素材の値段と買い取り
        ShopDef toolShop = ShopManager.shop("tool_shop");
        ShopDef tools = ShopManager.shop("food_shop");
        ShopDef outfit = ShopManager.shop("outfit_shop");
        ShopDef mats = ShopManager.shop("material_shop");
        check(f, toolShop.goods().stream().anyMatch(g -> g.item() == Items.TORCH) && toolShop.goods().stream().anyMatch(g -> g.item() == Items.WHITE_BED), "tool shop sells light and beds");
        check(f, tools.goods().stream().anyMatch(g -> g.item() == jp.revati.skyabyss.item.outfit.ConsumableGear.TOWN_BREAD.get()), "food vendor sells bread");
        check(f, outfit.goods().stream().anyMatch(g -> g.item() == jp.revati.skyabyss.item.outfit.ConsumableGear.TRAVEL_ARROW.get()), "outfitter sells arrows");
        check(f, toolShop.goods().stream().noneMatch(g -> g.item() == jp.revati.skyabyss.item.outfit.ConsumableGear.TOWN_BREAD.get() || g.item() == Items.IRON_SWORD), "tool shop no longer sells food or swords");
        ShopDef apo = ShopManager.shop("apothecary");
        // 薬は mod の飲み薬 (2026-10-27。バニラのポーションは売らない)
        check(f, apo.goods().stream().anyMatch(g -> g.item() == jp.revati.skyabyss.item.outfit.ConsumableGear.HEALING.get()), "apothecary sells healing");
        check(f, apo.goods().stream().anyMatch(g -> g.item() == jp.revati.skyabyss.item.outfit.ConsumableGear.NIGHT_EYE.get()), "apothecary sells night vision");
        check(f, apo.goods().stream().anyMatch(g -> g.item() == jp.revati.skyabyss.item.outfit.ConsumableGear.ANTIDOTE.get()), "apothecary sells an antidote");
        check(f, apo.goods().stream().noneMatch(g -> g.item() == Items.POTION || g.item() == Items.MILK_BUCKET), "apothecary still sells vanilla potions");

        p.getInventory().clearContent();

        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        Coins.give(p, 30);
        check(f, ShopService.buy(p, tools, "bread", 1) == ShopService.Result.OK, "buy bread");
        check(f, Coins.count(p) == 27 && p.getInventory().countItem(jp.revati.skyabyss.item.outfit.ConsumableGear.TOWN_BREAD.get()) == 3, "bread paid and given");
        check(f, ShopService.buy(p, toolShop, "bedroll", 3) == ShopService.Result.NO_COINS && Coins.count(p) == 27, "cannot overspend");
        check(f, ShopService.buy(p, tools, "nope", 1) == ShopService.Result.NO_GOOD, "unknown good");
        // 素材の値段
        check(f, ShopService.buy(p, mats, "resonance", 1) == ShopService.Result.NO_MATERIALS, "material price needs materials");
        p.getInventory().add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 3));
        int before = jp.revati.skyabyss.quest.QuestItems.count(p, "skyabyss:resonance_shard", null);
        check(f, ShopService.buy(p, mats, "resonance", 1) == ShopService.Result.OK
                && jp.revati.skyabyss.quest.QuestItems.count(p, "skyabyss:resonance_shard", null) == before + 1
                && jp.revati.skyabyss.quest.QuestItems.count(p, "skyabyss:stone_whale_shard", null) == 0, "pay with titan materials");
        // 章の届かない品
        check(f, ShopService.buy(p, mats, "socket_stone", 1) == ShopService.Result.LOCKED, "chapter-locked goods");
        // 売る
        p.getInventory().add(new ItemStack(jp.revati.skyabyss.guide.GuideSetup.BONE_CHIP.get(), 9));
        int idx = -1;
        for (int i = 0; i < mats.buys().size(); i++) {
            if (mats.buys().get(i).item() == jp.revati.skyabyss.guide.GuideSetup.BONE_CHIP.get()) {
                idx = i;
            }
        }
        int coins = Coins.count(p);
        check(f, idx >= 0 && ShopService.sell(p, mats, idx, 99) == ShopService.Result.OK, "sell bone chips");
        check(f, Coins.count(p) == coins + 2 && p.getInventory().countItem(jp.revati.skyabyss.guide.GuideSetup.BONE_CHIP.get()) == 1,
                "sold two lots, one chip left");
        check(f, ShopService.sell(p, mats, idx, 1) == ShopService.Result.NO_MATERIALS, "cannot sell a partial lot");
        ItemStack named = new ItemStack(Items.BONE, 8);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("keep"));
        check(f, !ShopService.plain(named), "named items are not sold");
        // 闇市の日替わり
        ShopDef black = ShopManager.shop("black_market");
        List<ShopDef.Good> d1 = black.today(10);
        check(f, d1.size() == 4, "black market shows 4 goods " + d1.size());
        check(f, d1.equals(black.today(10)), "black market rotation is fixed for the day");
        boolean differs = false;
        for (long d = 11; d < 20; d++) {
            differs |= !black.today(d).equals(d1);
        }
        check(f, differs, "black market rotation changes over days");
        ShopDef.Good limited = d1.stream().filter(g -> g.chapter() <= 1 && g.limit() == 1).findFirst().orElse(null);
        if (limited != null) {
            ShopLedger ledger = p.getData(FacilitySetup.LEDGER);
            ledger.add(black.id(), limited.id(), 10, 1);
            check(f, ledger.bought(black.id(), limited.id(), 10) == 1 && ledger.bought(black.id(), limited.id(), 11) == 0, "ledger counts per day");
            ledger.add(black.id(), limited.id(), 11, 1);
            check(f, ledger.bought(black.id(), limited.id(), 10) == 0, "old days are forgotten");
        }
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
    }

    // ---------------------------------------------------------------- 預かり所
    private static void stash(List<String> f, MinecraftServer server, FakePlayer p) {
        check(f, StashMenu.allowed(new ItemStack(Items.COBBLESTONE)), "stash takes plain items");
        check(f, StashMenu.allowed(new ItemStack(ModItems.STONE_WHALE_SHARD.get())), "stash takes titan materials");
        check(f, !StashMenu.allowed(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get())), "stash refuses sky key fragments");
        check(f, !StashMenu.allowed(new ItemStack(jp.revati.skyabyss.titan.travel.TravelSetup.STONE_WHALE_CALL.get())), "stash refuses titan calls");
        check(f, !StashMenu.allowed(new ItemStack(jp.revati.skyabyss.lore.LoreSetup.LORE_BOOK.get())), "stash refuses the lore book");
        StashData data = new StashData();
        data.items().set(0, new ItemStack(Items.IRON_INGOT, 12));
        data.items().set(53, new ItemStack(FacilitySetup.BELL_COIN.get(), 40));
        StashData copy = new StashData();
        copy.deserializeNBT(server.registryAccess(), data.serializeNBT(server.registryAccess()));
        check(f, copy.items().get(0).is(Items.IRON_INGOT) && copy.items().get(0).getCount() == 12 && copy.items().get(53).getCount() == 40
                && copy.count() == 2, "stash survives save");
        check(f, StashData.SIZE == 54, "stash has 54 slots");
        // 人の記録として同じ中身 (どの預かり番からも同じ)
        p.getData(FacilitySetup.STASH).items().set(5, new ItemStack(Items.APPLE));
        check(f, p.getData(FacilitySetup.STASH).items().get(5).is(Items.APPLE), "stash is per player");
        p.getData(FacilitySetup.STASH).items().set(5, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- 宿・修理
    private static void inn(List<String> f, ServerLevel level, FakePlayer p) {
        BlockPos spot = level.getSharedSpawnPos().offset(-3, 0, -3);
        spot = new BlockPos(spot.getX(), level.getMaxBuildHeight() - 30, spot.getZ());
        AttendantEntity keeper = Attendants.ensure(level, AttendantRole.INNKEEPER, spot);
        try {
            BlockPos bed = FacilityActions.innBed(p, keeper);
            check(f, bed != null, "inn bed found");
            if (bed != null && jp.revati.skyabyss.opening.OpeningSites.shelterExtras(level.getServer(), Attendants.INN_BED).isEmpty()) {
                check(f, bed.equals(spot), "without inn_bed the innkeeper's spot is used");
            }
            check(f, FacilityActions.innRest(p, keeper), "inn rest");
            check(f, bed != null && bed.equals(p.getRespawnPosition()) && p.getRespawnDimension() == Level.OVERWORLD, "respawn set to the inn");
        } finally {
            if (keeper != null) {
                keeper.discard();
            }
        }
        check(f, FacilityActions.rumorFor(p) != null, "rumor text");
        // 修理
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(sword.getMaxDamage() / 2);
        int cost = FacilityActions.repairCost(sword);
        check(f, cost == 10, "repair cost half-worn = 10 (" + cost + ")");
        check(f, FacilityActions.repairCost(new ItemStack(Items.IRON_SWORD)) == 0, "unworn costs nothing");
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        check(f, !FacilityActions.repair(p) && sword.isDamaged(), "repair needs coins");
        Coins.give(p, 10);
        check(f, FacilityActions.repair(p) && !sword.isDamaged() && Coins.count(p) == 0, "repair takes coins and mends");
    }
}
