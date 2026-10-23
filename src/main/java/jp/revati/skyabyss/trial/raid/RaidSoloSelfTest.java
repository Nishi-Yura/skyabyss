package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.TrialSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;

// どの部屋も一人で越えられる (docs/design/32 の 9 章)。登録したレイドの部屋すべてと、四体の中ボスの間を、
// 偽の人一人ずつで同時に建てて動かし、台本 (RaidSolo) で最後まで越えるのを見る。SKYABYSS_SELFTEST_ONLY="raid solo"
//  - 部屋は各巨神の背の次元の、区画の枠の外 (33 番目の枠) に横へ並べて建てる。人はそれぞれ別のグループ (一人)
//  - 毎 tick: 台本 → 部屋の tick。体力は毎 tick 戻す。倒れた・部屋が失敗を宣言したら、やり直し (reset → start) を数える (3 回まで)
//  - 建てた部屋が閉じているか (休み場から空気を辿って箱の外へ抜けない)。越えたか・かかった秒・やり直しの数を記録に出す。目的の帯の一行目 (始まった直後の objectiveKey) が lang にあるか、行き先の印も見る
public final class RaidSoloSelfTest {
    private RaidSoloSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("raid solo (setup)", RaidSoloSelfTest::setup), Step.until("raid solo", RaidSoloSelfTest::tick));
    }

    static final int FRAME = TrialSlot.MAX + 1;
    static final int SPACING = 160;
    static final int MAX_WIPES = 3;

    private static final class Case {
        final String id;
        final String titan;
        final RaidRoom room;
        final RaidCtx ctx;
        final FakePlayer p;
        final RaidSolo.Script script;
        int t;
        int wipes;
        boolean done;
        boolean failed;
        String firstKey;
        boolean guided;

        Case(String id, String titan, RaidRoom room, RaidCtx ctx, FakePlayer p, RaidSolo.Script script) {
            this.id = id;
            this.titan = titan;
            this.room = room;
            this.ctx = ctx;
            this.p = p;
            this.script = script;
        }
    }

    private static final List<Case> CASES = new ArrayList<>();
    private static final List<String> FAILS = new ArrayList<>();
    private static final List<ChunkPos> FORCED = new ArrayList<>();
    private static final Map<ServerLevel, List<ServerPlayer>> PLAYERS = new LinkedHashMap<>();
    private static Difficulty difficulty;
    private static MinecraftServer server;

    private static void setup(MinecraftServer s) {
        server = s;
        difficulty = s.getWorldData().getDifficulty();
        s.setDifficulty(Difficulty.NORMAL, true);
        CASES.clear();
        FAILS.clear();
        PLAYERS.clear();
        RaidSoloScripts.registerAll();
        Map<String, String> rooms = new LinkedHashMap<>(RaidRooms.all());
        for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
            String mid = jp.revati.skyabyss.trial.raid.mid.MidRooms.idOf(titan);
            if (mid != null) {
                rooms.put(mid, titan);
            }
        }
        Map<String, Integer> perLevel = new LinkedHashMap<>();
        TrialRoute.PLAYERS = l -> PLAYERS.getOrDefault(l, List.of());
        for (Map.Entry<String, String> e : rooms.entrySet()) {
            String id = e.getKey();
            String titan = e.getValue();
            ServerLevel back = s.getLevel(Titans.backOf(titan));
            if (back == null) {
                FAILS.add(id + ": no back dimension for " + titan);
                continue;
            }
            int idx = perLevel.merge(titan, 1, Integer::sum) - 1;
            BlockPos origin = new BlockPos(TrialSlot.frame(FRAME).dx() + idx * SPACING, RaidRun.FLOOR_Y, TrialSlot.ORIGIN_Z);
            for (int cx = (origin.getX() - 64) >> 4; cx <= (origin.getX() + 64) >> 4; cx++) {
                for (int cz = (origin.getZ() - 64) >> 4; cz <= (origin.getZ() + 64) >> 4; cz++) {
                    back.setChunkForced(cx, cz, true);
                    FORCED.add(new ChunkPos(cx, cz));
                }
            }
            boolean isMid = jp.revati.skyabyss.trial.raid.mid.MidRooms.isMid(id);
            RaidRoom room = isMid ? jp.revati.skyabyss.trial.raid.mid.MidRooms.create(titan) : RaidRooms.create(id);
            if (room == null) {
                FAILS.add(id + ": no room");
                continue;
            }
            FakePlayer p = fake(back, id);
            PLAYERS.computeIfAbsent(back, k -> new ArrayList<>()).add(p);
            UUID group = Groups.keyOf(p);
            int step = isMid ? 4 : slotStep(id, titan);
            RaidCtx ctx = RaidCtx.forSolo(back, group, titan, id, step, origin, id.hashCode() * 31L + 7);
            try {
                room.build(ctx);
            } catch (RuntimeException ex) {
                SkyAbyss.LOGGER.error("SELFTEST raid solo: {} build crashed", id, ex);
                FAILS.add(id + ": build crashed: " + ex);
                continue;
            }
            if (ctx.cell() == null) {
                FAILS.add(id + ": no cell was built");
                continue;
            }
            // 部屋の監査: 休み場から空気を辿って、部屋の箱の外へ抜けない (閉じた一室)
            String leak = jp.revati.skyabyss.titan.dungeon.TitanBackSelfTest.leak(back, ctx.cell().rest(), ctx.cell().bounds().inflate(1));
            if (leak != null) {
                FAILS.add(id + ": the room is not sealed, air reaches " + leak);
            }
            RaidSolo.Script script = RaidSolo.of(id);
            if (script == null) {
                if (isMid || slotOf(id, titan) == Trial.Slot.FIGHT) {
                    script = RaidSolo.FIGHT;
                } else {
                    FAILS.add(id + ": no solo script (RaidSolo.register)");
                    continue;
                }
            }
            RaidSolo.put(p, ctx.cell().rest());
            RaidKit.setGate(ctx, ctx.cell().entryGate(), true);
            room.start(ctx);
            Case c = new Case(id, titan, room, ctx, p, script);
            c.firstKey = room.objectiveKey();
            CASES.add(c);
        }
        SkyAbyss.LOGGER.info("SELFTEST raid solo: {} rooms built, one player each", CASES.size());
    }

    private static int slotStep(String id, String titan) {
        Trial.Slot s = slotOf(id, titan);
        return s == null ? 1 : s.ordinal() + 1;
    }

    private static Trial.Slot slotOf(String id, String titan) {
        for (Trial.Slot s : Trial.Slot.values()) {
            if (RaidRooms.idsOf(titan, s).contains(id)) {
                return s;
            }
        }
        return null;
    }

    private static FakePlayer fake(ServerLevel level, String id) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("raid solo " + id).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "[solo_" + id + "]")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        level.addFreshEntity(p);
        return p;
    }

    private static boolean tick(MinecraftServer s) {
        boolean all = true;
        for (Case c : CASES) {
            if (c.done || c.failed) {
                continue;
            }
            all = false;
            try {
                step(c);
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("SELFTEST raid solo: {} crashed", c.id, e);
                FAILS.add(c.id + ": crashed at " + c.t + " ticks: " + e);
                c.failed = true;
            }
        }
        if (!all) {
            return false;
        }
        finish();
        return true;
    }

    private static void step(Case c) {
        RaidCtx ctx = c.ctx;
        ServerPlayer p = c.p;
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.removeAllEffects();
        c.script.act(ctx, c.room, p, c.t);
        c.room.tick(ctx);
        if (!c.guided && c.room.guideTarget(ctx) != null) {
            c.guided = true;
        }
        c.t++;
        String fail = ctx.takeFail();
        boolean down = Downed.isDowned(p);
        if (fail != null || down) {
            c.wipes++;
            SkyAbyss.LOGGER.info("SELFTEST raid solo: {} reset after {} ticks ({})", c.id, c.t, down ? "downed" : fail);
            if (down) {
                Downed.reviveFull(p);
            }
            if (c.wipes > MAX_WIPES) {
                FAILS.add(c.id + ": one player could not clear it (" + c.wipes + " resets, last: " + (down ? "downed" : fail) + ")");
                c.failed = true;
                return;
            }
            ctx.clearMobs();
            c.room.reset(ctx);
            RaidSolo.put(p, ctx.cell().rest());
            c.room.start(ctx);
            return;
        }
        if (c.room.isComplete()) {
            c.done = true;
            SkyAbyss.LOGGER.info("SELFTEST raid solo: {} cleared alone in {} s ({} resets)", c.id, c.t / 20, c.wipes);
            return;
        }
        if (c.t > RaidSolo.limitOf(c.id)) {
            FAILS.add(c.id + ": not cleared by one player in " + c.t / 20 + " s (objective " + c.room.objectiveKey() + " " + c.room.counter() + "/" + c.room.counterMax() + ")");
            c.failed = true;
        }
    }

    private static void finish() {
        for (Case c : CASES) {
            // 一行目の文が lang にあるか (帯に生のキーが出ない)
            if (!net.minecraft.locale.Language.getInstance().has(c.firstKey)) {
                FAILS.add(c.id + ": the first objective line has no text: " + c.firstKey);
            }
            try {
                c.ctx.clearMobs();
                c.room.end(c.ctx);
                RaidKit.clear(c.ctx.level(), c.ctx.cell().bounds());
            } catch (RuntimeException ignored) {
                // 片付けの失敗は数えない
            }
            c.p.discard();
        }
        for (ServerLevel l : PLAYERS.keySet()) {
            for (ChunkPos cp : FORCED) {
                l.setChunkForced(cp.x, cp.z, false);
            }
        }
        TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        if (server != null && difficulty != null) {
            server.setDifficulty(difficulty, true);
        }
        long cleared = CASES.stream().filter(c -> c.done).count();
        if (FAILS.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid solo PASSED ({} rooms incl. the four mid-boss arenas, each cleared by one player)", cleared);
        } else {
            FAILS.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST raid solo FAILED: {}", f));
        }
    }
}
