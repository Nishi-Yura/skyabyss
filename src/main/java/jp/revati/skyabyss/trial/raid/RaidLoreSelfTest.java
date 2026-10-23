package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// レイドの部屋の物語 (RaidLore)。SKYABYSS_SELFTEST_ONLY="raid lore"
// 十二の部屋をそれぞれ遠くの空に建て、入口の間に碑があり文のキーが刻まれているか、心の声の二行と碑の四行の文があるか、
// 碑を読む (右クリックと同じ Tablets.read) と碑文の伝承が記されるか、越えたとき (RaidLore.onCleared) にも記されるかを見る。
// メインの碑文の段が、周ごとのどの引きでも済むか (draws) も見る
public final class RaidLoreSelfTest {
    private RaidLoreSelfTest() {
    }

    private static int drawsChecked;
    private static int stagesChecked;

    // 碑文の段 (main_song・main_tide・main_thunder の inscriptions / trials の lore の目的) が、周ごとのどの引き (枠ごとに 2 部屋から一つ) でも
    // 三つの部屋を越えれば済むか。越えたときに記されるのは、部屋の入口の碑の項目 (RaidLore.onCleared) と、岩鯨の部屋 1〜3 の決まった三つ
    // (TrialRoute.WHALE_ROOM_INSCRIPTIONS)。あわせて、その段に「心臓を止めたら飛ばす」(skip_if stilled) があるか
    static void draws(List<String> f) {
        drawsChecked = 0;
        stagesChecked = 0;
        RaidRooms.ensureLoaded();
        // 碑の文のキー → 伝承の項目 (unlock の sign の先で引く)
        java.util.Map<String, String> entryOfSign = new java.util.HashMap<>();
        for (jp.revati.skyabyss.lore.LoreEntry e : LoreManager.catalog().entries()) {
            for (jp.revati.skyabyss.lore.LoreTrigger t : e.unlock()) {
                if (t.type().equals("sign")) {
                    entryOfSign.put(t.target(), e.id());
                }
            }
        }
        // 巨神 → その巨神の部屋が記しうる項目と、引きの全部 (枠ごとの部屋の組)
        java.util.Map<String, java.util.Set<String>> possible = new java.util.HashMap<>();
        java.util.Map<String, List<java.util.Set<String>>> recordedByDraw = new java.util.HashMap<>();
        for (String titan : jp.revati.skyabyss.progress.ProgressRules.TITAN_ORDER) {
            if (!RaidRooms.active(titan)) {
                f.add(titan + ": no raid route registered");
                continue;
            }
            List<java.util.Set<String>> runs = new ArrayList<>();
            runs.add(new java.util.LinkedHashSet<>());
            for (jp.revati.skyabyss.trial.Trial.Slot slot : jp.revati.skyabyss.trial.Trial.Slot.values()) {
                List<java.util.Set<String>> next = new ArrayList<>();
                for (java.util.Set<String> run : runs) {
                    for (String room : RaidRooms.idsOf(titan, slot)) {
                        java.util.Set<String> r = new java.util.LinkedHashSet<>(run);
                        String key = RaidLore.SIGNS.get(room);
                        String entry = key == null ? null : entryOfSign.get(key);
                        if (entry == null) {
                            f.add(room + ": clearing the room records no inscription (sign " + key + ")");
                        } else {
                            r.add(entry);
                        }
                        next.add(r);
                    }
                }
                runs = next;
            }
            if (jp.revati.skyabyss.progress.Titans.STONE_WHALE.equals(titan)) {
                runs.forEach(r -> r.addAll(jp.revati.skyabyss.trial.TrialRoute.WHALE_ROOM_INSCRIPTIONS));
            }
            java.util.Set<String> all = new java.util.HashSet<>();
            runs.forEach(all::addAll);
            possible.put(titan, all);
            recordedByDraw.put(titan, runs);
            drawsChecked += runs.size();
        }
        for (jp.revati.skyabyss.quest.QuestDef q : jp.revati.skyabyss.quest.QuestManager.all()) {
            if (!q.main()) {
                continue;
            }
            for (jp.revati.skyabyss.quest.QuestDef.Stage st : q.stages()) {
                for (jp.revati.skyabyss.quest.QuestDef.Objective o : st.objectives()) {
                    if (!o.type().equals("lore") || o.targets().isEmpty() || !o.targets().stream().allMatch(t -> t.startsWith("chapter1/inscription_"))) {
                        continue;
                    }
                    String where = q.id() + "." + st.id() + "." + o.id();
                    String titan = null;
                    for (var e : possible.entrySet()) {
                        if (o.targets().stream().anyMatch(e.getValue()::contains)) {
                            titan = e.getKey();
                        }
                    }
                    if (titan == null) {
                        f.add(where + ": none of its inscriptions is on any raid route " + o.targets());
                        continue;
                    }
                    stagesChecked++;
                    for (java.util.Set<String> run : recordedByDraw.get(titan)) {
                        long have = o.targets().stream().filter(run::contains).count();
                        if (have < o.count()) {
                            f.add(where + ": the draw " + run + " records only " + have + " of the " + o.count() + " inscriptions it asks for");
                        }
                    }
                    if (!stilledIn(st.skipIf(), titan)) {
                        f.add(where + ": the stage should be skipped once the " + titan + " heart is stilled (skip_if stilled)");
                    }
                }
            }
        }
        if (stagesChecked < 3) {
            f.add("expected the three inscription stages (whale, tide, storm), found " + stagesChecked);
        }
    }

