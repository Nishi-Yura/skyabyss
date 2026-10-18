package jp.revati.skyabyss.opening;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongPredicate;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LorePageItem;
import jp.revati.skyabyss.lore.LoreSetup;
import jp.revati.skyabyss.opening.OpeningLogic.Env;
import jp.revati.skyabyss.opening.OpeningLogic.Facts;
import jp.revati.skyabyss.opening.OpeningLogic.Fx;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 序盤の拍子 B0〜B10 (17 の 3 章・8.3 の表) の自己テスト (SKYABYSS_SELFTEST=1)。
//  1. 判断 (OpeningLogic) を、時刻と状況の列で流す: 新しいワールド / 雨の日 / 洞窟にこもった人 / 寝た人 / 戦闘中 / 村と宿りのない世界 /
//     ホラーの強さ 0 / マルチ (二人目) / すでに遊んでいた人。起きた順・回数・後ろへのずらし方・ホラーの決まりを数える
//  2. 目的の帯の表、岩鯨の門、状態の保存
//  3. 本物の世界と人 (FakePlayer): B0 → 帳面と書 → 頁 → コマンドで進める・戻す、人ごとの状態、影のあとの破れた頁
public final class OpeningSelfTest {
    private OpeningSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        try {
            newWorld(failures);
            rainyDay(failures);
            caveDweller(failures);
            sleeper(failures);
            combat(failures);
            noSites(failures);
            margin(failures);
            horrorOff(failures);
            multiplayer(failures);
            legacy(failures);
            objectives(failures);
            whaleGate(failures);
            saved(server.overworld(), failures);
            live(server, failures);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST opening exception", e);
            failures.add("exception " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST opening PASSED");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST opening FAILED: {}", f));
        }
    }

    // ================================================================ 判断の流し込み
    private record Event(long at, String name) {
    }

    // 一人の道のり (時刻から状況を作る関数)。既定は「新しいワールドで、ふつうに遊ぶ人」
    private static final class Route {
        long joinAt = 0;
        boolean horror = true;
        boolean sites = true;
        LongPredicate sky = t -> true;
        LongPredicate deep = t -> false;
        LongPredicate sleeping = t -> false;
        LongPredicate danger = t -> false;
        LongPredicate raining = t -> false;
        // 帳面の場所に立つ (2 秒後)、宿りに向かって歩き出す、足跡のそば、語り部と話す、イサナと話す、石板のそば
        long padAt = 200;
        long leaveCampAt = 1500;
        long footprintsAt = 2300;
        long storytellerAt = 2600;
        long isanaAt = 26000;
        long slabAt = 31000;
        long asksAgainAfterPage = 3000;
        long whaleAt = 59500;
    }

    private static final class Sim implements Fx {
        final Route r;
        final OpeningState st = new OpeningState();
        final List<Event> events = new ArrayList<>();
        final List<String> objectives = new ArrayList<>();
        // 世界の流れ (全員共通。二人目の影の呼び出しで動く)
        long passStart = 6000;
        long passEnd = 6000 + 1257;
        boolean passCounted;
        long firstPassTick = -1;
        // その人の記録
        boolean hasBook;
        boolean campLore;
        boolean footprintLore;
        boolean shadowLore;
        boolean boneLore;
        boolean marginLore;
        boolean whaleLore;
        long pageReadAt = -1;
        long pageAskedAt = -1;
        long now;
        int stepsStarted;
        int songStarted;
        long lastStepsStart = -1;
        long lastSongStart = -1;
        int randomCalls;
        final java.util.Random random = new java.util.Random(7);
        boolean violation;
        String violationText = "";

        Sim(Route r) {
            this.r = r;
        }

        long awakeAt() {
            return st.awakeAt();
        }

        boolean metStoryteller() {
            return now >= r.storytellerAt && st.awakeAt() >= 0;
        }

        boolean metIsana() {
            return now >= r.isanaAt && metStoryteller();
        }

        String stage() {
            if (!metStoryteller()) {
                return hasBook ? "follow_smoke" : "";
            }
            if (!shadowLore) {
                return "watch_sky";
            }
            if (pageAskedAt < 0 || now < pageAskedAt) {
                return "ask_storyteller";
            }
            if (!metIsana()) {
                return "find_chaser";
            }
            return whaleLore ? "heart" : "see_whale";
        }

        boolean passActive(long t) {
            return t >= passStart && t < passEnd;
        }

        void tick(long t) {
            now = t;
            // 世界の流れ (その人が入る前から進む)
            if (t >= passStart && !passCounted) {
                passCounted = true;
                if (firstPassTick < 0) {
                    firstPassTick = t;
                }
            }
            if (t < r.joinAt) {
                return;
            }
            if (t >= r.whaleAt) {
                whaleLore = true;
            }
            if (pageReadAt >= 0 && t >= pageReadAt && !shadowLore) {
                shadowLore = true;
                pageAskedAt = t + r.asksAgainAfterPage;
                events.add(new Event(t, "shadowLore"));
            }
            boolean awakeNow = !r.sleeping.test(t);
            Env env = new Env(t, t, true, awakeNow, r.sky.test(t), r.deep.test(t), false, r.danger.test(t), r.horror, passActive(t));
            boolean passEndedNow = st.awakeAt() >= 0 && passEnd >= st.awakeAt() && t >= passEnd && t - passEnd <= OpeningLogic.PASS_END_WINDOW;
            double campDist = t < r.leaveCampAt ? 6 : 60 + (t - r.leaveCampAt) * 0.2;
            double notebookDist = t >= r.padAt && t < r.padAt + 100 ? 1.5 : 8;
            double shelterDist = t < r.leaveCampAt ? 190 : Math.max(8, 190 - 0.2 * (t - r.leaveCampAt));
            boolean footNear = t >= r.footprintsAt && t < r.footprintsAt + 100;
            boolean slabNear = t >= r.slabAt && t < r.slabAt + 100;
            Facts f = new Facts(false, hasBook, r.sites, r.sites ? notebookDist : -1, r.sites ? campDist : -1, r.sites ? shelterDist : -1, r.sites && footNear,
                    r.sites && slabNear, metStoryteller(), metIsana(), campLore, footprintLore, shadowLore, boneLore, marginLore, whaleLore, passEndedNow,
                    passCounted ? 1 : 0, firstPassTick, stage());
            OpeningLogic.step(st, env, f, this);
            String objective = OpeningLogic.objective(st, f, env);
            if (!objective.isEmpty() && (objectives.isEmpty() || !objectives.get(objectives.size() - 1).equals(objective))) {
                objectives.add(objective);
            }
            checkNoOverlap();
        }

        // 足音と歌が重ならない・夜でない時に始まらない
        void checkNoOverlap() {
            if (lastStepsStart >= 0 && lastSongStart >= 0 && Math.abs(lastStepsStart - lastSongStart) < OpeningLogic.STEPS_TOTAL + OpeningLogic.SONG_TOTAL) {
                violation = true;
                violationText = "footsteps and song overlap";
            }
        }

        long first(String name) {
            for (Event e : events) {
                if (e.name().equals(name)) {
                    return e.at();
                }
            }
            return -1;
        }

        int count(String name) {
            int n = 0;
            for (Event e : events) {
                if (e.name().equals(name)) {
                    n++;
                }
            }
            return n;
        }

        // ---- Fx
        @Override
        public void awake() {
            events.add(new Event(now, "awake"));
        }

        @Override
        public void giveNotebook() {
            hasBook = true;
            events.add(new Event(now, "notebook"));
        }

        @Override
        public void campLore() {
            campLore = true;
            events.add(new Event(now, "campLore"));
        }

        @Override
        public void ringBell() {
            events.add(new Event(now, "bell"));
        }

        @Override
        public void footprintsLore() {
            footprintLore = true;
            events.add(new Event(now, "footprints"));
        }

        @Override
        public void startFootsteps(long at) {
            stepsStarted++;
            lastStepsStart = at;
            events.add(new Event(at, "footsteps"));
            // 夜 (一日の時刻 13000〜21000) 以外・寝ている・戦っている・影の最中には始めない
            long tod = at % 24000;
            if (tod < OpeningLogic.NIGHT_START || tod > OpeningLogic.NIGHT_LAST || r.sleeping.test(at) || r.danger.test(at) || passActive(at)) {
                violation = true;
                violationText = "footsteps started at " + at + " (tod " + tod + ", sleeping " + r.sleeping.test(at) + ", danger " + r.danger.test(at) + ")";
            }
            if (r.deep.test(at) && !r.sky.test(at)) {
                violation = true;
                violationText = "footsteps started deep underground at " + at;
            }
        }

        @Override
        public void footstepsLore() {
            events.add(new Event(now, "footstepsLore"));
        }

        @Override
        public void startSong(long at) {
            songStarted++;
            lastSongStart = at;
            events.add(new Event(at, "song"));
            long tod = at % 24000;
            if (tod < OpeningLogic.NIGHT_START || tod > OpeningLogic.NIGHT_LAST || r.sleeping.test(at) || r.danger.test(at) || !r.sky.test(at)) {
                violation = true;
                violationText = "song started at " + at + " (tod " + tod + ", sky " + r.sky.test(at) + ")";
            }
        }

        @Override
        public void songLore() {
            events.add(new Event(now, "songLore"));
        }

        @Override
        public void dropShadowPage() {
            events.add(new Event(now, "page"));
            // その人は頁を拾って読む (3 秒後)
            pageReadAt = now + 60;
        }

        @Override
        public void readShadowPage() {
            shadowLore = true;
            events.add(new Event(now, "pageAutoRead"));
        }

        @Override
        public void startPersonalShadow() {
            events.add(new Event(now, "personalShadow"));
            passStart = now + 1;
            passEnd = now + 1 + 1257;
            passCounted = true;
        }

        @Override
        public void bonesLore() {
            boneLore = true;
            events.add(new Event(now, "bones"));
        }

        @Override
        public void marginPage() {
            marginLore = true;
            events.add(new Event(now, "margin"));
        }

        @Override
        public int random(int min, int max) {
            randomCalls++;
            return min + random.nextInt(max - min + 1);
        }
    }

    private static Sim run(Route route, int days) {
        Sim sim = new Sim(route);
        for (long t = 0; t < days * 24000L; t += 20) {
            sim.tick(t);
        }
        return sim;
    }

    private static void check(boolean ok, String text, List<String> failures) {
        if (!ok) {
            failures.add(text);
        }
    }

    private static String listEvents(Sim sim) {
        StringBuilder b = new StringBuilder();
        for (Event e : sim.events) {
            b.append(e.name()).append('@').append(e.at()).append(' ');
        }
        return b.toString();
    }

    // ---- 新しいワールド: B0〜B10 が順に起きる
    private static void newWorld(List<String> failures) {
        Sim sim = run(new Route(), 5);
        String all = listEvents(sim);
        check(!sim.violation, "new world: " + sim.violationText, failures);
        // 起きた順 (17 の 3.1節): 目覚め → 帳面 → 野営地の頁 → 鐘 → 足跡 → 影のあとの頁 → 足音 → 骨 → 歌
        String[] order = {"awake", "notebook", "campLore", "page", "footsteps", "footstepsLore", "bones", "song", "songLore"};
        long previous = -1;
        for (String name : order) {
            long at = sim.first(name);
            check(at >= 0, "new world: beat event " + name + " never happened: " + all, failures);
            if (at >= 0) {
                check(at >= previous, "new world: " + name + " came before the one preceding it: " + all, failures);
                previous = at;
            }
        }
        check(sim.first("bell") >= 0 && sim.first("footprints") >= 0, "new world: the bell or the footprints beat never happened: " + all, failures);
        check(sim.first("awake") == 0, "new world: B0 did not start at the first moment: " + all, failures);
        // 帳面は、足を置いて 2 秒後 (100 tick 以上たってから) に拾える
        check(sim.first("notebook") >= 100 && sim.first("notebook") <= 400, "new world: the notebook was picked at " + sim.first("notebook"), failures);
        check(sim.count("bell") >= 2, "new world: the bell did not ring along the trail (" + sim.count("bell") + ")", failures);
        // 鐘は 30 秒 (600 tick) おき以上
        long lastBell = -1000;
        for (Event e : sim.events) {
            if (e.name().equals("bell")) {
                check(e.at() - lastBell >= OpeningLogic.BELL_EVERY, "new world: bells rang closer than 30 s apart", failures);
                lastBell = e.at();
            }
        }
        // 足音と歌は一度だけ (慣らさない)
        check(sim.count("footsteps") == 1 && sim.count("song") == 1, "new world: footsteps " + sim.count("footsteps") + ", songs " + sim.count("song"), failures);
        // 足音は夜 1 (日 1 の日没のあと)、歌は夜 2
        check(sim.first("footsteps") >= 13000 && sim.first("footsteps") <= 21000, "new world: footsteps in the first night: " + sim.first("footsteps"), failures);
        check(sim.first("song") >= 24000 + 13000 && sim.first("song") <= 24000 + 21000, "new world: song in the second night: " + sim.first("song"), failures);
        // 影のあとの頁 (影が去って 3 秒後)
        check(sim.first("page") >= sim.passEnd + 60 - 40 && sim.first("page") <= sim.passEnd + 60 + 400, "new world: the torn page fell at " + sim.first("page"), failures);
        // 野営地の頁は、帳面を拾って 3 秒後
        check(sim.first("campLore") - sim.first("notebook") >= 40 && sim.first("campLore") - sim.first("notebook") <= 200, "new world: camp page timing", failures);
        // 足音の頁は 3 度目のあと (340 tick 後)、歌の頁は歌が終わって 5 秒後 (900 tick 後)
        check(sim.first("footstepsLore") - sim.first("footsteps") >= OpeningLogic.STEPS_LORE - 20 && sim.first("footstepsLore") - sim.first("footsteps") <= OpeningLogic.STEPS_LORE + 40,
                "new world: footsteps page timing", failures);
        check(sim.first("songLore") - sim.first("song") >= OpeningLogic.SONG_LORE - 20 && sim.first("songLore") - sim.first("song") <= OpeningLogic.SONG_LORE + 40, "new world: song page timing", failures);
        // 拍子が全部済んでいる (B10 は岩鯨の頁で)
        for (OpeningBeat beat : OpeningBeat.values()) {
            check(sim.st.done(beat), "new world: beat " + beat.id + " is not done at the end: " + all, failures);
        }
        // 目的の帯の移り変わり: 野営地 → 煙 → 空 → 宿り → イサナ → 骨の丘 (寄り道) → 岩鯨
        List<String> want = List.of("examine_camp", "follow_smoke", "watch_sky", "ask_storyteller", "find_isana", "bone_hill", "see_whale");
        int at = 0;
        for (String id : sim.objectives) {
            if (at < want.size() && id.equals(want.get(at))) {
                at++;
            }
        }
        check(at == want.size(), "new world: objective strip sequence was " + sim.objectives, failures);
        // 岩鯨を迎えられるのは、影・歌・イサナを済ませたあと
        check(OpeningLogic.whaleReady(sim.st), "new world: the whale gate is closed after every beat", failures);
        // 状態が保存の形に収まる
        check(sim.randomCalls <= 4, "new world: random was used " + sim.randomCalls + " times (only the time window may be random)", failures);
    }

    // ---- 雨の日: 雨は拍子を止めない (影そのものは SkyEvents が最初の一回は雨でも起こす)。足音・歌・頁は同じ
    private static void rainyDay(List<String> failures) {
        Route route = new Route();
        route.raining = t -> t < 24000;
        Sim sim = run(route, 3);
        check(!sim.violation, "rainy day: " + sim.violationText, failures);
        check(sim.first("page") >= 0 && sim.first("footsteps") >= 0, "rainy day: the first shadow's page or the footsteps did not happen: " + listEvents(sim), failures);
        check(sim.st.done(OpeningBeat.SHADOW), "rainy day: B4 did not happen", failures);
    }

    // ---- 洞窟にこもった人: 影のあとの頁は届く。足音と歌は、外に出てから (深い地下では起こさない)
    private static void caveDweller(List<String> failures) {
        Route route = new Route();
        // 目覚めて 30 秒で洞窟へ入り、日 2 の朝 (tick 36000) まで出てこない
        route.sky = t -> t < 600 || t >= 36000;
        route.deep = t -> t >= 600 && t < 36000;
        route.slabAt = 1_000_000;
        route.footprintsAt = 1_000_000;
        route.storytellerAt = 40000;
        Sim sim = run(route, 5);
        check(!sim.violation, "cave dweller: " + sim.violationText, failures);
        check(sim.first("page") >= 0, "cave dweller: the torn page never arrived: " + listEvents(sim), failures);
        long steps = sim.first("footsteps");
        check(steps >= 36000, "cave dweller: footsteps came while the player was deep underground (" + steps + ")", failures);
        check(sim.count("footsteps") == 1, "cave dweller: footsteps happened " + sim.count("footsteps") + " times", failures);
        long song = sim.first("song");
        check(song > steps + 20000, "cave dweller: the song did not wait a later night (" + song + ")", failures);
        check(sim.count("song") == 1, "cave dweller: songs " + sim.count("song"), failures);
    }

    // ---- 寝た人: 夜 1 を寝て過ごしたら、足音は次の夜
    private static void sleeper(List<String> failures) {
        Route route = new Route();
        // 日没の少し前から夜明けまで寝る
        route.sleeping = t -> t >= 12500 && t < 24000 + 1000;
        Sim sim = run(route, 4);
        check(!sim.violation, "sleeper: " + sim.violationText, failures);
        long steps = sim.first("footsteps");
        check(steps >= 24000 + 13000, "sleeper: footsteps came before the next night (" + steps + "): " + listEvents(sim), failures);
        check(steps <= 24000 + 21000, "sleeper: footsteps missed the second night (" + steps + ")", failures);
        check(sim.count("footsteps") == 1, "sleeper: footsteps happened " + sim.count("footsteps") + " times", failures);
        check(sim.first("song") >= 48000 + 13000 || sim.first("song") < 0, "sleeper: the song did not wait for a night after the footsteps (" + sim.first("song") + ")", failures);
    }

    // ---- 戦闘中: 足音は後ろへずれる (終わったあとに)
    private static void combat(List<String> failures) {
        Route route = new Route();
        route.danger = t -> t >= 13000 && t < 19000;
        Sim sim = run(route, 3);
        check(!sim.violation, "combat: " + sim.violationText, failures);
        long steps = sim.first("footsteps");
        check(steps >= 19000 && steps <= 21000, "combat: footsteps were not moved behind the fight (" + steps + "): " + listEvents(sim), failures);
        // 夜が終わるまで戦い続けたら、その夜は起こさない (殺さない・戦闘中に重ねない)
        Route longFight = new Route();
        longFight.danger = t -> t >= 13000 && t < 22000;
        Sim all = run(longFight, 2);
        check(!all.violation && all.first("footsteps") < 0 || all.first("footsteps") >= 24000, "combat: footsteps came during a night-long fight: " + listEvents(all), failures);
    }

    // ---- 村も宿りもない世界 (構造物がまだ建っていない・古いワールド): 詰まらない
    private static void noSites(List<String> failures) {
        Route route = new Route();
        route.sites = false;
        route.storytellerAt = 20000;
        Sim sim = run(route, 5);
        check(!sim.violation, "no sites: " + sim.violationText, failures);
        long note = sim.first("notebook");
        check(note >= OpeningLogic.NO_CAMP_WAIT && note <= OpeningLogic.NO_CAMP_WAIT + 100, "no sites: the notebook came at " + note + ", not after 20 s", failures);
        check(sim.count("bell") == 0, "no sites: the bell rang without a lodge", failures);
        check(sim.first("campLore") < 0, "no sites: a camp page was recorded without a camp", failures);
        check(sim.first("page") >= 0 && sim.first("footsteps") >= 0 && sim.first("song") >= 0, "no sites: the later beats did not happen: " + listEvents(sim), failures);
        check(sim.objectives.contains("follow_smoke"), "no sites: no objective after the notebook: " + sim.objectives, failures);
        // 帳面の場所にも行かず、野営地から離れた人にも、帳面は荷物へ届く (野営地があっても)
        Route walker = new Route();
        walker.padAt = 1_000_000;
        Sim away = run(walker, 2);
        check(away.first("notebook") >= OpeningLogic.LEAVE_AFTER && away.first("notebook") <= OpeningLogic.LEAVE_AFTER + 2000, "no pad: the notebook came at " + away.first("notebook"), failures);
        // 目覚めて 5 分、野営地のそばを動かなくても渡る
        Route stay = new Route();
        stay.padAt = 1_000_000;
        stay.leaveCampAt = 1_000_000;
        Sim still = run(stay, 2);
        check(still.first("notebook") >= OpeningLogic.CAMP_FALLBACK - 20 && still.first("notebook") <= OpeningLogic.CAMP_FALLBACK + 100, "no pad, no walk: the notebook came at " + still.first("notebook"), failures);
    }

    // ---- 判断 17-5: 日 3 の朝にも宿りに着いていない人の帳面に、余白の一行 (一度だけ)
    private static void margin(List<String> failures) {
        Route route = new Route();
        route.storytellerAt = 1_000_000;
        route.footprintsAt = 1_000_000;
        // 野営地のそばから動かない (宿りには着かない)
        route.leaveCampAt = 1_000_000;
        Sim sim = run(route, 4);
        check(sim.count("margin") == 1 && sim.first("margin") >= 48000 && sim.first("margin") % 24000 <= 6000, "margin: the margin line was not given once on the morning of day 3: " + listEvents(sim), failures);
        Sim normal = run(new Route(), 4);
        check(normal.count("margin") == 0, "margin: a player who reached the lodge was given the margin line", failures);
    }

    // ---- ホラーの強さ 0: 足音・歌・影の頁は起こさず、拍子は先へ進み、誘導は残る
    private static void horrorOff(List<String> failures) {
        Route route = new Route();
        route.horror = false;
        Sim sim = run(route, 3);
        check(sim.count("footsteps") == 0 && sim.count("song") == 0 && sim.count("footstepsLore") == 0 && sim.count("songLore") == 0, "horror 0: a horror beat happened: " + listEvents(sim), failures);
        check(sim.count("page") == 0 && sim.count("personalShadow") == 0, "horror 0: the shadow page was given", failures);
        check(sim.st.done(OpeningBeat.SHADOW) && sim.st.done(OpeningBeat.FOOTSTEPS) && sim.st.done(OpeningBeat.SONG), "horror 0: the beats did not move on", failures);
        check(sim.first("notebook") >= 0 && sim.count("bell") > 0 && sim.first("footprints") >= 0, "horror 0: guidance was lost: " + listEvents(sim), failures);
        check(OpeningLogic.whaleReady(sim.st) || !sim.metIsana(), "horror 0: whale gate", failures);
    }

    // ---- マルチ: 二人目 (最初の影のあとに入った人) にも B0〜B4 が起きる
    private static void multiplayer(List<String> failures) {
        Route host = new Route();
        Sim a = new Sim(host);
        Route guestRoute = new Route();
        guestRoute.joinAt = 30000;
        guestRoute.padAt = 30000 + 200;
        guestRoute.leaveCampAt = 30000 + 1500;
        guestRoute.footprintsAt = 30000 + 2300;
        guestRoute.storytellerAt = 30000 + 2600;
        guestRoute.isanaAt = 30000 + 8000;
        guestRoute.slabAt = 1_000_000;
        guestRoute.whaleAt = 1_000_000;
        Sim b = new Sim(guestRoute);
        // 一つの世界の流れ (影の窓) を二人で分け合う。二人目が呼んだ影は、ふつうの影として一人目にも起きるが、一人目はすでに済んでいる
        for (long t = 0; t < 5 * 24000L; t += 20) {
            a.tick(t);
            b.tick(t);
            // 世界の影の窓を二人で合わせる (二人目の呼びかけは世界を動かす)
            if (b.passStart != a.passStart && b.passStart > a.passStart) {
                a.passStart = b.passStart;
                a.passEnd = b.passEnd;
            }
        }
        check(!a.violation && !b.violation, "multi: " + a.violationText + b.violationText, failures);
        check(a.first("awake") == 0, "multi: the first player's B0", failures);
        check(b.first("awake") == 30000, "multi: the second player's B0 did not start when they arrived: " + listEvents(b), failures);
        check(b.first("notebook") >= 30000 && b.first("notebook") <= 30000 + 400, "multi: the second player's notebook " + b.first("notebook"), failures);
        check(b.count("personalShadow") == 1, "multi: the second player was not given their own shadow (" + b.count("personalShadow") + "): " + listEvents(b), failures);
        long personal = b.first("personalShadow");
        long dayOfJoin = 30000 / 24000;
        check(personal / 24000 == dayOfJoin + 1 && personal % 24000 >= 1000 && personal % 24000 <= 9000, "multi: the personal shadow was not in the next day's window (" + personal + ")", failures);
        check(b.first("page") > personal, "multi: the second player's torn page did not follow their shadow", failures);
        check(a.count("personalShadow") == 0, "multi: the first player was given a second shadow", failures);
        // 二人目の足音は、影を見たあとの夜 (影の日の夜)
        check(b.first("footsteps") >= personal && b.first("footsteps") % 24000 >= 13000, "multi: the second player's footsteps came before their shadow: " + listEvents(b), failures);
        check(b.count("footsteps") == 1 && a.count("footsteps") == 1, "multi: footsteps counts", failures);
    }

    // ---- すでに遊んでいた人 (書や記録がある): 何も起こさない
    private static void legacy(List<String> failures) {
        OpeningState st = new OpeningState();
        List<String> log = new ArrayList<>();
        Fx quiet = new Fx() {
            @Override
            public void awake() {
                log.add("awake");
            }

            @Override
            public void giveNotebook() {
                log.add("notebook");
            }

            @Override
            public void campLore() {
                log.add("campLore");
            }

            @Override
            public void ringBell() {
                log.add("bell");
            }

            @Override
            public void footprintsLore() {
                log.add("footprints");
            }

            @Override
            public void startFootsteps(long now) {
                log.add("footsteps");
            }

            @Override
            public void footstepsLore() {
                log.add("footstepsLore");
            }

            @Override
            public void startSong(long now) {
                log.add("song");
            }

            @Override
            public void songLore() {
                log.add("songLore");
            }

            @Override
            public void dropShadowPage() {
                log.add("page");
            }

            @Override
            public void readShadowPage() {
                log.add("read");
            }

            @Override
            public void startPersonalShadow() {
                log.add("personal");
            }

            @Override
            public void bonesLore() {
                log.add("bones");
            }

            @Override
            public void marginPage() {
                log.add("margin");
            }

            @Override
            public int random(int min, int max) {
                return min;
            }
        };
        for (long t = 0; t < 100000; t += 20) {
            Env env = new Env(t, t, true, true, true, false, false, false, true, false);
            Facts f = new Facts(true, true, true, 1, 5, 100, true, true, false, false, false, false, false, false, false, false, false, 1, 100, "");
            OpeningLogic.step(st, env, f, quiet);
        }
        check(log.isEmpty(), "legacy: a player with a record was shown beats: " + log, failures);
        check(st.legacy() && OpeningLogic.whaleReady(st), "legacy: the state is not marked", failures);
        check(OpeningLogic.objective(st, new Facts(true, true, true, 1, 5, 100, false, false, false, false, false, false, false, false, false, false, false, 1, 100, "follow_smoke"),
                new Env(0, 0, true, true, true, false, false, false, true, false)).isEmpty(), "legacy: the opening objective was shown", failures);
    }

    // ---- 目的の帯の表 (17 の 11.2節)
    private static void objectives(List<String> failures) {
        OpeningState st = new OpeningState();
        st.awakeAt = 0;
        st.awakeDay = 0;
        Env day = new Env(0, 2000, true, true, true, false, false, false, true, false);
        Facts base = new Facts(false, true, true, 5, 5, 100, false, false, false, false, false, false, false, false, false, false, false, 0, -1, "");
        check(OpeningLogic.objective(st, base, day).equals("examine_camp"), "objective: before the notebook", failures);
        st.markDone(OpeningBeat.CAMP);
        String[][] table = {{"", "follow_smoke"}, {"follow_smoke", "follow_smoke"}, {"watch_sky", "watch_sky"}, {"ask_storyteller", "ask_storyteller"},
                {"find_chaser", "find_isana"}, {"see_whale", "see_whale"}, {"board", ""}, {"done", ""}};
        for (String[] row : table) {
            Facts f = new Facts(false, true, true, 5, 5, 100, false, false, false, false, false, false, false, false, false, false, false, 0, -1, row[0]);
            check(OpeningLogic.objective(st, f, day).equals(row[1]), "objective: stage '" + row[0] + "' gave '" + OpeningLogic.objective(st, f, day) + "'", failures);
        }
        // 骨の丘: 日 2 の昼、イサナと話したあと、メインが find_chaser / see_whale のとき
        st.markDone(OpeningBeat.ISANA);
        Env day2noon = new Env(30000, 24000 + 6000, true, true, true, false, false, false, true, false);
        Facts seeWhale = new Facts(false, true, true, 5, 5, 100, false, false, true, true, false, false, true, false, false, false, false, 1, 100, "see_whale");
        check(OpeningLogic.objective(st, seeWhale, day2noon).equals("bone_hill"), "objective: the bone hill was not offered on day 2 noon", failures);
        check(OpeningLogic.objective(st, seeWhale, day).equals("see_whale"), "objective: the bone hill was offered on day 1", failures);
        Env day2night = new Env(40000, 24000 + 15000, true, true, true, false, false, false, true, false);
        check(OpeningLogic.objective(st, seeWhale, day2night).equals("see_whale"), "objective: the bone hill was offered at night", failures);
        st.markDone(OpeningBeat.BONES);
        check(OpeningLogic.objective(st, seeWhale, day2noon).equals("see_whale"), "objective: the bone hill stayed after the visit", failures);
        // 夜の番号
        check(OpeningLogic.nightId(12999) == -1 && OpeningLogic.nightId(13000) == 0 && OpeningLogic.nightId(24000 + 20000) == 1 && OpeningLogic.nightId(24000 + 22000) == -1,
                "night ids", failures);
        // 帯に出る文の鍵が、すべて語りの担当の表にある (日英の長さは OpeningStorySelfTest が数える)
        for (String id : new String[]{"examine_camp", "follow_smoke", "watch_sky", "ask_storyteller", "find_isana", "see_whale", "bone_hill"}) {
            check(OpeningSelfTest.class.getResourceAsStream("/assets/skyabyss_opening/lang/ja_jp.json") != null, "objective lang file", failures);
            try (var in = OpeningSelfTest.class.getResourceAsStream("/assets/skyabyss_opening/lang/en_us.json")) {
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                check(text.contains("\"objective.skyabyss." + id + "\""), "objective text missing: " + id, failures);
            } catch (java.io.IOException e) {
                failures.add("objective lang read: " + e);
            }
        }
    }

    // ---- 岩鯨の門 (17 の 3.9節): 日 3 でも、影・歌・イサナの前には出ない。日 5 には出る
    private static void whaleGate(List<String> failures) {
        long day3eve = 2 * 24000L + 11500;
        long day5 = 4 * 24000L + 11500;
        check(!OpeningLogic.whaleGateOpen(day3eve, false), "whale gate: open on day 3 before the beats", failures);
        check(OpeningLogic.whaleGateOpen(day3eve, true), "whale gate: closed on day 3 after the beats", failures);
        check(OpeningLogic.whaleGateOpen(day5, false), "whale gate: closed on day 5 (the stuck-guard)", failures);
        OpeningState st = new OpeningState();
        st.awakeAt = 0;
        check(!OpeningLogic.whaleReady(st), "whale gate: ready with nothing done", failures);
        st.markDone(OpeningBeat.SHADOW);
        st.markDone(OpeningBeat.SONG);
        check(!OpeningLogic.whaleReady(st), "whale gate: ready before Isana", failures);
        st.markDone(OpeningBeat.ISANA);
        check(OpeningLogic.whaleReady(st), "whale gate: not ready after shadow, song and Isana", failures);
    }

    // ---- 状態の保存
    private static void saved(ServerLevel level, List<String> failures) {
        OpeningState st = new OpeningState();
        st.awakeAt = 123;
        st.awakeDay = 4;
        st.markDone(OpeningBeat.AWAKE);
        st.markDone(OpeningBeat.CAMP);
        st.markDone(OpeningBeat.SONG);
        st.stepsNight = 1;
        st.stepsAt = 55;
        st.stepsDoneNight = 1;
        st.songLoreAt = 777;
        st.pageAt = 5;
        st.pageReadAt = 6;
        st.campLoreAt = 9;
        st.bellAt = 11;
        st.cueUntil = 100;
        DataResult<Tag> encoded = OpeningState.CODEC.encodeStart(NbtOps.INSTANCE, st);
        Tag tag = encoded.result().orElse(null);
        check(tag != null, "save: encode failed " + encoded.error(), failures);
        if (tag != null) {
            OpeningState back = OpeningState.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
            check(st.equals(back), "save: the state did not survive the round trip: " + back + " vs " + st, failures);
            // 古い記録 (項目が足りない) も読める
            OpeningState empty = OpeningState.CODEC.parse(NbtOps.INSTANCE, new net.minecraft.nbt.CompoundTag()).result().orElse(null);
            check(empty != null && empty.awakeAt() == -1 && !empty.legacy(), "save: an empty record did not load", failures);
        }
        // 巻き戻し: その拍子とそれ以後が戻り、予定が消える
        st.rewindTo(OpeningBeat.FOOTSTEPS);
        check(st.done(OpeningBeat.CAMP) && !st.done(OpeningBeat.SONG) && st.stepsAt == -1 && st.songLoreAt == -1, "save: rewind did not clear the later beats", failures);
    }

    // ================================================================ 本物の世界と人
    private static FakePlayer fake(ServerLevel level, String uuidTail, String name) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-0000000b" + uuidTail), name));
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, spawn);
        p.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        p.getInventory().clearContent();
        return p;
    }

    private static void live(MinecraftServer server, List<String> failures) {
        ServerLevel level = server.overworld();
        FakePlayer a = fake(level, "0001", "[opening_a]");
        FakePlayer b = fake(level, "0002", "[opening_b]");
        // 人ごとの状態は別 (一人の B0 が、もう一人に移らない)
        Lore.reset(a);
        Lore.reset(b);
        OpeningDirector.reset(a);
        OpeningDirector.reset(b);
        OpeningDirector.run(level, a);
        OpeningState sa = OpeningDirector.state(a);
        check(sa.done(OpeningBeat.AWAKE) && !sa.legacy() && sa.awakeAt() >= 0, "live: B0 did not start for a new player: " + sa, failures);
        check(OpeningDirector.state(b).awakeAt() == -1, "live: the first player's B0 reached the second player", failures);
        // あとから入った人: クエストの条件が語り部との記録を「読んだだけ」で空の記録ができていても、遊んでいた人 (legacy) と取り違えず、開幕の場面が始まる
        // (マルチで二人目に開幕が出なかった原因)
        jp.revati.skyabyss.cinematic.Cinematics.stop(b, false);
        jp.revati.skyabyss.cinematic.Cinematics.state(b).forgetAll();
        jp.revati.skyabyss.npc.NpcBonds.of(b).hasFlag(jp.revati.skyabyss.npc.NpcSetup.STORYTELLER_ID, "x");
        check(jp.revati.skyabyss.npc.NpcBonds.of(b).bonds().isEmpty(), "live: reading a bond flag created a record", failures);
        jp.revati.skyabyss.npc.NpcBonds.of(b).bond(jp.revati.skyabyss.npc.NpcSetup.STORYTELLER_ID);
        check(!jp.revati.skyabyss.npc.NpcBonds.of(b).hasSubstance(), "live: an empty bond counted as played", failures);
        OpeningDirector.run(level, b);
        check(OpeningDirector.state(b).done(OpeningBeat.AWAKE) && !OpeningDirector.state(b).legacy(), "live: the second player's B0 did not start (empty bond made them legacy)", failures);
        check(jp.revati.skyabyss.cinematic.Cinematics.active(b) && jp.revati.skyabyss.cinematic.Scenes.OPENING.equals(jp.revati.skyabyss.cinematic.Cinematics.activeScene(b)),
                "live: the second player did not get the opening scene", failures);
        check(jp.revati.skyabyss.cinematic.Cinematics.state(b).seen(jp.revati.skyabyss.cinematic.Scenes.OPENING), "live: the opening was not recorded for the second player", failures);
        jp.revati.skyabyss.cinematic.Cinematics.stop(b, false);
        // 帳面を拾う → 伝承の書と最初の頁 (B1)。野営地の頁も
        check(!Lore.hasBook(a), "live: the player held the book before the notebook", failures);
        OpeningDirector.force(a, OpeningBeat.CAMP);
        check(Lore.hasBook(a) && Lore.state(a).has(Lore.BOOK_ENTRY), "live: picking up the notebook did not give the lore book", failures);
        check(Lore.state(a).has("prologue/camp"), "live: the camp page was not recorded", failures);
        check(sa.done(OpeningBeat.AWAKE) && sa.done(OpeningBeat.CAMP) && !sa.done(OpeningBeat.TRAIL), "live: beat state after B1: " + sa, failures);
        // 足跡・骨・足音・歌の頁
        OpeningDirector.force(a, OpeningBeat.FOOTPRINTS);
        check(Lore.state(a).has("prologue/footprints"), "live: the footprints page was not recorded", failures);
        OpeningDirector.force(a, OpeningBeat.BONES);
        check(Lore.state(a).has("prologue/bone_hill"), "live: the bone hill page was not recorded", failures);
        OpeningDirector.force(a, OpeningBeat.FOOTSTEPS);
        check(sa.done(OpeningBeat.FOOTSTEPS) && sa.stepsLoreAt > level.getGameTime() && sa.cueUntil > level.getGameTime(), "live: footsteps were not started with a scheduled page: " + sa, failures);
        OpeningDirector.force(a, OpeningBeat.SONG);
        check(sa.done(OpeningBeat.SONG) && sa.songLoreAt > level.getGameTime(), "live: the song was not started", failures);
        // 影のあとの破れた頁 (書を持っている人): 落ちてくる → 読めば記される → 読まれなければ自動で読まれる
        Lore.lock(a, "prologue/shadow");
        check(OpeningDirector.deferShadowToPage(a), "live: a player holding the book was not given the page route", failures);
        check(!OpeningDirector.deferShadowToPage(b), "live: a player without the book was given the page route", failures);
        OpeningDirector.Live fx = new OpeningDirector.Live(level, a, sa);
        fx.dropShadowPage();
        List<ItemEntity> pages = level.getEntitiesOfClass(ItemEntity.class, new AABB(a.blockPosition()).inflate(8, 12, 8),
                e -> e.getItem().is(LoreSetup.LORE_PAGE.get()) && LorePageItem.source(e.getItem()).equals("shadow"));
        check(pages.size() == 1, "live: the torn page did not fall from the sky (" + pages.size() + ")", failures);
        check(LorePageItem.candidates(a, "shadow").stream().anyMatch(e -> e.id().equals("prologue/shadow")), "live: reading the torn page would not record the shadow", failures);
        // 頁の実体に持ち主の印が付く。サーバーの再起動 (覚えている ID が消える) のあとでも、印で探して消せる (M10)
        check(pages.get(0).getPersistentData().hasUUID(OpeningDirector.PAGE_OWNER_TAG), "live: the torn page carries no owner tag", failures);
        OpeningDirector.forgetPagesForTest();
        fx.readShadowPage();
        check(pages.get(0).isRemoved(), "live: the torn page stayed on the ground after a restart", failures);
        check(Lore.state(a).has("prologue/shadow"), "live: the unread page was not read automatically", failures);
        // 岩鯨を迎えられる条件は人ごと
        OpeningDirector.force(a, OpeningBeat.WHALE);
        check(OpeningLogic.whaleReady(sa) && !OpeningLogic.whaleReady(OpeningDirector.state(b)), "live: the whale gate is not per player", failures);
        // 目印: 場所が建っていなければ null、建っていれば場所
        BlockPos camp = OpeningDirector.target(server.overworld(), "examine_camp");
        check(OpeningSites.planned(server) || camp == null, "live: a target was given for a camp that is not planned", failures);
        check(OpeningDirector.target(server.overworld(), "see_whale") == null && OpeningDirector.target(server.overworld(), "watch_sky") == null, "live: a target was given for an objective without a place", failures);

        // コマンド: 進める・戻す・状態・全部消す
        CommandSourceStack source = a.createCommandSourceStack().withPermission(4);
        run(server, source, "skyabyss opening back footprints", failures);
        check(!sa.done(OpeningBeat.FOOTPRINTS) && !sa.done(OpeningBeat.SONG) && sa.done(OpeningBeat.CAMP) && !Lore.state(a).has("prologue/footprints") && !Lore.state(a).has("prologue/sky_song"),
                "live: /skyabyss opening back did not rewind: " + sa, failures);
        run(server, source, "skyabyss opening song", failures);
        check(sa.done(OpeningBeat.SONG) && sa.done(OpeningBeat.FOOTSTEPS) && sa.done(OpeningBeat.SHADOW), "live: /skyabyss opening song did not run the song and earlier beats: " + sa, failures);
        run(server, source, "skyabyss opening status", failures);
        run(server, source, "skyabyss opening reset", failures);
        check(sa.awakeAt() == -1 && sa.doneBeats().isEmpty(), "live: /skyabyss opening reset did not clear: " + sa, failures);
        OpeningDirector.run(level, a);
        check(sa.done(OpeningBeat.AWAKE) && !sa.legacy(), "live: B0 did not play again after reset (the book was kept): " + sa, failures);

        // すでに遊んでいた人 (記録のある人) には何も起こさない
        FakePlayer old = fake(level, "0003", "[opening_old]");
        Lore.reset(old);
        OpeningDirector.reset(old);
        OpeningDirector.state(old).fresh = false;
        Lore.unlock(old, Lore.BOOK_ENTRY, true);
        OpeningDirector.run(level, old);
        check(OpeningDirector.state(old).legacy(), "live: a player with a record was shown B0: " + OpeningDirector.state(old), failures);

        for (FakePlayer p : new FakePlayer[]{a, b, old}) {
            Lore.reset(p);
            OpeningDirector.reset(p);
            OpeningDirector.state(p).fresh = false;
            p.getInventory().clearContent();
        }
    }

    private static void run(MinecraftServer server, CommandSourceStack source, String command, List<String> failures) {
        try {
            server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            failures.add("command '" + command + "' failed: " + e.getMessage());
        }
    }
}
