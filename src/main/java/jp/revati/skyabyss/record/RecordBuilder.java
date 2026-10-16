package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreCatalog;
import jp.revati.skyabyss.lore.LoreChapter;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.lore.LoreSetup;
import jp.revati.skyabyss.lore.LoreState;
import jp.revati.skyabyss.npc.Affinity;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.npc.WorldLoop;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.record.RecordSnapshot.Kind;
import jp.revati.skyabyss.record.RecordSnapshot.Row;
import jp.revati.skyabyss.record.RecordSnapshot.Tab;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import jp.revati.skyabyss.rpg.MemoryFragments;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.titan.travel.TitanKind;
import jp.revati.skyabyss.titan.travel.TravelRules;
import jp.revati.skyabyss.titan.travel.TravelState;
import jp.revati.skyabyss.titan.travel.TravelWorldData;
import jp.revati.skyabyss.titan.travel.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 控え帳の中身を、サーバーの記録から組む。値はすべてここで決め、画面は描くだけ。
// 読むもの: 巨神の記憶と預かり (TitanMemoryData)、成長 (GrowthData)、技 (SkillData)、クエスト (QuestLog)、
// 好感度 (NpcBonds)、伝承の書 (LoreState)、巨神での移動 (TravelState)、大事な品の在りか (Keepsakes)。
// どれも読むだけで、書き換えない (巨神の記憶も peek で読む)
public final class RecordBuilder {
    static final String P = "skyabyss_record.";

    private RecordBuilder() {
    }

