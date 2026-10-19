package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.back.StormBackState.Trial;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// 三つの試練のわかりやすさ (docs/design/25 の 3章。潮殻の TideGuide と同じ型)。
//  - 目的の板 (画面右上、風見の目盛りの下): 今いる試練の「いま何をするか」の一行と、進みの点。サーバーが人ごとに決めて Gauge に載せる
//  - 石の上と入口の看板の上の「進みの点」: 点がいくつ灯っているか (その人のグループの進み。その人にだけ見える)
//  - 成功の手応え: 割れた床を渡った・柱が一段満ちた (音 + 一行)
//  - 閉じた戸の前に立つと、閉じている理由と今の進みを言う
// 状態は人ごとの「最後に言った進み」だけ。進みの数え方は progress() の一か所
final class StormGuide {
    // 目的の板に載せる内容。id は lang の gui.skyabyss.storm_goal.<id>、先頭の語 (west/east/front/hub) が見出し。
    // done/total は点の数。a・b・c は文に入れる数
    record Goal(String id, int done, int total, int a, int b, int c) {
        static final Goal NONE = new Goal("", 0, 0, 0, 0, 0);
    }

    // 目的の板の文の id (lang の gui.skyabyss.storm_goal.<id>)。自己テストが ja・en の両方にあることを見る
    static final List<String> GOAL_IDS = List.of("west.gap", "west.knob", "west.stone", "west.done", "west.sleep", "east.rods", "east.raiju", "east.stone",
            "east.done", "east.sleep", "front.mirror", "front.stone", "front.done", "front.sleep", "hub.stones", "hub.keel");

    static final int WEST_TOTAL = WEST_GAPS.length + 1;
    static final int EAST_TOTAL = 5;
    static final int FRONT_TOTAL = 4;

    // 点の色 (灯った点は金、まだの点は暗い青灰)
    private static final DustParticleOptions LIT = new DustParticleOptions(new Vector3f(1f, 0.88f, 0.45f), 1.1f);
    private static final DustParticleOptions DIM = new DustParticleOptions(new Vector3f(0.28f, 0.36f, 0.4f), 1.0f);

    private static final int[] NO_RODS = new int[0];
    private static int[] prevCharge = NO_RODS;
    private static int counter;
    private static final Map<UUID, Goal> LAST = new HashMap<>();
    private static final Map<UUID, Integer> WEST_BEST = new HashMap<>();
    private static final Map<UUID, Long> DOOR_HINT = new HashMap<>();

    private StormGuide() {
    }

    static void clear() {
        counter = 0;
        prevCharge = NO_RODS;
        LAST.clear();
        WEST_BEST.clear();
        DOOR_HINT.clear();
    }

    // ---------------------------------------------------------------- 進みの数え方
    // 西: 渡った割れた床の数 (その人の位置で数える)
    static int gapsPassed(Vec3 v) {
        int n = 0;
        for (int[] g : WEST_GAPS) {
            if (v.x < g[0]) {
                n++;
            }
        }
        return n;
    }

    // 試練ごとの進みの点 (done, total)。viewer の位置は西の「渡った床」を数えるのにだけ使う
    static int[] progress(StormProgress p, Trial trial, Vec3 viewer) {
        switch (trial) {
            case WEST -> {
                if (p.rung(trial)) {
                    return new int[] {WEST_TOTAL, WEST_TOTAL};
                }
                return new int[] {StormBackLayout.inWest(viewer) ? gapsPassed(viewer) : 0, WEST_TOTAL};
            }
            case EAST -> {
                if (p.rung(trial)) {
                    return new int[] {EAST_TOTAL, EAST_TOTAL};
                }
                return new int[] {p.openDoorCount() + (p.raijuDefeated() ? 1 : 0), EAST_TOTAL};
            }
            default -> {
                if (p.rung(trial)) {
                    return new int[] {FRONT_TOTAL, FRONT_TOTAL};
                }
                return new int[] {Integer.bitCount(p.receivers() & 7), FRONT_TOTAL};
            }
        }
    }

