package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.end.ReturnBeacons;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;

// 順路 (docs/design/32 の 2 章・5 章) の自己テスト。段の名前は "trial route"。
//  - 形: 四体とも、周回 1 の引きが原型の三つ (歩・智・闘、鍵が違う)、全部の試練に入口の休み場、中ボスの原型と前室、入口の箱
//  - 初回の流れ (背が建ててある巨神): 入口に立つ → 予告のあと段 1 へ → 越えるたびに次の段の入口へ → 三つで心臓の前室。
//    別のグループの人は動かない。越えた順が前後しても段は戻らない。途中で入口に立っても始め直さない。ほかのグループの初回の順路が
//    動いていても待たない (部屋は区画ごとに別)。周回のグループは入口では始まらない (祠から)
//  - 周回の流れ: 引いた三つ (新作を含む) の入口の休み場へ運ばれ、今の段のセルの戸だけが開く。中ボスの原型を引いていない周は
//    闘のあと中ボスの前室へ、倒すと心臓の前室へ。二つのグループは別の区画の番号で同時に進み、終えると番号が返る
//  - あきらめ (一人は提案で成立)・参加者がいなくなったとき・心臓を止めたときに順路が終わる。心臓で順路の記録 (回数) が増える
public final class TrialRouteSelfTest {
    private TrialRouteSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("trial route", 10, TrialRouteSelfTest::run));
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        int worlds = 0;
        try {
            for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
                shape(f, titan);
                ServerLevel back = server.getLevel(Titans.backOf(titan));
                if (back == null) {
                    f.add(titan + ": the back dimension is missing");
                    continue;
                }
                // 心臓の段の運び: 心臓の間は体の外の封じた部屋なので、背の体が建っていなくても部屋だけ建てて見る
                jp.revati.skyabyss.trial.raid.InstanceArenas.ensureHeart(back, titan);
                heartCarry(f, server, back, titan);
                if (ready(back, titan)) {
                    rests(f, back, titan);
                    firstRun(f, back, titan);
                    replay(f, back, titan);
                    worlds++;
                } else {
                    SkyAbyss.LOGGER.info("SELFTEST trial route: {} back is not built here, checked the shape only", titan);
                }
            }
            fattened(f, server);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST trial route crashed", e);
            f.add("crashed: " + e);
        } finally {
            TrialRoute.clearAll();
            TrialSlot.clearAll();
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial route PASSED (shape for four titans; walked first runs and replays in {} built back(s); long prototypes on replays)", worlds);
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST trial route FAILED: {}", s);
            }
        }
    }

    // ---------------------------------------------------------------- 太らせた原型 (R5。32 の 4 章)
    // 周回のグループ (心臓を一度止めた) だけが長い。初回は今の長さ。長さは 1.5〜2 倍
    private static void fattened(List<String> f, MinecraftServer server) {
        ServerLevel any = server.getLevel(Titans.backOf(Titans.STONE_WHALE));
        if (any != null) {
            UUID probe = UUID.randomUUID();
            BackGroupData.Record fresh = BackGroupData.get(any).of(probe);
            check(f, !RouteLength.extended(fresh), "a first-run group should play the prototypes at their usual length");
            fresh.markFirstCleared();
            check(f, RouteLength.extended(fresh) && RouteLength.extended(any, probe), "a replaying group should play the long prototypes");
            BackGroupData.get(any).remove(probe);
        }
        double waves = jp.revati.skyabyss.titan.dungeon.TitanBackTrials.replayWaves(true) / (double) jp.revati.skyabyss.titan.dungeon.TitanBackTrials.replayWaves(false);
        check(f, waves >= 1.5 && waves <= 2.0, "W09: the replay halls should have 1.5-2x the waves: " + waves);
        double nests = jp.revati.skyabyss.titan.tide.back.TideBackLayout.NEST_COUNT_LONG / (double) jp.revati.skyabyss.titan.tide.back.TideBackLayout.NEST_COUNT;
        check(f, nests >= 1.5 && nests <= 2.0 && jp.revati.skyabyss.titan.tide.back.TideBackLayout.NEST_COUNT_LONG <= jp.revati.skyabyss.titan.tide.back.TideBackLayout.NEST_SITES.length,
                "T09: the long flats should have 1.5-2x the nests within the sites: " + nests);
        check(f, jp.revati.skyabyss.titan.tide.back.TideWatch.STAGES_LONG == 2 && jp.revati.skyabyss.titan.storm.back.EyeMirrors.STAGES_LONG == 2
                && jp.revati.skyabyss.titan.ember.back.trial.Forge.KEYS_LONG == 2 && jp.revati.skyabyss.titan.ember.back.trial.Hearth.FLARES_LONG == 2,
                "T05/S06/E05/E09: the long rooms should ask for two rounds");
        float hearth = (jp.revati.skyabyss.titan.ember.back.trial.Hearth.MAX - jp.revati.skyabyss.titan.ember.back.trial.Hearth.START
                + jp.revati.skyabyss.titan.ember.back.trial.Hearth.MAX - jp.revati.skyabyss.titan.ember.back.trial.Hearth.RELIGHT)
                / (jp.revati.skyabyss.titan.ember.back.trial.Hearth.MAX - jp.revati.skyabyss.titan.ember.back.trial.Hearth.START);
        check(f, hearth >= 1.5 && hearth <= 2.0, "E09: the long hearth should burn 1.5-2x the fire: " + hearth);
        // W05 灯をたずさえる: 周回は灯を一つずつしか持てない。一人で歩く道のり (近道を通る) が 1.5〜2.2 倍
        double sum = 0;
        double lo = Double.MAX_VALUE;
        double hi = 0;
        int seeds = 24;
        for (long seed = 1; seed <= seeds; seed++) {
            int first = jp.revati.skyabyss.titan.dungeon.AnemoneLights.soloWalk(seed * 7919L, jp.revati.skyabyss.titan.dungeon.AnemoneLights.LIGHTS);
            // 周回: 一度に二つまで、苗床は入口の二つだけ (中ほどは眠っている)
            int replay = jp.revati.skyabyss.titan.dungeon.AnemoneLights.soloWalk(seed * 7919L, jp.revati.skyabyss.titan.dungeon.AnemoneLights.CARRY_LONG, 3, true);
            double r = replay / (double) Math.max(1, first);
            sum += r;
            lo = Math.min(lo, r);
            hi = Math.max(hi, r);
        }
        double mean = sum / seeds;
        // S05 雷の回路: 周回は二度届かせる。組み変わった回路にも解があり、解は前と違う
        check(f, jp.revati.skyabyss.titan.storm.back.StormProgress.longCircuitProblems().isEmpty(), "S05: " + jp.revati.skyabyss.titan.storm.back.StormProgress.longCircuitProblems());
        // 眼の間: 長い周は一つ目の配置を解くと次の配置に替わり、二つ目で嘴の戸が開く
        ServerLevel storm = server.getLevel(Titans.backOf(Titans.THUNDER_WING));
        if (storm != null) {
            UUID g = UUID.randomUUID();
            jp.revati.skyabyss.titan.storm.back.StormProgress sp = jp.revati.skyabyss.titan.storm.back.StormProgress.of(storm, g);
            BackGroupData.get(storm).of(g).markFirstCleared();
            int cfg0 = sp.mirrorConfig();
            jp.revati.skyabyss.titan.storm.back.EyeMirrors.testLightAll(storm, sp);
            check(f, sp.mirrorStage() == 1 && sp.receivers() == 0 && sp.mirrorConfig() != cfg0 && !sp.beakOpen(),
                    "S06: the long eye room should turn to a new pattern after the first: stage " + sp.mirrorStage() + " config " + sp.mirrorConfig());
            jp.revati.skyabyss.titan.storm.back.EyeMirrors.testLightAll(storm, sp);
            check(f, sp.receivers() == 7 && sp.beakOpen(), "S06: the second pattern should open the beak");
            BackGroupData.get(storm).remove(g);
        }
        ServerLevel tide = server.getLevel(Titans.backOf(Titans.TIDE_SHELL));
        if (tide != null) {
            UUID g = UUID.randomUUID();
            jp.revati.skyabyss.titan.tide.back.TideBackState.Group tg = jp.revati.skyabyss.titan.tide.back.TideBackState.of(tide, g);
            check(f, tg.eastStagesNeeded() == 1, "T05: a first-run group solves one watch pattern");
            BackGroupData.get(tide).of(g).markFirstCleared();
            int c0 = tg.eastConfig();
            tg.nextEastStage();
            check(f, tg.eastStagesNeeded() == 2 && tg.eastStage() == 1 && tg.eastConfig() != c0 && tg.eastConfig() >= 1,
                    "T05: the long watch room should move to another pattern: " + tg.eastConfig());
            BackGroupData.get(tide).remove(g);
        }
    }

    private static TrialDraw.Draw firstDraw(String titan) {
        List<TrialDraw.Pick> picks = new ArrayList<>();
        for (Def d : TrialCatalog.originals(titan)) {
            picks.add(new TrialDraw.Pick(d.id(), Mods.PLAIN));
        }
        return new TrialDraw.Draw(1, picks);
    }

    // ---------------------------------------------------------------- 形 (世界なし)
    private static void shape(List<String> f, String titan) {
        TrialDraw.Draw first = firstDraw(titan);
        check(f, first.picks().size() == 3, titan + ": the first draw is not three originals: " + first.encode());
        Set<String> keys = new HashSet<>();
        for (int step = 1; step <= 3; step++) {
            String key = TrialRoute.keyOf(titan, first, step);
            check(f, key != null && TrialRegions.keys(titan).contains(key), titan + ": step " + step + " has no stele key (" + key + ")");
            keys.add(key);
            check(f, TrialRoute.restOf(titan, first, step, false) != null, titan + ": no rest for first-run step " + step);
        }
        check(f, keys.size() == 3, titan + ": the first route repeats a stele: " + keys);
        check(f, TrialRoute.keysOf(titan).equals(java.util.Arrays.asList(TrialRoute.keyOf(titan, first, 1), TrialRoute.keyOf(titan, first, 2), TrialRoute.keyOf(titan, first, 3))),
                titan + ": the first route order is not walk, wit, fight: " + keys);
        check(f, TrialRoute.HEART_REST.equals(TrialRoute.restOf(titan, first, TrialRoute.LAST_STEP, false)), titan + ": the last step is not the heart rest");
        check(f, TrialRoute.defOf(first, 0) == null && TrialRoute.defOf(first, 4) == null, titan + ": defOf bounds");
        // 全部の試練に入口の休み場
        for (Def d : TrialCatalog.of(titan)) {
            TrialDraw.Draw one = new TrialDraw.Draw(2, List.of(new TrialDraw.Pick(d.id(), Mods.PLAIN)));
            check(f, TrialRoute.entryRest(titan, d, one) != null, titan + ": " + d.id() + " has no entry rest");
        }
        // 中ボス: 原型の中にいる。周回 1 は要らない。中ボスの原型を引いていない周は要る
        String host = TrialMidBoss.hostOf(titan);
        Def hostDef = host == null ? null : TrialCatalog.get(host);
        check(f, hostDef != null && hostDef.original(), titan + ": the mid-boss host is not an original trial: " + host);
        check(f, TrialMidBoss.restOf(titan) != null, titan + ": no mid-boss antechamber");
        check(f, TrialMidBoss.needed(titan, first) == jp.revati.skyabyss.trial.raid.RaidRooms.active(titan),
                titan + ": the first run's mid-boss step does not match the raid mode (raid routes always have a separate mid-boss step)");
        List<TrialDraw.Pick> noHost = new ArrayList<>();
        for (Trial.Slot s : Trial.Slot.values()) {
            for (Def d : TrialCatalog.inSlot(titan, s)) {
                if (!d.id().equals(host)) {
                    noHost.add(new TrialDraw.Pick(d.id(), Mods.PLAIN));
                    break;
                }
            }
        }
        check(f, TrialMidBoss.needed(titan, new TrialDraw.Draw(2, noHost)), titan + ": a draw without the mid-boss host has no mid-boss step");
        AABB entry = TrialRoute.entryBox(titan);
        check(f, entry != null && entry.getXsize() >= 3 && entry.getYsize() >= 3 && entry.getZsize() >= 3, titan + ": entry box missing or tiny: " + entry);
    }

    // 段 1 の休み場の足もとが固い (背が建ててある) か
    // 背が建っているか: 乗り込みで着く到着の台に立てる (巨神の中は建てないので、台だけを見る。docs/design/32 の 2.5)
    private static boolean ready(ServerLevel back, String titan) {
        BlockPos at = RouteSeals.safeFeet(titan);
        back.getChunk(at);
        return !back.getBlockState(at.below()).isAir() && back.getBlockState(at).isAir();
    }

    // 全部の試練の入口の休み場と中ボスの前室が、到着の台の代わり (知らない名前) ではない
    private static void rests(List<String> f, ServerLevel back, String titan) {
        BlockPos arrival = ReturnBeacons.arrivalOf(titan);
        List<String> ids = new ArrayList<>();
        for (Def d : TrialCatalog.of(titan)) {
            ids.add(TrialRoute.entryRest(titan, d, new TrialDraw.Draw(2, List.of(new TrialDraw.Pick(d.id(), Mods.PLAIN)))));
        }
        ids.add(TrialMidBoss.restOf(titan));
        ids.add(TrialRoute.HEART_REST);
        for (String id : ids) {
            if (id == null) {
                continue;
            }
            BlockPos at = TrialRoute.spawnOf(back, titan, id);
            check(f, at != null && at.distSqr(arrival) > 36, titan + ": rest " + id + " resolves to the arrival (unknown rest?) " + at);
        }
        // 入口の箱は床の上にある: 箱のまんなかの列に、箱の高さの中で立てる所 (固い足場の上に空気 2 つ) がある (宙に浮いた箱では始まらない)
        AABB box = TrialRoute.entryBox(titan);
        if (box != null) {
            int cx = (int) Math.floor(box.getCenter().x);
            int cz = (int) Math.floor(box.getCenter().z);
            boolean floor = false;
            for (int y = (int) Math.floor(box.minY); y < (int) Math.ceil(box.maxY) && !floor; y++) {
                BlockPos feet = new BlockPos(cx, y, cz);
                floor = back.getBlockState(feet.below()).isSolidRender(back, feet.below()) || !back.getBlockState(feet.below()).getCollisionShape(back, feet.below()).isEmpty();
                floor = floor && back.getBlockState(feet).getCollisionShape(back, feet).isEmpty() && back.getBlockState(feet.above()).getCollisionShape(back, feet.above()).isEmpty();
            }
            check(f, floor, titan + ": the entry box " + box + " has no floor to stand on in its middle column");
        }
    }

    private static FakePlayer fake(ServerLevel back, String id, String name) {
        FakePlayer p = FakePlayerFactory.get(back, new GameProfile(UUID.fromString(id), name));
        p.setPos(0, 0, 0);
        return p;
    }

    private static boolean near(ServerPlayer p, ServerLevel back, String titan, TrialDraw.Draw draw, int step, boolean mid) {
        // 心臓の段は、体の外の封じた心臓の間の中 (trial/raid/InstanceArenas)
        if (!mid && step >= TrialRoute.LAST_STEP) {
            BlockPos in = jp.revati.skyabyss.trial.raid.InstanceArenas.heartEntry(titan);
            return in != null && jp.revati.skyabyss.trial.raid.InstanceArenas.heartArena(titan).contains(p.position())
                    && p.position().distanceTo(Vec3.atBottomCenterOf(in)) < 2.5;
        }
        // レイドの部屋の段は、区画に建てた部屋の中 (docs/design/32 の 7 章)
        if (!mid && jp.revati.skyabyss.trial.raid.RaidRun.isRaidStep(back, Groups.keyOf(p), step)) {
            var run = jp.revati.skyabyss.trial.raid.RaidRun.of(Groups.keyOf(p));
            return run != null && run.step == step && run.ctx.cell().bounds().contains(p.position());
        }
        // 中ボスの段は、区画の段 4 に建てた中ボスの間の中 (trial/raid/mid。前室を歩かない)
        if (mid && jp.revati.skyabyss.trial.raid.mid.MidRooms.idOf(titan) != null) {
            var run = jp.revati.skyabyss.trial.raid.RaidRun.of(Groups.keyOf(p));
            return run != null && run.mid && run.ctx.cell().inner().contains(p.position());
        }
        String rest = TrialRoute.restFor(back, Groups.keyOf(p), titan, draw, step, mid);
        BlockPos at = rest == null ? null : TrialRoute.spawnOf(back, titan, rest);
        // 区画の写しで遊ぶ段は、写しの入口の休み場
        at = at == null ? null : TrialRoute.frameOfStep(back, Groups.keyOf(p), draw, step, mid).at(at);
        return at != null && p.position().distanceTo(Vec3.atBottomCenterOf(at)) < 2.5;
    }

    private static void clean(ServerLevel back, UUID... groups) {
        for (UUID g : groups) {
            TrialRoute.finish(back, g);
            TrialRuntime.stopGroup(back, g);
            GroupGates.reset(back, g);
            BackGroupData.get(back).remove(g);
        }
    }

    // ---------------------------------------------------------------- 心臓の段: パーティ全員が、体の外の封じた心臓の間の中へ直に運ばれる
    // (立てる床の上・頭まで空き・戦いの箱の中・入口の封印より奥。前室を歩かない)。心臓の間が背の体の外 (区画 0 番の枠) にある
    private static void heartCarry(List<String> f, MinecraftServer server, ServerLevel back, String titan) {
        TrialRoute.clearAll();
        jp.revati.skyabyss.party.PartyData parties = jp.revati.skyabyss.party.PartyData.get(server);
        net.minecraft.nbt.CompoundTag snapshot = parties.snapshot(server.registryAccess());
        FakePlayer a = fake(back, "7a0b1d00-0000-4000-8000-00000000e005", "route_e");
        FakePlayer b = fake(back, "7a0b1d00-0000-4000-8000-00000000f006", "route_f");
        UUID g = null;
        try {
            parties.clearAll();
            jp.revati.skyabyss.party.PartyData.Party party = parties.create(a.getUUID(), "route_e");
            parties.add(party, b.getUUID(), "route_f");
            g = Groups.keyOf(a);
            clean(back, g);
            List<ServerPlayer> both = List.of(a, b);
            TrialRoute.PLAYERS = l -> both;
            Vec3 entry = TrialRoute.entryBox(titan).getCenter();
            a.setPos(entry);
            b.setPos(entry.add(1, 0, 0));
            TrialRoute.start(back, g, titan, both);
            BackGroupData.Record r = GroupProgress.of(back, g);
            r.remove(TrialRoute.MID);
            r.putInt(TrialRoute.STEP, TrialRoute.LAST_STEP);
            r.changed();
            int n = TrialRoute.carry(back, g, titan, TrialRoute.LAST_STEP, false);
            check(f, n == 2, titan + ": the heart carry moved " + n + " of 2");
            AABB arena = jp.revati.skyabyss.trial.raid.InstanceArenas.heartArena(titan);
            check(f, arena.minX >= jp.revati.skyabyss.trial.TrialSlot.ORIGIN_X - 512 && arena.maxX < jp.revati.skyabyss.trial.TrialSlot.ORIGIN_X + jp.revati.skyabyss.trial.TrialSlot.STRIDE,
                    titan + ": the heart arena is not in the slot-0 instance frame: " + arena);
            for (ServerPlayer p : both) {
                BlockPos feet = p.blockPosition();
                check(f, arena.contains(p.position()), titan + ": " + p.getGameProfile().getName() + " was not carried inside the heart arena: " + p.position() + " (arena " + arena + ")");
                check(f, back.getBlockState(feet).getCollisionShape(back, feet).isEmpty() && back.getBlockState(feet.above()).getCollisionShape(back, feet.above()).isEmpty()
                        && !back.getBlockState(feet.below()).getCollisionShape(back, feet.below()).isEmpty(),
                        titan + ": the heart arrival is not a standing spot at " + feet.toShortString());
            }
            SkyAbyss.LOGGER.info("SELFTEST trial route {}: the party was carried into the heart arena at {}", titan, a.blockPosition().toShortString());
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST trial route heart carry crashed", e);
            f.add(titan + ": heart carry crashed: " + e);
        } finally {
            if (g != null) {
                clean(back, g);
            }
            parties.restore(snapshot, server.registryAccess());
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
    }

    // ---------------------------------------------------------------- 初回
    private static void firstRun(List<String> f, ServerLevel back, String titan) {
        TrialRoute.clearAll();
        FakePlayer a = fake(back, "7a0b1d00-0000-4000-8000-00000000a001", "route_a");
        FakePlayer b = fake(back, "7a0b1d00-0000-4000-8000-00000000b002", "route_b");
        UUID ga = Groups.keyOf(a);
        UUID gb = Groups.keyOf(b);
        clean(back, ga, gb);
        long now = back.getGameTime();
        AABB entry = TrialRoute.entryBox(titan);
        Vec3 door = entry.getCenter();
        Vec3 bAt = new Vec3(door.x + 40, door.y, door.z + 40);
        a.setPos(door);
        b.setPos(bAt);
        List<ServerPlayer> players = List.of(a, b);
        TrialRoute.PLAYERS = l -> players;
        try {
            // 入口: 立っただけでは始まらず、予告のあと始まる
            TrialRoute.tick(back, titan, players, now);
            check(f, TrialRoute.step(back, ga) == 0, titan + ": the route started without the warning");
            TrialRoute.tick(back, titan, players, now + TrialRoute.ENTRY_WAIT + 10);
            check(f, TrialRoute.step(back, ga) == 1, titan + ": the route did not start after the warning (step " + TrialRoute.step(back, ga) + ")");
            check(f, TrialRoute.step(back, gb) == 0, titan + ": another group's route started");
            TrialDraw.Draw draw = TrialData.current(back, ga);
            check(f, draw.cycle() == 1, titan + ": a fresh group is not on cycle 1");
            check(f, near(a, back, titan, draw, 1, false), titan + ": the starter was not carried to the first rest: " + a.position());
            check(f, b.position().distanceTo(bAt) < 0.5, titan + ": a player of another group was moved at the start");
            check(f, TrialRoute.isParticipant(back, ga, a.getUUID()) && !TrialRoute.isParticipant(back, ga, b.getUUID()), titan + ": the party record is wrong");
            check(f, TrialSlot.of(GroupProgress.of(back, ga)) > 0, titan + ": the route has no slot number");
            // ほかのグループの初回の順路が動いていても、待たずに始まる (部屋は区画ごとに別。docs/design/32 の 5.1)
            b.setPos(door);
            TrialRoute.tick(back, titan, players, now + 1000);
            TrialRoute.tick(back, titan, players, now + 1000 + TrialRoute.ENTRY_WAIT + 10);
            check(f, TrialRoute.step(back, gb) == 1, titan + ": a second first-run group waited for the other group's route");
            check(f, TrialSlot.of(GroupProgress.of(back, gb)) > 0 && TrialSlot.of(GroupProgress.of(back, gb)) != TrialSlot.of(GroupProgress.of(back, ga)),
                    titan + ": the two groups did not get their own slots");
            TrialRoute.finish(back, gb);
            b.setPos(bAt);
            // 越えるたびに次へ (4 秒後)。別のグループは動かない
            for (int i = 1; i <= 3; i++) {
                String key = TrialRoute.keyOf(titan, draw, i);
                // レイドの部屋を越えた形 (RaidRun.complete と同じ口)。四体とも試練の経験値が入る (fun_review P2-1)
                long xpBefore = jp.revati.skyabyss.rpg.Growth.get(a).level * 1_000_000L + jp.revati.skyabyss.rpg.Growth.get(a).xp;
                TrialRoute.onCleared(back, ga, titan, key, null, true);
                check(f, jp.revati.skyabyss.rpg.Growth.get(a).level * 1_000_000L + jp.revati.skyabyss.rpg.Growth.get(a).xp > xpBefore,
                        titan + ": clearing raid room " + i + " gave no trial xp");
                boolean toMid = i == 3 && TrialMidBoss.needed(titan, draw);
                int expect = toMid ? 3 : i + 1;
                check(f, TrialRoute.step(back, ga) == expect && TrialRoute.mid(back, ga) == toMid, titan + ": after clearing " + key + " the step is " + TrialRoute.step(back, ga) + ", expected " + expect);
                check(f, TrialRoute.pendingCount() == 1, titan + ": the carry was not queued");
                TrialRoute.tick(back, titan, players, now + 1);
                check(f, TrialRoute.pendingCount() == 1, titan + ": the carry ran before its delay");
                TrialRoute.tick(back, titan, players, now + 1 + TrialRoute.CARRY_DELAY);
                check(f, TrialRoute.pendingCount() == 0, titan + ": the carry stayed queued");
                check(f, near(a, back, titan, draw, expect, toMid), titan + ": not carried to step " + expect + " rest " + TrialRoute.restOf(titan, draw, expect, false) + ": " + a.position());
                check(f, b.position().distanceTo(bAt) < 0.5, titan + ": a player of another group was carried");
            }
            if (TrialRoute.mid(back, ga)) {
                // レイドの順路: 三つの部屋のあとに中ボスの段 (倒したことにして心臓の前室へ)
                TrialRoute.onMidBossDefeated(back, ga, titan);
                TrialRoute.flushPending();
                TrialRoute.tick(back, titan, players, now + 1);
            }
            check(f, TrialRoute.step(back, ga) == TrialRoute.LAST_STEP && !TrialRoute.mid(back, ga), titan + ": the first route did not reach the heart directly");
            // 越える順が前後しても段は戻らない
            TrialRoute.onCleared(back, ga, titan, TrialRoute.keyOf(titan, draw, 1), null);
            check(f, TrialRoute.step(back, ga) == TrialRoute.LAST_STEP, titan + ": clearing an earlier trial again moved the step back");
            // 順路の途中で入口に立っても始め直さない
            a.setPos(door);
            TrialRoute.tick(back, titan, players, now + 5000);
            TrialRoute.tick(back, titan, players, now + 5000 + TrialRoute.ENTRY_WAIT + 10);
            check(f, TrialRoute.step(back, ga) == TrialRoute.LAST_STEP, titan + ": standing at the entrance mid-route restarted the route");
            // 心臓を止めると順路が終わり、記録が増え、番号が返る
            int clears = TrialCodex.get(back.getServer()).routeClears(a.getUUID(), titan);
            BackGroupData.Record r = GroupProgress.of(back, ga);
            TrialRoute.onHeartStopped(back, r);
            TrialData.onHeartStopped(r);
            check(f, TrialRoute.step(back, ga) == 0 && TrialSlot.of(r) == 0, titan + ": the heart stop did not end the route");
            check(f, TrialCodex.get(back.getServer()).routeClears(a.getUUID(), titan) == clears + 1, titan + ": the heart stop did not count the route");
            // 周回のグループは入口では始まらない (祠から)
            r.markFirstCleared();
            a.setPos(door);
            TrialRoute.tick(back, titan, players, now + 9000);
            TrialRoute.tick(back, titan, players, now + 9000 + TrialRoute.ENTRY_WAIT + 10);
            check(f, TrialRoute.step(back, ga) == 0, titan + ": a replay group started the route from the entrance");
            // あきらめ: 一人のグループは提案で成立し、記録が消える (外へ出すのは偽の人では確かめない)
            TrialRoute.start(back, ga, titan, List.of(a));
            check(f, TrialRoute.step(back, ga) == 1, titan + ": start did not set step 1");
            try {
                TrialRoute.propose(a);
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.info("SELFTEST trial route: sending out a fake player threw ({}); checking the record only", e.toString());
            }
            check(f, TrialRoute.step(back, ga) == 0, titan + ": abandoning alone did not end the route (step " + TrialRoute.step(back, ga) + ")");
            check(f, !TrialRoute.abandonPending(ga), titan + ": an abandon proposal stayed after settling");
            // 参加者が背にいなくなると、猶予のあと順路が終わる
            TrialRoute.start(back, ga, titan, List.of(a));
            List<ServerPlayer> none = List.of(b);
            TrialRoute.PLAYERS = l -> none;
            TrialRoute.flushPending();
            TrialRoute.tick(back, titan, none, now + 20000);
            check(f, TrialRoute.step(back, ga) == 1, titan + ": the route ended at once when the party left");
            TrialRoute.tick(back, titan, none, now + 20000 + TrialRoute.EMPTY_GRACE + 1);
            check(f, TrialRoute.step(back, ga) == 0, titan + ": the route stayed after the party left the back");
        } finally {
            clean(back, ga, gb);
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
    }

    // ---------------------------------------------------------------- 周回 (引いた三つ・戸・中ボス・二つの区画)
    private static void replay(List<String> f, ServerLevel back, String titan) {
        TrialRoute.clearAll();
        FakePlayer a = fake(back, "7a0b1d00-0000-4000-8000-00000000c003", "route_c");
        FakePlayer b = fake(back, "7a0b1d00-0000-4000-8000-00000000d004", "route_d");
        UUID ga = Groups.keyOf(a);
        UUID gb = Groups.keyOf(b);
        clean(back, ga, gb);
        List<ServerPlayer> players = List.of(a, b);
        TrialRoute.PLAYERS = l -> players;
        long now = back.getGameTime();
        int midSeen = 0;
        try {
            for (int cycle = 2; cycle <= 6; cycle++) {
                for (UUID g : List.of(ga, gb)) {
                    BackGroupData.Record r = GroupProgress.of(back, g);
                    r.markFirstCleared();
                    r.putInt(TrialData.DONE, cycle - 1);
                    r.remove(TrialData.DRAW);
                }
                TrialRoute.start(back, ga, titan, List.of(a));
                TrialRoute.start(back, gb, titan, List.of(b));
                int sa = TrialSlot.of(GroupProgress.of(back, ga));
                int sb = TrialSlot.of(GroupProgress.of(back, gb));
                check(f, sa > 0 && sb > 0 && sa != sb, titan + " cycle " + cycle + ": two routes share a slot (" + sa + ", " + sb + ")");
                TrialDraw.Draw draw = TrialData.current(back, ga);
                check(f, draw.cycle() == cycle, titan + ": the replay draw is on cycle " + draw.cycle() + ", expected " + cycle);
                for (int step = 1; step <= 3; step++) {
                    Def cur = TrialRoute.defOf(draw, step);
                    check(f, near(a, back, titan, draw, step, false), titan + " cycle " + cycle + ": not at step " + step + " (" + (cur == null ? "-" : cur.id()) + ") rest "
                            + TrialRoute.restOf(titan, draw, step, false) + ": " + a.position());
                    // 今の段のセルの戸だけが開いている
                    TrialRuntime.syncGates(back, ga, titan, draw);
                    for (TrialCell c : TrialCells.of(titan)) {
                        if (c.ports().entrance().isEmpty() || !TrialCatalog.deckPlaced(titan)) {
                            continue;
                        }
                        // 区画の写しで遊ぶセルは写しの戸が開き、手本の戸は閉じたまま
                        boolean want = cur != null && c.def().id().equals(cur.id());
                        TrialFrame cf = TrialSlot.frameOf(back, ga, c);
                        boolean open = GroupGates.isOpen(back, ga, cf.at(c.ports().entrance()));
                        check(f, open == want, titan + " cycle " + cycle + " step " + step + ": the gate of " + c.def().id() + " is " + (open ? "open" : "closed"));
                        if (!cf.isZero()) {
                            check(f, !GroupGates.isOpen(back, ga, c.ports().entrance()), titan + " cycle " + cycle + ": the template gate of " + c.def().id() + " opened");
                        }
                    }
                    TrialRoute.onCleared(back, ga, titan, TrialRoute.keyOf(titan, draw, step), null);
                    TrialRoute.flushPending();
                    TrialRoute.tick(back, titan, players, now + 1);
                }
                boolean mid = TrialMidBoss.needed(titan, draw);
                if (mid) {
                    midSeen++;
                    check(f, TrialRoute.mid(back, ga) && TrialRoute.step(back, ga) == 3, titan + " cycle " + cycle + ": no mid-boss step after the fight");
                    check(f, near(a, back, titan, draw, 3, true), titan + " cycle " + cycle + ": not at the mid-boss antechamber: " + a.position());
                    // 戦いそのものは試練の自己テストが見る。ここでは倒したことにする
                    TrialRoute.onMidBossDefeated(back, ga, titan);
                    TrialRoute.flushPending();
                    TrialRoute.tick(back, titan, players, now + 1);
                }
                check(f, TrialRoute.step(back, ga) == TrialRoute.LAST_STEP && !TrialRoute.mid(back, ga), titan + " cycle " + cycle + ": the replay did not reach the heart");
                check(f, near(a, back, titan, draw, TrialRoute.LAST_STEP, false), titan + " cycle " + cycle + ": not at the heart rest: " + a.position());
                check(f, TrialRoute.step(back, gb) == 1, titan + " cycle " + cycle + ": the other group's route moved");
                TrialRoute.finish(back, ga);
                TrialRoute.finish(back, gb);
                check(f, TrialSlot.of(GroupProgress.of(back, ga)) == 0 && !TrialSlot.inUse(back).contains(sa), titan + ": the slot was not returned");
            }
            SkyAbyss.LOGGER.info("SELFTEST trial route {}: five replays walked, {} with a mid-boss step", titan, midSeen);
        } finally {
            clean(back, ga, gb);
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
    }
}