    public static RecordSnapshot build(ServerPlayer target, boolean self) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab(RecordSnapshot.KEEPSAKES, keepsakes(target, self)));
        tabs.addAll(titanTabs(target));
        tabs.add(new Tab(RecordSnapshot.GROWTH, growth(target)));
        tabs.add(new Tab(RecordSnapshot.BONDS, bonds(target)));
        return new RecordSnapshot(target.getUUID(), target.getDisplayName().copy(), self, tabs);
    }

    // ---------------------------------------------------------------- 行の道具
    static MutableComponent t(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    private static Row header(String key) {
        return new Row("h." + key, Kind.HEADER, ItemStack.EMPTY, t("h." + key), Component.empty(), RecordSnapshot.MARK_NONE, -1, List.of(),
                RecordSnapshot.ACTION_NONE);
    }

    private static Row value(String key, Component label, Component value, int mark, List<Component> detail) {
        return new Row(key, Kind.VALUE, ItemStack.EMPTY, label, value, mark, -1, detail, RecordSnapshot.ACTION_NONE);
    }

    private static TitanMemoryData.Record memory(ServerPlayer player) {
        return memory(player, TitanLoot.STONE_WHALE);
    }

    private static TitanMemoryData.Record memory(ServerPlayer player, String titan) {
        TitanMemoryData.Record r = TitanMemoryData.get(player.server).peek(player.getUUID(), titan);
        return r == null ? new TitanMemoryData.Record() : r;
    }

    // ---------------------------------------------------------------- 巨神の見え方
    // 巨神の名前を出してよいか: 心臓を止めた、または順番がその巨神まで来た (前の巨神の心臓を自分で止めた)
    static boolean revealed(ServerPlayer p, String titan) {
        return Progression.stopped(p.server, p.getUUID(), titan) || ProgressRules.canChallenge(Progression.stage(p), titan);
    }

    static Component titanName(ServerPlayer p, String titan) {
        return revealed(p, titan) ? Progression.titanName(titan) : t("r.unknown");
    }

    private static Row header(String key, Component label) {
        return new Row("h." + key, Kind.HEADER, ItemStack.EMPTY, label, Component.empty(), RecordSnapshot.MARK_NONE, -1, List.of(),
                RecordSnapshot.ACTION_NONE);
    }

    // ---------------------------------------------------------------- 大事な品
    private static List<Row> keepsakes(ServerPlayer p, boolean self) {
        List<Row> rows = new ArrayList<>();
        TravelState travel = Voyages.state(p);
        LoreState lore = Lore.state(p);
        TitanMemoryData data = TitanMemoryData.get(p.server);

        rows.add(header("sky_key"));
        // 天の鍵の欠片 (巨神ごとに一つ、同じ品)。在りかの分かる数と、受け取った数
        int expected = KeepsakeBonds.expected(p, KeepsakeBonds.Kind.FRAGMENT);
        boolean assembled = jp.revati.skyabyss.story.SkyKey.assembled(p);
        Keepsakes.Where fw = KeepsakeBonds.where(p, KeepsakeBonds.Kind.FRAGMENT);
        int missing = Math.max(0, expected - fw.total());
        List<Component> fragDetail = new ArrayList<>();
        fragDetail.add(t("d.where"));
        if (fw.total() == 0) {
            fragDetail.add(t(assembled ? "d.fragments_became_key" : expected > 0 ? "d.where_none" : "d.where_not_yet"));
        }
        places(fragDetail, fw);
        fragDetail.add(t("d.fragments_received", expected, FRAGMENTS_TOTAL));
        if (missing > 0) {
            fragDetail.add(t("d.fragments_missing", missing));
        }
        fragDetail.add(t("d.guard_fragment"));
        fragDetail.add(t("d.bond_fragment"));
        fragDetail.add(t("d.recover_fragment"));
        Component fragValue;
        int fragMark;
        if (assembled) {
            fragValue = t("v.became_key");
            fragMark = RecordSnapshot.MARK_SAFE;
        } else if (missing > 0) {
            fragValue = t("v.held_missing", fw.total(), expected);
            fragMark = RecordSnapshot.MARK_LOST;
        } else if (expected == 0 && fw.total() == 0) {
            fragValue = t("v.not_yet");
            fragMark = RecordSnapshot.MARK_LOCKED;
        } else {
            fragValue = t("v.held_of", fw.total(), FRAGMENTS_TOTAL);
            fragMark = fw.safe() >= fw.total() ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_ATTENTION;
        }
        rows.add(new Row("fragments", Kind.ITEM, new ItemStack(ModItems.SKY_KEY_FRAGMENT.get()), ModItems.SKY_KEY_FRAGMENT.get().getDescription().copy(),
                fragValue, fragMark, -1, fragDetail, self && missing > 0 ? RecordSnapshot.ACTION_RECALL_FRAGMENT : RecordSnapshot.ACTION_NONE));
        // 巨神ごとの欠片 (一人一つ)
        int order = 0;
        for (String titan : Titans.all()) {
            order++;
            TitanMemoryData.Record r = data.peek(p.getUUID(), titan);
            boolean got = r != null && r.fragment;
            boolean shown = revealed(p, titan);
            rows.add(value("fragment." + titan, t("r.fragment_of", t("n." + order), titanName(p, titan)),
                    got ? t("v.received") : shown ? t("v.not_yet_short") : t("r.unknown"),
                    got ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_LOCKED,
                    List.of(t(got ? "d.fragment_got" : shown ? "d.fragment_how" : "d.fragment_hidden", titanName(p, titan)), t("d.fragment_record"))));
        }
        // 天の鍵 (四つの欠片が一つになったもの)
        Item key = jp.revati.skyabyss.story.StorySetup.SKY_KEY.get();
        Keepsakes.Where kw = KeepsakeBonds.where(p, KeepsakeBonds.Kind.KEY);
        boolean keyMissing = assembled && kw.total() == 0;
        Row keyRow = keepsake("sky_key", key, kw, assembled, "guard_key", "recover_key");
        rows.add(new Row(keyRow.key(), keyRow.kind(), keyRow.icon(), keyRow.label(), keyRow.value(), keyRow.mark(), -1, keyRow.detail(),
                self && keyMissing ? RecordSnapshot.ACTION_RECALL_KEY : RecordSnapshot.ACTION_NONE));

        rows.add(header("carried"));
        for (String titan : Titans.all()) {
            TitanPages.Page page = TitanPages.page(titan);
            Item core = page == null ? null : page.coreItem();
            if (core == null || !revealed(p, titan)) {
                continue;
            }
            TitanMemoryData.Record r = memory(p, titan);
            rows.add(keepsake("core." + titan, core, Keepsakes.locate(p, core), r.firstCleared, "guard_core",
                    titan.equals(TitanLoot.STONE_WHALE) ? "recover_core" : "recover_core_other"));
        }
        for (TitanKind kind : TitanKind.all()) {
            Item call = kind.callItem().get();
            rows.add(keepsake("call." + kind.id(), call, Keepsakes.locate(p, call), travel.callGiven.contains(kind.id()), "guard_call",
                    "recover_call_" + kind.id()));
        }
        rows.add(keepsake("book", LoreSetup.LORE_BOOK.get(), Keepsakes.locate(p, LoreSetup.LORE_BOOK.get()), lore.bookGiven(), "guard_book", "recover_book"));

        rows.add(header("held"));
        List<Component> pendingDetail = new ArrayList<>();
        int pendingCount = 0;
        int pendingXp = 0;
        if (data.hasPending(p.getUUID())) {
            TitanMemoryData.Pending pending = data.pending(p.getUUID());
            pendingXp = pending.xp;
            for (ItemStack s : pending.items) {
                pendingCount++;
                pendingDetail.add(t("d.list_item", s.getHoverName(), s.getCount()));
            }
            if (pendingXp > 0) {
                pendingDetail.add(t("d.list_xp", pendingXp));
            }
        }
        boolean anyPending = pendingCount > 0 || pendingXp > 0;
        pendingDetail.add(0, t(anyPending ? "d.pending_has" : "d.pending_none"));
        pendingDetail.add(t("d.pending_how"));
        rows.add(new Row("pending", Kind.ITEM, new ItemStack(ModItems.RESONANCE_SHARD.get()), t("r.pending"),
                anyPending ? t("v.pending", pendingCount, pendingXp) : t("v.none"),
                anyPending ? RecordSnapshot.MARK_ATTENTION : RecordSnapshot.MARK_NONE, -1, pendingDetail,
                self && anyPending ? RecordSnapshot.ACTION_CLAIM : RecordSnapshot.ACTION_NONE));
        int kept = Keepsakes.keptStacks(p);
        rows.add(value("kept", t("r.kept"), kept > 0 ? t("v.stacks", kept) : t("v.none"),
                kept > 0 ? RecordSnapshot.MARK_ATTENTION : RecordSnapshot.MARK_NONE, List.of(t("d.kept"))));

        rows.add(header("rights"));
        TitanMemoryData.Record r = memory(p);
        for (String titan : Titans.all()) {
            if (TitanPages.page(titan) == null || !revealed(p, titan)) {
                continue;
            }
            TitanMemoryData.Record tr = memory(p, titan);
            Component choiceValue;
            int choiceMark;
            if (tr.choicePending) {
                choiceValue = t("v.choice_ready");
                choiceMark = RecordSnapshot.MARK_ATTENTION;
            } else if (tr.firstCleared) {
                choiceValue = t("v.choice_done");
                choiceMark = RecordSnapshot.MARK_SAFE;
            } else {
                choiceValue = t("v.choice_locked");
                choiceMark = RecordSnapshot.MARK_LOCKED;
            }
            // 岩鯨の行は前からの名前 (choice) のまま。選ぶボタンは岩鯨だけ (/titanmemory は岩鯨の選択を出す)
            boolean whale = titan.equals(TitanLoot.STONE_WHALE);
            rows.add(new Row(whale ? "choice" : "choice." + titan, Kind.VALUE, ItemStack.EMPTY, t("r.choice_of", Progression.titanName(titan)),
                    choiceValue, choiceMark, -1, List.of(t(whale ? "d.choice" : "d.choice_other", Progression.titanName(titan))),
                    self && whale && tr.choicePending ? RecordSnapshot.ACTION_CHOOSE : RecordSnapshot.ACTION_NONE));
        }
        ForgeRules.ExchangeState exchange = TitanLoot.exchangeState(r.clears > 0 ? r : null);
        int exchangeMark = switch (exchange) {
            case READY -> RecordSnapshot.MARK_ATTENTION;
            case USED, ALL_OWNED -> RecordSnapshot.MARK_SAFE;
            case NO_CLEAR -> RecordSnapshot.MARK_LOCKED;
        };
        rows.add(value("exchange", t("r.exchange"), t("v.exchange." + exchange.name().toLowerCase(java.util.Locale.ROOT)), exchangeMark,
                List.of(t("d.exchange", r.exchanges))));
        return rows;
    }

    // 天の鍵の欠片の総数 (巨神の数)
    static final int FRAGMENTS_TOTAL = jp.revati.skyabyss.story.SkyKey.FRAGMENTS;

    private static Row keepsake(String key, Item item, Keepsakes.Where where, boolean received, String guardKey, String recoverKey) {
        Component value;
        int mark;
        if (where.safe() > 0) {
            value = t("v.at_hand", where.safe());
            mark = RecordSnapshot.MARK_SAFE;
        } else if (where.pending() > 0) {
            value = t("v.in_pending", where.pending());
            mark = RecordSnapshot.MARK_ATTENTION;
        } else if (where.kept() > 0) {
            value = t("v.in_kept", where.kept());
            mark = RecordSnapshot.MARK_ATTENTION;
        } else if (where.stashCount() > 0) {
            value = t("v.in_stash", where.stashCount());
            mark = RecordSnapshot.MARK_ATTENTION;
        } else if (where.groundCount() > 0) {
            value = t("v.on_ground", where.groundCount());
            mark = RecordSnapshot.MARK_ATTENTION;
        } else if (received) {
            value = t("v.missing");
            mark = RecordSnapshot.MARK_LOST;
        } else {
            value = t("v.not_yet");
            mark = RecordSnapshot.MARK_LOCKED;
        }
        List<Component> detail = new ArrayList<>();
        detail.add(t("d.where"));
        if (where.total() == 0) {
            detail.add(t(received ? "d.where_none" : "d.where_not_yet"));
        }
        places(detail, where);
        detail.add(t("d.received_" + (received ? "yes" : "no")));
        detail.add(t("d." + guardKey));
        detail.add(t("d." + recoverKey));
        return new Row(key, Kind.ITEM, new ItemStack(item), item.getDescription().copy(), value, mark, -1, detail, RecordSnapshot.ACTION_NONE);
    }

    private static void places(List<Component> detail, Keepsakes.Where where) {
        place(detail, "inventory", where.inventory());
        place(detail, "bags", where.bags());
        place(detail, "ender", where.ender());
        where.holders().forEach((id, n) -> detail.add(Component.translatableWithFallback(P + "d.place_" + id, "- " + id + " x" + n, n)));
        place(detail, "pending", where.pending());
        place(detail, "kept", where.kept());
        for (Keepsakes.Ground g : where.stash()) {
            BlockPos pos = g.pos();
            detail.add(t(g.loaded() ? "d.place_stash" : "d.place_stash_far", g.count(), dimension(g.dimension()), pos.getX(), pos.getY(), pos.getZ()));
        }
        for (Keepsakes.Ground g : where.ground()) {
            BlockPos pos = g.pos();
            detail.add(t(g.loaded() ? "d.place_ground" : "d.place_ground_far", g.count(), dimension(g.dimension()), pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private static void place(List<Component> detail, String where, int count) {
        if (count > 0) {
            detail.add(t("d.place_" + where, count));
        }
    }

    static Component dimension(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return Component.translatableWithFallback(P + "dim." + path, id);
    }

    // ---------------------------------------------------------------- 巨神 (一覧と、巨神ごとの頁)
    // 一覧の頁 (titans) と、巨神の一覧 (Titans.all) の順の頁 (titans.巨神の名前)。巨神が増えれば頁も増える
    private static List<Tab> titanTabs(ServerPlayer p) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab(RecordSnapshot.TITANS, t("sub.overview"), RecordSnapshot.MARK_NONE, titanOverview(p)));
        for (String titan : Titans.all()) {
            boolean shown = revealed(p, titan);
            boolean stopped = Progression.stopped(p.server, p.getUUID(), titan);
            Component title = shown ? Component.translatable(P + "short." + titan) : t("sub.hidden");
            int mark = stopped ? RecordSnapshot.MARK_SAFE : shown ? RecordSnapshot.MARK_ATTENTION : RecordSnapshot.MARK_LOCKED;
            tabs.add(new Tab(RecordSnapshot.TITANS + RecordSnapshot.SUB + titan, title, mark, titanPage(p, titan)));
        }
        return tabs;
    }

    private static List<Row> titanOverview(ServerPlayer p) {
        List<Row> rows = new ArrayList<>();
        rows.add(header("progress"));
        int stage = Progression.stage(p);
        rows.add(value("stage", t("r.stage"), t("v.of", stage, Titans.all().size()), stage >= Titans.all().size() ? RecordSnapshot.MARK_SAFE
                : RecordSnapshot.MARK_NONE, List.of(t("d.stage"), t("d.level_cap", ProgressRules.levelCap(stage)))));
        int order = 0;
        for (String titan : Titans.all()) {
            order++;
            boolean shown = revealed(p, titan);
            boolean stopped = Progression.stopped(p.server, p.getUUID(), titan);
            TitanMemoryData.Record r = memory(p, titan);
            TitanPages.Page page = TitanPages.page(titan);
            Item icon = page == null ? null : page.coreItem();
            Component value;
            int mark;
            List<Component> detail = new ArrayList<>();
            if (stopped) {
                value = t("v.stopped", r.clears);
                mark = RecordSnapshot.MARK_SAFE;
                detail.add(t("d.titan_stopped", Progression.titanName(titan)));
            } else if (shown && page == null) {
                value = t("v.not_risen");
                mark = RecordSnapshot.MARK_LOCKED;
                detail.add(t("d.titan_not_risen", Progression.titanName(titan)));
            } else if (shown) {
                value = t("v.challenge", Titans.recommendedLevel(titan));
                mark = RecordSnapshot.MARK_ATTENTION;
                detail.add(t("d.titan_challenge", Progression.titanName(titan)));
            } else {
                value = t("v.unseen");
                mark = RecordSnapshot.MARK_LOCKED;
                detail.add(t("d.titan_unseen"));
            }
            detail.add(shown ? t("d.titan_level", Titans.recommendedLevel(titan)) : t("d.titan_level_hidden"));
            rows.add(new Row("titan." + titan, Kind.ITEM, shown && icon != null ? new ItemStack(icon) : ItemStack.EMPTY,
                    t("r.titan_of", t("n." + order), titanName(p, titan)), value, mark, -1, detail, RecordSnapshot.ACTION_NONE));
        }

        rows.add(header("travel"));
        TravelState travel = Voyages.state(p);
        List<Component> titanNames = new ArrayList<>();
        for (String id : travel.unlocked) {
            TitanKind kind = TitanKind.byId(id);
            titanNames.add(kind == null ? Component.literal(id) : Component.translatable(kind.nameKey()));
        }
        rows.add(value("travel_unlocked", t("r.travel_unlocked"), titanNames.isEmpty() ? t("v.none") : join(titanNames),
                titanNames.isEmpty() ? RecordSnapshot.MARK_LOCKED : RecordSnapshot.MARK_SAFE, List.of(t("d.travel_unlocked"))));
        List<Component> marks = new ArrayList<>();
        TravelWorldData world = TravelWorldData.get(p.server);
        for (BlockPos pos : travel.waymarks) {
            TravelWorldData.Waymark mark = world.waymark(pos);
            Component name = mark == null || mark.name().isEmpty() ? t("d.waymark_unnamed") : Component.literal(mark.name());
            marks.add(t("d.waymark", name, pos.getX(), pos.getY(), pos.getZ()));
        }
        if (marks.isEmpty()) {
            marks.add(t("d.waymarks_none"));
        }
        rows.add(value("waymarks", t("r.waymarks"), t("v.of", travel.waymarks.size(), TravelState.MAX_WAYMARKS), RecordSnapshot.MARK_NONE, marks));
        rows.add(value("voyages", t("r.voyages"), t("v.times", travel.voyages), RecordSnapshot.MARK_NONE, List.of(t("d.voyages"))));
        long left = TravelRules.cooldownLeft(p);
        rows.add(value("cooldown", t("r.cooldown"), left <= 0 ? t("v.ready") : t("v.seconds", (left + 19) / 20), RecordSnapshot.MARK_NONE,
                List.of(t("d.cooldown"))));
        return rows;
    }

    private static List<Row> titanPage(ServerPlayer p, String titan) {
        List<Row> rows = new ArrayList<>();
        TitanPages.Page page = TitanPages.page(titan);
        if (!revealed(p, titan)) {
            // まだ見ぬ巨神: 枠と伏せ字だけ
            rows.add(header(titan, t("r.unknown")));
            rows.add(value("unknown", t("r.unknown"), t("v.unseen"), RecordSnapshot.MARK_LOCKED, List.of(t("d.page_hidden"))));
            rows.add(value("memory", t("r.memory"), t("r.unknown"), RecordSnapshot.MARK_LOCKED, List.of(t("d.page_hidden"))));
            rows.add(value("collection", t("r.collection"), t("r.unknown"), RecordSnapshot.MARK_LOCKED, List.of(t("d.page_hidden"))));
            return rows;
        }
        rows.add(header(titan, Progression.titanName(titan)));
        if (page == null) {
            // 順番は来たが、この世界にまだ現れない (ベータでは本体がない)
            rows.add(value("not_risen", t("r.not_risen"), t("v.not_risen"), RecordSnapshot.MARK_LOCKED,
                    List.of(t("d.titan_not_risen", Progression.titanName(titan)), t("d.titan_level", Titans.recommendedLevel(titan)))));
            rows.add(value("memory", t("r.memory"), t("r.unknown"), RecordSnapshot.MARK_LOCKED, List.of(t("d.page_not_risen"))));
            rows.add(value("collection", t("r.collection"), t("r.unknown"), RecordSnapshot.MARK_LOCKED, List.of(t("d.page_not_risen"))));
            return rows;
        }
        TitanMemoryData.Record r = memory(p, titan);
        rows.add(value("memory", t("r.memory"), memoryBar(r.memory), r.memory >= TitanLoot.HARD_PITY - 1 ? RecordSnapshot.MARK_ATTENTION
                : RecordSnapshot.MARK_NONE, List.of(t("d.memory", TitanLoot.SOFT_PITY, TitanLoot.HARD_PITY),
                t(r.memory >= TitanLoot.HARD_PITY - 1 ? "d.memory_next_guaranteed" : r.memory >= TitanLoot.SOFT_PITY - 1 ? "d.memory_next_soft"
                        : "d.memory_next_plain"))));
        rows.add(value("clears", t("r.clears"), t("v.times", r.clears), RecordSnapshot.MARK_NONE, List.of(t("d.clears"))));
        List<Item> collection = page.collection().get();
        int owned = 0;
        for (Item item : collection) {
            if (r.owned.contains(TitanLoot.id(item))) {
                owned++;
            }
        }
        int size = collection.size();
        rows.add(value("collection", t("r.collection"), t("v.of", owned, size), owned >= size && size > 0 ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_NONE,
                List.of(titan.equals(TitanLoot.STONE_WHALE) ? t("d.collection") : t("d.collection_other", size))));
        rows.add(value("fragment_record", t("r.fragment_record"), t(r.fragment ? "v.yes" : "v.no"),
                r.fragment ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_LOCKED, List.of(t("d.fragment_record"))));
        if (page.exchange()) {
            rows.add(value("exchanges", t("r.exchanges"), t("v.times", r.exchanges), RecordSnapshot.MARK_NONE, List.of(t("d.exchanges"))));
        }
        TitanKind kind = TitanKind.byId(titan);
        if (kind != null) {
            boolean unlocked = Voyages.isUnlocked(p, kind);
            rows.add(value("travel", t("r.travel_of"), t(unlocked ? "v.travel_yes" : "v.travel_no"),
                    unlocked ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_LOCKED, List.of(t("d.travel_" + titan))));
        }
        rows.add(header("collection"));
        float multiplier = TitanLoot.pityMultiplier(r.memory);
        for (Item item : collection) {
            boolean has = r.owned.contains(TitanLoot.id(item));
            boolean hidden = !has && page.exclusive().test(item);
            int chance = (int) Math.round(page.chance().applyAsDouble(item) * multiplier * 100);
            rows.add(new Row("piece." + TitanLoot.id(item), Kind.ITEM, hidden ? ItemStack.EMPTY : new ItemStack(item),
                    hidden ? t("r.unknown") : item.getDescription().copy(), t(has ? "v.owned" : "v.not_owned"),
                    has ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_LOCKED, -1,
                    List.of(t(has ? "d.piece_owned" : "d.piece_missing"), t("d.piece_chance", chance)), RecordSnapshot.ACTION_NONE));
        }
        if (collection.isEmpty()) {
            rows.add(value("collection_none", t("r.collection_none"), Component.empty(), RecordSnapshot.MARK_LOCKED, List.of(t("d.collection_none"))));
        }
        return rows;
    }

    static MutableComponent memoryBar(int memory) {
        MutableComponent bar = Component.empty();
        for (int i = 1; i <= TitanLoot.HARD_PITY; i++) {
            boolean lit = i <= memory;
            int color = !lit ? 0x8E7B5E : i >= TitanLoot.HARD_PITY ? 0xB07A12 : i >= TitanLoot.SOFT_PITY ? 0x7A55C8 : 0x1D6E69;
            bar.append(Component.literal(lit ? "◆" : "◇").withColor(color));
        }
        return bar.append(Component.literal(" " + Math.min(memory, TitanLoot.HARD_PITY) + "/" + TitanLoot.HARD_PITY));
    }

    private static MutableComponent join(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(t("sep"));
            }
            out.append(parts.get(i));
        }
        return out;
    }

    // ---------------------------------------------------------------- 成長
    private static List<Row> growth(ServerPlayer p) {
        List<Row> rows = new ArrayList<>();
        GrowthData d = Growth.get(p);
        SkillData s = SkillManager.get(p);
        rows.add(header("level"));
        boolean max = d.level >= GrowthRules.MAX_LEVEL;
        int toNext = GrowthRules.xpToNext(d.level);
        rows.add(new Row("level", Kind.VALUE, ItemStack.EMPTY, t("r.level"), t("v.level", d.level), RecordSnapshot.MARK_NONE,
                max ? 1f : (float) d.xp / Math.max(1, toNext),
                List.of(t("d.level", GrowthRules.MAX_LEVEL), t("d.lifetime", d.lifetimeXp(), GrowthRules.totalXpFor(GrowthRules.MAX_LEVEL))),
                RecordSnapshot.ACTION_NONE));
        rows.add(value("xp", t("r.xp"), max ? t("v.max") : t("v.of", d.xp, toNext), RecordSnapshot.MARK_NONE, List.of(t("d.xp"))));
        int unspent = d.unspentPoints();
        rows.add(value("points", t("r.points"), t("v.of", unspent, d.totalPoints()), unspent > 0 ? RecordSnapshot.MARK_ATTENTION : RecordSnapshot.MARK_NONE,
                List.of(t("d.points", GrowthRules.POINTS_PER_LEVEL, GrowthRules.STAT_CAP))));
        rows.add(header("stats"));
        for (Stat stat : Stat.values()) {
            int v = d.stat(stat);
            rows.add(value("stat." + stat.id, Component.translatable(stat.nameKey()), Component.literal(String.valueOf(v)),
                    v >= GrowthRules.STAT_CAP ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_NONE,
                    List.of(effect(stat, v), Component.translatable("gui.skyabyss.growth.desc." + stat.id),
                            Component.translatable("gui.skyabyss.growth.cap", GrowthRules.STAT_CAP))));
        }
        rows.add(header("skills"));
        int skillUnspent = s.unspentPoints(d.level);
        rows.add(value("skill_points", t("r.skill_points"), t("v.of", skillUnspent, s.totalPoints(d.level)),
                skillUnspent > 0 ? RecordSnapshot.MARK_ATTENTION : RecordSnapshot.MARK_NONE, List.of(t("d.skill_points"))));
        double maxMana = SkillManager.maxMana(p);
        rows.add(new Row("mana", Kind.VALUE, ItemStack.EMPTY, t("r.mana"), t("v.of", Math.round(Math.max(0, s.mana)), Math.round(maxMana)),
                RecordSnapshot.MARK_NONE, maxMana <= 0 ? 0f : (float) Math.min(1, Math.max(0, s.mana) / maxMana),
                List.of(t("d.mana", Math.round(GrowthRules.BASE_MANA), Math.round(GrowthRules.SPIRIT_MANA))), RecordSnapshot.ACTION_NONE));
        List<Component> slotLines = new ArrayList<>();
        int placed = 0;
        for (int i = 0; i < SkillRules.SLOTS; i++) {
            Skill skill = s.slot(i);
            if (skill != null) {
                placed++;
            }
            slotLines.add(t("d.slot", i + 1, skill == null ? t("d.slot_empty") : Component.translatable(skill.nameKey())));
        }
        rows.add(value("slots", t("r.slots"), t("v.of", placed, SkillRules.SLOTS), RecordSnapshot.MARK_NONE, slotLines));
        for (Skill skill : Skill.values()) {
            int rank = s.rank(skill);
            if (rank > 0) {
                rows.add(value("skill." + skill.id, Component.translatable(skill.nameKey()), t("v.rank", rank, skill.maxRank),
                        rank >= skill.maxRank ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_NONE,
                        List.of(Component.translatable(skill.tree.nameKey()), Component.translatable(skill.flavorKey()))));
            }
        }
        rows.add(value("respecs", t("r.respecs"), t("v.times", s.respecs), RecordSnapshot.MARK_NONE, List.of(t("d.respecs"))));
        rows.add(header("fragments"));
        List<Component> fragLines = new ArrayList<>();
        fragLines.add(Component.translatable("gui.skyabyss.growth.fragments_desc"));
        for (Map.Entry<String, Integer> e : d.fragments.entrySet()) {
            fragLines.add(t("d.list_item", Component.translatable(MemoryFragments.nameKey(e.getKey())), e.getValue()));
        }
        rows.add(value("fragments", t("r.fragments"), t("v.of", d.fragmentCount(), d.fragmentCapacity), RecordSnapshot.MARK_NONE, fragLines));
        return rows;
    }

    // ステータス画面 (GrowthScreen.effect) と同じ文
    static Component effect(Stat stat, int value) {
        return switch (stat) {
            case STRENGTH -> Component.translatable("gui.skyabyss.growth.effect.strength", pct(GrowthRules.meleeMultiplier(value)));
            case AGILITY -> Component.translatable("gui.skyabyss.growth.effect.agility",
                    pct(GrowthRules.moveMultiplier(value)), pct(GrowthRules.projectileMultiplier(value)));
            case SPIRIT -> Component.translatable("gui.skyabyss.growth.effect.spirit", (int) Math.round(GrowthRules.BASE_MANA + GrowthRules.bonusMana(value)));
            case VITALITY -> Component.translatable("gui.skyabyss.growth.effect.vitality", String.format(java.util.Locale.ROOT, "%.1f", GrowthRules.bonusHealth(value)));
        };
    }

    private static String pct(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v * 100);
    }

    // ---------------------------------------------------------------- クエスト・好感度・伝承
    private static List<Row> bonds(ServerPlayer p) {
        List<Row> rows = new ArrayList<>();
        rows.add(header("quests"));
        QuestLog log = QuestLog.of(p);
        int listed = 0;
        for (QuestDef def : QuestManager.all()) {
            QuestLog.Entry e = log.entry(def.id());
            if (e == null) {
                continue;
            }
            listed++;
            rows.add(quest(def, e, def.id().equals(log.tracked())));
        }
        // 定義が消えたクエスト (データパックを外したなど) も、記録があれば出す
        for (Map.Entry<String, QuestLog.Entry> e : log.quests().entrySet()) {
            if (QuestManager.quest(e.getKey()) == null) {
                listed++;
                rows.add(value("quest." + e.getKey(), Component.literal(e.getKey()), e.getValue().done ? t("v.done") : t("v.stage_only", e.getValue().stage + 1),
                        RecordSnapshot.MARK_NONE, List.of(t("d.quest_missing"))));
            }
        }
        if (listed == 0) {
            rows.add(value("quests_none", t("r.quests_none"), Component.empty(), RecordSnapshot.MARK_LOCKED, List.of(t("d.quests_none"))));
        }

        rows.add(header("affinity"));
        NpcBonds bonds = NpcBonds.of(p);
        Set<String> roles = new LinkedHashSet<>(List.of(NpcSetup.STORYTELLER_ID, NpcSetup.WHALE_CHASER_ID));
        roles.addAll(bonds.bonds().keySet());
        for (String role : roles) {
            NpcBonds.Bond bond = bonds.bonds().get(role);
            int affinity = bond == null ? 0 : bond.affinity();
            int talks = bond == null ? 0 : bond.talks();
            rows.add(value("affinity." + role, Component.translatable("entity." + role.replace(':', '.')),
                    t("v.affinity", affinity, Component.translatable("gui.skyabyss.npc.tier." + Affinity.tier(affinity))),
                    affinity < 0 ? RecordSnapshot.MARK_LOST : affinity >= 60 ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_NONE,
                    List.of(t("d.affinity", Affinity.MIN, Affinity.MAX), t("d.talks", talks))));
        }

        rows.add(header("lore"));
        LoreCatalog catalog = LoreManager.catalog();
        LoreState state = Lore.state(p);
        int known = 0;
        int total = 0;
        for (LoreEntry entry : catalog.entries()) {
            if (entry.unlockable()) {
                total++;
                if (state.has(entry.id())) {
                    known++;
                }
            }
        }
        rows.add(value("lore_total", t("r.lore_total"), t("v.of", known, total), known >= total && total > 0 ? RecordSnapshot.MARK_SAFE
                : RecordSnapshot.MARK_NONE, List.of(t("d.lore_total"))));
        for (LoreChapter chapter : catalog.chapters()) {
            int k = 0;
            int n = 0;
            for (LoreEntry entry : catalog.inChapter(chapter.id())) {
                if (entry.unlockable()) {
                    n++;
                    if (state.has(entry.id())) {
                        k++;
                    }
                }
            }
            if (n == 0) {
                continue;
            }
            rows.add(value("lore." + chapter.id(), Component.translatable(chapter.nameKey()), t("v.of", k, n),
                    k >= n ? RecordSnapshot.MARK_SAFE : k == 0 ? RecordSnapshot.MARK_LOCKED : RecordSnapshot.MARK_NONE,
                    List.of(Component.translatable(chapter.tabKey()), t("d.lore_chapter"))));
        }
        rows.add(header("world"));
        rows.add(value("loop", t("r.loop"), t("v.times", WorldLoop.count(p.server)), RecordSnapshot.MARK_NONE, List.of(t("d.loop"))));
        return rows;
    }

    private static Row quest(QuestDef def, QuestLog.Entry e, boolean tracked) {
        List<Component> detail = new ArrayList<>();
        int stages = def.stages().size();
        Component value;
        int mark;
        if (e.done) {
            value = t("v.done");
            mark = RecordSnapshot.MARK_SAFE;
            detail.add(Component.translatable(def.doneKey()));
        } else {
            int index = Math.min(e.stage, stages - 1);
            QuestDef.Stage stage = def.stages().get(index);
            value = t("v.stage", Math.min(e.stage + 1, stages), stages);
            mark = tracked ? RecordSnapshot.MARK_ATTENTION : RecordSnapshot.MARK_NONE;
            detail.add(Component.translatable(def.journalKey(stage)));
            for (int i = 0; i < stage.objectives().size(); i++) {
                QuestDef.Objective ob = stage.objectives().get(i);
                int have = Math.min(e.progress(i), ob.count());
                detail.add(t(have >= ob.count() ? "d.objective_done" : "d.objective", Component.translatable(def.objectiveKey(stage, ob)), have, ob.count()));
            }
        }
        detail.add(t(def.main() ? "d.quest_main" : "d.quest_sub"));
        if (tracked) {
            detail.add(t("d.quest_tracked"));
        }
        return value("quest." + def.id(), Component.translatable(def.titleKey()), value, mark, detail);
    }
}