    // その人の「いま何をするか」。試練の外では広間でだけ (三つの石の数)、ほかは NONE
    static Goal goalFor(ServerLevel level, ServerPlayer player) {
        Vec3 v = player.position();
        StormProgress p = StormProgress.of(level, player);
        if (StormBackLayout.inWest(v)) {
            int[] pr = progress(p, Trial.WEST, v);
            if (p.rung(Trial.WEST)) {
                return new Goal(p.sleeping() == Trial.WEST ? "west.sleep" : "west.done", pr[0], pr[1], 0, 0, 0);
            }
            int passed = gapsPassed(v);
            String id = passed < WEST_GAPS.length ? "west.gap" : v.y >= WEST_LEDGE.floor() ? "west.stone" : "west.knob";
            return new Goal(id, pr[0], pr[1], passed, WEST_GAPS.length, 0);
        }
        if (StormBackLayout.inEast(v)) {
            int[] pr = progress(p, Trial.EAST, v);
            if (p.rung(Trial.EAST)) {
                return new Goal(p.sleeping() == Trial.EAST ? "east.sleep" : "east.done", pr[0], pr[1], 0, 0, 0);
            }
            if (p.raijuDefeated()) {
                return new Goal("east.stone", pr[0], pr[1], 0, 0, 0);
            }
            int doors = p.openDoorCount();
            if (doors >= EAST_DOORS.length) {
                return new Goal("east.raiju", pr[0], pr[1], 0, 0, 0);
            }
            return new Goal("east.rods", pr[0], pr[1], doors, EAST_DOORS.length, 0);
        }
        if (StormBackLayout.inFront(v)) {
            int[] pr = progress(p, Trial.FRONT, v);
            if (p.rung(Trial.FRONT)) {
                return new Goal(p.sleeping() == Trial.FRONT ? "front.sleep" : "front.done", pr[0], pr[1], 0, 0, 0);
            }
            if (p.receivers() == 7) {
                return new Goal("front.stone", pr[0], pr[1], 0, 0, 0);
            }
            return new Goal("front.mirror", pr[0], pr[1], Integer.bitCount(p.receivers() & 7), 3, 0);
        }
        if (HUB.aabb().inflate(2, 8, 2).contains(v) || GALLERY.aabb().inflate(1, 2, 1).contains(v)) {
            int n = p.rungCount();
            return n >= 3 ? new Goal("hub.keel", 3, 3, 0, 0, 0) : new Goal("hub.stones", n, 3, n, 3, 0);
        }
        return Goal.NONE;
    }

    // 入口の看板の上に出す点の中心 (西・東・前)
    private static Vec3 entrancePips(Trial t) {
        Inscription sign = ENTRANCE_SIGNS.get(t.ordinal());
        Vec3 c = Vec3.atCenterOf(sign.pos());
        return c.add(sign.facing().getStepX() * 0.7, 1.4, sign.facing().getStepZ() * 0.7);
    }

    // ---------------------------------------------------------------- 毎 5 tick (StormBackTrials から。人がいるときだけ)
    static void tick(ServerLevel level) {
        counter++;
        refreshGoals(level);
        tickWest(level);
        tickEast(level);
        tickDoorHints(level);
        if (counter % 2 == 0) {
            tickPips(level);
        }
    }