    private static boolean stilledIn(jp.revati.skyabyss.quest.Condition c, String titan) {
        if (c == null) {
            return false;
        }
        if (c.type().equals("stilled") && titan.equals(net.minecraft.util.GsonHelper.getAsString(c.json(), "titan", ""))) {
            return true;
        }
        if (c.type().equals("any")) {
            return c.children().stream().anyMatch(x -> stilledIn(x, titan));
        }
        return false;
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        var ja = SignFit.lang("ja_jp");
        FakePlayer reader = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000ad11"), "[raid_lore_a]"));
        FakePlayer clearer = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000ad12"), "[raid_lore_b]"));
        int i = 0;
        try {
            for (var e : RaidLore.SIGNS.entrySet()) {
                String id = e.getKey();
                String key = e.getValue();
                String loreId = "chapter1/inscription_" + key.substring(key.lastIndexOf("lore_") + 5);
                if (LoreManager.catalog().entry(loreId) == null) {
                    f.add(id + ": no lore entry " + loreId);
                }
                for (int n = 1; n <= 4; n++) {
                    if (ja.getOrDefault(key + "." + n, "").isEmpty()) {
                        f.add(id + ": inscription line " + key + "." + n + " is missing");
                    }
                }
                for (int n = 1; n <= 2; n++) {
                    if (ja.getOrDefault("message.skyabyss.raid_lore." + id + "." + n, "").isEmpty()) {
                        f.add(id + ": inner voice line " + n + " is missing");
                    }
                }
                RaidRoom room = RaidRooms.create(id);
                if (room == null) {
                    f.add(id + ": not registered");
                    continue;
                }
                UUID group = UUID.nameUUIDFromBytes(("raid lore " + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                RaidCtx ctx = new RaidCtx(level, group, "test", id, 0, 1, new BlockPos(29000 + 96 * i++, 230, 29000), 7L);
                try {
                    room.build(ctx);
                    if (ctx.cell() == null) {
                        f.add(id + ": no cell");
                        continue;
                    }
                    BlockPos at = RaidLore.tabletPos(ctx.cell());
                    if (!(level.getBlockEntity(at) instanceof TabletBlockEntity t) || !key.equals(t.key())) {
                        f.add(id + ": the entrance has no inscription " + key + " at " + at.toShortString());
                    } else if (level.getBlockState(at.west()).isAir()) {
                        f.add(id + ": the inscription is not hung on the entrance wall");
                    }
                    if (at.distSqr(ctx.cell().rest()) > 16) {
                        f.add(id + ": the inscription is too far from the resting spot");
                    }
                    // 読む (右クリック = TabletBlock.useWithoutItem → Tablets.read) → 伝承に記される
                    Lore.lock(reader, loreId);
                    Tablets.read(reader, key);
                    if (!Lore.state(reader).has(loreId)) {
                        f.add(id + ": reading the inscription did not record " + loreId);
                    }
                    // 越えた → 読まなかった人にも記される
                    Lore.lock(clearer, loreId);
                    RaidLore.onCleared(clearer, id);
                    if (!Lore.state(clearer).has(loreId)) {
                        f.add(id + ": clearing the room did not record " + loreId);
                    }
                } finally {
                    try {
                        room.end(ctx);
                    } catch (RuntimeException ignored) {
                        // 片付けの失敗は数えない
                    }
                    ctx.clearMobs();
                    RaidKit.clear(level, ctx.cell() == null ? new net.minecraft.world.phys.AABB(ctx.origin()) : ctx.cell().bounds());
                    RaidKit.release(level, group);
                }
            }
            draws(f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST raid lore crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid lore PASSED ({} rooms: entrance inscription placed and keyed, 4 inscription lines + 2 inner voice lines, "
                    + "reading records the lore entry, clearing records it too; {} draws x {} inscription stages complete)", RaidLore.SIGNS.size(), drawsChecked, stagesChecked);
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST raid lore FAILED: {}", x));
        }
    }
}
