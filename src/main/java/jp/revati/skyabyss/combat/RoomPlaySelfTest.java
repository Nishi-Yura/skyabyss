package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.RoomPlay.Room;
import jp.revati.skyabyss.combat.RoomPlay.Theme;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.titan.dungeon.RoomPlayRooms;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

// 広さの使い方 (RoomPlay。設計書 26 の 8.7 の B) の自己テスト。SKYABYSS_SELFTEST_ONLY="room play"
//  A. 人数ごとの湧き場所: 一・二人は入口側 (入口に近い・近すぎない)、人数が増えるほど広く、4 人から全部。砦・炉・嵐の巣の寄せ方も
//  B. 全部屋の罠・柱の置き場所: 入口・湧き場所・罠・柱が互いに重ならない。罠・柱の数は人数で単調に増える
//  C. 世界で: 板を置く→踏んで 10 tick→予告 40 tick (0.8 秒以上)→当たる。円の外・逃げた人は無傷。敵も踏めば動く (体力が減り足を取られる)。
//     ほかの傷の予告が当たる前後 10 tick には始めない。柱の段が人数 (3 人・5 人) で立つ
public final class RoomPlaySelfTest {
    private static final int X0 = 300;
    private static final int Z0 = 300;
    private static final int FLOOR = 200;
    private static final float HEALTH = 100f;