    // 目的が変わった人にだけ、目盛りの板を送り直す (毎秒の送りを待たない)
    private static void refreshGoals(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            Goal g = goalFor(level, p);
            Goal old = LAST.put(p.getUUID(), g);
            if (old != null && !old.equals(g)) {
                PacketDistributor.sendToPlayer(p, StormBackTrials.gauge(level, p));
            }
        }
    }

    // ---------------------------------------------------------------- 西: 割れた床を渡った
    private static void tickWest(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) {
                continue;
            }
            if (!StormBackLayout.inWest(p.position()) || p.position().x > W0.x1()) {
                WEST_BEST.remove(p.getUUID());
                continue;
            }
            int passed = gapsPassed(p.position());
            int best = WEST_BEST.getOrDefault(p.getUUID(), 0);
            // 落ちて戻ったときに何度も言わないよう、最高の数を超えたときだけ。床の上 (足が骨の床の高さ以上) にいるときだけ数える
            if (passed > best && p.getY() >= BONE_FLOOR + 0.9) {
                WEST_BEST.put(p.getUUID(), passed);
                success(level, p, 1.0f + 0.12f * passed);
                if (passed < WEST_GAPS.length) {
                    Notice.bar(p, ModText.progress("message.skyabyss.storm.gap_crossed", passed, WEST_GAPS.length));
                } else {
                    Notice.bar(p, ModText.progress("message.skyabyss.storm.gaps_done"));
                }
            }
        }
    }

    // ---------------------------------------------------------------- 東: 柱が一段満ちた
    private static void tickEast(ServerLevel level) {
        UUID owner = jp.revati.skyabyss.titan.group.RoomLocks.owner(StormBackTrials.EAST_ROOM);
        if (owner == null) {
            prevCharge = NO_RODS;
            return;
        }
        int n = EAST_RODS.size();
        if (prevCharge.length != n) {
            prevCharge = new int[n];
            for (int i = 0; i < n; i++) {
                prevCharge[i] = StormRods.chargeOf(level, EAST_RODS.get(i));
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            int c = StormRods.chargeOf(level, EAST_RODS.get(i));
            if (c > prevCharge[i] && c < StormRods.FULL) {
                for (ServerPlayer p : Groups.online(level, owner)) {
                    if (StormEast.inCorridor(p.position())) {
                        Notice.bar(p, ModText.progress("message.skyabyss.storm.rod_charged", c, StormRods.FULL));
                        success(level, p, 0.9f + 0.15f * c);
                    }
                }
            }
            prevCharge[i] = c;
        }
    }

    // ---------------------------------------------------------------- 閉じた戸の前: 閉じている理由と今の進み (3 秒に一度)
    private static void tickDoorHints(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || now - DOOR_HINT.getOrDefault(p.getUUID(), -1000L) < 60) {
                continue;
            }
            StormProgress pr = StormProgress.of(level, p);
            Vec3 v = p.position();
            // 東の骨の戸
            for (int i = 0; i < EAST_DOORS.length; i++) {
                if (!pr.eastDoorPassable(i) && Math.abs(v.x - EAST_DOORS[i]) < 2.4 && v.z > -20 && v.z < -14 && v.y > BONE_FLOOR && v.y < BONE_FLOOR + 5) {
                    DOOR_HINT.put(p.getUUID(), now);
                    Notice.bar(p, ModText.hint("message.skyabyss.storm.door_shut", StormRods.chargeOf(level, EAST_RODS.get(i)), StormRods.FULL));
                    break;
                }
            }
            // 嘴の戸
            if (!pr.beakOpen() && Math.abs(v.x) < 3 && v.z < BEAK_DOOR_Z2 + 3 && v.z > BEAK_DOOR_Z1 - 1 && v.y > EYE_HALL.floor() && v.y < EYE_HALL.floor() + 4) {
                DOOR_HINT.put(p.getUUID(), now);
                Notice.bar(p, ModText.hint("message.skyabyss.storm.beak_shut", Integer.bitCount(pr.receivers() & 7)));
            }
            // 竜骨の門 (広間の床の栓)
            if (!pr.keelOpen() && Math.abs(v.x) < 3.2 && v.z > -7.2 && v.z < -0.8 && v.y > HUB.floor() && v.y < HUB.floor() + 3) {
                DOOR_HINT.put(p.getUUID(), now);
                Notice.bar(p, ModText.hint("message.skyabyss.storm.keel_shut", pr.rungCount()));
            }
        }
    }

    // ---------------------------------------------------------------- 石と入口の看板の上の「進みの点」
    private static void tickPips(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) {
                continue;
            }
            StormProgress pr = StormProgress.of(level, p);
            for (Trial t : Trial.values()) {
                Vec3 stone = Vec3.atCenterOf(StormBackTrials.stonePos(t)).add(0, 1.4, 0);
                Vec3 entrance = entrancePips(t);
                boolean nearStone = p.position().distanceToSqr(stone) < 22 * 22;
                boolean nearEntrance = p.position().distanceToSqr(entrance) < 18 * 18;
                if (!nearStone && !nearEntrance) {
                    continue;
                }
                int[] pg = progress(pr, t, p.position());
                if (nearStone) {
                    pips(level, p, stone, pg[0], pg[1]);
                }
                if (nearEntrance) {
                    pips(level, p, entrance, pg[0], pg[1]);
                }
            }
        }
    }

    // 輪になった点。灯った分は金、残りは暗い。見せるのはその人にだけ
    static void pips(ServerLevel level, ServerPlayer p, Vec3 center, int done, int total) {
        for (int i = 0; i < total; i++) {
            double a = Math.PI * 2 * i / total;
            level.sendParticles(p, i < done ? LIT : DIM, false, center.x + Math.cos(a) * 0.55, center.y, center.z + Math.sin(a) * 0.55, 1, 0, 0, 0, 0);
        }
        if (done >= total && total > 0) {
            level.sendParticles(p, ParticleTypes.END_ROD, false, center.x, center.y + 0.3, center.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    // ---------------------------------------------------------------- 成功の手応え (その人にだけ)
    private static void success(ServerLevel level, ServerPlayer p, float pitch) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_CHIME), SoundSource.PLAYERS, p.getX(),
                p.getY(), p.getZ(), 1.4f, pitch, level.random.nextLong()));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.NOTE_BLOCK_CHIME.value()), SoundSource.PLAYERS, p.getX(),
                p.getY(), p.getZ(), 0.6f, pitch, level.random.nextLong()));
    }
}