    private RoomPlaySelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("room play (load)", RoomPlaySelfTest::load),
                SelfTestRunner.Step.once("room play", 30, RoomPlaySelfTest::run));
    }

    static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = 17; cx <= 23; cx++) {
            for (int cz = 17; cz <= 22; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            spawns(f, notes);
            layouts(f, notes);
            world(level, f, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST room play crashed", e);
            f.add("crashed: " + e);
        } finally {
            server.setDifficulty(before, true);
            for (int cx = 17; cx <= 23; cx++) {
                for (int cz = 17; cz <= 22; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST room play note: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST room play PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST room play FAILED: {}", m));
        }
    }

    private static void check(boolean ok, String message, List<String> f) {
        if (!ok) {
            f.add(message);
        }
    }

    private static double mean(List<BlockPos> list, BlockPos entrance) {
        double s = 0;
        for (BlockPos p : list) {
            s += RoomPlay.flat(p, entrance);
        }
        return list.isEmpty() ? 0 : s / list.size();
    }

    // ---------------------------------------------------------------- A. 湧き場所
    private static void spawns(List<String> f, List<String> notes) {
        StringBuilder sb = new StringBuilder("spawn mean distance from the entrance (n=1..6):");
        for (String id : List.of("e1", "ck", "e3")) {
            Room r = RoomPlayRooms.byId(id);
            List<BlockPos> all = r.keepClear();
            double prevReach = -1;
            int prevSize = 0;
            sb.append(' ').append(id).append('[');
            for (int n = 1; n <= 6; n++) {
                List<BlockPos> out = RoomPlay.spawnNear(all, r.entrance(), n);
                check(all.containsAll(out) && !out.isEmpty(), id + " n=" + n + ": spawn list is not a subset", f);
                check(out.size() >= prevSize, id + " n=" + n + ": spawn list shrank (" + out.size() + " < " + prevSize + ")", f);
                double mean = mean(out, r.entrance());
                double reach = out.stream().mapToDouble(p -> RoomPlay.flat(p, r.entrance())).max().orElse(0);
                check(reach >= prevReach - 1e-9, id + " n=" + n + ": the spawn reach shrank as players were added", f);
                if (n <= 3 && all.stream().filter(p -> RoomPlay.flat(p, r.entrance()) >= RoomPlay.MIN_SPAWN_FROM_ENTRANCE).count() >= out.size()) {
                    for (BlockPos p : out) {
                        check(RoomPlay.flat(p, r.entrance()) >= RoomPlay.MIN_SPAWN_FROM_ENTRANCE, id + " n=" + n + ": spawn " + p + " is on top of the entrance", f);
                    }
                }
                if (n >= 4) {
                    check(out.size() == all.size(), id + " n=" + n + ": the whole hall should be used from 4 players", f);
                }
                prevReach = reach;
                prevSize = out.size();
                sb.append(Math.round(mean)).append(n < 6 ? "," : "");
            }
            sb.append(']');
            double soloReach = RoomPlay.spawnNear(all, r.entrance(), 1).stream().mapToDouble(p -> RoomPlay.flat(p, r.entrance())).max().orElse(0);
            double fullReach = all.stream().mapToDouble(p -> RoomPlay.flat(p, r.entrance())).max().orElse(0);
            // 長い広間 (E1・E3) は一人の湧きが奥へ届かない。短い広間は入口側の列が入口に近すぎて使えないので、届き方が同じでもよい
            check(soloReach <= fullReach + 1e-9 && (!(id.equals("e1") || id.equals("e3")) || soloReach < fullReach - 3.0),
                    id + ": a solo group's spawns reach too far (" + soloReach + " vs " + fullReach + ")", f);
        }
        notes.add(sb.toString());
        // 砦と炉の口 (順番つき): 一人は入口に近い口が先、4 人からはもとの順
        int[] ids = {0, 1, 2, 3, 4, 5};
        List<BlockPos> slots = new ArrayList<>();
        for (int i = 0; i < jp.revati.skyabyss.trial.whale.W10Fort.SLOTS.length; i++) {
            slots.add(jp.revati.skyabyss.trial.whale.W10Fort.slotSpawn(i));
        }
        BlockPos w10 = RoomPlayRooms.entranceOf("w10");
        int[] solo = RoomPlay.orderNear(ids, slots, w10, 1);
        double first = RoomPlay.flat(slots.get(solo[0]), w10);
        for (int i : ids) {
            check(first <= RoomPlay.flat(slots.get(i), w10) + 1e-9, "w10 solo order does not start at the nearest slot", f);
        }
        check(java.util.Arrays.equals(RoomPlay.orderNear(ids, slots, w10, 4), ids), "w10 order changed at 4 players", f);
        // 嵐の巣: 一人・二人の敵 (呼び手 1〜3、支え 1〜3) は、入口から 7 マス以上
        BlockPos e09 = RoomPlayRooms.entranceOf("s09");
        double bearing = RoomPlay.bearingFor(36, 104, e09);
        double worst = 99;
        for (int n = 1; n <= 2; n++) {
            for (int count = 1; count <= 3; count++) {
                for (int i = 0; i < count; i++) {
                    for (double rad : new double[] {10, 8}) {
                        double a = RoomPlay.angleNear(0, i, count, bearing + (rad == 8 ? 0.35 : 0), n);
                        double x = 36.5 + Math.cos(a) * rad;
                        double z = 104.5 + Math.sin(a) * rad;
                        worst = Math.min(worst, Math.hypot(x - e09.getX() - 0.5, z - e09.getZ() - 0.5));
                    }
                }
            }
        }
        check(worst >= 6.5, "an S09 spawn is " + worst + " from the entrance (< 6.5)", f);
        check(RoomPlay.angleNear(1.23, 0, 2, bearing, 3) == 1.23, "S09 angles changed at 3 players", f);
        notes.add("s09 nearest small-group spawn to the entrance: " + Math.round(worst * 10) / 10.0);
    }

    // ---------------------------------------------------------------- B. 置き場所
    private static void layouts(List<String> f, List<String> notes) {
        StringBuilder sb = new StringBuilder("rooms (traps/pillars):");
        for (Room r : RoomPlayRooms.all()) {
            RoomPlay.Layout l = RoomPlay.layout(r, p -> true, p -> true);
            f.addAll(RoomPlay.overlaps(r, l));
            int minTraps = r.area() >= 500 ? 2 : 1;
            int minPillars = r.area() >= 500 ? 4 : 1;
            check(l.traps().size() >= minTraps && l.pillars().size() >= minPillars, r.id() + ": too few sites (" + l.traps().size() + "/" + l.pillars().size() + ")", f);
            // 決定的
            RoomPlay.Layout again = RoomPlay.layout(r, p -> true, p -> true);
            check(again.traps().equals(l.traps()) && again.pillars().equals(l.pillars()), r.id() + ": the layout is not deterministic", f);
            int total = l.traps().size();
            int prevArmed = 0;
            int prevPillars = 0;
            for (int n = 1; n <= 6; n++) {
                int armed = RoomPlay.trapsArmed(total, n);
                int pillars = RoomPlay.pillarsVisible(l.pillars().size(), n);
                check(armed >= prevArmed && armed >= 1 && armed <= total, r.id() + " n=" + n + ": armed traps " + armed, f);
                check(pillars >= prevPillars, r.id() + " n=" + n + ": visible pillars shrank", f);
                prevArmed = armed;
                prevPillars = pillars;
            }
            check(RoomPlay.trapsArmed(total, 6) == total, r.id() + ": not all traps armed at 6 players", f);
            check(RoomPlay.trapsArmed(total, 1) < total || total <= 2, r.id() + ": a solo group has every trap armed", f);
            check(RoomPlay.pillarsVisible(l.pillars().size(), 1) * 2 >= l.pillars().size() - 1, r.id() + ": a solo group has too little cover", f);
            check(RoomPlay.pillarsVisible(l.pillars().size(), 6) == l.pillars().size(), r.id() + ": not all pillars at 6 players", f);
            sb.append(' ').append(r.id()).append('=').append(total).append('/').append(l.pillars().size())
                    .append("(armed ").append(RoomPlay.trapsArmed(total, 1)).append("..").append(RoomPlay.trapsArmed(total, 6)).append(')');
        }
        notes.add(sb.toString());
        dump();
    }

    // 確認シート (tools/sheets/room_size_b_sheet.py) の元: 環境変数 SKYABYSS_ROOMPLAY_DUMP にファイルの名前があれば、置き場所と人数ごとの湧きを書く
    private static void dump() {
        String path = System.getenv("SKYABYSS_ROOMPLAY_DUMP");
        if (path == null || path.isBlank()) {
            return;
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Room r : RoomPlayRooms.all()) {
            List<BlockPos> spawns = switch (r.id()) {
                case "s09" -> List.of();
                case "e10" -> {
                    List<BlockPos> l = new ArrayList<>();
                    for (int i = 0; i < jp.revati.skyabyss.trial.ember.E10Furnace.SPAWNS.length; i++) {
                        l.add(jp.revati.skyabyss.trial.ember.E10Furnace.spawnAt(i));
                    }
                    yield l;
                }
                default -> r.keepClear();
            };
            RoomPlay.Layout l = RoomPlay.layout(r, p -> true, p -> true);
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"id\":\"").append(r.id()).append("\",\"x1\":").append(r.x1()).append(",\"x2\":").append(r.x2()).append(",\"z1\":").append(r.z1())
                    .append(",\"z2\":").append(r.z2()).append(",\"entrance\":[").append(r.entrance().getX()).append(',').append(r.entrance().getZ())
                    .append("],\"spawns\":").append(points(spawns)).append(",\"spawnsByN\":{");
            for (int n = 1; n <= 6; n++) {
                sb.append(n > 1 ? "," : "").append('"').append(n).append("\":").append(points(RoomPlay.spawnNear(spawns, r.entrance(), n)));
            }
            sb.append("},\"traps\":").append(points(l.traps())).append(",\"pillars\":").append(points(l.pillars())).append('}');
        }
        sb.append(']');
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(path), sb.toString());
        } catch (java.io.IOException e) {
            SkyAbyss.LOGGER.warn("could not write the room play dump", e);
        }
    }

    private static String points(List<BlockPos> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(i > 0 ? "," : "").append('[').append(list.get(i).getX()).append(',').append(list.get(i).getZ()).append(']');
        }
        return sb.append(']').toString();
    }

    // ---------------------------------------------------------------- C. 世界で
    private static FakePlayer fake(ServerLevel level, String tag, double x, double y, double z) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("roomplay " + tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "[rp_" + tag + "]")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        try {
            java.lang.reflect.Field spawn = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            SkyAbyss.LOGGER.warn("could not clear the spawn invulnerability of a fake player", e);
        }
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        p.setHealth(HEALTH);
        p.moveTo(x, y, z, 0, 0);
        p.setOnGround(true);
        level.addFreshEntity(p);
        return p;
    }

    private static void stand(net.minecraft.world.entity.LivingEntity e, double x, double z) {
        e.moveTo(x, FLOOR + 1, z, 0, 0);
        e.setOnGround(true);
    }

    private static void platform(ServerLevel level, Room r) {
        for (int x = r.x1() - 3; x <= r.x2() + 3; x++) {
            for (int z = r.z1() - 3; z <= r.z2() + 3; z++) {
                level.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = FLOOR + 1; y <= FLOOR + 12; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void world(ServerLevel level, List<String> f, List<String> notes) {
        Room room = new Room("test", "selftest_room", Theme.BONE, X0, X0 + 50, Z0, Z0 + 30, FLOOR, 10, new BlockPos(X0, FLOOR + 1, Z0 + 15),
                List.of(new BlockPos(X0 + 40, FLOOR + 1, Z0 + 5), new BlockPos(X0 + 40, FLOOR + 1, Z0 + 25)));
        platform(level, room);
        RoomPlay.clearAll();
        RoomPlay.clearLog();
        RoomPlayData.get(level).remove(room.id());
        FakePlayer p1 = fake(level, "one", X0 + 3, FLOOR + 1, Z0 + 15);
        Mob mob = null;
        UUID group = Groups.keyOf(p1);
        List<FakePlayer> crowd = new ArrayList<>();
        try {
            mob = ModEntities.BONE_SENTINEL.get().create(level);
            mob.setNoAi(true);
            if (mob instanceof GroupOwned owned) {
                owned.setGroupId(group);
            }
            mob.moveTo(X0 + 48, FLOOR + 1, Z0 + 2, 0, 0);
            level.addFreshEntity(mob);

            long v = 0;
            RoomPlay.step(level, room, v);
            RoomPlay.Layout l = RoomPlayData.get(level).get(room.id());
            check(l != null && l.traps().size() >= 3 && l.pillars().size() >= 4, "the room was not decorated (" + (l == null ? "null" : l.traps().size() + "/" + l.pillars().size()) + ")", f);
            if (l == null) {
                return;
            }
            f.addAll(RoomPlay.overlaps(room, l));
            check(RoomPlay.trapSiteOk(level, l.traps().get(0)) == false, "the placed plate still counts as a free site (it was not placed)", f);
            check(level.getBlockState(l.traps().get(0)).is(Theme.BONE.accent), "the trap accent block was not placed", f);
            int tier0 = 0;
            int gated = 0;
            for (int i = 0; i < l.pillars().size(); i++) {
                BlockPos above = l.pillars().get(i).above();
                if (RoomPlay.pillarTier(i, l.pillars().size()) == 0) {
                    tier0++;
                    check(level.getBlockState(above).is(Theme.BONE.pillar), "pillar " + i + " (tier 0) was not placed", f);
                } else {
                    gated++;
                    check(GroupGates.isGate(level, above), "pillar " + i + " (gated) has no sealed gate", f);
                    check(GroupGates.isOpen(level, group, above), "a gated pillar is closed for a solo group", f);
                }
            }
            notes.add("decorated: " + l.traps().size() + " traps, " + l.pillars().size() + " pillars (" + tier0 + " permanent, " + gated + " gated)");

            // 1) 板の上に立つ → 10 tick のち予告 → 40 tick のち当たる (崩れる床: 傷 3 と足取り)
            BlockPos t0 = l.traps().get(0);
            stand(p1, t0.getX() + 0.5, t0.getZ() + 0.5);
            Telegraph.tickNow(level);
            long onSince = v;
            long started = -1;
            long impactAt = -1;
            int events0 = RoomPlay.log().size();
            for (int i = 0; i < 120 && impactAt < 0; i++) {
                v++;
                if (v % RoomPlay.STEP == 0) {
                    RoomPlay.step(level, room, v);
                    if (started < 0 && RoomPlay.activeTraps(room, group) > 0) {
                        started = v;
                    }
                }
                Telegraph.tickNow(level);
                if (RoomPlay.log().size() > events0) {
                    impactAt = v;
                }
            }
            check(started >= 0 && impactAt >= 0, "the stepped-on trap never fired", f);
            long delay = started - onSince;
            long warn = impactAt - started;
            check(delay >= 5 && delay <= RoomPlay.TRIGGER_TICKS + RoomPlay.STEP, "the trap started " + delay + " ticks after the step (expected 5..15)", f);
            check(warn >= 38 && warn <= 41 && warn >= Telegraph.MIN_WARN_TICKS, "the telegraph lasted " + warn + " ticks (expected about 40, at least 16)", f);
            float lost = HEALTH - p1.getHealth();
            check(Math.abs(lost - RoomPlay.PLAYER_DAMAGE) < 0.05f, "a player in the circle lost " + lost + " (expected 3)", f);
            check(p1.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "the crumbling floor did not slow the player", f);
            notes.add("trap timing: step->telegraph " + delay + " ticks, telegraph->impact " + warn + " ticks, player damage " + lost);

            // 2) 逃げれば無傷 (噴き上げ。二番目の罠)
            p1.setHealth(HEALTH);
            p1.removeAllEffects();
            BlockPos t1 = l.traps().get(1);
            stand(p1, t1.getX() + 0.5, t1.getZ() + 0.5);
            started = -1;
            impactAt = -1;
            events0 = RoomPlay.log().size();
            long startedV = -1;
            for (int i = 0; i < 120 && impactAt < 0; i++) {
                v++;
                if (v % RoomPlay.STEP == 0) {
                    RoomPlay.step(level, room, v);
                    if (started < 0 && RoomPlay.activeTraps(room, group) > 0) {
                        started = v;
                    }
                }
                if (started >= 0 && v == started + 20) {
                    stand(p1, t1.getX() + 0.5 + RoomPlay.TRAP_R + 1.5, t1.getZ() + 0.5);
                }
                Telegraph.tickNow(level);
                if (RoomPlay.log().size() > events0) {
                    impactAt = v;
                }
            }
            check(impactAt >= 0, "the second trap never fired", f);
            check(Math.abs(HEALTH - p1.getHealth()) < 0.05f, "a player who left the circle still lost " + (HEALTH - p1.getHealth()), f);
            check(RoomPlay.log().size() > events0 && RoomPlay.log().get(RoomPlay.log().size() - 1).kind() == RoomPlay.Kind.BURST, "the second trap is not a burst", f);
            notes.add("dodge: stepped out 20 ticks after the mark, no damage");

            // 3) 敵が踏めば動く (三番目の罠)。体力が減り、足を取られる
            BlockPos t2 = l.traps().get(2);
            stand(p1, X0 + 3, Z0 + 15);
            float mobBefore = mob.getHealth();
            stand(mob, t2.getX() + 0.5, t2.getZ() + 0.5);
            events0 = RoomPlay.log().size();
            impactAt = -1;
            for (int i = 0; i < 120 && impactAt < 0; i++) {
                v++;
                if (v % RoomPlay.STEP == 0) {
                    RoomPlay.step(level, room, v);
                }
                Telegraph.tickNow(level);
                if (RoomPlay.log().size() > events0) {
                    impactAt = v;
                }
            }
            check(impactAt >= 0, "an enemy standing on a plate did not set it off", f);
            check(mob.getHealth() < mobBefore - 5.5f, "the enemy took no trap damage (" + mobBefore + " -> " + mob.getHealth() + ")", f);
            check(mob.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "the crumbled floor did not slow the enemy", f);
            check(Math.abs(HEALTH - p1.getHealth()) < 0.05f, "a player far from the circle was hurt by an enemy-triggered trap", f);
            notes.add("enemy on a plate: health " + mobBefore + " -> " + mob.getHealth());

            // 4) ほかの傷の予告が当たる前後 10 tick には始めない (重ならない)
            v += RoomPlay.COOLDOWN_TICKS + 20;
            stand(mob, X0 + 48, Z0 + 2);
            RoomPlay.step(level, room, v);
            double cx = t0.getX() + 0.5;
            double cz = t0.getZ() + 0.5;
            long manualImpact = v + 50;
            boolean[] other = {false};
            Telegraph.in(level, TelegraphShape.circle(cx, cz, 3.0)).floor(FLOOR + 1).warn(50).danger().onHit((lv, pl) -> {
            }).onImpact(lv -> other[0] = true).start();
            stand(p1, cx, cz);
            events0 = RoomPlay.log().size();
            long trapImpact = -1;
            for (int i = 0; i < 200 && trapImpact < 0; i++) {
                v++;
                if (v % RoomPlay.STEP == 0) {
                    RoomPlay.step(level, room, v);
                }
                Telegraph.tickNow(level);
                if (RoomPlay.log().size() > events0) {
                    trapImpact = v;
                }
            }
            check(trapImpact >= 0, "the trap never fired after the other telegraph", f);
            check(other[0], "the other telegraph never hit", f);
            check(Math.abs(trapImpact - manualImpact) >= RoomPlay.SPACING_TICKS, "the trap hit " + Math.abs(trapImpact - manualImpact) + " ticks from another telegraph on the same floor (< 10)", f);
            notes.add("no overlap: trap impact " + (trapImpact - manualImpact) + " ticks from the other telegraph's");

            // 5) 柱の段: 人数が 3 人 / 5 人から立つ。一人は立たない
            RoomPlay.clearAll();
            GroupGates.reset(level, group);
            stand(p1, X0 + 3, Z0 + 15);
            List<net.minecraft.server.level.ServerPlayer> party = new ArrayList<>();
            party.add(p1);
            RoomPlay.stepGroupForTest(level, room, group, party, v + 1);
            int[] st = RoomPlay.probeState(room, group);
            check(st != null && st[0] == 1 && st[1] == 0, "solo: state " + java.util.Arrays.toString(st), f);
            check(coverSealed(level, group, l, 1) == 0 && coverSealed(level, group, l, 2) == 0, "a solo group has gated pillars up", f);
            for (int i = 0; i < 2; i++) {
                crowd.add(fake(level, "crowd" + i, X0 + 3, FLOOR + 1, Z0 + 12 + i));
            }
            party.addAll(crowd);
            RoomPlay.stepGroupForTest(level, room, group, party, v + 2);
            st = RoomPlay.probeState(room, group);
            check(st != null && st[0] == 3 && st[1] == 1, "three players: state " + java.util.Arrays.toString(st), f);
            int tier1 = coverSealed(level, group, l, 1);
            check(tier1 > 0 && coverSealed(level, group, l, 2) == 0, "three players should raise tier 1 only (" + tier1 + ", " + coverSealed(level, group, l, 2) + ")", f);
            for (int i = 2; i < 4; i++) {
                crowd.add(fake(level, "crowd" + i, X0 + 4, FLOOR + 1, Z0 + 12 + i));
            }
            party.clear();
            party.add(p1);
            party.addAll(crowd);
            RoomPlay.stepGroupForTest(level, room, group, party, v + 3);
            st = RoomPlay.probeState(room, group);
            check(st != null && st[0] == 5 && st[1] == 2, "five players: state " + java.util.Arrays.toString(st), f);
            check(coverSealed(level, group, l, 2) > 0, "five players should raise tier 2", f);
            // 別のグループには立たない
            check(GroupGates.isOpen(level, UUID.randomUUID(), l.pillars().get(l.pillars().size() - 1).above()), "gated pillars are closed for another group", f);
            notes.add("cover by entrants: 1 -> tier 0 only, 3 -> +" + tier1 + " cells (tier 1), 5 -> tier 2 as well");
        } finally {
            for (FakePlayer c : crowd) {
                c.discard();
            }
            p1.discard();
            if (mob != null) {
                mob.discard();
            }
            RoomPlay.clearAll();
            GroupGates.reset(level, group);
            RoomPlayData.get(level).remove(room.id());
            // 後片づけ: 置いた柱と板をなくす
            for (int x = room.x1() - 3; x <= room.x2() + 3; x++) {
                for (int z = room.z1() - 3; z <= room.z2() + 3; z++) {
                    for (int y = FLOOR - 1; y <= FLOOR + 12; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // その段の柱の、そのグループにだけ閉じているブロックの数
    private static int coverSealed(ServerLevel level, UUID group, RoomPlay.Layout l, int tier) {
        int n = 0;
        for (int i = 0; i < l.pillars().size(); i++) {
            if (RoomPlay.pillarTier(i, l.pillars().size()) == tier) {
                for (BlockPos c : RoomPlay.pillarCells(l.pillars().get(i))) {
                    if (GroupGates.isGate(level, c) && !GroupGates.isOpen(level, group, c)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
